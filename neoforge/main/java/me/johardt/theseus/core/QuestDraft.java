package me.johardt.theseus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The lossless authoring document for one quest.
 *
 * <p>The document is the quest-file shape, not the enriched client sync
 * envelope. Callers can use the small raw JSON operations for extensions, or
 * the typed helpers for the fields owned by the built-in editor. Unknown
 * fields stay in the document and are copied through every operation.</p>
 */
public final class QuestDraft {
    private static final Set<String> CLIENT_RUNTIME_FIELDS = Set.of(
        "progress", "unlocked", "complete", "claimed", "claimed_rewards", "pinned", "issues"
    );
    private static final Set<String> SYNC_METADATA_FIELDS = Set.of("__chapters", "__editor_types");

    private final String originalId;
    private String id;
    private JsonObject baseline;
    private JsonObject document;

    private QuestDraft(String originalId, String id, JsonObject baseline, JsonObject document) {
        this.originalId = blankToNull(originalId);
        this.id = id == null ? "" : id;
        this.baseline = baseline.deepCopy();
        this.document = document.deepCopy();
    }

    public static QuestDraft open(JsonObject rawQuest) {
        return open(null, rawQuest);
    }

    public static QuestDraft open(String id, JsonObject rawQuest) {
        if (rawQuest == null) throw new IllegalArgumentException("Quest document is required");
        return new QuestDraft(id, id, rawQuest, rawQuest);
    }

    /** Opens one quest from the client sync envelope without treating progress as authored data. */
    public static QuestDraft fromClientSnapshot(String id, JsonObject snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Quest sync snapshot is required");
        JsonObject raw = snapshot.deepCopy();
        CLIENT_RUNTIME_FIELDS.forEach(raw::remove);
        SYNC_METADATA_FIELDS.forEach(raw::remove);
        return open(id, raw);
    }

    /** Creates the smallest valid raw quest document used by the create form. */
    public static QuestDraft create(String id) {
        JsonObject root = new JsonObject();
        JsonObject display = new JsonObject();
        JsonObject icon = new JsonObject();
        icon.addProperty("type", "theseus:item");
        icon.addProperty("item", "minecraft:map");
        display.add("icon", icon);
        display.addProperty("icon_background", "theseus:textures/gui/quest_backgrounds/default.png");
        display.addProperty("title", "");
        display.addProperty("subtitle", "");
        display.add("description", new JsonArray());
        JsonObject groups = new JsonObject();
        JsonObject main = new JsonObject();
        JsonArray position = new JsonArray();
        position.add(0);
        position.add(0);
        main.add("position", position);
        groups.add("Main", main);
        display.add("groups", groups);
        root.add("display", display);
        root.add("settings", new JsonObject());
        root.add("tasks", new JsonObject());
        root.add("rewards", new JsonObject());
        return open(id, root);
    }

    public String id() { return id; }
    public String originalId() { return originalId; }

    public void rename(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Quest ID is required");
        id = value.trim();
    }

    public JsonObject snapshot() { return document.deepCopy(); }
    public JsonObject baseline() { return baseline.deepCopy(); }

    public boolean isDirty() { return !orderedEquals(baseline, document); }

    /** Marks the current document as acknowledged without changing its contents. */
    public void accept() { baseline = document.deepCopy(); }

    /** Returns the loader-neutral projection used by forms and validation. */
    public QuestDefinition definition() { return definition(id == null || id.isBlank() ? "draft" : id); }

    /** Returns the loader-neutral projection used by forms and validation. */
    public QuestDefinition definition(String fallbackId) {
        String resolvedId = id == null || id.isBlank() ? fallbackId : id;
        return QuestDefinition.parse(resolvedId == null || resolvedId.isBlank() ? "draft" : resolvedId, document);
    }

    public JsonElement get(String field) { return document.get(field); }

    public JsonElement get(QuestPath path) {
        if (path == null) throw new IllegalArgumentException("Path is required");
        JsonElement current = document;
        for (QuestPath.Segment segment : path.segments()) {
            if (!current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(segment.value());
            if (current == null) return null;
        }
        return current;
    }

    public boolean has(String field) { return document.has(field); }

    public void remove(String field) {
        if (field != null) document.remove(field);
    }

    public void remove(QuestPath path) {
        Parent parent = parent(path, false);
        if (parent != null) parent.object().remove(parent.name());
    }

    /** Applies a shallow named patch while preserving fields not mentioned by the caller. */
    public void applyPatch(JsonObject patch) {
        if (patch == null) return;
        patch.entrySet().forEach(entry -> apply(entry.getKey(), entry.getValue()));
    }

    /** Replaces one top-level field without discarding fields an editor does not understand. */
    public void apply(String field, JsonElement value) {
        if (field == null || field.isBlank()) throw new IllegalArgumentException("Field name is required");
        if (value == null) document.remove(field);
        else document.add(field, value.deepCopy());
    }

    /** Replaces one nested value. The path must already exist except for its final object key. */
    public void replace(QuestPath path, JsonElement value) {
        if (path == null || path.segments().isEmpty()) throw new IllegalArgumentException("Nested path is required");
        Parent parent = parent(path, true);
        if (value == null) parent.object().remove(parent.name());
        else parent.object().add(parent.name(), value.deepCopy());
    }

    /** Renames an object key while retaining the value and the surrounding entry order. */
    public void renameKey(QuestPath path, String newName) {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("New key is required");
        Parent parent = parent(path, false);
        if (parent == null || !parent.object().has(parent.name())) throw new IllegalArgumentException("Path does not identify an object key");
        JsonObject rebuilt = new JsonObject();
        parent.object().entrySet().forEach(entry -> {
            if (entry.getKey().equals(parent.name())) rebuilt.add(newName, entry.getValue().deepCopy());
            else rebuilt.add(entry.getKey(), entry.getValue().deepCopy());
        });
        replaceObject(parent.containerPath(), rebuilt);
    }

    /** Moves an object key without rebuilding any child values. */
    public void moveKey(QuestPath objectPath, String key, int targetIndex) {
        JsonElement value = get(objectPath);
        if (value == null || !value.isJsonObject() || !value.getAsJsonObject().has(key)) {
            throw new IllegalArgumentException("Path does not identify an object key");
        }
        List<java.util.Map.Entry<String, JsonElement>> entries = new ArrayList<>(value.getAsJsonObject().entrySet());
        java.util.Map.Entry<String, JsonElement> moved = entries.stream()
            .filter(entry -> entry.getKey().equals(key))
            .findFirst().orElseThrow();
        entries.remove(moved);
        int bounded = Math.max(0, Math.min(targetIndex, entries.size()));
        entries.add(bounded, moved);
        JsonObject rebuilt = new JsonObject();
        entries.forEach(entry -> rebuilt.add(entry.getKey(), entry.getValue().deepCopy()));
        replaceObject(objectPath, rebuilt);
    }

    /** Starts a detached nested edit. Nothing reaches this draft until commit is called. */
    public EditSession edit(QuestPath path) {
        JsonElement value = get(path);
        if (value == null) throw new IllegalArgumentException("Path does not exist");
        return new EditSession(this, path, value.deepCopy());
    }

    /** Applies the editor-owned display fields while retaining unknown display data. */
    public void setDisplay(
        String title,
        String subtitle,
        String body,
        String iconItem,
        String background,
        JsonObject groups
    ) {
        setDisplayBasics(title, subtitle, background, groups);
        if (body != null) setDescription(body);
        if (iconItem != null) setIcon(QuestIconDefinition.item(iconItem).source());
    }

    /** Applies display fields that have an unconditional structured editor. */
    public void setDisplayBasics(
        String title,
        String subtitle,
        String background,
        JsonObject groups
    ) {
        JsonObject display = object(document, "display");
        document.add("display", display);
        if (title != null) display.addProperty("title", title);
        if (subtitle != null) display.addProperty("subtitle", subtitle);
        if (background != null) display.addProperty("icon_background", background);
        if (groups != null) mergeGroups(display, groups);
    }

    /** Replaces the description only after the author explicitly edits it. */
    public void setDescription(String body) {
        JsonObject display = object(document, "display");
        JsonArray lines = new JsonArray();
        // Keep trailing empty lines; they are intentional author input.
        for (String line : (body == null ? "" : body).split("\\n", -1)) lines.add(line);
        display.add("description", lines);
        document.add("display", display);
    }

    /** Replaces the full icon value without merging it into an unknown icon shape. */
    public void setIcon(JsonElement icon) {
        JsonObject display = object(document, "display");
        if (icon == null) display.remove("icon");
        else display.add("icon", icon.deepCopy());
        document.add("display", display);
    }

    /** Writes icon_size only when the editor explicitly owns that field. */
    public void setIconSize(int iconSize) {
        JsonObject display = object(document, "display");
        display.addProperty("icon_size", iconSize);
        document.add("display", display);
    }

    /** Applies editor settings using the spelling already present in the source document. */
    public void setSettings(
        boolean individualProgress,
        QuestDefinition.Visibility hiddenUntil,
        boolean unlockNotification,
        boolean showDependencyArrow,
        boolean repeatable,
        boolean autoClaimRewards
    ) {
        JsonObject settings = object(document, "settings");
        document.add("settings", settings);
        setting(settings, "individual_progress", null, individualProgress, false);
        setting(settings, "hidden", null, (hiddenUntil == null ? QuestDefinition.Visibility.LOCKED : hiddenUntil).name().toLowerCase(java.util.Locale.ROOT), "locked");
        setting(settings, "unlockNotification", "unlock_notification", unlockNotification, false);
        setting(settings, "showDependencyArrow", "show_dependency_arrow", showDependencyArrow, true);
        setting(settings, "repeatable", null, repeatable, false);
        setting(settings, "autoClaimRewards", "auto_claim_rewards", autoClaimRewards, false);
    }

    public void setGroupPosition(String group, int x, int y) {
        if (group == null || group.isBlank()) throw new IllegalArgumentException("Group is required");
        JsonObject display = object(document, "display");
        JsonObject groups = object(display, "groups");
        JsonObject placement = object(groups, group);
        JsonArray position = new JsonArray();
        position.add(x);
        position.add(y);
        placement.add("position", position);
        groups.add(group, placement);
        display.add("groups", groups);
        document.add("display", display);
    }

    public void replaceTasks(JsonObject tasks) {
        document.add("tasks", tasks == null ? new JsonObject() : tasks.deepCopy());
    }

    public void replaceRewards(JsonObject rewards) {
        document.add("rewards", rewards == null ? new JsonObject() : rewards.deepCopy());
    }

    /** Returns structured changes from the immutable baseline to the current document. */
    public ChangeSet changes() {
        List<Change> changes = new ArrayList<>();
        diff(QuestPath.root(), baseline, document, changes);
        return new ChangeSet(changes);
    }

    public List<QuestDiagnostics.Diagnostic> diagnostics(Predicate<String> validItem) {
        return diagnostics(validItem, null);
    }

    /** Runs the shared structural and registry diagnostics against this document. */
    public List<QuestDiagnostics.Diagnostic> diagnostics(
        Predicate<String> validItem,
        RegistryValidation.Resolver registryResolver
    ) {
        List<QuestDiagnostics.Diagnostic> diagnostics = new ArrayList<>(QuestDiagnostics.validate(
            id == null || id.isBlank() ? "draft" : id,
            document,
            validItem == null ? ignored -> true : validItem
        ));
        if (registryResolver != null) diagnostics.addAll(RegistryValidation.validate(
            id == null || id.isBlank() ? "draft" : id,
            document,
            registryResolver
        ));
        return List.copyOf(diagnostics);
    }

    /** Builds operation-neutral create data consumed by the network adapter. */
    public JsonObject createMutation(String group, int x, int y) {
        JsonObject request = new JsonObject();
        request.addProperty("id", id);
        request.add("document", snapshotWithPlacement(group, x, y));
        request.addProperty("group", group == null ? "Main" : group);
        request.addProperty("x", x);
        request.addProperty("y", y);
        return request;
    }

    /** Builds operation-neutral update data consumed by the network adapter. */
    public JsonObject updateMutation() {
        JsonObject request = new JsonObject();
        request.addProperty("original_id", originalId == null ? id : originalId);
        request.addProperty("id", id);
        request.add("document", snapshot());
        request.add("changed_paths", changes().toJson());
        return request;
    }

    /** Returns only authored configuration fields for clipboard/import transfer. */
    public JsonObject transferSnapshot() {
        JsonObject snapshot = snapshot();
        CLIENT_RUNTIME_FIELDS.forEach(snapshot::remove);
        SYNC_METADATA_FIELDS.forEach(snapshot::remove);
        return snapshot;
    }

    public QuestDraft copy() {
        return new QuestDraft(originalId, id, baseline, document);
    }

    /** Applies a wire change set to the latest server document. */
    public static JsonObject merge(JsonObject base, JsonObject proposed, JsonArray encodedChanges) {
        JsonObject result = base == null ? new JsonObject() : base.deepCopy();
        if (proposed == null || encodedChanges == null || encodedChanges.isEmpty()) return result;
        for (JsonElement encoded : encodedChanges) {
            if (!encoded.isJsonObject()) continue;
            JsonObject change = encoded.getAsJsonObject();
            QuestPath path = QuestPath.fromJson(change.has("path") ? change.getAsJsonArray("path") : new JsonArray());
            String operation = change.has("operation") ? change.get("operation").getAsString() : "replace";
            if (path.segments().isEmpty()) {
                if ("remove".equals(operation)) result = new JsonObject();
                else if (change.has("value") && change.get("value").isJsonObject()) result = change.getAsJsonObject("value").deepCopy();
                continue;
            }
            if ("remove".equals(operation)) removeAt(result, path);
            else writeAt(result, path, change.has("value") ? change.get("value") : null);
        }
        return result;
    }

    /** Returns a user-facing conflict message when a changed path no longer matches its edit base. */
    static String firstConflict(JsonObject latest, JsonArray encodedChanges) {
        if (encodedChanges == null) return null;
        for (JsonElement encoded : encodedChanges) {
            if (!encoded.isJsonObject()) return "This editor update cannot be checked for conflicts. Reopen the quest and reapply your changes.";
            JsonObject change = encoded.getAsJsonObject();
            if (!change.has("before_present") || !change.get("before_present").isJsonPrimitive()
                || !change.getAsJsonPrimitive("before_present").isBoolean()) {
                return "This editor update cannot be checked for conflicts. Reopen the quest and reapply your changes.";
            }
            boolean beforePresent = change.get("before_present").getAsBoolean();
            if (beforePresent != change.has("before")) {
                return "This editor update cannot be checked for conflicts. Reopen the quest and reapply your changes.";
            }

            QuestPath path = QuestPath.fromJson(change.has("path") && change.get("path").isJsonArray()
                ? change.getAsJsonArray("path") : new JsonArray());
            PathValue current = valueAt(latest, path);
            if (beforePresent != current.present()
                || (beforePresent && !orderedEquals(change.get("before"), current.value()))) {
                return "Another operator changed " + path + " in this quest. Reopen the quest and reapply your changes.";
            }
        }
        return null;
    }

    private static PathValue valueAt(JsonObject root, QuestPath path) {
        JsonElement current = root;
        for (QuestPath.Segment segment : path.segments()) {
            if (current == null || !current.isJsonObject()) return new PathValue(false, null);
            current = current.getAsJsonObject().get(segment.value());
            if (current == null) return new PathValue(false, null);
        }
        return new PathValue(current != null, current);
    }

    private JsonObject snapshotWithPlacement(String group, int x, int y) {
        QuestDraft copy = open(id, snapshot());
        copy.setGroupPosition(group == null || group.isBlank() ? "Main" : group, x, y);
        return copy.snapshot();
    }

    private void mergeGroups(JsonObject display, JsonObject requested) {
        JsonObject groups = object(display, "groups");
        requested.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject placement = object(groups, entry.getKey());
            entry.getValue().getAsJsonObject().entrySet().forEach(value -> placement.add(value.getKey(), value.getValue().deepCopy()));
            groups.add(entry.getKey(), placement);
        });
        display.add("groups", groups);
    }

    private void setting(JsonObject settings, String canonical, String legacy, boolean value, boolean defaultValue) {
        if (settings.has(canonical)) settings.addProperty(canonical, value);
        else if (legacy != null && settings.has(legacy)) settings.addProperty(legacy, value);
        else if (originalId == null || value != defaultValue) settings.addProperty(canonical, value);
    }

    private void setting(JsonObject settings, String canonical, String legacy, String value, String defaultValue) {
        if (settings.has(canonical)) settings.addProperty(canonical, value);
        else if (legacy != null && settings.has(legacy)) settings.addProperty(legacy, value);
        else if (originalId == null || !value.equals(defaultValue)) settings.addProperty(canonical, value);
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key).deepCopy() : new JsonObject();
    }

    private Parent parent(QuestPath path, boolean create) {
        if (path == null || path.segments().isEmpty()) return null;
        JsonObject current = document;
        List<QuestPath.Segment> segments = path.segments();
        for (int index = 0; index < segments.size() - 1; index++) {
            QuestPath.Segment segment = segments.get(index);
            JsonElement next = current.get(segment.value());
            if (next == null || !next.isJsonObject()) {
                if (!create) return null;
                next = new JsonObject();
                current.add(segment.value(), next);
            }
            current = next.getAsJsonObject();
        }
        QuestPath.Segment last = segments.getLast();
        return new Parent(current, last.value(), new QuestPath(segments.subList(0, segments.size() - 1)));
    }

    private void replaceObject(QuestPath path, JsonObject value) {
        if (path.segments().isEmpty()) document = value.deepCopy();
        else replace(path, value);
    }

    private static void writeAt(JsonObject root, QuestPath path, JsonElement value) {
        JsonObject current = root;
        List<QuestPath.Segment> segments = path.segments();
        for (int index = 0; index < segments.size() - 1; index++) {
            String name = segments.get(index).value();
            JsonElement next = current.get(name);
            if (next == null || !next.isJsonObject()) {
                next = new JsonObject();
                current.add(name, next);
            }
            current = next.getAsJsonObject();
        }
        String last = segments.getLast().value();
        if (value == null) current.remove(last);
        else current.add(last, value.deepCopy());
    }

    private static void removeAt(JsonObject root, QuestPath path) {
        JsonObject current = root;
        List<QuestPath.Segment> segments = path.segments();
        for (int index = 0; index < segments.size() - 1; index++) {
            JsonElement next = current.get(segments.get(index).value());
            if (next == null || !next.isJsonObject()) return;
            current = next.getAsJsonObject();
        }
        current.remove(segments.getLast().value());
    }

    private static void diff(QuestPath path, JsonElement before, JsonElement after, List<Change> changes) {
        if (before == null && after == null) return;
        if (before == null) {
            changes.add(new Change(ChangeOperation.REPLACE, path, after, null, false));
            return;
        }
        if (after == null) {
            changes.add(new Change(ChangeOperation.REMOVE, path, null, before, true));
            return;
        }
        if (before.isJsonObject() && after.isJsonObject()) {
            JsonObject left = before.getAsJsonObject();
            JsonObject right = after.getAsJsonObject();
            if (!new ArrayList<>(left.keySet()).equals(new ArrayList<>(right.keySet()))
                && left.keySet().equals(right.keySet())) {
                changes.add(new Change(ChangeOperation.REORDER, path, after, before, true));
            }
            Set<String> keys = new LinkedHashSet<>(left.keySet());
            keys.addAll(right.keySet());
            for (String key : keys) diff(path.field(key), left.get(key), right.get(key), changes);
            return;
        }
        if (before.isJsonArray() && after.isJsonArray()) {
            if (!before.equals(after)) changes.add(new Change(ChangeOperation.REPLACE, path, after, before, true));
            return;
        }
        if (!before.equals(after)) changes.add(new Change(ChangeOperation.REPLACE, path, after, before, true));
    }

    private static boolean orderedEquals(JsonElement left, JsonElement right) {
        if (left == null || right == null) return left == right;
        if (left.isJsonObject() && right.isJsonObject()) {
            List<String> leftKeys = new ArrayList<>(left.getAsJsonObject().keySet());
            List<String> rightKeys = new ArrayList<>(right.getAsJsonObject().keySet());
            if (!leftKeys.equals(rightKeys)) return false;
            for (String key : leftKeys) if (!orderedEquals(left.getAsJsonObject().get(key), right.getAsJsonObject().get(key))) return false;
            return true;
        }
        if (left.isJsonArray() && right.isJsonArray()) {
            if (left.getAsJsonArray().size() != right.getAsJsonArray().size()) return false;
            for (int index = 0; index < left.getAsJsonArray().size(); index++) {
                if (!orderedEquals(left.getAsJsonArray().get(index), right.getAsJsonArray().get(index))) return false;
            }
            return true;
        }
        return left.equals(right);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Parent(JsonObject object, String name, QuestPath containerPath) {}
    private record PathValue(boolean present, JsonElement value) {}

    public record Change(
        ChangeOperation operation,
        QuestPath path,
        JsonElement value,
        JsonElement before,
        boolean beforePresent
    ) {
        public Change(ChangeOperation operation, QuestPath path, JsonElement value) {
            this(operation, path, value, null, false);
        }

        public Change {
            if (operation == null || path == null) throw new IllegalArgumentException("Change operation and path are required");
            if (beforePresent != (before != null)) throw new IllegalArgumentException("Original value and presence must agree");
            value = value == null ? null : value.deepCopy();
            before = before == null ? null : before.deepCopy();
        }
    }

    public enum ChangeOperation { REPLACE, REMOVE, REORDER }

    public static final class ChangeSet {
        private final List<Change> changes;

        private ChangeSet(List<Change> changes) {
            this.changes = changes.stream().map(change -> new Change(
                change.operation(), change.path(), change.value(), change.before(), change.beforePresent()
            )).toList();
        }

        public List<Change> values() { return changes; }
        public boolean isEmpty() { return changes.isEmpty(); }

        public Set<String> rootFields() {
            Set<String> fields = new LinkedHashSet<>();
            changes.forEach(change -> {
                if (!change.path().segments().isEmpty()) fields.add(change.path().rootName());
            });
            return Set.copyOf(fields);
        }

        public JsonArray toJson() {
            JsonArray encoded = new JsonArray();
            changes.forEach(change -> {
                JsonObject value = new JsonObject();
                value.addProperty("operation", change.operation().name().toLowerCase(java.util.Locale.ROOT));
                value.add("path", change.path().toJson());
                if (change.value() != null) value.add("value", change.value().deepCopy());
                value.addProperty("before_present", change.beforePresent());
                if (change.beforePresent()) value.add("before", change.before().deepCopy());
                encoded.add(value);
            });
            return encoded;
        }
    }

    public static final class EditSession {
        private final QuestDraft owner;
        private final QuestPath path;
        private JsonElement value;
        private boolean completed;

        private EditSession(QuestDraft owner, QuestPath path, JsonElement value) {
            this.owner = owner;
            this.path = path;
            this.value = value;
        }

        public JsonElement value() { return value.deepCopy(); }

        public void replace(JsonElement next) {
            if (completed) throw new IllegalStateException("Edit session is already closed");
            value = next == null ? null : next.deepCopy();
        }

        public void commit() {
            if (completed) throw new IllegalStateException("Edit session is already closed");
            owner.replace(path, value);
            completed = true;
        }

        public void cancel() { completed = true; }
    }

    public static QuestDraft parse(String rawQuest) {
        return open(JsonParser.parseString(rawQuest).getAsJsonObject());
    }
}
