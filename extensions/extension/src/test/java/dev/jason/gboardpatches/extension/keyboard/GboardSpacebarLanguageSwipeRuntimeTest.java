package dev.jason.gboardpatches.extension.keyboard;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GboardSpacebarLanguageSwipeRuntimeTest {
    private static final float THRESHOLD_PX = 24f;

    @Test
    public void shortTapDoesNotConsumeSpace() {
        Object owner = new Object();
        Object space = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, space, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 110f, 51f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
    }

    @Test
    public void horizontalSwipeInEitherDirectionQualifiesOnce() {
        Object leftOwner = new Object();
        Object leftSpace = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(
                leftOwner, leftSpace, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(leftOwner, 70f, 54f, false);
        assertTrue(GboardSpacebarLanguageSwipeRuntime.finish(leftOwner, leftSpace));
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(leftOwner, leftSpace));

        Object rightOwner = new Object();
        Object rightSpace = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(
                rightOwner, rightSpace, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(rightOwner, 130f, 54f, false);
        assertTrue(GboardSpacebarLanguageSwipeRuntime.finish(rightOwner, rightSpace));
    }

    @Test
    public void verticalMovementNeverQualifies() {
        Object owner = new Object();
        Object space = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, space, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 104f, 100f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
    }

    @Test
    public void cursorOwnershipCancelsTheGesture() {
        Object owner = new Object();
        Object space = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, space, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 110f, 50f, true);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 150f, 50f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
    }

    @Test
    public void finishRequiresTheOriginalSpacebarAndClearCancels() {
        Object owner = new Object();
        Object originalSpace = new Object();
        Object retargetedKey = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(
                owner, originalSpace, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 130f, 50f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, retargetedKey));

        GboardSpacebarLanguageSwipeRuntime.begin(
                owner, originalSpace, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 130f, 50f, false);
        GboardSpacebarLanguageSwipeRuntime.clear(owner);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, originalSpace));
    }
}
