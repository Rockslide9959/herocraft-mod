package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.ironman.ability.era1.EraOneMk5Stealth;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * v0.16.7 (user) Iron Man Era I Mk V Stealth, at the two mob choke points (the {@code MobMindLockMixin} pattern):
 * <ul>
 * <li>{@code setTarget}: a mob the Sleeper Dart put to sleep / the EMP shut down takes no target, and nothing picks a
 * wearer running Silent Running (until he attacks).</li>
 * <li>{@code serverAiStep}: a sleeping / shut-down mob skips its AI step entirely -- no goals, no pathing, no attacks.</li>
 * </ul>
 */
@Mixin(Mob.class)
public abstract class IronManMk5MobMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$mk5RefusesTarget(LivingEntity target) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide() && EraOneMk5Stealth.refusesTarget(self, target)) {
			LivingEntity current = self.getTarget();
			// a mob keeps a target it already has (one that is not refused) instead of dropping it
			return current != null && current != target && current.isAlive() && !EraOneMk5Stealth.refusesTarget(self, current)
					? current : null;
		}
		return target;
	}

	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void projecthero$mk5FrozenNoAi(CallbackInfo ci) {
		if (EraOneMk5Stealth.frozen((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
