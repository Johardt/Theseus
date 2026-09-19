package me.johardt.theseus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.johardt.theseus.Theseus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Owns the on-disk quest document format and its mutation invariants.
 *
 * <p>The public surface deliberately deals in quest IDs and JSON documents;
 * callers do not need to discover files, choose temporary names, or coordinate
 * related writes. Every multi-file mutation is staged and rolled back if any
 * part of the commit fails. JSON is parsed only after duplicate-key detection,
 * so an editor never silently accepts an ambiguous source document.</p>
 */
public final class QuestDocumentStore {
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final List<String> DEMO_QUESTS = List.of(
        "welcome.json", "gather_logs.json", "craft_table.json", "combat.json", "nether_trip.json", "compatibility.json"
    );

    private final Path theseusDirectory;
    private final Path questsDirectory;

    public QuestDocumentStore(Path configDirectory) {
        if (configDirectory == null) throw new IllegalArgumentException("Config directory is required");
        this.theseusDirectory = configDirectory.toAbsolutePath().normalize().resolve(Theseus.MOD_ID);
        this.questsDirectory = theseusDirectory.resolve("quests");
    }

    static QuestDocumentStore forQuestDirectory(Path questsDirectory) {
        if (questsDirectory == null) throw new IllegalArgumentException("Quest directory is required");
        Path normalized = questsDirectory.toAbsolutePath().normalize();
        return new QuestDocumentStore(normalized.getParent(), normalized);
    }

    private QuestDocumentStore(Path theseusDirectory, Path questsDirectory) {
        this.theseusDirectory = theseusDirectory;
        this.questsDirectory = questsDirectory;
    }

    /** Loads raw documents and metadata without projecting away unknown fields. */
    public Snapshot load() throws IOException {
        Files.createDirectories(questsDirectory);
        installDemoIfEmpty();

        Map<String, List<Path>> pathsById = new LinkedHashMap<>();
        Map<String, Document> documents = new LinkedHashMap<>();
        List<LoadFailure> failures = new ArrayList<>();
        for (Path path : questFiles()) {
            String id = idFor(path);
            pathsById.computeIfAbsent(id, ignored -> new ArrayList<>()).add(path);
            try {
                documents.putIfAbsent(id, readDocument(path, id));
            } catch (Exception exception) {
                failures.add(new LoadFailure(path, id, message(exception)));
            }
        }

        Map<String, List<Path>> conflicts = new LinkedHashMap<>();
        pathsById.forEach((id, paths) -> {
            if (paths.size() > 1) conflicts.put(id, List.copyOf(paths));
        });

        List<String> groupOrder;
        try {
            groupOrder = readGroupOrder(theseusDirectory.resolve("groups.txt"));
        } catch (Exception exception) {
            failures.add(new LoadFailure(theseusDirectory.resolve("groups.txt"), "", message(exception)));
            groupOrder = List.of();
        }

        Map<String, QuestCatalog.ChapterSettings> chapterSettings;
        try {
            chapterSettings = readChapterSettings(theseusDirectory.resolve("group_settings.json"));
        } catch (Exception exception) {
            failures.add(new LoadFailure(theseusDirectory.resolve("group_settings.json"), "", message(exception)));
            chapterSettings = Map.of();
        }
        return new Snapshot(documents, conflicts, failures, groupOrder, chapterSettings);
    }

    /** Returns a defensive copy of one lossless raw quest document. */
    public JsonObject readQuest(String id) throws IOException {
        return readDocument(resolveUniquePath(id), id).root();
    }

    /**
     * Resolves a quest to a slash-normalized path relative to the quest store.
     * The real-path check makes the result safe even when a quest file or one
     * of its parent directories is a symlink.
     */
    public String relativeQuestPath(String id) throws IOException {
        Path path = resolveUniquePath(id);
        Path root = normalize(questsDirectory);
        Path realRoot = root.toRealPath();
        Path realPath = path.toRealPath();
        if (!Files.isRegularFile(realPath) || !realPath.startsWith(realRoot)) {
            throw new IOException("Quest file is outside the quest directory");
        }
        Path relative = root.relativize(normalize(path));
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Quest file path is invalid");
        }
        return relative.toString().replace(java.io.File.separatorChar, '/');
    }

    public boolean contains(String id) throws IOException {
        return !pathsFor(id).isEmpty();
    }

    /** Creates a new root-level document, refusing every existing path for the ID. */
    public void createQuest(String id, JsonObject root) throws IOException {
        requireId(id);
        requireDocument(root, id);
        if (!pathsFor(id).isEmpty()) throw new IOException("A quest with ID '" + id + "' already exists");
        Files.createDirectories(questsDirectory);
        commit(Map.of(questsDirectory.resolve(id + ".json"), encode(root)), Set.of());
    }

    /**
     * Saves a document and, when its ID changes, updates all dependency
     * references in the same rollback-safe transaction.
     */
    public void saveQuest(String originalId, String newId, JsonObject root) throws IOException {
        requireId(originalId);
        requireId(newId);
        requireDocument(root, newId);
        Path source = resolveUniquePath(originalId);
        Path target = originalId.equals(newId) ? source : source.resolveSibling(newId + ".json");
        if (!source.equals(target) && !pathsFor(newId).isEmpty()) {
            throw new IOException("A quest with ID '" + newId + "' already exists");
        }

        Map<Path, byte[]> writes = new LinkedHashMap<>();
        writes.put(target, encode(root));
        Set<Path> deletes = new LinkedHashSet<>();
        if (!source.equals(target)) {
            deletes.add(source);
            addDependencyReplacements(writes, originalId, newId, source);
        }
        commit(writes, deletes);
    }

    /** Replaces one existing document without changing its path or ID. */
    public void writeQuest(String id, JsonObject root) throws IOException {
        requireId(id);
        requireDocument(root, id);
        commit(Map.of(resolveUniquePath(id), encode(root)), Set.of());
    }

    /** Deletes a quest and removes its dependency from every other document. */
    public void deleteQuest(String id) throws IOException {
        requireId(id);
        Path source = resolveUniquePath(id);
        Map<Path, byte[]> writes = new LinkedHashMap<>();
        addDependencyReplacements(writes, id, null, source);
        commit(writes, Set.of(source));
    }

    /** Imports all documents in one commit; no target is created on failure. */
    public void importQuests(Map<String, JsonObject> quests) throws IOException {
        if (quests == null || quests.isEmpty()) throw new IOException("Quest import is empty");
        Map<Path, byte[]> writes = new LinkedHashMap<>();
        for (var entry : quests.entrySet()) {
            requireId(entry.getKey());
            requireDocument(entry.getValue(), entry.getKey());
            if (!pathsFor(entry.getKey()).isEmpty()) {
                throw new IOException("A quest with ID '" + entry.getKey() + "' already exists");
            }
            byte[] encoded = encode(entry.getValue());
            if (encoded.length > QuestImportBatch.MAX_IMPORT_BYTES) {
                throw new IOException("Quest '" + entry.getKey() + "' exceeds the 1 MiB import limit");
            }
            writes.put(questsDirectory.resolve(entry.getKey() + ".json"), encoded);
        }
        Files.createDirectories(questsDirectory);
        commit(writes, Set.of());
    }

    /** Updates one dependency array while retaining every other document field. */
    public void updateDependencies(String questId, Set<String> dependencies) throws IOException {
        JsonObject root = readQuest(questId);
        JsonArray values = new JsonArray();
        if (dependencies != null) dependencies.stream().sorted().forEach(values::add);
        root.add("dependencies", values);
        writeQuest(questId, root);
    }

    /** Copies or moves a document through the same create/rename transaction rules. */
    public void transferQuest(String sourceId, String targetId, JsonObject root, boolean move) throws IOException {
        if (move) saveQuest(sourceId, targetId, root);
        else createQuest(targetId, root);
    }

    /**
     * Persists chapter metadata and optional quest-group propagation together.
     * Unknown fields in group_settings.json are carried through unchanged.
     */
    public void updateChapters(
        List<String> order,
        Map<String, QuestCatalog.ChapterSettings> settings,
        ChapterChange change
    ) throws IOException {
        if (order == null || order.isEmpty()) throw new IOException("At least one chapter is required");
        if (settings == null) settings = Map.of();
        if (change == null) change = ChapterChange.none();

        Map<Path, byte[]> writes = new LinkedHashMap<>();
        if (change.affectsQuestGroups()) {
            for (StoredFile file : readAllFiles()) {
                JsonObject root = file.root();
                JsonObject display = object(root, "display");
                JsonObject groups = object(display, "groups");
                if (!groups.has(change.source())) continue;
                JsonElement placement = groups.remove(change.source());
                if (change.destination() != null) groups.add(change.destination(), placement);
                display.add("groups", groups);
                root.add("display", display);
                writes.put(file.path(), encode(root));
            }
        }

        JsonObject metadata = readOptionalObject(theseusDirectory.resolve("group_settings.json"));
        if (change.destination() != null && metadata.has(change.source())) {
            JsonObject previous = object(metadata, change.source());
            JsonObject replacement = object(metadata, change.destination());
            previous.entrySet().forEach(entry -> replacement.add(entry.getKey(), entry.getValue().deepCopy()));
            metadata.remove(change.source());
            metadata.add(change.destination(), replacement);
        }
        Set<String> retainedSettings = new LinkedHashSet<>(settings.keySet());
        new ArrayList<>(metadata.keySet()).stream()
            .filter(name -> !retainedSettings.contains(name))
            .forEach(metadata::remove);
        settings.forEach((name, value) -> {
            JsonObject current = object(metadata, name);
            current.addProperty("icon", value.icon());
            current.addProperty("iconEnabled", value.iconEnabled());
            current.addProperty("background", value.background());
            current.addProperty("backgroundOpacity", value.backgroundOpacity());
            metadata.add(name, current);
        });
        writes.put(theseusDirectory.resolve("groups.txt"), encodeLines(order));
        writes.put(theseusDirectory.resolve("group_settings.json"), encode(metadata));
        Files.createDirectories(theseusDirectory);
        commit(writes, Set.of());
    }

    static List<String> readGroupOrder(Path path) throws IOException {
        if (!Files.exists(path)) return List.of();
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .distinct()
            .toList();
    }

    static Map<String, QuestCatalog.ChapterSettings> readChapterSettings(Path path) throws IOException {
        if (!Files.exists(path)) return Map.of();
        JsonElement parsed;
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            List<String> duplicateKeys = JsonDuplicateKeyDetector.findDuplicates(source);
            if (!duplicateKeys.isEmpty()) throw new IOException("Duplicate JSON key(s): " + String.join(", ", duplicateKeys));
            parsed = JsonParser.parseString(source);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid chapter settings JSON: " + message(exception), exception);
        }
        if (!parsed.isJsonObject()) throw new IOException("Chapter settings must be a JSON object");
        Map<String, QuestCatalog.ChapterSettings> settings = new LinkedHashMap<>();
        parsed.getAsJsonObject().entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject value = entry.getValue().getAsJsonObject();
            settings.put(entry.getKey(), new QuestCatalog.ChapterSettings(
                value.has("icon") ? value.get("icon").getAsString() : "minecraft:map",
                value.has("background") ? value.get("background").getAsString() : "",
                !value.has("iconEnabled") || value.get("iconEnabled").getAsBoolean(),
                value.has("backgroundOpacity") ? Math.clamp(value.get("backgroundOpacity").getAsInt(), 0, 100) : 100
            ));
        });
        return settings;
    }

    private void installDemoIfEmpty() throws IOException {
        if (questFiles().stream().anyMatch(path -> path.getFileName().toString().endsWith(".json"))) return;
        Path target = questsDirectory.resolve("getting_started");
        Files.createDirectories(target);
        copyResource("/config/theseus/groups.txt", theseusDirectory.resolve("groups.txt"));
        copyResource("/config/theseus/group_settings.json", theseusDirectory.resolve("group_settings.json"));
        for (String quest : DEMO_QUESTS) {
            copyResource("/config/theseus/quests/getting_started/" + quest, target.resolve(quest));
        }
        Theseus.LOGGER.info("Installed demo quests into {}", questsDirectory);
    }

    private static void copyResource(String resource, Path target) throws IOException {
        try (InputStream stream = QuestDocumentStore.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing bundled resource " + resource);
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<StoredFile> readAllFiles() throws IOException {
        List<StoredFile> files = new ArrayList<>();
        for (Path path : questFiles()) {
            String id = idFor(path);
            files.add(new StoredFile(path, id, readDocument(path, id).root()));
        }
        return files;
    }

    private void addDependencyReplacements(Map<Path, byte[]> writes, String oldId, String newId, Path excluded) throws IOException {
        for (StoredFile file : readAllFiles()) {
            if (file.path().equals(excluded)) continue;
            if (!file.root().has("dependencies") || !file.root().get("dependencies").isJsonArray()) continue;
            JsonArray updated = new JsonArray();
            boolean changed = false;
            for (JsonElement value : file.root().getAsJsonArray("dependencies")) {
                String dependency = value.getAsString();
                if (dependency.equals(oldId)) {
                    changed = true;
                    if (newId != null) updated.add(newId);
                } else {
                    updated.add(value.deepCopy());
                }
            }
            if (changed) {
                JsonObject root = file.root();
                root.add("dependencies", updated);
                writes.put(file.path(), encode(root));
            }
        }
    }

    private Document readDocument(Path path, String id) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        List<String> duplicateKeys = JsonDuplicateKeyDetector.findDuplicates(source);
        if (!duplicateKeys.isEmpty()) throw new IOException("Duplicate JSON key(s): " + String.join(", ", duplicateKeys));
        try {
            JsonElement parsed = JsonParser.parseString(source);
            if (!parsed.isJsonObject()) throw new IOException("Quest document must be a JSON object");
            return new Document(id, path, source, parsed.getAsJsonObject());
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Malformed quest JSON: " + message(exception), exception);
        }
    }

    private List<Path> pathsFor(String id) throws IOException {
        if (id == null || id.isBlank() || !id.matches("[a-z0-9_.-]+")) return List.of();
        return questFiles().stream().filter(path -> path.getFileName().toString().equals(id + ".json")).toList();
    }

    private Path resolveUniquePath(String id) throws IOException {
        requireId(id);
        List<Path> matches = pathsFor(id);
        if (matches.isEmpty()) throw new IOException("Quest file not found for " + id);
        if (matches.size() > 1) throw new IOException("Multiple quest files found for " + id);
        return matches.getFirst();
    }

    private List<Path> questFiles() throws IOException {
        if (!Files.exists(questsDirectory)) return List.of();
        try (Stream<Path> files = Files.walk(questsDirectory)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    private static String idFor(Path path) {
        String filename = path.getFileName().toString();
        return filename.substring(0, filename.length() - ".json".length());
    }

    private static void requireId(String id) throws IOException {
        if (id == null || !id.matches("[a-z0-9_.-]+")) throw new IOException("Invalid quest ID " + id);
    }

    private static void requireDocument(JsonObject root, String id) throws IOException {
        if (root == null) throw new IOException("Quest '" + id + "' has no document");
    }

    private static byte[] encode(JsonObject root) {
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] encodeLines(List<String> lines) {
        String value = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private JsonObject readOptionalObject(Path path) throws IOException {
        if (!Files.exists(path)) return new JsonObject();
        return readDocument(path, "group_settings").root();
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject()
            ? parent.getAsJsonObject(key).deepCopy()
            : new JsonObject();
    }

    private void commit(Map<Path, byte[]> writes, Set<Path> deletes) throws IOException {
        Map<Path, byte[]> original = new LinkedHashMap<>();
        Set<Path> touched = new LinkedHashSet<>();
        writes.keySet().stream().map(QuestDocumentStore::normalize).sorted(Comparator.comparing(Path::toString)).forEach(touched::add);
        deletes.stream().map(QuestDocumentStore::normalize).sorted(Comparator.comparing(Path::toString)).forEach(touched::add);
        for (Path path : touched) original.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);

        Map<Path, Path> staged = new LinkedHashMap<>();
        try {
            for (var entry : writes.entrySet()) {
                Path target = normalize(entry.getKey());
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(target.getParent(), ".theseus-", ".tmp");
                Files.write(temporary, entry.getValue());
                staged.put(target, temporary);
            }
            for (var entry : staged.entrySet()) moveReplace(entry.getValue(), entry.getKey());
            for (Path path : deletes) Files.deleteIfExists(normalize(path));
        } catch (Exception failure) {
            try {
                restore(original);
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            if (failure instanceof IOException exception) throw exception;
            throw new IOException("Quest document transaction failed", failure);
        } finally {
            for (Path temporary : staged.values()) Files.deleteIfExists(temporary);
        }
    }

    private void restore(Map<Path, byte[]> original) throws IOException {
        for (var entry : original.entrySet()) {
            if (entry.getValue() == null) {
                Files.deleteIfExists(entry.getKey());
                continue;
            }
            Files.createDirectories(entry.getKey().getParent());
            Path temporary = Files.createTempFile(entry.getKey().getParent(), ".theseus-rollback-", ".tmp");
            try {
                Files.write(temporary, entry.getValue());
                moveReplace(temporary, entry.getKey());
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    }

    public record Snapshot(
        Map<String, Document> documents,
        Map<String, List<Path>> conflictingPaths,
        List<LoadFailure> failures,
        List<String> groupOrder,
        Map<String, QuestCatalog.ChapterSettings> chapterSettings
    ) {
        public Snapshot {
            documents = Map.copyOf(documents);
            conflictingPaths = conflictingPaths.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))
            );
            failures = List.copyOf(failures);
            groupOrder = List.copyOf(groupOrder);
            chapterSettings = Map.copyOf(chapterSettings);
        }
    }

    public record Document(String id, Path path, String source, JsonObject root) {
        public Document {
            root = root == null ? new JsonObject() : root.deepCopy();
        }

        @Override
        public JsonObject root() {
            return root.deepCopy();
        }
    }

    public record LoadFailure(Path path, String id, String message) {}

    private record StoredFile(Path path, String id, JsonObject root) {
        public StoredFile {
            root = root.deepCopy();
        }

        @Override
        public JsonObject root() {
            return root.deepCopy();
        }
    }

    public record ChapterChange(String source, String destination) {
        public ChapterChange {
            if (source == null) throw new IllegalArgumentException("Chapter source is required");
            if (destination != null && destination.isBlank()) throw new IllegalArgumentException("Chapter destination is invalid");
        }

        public static ChapterChange none() { return new ChapterChange("", null); }
        public boolean affectsQuestGroups() { return !source.isBlank(); }
    }
}
