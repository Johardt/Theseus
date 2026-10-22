package me.johardt.theseus.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestIconDefinitionTest {
    @Test
    void preservesCanonicalCustomAndLegacySources() {
        var customSource = JsonParser.parseString("""
            {"type":"example:animated","frames":["a","b"],"custom":{"keep":true}}
            """);
        QuestIconDefinition custom = QuestIconDefinition.parse(customSource, "minecraft:map");
        QuestIconDefinition legacy = QuestIconDefinition.parse(JsonParser.parseString("\"minecraft:diamond\""), "minecraft:map");

        assertEquals("example:animated", custom.type());
        assertEquals(customSource, custom.source());
        assertEquals(QuestIconDefinition.ITEM_TYPE, legacy.type());
        assertEquals("minecraft:diamond", legacy.item());
        assertTrue(legacy.configured());
    }

    @Test
    void missingIconUsesFallbackWithoutPretendingItWasConfigured() {
        QuestIconDefinition icon = QuestIconDefinition.parse(null, "minecraft:compass");

        assertEquals("minecraft:compass", icon.item());
        assertFalse(icon.configured());
    }
}
