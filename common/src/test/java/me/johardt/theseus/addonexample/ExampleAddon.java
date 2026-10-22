package me.johardt.theseus.addonexample;

import com.google.gson.JsonElement;
import me.johardt.theseus.core.QuestDefinition;
import me.johardt.theseus.core.QuestIconTypes;
import me.johardt.theseus.core.QuestRuntime;
import me.johardt.theseus.core.QuestWorld;
import me.johardt.theseus.core.RewardEngine;
import me.johardt.theseus.core.TaskEngine;
import net.minecraft.server.level.ServerPlayer;

/** Small executable addon fixture used by ThirdPartyAddonContractTest. */
public final class ExampleAddon {
    public static final String TASK_TYPE = "exampleaddon:deliver_package";
    public static final String REWARD_TYPE = "exampleaddon:coins";
    public static final String ICON_TYPE = "exampleaddon:badge";

    private static final TaskEngine.Handler TASK_HANDLER = (task, progress, signal) -> {
        if (signal instanceof TaskEngine.Signal.Manual manual
            && task.source().has("trigger")
            && task.source().get("trigger").getAsString().equals(manual.value())) {
            return new TaskEngine.Result(task.target(), 0);
        }
        return new TaskEngine.Result(progress, 0);
    };

    private static final RewardEngine.Handler REWARD_HANDLER = new RewardEngine.Handler() {
        @Override
        public boolean canClaim(QuestDefinition.Reward reward, ServerPlayer player, QuestWorld world) {
            return amount(reward) > 0;
        }

        @Override
        public String grant(QuestDefinition.Reward reward, ServerPlayer player, QuestWorld world) {
            int amount = amount(reward);
            world.message(player, "Granted " + amount + " example coins");
            return amount + " example coins";
        }

        private int amount(QuestDefinition.Reward reward) {
            JsonElement raw = reward.source().get("amount");
            return raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()
                ? raw.getAsInt()
                : 0;
        }
    };

    private ExampleAddon() {}

    /** Called from the addon's common mod initialization, before server startup. */
    public static void registerServerTypes() {
        QuestRuntime.registerTaskHandler(TASK_TYPE, TASK_HANDLER);
        QuestRuntime.registerRewardHandler(REWARD_TYPE, REWARD_HANDLER);
        QuestIconTypes.register(ICON_TYPE);
    }

}
