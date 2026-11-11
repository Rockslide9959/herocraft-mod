package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.gyro.GyroDisguise;
import com.projecthero.mod.gyro.GyroMarks;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Gyro Zeppeli: two refusals at the one choke point every target goal, retaliation and command goes through.
 * <ul>
 *   <li>A mob whose body is spinning (a Steel Ball hit) can't aim: it takes no target at all.</li>
 *   <li>A Gyro in his Face Spin disguise can't be targeted by a mob that hasn't seen him attack ({@link GyroDisguise}).</li>
 * </ul>
 */
@Mixin(Mob.class)
public abstract class GyroMobTargetMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity projecthero$gyroRefusesTarget(LivingEntity target) {
		Mob self = (Mob) (Object) this;
		if (target == null || self.level().isClientSide()) {
			return target;
		}
		if (GyroMarks.spun(self)) {
			return null;
		}
		if (target instanceof Player player && !GyroDisguise.mayTarget(self, player)) {
			return null;
		}
		return target;
	}
}
