package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.theme.ClientTheme;
import me.johardt.theseus.client.theme.ClientThemeLoader;
import me.johardt.theseus.core.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

final class QuestHud {

    static final SystemToast.SystemToastId UNLOCK_TOAST =
        new SystemToast.SystemToastId();
    static final SystemToast.SystemToastId COMPLETE_TOAST =
        new SystemToast.SystemToastId();
    static final SystemToast.SystemToastId REWARD_TOAST =
        new SystemToast.SystemToastId();
    private static final Identifier CHECK_ICON = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "textures/item/check.png"
    );
    private static final Identifier TRACKER_HEADER = sprite("pinned/pinned_fake_popup_background");
    private static final Identifier TRACKER_BODY = sprite("pinned/pinned_fake_popup_border");
    private static final int TRACKER_HEADER_HEIGHT = 12;
    private static final int TRACKER_CONTENT_TOP_PADDING = 4;
    private static final int TRACKER_BOTTOM_PADDING = 7;

    private QuestHud() {}

    static void render(
        GuiGraphicsExtractor graphics,
        JsonObject snapshot,
        boolean collapsed
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (
            minecraft.player == null ||
            minecraft.options.hideGui ||
            snapshot == null
        ) return;
        var pinned = snapshot
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().isJsonObject() && !entry.getKey().startsWith("__"))
            .filter(entry -> {
                JsonObject quest = entry.getValue().getAsJsonObject();
                return (
                    quest.has("pinned") && quest.get("pinned").getAsBoolean()
                );
            })
            .limit(5)
            .toList();
        if (pinned.isEmpty()) return;

        ClientTheme.Tracker theme = ClientThemeLoader.active().tracker();

        int width = 168;
        int desiredHeight = collapsed
            ? TRACKER_HEADER_HEIGHT + TRACKER_BOTTOM_PADDING
            : TRACKER_HEADER_HEIGHT +
              TRACKER_CONTENT_TOP_PADDING +
              TRACKER_BOTTOM_PADDING +
              pinned
                  .stream()
                  .mapToInt(
                      entry ->
                          15 +
                          taskRows(entry.getKey(), entry.getValue().getAsJsonObject()).size() *
                          11
                  )
                  .sum();
        QuestHudLayout.Bounds bounds = QuestHudLayout.layout(
            graphics.guiWidth(),
            graphics.guiHeight(),
            width,
            desiredHeight,
            TheseusClientOptions.trackerAnchor()
        );
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        int x = bounds.x();
        int top = bounds.y();
        int height = bounds.height();
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.maxX(), bounds.maxY());
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            TRACKER_HEADER,
            x,
            top,
            bounds.width(),
            Math.min(TRACKER_HEADER_HEIGHT, height)
        );
        if (height > TRACKER_HEADER_HEIGHT) graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            TRACKER_BODY,
            x,
            top + TRACKER_HEADER_HEIGHT,
            bounds.width(),
            height - TRACKER_HEADER_HEIGHT
        );
        Component trackerTitle = Component.literal(
            "Pinned quests " + (collapsed ? "[J] +" : "[J] −")
        );
        graphics.text(
            minecraft.font,
            trackerTitle,
            x + (bounds.width() - minecraft.font.width(trackerTitle)) / 2,
            top + 2,
            theme.title(),
            true
        );
        if (collapsed) {
            graphics.disableScissor();
            return;
        }
        int y = top + TRACKER_HEADER_HEIGHT + TRACKER_CONTENT_TOP_PADDING;
        int contentBottom = bounds.maxY();
        boolean overflow = desiredHeight > bounds.height();
        int reservedOverflowHeight = overflow ? 9 : 0;
        for (var entry : pinned) {
            if (y + 9 > contentBottom - reservedOverflowHeight) {
                overflow = true;
                break;
            }
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition quest = QuestDefinition.parse(entry.getKey(), json);
            graphics.text(
                minecraft.font,
                Component.literal(quest.title()),
                x + 6,
                y,
                theme.quest(),
                true
            );
            y += 12;
            JsonObject progress = json.getAsJsonObject("progress");
            for (TaskRow row : taskRows(entry.getKey(), json)) {
                if (y + 9 > contentBottom - reservedOverflowHeight) {
                    overflow = true;
                    break;
                }
                int value = progress.has(row.path())
                    ? progress.get(row.path()).getAsInt()
                    : 0;
                boolean complete = value >= row.task().target();
                String marker = complete ? "" : "• ";
                String progressText = complete
                    ? "Done"
                    : value + "/" + row.task().target();
                int progressX = x + width - minecraft.font.width(progressText) - 6;
                int labelX = x + (complete ? 20 : 10);
                int labelWidth = progressX - labelX - 4;
                String label = minecraft.font.plainSubstrByWidth(
                    marker + QuestPresentation.taskTitle(row.task()),
                    labelWidth
                );
                if (complete) graphics.blit(
                    CHECK_ICON,
                    x + 10,
                    y,
                    x + 18,
                    y + 8,
                    0,
                    0,
                    1,
                    1
                );
                graphics.text(
                    minecraft.font,
                    Component.literal(label),
                    labelX,
                    y,
                    complete ? theme.completed() : theme.task(),
                    false
                );
                graphics.text(
                    minecraft.font,
                    Component.literal(progressText),
                    progressX,
                    y,
                    complete ? theme.completed() : theme.progress(),
                    false
                );
                y += 11;
            }
            if (overflow) break;
            y += 3;
        }
        if (overflow && contentBottom - 9 >= top + 10) {
            graphics.text(
                minecraft.font,
                Component.translatable("hud.theseus.pinned_quests.overflow"),
                x + 6,
                contentBottom - 10,
                theme.task(),
                false
            );
        }
        graphics.disableScissor();
    }

    private static List<TaskRow> taskRows(String id, JsonObject json) {
        QuestDefinition quest = QuestDefinition.parse(id, json);
        List<TaskRow> rows = new ArrayList<>();
        collectTaskRows(quest.tasks(), "", rows);
        return rows;
    }

    private static void collectTaskRows(
        java.util.Map<String, QuestDefinition.Task> tasks,
        String prefix,
        List<TaskRow> rows
    ) {
        for (QuestDefinition.Task task : tasks.values()) {
            String path = prefix.isEmpty()
                ? task.id()
                : prefix + "/" + task.id();
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE &&
                !task.tasks().isEmpty()
            ) {
                collectTaskRows(task.tasks(), path, rows);
            } else {
                rows.add(new TaskRow(path, task));
            }
        }
    }

    private record TaskRow(String path, QuestDefinition.Task task) {}

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Theseus.MOD_ID, path);
    }
}
