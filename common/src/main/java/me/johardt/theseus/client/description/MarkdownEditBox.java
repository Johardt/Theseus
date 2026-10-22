package me.johardt.theseus.client.description;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import me.johardt.theseus.client.TheseusClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/** Multiline Markdown editor with selection formatting and bounded undo/redo. */
public final class MarkdownEditBox extends AbstractTextAreaWidget {
    private static final int TEXT_COLOR = 0xFFC9C9D1;
    private static final int SYNTAX_COLOR = 0xFFD4B931;
    private static final int ACCENT_COLOR = 0xFF90A5DB;
    private final Font font;
    private final int textWidth;
    private final MultilineTextField field;
    private List<Line> displayLines = List.of(new Line(0, 0));
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private Consumer<String> listener = ignored -> {};
    private String previous = "";
    private boolean replaying;
    private long focusedTime = System.currentTimeMillis();

    public MarkdownEditBox(Font font, int x, int y, int width, int height, Component narration) {
        super(x, y, width, height, narration, AbstractScrollArea.defaultSettings(5), false, true);
        this.font = font;
        this.textWidth = width - totalInnerPadding();
        this.field = new MultilineTextField(font, textWidth);
        this.field.setCursorListener(this::scrollToCursor);
        this.field.setValueListener(this::valueChanged);
    }

    public void setValue(String value) {
        replaying = true;
        field.setValue(value == null ? "" : value, true);
        replaying = false;
        previous = field.value();
        undo.clear();
        redo.clear();
    }

    public String getValue() { return field.value(); }
    public void setValueListener(Consumer<String> listener) { this.listener = listener == null ? ignored -> {} : listener; }

    public void surround(String marker) {
        String selected = field.getSelectedText();
        field.insertText(marker + selected + marker);
        if (selected.isEmpty()) field.seekCursor(Whence.RELATIVE, -marker.length());
    }

    public void prefixLine(String prefix) {
        int cursor = field.cursor();
        int start = getValue().lastIndexOf('\n', Math.max(0, cursor - 1)) + 1;
        field.setSelecting(false);
        field.seekCursor(Whence.ABSOLUTE, start);
        field.insertText(prefix);
    }

    public void insertLink(String label, String url) {
        String selected = field.getSelectedText();
        String visible = label == null || label.isBlank() ? selected : label;
        field.insertText("[" + visible + "](" + (url == null ? "" : url) + ")");
    }

    public void insertObject(String kind, String id) {
        String tag = "<" + kind + " " + kind + "=\"" + id + "\"/>";
        boolean needsLine = !getValue().isEmpty() && field.cursor() > 0 && getValue().charAt(field.cursor() - 1) != '\n';
        field.insertText((needsLine ? "\n" : "") + tag + "\n");
    }

    public void insert(String value) {
        field.insertText(value == null ? "" : value);
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(getValue());
        replay(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(getValue());
        replay(redo.pop());
        return true;
    }

    private void replay(String value) {
        replaying = true;
        field.setValue(value, true);
        replaying = false;
        previous = value;
        listener.accept(value);
    }

    private void valueChanged(String value) {
        displayLines = wrapLines(value);
        if (!replaying && !value.equals(previous)) {
            undo.push(previous);
            while (undo.size() > TheseusClientOptions.maxEditorHistory()) undo.removeLast();
            redo.clear();
        }
        previous = value;
        listener.accept(value);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", getMessage(), getValue()));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick) field.selectWordAtCursor();
        else {
            field.setSelecting(event.hasShiftDown());
            seekCursorScreen(event.x(), event.y());
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        field.setSelecting(true);
        seekCursorScreen(event.x(), event.y());
        field.setSelecting(event.hasShiftDown());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown() && event.key() == 90) return event.hasShiftDown() ? redo() : undo();
        if (event.hasControlDown() && event.key() == 89) return redo();
        if (event.hasControlDown() && event.key() == 66) { surround("**"); return true; }
        if (event.hasControlDown() && event.key() == 73) { surround("--"); return true; }
        return field.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!visible || !isFocused() || !event.isAllowedChatCharacter()) return false;
        field.insertText(event.codepoint() == '§' ? "&&" : event.codepointAsString());
        return true;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        String value = field.value();
        int cursor = field.cursor();
        boolean showCursor = isFocused() && TextCursorUtils.isCursorVisible(System.currentTimeMillis() - focusedTime);
        int drawTop = getInnerTop();
        int innerLeft = getInnerLeft();
        int cursorX = innerLeft;
        int cursorY = drawTop;
        for (Line line : displayLines) {
            String raw = value.substring(line.begin(), line.end());
            if (withinContentAreaTopBottom(drawTop, drawTop + 9)) {
                graphics.text(font, highlight(raw), innerLeft, drawTop, TEXT_COLOR, false);
                if (cursor >= line.begin() && cursor <= line.end()) {
                    cursorX = innerLeft + font.width(value.substring(line.begin(), cursor));
                    cursorY = drawTop;
                    if (showCursor && cursor < value.length()) TextCursorUtils.extractInsertCursor(graphics, cursorX, cursorY, 0xFFD0D0D0, 10);
                }
            }
            drawTop += 9;
        }
        if (showCursor && cursor >= value.length() && withinContentAreaTopBottom(cursorY, cursorY + 9)) {
            TextCursorUtils.extractAppendCursor(graphics, font, cursorX, cursorY, 0xFFD0D0D0, false);
        }
        if (isHovered()) graphics.requestCursor(CursorTypes.IBEAM);
    }

    private static Component highlight(String raw) {
        if (raw.startsWith("# ") || raw.startsWith("## ") || raw.startsWith("> ") || raw.startsWith("- ")) {
            int split = raw.indexOf(' ') + 1;
            return Component.literal(raw.substring(0, split)).withStyle(Style.EMPTY.withColor(SYNTAX_COLOR))
                .append(Component.literal(raw.substring(split)).withStyle(Style.EMPTY.withColor(ACCENT_COLOR)));
        }
        MutableComponent result = CommonComponents.EMPTY.copy();
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            boolean marker = "`*/_~-|".indexOf(raw.charAt(i)) >= 0;
            if (!marker) continue;
            if (i > start) result.append(Component.literal(raw.substring(start, i)));
            int end = i + 1;
            while (end < raw.length() && raw.charAt(end) == raw.charAt(i)) end++;
            result.append(Component.literal(raw.substring(i, end)).withStyle(Style.EMPTY.withColor(SYNTAX_COLOR)));
            start = end;
            i = end - 1;
        }
        if (start < raw.length()) result.append(Component.literal(raw.substring(start)));
        return result;
    }

    @Override public int getInnerHeight() { return 9 * displayLines.size(); }

    private void scrollToCursor() {
        int line = Math.max(0, field.getLineAtCursor());
        double top = line * 9.0;
        if (top < scrollAmount()) setScrollAmount(top);
        else if (top + 9 > scrollAmount() + height - totalInnerPadding()) {
            setScrollAmount(top - height + 9 + totalInnerPadding());
        }
    }

    private void seekCursorScreen(double x, double y) {
        field.seekCursorToPoint(
            x - getX() - innerPadding(),
            y - getY() - innerPadding() + scrollAmount()
        );
    }

    private List<Line> wrapLines(String value) {
        List<Line> result = new ArrayList<>();
        if (value.isEmpty()) {
            result.add(new Line(0, 0));
        } else {
            font.getSplitter().splitLines(value, textWidth, Style.EMPTY, false,
                (style, begin, end) -> result.add(new Line(begin, end)));
            if (value.charAt(value.length() - 1) == '\n') {
                result.add(new Line(value.length(), value.length()));
            }
        }
        return List.copyOf(result);
    }

    private record Line(int begin, int end) {}

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) focusedTime = System.currentTimeMillis();
        Minecraft.getInstance().onTextInputFocusChange(this, focused);
    }
}
