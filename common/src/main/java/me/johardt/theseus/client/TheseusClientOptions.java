package me.johardt.theseus.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/** Durable client-only preferences. The file format is private and versioned. */
public final class TheseusClientOptions {
    public static final int CURRENT_SCHEMA_VERSION = 3;
    public static final int DEFAULT_MAX_EDITOR_HISTORY = 100;

    private static final String CONFIG_DIRECTORY = "config";
    private static final String FILE_NAME = "theseus_options.jsonc";
    private static final double DEFAULT_MINIMAP_X = 1.0;
    private static final double DEFAULT_MINIMAP_Y = 1.0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = Logger.getLogger(TheseusClientOptions.class.getName());

    private static Preferences preferences = Preferences.DEFAULT;
    private static Path file;

    private TheseusClientOptions() {}

    public enum MinimapMode {
        DOCKED,
        UNDOCKED
    }

    public enum TrackerAnchor {
        TOP_LEFT,
        UPPER_LEFT,
        LOWER_LEFT,
        BOTTOM_LEFT,
        TOP_RIGHT,
        UPPER_RIGHT,
        LOWER_RIGHT,
        BOTTOM_RIGHT
    }

    /** A validated snapshot of all preferences exposed to later client features. */
    public record Preferences(
        int schemaVersion,
        int maxEditorHistory,
        MinimapMode defaultMinimapMode,
        boolean disableMinimap,
        double minimapX,
        double minimapY,
        boolean showGrid,
        boolean snapToGrid,
        TrackerAnchor trackerAnchor,
        boolean tutorialAutoShow,
        boolean tutorialSeen,
        boolean trackerCollapsed
    ) {
        /** Compatibility constructor for callers written against schema 2. */
        public Preferences(
            int schemaVersion,
            int maxEditorHistory,
            MinimapMode defaultMinimapMode,
            boolean disableMinimap,
            double minimapX,
            double minimapY,
            boolean showGrid,
            boolean snapToGrid,
            TrackerAnchor trackerAnchor,
            boolean tutorialAutoShow,
            boolean tutorialSeen
        ) {
            this(
                schemaVersion,
                maxEditorHistory,
                defaultMinimapMode,
                disableMinimap,
                minimapX,
                minimapY,
                showGrid,
                snapToGrid,
                trackerAnchor,
                tutorialAutoShow,
                tutorialSeen,
                false
            );
        }

        public static final Preferences DEFAULT = new Preferences(
            CURRENT_SCHEMA_VERSION,
            DEFAULT_MAX_EDITOR_HISTORY,
            MinimapMode.UNDOCKED,
            false,
            DEFAULT_MINIMAP_X,
            DEFAULT_MINIMAP_Y,
            false,
            false,
            TrackerAnchor.TOP_LEFT,
            true,
            false,
            false
        );

        public Preferences {
            schemaVersion = schemaVersion < CURRENT_SCHEMA_VERSION
                ? CURRENT_SCHEMA_VERSION
                : schemaVersion;
            maxEditorHistory = boundedHistory(maxEditorHistory);
            defaultMinimapMode = defaultMinimapMode == null ? MinimapMode.UNDOCKED : defaultMinimapMode;
            minimapX = normalizedPosition(minimapX, DEFAULT_MINIMAP_X);
            minimapY = normalizedPosition(minimapY, DEFAULT_MINIMAP_Y);
            trackerAnchor = trackerAnchor == null ? TrackerAnchor.TOP_LEFT : trackerAnchor;
        }

        private static int boundedHistory(int value) {
            return Math.max(10, Math.min(1000, value));
        }

        private static double normalizedPosition(double value, double fallback) {
            if (!Double.isFinite(value)) return fallback;
            return Math.max(0.0, Math.min(1.0, value));
        }

        public Preferences withMaxEditorHistory(int value) {
            return new Preferences(
                schemaVersion, value, defaultMinimapMode, disableMinimap, minimapX, minimapY,
                showGrid, snapToGrid, trackerAnchor, tutorialAutoShow, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withDefaultMinimapMode(MinimapMode value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, value, disableMinimap, minimapX, minimapY,
                showGrid, snapToGrid, trackerAnchor, tutorialAutoShow, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withDisableMinimap(boolean value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, value, minimapX, minimapY,
                showGrid, snapToGrid, trackerAnchor, tutorialAutoShow, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withMinimapPosition(double x, double y) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, disableMinimap, x, y,
                showGrid, snapToGrid, trackerAnchor, tutorialAutoShow, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withShowGrid(boolean value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, disableMinimap, minimapX, minimapY,
                value, snapToGrid, trackerAnchor, tutorialAutoShow, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withSnapToGrid(boolean value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, disableMinimap, minimapX, minimapY,
                showGrid, value, trackerAnchor, tutorialAutoShow, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withTrackerAnchor(TrackerAnchor value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, disableMinimap, minimapX, minimapY,
                showGrid, snapToGrid, value, tutorialAutoShow, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withTutorialAutoShow(boolean value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, disableMinimap, minimapX, minimapY,
                showGrid, snapToGrid, trackerAnchor, value, tutorialSeen, trackerCollapsed
            );
        }

        public Preferences withTutorialSeen(boolean value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, disableMinimap, minimapX, minimapY,
                showGrid, snapToGrid, trackerAnchor, tutorialAutoShow, value, trackerCollapsed
            );
        }

        public Preferences withTrackerCollapsed(boolean value) {
            return new Preferences(
                schemaVersion, maxEditorHistory, defaultMinimapMode, disableMinimap, minimapX, minimapY,
                showGrid, snapToGrid, trackerAnchor, tutorialAutoShow, tutorialSeen, value
            );
        }
    }

    public static synchronized void load(Path gameDirectory) {
        file = gameDirectory.resolve(CONFIG_DIRECTORY).resolve(FILE_NAME);
        preferences = Preferences.DEFAULT;
        if (!Files.exists(file)) {
            if (!ResourcefulConfigBridge.isAvailable()) save();
            return;
        }

        try {
            JsonElement parsed = JsonParser.parseString(stripJsonComments(
                Files.readString(file, StandardCharsets.UTF_8)
            ));
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("root value is not an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            preferences = read(root);
        } catch (Exception exception) {
            LOGGER.warning("Could not read client options " + file + ": " + exception.getMessage());
        }
    }

    public static synchronized Preferences preferences() {
        return preferences;
    }

    public static synchronized int maxEditorHistory() {
        return preferences.maxEditorHistory();
    }

    public static synchronized MinimapMode defaultMinimapMode() {
        return preferences.defaultMinimapMode();
    }

    public static synchronized boolean disableMinimap() {
        return preferences.disableMinimap();
    }

    public static synchronized double minimapX() {
        return preferences.minimapX();
    }

    public static synchronized double minimapY() {
        return preferences.minimapY();
    }

    public static synchronized double floatingMinimapX() {
        return minimapX();
    }

    public static synchronized double floatingMinimapY() {
        return minimapY();
    }

    public static synchronized boolean showGrid() {
        return preferences.showGrid();
    }

    public static synchronized boolean snapToGrid() {
        return preferences.snapToGrid();
    }

    public static synchronized TrackerAnchor trackerAnchor() {
        return preferences.trackerAnchor();
    }

    public static synchronized boolean tutorialAutoShow() {
        return preferences.tutorialAutoShow();
    }

    public static synchronized boolean tutorialSeen() {
        return preferences.tutorialSeen();
    }

    public static synchronized boolean trackerCollapsed() {
        return preferences.trackerCollapsed();
    }

    public static synchronized void setMaxEditorHistory(int value) {
        update(preferences.withMaxEditorHistory(value));
    }

    public static synchronized void setDefaultMinimapMode(MinimapMode value) {
        update(preferences.withDefaultMinimapMode(value));
    }

    public static synchronized void setDisableMinimap(boolean value) {
        update(preferences.withDisableMinimap(value));
    }

    public static synchronized void setMinimapPosition(double x, double y) {
        update(preferences.withMinimapPosition(x, y));
    }

    public static synchronized void setFloatingMinimapPosition(double x, double y) {
        setMinimapPosition(x, y);
    }

    public static synchronized void setMinimapX(double value) {
        setMinimapPosition(value, preferences.minimapY());
    }

    public static synchronized void setMinimapY(double value) {
        setMinimapPosition(preferences.minimapX(), value);
    }

    public static synchronized void setShowGrid(boolean value) {
        update(preferences.withShowGrid(value));
    }

    public static synchronized void setSnapToGrid(boolean value) {
        update(preferences.withSnapToGrid(value));
    }

    public static synchronized void setTrackerAnchor(TrackerAnchor value) {
        update(preferences.withTrackerAnchor(value));
    }

    public static synchronized void setTutorialAutoShow(boolean value) {
        update(preferences.withTutorialAutoShow(value));
    }

    public static synchronized void setTutorialSeen(boolean value) {
        update(preferences.withTutorialSeen(value));
    }

    public static synchronized void setTrackerCollapsed(boolean value) {
        update(preferences.withTrackerCollapsed(value));
    }

    /** Applies values loaded by an optional configuration provider. */
    public static synchronized void applyExternalPreferences(Preferences value) {
        if (value == null || preferences.equals(value)) return;
        preferences = value;
    }

    /** Explicit save is useful when a later screen batches several preference changes. */
    public static synchronized void save() {
        if (file == null) return;

        Path temporary = file.resolveSibling(
            file.getFileName() + ".tmp-" + UUID.randomUUID()
        );
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                temporary,
                GSON.toJson(toJson(preferences)),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            LOGGER.warning("Could not save client options " + file + ": " + exception.getMessage());
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException exception) {
                LOGGER.fine("Could not remove temporary client options file " + temporary + ": " + exception.getMessage());
            }
        }
    }

    private static void update(Preferences updated) {
        if (preferences.equals(updated)) return;
        preferences = updated;
        if (!ResourcefulConfigBridge.save(updated)) save();
    }

    private static Preferences read(JsonObject root) {
        return new Preferences(
            readInt(root, "schemaVersion", CURRENT_SCHEMA_VERSION),
            readInt(root, "maxEditorHistory", Preferences.DEFAULT.maxEditorHistory()),
            readDefaultMinimapMode(root),
            readBoolean(root, "disableMinimap", Preferences.DEFAULT.disableMinimap()),
            readDouble(root, "minimapX", Preferences.DEFAULT.minimapX()),
            readDouble(root, "minimapY", Preferences.DEFAULT.minimapY()),
            readBoolean(root, "showGrid", Preferences.DEFAULT.showGrid()),
            readBoolean(root, "snapToGrid", Preferences.DEFAULT.snapToGrid()),
            readEnum(root, "trackerAnchor", TrackerAnchor.class, Preferences.DEFAULT.trackerAnchor()),
            readBoolean(root, "tutorialAutoShow", Preferences.DEFAULT.tutorialAutoShow()),
            readBoolean(root, "tutorialSeen", Preferences.DEFAULT.tutorialSeen()),
            readBoolean(root, "trackerCollapsed", Preferences.DEFAULT.trackerCollapsed())
        );
    }

    private static JsonObject toJson(Preferences value) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", value.schemaVersion());
        root.addProperty("maxEditorHistory", value.maxEditorHistory());
        root.addProperty("defaultMinimapMode", value.defaultMinimapMode().name());
        root.addProperty("disableMinimap", value.disableMinimap());
        root.addProperty("minimapX", value.minimapX());
        root.addProperty("minimapY", value.minimapY());
        root.addProperty("showGrid", value.showGrid());
        root.addProperty("snapToGrid", value.snapToGrid());
        root.addProperty("trackerAnchor", value.trackerAnchor().name());
        root.addProperty("tutorialAutoShow", value.tutorialAutoShow());
        root.addProperty("tutorialSeen", value.tutorialSeen());
        root.addProperty("trackerCollapsed", value.trackerCollapsed());
        return root;
    }

    private static MinimapMode readDefaultMinimapMode(JsonObject root) {
        if (root.has("defaultMinimapMode")) {
            return readEnum(
                root,
                "defaultMinimapMode",
                MinimapMode.class,
                Preferences.DEFAULT.defaultMinimapMode()
            );
        }
        if (!root.has("minimapMode")) return Preferences.DEFAULT.defaultMinimapMode();
        try {
            JsonElement element = root.get("minimapMode");
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException("not a string");
            return switch (element.getAsString().toUpperCase(Locale.ROOT)) {
                case "DOCKED" -> MinimapMode.DOCKED;
                case "FLOATING", "HIDDEN" -> MinimapMode.UNDOCKED;
                default -> throw new IllegalArgumentException("unknown minimap mode");
            };
        } catch (Exception exception) {
            logMalformed("minimapMode", Preferences.DEFAULT.defaultMinimapMode(), exception);
            return Preferences.DEFAULT.defaultMinimapMode();
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        if (!root.has(key)) return fallback;
        try {
            JsonElement element = root.get(key);
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException("not a number");
            BigDecimal number = new BigDecimal(element.getAsString());
            if (number.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("not an integer");
            }
            return number.intValueExact();
        } catch (Exception exception) {
            logMalformed(key, fallback, exception);
            return fallback;
        }
    }

    private static double readDouble(JsonObject root, String key, double fallback) {
        if (!root.has(key)) return fallback;
        try {
            JsonElement element = root.get(key);
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException("not a number");
            double value = Double.parseDouble(element.getAsString());
            if (!Double.isFinite(value)) throw new IllegalArgumentException("not finite");
            return value;
        } catch (Exception exception) {
            logMalformed(key, fallback, exception);
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        if (!root.has(key)) return fallback;
        try {
            JsonElement element = root.get(key);
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException("not a boolean");
            String value = element.getAsString().toLowerCase(Locale.ROOT);
            if (!value.equals("true") && !value.equals("false")) {
                throw new IllegalArgumentException("not true or false");
            }
            return Boolean.parseBoolean(value);
        } catch (Exception exception) {
            logMalformed(key, fallback, exception);
            return fallback;
        }
    }

    private static <T extends Enum<T>> T readEnum(
        JsonObject root,
        String key,
        Class<T> type,
        T fallback
    ) {
        if (!root.has(key)) return fallback;
        try {
            JsonElement element = root.get(key);
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException("not a string");
            return Enum.valueOf(type, element.getAsString().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            logMalformed(key, fallback, exception);
            return fallback;
        }
    }

    private static void logMalformed(String key, Object fallback, Exception exception) {
        LOGGER.warning(
            "Ignoring malformed client option '" + key + "'; using " + fallback +
                " (" + exception.getMessage() + ")"
        );
    }

    private static String stripJsonComments(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean inString = false;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    result.append(current);
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    index++;
                } else if (current == '\n') {
                    result.append(current);
                }
                continue;
            }
            if (inString) {
                result.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') {
                inString = true;
                result.append(current);
            } else if (current == '/' && next == '/') {
                lineComment = true;
                index++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
}
