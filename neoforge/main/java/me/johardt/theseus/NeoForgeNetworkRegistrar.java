package me.johardt.theseus;

import me.johardt.theseus.core.QuestNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class NeoForgeNetworkRegistrar implements QuestNetwork.Registrar {
    private final PayloadRegistrar registrar;
    NeoForgeNetworkRegistrar(RegisterPayloadHandlersEvent event) { registrar = event.registrar("1"); }
    public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        registrar.playToClient(type, codec);
    }
    public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec, java.util.function.BiConsumer<T, ServerPlayer> handler) {
        registrar.playToServer(type, codec, (payload, context) -> handler.accept(payload, (ServerPlayer) context.player()));
    }
}
