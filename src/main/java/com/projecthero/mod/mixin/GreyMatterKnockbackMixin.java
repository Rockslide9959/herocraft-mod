package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;

import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2 Grey Matter revamp, weakness Swatted (being flicked away is the show's running joke): he takes x1.5 knockback
 * ({@link GreyMatterKit#knockbackScale}; the doc's 2x was trimmed to 1.5x for a 20-HP alien).
 */
@Mixin(LivingEntity.class)
public abstract class GreyMatterKnockbackMixin {
	@ModifyVariable(method = "knockback", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double projecthero$greyMatterSwatted(double strength) {
		return strength * GreyMatterKit.knockbackScale((LivingEntity) (Object) this);
	}
}
