package me.johardt.theseus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import me.johardt.theseus.client.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class ServerQuestWorldTest {

    @Test
    void itemDeliveryConservesFullPartialAndUninsertedStacks() {
        MinecraftTestBootstrap.ensureBootstrapped();

        assertDelivery(10, 10, 0);
        assertDelivery(4, 4, 6);
        assertDelivery(0, 0, 10);
    }

    @Test
    void emptyInsertionRemainderIsNotDropped() {
        MinecraftTestBootstrap.ensureBootstrapped();
        ItemStack reward = new ItemStack(Items.DIAMOND, 10);
        List<ItemStack> drops = new ArrayList<>();

        ServerQuestWorld.deliverItem(
            reward,
            remainder -> {
                remainder.shrink(remainder.getCount());
                return false;
            },
            drops::add
        );

        assertTrue(drops.isEmpty());
        assertEquals(10, reward.getCount());
    }

    private static void assertDelivery(
        int inventoryCapacity,
        int expectedInserted,
        int expectedDropped
    ) {
        ItemStack reward = new ItemStack(Items.DIAMOND, 10);
        List<ItemStack> drops = new ArrayList<>();
        int[] inserted = { 0 };

        ServerQuestWorld.deliverItem(
            reward,
            remainder -> {
                inserted[0] = Math.min(inventoryCapacity, remainder.getCount());
                remainder.shrink(inserted[0]);
                return remainder.isEmpty();
            },
            remainder -> drops.add(remainder.copy())
        );

        int dropped = drops.stream().mapToInt(ItemStack::getCount).sum();
        assertEquals(expectedInserted, inserted[0]);
        assertEquals(expectedDropped, dropped);
        assertEquals(10, inserted[0] + dropped);
        assertEquals(10, reward.getCount());
        assertEquals(expectedDropped == 0 ? 0 : 1, drops.size());
    }
}
