package com.projecthero.mod.ironman;

import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The single per-player-per-tick entry point for the Iron Man system, called from
 * {@code ProjectHeroMod}'s server-tick loop (wrapped in the existing {@code TickWatchdog}).
 *
 * <ol>
 *   <li>{@link IronManArmor#enforce} -- eject Iron Man armour from unauthorised wearers;</li>
 *   <li>{@link IronManFlight#tick} -- repulsor-flight energy drain + end conditions;</li>
 *   <li>{@link IronManSuitUpManager#tick} -- advance any suit-up / suit-down sequence;</li>
 *   <li>{@link IronManPassives#tick} -- reconcile the suit's strength / mobility passives;</li>
 *   <li>{@link IronManSuitCall#tickPending} -- count down an armour still travelling from an unloaded
 *       Suit Platform;</li>
 *   <li>tick the continuous Unibeam / Repulsor Barrier / charged-repulsor spin-up and recharge the
 *       worn suit -- but <b>every one of those cuts out the instant the suit comes off or the power
 *       drops</b>, via {@link #shutDownAllSystems}.</li>
 * </ol>
 */
public final class IronManSuitTicker {
	private IronManSuitTicker() {
	}

	public static void tick(ServerPlayer player) {
		IronManArmor.enforce(player);
		// "changes 19": a lifted faceplate / extended blades must not survive the armour coming off.
		IronManFaceplate.reconcile(player);
		IronManBlade.tick(player);
		IronManFlight.tick(player);
		IronManSuitUpManager.tick(player);
		IronManPassives.tick(player);
		IronManSuitCall.tickPending(player);

		TonyStarkState s = TonyStark.state(player);

		// "changes 17": Protocol Phoenix -- if the emergency suit-inbound state is active, tick it (it
		// keeps the player incapacitated + watches for the suit to finish equipping / a failsafe).
		if (s.phoenixEmergencyUntil != 0L) {
			ProtocolPhoenix.tick(player);
		}

		// Mark 1 flamethrower heat gauge ("changes 14"): vent it back toward zero whenever the stream
		// isn't being held, regardless of suit/power state.
		if (!s.flamethrowerHeld && s.flamethrowerHeat > 0f) {
			IronManAbilities.ventFlamethrowerHeat(player);
			s = TonyStark.state(player);
		}

		if (!TonyStark.hasPower(player)) {
			shutDownAllSystems(player, s, null);
			clearHelmetNightVision(player);
			return;
		}

		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		// "changes 21": the mob-highlight toggle is a helmet HUD overlay -- the instant an Iron Man
		// helmet is no longer worn (docked into a Suit Platform, pulled off by hand, or the suit
		// swapped for one without the toggle) the toggle is dropped, so it can never silently linger
		// and snap back on the moment a helmet returns. The suit==null path below also shuts it down
		// via shutDownAllSystems; this additionally covers "helmet off, other pieces still on".
		if (s.mobHighlightOn && (suitId == null || !IronManArmor.hasHelmet(player, suitId))) {
			IronManAbilities.clearMobHighlight(player);
			s = TonyStark.state(player);
		}

		// v0.11.12, explicit user request: Mark 1's mob-highlight toggle now expires on its own after
		// IronManAbilities.MARK_1_MOB_HIGHLIGHT_DURATION_TICKS instead of running indefinitely -- every
		// other suit sharing this ability is unaffected (their toggle stays free and indefinite).
		if (s.mobHighlightOn && suitId != null
				&& player.level().getGameTime() >= IronManAbilities.mark1MobHighlightUntil(s, suitId)
				&& IronManAbilities.mark1MobHighlightUntil(s, suitId) != 0L) {
			IronManAbilities.clearMobHighlight(player);
			s = TonyStark.state(player);
		}

		// No valid suit worn -> everything the suit was doing stops.
		if (suit == null) {
			shutDownAllSystems(player, s, null);
			clearHelmetNightVision(player);
			if (s.suitAir < 1.0f) {
				TonyStark.setSuitAir(player, 1.0f);
			}
			return;
		}

		// "changes 17": mechanical systems that run regardless of suit energy -- the air tank, the
		// helmet's Night Vision optics, and the Mark 1's heavy-in-water drag.
		IronManAirTank.tick(player, suit);
		applyHelmetOptics(player, suit, suitId);
		applyWaterDrag(player, suit, suitId);

		boolean integrityFailed = IronManEnergy.integrity(player, suitId) <= 0f;
		// "Systems freeze" above a suit's altitude ceiling (Mark 2, spec "changes 12") behaves like a
		// depleted suit for as long as the player is up there -- no active abilities, flight cut.
		boolean altitudeLocked = suit.altitudeCeiling() > 0.0 && player.getY() >= suit.altitudeCeiling();
		// Mark 2 ("changes 17"): entering the locked zone also gives Freeze + a hard 4 s systems lockout.
		boolean ceilingFreezing = false;
		if (suit.ceilingFreeze()) {
			long now = player.level().getGameTime();
			if (altitudeLocked && !s.wasAboveCeiling) {
				s.ceilingFreezeUntil = now + 80;
				((net.minecraft.server.level.ServerLevel) player.level()).playSound(null, player.blockPosition(),
						net.minecraft.sounds.SoundEvents.PLAYER_HURT_FREEZE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.7f);
				player.displayClientMessage(net.minecraft.network.chat.Component
						.translatable("hud.projecthero.ironman.ceiling_freeze")
						.withStyle(net.minecraft.ChatFormatting.AQUA), true);
			}
			s.wasAboveCeiling = altitudeLocked;
			ceilingFreezing = s.ceilingFreezeUntil > now;
			if (ceilingFreezing) {
				player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() - 1, player.getTicksFrozen() + 5));
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 3, true, false, false));
			}
		}
		// Mark 4 systems overload ("changes 15"): the whole suit is offline for 15 s after the wrist
		// laser fires -- but keep ticking the wrist laser itself down first if it is still mid-beam.
		if (s.wristLaserUntil != 0L) {
			IronManAbilities.tickWristLaser(player, suit);
			s = TonyStark.state(player);
		}
		boolean overloaded = TonyStark.overloaded(player);
		boolean depleted = IronManEnergy.energy(player, suitId) <= 0f || integrityFailed || altitudeLocked
				|| overloaded || ceilingFreezing;
		if (depleted) {
			// Suit is powered down: no active abilities run, targeting drops. Physical armour only.
			shutDownAllSystems(player, s, suit);
			IronManEnergy.tickRecharge(player, suit); // the Arc Reactor still trickles it back up
			IronManEnergy.tickArmorRegen(player, suit); // "changes 18": worn self-repair keeps working
			tickIntegrityFailure(player, suitId, integrityFailed);
			return;
		}
		tickIntegrityFailure(player, suitId, false);

		// Targeting mode also needs the helmet, and expires on its own timer.
		if (!IronManArmor.hasHelmet(player, suitId) || s.targetingUntil <= player.level().getGameTime()) {
			s.targetingUntil = 0L;
		}

		if (s.unibeamUntil != 0L) {
			IronManAbilities.tickUnibeam(player, suit);
		}
		if (s.barrierHeld) {
			IronManAbilities.tickBarrier(player, suit);
		}
		if (s.chargeStartTick != 0L) {
			IronManAbilities.tickCharge(player, suit);
		}
		if (s.repulsorWindupAt != 0L) {
			IronManAbilities.tickRepulsorWindup(player, suit);
		}
		if (s.flamethrowerHeld) {
			IronManAbilities.tickFlamethrower(player, suit);
		}
		if (s.pendingMissiles > 0) {
			IronManAbilities.tickMicroMissiles(player, suit);
		}
		IronManEnergy.tickRecharge(player, suit);
		IronManEnergy.tickArmorRegen(player, suit); // "changes 18": Mark III+ slowly self-repair while worn
	}

	/** Ticks up to 3.6s, refreshed every tick while integrity is still zero -- see IronManDamage's
	 *  class javadoc for why a short refreshed duration is used instead of an explicit remove/add. */
	private static final int INTEGRITY_FAILURE_EFFECT_TICKS = 72;

	/**
	 * "Changes 12": once a worn suit's integrity is fully depleted, its life-support and stabilisers
	 * are gone too -- the wearer is slowed and weakened for as long as it stays at zero. Refreshed every
	 * tick (rather than applied once) so it disappears on its own the instant integrity is repaired or
	 * the suit comes off, without ever needing to touch an unrelated Slowness/Weakness the player picked
	 * up from something else.
	 */
	private static void tickIntegrityFailure(ServerPlayer player, String suitId, boolean failed) {
		if (!failed) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, INTEGRITY_FAILURE_EFFECT_TICKS, 1, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, INTEGRITY_FAILURE_EFFECT_TICKS, 1, true, false, false));
	}

	/**
	 * "changes 17": every Iron Man helmet except the Mark 1's carries a Night Vision optic -- while it
	 * is worn the wearer sees in the dark. Refreshed every tick as a hidden ambient effect; it fades on
	 * its own a moment after the helmet comes off.
	 */
	private static void applyHelmetOptics(ServerPlayer player, IronManSuit suit, String suitId) {
		if (suit.helmetNightVision() && IronManArmor.hasHelmet(player, suitId)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
		} else {
			// "changes 18": the instant the helmet comes off (or this mark has no NV optic) the Night
			// Vision goes with it -- no 15 s lingering fade.
			clearHelmetNightVision(player);
		}
	}

	/**
	 * Remove the helmet's ambient Night Vision if it is ours. Our optic is the only invisible, no-icon,
	 * ambient Night Vision with a duration of 400 ticks or less, so this never touches a Night Vision
	 * the player got from a potion or a beacon.
	 */
	static void clearHelmetNightVision(ServerPlayer player) {
		MobEffectInstance eff = player.getEffect(MobEffects.NIGHT_VISION);
		if (eff != null && eff.isAmbient() && !eff.isVisible() && !eff.showIcon() && eff.getDuration() <= 400) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	/**
	 * "changes 17": the Mark 1 is bulky and heavy -- swimming in it is 50% slower. Modelled as a strong
	 * Slowness while the full suit is worn and the player is in water; it clears on its own the instant
	 * they leave the water or take the suit off.
	 */
	private static void applyWaterDrag(ServerPlayer player, IronManSuit suit, String suitId) {
		if (suit.waterMoveMultiplier() < 1.0 && player.isInWater()
				&& IronManArmor.wearingFullSuit(player, suitId)) {
			// amplifier 2 = Slowness III ~= -45% ground speed, close to the intended 50% swim penalty
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, true, false, false));
		}
	}

	/**
	 * Cancel every active Iron Man system in one place, so "the suit is on or off" is the single
	 * switch for all of it (spec "changes 9" follow-up). Called when the suit comes off, the power is
	 * lost, or the suit runs out of energy / integrity.
	 */
	public static void shutDownAllSystems(ServerPlayer player, TonyStarkState s, IronManSuit suit) {
		s.targetingUntil = 0L;
		s.unibeamUntil = 0L;
		s.chargeStartTick = 0L;
		s.chargeReadyPinged = false;
		s.repulsorWindupAt = 0L;
		s.flamethrowerHeld = false;
		s.pendingMissiles = 0;
		s.pendingMissileSuit = "";
		// "changes 13": the mob-highlight toggle must not stay latched on with the suit off / unpowered.
		IronManAbilities.clearMobHighlight(player);
		// "changes 19": retract the Mark 5 blades when the suit powers down / comes off.
		IronManBlade.retract(player);
		if (s.barrierHeld) {
			IronManAbilities.stopBarrier(player, suit, false);
		}
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		// "changes 15": end a Mark 1 flight burst cleanly (clears the synced timer + starts its 13 s cd)
		// and drop a Mark 4 wrist-laser beam if the suit comes off mid-fire. Done last -- these swap the
		// attachment, so the `s` reference above must not be read afterwards.
		if (TonyStark.state(player).timedFlightUntil != 0L) {
			IronManAbilities.endTimedFlight(player, suit == null ? null : suit.id());
		}
		if (TonyStark.state(player).supersonicUntil != 0L) {
			IronManAbilities.endSupersonic(player, suit == null ? null : suit.id());
		}
		if (TonyStark.state(player).wristLaserUntil != 0L) {
			TonyStark.setWristLaserUntil(player, 0L);
		}
	}
}
