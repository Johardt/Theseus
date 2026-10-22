package me.johardt.theseus.client.theme;

import com.google.gson.JsonParser;
import me.johardt.theseus.Theseus;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Resource-reload adapter for the pure {@link ClientTheme} parser. */
public final class ClientThemeLoader extends SimplePreparableReloadListener<ClientTheme> {
    public static final Identifier THEME_LOCATION = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "theme.json"
    );
    public static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "client_theme"
    );
    public static final ClientThemeLoader INSTANCE = new ClientThemeLoader();

    private static final AtomicReference<ClientTheme> ACTIVE = new AtomicReference<>(
        ClientTheme.DEFAULT
    );

    private ClientThemeLoader() {}

    public static ClientTheme active() {
        return ACTIVE.get();
    }


    @Override
    protected @NotNull ClientTheme prepare(
        ResourceManager manager,
        ProfilerFiller profiler
    ) {
        Optional<net.minecraft.server.packs.resources.Resource> resource =
            manager.getResource(THEME_LOCATION);
        if (resource.isEmpty()) return ClientTheme.DEFAULT;

        try (Reader reader = resource.get().openAsReader()) {
            return ClientTheme.parse(JsonParser.parseReader(reader));
        } catch (Exception exception) {
            Theseus.LOGGER.warn(
                "Could not load client theme {}: {}",
                THEME_LOCATION,
                exception.getMessage()
            );
            return ClientTheme.DEFAULT;
        }
    }

    @Override
    protected void apply(
        ClientTheme theme,
        ResourceManager manager,
        ProfilerFiller profiler
    ) {
        ACTIVE.set(theme == null ? ClientTheme.DEFAULT : theme);
    }
}
