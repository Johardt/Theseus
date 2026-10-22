package me.johardt.theseus.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import me.johardt.theseus.client.QuestAuthoringSession.RewardDraft;
import me.johardt.theseus.client.QuestAuthoringSession.TaskDraft;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDiagnostics;
import me.johardt.theseus.core.RegistryValidation;

/** JSON normalization, nesting, and validation rules for quest editor drafts. */
final class QuestDraftValidation {
    private QuestDraftValidation() {}

    static String validateTaskDraft(
        TaskDraft task,
        List<TaskDraft> siblings,
        int editedIndex,
        RegistryLookup registries
    ) {
        if (task.id == null || !task.id.matches("[a-z0-9_.-]+")) {
            return "ID may only contain lowercase letters, numbers, ., _, and -.";
        }
        for (int index = 0; index < siblings.size(); index++) {
            if (index != editedIndex && siblings.get(index).id.equals(task.id)) {
                return "Another task already uses this ID.";
            }
        }
        String structuredError = normalizeStructuredTaskFields(task);
        if (!structuredError.isEmpty()) return structuredError;
        String registryError = RegistryValidation.validate(
                "editor",
                taskRoot(task),
                registries.targetResolver()
            ).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(QuestDiagnostics.Diagnostic::message)
            .findFirst().orElse("");
        if (!registryError.isEmpty()) return registryError;
        if (task.type.equals("theseus:dummy") && jsonString(task.source, "value", "").isBlank()) {
            return "Trigger value is required.";
        }
        if (List.of("theseus:item", "theseus:xp", "theseus:kill_entity", "theseus:composite").contains(task.type)
            && jsonInt(task.source, "amount", 0) < 1) return "Amount must be at least 1.";
        if (task.type.equals("theseus:stat") && jsonInt(task.source, "target", 0) < 1) {
            return "Target must be at least 1.";
        }
        if (List.of("theseus:item", "theseus:item_interaction", "theseus:item_use").contains(task.type)) {
            String item = registryValueString(task.source, "item", "");
            if (!validIdentifier(item.startsWith("#") ? item.substring(1) : item)) {
                return "Enter a valid item or #tag identifier.";
            }
            if (!item.startsWith("#") && !registries.itemExists().test(item)) return "That item does not exist.";
        }
        if (List.of("theseus:kill_entity", "theseus:entity_interaction").contains(task.type)) {
            String entity = registryValueString(task.source, "entity", "");
            String id = entity.startsWith("#") ? entity.substring(1) : entity;
            if (!validIdentifier(id) || (!entity.startsWith("#") && !registries.entityExists().test(entity))) {
                return "That entity does not exist.";
            }
        }
        String identifierError = validateTaskIdentifiers(task);
        if (!identifierError.isEmpty()) return identifierError;
        return QuestDefinition.parse("editor", taskRoot(task)).issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(QuestDefinition.ValidationIssue::message)
            .findFirst().orElse("");
    }

    static String validateRewardDraft(
        RewardDraft reward,
        List<RewardDraft> siblings,
        int editedIndex,
        boolean nested,
        RegistryLookup registries
    ) {
        if (reward.id == null || !reward.id.matches("[a-z0-9_.-]+")) {
            return "ID may only contain lowercase letters, numbers, ., _, and -.";
        }
        for (int index = 0; index < siblings.size(); index++) {
            if (index != editedIndex && siblings.get(index).id.equals(reward.id)) {
                return "Another reward already uses this ID.";
            }
        }
        if (nested && reward.type.equals("theseus:selectable")) {
            return "Selectable rewards cannot contain selectable rewards.";
        }
        if (reward.type.equals("theseus:item")) {
            if (!registries.itemExists().test(rewardItemId(reward.source))) return "That item does not exist.";
            if (rewardItemCount(reward.source) < 1) return "Amount must be at least 1.";
        }
        if (reward.type.equals("theseus:xp") && jsonInt(reward.source, "amount", 0) < 1) {
            return "Amount must be at least 1.";
        }
        if (reward.type.equals("theseus:loottable") && !validIdentifier(jsonString(reward.source, "loot_table", ""))) {
            return "Enter a valid loot table identifier.";
        }
        if (reward.type.equals("theseus:command") && jsonString(reward.source, "command", "").isBlank()) {
            return "Command is required.";
        }
        if (reward.type.equals("theseus:selectable")) {
            List<RewardDraft> choices = nestedRewards(reward);
            int amount = jsonInt(reward.source, "amount", 0);
            if (choices.isEmpty()) return "Add at least one selectable reward choice.";
            if (amount < 1 || amount > choices.size()) {
                return "Selection amount must be between 1 and the number of choices.";
            }
            if (choices.stream().anyMatch(choice -> choice.type.equals("theseus:selectable"))) {
                return "Selectable rewards cannot contain selectable rewards.";
            }
        }
        QuestDefinition parsed = QuestDefinition.parse("editor", rewardRoot(reward));
        return parsed.issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(QuestDefinition.ValidationIssue::message)
            .findFirst().orElse("");
    }

    static JsonObject taskRoot(TaskDraft task) {
        JsonObject root = new JsonObject();
        root.addProperty("title", "Editor preview");
        JsonObject tasks = new JsonObject();
        tasks.add(task.id, task.source.deepCopy());
        root.add("tasks", tasks);
        return root;
    }

    static JsonObject rewardRoot(RewardDraft reward) {
        JsonObject root = new JsonObject();
        root.addProperty("title", "Editor preview");
        JsonObject rewards = new JsonObject();
        rewards.add(reward.id, reward.source.deepCopy());
        root.add("rewards", rewards);
        return root;
    }

    static String rewardItemId(JsonObject source) {
        if (!source.has("item")) return "minecraft:stone";
        if (source.get("item").isJsonObject()) return jsonString(source.getAsJsonObject("item"), "id", "minecraft:stone");
        return source.get("item").getAsString();
    }

    static int rewardItemCount(JsonObject source) {
        return source.has("item") && source.get("item").isJsonObject()
            ? jsonInt(source.getAsJsonObject("item"), "count", 1) : 1;
    }

    static void setRewardItem(JsonObject source, String id, int count) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("count", count);
        source.add("item", item);
    }

    static List<RewardDraft> nestedRewards(RewardDraft parent) {
        List<RewardDraft> rewards = new ArrayList<>();
        if (parent == null || !parent.source.has("rewards") || !parent.source.get("rewards").isJsonObject()) return rewards;
        parent.source.getAsJsonObject("rewards").entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject()) {
                JsonObject source = entry.getValue().getAsJsonObject();
                rewards.add(new RewardDraft(entry.getKey(), jsonString(source, "type", "theseus:item"), source.deepCopy()));
            }
        });
        return rewards;
    }

    static void setNestedRewards(RewardDraft parent, List<RewardDraft> rewards) {
        JsonObject object = new JsonObject();
        rewards.forEach(reward -> object.add(reward.id, reward.source.deepCopy()));
        parent.source.add("rewards", object);
    }

    static List<TaskDraft> nestedTasks(TaskDraft parent) {
        List<TaskDraft> tasks = new ArrayList<>();
        if (parent == null || !parent.source.has("tasks") || !parent.source.get("tasks").isJsonObject()) return tasks;
        parent.source.getAsJsonObject("tasks").entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject source = entry.getValue().getAsJsonObject();
            tasks.add(new TaskDraft(entry.getKey(), jsonString(source, "type", "theseus:unknown"), source.deepCopy()));
        });
        return tasks;
    }

    static void setNestedTasks(TaskDraft parent, List<TaskDraft> tasks) {
        JsonObject object = new JsonObject();
        tasks.forEach(task -> object.add(task.id, task.source.deepCopy()));
        parent.source.add("tasks", object);
    }

    static boolean validIdentifier(String value) {
        return value != null && value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    }

    static String jsonString(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    static String registryValueString(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonPrimitive()) return value.getAsString();
        if (!value.isJsonObject()) return fallback;
        JsonObject registryValue = value.getAsJsonObject();
        if (registryValue.has("tag")) return "#" + jsonString(registryValue, "tag", "");
        return jsonString(registryValue, "id", fallback);
    }

    static int jsonInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    static JsonArray stringArray(String values) {
        JsonArray array = new JsonArray();
        for (String value : values.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) array.add(trimmed);
        }
        return array;
    }

    static JsonObject defaultLocationPredicate() {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("dimension", "minecraft:overworld");
        return predicate;
    }

    static String friendly(String value) {
        String text = value.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String normalizeStructuredTaskFields(TaskDraft task) {
        List<String> keys = switch (task.type) {
            case "theseus:check", "theseus:entity_interaction",
                 "theseus:item_interaction", "theseus:item_use" -> List.of("components");
            case "theseus:block_interaction" -> List.of("components", "state");
            case "theseus:location" -> List.of("predicate");
            case "theseus:composite" -> List.of("tasks");
            default -> List.of();
        };
        for (String key : keys) {
            JsonElement value = task.source.get(key);
            if (value == null) continue;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                try {
                    value = JsonParser.parseString(value.getAsString());
                    task.source.add(key, value);
                } catch (RuntimeException exception) {
                    return friendly(key) + " must be valid JSON.";
                }
            }
            if (!value.isJsonObject()) return friendly(key) + " must be a JSON object.";
        }
        return "";
    }

    private static String validateTaskIdentifiers(TaskDraft task) {
        List<String> scalarKeys = switch (task.type) {
            case "theseus:biome" -> List.of("biomes");
            case "theseus:block_interaction" -> List.of("block");
            case "theseus:changed_dimension" -> List.of("from", "to");
            case "theseus:stat" -> List.of("stat");
            case "theseus:structure" -> List.of("structures");
            default -> List.of();
        };
        for (String key : scalarKeys) {
            String value = registryValueString(task.source, key, "");
            if (task.type.equals("theseus:changed_dimension") && value.isBlank()) continue;
            String id = value.startsWith("#") ? value.substring(1) : value;
            if (!validIdentifier(id)) {
                return friendly(key) + " must be a valid identifier" + (key.equals("from") || key.equals("to") ? " or blank." : ".");
            }
        }
        for (String key : List.of("advancements", "recipes")) {
            if (!task.source.has(key)) continue;
            JsonElement values = task.source.get(key);
            if (!values.isJsonArray() || values.getAsJsonArray().isEmpty()) {
                return friendly(key) + " must contain at least one identifier.";
            }
            for (JsonElement value : values.getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !validIdentifier(value.getAsString())) {
                    return friendly(key) + " contains an invalid identifier.";
                }
            }
        }
        return "";
    }

    record RegistryLookup(
        RegistryValidation.Resolver targetResolver,
        Predicate<String> itemExists,
        Predicate<String> entityExists
    ) {}
}
