package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    private static final Map<ClassLoader, ReflectionHandles> REFLECTION_BY_LOADER =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, ReflectionHandles>());

    private GboardSpacebarLanguageSwitchRuntime() {
    }

    public static Object patchIncomingSoftKeyMetadata(Object receiver, Object keyMetadata) {
        if (!(receiver instanceof View view) || view.getId() != SPACEBAR_VIEW_ID
                || keyMetadata == null) {
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

            Object builder = handles.keyMetadataBuilderConstructor.newInstance();
            handles.copyKeyMetadataMethod.invoke(builder, keyMetadata);
            Object slideLeft = handles.buildEmptySlideAction(handles.slideLeftActionType);
            Object slideRight = handles.buildEmptySlideAction(handles.slideRightActionType);
            if (slideLeft == null || slideRight == null) {
                return keyMetadata;
            }
            handles.putActionMethod.invoke(builder, slideLeft);
            handles.putActionMethod.invoke(builder, slideRight);
            Object patched = handles.buildKeyMetadataMethod.invoke(builder);
            return patched != null ? patched : keyMetadata;
        } catch (Throwable ignored) {
            // Keep the stock key metadata if the target Gboard ABI changes.
            return keyMetadata;
        }
    }

    /**
     * Called before Gboard's stock gesture dispatcher. Returns true only for
     * horizontal slide actions originating from the space key.
     */
    public static boolean maybeConsumeGesture(Object gestureDispatcher, Object action,
            Object keyMetadata) {
        if (!(action instanceof Enum<?> actionEnum)) {
            return false;
        }
        String actionName = actionEnum.name();
        if (!"SLIDE_LEFT".equals(actionName) && !"SLIDE_RIGHT".equals(actionName)) {
            return false;
        }
        if (keyMetadata == null) {
            return false;
        }

        ClassLoader classLoader = gestureDispatcher != null
                ? gestureDispatcher.getClass().getClassLoader()
                : keyMetadata.getClass().getClassLoader();
        final ReflectionHandles handles;
        try {
            handles = reflectionHandles(classLoader);
            if (!handles.isSpacebar(keyMetadata)
                    || !handles.hasAction(keyMetadata, handles.slideLeftActionType)
                    || !handles.hasAction(keyMetadata, handles.slideRightActionType)) {
                return false;
            }
        } catch (Throwable ignored) {
            // Never swallow a gesture unless the source key was positively identified.
            return false;
        }

        try {
            Context context = extractGestureDispatcherContext(gestureDispatcher, classLoader);
            if (context != null) {
                // Left = previous enabled language; right = next enabled language.
                switchEnabledLanguage(context, classLoader, "SLIDE_RIGHT".equals(actionName));
            }
        } catch (Throwable ignored) {
            // The action belongs to our spacebar, so consume its empty fallback
            // even if a transient manager/reflection failure prevents switching.
        }
        return true;
    }

    static int adjacentRotationIndex(int currentIndex, int itemCount, boolean next) {
        if (itemCount < 2 || currentIndex < 0 || currentIndex >= itemCount) {
            return -1;
        }
        return next
                ? (currentIndex + 1) % itemCount
                : (currentIndex + itemCount - 1) % itemCount;
    }

    private static void switchEnabledLanguage(Context context, ClassLoader classLoader,
            boolean next) throws Throwable {
        Class<?> managerClass = Class.forName("pga", false, classLoader);
        Class<?> sourceClass = Class.forName("pfi", false, classLoader);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object source = Enum.valueOf((Class<? extends Enum>) sourceClass.asSubclass(Enum.class),
                "GLOBE_KEY");

        Method managerFactory = managerClass.getDeclaredMethod("F", Context.class);
        managerFactory.setAccessible(true);
        Object manager = managerFactory.invoke(null, context);
        if (manager == null) {
            return;
        }

        Field rotationField = managerClass.getDeclaredField("Y");
        rotationField.setAccessible(true);
        Object rotation = rotationField.get(manager);
        if (rotation == null) {
            return;
        }

        Method currentEntryMethod = managerClass.getDeclaredMethod("C");
        currentEntryMethod.setAccessible(true);
        Object currentEntry = currentEntryMethod.invoke(manager);
        if (currentEntry == null) {
            return;
        }

        Class<?> rotationClass = rotation.getClass();
        Method currentIndexMethod = rotationClass.getDeclaredMethod("j", currentEntry.getClass());
        currentIndexMethod.setAccessible(true);
        int currentIndex = ((Number) currentIndexMethod.invoke(rotation, currentEntry)).intValue();

        Field entriesField = rotationClass.getDeclaredField("b");
        Field orderField = rotationClass.getDeclaredField("c");
        entriesField.setAccessible(true);
        orderField.setAccessible(true);
        Object entries = entriesField.get(rotation);
        int[] order = (int[]) orderField.get(rotation);
        if (entries == null || order == null || order.length < 2) {
            return;
        }

        int targetRotationIndex = adjacentRotationIndex(currentIndex, order.length, next);
        if (targetRotationIndex < 0) {
            return;
        }
        int targetEntryIndex = order[targetRotationIndex];
        Method getEntryMethod = entries.getClass().getMethod("get", int.class);
        getEntryMethod.setAccessible(true);
        Object targetEntry = getEntryMethod.invoke(entries, targetEntryIndex);
        if (targetEntry == null || targetEntry.equals(currentEntry)) {
            return;
        }

        Method setCurrentEntryMethod = managerClass.getDeclaredMethod(
                "l", currentEntry.getClass(), sourceClass);
        setCurrentEntryMethod.setAccessible(true);
        setCurrentEntryMethod.invoke(manager, targetEntry, source);
    }

    private static Context extractGestureDispatcherContext(Object dispatcher,
            ClassLoader classLoader) throws Throwable {
        if (dispatcher == null || classLoader == null) {
            return null;
        }
        Class<?> dispatcherClass = Class.forName("qil", false, classLoader);
        Field contextField = dispatcherClass.getDeclaredField("b");
        contextField.setAccessible(true);
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
        private final Object commitIntention;

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

            pressActionType = enumValue(actionTypeClass, "PRESS");
            slideLeftActionType = enumValue(actionTypeClass, "SLIDE_LEFT");
            slideRightActionType = enumValue(actionTypeClass, "SLIDE_RIGHT");
            commitIntention = enumValue(intentionClass, "COMMIT");

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

        boolean isSpacebar(Object metadata) throws Throwable {
            if (metadata == null) {
                return false;
            }
            Object pressAction = pressActionMethod.invoke(metadata, pressActionType);
            Object actionData = pressAction != null
                    ? primaryActionDataMethod.invoke(pressAction) : null;
            return actionData != null && actionDataKeycodeField.getInt(actionData) == SPACE_KEYCODE;
        }

        boolean hasAction(Object metadata, Object actionType) throws Throwable {
            return pressActionMethod.invoke(metadata, actionType) != null;
        }

        Object buildEmptySlideAction(Object actionType) throws Throwable {
            Object builder = actionBuilderConstructor.newInstance();
            actionTypeField.set(builder, actionType);
            setPopupLabelsMethod.invoke(builder, (Object) new String[] {""});
            setSingleActionMethod.invoke(builder, PLAIN_TEXT_KEYCODE, commitIntention, "");
            return buildActionMethod.invoke(builder);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }
}
