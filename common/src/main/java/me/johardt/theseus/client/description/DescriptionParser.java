package me.johardt.theseus.client.description;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.johardt.theseus.client.description.DescriptionDocument.BlockKind;
import static me.johardt.theseus.client.description.DescriptionDocument.InlineStyle;

/** Compiles the original Theseus Markdown dialect without an HTML dependency. */
public final class DescriptionParser {
    private static final Pattern OBJECT = Pattern.compile("^<(task|reward)\\s+(?:task|reward)=\"([^\"]+)\"\\s*/>$");
    private static final Pattern LINK = Pattern.compile("\\[([^]\\n]+)]\\(([^)\\s]+)\\)");
    private static final Map<String, InlineStyle> MARKERS = new LinkedHashMap<>();
    private static final Map<Character, Integer> COLORS = Map.ofEntries(
        Map.entry('0', 0x000000), Map.entry('1', 0x0000AA), Map.entry('2', 0x00AA00), Map.entry('3', 0x00AAAA),
        Map.entry('4', 0xAA0000), Map.entry('5', 0xAA00AA), Map.entry('6', 0xFFAA00), Map.entry('7', 0xAAAAAA),
        Map.entry('8', 0x555555), Map.entry('9', 0x5555FF), Map.entry('a', 0x55FF55), Map.entry('b', 0x55FFFF),
        Map.entry('c', 0xFF5555), Map.entry('d', 0xFF55FF), Map.entry('e', 0xFFFF55), Map.entry('f', 0xFFFFFF)
    );

    static {
        MARKERS.put("**", InlineStyle.BOLD);
        MARKERS.put("--", InlineStyle.ITALIC);
        MARKERS.put("__", InlineStyle.UNDERLINE);
        MARKERS.put("~~", InlineStyle.STRIKETHROUGH);
        MARKERS.put("||", InlineStyle.OBFUSCATED);
        MARKERS.put("`", InlineStyle.CODE);
    }

    private DescriptionParser() {}

    public static DescriptionDocument parse(List<String> lines) {
        List<DescriptionDocument.Block> blocks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (lines == null) return new DescriptionDocument(List.of(), List.of());
        for (String source : lines) {
            String line = source == null ? "" : source;
            String trimmed = line.trim();
            Matcher object = OBJECT.matcher(trimmed);
            if (object.matches()) {
                BlockKind kind = object.group(1).equals("task") ? BlockKind.TASK : BlockKind.REWARD;
                blocks.add(new DescriptionDocument.Block(kind, List.of(), object.group(2)));
            } else if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
                warnings.add("Unsupported description tag: " + trimmed);
                blocks.add(textBlock(BlockKind.PARAGRAPH, line, warnings));
            } else if (trimmed.equals("---")) {
                blocks.add(new DescriptionDocument.Block(BlockKind.RULE, List.of(), ""));
            } else if (trimmed.isEmpty()) {
                blocks.add(new DescriptionDocument.Block(BlockKind.SPACER, List.of(), ""));
            } else if (trimmed.startsWith("## ")) {
                blocks.add(textBlock(BlockKind.HEADING_2, trimmed.substring(3), warnings));
            } else if (trimmed.startsWith("# ")) {
                blocks.add(textBlock(BlockKind.HEADING_1, trimmed.substring(2), warnings));
            } else if (trimmed.startsWith("> ")) {
                blocks.add(textBlock(BlockKind.QUOTE, trimmed.substring(2), warnings));
            } else if (trimmed.startsWith("- ")) {
                blocks.add(textBlock(BlockKind.LIST_ITEM, trimmed.substring(2), warnings));
            } else {
                blocks.add(textBlock(BlockKind.PARAGRAPH, line, warnings));
            }
        }
        return new DescriptionDocument(blocks, warnings);
    }

    private static DescriptionDocument.Block textBlock(BlockKind kind, String value, List<String> warnings) {
        return new DescriptionDocument.Block(kind, inline(value, warnings), "");
    }

    private static List<DescriptionDocument.Span> inline(String source, List<String> warnings) {
        List<DescriptionDocument.Span> spans = new ArrayList<>();
        EnumSet<InlineStyle> styles = EnumSet.noneOf(InlineStyle.class);
        Integer color = null;
        StringBuilder text = new StringBuilder();
        int index = 0;
        while (index < source.length()) {
            if (source.charAt(index) == '\\' && index + 1 < source.length()) {
                text.append(source.charAt(index + 1));
                index += 2;
                continue;
            }
            Matcher link = LINK.matcher(source);
            link.region(index, source.length());
            if (link.lookingAt() && safeLink(link.group(2))) {
                flush(spans, text, styles, color, null);
                spans.add(new DescriptionDocument.Span(link.group(1), styles, color, link.group(2)));
                index = link.end();
                continue;
            }
            if (link.lookingAt()) warnings.add("Ignored unsafe description link: " + link.group(2));

            String marker = markerAt(source, index);
            if (marker != null) {
                flush(spans, text, styles, color, null);
                InlineStyle style = MARKERS.get(marker);
                if (!styles.add(style)) styles.remove(style);
                index += marker.length();
                continue;
            }
            if (index + 2 < source.length() && source.charAt(index) == '/'
                && source.charAt(index + 2) == '/' && COLORS.containsKey(Character.toLowerCase(source.charAt(index + 1)))) {
                flush(spans, text, styles, color, null);
                int selected = COLORS.get(Character.toLowerCase(source.charAt(index + 1)));
                color = color != null && color == selected ? null : selected;
                index += 3;
                continue;
            }
            if (index + 2 < source.length() && source.charAt(index) == '&' && source.charAt(index + 1) == '&') {
                char code = Character.toLowerCase(source.charAt(index + 2));
                if (COLORS.containsKey(code)) {
                    flush(spans, text, styles, color, null);
                    color = COLORS.get(code);
                    index += 3;
                    continue;
                }
            }
            text.append(source.charAt(index++));
        }
        flush(spans, text, styles, color, null);
        return spans;
    }

    private static String markerAt(String value, int index) {
        for (String marker : MARKERS.keySet()) if (value.startsWith(marker, index)) return marker;
        return null;
    }

    private static boolean safeLink(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void flush(
        List<DescriptionDocument.Span> spans,
        StringBuilder text,
        EnumSet<InlineStyle> styles,
        Integer color,
        String link
    ) {
        if (text.isEmpty()) return;
        spans.add(new DescriptionDocument.Span(text.toString(), styles, color, link));
        text.setLength(0);
    }
}
