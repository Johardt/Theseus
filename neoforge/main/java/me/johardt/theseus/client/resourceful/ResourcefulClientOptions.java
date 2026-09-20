package me.johardt.theseus.client.resourceful;

import com.teamresourceful.resourcefulconfig.api.annotations.Config;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.entries.Observable;
import me.johardt.theseus.Theseus;
import me.johardt.theseus.client.TheseusClientOptions;

/** Resourceful Config view of the versioned Theseus client preferences. */
@Config(value = "theseus_options", version = TheseusClientOptions.CURRENT_SCHEMA_VERSION)
@ConfigInfo(
    icon = "settings",
    title = "Theseus Options",
    titleTranslation = "config.theseus.options.title",
    description = "Client-side display and editor preferences for Theseus.",
    descriptionTranslation = "config.theseus.options.description"
)
public final class ResourcefulClientOptions {
    @ConfigEntry(id = "maxEditorHistory", translation = "config.theseus.option.max_editor_history")
    @ConfigOption.Range(min = 10, max = 1000)
    public static Observable<Integer> maxEditorHistory = Observable.of(
        TheseusClientOptions.DEFAULT_MAX_EDITOR_HISTORY
    );

    @ConfigEntry(id = "defaultMinimapMode", translation = "config.theseus.option.default_minimap_mode")
    public static Observable<TheseusClientOptions.MinimapMode> defaultMinimapMode = Observable.of(
        TheseusClientOptions.MinimapMode.UNDOCKED
    );

    @ConfigEntry(id = "disableMinimap", translation = "config.theseus.option.disable_minimap")
    public static Observable<Boolean> disableMinimap = Observable.of(false);

    @ConfigEntry(id = "minimapX", translation = "config.theseus.option.minimap_x")
    @ConfigOption.Range(min = 0, max = 1)
    @ConfigOption.Slider
    public static Observable<Double> minimapX = Observable.of(1.0);

    @ConfigEntry(id = "minimapY", translation = "config.theseus.option.minimap_y")
    @ConfigOption.Range(min = 0, max = 1)
    @ConfigOption.Slider
    public static Observable<Double> minimapY = Observable.of(1.0);

    @ConfigEntry(id = "showGrid", translation = "config.theseus.option.show_grid")
    public static Observable<Boolean> showGrid = Observable.of(false);

    @ConfigEntry(id = "snapToGrid", translation = "config.theseus.option.snap_to_grid")
    public static Observable<Boolean> snapToGrid = Observable.of(false);

    @ConfigEntry(id = "trackerAnchor", translation = "config.theseus.option.tracker_anchor")
    public static Observable<TheseusClientOptions.TrackerAnchor> trackerAnchor = Observable.of(
        TheseusClientOptions.TrackerAnchor.TOP_LEFT
    );

    @ConfigEntry(id = "tutorialAutoShow", translation = "config.theseus.option.tutorial_auto_show")
    public static Observable<Boolean> tutorialAutoShow = Observable.of(true);

    @ConfigEntry(id = "tutorialSeen", translation = "config.theseus.option.tutorial_seen")
    public static Observable<Boolean> tutorialSeen = Observable.of(false);

    @ConfigEntry(id = "trackerCollapsed", translation = "config.theseus.option.tracker_collapsed")
    public static Observable<Boolean> trackerCollapsed = Observable.of(false);

    private static Configurator configurator;
    private static boolean applying;

    private ResourcefulClientOptions() {}

    public static void register() {
        if (configurator != null) return;

        setFromPreferences(TheseusClientOptions.preferences());
        addListeners();
        applying = true;
        try {
            configurator = new Configurator(Theseus.MOD_ID);
            configurator.register(ResourcefulClientOptions.class);
        } finally {
            applying = false;
        }

        TheseusClientOptions.applyExternalPreferences(currentPreferences());
        saveFromPreferences(TheseusClientOptions.preferences());
    }

    public static void saveFromPreferences(TheseusClientOptions.Preferences preferences) {
        if (configurator == null || preferences == null) return;

        applying = true;
        try {
            setFromPreferences(preferences);
            configurator.saveConfig(ResourcefulClientOptions.class);
        } finally {
            applying = false;
        }
    }

    private static void addListeners() {
        maxEditorHistory.addListener((previous, current) -> changed());
        defaultMinimapMode.addListener((previous, current) -> changed());
        disableMinimap.addListener((previous, current) -> changed());
        minimapX.addListener((previous, current) -> changed());
        minimapY.addListener((previous, current) -> changed());
        showGrid.addListener((previous, current) -> changed());
        snapToGrid.addListener((previous, current) -> changed());
        trackerAnchor.addListener((previous, current) -> changed());
        tutorialAutoShow.addListener((previous, current) -> changed());
        tutorialSeen.addListener((previous, current) -> changed());
        trackerCollapsed.addListener((previous, current) -> changed());
    }

    private static void changed() {
        if (!applying) TheseusClientOptions.applyExternalPreferences(currentPreferences());
    }

    private static TheseusClientOptions.Preferences currentPreferences() {
        return new TheseusClientOptions.Preferences(
            TheseusClientOptions.CURRENT_SCHEMA_VERSION,
            maxEditorHistory.get(),
            defaultMinimapMode.get(),
            disableMinimap.get(),
            minimapX.get(),
            minimapY.get(),
            showGrid.get(),
            snapToGrid.get(),
            trackerAnchor.get(),
            tutorialAutoShow.get(),
            tutorialSeen.get(),
            trackerCollapsed.get()
        );
    }

    private static void setFromPreferences(TheseusClientOptions.Preferences preferences) {
        maxEditorHistory = update(maxEditorHistory, preferences.maxEditorHistory());
        defaultMinimapMode = update(defaultMinimapMode, preferences.defaultMinimapMode());
        disableMinimap = update(disableMinimap, preferences.disableMinimap());
        minimapX = update(minimapX, preferences.minimapX());
        minimapY = update(minimapY, preferences.minimapY());
        showGrid = update(showGrid, preferences.showGrid());
        snapToGrid = update(snapToGrid, preferences.snapToGrid());
        trackerAnchor = update(trackerAnchor, preferences.trackerAnchor());
        tutorialAutoShow = update(tutorialAutoShow, preferences.tutorialAutoShow());
        tutorialSeen = update(tutorialSeen, preferences.tutorialSeen());
        trackerCollapsed = update(trackerCollapsed, preferences.trackerCollapsed());
    }

    private static <T> Observable<T> update(Observable<T> current, T value) {
        current.accept(value);
        return current;
    }
}
