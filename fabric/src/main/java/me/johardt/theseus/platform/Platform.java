package me.johardt.theseus.platform;

import java.nio.file.Path;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class Platform {
    private Platform() {}
    public static Path configDir() { return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir(); }
    public static boolean isModLoaded(String id) { return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id); }
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) { net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload); }
    public static void sendToServer(CustomPacketPayload payload) { net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload); }
}
