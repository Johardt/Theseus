package me.johardt.theseus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Lossless quest-icon configuration with a small typed projection for clients.
 *
 * <p>Legacy primitive item icons and unknown object shapes retain their source
 * representation. Callers must only replace the value after an explicit icon
 * edit.</p>
 */
public final class QuestIconDefinition {
    public static final String ITEM_TYPE = "theseus:item";

    private final JsonElement source;
    private final String type;
    private final String item;
    private final boolean configured;

    private QuestIconDefinition(JsonElement source, String type, String item, boolean configured) {
        this.source = source == null ? JsonNull.INSTANCE : source.deepCopy();
        this.type = type;
        this.item = item;
        this.configured = configured;
    }

    public static QuestIconDefinition parse(JsonElement source, String fallbackItem) {
        String fallback = fallbackItem == null || fallbackItem.isBlank() ? "minecraft:map" : fallbackItem;
        if (source == null || source.isJsonNull()) {
            return new QuestIconDefinition(JsonNull.INSTANCE, ITEM_TYPE, fallback, false);
        }
        if (source.isJsonPrimitive() && source.getAsJsonPrimitive().isString()) {
            return new QuestIconDefinition(source, ITEM_TYPE, source.getAsString(), true);
        }
        if (!source.isJsonObject()) {
            return new QuestIconDefinition(source, "theseus:unknown", fallback, true);
        }
        JsonObject object = source.getAsJsonObject();
        String type = primitiveString(object.get("type"), object.has("item") ? ITEM_TYPE : "theseus:unknown");
        String item = primitiveString(object.get("item"), fallback);
        return new QuestIconDefinition(source, type, item, true);
    }

    public static QuestIconDefinition item(String item) {
        JsonObject source = new JsonObject();
        source.addProperty("type", ITEM_TYPE);
        source.addProperty("item", item == null || item.isBlank() ? "minecraft:map" : item);
        return parse(source, "minecraft:map");
    }

    public String type() { return type; }
    public String item() { return item; }
    public boolean configured() { return configured; }
    public JsonElement source() { return source.deepCopy(); }

    private static String primitiveString(JsonElement value, String fallback) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
            ? value.getAsString()
            : fallback;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuestIconDefinition icon
            && configured == icon.configured
            && type.equals(icon.type)
            && item.equals(icon.item)
            && source.equals(icon.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, type, item, configured);
    }

    @Override
    public String toString() {
        return source.toString();
    }
}
