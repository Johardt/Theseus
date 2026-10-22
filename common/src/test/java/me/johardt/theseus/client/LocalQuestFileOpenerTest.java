package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalQuestFileOpenerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void opensOnlyRegularFilesContainedByTheQuestDirectory() throws Exception {
        Path quests = tempDirectory.resolve("config/theseus/quests");
        Path file = quests.resolve("chapter/quest.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}");
        List<Path> opened = new ArrayList<>();
        LocalQuestFileOpener opener = new LocalQuestFileOpener(opened::add);

        LocalQuestFileOpener.Result result = opener.open(quests, "chapter/quest.json");

        assertTrue(result.success());
        assertEquals(file.toRealPath(), result.path());
        assertEquals(List.of(file.toRealPath()), opened);
    }

    @Test
    void rejectsAbsoluteTraversalMissingAndEscapingSymlinkPaths() throws Exception {
        Path quests = tempDirectory.resolve("quests");
        Files.createDirectories(quests);
        Files.writeString(quests.resolve("inside.json"), "{}");
        Path outside = tempDirectory.resolve("outside.json");
        Files.writeString(outside, "{}");
        Files.createSymbolicLink(quests.resolve("escape.json"), outside);
        LocalQuestFileOpener opener = new LocalQuestFileOpener(path -> {
            throw new AssertionError("invalid path was opened");
        });

        assertFalse(opener.open(quests, outside.toString()).success());
        assertFalse(opener.open(quests, "../outside.json").success());
        assertFalse(opener.open(quests, "missing.json").success());
        LocalQuestFileOpener.Result symlink = opener.open(quests, "escape.json");
        assertFalse(symlink.success());
        assertNull(symlink.path());
    }
}
