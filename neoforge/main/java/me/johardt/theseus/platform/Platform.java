package me.johardt.theseus.platform;

import java.nio.file.Path;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class Platform {
    private Platform() {}
    public static Path configDir() { return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get(); }
    public static boolean isModLoaded(String id) { return net.neoforged.fml.loading.FMLLoader.getCurrent().getLoadingModList().getModFileById(id) != null; }
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload); }
    public static void sendToServer(CustomPacketPayload payload) { net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload); }
}
