package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Automatic (v0.11.10, explicit user request): no key press, no construct-wheel slot. Whenever a
 * bonded Green Lantern is underwater and their own air supply drops below half, the ring builds a
 * small hard-light rebreather on their own body -- a "tank" of particles on the upper back, a "mask"
 * just below the chin, and connecting "pipes" down both sides, all re-derived from the wearer's live
 * position/yaw every tick ({@link #emitParticles}) so it tracks them perfectly as they swim rather than
 * drifting loose or clipping through their model.
 *
 * <p>While active it keeps the wearer's vanilla air meter topped up every tick (a genuine rebreather,
 * not just a light show) at {@link GreenLanternConfig#AIR_TANK_UPKEEP_PER_SEC} Ring Charge/sec, checked
 * once/sec like every other upkeep in this power. It turns off the instant the wearer resurfaces or the
 * ring can no longer afford the drain -- {@link #clearFor} covers death/respawn/logout/dimension-change/
 * power-loss the same way {@link GreenLanternOath#clearFor} does for the Oath.
 *
 * <p>Per explicit user request: tell the player when the tank switches on, but never announce it
 * switching back off -- {@link #start} is the only place that sends a message.
 */
public final class GreenLanternAirTank {
	private GreenLanternAirTank() {
	}

	public static boolean isActive(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_AIR_TANK_ACTIVE, false);
	}

	/** Per-player server tick, called from {@code GreenLanternAbilityManager#serverTick}. */
	public static void tick(ServerPlayer player) {
		boolean active = isActive(player);
		if (!active) {
			if (player.isUnderWater() && player.getAirSupply() <= player.getMaxAirSupply() / 2) {
				start(player);
				active = true;
			} else {
				return;
			}
		} else if (!player.isUnderWater()) {
			stop(player);
			return;
		}
		if (player.tickCount % 20 == 0
				&& !GreenLanternEnergy.drainTick(player, GreenLanternConfig.AIR_TANK_UPKEEP_PER_SEC)) {
			stop(player);
			return;
		}
		player.setAirSupply(player.getMaxAirSupply());
		emitParticles(player);
	}

	private static void start(ServerPlayer player) {
		player.setAttached(ModAttachments.GREEN_LANTERN_AIR_TANK_ACTIVE, true);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.air_tank.activated"), true);
	}

	private static void stop(ServerPlayer player) {
		if (isActive(player)) {
			player.setAttached(ModAttachments.GREEN_LANTERN_AIR_TANK_ACTIVE, false);
		}
	}

	/**
	 * A tank on the upper back, a mask ring just under the chin, and a pipe down each side connecting
	 * them -- entirely derived from the player's own live position/yaw so it never drifts loose or looks
	 * detached as they swim.
	 */
	private static void emitParticles(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		double yawRad = Math.toRadians(player.getYRot());
		Vec3 back = new Vec3(Math.sin(yawRad), 0, -Math.cos(yawRad));
		Vec3 side = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
		double h = player.getBbHeight();
		Vec3 origin = player.position();

		Vec3 tank = origin.add(back.scale(0.32)).add(0, h * 0.62, 0);
		level.sendParticles(ParticleTypes.BUBBLE, tank.x, tank.y, tank.z, 2, 0.08, 0.1, 0.08, 0.01);

		Vec3 mask = origin.add(back.scale(-0.22)).add(0, h * 0.84, 0);
		level.sendParticles(ParticleTypes.BUBBLE_POP, mask.x, mask.y, mask.z, 1, 0.05, 0.03, 0.05, 0.0);

		for (int sgn = -1; sgn <= 1; sgn += 2) {
			Vec3 pipeStart = mask.add(side.scale(sgn * 0.22));
			Vec3 pipeEnd = tank.add(side.scale(sgn * 0.22));
			AbilityHelpers.line(level, pipeStart, pipeEnd, ParticleTypes.BUBBLE_COLUMN_UP, 4.0);
		}
	}

	/** Lifecycle cleanup -- death/respawn/logout/dimension-change/power-loss must not leave this running. */
	public static void clearFor(ServerPlayer player) {
		player.setAttached(ModAttachments.GREEN_LANTERN_AIR_TANK_ACTIVE, false);
	}
}
