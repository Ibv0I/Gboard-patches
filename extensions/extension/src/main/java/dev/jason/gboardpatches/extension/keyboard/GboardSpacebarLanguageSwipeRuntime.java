package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class GboardSpacebarLanguageSwipeRuntime {
    private static final float HORIZONTAL_DOMINANCE = 1.25f;
    private static final Map<Object, Session> SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<Object, Session>());
    private static volatile NativeLanguageSwitcher nativeLanguageSwitcher =
            GboardSpacebarLanguageSwipeRuntime::switchToNextGboardLanguage;

    private GboardSpacebarLanguageSwipeRuntime() { }

    interface NativeLanguageSwitcher {
        boolean switchToNext(Object pointerOwner);
    }

    static void setNativeLanguageSwitcherForTests(NativeLanguageSwitcher switcher) {
        nativeLanguageSwitcher = switcher == null
                ? GboardSpacebarLanguageSwipeRuntime::switchToNextGboardLanguage : switcher;
    }

    private static final class Session {
        final Object originalSpacebar;
        final float startX, startY, thresholdPx;
        boolean qualified, cancelled;
        Session(Object originalSpacebar, float x, float y, float thresholdPx) {
            this.originalSpacebar = originalSpacebar;
            this.startX = x;
            this.startY = y;
            this.thresholdPx = thresholdPx;
        }
    }

    /** Called after Gboard's qin.F hit-test: recognize PRESS keycode 62 only. */
    public static void beginFromSpacebar(Object owner) {
        if (owner == null) return;
        try {
            ClassLoader loader = owner.getClass().getClassLoader();
            Object keyView = field(owner, "m").get(owner);
            if (keyView == null) { clear(owner); return; }
            Class<?> qae = Class.forName("defpackage.qae", false, loader);
            Object press = qae.getField("a").get(null);
            Object action = keyView.getClass().getMethod("f", qae).invoke(keyView, press);
            if (action == null) { clear(owner); return; }
            Object key = action.getClass().getMethod("b").invoke(action);
            if (key == null || field(key, "c").getInt(key) != 62) {
                clear(owner);
                return;
            }
            Object qil = field(owner, "r").get(owner);
            if (qil == null) { clear(owner); return; }
            Context context = (Context) field(qil, "b").get(qil);
            if (context == null) { clear(owner); return; }
            float x = field(owner, "d").getFloat(owner);
            float y = field(owner, "e").getFloat(owner);
            float threshold = Math.max(
                    ViewConfiguration.get(context).getScaledTouchSlop() * 2.0f,
                    context.getResources().getDisplayMetrics().density * 10.0f);
            begin(owner, keyView, x, y, threshold);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            clear(owner);
        }
    }

    /** Called after qin.t retargets the pointer and updates its coordinates. */
    public static void observePointer(Object owner, MotionEvent event, int pointerIndex) {
        if (owner == null) return;
        try {
            if (event == null || pointerIndex < 0 || pointerIndex >= event.getPointerCount()
                    || !Boolean.TRUE.equals(owner.getClass().getMethod("M").invoke(owner))) {
                observe(owner, Float.NaN, Float.NaN, true);
                return;
            }
            float x = event.getX(pointerIndex);
            float y = event.getY(pointerIndex);
            Object cursor = field(owner, "q").get(owner);
            boolean cursorOwns = cursor != null && Boolean.TRUE.equals(
                    cursor.getClass().getMethod("c", float.class, float.class, boolean.class)
                            .invoke(cursor, x, y, false));
            observe(owner, x, y, cursorOwns);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            observe(owner, Float.NaN, Float.NaN, true);
        }
    }

    /** Returning null consumes only the stock ActionDef; all other paths return it unchanged. */
    public static Object resolveAction(Object owner, Object stockAction,
            MotionEvent event, int actionIndex) {
        if (owner == null || event == null) return stockAction;
        final Session session;
        synchronized (SESSIONS) {
            session = SESSIONS.get(owner);
            if (session == null || session.cancelled || !session.qualified) return stockAction;
        }
        Field gestureState = null;
        Object previousState = null;
        try {
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_POINTER_UP) {
                return stockAction;
            }
            if (actionIndex != event.getActionIndex() || actionIndex < 0
                    || actionIndex >= event.getPointerCount()
                    || event.getPointerId(actionIndex) != field(owner, "a").getInt(owner)) {
                return stockAction;
            }

            gestureState = field(owner, "i");
            previousState = gestureState.get(owner);
            gestureState.set(owner, null);
            if (finish(owner, session.originalSpacebar)) return null;
            gestureState.set(owner, previousState);
            return stockAction;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            if (gestureState != null) {
                try { gestureState.set(owner, previousState); }
                catch (IllegalAccessException | RuntimeException ignoredRestore) { }
            }
            return stockAction;
        }
    }

    public static void begin(Object owner, Object originalSpacebar,
            float x, float y, float thresholdPx) {
        if (owner == null) return;
        if (originalSpacebar == null || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(thresholdPx) || thresholdPx <= 0f) {
            clear(owner);
            return;
        }
        synchronized (SESSIONS) {
            SESSIONS.put(owner, new Session(originalSpacebar, x, y, thresholdPx));
        }
    }

    public static void observe(Object owner, float x, float y, boolean cursorOwns) {
        if (owner == null) return;
        synchronized (SESSIONS) {
            Session s = SESSIONS.get(owner);
            if (s == null || s.cancelled || s.qualified) return;
            if (cursorOwns || !Float.isFinite(x) || !Float.isFinite(y)) {
                s.cancelled = true;
                return;
            }
            float dx = Math.abs(x - s.startX), dy = Math.abs(y - s.startY);
            if (dx >= s.thresholdPx && dx >= dy * HORIZONTAL_DOMINANCE) s.qualified = true;
        }
    }

    public static boolean finish(Object owner, Object originalSpacebar) {
        if (owner == null) return false;
        synchronized (SESSIONS) {
            Session s = SESSIONS.remove(owner);
            if (s == null || s.cancelled || !s.qualified || s.originalSpacebar != originalSpacebar) {
                return false;
            }
        }
        try { return nativeLanguageSwitcher.switchToNext(owner); }
        catch (RuntimeException | LinkageError ignored) { return false; }
    }

    public static void clear(Object owner) {
        if (owner != null) synchronized (SESSIONS) { SESSIONS.remove(owner); }
    }

    public static void clearAll() {
        synchronized (SESSIONS) { SESSIONS.clear(); }
    }

    private static Field field(Object owner, String name) throws NoSuchFieldException {
        return owner.getClass().getField(name);
    }

    /** Gboard's own configured language/layout rotation, not InputMethodManager. */
    private static boolean switchToNextGboardLanguage(Object owner) {
        try {
            Object qil = field(owner, "r").get(owner);
            if (qil == null) return false;
            Context context = (Context) field(qil, "b").get(qil);
            if (context == null) return false;
            ClassLoader loader = context.getClassLoader();
            Class<?> pga = Class.forName("defpackage.pga", false, loader);
            Object manager = pga.getMethod("F", Context.class).invoke(null, context);
            if (manager == null) return false;
            Class<?> pfi = Class.forName("defpackage.pfi", false, loader);
            Object globe = pfi.getField("d").get(null);
            return Boolean.TRUE.equals(pga.getMethod("t", boolean.class, pfi)
                    .invoke(manager, true, globe));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
            return false;
        }
    }
}
