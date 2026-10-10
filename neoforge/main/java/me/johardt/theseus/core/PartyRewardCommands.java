package me.johardt.theseus.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;

/** Explicit operator recovery; never runs reward side effects itself. */
final class PartyRewardCommands {
    private PartyRewardCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> create(Supplier<QuestRuntime> runtime) {
        var root = Commands.literal("rewards").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (String operation : new String[] {"inspect", "repair", "reset", "acknowledge"}) {
            var quest = Commands.argument("quest", StringArgumentType.string());
            if (operation.equals("inspect") || operation.equals("repair")) {
                quest.executes(context -> run(context, runtime.get(), operation, ""));
            } else {
                quest.then(Commands.argument("reward", StringArgumentType.string())
                    .executes(context -> run(context, runtime.get(), operation, StringArgumentType.getString(context, "reward"))));
            }
            root.then(Commands.literal(operation).then(Commands.argument("player", UuidArgument.uuid()).then(quest)));
        }
        return root;
    }

    private static int run(CommandContext<CommandSourceStack> context, QuestRuntime runtime, String operation, String reward) {
        UUID player = UuidArgument.getUuid(context, "player");
        String id = StringArgumentType.getString(context, "quest");
        QuestDefinition quest = runtime.catalog.quests().get(id);
        if (quest == null) {
            context.getSource().sendFailure(Component.literal("Unknown quest '" + id + "'"));
            return 0;
        }
        QuestProgressState state = runtime.progress(player, id);
        if (operation.equals("inspect")) {
            context.getSource().sendSuccess(() -> Component.literal("Player " + player + ", quest " + id
                + ": shared source=" + state.partyRewardSource() + ", prerequisites met=" + runtime.prerequisitesMet(player, quest)
                + ", completion recorded=" + state.completionRecorded()
                + ", claimed=" + state.claimedRewards() + ", interrupted=" + state.pendingRewards()), false);
            return 1;
        }
        if (operation.equals("repair")) {
            UUID operator = context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer actor
                ? runtime.world.playerId(actor) : new UUID(0, 0);
            state.earnPartyRewards(new QuestProgressState.PartyRewardSource(new UUID(0, 0), "Operator repair", operator));
            completeTasks(state, quest.tasks(), "");
        } else {
            if (!quest.rewards().containsKey(reward)) {
                context.getSource().sendFailure(Component.literal("Unknown reward '" + reward + "'"));
                return 0;
            }
            if (operation.equals("acknowledge")) {
                state.markRewardClaimed(reward);
                state.finishRewardGrant(reward);
            } else state.unmarkRewardClaimed(reward);
        }
        if (!runtime.saveProgress()) {
            context.getSource().sendFailure(Component.literal("Could not persist reward recovery; repair the progress store before continuing."));
            return 0;
        }
        runtime.world.onlinePlayers().stream().filter(online -> runtime.world.playerId(online).equals(player))
            .forEach(online -> runtime.sync(online, false));
        context.getSource().sendSuccess(() -> Component.literal(operation.equals("repair")
            ? "Quest tasks marked complete; prerequisites still gate rewards and existing claims are preserved."
            : operation.equals("acknowledge") ? "Reward recorded as delivered without granting it again."
            : "Reward receipt reset. This player can receive this reward again if eligible."), true);
        return 1;
    }

    private static void completeTasks(QuestProgressState state, java.util.Map<String, QuestDefinition.Task> tasks, String prefix) {
        tasks.forEach((id, task) -> {
            state.setTaskProgress(prefix + id, task.target());
            completeTasks(state, task.tasks(), prefix + id + "/");
        });
    }
}
