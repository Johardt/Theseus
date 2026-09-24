package me.johardt.theseus;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TheseusItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Theseus.MOD_ID);

    public static final DeferredItem<Item> QUEST_BOOK = ITEMS.registerSimpleItem("quest_book");

    private TheseusItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(TheseusItems::addCreativeItems);
    }

    private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(QUEST_BOOK.get());
        }
    }
}
