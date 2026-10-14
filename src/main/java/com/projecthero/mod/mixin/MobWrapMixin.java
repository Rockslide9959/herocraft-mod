package com.projecthero.mod.mixin;

import com.projecthero.mod.spider.SpiderWrap;

import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.1: a mob cocooned by Spider-Man's Web Shot ({@link SpiderWrap}) thinks nothing at all -- its whole server AI
 * step is skipped: no goals (so no melee, no ranged attack, no creeper swell goal), no brain behaviours (wardens,
 * piglins, villagers...), no navigation, move, look or jump control, no boss special attacks.
 */
@Mixin(Mob.class)
public abstract class MobWrapMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void projecthero$cocoonedThinksNothing(CallbackInfo ci) {
		if (SpiderWrap.isWrapped((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
