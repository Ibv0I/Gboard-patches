package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import android.content.Context;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds left/right slide actions to the actual space key and routes them through
 * Gboard's enabled-language rotation list. A normal PRESS action is left intact.
 */
public final class GboardSpacebarLanguageSwitchRuntime {
    private static final int SPACEBAR_VIEW_ID = 0x7f0b05b5;
    private static final int SPACE_KEYCODE = 62;
    private static final int PLAIN_TEXT_KEYCODE = -0x2719;
    private static final String TAG = "GboardSpacebarLang";

    private static final Map<ClassLoader, ReflectionHandles> REFLECTION_BY_LOADER =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, ReflectionHandles>());

    private GboardSpacebarLanguageSwitchRuntime() {
    }

    public static Object patchIncomingSoftKeyMetadata(Object receiver, Object keyMetadata) {
        if (!(receiver instanceof View view) || view.getId() != SPACEBAR_VIEW_ID
                || keyMetadata == null) {
            return keyMetadata;
        }
        if (!GboardSpacebarLanguageSwitchSettings.readEnabled(view.getContext())) {
            return keyMetadata;
        }
        try {
            ReflectionHandles handles = reflectionHandles(receiver.getClass().getClassLoader());
            if (!handles.isSpacebar(keyMetadata)) {
                return keyMetadata;
            }
            if (handles.hasAction(keyMetadata, handles.slideLeftActionType)
                    && handles.hasAction(keyMetadata, handles.slideRightActionType)) {
                return keyMetadata;
            }

            Object pressAction = handles.pressAction(keyMetadata);
            if (pressAction == null) {
                return keyMetadata;
            }
            Object builder = handles.keyMetadataBuilderConstructor.newInstance();
            handles.copyKeyMetadataMethod.invoke(builder, keyMetadata);
            Object slideLeft = handles.buildEmptySlideAction(handles.slideLeftActionType, pressAction);
            Object slideRight = handles.buildEmptySlideAction(handles.slideRightActionType, pressAction);
            if (slideLeft == null || slideRight == null) {
                return keyMetadata;
            }
            handles.putActionMethod.invoke(builder, slideLeft);
            handles.putActionMethod.invoke(builder, slideRight);
            Object patched = handles.buildKeyMetadataMethod.invoke(builder);
            if (patched == null
                    || !handles.hasAction(patched, handles.slideLeftActionType)
                    || !handles.hasAction(patched, handles.slideRightActionType)) {
                Log.e(TAG, "Gboard returned spacebar metadata without both slide actions");
                return keyMetadata;
            }
            Log.d(TAG, "Injected left/right slide actions into the spacebar");
            return patched;
        } catch (Throwable error) {
            // Keep the stock key metadata if the target Gboard ABI changes.
            Log.e(TAG, "Failed to add horizontal slide actions to the spacebar", error);
            return keyMetadata;
        }
    }

    /**
     * Called before Gboard's stock gesture dispatcher. Returns true only for
     * horizontal slide actions originating from the space key.
     */
    public static boolean maybeConsumeGesture(Object gestureDispatcher, Object action,
            Object keyMetadata) {
        if (action == null || keyMetadata == null) {
            return false;
        }

        ClassLoader classLoader = gestureDispatcher != null
                ? gestureDispatcher.getClass().getClassLoader()
                : keyMetadata.getClass().getClassLoader();
        final ReflectionHandles handles;
        final boolean next;
        final String actionName;
        try {
            handles = reflectionHandles(classLoader);
            if (action.equals(handles.slideLeftActionType)) {
                next = false;
                actionName = "SLIDE_LEFT";
            } else if (action.equals(handles.slideRightActionType)) {
                next = true;
                actionName = "SLIDE_RIGHT";
            } else {
                return false;
            }
            if (!handles.isSpacebar(keyMetadata)
                    || !handles.hasAction(keyMetadata, handles.slideLeftActionType)
                    || !handles.hasAction(keyMetadata, handles.slideRightActionType)) {
                return false;
            }
        } catch (Throwable error) {
            Log.e(TAG, "Cannot verify the source spacebar for a slide gesture", error);
            return false;
        }

        try {
            Context context = extractGestureDispatcherContext(gestureDispatcher, classLoader);
            if (context == null) {
                Log.e(TAG, "Gesture dispatcher context was not available");
                return false;
            }
            if (!GboardSpacebarLanguageSwitchSettings.readEnabled(context)) {
                // The actions may still be present until Gboard rebuilds its keyboard view.
                // Consume the slide without switching or dispatching the empty placeholder action.
                Log.d(TAG, "Spacebar language swipe is disabled");
                return true;
            }

            boolean switched = switchEnabledLanguage(context, classLoader, next);
            if (!switched) {
                Log.w(TAG, "Gboard did not switch language for " + actionName);
                return false;
            }
            Log.d(TAG, "Handled " + actionName + " on the spacebar");
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Failed to switch language for " + actionName, error);
            return false;
        }
    }

    static int adjacentRotationIndex(int currentIndex, int itemCount, boolean next) {
        if (itemCount < 2 || currentIndex < 0 || currentIndex >= itemCount) {
            return -1;
        }
        return next
                ? (currentIndex + 1) % itemCount
                : (currentIndex + itemCount - 1) % itemCount;
    }

    private static boolean switchEnabledLanguage(Context context, ClassLoader classLoader,
            boolean next) throws Throwable {
        Class<?> managerClass = Class.forName("pga", false, classLoader);
        Class<?> entryClass = Class.forName("pev", false, classLoader);
        Class<?> sourceClass = Class.forName("pfi", false, classLoader);
        // The stock Gboard language-switch callback calls pga.t(z, pfi.d).
        // The obfuscated enum constant is named "d" in this Gboard build.
        Object source = enumConstant(sourceClass, "d");

        Method managerFactory = managerClass.getDeclaredMethod("F", Context.class);
        managerFactory.setAccessible(true);
        Object manager = managerFactory.invoke(null, context);
        if (manager == null) {
            return false;
        }

        Method currentEntryMethod = managerClass.getDeclaredMethod("C");
        currentEntryMethod.setAccessible(true);
        Object currentEntry = currentEntryMethod.invoke(manager);
        if (currentEntry == null || !entryClass.isInstance(currentEntry)) {
            return false;
        }

        if (next) {
            // Use Gboard's own "switchToNextInputMethodEntry" implementation.
            // Its boolean parameter controls the fallback behavior, not direction.
            Method switchNextMethod = managerClass.getDeclaredMethod("t", boolean.class, sourceClass);
            switchNextMethod.setAccessible(true);
            Object result = switchNextMethod.invoke(manager, false, source);
            return Boolean.TRUE.equals(result);
        }

        Field rotationField = findField(managerClass, "Y");
        Object rotation = rotationField.get(manager);
        if (rotation == null) {
            return false;
        }

        Class<?> rotationClass = rotation.getClass();
        Method currentIndexMethod = findCompatibleMethod(
                rotationClass, "j", currentEntry.getClass());
        Object indexValue = currentIndexMethod.invoke(rotation, currentEntry);
        if (!(indexValue instanceof Number)) {
            return false;
        }
        int currentIndex = ((Number) indexValue).intValue();

        Field entriesField = findField(rotationClass, "b");
        Field orderField = findField(rotationClass, "c");
        Object entries = entriesField.get(rotation);
        Object rawOrder = orderField.get(rotation);
        int[] order = readOrder(rawOrder);
        if (entries == null || order == null || order.length < 2) {
            return false;
        }

        int targetRotationIndex = adjacentRotationIndex(currentIndex, order.length, false);
        if (targetRotationIndex < 0) {
            return false;
        }
        int targetEntryIndex = order[targetRotationIndex];
        Object targetEntry = getIndexedValue(entries, targetEntryIndex);
        if (targetEntry == null || !entryClass.isInstance(targetEntry)
                || targetEntry.equals(currentEntry)) {
            return false;
        }

        Method setCurrentEntryMethod = findCompatibleMethod(
                managerClass, "l", currentEntry.getClass(), source.getClass());
        setCurrentEntryMethod.invoke(manager, targetEntry, source);
        return true;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method findCompatibleMethod(Class<?> type, String name,
            Class<?>... argumentTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!name.equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != argumentTypes.length) {
                    continue;
                }
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (!parameters[i].isAssignableFrom(argumentTypes[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static int[] readOrder(Object value) {
        if (value instanceof int[] order) {
            return order;
        }
        if (value instanceof List<?> list) {
            int[] order = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof Number)) {
                    return null;
                }
                order[i] = ((Number) item).intValue();
            }
            return order;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            int[] order = new int[length];
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                if (!(item instanceof Number)) {
                    return null;
                }
                order[i] = ((Number) item).intValue();
            }
            return order;
        }
        return null;
    }

    private static Object getIndexedValue(Object values, int index) throws Throwable {
        if (index < 0) {
            return null;
        }
        if (values instanceof List<?> list) {
            return index < list.size() ? list.get(index) : null;
        }
        if (values.getClass().isArray()) {
            return index < Array.getLength(values) ? Array.get(values, index) : null;
        }
        Method getMethod = findCompatibleMethod(values.getClass(), "get", int.class);
        return getMethod.invoke(values, index);
    }

    private static Context extractGestureDispatcherContext(Object dispatcher,
            ClassLoader classLoader) throws Throwable {
        if (dispatcher == null || classLoader == null) {
            return null;
        }
        Class<?> dispatcherClass = Class.forName("qil", false, classLoader);
        Field contextField = findField(dispatcherClass, "b");
        Object context = contextField.get(dispatcher);
        return context instanceof Context ? (Context) context : null;
    }

    private static ReflectionHandles reflectionHandles(ClassLoader classLoader)
            throws Throwable {
        if (classLoader == null) {
            throw new IllegalStateException("Gboard class loader is unavailable");
        }
        synchronized (REFLECTION_BY_LOADER) {
            ReflectionHandles cached = REFLECTION_BY_LOADER.get(classLoader);
            if (cached != null) {
                return cached;
            }
            ReflectionHandles created = new ReflectionHandles(classLoader);
            REFLECTION_BY_LOADER.put(classLoader, created);
            return created;
        }
    }

    private static final class ReflectionHandles {
        private final Method pressActionMethod;
        private final Method primaryActionDataMethod;
        private final Field actionDataKeycodeField;
        private final Constructor<?> actionBuilderConstructor;
        private final Field actionTypeField;
        private final Method setPopupLabelsMethod;
        private final Method setSingleActionMethod;
        private final Method buildActionMethod;
        private final Constructor<?> keyMetadataBuilderConstructor;
        private final Method copyKeyMetadataMethod;
        private final Method buildKeyMetadataMethod;
        private final Method putActionMethod;
        private final Object pressActionType;
        private final Object slideLeftActionType;
        private final Object slideRightActionType;
        private final Class<?> intentionClass;

        ReflectionHandles(ClassLoader classLoader) throws Throwable {
            Class<?> actionTypeClass = Class.forName("qae", false, classLoader);
            Class<?> keyMetadataClass = Class.forName(
                    "com.google.android.libraries.inputmethod.metadata.SoftKeyDef",
                    false, classLoader);
            Class<?> actionDefClass = Class.forName(
                    "com.google.android.libraries.inputmethod.metadata.ActionDef",
                    false, classLoader);
            Class<?> actionDataClass = Class.forName("qbc", false, classLoader);
            Class<?> actionBuilderClass = Class.forName("qaf", false, classLoader);
            Class<?> keyMetadataBuilderClass = Class.forName("qcv", false, classLoader);
            Class<?> intentionClass = Class.forName("qbb", false, classLoader);

            pressActionMethod = keyMetadataClass.getDeclaredMethod("g", actionTypeClass);
            primaryActionDataMethod = actionDefClass.getDeclaredMethod("b");
            actionDataKeycodeField = actionDataClass.getDeclaredField("c");
            actionBuilderConstructor = actionBuilderClass.getDeclaredConstructor();
            actionTypeField = actionBuilderClass.getDeclaredField("a");
            setPopupLabelsMethod = actionBuilderClass.getDeclaredMethod("t", String[].class);
            setSingleActionMethod = actionBuilderClass.getDeclaredMethod(
                    "s", int.class, intentionClass, Object.class);
            buildActionMethod = actionBuilderClass.getDeclaredMethod("c");
            keyMetadataBuilderConstructor = keyMetadataBuilderClass.getDeclaredConstructor();
            copyKeyMetadataMethod = keyMetadataBuilderClass.getDeclaredMethod("k",
                    keyMetadataClass);
            buildKeyMetadataMethod = keyMetadataBuilderClass.getDeclaredMethod("b");
            putActionMethod = keyMetadataBuilderClass.getDeclaredMethod("t", actionDefClass);

            // Gboard 18.5.4 obfuscates qae. qin.h(float, float, qae)
            // maps horizontal left/right swipes to qae.c/qae.d and vertical
            // up/down swipes to qae.e/qae.f. qae.a remains the press action.
            pressActionType = enumConstant(actionTypeClass, "a");
            slideLeftActionType = enumConstant(actionTypeClass, "c");
            slideRightActionType = enumConstant(actionTypeClass, "d");
            this.intentionClass = intentionClass;

            pressActionMethod.setAccessible(true);
            primaryActionDataMethod.setAccessible(true);
            actionDataKeycodeField.setAccessible(true);
            actionBuilderConstructor.setAccessible(true);
            actionTypeField.setAccessible(true);
            setPopupLabelsMethod.setAccessible(true);
            setSingleActionMethod.setAccessible(true);
            buildActionMethod.setAccessible(true);
            keyMetadataBuilderConstructor.setAccessible(true);
            copyKeyMetadataMethod.setAccessible(true);
            buildKeyMetadataMethod.setAccessible(true);
            putActionMethod.setAccessible(true);
        }

        Object pressAction(Object metadata) throws Throwable {
            return metadata != null ? pressActionMethod.invoke(metadata, pressActionType) : null;
        }

        boolean isSpacebar(Object metadata) throws Throwable {
            Object pressAction = pressAction(metadata);
            Object actionData = pressAction != null
                    ? primaryActionDataMethod.invoke(pressAction) : null;
            return actionData != null && actionDataKeycodeField.getInt(actionData) == SPACE_KEYCODE;
        }

        boolean hasAction(Object metadata, Object actionType) throws Throwable {
            return pressActionMethod.invoke(metadata, actionType) != null;
        }

        Object buildEmptySlideAction(Object actionType, Object pressAction) throws Throwable {
            Object builder = actionBuilderConstructor.newInstance();
            actionTypeField.set(builder, actionType);
            Object intention = fieldValueOfType(pressAction, intentionClass);
            setPopupLabelsMethod.invoke(builder, (Object) new String[] {""});
            // Reuse the real spacebar PRESS intention rather than assuming a
            // deobfuscated enum name for qbb in the current Gboard build.
            setSingleActionMethod.invoke(builder, PLAIN_TEXT_KEYCODE, intention, "");
            return buildActionMethod.invoke(builder);
        }
    }

    private static Object fieldValueOfType(Object instance, Class<?> fieldType)
            throws ReflectiveOperationException {
        Class<?> current = instance.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == fieldType) {
                    field.setAccessible(true);
                    Object value = field.get(instance);
                    if (value != null) {
                        return value;
                    }
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException(
                "No " + fieldType.getName() + " field on " + instance.getClass().getName());
    }

    private static Object enumConstant(Class<?> enumClass, String fieldName)
            throws ReflectiveOperationException {
        Field field = enumClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(null);
        if (value == null || !enumClass.isInstance(value)) {
            throw new IllegalStateException(
                    "Field " + enumClass.getName() + "." + fieldName + " is not an enum constant");
        }
        return value;
    }

}
