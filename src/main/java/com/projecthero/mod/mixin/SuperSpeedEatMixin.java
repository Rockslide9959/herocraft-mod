package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * Super Speed passive (v0.14.5): eat and drink 50% faster. Every other tick of eating / drinking (food, potions,
 * milk, honey, stews ...) counts twice, so a 32-tick meal takes about 22. The extra step never takes the counter to
 * zero itself -- vanilla's own decrement still finishes the item, so completion stays server-authoritative. Runs on
 * both sides; the owner's client reads the same synced power state, so the eating animation keeps pace.
 */
@Mixin(LivingEntity.class)
public abstract class SuperSpeedEatMixin {
	@Shadow
	protected int useItemRemaining;

	@Inject(method = "updateUsingItem", at = @At("TAIL"))
	private void projecthero$speedEat(ItemStack stack, CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || this.useItemRemaining <= 1 || !player.isUsingItem()) {
			return;
		}
		// v0.17.1 (user): the Speed Force Speedster in Speed Mode eats and drinks THREE times as fast -- two extra steps a
		// tick on top of vanilla's one, never taking the counter to zero itself
		// v0.17.2 (user: "make xlr8 eat food three times as fast"): XLR8 too
		if (com.projecthero.mod.speedforce.SpeedForceSpeedster.speedActive(player)
				|| com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit.is(player)) {
			UseAnim fast = stack.getUseAnimation();
			if (fast == UseAnim.EAT || fast == UseAnim.DRINK) {
				this.useItemRemaining = Math.max(1, this.useItemRemaining - 2);
			}
			return;
		}
		if ((player.tickCount & 1) != 0 || !(SuperSpeedHandlers.owns(player)
				|| com.projecthero.mod.speedforce.SpeedForceSpeedster.hasPower(player))) { // v0.17.1: the Speed Force Speedster too
			return;
		}
		UseAnim anim = stack.getUseAnimation();
		if (anim == UseAnim.EAT || anim == UseAnim.DRINK) {
			this.useItemRemaining--;
		}
	}
}
