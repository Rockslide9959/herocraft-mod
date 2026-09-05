package com.herocraft.mod.hero.power;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.Ability;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.power.ThorPowers;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A fixed-duration personal flight granted by a non-flight ability (Geokinesis rock flight,
 * Pyrokinesis flame flight). Independent of {@link HeroFlight} and Thor flight: it stores its own
 * countdown in an {@link ExperimentalPowers} resource ({@code <tag>_fly_ticks}) plus a 0..100 HUD
 * meter ({@code <tag>flight}), reconciles vanilla's {@code mayfly}/{@code flying} flags every tick
 * from that countdown, and only starts the owning ability's cooldown once the timer runs out.
 */
public final class TimedSelfFlight {
	public static final int DURATION_TICKS = 20 * 20;

	private TimedSelfFlight() {
	}

	private static String ticksKey(String tag) {
		return tag + "_fly_ticks";
	}

	private static String meterKey(String tag) {
		return tag + "flight";
	}

	public static boolean isActive(ServerPlayer p, Power power, String tag) {
		return ExperimentalPowers.getResource(p, power, ticksKey(tag)) > 0.5f;
	}

	/** True if any timed self-flight (rock or flame) is currently running for any power the player owns. */
	public static boolean anyActive(ServerPlayer p) {
		for (String key : ExperimentalPowers.state(p).ownedPowers) {
			Power power = com.herocraft.mod.hero.Powers.byKey(key);
			if (power != null
					&& (ExperimentalPowers.getResource(p, power, ticksKey("rock")) > 0.5f
							|| ExperimentalPowers.getResource(p, power, ticksKey("flame")) > 0.5f)) {
				return true;
			}
		}
		return false;
	}

	/** @return false if it could not start (already flying, creative, Thor flight, on cooldown). */
	public static boolean start(ServerPlayer p, Power power, Ability ability, String tag) {
		if (isActive(p, power, tag) || p.getAbilities().instabuild
				|| ThorPowers.isFlying(p) || HeroFlight.isFlying(p)
				|| !ExperimentalPowers.cooldownReady(p, power, ability)) {
			return false;
		}
		ExperimentalPowers.setResource(p, power, ticksKey(tag), DURATION_TICKS, DURATION_TICKS);
		ExperimentalPowers.setResource(p, power, meterKey(tag), 100.0f, 100.0f);
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
		// drives the Thor-style flight pose (arms at sides -- see FlightPoseHelper)
		p.setAttached(ModAttachments.HERO_FLYING, true);
		return true;
	}

	/** Call every server tick from the owning power's {@code PowerPassives.registerTick}. */
	public static void tick(ServerPlayer p, Power power, Ability ability, String tag, ParticleOptions feet) {
		float remaining = ExperimentalPowers.getResource(p, power, ticksKey(tag));
		if (remaining <= 0.5f) {
			return;
		}
		if (p.getAbilities().instabuild || ThorPowers.isFlying(p)) {
			stop(p, power, ability, tag, false);
			return;
		}
		remaining -= 1.0f;
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.resetFallDistance();
		ExperimentalPowers.setResource(p, power, ticksKey(tag), remaining, DURATION_TICKS);
		ExperimentalPowers.setResource(p, power, meterKey(tag), 100.0f * remaining / DURATION_TICKS, 100.0f);
		if (p.level() instanceof ServerLevel sl && p.tickCount % 3 == 0) {
			sl.sendParticles(feet, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.35, 0.1, 0.35, 0.02);
		}
		if (remaining <= 0.5f) {
			stop(p, power, ability, tag, true);
		}
	}

	public static void stop(ServerPlayer p, Power power, Ability ability, String tag, boolean startCooldown) {
		ExperimentalPowers.setResource(p, power, ticksKey(tag), 0.0f, DURATION_TICKS);
		ExperimentalPowers.setResource(p, power, meterKey(tag), 0.0f, 100.0f);
		if (!anyActive(p)) {
			p.setAttached(ModAttachments.HERO_FLYING, false);
		}
		if (!p.getAbilities().instabuild) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			p.resetFallDistance();
		}
		if (startCooldown) {
			ExperimentalPowers.triggerCooldown(p, power, ability,
					com.herocraft.mod.hero.HeroConfig.get().scaledCooldown(ability.cooldownTicks()));
		}
	}
}
