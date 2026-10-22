package me.johardt.theseus.core;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Type-first editor metadata.  The editor can discover a type without knowing
 * how its form is implemented, while unknown types remain explicitly
 * read-only instead of being represented by a fake fallback.
 */
public final class EditorTypeRegistry {
    /** Client-side extension point; server sync still controls execution. */
    private static final Builder GLOBAL = defaultBuilder();
    private final Map<Key, Descriptor> descriptors;

    private EditorTypeRegistry(Map<Key, Descriptor> descriptors) {
        this.descriptors = Map.copyOf(descriptors);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EditorTypeRegistry defaults() {
        return defaultBuilder().build();
    }

    /** Returns a snapshot of descriptors registered by this client. */
    public static synchronized EditorTypeRegistry registered() {
        return GLOBAL.build();
    }

    /** Registers a client editor descriptor without coupling an addon to {@code QuestScreen}. */
    public static synchronized void register(Descriptor descriptor) {
        GLOBAL.register(descriptor);
    }

    private static Builder defaultBuilder() {
        Builder builder = builder();
        registerTasks(builder);
        registerRewards(builder);
        registerIcons(builder);
        return builder;
    }

    public Optional<Descriptor> find(Kind kind, String id) {
        return Optional.ofNullable(descriptors.get(new Key(kind, id)));
    }

    public Descriptor resolve(Kind kind, String id) {
        return find(kind, id).orElseGet(() -> Descriptor.unknown(kind, id));
    }

    public List<Descriptor> descriptors(Kind kind) {
        return descriptors.values().stream().filter(value -> value.kind() == kind).toList();
    }

    /**
     * Resolves client editor metadata against the authoritative server type
     * list. A descriptor alone never makes a type executable.
     */
    public Resolution resolve(Kind kind, String id, Set<String> serverTypes) {
        Descriptor descriptor = find(kind, id).orElse(null);
        boolean serverKnowsType = serverTypes != null && serverTypes.contains(id);
        if (descriptor != null && descriptor.editable() && serverKnowsType) {
            return new Resolution(Availability.EXECUTABLE_EDITABLE, descriptor);
        }
        if (serverKnowsType) {
            return new Resolution(Availability.EXECUTABLE_READ_ONLY,
                descriptor == null ? Descriptor.unknown(kind, id) : descriptor);
        }
        if (descriptor != null) return new Resolution(Availability.UNAVAILABLE_ON_SERVER, descriptor);
        return new Resolution(Availability.UNKNOWN_CONFIGURATION, Descriptor.unknown(kind, id));
    }

    public enum Availability {
        EXECUTABLE_EDITABLE,
        EXECUTABLE_READ_ONLY,
        UNAVAILABLE_ON_SERVER,
        UNKNOWN_CONFIGURATION
    }

    public record Resolution(Availability availability, Descriptor descriptor) {
        public boolean editable() { return availability == Availability.EXECUTABLE_EDITABLE; }
        public boolean executable() {
            return availability == Availability.EXECUTABLE_EDITABLE
                || availability == Availability.EXECUTABLE_READ_ONLY;
        }
    }

    public record Descriptor(
        Kind kind,
        String id,
        String label,
        String availabilityReason,
        boolean editable,
        boolean executable,
        boolean allowsNested
    ) {
        public Descriptor {
            if (kind == null || id == null || id.isBlank()) throw new IllegalArgumentException("Type kind and ID are required");
            label = label == null || label.isBlank() ? id : label;
            availabilityReason = availabilityReason == null ? "" : availabilityReason;
        }

        public static Descriptor builtIn(Kind kind, String id, String label) {
            return new Descriptor(kind, id, label, "", true, true, kind == Kind.TASK && id.equals("theseus:composite"));
        }

        /** An addon editor descriptor; server sync still determines execution. */
        public static Descriptor editor(Kind kind, String id, String label, boolean editable, boolean allowsNested) {
            return new Descriptor(kind, id, label, "", editable, false, allowsNested);
        }

        public static Descriptor unknown(Kind kind, String id) {
            return new Descriptor(kind, id == null || id.isBlank() ? "unknown" : id, "Unknown type", "No editor is registered for this type", false, false, false);
        }

        public JsonObject defaultSource() {
            JsonObject source = new JsonObject();
            source.addProperty("type", id);
            return source;
        }
    }

    public enum Kind { TASK, REWARD, ICON }

    public static final class Builder {
        private final Map<Key, Descriptor> descriptors = new LinkedHashMap<>();

        public Builder register(Descriptor descriptor) {
            Key key = new Key(descriptor.kind(), descriptor.id());
            if (descriptors.putIfAbsent(key, descriptor) != null) {
                throw new IllegalArgumentException("Editor type already registered: " + descriptor.kind() + " " + descriptor.id());
            }
            return this;
        }

        public EditorTypeRegistry build() {
            return new EditorTypeRegistry(descriptors);
        }
    }

    private record Key(Kind kind, String id) {}

    private static void registerTasks(Builder builder) {
        register(builder, Kind.TASK, "theseus:dummy", "Dummy");
        register(builder, Kind.TASK, "theseus:item", "Acquire Item");
        register(builder, Kind.TASK, "theseus:xp", "Experience");
        register(builder, Kind.TASK, "theseus:kill_entity", "Kill Entity");
        register(builder, Kind.TASK, "theseus:advancement", "Advancement");
        register(builder, Kind.TASK, "theseus:biome", "Biome");
        register(builder, Kind.TASK, "theseus:block_interaction", "Block Interaction");
        register(builder, Kind.TASK, "theseus:changed_dimension", "Changed Dimension");
        register(builder, Kind.TASK, "theseus:check", "Check");
        register(builder, Kind.TASK, "theseus:composite", "Composite");
        register(builder, Kind.TASK, "theseus:entity_interaction", "Entity Interaction");
        register(builder, Kind.TASK, "theseus:item_interaction", "Item Interaction");
        register(builder, Kind.TASK, "theseus:item_use", "Item Use");
        register(builder, Kind.TASK, "theseus:location", "Location");
        register(builder, Kind.TASK, "theseus:recipe", "Recipe");
        register(builder, Kind.TASK, "theseus:stat", "Stat");
        register(builder, Kind.TASK, "theseus:structure", "Structure");
    }

    private static void registerRewards(Builder builder) {
        register(builder, Kind.REWARD, "theseus:xp", "Experience");
        register(builder, Kind.REWARD, "theseus:item", "Item");
        register(builder, Kind.REWARD, "theseus:loottable", "Loot Table");
        register(builder, Kind.REWARD, "theseus:command", "Command");
        register(builder, Kind.REWARD, "theseus:selectable", "Selectable Reward");
    }

    private static void registerIcons(Builder builder) {
        register(builder, Kind.ICON, "theseus:item", "Item icon");
    }

    private static void register(Builder builder, Kind kind, String id, String label) {
        builder.register(Descriptor.builtIn(kind, id, label));
    }
}
