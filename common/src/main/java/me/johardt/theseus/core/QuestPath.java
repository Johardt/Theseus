package me.johardt.theseus.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A structured path into a quest document.  A path deliberately distinguishes
 * schema fields from map entries so IDs containing dots remain unambiguous.
 */
public record QuestPath(List<Segment> segments) {
    public QuestPath {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    public static QuestPath root() {
        return new QuestPath(List.of());
    }

    public QuestPath field(String name) {
        return append(new Segment(Kind.FIELD, name));
    }

    public QuestPath key(String name) {
        return append(new Segment(Kind.KEY, name));
    }

    public QuestPath append(Segment segment) {
        if (segment == null || segment.value() == null || segment.value().isEmpty()) {
            throw new IllegalArgumentException("Path segment is required");
        }
        List<Segment> next = new ArrayList<>(segments);
        next.add(segment);
        return new QuestPath(next);
    }

    public String rootName() {
        return segments.isEmpty() ? "" : segments.getFirst().value();
    }

    public JsonArray toJson() {
        JsonArray encoded = new JsonArray();
        segments.forEach(segment -> {
            JsonObject value = new JsonObject();
            value.addProperty("kind", segment.kind().name().toLowerCase(java.util.Locale.ROOT));
            value.addProperty("value", segment.value());
            encoded.add(value);
        });
        return encoded;
    }

    public static QuestPath fromJson(JsonArray encoded) {
        List<Segment> segments = new ArrayList<>();
        if (encoded == null) return root();
        encoded.forEach(value -> {
            if (!value.isJsonObject()) return;
            JsonObject object = value.getAsJsonObject();
            String kind = object.has("kind") ? object.get("kind").getAsString() : "field";
            String name = object.has("value") ? object.get("value").getAsString() : "";
            if (name.isBlank()) return;
            segments.add(new Segment(
                "key".equalsIgnoreCase(kind) ? Kind.KEY : Kind.FIELD,
                name
            ));
        });
        return new QuestPath(segments);
    }

    @Override
    public String toString() {
        if (segments.isEmpty()) return "$";
        return "$" + segments.stream().map(segment ->
            segment.kind() == Kind.KEY
                ? "[" + segment.value() + "]"
                : "." + segment.value()
        ).collect(java.util.stream.Collectors.joining());
    }

    public enum Kind { FIELD, KEY }
    public record Segment(Kind kind, String value) {
        public Segment {
            if (kind == null || value == null || value.isBlank()) {
                throw new IllegalArgumentException("Path segment kind and value are required");
            }
        }
    }
}
