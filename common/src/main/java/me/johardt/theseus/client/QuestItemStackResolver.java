package me.johardt.theseus.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/** Resolves the lossless item-shaped quest values used by targets and renderers. */
final class QuestItemStackResolver {
    private QuestItemStackResolver() {}

    static Optional<ItemStack> resolve(JsonElement source) {
        if (source == null || source.isJsonNull()) return Optional.empty();

        if (source.isJsonObject()) {
            JsonObject object = source.getAsJsonObject();
            JsonElement nested = object.get("item");
            if (nested != null && nested.isJsonObject()) {
                JsonObject merged = nested.getAsJsonObject().deepCopy();
                copyIfMissing(object, merged, "count");
                copyIfMissing(object, merged, "components");
                return resolve(merged);
            }
            if (nested != null) return resolveValue(nested, object);
            if (object.has("id")) return resolveValue(object.get("id"), object);
            if (object.has("tag")) {
                JsonElement tag = object.get("tag");
                if (!tag.isJsonPrimitive()) return Optional.empty();
                String value = tag.getAsString();
                return resolveValue(new com.google.gson.JsonPrimitive(
                    value.startsWith("#") ? value : "#" + value
                ), object);
            }
            return Optional.empty();
        }
        return resolveValue(source, null);
    }

    private static Optional<ItemStack> resolveValue(JsonElement value, JsonObject source) {
        if (value == null || !value.isJsonPrimitive()) return Optional.empty();
        String itemId;
        try {
            itemId = value.getAsString();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (itemId == null || itemId.isBlank()) return Optional.empty();

        int count = 1;
        if (source != null && source.has("count")
            && source.get("count").isJsonPrimitive()
            && source.get("count").getAsJsonPrimitive().isNumber()) {
            count = Math.max(1, source.get("count").getAsInt());
        }

        try {
            if (itemId.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(itemId.substring(1)));
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    return Optional.of(withComponents(new ItemStack(holder.value(), count), source));
                }
                return Optional.empty();
            }
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (item == null || item == Items.AIR) return Optional.empty();
            return Optional.of(withComponents(new ItemStack(item, count), source));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static ItemStack withComponents(ItemStack stack, JsonObject source) {
        if (source != null && source.has("components") && source.get("components").isJsonObject()) {
            try {
                DataComponentPatch.CODEC
                    .parse(JsonOps.INSTANCE, source.get("components"))
                    .result()
                    .ifPresent(stack::applyComponents);
            } catch (RuntimeException ignored) {
                // Invalid optional components must not make the whole quest icon fatal.
            }
        }
        return stack;
    }

    private static void copyIfMissing(JsonObject from, JsonObject to, String key) {
        if (from.has(key) && !to.has(key)) to.add(key, from.get(key).deepCopy());
    }
}
