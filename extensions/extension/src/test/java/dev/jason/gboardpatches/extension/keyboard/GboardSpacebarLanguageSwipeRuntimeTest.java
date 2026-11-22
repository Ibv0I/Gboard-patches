package dev.jason.gboardpatches.extension.keyboard;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GboardSpacebarLanguageSwipeRuntimeTest {
    private static final float THRESHOLD_PX = 24f;

    @Before
    public void setUp() {
        GboardSpacebarLanguageSwipeRuntime.setNativeLanguageSwitcherForTests(owner -> true);
    }

    @After
    public void tearDown() {
        GboardSpacebarLanguageSwipeRuntime.setNativeLanguageSwitcherForTests(null);
        GboardSpacebarLanguageSwipeRuntime.clearAll();
    }

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
    public void diagonalMovementMustBeClearlyHorizontal() {
        Object owner = new Object();
        Object space = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, space, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 130f, 30f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
    }

    @Test
    public void horizontalThresholdIsInclusive() {
        Object owner = new Object();
        Object space = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, space, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 124f, 50f, false);
        assertTrue(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
    }

    @Test
    public void replacingPointerSessionCannotReusePreviousGesture() {
        Object owner = new Object();
        Object firstSpace = new Object();
        Object secondSpace = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, firstSpace, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 140f, 50f, false);

        GboardSpacebarLanguageSwipeRuntime.begin(owner, secondSpace, 100f, 50f, THRESHOLD_PX);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, firstSpace));
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 101f, 50f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, secondSpace));
    }

    @Test
    public void sessionsForDifferentPointerOwnersAreIndependent() {
        Object firstOwner = new Object();
        Object secondOwner = new Object();
        Object firstSpace = new Object();
        Object secondSpace = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(
                firstOwner, firstSpace, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.begin(
                secondOwner, secondSpace, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(firstOwner, 130f, 50f, false);

        assertTrue(GboardSpacebarLanguageSwipeRuntime.finish(firstOwner, firstSpace));
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(secondOwner, secondSpace));
    }

    @Test
    public void nativeRotationFailureDoesNotReportGestureAsConsumed() {
        GboardSpacebarLanguageSwipeRuntime.setNativeLanguageSwitcherForTests(owner -> false);
        Object owner = new Object();
        Object space = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, space, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 130f, 50f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
    }


    @Test
    public void laterVerticalMovementRevokesAnEarlierHorizontalQualification() {
        Object owner = new Object();
        Object space = new Object();
        GboardSpacebarLanguageSwipeRuntime.begin(owner, space, 100f, 50f, THRESHOLD_PX);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 130f, 50f, false);
        GboardSpacebarLanguageSwipeRuntime.observe(owner, 130f, 90f, false);
        assertFalse(GboardSpacebarLanguageSwipeRuntime.finish(owner, space));
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
