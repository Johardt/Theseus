package me.johardt.theseus.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Overlay policy for the quest editor.
 *
 * <p>The screen owns Minecraft widgets and pixels; this module owns the
 * policy that decides which transient layer is active, which layer is below
 * it, how dismissal is guarded by dirty state, and when a rebuilt widget tree
 * must receive focus. Keeping that policy here makes adding an overlay a
 * single state transition instead of another independent precedence check in
 * every screen callback.</p>
 */
public final class QuestModalHost {
    /** GLFW's Escape key, kept here so the policy module does not depend on Minecraft classes. */
    public static final int ESCAPE_KEY = 256;

    private final Deque<Modal> layers = new ArrayDeque<>();
    private Runnable pendingDiscard;
    private boolean focusRestoreRequested;
    private Object inputLifetime = new Object();

    /** A replaced or reopened layer cannot inherit an earlier physical press. */
    Object inputLifetime() { return inputLifetime; }

    public Modal active() {
        return layers.peekLast() == null ? Modal.NONE : layers.peekLast();
    }

    public Modal parent() {
        if (layers.size() < 2) return Modal.NONE;
        var iterator = layers.descendingIterator();
        iterator.next();
        Modal parent = iterator.next();
        return parent == null ? Modal.NONE : parent;
    }

    public boolean isOpen() {
        return active() != Modal.NONE;
    }

    public boolean is(Modal modal) {
        return active() == modal;
    }

    public boolean isOneOf(Modal... modals) {
        Modal active = active();
        for (Modal modal : modals) {
            if (active == modal) return true;
        }
        return false;
    }

    public boolean contains(Modal modal) {
        return layers.contains(modal);
    }

    /** Whether the active layer must receive input before the editor below it. */
    public boolean blocksInput() {
        return isOpen();
    }

    /** @deprecated Use {@link #blocksInput()} to keep input policy at the modal seam. */
    @Deprecated
    public boolean shouldBlockUnderlyingInput() {
        return blocksInput();
    }

    /** Whether the active layer is rendered as a full-screen foreground overlay. */
    public boolean rendersOverlay() {
        return active().rendersAsOverlay;
    }

    /** @deprecated Use {@link #rendersOverlay()} to keep rendering policy at the modal seam. */
    @Deprecated
    public boolean rendersAsOverlay() {
        return rendersOverlay();
    }

    /** Whether {@link QuestScreen#init()} should build only this layer's widgets. */
    public boolean ownsWidgetTree() {
        return active().ownsWidgetTree;
    }

    /**
     * Resolve the modal-owned part of a key event.
     *
     * <p>A pass-through result means the screen may route the event to its
     * widgets or editor. A consumed result is used by transient choosers that
     * have custom mouse handling but must not leak keyboard input to the
     * editor underneath. Escape is resolved here so the screen does not need
     * to repeat the modal precedence table.</p>
     */
    public Outcome handles(int key) {
        if (key == ESCAPE_KEY) return active().escapeOutcome;
        return active().consumesOtherKeys ? Outcome.CONSUMED : Outcome.PASS;
    }

    public boolean isTaskChooserOpen() {
        return is(Modal.TASK_CHOOSER);
    }

    public boolean isNestedTaskChooserOpen() {
        return is(Modal.NESTED_TASK_CHOOSER);
    }

    public boolean showsNestedTasks() {
        return isOneOf(Modal.NESTED_TASKS, Modal.NESTED_TASK_CHOOSER)
            || (is(Modal.DISCARD_CONFIRMATION) && parent() == Modal.NESTED_TASKS);
    }

    public boolean isRewardChooserOpen() {
        return is(Modal.REWARD_CHOOSER);
    }

    public boolean isNestedRewardChooserOpen() {
        return is(Modal.NESTED_REWARD_CHOOSER);
    }

    public boolean showsNestedRewards() {
        return isOneOf(Modal.NESTED_REWARDS, Modal.NESTED_REWARD_CHOOSER)
            || (is(Modal.DISCARD_CONFIRMATION) && parent() == Modal.NESTED_REWARDS);
    }

    public boolean isChapterEditorOpen() {
        return contains(Modal.CHAPTER_EDITOR);
    }

    /** Push a layer above the current one, preserving the return path on close. */
    public void open(Modal modal) {
        requireRealModal(modal);
        if (active() == modal) return;
        layers.addLast(modal);
        inputLifetime = new Object();
        focusRestoreRequested = true;
    }

    /** Replace the whole overlay stack with one layer. */
    public void replace(Modal modal) {
        requireRealModal(modal);
        layers.clear();
        layers.addLast(modal);
        pendingDiscard = null;
        inputLifetime = new Object();
        focusRestoreRequested = true;
    }

    /** Pop the active layer and return to the layer below it. */
    public void close() {
        if (layers.isEmpty()) return;
        boolean wasDiscard = active() == Modal.DISCARD_CONFIRMATION;
        layers.removeLast();
        if (wasDiscard) pendingDiscard = null;
        inputLifetime = new Object();
        focusRestoreRequested = true;
    }

    /** Close every layer, used when a server mutation takes ownership of the screen. */
    public void closeAll() {
        if (layers.isEmpty() && pendingDiscard == null) return;
        layers.clear();
        pendingDiscard = null;
        inputLifetime = new Object();
        focusRestoreRequested = true;
    }

    /**
     * Request a dismissal that may discard edits. Returns true when the caller
     * must rebuild for a confirmation layer; a clean dismissal runs immediately.
     */
    public boolean requestDismissal(boolean dirty, Runnable discardAction) {
        Objects.requireNonNull(discardAction, "discardAction");
        if (!dirty) {
            discardAction.run();
            return false;
        }
        if (is(Modal.DISCARD_CONFIRMATION)) return true;
        pendingDiscard = discardAction;
        open(Modal.DISCARD_CONFIRMATION);
        return true;
    }

    public boolean hasPendingDismissal() {
        return is(Modal.DISCARD_CONFIRMATION) && pendingDiscard != null;
    }

    /** Cancel the pending dismissal and return to the previous layer. */
    public void cancelDismissal() {
        if (!is(Modal.DISCARD_CONFIRMATION)) return;
        pendingDiscard = null;
        close();
    }

    /** Confirm the pending dismissal. The action runs after the confirmation layer is removed. */
    public boolean confirmDismissal() {
        if (!hasPendingDismissal()) return false;
        Runnable action = pendingDiscard;
        pendingDiscard = null;
        close();
        action.run();
        return true;
    }

    /** Consume the focus request generated by an overlay transition. */
    public boolean consumeFocusRestoreRequest() {
        boolean requested = focusRestoreRequested;
        focusRestoreRequested = false;
        return requested;
    }

    public QuestModalHost copy() {
        QuestModalHost copy = new QuestModalHost();
        copy.layers.addAll(layers);
        copy.pendingDiscard = pendingDiscard;
        copy.focusRestoreRequested = focusRestoreRequested;
        return copy;
    }

    private static void requireRealModal(Modal modal) {
        if (modal == null || modal == Modal.NONE) {
            throw new IllegalArgumentException("A real overlay is required");
        }
    }

    public enum Modal {
        NONE(false, false, Outcome.PASS, false),
        DIAGNOSTICS(true, true, Outcome.CLOSE, false),
        FILE_IMPORT(true, true, Outcome.CLOSE, false),
        PICKER(true, true, Outcome.CLOSE, false),
        CONFIRMATION(true, true, Outcome.CLOSE, false),
        EDITOR(true, true, Outcome.CLOSE, false),
        DELETE_QUEST_CONFIRMATION(true, true, Outcome.CLOSE, false),
        PROGRESS_RESET_CONFIRMATION(true, true, Outcome.CLOSE, false),
        DISCARD_CONFIRMATION(true, true, Outcome.CANCEL_DISMISSAL, false),
        TASK_DELETE_CONFIRMATION(true, true, Outcome.CLOSE, false),
        CHAPTER_EDITOR(true, true, Outcome.REQUEST_DISMISSAL, false),
        PASTE_ID_PROMPT(true, true, Outcome.CLOSE, false),
        RAW_INSPECTOR(true, true, Outcome.CLOSE, false),
        DESCRIPTION_EDITOR(true, true, Outcome.CLOSE, false),
        TASK_EDITOR(true, true, Outcome.REQUEST_DISMISSAL, false),
        NESTED_TASKS(true, true, Outcome.REQUEST_DISMISSAL, false),
        TASK_CHOOSER(false, false, Outcome.CLOSE, true),
        NESTED_TASK_CHOOSER(true, true, Outcome.CLOSE, true),
        REWARD_EDITOR(true, true, Outcome.REQUEST_DISMISSAL, false),
        NESTED_REWARDS(true, true, Outcome.REQUEST_DISMISSAL, false),
        REWARD_CHOOSER(false, false, Outcome.CLOSE, true),
        NESTED_REWARD_CHOOSER(true, true, Outcome.CLOSE, true),
        NESTED_REWARD_EDITOR(true, true, Outcome.REQUEST_DISMISSAL, false);

        private final boolean rendersAsOverlay;
        private final boolean ownsWidgetTree;
        private final Outcome escapeOutcome;
        private final boolean consumesOtherKeys;

        Modal(
            boolean rendersAsOverlay,
            boolean ownsWidgetTree,
            Outcome escapeOutcome,
            boolean consumesOtherKeys
        ) {
            this.rendersAsOverlay = rendersAsOverlay;
            this.ownsWidgetTree = ownsWidgetTree;
            this.escapeOutcome = escapeOutcome;
            this.consumesOtherKeys = consumesOtherKeys;
        }
    }

    public enum Outcome {
        PASS,
        CONSUMED,
        CLOSE,
        CANCEL_DISMISSAL,
        REQUEST_DISMISSAL
    }

    public record ProgressResetTarget(
        String scope,
        String questId,
        String questTitle,
        String entryId,
        String displayLabel
    ) {
        public ProgressResetTarget {
            scope = scope == null ? "" : scope;
            questId = questId == null ? "" : questId;
            questTitle = questTitle == null ? "" : questTitle;
            entryId = entryId == null ? "" : entryId;
            displayLabel = displayLabel == null ? "" : displayLabel;
        }
    }
}
