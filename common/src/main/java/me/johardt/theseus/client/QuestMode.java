package me.johardt.theseus.client;

/** The two top-level behaviours that can occupy the quest graph screen. */
sealed interface QuestMode permits PlayMode, AuthorMode {
    boolean isAuthoring();

    EditorTool editorTool();

    void setEditorTool(EditorTool tool);
}

/** Player-facing mode. It deliberately has no authoring state or tool. */
final class PlayMode implements QuestMode {
    @Override
    public boolean isAuthoring() {
        return false;
    }

    @Override
    public EditorTool editorTool() {
        return EditorTool.SELECT;
    }

    @Override
    public void setEditorTool(EditorTool tool) {
        // The play mode cannot select editor tools.
    }
}

/** Editor adapter and owner of all in-progress quest authoring state. */
final class AuthorMode extends QuestAuthoringSession implements QuestMode {
    private EditorTool editorTool = EditorTool.SELECT;

    AuthorMode(int defaultIconSize) {
        super(defaultIconSize);
    }

    AuthorMode(AuthorMode source) {
        super(source);
        this.editorTool = source.editorTool;
    }

    AuthorMode copy() {
        return new AuthorMode(this);
    }

    @Override
    public boolean isAuthoring() {
        return true;
    }

    @Override
    public EditorTool editorTool() {
        return editorTool;
    }

    @Override
    public void setEditorTool(EditorTool tool) {
        editorTool = tool == null ? EditorTool.SELECT : tool;
    }
}
