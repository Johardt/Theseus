package me.johardt.theseus.client.description;

import java.util.List;
import java.util.Set;

/** Immutable, renderer-neutral result of compiling a quest description. */
public record DescriptionDocument(List<Block> blocks, List<String> warnings) {
    public DescriptionDocument {
        blocks = List.copyOf(blocks);
        warnings = List.copyOf(warnings);
    }

    public record Block(BlockKind kind, List<Span> spans, String reference) {
        public Block {
            spans = List.copyOf(spans);
            reference = reference == null ? "" : reference;
        }
    }

    public record Span(String text, Set<InlineStyle> styles, Integer color, String link) {
        public Span {
            text = text == null ? "" : text;
            styles = Set.copyOf(styles);
        }
    }

    public enum BlockKind { PARAGRAPH, HEADING_1, HEADING_2, RULE, QUOTE, LIST_ITEM, SPACER, TASK, REWARD }
    public enum InlineStyle { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, OBFUSCATED, CODE }
}
