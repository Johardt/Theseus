package me.johardt.theseus.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestMutationTest {
    @Test
    void operationNamesRoundTripThroughTheSealedVocabulary() {
        JsonObject request = new JsonObject();
        request.addProperty("id", "example");

        for (QuestMutation.Kind kind : QuestMutation.Kind.values()) {
            QuestMutation mutation = QuestMutation.of(kind, request);

            assertEquals(kind, mutation.kind());
            assertEquals(kind.operation(), mutation.operation());
            assertEquals(kind, QuestMutation.Kind.fromOperation(mutation.operation()));
            assertEquals(kind, QuestMutation.of(mutation.operation(), request).kind());
        }
    }

    @Test
    void mutationCopiesItsRequest() {
        JsonObject request = new JsonObject();
        request.addProperty("id", "before");

        QuestMutation mutation = new QuestMutation.CreateQuest(request);
        request.addProperty("id", "after");

        assertEquals("before", mutation.request().get("id").getAsString());
    }

    @Test
    void unknownOperationsAreRejectedBeforeDispatch() {
        assertThrows(
            IllegalArgumentException.class,
            () -> QuestMutation.of("create_quest_via_action", new JsonObject())
        );
    }
}
