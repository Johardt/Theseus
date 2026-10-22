package me.johardt.theseus.core;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDraftTest {
    @Test
    void patchesPreserveUnknownFieldsAndNullRemovesAField() {
        QuestDraft draft = QuestDraft.open(JsonParser.parseString("""
            {"display":{"title":"Quest"},"custom":{"keep":true}}
            """).getAsJsonObject());
        draft.applyPatch(JsonParser.parseString("{\"display\":{\"title\":\"Edited\"}}").getAsJsonObject());
        draft.remove("display");

        assertTrue(draft.snapshot().has("custom"));
        assertFalse(draft.snapshot().has("display"));
        assertTrue(draft.isDirty());
    }

    @Test
    void copiesPreserveBaselineAndNestedEditsCommitTransactionally() {
        QuestDraft draft = QuestDraft.open("quest", JsonParser.parseString("""
            {"tasks":{"outer":{"type":"theseus:composite","tasks":{"child":{"type":"example:task","custom":{"keep":true}}}}},"custom":{"keep":true}}
            """).getAsJsonObject());
        QuestPath child = QuestPath.root().field("tasks").key("outer").field("tasks").key("child");

        QuestDraft.EditSession edit = draft.edit(child);
        edit.value().getAsJsonObject().addProperty("changed", true);
        edit.cancel();
        assertFalse(draft.isDirty());

        edit = draft.edit(child);
        var changed = edit.value();
        changed.getAsJsonObject().addProperty("changed", true);
        edit.replace(changed);
        edit.commit();

        assertTrue(draft.isDirty());
        assertTrue(draft.get(child).getAsJsonObject().get("custom").getAsJsonObject().get("keep").getAsBoolean());
        assertTrue(draft.get(child).getAsJsonObject().get("changed").getAsBoolean());
        assertFalse(draft.copy().baseline().equals(draft.copy().snapshot()));
    }

    @Test
    void runtimeEnvelopeIsStrippedButUnknownAuthoredFieldsSurvive() {
        QuestDraft draft = QuestDraft.fromClientSnapshot("quest", JsonParser.parseString("""
            {"display":{"title":"Quest"},"custom":{"keep":true},"progress":{"task":2},"complete":true,"claimed_rewards":["reward"],"__editor_types":{}}
            """).getAsJsonObject());

        assertTrue(draft.snapshot().has("custom"));
        assertFalse(draft.snapshot().has("progress"));
        assertFalse(draft.snapshot().has("complete"));
        assertFalse(draft.snapshot().has("claimed_rewards"));
        assertFalse(draft.snapshot().has("__editor_types"));
        assertFalse(draft.transferSnapshot().has("claimed_rewards"));
    }

    @Test
    void clientSnapshotIsDetachedFromTheSourceDocument() {
        JsonObject source = JsonParser.parseString("""
            {"display":{"title":"Quest"},"custom":{"keep":true},"progress":{"task":2}}
            """).getAsJsonObject();
        QuestDraft draft = QuestDraft.fromClientSnapshot("quest", source);

        draft.apply("custom", JsonParser.parseString("""
            {"keep":false}
            """).getAsJsonObject());

        assertTrue(source.getAsJsonObject("custom").get("keep").getAsBoolean());
        assertFalse(draft.transferSnapshot().has("progress"));
    }

    @Test
    void settingAliasesAreUpdatedWithoutRewritingTheirSpelling() {
        QuestDraft draft = QuestDraft.open("quest", JsonParser.parseString("""
            {"settings":{"unlock_notification":false,"custom_setting":{"keep":true}}}
            """).getAsJsonObject());
        draft.setSettings(false, QuestDefinition.Visibility.LOCKED, true, true, false, true);

        JsonObject settings = draft.snapshot().getAsJsonObject("settings");
        assertTrue(settings.get("unlock_notification").getAsBoolean());
        assertTrue(settings.has("custom_setting"));
        assertFalse(settings.has("unlockNotification"));
    }

    @Test
    void reorderIsARealChangeAndIsEncodedAsStructuredData() {
        QuestDraft draft = QuestDraft.open("quest", JsonParser.parseString("""
            {"tasks":{"a":{"type":"example:a"},"b":{"type":"example:b"}}}
            """).getAsJsonObject());
        draft.moveKey(QuestPath.root().field("tasks"), "b", 0);

        assertTrue(draft.isDirty());
        assertEquals("b", draft.snapshot().getAsJsonObject("tasks").entrySet().iterator().next().getKey());
        assertTrue(draft.changes().toJson().toString().contains("reorder"));
    }

    @Test
    void updateMutationMergesOnlyChangedPathsIntoTheLatestDocument() {
        QuestDraft draft = QuestDraft.open("quest", JsonParser.parseString("""
            {"display":{"title":"Original","custom":{"server":true}},"custom":{"keep":true}}
            """).getAsJsonObject());
        draft.replace(QuestPath.root().field("display").field("title"), JsonParser.parseString("\"Edited\""));

        JsonObject latest = JsonParser.parseString("""
            {"display":{"title":"Original","custom":{"server":false}},"custom":{"keep":true,"new":1}}
            """).getAsJsonObject();
        JsonObject merged = QuestDraft.merge(latest, draft.snapshot(), draft.changes().toJson());

        assertEquals("Edited", merged.getAsJsonObject("display").get("title").getAsString());
        assertFalse(merged.getAsJsonObject("display").getAsJsonObject("custom").get("server").getAsBoolean());
        assertTrue(merged.getAsJsonObject("custom").has("new"));
    }

    @Test
    void basicDisplayEditsDoNotNormalizeUntouchedDescriptionOrIcon() {
        JsonObject source = JsonParser.parseString("""
            {"display":{"title":"Original","description":{"legacy":"keep"},
              "icon":{"type":"example:animated","frames":[1,2]}}}
            """).getAsJsonObject();
        QuestDraft draft = QuestDraft.open("quest", source);

        draft.setDisplayBasics("Edited", null, null, null);

        JsonObject display = draft.snapshot().getAsJsonObject("display");
        assertEquals(source.getAsJsonObject("display").get("description"), display.get("description"));
        assertEquals(source.getAsJsonObject("display").get("icon"), display.get("icon"));
    }

    @Test
    void explicitDescriptionAndIconEditsReplaceWholeSubtrees() {
        QuestDraft draft = QuestDraft.open("quest", JsonParser.parseString("""
            {"display":{"description":{"legacy":"old"},"icon":{"type":"example:old","keep":true}}}
            """).getAsJsonObject());

        draft.setDescription("first\n\nlast\n");
        draft.setIcon(QuestIconDefinition.item("minecraft:diamond").source());

        JsonObject display = draft.snapshot().getAsJsonObject("display");
        assertEquals(4, display.getAsJsonArray("description").size());
        assertEquals("theseus:item", display.getAsJsonObject("icon").get("type").getAsString());
        assertFalse(display.getAsJsonObject("icon").has("keep"));
    }

    @Test
    void iconSizePatchIsExplicitAndLeavesUnknownDisplayDataUntouched() {
        QuestDraft absent = QuestDraft.open("quest", JsonParser.parseString("""
            {"display":{"title":"Quest","custom":{"keep":true}}}
            """).getAsJsonObject());
        absent.setDisplayBasics("Edited", null, null, null);
        assertFalse(absent.snapshot().getAsJsonObject("display").has("icon_size"));
        absent.setIconSize(32);
        assertEquals(32, absent.snapshot().getAsJsonObject("display").get("icon_size").getAsInt());
        assertTrue(absent.snapshot().getAsJsonObject("display").getAsJsonObject("custom").get("keep").getAsBoolean());

        QuestDraft present = QuestDraft.open("quest", JsonParser.parseString("""
            {"display":{"icon_size":24,"custom":{"keep":true}}}
            """).getAsJsonObject());
        present.setDisplayBasics("Edited", null, null, null);
        assertEquals(24, present.snapshot().getAsJsonObject("display").get("icon_size").getAsInt());
    }
}
