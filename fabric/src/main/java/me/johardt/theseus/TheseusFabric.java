package me.johardt.theseus;

import com.google.gson.JsonObject;
import me.johardt.theseus.core.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public final class TheseusFabric implements ModInitializer {
    private static QuestRuntime runtime;

    @Override
    public void onInitialize() {
        TheseusItems.register();
        QuestNetwork.register(new QuestNetwork.Registrar() {
            public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
                // UTF-8 quest JSON can exceed vanilla custom-payload limits.
                PayloadTypeRegistry.clientboundPlay().registerLarge(type, codec, 4 * 1024 * 1024);
            }
            public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec, java.util.function.BiConsumer<T, ServerPlayer> handler) {
                PayloadTypeRegistry.serverboundPlay().registerLarge(type, codec, 4 * 1024 * 1024);
                ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
            }
        }, TheseusFabric::runtime);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> runtime = QuestRuntime.create(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (runtime != null) runtime.close();
            runtime = null;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> QuestCommands.register(dispatcher, TheseusFabric::runtime));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            runtime().initialize(handler.player);
            runtime().sync(handler.player, Boolean.getBoolean("theseus.openQuestScreen"));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.tickCount % 20 == 0) runtime().updateInventoryTasks(player);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer player) {
                signal(player, new TaskEngine.Signal.EntityKilled(QuestRuntime.registryEntry(entity.getType().builtInRegistryHolder(), new JsonObject(), 1)));
            }
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) ->
            signal(player, new TaskEngine.Signal.DimensionChanged(from.dimension().identifier().toString(), to.dimension().identifier().toString())));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                signal(serverPlayer, new TaskEngine.Signal.BlockInteracted(QuestRuntime.registryEntry(level.getBlockState(hit.getBlockPos()).getBlock().builtInRegistryHolder(), new JsonObject(), 1)));
                signal(serverPlayer, new TaskEngine.Signal.ItemInteracted(QuestRuntime.itemEntry(serverPlayer, player.getItemInHand(hand))));
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                signal(serverPlayer, new TaskEngine.Signal.EntityInteracted(QuestRuntime.registryEntry(entity.getType().builtInRegistryHolder(), new JsonObject(), 1)));
                signal(serverPlayer, new TaskEngine.Signal.ItemInteracted(QuestRuntime.itemEntry(serverPlayer, player.getItemInHand(hand))));
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                signal(serverPlayer, new TaskEngine.Signal.ItemInteracted(QuestRuntime.itemEntry(serverPlayer, player.getItemInHand(hand))));
            }
            return InteractionResult.PASS;
        });
        Theseus.LOGGER.info("Theseus core quest runtime loaded on Fabric");
    }

    public static void signal(ServerPlayer player, TaskEngine.Signal signal) {
        if (runtime != null) runtime.signal(player, signal);
    }

    private static QuestRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("Theseus quest runtime is not started");
        return runtime;
    }
}
