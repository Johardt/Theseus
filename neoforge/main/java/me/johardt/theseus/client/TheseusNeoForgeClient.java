package me.johardt.theseus.client;

import me.johardt.theseus.Theseus;
import me.johardt.theseus.TheseusItems;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

@Mod(value = Theseus.MOD_ID, dist = Dist.CLIENT)
public final class TheseusNeoForgeClient {
    public TheseusNeoForgeClient(IEventBus bus) {
        TheseusClient.initialize(FMLPaths.GAMEDIR.get());
        bus.addListener(this::keys);
        bus.addListener(this::payloads);
        bus.addListener(this::hud);
        bus.addListener((AddClientReloadListenersEvent event) -> {
            event.addListener(ClientThemeLoader.LISTENER_ID, ClientThemeLoader.INSTANCE);
            event.addListener(QuestTutorialContentLoader.LISTENER_ID, QuestTutorialContentLoader.INSTANCE);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> TheseusClient.clientTick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> TheseusClient.clientLoggedIn(event.getConnection()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> TheseusClient.clientLoggedOut(event.getConnection()));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (event.getLevel().isClientSide() && event.getItemStack().getItem() == TheseusItems.QUEST_BOOK.get()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                TheseusClient.openQuestScreen();
            }
        });
    }

    private void keys(RegisterKeyMappingsEvent event) {
        event.registerCategory(TheseusClient.CATEGORY);
        event.register(TheseusClient.OPEN_QUESTS);
        event.register(TheseusClient.TOGGLE_TRACKER);
    }

    private void payloads(RegisterClientPayloadHandlersEvent event) {
        TheseusClient.registerPayloadHandlers(new TheseusClient.PayloadRegistrar() {
            public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, java.util.function.Consumer<T> handler) {
                event.register(type, (payload, context) -> handler.accept(payload));
            }
        });
    }

    private void hud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quest_tracker"),
            (graphics, delta) -> TheseusClient.renderHud(graphics));
    }
}
