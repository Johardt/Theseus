package me.johardt.theseus.core;

import java.util.LinkedHashSet;
import java.util.Set;

/** Server-safe registry of icon configuration types advertised to clients. */
public final class QuestIconTypes {
    private static final Set<String> TYPES = new LinkedHashSet<>(Set.of(QuestIconDefinition.ITEM_TYPE));

    private QuestIconTypes() {}

    public static synchronized void register(String type) {
        if (type == null || !type.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Invalid icon type: " + type);
        }
        if (!TYPES.add(type)) throw new IllegalArgumentException("Icon type already registered: " + type);
    }

    public static synchronized Set<String> types() {
        return Set.copyOf(TYPES);
    }

    public static synchronized boolean contains(String type) {
        return TYPES.contains(type);
    }
}
