package com.projecthero.mod.worthy;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.FlightLanding;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.power.ThorThrown;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor x Super Soldier (user: "super soldier should be able to fly with Mjolnir or stormbreaker"): a Super Soldier holding a
 * lent Mjolnir or Stormbreaker flies like Thor -- <b>double-tap Space</b> in the air takes off (a little lift, the hammer whirled
 * overhead: {@code WorthyPose}'s spin-up), the shared directional flight moves him (client {@code DirectionalFlight}: W along the
 * look, Sprint faster, a dead hover with no input), Thor's flight pose with the weapon raised ({@code FlightPoseHelper} counts this as
 * Thor flight while a Thor weapon is in hand). A little slower than Thor: 15 b/s cruise, 30 sprinting with Mjolnir (Thor 18 / 36),
 * 13 / 26 with Stormbreaker; X while flying is the Flight Boost (x1.5). Free (he has no Storm Energy). It ends on landing, another
 * double-tap, or when no lent weapon is in his hands (his own throw keeps him up until it is back).
 */
public final class WorthyFlight {
	private WorthyFlight() {
	}

	/** Client-safe (the state is synced to everyone). */
	public static boolean isFlying(Player player) {
		return WorthyState.of(player).flying;
	}

	/** The directional-flight tune for {@code player} (client): the lent weapon's speed, the Flight Boost. */
	public static DirectionalFlightModel.Tune tune(Player player, float flyingSpeed, boolean sprint) {
		boolean axe = WorthyLending.lentWeapon(player) == ThorWeapon.STORMBREAKER;
		double bps = sprint ? (axe ? WorthyConfig.FLIGHT_SPRINT_BPS_STORMBREAKER : WorthyConfig.FLIGHT_SPRINT_BPS_MJOLNIR)
				: (axe ? WorthyConfig.FLIGHT_CRUISE_BPS_STORMBREAKER : WorthyConfig.FLIGHT_CRUISE_BPS_MJOLNIR);
		if (WorthyState.of(player).boostUntil > player.level().getGameTime()) {
			bps *= WorthyConfig.FLIGHT_BOOST_MULT;
		}
		return DirectionalFlightModel.fixedCruise(DirectionalFlightModel.vanilla(flyingSpeed, sprint), bps / 20.0);
	}

	/** The double-tap (server re-checks): lands if flying, else takes off with a lent weapon in hand. Public for the tests. */
	public static boolean toggle(ServerPlayer p) {
		if (isFlying(p)) {
			stop(p);
			return false;
		}
		long now = p.level().getGameTime();
		if (WorthyState.of(p).flyToggleReady > now) {
			return false;
		}
		if (WorthyKit.kit(p) == WorthyKit.Kit.NONE || !p.isAlive() || p.isSpectator() || p.isPassenger() || p.isFallFlying()
				|| p.getAbilities().instabuild) {
			return false;
		}
		start(p, true);
		return true;
	}

	/** Takes off ({@code lift}: the little hop of a double-tap; a Launch is already rising). */
	public static void start(ServerPlayer p, boolean lift) {
		long now = p.level().getGameTime();
		WorthyState.edit(p, n -> {
			n.flying = true;
			n.flyStart = now;
			n.flyToggleReady = now + WorthyConfig.FLIGHT_TOGGLE_CD;
			n.anim = WorthyState.ANIM_TAKEOFF;
			n.animStart = now;
			n.animOff = WorthyLending.lentHand(p) == net.minecraft.world.InteractionHand.OFF_HAND;
		});
		grant(p);
		p.resetFallDistance();
		if (lift) {
			Vec3 v = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(v.x, Math.max(v.y, WorthyConfig.TAKEOFF_LIFT), v.z));
		}
		ServerLevel level = p.serverLevel();
		boolean axe = WorthyLending.lentWeapon(p) == ThorWeapon.STORMBREAKER;
		WorthyFx.crackle(level, p.position().add(0, 2.2, 0), 14, 0.35, axe);
		WorthyFx.sound(level, p.position(), SoundEvents.BREEZE_JUMP, 0.8f, 1.1f);
		WorthyFx.sound(level, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.6f, axe ? 0.9f : 1.15f);
	}

	private static void grant(ServerPlayer p) {
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
	}

	/** Ends the flight if it is running (no fall damage from it). */
	public static void stop(ServerPlayer p) {
		if (!isFlying(p)) {
			return;
		}
		long now = p.level().getGameTime();
		WorthyState.edit(p, n -> {
			n.flying = false;
			n.boostUntil = 0L;
			n.flyToggleReady = now + WorthyConfig.FLIGHT_TOGGLE_CD;
			n.noFallUntil = Math.max(n.noFallUntil, now + WorthyConfig.NO_FALL_TICKS);
		});
		if (!p.getAbilities().instabuild && !p.isSpectator() && !otherFlight(p)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		WorthyFx.sound(p.serverLevel(), p.position(), SoundEvents.BREEZE_LAND, 0.7f, 0.9f);
	}

	/** Every server tick for every player. */
	static void tick(ServerPlayer p) {
		if (!isFlying(p)) {
			return;
		}
		if (p.isSpectator() || p.isPassenger() || p.isFallFlying() || !p.isAlive() || p.getAbilities().instabuild) {
			stop(p);
			return;
		}
		// the weapon left his hands (called back, dropped, the loan ended) -- his own throw keeps him up until it is back
		boolean armed = WorthyKit.kit(p) != WorthyKit.Kit.NONE
				|| (ThorThrown.any(p) && WorthyState.of(p).throwWeapon >= 0);
		if (!armed) {
			stop(p);
			return;
		}
		WorthyState s = WorthyState.of(p);
		long now = p.level().getGameTime();
		if (now - s.flyStart >= WorthyConfig.FLIGHT_LIFTOFF_GRACE && FlightLanding.landed(p, s.flyStart)) {
			stop(p);
			return;
		}
		if (!p.getAbilities().flying || !p.getAbilities().mayfly) {
			if (now - s.flyStart >= WorthyConfig.FLIGHT_LIFTOFF_GRACE && !p.getAbilities().flying && p.getAbilities().mayfly) {
				stop(p); // the client dropped vanilla flight on its own (landed / double-tapped): follow it
				return;
			}
			grant(p);
		}
		p.resetFallDistance();
	}

	private static boolean otherFlight(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.FLYING, false)
				|| p.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| p.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
	}
}
