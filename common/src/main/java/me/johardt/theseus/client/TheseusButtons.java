package me.johardt.theseus.client;

import com.mojang.blaze3d.platform.InputConstants;
import earth.terrarium.olympus.client.components.buttons.Button;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/** Screen-owned interaction lifetime and the adapter for disposable Olympus buttons. */
final class TheseusButtons {
    private record Control(Object layer, Object identity) {}

    private final ButtonPressState presses = new ButtonPressState();
    private final Set<Control> controls = new HashSet<>();
    private final Supplier<Object> layer;
    private Object builtLayer;

    TheseusButtons(Supplier<Object> layer) {
        this.layer = layer;
    }

    void beginBuild() {
        builtLayer = layer.get();
        controls.clear();
    }

    void endBuild() {
        presses.retainControls(controls);
    }

    Button button(Object identity, Consumer<Button> configure) {
        Control control = new Control(builtLayer, identity);
        if (!controls.add(control)) throw new IllegalArgumentException("Duplicate button identity: " + identity);
        Button button = new HeldButton(control);
        configure.accept(button);
        return button;
    }

    void mouseReleased(MouseButtonEvent event) {
        presses.release(new ButtonPressState.Input(true, event.input()));
    }

    void keyReleased(KeyEvent event) {
        presses.release(new ButtonPressState.Input(false, event.key()));
    }

    void cancel() {
        presses.cancel();
    }

    void reconcile() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isWindowActive()) {
            cancel();
            return;
        }
        if (!Objects.equals(builtLayer, layer.get())) presses.retainControls(Set.of());
        long window = client.getWindow().handle();
        presses.reconcile(input -> input.mouse()
            ? GLFW.glfwGetMouseButton(window, input.code()) == GLFW.GLFW_PRESS
            : InputConstants.isKeyDown(client.getWindow(), input.code()));
    }

    private final class HeldButton extends Button {
        private final Control control;
        private ButtonPressState.Input dispatching;

        HeldButton(Control control) {
            this.control = control;
        }

        @Override
        public Button withCallback(Runnable callback) {
            return super.withCallback(capture(callback));
        }

        @Override
        public Button withCallback(int input, Runnable callback) {
            return super.withCallback(input, capture(callback));
        }

        private Runnable capture(Runnable callback) {
            return () -> {
                // Olympus has accepted the shape, visibility, enabled state and action.
                // Capture before a synchronous callback can rebuild or replace the screen.
                if (dispatching != null && controls.contains(control) && Objects.equals(control.layer(), layer.get())) {
                    presses.press(dispatching, control);
                }
                callback.run();
            };
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            dispatching = new ButtonPressState.Input(true, event.input());
            try {
                return super.mouseClicked(event, doubleClick);
            } finally {
                dispatching = null;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            dispatching = new ButtonPressState.Input(false, event.key());
            try {
                return super.keyPressed(event);
            } finally {
                dispatching = null;
            }
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            if (presses.isPressed(control)) {
                // Darken existing art, including custom textureless controls. No pressed asset.
                // A new stratum places the shade above deferred text/items as well as sprites.
                graphics.nextStratum();
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x33000000);
                graphics.nextStratum();
            }
        }
    }
}
