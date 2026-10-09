package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.hero.power.p10.TelekinesisHandlers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * v0.14.3: a Mind-Locked mob (Telekinesis N) cannot pick a target at all. The lock used to clear the target from the
 * mod's own tick, but the mob's AI goals run in the entity tick and could pick one straight back up in between -- so
 * whether a locked mob "had a target" depended on tick order (a real race the CI gametest kept tripping over). Refusing
 * the target at the one choke point closes it.
 */
@Mixin(Mob.class)
public abstract class MobMindLockMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$mindLockedHasNoTarget(LivingEntity target) {
		Mob self = (Mob) (Object) this;
		// v0.15.18: so can't a mob the Punisher stunned, flashbanged or hid from in his smoke
		if (target != null && !self.level().isClientSide()
				&& (TelekinesisHandlers.isLocked(self) || com.projecthero.mod.punisher.PunisherControl.refusesTarget(self))) {
			return null;
		}
		return target;
	}
}
