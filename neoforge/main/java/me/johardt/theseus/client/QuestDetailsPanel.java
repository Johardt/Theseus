package me.johardt.theseus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.description.DescriptionDocument;
import me.johardt.theseus.client.description.DescriptionParser;
import me.johardt.theseus.client.description.QuestDescriptionRenderer;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import me.johardt.theseus.core.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

final class QuestDetailsPanel {
    private static final int CARD_HEIGHT = 48;
    private static final Identifier CHECK_ICON = Identifier.fromNamespaceAndPath(Theseus.MOD_ID, "textures/item/check.png");
    private static final Identifier PROGRESS_ACTIVE = sprite("widgets/progress_bar_0");
    private static final Identifier PROGRESS_COMPLETE = sprite("widgets/progress_bar_1");
    private static final Identifier PROGRESS_FILL = sprite("widgets/progress_bar_2");
    private static final Identifier HEADING_IN_PROGRESS_LEFT = sprite("headings/in_progress_left");
    private static final Identifier HEADING_IN_PROGRESS_RIGHT = sprite("headings/in_progress_right");
    private static final Identifier HEADING_COMPLETED_LEFT = sprite("headings/claimed_left");
    private static final Identifier HEADING_COMPLETED_RIGHT = sprite("headings/claimed_right");

    private final List<RewardChoiceTarget> rewardChoiceTargets = new ArrayList<>();
    private final List<ProgressCardTarget> taskCardTargets = new ArrayList<>();
    private final List<ProgressCardTarget> rewardCardTargets = new ArrayList<>();
    private final List<DetailTextTarget> detailTextTargets = new ArrayList<>();
    private final List<QuestDescriptionRenderer.Interaction> descriptionInteractions = new ArrayList<>();
    private final List<LockQuestTarget> lockQuestTargets = new ArrayList<>();
    private final List<RecipeViewerTarget> recipeViewerTargets = new ArrayList<>();
    private int scroll;
    private int maxScroll;
    private int detailContentTop;
    private int detailContentBottom;
    private int width;
    private int height;
    private int panelWidth;
    private Font font;
    private Model model;

    QuestDetailsPanel() {}

    private QuestDetailsPanel(QuestDetailsPanel previous) {
        scroll = previous.scroll;
    }

    QuestDetailsPanel copyForRebuild() {
        return new QuestDetailsPanel(this);
    }

    void resetScroll() {
        scroll = 0;
    }

    void scroll(double wheelDelta) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(wheelDelta * 18)));
    }

    void render(
        GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight,
        int panelWidth, Model model, int mouseX, int mouseY
    ) {
        this.font = font;
        this.width = screenWidth;
        this.height = screenHeight;
        this.panelWidth = panelWidth;
        this.model = model;
        renderDetails(graphics, mouseX, mouseY);
    }

    String lockQuestAt(double mouseX, double mouseY) {
        return lockQuestTargets.stream().filter(target -> target.bounds().contains(mouseX, mouseY))
            .map(LockQuestTarget::questId).findFirst().orElse(null);
    }

    QuestDescriptionRenderer.Interaction descriptionInteractionAt(double mouseX, double mouseY) {
        return descriptionInteractions.stream().filter(value -> value.contains(mouseX, mouseY)).findFirst().orElse(null);
    }

    RewardChoiceTarget rewardChoiceAt(double mouseX, double mouseY) {
        return rewardChoiceTargets.stream().filter(target -> target.bounds().contains(mouseX, mouseY)).findFirst().orElse(null);
    }

    ProgressCardTarget progressCardAt(double mouseX, double mouseY, QuestScreen.DetailTab tab) {
        List<ProgressCardTarget> targets = tab == QuestScreen.DetailTab.TASKS
            ? taskCardTargets : tab == QuestScreen.DetailTab.REWARDS ? rewardCardTargets : List.of();
        return targets.stream().filter(target -> target.bounds().contains(mouseX, mouseY)).findFirst().orElse(null);
    }

    Optional<ItemStack> recipeViewerItemAt(double mouseX, double mouseY) {
        return recipeViewerTargets.stream().filter(target -> target.contains(mouseX, mouseY))
            .reduce((first, second) -> second).map(RecipeViewerTarget::stack);
    }

    private void drawClippedDetailText(
        GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color
    ) {
        String text = value == null ? "" : value;
        if (maxWidth <= 0) return;
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        }
        graphics.text(font, Component.literal(text), x, y, color, false);
        if (font.width(value == null ? "" : value) > maxWidth) {
            detailTextTargets.add(new DetailTextTarget(new Bounds(x, y - 2, Math.max(1, maxWidth), font.lineHeight + 4), value));
        }
    }

    private void renderDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        detailTextTargets.clear();
        descriptionInteractions.clear();
        lockQuestTargets.clear();
        taskCardTargets.clear();
        rewardCardTargets.clear();
        recipeViewerTargets.clear();
        int detailsWidth = panelWidth;
        int panelLeft = width - detailsWidth;
        int x = panelLeft + 12;
        int contentWidth = detailsWidth - 24;
        QuestData quest = model.selected();
        if (quest == null) {
            graphics.text(
                font,
                Component.translatable("gui.theseus.editor.select_a_quest"),
                x,
                42,
                0xFFAAAAAA,
                false
            );
            return;
        }
        graphics.textWithWordWrap(
            font,
            Component.literal(quest.definition().title()),
            x,
            38,
            contentWidth,
            0xFFFFFFFF,
            true
        );
        int y =
            50 +
            font.wordWrapHeight(
                Component.literal(quest.definition().title()),
                contentWidth
            );
        if (!quest.definition().subtitle().isBlank()) {
            graphics.textWithWordWrap(
                font,
                Component.literal(quest.definition().subtitle()),
                x,
                y,
                contentWidth,
                0xFFB8C0CC
            );
            y +=
                font.wordWrapHeight(
                    Component.literal(quest.definition().subtitle()),
                    contentWidth
                ) + 6;
        }
        graphics.horizontalLine(x, x + contentWidth, y, 0xFF49515E);
        int contentTop = y + 7;
        int contentBottom = height - 38;
        detailContentTop = contentTop;
        detailContentBottom = contentBottom;
        graphics.enableScissor(panelLeft + 1, contentTop, width, contentBottom);
        int contentHeight = switch (model.tab()) {
            case OVERVIEW -> drawOverview(
                graphics,
                quest,
                x,
                contentTop - scroll,
                contentWidth
            );
            case TASKS -> drawTasks(
                graphics,
                quest,
                x,
                contentTop - scroll,
                contentWidth
            );
            case REWARDS -> drawRewards(
                graphics,
                quest,
                x,
                contentTop - scroll,
                contentWidth
            );
        };
        graphics.disableScissor();
        maxScroll = Math.max(
            0,
            contentHeight - (contentBottom - contentTop)
        );
        scroll = Math.min(scroll, maxScroll);
        drawDetailTextTooltip(graphics, mouseX, mouseY);
        drawRecipeViewerTooltip(graphics, mouseX, mouseY);
        if (model.tab() == QuestScreen.DetailTab.OVERVIEW) {
            descriptionInteractions.stream()
                .filter(interaction -> interaction.contains(mouseX, mouseY))
                .findFirst()
                .ifPresent(interaction -> graphics.setTooltipForNextFrame(interaction.tooltip(), mouseX, mouseY));
        }
    }

    private void drawRecipeViewerTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!RecipeViewer.isAvailable()) return;
        recipeViewerTargets.stream()
            .filter(target -> target.contains(mouseX, mouseY))
            .reduce((first, second) -> second)
            .ifPresent(target -> {
                List<Component> tooltip = new ArrayList<>(target.stack().getTooltipLines(
                    Item.TooltipContext.EMPTY,
                    Minecraft.getInstance().player,
                    TooltipFlag.NORMAL
                ));
                tooltip.add(Component.translatable("screen.theseus.recipe_viewer_hint"));
                graphics.setTooltipForNextFrame(
                    font,
                    tooltip,
                    target.stack().getTooltipImage(),
                    mouseX,
                    mouseY
                );
            });
    }

    private void registerRecipeViewerTarget(java.util.Optional<ItemStack> stack, int x, int y) {
        if (stack.isEmpty()) return;
        if (y < detailContentTop || y + 16 > detailContentBottom) return;
        recipeViewerTargets.add(new RecipeViewerTarget(stack.get().copy(), x, y, 16, 16));
    }

    private int drawOverview(
        GuiGraphicsExtractor graphics,
        QuestData quest,
        int x,
        int y,
        int contentWidth
    ) {
        int startY = y;
        if (!quest.unlocked()) y += drawLockedBanner(
            graphics,
            quest,
            model.lockExplanation(),
            x,
            y,
            contentWidth
        );
        int completed = (int) quest.definition()
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress().getOrDefault(task.id(), 0) >= task.target()
            )
            .count();
        graphics.text(
            font,
            Component.translatable("gui.theseus.editor.quest_progress"),
            x,
            y,
            ClientThemeLoader.active().questDetails().summaryTitle(),
            true
        );
        graphics.text(
            font,
            Component.literal(
                completed + "/" + quest.definition().tasks().size()
            ),
            x + contentWidth - 34,
            y,
            0xFFFFFFFF,
            false
        );
        y += 14;
        drawProgressBar(
            graphics,
            x,
            y,
            contentWidth,
            questProgress(quest)
        );
        y += 13;
        descriptionInteractions.clear();
        DescriptionDocument description = DescriptionParser.parse(quest.definition().description());
        QuestDescriptionRenderer.Result rendered = QuestDescriptionRenderer.render(
            graphics, font, description, x, y, contentWidth,
            (kind, id) -> descriptionReference(quest, kind, id)
        );
        descriptionInteractions.addAll(rendered.interactions());
        y += rendered.height();
        y += 4;
        graphics.text(
            font,
            Component.translatable("gui.theseus.editor.status"),
            x,
            y,
            0xFFFFD966,
            true
        );
        y += 14;
        graphics.text(
            font,
            status(quest),
            x,
            y,
            nodeStateColor(quest),
            false
        );
        return y - startY + 18;
    }

    private int drawTasks(
        GuiGraphicsExtractor graphics,
        QuestData quest,
        int x,
        int y,
        int contentWidth
    ) {
        int startY = y;
        if (!quest.unlocked()) y += drawLockedBanner(
            graphics,
            quest,
            model.lockExplanation(),
            x,
            y,
            contentWidth
        );
        List<QuestDefinition.Task> active = quest.definition()
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress().getOrDefault(task.id(), 0) < task.target()
            )
            .toList();
        List<QuestDefinition.Task> complete = quest.definition()
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress().getOrDefault(task.id(), 0) >= task.target()
            )
            .toList();
        if (!active.isEmpty()) {
            y = drawSectionHeading(
                graphics,
                Component.translatable("quest.theseus.in_progress").getString(),
                active.size(),
                x,
                y,
                contentWidth,
                0xFF4C9AFF,
                false
            );
            for (QuestDefinition.Task task : active) {
                y = drawTaskTree(
                    graphics,
                    quest,
                    task,
                    task.id(),
                    x,
                    y,
                    contentWidth,
                    false
                );
            }
        }
        if (!complete.isEmpty()) {
            y = drawSectionHeading(
                graphics,
                Component.translatable("quest.theseus.completed").getString(),
                complete.size(),
                x,
                y + (active.isEmpty() ? 0 : 4),
                contentWidth,
                0xFF55D86A,
                true
            );
            for (QuestDefinition.Task task : complete) {
                y = drawTaskTree(
                    graphics,
                    quest,
                    task,
                    task.id(),
                    x,
                    y,
                    contentWidth,
                    true
                );
            }
        }
        if (active.isEmpty() && complete.isEmpty()) graphics.text(
            font,
            Component.translatable("gui.theseus.editor.no_tasks"),
            x,
            y,
            0xFF9AA1AC,
            false
        );
        return y - startY + 6;
    }

    private int drawLockedBanner(
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
                visibilityLabel(quest.definition().settings().hiddenUntil())
            ))
            : explanation.blockers().stream().<Component>map(blocker -> Component.translatable(
                blocker.selectable() ? "gui.theseus.quest.open_prerequisite" : "gui.theseus.quest.complete_prerequisite",
                blocker.label()
            )).toList();
        int textWidth = width - 14;
        int bannerHeight = 17 + lines.stream()
            .mapToInt(line -> font.wordWrapHeight(line, textWidth) + 3)
            .sum();
        graphics.fill(x, y, x + width, y + bannerHeight, 0xFF302D27);
        graphics.outline(x, y, width, bannerHeight, 0xFFFFD966);
        graphics.text(
            font,
            Component.translatable(explanation.kind() == QuestSurfaceLayout.LockKind.DEPENDENCY
                ? "gui.theseus.quest.locked_prerequisites"
                : "gui.theseus.quest.locked_policy"),
            x + 7, y + 5, 0xFFFFD966, true
        );
        int lineY = y + 16;
        for (int index = 0; index < lines.size(); index++) {
            Component line = lines.get(index);
            int lineHeight = font.wordWrapHeight(line, textWidth) + 3;
            boolean selectable = !explanation.blockers().isEmpty()
                && explanation.blockers().get(index).selectable();
            graphics.textWithWordWrap(
                font, line, x + 7, lineY, textWidth,
                selectable ? 0xFF69A7FF : 0xFFB8C0CC
            );
            if (selectable) lockQuestTargets.add(new LockQuestTarget(
                new Bounds(x + 7, lineY, textWidth, lineHeight),
                explanation.blockers().get(index).questId()
            ));
            lineY += lineHeight;
        }
        return bannerHeight + 6;
    }

    private int drawRewards(
        GuiGraphicsExtractor graphics,
        QuestData quest,
        int x,
        int y,
        int contentWidth
    ) {
        rewardChoiceTargets.clear();
        int startY = y;
        if (quest.definition().rewards().isEmpty()) {
            graphics.text(
                font,
                Component.translatable("gui.theseus.editor.no_rewards"),
                x,
                y,
                0xFF9AA1AC,
                false
            );
            return 18;
        }
        y = drawSectionHeading(
            graphics,
            (quest.claimed() ? Component.translatable("quest.theseus.claimed") : Component.translatable("gui.theseus.rewards.title")).getString(),
            quest.definition().rewards().size(),
            x,
            y,
            contentWidth,
            quest.claimed() ? 0xFF55D86A : 0xFFFFD966,
            quest.claimed()
        );
        for (QuestDefinition.Reward reward : quest.definition()
            .rewards()
            .values()) {
            boolean rewardClaimed = quest.claimedRewards().contains(reward.id());
            boolean rewardTypeAvailable = isRewardTypeAvailable(reward, model.serverRewardTypes());
            int border =
                reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED && !rewardTypeAvailable
                    ? 0xFFE57373
                    : rewardClaimed
                      ? 0xFF55D86A
                      : 0xFF626A76;
            graphics.fill(x, y, x + contentWidth, y + 40, 0xFF30353D);
            graphics.outline(x, y, contentWidth, 40, border);
            rewardCardTargets.add(new ProgressCardTarget(
                "reward",
                reward.id(),
                QuestPresentation.rewardTitle(reward),
                new Bounds(x, y, contentWidth, 40)
            ));
            QuestPresentation.renderRewardIcon(graphics, reward, x + 7, y + 11);
            registerRecipeViewerTarget(QuestPresentation.rewardIconTarget(reward), x + 7, y + 11);
            int textWidth = Math.max(1, contentWidth - 36);
            drawClippedDetailText(
                graphics,
                QuestPresentation.rewardTitle(reward),
                x + 30,
                y + 8,
                textWidth,
                0xFFFFFFFF
            );
            String detail = rewardClaimed ? "Claimed" : switch (reward.kind()) {
                case SELECTABLE -> "Choose up to " + reward.amount();
                case UNSUPPORTED -> rewardTypeAvailable
                    ? "Add-on reward: " + reward.type()
                    : "Not supported by this server: " + reward.type();
                default -> "Amount: " + reward.amount();
            };
            drawClippedDetailText(
                graphics,
                detail,
                x + 30,
                y + 22,
                textWidth,
                reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED && !rewardTypeAvailable
                    ? 0xFFFFA0A0
                    : 0xFFB8C0CC
            );
            y += 45;
            if (reward.kind() == QuestDefinition.RewardKind.SELECTABLE) {
                String selectionKey = quest.definition().id() + "|" + reward.id();
                Set<String> selected = model.rewardSelections().getOrDefault(selectionKey, Set.of());
                for (QuestDefinition.Reward choice : reward
                    .rewards()
                    .values()) {
                    boolean chosen = selected.contains(choice.id());
                    int choiceHeight = 34;
                    graphics.fill(
                        x + 10,
                        y,
                        x + contentWidth,
                        y + choiceHeight,
                        chosen ? 0xFF344637 : 0xFF292E35
                    );
                    graphics.outline(
                        x + 10,
                        y,
                        contentWidth - 10,
                        choiceHeight,
                        chosen ? 0xFF55D86A : 0xFF626A76
                    );
                    QuestPresentation.renderRewardIcon(graphics, choice, x + 16, y + 9);
                    registerRecipeViewerTarget(QuestPresentation.rewardIconTarget(choice), x + 16, y + 9);
                    drawClippedDetailText(
                        graphics,
                        QuestPresentation.rewardTitle(choice),
                        x + 39,
                        y + 7,
                        Math.max(1, contentWidth - 45),
                        0xFFFFFFFF
                    );
                    graphics.text(
                        font,
                        Component.literal(
                            chosen ? "Selected" : "Click to select"
                        ),
                        x + 39,
                        y + 20,
                        chosen ? 0xFF7DE68D : 0xFFADB4BF,
                        false
                    );
                    rewardChoiceTargets.add(
                        new RewardChoiceTarget(
                            selectionKey,
                            choice.id(),
                            reward.amount(),
                            new Bounds(
                                x + 10,
                                y,
                                contentWidth - 10,
                                choiceHeight
                            )
                        )
                    );
                    y += choiceHeight + 4;
                }
                y += 3;
            }
        }
        return y - startY;
    }

    private int drawSectionHeading(
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
        int titleWidth = font.width(title) + 14;
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
            font,
            Component.literal(title),
            x + 7,
            y + 4,
            0xFFFFFFFF,
            true
        );
        String amount = Integer.toString(count);
        graphics.text(
            font,
            Component.literal(amount),
            x + width - font.width(amount) - 5,
            y + 4,
            0xFFB8C0CC,
            false
        );
        return y + 19;
    }

    private int drawTaskTree(
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
            font,
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

    private int drawTaskCard(
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
        taskCardTargets.add(new ProgressCardTarget(
            "task",
            progressKey,
            QuestPresentation.taskTitle(task),
            new Bounds(x, y, width, CARD_HEIGHT)
        ));
        if (
            task.kind() == QuestDefinition.TaskKind.CHECK &&
            !hasCustomTaskIcon(task)
        ) {
            graphics.blit(CHECK_ICON, x + 7, y + 11, x + 23, y + 27, 0, 0, 1, 1);
        } else {
            QuestPresentation.renderTaskIcon(graphics, task, x + 7, y + 11);
            registerRecipeViewerTarget(QuestPresentation.taskIconTarget(task), x + 7, y + 11);
        }
        String progressText = progress + "/" + task.target();
        int textX = x + 30;
        drawClippedDetailText(
            graphics,
            QuestPresentation.taskTitle(task),
            textX,
            y + 6,
            Math.max(1, x + width - font.width(progressText) - 10 - textX),
            complete ? 0xFFD8F5DD : 0xFFFFFFFF
        );
        drawClippedDetailText(
            graphics,
            QuestPresentation.taskDescription(task),
            textX,
            y + 18,
            Math.max(1, x + width - 6 - textX),
            0xFFADB4BF
        );
        graphics.text(
            font,
            Component.literal(progressText),
            x + width - font.width(progressText) - 5,
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

    private static void drawProgressBar(
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

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Theseus.MOD_ID, path);
    }

    private static boolean hasCustomTaskIcon(QuestDefinition.Task task) {
        return task.source().has("icon") && !task.source().get("icon").isJsonNull();
    }


    private void drawDetailTextTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        DetailTextTarget hovered = detailTextTargets.stream()
            .filter(value -> value.bounds().contains(mouseX, mouseY))
            .reduce((first, second) -> second).orElse(null);
        if (hovered == null) return;
        int maximumWidth = Math.min(280, Math.max(120, width - 24));
        Component text = Component.literal(hovered.text());
        int tooltipWidth = Math.min(maximumWidth, Math.max(48, Math.min(font.width(hovered.text()) + 12, maximumWidth)));
        int tooltipHeight = font.wordWrapHeight(text, tooltipWidth - 12) + 10;
        int x = Math.max(6, Math.min(mouseX + 10, width - tooltipWidth - 6));
        int y = Math.max(6, Math.min(mouseY + 10, height - tooltipHeight - 6));
        graphics.fill(x, y, x + tooltipWidth, y + tooltipHeight, 0xF020242B);
        graphics.outline(x, y, tooltipWidth, tooltipHeight, 0xFF8A929F);
        graphics.textWithWordWrap(font, text, x + 6, y + 5, tooltipWidth - 12, 0xFFFFFFFF, false);
    }

    private static Component status(QuestData quest) {
        if (!quest.unlocked()) return Component.translatable("quest.theseus.locked");
        if (quest.claimed()) return Component.translatable("quest.theseus.completed_claimed");
        if (quest.complete()) return Component.translatable("quest.theseus.completed");
        return Component.translatable("quest.theseus.in_progress");
    }

    private static int nodeStateColor(QuestData quest) {
        if (!quest.unlocked()) return 0xFF737B87;
        if (quest.claimed()) return 0xFF55D86A;
        if (quest.complete()) return 0xFFFFD966;
        return 0xFF4C9AFF;
    }

    private static double questProgress(QuestData quest) {
        if (quest.definition().tasks().isEmpty()) return quest.complete() ? 1 : 0;
        double progress = 0;
        for (QuestDefinition.Task task : quest.definition().tasks().values()) {
            progress += Math.min(1, quest.progress().getOrDefault(task.id(), 0) / (double) Math.max(1, task.target()));
        }
        return progress / quest.definition().tasks().size();
    }

    private static String descriptionReference(QuestData quest, DescriptionDocument.BlockKind kind, String id) {
        if (kind == DescriptionDocument.BlockKind.TASK) {
            QuestDefinition.Task task = quest.definition().tasks().get(id);
            return task == null ? id + " (missing)" : task.title();
        }
        QuestDefinition.Reward reward = quest.definition().rewards().get(id);
        return reward == null ? id + " (missing)" : reward.title();
    }

    private static boolean isRewardTypeAvailable(QuestDefinition.Reward reward, Set<String> serverRewardTypes) {
        if (reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED && !serverRewardTypes.contains(reward.type())) return false;
        return reward.kind() != QuestDefinition.RewardKind.SELECTABLE
            || reward.rewards().values().stream().allMatch(choice -> isRewardTypeAvailable(choice, serverRewardTypes));
    }



    private static Component visibilityLabel(QuestDefinition.Visibility visibility) {
        return switch (visibility) {
            case NEVER -> Component.translatable("gui.theseus.editor.visibility.never");
            case LOCKED -> Component.translatable("quest.theseus.locked");
            case DEPENDENCIES_VISIBLE -> Component.translatable("quest.theseus.dependencies_visible");
            case IN_PROGRESS -> Component.translatable("quest.theseus.in_progress");
            case COMPLETED -> Component.translatable("quest.theseus.completed");
        };
    }



    record QuestData(QuestDefinition definition, Map<String, Integer> progress, boolean unlocked,
        boolean complete, boolean claimed, Set<String> claimedRewards) {}

    record Model(QuestData selected, QuestSurfaceLayout.LockExplanation lockExplanation,
        QuestScreen.DetailTab tab, Map<String, Set<String>> rewardSelections, Set<String> serverRewardTypes) {}

    record RewardChoiceTarget(String selectionKey, String choiceId, int maximumSelections, Bounds bounds) {}

    record ProgressCardTarget(String kind, String id, String displayLabel, Bounds bounds) {}

    private record DetailTextTarget(Bounds bounds, String text) {}

    private record LockQuestTarget(Bounds bounds, String questId) {}

    private record Bounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
