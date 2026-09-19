package me.johardt.theseus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The loader-neutral quest model shared by gameplay, networking, and UI. */
public record QuestDefinition(
    String id,
    Display display,
    Settings settings,
    Set<String> dependencies,
    Map<String, Task> tasks,
    Map<String, Reward> rewards,
    List<ValidationIssue> issues
) {
    public static QuestDefinition parse(String id, JsonObject root) {
        List<ValidationIssue> issues = new ArrayList<>();
        for (String key : List.of("display", "tasks", "rewards", "settings")) {
            if (root.has(key) && !root.get(key).isJsonObject()) {
                issues.add(new ValidationIssue(Severity.ERROR, key, "Expected an object"));
            }
        }
        if (root.has("dependencies") && !root.get("dependencies").isJsonArray() && !root.get("dependencies").isJsonPrimitive()) {
            issues.add(new ValidationIssue(Severity.ERROR, "dependencies", "Expected a quest ID or an array of quest IDs"));
        }
        JsonObject displayJson = object(root, "display");
        Map<String, GroupDisplay> groups = new LinkedHashMap<>();
        object(displayJson, "groups").entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) {
                issues.add(new ValidationIssue(Severity.ERROR, "display.groups." + entry.getKey(), "Group placement must be an object"));
                return;
            }
            JsonObject group = entry.getValue().getAsJsonObject();
            JsonArray position = array(group, "position");
            if (position.size() != 2 || !position.get(0).isJsonPrimitive() || !position.get(1).isJsonPrimitive()
                || !position.get(0).getAsJsonPrimitive().isNumber() || !position.get(1).getAsJsonPrimitive().isNumber()) {
                issues.add(new ValidationIssue(Severity.ERROR, "display.groups." + entry.getKey() + ".position", "Position must contain exactly two numbers"));
            }
            groups.put(entry.getKey(), new GroupDisplay(
                numeric(position, 0), numeric(position, 1)
            ));
        });
        if (groups.isEmpty()) groups.put("Main", new GroupDisplay(0, 0));

        Map<String, Task> tasks = parseTasks(object(root, "tasks"), "tasks", issues);

        Map<String, Reward> rewards = parseRewards(object(root, "rewards"), "rewards", issues);

        JsonObject settingsJson = object(root, "settings");
        String hidden = string(settingsJson, "hidden", "locked");
        try {
            Visibility.valueOf(hidden.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            issues.add(new ValidationIssue(Severity.WARNING, "settings.hidden", "Unknown visibility '" + hidden + "'; using locked"));
        }
        Settings settings = new Settings(
            bool(settingsJson, "individual_progress", false),
            Visibility.from(hidden),
            bool(settingsJson, "unlockNotification", "unlock_notification", false),
            bool(settingsJson, "showDependencyArrow", "show_dependency_arrow", true),
            bool(settingsJson, "repeatable", false),
            bool(settingsJson, "autoClaimRewards", "auto_claim_rewards", false)
        );

        return new QuestDefinition(
            id,
            new Display(
                QuestIconDefinition.parse(displayJson.get("icon"), "minecraft:map"),
                string(displayJson, "icon_background", "theseus:textures/gui/quest_backgrounds/default.png"),
                iconSize(displayJson),
                componentText(displayJson.get("title"), id),
                componentText(displayJson.get("subtitle"), ""),
                strings(displayJson.get("description")),
                Collections.unmodifiableMap(new LinkedHashMap<>(groups))
            ),
            settings,
            Collections.unmodifiableSet(new LinkedHashSet<>(strings(root.get("dependencies")))),
            Collections.unmodifiableMap(new LinkedHashMap<>(tasks)),
            Collections.unmodifiableMap(new LinkedHashMap<>(rewards)),
            List.copyOf(issues)
        );
    }

    private static Map<String, Task> parseTasks(JsonObject root, String path, List<ValidationIssue> issues) {
        Map<String, Task> tasks = new LinkedHashMap<>();
        root.entrySet().forEach(entry -> {
            String taskPath = path + "." + entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                issues.add(new ValidationIssue(Severity.ERROR, taskPath, "Task must be an object"));
                return;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
                issues.add(new ValidationIssue(Severity.ERROR, taskPath + ".type", "Task type must be a string identifier"));
            }
            String type = string(json, "type", "theseus:unknown");
            validateIdentifier(type, taskPath + ".type", issues);
            TaskKind kind = TaskKind.from(type);
            if (kind == TaskKind.UNSUPPORTED) {
                issues.add(new ValidationIssue(Severity.WARNING, taskPath + ".type", "Task type is not handled by the built-in engine; it needs an add-on handler: " + type));
            }
            String targetKey = kind == TaskKind.STAT ? "target" : "amount";
            int target = kind.isCounting() || kind == TaskKind.COMPOSITE
                ? positiveInteger(json, targetKey, 1, issues, taskPath + "." + targetKey) : 1;
            String value = switch (kind) {
                case ITEM, ITEM_INTERACTION, ITEM_USE -> string(json, "item", "minecraft:air");
                case KILL_ENTITY, ENTITY_INTERACTION -> string(json, "entity", "minecraft:pig");
                case BLOCK_INTERACTION -> string(json, "block", "minecraft:air");
                case BIOME -> string(json, "biomes", string(json, "biome", "minecraft:plains"));
                case ADVANCEMENT -> firstString(json.get("advancements"), string(json, "advancement", ""));
                case RECIPE -> firstString(json.get("recipes"), string(json, "recipe", ""));
                case STAT -> string(json, "stat", "");
                case DUMMY -> string(json, "value", "");
                default -> "";
            };
            Map<String, Task> children = kind == TaskKind.COMPOSITE
                ? parseTasks(object(json, "tasks"), taskPath + ".tasks", issues) : Map.of();
            if (kind == TaskKind.COMPOSITE && children.isEmpty()) {
                issues.add(new ValidationIssue(Severity.ERROR, taskPath + ".tasks", "Composite task must contain at least one task"));
            }
            if (kind == TaskKind.COMPOSITE && target > children.size() && !children.isEmpty()) {
                issues.add(new ValidationIssue(Severity.ERROR, taskPath + ".amount", "Composite amount exceeds its number of tasks"));
            }
            validateTaskTarget(kind, json, value, taskPath, issues);
            tasks.put(entry.getKey(), new Task(entry.getKey(), type, kind, string(json, "title", entry.getKey()), value, target, json.deepCopy(), Collections.unmodifiableMap(new LinkedHashMap<>(children))));
        });
        return tasks;
    }

    private static Map<String, Reward> parseRewards(JsonObject root, String path, List<ValidationIssue> issues) {
        Map<String, Reward> rewards = new LinkedHashMap<>();
        root.entrySet().forEach(entry -> {
            String rewardPath = path + "." + entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                issues.add(new ValidationIssue(Severity.ERROR, rewardPath, "Reward must be an object"));
                return;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
                issues.add(new ValidationIssue(Severity.ERROR, rewardPath + ".type", "Reward type must be a string identifier"));
            }
            String type = string(json, "type", "theseus:unknown");
            validateIdentifier(type, rewardPath + ".type", issues);
            RewardKind kind = RewardKind.from(type);
            if (kind == RewardKind.UNSUPPORTED) {
                issues.add(new ValidationIssue(Severity.WARNING, rewardPath + ".type", "Reward type is not handled by the built-in executor; it needs an add-on handler: " + type));
            }
            RewardValue value = rewardValue(kind, json, issues, rewardPath);
            Map<String, Reward> choices = kind == RewardKind.SELECTABLE
                ? parseRewards(object(json, "rewards"), rewardPath + ".rewards", issues) : Map.of();
            if (kind == RewardKind.SELECTABLE && choices.isEmpty()) {
                issues.add(new ValidationIssue(Severity.ERROR, rewardPath + ".rewards", "Selectable reward must contain at least one reward"));
            }
            if (kind == RewardKind.SELECTABLE && value.amount() > choices.size() && !choices.isEmpty()) {
                issues.add(new ValidationIssue(Severity.ERROR, rewardPath + ".amount", "Selection amount exceeds the number of rewards"));
            }
            if (kind == RewardKind.SELECTABLE && choices.values().stream().anyMatch(choice -> choice.kind() == RewardKind.SELECTABLE)) {
                issues.add(new ValidationIssue(Severity.ERROR, rewardPath + ".rewards", "Selectable rewards cannot contain another selectable reward"));
            }
            if (kind == RewardKind.ITEM || kind == RewardKind.LOOT_TABLE) validateIdentifier(value.value(), rewardPath, issues);
            if (kind == RewardKind.COMMAND && value.value().isBlank()) {
                issues.add(new ValidationIssue(Severity.ERROR, rewardPath + ".command", "Command must not be empty"));
            }
            rewards.put(entry.getKey(), new Reward(entry.getKey(), type, kind, string(json, "title", entry.getKey()), value.value(), value.amount(), json.deepCopy(), Collections.unmodifiableMap(new LinkedHashMap<>(choices))));
        });
        return rewards;
    }

    public String title() { return display.title(); }
    public String subtitle() { return display.subtitle(); }
    public List<String> description() { return display.description(); }
    public GroupDisplay position(String group) { return display.groups().getOrDefault(group, new GroupDisplay(0, 0)); }

    public Identifier itemId(Task task) {
        return Identifier.parse(task.value());
    }

    private static RewardValue rewardValue(RewardKind kind, JsonObject json, List<ValidationIssue> issues, String path) {
        if (kind == RewardKind.XP) {
            return new RewardValue(string(json, "xptype", "level"), positiveInteger(json, "amount", 1, issues, path + ".amount"));
        }
        if (kind == RewardKind.ITEM) {
            JsonElement item = json.get("item");
            if (item != null && item.isJsonObject()) {
                return new RewardValue(string(item.getAsJsonObject(), "id", "minecraft:air"), positiveInteger(item.getAsJsonObject(), "count", 1, issues, path + ".item.count"));
            }
            return new RewardValue(item == null ? "minecraft:air" : item.getAsString(), 1);
        }
        if (kind == RewardKind.LOOT_TABLE) return new RewardValue(string(json, "loot_table", ""), 1);
        if (kind == RewardKind.COMMAND) return new RewardValue(string(json, "command", ""), 1);
        if (kind == RewardKind.SELECTABLE) return new RewardValue("", positiveInteger(json, "amount", 1, issues, path + ".amount"));
        return new RewardValue("", 1);
    }

    private static int positiveInteger(JsonObject object, String key, int fallback, List<ValidationIssue> issues, String path) {
        if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isNumber())) {
            issues.add(new ValidationIssue(Severity.ERROR, path, "Expected a positive integer"));
            return fallback;
        }
        int value = integer(object, key, fallback);
        if (value > 0) return value;
        issues.add(new ValidationIssue(Severity.ERROR, path, "Value must be positive"));
        return fallback;
    }

    private static int numeric(JsonArray values, int index) {
        if (values.size() <= index || !values.get(index).isJsonPrimitive() || !values.get(index).getAsJsonPrimitive().isNumber()) return 0;
        return values.get(index).getAsInt();
    }

    private static void validateIdentifier(String value, String path, List<ValidationIssue> issues) {
        String normalized = value == null ? "" : value;
        boolean valid = normalized.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
        if (!valid) {
            issues.add(new ValidationIssue(Severity.ERROR, path, "Invalid identifier '" + value + "'"));
        }
    }

    private static void validateTaskTarget(TaskKind kind, JsonObject json, String value, String path, List<ValidationIssue> issues) {
        switch (kind) {
            case ITEM, ITEM_INTERACTION, ITEM_USE, KILL_ENTITY, ENTITY_INTERACTION, BLOCK_INTERACTION,
                 BIOME, ADVANCEMENT, RECIPE, STAT -> {
                if (!value.isBlank()) validateIdentifier(value.startsWith("#") ? value.substring(1) : value, path, issues);
            }
            default -> {}
        }
        if (kind == TaskKind.CHANGED_DIMENSION) {
            for (String key : List.of("from", "to")) {
                String dimension = string(json, key, "");
                if (!dimension.isBlank()) validateIdentifier(dimension, path + "." + key, issues);
            }
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean()
            ? object.get(key).getAsBoolean() : fallback;
    }

    private static boolean bool(JsonObject object, String key, String alias, boolean fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean()) {
            return object.get(key).getAsBoolean();
        }
        return bool(object, alias, fallback);
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static int iconSize(JsonObject display) {
        if (!display.has("icon_size")) return 16;
        try {
            JsonElement value = display.get("icon_size");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 16;
            BigDecimal number = new BigDecimal(value.getAsString());
            if (number.stripTrailingZeros().scale() > 0) return 16;
            int size = number.intValueExact();
            return size >= 8 && size <= 64 ? size : 16;
        } catch (RuntimeException ignored) {
            return 16;
        }
    }

    private static List<String> strings(JsonElement element) {
        if (element == null) return List.of();
        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            element.getAsJsonArray().forEach(value -> {
                if (value.isJsonPrimitive()) values.add(value.getAsString());
            });
            return List.copyOf(values);
        }
        return element.isJsonPrimitive() ? List.of(element.getAsString()) : List.of();
    }

    private static String firstString(JsonElement element, String fallback) {
        List<String> values = strings(element);
        return values.isEmpty() ? fallback : values.getFirst();
    }

    private static String componentText(JsonElement element, String fallback) {
        if (element == null) return fallback;
        if (element.isJsonPrimitive()) return element.getAsString();
        JsonObject component = element.getAsJsonObject();
        return string(component, "text", string(component, "translate", fallback));
    }

    private record RewardValue(String value, int amount) {}
    public record Display(QuestIconDefinition icon, String iconBackground, int iconSize, String title, String subtitle, List<String> description, Map<String, GroupDisplay> groups) {
        public Display(
            QuestIconDefinition icon,
            String iconBackground,
            String title,
            String subtitle,
            List<String> description,
            Map<String, GroupDisplay> groups
        ) {
            this(icon, iconBackground, 16, title, subtitle, description, groups);
        }
    }
    public record GroupDisplay(int x, int y) {}
    public record Settings(boolean individualProgress, Visibility hiddenUntil, boolean unlockNotification, boolean showDependencyArrow, boolean repeatable, boolean autoClaimRewards) {}
    public record Task(String id, String type, TaskKind kind, String title, String value, int target, JsonObject source, Map<String, Task> tasks) {}
    public record Reward(String id, String type, RewardKind kind, String title, String value, int amount, JsonObject source, Map<String, Reward> rewards) {}
    public record ValidationIssue(Severity severity, String path, String message) {}
    public enum Severity { WARNING, ERROR }
    public enum Visibility { NEVER, LOCKED, DEPENDENCIES_VISIBLE, IN_PROGRESS, COMPLETED;
        static Visibility from(String value) {
            try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return LOCKED; }
        }
    }
    public enum TaskKind {
        DUMMY, ITEM, CHECK, ADVANCEMENT, XP, KILL_ENTITY, ITEM_INTERACTION, ITEM_USE,
        BLOCK_INTERACTION, ENTITY_INTERACTION, CHANGED_DIMENSION, BIOME, LOCATION, RECIPE, STAT,
        STRUCTURE, COMPOSITE, UNSUPPORTED;

        public boolean isCounting() {
            return this == ITEM || this == XP || this == KILL_ENTITY || this == STAT;
        }

        static TaskKind from(String type) {
            return switch (type) {
                case "theseus:dummy" -> DUMMY;
                case "theseus:item" -> ITEM;
                case "theseus:check" -> CHECK;
                case "theseus:advancement" -> ADVANCEMENT;
                case "theseus:xp" -> XP;
                case "theseus:kill_entity" -> KILL_ENTITY;
                case "theseus:item_interaction" -> ITEM_INTERACTION;
                case "theseus:item_use" -> ITEM_USE;
                case "theseus:block_interaction" -> BLOCK_INTERACTION;
                case "theseus:entity_interaction" -> ENTITY_INTERACTION;
                case "theseus:changed_dimension" -> CHANGED_DIMENSION;
                case "theseus:biome" -> BIOME;
                case "theseus:location" -> LOCATION;
                case "theseus:recipe" -> RECIPE;
                case "theseus:stat" -> STAT;
                case "theseus:structure" -> STRUCTURE;
                case "theseus:composite" -> COMPOSITE;
                default -> UNSUPPORTED;
            };
        }
    }
    public enum RewardKind { XP, ITEM, LOOT_TABLE, COMMAND, SELECTABLE, UNSUPPORTED;
        static RewardKind from(String type) {
            return switch (type) {
                case "theseus:xp" -> XP;
                case "theseus:item" -> ITEM;
                case "theseus:loottable" -> LOOT_TABLE;
                case "theseus:command" -> COMMAND;
                case "theseus:selectable" -> SELECTABLE;
                default -> UNSUPPORTED;
            };
        }
    }
}
