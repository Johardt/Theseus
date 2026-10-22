package me.johardt.theseus.mixin;

import me.johardt.theseus.TheseusFabric;
import me.johardt.theseus.core.TaskEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "awardStat", at = @At("TAIL"))
    private void theseus$statistic(Stat<?> stat, int amount, CallbackInfo callback) {
        if (stat.getType() == Stats.CUSTOM && stat.getValue() instanceof Identifier id) {
            TheseusFabric.signal((ServerPlayer) (Object) this, new TaskEngine.Signal.Statistic(id.toString(), amount));
        }
    }
}
