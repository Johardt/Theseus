package me.johardt.theseus.client;

import me.johardt.theseus.Theseus;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** Minecraft resource-reload adapter for {@link QuestTutorialContent}. */
public final class QuestTutorialContentLoader extends SimplePreparableReloadListener<String> {
    public static final Identifier TUTORIAL_LOCATION = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "tutorial.md"
    );
    public static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(
        Theseus.MOD_ID,
        "client_tutorial"
    );
    public static final QuestTutorialContentLoader INSTANCE = new QuestTutorialContentLoader();

    private QuestTutorialContentLoader() {}


    @Override
    protected @NotNull String prepare(ResourceManager manager, ProfilerFiller profiler) {
        Optional<net.minecraft.server.packs.resources.Resource> resource =
            manager.getResource(TUTORIAL_LOCATION);
        if (resource.isEmpty()) return QuestTutorialContent.fallback();
        try (var reader = resource.get().openAsReader()) {
            return QuestTutorialContent.read(reader);
        } catch (Exception exception) {
            Theseus.LOGGER.warn(
                "Could not load editor tutorial {}: {}",
                TUTORIAL_LOCATION,
                exception.getMessage()
            );
            return QuestTutorialContent.fallback();
        }
    }

    @Override
    protected void apply(String value, ResourceManager manager, ProfilerFiller profiler) {
        QuestTutorialContent.publish(value);
    }
}
