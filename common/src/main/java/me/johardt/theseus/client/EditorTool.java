package me.johardt.theseus.client;

enum EditorTool {
    SELECT("move", "gui.theseus.editor.tool.select", "S"),
    HAND("drag", "gui.theseus.editor.tool.pan", "H"),
    ADD("add", "gui.theseus.editor.tool.add", "A"),
    LINK("link", "gui.theseus.editor.tool.link", "L");

    final String icon;
    final String tooltipKey;
    final String shortcut;

    EditorTool(String icon, String tooltipKey, String shortcut) {
        this.icon = icon;
        this.tooltipKey = tooltipKey;
        this.shortcut = shortcut;
    }
}
