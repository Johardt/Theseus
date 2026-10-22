package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.core.QuestNetwork;
import me.johardt.theseus.platform.Platform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class TheseusClient {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quests")
    );
    public static final KeyMapping OPEN_QUESTS = new KeyMapping(
        "key.theseus.open_quests",
        InputConstants.Type.KEYSYM,
        72,
        CATEGORY
    );
    public static final KeyMapping TOGGLE_TRACKER = new KeyMapping(
        "key.theseus.toggle_tracker",
        InputConstants.Type.KEYSYM,
        74,
        CATEGORY
    );
    private static final SoundEvent QUEST_COMPLETE_SOUND = SoundEvent.createVariableRangeEvent(
        Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quest_complete")
    );
    private static QuestClientSnapshot snapshots = new QuestClientSnapshot();
    private static boolean trackerCollapsed;
    private static QuestScreen disconnectedEditor;
    private static String disconnectedServer;

    /** Opens the quest screen through the normal server-backed flow. */
    public static void openQuestScreen() {
        if (Minecraft.getInstance().player != null) {
            Platform.sendToServer(
                new QuestNetwork.ActionPayload("open", "")
            );
        }
    }

    public static void initialize(java.nio.file.Path gameDir) {
        TheseusClientOptions.load(gameDir);
        ResourcefulConfigBridge.registerIfAvailable();
        trackerCollapsed = TheseusClientOptions.trackerCollapsed();
    }

    public interface PayloadRegistrar {
        <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void register(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type,
            java.util.function.Consumer<T> handler);
    }

    public static void registerPayloadHandlers(PayloadRegistrar event) {
        event.register(QuestNetwork.SyncPayload.TYPE, payload -> {
            JsonObject incoming = JsonParser.parseString(payload.json()).getAsJsonObject();
            QuestClientSnapshot.Kind kind = QuestClientSnapshot.kindOf(incoming);
            if (kind == QuestClientSnapshot.Kind.CHAPTER) {
                snapshots.accept(incoming);
                if (Minecraft.getInstance().screen instanceof QuestScreen screen
                    && screen.snapshots == snapshots) {
                    screen.snapshotChanged();
                }
                return;
            }
            snapshots = new QuestClientSnapshot(incoming);
            if (
                payload.open() ||
                Minecraft.getInstance().screen instanceof QuestScreen
            ) {
                QuestScreen previous = Minecraft.getInstance().screen instanceof QuestScreen screen
                    ? screen
                    : payload.open() ? takeDisconnectedEditor() : null;
                QuestScreen screen = new QuestScreen(snapshots, previous);
                Minecraft.getInstance().setScreen(screen);
                if (kind == QuestClientSnapshot.Kind.INDEX) screen.requestActiveChapter();
            }
        });
        event.register(
            QuestNetwork.NotificationPayload.TYPE,
            payload -> {
                if (payload.kind().equals("complete")) {
                    Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(QUEST_COMPLETE_SOUND, 1.0F)
                    );
                }
                var id = switch (payload.kind()) {
                    case "unlock" -> QuestHud.UNLOCK_TOAST;
                    case "complete" -> QuestHud.COMPLETE_TOAST;
                    default -> QuestHud.REWARD_TOAST;
                };
                net.minecraft.client.gui.components.toasts.SystemToast.add(
                    Minecraft.getInstance().getToastManager(),
                    id,
                    Component.literal(payload.title()),
                    Component.literal(payload.detail())
                );
            }
        );
        event.register(QuestNetwork.EditorResultPayload.TYPE, payload -> {
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.handleEditorResult(payload);
            }
        });
        event.register(QuestNetwork.OpenQuestFileResultPayload.TYPE, payload -> {
            if (Minecraft.getInstance().screen instanceof QuestScreen screen) {
                screen.handleOpenQuestFileResult(payload);
            }
        });
    }

    public static void renderHud(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        QuestHud.render(graphics, snapshots.raw(), TheseusClientOptions.trackerCollapsed());
    }

    public static void clientTick() {
        while (OPEN_QUESTS.consumeClick()) {
            openQuestScreen();
        }
        while (TOGGLE_TRACKER.consumeClick()) {
            trackerCollapsed = !TheseusClientOptions.trackerCollapsed();
            TheseusClientOptions.setTrackerCollapsed(trackerCollapsed);
        }
    }

    public static void clientLoggedOut(net.minecraft.network.Connection connection) {
        snapshots = new QuestClientSnapshot();
        if (!(Minecraft.getInstance().screen instanceof QuestScreen screen)) {
            clearDisconnectedEditor();
            return;
        }
        String server = serverKey(connection);
        if (server == null) {
            clearDisconnectedEditor();
            return;
        }
        screen.handleConnectionLost();
        disconnectedEditor = screen;
        disconnectedServer = server;
    }

    public static void clientLoggedIn(net.minecraft.network.Connection connection) {
        if (disconnectedEditor != null
            && !java.util.Objects.equals(disconnectedServer, serverKey(connection))) {
            clearDisconnectedEditor();
        }
    }

    private static QuestScreen takeDisconnectedEditor() {
        QuestScreen retained = disconnectedEditor;
        clearDisconnectedEditor();
        return retained;
    }

    private static void clearDisconnectedEditor() {
        disconnectedEditor = null;
        disconnectedServer = null;
    }

    private static String serverKey(net.minecraft.network.Connection connection) {
        return connection == null || connection.getRemoteAddress() == null
            ? null
            : connection.getRemoteAddress().toString();
    }
}
