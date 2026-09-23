package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.johardt.theseus.core.QuestDefinition;

import static me.johardt.theseus.client.QuestDraftValidation.jsonString;

/** Owns the raw HUD snapshot and the parsed quest/chapter state used by the screen. */
final class QuestClientSnapshot {
    enum Kind { INDEX, CHAPTER, FULL }

    private JsonObject raw = new JsonObject();
    private final List<ClientQuest> quests = new ArrayList<>();
    private final List<String> chapters = new ArrayList<>();
    private final Map<String, ChapterDisplay> chapterDisplays = new HashMap<>();
    private final Set<String> loadedChapters = new LinkedHashSet<>();
    private final Set<String> pendingChapterLoads = new LinkedHashSet<>();
    private final Set<String> serverTaskTypes = new LinkedHashSet<>();
    private final Set<String> serverRewardTypes = new LinkedHashSet<>();
    private final Set<String> serverIconTypes = new LinkedHashSet<>();

    private final List<ClientQuest> questView = Collections.unmodifiableList(quests);
    private final List<String> chapterView = Collections.unmodifiableList(chapters);
    private final Map<String, ChapterDisplay> chapterDisplayView = Collections.unmodifiableMap(chapterDisplays);
    private final Set<String> loadedChapterView = Collections.unmodifiableSet(loadedChapters);
    private final Set<String> taskTypeView = Collections.unmodifiableSet(serverTaskTypes);
    private final Set<String> rewardTypeView = Collections.unmodifiableSet(serverRewardTypes);
    private final Set<String> iconTypeView = Collections.unmodifiableSet(serverIconTypes);

    QuestClientSnapshot() {}

    QuestClientSnapshot(JsonObject initial) {
        accept(initial);
    }

    JsonObject raw() { return raw; }
    List<ClientQuest> quests() { return questView; }
    List<String> chapters() { return chapterView; }
    Map<String, ChapterDisplay> chapterDisplays() { return chapterDisplayView; }
    Set<String> loadedChapters() { return loadedChapterView; }
    Set<String> serverTaskTypes() { return taskTypeView; }
    Set<String> serverRewardTypes() { return rewardTypeView; }
    Set<String> serverIconTypes() { return iconTypeView; }

    Set<String> groups() {
        Set<String> result = new LinkedHashSet<>(chapters);
        quests.forEach(quest -> result.addAll(quest.definition().display().groups().keySet()));
        return result;
    }

    void reorderChapters(List<String> order) {
        chapters.clear();
        chapters.addAll(order);
    }

    boolean requestChapter(String chapter) {
        return chapter != null && !loadedChapters.contains(chapter) && pendingChapterLoads.add(chapter);
    }

    Kind accept(JsonObject incoming) {
        if (incoming == null) throw new IllegalArgumentException("Quest snapshot is required");
        Kind kind = kindOf(incoming);
        if (kind == Kind.CHAPTER) mergeRawChapter(incoming);
        else {
            raw = incoming.deepCopy();
            quests.clear();
            chapters.clear();
            chapterDisplays.clear();
            loadedChapters.clear();
            pendingChapterLoads.clear();
            serverTaskTypes.clear();
            serverRewardTypes.clear();
            serverIconTypes.clear();
        }
        readServerTypes(incoming);
        readChapters(incoming, kind);
        readQuests(incoming);
        if (kind == Kind.FULL) loadedChapters.addAll(groups());
        else if (kind == Kind.CHAPTER && incoming.has("__chapter")
            && incoming.get("__chapter").isJsonPrimitive()) {
            String chapter = incoming.get("__chapter").getAsString();
            loadedChapters.add(chapter);
            pendingChapterLoads.remove(chapter);
        }
        return kind;
    }

    static Kind kindOf(JsonObject incoming) {
        if (!incoming.has("__snapshot_kind") || !incoming.get("__snapshot_kind").isJsonPrimitive()) {
            return Kind.FULL;
        }
        return switch (incoming.get("__snapshot_kind").getAsString()) {
            case "index" -> Kind.INDEX;
            case "chapter" -> Kind.CHAPTER;
            default -> Kind.FULL;
        };
    }

    private void mergeRawChapter(JsonObject incoming) {
        incoming.entrySet().forEach(entry -> {
            String key = entry.getKey();
            if (key.equals("__snapshot_kind") || key.equals("__chapter")) return;
            if (key.startsWith("__") && raw.has(key)) return;
            raw.add(key, entry.getValue().deepCopy());
        });
    }

    private void readServerTypes(JsonObject incoming) {
        if (!incoming.has("__editor_types") || !incoming.get("__editor_types").isJsonObject()) return;
        JsonObject types = incoming.getAsJsonObject("__editor_types");
        readTypeSet(types, "tasks", serverTaskTypes);
        readTypeSet(types, "rewards", serverRewardTypes);
        readTypeSet(types, "icons", serverIconTypes);
    }

    private static void readTypeSet(JsonObject root, String key, Set<String> target) {
        if (!root.has(key) || !root.get(key).isJsonArray()) return;
        root.getAsJsonArray(key).forEach(value -> {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) target.add(value.getAsString());
        });
    }

    private void readChapters(JsonObject incoming, Kind kind) {
        if (!incoming.has("__chapters") || !incoming.get("__chapters").isJsonObject()) return;
        JsonObject metadata = incoming.getAsJsonObject("__chapters");
        if (kind != Kind.CHAPTER && metadata.has("order") && metadata.get("order").isJsonArray()) {
            metadata.getAsJsonArray("order").forEach(value -> chapters.add(value.getAsString()));
        }
        if (!metadata.has("settings") || !metadata.get("settings").isJsonObject()) return;
        metadata.getAsJsonObject("settings").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            chapterDisplays.put(entry.getKey(), new ChapterDisplay(
                jsonString(value, "icon", "minecraft:map"),
                jsonString(value, "background", ""),
                !value.has("iconEnabled") || value.get("iconEnabled").getAsBoolean(),
                value.has("backgroundOpacity") ? Math.clamp(value.get("backgroundOpacity").getAsInt(), 0, 100) : 100
            ));
        });
    }

    private void readQuests(JsonObject incoming) {
        incoming.entrySet().forEach(entry -> {
            if (entry.getKey().startsWith("__") || !entry.getValue().isJsonObject()) return;
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition definition = QuestDefinition.parse(entry.getKey(), json);
            Map<String, Integer> progress = new HashMap<>();
            if (json.has("progress") && json.get("progress").isJsonObject()) {
                json.getAsJsonObject("progress").entrySet()
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
            quests.removeIf(existing -> existing.definition().id().equals(definition.id()));
            quests.add(new ClientQuest(
                definition,
                Map.copyOf(progress),
                json.get("unlocked").getAsBoolean(),
                json.get("complete").getAsBoolean(),
                json.get("claimed").getAsBoolean(),
                json.has("pinned") && json.get("pinned").getAsBoolean(),
                Set.copyOf(claimedRewards),
                json.deepCopy()
            ));
        });
        quests.sort(Comparator.comparing(quest -> quest.definition().id()));
    }

    record ChapterDisplay(String icon, String background, boolean iconEnabled, int backgroundOpacity) {}

    record ClientQuest(
        QuestDefinition definition,
        Map<String, Integer> progress,
        boolean unlocked,
        boolean complete,
        boolean claimed,
        boolean pinned,
        Set<String> claimedRewards,
        JsonObject raw
    ) {}
}
