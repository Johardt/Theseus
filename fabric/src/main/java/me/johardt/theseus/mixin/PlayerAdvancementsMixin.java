package me.johardt.theseus.mixin;

import me.johardt.theseus.TheseusFabric;
import me.johardt.theseus.core.TaskEngine;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
    @Shadow private ServerPlayer player;

    // This call only occurs when an advancement transitions to complete.
    @Inject(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V", shift = At.Shift.AFTER))
    private void theseus$advancementGranted(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> callback) {
        TheseusFabric.signal(player, new TaskEngine.Signal.AdvancementGranted(advancement.id().toString()));
    }
}
