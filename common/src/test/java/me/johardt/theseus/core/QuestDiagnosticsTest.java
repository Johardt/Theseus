package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDiagnosticsTest {
    @Test
    void returnsEveryBlockingDiagnosticAndNonBlockingWarnings() {
        JsonObject quest = JsonParser.parseString("""
            {"display":{"icon":{"item":"missing:item"}},"tasks":{"bad":{"type":"theseus:item","item":"bad id","amount":0}}}
            """).getAsJsonObject();

        var diagnostics = QuestDiagnostics.validate("Bad ID", quest, item -> false);

        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals("invalid_quest_id")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.path().equals("display.icon.item")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.path().equals("tasks.bad.amount")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals("empty_rewards") && !diagnostic.blocksSave()));
    }

    @Test
    void detectsDuplicateKeysBeforeGsonCanDiscardThem() {
        assertEquals(java.util.List.of("$.tasks.item.type"), JsonDuplicateKeyDetector.findDuplicates("""
            {"tasks":{"item":{"type":"theseus:item","type":"theseus:check"}}}
            """));
    }

    @Test
    void detectsDuplicateKeysAfterDecodingEveryJsonStringEscape() {
        assertDuplicateKeyPair("letter", "\\u006cetter");
        assertDuplicateKeyPair("\\\"", "\\u0022");
        assertDuplicateKeyPair("\\\\", "\\u005c");
        assertDuplicateKeyPair("\\/", "/");
        assertDuplicateKeyPair("\\b", "\\u0008");
        assertDuplicateKeyPair("\\f", "\\u000c");
        assertDuplicateKeyPair("\\n", "\\u000a");
        assertDuplicateKeyPair("\\r", "\\u000d");
        assertDuplicateKeyPair("\\t", "\\u0009");

        String supplementaryCharacter = new String(Character.toChars(0x1F600));
        assertDuplicateKeyPair(supplementaryCharacter, "\\uD83D\\uDE00");

        String distinctUnicodeKeys = "{\"\\u0061\":0,\"\\u0062\":1}";
        assertTrue(JsonDuplicateKeyDetector.findDuplicates(distinctUnicodeKeys).isEmpty());
        assertEquals(2, JsonParser.parseString(distinctUnicodeKeys).getAsJsonObject().size());
    }

    @Test
    void rejectsMalformedJsonStringEscapesAndUnescapedControls() {
        List<String> malformed = List.of(
            "{\"bad\\q\":0}",
            "{\"bad\\u12\":0}",
            "{\"bad\\u12xz\":0}",
            "{\"bad" + '\\' + "\":0}",
            "{\"bad" + (char) 1 + "\":0}"
        );

        for (String source : malformed) {
            assertThrows(
                IllegalArgumentException.class,
                () -> JsonDuplicateKeyDetector.findDuplicates(source),
                source
            );
        }
    }

    private static void assertDuplicateKeyPair(String firstEncodedKey, String secondEncodedKey) {
        String source = "{\"" + firstEncodedKey + "\":0,\"" + secondEncodedKey + "\":1}";
        assertEquals(1, JsonDuplicateKeyDetector.findDuplicates(source).size(), source);
    }

    @Test
    void acceptsMaximumSupportedCompositeTaskScopesWithinRawJsonLimit() {
        JsonObject root = new JsonObject();
        JsonObject display = new JsonObject();
        display.addProperty("title", "Deep quest");
        root.add("display", display);
        JsonObject tasks = new JsonObject();
        root.add("tasks", tasks);
        root.add("rewards", new JsonObject());

        for (int depth = 1; depth < QuestDiagnostics.MAX_NESTING_DEPTH; depth++) {
            JsonObject composite = new JsonObject();
            composite.addProperty("type", "theseus:composite");
            composite.addProperty("amount", 1);
            JsonObject children = new JsonObject();
            composite.add("tasks", children);
            tasks.add("level" + depth, composite);
            tasks = children;
        }
        JsonObject leaf = new JsonObject();
        leaf.addProperty("type", "theseus:dummy");
        leaf.addProperty("value", "deep_leaf");
        tasks.add("leaf", leaf);

        String source = root.toString();
        JsonObject parsed = JsonParser.parseString(source).getAsJsonObject();

        assertTrue(JsonDuplicateKeyDetector.findDuplicates(source).isEmpty());
        assertFalse(QuestDiagnostics.validate("deep_quest", parsed).stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("nesting_too_deep")));
    }

    @Test
    void boundsRawObjectAndArrayDepthBeforeRecursiveParsing() {
        String atLimit = "[".repeat(JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH)
            + "0"
            + "]".repeat(JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH);
        assertTrue(JsonDuplicateKeyDetector.findDuplicates(atLimit).isEmpty());

        String beyondLimit = "[".repeat(JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH + 1)
            + "0"
            + "]".repeat(JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH + 1);
        JsonDuplicateKeyDetector.NestingLimitException exception = assertThrows(
            JsonDuplicateKeyDetector.NestingLimitException.class,
            () -> JsonDuplicateKeyDetector.findDuplicates(beyondLimit)
        );
        assertEquals(
            "JSON nesting exceeds the maximum raw depth of " + JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH,
            exception.getMessage()
        );

        String nestedObjects = "{\"child\":".repeat(JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH + 1)
            + "0"
            + "}".repeat(JsonDuplicateKeyDetector.MAX_RAW_NESTING_DEPTH + 1);
        assertThrows(
            JsonDuplicateKeyDetector.NestingLimitException.class,
            () -> JsonDuplicateKeyDetector.findDuplicates(nestedObjects)
        );
    }

    @Test
    void boundsDuplicatePathLengthAndRetainedDuplicateCount() {
        String longKey = "k".repeat(JsonDuplicateKeyDetector.MAX_DUPLICATE_PATH_LENGTH + 100);
        String longPathDocument = "{\"outer\":{\"" + longKey + "\":0,\"" + longKey + "\":1}}";
        String longPath = JsonDuplicateKeyDetector.findDuplicates(longPathDocument).getFirst();
        assertTrue(longPath.length() <= JsonDuplicateKeyDetector.MAX_DUPLICATE_PATH_LENGTH);
        assertTrue(longPath.endsWith("…[path truncated]"));

        int duplicateCount = JsonDuplicateKeyDetector.MAX_RETAINED_DUPLICATE_PATHS + 5;
        String repeatedKey = "{\"same\":0" + ",\"same\":0".repeat(duplicateCount) + "}";
        var duplicatePaths = JsonDuplicateKeyDetector.findDuplicates(repeatedKey);
        assertEquals(JsonDuplicateKeyDetector.MAX_RETAINED_DUPLICATE_PATHS, duplicatePaths.size());
        assertEquals("$.same", duplicatePaths.getFirst());
        assertEquals("$ [additional duplicate paths omitted]", duplicatePaths.getLast());
    }

    @Test
    void rejectsCompositeDepthPastDefensiveLimit() {
        JsonObject root = new JsonObject();
        JsonObject tasks = new JsonObject(); root.add("tasks", tasks);
        JsonObject current = tasks;
        for (int depth = 0; depth <= QuestDiagnostics.MAX_NESTING_DEPTH; depth++) {
            JsonObject composite = new JsonObject(); composite.addProperty("type", "theseus:composite"); composite.addProperty("amount", 1);
            JsonObject children = new JsonObject(); composite.add("tasks", children); current.add("child" + depth, composite); current = children;
        }
        assertTrue(QuestDiagnostics.validate("quest", root).stream().anyMatch(diagnostic -> diagnostic.code().equals("nesting_too_deep")));
    }

    @Test
    void diagnosticWireFormatRemainsValidWhenBounded() {
        var diagnostics = java.util.List.of(
            new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "bad", "quest", "tasks.a", "A recoverable message", "Fix it"),
            new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.WARNING, "warn", "quest", "tasks.b", "Another message", null)
        );
        var decoded = QuestDiagnostics.decode(QuestDiagnostics.encode(diagnostics, 180));
        assertTrue(!decoded.isEmpty());
        assertEquals("bad", decoded.getFirst().code());
    }

    @Test
    void customIconsRemainNonBlockingAndItemOverridesAreValidatedRecursively() {
        JsonObject quest = JsonParser.parseString("""
            {
              "display":{"title":"Icons","icon":{"type":"example:animated","frames":[1]}},
              "tasks":{"outer":{"type":"theseus:composite","amount":1,"tasks":{
                "child":{"type":"theseus:check","icon":{"type":"theseus:item","item":"missing:item"}}
              }}},
              "rewards":{}
            }
            """).getAsJsonObject();

        var diagnostics = QuestDiagnostics.validate("icons", quest, item -> !item.startsWith("missing:"));

        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("unknown_icon_type") && !value.blocksSave()));
        assertTrue(diagnostics.stream().anyMatch(value -> value.path().equals("tasks.outer.tasks.child.icon.item") && value.blocksSave()));
    }

    @Test
    void iconSizeDiagnosticsBlockInvalidImportsAndAcceptTheInclusiveRange() {
        JsonObject invalid = JsonParser.parseString("""
            {"display":{"title":"Icons","icon_size":65},"tasks":{},"rewards":{}}
            """).getAsJsonObject();
        var diagnostics = QuestDiagnostics.validate("icons", invalid);
        var size = diagnostics.stream().filter(value -> value.code().equals("invalid_icon_size")).findFirst().orElseThrow();
        assertTrue(size.blocksSave());
        assertEquals("display.icon_size", size.path());

        JsonObject valid = JsonParser.parseString("""
            {"display":{"title":"Icons","icon_size":8},"tasks":{},"rewards":{}}
            """).getAsJsonObject();
        assertTrue(QuestDiagnostics.validate("icons", valid).stream().noneMatch(value -> value.code().equals("invalid_icon_size")));
    }

    @Test
    void legacyDisplayValidationUsesTheSameIconSizeDiagnostic() {
        JsonObject draft = JsonParser.parseString("""
            {"title":"Icons","icon_size":7}
            """).getAsJsonObject();

        var diagnostics = QuestDiagnostics.validateDisplay(draft, null, ignored -> true);
        var size = diagnostics.stream().filter(value -> value.code().equals("invalid_icon_size")).findFirst().orElseThrow();
        assertEquals("display.icon_size", size.path());
        assertTrue(size.blocksSave());
    }
}
