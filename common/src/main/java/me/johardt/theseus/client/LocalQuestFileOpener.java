package me.johardt.theseus.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Validates a server-returned relative quest path before asking the OS to open it. */
public final class LocalQuestFileOpener {
    private final FileOpener opener;

    public LocalQuestFileOpener() {
        this(path -> net.minecraft.util.Util.getPlatform().openFile(path.toFile()));
    }

    public LocalQuestFileOpener(FileOpener opener) {
        this.opener = Objects.requireNonNull(opener, "File opener is required");
    }

    public Result open(Path questsDirectory, String relativePath) {
        try {
            Path path = resolve(questsDirectory, relativePath);
            opener.open(path);
            return Result.success(path);
        } catch (IOException | RuntimeException exception) {
            return Result.failure(boundMessage(exception));
        }
    }

    public static Path resolve(Path questsDirectory, String relativePath) throws IOException {
        if (questsDirectory == null) throw new IOException("Quest directory is unavailable");
        if (relativePath == null || relativePath.isBlank()) throw new IOException("Quest file path is empty");
        if (relativePath.indexOf('\\') >= 0) throw new IOException("Quest file path is invalid");

        Path relative;
        try {
            relative = Path.of(relativePath);
        } catch (RuntimeException exception) {
            throw new IOException("Quest file path is invalid", exception);
        }
        if (relative.isAbsolute()) throw new IOException("Quest file path must be relative");
        for (Path part : relative) {
            if (part.toString().equals("..")) throw new IOException("Quest file path escapes the quest directory");
        }

        Path root = questsDirectory.toAbsolutePath().normalize();
        Path realRoot = root.toRealPath();
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) throw new IOException("Quest file path escapes the quest directory");

        Path realCandidate = candidate.toRealPath();
        if (!Files.isRegularFile(realCandidate) || !realCandidate.startsWith(realRoot)) {
            throw new IOException("Quest file path is outside the quest directory");
        }
        return realCandidate;
    }

    private static String boundMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Failed to open quest file";
        return message.length() <= 256 ? message : message.substring(0, 255) + "…";
    }

    @FunctionalInterface
    public interface FileOpener {
        void open(Path path);
    }

    public record Result(boolean success, String message, Path path) {
        public static Result success(Path path) {
            return new Result(true, "Quest file opened", path);
        }

        public static Result failure(String message) {
            return new Result(false, message == null ? "Failed to open quest file" : message, null);
        }
    }
}
