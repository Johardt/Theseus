package me.johardt.theseus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Structured validation of concrete game identifiers behind a registry seam. */
public final class RegistryValidation {
    private RegistryValidation() {}

    public enum Target {
        ITEM, ENTITY, BLOCK, BIOME, DIMENSION, STRUCTURE, STAT, ADVANCEMENT, RECIPE, LOOT_TABLE
    }

    @FunctionalInterface
    public interface Resolver {
        boolean contains(Target target, String identifier);
    }

    public static List<QuestDiagnostics.Diagnostic> validate(String questId, JsonObject root, Resolver resolver) {
        List<QuestDiagnostics.Diagnostic> diagnostics = new ArrayList<>();
        validateTasks(questId, object(root, "tasks"), "tasks", resolver, diagnostics);
        validateRewards(questId, object(root, "rewards"), "rewards", resolver, diagnostics);
        return List.copyOf(diagnostics);
    }

    private static void validateTasks(String questId, JsonObject tasks, String path, Resolver resolver, List<QuestDiagnostics.Diagnostic> diagnostics) {
        tasks.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject task = entry.getValue().getAsJsonObject();
            String type = string(task, "type", "");
            String taskPath = path + "." + entry.getKey();
            switch (type) {
                case "theseus:item", "theseus:item_interaction", "theseus:item_use" -> check(questId, task, taskPath, "item", Target.ITEM, resolver, diagnostics);
                case "theseus:kill_entity", "theseus:entity_interaction" -> check(questId, task, taskPath, "entity", Target.ENTITY, resolver, diagnostics);
                case "theseus:block_interaction" -> check(questId, task, taskPath, "block", Target.BLOCK, resolver, diagnostics);
                case "theseus:biome" -> check(questId, task, taskPath, "biomes", Target.BIOME, resolver, diagnostics);
                case "theseus:changed_dimension" -> {
                    check(questId, task, taskPath, "from", Target.DIMENSION, resolver, diagnostics);
                    check(questId, task, taskPath, "to", Target.DIMENSION, resolver, diagnostics);
                }
                case "theseus:structure" -> check(questId, task, taskPath, "structures", Target.STRUCTURE, resolver, diagnostics);
                case "theseus:stat" -> check(questId, task, taskPath, "stat", Target.STAT, resolver, diagnostics);
                case "theseus:advancement" -> check(questId, task, taskPath, "advancements", Target.ADVANCEMENT, resolver, diagnostics);
                case "theseus:recipe" -> check(questId, task, taskPath, "recipes", Target.RECIPE, resolver, diagnostics);
                default -> { }
            }
            if ("theseus:composite".equals(type)) validateTasks(questId, object(task, "tasks"), taskPath + ".tasks", resolver, diagnostics);
        });
    }

    private static void validateRewards(String questId, JsonObject rewards, String path, Resolver resolver, List<QuestDiagnostics.Diagnostic> diagnostics) {
        rewards.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject reward = entry.getValue().getAsJsonObject();
            String type = string(reward, "type", "");
            String rewardPath = path + "." + entry.getKey();
            if ("theseus:item".equals(type)) {
                JsonElement item = reward.get("item");
                String id = item != null && item.isJsonObject() ? string(item.getAsJsonObject(), "id", "") : item == null ? "" : item.getAsString();
                checkValue(questId, id, rewardPath + ".item", Target.ITEM, resolver, diagnostics);
            } else if ("theseus:loottable".equals(type)) {
                checkValue(questId, string(reward, "loot_table", ""), rewardPath + ".loot_table", Target.LOOT_TABLE, resolver, diagnostics);
            }
            if ("theseus:selectable".equals(type)) validateRewards(questId, object(reward, "rewards"), rewardPath + ".rewards", resolver, diagnostics);
        });
    }

    private static void check(String questId, JsonObject object, String path, String key, Target target, Resolver resolver, List<QuestDiagnostics.Diagnostic> diagnostics) {
        if (!object.has(key)) return;
        JsonElement value = object.get(key);
        if (value.isJsonArray()) {
            for (int index = 0; index < value.getAsJsonArray().size(); index++) {
                JsonElement entry = value.getAsJsonArray().get(index);
                if (entry.isJsonPrimitive()) checkValue(questId, entry.getAsString(), path + "." + key + "[" + index + "]", target, resolver, diagnostics);
            }
        } else if (value.isJsonPrimitive()) {
            checkValue(questId, value.getAsString(), path + "." + key, target, resolver, diagnostics);
        }
    }

    private static void checkValue(String questId, String value, String path, Target target, Resolver resolver, List<QuestDiagnostics.Diagnostic> diagnostics) {
        if (value == null || value.isBlank()) return;
        String lookup = value.startsWith("#") ? value.substring(1) : value;
        if (lookup.isBlank() || !resolver.contains(target, value)) {
            diagnostics.add(new QuestDiagnostics.Diagnostic(
                QuestDiagnostics.Severity.ERROR,
                "unknown_" + target.name().toLowerCase(java.util.Locale.ROOT),
                questId,
                path,
                "Unknown " + target.name().toLowerCase(java.util.Locale.ROOT) + " '" + value + "'",
                "Choose a value registered on this server."
            ));
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
}
