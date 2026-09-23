package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestNetwork;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;

/** Applies full and per-chapter snapshots to the screen state. */
final class QuestScreenSnapshot {
    private final QuestScreen screen;

    QuestScreenSnapshot(QuestScreen screen) {
        this.screen = screen;
    }

    private static void readServerTypes(JsonObject root, String key, Set<String> target) {
        if (!root.has(key) || !root.get(key).isJsonArray()) return;
        root.getAsJsonArray(key).forEach(value -> {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) target.add(value.getAsString());
        });
    }

    void readSnapshot(JsonObject snapshot) {
        String snapshotKind = snapshot.has("__snapshot_kind") && snapshot.get("__snapshot_kind").isJsonPrimitive()
            ? snapshot.get("__snapshot_kind").getAsString()
            : "full";
        boolean indexSnapshot = snapshotKind.equals("index");
        boolean chapterSnapshot = snapshotKind.equals("chapter");
        if (indexSnapshot || !chapterSnapshot) {
            screen.quests.clear();
            screen.chapters.clear();
            screen.chapterDisplays.clear();
            screen.loadedChapters.clear();
            screen.pendingChapterLoads.clear();
        }
        if (snapshot.has("__editor_types") && snapshot.get("__editor_types").isJsonObject()) {
            JsonObject types = snapshot.getAsJsonObject("__editor_types");
            readServerTypes(types, "tasks", screen.serverTaskTypes);
            readServerTypes(types, "rewards", screen.serverRewardTypes);
            readServerTypes(types, "icons", screen.serverIconTypes);
        }
        if (snapshot.has("__chapters") && snapshot.get("__chapters").isJsonObject()) {
            JsonObject metadata = snapshot.getAsJsonObject("__chapters");
            if (!chapterSnapshot && metadata.has("order") && metadata.get("order").isJsonArray()) {
                metadata.getAsJsonArray("order").forEach(value -> screen.chapters.add(value.getAsString()));
            }
            if (metadata.has("settings") && metadata.get("settings").isJsonObject()) {
                metadata.getAsJsonObject("settings").entrySet().forEach(entry -> {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    screen.chapterDisplays.put(entry.getKey(), new ChapterDisplay(
                        jsonString(value, "icon", "minecraft:map"),
                        jsonString(value, "background", ""),
                        !value.has("iconEnabled") || value.get("iconEnabled").getAsBoolean(),
                        value.has("backgroundOpacity") ? Math.clamp(value.get("backgroundOpacity").getAsInt(), 0, 100) : 100
                    ));
                });
            }
        }
        snapshot.entrySet().forEach(entry -> {
            if (entry.getKey().startsWith("__") || !entry.getValue().isJsonObject()) return;
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition definition = QuestDefinition.parse(entry.getKey(), json);
            Map<String, Integer> progress = new HashMap<>();
            if (json.has("progress") && json.get("progress").isJsonObject()) {
                json.getAsJsonObject("progress")
                    .entrySet()
                    .forEach(task -> progress.put(task.getKey(), task.getValue().getAsInt()));
            }
            Set<String> claimedRewards = new LinkedHashSet<>();
            if (json.has("claimed_rewards") && json.get("claimed_rewards").isJsonArray()) {
                json.getAsJsonArray("claimed_rewards").forEach(reward -> {
                    if (reward.isJsonPrimitive() && reward.getAsJsonPrimitive().isString()) {
                        String id = reward.getAsString();
                        if (definition.rewards().containsKey(id)) claimedRewards.add(id);
                    }
                });
            } else if (json.has("claimed") && json.get("claimed").isJsonPrimitive()
                && json.get("claimed").getAsBoolean()) {
                claimedRewards.addAll(definition.rewards().keySet());
            }
            screen.quests.removeIf(existing -> existing.definition().id().equals(definition.id()));
            screen.quests.add(new ClientQuest(
                definition,
                Map.copyOf(progress),
                json.get("unlocked").getAsBoolean(),
                json.get("complete").getAsBoolean(),
                json.get("claimed").getAsBoolean(),
                json.has("pinned") && json.get("pinned").getAsBoolean(),
                Set.copyOf(claimedRewards),
                json.deepCopy()
            )
            );
        });
        screen.quests.sort(Comparator.comparing(quest -> quest.definition().id()));
        if (!chapterSnapshot && !indexSnapshot) screen.loadedChapters.addAll(screen.actions.groups());
        else if (snapshot.has("__chapter") && snapshot.get("__chapter").isJsonPrimitive()) {
            String chapter = snapshot.get("__chapter").getAsString();
            screen.loadedChapters.add(chapter);
            screen.pendingChapterLoads.remove(chapter);
        }
    }

    /** Requests full task/reward/description data for the active chapter. */
    void requestActiveChapter() {
        requestChapter(screen.group);
    }

    void requestChapter(String chapter) {
        if (chapter == null || screen.loadedChapters.contains(chapter) || !screen.pendingChapterLoads.add(chapter)) return;
        ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("load_chapter", chapter));
    }

    void mergeSnapshot(JsonObject snapshot) {
        if (snapshot == null) return;
        readSnapshot(snapshot);
        screen.graphViewport.activateChapter(screen.group, screen.layout.graphCanvasBounds(), screen.layout.graphWorldBounds());
        screen.rebuildWidgets();
    }
}
