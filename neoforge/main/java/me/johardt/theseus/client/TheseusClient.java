package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import me.johardt.theseus.core.QuestNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Theseus.MOD_ID, dist = Dist.CLIENT)
public final class TheseusClient {

    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quests")
    );
    private static final KeyMapping OPEN_QUESTS = new KeyMapping(
        "key.theseus.open_quests",
        InputConstants.Type.KEYSYM,
        72,
        CATEGORY
    );
    private static final KeyMapping TOGGLE_TRACKER = new KeyMapping(
        "key.theseus.toggle_tracker",
        InputConstants.Type.KEYSYM,
        74,
        CATEGORY
    );
    private static final SoundEvent QUEST_COMPLETE_SOUND = SoundEvent.createVariableRangeEvent(
        Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quest_complete")
    );
    private static JsonObject snapshot = new JsonObject();
    private static boolean trackerCollapsed;
    private static QuestScreen disconnectedEditor;
    private static String disconnectedServer;

    /** Opens the quest screen through the normal server-backed flow. */
    public static void openQuestScreen() {
        if (Minecraft.getInstance().player != null) {
            ClientPacketDistributor.sendToServer(
                new QuestNetwork.ActionPayload("open", "")
            );
        }
    }

    public TheseusClient(IEventBus modBus) {
        TheseusClientOptions.load(FMLPaths.GAMEDIR.get());
        ResourcefulConfigBridge.registerIfAvailable();
        trackerCollapsed = TheseusClientOptions.trackerCollapsed();
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerPayloadHandlers);
        modBus.addListener(this::registerGuiLayers);
        modBus.addListener(ClientThemeLoader::register);
        modBus.addListener(QuestTutorialContentLoader::register);
        NeoForge.EVENT_BUS.addListener(this::clientTick);
        NeoForge.EVENT_BUS.addListener(this::clientLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::clientLoggedOut);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_QUESTS);
        event.register(TOGGLE_TRACKER);
    }

    private void registerPayloadHandlers(
        RegisterClientPayloadHandlersEvent event
    ) {
        event.register(QuestNetwork.SyncPayload.TYPE, (payload, context) -> {
            JsonObject incoming = JsonParser.parseString(payload.json()).getAsJsonObject();
            if ("chapter".equals(snapshotKind(incoming))) {
                mergeChapterSnapshot(incoming);
                if (Minecraft.getInstance().gui.screen() instanceof QuestScreen screen) {
                    screen.mergeSnapshot(incoming);
                }
                return;
            }
            snapshot = incoming;
            if (
                payload.open() ||
                Minecraft.getInstance().gui.screen() instanceof QuestScreen
            ) {
                QuestScreen previous = Minecraft.getInstance().gui.screen() instanceof QuestScreen screen
                    ? screen
                    : payload.open() ? takeDisconnectedEditor() : null;
                QuestScreen screen = new QuestScreen(snapshot, previous);
                Minecraft.getInstance().gui.setScreen(screen);
                if ("index".equals(snapshotKind(snapshot))) screen.requestActiveChapter();
            }
        });
        event.register(
            QuestNetwork.NotificationPayload.TYPE,
            (payload, context) -> {
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
                    Minecraft.getInstance().gui.toastManager(),
                    id,
                    Component.literal(payload.title()),
                    Component.literal(payload.detail())
                );
            }
        );
        event.register(QuestNetwork.EditorResultPayload.TYPE, (payload, context) -> {
            if (Minecraft.getInstance().gui.screen() instanceof QuestScreen screen) {
                screen.handleEditorResult(payload);
            }
        });
        event.register(QuestNetwork.OpenQuestFileResultPayload.TYPE, (payload, context) -> {
            if (Minecraft.getInstance().gui.screen() instanceof QuestScreen screen) {
                screen.handleOpenQuestFileResult(payload);
            }
        });
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CHAT,
            Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "quest_tracker"),
            (graphics, delta) ->
                QuestHud.render(graphics, snapshot, TheseusClientOptions.trackerCollapsed())
        );
    }

    private static String snapshotKind(JsonObject value) {
        return value.has("__snapshot_kind") && value.get("__snapshot_kind").isJsonPrimitive()
            ? value.get("__snapshot_kind").getAsString()
            : "full";
    }

    private static void mergeChapterSnapshot(JsonObject incoming) {
        if (snapshot == null) snapshot = new JsonObject();
        incoming.entrySet().forEach(entry -> {
            String key = entry.getKey();
            if (key.equals("__snapshot_kind") || key.equals("__chapter")) return;
            if (key.startsWith("__") && snapshot.has(key)) return;
            snapshot.add(key, entry.getValue().deepCopy());
        });
    }

    private void clientTick(ClientTickEvent.Post event) {
        while (OPEN_QUESTS.consumeClick()) {
            openQuestScreen();
        }
        while (TOGGLE_TRACKER.consumeClick()) {
            trackerCollapsed = !TheseusClientOptions.trackerCollapsed();
            TheseusClientOptions.setTrackerCollapsed(trackerCollapsed);
        }
    }

    private void clientLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = new JsonObject();
        if (!(Minecraft.getInstance().gui.screen() instanceof QuestScreen screen)) {
            clearDisconnectedEditor();
            return;
        }
        String server = serverKey(event.getConnection());
        if (server == null) {
            clearDisconnectedEditor();
            return;
        }
        screen.handleConnectionLost();
        disconnectedEditor = screen;
        disconnectedServer = server;
    }

    private void clientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (disconnectedEditor != null
            && !java.util.Objects.equals(disconnectedServer, serverKey(event.getConnection()))) {
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
