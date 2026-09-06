package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.punisher.Punisher;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * Arrows loosed by the Punisher hit twice as hard (spec / user request). Applied to the arrow's base
 * damage the moment it is launched, so it flows through vanilla's normal
 * {@code onHitEntity} damage maths (which reads the base-damage field). Guarded on the owner being a
 * Punisher player, so a bow the Punisher lends out fires normal arrows.
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
	@Inject(method = "shoot(DDDFF)V", at = @At("TAIL"))
	private void projecthero$punisherArrowDamage(double x, double y, double z, float velocity, float inaccuracy,
			CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (self.getOwner() instanceof Player player && Punisher.hasPower(player)) {
			self.setBaseDamage(self.getBaseDamage() * 2.0);
		}
	}
}
