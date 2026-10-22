package me.johardt.theseus.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/** Server-side extension registry for non-built-in quest rewards. */
public final class RewardEngine {
    private static final Set<String> BUILT_IN_TYPES = Set.of(
        "theseus:xp", "theseus:item", "theseus:loottable", "theseus:command", "theseus:selectable"
    );
    private final Map<String, Handler> handlers;

    private RewardEngine(Map<String, Handler> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<String> types() {
        return Set.copyOf(handlers.keySet());
    }

    public boolean canClaim(
        QuestDefinition.Reward reward,
        ServerPlayer player,
        QuestWorld world
    ) {
        Handler handler = handlers.get(reward.type());
        return handler != null && handler.canClaim(reward, player, world);
    }

    /** Returns the notification detail supplied by the extension, or an empty string. */
    public String grant(
        QuestDefinition.Reward reward,
        ServerPlayer player,
        QuestWorld world
    ) {
        Handler handler = handlers.get(reward.type());
        if (handler == null) {
            throw new IllegalStateException("No reward handler is registered for " + reward.type());
        }
        String detail = handler.grant(reward, player, world);
        return detail == null ? "" : detail;
    }

    public interface Handler {
        boolean canClaim(QuestDefinition.Reward reward, ServerPlayer player, QuestWorld world);
        String grant(QuestDefinition.Reward reward, ServerPlayer player, QuestWorld world);
    }

    public static final class Builder {
        private final Map<String, Handler> handlers = new LinkedHashMap<>();

        public Builder register(String type, Handler handler) {
            if (type == null || !type.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                throw new IllegalArgumentException("Reward type must be a namespaced identifier: " + type);
            }
            if (BUILT_IN_TYPES.contains(type)) {
                throw new IllegalArgumentException("Built-in reward type cannot be replaced: " + type);
            }
            if (handlers.putIfAbsent(type, java.util.Objects.requireNonNull(handler, "handler")) != null) {
                throw new IllegalArgumentException("Reward handler already registered: " + type);
            }
            return this;
        }

        public RewardEngine build() {
            return new RewardEngine(handlers);
        }
    }
}
