package me.johardt.theseus.mixin;

import me.johardt.theseus.TheseusFabric;
import me.johardt.theseus.core.QuestRuntime;
import me.johardt.theseus.core.TaskEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Shadow protected ItemStack useItem;

    @Inject(method = "completeUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private void theseus$itemUsed(CallbackInfo callback) {
        if ((Object) this instanceof ServerPlayer player) {
            TheseusFabric.signal(player, new TaskEngine.Signal.ItemUsed(QuestRuntime.itemEntry(player, useItem)));
        }
    }
}
