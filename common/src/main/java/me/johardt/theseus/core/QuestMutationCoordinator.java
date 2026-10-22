package me.johardt.theseus.core;

import com.google.gson.JsonObject;

/**
 * Tracks one acknowledged editor mutation.  The screen owns presentation, but
 * this module owns request identity and the invariant that only the matching
 * result can settle a pending mutation.
 */
public final class QuestMutationCoordinator {
    private int nextRequestId;
    private Pending pending;

    public QuestMutationCoordinator() {}

    private QuestMutationCoordinator(int nextRequestId, Pending pending) {
        this.nextRequestId = nextRequestId;
        this.pending = pending;
    }

    public Pending begin(QuestMutation mutation) {
        if (mutation == null) throw new IllegalArgumentException("Editor mutation is required");
        return begin(mutation.operation(), mutation.request());
    }

    public Pending begin(String operation, JsonObject request) {
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("Mutation operation is required");
        if (pending != null) throw new IllegalStateException("An editor mutation is already pending");
        Pending created = new Pending(++nextRequestId, operation, request == null ? new JsonObject() : request.deepCopy());
        pending = created;
        return created;
    }

    public boolean isPending() {
        return pending != null;
    }

    public Pending pending() {
        return pending;
    }

    public boolean accepts(int requestId) {
        return pending != null && pending.requestId() == requestId;
    }

    public Completion complete(int requestId, boolean success, String message) {
        if (!accepts(requestId)) return null;
        Pending completed = pending;
        pending = null;
        return new Completion(completed, success, message == null ? "" : message);
    }

    public void cancel() {
        pending = null;
    }

    /**
     * Detaches a request whose server outcome became unknowable when the
     * connection closed.  The caller may retain the returned request for
     * presentation, but it must not be replayed automatically.
     */
    public Pending connectionLost() {
        Pending interrupted = pending;
        pending = null;
        return interrupted;
    }

    public QuestMutationCoordinator copy() {
        return new QuestMutationCoordinator(nextRequestId, pending == null ? null : pending.copy());
    }

    public record Pending(int requestId, String operation, JsonObject request) {
        public Pending {
            request = request == null ? new JsonObject() : request.deepCopy();
        }

        private Pending copy() {
            return new Pending(requestId, operation, request);
        }
    }

    public record Completion(Pending pending, boolean success, String message) {}
}
