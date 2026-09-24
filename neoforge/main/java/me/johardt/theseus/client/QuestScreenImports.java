package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.core.QuestDiagnostics;
import me.johardt.theseus.core.QuestMutation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import static me.johardt.theseus.client.QuestDraftValidation.*;
import static me.johardt.theseus.client.QuestEditorCatalog.*;
import static me.johardt.theseus.client.QuestScreen.*;

/** Handles quest file import state and diagnostics UI. */
final class QuestScreenImports {
    private final QuestScreen screen;
    private final Map<String, Button> diagnosticButtons = new LinkedHashMap<>();
    private Button importButton;

    QuestScreenImports(QuestScreen screen) {
        this.screen = screen;
    }

    void closeDiagnosticsModal() {
        screen.modalHost.close();
    }

    void openImportDiagnostics(String key) {
        QuestImportController.Entry entry = screen.importController.entries().stream()
            .filter(candidate -> candidate.key().equals(key))
            .findFirst()
            .orElse(null);
        if (entry == null || entry.diagnostics().isEmpty()) return;
        screen.diagnostics = entry.diagnostics();
        screen.diagnosticsScroll = 0;
        screen.modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
        screen.rebuildWidgets();
    }

    void openBatchDiagnostics() {
        if (screen.importController.batchDiagnostics().isEmpty()) return;
        screen.diagnostics = screen.importController.batchDiagnostics();
        screen.diagnosticsScroll = 0;
        screen.modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
        screen.rebuildWidgets();
    }

    List<String> diagnosticLines(int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (QuestDiagnostics.Diagnostic diagnostic : screen.diagnostics) {
            String prefix = "[" + diagnostic.severity() + "] "
                + (diagnostic.questId() == null || diagnostic.questId().isBlank() ? "" : diagnostic.questId() + " ")
                + diagnostic.path();
            addWrappedDiagnosticLine(lines, prefix, maxWidth);
            addWrappedDiagnosticLine(lines, diagnostic.message(), maxWidth);
            if (diagnostic.suggestedFix() != null && !diagnostic.suggestedFix().isBlank()) {
                addWrappedDiagnosticLine(lines, "Fix: " + diagnostic.suggestedFix(), maxWidth);
            }
        }
        return List.copyOf(lines);
    }

    int importVisibleRows() {
        return importModalLayout().visibleRows();
    }

    private ImportModalLayout importModalLayout() {
        int width = Math.max(1, Math.min(500, screen.guiWidth() - 16));
        int height = Math.max(1, Math.min(340, screen.guiHeight() - 16));
        int left = (screen.guiWidth() - width) / 2;
        int top = (screen.guiHeight() - height) / 2;
        boolean hasBatchDiagnostics = !screen.importController.batchDiagnostics().isEmpty();
        boolean compactRows = width < 384;
        int rowHeight = compactRows ? 48 : 32;
        boolean compactHeader = height - 44 - (hasBatchDiagnostics ? 64 : 52) < rowHeight;
        int listTop = top + (compactHeader ? 26 : hasBatchDiagnostics ? 64 : 52);
        int footerY = top + height - 36;
        int listBottom = Math.max(listTop, footerY - (compactHeader ? 4 : 8));
        int visibleRows = Math.max(0, (listBottom - listTop) / rowHeight);

        int idX;
        int idWidth;
        int detailsX;
        int detailsWidth;
        int removeX;
        int removeWidth;
        int labelWidth;
        if (compactRows) {
            int insideWidth = Math.max(0, width - 24);
            int buttonWidth = Math.max(
                1,
                Math.min(62, (insideWidth - 8 - Math.min(52, insideWidth / 3)) / 2)
            );
            idWidth = Math.max(1, insideWidth - 2 * buttonWidth - 8);
            idX = left + 12;
            detailsX = idX + idWidth + 4;
            detailsWidth = buttonWidth;
            removeX = detailsX + detailsWidth + 4;
            removeWidth = buttonWidth;
            labelWidth = Math.max(1, width - 24);
        } else {
            int rowRight = left + width - 18;
            int controlsX = Math.max(left + 90, rowRight - 232);
            int controlsWidth = Math.max(1, rowRight - controlsX - 8);
            idWidth = Math.min(100, Math.max(1, controlsWidth - 124));
            int actionWidth = Math.max(1, Math.min(62, (controlsWidth - idWidth) / 2));
            idX = controlsX;
            detailsX = idX + idWidth + 4;
            detailsWidth = actionWidth;
            removeX = detailsX + detailsWidth + 4;
            removeWidth = Math.max(1, Math.min(62, rowRight - removeX));
            labelWidth = Math.max(1, controlsX - (left + 20));
        }

        int buttonCount = hasBatchDiagnostics ? 3 : 2;
        int buttonGap = 8;
        int targetButtonWidth = hasBatchDiagnostics ? 320 : 200;
        int availableButtonWidth = Math.max(buttonCount, width - 24 - buttonGap * (buttonCount - 1));
        double buttonScale = Math.min(1.0, availableButtonWidth / (double) targetButtonWidth);
        int cancelWidth = Math.max(1, (int) (100 * buttonScale));
        int batchDetailsWidth = hasBatchDiagnostics ? Math.max(1, (int) (120 * buttonScale)) : 0;
        int importWidth = Math.max(1, (int) (100 * buttonScale));
        int cancelX = left + 12;
        int batchDetailsX = cancelX + cancelWidth + buttonGap;
        int importX = left + width - 12 - importWidth;

        return new ImportModalLayout(
            left, top, width, height, listTop, listBottom, footerY,
            rowHeight, visibleRows, compactHeader, compactRows,
            idX, idWidth, detailsX, detailsWidth, removeX, removeWidth, labelWidth,
            cancelX, cancelWidth, batchDetailsX, batchDetailsWidth, importX, importWidth
        );
    }

    private String fitText(String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (screen.guiFont().width(value) <= maxWidth) return value;
        String suffix = "…";
        int suffixWidth = screen.guiFont().width(suffix);
        if (suffixWidth > maxWidth) return screen.guiFont().plainSubstrByWidth(value, maxWidth);
        int textWidth = maxWidth - suffixWidth;
        return screen.guiFont().plainSubstrByWidth(value, textWidth) + suffix;
    }

    void addWrappedDiagnosticLine(List<String> lines, String value, int maxWidth) {
        String remaining = value == null ? "" : value;
        if (remaining.isEmpty()) {
            lines.add("");
            return;
        }
        while (!remaining.isEmpty()) {
            String line = screen.guiFont().plainSubstrByWidth(remaining, maxWidth);
            if (line.isEmpty()) line = remaining.substring(0, 1);
            lines.add(line);
            remaining = remaining.substring(line.length()).stripLeading();
        }
    }

    void drawDiagnosticsModal(GuiGraphicsExtractor graphics) {
        int left = (screen.guiWidth() - 440) / 2;
        int top = (screen.guiHeight() - 300) / 2;
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x99000000);
        graphics.fill(left, top, left + 440, top + 300, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + 439, top + 28, 0xFF303640);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.validation_diagnostics"), left + 12, top + 9, 0xFFFFFFFF, true);
        int visibleRows = 13;
        List<String> lines = diagnosticLines(416);
        int maxScroll = Math.max(0, lines.size() - visibleRows);
        screen.diagnosticsScroll = Math.max(0, Math.min(maxScroll, screen.diagnosticsScroll));
        graphics.enableScissor(left + 8, top + 34, left + 432, top + 254);
        for (int index = screen.diagnosticsScroll; index < lines.size() && index < screen.diagnosticsScroll + visibleRows; index++) {
            int y = top + 38 + (index - screen.diagnosticsScroll) * 16;
            String line = lines.get(index);
            int color = line.startsWith("[ERROR]") ? 0xFFFF9999
                : line.startsWith("[WARNING]") ? 0xFFFFD27D
                : line.startsWith("Fix:") ? 0xFF9FDFFF : 0xFFB8C0CC;
            graphics.text(screen.guiFont(), Component.literal(line), left + 12, y, color, false);
        }
        graphics.disableScissor();
        if (screen.diagnostics.isEmpty()) graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.no_diagnostics_reported"), left + 12, top + 42, 0xFFB8C0CC, false);
        else if (maxScroll > 0) graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.scroll_for_more"), left + 12, top + 270, 0xFF8893A3, false);
    }

    void addImportModalWidgets() {
        ImportModalLayout layout = importModalLayout();
        screen.importIdFields.clear();
        diagnosticButtons.clear();
        importButton = null;
        List<QuestImportController.Entry> entries = screen.importController.entries();
        int visibleRows = layout.visibleRows();
        int first = Math.max(0, Math.min(screen.importScroll, Math.max(0, entries.size() - visibleRows)));
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int rowY = layout.listTop() + (index - first) * layout.rowHeight();
            int controlsY = rowY + (layout.compactRows() ? 26 : 0);
            EditBox id = new EditBox(
                screen.guiFont(), layout.idX(), controlsY, layout.idWidth(), 18,
                Component.translatable("gui.theseus.editor.quest_id")
            );
            id.setValue(entry.id() == null ? "" : entry.id());
            id.setResponder(value -> changeImportId(entry.key(), value));
            screen.importIdFields.put(entry.key(), id);
            screen.addScreenWidget(id);
            Button details = Widgets.button(widget -> {
                widget.withPosition(layout.detailsX(), controlsY).withSize(layout.detailsWidth(), 20);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.details")));
                widget.withCallback(() -> openImportDiagnostics(entry.key()));
                widget.withTooltip(Component.translatable("gui.theseus.editor.view_every_diagnostic_for_this_file"));
            });
            diagnosticButtons.put(entry.key(), details);
            screen.addScreenWidget(details);
            screen.addScreenWidget(Widgets.button(widget -> {
                widget.withPosition(layout.removeX(), controlsY).withSize(layout.removeWidth(), 20);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.remove")));
                widget.withCallback(() -> removeImportFile(entry.key()));
            }));
        }
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(layout.cancelX(), layout.footerY()).withSize(layout.cancelWidth(), 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(this::cancelImport);
        }));
        if (!screen.importController.batchDiagnostics().isEmpty()) screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(layout.batchDetailsX(), layout.footerY()).withSize(layout.batchDetailsWidth(), 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.batch_details")));
            widget.withCallback(this::openBatchDiagnostics);
            widget.withTooltip(Component.translatable("gui.theseus.editor.view_batch_level_server_diagnostics"));
        }));
        importButton = Widgets.button(widget -> {
            widget.withPosition(layout.importX(), layout.footerY()).withSize(layout.importWidth(), 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.import")));
            widget.withCallback(this::sendImport);
        });
        screen.addScreenWidget(importButton);
        refreshImportControls();
    }

    void drawImportModal(GuiGraphicsExtractor graphics) {
        ImportModalLayout layout = importModalLayout();
        int left = layout.left();
        int top = layout.top();
        int right = left + layout.width();
        int bottom = top + layout.height();
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x99000000);
        graphics.fill(left, top, right, bottom, 0xFF20242B);
        graphics.fill(left + 1, top + 1, right - 1, Math.min(bottom - 1, top + 28), 0xFF303640);
        graphics.text(
            screen.guiFont(),
            Component.literal(fitText(Component.translatable("gui.theseus.editor.import_quests").getString(), layout.width() - 24)),
            left + 12, top + 9, 0xFFFFFFFF, true
        );
        if (!layout.compactHeader()) graphics.text(
            screen.guiFont(),
            Component.literal(fitText(
                Component.translatable("gui.theseus.editor.each_file_is_checked_independently_import_is_all_or_nothing").getString(),
                layout.width() - 24
            )),
            left + 12, top + 30, 0xFFB8C0CC, false
        );
        if (!layout.compactHeader() && !screen.importController.batchDiagnostics().isEmpty()) {
            long errors = screen.importController.batchDiagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            graphics.text(
                screen.guiFont(),
                Component.literal(fitText(Component.translatable("gui.theseus.editor.batch_rejected", errors).getString(), layout.width() - 24)),
                left + 12, top + 42, 0xFFFF9999, false
            );
        }
        List<QuestImportController.Entry> entries = screen.importController.entries();
        int visibleRows = layout.visibleRows();
        int first = Math.max(0, Math.min(screen.importScroll, Math.max(0, entries.size() - visibleRows)));
        int scissorInset = Math.min(8, layout.width() / 2);
        graphics.enableScissor(left + scissorInset, layout.listTop() - 4, right - scissorInset, layout.listBottom());
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int rowY = layout.listTop() + (index - first) * layout.rowHeight();
            int textY = rowY + (layout.compactRows() ? 0 : 4);
            int color = entry.valid() ? 0xFF77DD99 : 0xFFFF9999;
            String label = entry.key() + " (" + entry.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes)";
            label = fitText(label, layout.labelWidth());
            graphics.text(screen.guiFont(), Component.literal(label), left + 12, textY, 0xFFFFFFFF, false);
            long errors = entry.diagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            long warnings = entry.diagnostics().stream().filter(diagnostic -> diagnostic.severity() == QuestDiagnostics.Severity.WARNING).count();
            String detail = entry.diagnostics().isEmpty() ? "ready" : errors + " error(s), " + warnings + " warning(s) — Details";
            detail = fitText(detail, layout.labelWidth());
            graphics.text(screen.guiFont(), Component.literal(detail), left + 12, textY + 14, color, false);
        }
        graphics.disableScissor();
    }

    void cancelImport() {
        screen.importController.clear();
        screen.modalHost.close();
        screen.importIdFields.clear();
        screen.rebuildWidgets();
    }

    /** Parsing is independent per file and failed files remain removable. */
    void onFilesDrop(List<java.nio.file.Path> paths) {
        screen.importController.addFiles(paths);
        updateImportMessage();
        if (!paths.isEmpty()) screen.modalHost.open(QuestModalHost.Modal.FILE_IMPORT);
        screen.rebuildWidgets();
    }

    void updateImportMessage() {
        boolean canSubmit = screen.importController.canSubmit();
        String summary = screen.importController.summary().replace('\n', ' ').replace('\r', ' ').strip();
        String status = canSubmit
            ? "Import ready (Ctrl-Enter to submit)."
            : "Import contains invalid files; remove or correct them.";
        screen.editorMessage = summary.isEmpty() ? status : summary + " " + status;
        screen.editorMessageSuccess = canSubmit;
    }

    void openNativeFilePicker() {
        Theseus.LOGGER.info(
            "Quest import button clicked on thread '{}' (headless property='{}')",
            Thread.currentThread().getName(),
            System.getProperty("java.awt.headless")
        );
        NativeFilePicker.open(
            paths -> {
                Theseus.LOGGER.info(
                    "Quest import picker returned {} file(s) on thread '{}'; scheduling processing on Minecraft thread",
                    paths.size(),
                    Thread.currentThread().getName()
                );
                Minecraft.getInstance().execute(() -> {
                    Theseus.LOGGER.info("Quest import processing started on thread '{}'", Thread.currentThread().getName());
                    try {
                        onFilesDrop(paths);
                        Theseus.LOGGER.info("Quest import processing completed for {} file(s)", paths.size());
                    } catch (RuntimeException exception) {
                        Theseus.LOGGER.error("Quest import processing failed for {} file(s)", paths.size(), exception);
                        screen.editorMessage = "Import failed; check the game log for details.";
                        screen.editorMessageSuccess = false;
                        screen.rebuildWidgets();
                    }
                });
            },
            error -> {
                Theseus.LOGGER.error("Quest import picker reported an error on thread '{}': {}", Thread.currentThread().getName(), error);
                Minecraft.getInstance().execute(() -> {
                    Theseus.LOGGER.info("Quest import error is being displayed on thread '{}'", Thread.currentThread().getName());
                    screen.editorMessage = error;
                    screen.editorMessageSuccess = false;
                    screen.rebuildWidgets();
                });
            }
        );
    }

    void removeImportFile(String key) {
        screen.importController.remove(key);
        updateImportMessage();
        screen.rebuildWidgets();
    }

    boolean changeImportId(String key, String id) {
        boolean changed = screen.importController.changeId(key, id);
        if (changed) {
            updateImportMessage();
            refreshImportControls();
        }
        return changed;
    }

    void sendImport() {
        JsonObject request = buildImportRequest(
            screen.importController,
            screen.mutations.isPending()
        );
        if (request == null) {
            refreshImportControls();
            return;
        }
        screen.editorMessage = "Importing…";
        screen.editorMessageSuccess = false;
        request.addProperty("chapter", screen.group);
        screen.editor.sendEditorMutation(new QuestMutation.ImportQuests(request));
        refreshImportControls();
    }

    void refreshImportControls() {
        if (importButton == null && diagnosticButtons.isEmpty()) return;
        ImportControlState state = importControlState(
            screen.importController,
            screen.mutations.isPending()
        );
        diagnosticButtons.forEach((key, button) ->
            button.active = state.diagnosticsEnabled().getOrDefault(key, false)
        );
        if (importButton != null) importButton.active = state.importEnabled();
    }

    static ImportControlState importControlState(
        QuestImportController controller,
        boolean pending
    ) {
        Map<String, Boolean> diagnosticsEnabled = new LinkedHashMap<>();
        controller.entries().forEach(entry ->
            diagnosticsEnabled.put(entry.key(), !entry.diagnostics().isEmpty())
        );
        return new ImportControlState(
            Map.copyOf(diagnosticsEnabled),
            controller.canSubmit() && !pending
        );
    }

    static JsonObject buildImportRequest(
        QuestImportController controller,
        boolean pending
    ) {
        if (!controller.canSubmit() || pending) return null;
        return controller.request();
    }

    private record ImportModalLayout(
        int left,
        int top,
        int width,
        int height,
        int listTop,
        int listBottom,
        int footerY,
        int rowHeight,
        int visibleRows,
        boolean compactHeader,
        boolean compactRows,
        int idX,
        int idWidth,
        int detailsX,
        int detailsWidth,
        int removeX,
        int removeWidth,
        int labelWidth,
        int cancelX,
        int cancelWidth,
        int batchDetailsX,
        int batchDetailsWidth,
        int importX,
        int importWidth
    ) {}

    record ImportControlState(Map<String, Boolean> diagnosticsEnabled, boolean importEnabled) {}
}
