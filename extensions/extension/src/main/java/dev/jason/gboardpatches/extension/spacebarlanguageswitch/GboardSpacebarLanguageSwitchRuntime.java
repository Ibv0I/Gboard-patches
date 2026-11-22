package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

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
    private static final int SPACEBAR_IME_PICKER_ACTION_CODE = -10022;
    private static final String TAG = "GboardSpacebarLang";

    private static final Map<ClassLoader, ReflectionHandles> REFLECTION_BY_LOADER =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, ReflectionHandles>());
    private static final Map<Object, SpacebarSwipeState> SPACEBAR_SWIPE_STATES =
            Collections.synchronizedMap(new WeakHashMap<Object, SpacebarSwipeState>());
    private static final Map<ClassLoader, Long> PENDING_CURSOR_RELEASES =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, Long>());
    private static final Map<ClassLoader, Long> PENDING_CURSOR_LONG_PRESSES =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, Long>());
    private static final long CURSOR_LONG_PRESS_WINDOW_MS = 5_000L;
    private static final long CURSOR_TRACKPAD_HOLD_MS = 850L;
    private static final java.util.concurrent.atomic.AtomicInteger POINTER_DEBUG_LOG_COUNT =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final class SpacebarSwipeState {
        final long downTime;
        boolean handled;
        boolean nativeCursorDrag;
        SpacebarSwipeState(long downTime) {
            this.downTime = downTime;
        }
    }

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
            boolean isSpacebar = handles.isSpacebar(keyMetadata);
            if (!isSpacebar) {
                return keyMetadata;
            }
            Log.i(TAG, "Found spacebar SoftKeyDef at viewId="
                    + Integer.toHexString(view.getId()));
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
            Object slideLeft = handles.buildEmptySlideAction(handles.slideLeftActionType);
            Object slideRight = handles.buildEmptySlideAction(handles.slideRightActionType);
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
            Log.i(TAG, "Injected horizontal e/f slide actions into spacebar viewId="
                    + Integer.toHexString(view.getId()) + ", keyCode=" + SPACE_KEYCODE);
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
            if (consumePendingSpacebarCursorRelease(classLoader, action, keyMetadata, handles)) {
                Log.i(TAG, "Consumed the spacebar press ending a cursor drag");
                return true;
            }

            boolean isSpacebar = handles.isSpacebar(keyMetadata);
            if (action.equals(handles.pressActionType) && isSpacebar
                    && hasPendingSpacebarCursorLongPress(classLoader)) {
                consumePendingSpacebarCursorLongPress(classLoader);
                Log.i(TAG, "Cleared unused cursor LONG_PRESS marker on spacebar release");
            }

            boolean isHorizontalSlide = action.equals(handles.slideLeftActionType)
                    || action.equals(handles.slideRightActionType);
            if (shouldPassThroughNativeCursorSlide(
                    isHorizontalSlide,
                    isSpacebar,
                    hasPendingSpacebarCursorLongPress(classLoader)
                            || hasPendingSpacebarCursorRelease(classLoader))) {
                Log.i(TAG, "Passed native spacebar cursor-trackpad action to Gboard");
                return false;
            }

            String dispatchedActionName = action instanceof Enum<?> enumAction
                    ? enumAction.name() : String.valueOf(action);
            if (shouldCaptureCursorLongPress(dispatchedActionName, isSpacebar)) {
                Context context = extractGestureDispatcherContext(
                        gestureDispatcher, classLoader);
                if (context != null && GboardSpacebarLanguageSwitchSettings.readEnabled(context)) {
                    markPendingSpacebarCursorLongPress(classLoader);
                    Log.i(TAG, "Observed spacebar LONG_PRESS; passing it to Gboard "
                            + "to activate the native cursor trackpad");
                }
            }

            if (action.equals(handles.slideLeftActionType)) {
                next = false;
                actionName = "SLIDE_LEFT";
            } else if (action.equals(handles.slideRightActionType)) {
                next = true;
                actionName = "SLIDE_RIGHT";
            } else {
                // The dispatcher can receive ordinary space presses as well. Log only
                // when the metadata belongs to the spacebar so we can distinguish
                // "gesture hook not reached" from "Gboard dispatched another action".
                if ("com.google.android.libraries.inputmethod.metadata.SoftKeyDef"
                        .equals(keyMetadata.getClass().getName())) {
                    try {
                        if (handles.isSpacebar(keyMetadata)) {
                            String dispatchedAction = action instanceof Enum<?> enumAction
                                    ? enumAction.name() : String.valueOf(action);
                            Log.i(TAG, "Spacebar dispatcher received non-horizontal action="
                                    + dispatchedAction
                                    + ", expected=" + handles.slideLeftActionType
                                    + "/" + handles.slideRightActionType
                                    + ", metadataHasLeft="
                                    + handles.hasAction(keyMetadata, handles.slideLeftActionType)
                                    + ", metadataHasRight="
                                    + handles.hasAction(keyMetadata, handles.slideRightActionType));
                        }
                    } catch (Throwable diagnosticError) {
                        Log.w(TAG, "Could not inspect non-horizontal spacebar dispatch",
                                diagnosticError);
                    }
                }
                return false;
            }
            Log.i(TAG, "Observed " + actionName + " gesture at Gboard dispatcher");
            boolean hasLeft = handles.hasAction(keyMetadata, handles.slideLeftActionType);
            boolean hasRight = handles.hasAction(keyMetadata, handles.slideRightActionType);
            if (!isSpacebar || !hasLeft || !hasRight) {
                Log.w(TAG, "Gesture source check failed: spacebar=" + isSpacebar
                        + ", leftAction=" + hasLeft + ", rightAction=" + hasRight);
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

    /**
     * Detects a horizontal swipe from the original spacebar touch coordinates.
     * Gboard's qin.h() may keep returning PRESS for this key, so we do not rely
     * on its slide resolver. We suppress key-edge retargeting while the finger
     * moves horizontally and switch exactly once after a deliberate swipe.
     */
    public static boolean maybePromoteSpacebarHorizontalSwipe(Object tracker,
            MotionEvent motionEvent, int pointerIndex) {
        if (tracker == null || motionEvent == null
                || motionEvent.getActionMasked() != MotionEvent.ACTION_MOVE
                || pointerIndex < 0 || pointerIndex >= motionEvent.getPointerCount()) {
            return false;
        }

        ClassLoader classLoader = tracker.getClass().getClassLoader();
        if (classLoader == null) {
            return false;
        }

        try {
            ReflectionHandles handles = reflectionHandles(classLoader);
            Object currentView = handles.pointerCurrentViewField.get(tracker);
            boolean longPressPending = hasPendingSpacebarCursorLongPress(classLoader);
            if (!(currentView instanceof View spacebarView)
                    || spacebarView.getId() != SPACEBAR_VIEW_ID) {
                if (longPressPending) {
                    logPointerDebug("MOVE ignored: currentView="
                            + (currentView == null ? "null" : currentView.getClass().getName())
                            + ", viewId=" + (currentView instanceof View
                                    ? "0x" + Integer.toHexString(((View) currentView).getId())
                                    : "n/a")
                            + ", tracker=" + tracker.getClass().getName());
                }
                return false;
            }
            if (!GboardSpacebarLanguageSwitchSettings.readEnabled(spacebarView.getContext())) {
                if (longPressPending) {
                    logPointerDebug("MOVE ignored: spacebar swipe setting is disabled");
                }
                return false;
            }

            long downTime = motionEvent.getDownTime();
            SpacebarSwipeState state;
            synchronized (SPACEBAR_SWIPE_STATES) {
                state = SPACEBAR_SWIPE_STATES.get(tracker);
                if (state == null || state.downTime != downTime) {
                    state = new SpacebarSwipeState(downTime);
                    SPACEBAR_SWIPE_STATES.put(tracker, state);
                }
            }
            if (state.nativeCursorDrag) {
                // The original Gboard pointer handler owns cursor movement.
                if (longPressPending) {
                    logPointerDebug("MOVE passthrough: native cursor drag already active");
                }
                return false;
            }
            if (state.handled) {
                return true;
            }

            // qin.B() stores the original ACTION_DOWN coordinates in b/c.
            float startX = handles.pointerStartXField.getFloat(tracker);
            float startY = handles.pointerStartYField.getFloat(tracker);
            if (!Float.isFinite(startX) || !Float.isFinite(startY)) {
                return false;
            }

            float deltaX = motionEvent.getX(pointerIndex) - startX;
            float deltaY = motionEvent.getY(pointerIndex) - startY;
            float absX = Math.abs(deltaX);
            float absY = Math.abs(deltaY);
            int touchSlop = ViewConfiguration.get(spacebarView.getContext())
                    .getScaledTouchSlop();
            float density = spacebarView.getResources().getDisplayMetrics().density;
            float switchThreshold = Math.max(touchSlop * 2.0f, 24.0f * density);

            // Once the movement is clearly horizontal, keep the pointer assigned
            // to the spacebar even if it crosses its visual edge. This prevents
            // qin.t() from retargeting it to an adjacent key before the swipe ends.
            if (Math.max(absX, absY) < touchSlop) {
                return false;
            }

            long heldDuration = Math.max(0L,
                    motionEvent.getEventTime() - motionEvent.getDownTime());
            long cursorHoldThreshold = Math.max(
                    CURSOR_TRACKPAD_HOLD_MS,
                    (long) ViewConfiguration.getLongPressTimeout());
            boolean dispatchedLongPress = longPressPending;
            if (dispatchedLongPress) {
                logPointerDebug("MOVE on spacebar: tracker=" + tracker.getClass().getName()
                        + ", viewId=0x" + Integer.toHexString(spacebarView.getId())
                        + ", downTime=" + downTime
                        + ", eventTime=" + motionEvent.getEventTime()
                        + ", held=" + heldDuration + "ms"
                        + ", dx=" + Math.round(deltaX) + ", dy=" + Math.round(deltaY)
                        + ", touchSlop=" + touchSlop
                        + ", cursorThreshold=" + cursorHoldThreshold + "ms");
            }
            if (dispatchedLongPress && heldDuration < cursorHoldThreshold) {
                // Gboard has recognized the long press, but cursor mode is not
                // active until about one second of holding. Reserve the gesture:
                // it must not fall through to our language-swipe recognizer.
                logPointerDebug("MOVE held below cursor threshold: "
                        + heldDuration + "/" + cursorHoldThreshold + "ms");
                return false;
            }
            if (shouldStartCursorDrag(
                    dispatchedLongPress, heldDuration, cursorHoldThreshold)) {
                if (absX <= absY * 1.2f) {
                    return false;
                }
                synchronized (SPACEBAR_SWIPE_STATES) {
                    state.nativeCursorDrag = true;
                }
                logPointerDebug("PROMOTED to native cursor drag: held=" + heldDuration
                        + "ms, dx=" + Math.round(deltaX) + ", dy=" + Math.round(deltaY));
                consumePendingSpacebarCursorLongPress(classLoader);
                markPendingSpacebarCursorRelease(classLoader);
                Log.i(TAG, "Entered native Gboard cursor trackpad"
                        + " (held=" + heldDuration + "ms, dx=" + Math.round(deltaX) + ")");
                // Preserve the original MotionEvent and let qin.t() perform native cursor movement.
                return false;
            }

            if (absX < touchSlop || absX <= absY * 1.2f) {
                return false;
            }

            if (absX < switchThreshold) {
                return true;
            }

            synchronized (SPACEBAR_SWIPE_STATES) {
                state.handled = true;
            }

            boolean next = deltaX > 0;
            String direction = next ? "RIGHT" : "LEFT";
            Log.i(TAG, "Detected spacebar swipe " + direction
                    + " (dx=" + Math.round(deltaX)
                    + ", dy=" + Math.round(deltaY)
                    + ", threshold=" + Math.round(switchThreshold) + ")");

            boolean switched = switchEnabledLanguage(
                    spacebarView.getContext(), classLoader, next);
            if (switched) {
                Log.i(TAG, "Switched language from spacebar swipe " + direction);
            } else {
                Log.w(TAG, "Could not switch language for spacebar swipe " + direction);
            }
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Failed to detect horizontal spacebar swipe", error);
            return false;
        }
    }

    /**
     * Suppresses only the spacebar long-press input-method picker request.
     * Other picker sources (globe key, accessibility, etc.) remain untouched.
     */
    public static boolean maybeConsumeSpacebarLanguagePickerEvent(Object inputEvent) {
        if (inputEvent == null) {
            return false;
        }
        try {
            ReflectionHandles handles = reflectionHandles(
                    inputEvent.getClass().getClassLoader());
            Object actionData = handles.inputEventGetActionDataMethod.invoke(inputEvent);
            if (actionData == null) {
                return false;
            }
            int eventCode = handles.actionDataKeycodeField.getInt(actionData);
            Object payload = handles.actionDataPayloadField.get(actionData);
            if (!isSpacebarLanguagePickerEvent(eventCode, payload)) {
                return false;
            }
            Log.i(TAG, "Suppressed spacebar long-press language picker; "
                    + "native cursor control remains available");
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Could not inspect spacebar long-press picker event", error);
            return false;
        }
    }

    static boolean shouldConsumePointerMove(boolean languageSwipeHandled,
            boolean cursorDragHandled) {
        return languageSwipeHandled || cursorDragHandled;
    }

    private static void logPointerDebug(String message) {
        try {
            if (POINTER_DEBUG_LOG_COUNT.getAndIncrement() < 120) {
                Log.i(TAG, "Cursor pointer diagnostic: " + message);
            }
        } catch (Throwable ignored) {
            // Diagnostics must never affect Gboard's pointer processing.
        }
    }

    private static void markPendingSpacebarCursorLongPress(ClassLoader classLoader) {
        if (classLoader != null) {
            PENDING_CURSOR_LONG_PRESSES.put(classLoader,
                    SystemClock.uptimeMillis() + CURSOR_LONG_PRESS_WINDOW_MS);
        }
    }

    private static boolean hasPendingSpacebarCursorLongPress(ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        Long deadline = PENDING_CURSOR_LONG_PRESSES.get(classLoader);
        if (deadline == null) {
            return false;
        }
        if (SystemClock.uptimeMillis() > deadline.longValue()) {
            PENDING_CURSOR_LONG_PRESSES.remove(classLoader);
            return false;
        }
        return true;
    }

    private static void consumePendingSpacebarCursorLongPress(ClassLoader classLoader) {
        if (classLoader != null) {
            PENDING_CURSOR_LONG_PRESSES.remove(classLoader);
        }
    }

    static boolean shouldCaptureCursorLongPress(String actionName, boolean isSpacebar) {
        return isSpacebar && "LONG_PRESS".equals(actionName);
    }

    static boolean shouldStartCursorDrag(boolean dispatchedLongPress,
            long heldDuration, long cursorHoldThreshold) {
        return dispatchedLongPress && heldDuration >= cursorHoldThreshold;
    }

    static boolean shouldPassThroughNativeCursorSlide(boolean horizontalSlide,
            boolean isSpacebar, boolean nativeCursorPendingOrActive) {
        return horizontalSlide && isSpacebar && nativeCursorPendingOrActive;
    }

    private static boolean hasPendingSpacebarCursorRelease(ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        Long deadline = PENDING_CURSOR_RELEASES.get(classLoader);
        if (deadline == null) {
            return false;
        }
        if (SystemClock.uptimeMillis() > deadline.longValue()) {
            PENDING_CURSOR_RELEASES.remove(classLoader);
            return false;
        }
        return true;
    }

    private static void markPendingSpacebarCursorRelease(ClassLoader classLoader) {
        if (classLoader != null) {
            PENDING_CURSOR_RELEASES.put(classLoader,
                    SystemClock.uptimeMillis() + 10_000L);
        }
    }

    private static boolean consumePendingSpacebarCursorRelease(ClassLoader classLoader,
            Object action, Object metadata, ReflectionHandles handles) throws Throwable {
        if (classLoader == null || action == null || metadata == null
                || !action.equals(handles.pressActionType) || !handles.isSpacebar(metadata)) {
            return false;
        }
        Long deadline = PENDING_CURSOR_RELEASES.get(classLoader);
        if (deadline == null) {
            return false;
        }
        PENDING_CURSOR_RELEASES.remove(classLoader);
        return SystemClock.uptimeMillis() <= deadline.longValue();
    }

    /** Consumes the normal PRESS event generated when a held spacebar cursor drag ends. */
    public static boolean maybeConsumeSpacebarCursorReleaseEvent(String actionTypeName,
            Object metadata, ClassLoader classLoader) {
        if (!"PRESS".equals(actionTypeName) || metadata == null || classLoader == null) {
            return false;
        }
        try {
            ReflectionHandles handles = reflectionHandles(classLoader);
            return handles.isSpacebar(metadata) && consumePendingSpacebarCursorRelease(classLoader);
        } catch (Throwable error) {
            Log.w(TAG, "Could not inspect the spacebar cursor-drag release event", error);
            return false;
        }
    }

    private static boolean consumePendingSpacebarCursorRelease(ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        Long deadline = PENDING_CURSOR_RELEASES.get(classLoader);
        if (deadline == null) {
            return false;
        }
        PENDING_CURSOR_RELEASES.remove(classLoader);
        return SystemClock.uptimeMillis() <= deadline.longValue();
    }

    static boolean isSpacebarLanguagePickerEvent(int eventCode, Object payload) {
        return eventCode == SPACEBAR_IME_PICKER_ACTION_CODE && "space".equals(payload);
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

        // Rotate only through Gboard's enabled language entries. Do not call
        // pga.t(), because that advances the Android input-method list and can
        // switch away from Gboard when another keyboard is installed.
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

        int targetRotationIndex = adjacentRotationIndex(currentIndex, order.length, next);
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
        Log.i(TAG, "Rotated Gboard language " + (next ? "forward" : "backward")
                + ": index " + currentIndex + " -> " + targetRotationIndex
                + ", enabledLanguages=" + order.length);
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
        private final Field actionDataPayloadField;
        private final Method inputEventGetActionDataMethod;
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
        private final Class<?> actionTypeClass;
        private final Field pointerCurrentViewField;
        private final Field pointerStartXField;
        private final Field pointerStartYField;
        private final Method pointerCurrentActionMethod;
        private final Method pointerResolveActionMethod;
        private final Method pointerFindActionMethod;

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
            Class<?> pointerTrackerClass = Class.forName("qin", false, classLoader);
            Class<?> inputEventClass = Class.forName("ohe", false, classLoader);
            this.actionTypeClass = actionTypeClass;

            pointerCurrentViewField = pointerTrackerClass.getDeclaredField("m");
            pointerStartXField = pointerTrackerClass.getDeclaredField("b");
            pointerStartYField = pointerTrackerClass.getDeclaredField("c");
            pointerCurrentActionMethod = pointerTrackerClass.getDeclaredMethod("i");
            pointerResolveActionMethod = pointerTrackerClass.getDeclaredMethod(
                    "h", float.class, float.class, actionTypeClass);
            pointerFindActionMethod = pointerTrackerClass.getDeclaredMethod("k", actionTypeClass);

            pressActionMethod = keyMetadataClass.getDeclaredMethod("g", actionTypeClass);
            primaryActionDataMethod = actionDefClass.getDeclaredMethod("b");
            actionDataKeycodeField = actionDataClass.getDeclaredField("c");
            actionDataPayloadField = actionDataClass.getDeclaredField("e");
            inputEventGetActionDataMethod = inputEventClass.getDeclaredMethod("g");
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

            // Gboard 18.5.4 horizontal movement is qae.e (left) / qae.f (right).
            // qin.t() independently confirms e/f for horizontal key-exit gestures;
            // c/d are vertical up/down actions. qae.a is the normal press action.
            pressActionType = enumConstant(actionTypeClass, "a");
            slideLeftActionType = enumConstant(actionTypeClass, "e");
            slideRightActionType = enumConstant(actionTypeClass, "f");
            // Gboard obfuscates enum backing fields, so reflection by field name fails.
            // Resolve the preserved enum constant name, matching the top-row swipe runtime.
            commitIntention = Enum.valueOf(intentionClass.asSubclass(Enum.class), "COMMIT");

            pressActionMethod.setAccessible(true);
            primaryActionDataMethod.setAccessible(true);
            actionDataKeycodeField.setAccessible(true);
            actionDataPayloadField.setAccessible(true);
            inputEventGetActionDataMethod.setAccessible(true);
            actionBuilderConstructor.setAccessible(true);
            actionTypeField.setAccessible(true);
            setPopupLabelsMethod.setAccessible(true);
            setSingleActionMethod.setAccessible(true);
            buildActionMethod.setAccessible(true);
            keyMetadataBuilderConstructor.setAccessible(true);
            copyKeyMetadataMethod.setAccessible(true);
            buildKeyMetadataMethod.setAccessible(true);
            putActionMethod.setAccessible(true);
            pointerCurrentViewField.setAccessible(true);
            pointerStartXField.setAccessible(true);
            pointerStartYField.setAccessible(true);
            pointerCurrentActionMethod.setAccessible(true);
            pointerResolveActionMethod.setAccessible(true);
            pointerFindActionMethod.setAccessible(true);
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

        Object buildEmptySlideAction(Object actionType) throws Throwable {
            Object builder = actionBuilderConstructor.newInstance();
            actionTypeField.set(builder, actionType);
            setPopupLabelsMethod.invoke(builder, (Object) new String[] {""});
            // Match GboardPatches' established custom-key action construction.
            setSingleActionMethod.invoke(builder, PLAIN_TEXT_KEYCODE, commitIntention, "");
            return buildActionMethod.invoke(builder);
        }
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
