package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheseusClientOptionsTest {
    @TempDir
    Path gameDirectory;

    @Test
    void missingFileUsesDefaults() throws Exception {
        TheseusClientOptions.load(gameDirectory);

        assertEquals(
            TheseusClientOptions.Preferences.DEFAULT,
            TheseusClientOptions.preferences()
        );
        assertTrue(Files.exists(optionsFile()));
        JsonObject defaults = JsonParser.parseString(
            Files.readString(optionsFile(), StandardCharsets.UTF_8)
        ).getAsJsonObject();
        assertEquals(3, defaults.get("schemaVersion").getAsInt());
        assertEquals("UNDOCKED", defaults.get("defaultMinimapMode").getAsString());
        assertFalse(defaults.get("disableMinimap").getAsBoolean());
        assertFalse(defaults.has("minimapMode"));
        assertEquals("TOP_LEFT", defaults.get("trackerAnchor").getAsString());
        assertFalse(defaults.get("trackerCollapsed").getAsBoolean());
    }

    @Test
    void newFormatRoundTrips() throws Exception {
        TheseusClientOptions.load(gameDirectory);
        TheseusClientOptions.setMaxEditorHistory(321);
        TheseusClientOptions.setDefaultMinimapMode(TheseusClientOptions.MinimapMode.DOCKED);
        TheseusClientOptions.setDisableMinimap(true);
        TheseusClientOptions.setMinimapPosition(0.25, 0.75);
        TheseusClientOptions.setShowGrid(true);
        TheseusClientOptions.setSnapToGrid(true);
        TheseusClientOptions.setTrackerAnchor(TheseusClientOptions.TrackerAnchor.BOTTOM_RIGHT);
        TheseusClientOptions.setTutorialAutoShow(false);
        TheseusClientOptions.setTutorialSeen(true);
        TheseusClientOptions.setTrackerCollapsed(true);

        TheseusClientOptions.load(gameDirectory);

        assertEquals(3, TheseusClientOptions.preferences().schemaVersion());
        assertEquals(321, TheseusClientOptions.maxEditorHistory());
        assertEquals(TheseusClientOptions.MinimapMode.DOCKED, TheseusClientOptions.defaultMinimapMode());
        assertTrue(TheseusClientOptions.disableMinimap());
        assertEquals(0.25, TheseusClientOptions.minimapX());
        assertEquals(0.75, TheseusClientOptions.minimapY());
        assertTrue(TheseusClientOptions.showGrid());
        assertTrue(TheseusClientOptions.snapToGrid());
        assertEquals(TheseusClientOptions.TrackerAnchor.BOTTOM_RIGHT, TheseusClientOptions.trackerAnchor());
        assertFalse(TheseusClientOptions.tutorialAutoShow());
        assertTrue(TheseusClientOptions.tutorialSeen());
        assertTrue(TheseusClientOptions.trackerCollapsed());
    }

    @Test
    void newFieldsWinAndMalformedFieldsFallBackIndependently() throws Exception {
        write("""
            {
              "maxEditorHistory": "not a number",
              "defaultMinimapMode": "not-a-mode",
              "disableMinimap": "not a boolean",
              "minimapX": 2.0,
              "minimapY": "bad",
              "showGrid": "not a boolean",
              "snapToGrid": true,
              "trackerAnchor": "not-an-anchor",
              "tutorialAutoShow": true,
              "tutorialSeen": "bad"
            }
            """);

        TheseusClientOptions.load(gameDirectory);

        assertEquals(100, TheseusClientOptions.maxEditorHistory());
        assertEquals(TheseusClientOptions.MinimapMode.UNDOCKED, TheseusClientOptions.defaultMinimapMode());
        assertFalse(TheseusClientOptions.disableMinimap());
        assertEquals(1.0, TheseusClientOptions.minimapX());
        assertEquals(1.0, TheseusClientOptions.minimapY());
        assertFalse(TheseusClientOptions.showGrid());
        assertTrue(TheseusClientOptions.snapToGrid());
        assertEquals(TheseusClientOptions.TrackerAnchor.TOP_LEFT, TheseusClientOptions.trackerAnchor());
        assertTrue(TheseusClientOptions.tutorialAutoShow());
        assertFalse(TheseusClientOptions.tutorialSeen());
        assertFalse(TheseusClientOptions.trackerCollapsed());
    }

    @Test
    void legacyMinimapModeIsMigratedToTheNewDefaultMode() throws Exception {
        write("""
            {
              "minimapMode": "hidden"
            }
            """);

        TheseusClientOptions.load(gameDirectory);

        assertEquals(TheseusClientOptions.MinimapMode.UNDOCKED, TheseusClientOptions.defaultMinimapMode());

        write("""
            {
              "minimapMode": "docked"
            }
            """);
        TheseusClientOptions.load(gameDirectory);

        assertEquals(TheseusClientOptions.MinimapMode.DOCKED, TheseusClientOptions.defaultMinimapMode());
    }

    @Test
    void numericValuesAreBounded() throws Exception {
        write("""
            {
              "maxEditorHistory": 99999,
              "minimapX": -10,
              "minimapY": 10
            }
            """);

        TheseusClientOptions.load(gameDirectory);

        assertEquals(1000, TheseusClientOptions.maxEditorHistory());
        assertEquals(0.0, TheseusClientOptions.minimapX());
        assertEquals(1.0, TheseusClientOptions.minimapY());
        assertEquals(TheseusClientOptions.TrackerAnchor.TOP_LEFT, TheseusClientOptions.trackerAnchor());
    }

    @Test
    void jsoncCommentsAreAccepted() throws Exception {
        write("""
            {
              // Resourceful Config uses comments for entry hints.
              "schemaVersion": 1,
              /* Keep this file editable without Resourceful Config. */
              "maxEditorHistory": 250
            }
            """);

        TheseusClientOptions.load(gameDirectory);

        assertEquals(250, TheseusClientOptions.maxEditorHistory());
    }

    @Test
    void unchangedSetterDoesNotRewrite() throws Exception {
        TheseusClientOptions.load(gameDirectory);
        TheseusClientOptions.setMaxEditorHistory(200);
        String before = Files.readString(optionsFile(), StandardCharsets.UTF_8);

        TheseusClientOptions.setMaxEditorHistory(200);

        assertEquals(before, Files.readString(optionsFile(), StandardCharsets.UTF_8));
    }

    @Test
    void saveProducesValidJson() throws Exception {
        TheseusClientOptions.load(gameDirectory);
        TheseusClientOptions.setSnapToGrid(true);

        JsonObject root = JsonParser.parseString(
            Files.readString(optionsFile(), StandardCharsets.UTF_8)
        ).getAsJsonObject();

        assertEquals(3, root.get("schemaVersion").getAsInt());
        assertEquals(true, root.get("snapToGrid").getAsBoolean());
        assertEquals(false, root.get("trackerCollapsed").getAsBoolean());
        assertNotEquals(0, root.size());
    }

    @Test
    void schemaTwoMigratesToCurrentAndDefaultsCollapseState() throws Exception {
        write("""
            {
              "schemaVersion": 2,
              "trackerAnchor": "BOTTOM_LEFT",
              "tutorialSeen": true
            }
            """);

        TheseusClientOptions.load(gameDirectory);

        assertEquals(3, TheseusClientOptions.preferences().schemaVersion());
        assertEquals(TheseusClientOptions.TrackerAnchor.BOTTOM_LEFT, TheseusClientOptions.trackerAnchor());
        assertTrue(TheseusClientOptions.tutorialSeen());
        assertFalse(TheseusClientOptions.trackerCollapsed());
    }

    private Path optionsFile() {
        return gameDirectory.resolve("config").resolve("theseus_options.jsonc");
    }

    private void write(String json) throws Exception {
        Files.createDirectories(optionsFile().getParent());
        Files.writeString(optionsFile(), json, StandardCharsets.UTF_8);
    }
}
