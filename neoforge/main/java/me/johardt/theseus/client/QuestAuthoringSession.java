package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestDiagnostics;
import me.johardt.theseus.core.QuestDraft;
import me.johardt.theseus.core.QuestIconDefinition;
import me.johardt.theseus.core.RegistryValidation;

import static me.johardt.theseus.client.QuestDraftValidation.nestedRewards;
import static me.johardt.theseus.client.QuestDraftValidation.nestedTasks;
import static me.johardt.theseus.client.QuestDraftValidation.setNestedRewards;
import static me.johardt.theseus.client.QuestDraftValidation.setNestedTasks;

/**
 * Mutable state for authoring one quest.
 *
 * <p>This is deliberately independent of widgets and rendering. A screen may be
 * rebuilt around a copied session without reassembling the authored document
 * field-by-field.</p>
 */
class QuestAuthoringSession {
    private QuestDraft baseline;
    boolean open;
    boolean editingExisting;
    String originalId;
    int x;
    int y;
    String xText = "0";
    String yText = "0";
    boolean xInvalid;
    boolean yInvalid;
    String id = "";
    String title = "";
    String subtitle = "";
    String body = "";
    String icon = "minecraft:map";
    boolean descriptionTouched;
    boolean iconTouched;
    String background = "theseus:textures/gui/quest_backgrounds/default.png";
    boolean individualProgress;
    QuestDefinition.Visibility hiddenUntil = QuestDefinition.Visibility.LOCKED;
    boolean unlockNotification;
    boolean showDependencyArrow = true;
    boolean repeatable;
    boolean autoClaimRewards;
    JsonObject groups = new JsonObject();
    int iconSize;
    String iconSizeText;
    boolean iconSizeTouched;
    boolean iconSizeInvalid;
    final List<TaskDraft> tasks = new ArrayList<>();
    int editingTaskIndex = -1;
    TaskDraft editingTask;
    TaskDraft editingTaskBaseline;
    private final List<TaskDraft> taskEditorParents = new ArrayList<>();
    private final List<Integer> taskEditorParentIndexes = new ArrayList<>();
    private final List<TaskDraft> taskEditorParentBaselines = new ArrayList<>();
    int nestedTaskScroll;
    String taskEditorError = "";
    int taskDeleteConfirmation = -1;
    final List<RewardDraft> rewards = new ArrayList<>();
    int editingRewardIndex = -1;
    RewardDraft editingReward;
    RewardDraft editingRewardBaseline;
    String rewardEditorError = "";
    int nestedRewardScroll;
    int editingNestedRewardIndex = -1;
    RewardDraft editingNestedReward;
    RewardDraft editingNestedRewardBaseline;

    QuestAuthoringSession(int defaultIconSize) {
        iconSize = defaultIconSize;
        iconSizeText = Integer.toString(defaultIconSize);
    }

    QuestAuthoringSession(QuestAuthoringSession source) {
        this(source.iconSize);
        this.baseline = source.baseline == null ? null : source.baseline.copy();
        this.open = source.open;
        this.editingExisting = source.editingExisting;
        this.originalId = source.originalId;
        this.x = source.x;
        this.y = source.y;
        this.xText = source.xText;
        this.yText = source.yText;
        this.xInvalid = source.xInvalid;
        this.yInvalid = source.yInvalid;
        this.id = source.id;
        this.title = source.title;
        this.subtitle = source.subtitle;
        this.body = source.body;
        this.icon = source.icon;
        this.descriptionTouched = source.descriptionTouched;
        this.iconTouched = source.iconTouched;
        this.background = source.background;
        this.individualProgress = source.individualProgress;
        this.hiddenUntil = source.hiddenUntil;
        this.unlockNotification = source.unlockNotification;
        this.showDependencyArrow = source.showDependencyArrow;
        this.repeatable = source.repeatable;
        this.autoClaimRewards = source.autoClaimRewards;
        this.groups = source.groups.deepCopy();
        this.iconSize = source.iconSize;
        this.iconSizeText = source.iconSizeText;
        this.iconSizeTouched = source.iconSizeTouched;
        this.iconSizeInvalid = source.iconSizeInvalid;
        source.tasks.forEach(task -> this.tasks.add(task.copy()));
        this.editingTaskIndex = source.editingTaskIndex;
        this.editingTask = source.editingTask == null ? null : source.editingTask.copy();
        this.editingTaskBaseline = source.editingTaskBaseline == null ? null : source.editingTaskBaseline.copy();
        source.taskEditorParents.forEach(parent -> this.taskEditorParents.add(parent.copy()));
        this.taskEditorParentIndexes.addAll(source.taskEditorParentIndexes);
        source.taskEditorParentBaselines.forEach(parent -> this.taskEditorParentBaselines.add(parent.copy()));
        this.nestedTaskScroll = source.nestedTaskScroll;
        this.taskEditorError = source.taskEditorError;
        this.taskDeleteConfirmation = source.taskDeleteConfirmation;
        source.rewards.forEach(reward -> this.rewards.add(reward.copy()));
        this.editingRewardIndex = source.editingRewardIndex;
        this.editingReward = source.editingReward == null ? null : source.editingReward.copy();
        this.editingRewardBaseline = source.editingRewardBaseline == null ? null : source.editingRewardBaseline.copy();
        this.rewardEditorError = source.rewardEditorError;
        this.nestedRewardScroll = source.nestedRewardScroll;
        this.editingNestedRewardIndex = source.editingNestedRewardIndex;
        this.editingNestedReward = source.editingNestedReward == null ? null : source.editingNestedReward.copy();
        this.editingNestedRewardBaseline = source.editingNestedRewardBaseline == null
            ? null
            : source.editingNestedRewardBaseline.copy();
    }

    QuestAuthoringSession copy() {
        return new QuestAuthoringSession(this);
    }

    void begin(QuestDraft draft) {
        baseline = draft.copy();
        open = true;
    }

    void beginExisting(QuestDefinition definition, JsonObject snapshot, String group) {
        editingExisting = true;
        originalId = definition.id();
        id = definition.id();
        title = definition.title();
        subtitle = definition.subtitle();
        body = String.join("\n", definition.description());
        icon = definition.display().icon().item();
        iconSize = definition.display().iconSize();
        iconSizeText = Integer.toString(iconSize);
        iconSizeTouched = false;
        iconSizeInvalid = false;
        descriptionTouched = false;
        iconTouched = false;
        background = definition.display().iconBackground();
        individualProgress = definition.settings().individualProgress();
        hiddenUntil = definition.settings().hiddenUntil();
        unlockNotification = definition.settings().unlockNotification();
        showDependencyArrow = definition.settings().showDependencyArrow();
        repeatable = definition.settings().repeatable();
        autoClaimRewards = definition.settings().autoClaimRewards();
        groups = new JsonObject();
        definition.display().groups().forEach((name, position) -> {
            JsonObject placement = new JsonObject();
            com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
            coordinates.add(position.x());
            coordinates.add(position.y());
            placement.add("position", coordinates);
            groups.add(name, placement);
        });
        QuestDefinition.GroupDisplay position = definition.position(group);
        x = position.x();
        y = position.y();
        xText = Integer.toString(x);
        yText = Integer.toString(y);
        xInvalid = false;
        yInvalid = false;
        tasks.clear();
        definition.tasks().values().forEach(task -> tasks.add(new TaskDraft(task.id(), task.type(), task.source().deepCopy())));
        rewards.clear();
        definition.rewards().values().forEach(reward -> rewards.add(new RewardDraft(reward.id(), reward.type(), reward.source().deepCopy())));
        resetEditors();
        begin(QuestDraft.fromClientSnapshot(definition.id(), snapshot));
        acceptCurrentAsBaseline();
    }

    void beginNew(String group, int x, int y) {
        id = "";
        title = "";
        subtitle = "";
        body = "";
        icon = "minecraft:map";
        iconSize = QuestSurfaceLayout.DEFAULT_ICON_SIZE;
        iconSizeText = Integer.toString(iconSize);
        iconSizeTouched = false;
        iconSizeInvalid = false;
        descriptionTouched = false;
        iconTouched = false;
        background = "theseus:textures/gui/quest_backgrounds/default.png";
        individualProgress = false;
        hiddenUntil = QuestDefinition.Visibility.LOCKED;
        unlockNotification = false;
        showDependencyArrow = true;
        repeatable = false;
        autoClaimRewards = false;
        tasks.clear();
        rewards.clear();
        editingExisting = false;
        originalId = null;
        groups = new JsonObject();
        this.x = x;
        this.y = y;
        xText = Integer.toString(x);
        yText = Integer.toString(y);
        xInvalid = false;
        yInvalid = false;
        resetEditors();
        baseline = null;
        setGroupPosition(group, x, y);
        begin(QuestDraft.create(null));
    }

    private void acceptCurrentAsBaseline() {
        QuestDraft initial = draft();
        initial.accept();
        begin(initial);
    }

    private void resetEditors() {
        editingTask = null;
        editingTaskBaseline = null;
        editingTaskIndex = -1;
        taskEditorParents.clear();
        taskEditorParentIndexes.clear();
        taskEditorParentBaselines.clear();
        taskEditorError = "";
        editingReward = null;
        editingRewardBaseline = null;
        editingRewardIndex = -1;
        editingNestedReward = null;
        editingNestedRewardBaseline = null;
        editingNestedRewardIndex = -1;
        rewardEditorError = "";
    }

    void discard() {
        open = false;
        editingExisting = false;
        originalId = null;
        baseline = null;
        resetEditors();
    }

    boolean hasBaseline() {
        return baseline != null;
    }

    void setGroupPosition(String group, int x, int y) {
        JsonObject placement = groups.has(group) && groups.get(group).isJsonObject()
            ? groups.getAsJsonObject(group) : new JsonObject();
        com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
        coordinates.add(x);
        coordinates.add(y);
        placement.add("position", coordinates);
        groups.add(group, placement);
        if (baseline != null) baseline.setGroupPosition(group, x, y);
    }

    void editTask(int index) {
        editingTaskIndex = index;
        editingTask = tasks.get(index).copy();
        editingTaskBaseline = editingTask.copy();
        taskEditorParents.clear();
        taskEditorParentIndexes.clear();
        taskEditorParentBaselines.clear();
        taskEditorError = "";
    }

    void createTask(TaskDraft task) {
        editingTaskIndex = -1;
        editingTask = task;
        editingTaskBaseline = task.copy();
        taskEditorParents.clear();
        taskEditorParentIndexes.clear();
        taskEditorParentBaselines.clear();
        taskEditorError = "";
    }

    void editChildTask(int index) {
        List<TaskDraft> children = nestedTasks(editingTask);
        taskEditorParents.add(editingTask);
        taskEditorParentIndexes.add(editingTaskIndex);
        taskEditorParentBaselines.add(editingTaskBaseline);
        editingTask = children.get(index).copy();
        editingTaskBaseline = editingTask.copy();
        editingTaskIndex = index;
        taskEditorError = "";
    }

    void createChildTask(TaskDraft task) {
        taskEditorParents.add(editingTask);
        taskEditorParentIndexes.add(editingTaskIndex);
        taskEditorParentBaselines.add(editingTaskBaseline);
        editingTask = task;
        editingTaskBaseline = task.copy();
        editingTaskIndex = -1;
        taskEditorError = "";
    }

    String taskBreadcrumbs() {
        if (taskEditorParents.isEmpty()) return editingTask.id;
        return taskEditorParents.stream().map(parent -> parent.id)
            .collect(java.util.stream.Collectors.joining(" › ")) + " › " + editingTask.id;
    }

    void moveNestedTask(int index, int direction) {
        List<TaskDraft> children = nestedTasks(editingTask);
        int target = index + direction;
        if (target < 0 || target >= children.size()) return;
        java.util.Collections.swap(children, index, target);
        setNestedTasks(editingTask, children);
    }

    void removeNestedTask(int index) {
        List<TaskDraft> children = nestedTasks(editingTask);
        children.remove(index);
        setNestedTasks(editingTask, children);
        nestedTaskScroll = Math.min(nestedTaskScroll, Math.max(0, children.size() - 4));
    }

    void removeTask(int index) {
        if (index >= 0 && index < tasks.size()) tasks.remove(index);
    }

    boolean saveTask(QuestDraftValidation.RegistryLookup registries) {
        List<TaskDraft> peers = taskEditorParents.isEmpty() ? tasks : nestedTasks(taskEditorParents.getLast());
        taskEditorError = QuestDraftValidation.validateTaskDraft(editingTask, peers, editingTaskIndex, registries);
        if (!taskEditorError.isEmpty()) return false;
        if (taskEditorParents.isEmpty()) {
            if (editingTaskIndex < 0) tasks.add(editingTask.copy());
            else tasks.set(editingTaskIndex, editingTask.copy());
        } else {
            TaskDraft parent = taskEditorParents.getLast();
            List<TaskDraft> children = nestedTasks(parent);
            if (editingTaskIndex < 0) children.add(editingTask.copy());
            else children.set(editingTaskIndex, editingTask.copy());
            setNestedTasks(parent, children);
        }
        editingTaskBaseline = editingTask.copy();
        return true;
    }

    String validateTask(int index, QuestDraftValidation.RegistryLookup registries) {
        return QuestDraftValidation.validateTaskDraft(tasks.get(index).copy(), tasks, index, registries);
    }

    /** Returns whether an open parent task remains after closing this editor. */
    boolean closeTaskEditor() {
        boolean hasParent = !taskEditorParents.isEmpty();
        if (hasParent) {
            editingTask = taskEditorParents.removeLast();
            editingTaskIndex = taskEditorParentIndexes.removeLast();
            editingTaskBaseline = taskEditorParentBaselines.removeLast();
        } else {
            editingTask = null;
            editingTaskBaseline = null;
            editingTaskIndex = -1;
        }
        taskEditorError = "";
        return hasParent;
    }

    void editReward(int index) {
        editingRewardIndex = index;
        editingReward = rewards.get(index).copy();
        editingRewardBaseline = editingReward.copy();
        rewardEditorError = "";
    }

    void createReward(RewardDraft reward) {
        editingRewardIndex = -1;
        editingReward = reward;
        editingRewardBaseline = reward.copy();
        rewardEditorError = "";
    }

    void editNestedReward(int index) {
        editingNestedRewardIndex = index;
        editingNestedReward = nestedRewards(editingReward).get(index).copy();
        editingNestedRewardBaseline = editingNestedReward.copy();
        rewardEditorError = "";
    }

    void createNestedReward(RewardDraft reward) {
        editingNestedRewardIndex = -1;
        editingNestedReward = reward;
        editingNestedRewardBaseline = reward.copy();
        rewardEditorError = "";
    }

    void moveNestedReward(int index, int direction) {
        List<RewardDraft> children = nestedRewards(editingReward);
        int target = index + direction;
        if (target < 0 || target >= children.size()) return;
        java.util.Collections.swap(children, index, target);
        setNestedRewards(editingReward, children);
    }

    void removeNestedReward(int index) {
        List<RewardDraft> children = nestedRewards(editingReward);
        children.remove(index);
        setNestedRewards(editingReward, children);
        nestedRewardScroll = Math.min(nestedRewardScroll, Math.max(0, children.size() - 4));
    }

    void removeReward(int index) {
        rewards.remove(index);
    }

    boolean saveReward(boolean nested, QuestDraftValidation.RegistryLookup registries) {
        RewardDraft reward = nested ? editingNestedReward : editingReward;
        List<RewardDraft> peers = nested ? nestedRewards(editingReward) : rewards;
        int index = nested ? editingNestedRewardIndex : editingRewardIndex;
        rewardEditorError = QuestDraftValidation.validateRewardDraft(reward, peers, index, nested, registries);
        if (!rewardEditorError.isEmpty()) return false;
        if (nested) {
            if (index < 0) peers.add(reward.copy());
            else peers.set(index, reward.copy());
            setNestedRewards(editingReward, peers);
        } else if (index < 0) rewards.add(reward.copy());
        else rewards.set(index, reward.copy());
        if (nested) editingNestedRewardBaseline = reward.copy();
        else editingRewardBaseline = reward.copy();
        return true;
    }

    void closeRewardEditor(boolean nested) {
        rewardEditorError = "";
        if (nested) {
            editingNestedReward = null;
            editingNestedRewardBaseline = null;
            editingNestedRewardIndex = -1;
        } else {
            editingReward = null;
            editingRewardBaseline = null;
            editingRewardIndex = -1;
        }
    }

    boolean hasUnsavedEditorChanges() {
        if (editingTask != null) {
            return editingTaskBaseline == null || !editingTask.sameAs(editingTaskBaseline);
        }
        if (editingNestedReward != null) {
            return editingNestedRewardBaseline == null
                || !editingNestedReward.sameAs(editingNestedRewardBaseline);
        }
        if (editingReward != null) {
            return editingRewardBaseline == null || !editingReward.sameAs(editingRewardBaseline);
        }
        return false;
    }

    QuestDraft draft() {
        QuestDraft result = baseline == null ? QuestDraft.create(null) : baseline.copy();
        if (id != null && !id.isBlank()) result.rename(id);
        result.setDisplayBasics(title, subtitle, background, groups);
        if (descriptionTouched) result.setDescription(body);
        if (iconTouched) result.setIcon(QuestIconDefinition.item(icon).source());
        if (iconSizeTouched && !iconSizeInvalid) result.setIconSize(iconSize);
        result.setSettings(
            individualProgress,
            hiddenUntil,
            unlockNotification,
            showDependencyArrow,
            repeatable,
            autoClaimRewards
        );
        JsonObject taskDocument = new JsonObject();
        tasks.forEach(task -> taskDocument.add(task.id, task.source.deepCopy()));
        result.replaceTasks(taskDocument);
        JsonObject rewardDocument = new JsonObject();
        rewards.forEach(reward -> rewardDocument.add(reward.id, reward.source.deepCopy()));
        result.replaceRewards(rewardDocument);
        return result;
    }

    String validationError(
        Predicate<String> validItem,
        RegistryValidation.Resolver validRegistryTarget
    ) {
        if (!id.matches("[a-z0-9_.-]+")) return "Quest ID may only contain lowercase letters, numbers, ., _, and -.";
        if (title.trim().isEmpty()) return "Quest title is required.";
        if (xInvalid) return "Position X must be a valid integer.";
        if (yInvalid) return "Position Y must be a valid integer.";
        if (iconSizeInvalid) return "Icon size must be an integer from 8 to 64.";
        Set<String> taskIds = new HashSet<>();
        for (TaskDraft task : tasks) {
            if (task.id == null || !task.id.matches("[a-z0-9_.-]+")) return "Task IDs may only contain lowercase letters, numbers, ., _, and -.";
            if (!taskIds.add(task.id)) return "Duplicate task ID: " + task.id;
        }
        Set<String> rewardIds = new HashSet<>();
        for (RewardDraft reward : rewards) {
            if (reward.id == null || !reward.id.matches("[a-z0-9_.-]+")) return "Reward IDs may only contain lowercase letters, numbers, ., _, and -.";
            if (!rewardIds.add(reward.id)) return "Duplicate reward ID: " + reward.id;
        }
        return draft().diagnostics(validItem, validRegistryTarget).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(diagnostic -> diagnostic.path() + ": " + diagnostic.message())
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    static final class TaskDraft {
        String id;
        final String type;
        final JsonObject source;

        TaskDraft(String id, String type, JsonObject source) {
            this.id = id;
            this.type = type;
            this.source = source;
        }

        TaskDraft copy() { return new TaskDraft(id, type, source.deepCopy()); }
        boolean sameAs(TaskDraft other) {
            return other != null && java.util.Objects.equals(id, other.id)
                && type.equals(other.type) && source.equals(other.source);
        }
    }

    static final class RewardDraft {
        String id;
        final String type;
        final JsonObject source;

        RewardDraft(String id, String type, JsonObject source) {
            this.id = id;
            this.type = type;
            this.source = source;
        }

        RewardDraft copy() { return new RewardDraft(id, type, source.deepCopy()); }
        boolean sameAs(RewardDraft other) {
            return other != null && java.util.Objects.equals(id, other.id)
                && type.equals(other.type) && source.equals(other.source);
        }
    }
}
