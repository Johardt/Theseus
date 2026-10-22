package me.johardt.theseus.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Immutable client colors. Resource packs may override any individual field. */
public record ClientTheme(
    QuestTree questTree,
    QuestDetails questDetails,
    Tracker tracker,
    Toasts toasts,
    Modals modals,
    Editor editor,
    GenericControls genericControls
) {
    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
        Map.entry("black", 0xFF000000),
        Map.entry("blue", 0xFF0000FF),
        Map.entry("cyan", 0xFF00FFFF),
        Map.entry("gray", 0xFF808080),
        Map.entry("grey", 0xFF808080),
        Map.entry("green", 0xFF008000),
        Map.entry("lime", 0xFF00FF00),
        Map.entry("magenta", 0xFFFF00FF),
        Map.entry("orange", 0xFFFFA500),
        Map.entry("purple", 0xFF800080),
        Map.entry("red", 0xFFFF0000),
        Map.entry("white", 0xFFFFFFFF),
        Map.entry("yellow", 0xFFFFFF00)
    );
    private static final Logger LOGGER = Logger.getLogger(ClientTheme.class.getName());

    public static final ClientTheme DEFAULT = new ClientTheme(
        new QuestTree(
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0x405A6472
        ),
        new QuestDetails(
            0xFFFFFFFF,
            0xFFADB4BF,
            0xFFFFFFFF,
            0xFFB8C0CC,
            0xFFFFFFFF,
            0xFF626A76,
            0xFFFFFFFF,
            0xFFB8C0CC,
            0xFFFFFFFF,
            0xFFFFD966,
            0xFFFFFFFF,
            0xFFB8C0CC,
            0xFFFFFFFF,
            0xFF5A4300
        ),
        new Tracker(
            0xFFFFD966,
            0xFFFFD966,
            0xFFD0D4DA,
            0xFFFFFFFF,
            0xFF70C779
        ),
        new Toasts(
            0xFFB52CC8,
            0xFF800080,
            0xFF404040,
            0xFFFFFFFF,
            0xFF808080,
            0xFFA0A0A0
        ),
        new Modals(
            0xFFFFFFFF,
            0xFFB8C0CC,
            0xFF20242B,
            0xFF8A929F,
            0xFFFF7777
        ),
        new Editor(
            0xFFB8C0CC,
            0xFFB8C0CC,
            0xFFB8C0CC,
            0xFFB8C0CC,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFF7777
        ),
        new GenericControls(
            0xFFFFFFFF,
            0xFFA0A0A0,
            0xFFFFFFFF,
            0xFFB8C0CC,
            0xFF4C9AFF,
            0xFFFF7777
        )
    );

    public ClientTheme {
        questTree = questTree == null ? DEFAULT.questTree() : questTree;
        questDetails = questDetails == null ? DEFAULT.questDetails() : questDetails;
        tracker = tracker == null ? DEFAULT.tracker() : tracker;
        toasts = toasts == null ? DEFAULT.toasts() : toasts;
        modals = modals == null ? DEFAULT.modals() : modals;
        editor = editor == null ? DEFAULT.editor() : editor;
        genericControls = genericControls == null ? DEFAULT.genericControls() : genericControls;
    }

    public static ClientTheme parse(String json) {
        try {
            return parse(JsonParser.parseString(json));
        } catch (Exception exception) {
            LOGGER.warning("Could not parse client theme: " + exception.getMessage());
            return DEFAULT;
        }
    }

    public static ClientTheme parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            LOGGER.warning("Could not parse client theme: root value is not an object");
            return DEFAULT;
        }
        try {
            JsonObject root = element.getAsJsonObject();
            return new ClientTheme(
                parseQuestTree(group(root, "questTree", "questsScreen", "questTree")),
                parseQuestDetails(group(root, "questDetails", "questScreen", "questDetails")),
                parseTracker(group(root, "tracker", "pinnedQuests", "tracker")),
                parseToasts(group(root, "toasts", null, "toasts")),
                parseModals(group(root, "modals", null, "modals")),
                parseEditor(group(root, "editor", null, "editor")),
                parseGenericControls(group(root, "genericControls", "generic", "genericControls"))
            );
        } catch (RuntimeException exception) {
            LOGGER.warning("Could not parse client theme: " + exception.getMessage());
            return DEFAULT;
        }
    }

    private static JsonObject group(
        JsonObject root,
        String preferred,
        String legacy,
        String label
    ) {
        JsonElement element = root.has(preferred)
            ? root.get(preferred)
            : legacy != null && root.has(legacy) ? root.get(legacy) : null;
        if (element == null) return null;
        if (!element.isJsonObject()) {
            LOGGER.warning("Ignoring malformed client theme group '" + label + "'; using defaults");
            return null;
        }
        return element.getAsJsonObject();
    }

    private static QuestTree parseQuestTree(JsonObject json) {
        return new QuestTree(
            color(json, "headerTitle", ClientTheme.DEFAULT.questTree().headerTitle(), "questTree"),
            color(json, "headerGroupsTitle", ClientTheme.DEFAULT.questTree().headerGroupsTitle(), "questTree"),
            color(json, "groupName", ClientTheme.DEFAULT.questTree().groupName(), "questTree"),
            color(json, "grid", ClientTheme.DEFAULT.questTree().grid(), "questTree")
        );
    }

    private static QuestDetails parseQuestDetails(JsonObject json) {
        QuestDetails defaults = ClientTheme.DEFAULT.questDetails();
        return new QuestDetails(
            color(json, "taskTitle", defaults.taskTitle(), "questDetails"),
            color(json, "taskDescription", defaults.taskDescription(), "questDetails"),
            color(json, "taskProgress", defaults.taskProgress(), "questDetails"),
            color(json, "taskNestedTitle", defaults.taskNestedTitle(), "questDetails"),
            color(json, "taskSubmit", defaults.taskSubmit(), "questDetails"),
            color(json, "taskSubmitDisabled", defaults.taskSubmitDisabled(), "questDetails"),
            color(json, "rewardTitle", defaults.rewardTitle(), "questDetails"),
            color(json, "rewardDescription", defaults.rewardDescription(), "questDetails"),
            color(json, "taskRewardStatusHeading", defaults.taskRewardStatusHeading(), "questDetails"),
            color(json, "summaryTitle", defaults.summaryTitle(), "questDetails"),
            color(json, "summaryProgress", defaults.summaryProgress(), "questDetails"),
            color(json, "summaryDescription", defaults.summaryDescription(), "questDetails"),
            color(json, "tabButton", defaults.tabButton(), "questDetails"),
            color(json, "tabButtonSelected", defaults.tabButtonSelected(), "questDetails")
        );
    }

    private static Tracker parseTracker(JsonObject json) {
        Tracker defaults = ClientTheme.DEFAULT.tracker();
        return new Tracker(
            color(json, "title", defaults.title(), "tracker"),
            color(json, "quest", defaults.quest(), "tracker"),
            color(json, "task", defaults.task(), "tracker"),
            color(json, "progress", defaults.progress(), "tracker"),
            color(json, "completed", defaults.completed(), "tracker")
        );
    }

    private static Toasts parseToasts(JsonObject json) {
        Toasts defaults = ClientTheme.DEFAULT.toasts();
        return new Toasts(
            color(json, "title", defaults.title(), "toasts"),
            color(json, "claimedTitle", defaults.claimedTitle(), "toasts"),
            color(json, "tutorialTitle", defaults.tutorialTitle(), "toasts"),
            color(json, "content", defaults.content(), "toasts"),
            color(json, "tutorialContent", defaults.tutorialContent(), "toasts"),
            color(json, "keybinding", defaults.keybinding(), "toasts")
        );
    }

    private static Modals parseModals(JsonObject json) {
        Modals defaults = ClientTheme.DEFAULT.modals();
        return new Modals(
            color(json, "title", defaults.title(), "modals"),
            color(json, "rewardsAmount", defaults.rewardsAmount(), "modals"),
            color(json, "background", defaults.background(), "modals"),
            color(json, "border", defaults.border(), "modals"),
            color(json, "error", defaults.error(), "modals")
        );
    }

    private static Editor parseEditor(JsonObject json) {
        Editor defaults = ClientTheme.DEFAULT.editor();
        return new Editor(
            color(json, "modalUploadingTitle", defaults.modalUploadingTitle(), "editor"),
            color(json, "modalDependenciesTitle", defaults.modalDependenciesTitle(), "editor"),
            color(json, "modalIconsTitle", defaults.modalIconsTitle(), "editor"),
            color(json, "modalTextTitle", defaults.modalTextTitle(), "editor"),
            color(json, "modalUploadingFileName", defaults.modalUploadingFileName(), "editor"),
            color(json, "modalDependenciesDependencyTitle", defaults.modalDependenciesDependencyTitle(), "editor"),
            color(json, "modalUploadingFileSize", defaults.modalUploadingFileSize(), "editor"),
            color(json, "modalEditSettingTitle", defaults.modalEditSettingTitle(), "editor"),
            color(json, "error", defaults.error(), "editor")
        );
    }

    private static GenericControls parseGenericControls(JsonObject json) {
        GenericControls defaults = ClientTheme.DEFAULT.genericControls();
        return new GenericControls(
            color(json, "buttonActive", defaults.buttonActive(), "genericControls"),
            color(json, "buttonInactive", defaults.buttonInactive(), "genericControls"),
            color(json, "buttonHover", defaults.buttonHover(), "genericControls"),
            color(json, "text", defaults.text(), "genericControls"),
            color(json, "accent", defaults.accent(), "genericControls"),
            color(json, "error", defaults.error(), "genericControls")
        );
    }

    private static int color(JsonObject json, String key, int fallback, String group) {
        if (json == null || !json.has(key)) return fallback;
        try {
            JsonElement element = json.get(key);
            if (element.isJsonObject()) {
                JsonObject channels = element.getAsJsonObject();
                int red = channel(channels, "r");
                int green = channel(channels, "g");
                int blue = channel(channels, "b");
                int alpha = channel(channels, "a");
                return alpha << 24 | red << 16 | green << 8 | blue;
            }
            if (!element.isJsonPrimitive()) throw new IllegalArgumentException("value is not a color");
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                long numeric = Long.parseLong(primitive.getAsString());
                if (numeric >= 0 && numeric <= 0xFFFFFF) {
                    return (int) (0xFF000000L | numeric);
                }
                if (numeric >= Integer.MIN_VALUE && numeric <= 0xFFFFFFFFL) {
                    return (int) numeric;
                }
                throw new IllegalArgumentException("numeric color is out of range");
            }
            String raw = primitive.getAsString().trim().toLowerCase(Locale.ROOT);
            Integer named = NAMED_COLORS.get(raw);
            if (named != null) return named;
            String digits = raw;
            if (digits.startsWith("#")) digits = digits.substring(1);
            else if (digits.startsWith("0x")) digits = digits.substring(2);
            long parsed = Long.parseLong(digits, 16);
            if (digits.length() == 6) return (int) (0xFF000000L | parsed);
            if (digits.length() == 8) return (int) parsed;
            throw new IllegalArgumentException("expected RRGGBB or AARRGGBB");
        } catch (Exception exception) {
            LOGGER.warning(
                "Ignoring malformed client theme color '" + group + "." + key +
                    "'; using 0x" + String.format(Locale.ROOT, "%08X", fallback) +
                    " (" + exception.getMessage() + ")"
            );
            return fallback;
        }
    }

    private static int channel(JsonObject color, String key) {
        if (!color.has(key)) return 255;
        int value = Integer.parseInt(color.get(key).getAsString());
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("channel '" + key + "' is out of range");
        }
        return value;
    }

    public record QuestTree(int headerTitle, int headerGroupsTitle, int groupName, int grid) {
        public QuestTree(int headerTitle, int headerGroupsTitle, int groupName) {
            this(headerTitle, headerGroupsTitle, groupName, ClientTheme.DEFAULT.questTree().grid());
        }
    }

    public record QuestDetails(
        int taskTitle,
        int taskDescription,
        int taskProgress,
        int taskNestedTitle,
        int taskSubmit,
        int taskSubmitDisabled,
        int rewardTitle,
        int rewardDescription,
        int taskRewardStatusHeading,
        int summaryTitle,
        int summaryProgress,
        int summaryDescription,
        int tabButton,
        int tabButtonSelected
    ) {}

    public record Tracker(int title, int quest, int task, int progress, int completed) {}

    public record Toasts(
        int title,
        int claimedTitle,
        int tutorialTitle,
        int content,
        int tutorialContent,
        int keybinding
    ) {}

    public record Modals(int title, int rewardsAmount, int background, int border, int error) {}

    public record Editor(
        int modalUploadingTitle,
        int modalDependenciesTitle,
        int modalIconsTitle,
        int modalTextTitle,
        int modalUploadingFileName,
        int modalDependenciesDependencyTitle,
        int modalUploadingFileSize,
        int modalEditSettingTitle,
        int error
    ) {}

    public record GenericControls(
        int buttonActive,
        int buttonInactive,
        int buttonHover,
        int text,
        int accent,
        int error
    ) {}
}
