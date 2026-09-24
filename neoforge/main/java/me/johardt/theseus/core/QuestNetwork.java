package me.johardt.theseus.core;

import com.google.gson.JsonParser;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class QuestNetwork {

    private QuestNetwork() {}

    public static void register(
        RegisterPayloadHandlersEvent event,
        Supplier<QuestRuntime> runtime
    ) {
        var registrar = event.registrar("1");
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC);
        registrar.playToClient(
            NotificationPayload.TYPE,
            NotificationPayload.STREAM_CODEC
        );
        registrar.playToClient(EditorResultPayload.TYPE, EditorResultPayload.STREAM_CODEC);
        registrar.playToClient(OpenQuestFileResultPayload.TYPE, OpenQuestFileResultPayload.STREAM_CODEC);
        registrar.playToServer(
            EditorMutationPayload.TYPE,
            EditorMutationPayload.STREAM_CODEC,
            (payload, context) -> {
                ServerPlayer player = (ServerPlayer) context.player();
                QuestRuntime.MutationResult result;
                try {
                    result = runtime.get().applyEditorMutationLazy(player, payload::mutation);
                } catch (RuntimeException exception) {
                    result = QuestRuntime.MutationResult.failure(exception.getMessage() == null ? "Invalid quest data" : exception.getMessage());
                }
                PacketDistributor.sendToPlayer(player, new EditorResultPayload(
                    payload.requestId(),
                    result.success(),
                    truncate(result.message(), EditorResultPayload.MAX_MESSAGE_LENGTH),
                    QuestDiagnostics.encode(result.diagnostics(), EditorResultPayload.MAX_DIAGNOSTICS_LENGTH)
                ));
            }
        );
        registrar.playToServer(
            OpenQuestFilePayload.TYPE,
            OpenQuestFilePayload.STREAM_CODEC,
            (payload, context) -> {
                ServerPlayer player = (ServerPlayer) context.player();
                QuestRuntime.QuestFileResult result = runtime.get().openQuestFileResult(player, payload.questId());
                PacketDistributor.sendToPlayer(player, new OpenQuestFileResultPayload(
                    payload.requestId(),
                    result.success(),
                    truncate(result.message(), OpenQuestFileResultPayload.MAX_MESSAGE_LENGTH),
                    truncate(result.relativePath(), OpenQuestFileResultPayload.MAX_PATH_LENGTH)
                ));
            }
        );
        registrar.playToServer(
            ActionPayload.TYPE,
            ActionPayload.STREAM_CODEC,
            (payload, context) -> {
                ServerPlayer player = (ServerPlayer) context.player();
                QuestRuntime questRuntime = runtime.get();
                switch (payload.action()) {
                    case "open" -> questRuntime.sync(player, true);
                    case "load_chapter" -> questRuntime.syncChapter(player, payload.argument());
                    case "claim" -> {
                        if (!payload.argument().startsWith("{")) {
                            questRuntime.claim(player, payload.argument());
                            break;
                        }
                        try {
                            var json = JsonParser.parseString(
                                payload.argument()
                            ).getAsJsonObject();
                            java.util.Map<
                                String,
                                java.util.List<String>
                            > selections = new java.util.HashMap<>();
                            if (
                                json.has("selections") &&
                                json.get("selections").isJsonObject()
                            ) {
                                json.getAsJsonObject("selections")
                                    .entrySet()
                                    .forEach(entry ->
                                        selections.put(
                                            entry.getKey(),
                                            entry
                                                .getValue()
                                                .getAsJsonArray()
                                                .asList()
                                                .stream()
                                                .map(value ->
                                                    value.getAsString()
                                                )
                                                .toList()
                                        )
                                    );
                            }
                            questRuntime.claim(
                                player,
                                json.get("quest").getAsString(),
                                selections
                            );
                        } catch (RuntimeException ignored) {
                            player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal(
                                    "Invalid Theseus reward selection"
                                )
                            );
                        }
                    }
                    case "submit" -> {
                        String[] parts = payload.argument().split("\\|", 2);
                        if (parts.length == 2) questRuntime.submit(
                            player,
                            parts[0],
                            parts[1]
                        );
                    }
                    case "pin" -> questRuntime.togglePinned(
                        player,
                        payload.argument()
                    );
                    default -> {
                    }
                }
            }
        );
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    public record NotificationPayload(
        String kind,
        String title,
        String detail
    ) implements CustomPacketPayload {
        public static final Type<NotificationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("theseus", "notification")
        );
        public static final StreamCodec<
            RegistryFriendlyByteBuf,
            NotificationPayload
        > STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.kind(), 16);
                buffer.writeUtf(payload.title(), 256);
                buffer.writeUtf(payload.detail(), 1024);
            },
            buffer ->
                new NotificationPayload(
                    buffer.readUtf(16),
                    buffer.readUtf(256),
                    buffer.readUtf(1024)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SyncPayload(
        String json,
        boolean open
    ) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("theseus", "sync")
        );
        public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SyncPayload
        > STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.json(), 1_048_576);
                buffer.writeBoolean(payload.open());
            },
            buffer ->
                new SyncPayload(buffer.readUtf(1_048_576), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EditorMutationPayload(
        int requestId,
        QuestMutation.Kind kind,
        String json
    ) implements CustomPacketPayload {
        public static final int MAX_JSON_LENGTH = 1_048_576;
        public static final Type<EditorMutationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("theseus", "editor_mutation")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, EditorMutationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId());
                buffer.writeVarInt(payload.kind().id());
                buffer.writeUtf(payload.json(), MAX_JSON_LENGTH);
            },
            buffer -> new EditorMutationPayload(
                buffer.readVarInt(), QuestMutation.Kind.fromId(buffer.readVarInt()), buffer.readUtf(MAX_JSON_LENGTH)
            )
        );

        public EditorMutationPayload(int requestId, QuestMutation mutation) {
            this(requestId, mutation.kind(), mutation.request().toString());
        }

        public QuestMutation mutation() {
            var duplicateKeys = JsonDuplicateKeyDetector.findDuplicates(json);
            if (!duplicateKeys.isEmpty()) {
                throw new IllegalArgumentException("Duplicate JSON key(s): " + String.join(", ", duplicateKeys));
            }
            var element = JsonParser.parseString(json);
            if (!element.isJsonObject()) throw new IllegalArgumentException("Mutation request must be a JSON object");
            return QuestMutation.of(kind, element.getAsJsonObject());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EditorResultPayload(
        int requestId,
        boolean success,
        String message,
        String diagnostics
    ) implements CustomPacketPayload {
        public static final int MAX_MESSAGE_LENGTH = 32_768;
        public static final int MAX_DIAGNOSTICS_LENGTH = 32_768;
        public static final Type<EditorResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("theseus", "editor_result")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, EditorResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId());
                buffer.writeBoolean(payload.success());
                buffer.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
                buffer.writeUtf(payload.diagnostics(), MAX_DIAGNOSTICS_LENGTH);
            },
            buffer -> new EditorResultPayload(buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(MAX_MESSAGE_LENGTH), buffer.readUtf(MAX_DIAGNOSTICS_LENGTH))
        );

        public EditorResultPayload(int requestId, boolean success, String message) {
            this(requestId, success, message, "[]");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenQuestFilePayload(
        int requestId,
        String questId
    ) implements CustomPacketPayload {
        public static final int MAX_QUEST_ID_LENGTH = 256;
        public static final Type<OpenQuestFilePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("theseus", "open_quest_file")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestFilePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId());
                buffer.writeUtf(payload.questId(), MAX_QUEST_ID_LENGTH);
            },
            buffer -> new OpenQuestFilePayload(buffer.readVarInt(), buffer.readUtf(MAX_QUEST_ID_LENGTH))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenQuestFileResultPayload(
        int requestId,
        boolean success,
        String message,
        String relativePath
    ) implements CustomPacketPayload {
        public static final int MAX_MESSAGE_LENGTH = 1024;
        public static final int MAX_PATH_LENGTH = 1024;
        public static final Type<OpenQuestFileResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("theseus", "open_quest_file_result")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestFileResultPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.requestId());
                buffer.writeBoolean(payload.success());
                buffer.writeUtf(payload.message(), MAX_MESSAGE_LENGTH);
                buffer.writeUtf(payload.relativePath(), MAX_PATH_LENGTH);
            },
            buffer -> new OpenQuestFileResultPayload(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_MESSAGE_LENGTH),
                buffer.readUtf(MAX_PATH_LENGTH)
            )
        );

        public OpenQuestFileResultPayload(int requestId, boolean success, String message) {
            this(requestId, success, message, "");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionPayload(
        String action,
        String argument
    ) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("theseus", "action")
        );
        public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ActionPayload
        > STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.action(), 32);
                buffer.writeUtf(payload.argument(), 8192);
            },
            buffer ->
                new ActionPayload(buffer.readUtf(32), buffer.readUtf(8192))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
