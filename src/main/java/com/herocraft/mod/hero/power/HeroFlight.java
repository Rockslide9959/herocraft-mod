package com.herocraft.mod.hero.power;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.Ability;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.Power;
import com.herocraft.mod.power.ThorPowers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Shared experimental flight, used by the Flight power and by Wind/Psychic/Magnetic flight toggles.
 * Completely independent of Thor's flight ({@code ThorPowers}): its own attachment
 * ({@link ModAttachments#HERO_FLYING}), its own stamina resource, and it refuses to engage while
 * Thor flight is active so the two never fight over {@code abilities.flying}.
 *
 * <p>Stamina is stored as an {@link ExperimentalPowers} resource named {@code "flight"} (0..100) on
 * whichever power owns the flight; it drains while airborne and regenerates on the ground. When it
 * hits zero, flight cuts out — cleanly, with fall distance reset so there is no sudden fall-damage
 * spike (spec section 18).
 */
public final class HeroFlight {
	public static final String STAMINA = "flight";
	public static final float MAX_STAMINA = 100.0f;
	/**
	 * v0.9.2: the shared "hero flight" horizontal speed. The vanilla creative default is {@code 0.05};
	 * this is a touch quicker so hero flight feels distinct from a creative-mode drift. Vanilla doubles
	 * it (to ~{@code 0.12}) while the player is sprinting. Thor flight ({@code ThorPowers}) and Turbo
	 * Flight ({@code MaxSteelFlight}) apply the same value so all three fly at one speed.
	 */
	public static final float HERO_FLYING_SPEED = 0.06f;
	/** Vanilla creative-flight default -- restored whenever a hero flight ends. */
	public static final float VANILLA_FLYING_SPEED = 0.05f;
	private static final float DRAIN_PER_TICK = 100.0f / (25 * 20); // ~25 s of flight from full
	private static final float REGEN_PER_TICK = 100.0f / (12 * 20);

	private HeroFlight() {
	}

	/**
	 * True only if this power actually has a flight ability in one of its six slots (Flight,
	 * Wind, Telekinesis, Magnetism). Powers without one never touch the {@code "flight"} stamina
	 * resource, so the HUD never draws a flight bar for them.
	 */
	public static boolean hasFlight(Power power) {
		if (power == null) {
			return false;
		}
		for (Ability ability : power.abilities()) {
			if (ability.id().contains("flight")) {
				return true;
			}
		}
		return false;
	}

	public static boolean isFlying(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.HERO_FLYING, false);
	}

	/**
	 * These powers do not use the shared flight-stamina meter: the Flight power flies indefinitely,
	 * and Telekinesis flight is gated by its own Telekinetic Energy (psi) bar instead. No HUD flight bar
	 * for either.
	 */
	public static boolean infinite(Power power) {
		return power != null
				&& ("power_03_flight".equals(power.key()) || "power_10_telekinesis".equals(power.key()));
	}

	public static boolean toggle(ServerPlayer player, Power power) {
		if (isFlying(player)) {
			setFlying(player, false);
			return false;
		}
		return startFlying(player, power);
	}

	/** Turn hero flight on. Returns false (and does nothing) if it cannot engage right now. */
	public static boolean startFlying(ServerPlayer player, Power power) {
		if (isFlying(player)) {
			return true;
		}
		if (ThorPowers.isFlying(player) || player.getAbilities().instabuild) {
			return false;
		}
		if (infinite(power)) {
			setFlying(player, true);
			return true;
		}
		if (ExperimentalPowers.getResource(player, power, STAMINA) <= 0.0f) {
			// seed the meter full on the very first activation
			ExperimentalPowers.setResource(player, power, STAMINA, MAX_STAMINA, MAX_STAMINA);
		}
		if (ExperimentalPowers.getResource(player, power, STAMINA) < 5.0f) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.herocraft.flight.no_stamina"), true);
			return false;
		}
		setFlying(player, true);
		return true;
	}

	public static void setFlying(ServerPlayer player, boolean flying) {
		player.setAttached(ModAttachments.HERO_FLYING, flying);
		if (player.getAbilities().instabuild) {
			return;
		}
		player.getAbilities().mayfly = flying;
		player.getAbilities().flying = flying;
		player.getAbilities().setFlyingSpeed(flying ? HERO_FLYING_SPEED : VANILLA_FLYING_SPEED);
		player.onUpdateAbilities();
		if (!flying) {
			player.resetFallDistance();
			player.level().playSound(null, player.blockPosition(), SoundEvents.BREEZE_LAND,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.0f);
		} else {
			player.level().playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.2f);
		}
	}

	/** Per-tick: drain/regen stamina, cut flight when empty or when Thor flight takes over. */
	public static void tick(ServerPlayer player) {
		Power active = ExperimentalPowers.getActive(player);
		boolean flying = isFlying(player);
		boolean canFly = hasFlight(active);

		// A timed self-flight (rock/flame flight) borrows the HERO_FLYING flag purely to drive the
		// flight pose; it manages its own mayfly/flying and must not be cut here.
		if (TimedSelfFlight.anyActive(player)) {
			return;
		}

		if (flying && (ThorPowers.isFlying(player) || !canFly || player.getAbilities().instabuild)) {
			setFlying(player, false);
			return;
		}
		// A power with no flight ability never seeds or regenerates the "flight" stamina resource,
		// so the ability HUD has no meter to draw for it.
		if (!canFly) {
			return;
		}
		if (infinite(active)) {
			if (flying) {
				player.getAbilities().flying = true;
				player.resetFallDistance();
			}
			return;
		}
		if (flying) {
			player.getAbilities().flying = true;
			player.resetFallDistance();
			float stamina = ExperimentalPowers.getResource(player, active, STAMINA) - DRAIN_PER_TICK;
			if (stamina <= 0.0f) {
				ExperimentalPowers.setResource(player, active, STAMINA, 0.0f, MAX_STAMINA);
				setFlying(player, false);
				player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
						"message.herocraft.flight.stamina_out"), true);
			} else if (player.tickCount % 5 == 0) {
				ExperimentalPowers.setResource(player, active, STAMINA, stamina, MAX_STAMINA);
			}
		} else if (active != null && player.onGround()
				&& ExperimentalPowers.getResource(player, active, STAMINA) < MAX_STAMINA
				&& player.tickCount % 5 == 0) {
			ExperimentalPowers.addResource(player, active, STAMINA,
					REGEN_PER_TICK * 5, MAX_STAMINA);
		}
	}

	/** Safety net on join/respawn: a fresh player entity must not keep stale creative-style flight. */
	public static void clearStale(ServerPlayer player) {
		if (!isFlying(player) && !player.getAbilities().instabuild && player.getAbilities().mayfly
				&& !ThorPowers.isFlying(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.getAbilities().setFlyingSpeed(VANILLA_FLYING_SPEED);
			player.onUpdateAbilities();
		}
		player.setAttached(ModAttachments.HERO_FLYING, false);
	}

	public static void applyForwardBoost(ServerPlayer player, double speed) {
		AbilityHelpers.addImpulse(player, player.getLookAngle().scale(speed));
	}
}
