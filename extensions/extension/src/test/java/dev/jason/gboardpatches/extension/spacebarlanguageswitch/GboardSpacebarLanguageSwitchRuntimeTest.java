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
    public void cursorMovementUsesSelectionEdgeAndClampsToEditorBounds() {
        assertEquals(4, GboardSpacebarLanguageSwitchRuntime.targetCursorPosition(
                5, 8, -1, 0, 20));
        assertEquals(9, GboardSpacebarLanguageSwitchRuntime.targetCursorPosition(
                5, 8, 1, 0, 20));
        assertEquals(0, GboardSpacebarLanguageSwitchRuntime.targetCursorPosition(
                0, 0, -3, 0, 20));
        assertEquals(20, GboardSpacebarLanguageSwitchRuntime.targetCursorPosition(
                20, 20, 3, 0, 20));
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
