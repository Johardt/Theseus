package me.johardt.theseus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileProgressStoreTest {

    @TempDir
    Path directory;

    @Test
    void missingStoreLoadsAsEmptyDocument() throws Exception {
        ProgressStore store = new FileProgressStore(
            directory.resolve("data/progress.json")
        );

        assertEquals(new JsonObject(), store.load());
    }

    @Test
    void replacesTheWholeDocument() throws Exception {
        ProgressStore store = new FileProgressStore(
            directory.resolve("data/progress.json")
        );
        JsonObject first = new JsonObject();
        first.addProperty("version", 1);
        JsonObject second = new JsonObject();
        second.addProperty("version", 2);

        store.save(first);
        store.save(second);

        assertEquals(second, store.load());
    }
}
