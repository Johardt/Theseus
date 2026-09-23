package me.johardt.theseus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestIconDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Locale;
import java.util.Optional;

/** Converts loader-neutral quest data into compact, player-facing client content. */
final class QuestPresentation {
    private QuestPresentation() {}

    static Component status(boolean unlocked, boolean claimed, boolean complete) {
        if (!unlocked) return Component.translatable("quest.theseus.locked");
        if (claimed) return Component.translatable("quest.theseus.completed_claimed");
        if (complete) return Component.translatable("quest.theseus.completed");
        return Component.translatable("quest.theseus.in_progress");
    }

    static int nodeStateColor(boolean unlocked, boolean claimed, boolean complete) {
        if (!unlocked) return 0xFF737B87;
        if (claimed) return 0xFF55D86A;
        if (complete) return 0xFFFFD966;
        return 0xFF4C9AFF;
    }

    static Component visibilityLabel(QuestDefinition.Visibility visibility) {
        return switch (visibility) {
            case NEVER -> Component.translatable("gui.theseus.editor.visibility.never");
            case LOCKED -> Component.translatable("quest.theseus.locked");
            case DEPENDENCIES_VISIBLE -> Component.translatable("quest.theseus.dependencies_visible");
            case IN_PROGRESS -> Component.translatable("quest.theseus.in_progress");
            case COMPLETED -> Component.translatable("quest.theseus.completed");
        };
    }

    static ItemStack questIcon(QuestDefinition quest) {
        return QuestIconRegistry.itemStack(quest.display().icon()).orElseGet(() -> new ItemStack(Items.MAP));
    }

    static Optional<ItemStack> questIconTarget(QuestDefinition quest) {
        return QuestIconRegistry.itemStack(quest.display().icon());
    }

    static boolean renderQuestIcon(GuiGraphicsExtractor graphics, QuestDefinition quest, int x, int y) {
        return renderQuestIcon(graphics, quest, x, y, quest.display().iconSize());
    }

    static boolean renderQuestIcon(
        GuiGraphicsExtractor graphics,
        QuestDefinition quest,
        int x,
        int y,
        int size
    ) {
        return QuestIconRegistry.render(
            graphics,
            quest.display().icon(),
            new ItemStack(Items.BARRIER),
            x,
            y,
            QuestSurfaceLayout.clampIconSize(size)
        );
    }

    static boolean renderTaskIcon(GuiGraphicsExtractor graphics, QuestDefinition.Task task, int x, int y) {
        ItemStack fallback = defaultTaskIcon(task);
        if (!task.source().has("icon")) {
            graphics.item(fallback, x, y);
            return true;
        }
        return QuestIconRegistry.render(
            graphics, QuestIconDefinition.parse(task.source().get("icon"), itemId(fallback)),
            new ItemStack(Items.BARRIER), x, y, 16
        );
    }

    static boolean renderRewardIcon(GuiGraphicsExtractor graphics, QuestDefinition.Reward reward, int x, int y) {
        ItemStack fallback = defaultRewardIcon(reward);
        if (!reward.source().has("icon")) {
            graphics.item(fallback, x, y);
            return true;
        }
        return QuestIconRegistry.render(
            graphics, QuestIconDefinition.parse(reward.source().get("icon"), itemId(fallback)),
            new ItemStack(Items.BARRIER), x, y, 16
        );
    }

    static ItemStack taskIcon(QuestDefinition.Task task) {
        JsonElement icon = task.source().get("icon");
        if (isItemIcon(icon)) return item(icon, Items.PAPER);
        return defaultTaskIcon(task);
    }

    static Optional<ItemStack> taskIconTarget(QuestDefinition.Task task) {
        JsonElement icon = task.source().get("icon");
        if (task.source().has("icon")) {
            if (!isItemIcon(icon)) return Optional.empty();
            return QuestIconRegistry.itemStack(
                QuestIconDefinition.parse(icon, itemId(defaultTaskIcon(task)))
            );
        }
        return switch (task.kind()) {
            case ITEM, ITEM_INTERACTION, ITEM_USE -> resolveItem(task.source().get("item"));
            default -> Optional.empty();
        };
    }

    private static ItemStack defaultTaskIcon(QuestDefinition.Task task) {
        return switch (task.kind()) {
            case ITEM, ITEM_INTERACTION, ITEM_USE -> item(task.source().get("item"), Items.PAPER);
            case BLOCK_INTERACTION -> blockItem(task.source().get("block"), Items.STONE_BUTTON);
            case KILL_ENTITY, ENTITY_INTERACTION -> entityItem(task.source().get("entity"), Items.IRON_SWORD);
            case ADVANCEMENT -> new ItemStack(Items.WRITABLE_BOOK);
            case RECIPE -> new ItemStack(Items.KNOWLEDGE_BOOK);
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case STAT -> new ItemStack(Items.FEATHER);
            case STRUCTURE, LOCATION -> new ItemStack(Items.COMPASS);
            case CHANGED_DIMENSION -> new ItemStack(Items.ENDER_PEARL);
            case BIOME -> new ItemStack(Items.GRASS_BLOCK);
            case CHECK -> new ItemStack(Items.EMERALD);
            case COMPOSITE -> new ItemStack(Items.BUNDLE);
            case UNSUPPORTED -> new ItemStack(Items.BARRIER);
            default -> new ItemStack(Items.PAPER);
        };
    }

    static ItemStack rewardIcon(QuestDefinition.Reward reward) {
        JsonElement icon = reward.source().get("icon");
        if (isItemIcon(icon)) return item(icon, Items.CHEST);
        return defaultRewardIcon(reward);
    }

    static Optional<ItemStack> rewardIconTarget(QuestDefinition.Reward reward) {
        JsonElement icon = reward.source().get("icon");
        if (reward.source().has("icon")) {
            if (!isItemIcon(icon)) return Optional.empty();
            return QuestIconRegistry.itemStack(
                QuestIconDefinition.parse(icon, itemId(defaultRewardIcon(reward)))
            );
        }
        return reward.kind() == QuestDefinition.RewardKind.ITEM
            ? resolveItem(reward.source().get("item"))
            : Optional.empty();
    }

    private static ItemStack defaultRewardIcon(QuestDefinition.Reward reward) {
        return switch (reward.kind()) {
            case ITEM -> item(reward.source().get("item"), Items.CHEST);
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case LOOT_TABLE, SELECTABLE -> new ItemStack(Items.CHEST);
            case COMMAND -> new ItemStack(Items.COMMAND_BLOCK);
            case UNSUPPORTED -> new ItemStack(Items.BARRIER);
        };
    }

    static String taskTitle(QuestDefinition.Task task) {
        if (!task.title().equals(task.id())) return task.title();
        return switch (task.kind()) {
            case ITEM -> "Collect " + displayValue(task.source().get("item"), task.value());
            case KILL_ENTITY -> "Defeat " + displayValue(task.source().get("entity"), task.value());
            case BLOCK_INTERACTION -> "Interact with " + displayValue(task.source().get("block"), task.value());
            case ENTITY_INTERACTION -> "Interact with " + displayValue(task.source().get("entity"), task.value());
            case ITEM_INTERACTION, ITEM_USE -> "Use " + displayValue(task.source().get("item"), task.value());
            case ADVANCEMENT -> "Complete an advancement";
            case RECIPE -> "Unlock a recipe";
            case XP -> "Gather experience";
            case STAT -> "Increase " + friendly(task.value());
            case STRUCTURE -> "Visit a structure";
            case LOCATION -> "Reach the location";
            case CHANGED_DIMENSION -> "Travel between dimensions";
            case BIOME -> "Visit " + displayValue(task.source().get("biomes"), task.value());
            case CHECK -> "Complete the check";
            case COMPOSITE -> "Complete " + task.target() + " combined task" + plural(task.target());
            default -> task.id();
        };
    }

    static String taskDescription(QuestDefinition.Task task) {
        return switch (task.kind()) {
            case ITEM -> "Collect " + task.target() + " matching item" + plural(task.target());
            case KILL_ENTITY -> "Defeat " + task.target() + " matching entit" + (task.target() == 1 ? "y" : "ies");
            case XP -> "Reach or submit " + task.target() + " experience " + suffix(string(task.source(), "xpType", "levels"));
            case STAT -> "Reach a value of " + task.target();
            case RECIPE -> "Discover one of the configured recipes";
            case STRUCTURE -> "Enter the configured structure";
            case LOCATION -> "Enter the configured area";
            case ADVANCEMENT -> "Complete one of the configured advancements";
            case BIOME -> "Enter the configured biome";
            case CHANGED_DIMENSION -> "Travel through the configured dimensions";
            case CHECK -> "Submit this task when its conditions are met";
            case COMPOSITE -> "Complete enough of the nested tasks";
            case BLOCK_INTERACTION, ENTITY_INTERACTION, ITEM_INTERACTION, ITEM_USE -> "Perform the configured interaction";
            default -> task.kind() == QuestDefinition.TaskKind.UNSUPPORTED ? "Unsupported task type: " + task.type() : "Complete this task";
        };
    }

    static String rewardTitle(QuestDefinition.Reward reward) {
        if (!reward.title().equals(reward.id())) return reward.title();
        return switch (reward.kind()) {
            case ITEM -> friendly(reward.value());
            case XP -> "Experience";
            case LOOT_TABLE -> "Loot table reward";
            case COMMAND -> "Command reward";
            case SELECTABLE -> "Choose rewards";
            case UNSUPPORTED -> "Unsupported reward";
        };
    }

    private static ItemStack item(JsonElement element, Item fallback) {
        return resolveItem(element).orElseGet(() -> new ItemStack(fallback));
    }

    private static Optional<ItemStack> resolveItem(JsonElement element) {
        return QuestItemStackResolver.resolve(element);
    }

    private static ItemStack blockItem(JsonElement element, Item fallback) {
        if (element == null || !element.isJsonPrimitive() || element.getAsString().startsWith("#")) return new ItemStack(fallback);
        try {
            Item item = BuiltInRegistries.BLOCK.getValue(Identifier.parse(element.getAsString())).asItem();
            return item == Items.AIR ? new ItemStack(fallback) : new ItemStack(item);
        } catch (Exception ignored) {
            return new ItemStack(fallback);
        }
    }

    private static ItemStack entityItem(JsonElement element, Item fallback) {
        if (element == null) return new ItemStack(fallback);
        if (element.isJsonObject()) element = element.getAsJsonObject().get("type");
        if (element == null || !element.isJsonPrimitive() || element.getAsString().startsWith("#")) return new ItemStack(fallback);
        try {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(element.getAsString()));
            return SpawnEggItem.byId(type).map(holder -> new ItemStack(holder.value())).orElseGet(() -> new ItemStack(fallback));
        } catch (Exception ignored) {
            return new ItemStack(fallback);
        }
    }

    private static String displayValue(JsonElement element, String fallback) {
        if (element == null) return friendly(fallback);
        if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) return displayValue(element.getAsJsonArray().get(0), fallback);
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("tag")) return "#" + friendly(object.get("tag").getAsString());
            if (object.has("id")) return friendly(object.get("id").getAsString());
            if (object.has("type")) return displayValue(object.get("type"), fallback);
        }
        return element.isJsonPrimitive() ? friendly(element.getAsString()) : friendly(fallback);
    }

    private static String friendly(String value) {
        if (value == null || value.isBlank()) return "configured target";
        String path = value.startsWith("#") ? value.substring(1) : value;
        int separator = path.indexOf(':');
        if (separator >= 0) path = path.substring(separator + 1);
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        if (path.isBlank()) return "configured target";
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }

    private static String plural(int amount) {
        return amount == 1 ? "" : "s";
    }

    private static String suffix(String value) {
        int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
        return value.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static String itemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "minecraft:barrier" : id.toString();
    }

    private static boolean isItemIcon(JsonElement icon) {
        if (icon == null || icon.isJsonNull()) return false;
        if (icon.isJsonPrimitive()) return icon.getAsJsonPrimitive().isString();
        return icon.isJsonObject() && icon.getAsJsonObject().has("item");
    }
}
