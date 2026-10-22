package me.johardt.theseus;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class TheseusItems {
    private TheseusItems() {}
    private static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quest_book"));
    public static final Item QUEST_BOOK = Registry.register(BuiltInRegistries.ITEM, KEY, new Item(new Item.Properties().setId(KEY)));

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(QUEST_BOOK));
    }
}
