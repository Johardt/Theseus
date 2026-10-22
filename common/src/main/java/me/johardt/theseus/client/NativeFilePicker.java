package me.johardt.theseus.client;

import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import me.johardt.theseus.Theseus;

/** Small platform adapter for the native multi-file JSON picker. */
public final class NativeFilePicker {
    private NativeFilePicker() {}

    public static void open(Consumer<List<Path>> selected, Consumer<String> failed) {
        long startedAt = System.nanoTime();
        Theseus.LOGGER.info(
                "Native quest file picker requested on thread '{}' (headless property='{}', os='{}', java='{}')",
                Thread.currentThread().getName(),
                System.getProperty("java.awt.headless"),
                System.getProperty("os.name"),
                System.getProperty("java.version")
        );
        Thread picker = new Thread(() -> {
            Theseus.LOGGER.info("Native quest file picker worker started on thread '{}'", Thread.currentThread().getName());

            if (isMacOs()) {
                Theseus.LOGGER.info("Using macOS osascript file picker to avoid AWT initialization");
                openMacFilePicker(selected, failed);
                return;
            }

            // Minecraft's LWJGL window and AWT's desktop detection are independent.
            // Some development/client launchers pass this property even though the
            // client itself has a visible window. Set it before AWT initializes;
            // genuinely headless environments still fail gracefully below.
            if ("true".equalsIgnoreCase(System.getProperty("java.awt.headless"))) {
                Theseus.LOGGER.warn("Native quest file picker found java.awt.headless=true; overriding it for the visible client");
                System.setProperty("java.awt.headless", "false");
            }

            try {
                Theseus.LOGGER.info("Native quest file picker invoking AWT event thread (elapsed={} ms)", elapsedMillis(startedAt));
                EventQueue.invokeAndWait(() -> showDialog(selected, failed));
                Theseus.LOGGER.info("Native quest file picker AWT invocation completed (elapsed={} ms)", elapsedMillis(startedAt));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                Theseus.LOGGER.warn("Native quest file picker worker was interrupted after {} ms", elapsedMillis(startedAt), exception);
                failed.accept("The native file picker was interrupted");
            } catch (InvocationTargetException exception) {
                Theseus.LOGGER.error("Native quest file picker failed on the AWT event thread after {} ms", elapsedMillis(startedAt), exception.getCause());
                failed.accept(awtErrorMessage(exception.getCause()));
            } catch (RuntimeException exception) {
                Theseus.LOGGER.error("Native quest file picker worker failed after {} ms", elapsedMillis(startedAt), exception);
                failed.accept(awtErrorMessage(exception));
            }
        }, "theseus-file-picker");
        picker.setDaemon(true);
        picker.start();
        Theseus.LOGGER.info("Native quest file picker worker launched (elapsed={} ms)", elapsedMillis(startedAt));
    }

    private static void openMacFilePicker(Consumer<List<Path>> selected, Consumer<String> failed) {
        long startedAt = System.nanoTime();
        String script = "set selectedFiles to choose file with prompt \"Import Theseus quests\" of type {\"public.json\"} with multiple selections allowed\n"
                + "set output to \"\"\n"
                + "repeat with selectedFile in selectedFiles\n"
                + "    set output to output & (POSIX path of selectedFile) & linefeed\n"
                + "end repeat\n"
                + "return output";
        try {
            Process process = new ProcessBuilder("/usr/bin/osascript", "-e", script).start();
            Theseus.LOGGER.info("macOS osascript file picker launched (pid={}, elapsed={} ms)", process.pid(), elapsedMillis(startedAt));
            int exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (exitCode != 0) {
                if (errorOutput.toLowerCase(Locale.ROOT).contains("user canceled") || errorOutput.contains("-128")) {
                    Theseus.LOGGER.info("macOS osascript file picker was cancelled (elapsed={} ms)", elapsedMillis(startedAt));
                    selected.accept(List.of());
                    return;
                }
                throw new IOException("osascript exited with code " + exitCode + (errorOutput.isEmpty() ? "" : ": " + errorOutput));
            }

            List<Path> paths = output.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(Path::of)
                    .toList();
            Theseus.LOGGER.info("macOS osascript file picker returned {} file(s): {} (elapsed={} ms)", paths.size(), paths, elapsedMillis(startedAt));
            selected.accept(paths);
            Theseus.LOGGER.info("macOS osascript selection callback returned (elapsed={} ms)", elapsedMillis(startedAt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Theseus.LOGGER.warn("macOS osascript file picker was interrupted after {} ms", elapsedMillis(startedAt), exception);
            failed.accept("The native file picker was interrupted");
        } catch (IOException | RuntimeException exception) {
            Theseus.LOGGER.error("macOS osascript file picker failed after {} ms", elapsedMillis(startedAt), exception);
            failed.accept(errorMessage(exception));
        }
    }

    private static void showDialog(Consumer<List<Path>> selected, Consumer<String> failed) {
        long startedAt = System.nanoTime();
        Theseus.LOGGER.info(
                "Native quest file picker entered dialog phase on thread '{}' (AWT event thread={}, headless={}, headless property='{}')",
                Thread.currentThread().getName(),
                EventQueue.isDispatchThread(),
                GraphicsEnvironment.isHeadless(),
                System.getProperty("java.awt.headless")
        );
        try {
            if (GraphicsEnvironment.isHeadless()) {
                Theseus.LOGGER.warn("Native quest file picker is still headless after preparation; no dialog will be created");
                throw new IllegalStateException("Native file dialogs are unavailable in this Java runtime");
            }

            Theseus.LOGGER.info("Native quest file picker creating FileDialog (elapsed={} ms)", elapsedMillis(startedAt));
            FileDialog dialog = new FileDialog((Frame) null, "Import Theseus quests", FileDialog.LOAD);
            dialog.setMultipleMode(true);
            dialog.setFilenameFilter((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    Theseus.LOGGER.info("Native quest file picker windowClosed callback received");
                    dialog.dispose();
                }
            });
            Theseus.LOGGER.info("Native quest file picker showing FileDialog; the next log appears after it closes (elapsed={} ms)", elapsedMillis(startedAt));
            dialog.setVisible(true);

            File[] files = dialog.getFiles();
            List<Path> paths = files == null ? List.of() : Arrays.stream(files).map(File::toPath).toList();
            if (paths.isEmpty()) {
                Theseus.LOGGER.info("Native quest file picker closed without a selection (cancelled or no matching files, elapsed={} ms)", elapsedMillis(startedAt));
            } else {
                Theseus.LOGGER.info("Native quest file picker selected {} file(s): {} (elapsed={} ms)", paths.size(), paths, elapsedMillis(startedAt));
            }
            Theseus.LOGGER.info("Native quest file picker invoking selection callback");
            selected.accept(paths);
            Theseus.LOGGER.info("Native quest file picker selection callback returned");
        } catch (RuntimeException exception) {
            Theseus.LOGGER.error("Native quest file picker dialog phase failed after {} ms", elapsedMillis(startedAt), exception);
            failed.accept(awtErrorMessage(exception));
        }
    }

    private static String errorMessage(Throwable exception) {
        if (exception instanceof HeadlessException) {
            return "Native file dialogs are unavailable in this Java runtime; drag JSON files onto the quest window instead";
        }
        return exception == null || exception.getMessage() == null
                ? "Could not open the native file picker"
                : exception.getMessage();
    }

    private static String awtErrorMessage(Throwable exception) {
        if (exception instanceof HeadlessException || GraphicsEnvironment.isHeadless()) {
            return "Native file dialogs are unavailable in this Java runtime; drag JSON files onto the quest window instead";
        }
        return errorMessage(exception);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
