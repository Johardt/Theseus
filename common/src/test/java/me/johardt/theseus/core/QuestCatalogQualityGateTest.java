package me.johardt.theseus.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the checked-in scale fixture through the same catalog path used at server startup. */
class QuestCatalogQualityGateTest {
    private static final int EXPECTED_QUESTS = 700;
    private static final int EXPECTED_DEPENDENCY_EDGES = 1_439;
    private static final long MAX_CATALOG_LOAD_MILLIS = 10_000;

    @Test
    void loadsTheQualityGatePackQuicklyAndWithoutValidationIssues() throws Exception {
        Path configDirectory = Path.of(
            "smoke-test",
            "fixtures",
            "quality_gate",
            "config"
        ).toAbsolutePath().normalize();
        assertTrue(
            Files.isDirectory(configDirectory.resolve("theseus/quests")),
            () -> "Quality gate fixture directory is missing: " + configDirectory
        );

        long startedAt = System.nanoTime();
        QuestCatalog catalog = QuestCatalog.load(configDirectory);
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - startedAt
        );
        int dependencyEdges = catalog.quests().values().stream()
            .mapToInt(quest -> quest.dependencies().size())
            .sum();

        assertEquals(EXPECTED_QUESTS, catalog.quests().size());
        assertEquals(EXPECTED_DEPENDENCY_EDGES, dependencyEdges);
        assertEquals(10, catalog.groupOrder().size());
        assertTrue(catalog.issues().isEmpty(), () -> catalog.issues().toString());
        assertTrue(
            elapsedMillis <= MAX_CATALOG_LOAD_MILLIS,
            () -> "Loading the 700-quest fixture took " + elapsedMillis + " ms"
        );
    }
}
