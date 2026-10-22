package me.johardt.theseus.client.description;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static me.johardt.theseus.client.description.DescriptionDocument.BlockKind;
import static me.johardt.theseus.client.description.DescriptionDocument.InlineStyle;

/** Shared description renderer used by the player dock and authoring preview. */
public final class QuestDescriptionRenderer {
    private QuestDescriptionRenderer() {}

    public static Result render(
        GuiGraphicsExtractor graphics,
        Font font,
        DescriptionDocument document,
        int x,
        int y,
        int width,
        ReferenceResolver references
    ) {
        int start = y;
        List<Interaction> interactions = new ArrayList<>();
        for (DescriptionDocument.Block block : document.blocks()) {
            if (block.kind() == BlockKind.SPACER) {
                y += 7;
                continue;
            }
            if (block.kind() == BlockKind.RULE) {
                graphics.horizontalLine(x, x + width, y + 3, 0xFF596271);
                y += 9;
                continue;
            }
            if (block.kind() == BlockKind.TASK || block.kind() == BlockKind.REWARD) {
                String label = references.resolve(block.kind(), block.reference());
                graphics.fill(x, y, x + width, y + 20, 0xA029303B);
                graphics.fill(x, y, x + 3, y + 20,
                    block.kind() == BlockKind.TASK ? 0xFF5F92D8 : 0xFFD9AF4A);
                Component text = Component.literal(
                    (block.kind() == BlockKind.TASK ? "Task: " : "Reward: ") + label
                );
                graphics.text(font, text, x + 7, y + 6, 0xFFE6E9EF, false);
                interactions.add(new Interaction(x, y, width, 20, null,
                    Component.literal(block.reference())));
                y += 25;
                continue;
            }

            MutableComponent component = Component.empty();
            if (block.kind() == BlockKind.LIST_ITEM) component.append(Component.literal("• "));
            for (DescriptionDocument.Span span : block.spans()) component.append(component(span));

            int color = switch (block.kind()) {
                case HEADING_1 -> 0xFFFFD966;
                case HEADING_2 -> 0xFFD9C47A;
                case QUOTE -> 0xFFB6BFCC;
                default -> 0xFFE1E4E8;
            };
            boolean quote = block.kind() == BlockKind.QUOTE;
            int quotePrefixWidth = quote ? font.width("│ ") : 0;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                component,
                Math.max(1, width - quotePrefixWidth)
            );
            int lineHeight = block.kind() == BlockKind.HEADING_1 ? 11 : 9;
            int blockTop = y;
            for (var line : lines) {
                if (quote) {
                    graphics.text(
                        font,
                        Component.literal("│ ").withStyle(Style.EMPTY.withColor(0x8290A3)),
                        x,
                        y,
                        0xFF8290A3,
                        false
                    );
                }
                graphics.text(font, line, x + quotePrefixWidth, y, color, false);
                y += lineHeight;
            }
            String firstLink = block.spans().stream().map(DescriptionDocument.Span::link)
                .filter(link -> link != null && !link.isBlank()).findFirst().orElse(null);
            if (firstLink != null) {
                Style style = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(firstLink)))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(firstLink)));
                interactions.add(new Interaction(x, blockTop, width, Math.max(9, y - blockTop), style,
                    Component.literal(firstLink)));
            }
            y += block.kind() == BlockKind.HEADING_1 ? 5 : 4;
        }
        return new Result(y - start, List.copyOf(interactions));
    }

    private static Component component(DescriptionDocument.Span span) {
        Style style = Style.EMPTY;
        if (span.color() != null) style = style.withColor(span.color());
        if (span.styles().contains(InlineStyle.BOLD)) style = style.withBold(true);
        if (span.styles().contains(InlineStyle.ITALIC)) style = style.withItalic(true);
        if (span.styles().contains(InlineStyle.UNDERLINE)) style = style.withUnderlined(true);
        if (span.styles().contains(InlineStyle.STRIKETHROUGH)) style = style.withStrikethrough(true);
        if (span.styles().contains(InlineStyle.OBFUSCATED)) style = style.withObfuscated(true);
        if (span.styles().contains(InlineStyle.CODE)) style = style.withColor(0xE8C77B);
        if (span.link() != null) {
            style = style.withColor(0x69A7FF).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(span.link())))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(span.link())));
        }
        return Component.literal(span.text()).withStyle(style);
    }

    public record Result(int height, List<Interaction> interactions) {}

    public record Interaction(int x, int y, int width, int height, Style clickStyle, Component tooltip) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    @FunctionalInterface
    public interface ReferenceResolver {
        String resolve(BlockKind kind, String id);
    }
}
