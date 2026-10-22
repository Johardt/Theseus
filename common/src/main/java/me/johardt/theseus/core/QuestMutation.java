package me.johardt.theseus.core;

import com.google.gson.JsonObject;

/**
 * The complete vocabulary of acknowledged quest-authoring mutations.
 *
 * Each mutation carries only its request body.  The variant is the operation
 * identity, so callers cannot accidentally send an authoring operation through
 * the gameplay action channel.
 */
public sealed interface QuestMutation
    permits QuestMutation.CreateQuest,
        QuestMutation.UpdateQuest,
        QuestMutation.ImportQuests,
        QuestMutation.PasteQuest,
        QuestMutation.DeleteQuest,
        QuestMutation.ChapterAction,
        QuestMutation.SetDependency,
        QuestMutation.RemoveQuestGroup,
        QuestMutation.ResetProgress {

    JsonObject request();

    Kind kind();

    default String operation() {
        return kind().operation();
    }

    static QuestMutation of(Kind kind, JsonObject request) {
        return switch (kind) {
            case CREATE_QUEST -> new CreateQuest(request);
            case UPDATE_QUEST -> new UpdateQuest(request);
            case IMPORT_QUESTS -> new ImportQuests(request);
            case PASTE_QUEST -> new PasteQuest(request);
            case DELETE_QUEST -> new DeleteQuest(request);
            case CHAPTER_ACTION -> new ChapterAction(request);
            case SET_DEPENDENCY -> new SetDependency(request);
            case REMOVE_QUEST_GROUP -> new RemoveQuestGroup(request);
            case RESET_PROGRESS -> new ResetProgress(request);
        };
    }

    static QuestMutation of(String operation, JsonObject request) {
        return of(Kind.fromOperation(operation), request);
    }

    private static JsonObject copy(JsonObject request) {
        return request == null ? new JsonObject() : request.deepCopy();
    }

    enum Kind {
        CREATE_QUEST("create_quest"),
        UPDATE_QUEST("update_quest"),
        IMPORT_QUESTS("import_quests"),
        PASTE_QUEST("paste_quest"),
        DELETE_QUEST("delete_quest"),
        CHAPTER_ACTION("chapter_action"),
        SET_DEPENDENCY("set_dependency"),
        REMOVE_QUEST_GROUP("remove_quest_group"),
        RESET_PROGRESS("reset_progress");

        private final String operation;

        Kind(String operation) {
            this.operation = operation;
        }

        public String operation() {
            return operation;
        }

        public static Kind fromOperation(String operation) {
            for (Kind kind : values()) {
                if (kind.operation.equals(operation)) return kind;
            }
            throw new IllegalArgumentException("Unknown editor mutation: " + operation);
        }

        public static Kind fromId(int id) {
            Kind[] kinds = values();
            if (id < 0 || id >= kinds.length) {
                throw new IllegalArgumentException("Unknown editor mutation id: " + id);
            }
            return kinds[id];
        }

        public int id() {
            return ordinal();
        }
    }

    record CreateQuest(JsonObject request) implements QuestMutation {
        public CreateQuest {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.CREATE_QUEST;
        }
    }

    record UpdateQuest(JsonObject request) implements QuestMutation {
        public UpdateQuest {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.UPDATE_QUEST;
        }
    }

    record ImportQuests(JsonObject request) implements QuestMutation {
        public ImportQuests {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.IMPORT_QUESTS;
        }
    }

    record PasteQuest(JsonObject request) implements QuestMutation {
        public PasteQuest {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.PASTE_QUEST;
        }
    }

    record DeleteQuest(JsonObject request) implements QuestMutation {
        public DeleteQuest {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.DELETE_QUEST;
        }
    }

    record ChapterAction(JsonObject request) implements QuestMutation {
        public ChapterAction {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.CHAPTER_ACTION;
        }
    }

    record SetDependency(JsonObject request) implements QuestMutation {
        public SetDependency {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.SET_DEPENDENCY;
        }
    }

    record RemoveQuestGroup(JsonObject request) implements QuestMutation {
        public RemoveQuestGroup {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.REMOVE_QUEST_GROUP;
        }
    }

    record ResetProgress(JsonObject request) implements QuestMutation {
        public ResetProgress {
            request = QuestMutation.copy(request);
        }

        @Override
        public Kind kind() {
            return Kind.RESET_PROGRESS;
        }
    }
}
