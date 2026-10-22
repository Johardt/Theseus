package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTutorialTest {
    @Test
    void autoShowRequiresEditorAccessAndAnUnseenTutorial() {
        assertTrue(QuestTutorial.shouldAutoShow(true, true, false));
        assertFalse(QuestTutorial.shouldAutoShow(false, true, false));
        assertFalse(QuestTutorial.shouldAutoShow(true, false, false));
        assertFalse(QuestTutorial.shouldAutoShow(true, true, true));
    }

    @Test
    void unreadableOrEmptyResourceFallsBackToBundledContent() {
        assertEquals(QuestTutorialContent.fallback(), QuestTutorialContent.read(null));
        assertEquals(QuestTutorialContent.fallback(), QuestTutorialContent.read(new StringReader("  ")));
        assertEquals("# Custom tutorial", QuestTutorialContent.read(new StringReader("# Custom tutorial")));
    }
}
