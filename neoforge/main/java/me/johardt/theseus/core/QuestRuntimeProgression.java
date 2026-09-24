package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
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
    interface ItemInventory {
        List<ItemSlot> slots();
    }

    interface ExperienceAccount {
        int levels();

        int points();

        int consume(boolean points, int amount);
    }

    record ItemSlot(TaskEngine.Signal.RegistryEntry entry, IntConsumer shrink) {}

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
        updateExperienceTasks(player);
        updatePassiveTasks(player);
    }

    boolean updateExperienceTasks(ServerPlayer player) {
        return updateExperienceTasks(player, experienceAccount(player));
    }

    boolean updateExperienceTasks(
        ServerPlayer player,
        ExperienceAccount account
    ) {
        if (account == null) return false;
        Map<String, Boolean> wasUnlocked = runtime.questStates(player, false);
        Map<String, Boolean> wasComplete = runtime.questStates(player, true);
        TaskEngine.Signal.Experience automaticSignal = experienceSignal(
            account,
            false
        );
        boolean changed = false;

        for (QuestDefinition quest : runtime.catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                changed |= applyExperienceTasks(
                    player,
                    quest,
                    task,
                    task.id(),
                    account,
                    automaticSignal,
                    true
                );
            }
            changed |= refreshCompositeProgressChanged(player, quest);
        }

        for (QuestDefinition quest : runtime.catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                changed |= applyExperienceTasks(
                    player,
                    quest,
                    task,
                    task.id(),
                    account,
                    null,
                    false
                );
            }
            changed |= refreshCompositeProgressChanged(player, quest);
        }

        if (changed) runtime.changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    boolean signal(ServerPlayer player, TaskEngine.Signal signal) {
        ItemInventory itemInventory = signal instanceof TaskEngine.Signal.Inventory
            ? itemInventory(player)
            : null;
        return signal(player, signal, itemInventory);
    }

    boolean signal(
        ServerPlayer player,
        TaskEngine.Signal signal,
        ItemInventory itemInventory
    ) {
        Map<String, Boolean> wasUnlocked = runtime.questStates(player, false);
        Map<String, Boolean> wasComplete = runtime.questStates(player, true);
        boolean changed = false;
        for (QuestDefinition quest : runtime.catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                changed |= applyTask(
                    player,
                    quest,
                    task,
                    task.id(),
                    signal,
                    itemInventory
                );
            }
        }
        if (changed) runtime.changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    boolean submit(ServerPlayer player, String questId, String taskId) {
        ItemInventory itemInventory = itemInventory(player);
        return submit(player, questId, taskId, itemInventory);
    }

    boolean submit(
        ServerPlayer player,
        String questId,
        String taskId,
        ItemInventory itemInventory
    ) {
        return submit(
            player,
            questId,
            taskId,
            itemInventory,
            player == null ? null : experienceAccount(player)
        );
    }

    boolean submit(
        ServerPlayer player,
        String questId,
        String taskId,
        ItemInventory itemInventory,
        ExperienceAccount experienceAccount
    ) {
        Map<String, Boolean> wasUnlocked = runtime.questStates(player, false);
        Map<String, Boolean> wasComplete = runtime.questStates(player, true);
        QuestDefinition quest = runtime.catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestDefinition.Task task = resolveTask(quest.tasks(), taskId);
        if (task == null) return false;
        TaskEngine.Signal signal;
        if (task.kind() == QuestDefinition.TaskKind.ITEM) {
            signal = new TaskEngine.Signal.Inventory(List.of(), true);
        } else if (task.kind() == QuestDefinition.TaskKind.XP) {
            if (experienceAccount == null) return false;
            signal = experienceSignal(experienceAccount, true);
        } else if (task.kind() == QuestDefinition.TaskKind.CHECK) {
            signal = new TaskEngine.Signal.Check(playerData(player), true);
        } else {
            return false;
        }
        boolean changed = applyTask(
            player,
            quest,
            task,
            taskId,
            signal,
            itemInventory,
            experienceAccount
        );
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
        refreshCompositeProgressChanged(player, quest);
    }

    private boolean refreshCompositeProgressChanged(
        ServerPlayer player,
        QuestDefinition quest
    ) {
        boolean changed = false;
        for (QuestDefinition.Task task : quest.tasks().values()) {
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE
            ) changed |= updateCompositeSummary(player, quest, task, task.id());
        }
        return changed;
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
        var playerId = runtime.world.playerId(player);
        runtime.progress.remove(playerId);
        runtime.deferredProgress.remove(playerId);
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
        ItemInventory itemInventory = signal instanceof TaskEngine.Signal.Inventory
            ? itemInventory(player)
            : null;
        return applyTask(player, quest, task, task.id(), signal, itemInventory);
    }

    boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        TaskEngine.Signal signal
    ) {
        ItemInventory itemInventory = signal instanceof TaskEngine.Signal.Inventory
            ? itemInventory(player)
            : null;
        return applyTask(
            player,
            quest,
            task,
            progressKey,
            signal,
            itemInventory
        );
    }

    boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        TaskEngine.Signal signal,
        ItemInventory itemInventory
    ) {
        return applyTask(
            player,
            quest,
            task,
            progressKey,
            signal,
            itemInventory,
            null
        );
    }

    private boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        TaskEngine.Signal signal,
        ItemInventory itemInventory,
        ExperienceAccount experienceAccount
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            boolean changed = false;
            for (QuestDefinition.Task child : task.tasks().values()) {
                changed |= applyTask(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id(),
                    signal,
                    itemInventory,
                    experienceAccount
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
        TaskEngine.Signal taskSignal = signal;
        if (
            task.kind() == QuestDefinition.TaskKind.ITEM &&
            itemInventory != null &&
            signal instanceof TaskEngine.Signal.Inventory inventorySignal
        ) {
            taskSignal = inventory(itemInventory, inventorySignal.submit());
        }
        TaskEngine.Result result = runtime.taskEngine.apply(task, current, taskSignal);
        int consumed = result.consumeAmount() > 0
            ? task.kind() == QuestDefinition.TaskKind.XP && experienceAccount != null
                ? consumeExperience(
                    experienceAccount,
                    task,
                    result.consumeAmount()
                )
                : consume(player, task, result.consumeAmount(), itemInventory)
            : 0;
        int progress = result.progress();
        if (
            (task.kind() == QuestDefinition.TaskKind.ITEM || task.kind() == QuestDefinition.TaskKind.XP) &&
            result.consumeAmount() > 0
        ) progress = Math.min(progress, current + consumed);
        return setTaskProgress(
            player,
            quest,
            task,
            progressKey,
            progress
        );
    }

    private boolean applyExperienceTasks(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        ExperienceAccount account,
        TaskEngine.Signal.Experience automaticSignal,
        boolean automatic
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            boolean changed = false;
            for (QuestDefinition.Task child : task.tasks().values()) {
                changed |= applyExperienceTasks(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id(),
                    account,
                    automaticSignal,
                    automatic
                );
            }
            return changed;
        }
        if (task.kind() != QuestDefinition.TaskKind.XP) return false;

        String collection = task.source().has("collectionType")
            ? task.source().get("collectionType").getAsString()
            : task.source().has("collection")
                ? task.source().get("collection").getAsString()
                : "consume";
        String mode = suffix(collection);
        if (
            automatic
                ? !mode.equals("automatic")
                : mode.equals("automatic") || mode.equals("manual")
        ) {
            return false;
        }

        int current = runtime.progress(player, quest.id()).getTaskProgress(progressKey);
        TaskEngine.Signal.Experience signal = automatic
            ? automaticSignal
            : experienceSignal(account, false);
        TaskEngine.Result result = runtime.taskEngine.apply(task, current, signal);
        int consumed = result.consumeAmount() > 0
            ? consumeExperience(account, task, result.consumeAmount())
            : 0;
        int progress = result.consumeAmount() > 0
            ? Math.min(result.progress(), current + consumed)
            : result.progress();
        return setTaskProgress(player, quest, task, progressKey, progress);
    }

    static TaskEngine.Signal.Experience experienceSignal(
        ExperienceAccount account,
        boolean submit
    ) {
        return new TaskEngine.Signal.Experience(
            Math.max(0, account.levels()),
            Math.max(0, account.points()),
            submit
        );
    }

    static int spendableExperiencePoints(
        int level,
        float progress,
        int xpNeededForNextLevel
    ) {
        long pointsAtLevel = experiencePointsAtLevel(level);
        float boundedProgress = Float.isFinite(progress)
            ? Math.max(0, Math.min(1, progress))
            : 0;
        long pointsInBar = Math.round(
            (double) boundedProgress * Math.max(0, xpNeededForNextLevel)
        );
        return (int) Math.min(
            Integer.MAX_VALUE,
            pointsAtLevel + pointsInBar
        );
    }

    private static long experiencePointsAtLevel(int level) {
        long boundedLevel = Math.max(0, level);
        double total;
        if (boundedLevel <= 16) {
            total = boundedLevel * boundedLevel + 6 * boundedLevel;
        } else if (boundedLevel <= 31) {
            total = (5 * (double) boundedLevel * boundedLevel - 81 * boundedLevel + 720) / 2;
        } else {
            total = (9 * (double) boundedLevel * boundedLevel - 325 * boundedLevel + 4440) / 2;
        }
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (long) total;
    }

    private static ExperienceAccount experienceAccount(ServerPlayer player) {
        if (player == null) return null;
        return new ExperienceAccount() {
            @Override
            public int levels() {
                return player.experienceLevel;
            }

            @Override
            public int points() {
                return spendableExperiencePoints(
                    player.experienceLevel,
                    player.experienceProgress,
                    player.getXpNeededForNextLevel()
                );
            }

            @Override
            public int consume(boolean points, int amount) {
                if (amount <= 0) return 0;
                int before = points ? points() : levels();
                if (points) player.giveExperiencePoints(-amount);
                else player.giveExperienceLevels(-amount);
                int after = points ? points() : levels();
                return Math.min(amount, Math.max(0, before - after));
            }
        };
    }

    private static boolean usesExperiencePoints(QuestDefinition.Task task) {
        String unit = task.source().has("xpType")
            ? task.source().get("xpType").getAsString()
            : "level";
        return suffix(unit).equals("points");
    }

    private static int consumeExperience(
        ExperienceAccount account,
        QuestDefinition.Task task,
        int amount
    ) {
        return Math.min(
            amount,
            Math.max(0, account.consume(usesExperiencePoints(task), amount))
        );
    }

    private static String suffix(String value) {
        int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
        return value.substring(separator + 1).toLowerCase(java.util.Locale.ROOT);
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

    static int consume(
        ServerPlayer player,
        QuestDefinition.Task task,
        int amount
    ) {
        return consume(player, task, amount, itemInventory(player));
    }

    private static int consume(
        ServerPlayer player,
        QuestDefinition.Task task,
        int amount,
        ItemInventory itemInventory
    ) {
        if (task.kind() == QuestDefinition.TaskKind.XP) {
            ExperienceAccount account = experienceAccount(player);
            return account == null
                ? 0
                : account.consume(usesExperiencePoints(task), amount);
        }
        if (task.kind() == QuestDefinition.TaskKind.ITEM) {
            return consumeItems(itemInventory, task, amount);
        }
        return 0;
    }

    private static int consumeItems(
        ItemInventory itemInventory,
        QuestDefinition.Task task,
        int amount
    ) {
        if (itemInventory == null) {
            throw new IllegalStateException("Item inventory is required to consume item tasks");
        }
        int remaining = amount;
        for (ItemSlot slot : itemInventory.slots()) {
            if (remaining == 0) break;
            TaskEngine.Signal.RegistryEntry entry = slot.entry();
            if (!matchesItem(task, entry)) continue;
            int removed = Math.min(entry.count(), remaining);
            if (removed <= 0) continue;
            slot.shrink().accept(removed);
            remaining -= removed;
        }
        return amount - remaining;
    }

    private static boolean matchesItem(
        QuestDefinition.Task task,
        TaskEngine.Signal.RegistryEntry entry
    ) {
        return RegistryPredicate.matches(
                task.source().get("item"),
                task.value(),
                entry
            ) && RegistryPredicate.contains(
                task.source().get("components"),
                entry.data()
            ) && RegistryPredicate.contains(
                task.source().get("nbt"),
                entry.data()
            );
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
        return inventory(itemInventory(player), submit);
    }

    static TaskEngine.Signal.Inventory inventory(
        ItemInventory itemInventory,
        boolean submit
    ) {
        if (itemInventory == null) {
            return new TaskEngine.Signal.Inventory(List.of(), submit);
        }
        List<TaskEngine.Signal.RegistryEntry> entries = new ArrayList<>();
        for (ItemSlot slot : itemInventory.slots()) {
            if (slot.entry().count() > 0) entries.add(slot.entry());
        }
        return new TaskEngine.Signal.Inventory(entries, submit);
    }

    private static ItemInventory itemInventory(ServerPlayer player) {
        return () -> {
            List<ItemSlot> slots = new ArrayList<>();
            for (
                int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++
            ) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    slots.add(new ItemSlot(
                        QuestRuntime.itemEntry(player, stack),
                        stack::shrink
                    ));
                }
            }
            return slots;
        };
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
