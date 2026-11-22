package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GboardSpacebarLanguageSwitchRuntimeTest {

    @Test
    public void cursorDragIsHandledWithoutRetargetingTheSpacebarPointer() {
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldConsumePointerMove(false, false));
        assertEquals(true,
                GboardSpacebarLanguageSwitchRuntime.shouldConsumePointerMove(true, false));
        assertEquals(true,
                GboardSpacebarLanguageSwitchRuntime.shouldConsumePointerMove(false, true));
        assertEquals(true,
                GboardSpacebarLanguageSwitchRuntime.shouldConsumePointerMove(true, true));
    }

    @Test
    public void cursorDragRequiresConfirmedLongPressAndMatchingHoldDuration() {
        assertEquals(true,
                GboardSpacebarLanguageSwitchRuntime.shouldStartCursorDrag(true, 900, 850));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldStartCursorDrag(true, 700, 850));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldStartCursorDrag(false, 1000, 850));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldStartCursorDrag(false, 200, 850));
    }

    @Test
    public void onlySpacebarLongPressStartsCursorMode() {
        assertEquals(true,
                GboardSpacebarLanguageSwitchRuntime.shouldCaptureCursorLongPress(
                        "LONG_PRESS", true));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldCaptureCursorLongPress(
                        "PRESS", true));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldCaptureCursorLongPress(
                        "LONG_PRESS", false));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldCaptureCursorLongPress(
                        "SLIDE_LEFT", true));
    }

    @Test
    public void nativeCursorSlidesArePassedThroughToGboard() {
        assertEquals(true,
                GboardSpacebarLanguageSwitchRuntime.shouldPassThroughNativeCursorSlide(
                        true, true, true));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldPassThroughNativeCursorSlide(
                        true, false, true));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldPassThroughNativeCursorSlide(
                        true, true, false));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.shouldPassThroughNativeCursorSlide(
                        false, true, true));
    }

    @Test
    public void onlySpacebarLanguagePickerEventIsSuppressed() {
        assertEquals(true,
                GboardSpacebarLanguageSwitchRuntime.isSpacebarLanguagePickerEvent(
                        -10022, "space"));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.isSpacebarLanguagePickerEvent(
                        -10022, "globe"));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.isSpacebarLanguagePickerEvent(
                        -10000, "space"));
        assertEquals(false,
                GboardSpacebarLanguageSwitchRuntime.isSpacebarLanguagePickerEvent(
                        -10022, null));
    }

    @Test
    public void nextLanguageAdvancesAndWrapsAtEnd() {
        assertEquals(1, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(0, 3, true));
        assertEquals(2, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(1, 3, true));
        assertEquals(0, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(2, 3, true));
    }

    @Test
    public void previousLanguageMovesBackAndWrapsAtBeginning() {
        assertEquals(2, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(0, 3, false));
        assertEquals(0, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(1, 3, false));
        assertEquals(1, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(2, 3, false));
    }

    @Test
    public void noAdjacentLanguageExistsForSingleEntryOrInvalidIndex() {
        assertEquals(-1, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(0, 1, true));
        assertEquals(-1, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(0, 1, false));
        assertEquals(-1, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(-1, 3, true));
        assertEquals(-1, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(3, 3, false));
        assertEquals(-1, GboardSpacebarLanguageSwitchRuntime.adjacentRotationIndex(0, 0, true));
    }
}
