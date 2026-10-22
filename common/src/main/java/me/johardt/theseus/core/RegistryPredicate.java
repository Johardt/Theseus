package me.johardt.theseus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** Matches registry values from the original Heracles format and optional structured data requirements. */
public final class RegistryPredicate {
    private RegistryPredicate() {}

    public static boolean matches(JsonElement configured, String fallback, TaskEngine.Signal.RegistryEntry actual) {
        if (configured == null || configured.isJsonNull()) return fallback.equals(actual.id());
        if (configured.isJsonArray()) {
            return configured.getAsJsonArray().asList().stream().anyMatch(value -> matches(value, fallback, actual));
        }
        if (configured.isJsonPrimitive()) {
            String value = configured.getAsString();
            return value.startsWith("#") ? actual.tags().contains(stripHash(value)) : value.equals(actual.id());
        }
        JsonObject object = configured.getAsJsonObject();
        if (object.has("values")) return matches(object.get("values"), fallback, actual);
        if (object.has("tag")) return actual.tags().contains(stripHash(object.get("tag").getAsString()));
        if (object.has("id")) return object.get("id").getAsString().equals(actual.id());
        if (object.has("type")) return matches(object.get("type"), fallback, actual);
        return fallback.equals(actual.id());
    }

    public static boolean contains(JsonElement required, JsonElement actual) {
        if (required == null || required.isJsonNull()) return true;
        if (actual == null || actual.isJsonNull()) return false;
        if (required.isJsonObject()) {
            if (!actual.isJsonObject()) return false;
            for (Map.Entry<String, JsonElement> entry : required.getAsJsonObject().entrySet()) {
                if (!contains(entry.getValue(), actual.getAsJsonObject().get(entry.getKey()))) return false;
            }
            return true;
        }
        if (required.isJsonArray()) {
            if (!actual.isJsonArray() || required.getAsJsonArray().size() > actual.getAsJsonArray().size()) return false;
            for (JsonElement expected : required.getAsJsonArray()) {
                boolean found = actual.getAsJsonArray().asList().stream().anyMatch(observed -> contains(expected, observed));
                if (!found) return false;
            }
            return true;
        }
        if (required.isJsonPrimitive() && actual.isJsonPrimitive()) {
            var expected = required.getAsJsonPrimitive();
            var observed = actual.getAsJsonPrimitive();
            if (expected.isBoolean() && observed.isNumber()) return expected.getAsBoolean() == (observed.getAsInt() != 0);
            if (expected.isNumber() && observed.isBoolean()) return (expected.getAsInt() != 0) == observed.getAsBoolean();
        }
        return required.equals(actual);
    }

    private static String stripHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }
}
