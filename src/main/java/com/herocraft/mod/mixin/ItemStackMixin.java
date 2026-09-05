package com.herocraft.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.herocraft.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Super Speed: you eat/drink as fast as you move. The use duration of a consumable is divided by the
 * same movement factor Super Speed applies (×3 Speed Mode, ×6 Overdrive). Inert otherwise.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
	private void herocraft$speedConsume(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
		if (!(entity instanceof Player player)) {
			return;
		}
		ItemStack self = (ItemStack) (Object) this;
		if (!self.has(DataComponents.FOOD)) {
			return;
		}
		float factor = SuperSpeedHandlers.speedFactor(player);
		if (factor > 1.01f) {
			cir.setReturnValue(Math.max(1, Math.round(cir.getReturnValue() / factor)));
		}
	}
}
