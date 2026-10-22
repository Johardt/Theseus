package me.johardt.theseus.client;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ButtonPressStateTest {
    private static final ButtonPressState.Input LEFT = new ButtonPressState.Input(true, 0);
    private static final ButtonPressState.Input RIGHT = new ButtonPressState.Input(true, 1);
    private static final ButtonPressState.Input ENTER = new ButtonPressState.Input(false, 257);

    @Test
    void rebuildAndArbitrarilyManyFramesKeepTheSameLogicalButtonHeld() {
        ButtonPressState state = new ButtonPressState();
        state.press(LEFT, List.of("chapter-up", "intro"));
        Object rebuilt = List.of("chapter-up", "intro");
        state.retainControls(Set.of(rebuilt, List.of("chapter-up", "other")));
        for (int frame = 0; frame < 10_000; frame++) state.reconcile(input -> true);
        assertTrue(state.isPressed(rebuilt));
        assertFalse(state.isPressed(List.of("chapter-up", "other")));
        state.release(RIGHT);
        state.release(ENTER);
        assertTrue(state.isPressed(rebuilt));
        state.release(LEFT);
        assertFalse(state.isPressed(rebuilt));
    }

    @Test
    void missingControlDoesNotTransferOrResurrectUntilPhysicalRelease() {
        ButtonPressState state = new ButtonPressState();
        state.press(ENTER, "old");
        state.retainControls(Set.of("replacement"));
        state.press(ENTER, "replacement"); // keyboard repeat or a focus change
        state.retainControls(Set.of("old", "replacement"));
        state.press(ENTER, "old");
        assertFalse(state.isPressed("old"));
        assertFalse(state.isPressed("replacement"));
        state.release(ENTER);
        state.press(ENTER, "replacement");
        assertTrue(state.isPressed("replacement"));
    }

    @Test
    void simultaneousInputsReleaseIndependently() {
        ButtonPressState state = new ButtonPressState();
        state.press(LEFT, "toggle");
        state.press(ENTER, "toggle");
        state.press(RIGHT, "other");
        state.release(LEFT);
        assertTrue(state.isPressed("toggle"));
        state.release(ENTER);
        assertFalse(state.isPressed("toggle"));
        assertTrue(state.isPressed("other"));
    }

    @Test
    void missedReleasesAndScreenRemovalClearPhysicalOwnership() {
        ButtonPressState state = new ButtonPressState();
        state.press(LEFT, "toggle");
        state.press(ENTER, "other");
        state.reconcile(input -> !input.mouse());
        assertFalse(state.isPressed("toggle"));
        assertTrue(state.isPressed("other"));
        state.cancel();
        assertFalse(state.isPressed("other"));
        state.press(ENTER, "new-screen");
        assertTrue(state.isPressed("new-screen"));
    }

    @Test
    void quickPressHasNoTimedFlash() {
        ButtonPressState state = new ButtonPressState();
        state.press(LEFT, "toggle");
        state.release(LEFT);
        assertFalse(state.isPressed("toggle"));
    }
}
