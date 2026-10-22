package me.johardt.theseus.client;

import me.johardt.theseus.Theseus;

import java.io.Reader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** Reloadable Markdown content for the editor tutorial. */
public final class QuestTutorialContent {
    private static final String FALLBACK = """
        # Theseus editor tutorial

        Select lets you move and edit quests. Hand pans the graph, Add creates a quest, and Link connects a prerequisite to a dependent quest.

        Use the minimap to move around a large graph. Grid, snap-to-grid, and icon-size controls help keep new quests aligned.

        Quest and chapter context menus contain additional actions. Save sends your draft to the server for acknowledgement; a dirty editor means there are changes waiting to be saved.

        Resetting progress is safe for player progress. Editing a quest definition changes the quest itself, so save only when you intend to update the pack.
        """;
    private static final AtomicReference<String> ACTIVE = new AtomicReference<>(FALLBACK);

    private QuestTutorialContent() {}

    public static String text() {
        return ACTIVE.get();
    }

    public static String fallback() {
        return FALLBACK;
    }

    /** Reads one resource without allowing a broken pack to remove onboarding. */
    public static String read(Reader reader) {
        if (reader == null) return FALLBACK;
        try {
            String value = readFully(reader).strip();
            return value.isEmpty() ? FALLBACK : value;
        } catch (IOException | RuntimeException exception) {
            Theseus.LOGGER.warn("Could not read editor tutorial: {}", exception.getMessage());
            return FALLBACK;
        }
    }

    static void publish(String value) {
        ACTIVE.set(value == null || value.isBlank() ? FALLBACK : value);
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder value = new StringBuilder();
        char[] buffer = new char[1024];
        int read;
        while ((read = reader.read(buffer)) >= 0) value.append(buffer, 0, read);
        return value.toString();
    }
}
