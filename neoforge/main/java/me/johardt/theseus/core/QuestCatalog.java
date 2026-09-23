package me.johardt.theseus.core;

import me.johardt.theseus.Theseus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuestCatalog {
    private final Map<String, QuestDefinition> quests;
    private final Map<String, Set<String>> dependents;
    private final Set<String> groups;
    private final List<String> groupOrder;
    private final Map<String, ChapterSettings> chapterSettings;
    private final List<QuestDefinition.ValidationIssue> issues;
    private final Map<String, List<Path>> conflictingPaths;
    private final Set<String> failedQuestIds;
    private final QuestDocumentStore documents;
    /** Lossless documents retained from the catalog load for network snapshots. */
    private final Map<String, QuestDocumentStore.Document> rawDocuments;

    private QuestCatalog(
        QuestDocumentStore documents,
        Map<String, QuestDefinition> quests,
        Map<String, QuestDocumentStore.Document> rawDocuments,
        Set<String> failedQuestIds,
        List<String> configuredOrder,
        Map<String, ChapterSettings> chapterSettings,
        Map<String, List<Path>> conflictingPaths,
        List<QuestDefinition.ValidationIssue> storageIssues
    ) {
        this.documents = documents;
        this.quests = Map.copyOf(quests);
        this.rawDocuments = Map.copyOf(rawDocuments);
        this.failedQuestIds = Set.copyOf(failedQuestIds);
        this.dependents = buildDependents(quests);
        this.groups = quests.values().stream()
            .flatMap(quest -> quest.display().groups().keySet().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LinkedHashSet<String> order = new LinkedHashSet<>(configuredOrder);
        order.addAll(this.groups);
        if (order.isEmpty()) order.add("Main");
        this.groupOrder = List.copyOf(order);
        this.chapterSettings = Map.copyOf(chapterSettings);
        this.conflictingPaths = Map.copyOf(conflictingPaths);
        List<QuestDefinition.ValidationIssue> allIssues = new java.util.ArrayList<>(storageIssues);
        allIssues.addAll(validate(quests));
        conflictingPaths.forEach((id, paths) -> allIssues.add(new QuestDefinition.ValidationIssue(
            QuestDefinition.Severity.ERROR, id, "Duplicate quest ID '" + id + "' in " + paths.stream().map(Path::toString).collect(java.util.stream.Collectors.joining(" and "))
        )));
        this.issues = List.copyOf(allIssues);
    }

    public static QuestCatalog load(Path configDirectory) {
        QuestDocumentStore documents = new QuestDocumentStore(configDirectory);
        try {
            Map<String, QuestDefinition> quests = new LinkedHashMap<>();
            QuestDocumentStore.Snapshot snapshot = documents.load();
            Set<String> failedQuestIds = new HashSet<>();
            snapshot.failures().stream()
                .map(QuestDocumentStore.LoadFailure::id)
                .filter(id -> !id.isBlank())
                .forEach(failedQuestIds::add);
            List<QuestDefinition.ValidationIssue> storageIssues = snapshot.failures().stream()
                .map(failure -> new QuestDefinition.ValidationIssue(
                    QuestDefinition.Severity.ERROR,
                    failure.path().toString(),
                    failure.message()
                ))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            snapshot.documents().forEach((id, document) -> {
                try {
                    quests.put(id, QuestDefinition.parse(id, document.root()));
                } catch (RuntimeException exception) {
                    failedQuestIds.add(id);
                    String message = exception.getMessage();
                    if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
                    storageIssues.add(new QuestDefinition.ValidationIssue(
                        QuestDefinition.Severity.ERROR,
                        document.path().toString(),
                        "Failed to parse quest definition: " + message
                    ));
                }
            });
            QuestCatalog catalog = new QuestCatalog(
                documents,
                quests,
                snapshot.documents(),
                failedQuestIds,
                snapshot.groupOrder(),
                snapshot.chapterSettings(),
                snapshot.conflictingPaths(),
                storageIssues
            );
            Theseus.LOGGER.info("Loaded {} core quests ({} validation issues)", quests.size(), catalog.issues.size());
            catalog.issues.forEach(issue -> {
                if (issue.severity() == QuestDefinition.Severity.ERROR) {
                    Theseus.LOGGER.error("Quest validation: {}: {}", issue.path(), issue.message());
                } else {
                    Theseus.LOGGER.warn("Quest validation: {}: {}", issue.path(), issue.message());
                }
            });
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Theseus quests", exception);
        }
    }

    public Map<String, QuestDefinition> quests() {
        return quests;
    }

    /** Quest IDs whose files were found but could not be loaded into definitions. */
    public Set<String> failedQuestIds() {
        return failedQuestIds;
    }

    public QuestDocumentStore documents() {
        return documents;
    }

    /**
     * Returns the lossless document captured during the last catalog load.
     *
     * <p>The returned value is a defensive copy. Keeping this data in the
     * catalog avoids rediscovering and reparsing the quest directory whenever
     * a player opens the quest screen.</p>
     */
    public com.google.gson.JsonObject rawQuest(String id) throws IOException {
        if (hasConflict(id)) throw new IOException("Multiple quest files found for " + id);
        QuestDocumentStore.Document document = rawDocuments.get(id);
        if (document == null) throw new IOException("Quest file not found for " + id);
        return document.root();
    }

    public Set<String> groups() {
        return groups;
    }

    public List<String> groupOrder() { return groupOrder; }

    public Map<String, ChapterSettings> chapterSettings() { return chapterSettings; }

    static List<String> loadGroupOrder(Path path) throws IOException {
        return QuestDocumentStore.readGroupOrder(path);
    }

    static Map<String, ChapterSettings> loadChapterSettings(Path path) throws IOException {
        return QuestDocumentStore.readChapterSettings(path);
    }

    public record ChapterSettings(String icon, String background, boolean iconEnabled, int backgroundOpacity) {
        public ChapterSettings {
            icon = icon == null || icon.isBlank() ? "minecraft:map" : icon;
            background = background == null ? "" : background;
            backgroundOpacity = Math.clamp(backgroundOpacity, 0, 100);
        }

        /** Compatibility constructor for existing callers and old metadata. */
        public ChapterSettings(String icon, String background) {
            this(icon, background, true, 100);
        }
    }

    public Set<String> dependents(String questId) {
        return dependents.getOrDefault(questId, Set.of());
    }

    public List<QuestDefinition.ValidationIssue> issues() {
        return issues;
    }

    /** IDs with more than one source file. Mutations against these IDs are unsafe. */
    public Map<String, List<Path>> conflictingPaths() { return conflictingPaths; }

    public boolean hasConflict(String questId) { return conflictingPaths.containsKey(questId); }

    static void writeDependencies(
        Path configDirectory,
        String questId,
        Set<String> dependencies
    ) throws IOException {
        new QuestDocumentStore(configDirectory).updateDependencies(questId, dependencies);
    }

    static boolean wouldCreateCycle(
        Map<String, QuestDefinition> quests,
        String prerequisiteId,
        String dependentId
    ) {
        return dependsOn(quests, prerequisiteId, dependentId, new HashSet<>());
    }

    /** Returns the complete cycle introduced by adding dependent → prerequisite, if any. */
    public static List<String> dependencyCyclePath(Map<String, QuestDefinition> quests, String prerequisiteId, String dependentId) {
        List<String> path = new java.util.ArrayList<>();
        if (!findDependencyPath(quests, prerequisiteId, dependentId, new HashSet<>(), path)) return List.of();
        path.add(0, dependentId);
        return List.copyOf(path);
    }

    private static boolean findDependencyPath(Map<String, QuestDefinition> quests, String current, String target, Set<String> visited, List<String> path) {
        if (!visited.add(current)) return false;
        path.add(current);
        if (current.equals(target)) return true;
        QuestDefinition quest = quests.get(current);
        if (quest != null) for (String dependency : quest.dependencies()) {
            if (findDependencyPath(quests, dependency, target, visited, path)) return true;
        }
        path.removeLast();
        return false;
    }

    private static boolean dependsOn(
        Map<String, QuestDefinition> quests,
        String questId,
        String targetId,
        Set<String> visited
    ) {
        if (!visited.add(questId)) return false;
        QuestDefinition quest = quests.get(questId);
        if (quest == null) return false;
        if (quest.dependencies().contains(targetId)) return true;
        return quest.dependencies().stream().anyMatch(dependency ->
            dependsOn(quests, dependency, targetId, visited)
        );
    }

    private static Map<String, Set<String>> buildDependents(Map<String, QuestDefinition> quests) {
        Map<String, Set<String>> result = new HashMap<>();
        quests.forEach((id, quest) -> quest.dependencies().forEach(dependency ->
            result.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(id)));
        result.replaceAll((ignored, values) -> Set.copyOf(values));
        return Map.copyOf(result);
    }

    private static List<QuestDefinition.ValidationIssue> validate(Map<String, QuestDefinition> quests) {
        List<QuestDefinition.ValidationIssue> issues = new java.util.ArrayList<>();
        quests.forEach((id, quest) -> {
            quest.issues().forEach(issue -> issues.add(new QuestDefinition.ValidationIssue(
                issue.severity(), id + "." + issue.path(), issue.message())));
            quest.dependencies().stream().filter(dependency -> !quests.containsKey(dependency)).forEach(dependency ->
                issues.add(new QuestDefinition.ValidationIssue(QuestDefinition.Severity.ERROR, id + ".dependencies", "Missing quest " + dependency)));
            detectCycle(id, id, quests, new HashSet<>(), issues);
        });
        return List.copyOf(issues);
    }

    /** Validates only dependency references and cycles for an in-memory catalog. */
    public static List<QuestDefinition.ValidationIssue> validateDependencies(Map<String, QuestDefinition> quests) {
        List<QuestDefinition.ValidationIssue> issues = new java.util.ArrayList<>();
        quests.forEach((id, quest) -> {
            quest.dependencies().stream()
                .filter(dependency -> !quests.containsKey(dependency))
                .forEach(dependency -> issues.add(new QuestDefinition.ValidationIssue(
                    QuestDefinition.Severity.ERROR,
                    id + ".dependencies",
                    "Missing quest " + dependency
                )));
            List<String> cycle = dependencyCyclePath(quests, id);
            if (!cycle.isEmpty()) {
                issues.add(new QuestDefinition.ValidationIssue(
                    QuestDefinition.Severity.ERROR,
                    id + ".dependencies",
                    "Dependency cycle: " + String.join(" → ", cycle)
                ));
            }
        });
        return issues.stream().distinct().toList();
    }

    private static List<String> dependencyCyclePath(Map<String, QuestDefinition> quests, String origin) {
        return findCyclePath(quests, origin, origin, new LinkedHashSet<>());
    }

    private static List<String> findCyclePath(Map<String, QuestDefinition> quests, String origin, String current, Set<String> path) {
        if (!path.add(current)) return current.equals(origin) ? List.of(origin) : List.of();
        QuestDefinition quest = quests.get(current);
        if (quest != null) {
            for (String dependency : quest.dependencies()) {
                if (dependency.equals(origin)) {
                    List<String> cycle = new java.util.ArrayList<>(path);
                    cycle.add(origin);
                    return List.copyOf(cycle);
                }
                List<String> nested = findCyclePath(quests, origin, dependency, new LinkedHashSet<>(path));
                if (!nested.isEmpty()) return nested;
            }
        }
        return List.of();
    }

    private static void detectCycle(String origin, String current, Map<String, QuestDefinition> quests, Set<String> path, List<QuestDefinition.ValidationIssue> issues) {
        if (!path.add(current)) {
            if (current.equals(origin)) {
                QuestDefinition.ValidationIssue issue = new QuestDefinition.ValidationIssue(QuestDefinition.Severity.ERROR, origin + ".dependencies", "Dependency cycle detected");
                if (!issues.contains(issue)) issues.add(issue);
            }
            return;
        }
        QuestDefinition quest = quests.get(current);
        if (quest != null) quest.dependencies().forEach(dependency -> detectCycle(origin, dependency, quests, new HashSet<>(path), issues));
    }
}
