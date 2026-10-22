package me.johardt.theseus.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** File-system adapter for {@link ProgressStore}. */
public final class FileProgressStore implements ProgressStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public FileProgressStore(Path file) {
        this.file = file;
    }

    @Override
    public JsonObject load() throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        JsonElement parsed = JsonParser.parseString(
            Files.readString(file, StandardCharsets.UTF_8)
        );
        if (!parsed.isJsonObject()) throw new IOException(
            "Progress file must be a JSON object"
        );
        return parsed.getAsJsonObject();
    }

    @Override
    public void save(JsonObject progress) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(
            parent,
            ".theseus-progress-",
            ".tmp"
        );
        try {
            Files.writeString(
                temporary,
                GSON.toJson(progress),
                StandardCharsets.UTF_8
            );
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
