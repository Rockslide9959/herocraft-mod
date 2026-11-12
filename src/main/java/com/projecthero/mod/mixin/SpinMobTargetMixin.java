package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.spin.SpinDisguise;
import com.projecthero.mod.spin.SpinMarks;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 the Spin: two refusals at the one choke point every target goal, retaliation and command goes through.
 * <ul>
 *   <li>A mob whose body is spinning (a Steel Ball hit) can't aim: it takes no target at all.</li>
 *   <li>A Spin in his Face Spin disguise can't be targeted by a mob that hasn't seen him attack ({@link SpinDisguise}).</li>
 * </ul>
 */
@Mixin(Mob.class)
public abstract class SpinMobTargetMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$spinRefusesTarget(LivingEntity target) {
		Mob self = (Mob) (Object) this;
		if (target == null || self.level().isClientSide()) {
			return target;
		}
		if (SpinMarks.spun(self)) {
			return null;
		}
		if (target instanceof Player player && !SpinDisguise.mayTarget(self, player)) {
			return null;
		}
		return target;
	}
}
