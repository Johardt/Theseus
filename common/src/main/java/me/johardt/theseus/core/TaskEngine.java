package me.johardt.theseus.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pure task evaluation module. NeoForge adapters translate game events into signals. */
public final class TaskEngine {
    private final Map<String, Handler> handlers;

    private TaskEngine(Map<String, Handler> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TaskEngine defaults() {
        return defaultBuilder().build();
    }

    public static Builder defaultBuilder() {
        Builder builder = builder();
        Map<String, Handler> handlers = builder.handlers;
        handlers.put("theseus:dummy", (task, progress, signal) ->
            new Result(signal instanceof Signal.Manual manual && task.value().equals(manual.value()) ? 1 : progress, 0));
        handlers.put("theseus:check", (task, progress, signal) ->
            new Result(signal instanceof Signal.Check check && check.submitted()
                && RegistryPredicate.contains(task.source().has("components") ? task.source().get("components") : task.source().get("nbt"), check.data()) ? 1 : progress, 0));
        handlers.put("theseus:kill_entity", (task, progress, signal) -> {
            if (signal instanceof Signal.EntityKilled killed
                && RegistryPredicate.matches(task.source().get("entity"), task.value(), killed.entity())) {
                return new Result(Math.min(task.target(), progress + 1), 0);
            }
            return new Result(progress, 0);
        });
        handlers.put("theseus:item", (task, progress, signal) -> {
            if (!(signal instanceof Signal.Inventory inventory)) {
                return new Result(progress, 0);
            }
            int count = inventory.entries().stream()
                .filter(entry -> RegistryPredicate.matches(task.source().get("item"), task.value(), entry))
                .filter(entry -> RegistryPredicate.contains(task.source().get("components"), entry.data()))
                .filter(entry -> RegistryPredicate.contains(task.source().get("nbt"), entry.data()))
                .mapToInt(Signal.RegistryEntry::count).sum();
            String collection = itemCollection(task);
            int remaining = Math.max(0, task.target() - progress);
            return switch (collection) {
                case "consume" -> count >= remaining
                    ? new Result(task.target(), remaining)
                    : new Result(progress, 0);
                case "manual" -> inventory.submit()
                    ? new Result(progress + Math.min(remaining, count), Math.min(remaining, count))
                    : new Result(progress, 0);
                default -> new Result(Math.min(task.target(), count), 0);
            };
        });
        handlers.put("theseus:advancement", (task, progress, signal) -> {
            if (!(signal instanceof Signal.AdvancementGranted advancement)) return new Result(progress, 0);
            var configured = task.source().get("advancements");
            boolean matches = configured != null && configured.isJsonArray()
                ? configured.getAsJsonArray().asList().stream().anyMatch(value -> value.getAsString().equals(advancement.advancement()))
                : task.value().equals(advancement.advancement());
            return new Result(matches ? 1 : progress, 0);
        });
        handlers.put("theseus:recipe", (task, progress, signal) -> {
            if (!(signal instanceof Signal.RecipeUnlocked recipe)) return new Result(progress, 0);
            return new Result(matches(task.source().get("recipes"), task.value(), recipe.recipe()) ? 1 : progress, 0);
        });
        handlers.put("theseus:stat", (task, progress, signal) -> {
            if (!(signal instanceof Signal.Statistic statistic) || !task.value().equals(statistic.stat())) return new Result(progress, 0);
            return new Result(Math.min(task.target(), Math.max(progress, statistic.value())), 0);
        });
        handlers.put("theseus:structure", (task, progress, signal) -> {
            if (!(signal instanceof Signal.Structures structures)) return new Result(progress, 0);
            boolean matched = structures.values().stream()
                .anyMatch(value -> RegistryPredicate.matches(task.source().get("structures"), task.value(), value));
            return new Result(matched ? 1 : progress, 0);
        });
        handlers.put("theseus:xp", (task, progress, signal) -> {
            if (!(signal instanceof Signal.Experience experience)) return new Result(progress, 0);
            String unit = suffix(string(task, "xpType", "level"));
            String collection = suffix(string(task, "collectionType", string(task, "collection", "consume")));
            int available = unit.equals("points") ? experience.points() : experience.levels();
            int remaining = Math.max(0, task.target() - progress);
            return switch (collection) {
                case "automatic" -> new Result(Math.min(task.target(), Math.max(progress, available)), 0);
                case "manual" -> experience.submit() && available >= remaining ? new Result(task.target(), remaining) : new Result(progress, 0);
                default -> available >= remaining ? new Result(task.target(), remaining) : new Result(progress, 0);
            };
        });
        handlers.put("theseus:block_interaction", (task, progress, signal) ->
            new Result(signal instanceof Signal.BlockInteracted block
                && RegistryPredicate.matches(task.source().get("block"), task.value(), block.block()) ? 1 : progress, 0));
        handlers.put("theseus:entity_interaction", (task, progress, signal) ->
            new Result(signal instanceof Signal.EntityInteracted entity
                && RegistryPredicate.matches(task.source().get("entity"), task.value(), entity.entity()) ? 1 : progress, 0));
        handlers.put("theseus:item_interaction", (task, progress, signal) ->
            new Result(signal instanceof Signal.ItemInteracted item
                && RegistryPredicate.matches(task.source().get("item"), task.value(), item.item())
                && RegistryPredicate.contains(task.source().get("components"), item.item().data()) ? 1 : progress, 0));
        handlers.put("theseus:item_use", (task, progress, signal) ->
            new Result(signal instanceof Signal.ItemUsed item
                && RegistryPredicate.matches(task.source().get("item"), task.value(), item.item())
                && RegistryPredicate.contains(task.source().get("components"), item.item().data()) ? 1 : progress, 0));
        handlers.put("theseus:biome", (task, progress, signal) ->
            new Result(signal instanceof Signal.WorldState world
                && RegistryPredicate.matches(task.source().get("biomes"), task.value(), world.biome()) ? 1 : progress, 0));
        handlers.put("theseus:changed_dimension", (task, progress, signal) -> {
            if (!(signal instanceof Signal.DimensionChanged changed)) return new Result(progress, 0);
            String from = string(task, "from", "");
            String to = string(task, "to", "");
            boolean matches = (from.isBlank() || from.equals(changed.from())) && (to.isBlank() || to.equals(changed.to()));
            return new Result(matches ? 1 : progress, 0);
        });
        handlers.put("theseus:location", (task, progress, signal) -> {
            if (!(signal instanceof Signal.WorldState world)) return new Result(progress, 0);
            var predicate = object(task.source(), "predicate");
            String dimension = jsonString(predicate, "dimension", "");
            var position = object(predicate, "position");
            boolean matches = (dimension.isBlank() || dimension.equals(world.dimension()))
                && RegistryPredicate.matches(predicate.has("biomes") ? predicate.get("biomes") : predicate.get("biome"), world.biome().id(), world.biome())
                && inRange(world.x(), object(position, "x"))
                && inRange(world.y(), object(position, "y"))
                && inRange(world.z(), object(position, "z"));
            return new Result(matches ? 1 : progress, 0);
        });
        return builder;
    }

    private static String suffix(String value) {
        int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
        return value.substring(separator + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String itemCollection(QuestDefinition.Task task) {
        if (task.source().has("collection")) return suffix(task.source().get("collection").getAsString());
        if (task.source().has("manual")) return task.source().get("manual").getAsBoolean() ? "manual" : "consume";
        return "automatic";
    }

    private static String string(QuestDefinition.Task task, String key, String fallback) {
        return task.source().has(key) ? task.source().get(key).getAsString() : fallback;
    }

    private static boolean matches(com.google.gson.JsonElement configured, String fallback, String actual) {
        if (configured == null) return fallback.equals(actual);
        if (configured.isJsonArray()) return configured.getAsJsonArray().asList().stream().anyMatch(value -> value.getAsString().equals(actual));
        if (configured.isJsonPrimitive()) return configured.getAsString().equals(actual);
        return fallback.equals(actual);
    }

    private static com.google.gson.JsonObject object(com.google.gson.JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new com.google.gson.JsonObject();
    }

    private static String jsonString(com.google.gson.JsonObject parent, String key, String fallback) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsString() : fallback;
    }

    private static boolean inRange(double value, com.google.gson.JsonObject range) {
        double minimum = range.has("min") ? range.get("min").getAsDouble() : Double.NEGATIVE_INFINITY;
        double maximum = range.has("max") ? range.get("max").getAsDouble() : Double.POSITIVE_INFINITY;
        return value >= minimum && value <= maximum;
    }

    public Result apply(QuestDefinition.Task task, int progress, Signal signal) {
        Handler handler = handlers.get(task.type());
        return handler == null ? new Result(progress, 0) : handler.apply(task, progress, signal);
    }

    /** Types this engine can execute; included in the editor synchronization. */
    public Set<String> types() {
        return Set.copyOf(handlers.keySet());
    }

    @FunctionalInterface
    public interface Handler {
        Result apply(QuestDefinition.Task task, int progress, Signal signal);
    }

    public static final class Builder {
        private final Map<String, Handler> handlers = new LinkedHashMap<>();

        public Builder register(String type, Handler handler) {
            if (handlers.putIfAbsent(type, handler) != null) {
                throw new IllegalArgumentException("Task handler already registered: " + type);
            }
            return this;
        }

        public TaskEngine build() {
            return new TaskEngine(handlers);
        }
    }

    public record Result(int progress, int consumeAmount) {}

    public interface Signal {
        record Manual(String value) implements Signal {}
        record Check(com.google.gson.JsonObject data, boolean submitted) implements Signal {
            public Check { data = data.deepCopy(); }
            public Check(boolean submitted) { this(new com.google.gson.JsonObject(), submitted); }
            public Check(com.google.gson.JsonObject data) { this(data, false); }
        }
        record EntityKilled(RegistryEntry entity) implements Signal {
            public EntityKilled(String entity) { this(RegistryEntry.simple(entity)); }
        }
        record RegistryEntry(String id, java.util.Set<String> tags, com.google.gson.JsonObject data, int count) {
            public RegistryEntry {
                tags = java.util.Set.copyOf(tags);
                data = data.deepCopy();
            }
            public static RegistryEntry simple(String id) {
                return new RegistryEntry(id, java.util.Set.of(), new com.google.gson.JsonObject(), 1);
            }
        }
        record Inventory(java.util.List<RegistryEntry> entries, boolean submit) implements Signal {
            public Inventory { entries = java.util.List.copyOf(entries); }
            public Inventory(String item, int count, boolean submit) {
                this(java.util.List.of(new RegistryEntry(item, java.util.Set.of(), new com.google.gson.JsonObject(), count)), submit);
            }
        }
        record AdvancementGranted(String advancement) implements Signal {}
        record RecipeUnlocked(String recipe) implements Signal {}
        record Statistic(String stat, int value) implements Signal {}
        record Structures(java.util.Set<RegistryEntry> values) implements Signal {
            public Structures { values = java.util.Set.copyOf(values); }
        }
        record Experience(int levels, int points, boolean submit) implements Signal {}
        record BlockInteracted(RegistryEntry block) implements Signal {
            public BlockInteracted(String block) { this(RegistryEntry.simple(block)); }
        }
        record EntityInteracted(RegistryEntry entity) implements Signal {
            public EntityInteracted(String entity) { this(RegistryEntry.simple(entity)); }
        }
        record ItemInteracted(RegistryEntry item) implements Signal {
            public ItemInteracted(String item) { this(RegistryEntry.simple(item)); }
        }
        record ItemUsed(RegistryEntry item) implements Signal {
            public ItemUsed(String item) { this(RegistryEntry.simple(item)); }
        }
        record WorldState(String dimension, RegistryEntry biome, double x, double y, double z) implements Signal {
            public WorldState(String dimension, String biome, double x, double y, double z) {
                this(dimension, RegistryEntry.simple(biome), x, y, z);
            }
        }
        record DimensionChanged(String from, String to) implements Signal {}
    }
}
