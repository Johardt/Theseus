package me.johardt.theseus.client;

import me.johardt.theseus.core.QuestDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestModeTest {
    @Test
    void playModeCannotAccidentallySelectAnEditorTool() {
        QuestMode mode = new PlayMode();

        mode.setEditorTool(EditorTool.LINK);

        assertFalse(mode.isAuthoring());
        assertEquals(EditorTool.SELECT, mode.editorTool());
    }

    @Test
    void authorModeCopiesDraftAndToolStateTogether() {
        AuthorMode mode = new AuthorMode(16);
        mode.begin(QuestDraft.create(null));
        mode.id = "copied_quest";
        mode.setEditorTool(EditorTool.LINK);

        AuthorMode copy = mode.copy();
        copy.title = "Changed in copy";
        copy.groups.addProperty("Copied", true);

        assertTrue(copy.isAuthoring());
        assertEquals(EditorTool.LINK, copy.editorTool());
        assertEquals("copied_quest", copy.id);
        assertEquals("", mode.title);
        assertFalse(mode.groups.has("Copied"));
    }
}
