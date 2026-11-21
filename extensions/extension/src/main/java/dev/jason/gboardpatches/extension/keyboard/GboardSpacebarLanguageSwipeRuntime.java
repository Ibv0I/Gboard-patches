package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Small, fail-closed state machine for a horizontal gesture that starts on the spacebar.
 *
 * The bytecode patch is responsible for calling begin only for the original spacebar key,
 * observe only after Gboard's cursor/trackpad ownership test, and finish immediately before
 * Gboard dispatches the normal key action. A true finish result means that action may be
 * consumed and the caller may invoke Gboard's native next-language action.
 */
public final class GboardSpacebarLanguageSwipeRuntime {
    private static final float HORIZONTAL_DOMINANCE = 1.25f;

    private static final Map<Object, Session> SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<Object, Session>());

    private GboardSpacebarLanguageSwipeRuntime() {
    }

    private static final class Session {
        final Object originalSpacebar;
        final float startX;
        final float startY;
        final float thresholdPx;
        boolean qualified;
        boolean cancelled;

        Session(Object originalSpacebar, float startX, float startY, float thresholdPx) {
            this.originalSpacebar = originalSpacebar;
            this.startX = startX;
            this.startY = startY;
            this.thresholdPx = thresholdPx;
        }
    }

    /**
     * Starts/replaces the current pointer session. Call only after verifying that the
     * original hit key is the spacebar, before any stock key retargeting occurs.
     */
    public static void begin(Object pointerOwner, Object originalSpacebar,
            float x, float y, float thresholdPx) {
        if (pointerOwner == null) {
            return;
        }
        if (originalSpacebar == null || !Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(thresholdPx) || thresholdPx <= 0f) {
            synchronized (SESSIONS) {
                SESSIONS.remove(pointerOwner);
            }
            return;
        }
        synchronized (SESSIONS) {
            SESSIONS.put(pointerOwner,
                    new Session(originalSpacebar, x, y, thresholdPx));
        }
    }

    /**
     * Observes a move after Gboard evaluated its native cursor/trackpad ownership.
     * Once cursor handling owns the pointer, this session can never trigger.
     */
    public static void observe(Object pointerOwner, float x, float y,
            boolean cursorOwnsPointer) {
        if (pointerOwner == null) {
            return;
        }
        synchronized (SESSIONS) {
            Session session = SESSIONS.get(pointerOwner);
            if (session == null || session.cancelled || session.qualified) {
                return;
            }
            if (cursorOwnsPointer || !Float.isFinite(x) || !Float.isFinite(y)) {
                session.cancelled = true;
                return;
            }

            float dx = Math.abs(x - session.startX);
            float dy = Math.abs(y - session.startY);
            if (dx >= session.thresholdPx && dx >= dy * HORIZONTAL_DOMINANCE) {
                session.qualified = true;
            }
        }
    }

    /**
     * Resolves exactly once on pointer-up. True consumes the normal spacebar action.
     */
    public static boolean finish(Object pointerOwner, Object originalSpacebar) {
        if (pointerOwner == null) {
            return false;
        }
        synchronized (SESSIONS) {
            Session session = SESSIONS.remove(pointerOwner);
            if (session == null || session.cancelled || !session.qualified
                    || session.originalSpacebar != originalSpacebar) {
                return false;
            }
            // Consume the stock space action only when Gboard confirms the language switch.
            return switchToNextGboardLanguage(pointerOwner);
        }
    }

    /**
     * Uses Gboard's native layout rotation: pga.F(context).t(true, pfi.d).
     * If reflection or rotation fails, return false so the stock space action remains.
     */
    private static boolean switchToNextGboardLanguage(Object pointerOwner) {
        try {
            Object dispatcher = pointerOwner.getClass().getField("r").get(pointerOwner);
            if (dispatcher == null) return false;
            Context context = (Context) dispatcher.getClass().getField("b").get(dispatcher);
            if (context == null) return false;
            ClassLoader loader = context.getClassLoader();
            Class<?> managerClass = Class.forName("defpackage.pga", false, loader);
            Object manager = managerClass.getMethod("F", Context.class).invoke(null, context);
            if (manager == null) return false;
            Class<?> sourceClass = Class.forName("defpackage.pfi", false, loader);
            Object globeKeySource = sourceClass.getField("d").get(null);
            Object handled = managerClass.getMethod("t", boolean.class, sourceClass)
                    .invoke(manager, true, globeKeySource);
            return Boolean.TRUE.equals(handled);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ignored) {
            return false;
        }
    }

    /** Clears state on cancel, reset, pointer replacement, or abandoned gesture. */
    public static void clear(Object pointerOwner) {
        if (pointerOwner != null) {
            synchronized (SESSIONS) {
                SESSIONS.remove(pointerOwner);
            }
        }
    }
}
