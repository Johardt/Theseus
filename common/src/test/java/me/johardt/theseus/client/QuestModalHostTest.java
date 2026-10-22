package me.johardt.theseus.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestModalHostTest {
    @Test
    void replacingOrReopeningTheSameModalCreatesANewInputLifetime() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.RAW_INSPECTOR);
        Object first = host.inputLifetime();
        host.open(QuestModalHost.Modal.RAW_INSPECTOR);
        org.junit.jupiter.api.Assertions.assertSame(first, host.inputLifetime());
        host.replace(QuestModalHost.Modal.RAW_INSPECTOR);
        org.junit.jupiter.api.Assertions.assertNotSame(first, host.inputLifetime());
        Object replacement = host.inputLifetime();
        host.close();
        host.open(QuestModalHost.Modal.RAW_INSPECTOR);
        org.junit.jupiter.api.Assertions.assertNotSame(replacement, host.inputLifetime());
    }

    @Test
    void rawInspectorIsAnExclusiveModalLayerAndSurvivesScreenRebuildCopies() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.EDITOR);
        host.open(QuestModalHost.Modal.RAW_INSPECTOR);

        QuestModalHost copy = host.copy();

        assertTrue(copy.blocksInput());
        assertEquals(QuestModalHost.Modal.RAW_INSPECTOR, copy.active());
    }

    @Test
    void closingAChildOverlayReturnsToItsParentAndRequestsFocus() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.TASK_EDITOR);
        assertTrue(host.consumeFocusRestoreRequest());

        host.open(QuestModalHost.Modal.PICKER);
        assertEquals(QuestModalHost.Modal.TASK_EDITOR, host.parent());
        assertTrue(host.rendersOverlay());

        host.close();
        assertEquals(QuestModalHost.Modal.TASK_EDITOR, host.active());
        assertTrue(host.consumeFocusRestoreRequest());
    }

    @Test
    void dirtyDismissalIsHandledAtTheOverlaySeam() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.EDITOR);
        host.consumeFocusRestoreRequest();
        var discarded = new boolean[1];

        assertTrue(host.requestDismissal(true, () -> discarded[0] = true));
        assertEquals(QuestModalHost.Modal.DISCARD_CONFIRMATION, host.active());
        assertTrue(host.hasPendingDismissal());

        host.cancelDismissal();
        assertEquals(QuestModalHost.Modal.EDITOR, host.active());
        assertFalse(discarded[0]);

        assertTrue(host.requestDismissal(true, () -> discarded[0] = true));
        assertTrue(host.confirmDismissal());
        assertTrue(discarded[0]);
        assertEquals(QuestModalHost.Modal.EDITOR, host.active());
    }

    @Test
    void inlineChooserBlocksInputWithoutReplacingTheParentWidgetTree() {
        QuestModalHost host = new QuestModalHost();
        host.open(QuestModalHost.Modal.TASK_CHOOSER);

        assertTrue(host.blocksInput());
        assertFalse(host.rendersOverlay());
        assertFalse(host.ownsWidgetTree());
    }

    @Test
    void progressResetConfirmationIsARealModalWithAnImmutableTarget() {
        QuestModalHost host = new QuestModalHost();
        QuestModalHost.ProgressResetTarget target = new QuestModalHost.ProgressResetTarget(
            "task", "quest", "Quest title", "outer/leaf", "Leaf"
        );
        host.open(QuestModalHost.Modal.PROGRESS_RESET_CONFIRMATION);

        assertTrue(host.blocksInput());
        assertEquals("outer/leaf", target.entryId());
        assertEquals("Leaf", target.displayLabel());
    }

    @Test
    void inputPolicyRoutesEscapeAndTrapsTransientChooserKeys() {
        QuestModalHost host = new QuestModalHost();

        assertEquals(QuestModalHost.Outcome.PASS, host.handles(65));

        host.open(QuestModalHost.Modal.TASK_CHOOSER);
        assertEquals(QuestModalHost.Outcome.CONSUMED, host.handles(65));
        assertEquals(QuestModalHost.Outcome.CLOSE, host.handles(QuestModalHost.ESCAPE_KEY));

        host.replace(QuestModalHost.Modal.TASK_EDITOR);
        assertEquals(
            QuestModalHost.Outcome.REQUEST_DISMISSAL,
            host.handles(QuestModalHost.ESCAPE_KEY)
        );

        host.replace(QuestModalHost.Modal.DISCARD_CONFIRMATION);
        assertEquals(
            QuestModalHost.Outcome.CANCEL_DISMISSAL,
            host.handles(QuestModalHost.ESCAPE_KEY)
        );
    }
}
