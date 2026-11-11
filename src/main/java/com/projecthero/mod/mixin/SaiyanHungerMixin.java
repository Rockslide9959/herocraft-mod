package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Saiyan revamp (user: "food saturation lasts 30% longer"): all the food exhaustion a Saiyan takes is divided by 1.3, so
 * his saturation (and then his hunger) lasts 30% longer.
 */
@Mixin(Player.class)
public abstract class SaiyanHungerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float projecthero$saiyanSaturationLonger(float exhaustion) {
		if (exhaustion > 0f && (Object) this instanceof ServerPlayer sp && Saiyan.hasPower(sp)) {
			return exhaustion / SaiyanConfig.SATURATION_STRETCH;
		}
		return exhaustion;
	}
}
