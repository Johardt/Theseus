package me.johardt.theseus.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeViewerTargetTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void constructorRejectsNullEmptyAndNonPositiveBounds() {
        assertThrows(NullPointerException.class, () -> new RecipeViewerTarget(null, 0, 0, 16, 16));
        assertThrows(IllegalArgumentException.class, () -> new RecipeViewerTarget(new ItemStack(Items.AIR), 0, 0, 16, 16));
        assertThrows(IllegalArgumentException.class, () -> new RecipeViewerTarget(new ItemStack(Items.DIAMOND), 0, 0, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new RecipeViewerTarget(new ItemStack(Items.DIAMOND), 0, 0, 16, -1));
    }

    @Test
    void containsUsesExactHalfOpenBounds() {
        RecipeViewerTarget target = new RecipeViewerTarget(new ItemStack(Items.DIAMOND), 10, 20, 16, 12);

        assertTrue(target.contains(10, 20));
        assertTrue(target.contains(25.999, 31.999));
        assertFalse(target.contains(26, 25));
        assertFalse(target.contains(25, 32));
        assertFalse(target.contains(9.999, 20));
    }
}
