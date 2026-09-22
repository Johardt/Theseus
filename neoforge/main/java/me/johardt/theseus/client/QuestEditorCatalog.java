package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import java.util.List;
import me.johardt.theseus.client.QuestAuthoringSession.RewardDraft;
import me.johardt.theseus.client.QuestAuthoringSession.TaskDraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Built-in editor choices and the lossless starter documents they create. */
final class QuestEditorCatalog {
    private QuestEditorCatalog() {}

    static List<TaskChoice> tasks() {
        return Entries.TASKS;
    }

    static List<RewardChoice> rewards() {
        return Entries.REWARDS;
    }

    static TaskChoice taskChoice(TaskDraft task) {
        return tasks().stream().filter(choice -> choice.type.equals(task.type))
            .findFirst().orElse(tasks().getFirst());
    }

    static RewardChoice rewardChoice(RewardDraft reward) {
        return rewards().stream().filter(choice -> choice.type.equals(reward.type))
            .findFirst().orElse(rewards().getFirst());
    }

    static TaskDraft createTaskDraft(TaskChoice choice, List<TaskDraft> siblings) {
        String id = uniqueId(choice.type, siblings.stream().map(task -> task.id).toList());
        var source = new JsonObject();
        source.addProperty("type", choice.type);
        source.addProperty("title", choice.label);
        switch (choice.type) {
            case "theseus:dummy" -> source.addProperty("value", id);
            case "theseus:item" -> {
                source.addProperty("item", "minecraft:stone");
                source.addProperty("amount", 1);
                source.addProperty("collection", "automatic");
            }
            case "theseus:xp" -> {
                source.addProperty("amount", 1);
                source.addProperty("xpType", "level");
                source.addProperty("collectionType", "automatic");
            }
            case "theseus:kill_entity" -> {
                source.addProperty("entity", "minecraft:pig");
                source.addProperty("amount", 1);
            }
            case "theseus:advancement" -> source.add("advancements", QuestDraftValidation.stringArray("minecraft:story/mine_stone"));
            case "theseus:biome" -> source.addProperty("biomes", "minecraft:plains");
            case "theseus:block_interaction" -> source.addProperty("block", "minecraft:stone");
            case "theseus:changed_dimension" -> source.addProperty("to", "minecraft:the_nether");
            case "theseus:check" -> source.add("components", new JsonObject());
            case "theseus:composite" -> {
                source.addProperty("amount", 1);
                var tasks = new JsonObject();
                var check = new JsonObject();
                check.addProperty("type", "theseus:check");
                check.add("components", new com.google.gson.JsonObject());
                tasks.add("check", check);
                source.add("tasks", tasks);
            }
            case "theseus:entity_interaction" -> source.addProperty("entity", "minecraft:pig");
            case "theseus:item_interaction", "theseus:item_use" -> source.addProperty("item", "minecraft:stick");
            case "theseus:location" -> {
                source.addProperty("description", "Reach the configured location");
                source.add("predicate", QuestDraftValidation.defaultLocationPredicate());
            }
            case "theseus:recipe" -> source.add("recipes", QuestDraftValidation.stringArray("minecraft:crafting_table"));
            case "theseus:stat" -> {
                source.addProperty("stat", "minecraft:jump");
                source.addProperty("target", 1);
            }
            case "theseus:structure" -> source.addProperty("structures", "#minecraft:village");
            default -> throw new IllegalArgumentException("Task type is not implemented: " + choice.type);
        }
        return new TaskDraft(id, choice.type, source);
    }

    static RewardDraft createRewardDraft(RewardChoice choice, List<RewardDraft> siblings) {
        String id = uniqueId(choice.type, siblings.stream().map(reward -> reward.id).toList());
        var source = new JsonObject();
        source.addProperty("type", choice.type);
        source.addProperty("title", choice.label);
        switch (choice.type) {
            case "theseus:xp" -> {
                source.addProperty("xptype", "level");
                source.addProperty("amount", 1);
            }
            case "theseus:item" -> QuestDraftValidation.setRewardItem(source, "minecraft:stone", 1);
            case "theseus:loottable" -> source.addProperty("loot_table", "minecraft:chests/simple_dungeon");
            case "theseus:command" -> source.addProperty("command", "say Quest complete");
            case "theseus:selectable" -> {
                source.addProperty("amount", 1);
                source.add("rewards", new JsonObject());
            }
            default -> throw new IllegalArgumentException("Unknown reward type " + choice.type);
        }
        return new RewardDraft(id, choice.type, source);
    }

    private static String uniqueId(String type, List<String> siblingIds) {
        String base = type.substring(type.indexOf(':') + 1);
        String id = base;
        int suffix = 1;
        while (siblingIds.contains(id)) id = base + "_" + ++suffix;
        return id;
    }

    record TaskChoice(String type, String label, Item icon, boolean implemented) {}

    record RewardChoice(String type, String label, Item icon) {}

    /** Keep Minecraft item initialization lazy so draft logic can be tested without a client bootstrap. */
    private static final class Entries {
        private static final List<TaskChoice> TASKS = List.of(
            new TaskChoice("theseus:dummy", "Dummy", Items.PAPER, true),
            new TaskChoice("theseus:item", "Acquire Item", Items.CHEST, true),
            new TaskChoice("theseus:xp", "Experience", Items.EXPERIENCE_BOTTLE, true),
            new TaskChoice("theseus:kill_entity", "Kill Entity", Items.IRON_SWORD, true),
            new TaskChoice("theseus:advancement", "Advancement", Items.WRITABLE_BOOK, true),
            new TaskChoice("theseus:biome", "Biome", Items.GRASS_BLOCK, true),
            new TaskChoice("theseus:block_interaction", "Block Interaction", Items.STONE_BUTTON, true),
            new TaskChoice("theseus:changed_dimension", "Changed Dimension", Items.ENDER_PEARL, true),
            new TaskChoice("theseus:check", "Check", Items.EMERALD, true),
            new TaskChoice("theseus:composite", "Composite", Items.BUNDLE, true),
            new TaskChoice("theseus:entity_interaction", "Entity Interaction", Items.LEAD, true),
            new TaskChoice("theseus:item_interaction", "Item Interaction", Items.STICK, true),
            new TaskChoice("theseus:item_use", "Item Use", Items.CARROT_ON_A_STICK, true),
            new TaskChoice("theseus:location", "Location", Items.COMPASS, true),
            new TaskChoice("theseus:recipe", "Recipe", Items.KNOWLEDGE_BOOK, true),
            new TaskChoice("theseus:stat", "Stat", Items.FEATHER, true),
            new TaskChoice("theseus:structure", "Structure", Items.STRUCTURE_BLOCK, true)
        );

        private static final List<RewardChoice> REWARDS = List.of(
            new RewardChoice("theseus:xp", "Experience", Items.EXPERIENCE_BOTTLE),
            new RewardChoice("theseus:item", "Item", Items.CHEST),
            new RewardChoice("theseus:loottable", "Loot Table", Items.CHEST),
            new RewardChoice("theseus:command", "Command", Items.COMMAND_BLOCK),
            new RewardChoice("theseus:selectable", "Selectable Reward", Items.BUNDLE)
        );
    }
}
