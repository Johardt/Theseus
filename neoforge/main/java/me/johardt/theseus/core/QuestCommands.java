package me.johardt.theseus.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class QuestCommands {
    private QuestCommands() {}

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        Supplier<QuestRuntime> runtime
    ) {
        dispatcher.register(Commands.literal("theseus")
            .executes(context -> status(context.getSource(), runtime.get()))
            .then(PartyRewardCommands.create(runtime))
            .then(Commands.literal("open").executes(context -> open(context.getSource(), runtime.get())))
            .then(Commands.literal("dummy")
                .then(Commands.argument("value", StringArgumentType.string())
                    .executes(context -> dummy(context.getSource(), StringArgumentType.getString(context, "value"), runtime.get()))))
            .then(Commands.literal("demo")
                .executes(context -> dummy(context.getSource(), "demo_welcome", runtime.get())))
            .then(Commands.literal("claim")
                .then(Commands.argument("quest", StringArgumentType.string())
                    .executes(context -> claim(context.getSource(), StringArgumentType.getString(context, "quest"), runtime.get()))))
            .then(Commands.literal("submit")
                .then(Commands.argument("quest", StringArgumentType.string())
                    .then(Commands.argument("task", StringArgumentType.string())
                        .executes(context -> submit(context.getSource(), StringArgumentType.getString(context, "quest"), StringArgumentType.getString(context, "task"), runtime.get())))))
            .then(Commands.literal("reset")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> reset(context.getSource(), runtime.get())))
            .then(Commands.literal("reload")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> reload(context.getSource(), runtime.get())))
            .then(Commands.literal("validate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> validate(context.getSource(), runtime.get())))
        );
    }

    private static int status(CommandSourceStack source, QuestRuntime runtime) {
        source.sendSuccess(() -> Component.literal("Theseus quests are active. Party rewards: "
            + (runtime.parties.available() ? "OPAC available" : "personal completions and saved eligibility")
            + ". Use /theseus open or press H."), false);
        return 1;
    }

    private static int open(CommandSourceStack source, QuestRuntime runtime) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        runtime.sync(source.getPlayerOrException(), true);
        return 1;
    }

    private static int dummy(CommandSourceStack source, String value, QuestRuntime runtime) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean changed = runtime.triggerDummy(player, value);
        String locked = changed ? null : runtime.lockedDummyReason(player, value);
        source.sendSuccess(() -> Component.literal(changed ? "Dummy quest task completed."
            : locked != null ? locked : "No dummy task matched '" + value + "'."), false);
        return changed ? 1 : 0;
    }

    private static int claim(CommandSourceStack source, String quest, QuestRuntime runtime) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean claimed = runtime.claim(source.getPlayerOrException(), quest);
        source.sendSuccess(() -> Component.literal(claimed ? "Your quest rewards were claimed." : "Rewards are unavailable, already claimed, or need a choice or operator review."), false);
        return claimed ? 1 : 0;
    }

    private static int submit(CommandSourceStack source, String quest, String task, QuestRuntime runtime) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean submitted = runtime.submit(source.getPlayerOrException(), quest, task);
        source.sendSuccess(() -> Component.literal(submitted ? "Task submission accepted." : "Task could not be submitted."), false);
        return submitted ? 1 : 0;
    }

    private static int reset(CommandSourceStack source, QuestRuntime runtime) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        runtime.reset(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("Your Theseus tasks were reset. Earned eligibility and reward receipts are preserved."), false);
        return 1;
    }

    private static int reload(CommandSourceStack source, QuestRuntime runtime) {
        int count = runtime.reload();
        int issues = runtime.validationIssues().size();
        source.sendSuccess(() -> Component.literal("Reloaded " + count + " Theseus quests with " + issues + " validation issue(s)."), true);
        return count;
    }

    private static int validate(CommandSourceStack source, QuestRuntime runtime) {
        var issues = runtime.validationIssues();
        if (issues.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Theseus quest validation issues found."), false);
            return 1;
        }
        issues.forEach(issue -> source.sendFailure(Component.literal(
            issue.severity() + " " + issue.path() + ": " + issue.message())));
        return 0;
    }
}
