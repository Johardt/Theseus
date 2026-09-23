package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.TagValueOutput;

import static me.johardt.theseus.core.QuestRuntime.*;

/** Tracks player task progress and reward claims. */
final class QuestRuntimeProgression {
    private final QuestRuntime runtime;

    QuestRuntimeProgression(QuestRuntime runtime) {
        this.runtime = runtime;
    }

    void initialize(ServerPlayer player) {
        runtime.suppressNotifications.add(runtime.world.playerId(player));
        try {
            updateInventoryTasks(player);
            for (QuestDefinition quest : runtime.catalog.quests().values()) {
                if (!isUnlocked(player, quest)) continue;
                for (QuestDefinition.Task task : flattenTasks(quest.tasks())) {
                    if (
                        task.kind() != QuestDefinition.TaskKind.ADVANCEMENT
                    ) continue;
                    for (String advancement : configuredStrings(
                        task,
                        "advancements",
                        task.value()
                    )) {
                        if (runtime.world.advancementGranted(player, advancement)) {
                            signal(
                                player,
                                new TaskEngine.Signal.AdvancementGranted(
                                    advancement
                                )
                            );
                        }
                    }
                }
            }
            updatePassiveTasks(player);
        } finally {
            runtime.suppressNotifications.remove(runtime.world.playerId(player));
        }
    }

    boolean triggerDummy(ServerPlayer player, String value) {
        return signal(player, new TaskEngine.Signal.Manual(value));
    }

    String lockedDummyReason(ServerPlayer player, String value) {
        for (QuestDefinition quest : runtime.catalog.quests().values()) {
            boolean matches = flattenTasks(quest.tasks())
                .stream()
                .anyMatch(
                    task ->
                        task.kind() == QuestDefinition.TaskKind.DUMMY &&
                        task.value().equals(value)
                );
            if (!matches || isUnlocked(player, quest)) continue;
            String dependencies = quest
                .dependencies()
                .stream()
                .filter(dependency -> {
                    QuestDefinition required = runtime.catalog.quests().get(dependency);
                    return required == null || !isComplete(player, required);
                })
                .map(dependency -> {
                    QuestDefinition required = runtime.catalog.quests().get(dependency);
                    if (required == null) return (
                        "Unknown chapter › " + dependency
                    );
                    String chapter = required
                        .display()
                        .groups()
                        .keySet()
                        .stream()
                        .sorted()
                        .findFirst()
                        .orElse("Main");
                    return chapter + " › " + required.display().title();
                })
                .collect(Collectors.joining(", "));
            return (
                "Task '" +
                value +
                "' is locked by: " +
                (dependencies.isBlank() ? "quest dependencies" : dependencies) +
                "."
            );
        }
        return null;
    }

    void updateInventoryTasks(ServerPlayer player) {
        TaskEngine.Signal.Inventory inventory = inventory(player, false);
        signal(player, inventory);
        signal(player, playerState(player));
        updatePassiveTasks(player);
    }

    boolean signal(ServerPlayer player, TaskEngine.Signal signal) {
        Map<String, Boolean> wasUnlocked = runtime.questStates(player, false);
        Map<String, Boolean> wasComplete = runtime.questStates(player, true);
        boolean changed = false;
        for (QuestDefinition quest : runtime.catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                changed |= applyTask(player, quest, task, signal);
            }
        }
        if (changed) runtime.changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    boolean submit(ServerPlayer player, String questId, String taskId) {
        Map<String, Boolean> wasUnlocked = runtime.questStates(player, false);
        Map<String, Boolean> wasComplete = runtime.questStates(player, true);
        QuestDefinition quest = runtime.catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestDefinition.Task task = resolveTask(quest.tasks(), taskId);
        if (task == null) return false;
        TaskEngine.Signal signal;
        if (task.kind() == QuestDefinition.TaskKind.ITEM) {
            signal = inventory(player, true);
        } else if (task.kind() == QuestDefinition.TaskKind.XP) {
            signal = new TaskEngine.Signal.Experience(
                player.experienceLevel,
                player.totalExperience,
                true
            );
        } else if (task.kind() == QuestDefinition.TaskKind.CHECK) {
            signal = new TaskEngine.Signal.Check(playerData(player), true);
        } else {
            return false;
        }
        boolean changed = applyTask(player, quest, task, taskId, signal);
        if (changed) refreshCompositeProgress(player, quest);
        if (changed) runtime.changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    static QuestDefinition.Task resolveTask(
        Map<String, QuestDefinition.Task> tasks,
        String path
    ) {
        if (path == null || path.isBlank()) return null;
        String[] parts = path.split("/", -1);
        Map<String, QuestDefinition.Task> current = tasks;
        QuestDefinition.Task task = null;
        for (String part : parts) {
            if (part.isBlank()) return null;
            task = current.get(part);
            if (task == null) return null;
            current = task.tasks();
        }
        return task;
    }

    void refreshCompositeProgress(
        ServerPlayer player,
        QuestDefinition quest
    ) {
        for (QuestDefinition.Task task : quest.tasks().values()) {
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE
            ) updateCompositeSummary(player, quest, task, task.id());
        }
    }

    boolean updateCompositeSummary(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey
    ) {
        boolean changed = false;
        for (QuestDefinition.Task child : task.tasks().values()) {
            if (child.kind() == QuestDefinition.TaskKind.COMPOSITE) {
                changed |= updateCompositeSummary(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id()
                );
            }
        }
        double total = task
            .tasks()
            .values()
            .stream()
            .mapToDouble(child ->
                taskFraction(
                    player,
                    quest.id(),
                    child,
                    progressKey + "/" + child.id()
                )
            )
            .sum();
        return (
            setTaskProgress(
                player,
                quest,
                task,
                progressKey,
                Math.min(task.target(), (int) Math.floor(total + 0.000001))
            ) || changed
        );
    }

    boolean claim(ServerPlayer player, String questId) {
        return claim(player, questId, Map.of());
    }

    boolean claim(
        ServerPlayer player,
        String questId,
        Map<String, List<String>> selections
    ) {
        QuestDefinition quest = runtime.catalog.quests().get(questId);
        if (quest == null || !isComplete(player, quest) || quest.rewards().isEmpty()) return false;
        QuestProgressState state = runtime.progress(player, questId);
        List<QuestDefinition.Reward> missing = quest.rewards().values().stream()
            .filter(reward -> !state.claimedRewards().contains(reward.id()))
            .toList();
        if (missing.isEmpty()) return false;
        for (QuestDefinition.Reward reward : missing) {
            if (
                !canClaimReward(
                    player,
                    reward,
                    selections.getOrDefault(reward.id(), List.of())
                )
            ) {
                runtime.world.message(
                    player,
                    "Cannot claim unsupported or incomplete reward: " +
                        reward.title()
                );
                return false;
            }
        }
        List<String> granted = new java.util.ArrayList<>();
        for (QuestDefinition.Reward reward : missing) {
            grantReward(
                player,
                reward,
                selections.getOrDefault(reward.id(), List.of()),
                granted
            );
            state.markRewardClaimed(reward.id());
        }
        runtime.changed(player);
        runtime.notify(
            player,
            "reward",
            "Rewards claimed",
            granted.isEmpty() ? quest.title() : String.join(", ", granted)
        );
        return true;
    }

    boolean togglePinned(ServerPlayer player, String questId) {
        QuestDefinition quest = runtime.catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestProgressState state = runtime.progress(player, questId);
        state.setPinned(!state.isPinned());
        runtime.changed(player);
        return true;
    }

    void reset(ServerPlayer player) {
        runtime.progress.remove(runtime.world.playerId(player));
        runtime.changed(player);
    }

    boolean isUnlocked(ServerPlayer player, QuestDefinition quest) {
        return quest
            .dependencies()
            .stream()
            .allMatch(dependency -> {
                QuestDefinition required = runtime.catalog.quests().get(dependency);
                return required != null && isComplete(player, required);
            });
    }

    boolean isComplete(ServerPlayer player, QuestDefinition quest) {
        QuestProgressState progress = runtime.progress(player, quest.id());
        return quest
            .tasks()
            .values()
            .stream()
            .allMatch(
                task ->
                    progress.getTaskProgress(task.id()) >= task.target()
            );
    }

    boolean setTaskProgress(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        int value
    ) {
        QuestProgressState progress = runtime.progress(player, quest.id());
        int previous = progress.getTaskProgress(progressKey);
        if (previous == value) return false;
        progress.setTaskProgress(progressKey, value);
        return true;
    }

    boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        TaskEngine.Signal signal
    ) {
        return applyTask(player, quest, task, task.id(), signal);
    }

    boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        TaskEngine.Signal signal
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            boolean changed = false;
            for (QuestDefinition.Task child : task.tasks().values()) {
                changed |= applyTask(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id(),
                    signal
                );
            }
            double total = task
                .tasks()
                .values()
                .stream()
                .mapToDouble(child ->
                    taskFraction(
                        player,
                        quest.id(),
                        child,
                        progressKey + "/" + child.id()
                    )
                )
                .sum();
            int summarized = Math.min(
                task.target(),
                (int) Math.floor(total + 0.000001)
            );
            changed |= setTaskProgress(
                player,
                quest,
                task,
                progressKey,
                summarized
            );
            return changed;
        }
        int current = runtime.progress(player, quest.id()).getTaskProgress(progressKey);
        TaskEngine.Result result = runtime.taskEngine.apply(task, current, signal);
        if (result.consumeAmount() > 0) consume(
            player,
            task,
            result.consumeAmount()
        );
        return setTaskProgress(
            player,
            quest,
            task,
            progressKey,
            result.progress()
        );
    }

    double taskFraction(
        ServerPlayer player,
        String questId,
        QuestDefinition.Task task,
        String progressKey
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            double total = task
                .tasks()
                .values()
                .stream()
                .mapToDouble(child ->
                    taskFraction(
                        player,
                        questId,
                        child,
                        progressKey + "/" + child.id()
                    )
                )
                .sum();
            return Math.min(1, total / Math.max(1, task.target()));
        }
        return Math.min(
            1,
            runtime.progress(player, questId).getTaskProgress(progressKey) /
                (double) Math.max(1, task.target())
        );
    }

    void updatePassiveTasks(ServerPlayer player) {
        for (QuestDefinition quest : runtime.catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : flattenTasks(quest.tasks())) {
                if (task.kind() == QuestDefinition.TaskKind.RECIPE) {
                    for (String recipe : configuredStrings(
                        task,
                        "recipes",
                        task.value()
                    )) {
                        ResourceKey<Recipe<?>> key = ResourceKey.create(
                            Registries.RECIPE,
                            net.minecraft.resources.Identifier.parse(recipe)
                        );
                        if (player.getRecipeBook().contains(key)) signal(
                            player,
                            new TaskEngine.Signal.RecipeUnlocked(recipe)
                        );
                    }
                } else if (
                    task.kind() == QuestDefinition.TaskKind.STAT &&
                    !task.value().isBlank()
                ) {
                    var id = net.minecraft.resources.Identifier.parse(
                        task.value()
                    );
                    signal(
                        player,
                        new TaskEngine.Signal.Statistic(
                            task.value(),
                            player.getStats().getValue(Stats.CUSTOM, id)
                        )
                    );
                }
            }
        }
        Set<TaskEngine.Signal.RegistryEntry> structures = structuresAt(player);
        if (!structures.isEmpty()) signal(
            player,
            new TaskEngine.Signal.Structures(structures)
        );
    }

    static void consume(
        ServerPlayer player,
        QuestDefinition.Task task,
        int amount
    ) {
        if (task.kind() == QuestDefinition.TaskKind.XP) {
            String unit = task.source().has("xpType")
                ? task
                      .source()
                      .get("xpType")
                      .getAsString()
                      .toLowerCase(java.util.Locale.ROOT)
                : "level";
            if (unit.endsWith("points")) player.giveExperiencePoints(-amount);
            else player.giveExperienceLevels(-amount);
            return;
        }
        if (task.kind() != QuestDefinition.TaskKind.ITEM) return;
        int remaining = amount;
        for (
            int slot = 0;
            slot < player.getInventory().getContainerSize() && remaining > 0;
            slot++
        ) {
            ItemStack stack = player.getInventory().getItem(slot);
            TaskEngine.Signal.RegistryEntry entry = QuestRuntime.itemEntry(player, stack);
            if (
                !RegistryPredicate.matches(
                    task.source().get("item"),
                    task.value(),
                    entry
                )
            ) continue;
            if (
                !RegistryPredicate.contains(
                    task.source().get("components"),
                    entry.data()
                )
            ) continue;
            if (
                !RegistryPredicate.contains(
                    task.source().get("nbt"),
                    entry.data()
                )
            ) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    boolean canClaimReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected
    ) {
        return switch (reward.kind()) {
            case XP, COMMAND -> true;
            case ITEM -> validIdentifier(reward.value());
            case LOOT_TABLE -> {
                if (!validIdentifier(reward.value())) yield false;
                yield runtime.world.hasLootTable(reward.value());
            }
            case SELECTABLE -> !selected.isEmpty() &&
                selected.size() <= reward.amount() &&
                selected.stream().distinct().count() == selected.size() &&
                selected.stream().allMatch(id -> {
                    QuestDefinition.Reward choice = reward.rewards().get(id);
                    return (
                        choice != null &&
                        choice.kind() !=
                            QuestDefinition.RewardKind.SELECTABLE &&
                        canClaimReward(player, choice, List.of())
                    );
                });
            case UNSUPPORTED -> runtime.rewardEngine.canClaim(reward, player, runtime.world);
        };
    }

    void grantReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected,
        List<String> granted
    ) {
        switch (reward.kind()) {
            case XP -> {
                if (
                    reward
                        .value()
                        .toLowerCase(java.util.Locale.ROOT)
                        .endsWith("points")
                ) runtime.world.grantExperience(player, reward.amount(), true);
                else runtime.world.grantExperience(player, reward.amount(), false);
                granted.add(
                    reward.amount() +
                        (reward
                            .value()
                            .toLowerCase(java.util.Locale.ROOT)
                            .endsWith("points")
                            ? " XP"
                            : " levels")
                );
            }
            case ITEM -> {
                ItemStack stack = new ItemStack(
                    BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.parse(reward.value())
                    ),
                    reward.amount()
                );
                runtime.world.giveItem(player, stack);
                granted.add(
                    stack.getCount() + "× " + stack.getHoverName().getString()
                );
            }
            case COMMAND -> runtime.world.runCommand(player, reward.value());
            case LOOT_TABLE -> {
                runtime.world.generateLoot(player, reward.value(), stack -> {
                    runtime.world.giveItem(player, stack.copy());
                    granted.add(
                        stack.getCount() +
                            "× " +
                            stack.getHoverName().getString()
                    );
                });
            }
            case SELECTABLE -> selected
                .stream()
                .map(reward.rewards()::get)
                .forEach(choice ->
                    grantReward(player, choice, List.of(), granted)
                );
            case UNSUPPORTED -> {
                String detail = runtime.rewardEngine.grant(reward, player, runtime.world);
                if (!detail.isBlank()) granted.add(detail);
            }
        }
    }

    static boolean validIdentifier(String value) {
        try {
            net.minecraft.resources.Identifier.parse(value);
            return !value.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    static TaskEngine.Signal.WorldState playerState(
        ServerPlayer player
    ) {
        var biome = player.level().getBiome(player.blockPosition());
        return new TaskEngine.Signal.WorldState(
            player.level().dimension().identifier().toString(),
            QuestRuntime.registryEntry(biome, new JsonObject(), 1),
            player.getX(),
            player.getY(),
            player.getZ()
        );
    }

    static TaskEngine.Signal.Inventory inventory(
        ServerPlayer player,
        boolean submit
    ) {
        java.util.List<TaskEngine.Signal.RegistryEntry> entries =
            new java.util.ArrayList<>();
        for (
            int slot = 0;
            slot < player.getInventory().getContainerSize();
            slot++
        ) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) entries.add(QuestRuntime.itemEntry(player, stack));
        }
        return new TaskEngine.Signal.Inventory(entries, submit);
    }

    Set<TaskEngine.Signal.RegistryEntry> structuresAt(
        ServerPlayer player
    ) {
        java.util.List<QuestDefinition.Task> tasks = runtime.catalog
            .quests()
            .values()
            .stream()
            .filter(quest -> isUnlocked(player, quest))
            .flatMap(quest -> flattenTasks(quest.tasks()).stream())
            .filter(task -> task.kind() == QuestDefinition.TaskKind.STRUCTURE)
            .toList();
        if (tasks.isEmpty()) return Set.of();
        return runtime.world.structuresAt(player)
            .stream()
            .filter(entry -> tasks.stream().anyMatch(task ->
                RegistryPredicate.matches(
                    task.source().get("structures"),
                    task.value(),
                    entry
                )
            ))
            .collect(Collectors.toSet());
    }

    static JsonObject playerData(ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING,
            player.registryAccess()
        );
        player.saveWithoutId(output);
        com.google.gson.JsonElement json = NbtOps.INSTANCE.convertTo(
            JsonOps.INSTANCE,
            output.buildResult()
        );
        return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
    }

    static java.util.List<String> configuredStrings(
        QuestDefinition.Task task,
        String key,
        String fallback
    ) {
        if (!task.source().has(key)) return fallback.isBlank()
            ? java.util.List.of()
            : java.util.List.of(fallback);
        var value = task.source().get(key);
        if (value.isJsonArray()) return value
            .getAsJsonArray()
            .asList()
            .stream()
            .map(com.google.gson.JsonElement::getAsString)
            .toList();
        return java.util.List.of(value.getAsString());
    }

    static List<QuestDefinition.Task> flattenTasks(
        Map<String, QuestDefinition.Task> tasks
    ) {
        List<QuestDefinition.Task> result = new java.util.ArrayList<>();
        for (QuestDefinition.Task task : tasks.values()) {
            result.add(task);
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE
            ) result.addAll(flattenTasks(task.tasks()));
        }
        return result;
    }
}
