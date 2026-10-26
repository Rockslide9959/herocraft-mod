package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p04.SpeedsterStepUp;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "they slow down slightly when running up blocks with the step assist ... they should maintain their speed and
 * momentum even if running up blocks"): vanilla's step-up only climbs ONE step a move. At 10+ blocks a tick a speedster's
 * move crosses several steps of a hill at once, so the step-up stopped at the second one, the move came back short, and the
 * short axis's velocity was zeroed ({@code horizontalCollision}) -- and the client then dropped the sprint too. For a
 * speedster running in a mode the rest of the move is climbed step by step instead ({@link SpeedsterStepUp}). Both sides
 * (the owner's client simulates, the server replays the same move).
 */
@Mixin(Entity.class)
public abstract class SpeedsterStepUpMixin {
	@Inject(method = "collide", at = @At("RETURN"), cancellable = true)
	private void projecthero$speedsterStepUp(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
		if ((Object) this instanceof Player player && SpeedsterStepUp.applies(player)) {
			Vec3 better = SpeedsterStepUp.climb(player, movement, cir.getReturnValue());
			if (better != null) {
				cir.setReturnValue(better);
			}
		}
	}
}
