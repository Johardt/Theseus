package me.johardt.theseus.client;

import com.google.gson.JsonObject;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.ArrayList;
import java.util.List;
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
        return screen.importController.batchDiagnostics().isEmpty() ? 8 : 7;
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
        int left = (screen.guiWidth() - 500) / 2;
        int top = (screen.guiHeight() - 340) / 2;
        screen.importIdFields.clear();
        List<QuestImportController.Entry> entries = screen.importController.entries();
        int visibleRows = importVisibleRows();
        int first = Math.max(0, Math.min(screen.importScroll, Math.max(0, entries.size() - visibleRows)));
        int listTop = top + (screen.importController.batchDiagnostics().isEmpty() ? 52 : 64);
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int y = listTop + (index - first) * 32;
            EditBox id = new EditBox(screen.guiFont(), left + 250, y, 100, 18, Component.translatable("gui.theseus.editor.quest_id"));
            id.setValue(entry.id() == null ? "" : entry.id());
            id.setResponder(value -> {
                screen.importController.changeId(entry.key(), value);
                updateImportMessage();
            });
            screen.importIdFields.put(entry.key(), id);
            screen.addScreenWidget(id);
            screen.addScreenWidget(Widgets.button(widget -> {
                widget.withPosition(left + 354, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.details")));
                widget.active = !entry.diagnostics().isEmpty();
                widget.withCallback(() -> openImportDiagnostics(entry.key()));
                widget.withTooltip(Component.translatable("gui.theseus.editor.view_every_diagnostic_for_this_file"));
            }));
            screen.addScreenWidget(Widgets.button(widget -> {
                widget.withPosition(left + 420, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.remove")));
                widget.withCallback(() -> removeImportFile(entry.key()));
            }));
        }
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.cancel")));
            widget.withCallback(this::cancelImport);
        }));
        if (!screen.importController.batchDiagnostics().isEmpty()) screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 120, top + 304).withSize(120, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.batch_details")));
            widget.withCallback(this::openBatchDiagnostics);
            widget.withTooltip(Component.translatable("gui.theseus.editor.view_batch_level_server_diagnostics"));
        }));
        screen.addScreenWidget(Widgets.button(widget -> {
            widget.withPosition(left + 388, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.translatable("gui.theseus.editor.import")));
            widget.active = screen.importController.canSubmit() && !screen.mutations.isPending();
            widget.withCallback(this::sendImport);
        }));
    }

    void drawImportModal(GuiGraphicsExtractor graphics) {
        int left = (screen.guiWidth() - 500) / 2;
        int top = (screen.guiHeight() - 340) / 2;
        graphics.fill(0, 0, screen.guiWidth(), screen.guiHeight(), 0x99000000);
        graphics.fill(left, top, left + 500, top + 340, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + 499, top + 28, 0xFF303640);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.import_quests"), left + 12, top + 9, 0xFFFFFFFF, true);
        graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.each_file_is_checked_independently_import_is_all_or_nothing"), left + 12, top + 30, 0xFFB8C0CC, false);
        if (!screen.importController.batchDiagnostics().isEmpty()) {
            long errors = screen.importController.batchDiagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            graphics.text(screen.guiFont(), Component.translatable("gui.theseus.editor.batch_rejected", errors), left + 12, top + 42, 0xFFFF9999, false);
        }
        List<QuestImportController.Entry> entries = screen.importController.entries();
        int listTop = top + (screen.importController.batchDiagnostics().isEmpty() ? 52 : 64);
        int visibleRows = importVisibleRows();
        int first = Math.max(0, Math.min(screen.importScroll, Math.max(0, entries.size() - visibleRows)));
        graphics.enableScissor(left + 8, listTop - 4, left + 492, top + 292);
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int y = listTop + 4 + (index - first) * 32;
            int color = entry.valid() ? 0xFF77DD99 : 0xFFFF9999;
            String label = entry.key() + " (" + entry.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes)";
            if (label.length() > 42) label = label.substring(0, 41) + "…";
            graphics.text(screen.guiFont(), Component.literal(label), left + 12, y, 0xFFFFFFFF, false);
            long errors = entry.diagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            long warnings = entry.diagnostics().stream().filter(diagnostic -> diagnostic.severity() == QuestDiagnostics.Severity.WARNING).count();
            String detail = entry.diagnostics().isEmpty() ? "ready" : errors + " error(s), " + warnings + " warning(s) — Details";
            if (detail.length() > 42) detail = detail.substring(0, 41) + "…";
            graphics.text(screen.guiFont(), Component.literal(detail), left + 12, y + 14, color, false);
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
            screen.rebuildWidgets();
        }
        return changed;
    }

    void sendImport() {
        screen.editorMessage = "Importing…";
        screen.editorMessageSuccess = false;
        JsonObject request = screen.importController.request();
        request.addProperty("chapter", screen.group);
        screen.editor.sendEditorMutation(new QuestMutation.ImportQuests(request));
    }
}
