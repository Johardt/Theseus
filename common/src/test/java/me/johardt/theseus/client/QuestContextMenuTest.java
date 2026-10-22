package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestContextMenuTest {
    @Test
    void layoutClampsToBothScreenEdgesAndKeepsSeparators() {
        List<QuestContextMenu.Entry> entries = List.of(
            QuestContextMenu.Entry.item("Open details", () -> {}),
            QuestContextMenu.Entry.separator(),
            QuestContextMenu.Entry.item("Delete", () -> {})
        );

        QuestContextMenu.Bounds bounds = QuestContextMenu.layoutBounds(500, 500, 240, 100, entries);
        assertTrue(bounds.x() >= 0);
        assertTrue(bounds.y() >= 0);
        assertTrue(bounds.maxX() <= 240);
        assertTrue(bounds.maxY() <= 100);
    }

    @Test
    void keyboardTraversalSkipsDisabledAndSeparatorsAndEnterActivates() {
        List<String> activated = new ArrayList<>();
        QuestContextMenu menu = new QuestContextMenu(2, 3, 300, 200, List.of(
            QuestContextMenu.Entry.item("Disabled", "", false, false, () -> activated.add("disabled")),
            QuestContextMenu.Entry.separator(),
            QuestContextMenu.Entry.item("First", "A", true, false, () -> activated.add("first")),
            QuestContextMenu.Entry.item("Danger", "", true, true, () -> activated.add("danger"))
        ), () -> activated.add("dismiss"));

        assertEquals(2, menu.selectedIndex());
        menu.keyPressed(QuestContextMenu.KEY_DOWN);
        assertEquals(3, menu.selectedIndex());
        menu.keyPressed(QuestContextMenu.KEY_RETURN);
        assertEquals(List.of("dismiss", "danger"), activated);
        assertFalse(menu.isOpen());
    }

    @Test
    void outsideClickDismissesAndConsumesTheClick() {
        QuestContextMenu menu = new QuestContextMenu(10, 10, 300, 200,
            List.of(QuestContextMenu.Entry.item("Open", () -> {})), () -> {});

        assertEquals(QuestContextMenu.Result.DISMISSED, menu.mouseClicked(0, 0, 0));
        assertFalse(menu.isOpen());
        assertEquals(QuestContextMenu.Result.IGNORED, menu.mouseClicked(15, 15, 0));
        assertTrue(menu.bounds().contains(10, 10));
    }
}
