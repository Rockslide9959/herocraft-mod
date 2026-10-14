package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.2: Spider-Man is hard to notice -- every mob's target search multiplies its detection range by
 * {@code getVisibilityPercent}; {@link com.projecthero.mod.spider.SpiderStealth#factor} scales it for him (walking,
 * sneaking, crawling / perched / hanging, the dark; bosses, the Warden and point-blank range ignore it).
 */
@Mixin(LivingEntity.class)
public abstract class SpiderStealthVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void projecthero$spiderStealth(Entity looker, CallbackInfoReturnable<Double> cir) {
		if ((Object) this instanceof Player player) {
			double factor = com.projecthero.mod.spider.SpiderStealth.factor(player, looker);
			if (factor < 1.0) {
				cir.setReturnValue(cir.getReturnValue() * factor);
			}
		}
	}
}
