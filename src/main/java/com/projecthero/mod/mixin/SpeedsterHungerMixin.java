package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (user: "reduce the speedsters' sprint hunger cost so they don't starve mid-run"): vanilla charges sprinting by
 * the block (0.1 exhaustion a block, in {@code checkMovementStatistics}), so at 167-250 blocks a second a Speed Force
 * Speedster emptied the hunger bar in about 4 s and lost the sprint. For a speedster -- the plain Speedster in Speed Mode /
 * Overdrive, the Speed Force Speedster in Speed Mode -- the movement exhaustion is scaled down to what a normal sprint
 * costs over the same TIME: they still get hungry, at an ordinary runner's rate, however fast they go. Only the exhaustion
 * charged for that movement is touched (jumping, fighting and the rest are untouched).
 */
@Mixin(Player.class)
public abstract class SpeedsterHungerMixin {
	/** Vanilla sprint speed, blocks a tick (5.6 b/s): the rate the hunger cost is pinned to. */
	@Unique
	private static final double PLAIN_SPRINT_PER_TICK = 0.28;

	/** The horizontal distance being charged for right now (inside checkMovementStatistics), or -1. */
	@Unique
	private double projecthero$movedNow = -1.0;

	@Inject(method = "checkMovementStatistics", at = @At("HEAD"))
	private void projecthero$movedStart(double dx, double dy, double dz, CallbackInfo ci) {
		projecthero$movedNow = Math.sqrt(dx * dx + dz * dz);
	}

	@Inject(method = "checkMovementStatistics", at = @At("RETURN"))
	private void projecthero$movedEnd(double dx, double dy, double dz, CallbackInfo ci) {
		projecthero$movedNow = -1.0;
	}

	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float projecthero$speedsterSprintHunger(float exhaustion) {
		double moved = projecthero$movedNow;
		if (exhaustion <= 0f || moved <= PLAIN_SPRINT_PER_TICK || !((Object) this instanceof ServerPlayer sp)) {
			return exhaustion;
		}
		boolean speedster = SpeedForceSpeedster.speedActive(sp)
				|| (SuperSpeedHandlers.owns(sp) && (SuperSpeedHandlers.speedMode(sp) || SuperSpeedHandlers.overdrive(sp)));
		return speedster ? (float) (exhaustion * (PLAIN_SPRINT_PER_TICK / moved)) : exhaustion;
	}
}
