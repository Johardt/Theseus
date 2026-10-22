package me.johardt.theseus.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Physical input ownership outlives the widgets that accepted it. */
final class ButtonPressState {
    record Input(boolean mouse, int code) {}

    private final Map<Input, Object> held = new HashMap<>();

    void press(Input input, Object control) {
        if (!held.containsKey(input)) held.put(input, control);
    }

    boolean isPressed(Object control) {
        return held.containsValue(control);
    }

    void release(Input input) {
        held.remove(input);
    }

    void retainControls(Set<?> controls) {
        // Keep ownership until release, but never resurrect a vanished control.
        held.replaceAll((input, control) -> control != null && controls.contains(control) ? control : null);
    }

    void reconcile(Predicate<Input> isDown) {
        held.keySet().removeIf(input -> !isDown.test(input));
    }

    void cancel() {
        held.clear();
    }
}
