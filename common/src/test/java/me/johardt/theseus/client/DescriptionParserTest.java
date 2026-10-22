package me.johardt.theseus.client;

import me.johardt.theseus.client.description.DescriptionDocument;
import me.johardt.theseus.client.description.DescriptionParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptionParserTest {
    @Test
    void compilesLegacyBlocksStylesColorsLinksAndObjects() {
        DescriptionDocument document = DescriptionParser.parse(List.of(
            "# Heading", "> **Bold** and --italic--", "- /a/Green/a/", "[Docs](https://example.com/path)",
            "<task task=\"gather\"/>", "<reward reward=\"prize\"/>", "---"
        ));

        assertEquals(DescriptionDocument.BlockKind.HEADING_1, document.blocks().get(0).kind());
        assertTrue(document.blocks().get(1).spans().stream().anyMatch(span -> span.styles().contains(DescriptionDocument.InlineStyle.BOLD)));
        assertTrue(document.blocks().get(1).spans().stream().anyMatch(span -> span.styles().contains(DescriptionDocument.InlineStyle.ITALIC)));
        assertEquals(0x55FF55, document.blocks().get(2).spans().getFirst().color());
        assertEquals("https://example.com/path", document.blocks().get(3).spans().getFirst().link());
        assertEquals("gather", document.blocks().get(4).reference());
        assertEquals(DescriptionDocument.BlockKind.REWARD, document.blocks().get(5).kind());
        assertTrue(document.warnings().isEmpty());
    }

    @Test
    void unsafeLinksAndUnknownTagsStayVisibleInsteadOfExecutingOrCrashing() {
        DescriptionDocument document = DescriptionParser.parse(List.of(
            "[Run](javascript:alert(1))", "<script>bad</script>"
        ));

        assertFalse(document.warnings().isEmpty());
        assertTrue(document.blocks().getFirst().spans().stream().allMatch(span -> span.link() == null));
        assertTrue(document.blocks().get(1).spans().stream().map(DescriptionDocument.Span::text).anyMatch(text -> text.contains("script")));
    }

    @Test
    void originalDialectGoldenFixtureKeepsItsBlockAndInlineMeaning() throws Exception {
        String fixture;
        try (var stream = getClass().getResourceAsStream("/fixtures/descriptions/original-markdown.txt")) {
            fixture = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
        DescriptionDocument document = DescriptionParser.parse(List.of(fixture.stripTrailing().split("\n", -1)));

        assertEquals(List.of(
            DescriptionDocument.BlockKind.HEADING_1,
            DescriptionDocument.BlockKind.HEADING_2,
            DescriptionDocument.BlockKind.QUOTE,
            DescriptionDocument.BlockKind.LIST_ITEM,
            DescriptionDocument.BlockKind.PARAGRAPH,
            DescriptionDocument.BlockKind.TASK,
            DescriptionDocument.BlockKind.REWARD,
            DescriptionDocument.BlockKind.RULE
        ), document.blocks().stream().map(DescriptionDocument.Block::kind).toList());
        assertTrue(document.warnings().isEmpty());
    }
}
