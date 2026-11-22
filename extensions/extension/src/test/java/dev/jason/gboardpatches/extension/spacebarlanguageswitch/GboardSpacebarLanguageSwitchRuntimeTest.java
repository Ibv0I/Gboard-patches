package dev.jason.gboardpatches.extension.spacebarlanguageswitch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GboardSpacebarLanguageSwitchRuntimeTest {
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
