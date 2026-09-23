package me.johardt.theseus.client;
import java.util.List;
import me.johardt.theseus.core.QuestDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import static me.johardt.theseus.client.QuestDetailsPanel.*;

/** Draws quest overview, task, and reward content for the details dock. */
final class QuestDetailsContent {
    private final QuestDetailsPanel panel;

    QuestDetailsContent(QuestDetailsPanel panel) {
        this.panel = panel;
    }

    int drawLockedBanner(
        GuiGraphicsExtractor graphics,
        QuestData quest,
        QuestSurfaceLayout.LockExplanation explanation,
        int x,
        int y,
        int width
    ) {
        List<Component> lines = explanation.blockers().isEmpty()
            ? List.of(Component.translatable(
                "gui.theseus.quest.locked_policy_visibility",
                QuestPresentation.visibilityLabel(quest.definition().settings().hiddenUntil())
            ))
            : explanation.blockers().stream().<Component>map(blocker -> Component.translatable(
                blocker.selectable() ? "gui.theseus.quest.open_prerequisite" : "gui.theseus.quest.complete_prerequisite",
                blocker.label()
            )).toList();
        int textWidth = width - 14;
        int bannerHeight = 17 + lines.stream()
            .mapToInt(line -> panel.font.wordWrapHeight(line, textWidth) + 3)
            .sum();
        graphics.fill(x, y, x + width, y + bannerHeight, 0xFF302D27);
        graphics.outline(x, y, width, bannerHeight, 0xFFFFD966);
        graphics.text(
            panel.font,
            Component.translatable(explanation.kind() == QuestSurfaceLayout.LockKind.DEPENDENCY
                ? "gui.theseus.quest.locked_prerequisites"
                : "gui.theseus.quest.locked_policy"),
            x + 7, y + 5, 0xFFFFD966, true
        );
        int lineY = y + 16;
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            int lineHeight = panel.font.wordWrapHeight(line, textWidth) + 3;
            boolean selectable = !explanation.blockers().isEmpty()
                && explanation.blockers().get(index).selectable();
            graphics.textWithWordWrap(
                panel.font, line, x + 7, lineY, textWidth,
                selectable ? 0xFF69A7FF : 0xFFB8C0CC
            );
            if (selectable) panel.lockQuestTargets.add(new LockQuestTarget(
                new Bounds(x + 7, lineY, textWidth, lineHeight),
                explanation.blockers().get(index).questId()
            ));
            lineY += lineHeight;
        }
        return bannerHeight + 6;
    }

    int drawSectionHeading(
        GuiGraphicsExtractor graphics,
        String title,
        int count,
        int x,
        int y,
        int width,
        int color,
        boolean completed
    ) {
        Identifier left = completed ? HEADING_COMPLETED_LEFT : HEADING_IN_PROGRESS_LEFT;
        Identifier right = completed ? HEADING_COMPLETED_RIGHT : HEADING_IN_PROGRESS_RIGHT;
        int titleWidth = panel.font.width(title) + 14;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, left, x, y + 1, titleWidth, 13);
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            right,
            x + titleWidth,
            y + 1,
            width - titleWidth,
            13
        );
        graphics.text(
            panel.font,
            Component.literal(title),
            x + 7,
            y + 4,
            0xFFFFFFFF,
            true
        );
        String amount = Integer.toString(count);
        graphics.text(
            panel.font,
            Component.literal(amount),
            x + width - panel.font.width(amount) - 5,
            y + 4,
            0xFFB8C0CC,
            false
        );
        return y + 19;
    }

    int drawTaskTree(
        GuiGraphicsExtractor graphics,
        QuestData quest,
        QuestDefinition.Task task,
        String progressKey,
        int x,
        int y,
        int width,
        boolean complete
    ) {
        y = drawTaskCard(
            graphics,
            quest,
            task,
            progressKey,
            x,
            y,
            width,
            complete
        );
        if (
            task.kind() != QuestDefinition.TaskKind.COMPOSITE ||
            task.tasks().isEmpty()
        ) return y;

        int branchTop = y;
        int nestedX = x + 10;
        int nestedWidth = width - 10;
        String requirement =
            "Options · complete " +
            task.target() +
            " of " +
            task.tasks().size();
        graphics.fill(nestedX, y, nestedX + nestedWidth, y + 15, 0xFF252A31);
        graphics.text(
            panel.font,
            Component.literal(requirement),
            nestedX + 7,
            y + 3,
            0xFFB8C0CC,
            false
        );
        y += 19;
        for (QuestDefinition.Task child : task.tasks().values()) {
            String childKey = progressKey + "/" + child.id();
            boolean childComplete =
                quest.progress().getOrDefault(childKey, 0) >= child.target();
            y = drawTaskTree(
                graphics,
                quest,
                child,
                childKey,
                nestedX,
                y,
                nestedWidth,
                childComplete
            );
        }
        graphics.fill(
            x + 3,
            branchTop,
            x + 5,
            y - 5,
            complete ? 0xFF55D86A : 0xFF4C9AFF
        );
        return y + 2;
    }

    int drawTaskCard(
        GuiGraphicsExtractor graphics,
        QuestData quest,
        QuestDefinition.Task task,
        String progressKey,
        int x,
        int y,
        int width,
        boolean complete
    ) {
        int progress = quest.progress().getOrDefault(progressKey, 0);
        int state = complete ? 0xFF55D86A : 0xFF626A76;
        graphics.fill(
            x,
            y,
            x + width,
            y + CARD_HEIGHT,
            complete ? 0xFF2D3932 : 0xFF30353D
        );
        graphics.outline(x, y, width, CARD_HEIGHT, state);
        panel.taskCardTargets.add(new ProgressCardTarget(
            "task",
            progressKey,
            QuestPresentation.taskTitle(task),
            new Bounds(x, y, width, CARD_HEIGHT)
        ));
        if (
            task.kind() == QuestDefinition.TaskKind.CHECK &&
            !panel.hasCustomTaskIcon(task)
        ) {
            graphics.blit(CHECK_ICON, x + 7, y + 11, x + 23, y + 27, 0, 0, 1, 1);
        } else {
            QuestPresentation.renderTaskIcon(graphics, task, x + 7, y + 11);
            panel.registerRecipeViewerTarget(QuestPresentation.taskIconTarget(task), x + 7, y + 11);
        }
        String progressText = progress + "/" + task.target();
        int textX = x + 30;
        panel.drawClippedDetailText(
            graphics,
            QuestPresentation.taskTitle(task),
            textX,
            y + 6,
            Math.max(1, x + width - panel.font.width(progressText) - 10 - textX),
            complete ? 0xFFD8F5DD : 0xFFFFFFFF
        );
        panel.drawClippedDetailText(
            graphics,
            QuestPresentation.taskDescription(task),
            textX,
            y + 18,
            Math.max(1, x + width - 6 - textX),
            0xFFADB4BF
        );
        graphics.text(
            panel.font,
            Component.literal(progressText),
            x + width - panel.font.width(progressText) - 5,
            y + 6,
            0xFFFFFFFF,
            false
        );
        drawProgressBar(
            graphics,
            x + 30,
            y + CARD_HEIGHT - 9,
            width - 36,
            task.target() == 0 ? 0 : progress / (double) task.target()
        );
        return y + CARD_HEIGHT + 5;
    }

    static void drawProgressBar(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        double progress
    ) {
        double clamped = Math.max(0, Math.min(1, progress));
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            clamped >= 1 ? PROGRESS_COMPLETE : PROGRESS_ACTIVE,
            x,
            y,
            width,
            5
        );
        int fill = (int) Math.round(width * clamped);
        if (fill > 0 && clamped < 1) graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            PROGRESS_FILL,
            x,
            y,
            fill,
            5
        );
    }
}
