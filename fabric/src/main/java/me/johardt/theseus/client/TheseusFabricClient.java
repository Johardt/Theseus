package me.johardt.theseus.client;

import me.johardt.theseus.Theseus;
import me.johardt.theseus.TheseusItems;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;

public final class TheseusFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TheseusClient.initialize(FabricLoader.getInstance().getGameDir());
        net.minecraft.client.KeyMapping.Category.register(TheseusClient.CATEGORY.id());
        KeyMappingHelper.registerKeyMapping(TheseusClient.OPEN_QUESTS);
        KeyMappingHelper.registerKeyMapping(TheseusClient.TOGGLE_TRACKER);
        TheseusClient.registerPayloadHandlers(new TheseusClient.PayloadRegistrar() {
            public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, java.util.function.Consumer<T> handler) {
                ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> TheseusClient.clientTick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> TheseusClient.clientLoggedIn(handler.getConnection()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TheseusClient.clientLoggedOut(handler.getConnection()));
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quest_tracker"),
            (graphics, delta) -> TheseusClient.renderHud(graphics));
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ClientThemeLoader.LISTENER_ID, ClientThemeLoader.INSTANCE);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(QuestTutorialContentLoader.LISTENER_ID, QuestTutorialContentLoader.INSTANCE);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && player.getItemInHand(hand).is(TheseusItems.QUEST_BOOK)) {
                TheseusClient.openQuestScreen();
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
