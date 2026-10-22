package me.johardt.theseus.core;

import net.minecraft.server.level.ServerPlayer;
import me.johardt.theseus.platform.Platform;

/** Packet adapter for {@link QuestSync}. */
public final class PacketQuestSync implements QuestSync {

    @Override
    public void snapshot(ServerPlayer player, String json, boolean open) {
        Platform.sendToPlayer(
            player,
            new QuestNetwork.SyncPayload(json, open)
        );
    }

    @Override
    public void notification(
        ServerPlayer player,
        String kind,
        String title,
        String detail
    ) {
        Platform.sendToPlayer(
            player,
            new QuestNetwork.NotificationPayload(kind, title, detail)
        );
    }
}
