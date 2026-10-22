package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestMutationCoordinatorTest {
    @Test
    void onlyMatchingAcknowledgementSettlesPendingMutation() {
        QuestMutationCoordinator coordinator = new QuestMutationCoordinator();
        JsonObject request = new JsonObject();
        request.addProperty("id", "quest");

        var pending = coordinator.begin("save", request);
        assertTrue(coordinator.isPending());
        assertNull(coordinator.complete(pending.requestId() + 1, true, "wrong request"));
        assertTrue(coordinator.isPending());

        var completion = coordinator.complete(pending.requestId(), true, "saved");
        assertEquals("save", completion.pending().operation());
        assertEquals("saved", completion.message());
        assertTrue(completion.success());
        assertTrue(!coordinator.isPending());
    }

    @Test
    void resetAcknowledgementSurvivesSyncDrivenCoordinatorReconstruction() {
        QuestMutationCoordinator coordinator = new QuestMutationCoordinator();
        var request = new JsonObject();
        request.addProperty("scope", "task");
        request.addProperty("quest", "quest");
        request.addProperty("entry", "outer/leaf");
        var pending = coordinator.begin("reset_progress", request);

        QuestMutationCoordinator reconstructed = coordinator.copy();
        var completion = reconstructed.complete(pending.requestId(), true, "Reset task progress");

        assertEquals("reset_progress", completion.pending().operation());
        assertTrue(completion.success());
        assertTrue(!reconstructed.isPending());
    }

    @ParameterizedTest(name = "disconnect timing: {0}")
    @EnumSource(DisconnectTiming.class)
    void connectionLossDetachesUnknownRequestAndRejectsItsLateAcknowledgement(DisconnectTiming timing) {
        QuestMutationCoordinator coordinator = new QuestMutationCoordinator();
        JsonObject request = new JsonObject();
        request.addProperty("id", "unsaved_quest");
        var pending = coordinator.begin("create_quest", request);

        var interrupted = coordinator.connectionLost();
        QuestMutationCoordinator reconnected = coordinator.copy();

        assertEquals(pending, interrupted);
        assertFalse(reconnected.isPending());
        assertEquals("unsaved_quest", interrupted.request().get("id").getAsString());
        assertNull(reconnected.complete(pending.requestId(), true, "stale acknowledgement"));

        var later = reconnected.begin("create_quest", request);
        assertNotEquals(pending.requestId(), later.requestId());
        assertNull(reconnected.complete(pending.requestId(), true, "still stale"));
        assertTrue(reconnected.isPending());
    }

    private enum DisconnectTiming {
        BEFORE_SERVER_COMMIT,
        AFTER_SERVER_COMMIT_BEFORE_ACKNOWLEDGEMENT
    }
}
