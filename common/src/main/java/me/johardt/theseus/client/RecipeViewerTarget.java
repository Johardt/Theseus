package me.johardt.theseus.client;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** The exact item and tight bounds represented by a player-facing detail icon. */
public record RecipeViewerTarget(ItemStack stack, int x, int y, int width, int height) {
    public RecipeViewerTarget {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) throw new IllegalArgumentException("Recipe viewer target must not be empty");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Recipe viewer target must have bounds");
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
