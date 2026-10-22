package me.johardt.theseus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.johardt.theseus.core.QuestIconDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Client extension seam for icon rendering and structured editor defaults. */
public final class QuestIconRegistry {
    private static final Map<String, Descriptor> TYPES = new LinkedHashMap<>();

    static {
        register(new Descriptor(
            QuestIconDefinition.ITEM_TYPE,
            "Item",
            () -> QuestIconDefinition.item("minecraft:map").source(),
            QuestIconRegistry::renderItem
        ));
    }

    private QuestIconRegistry() {}

    public static synchronized void register(Descriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.type().isBlank()) throw new IllegalArgumentException("Icon type is required");
        if (TYPES.putIfAbsent(descriptor.type(), descriptor) != null) {
            throw new IllegalArgumentException("Icon type already registered: " + descriptor.type());
        }
    }

    public static synchronized Descriptor descriptor(String type) {
        return TYPES.get(type);
    }

    public static synchronized Map<String, Descriptor> descriptors() {
        return Map.copyOf(TYPES);
    }

    /** Resolves only the built-in item icon type; custom renderers are never inferred as items. */
    static Optional<ItemStack> itemStack(QuestIconDefinition icon) {
        if (!QuestIconDefinition.ITEM_TYPE.equals(icon.type())) return Optional.empty();
        return QuestItemStackResolver.resolve(icon.source());
    }

    /** Returns false for an unknown type so the caller can render an explicit fallback. */
    public static boolean render(
        GuiGraphicsExtractor graphics,
        QuestIconDefinition icon,
        ItemStack fallback,
        int x,
        int y,
        int size
    ) {
        Descriptor descriptor = descriptor(icon.type());
        if (descriptor == null) {
            renderItemStack(
                graphics,
                fallback.isEmpty() ? new ItemStack(Items.BARRIER) : fallback,
                x,
                y,
                size
            );
            return false;
        }
        descriptor.renderer().render(graphics, icon.source(), x, y, size);
        return true;
    }

    private static void renderItem(
        GuiGraphicsExtractor graphics,
        JsonElement source,
        int x,
        int y,
        int size
    ) {
        renderItemStack(
            graphics,
            QuestItemStackResolver.resolve(source).orElseGet(() -> new ItemStack(Items.MAP)),
            x,
            y,
            size
        );
    }

    private static void renderItemStack(
        GuiGraphicsExtractor graphics,
        ItemStack stack,
        int x,
        int y,
        int size
    ) {
        int safeSize = QuestSurfaceLayout.clampIconSize(size);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(safeSize / 16.0f);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    public record Descriptor(
        String type,
        String label,
        Supplier<JsonElement> defaultSource,
        Renderer renderer
    ) {
        public Descriptor {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(defaultSource, "defaultSource");
            Objects.requireNonNull(renderer, "renderer");
        }

        public JsonElement createDefault() {
            JsonElement value = defaultSource.get();
            return value == null ? new JsonObject() : value.deepCopy();
        }
    }

    @FunctionalInterface
    public interface Renderer {
        void render(GuiGraphicsExtractor graphics, JsonElement source, int x, int y, int size);
    }
}
