package me.johardt.theseus.addonexample;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.johardt.theseus.client.QuestIconRegistry;
import me.johardt.theseus.core.EditorTypeRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Client-only registrations kept separate from the dedicated-server example. */
public final class ExampleAddonClient {
    private ExampleAddonClient() {}

    public static void registerClientTypes() {
        EditorTypeRegistry.register(EditorTypeRegistry.Descriptor.editor(
            EditorTypeRegistry.Kind.TASK, ExampleAddon.TASK_TYPE, "Deliver Package", true, false
        ));
        EditorTypeRegistry.register(EditorTypeRegistry.Descriptor.editor(
            EditorTypeRegistry.Kind.REWARD, ExampleAddon.REWARD_TYPE, "Example Coins", true, false
        ));
        EditorTypeRegistry.register(EditorTypeRegistry.Descriptor.editor(
            EditorTypeRegistry.Kind.ICON, ExampleAddon.ICON_TYPE, "Example Badge", true, false
        ));
        QuestIconRegistry.register(new QuestIconRegistry.Descriptor(
            ExampleAddon.ICON_TYPE,
            "Example Badge",
            ExampleAddonClient::defaultIcon,
            ExampleAddonClient::renderBadge
        ));
    }

    private static JsonObject defaultIcon() {
        JsonObject source = new JsonObject();
        source.addProperty("type", ExampleAddon.ICON_TYPE);
        source.addProperty("badge", "gold");
        return source;
    }

    private static void renderBadge(GuiGraphicsExtractor graphics, JsonElement source, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, 0xFFFFB52E);
    }
}
