package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.network.IronManBeamPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The shared Iron Man ability implementations. Slot layout for the advanced marks (III / V / VII / 42 /
 * 50), updated in "changes 13":
 * <pre>
 *   R  slot 1  Repulsor Blast     tap = shot (80 energy); HOLD >= 2 s then release = Charged Repulsor
 *                                 (250 energy, 3x the damage). Mark 2 (v0.11.13): a quick tap = a forced
 *                                 1 s spin-up then an ordinary blast (20 energy); HOLD >= 2 s then
 *                                 release instead fires its own cheaper Charged Repulsor (50 energy).
 *   G  slot 2  Repulsor Shield    frontal energy shield: blocks 90% of any hit landing in the 180-deg
 *                                 front arc + deflects projectiles while up, 40 energy/s, 6 s cooldown
 *   X  slot 3  Micro-Missiles     volley fired one at a time (each lands + blasts separately)
 *   Z  slot 4  Unibeam            5 s chest beam, 10 dmg/tick, 700 energy, light block-break, 30 s cd
 *   V  slot 5  Mob Highlight      client-only on/off outline of nearby hostiles; auto-clears when the
 *                                 suit comes off or loses power (Targeting Mode was removed here)
 *   C  slot 6  Suit toggle        (suit-down when worn; call armour handled in IronManAbilityManager)
 * </pre>
 * Every ability re-checks {@link IronManArmor#canOperate} + required worn pieces + suit energy
 * server-side. Beams broadcast as an {@link IronManBeamPayload} so they show first and third person.
 */
public final class IronManAbilities {
	public static final String REPULSOR_BLAST = "repulsor_blast";
	public static final String CHARGED_REPULSOR = "charged_repulsor";
	public static final String REPULSOR_BARRIER = "repulsor_barrier";
	public static final String UNIBEAM = "unibeam";
	public static final String MICRO_MISSILES = "micro_missiles";
	public static final String SUIT_TOGGLE = "suit_toggle";
	// -- Mark 1 / Mark 2 ("changes 12") --
	public static final String STRONG_PUNCH = "strong_punch";
	public static final String FLAMETHROWER = "flamethrower";
	public static final String ROCKET = "rocket";
	public static final String FLARE = "flare";
	/** v0.14.27: Mark 2 G -- clap the repulsors together for a sonic cone ({@link IronManSonicClap}). */
	public static final String SONIC_CLAP = "sonic_clap";
	/** "changes 19": Mark 5 -- extend / retract the gauntlet blades (+4 melee, no block placing). */
	public static final String BLADE = "blade";
	public static final String MOB_HIGHLIGHT_TOGGLE = "mob_highlight_toggle";
	public static final String TIMED_FLIGHT = "timed_flight";
	/** Mark 4 ("changes 15"): one-shot wrist laser, reached by sneaking + the V slot. */
	public static final String WRIST_LASER = "wrist_laser";
	// -- Mark 7 weapon wheel ("changes 16") --
	/** Slot 5 (V) on a weapon-wheel suit: opens the wheel that re-binds slot 3. */
	public static final String WEAPON_WHEEL = "weapon_wheel";
	/** Slot 3 (X) on a weapon-wheel suit: dispatches to whatever the wheel currently has selected. */
	public static final String WEAPON_WHEEL_SLOT = "weapon_wheel_slot";
	/** A timed high-speed flight burst -- one of the weapon-wheel X options. */
	public static final String SUPERSONIC_FLIGHT = "supersonic_flight";
	/** "changes 17": a non-X-binding weapon-wheel entry -- toggles the coloured entity-glow overlay. */
	public static final String ENTITY_GLOW_TOGGLE = "entity_glow_toggle";
	/**
	 * v0.14.21 round two: a Mark VII weapon-wheel X option -- four missiles that lock on to whatever is nearest the
	 * crosshair within {@link #HOMING_RANGE} blocks and steer onto it. Same energy as a Micro-Missiles volley.
	 */
	public static final String HOMING_MISSILES = "homing_missiles";
	public static final int HOMING_MISSILE_COUNT = 4;
	public static final double HOMING_RANGE = 30.0;
	/** Half-angle of the lock-on cone around the crosshair (degrees). */
	public static final double HOMING_CONE_DEG = 30.0;
	/** The abilities the Mark 7 weapon wheel can bind to slot 3, in wheel order. */
	public static final String[] WEAPON_WHEEL_OPTIONS = {
			MICRO_MISSILES, HOMING_MISSILES, FLAMETHROWER, WRIST_LASER, ROCKET, SUPERSONIC_FLIGHT };
	/** Every weapon-wheel sector, in wheel order: the six X bindings plus the entity-glow toggle (v0.14.21: 7 wedges). */
	public static final String[] WEAPON_WHEEL_SECTORS = {
			MICRO_MISSILES, HOMING_MISSILES, FLAMETHROWER, WRIST_LASER, ROCKET, SUPERSONIC_FLIGHT, ENTITY_GLOW_TOGGLE };

	/** How long slot 1 must be held before a release fires the Charged Repulsor instead of a tap shot. */
	public static final int CHARGE_HOLD_TICKS = 40; // 2 seconds

	// -- flat energy costs ("changes 13"; retuned "changes 18"): one figure for every mark --
	/** Energy a standard Repulsor Blast costs ("changes 18": 80). */
	public static final float REPULSOR_ENERGY = 80.0f;
	/** Energy a Charged Repulsor costs ("changes 18": 250, and it hits 3x as hard as a plain blast). */
	public static final float CHARGED_REPULSOR_ENERGY = 250.0f;
	// v0.14.27: each suit's tap / charged repulsor numbers are IronManSuit builder data (repulsorTap / repulsorCharged).
	/** How much harder a Charged Repulsor hits than a plain Repulsor Blast ("changes 18"). */
	public static final float CHARGED_REPULSOR_DAMAGE_MULTIPLIER = 3.0f;
	/** Total energy one full Unibeam channel costs ("changes 18": 700, spread evenly across its 5 s). */
	public static final float UNIBEAM_ENERGY = 700.0f;
	// v0.14.27: the Unibeam channel length / damage / energy / cooldown are IronManSuit#unibeamChannel builder data.

	private static final int BARRIER_COOLDOWN_TICKS = 6 * 20; // after you drop it
	private static final float BARRIER_ENERGY_PER_TICK = 2.0f; // "changes 18": Repulsor Shield costs 40 energy/second
	private static final float BARRIER_ACTIVATION_COST = 40.0f;
	/** Fraction of incoming <em>frontal</em> damage that gets through while the Repulsor Shield holds
	 *  (0.1 = 90% blocked, "changes 13"). Damage from behind the 180-degree front arc is unaffected --
	 *  see {@link com.projecthero.mod.ironman.IronManDamage}. */
	public static final float BARRIER_DAMAGE_MULT = 0.1f;

	// -- Mark 1 / Mark 2 ("changes 12") --
	public static final float PUNCH_DAMAGE = 15.0f; // v0.14.27: explicit user request
	private static final double PUNCH_RANGE = 4.0;
	public static final int PUNCH_COOLDOWN_TICKS = 20; // v0.14.27: 1 s
	public static final float PUNCH_ENERGY_COST = 10f; // v0.14.27: explicit user request

	private static final float FLAMETHROWER_ENERGY_PER_TICK = 0.25f; // v0.11.12: 5/sec (was 6/tick = 120/sec), explicit user request
	private static final double FLAMETHROWER_REACH = 6.0;
	private static final float FLAMETHROWER_DAMAGE = 2.5f;
	/** v0.14.22, explicit user request: Mark 1 burns harder than the Mark VII wheel flamethrower (was 2.5). */
	public static final float MARK_1_FLAMETHROWER_DAMAGE = 5.0f;
	/**
	 * Mark 1 Flamethrower heat gauge ("changes 14") -- the same overheat model as Pyrokinesis's
	 * flamethrower ability. Starts at 0, climbs {@value #FLAMETHROWER_HEAT_PER_TICK}/tick while the
	 * stream is held (~13 s to overheat), vents {@value #FLAMETHROWER_HEAT_VENT_PER_TICK}/tick while
	 * idle; at max it cuts out and can't be re-lit until it drops back below MAX - MIN.
	 */
	public static final float FLAMETHROWER_MAX_HEAT = 500.0f;

	/** The heat-gauge ceiling for a given suit ("changes 17": Mark 7's is 50% bigger). */
	public static float flamethrowerMaxHeat(IronManSuit suit) {
		if (suit != null && suit.flamethrowerMaxHeatOverride() > 0f) {
			return suit.flamethrowerMaxHeatOverride(); // v0.14.27: Mark 1 = 100
		}
		return FLAMETHROWER_MAX_HEAT * (suit == null ? 1f : suit.flamethrowerHeatMultiplier());
	}

	private static final float FLAMETHROWER_HEAT_PER_TICK = 1.9f;
	private static final float FLAMETHROWER_HEAT_VENT_PER_TICK = 1.0f;
	private static final float FLAMETHROWER_HEAT_MIN = 20.0f;

	private static final float ROCKET_DAMAGE = 15.0f;
	private static final float ROCKET_ENERGY_COST = 300f; // "changes 18"
	private static final int ROCKET_COOLDOWN_TICKS = 20 * 20;
	// v0.11.13, explicit user request: Mark 1 and Mark 2 now each have their own Rocket tuning (was one
	// shared set of constants for both) and both now break blocks on impact (see IronManMissileEntity).
	public static final float MARK_1_ROCKET_DAMAGE = 30.0f; // v0.14.22: up from 25, explicit user request
	private static final float MARK_1_ROCKET_ENERGY_COST = 100f;
	public static final int MARK_1_ROCKET_COOLDOWN_TICKS = 10 * 20; // v0.14.22: down from 25 s, explicit user request
	private static final float MARK_2_ROCKET_ENERGY_COST = 50f;

	/** v0.14.27: the Mark 2 X flares' cooldown (the flares themselves live in {@link IronManFlares}). */
	public static final int FLARE_COOLDOWN_TICKS = 10 * 20;
	/** v0.14.27: Mark 2 Shift+X while flying -- a 30 s supersonic boost for 50 energy. */
	public static final int MARK_2_BOOST_TICKS = 30 * 20;
	public static final float MARK_2_BOOST_ENERGY = 50f;
	/** v0.14.27: Mark 2 Shift+X on the ground -- light the block you look at, like flint and steel. */
	public static final float IGNITE_ENERGY = 1f;

	public static final int TIMED_FLIGHT_TICKS = 20 * 20;
	public static final float TIMED_FLIGHT_ACTIVATION_COST = 50f; // v0.14.27: 50 on activation, explicit user request
	/** v0.11.12, explicit user request: on top of the flat activation cost, the burst also drains this
	 *  much energy per second for as long as it stays airborne. */
	public static final float TIMED_FLIGHT_DRAIN_PER_SECOND = 0f; // v0.14.27: no drain -- the burst halves energy regen instead
	/** v0.14.27: Shift+X -- launch along the look, then the burst switches on this long afterwards. */
	public static final int LAUNCH_BURST_DELAY_TICKS = 3 * 20;
	/** v0.14.27: how hard Shift+X throws the wearer along the look (blocks per tick). */
	public static final double LAUNCH_SPEED = 3.2;
	private static final String FLIGHT_BURST_PENDING_KEY = "flight_burst_pending_at";
	private static final String FLAMETHROWER_VENT_KEY = "flamethrower_vent_at";
	/** Cooldown applied to the Mark 1 flight burst once it ends ("changes 15"). */
	public static final int TIMED_FLIGHT_COOLDOWN_TICKS = 13 * 20;

	// v0.14.27: the V highlight's switch-on cost and auto-off timer are IronManSuit#mobHighlight builder data.
	/** Key suffix stored in the shared, already-synced {@code abilityReadyAt} map for the highlight's
	 *  own expiry timestamp -- avoids adding a 17th field to {@link TonyStarkState}'s codec, which is
	 *  already at its 16-field ceiling per that class's own javadoc. */
	private static final String MOB_HIGHLIGHT_UNTIL_KEY = "mob_highlight_until";

	// -- Mark 4 wrist laser ("changes 15") --
	public static final int WRIST_LASER_TICKS = 4 * 20;      // 4 s active
	/** Applied every tick; vanilla i-frames gate it to a hit roughly every half-second, so ~20/s --
	 *  the same i-frame-limited model the Unibeam uses. */
	public static final float WRIST_LASER_DAMAGE_PER_TICK = 10.0f;
	public static final int OVERLOAD_TICKS = 30 * 20;        // "changes 16": systems offline for 30 s afterwards
	private static final float WRIST_LASER_ACTIVATION_COST = 500f; // "changes 18"
	private static final double WRIST_LASER_RANGE = 40.0;

	// -- supersonic flight ("changes 16"; reworked "changes 17") --
	/** 20 s of supersonic flight; can be ended early by pressing the button again. */
	public static final int SUPERSONIC_TICKS = 20 * 20;
	private static final float SUPERSONIC_ACTIVATION_COST = 300f;
	/** 10 s cooldown -- starts only once the burst ends (early cancel or timer). */
	public static final int SUPERSONIC_COOLDOWN_TICKS = 10 * 20;
	private static final double SUPERSONIC_SPEED_MPS = 80.0;
	/** Supersonic flight burns flight energy at this multiple of the ordinary rate. */
	public static final float SUPERSONIC_FLIGHT_COST_MULTIPLIER = 3.0f;

	private IronManAbilities() {
	}

	/** Dispatch one slot edge. {@code pressed} true = key-down, false = key-up. */
	public static void trigger(ServerPlayer player, IronManSuit suit, int slot, boolean pressed) {
		String ability = suit.abilityInSlot(slot);
		if (ability == null) {
			return;
		}
		if (!IronManArmor.canOperate(player)) {
			reject(player);
			return;
		}
		// Mark 2's altitude ceiling ("changes 12"): systems freeze above it, same as a depleted suit --
		// this only needs to block the initial press, not the SUIT_TOGGLE store/summon toggle, which
		// should still work to get the player OUT of the suit if they're stuck up there.
		if (pressed && !SUIT_TOGGLE.equals(ability)
				&& suit.altitudeCeiling() > 0.0 && player.getY() >= suit.altitudeCeiling()) {
			player.displayClientMessage(Component.translatable("hud.projecthero.ironman.systems_frozen")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			return;
		}
		// Mark 4 systems overload ("changes 15" / 30 s in "changes 16"): the suit is offline afterwards --
		// nothing but the store toggle responds, on either key edge (a blocked press must not leave a
		// release that still fires the shot -- "changes 16").
		if (!SUIT_TOGGLE.equals(ability) && TonyStark.overloaded(player)) {
			if (pressed) {
				player.displayClientMessage(Component.translatable("message.projecthero.ironman.systems_overloaded")
						.withStyle(net.minecraft.ChatFormatting.RED), true);
			}
			return;
		}
		switch (ability) {
			case REPULSOR_BLAST -> {
				// v0.14.27: Shift+R on a dash suit is the repulsor dash, not a shot
				if (pressed && player.isShiftKeyDown() && suit.hasDash()) {
					TonyStarkState rs = TonyStark.state(player);
					rs.chargeStartTick = 0L;
					rs.chargeReadyPinged = false;
					IronManDash.start(player, suit.dashDamage(), suit.dashEnergy(), suit.dashCooldownTicks());
				} else {
					repulsorSlot(player, suit, pressed);
				}
			}
			case SONIC_CLAP -> {
				if (pressed) {
					IronManSonicClap.fire(player, suit.sonicClapDamage(), suit.sonicClapEnergy(), suit.sonicClapCooldownTicks());
				}
			}
			case REPULSOR_BARRIER -> {
				if (pressed) {
					startBarrier(player, suit);
				} else {
					stopBarrier(player, suit, true);
				}
			}
			case MICRO_MISSILES -> { if (pressed) microMissiles(player, suit); }
			case UNIBEAM -> { if (pressed) startUnibeam(player, suit); }
			case SUIT_TOGGLE -> {
				if (pressed) {
					// "changes 15": sneak + C on the Mark 5 folds it into its suitcase item instead of
					// storing four pieces in the inventory.
					if (player.isShiftKeyDown()
							&& suit.summonType() == com.projecthero.mod.ironman.suit.SummonType.SUITCASE_ITEM) {
						IronManSuitUpManager.beginSuitDownToCase(player, suit.id());
					} else {
						IronManSuitUpManager.beginSuitDown(player, suit.id());
					}
				}
			}
			case STRONG_PUNCH -> { if (pressed) strongPunch(player, suit); }
			case FLAMETHROWER -> {
				if (!pressed) {
					TonyStark.state(player).flamethrowerHeld = false;
				} else if (TonyStark.state(player).flamethrowerHeat >= flamethrowerMaxHeat(suit) - FLAMETHROWER_HEAT_MIN) {
					player.displayClientMessage(
							Component.translatable("message.projecthero.ironman.flamethrower_overheated"), true);
				} else {
					TonyStark.state(player).flamethrowerHeld = true;
				}
			}
			case ROCKET -> { if (pressed) rocket(player, suit); }
			case FLARE -> { if (pressed) flareSlot(player, suit); }
			case BLADE -> { if (pressed) com.projecthero.mod.ironman.IronManBlade.toggle(player); }
			case MOB_HIGHLIGHT_TOGGLE -> {
				if (pressed) {
					// Mark 4 ("changes 15"): sneak + V fires the one-shot wrist laser instead of toggling
					// the highlight. A plain V still toggles the highlight as on every other mark.
					if (suit.hasWristLaser() && player.isShiftKeyDown()) {
						wristLaser(player, suit);
					} else {
						toggleMobHighlight(player, suit);
					}
				}
			}
			case WRIST_LASER -> { if (pressed) wristLaser(player, suit); }
			case TIMED_FLIGHT -> {
				if (pressed) {
					if (player.isShiftKeyDown()) {
						launchFlightBurst(player, suit);
					} else {
						timedFlight(player, suit);
					}
				}
			}
			case SUPERSONIC_FLIGHT -> { if (pressed) supersonicFlight(player, suit); }
			case WEAPON_WHEEL -> { if (pressed) openWeaponWheel(player, suit); }
			case WEAPON_WHEEL_SLOT -> dispatchWheelChoice(player, suit, pressed);
			default -> { }
		}
	}

	// ---------------- Mark 7 weapon wheel ("changes 16") ----------------

	/** Slot 5 (V) on a weapon-wheel suit: tell the client to open the wheel. */
	private static void openWeaponWheel(ServerPlayer player, IronManSuit suit) {
		if (!requireHelmet(player, suit)) {
			return;
		}
		ServerPlayNetworking.send(player, new com.projecthero.mod.network.IronManWeaponWheelPayload(""));
	}

	/** Slot 3 (X) on a weapon-wheel suit: run whatever the wheel currently has bound. */
	private static void dispatchWheelChoice(ServerPlayer player, IronManSuit suit, boolean pressed) {
		String chosen = TonyStark.weaponWheelChoice(player);
		switch (chosen) {
			case MICRO_MISSILES -> { if (pressed) microMissiles(player, suit); }
			case HOMING_MISSILES -> { if (pressed) homingMissiles(player, suit); }
			case ROCKET -> { if (pressed) rocket(player, suit); }
			case WRIST_LASER -> { if (pressed) wristLaser(player, suit); }
			case SUPERSONIC_FLIGHT -> { if (pressed) supersonicFlight(player, suit); }
			case FLAMETHROWER -> {
				if (!pressed) {
					TonyStark.state(player).flamethrowerHeld = false;
				} else if (TonyStark.state(player).flamethrowerHeat >= flamethrowerMaxHeat(suit) - FLAMETHROWER_HEAT_MIN) {
					player.displayClientMessage(
							Component.translatable("message.projecthero.ironman.flamethrower_overheated"), true);
				} else {
					TonyStark.state(player).flamethrowerHeld = true;
				}
			}
			default -> { }
		}
	}

	// ---------------- repulsors (slot 1: tap = blast, hold >= 2 s = charged) ----------------

	private static void repulsorSlot(ServerPlayer player, IronManSuit suit, boolean pressed) {
		TonyStarkState s = TonyStark.state(player);
		long now = player.level().getGameTime();

		// Mark 2 ("changes 13"; Charged variant added v0.11.13, explicit user request): a quick tap
		// commits to the forced 1 s spin-up, then an ordinary blast fires (tickRepulsorWindup). Holding
		// the key for >= CHARGE_HOLD_TICKS before releasing instead fires a Charged Repulsor directly --
		// no separate windup needed, the hold itself is the charge-up.
		if (suit.repulsorWindupTicks() > 0) {
			if (pressed) {
				if (s.chargeStartTick == 0L) {
					s.chargeStartTick = now;
					s.chargeReadyPinged = false;
				}
				return;
			}
			if (s.chargeStartTick == 0L) {
				return; // v0.14.27: a release with no press behind it (the press was a Shift+R dash)
			}
			long held = now - s.chargeStartTick;
			s.chargeStartTick = 0L;
			s.chargeReadyPinged = false;
			if (held >= suit.chargeHoldTicks()) {
				chargedRepulsor(player, suit);
				return;
			}
			if (s.repulsorWindupAt == 0L && requireChest(player, suit)
					&& cooldownReady(player, suit.id(), REPULSOR_BLAST)) {
				TonyStark.state(player).repulsorWindupAt = now + suit.repulsorWindupTicks();
				AbilityHelpers.sound(player, SoundEvents.BEACON_AMBIENT, 0.4f, 0.6f);
			}
			return;
		}

		if (pressed) {
			// Multiple key-down edges in one press are fine -- just (re)start the hold timer.
			if (s.chargeStartTick == 0L) {
				s.chargeStartTick = now;
				s.chargeReadyPinged = false;
			}
			return;
		}
		if (s.chargeStartTick == 0L) {
			return; // v0.14.27: a release with no press behind it (the press was a Shift+R dash)
		}
		long held = now - s.chargeStartTick;
		s.chargeStartTick = 0L;
		s.chargeReadyPinged = false;
		if (held >= suit.chargeHoldTicks()) {
			chargedRepulsor(player, suit);
		} else {
			repulsorBlast(player, suit);
		}
	}

	/** Called every tick from {@link com.projecthero.mod.ironman.IronManSuitTicker} while a Mark-2-style
	 *  windup shot is pending. Draws the spin-up and fires an ordinary blast once the timer elapses. */
	public static void tickRepulsorWindup(ServerPlayer player, IronManSuit suit) {
		long windupAt = TonyStark.state(player).repulsorWindupAt;
		if (windupAt == 0L) {
			return;
		}
		if (!IronManArmor.hasChestplate(player, suit.id())) {
			TonyStark.state(player).repulsorWindupAt = 0L;
			return;
		}
		long now = player.level().getGameTime();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(player, look);
		Vec3 muzzle = player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.35, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.02);
		com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.CHARGING); // v0.14.26 pose
		// v0.11.13, explicit user request: an audible spin-up, not just a single blip when the windup
		// starts -- a rising-pitch hum every 4 ticks as it nears firing.
		long remaining = Math.max(0L, windupAt - now);
		if (remaining % 4 == 0) {
			float pitch = 0.6f + 0.5f * (1f - (float) remaining / suit.repulsorWindupTicks());
			AbilityHelpers.sound(player, SoundEvents.BEACON_AMBIENT, 0.3f, pitch);
		}
		if (now >= windupAt) {
			TonyStark.state(player).repulsorWindupAt = 0L;
			repulsorBlast(player, suit);
		}
	}

	/**
	 * Spin-up feedback while slot 1 is held (called every tick from {@link com.projecthero.mod.ironman.IronManSuitTicker}).
	 * Bails and clears the hold if the chestplate is lost mid-charge.
	 */
	public static void tickCharge(ServerPlayer player, IronManSuit suit) {
		TonyStarkState s = TonyStark.state(player);
		if (s.chargeStartTick == 0L) {
			return;
		}
		if (!IronManArmor.hasChestplate(player, suit.id())) {
			s.chargeStartTick = 0L;
			s.chargeReadyPinged = false;
			return;
		}
		long now = player.level().getGameTime();
		long held = now - s.chargeStartTick;
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(player, look);
		Vec3 muzzle = player.getEyePosition().add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.35, 0);
		int count = (int) Math.min(8, 1 + held / 6);
		com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.CHARGING); // v0.14.26 pose
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, count, 0.06, 0.06, 0.06, 0.02);
		if (held % 6 == 0) {
			AbilityHelpers.sound(player, SoundEvents.BEACON_AMBIENT, 0.25f, 0.8f + Math.min(1.2f, held / 40f));
		}
		if (held >= suit.chargeHoldTicks() && !s.chargeReadyPinged) {
			s.chargeReadyPinged = true;
			level.sendParticles(ParticleTypes.END_ROD, muzzle.x, muzzle.y, muzzle.z, 12, 0.1, 0.1, 0.1, 0.05);
			AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.7f);
		}
	}

	private static void repulsorBlast(ServerPlayer player, IronManSuit suit) {
		if (!requireChest(player, suit) || !cooldownReady(player, suit.id(), REPULSOR_BLAST)) {
			return;
		}
		if (!pay(player, suit, suit.repulsorTapEnergy())) {
			return;
		}
		fireRepulsor(player, suit.repulsorDamage(), false, 24.0);
		triggerCooldown(player, suit.id(), REPULSOR_BLAST, suit.repulsorTapCooldownTicks());
	}

	private static void chargedRepulsor(ServerPlayer player, IronManSuit suit) {
		if (!requireChest(player, suit)) {
			return;
		}
		if (!TonyStark.abilityReady(player, suit.id(), CHARGED_REPULSOR)) {
			// Charged still cooling down -- don't waste the input, fire an ordinary blast instead.
			repulsorBlast(player, suit);
			return;
		}
		if (!pay(player, suit, suit.chargedRepulsorEnergy())) {
			return;
		}
		fireRepulsor(player, suit.chargedRepulsorDamage(), true, 32.0);
		triggerCooldown(player, suit.id(), CHARGED_REPULSOR, suit.chargedRepulsorCooldownTicks());
	}

	/**
	 * "changes 22": one repulsor blast fired from a bare {@code RepulsorItem} held in the hand -- no
	 * suit, no suit energy, no ability cooldown table. It is deliberately the <em>same</em> shot the
	 * Mark 2 fires (same damage, range, beam, knockback and glass-breaking), routed through the same
	 * {@link #fireRepulsor} the suits use, so a hand repulsor and a Mark 2 repulsor look and behave
	 * identically. Pacing is the item's own use-cooldown, set by the caller.
	 */
	public static void fireHandRepulsor(ServerPlayer player) {
		fireRepulsor(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_2.repulsorDamage(), false, 24.0);
	}

	private static void fireRepulsor(ServerPlayer player, float damage, boolean charged, double range) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(player, look);
		Vec3 origin = player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.35, 0);

		// v0.14.26: a Mark III lock auto-aims the blast
		LivingEntity locked = com.projecthero.mod.ironman.IronManTargeting.lockedWithin(player, range);
		LivingEntity target = locked != null ? locked : AbilityHelpers.raycastEntity(player, range);
		var blockHit = AbilityHelpers.raycastBlock(player, range);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
			if (locked != null) {
				look = end.subtract(origin).normalize();
			}
		} else if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		} else {
			end = origin.add(look.scale(range));
		}

		broadcastBeam(player, origin, end, charged ? 1 : 0);
		com.projecthero.mod.ironman.IronManAbilityFx.play(player, charged ? com.projecthero.mod.ironman.IronManAbilityFx.CHARGED : com.projecthero.mod.ironman.IronManAbilityFx.REPULSOR, charged ? 14 : 10); // v0.14.26 pose
		AbilityHelpers.burst(level, end, ParticleTypes.ELECTRIC_SPARK, charged ? 24 : 12, 0.3);
		AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 1.0f, charged ? 0.7f : 1.4f);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE, charged ? 0.8f : 0.35f, 1.6f);

		if (charged) {
			// Charged Repulsor only breaks blocks when it is actually shot AT a block (no entity in the
			// way) -- and then only a short line of ~3, never a crater, and never anything when shot at
			// a mob (spec "changes 9").
			if (target == null && blockHit.getType() == HitResult.Type.BLOCK) {
				IronManBlockBreak.breakLine(level, player, blockHit.getBlockPos(), look, 3);
			}
		} else {
			IronManBlockBreak.breakGlassAlong(level, player, origin, end);
		}

		if (target != null) {
			AbilityHelpers.hurt(player, target, damage);
			AbilityHelpers.knockbackFrom(target, player.position(), charged ? 2.4 : 1.1);
		}
	}

	// ---------------- Repulsor Barrier (slot 2 / G) ----------------

	/** Key-down on slot 2: raise the barrier and keep it up for as long as the key is held. */
	private static void startBarrier(ServerPlayer player, IronManSuit suit) {
		if (!requireChest(player, suit)) {
			return;
		}
		if (TonyStark.state(player).barrierHeld) {
			return; // already up
		}
		if (!cooldownReady(player, suit.id(), REPULSOR_BARRIER)) {
			return;
		}
		if (!pay(player, suit, BARRIER_ACTIVATION_COST)) {
			return;
		}
		// re-fetch: IronManEnergy.spend() swaps the attachment for a copy.
		TonyStark.state(player).barrierHeld = true;
		AbilityHelpers.sound(player, SoundEvents.CONDUIT_ACTIVATE, 1.0f, 1.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.barrier_up"), true);
	}

	/** Key-up on slot 2 (or the suit shutting down): drop the barrier, and -- if it was up -- start the cooldown. */
	public static void stopBarrier(ServerPlayer player, IronManSuit suit, boolean startCooldown) {
		if (!TonyStark.state(player).barrierHeld) {
			return;
		}
		TonyStark.state(player).barrierHeld = false;
		if (startCooldown && suit != null) {
			triggerCooldown(player, suit.id(), REPULSOR_BARRIER, BARRIER_COOLDOWN_TICKS);
		}
		AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.3f);
	}

	/** True while the player's Repulsor Barrier is up -- read by {@link com.projecthero.mod.ironman.IronManDamage}. */
	public static boolean barrierActive(ServerPlayer player) {
		return TonyStark.state(player).barrierHeld;
	}

	/** Per-tick while the barrier is held: drain energy, deflect incoming projectiles, draw the shield disc. */
	public static void tickBarrier(ServerPlayer player, IronManSuit suit) {
		if (!TonyStark.state(player).barrierHeld) {
			return;
		}
		long now = player.level().getGameTime();
		String suitId = suit.id();
		if (!IronManArmor.hasChestplate(player, suitId)
				|| !IronManEnergy.spend(player, suitId, BARRIER_ENERGY_PER_TICK * suit.energyCostMultiplier())) {
			TonyStark.state(player).barrierHeld = false;
			triggerCooldown(player, suitId, REPULSOR_BARRIER, BARRIER_COOLDOWN_TICKS);
			AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.2f);
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		boolean fullBody = suit.fullBodyShield();
		com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.BARRIER); // v0.14.26 pose + shield model
		Vec3 centre = fullBody
				? player.position().add(0, player.getBbHeight() * 0.5, 0)
				: player.position().add(0, player.getBbHeight() * 0.55, 0).add(look.scale(1.1));
		Vec3 right = rightOf(player, look);
		Vec3 up = right.cross(look).normalize();

		if (now % 2 == 0) {
			if (fullBody) {
				// "changes 16": a full sphere around the player, not a disc in front
				double rr = 1.15;
				for (int i = 0; i < 14; i++) {
					double a = i / 14.0 * Math.PI * 2 + now * 0.05;
					double b = (i % 5) / 5.0 * Math.PI - Math.PI / 2;
					Vec3 p = centre.add(Math.cos(a) * Math.cos(b) * rr, Math.sin(b) * rr, Math.sin(a) * Math.cos(b) * rr);
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			} else {
				// hex-disc of particles facing the way the player looks
				for (int i = 0; i < 10; i++) {
					double a = i / 10.0 * Math.PI * 2 + now * 0.06;
					double r = 0.95;
					Vec3 p = centre.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}

		// deflect projectiles heading for the player through the shield arc
		for (var proj : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
				player.getBoundingBox().inflate(3.0))) {
			if (proj.getOwner() == player || proj.isRemoved()) {
				continue;
			}
			Vec3 toPlayer = player.position().subtract(proj.position()).normalize();
			Vec3 vel = proj.getDeltaMovement();
			if (vel.lengthSqr() < 1.0E-4 || vel.normalize().dot(toPlayer) < 0.2) {
				continue; // not actually incoming
			}
			if (!fullBody && look.dot(proj.position().subtract(player.position()).normalize()) < 0.1) {
				continue; // behind a front-only shield
			}
			proj.setDeltaMovement(vel.scale(-0.6));
			if (proj instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile fb) {
				fb.setDeltaMovement(vel.scale(-0.8));
			}
			proj.hasImpulse = true;
			level.sendParticles(ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
			level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);
		}
	}

	// ---------------- micro missiles ----------------

	/** Ticks between successive missiles in a Micro-Missiles volley ("changes 18"). */
	private static final int MICRO_MISSILE_STAGGER_TICKS = 4;

	private static void microMissiles(ServerPlayer player, IronManSuit suit) {
		if (!requireHelmet(player, suit)) {
			return;
		}
		if (suit.missileCount() <= 0) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_missiles"), true);
			return;
		}
		if (!cooldownReady(player, suit.id(), MICRO_MISSILES)) {
			return;
		}
		if (TonyStark.state(player).pendingMissiles > 0) {
			return; // a volley is still launching
		}
		if (!pay(player, suit, suit.missileEnergyCost())) {
			return;
		}
		// "changes 18": fire the volley ONE missile at a time (see tickMicroMissiles) so each missile
		// lands and blasts on its own i-frame window instead of the whole volley hitting at once for a
		// single 8-damage tick.
		TonyStarkState s = TonyStark.state(player);
		s.pendingMissiles = suit.missileCount();
		com.projecthero.mod.ironman.IronManAbilityFx.play(player, com.projecthero.mod.ironman.IronManAbilityFx.MISSILES, 30); // v0.14.26 pose
		s.pendingMissileNextTick = player.level().getGameTime();
		s.pendingMissileSuit = suit.id();
		s.pendingMissileHoming = false;
		s.pendingMissileTargetId = -1;
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
		triggerCooldown(player, suit.id(), MICRO_MISSILES, 8 * 20);
	}

	/** Per-tick from {@link com.projecthero.mod.ironman.IronManSuitTicker}: launch the next missile of a
	 *  staggered Micro-Missiles volley when its timer is up ("changes 18"). */
	public static void tickMicroMissiles(ServerPlayer player, IronManSuit suit) {
		TonyStarkState s = TonyStark.state(player);
		if (s.pendingMissiles <= 0) {
			return;
		}
		if (!suit.id().equals(s.pendingMissileSuit) || !IronManArmor.hasHelmet(player, suit.id())) {
			s.pendingMissiles = 0;
			s.pendingMissileSuit = "";
			s.pendingMissileHoming = false;
			s.pendingMissileTargetId = -1;
			return;
		}
		long now = player.level().getGameTime();
		if (now < s.pendingMissileNextTick) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 shoulder = player.getEyePosition().add(0, 0.15, 0);
		Vec3 look = player.getLookAngle();
		Vec3 dir = look.add((level.random.nextDouble() - 0.5) * 0.12, (level.random.nextDouble() - 0.5) * 0.12,
				(level.random.nextDouble() - 0.5) * 0.12).normalize();
		IronManMissileEntity missile = new IronManMissileEntity(level, player, dir.scale(1.2))
				.withDamage(suit.missileDamage(), suit.missileDamage() * 0.6f)
				.withBlastRadius(2.0f);
		// v0.14.26: with a Mark III lock, every micro missile homes onto the locked target
		LivingEntity tsLock = com.projecthero.mod.ironman.IronManTargeting.locked(player);
		if (!s.pendingMissileHoming && tsLock != null) {
			missile.withHoming();
			missile.withTarget(tsLock);
		}
		if (s.pendingMissileHoming) {
			// v0.14.21 round two: Homing Missiles -- fan out a little, then steer onto the locked target (or, if it
			// is gone, the nearest hostile in flight)
			net.minecraft.world.entity.Entity locked = s.pendingMissileTargetId >= 0 ? level.getEntity(s.pendingMissileTargetId) : null;
			missile.withHoming();
			if (locked instanceof LivingEntity le && le.isAlive()) {
				missile.withTarget(le);
			}
			Vec3 up = new Vec3(0, 1, 0);
			Vec3 side = look.cross(up).lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : look.cross(up).normalize();
			double spread = (s.pendingMissiles % 2 == 0 ? 1 : -1) * 0.35;
			dir = look.add(side.scale(spread)).add(0, 0.12, 0).normalize();
			missile.setDeltaMovement(dir.scale(1.0));
		}
		missile.setPos(shoulder.x + dir.x, shoulder.y + dir.y, shoulder.z + dir.z);
		level.addFreshEntity(missile);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.6f, 1.5f);
		s.pendingMissiles--;
		s.pendingMissileNextTick = now + MICRO_MISSILE_STAGGER_TICKS;
		if (s.pendingMissiles <= 0) {
			s.pendingMissileSuit = "";
			s.pendingMissileHoming = false;
			s.pendingMissileTargetId = -1;
		}
	}

	/**
	 * v0.14.21 round two: Homing Missiles (Mark VII weapon wheel). Four missiles, launched one every
	 * {@link #MICRO_MISSILE_STAGGER_TICKS} like a Micro-Missiles volley and costing the same energy, locked on to
	 * {@link #homingTarget}. With nothing to lock on to they still fire, homing on the nearest hostile in flight.
	 */
	private static void homingMissiles(ServerPlayer player, IronManSuit suit) {
		if (!requireHelmet(player, suit)) {
			return;
		}
		if (!cooldownReady(player, suit.id(), HOMING_MISSILES)) {
			return;
		}
		if (TonyStark.state(player).pendingMissiles > 0) {
			return; // a volley is still launching
		}
		if (!pay(player, suit, suit.missileEnergyCost())) {
			return;
		}
		LivingEntity target = homingTarget(player);
		TonyStarkState s = TonyStark.state(player);
		s.pendingMissiles = HOMING_MISSILE_COUNT;
		com.projecthero.mod.ironman.IronManAbilityFx.play(player, com.projecthero.mod.ironman.IronManAbilityFx.MISSILES, 30); // v0.14.26 pose
		s.pendingMissileNextTick = player.level().getGameTime();
		s.pendingMissileSuit = suit.id();
		s.pendingMissileHoming = true;
		s.pendingMissileTargetId = target == null ? -1 : target.getId();
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
		if (target != null) {
			AbilityHelpers.sound(player, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, 1.8f);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.homing_locked",
					target.getDisplayName()), true);
		} else {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.homing_no_lock"), true);
		}
		triggerCooldown(player, suit.id(), HOMING_MISSILES, 8 * 20);
	}

	/**
	 * What a Homing Missiles volley locks on to: the living thing the crosshair is directly on (if
	 * {@link com.projecthero.mod.combat.HeroTargets#canHarm} allows hitting it -- an aimed shot), otherwise the
	 * {@link com.projecthero.mod.combat.HeroTargets#isHostile hostile} closest to the crosshair inside a
	 * {@link #HOMING_CONE_DEG} cone within {@link #HOMING_RANGE} blocks. Null if there is none.
	 */
	public static LivingEntity homingTarget(net.minecraft.world.entity.player.Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(HOMING_RANGE));
		net.minecraft.world.phys.AABB box = player.getBoundingBox().expandTowards(look.scale(HOMING_RANGE)).inflate(1.0);
		net.minecraft.world.phys.EntityHitResult aimed = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
				player.level(), player, eye, end, box,
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator()
						&& com.projecthero.mod.combat.HeroTargets.canHarm(player, le), 0.3f);
		if (aimed != null && aimed.getEntity() instanceof LivingEntity hit) {
			net.minecraft.world.phys.BlockHitResult wall = player.level().clip(new net.minecraft.world.level.ClipContext(eye,
					hit.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			if (wall.getType() == HitResult.Type.MISS || wall.getLocation().distanceToSqr(eye) >= hit.distanceToSqr(eye) - 1.0) {
				return hit;
			}
		}
		double cosCone = Math.cos(Math.toRadians(HOMING_CONE_DEG));
		LivingEntity best = null;
		double bestCos = cosCone;
		for (LivingEntity e : com.projecthero.mod.combat.HeroTargets.hostiles(player.level(), player,
				com.projecthero.mod.combat.HeroTargets.around(eye, HOMING_RANGE))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist > HOMING_RANGE || dist < 1.0E-3 || !e.isAlive()) {
				continue;
			}
			double cos = to.scale(1.0 / dist).dot(look);
			if (cos >= bestCos) {
				bestCos = cos;
				best = e;
			}
		}
		return best;
	}

	// ---------------- Unibeam (continuous) ----------------

	/** v0.14.27: each suit's total Unibeam channel cost ({@link IronManSuit#unibeamTotalEnergy()}). */
	private static float unibeamTotalEnergy(IronManSuit suit) {
		return suit.unibeamTotalEnergy();
	}

	private static void startUnibeam(ServerPlayer player, IronManSuit suit) {
		if (!requireChest(player, suit)) {
			return;
		}
		TonyStarkState s = TonyStark.state(player);
		if (s.unibeamUntil > player.level().getGameTime()) {
			return; // already firing
		}
		if (!cooldownReady(player, suit.id(), UNIBEAM)) {
			return;
		}
		float totalEnergy = unibeamTotalEnergy(suit);
		if (!canPay(player, suit, totalEnergy / (float) suit.unibeamChannelTicks())) {
			noEnergy(player, totalEnergy * suit.energyCostMultiplier());
			return;
		}
		s.unibeamUntil = player.level().getGameTime() + suit.unibeamChannelTicks();
		AbilityHelpers.sound(player, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.unibeam_firing"), true);
	}

	/** Called every tick from {@link com.projecthero.mod.ironman.IronManSuitTicker} while the beam is up. */
	public static void tickUnibeam(ServerPlayer player, IronManSuit suit) {
		long unibeamUntil = TonyStark.state(player).unibeamUntil;
		long now = player.level().getGameTime();
		if (unibeamUntil == 0L) {
			return;
		}
		String suitId = suit.id();
		float perTick = unibeamTotalEnergy(suit) / (float) suit.unibeamChannelTicks();
		if (now >= unibeamUntil || !IronManArmor.hasChestplate(player, suitId)
				|| !IronManEnergy.spend(player, suitId, perTick * suit.energyCostMultiplier())) {
			// re-fetch after the spend (which swaps the attachment) before clearing the flag
			TonyStark.state(player).unibeamUntil = 0L;
			triggerCooldown(player, suitId, UNIBEAM, suit.unibeamCooldownTicks());
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 chest0 = player.position().add(0, player.getBbHeight() * 0.62, 0);
		Vec3 dir = com.projecthero.mod.ironman.IronManTargeting.aim(player, chest0, player.getLookAngle(), 28); // v0.14.26 auto-aim
		Vec3 chest = chest0.add(dir.scale(0.4));
		Vec3 end = chest.add(dir.scale(28));
		com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.UNIBEAM); // v0.14.26 pose

		// beam VFX only every other tick -- the client line was the main FPS cost
		if (now % 2 == 0) {
			broadcastBeam(player, chest, end, 2);
		}

		// block-breaking parity with Laser Vision's ultimate: at most one soft block, and only every
		// ~half-second, at the point the beam actually hits -- never a carved tunnel.
		if (now % 10 == 0) {
			var bh = AbilityHelpers.raycastBlock(player, 28.0);
			if (bh.getType() == HitResult.Type.BLOCK) {
				BlockPos bp = bh.getBlockPos();
				float speed = level.getBlockState(bp).getDestroySpeed(level, bp);
				if (speed >= 0f && speed < 3.0f) {
					level.destroyBlock(bp, true, player);
				}
			}
		}

		for (LivingEntity e : AbilityHelpers.enemiesAround(player, chest.add(dir.scale(12)), 12.0)) {
			if (e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(chest).normalize().dot(dir) > 0.9) {
				AbilityHelpers.hurt(player, e, suit.unibeamDamagePerTick());
				e.igniteForSeconds(2);
			}
		}
		if (now % 10 == 0) {
			AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE, 0.4f, 0.7f);
		}
	}

	// ---------------- Mark 1: Strong Punch (R) ----------------

	private static void strongPunch(ServerPlayer player, IronManSuit suit) {
		if (!cooldownReady(player, suit.id(), STRONG_PUNCH)) {
			return;
		}
		if (!pay(player, suit, PUNCH_ENERGY_COST)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, PUNCH_RANGE);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 fist = player.getEyePosition().add(player.getLookAngle().scale(1.2)).add(0, -0.4, 0);
		com.projecthero.mod.ironman.IronManAbilityFx.play(player, com.projecthero.mod.ironman.IronManAbilityFx.PUNCH, 10); // v0.14.26 pose + shockwave
		AbilityHelpers.burst(level, fist, ParticleTypes.CRIT, 10, 0.2);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.7f);
		if (target != null) {
			AbilityHelpers.hurt(player, target, PUNCH_DAMAGE);
			AbilityHelpers.knockbackFrom(target, player.position(), 1.8);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5,
					target.getZ(), 1, 0, 0, 0, 0);
		}
		triggerCooldown(player, suit.id(), STRONG_PUNCH, PUNCH_COOLDOWN_TICKS);
	}

	// ---------------- Mark 1: Flamethrower (G) ----------------

	/**
	 * Called every tick from {@link com.projecthero.mod.ironman.IronManSuitTicker} while
	 * {@code TonyStarkState.flamethrowerHeld} is true. The stream / fire-catching / hit-cone mechanic
	 * is the same one {@code PyrokinesisHandlers}' "flamethrower" ability uses; the only real
	 * difference is the fuel source -- suit energy instead of Pyrokinesis's own heat-buildup gauge, per
	 * "changes 12" ("make it function the same ... but make it drain energy as well").
	 */
	public static void tickFlamethrower(ServerPlayer player, IronManSuit suit) {
		TonyStarkState s = TonyStark.state(player);
		if (!s.flamethrowerHeld) {
			return;
		}
		if (!IronManArmor.hasChestplate(player, suit.id())
				|| !IronManEnergy.spend(player, suit.id(), suit.flamethrowerEnergyPerSecond() / 20f * suit.energyCostMultiplier())) {
			TonyStark.state(player).flamethrowerHeld = false;
			if (!IronManArmor.hasChestplate(player, suit.id())) {
				return;
			}
			noEnergy(player);
			return;
		}
		// Heat gauge ("changes 14"): overheat cuts the stream, same as Pyrokinesis's flamethrower.
		setFlamethrowerHeat(player, TonyStark.state(player).flamethrowerHeat + suit.flamethrowerHeatPerSecond() / 20f);
		markFlamethrowerUsed(player, suit);
		if (TonyStark.state(player).flamethrowerHeat >= flamethrowerMaxHeat(suit)) {
			TonyStark.state(player).flamethrowerHeld = false;
			player.displayClientMessage(
					Component.translatable("message.projecthero.ironman.flamethrower_overheated"), true);
			return;
		}
					com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.FLAME); // v0.14.26 pose + flame cone
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 origin = player.getEyePosition();

		for (LivingEntity e : AbilityHelpers.enemiesAround(player, origin.add(look.scale(2.5)), 3.0)) {
			Vec3 to = e.position().subtract(origin).normalize();
			if (to.dot(look) > 0.6) {
				// v0.14.27: a suit with a damage-per-second figure (Mark 1: 8/s) lands half of it per i-frame window
				AbilityHelpers.hurt(player, e, AbilityHelpers.fire(player), suit.flamethrowerDamagePerSecond() > 0f
						? suit.flamethrowerDamagePerSecond() * 0.5f
						: "mark_1".equals(suit.id()) ? MARK_1_FLAMETHROWER_DAMAGE : FLAMETHROWER_DAMAGE);
				e.setRemainingFireTicks(80);
			}
		}

		// The stream only reaches as far as the first wall/floor it meets.
		var bhr = AbilityHelpers.raycastBlock(player, FLAMETHROWER_REACH);
		double streamLen = bhr.getType() == HitResult.Type.BLOCK
				? origin.distanceTo(bhr.getLocation()) : FLAMETHROWER_REACH;
		// v0.14.22, explicit user request: the flame leaves the suit wrist, not the hand / eyes, and
		// converges on the same aim point the damage cone uses.
		Vec3 nozzle = flamethrowerNozzle(player, suit);
		Vec3 aimEnd = origin.add(look.scale(streamLen));
		Vec3 stream = aimEnd.subtract(nozzle);
		double nozzleLen = Math.max(0.5, stream.length());
		Vec3 streamDir = stream.scale(1.0 / nozzleLen);
		for (double d = 0.5; d <= streamLen + 0.01; d += 0.5) {
			Vec3 pt = origin.add(look.scale(d));
			Vec3 fx = nozzle.add(streamDir.scale(nozzleLen * d / streamLen));
			level.sendParticles(ParticleTypes.FLAME, fx.x, fx.y, fx.z, 3, 0.12 * d, 0.12 * d, 0.12 * d, 0.02);
			if (player.tickCount % 2 == 0 && flamethrowerFireOk()) {
				BlockPos bp = BlockPos.containing(pt);
				boolean nearSurface = !level.getBlockState(bp.below()).isAir() || !level.getBlockState(bp.above()).isAir()
						|| !level.getBlockState(bp.north()).isAir() || !level.getBlockState(bp.south()).isAir()
						|| !level.getBlockState(bp.east()).isAir() || !level.getBlockState(bp.west()).isAir();
				if (nearSurface) {
					placeStreamFire(level, bp, 100);
				}
			}
		}
		if (bhr.getType() == HitResult.Type.BLOCK && flamethrowerFireOk()) {
			placeStreamFire(level, bhr.getBlockPos().relative(bhr.getDirection()), 120);
		}
		if (player.tickCount % 4 == 0) {
			AbilityHelpers.sound(player, SoundEvents.BLAZE_BURN, 0.5f, 1.1f);
		}
	}

	/**
	 * "changes 22": a flamethrower <b>sets what it is pointed at on fire</b>. Full stop -- that is the
	 * ability, not an optional extra.
	 *
	 * <p>It used to be gated on {@code HeroConfig.abilityFireSpread}, which defaults to {@code false}.
	 * That flag exists to stop <em>incidental</em> fire (a fireball's trail, an explosion's scorch)
	 * from burning a base down, and applying it here meant the flamethrower quietly never lit anything
	 * in a default install -- the surface-fire code below has been present and dead the whole time.
	 * Only {@code abilityTerrainDamage} (via {@code canGrief}) still gates it, so a server that has
	 * genuinely turned off all world modification is still respected.
	 */
	private static boolean flamethrowerFireOk() {
		return com.projecthero.mod.hero.power.AbilityHelpers.canGrief();
	}

	/**
	 * Lay one temporary fire on a surface the stream washed over. Air-only, matching
	 * {@code PyrokinesisHandlers.placeStreamFire}: {@code TempBlocks} would otherwise happily accept
	 * any {@code canBeReplaced()} block, which means briefly turning water and tall grass into fire.
	 * {@code TempBlocks} restores whatever was there when the TTL runs out.
	 */
	private static void placeStreamFire(ServerLevel level, BlockPos pos, int ttl) {
		if (flamethrowerFireOk() && level.getBlockState(pos).isAir()) {
			com.projecthero.mod.hero.power.TempBlocks.place(level, pos,
					net.minecraft.world.level.block.BaseFireBlock.getState(level, pos), ttl);
		}
	}

	// ---------------- Mark 1 / Mark 2: Rocket ----------------

	/**
	 * v0.14.22: where the flamethrower stream starts -- the main-arm wrist, a little behind and below
	 * where {@link AbilityHelpers#handPosition} puts the held item, scaled with the suit (Mark 1 is 1.25x).
	 */
	static Vec3 flamethrowerNozzle(ServerPlayer player, IronManSuit suit) {
		float scale = suit == null ? 1f : suit.scale();
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		if (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) {
			right = right.scale(-1);
		}
		return player.getEyePosition().add(look.scale(0.15 * scale)).add(right.scale(0.42 * scale)).add(0, -0.6 * scale, 0);
	}

	private static void rocket(ServerPlayer player, IronManSuit suit) {
		boolean mark1 = "mark_1".equals(suit.id());
		int cooldownTicks = mark1 ? MARK_1_ROCKET_COOLDOWN_TICKS : ROCKET_COOLDOWN_TICKS;
		if (!requireHelmet(player, suit) || !cooldownReady(player, suit.id(), ROCKET)) {
			return;
		}
		float cost = mark1 ? MARK_1_ROCKET_ENERGY_COST
				: "mark_2".equals(suit.id()) ? MARK_2_ROCKET_ENERGY_COST : ROCKET_ENERGY_COST;
		if (!pay(player, suit, cost)) {
			return;
		}
		float damage = mark1 ? MARK_1_ROCKET_DAMAGE : ROCKET_DAMAGE;
		ServerLevel level = (ServerLevel) player.level();
		Vec3 shoulder = player.getEyePosition().add(0, 0.15, 0);
		// "changes 14": the rocket is dumb-fire -- it flies exactly where the player aimed, no tracking,
		// and makes a sizeable AoE blast where it lands. v0.14.26: a Mark III lock aims it.
		Vec3 dir = com.projecthero.mod.ironman.IronManTargeting.aim(player, shoulder, player.getLookAngle(), 100);
		com.projecthero.mod.ironman.entity.IronManMissileEntity missile =
				new com.projecthero.mod.ironman.entity.IronManMissileEntity(level, player, dir.scale(1.4))
						.withDamage(damage, damage * 0.7f)
						.withBlastRadius(3.0f)
						// v0.11.13, explicit user request: the Mark 1/2 rocket now actually breaks blocks
						// (TNT-style), unlike every other missile this entity type is shared with.
						.withBreaksBlocks();
						com.projecthero.mod.ironman.IronManAbilityFx.play(player, com.projecthero.mod.ironman.IronManAbilityFx.ROCKET, 14); // v0.14.26 pose
		missile.setPos(shoulder.x + dir.x, shoulder.y + dir.y, shoulder.z + dir.z);
		level.addFreshEntity(missile);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.9f);
		triggerCooldown(player, suit.id(), ROCKET, cooldownTicks);
	}

	// ---------------- Mark 2: Flares (X) / Shift+X ----------------

	/**
	 * v0.14.27: Mark 2 X -- flares ({@link IronManFlares#fire}). Shift+X lights the block you are looking at like
	 * flint and steel (1 energy); while flying, Shift+X instead gives a 30 s supersonic boost (50 energy).
	 */
	private static void flareSlot(ServerPlayer player, IronManSuit suit) {
		if (!player.isShiftKeyDown()) {
			IronManFlares.fire(player, false, FLARE_COOLDOWN_TICKS);
			return;
		}
		if (IronManFlight.isFlying(player)) {
			IronManFlares.supersonicBoost(player, MARK_2_BOOST_TICKS, MARK_2_BOOST_ENERGY);
			return;
		}
		ignite(player, suit);
	}

	/** v0.14.27: flint and steel from the gauntlet -- fire on the face of the block the wearer looks at. */
	public static boolean ignite(ServerPlayer player, IronManSuit suit) {
		var hit = AbilityHelpers.raycastBlock(player, 5.0);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		BlockPos firePos = hit.getBlockPos().relative(hit.getDirection());
		net.minecraft.world.level.block.state.BlockState target = level.getBlockState(hit.getBlockPos());
		boolean lit;
		if (net.minecraft.world.level.block.CampfireBlock.canLight(target)
				|| net.minecraft.world.level.block.CandleBlock.canLight(target)
				|| net.minecraft.world.level.block.CandleCakeBlock.canLight(target)) {
			if (!pay(player, suit, IGNITE_ENERGY)) {
				return false;
			}
			level.setBlock(hit.getBlockPos(), target.setValue(
					net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), 11);
			lit = true;
		} else if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, firePos, hit.getDirection())) {
			if (!pay(player, suit, IGNITE_ENERGY)) {
				return false;
			}
			level.setBlock(firePos, net.minecraft.world.level.block.BaseFireBlock.getState(level, firePos), 11);
			lit = true;
		} else {
			lit = false;
		}
		if (lit) {
			level.playSound(null, firePos, SoundEvents.FLINTANDSTEEL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f,
					level.random.nextFloat() * 0.4f + 0.8f);
		}
		return lit;
	}

	// ---------------- Mark 1 / Mark 2: mob-highlight toggle (V) ----------------

	private static void toggleMobHighlight(ServerPlayer player, IronManSuit suit) {
		if (!requireHelmet(player, suit)) {
			return;
		}
		boolean turningOn = !TonyStark.state(player).mobHighlightOn;
		// v0.14.27: a suit's switch-on cost and auto-off timer are builder data (Mark 1 / Mark 2: 10 energy, 20 s);
		// switching it off is always free.
		if (turningOn && suit.mobHighlightEnergy() > 0f && !pay(player, suit, suit.mobHighlightEnergy())) {
			return;
		}
		boolean timed = suit.mobHighlightDurationTicks() > 0;
		TonyStarkState s = TonyStark.state(player).copy();
		s.mobHighlightOn = turningOn;
		if (timed) {
			String key = suit.id() + "/" + MOB_HIGHLIGHT_UNTIL_KEY;
			if (turningOn) {
				s.abilityReadyAt.put(key, player.level().getGameTime() + suit.mobHighlightDurationTicks());
			} else {
				s.abilityReadyAt.remove(key);
			}
		}
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, s);
		AbilityHelpers.sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, s.mobHighlightOn ? 1.8f : 1.2f);
		player.displayClientMessage(Component.translatable(s.mobHighlightOn
				? "message.projecthero.ironman.mob_highlight_on" : "message.projecthero.ironman.mob_highlight_off"), true);
	}

	/** Absolute game-time Mark 1's mob-highlight toggle expires, or 0 if not active/not Mark 1. Works
	 *  from either side (server or the client's own synced state) since it only reads the state object. */
	public static long mark1MobHighlightUntil(TonyStarkState state, String suitId) {
		return state.abilityReadyAt.getOrDefault(suitId + "/" + MOB_HIGHLIGHT_UNTIL_KEY, 0L);
	}

	/**
	 * "changes 17": public entry point for the Mark 7 weapon-wheel "Entity Glow" sector -- flips the
	 * same {@code mobHighlightOn} flag the V-slot toggle uses, with its own coloured-glow message.
	 */
	public static void toggleEntityGlowFromWheel(ServerPlayer player) {
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || !IronManArmor.hasHelmet(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_helmet"), true);
			return;
		}
		TonyStarkState s = TonyStark.state(player).copy();
		s.mobHighlightOn = !s.mobHighlightOn;
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, s);
		AbilityHelpers.sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, s.mobHighlightOn ? 1.8f : 1.2f);
		player.displayClientMessage(Component.translatable(s.mobHighlightOn
				? "message.projecthero.ironman.entity_glow_on" : "message.projecthero.ironman.entity_glow_off"), true);
	}

	/**
	 * Force the mob-highlight toggle off (called from {@link com.projecthero.mod.ironman.IronManSuitTicker}
	 * when the suit comes off or loses power) so it can never stay latched on with no way to see it,
	 * and the wearer has to deliberately re-enable it. No-ops if it is already off.
	 */
	public static void clearMobHighlight(ServerPlayer player) {
		if (!TonyStark.state(player).mobHighlightOn) {
			return;
		}
		TonyStarkState s = TonyStark.state(player).copy();
		s.mobHighlightOn = false;
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, s);
	}

	// ---------------- Mark 1: flamethrower heat gauge ("changes 14") ----------------

	private static void setFlamethrowerHeat(ServerPlayer player, float value) {
		TonyStarkState s = TonyStark.state(player).copy();
		// upper ceiling is enforced per-suit by the overheat check in tickFlamethrower / trigger; clamp
		// here only against a generous absolute maximum (Mark 7's bar is 1.5x the base).
		s.flamethrowerHeat = Math.max(0f, Math.min(FLAMETHROWER_MAX_HEAT * 2f, value));
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, s);
	}

	/**
	 * Bleed the Mark 1 flamethrower heat gauge back toward zero while the stream is not being held.
	 * Called every tick from {@link com.projecthero.mod.ironman.IronManSuitTicker}.
	 */
	public static void ventFlamethrowerHeat(ServerPlayer player) {
		TonyStarkState st = TonyStark.state(player);
		float heat = st.flamethrowerHeat;
		if (heat <= 0f) {
			return;
		}
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
		float vent = FLAMETHROWER_HEAT_VENT_PER_TICK;
		if (suit != null) {
			// v0.14.27: the heat only starts seeping away once the stream has been idle for the suit's delay (Mark 1: 3 s)
			Long ventAt = st.abilityReadyAt.get(suit.id() + "/" + FLAMETHROWER_VENT_KEY);
			if (ventAt != null && player.level().getGameTime() < ventAt) {
				return;
			}
			vent = suit.flamethrowerVentPerSecond() / 20f;
		}
		setFlamethrowerHeat(player, heat - vent);
	}

	/** v0.14.27: remember when the stream last burned, so the heat only vents after the suit's idle delay. */
	private static void markFlamethrowerUsed(ServerPlayer player, IronManSuit suit) {
		if (suit.flamethrowerVentDelayTicks() <= 0) {
			return;
		}
		TonyStarkState s = TonyStark.state(player).copy();
		s.abilityReadyAt.put(suit.id() + "/" + FLAMETHROWER_VENT_KEY,
				player.level().getGameTime() + suit.flamethrowerVentDelayTicks());
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, s);
	}

	// ---------------- Mark 1: timed flight (X) ----------------

	private static void timedFlight(ServerPlayer player, IronManSuit suit) {
		if (!IronManArmor.canOperate(player)
				|| !IronManArmor.isPieceWorn(player, net.minecraft.world.entity.EquipmentSlot.FEET, suit.id())) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_boots"), true);
			return;
		}
		TonyStarkState s = TonyStark.state(player);
		if (s.timedFlightUntil > player.level().getGameTime()) {
			// v0.14.27: pressing X again switches the burst off
			endTimedFlight(player, suit.id());
			IronManFlight.setFlying(player, false);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.timed_flight_offline"), true);
			return;
		}
		if (s.abilityReadyAt.containsKey(suit.id() + "/" + FLIGHT_BURST_PENDING_KEY)) {
			return; // a Shift+X launch is already about to switch it on
		}
		if (!cooldownReady(player, suit.id(), TIMED_FLIGHT)) {
			return; // 13 s cooldown after the last burst ended
		}
		if (!pay(player, suit, TIMED_FLIGHT_ACTIVATION_COST)) {
			return;
		}
		startTimedFlight(player, suit, true);
	}

	/** Switch the burst on (the cost is already paid). {@code hop} = the little take-off pop from the ground. */
	private static void startTimedFlight(ServerPlayer player, IronManSuit suit, boolean hop) {
		TonyStark.setTimedFlightUntil(player, player.level().getGameTime() + TIMED_FLIGHT_TICKS);
		IronManFlight.setFlying(player, true);
		// A real "burst": pop the player up off the ground so the flight visibly launches (matches the
		// feel of Pyrokinesis / Geokinesis timed flight rather than just quietly enabling creative fly).
		if (hop) {
			player.setDeltaMovement(player.getDeltaMovement().x, 0.6, player.getDeltaMovement().z);
			player.hurtMarked = true;
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
		}
		player.fallDistance = 0.0f;
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 24, 0.3, 0.1, 0.3, 0.05);
		AbilityHelpers.sound(player, SoundEvents.FIRECHARGE_USE, 1.0f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.timed_flight_online"), true);
	}

	/**
	 * v0.14.27, Mark 1 Shift+X: throw the wearer far along the look (50 energy), and 3 s later the flight burst
	 * switches on by itself ({@link #tickPendingFlightBurst}).
	 */
	private static void launchFlightBurst(ServerPlayer player, IronManSuit suit) {
		if (!IronManArmor.isPieceWorn(player, net.minecraft.world.entity.EquipmentSlot.FEET, suit.id())) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_boots"), true);
			return;
		}
		TonyStarkState s = TonyStark.state(player);
		long now = player.level().getGameTime();
		if (s.timedFlightUntil > now || s.abilityReadyAt.containsKey(suit.id() + "/" + FLIGHT_BURST_PENDING_KEY)) {
			return;
		}
		if (!cooldownReady(player, suit.id(), TIMED_FLIGHT)) {
			return;
		}
		if (!pay(player, suit, TIMED_FLIGHT_ACTIVATION_COST)) {
			return;
		}
		TonyStarkState c = TonyStark.state(player).copy();
		c.abilityReadyAt.put(suit.id() + "/" + FLIGHT_BURST_PENDING_KEY, now + LAUNCH_BURST_DELAY_TICKS);
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, c);
		Vec3 look = player.getLookAngle();
		Vec3 v = look.scale(LAUNCH_SPEED).add(0, 0.6, 0);
		player.setDeltaMovement(v);
		player.hurtMarked = true;
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
		player.fallDistance = 0f;
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 30, 0.3, 0.1, 0.3, 0.08);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.4, 0.1, 0.4, 0.05);
		AbilityHelpers.sound(player, SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.flight_launch"), true);
	}

	/** v0.14.27: per tick -- switch the burst on once a Shift+X launch's 3 s are up. */
	public static void tickPendingFlightBurst(ServerPlayer player, IronManSuit suit) {
		String key = suit.id() + "/" + FLIGHT_BURST_PENDING_KEY;
		Long at = TonyStark.state(player).abilityReadyAt.get(key);
		if (at == null) {
			return;
		}
		player.fallDistance = 0f;
		if (player.level().getGameTime() < at) {
			return;
		}
		TonyStarkState c = TonyStark.state(player).copy();
		c.abilityReadyAt.remove(key);
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, c);
		if (IronManArmor.isPieceWorn(player, net.minecraft.world.entity.EquipmentSlot.FEET, suit.id())) {
			startTimedFlight(player, suit, false);
		}
	}

	/** v0.14.27: drop any queued Shift+X burst (the suit came off / lost power before the 3 s were up). */
	public static void clearPendingFlightBurst(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		boolean any = false;
		for (String k : s.abilityReadyAt.keySet()) {
			if (k.endsWith("/" + FLIGHT_BURST_PENDING_KEY)) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		TonyStarkState c = s.copy();
		c.abilityReadyAt.keySet().removeIf(k -> k.endsWith("/" + FLIGHT_BURST_PENDING_KEY));
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.TONY_STARK_STATE, c);
	}

	/** v0.14.27: true while a Shift+X launch is waiting to switch the burst on. */
	public static boolean flightBurstPending(TonyStarkState state, String suitId) {
		return state.abilityReadyAt.containsKey(suitId + "/" + FLIGHT_BURST_PENDING_KEY);
	}

	/**
	 * End a Mark 1 timed-flight burst -- clears the synced timer and puts the ability on its 13 s
	 * cooldown ("changes 15"). Called from {@link com.projecthero.mod.ironman.IronManFlight} both when the
	 * timer elapses and when the player lands / stores the suit early.
	 */
	public static void endTimedFlight(ServerPlayer player, String suitId) {
		if (TonyStark.state(player).timedFlightUntil == 0L) {
			return;
		}
		TonyStark.setTimedFlightUntil(player, 0L);
		if (suitId != null) {
			TonyStark.triggerCooldown(player, suitId, TIMED_FLIGHT, TIMED_FLIGHT_COOLDOWN_TICKS);
		}
	}

	// ---------------- supersonic flight (weapon-wheel X option, "changes 16") ----------------

	private static void supersonicFlight(ServerPlayer player, IronManSuit suit) {
		// "changes 17": pressing the button again while a burst is running ends it early.
		if (TonyStark.state(player).supersonicUntil > player.level().getGameTime()) {
			endSupersonic(player, suit.id());
			return;
		}
		if (!IronManArmor.isPieceWorn(player, net.minecraft.world.entity.EquipmentSlot.FEET, suit.id())) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_boots"), true);
			return;
		}
		if (!cooldownReady(player, suit.id(), SUPERSONIC_FLIGHT)) {
			return;
		}
		if (!pay(player, suit, SUPERSONIC_ACTIVATION_COST)) {
			return;
		}
		long now = player.level().getGameTime();
		TonyStark.setSupersonicUntil(player, now + SUPERSONIC_TICKS);
		IronManFlight.setFlying(player, true);
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.scale(1.6));
		player.hurtMarked = true;
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
		player.fallDistance = 0f;
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4, player.getZ(), 40, 0.4, 0.4, 0.4, 0.1);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.4f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.BREEZE_SHOOT, 1.2f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.supersonic_online"), true);
		// The 10 s cooldown starts only when the burst ENDS -- see endSupersonic.
	}

	/**
	 * End a supersonic-flight burst ("changes 17"): clears the timer, starts the 10 s cooldown, and
	 * tells the player it disengaged. Called on an early re-press, when the 20 s timer elapses
	 * ({@link com.projecthero.mod.ironman.IronManFlight}), and when the suit shuts down.
	 */
	public static void endSupersonic(ServerPlayer player, String suitId) {
		if (TonyStark.state(player).supersonicUntil == 0L) {
			return;
		}
		TonyStark.setSupersonicUntil(player, 0L);
		if (suitId != null) {
			triggerCooldown(player, suitId, SUPERSONIC_FLIGHT, SUPERSONIC_COOLDOWN_TICKS);
		}
		AbilityHelpers.sound(player, SoundEvents.BREEZE_LAND, 1.0f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.supersonic_offline")
				.withStyle(net.minecraft.ChatFormatting.GRAY), true);
	}

	/** Per-tick while a supersonic burst is active -- strong forward drive, sonic-boom particles.
	 *  Called from {@link com.projecthero.mod.ironman.IronManFlight}. */
	public static double supersonicSpeedCap(ServerPlayer player) {
		return player.level().getGameTime() < TonyStark.state(player).supersonicUntil ? SUPERSONIC_SPEED_MPS : 0.0;
	}

	// ---------------- Mark 4: wrist laser (shift + V) ----------------

	/**
	 * The Mark 4 wrist laser ("changes 15"): a thin, highly destructive red beam -- 20 damage / second
	 * for 4 seconds -- fired by sneaking and pressing the V (mob-highlight) slot. One shot per charge:
	 * it only reloads when the suit is docked back into a Suit Platform. Firing it overloads the armour,
	 * taking every system offline for 15 seconds afterwards.
	 */
	private static void wristLaser(ServerPlayer player, IronManSuit suit) {
		if (!requireChest(player, suit)) {
			return;
		}
		String suitId = suit.id();
		if (TonyStark.state(player).wristLaserUntil > player.level().getGameTime()) {
			return; // already firing
		}
		if (TonyStark.wristLaserSpent(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.wrist_laser_spent")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			return;
		}
		if (!pay(player, suit, WRIST_LASER_ACTIVATION_COST)) {
			return;
		}
		TonyStark.setWristLaserSpent(player, suitId, true);
		TonyStark.setWristLaserUntil(player, player.level().getGameTime() + WRIST_LASER_TICKS);
		AbilityHelpers.sound(player, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.3f);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE, 0.6f, 0.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.wrist_laser_firing")
				.withStyle(net.minecraft.ChatFormatting.RED), true);
	}

	/** Per-tick while the wrist laser is up (called from {@link com.projecthero.mod.ironman.IronManSuitTicker}). */
	public static void tickWristLaser(ServerPlayer player, IronManSuit suit) {
		long until = TonyStark.state(player).wristLaserUntil;
		if (until == 0L) {
			return;
		}
		long now = player.level().getGameTime();
		String suitId = suit.id();
		if (now >= until || !IronManArmor.hasChestplate(player, suitId)) {
			TonyStark.setWristLaserUntil(player, 0L);
			// Firing the laser overloads the armour: everything offline for 15 s.
			TonyStark.setOverloadUntil(player, now + OVERLOAD_TICKS);
			AbilityHelpers.sound(player, SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.4f);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.systems_overloaded")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(player, look);
		Vec3 origin = player.getEyePosition().add(look.scale(0.5)).add(right.scale(0.35)).add(0, -0.35, 0);

		LivingEntity target = AbilityHelpers.raycastEntity(player, WRIST_LASER_RANGE);
		com.projecthero.mod.ironman.IronManAbilityFx.hold(player, com.projecthero.mod.ironman.IronManAbilityFx.LASER); // v0.14.26 pose
		var blockHit = AbilityHelpers.raycastBlock(player, WRIST_LASER_RANGE);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
		} else if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		} else {
			end = origin.add(look.scale(WRIST_LASER_RANGE));
		}
		broadcastBeam(player, origin, end, 3); // kind 3 = thin red laser

		if (target != null) {
			AbilityHelpers.hurt(player, target, WRIST_LASER_DAMAGE_PER_TICK);
			target.igniteForSeconds(1);
		}
		// A thin, genuinely destructive beam -- carve straight through terrain along its length.
		if (now % 2 == 0 && com.projecthero.mod.hero.power.AbilityHelpers.canGrief()) {
			var bh = AbilityHelpers.raycastBlock(player, WRIST_LASER_RANGE);
			if (bh.getType() == HitResult.Type.BLOCK) {
				BlockPos bp = bh.getBlockPos();
				float speed = level.getBlockState(bp).getDestroySpeed(level, bp);
				if (speed >= 0f && speed < 50f) {
					level.destroyBlock(bp, false, player);
				}
			}
		}
		if (now % 4 == 0) {
			AbilityHelpers.sound(player, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 0.6f, 0.5f);
		}
	}

	// ---------------- helpers ----------------

	/**
	 * A stable "player's right" unit vector for placing muzzle / shield geometry.
	 *
	 * <p>The obvious {@code look.cross(UP)} collapses to the zero vector when the player looks straight
	 * up or straight down ({@code Vec3.normalize()} returns ZERO below 1.0E-4), which silently dropped
	 * the repulsor muzzle back onto the eye position and flattened the barrier's particle disc to a
	 * single point at exactly the angles a flying Iron Man looks at most. Falling back to the body's
	 * yaw keeps a well-defined right-hand direction at every pitch.
	 */
	static Vec3 rightOf(ServerPlayer player, Vec3 look) {
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-6) {
			double yaw = Math.toRadians(player.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		}
		return right.normalize();
	}

	private static boolean requireChest(ServerPlayer player, IronManSuit suit) {
		if (IronManArmor.hasChestplate(player, suit.id())) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
		return false;
	}

	private static boolean requireHelmet(ServerPlayer player, IronManSuit suit) {
		if (IronManArmor.hasHelmet(player, suit.id())) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_helmet"), true);
		return false;
	}

	static void broadcastBeam(ServerPlayer player, Vec3 start, Vec3 end, int kind) {
		IronManBeamPayload payload = new IronManBeamPayload(start, end, kind);
		// v0.14.21: not just the shooter's trackers -- anyone near the beam (the target may be far outside tracking range)
		for (ServerPlayer viewer : IronManBeamRecipients.recipients(player, start, end)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	static boolean cooldownReady(ServerPlayer player, String suitId, String abilityId) {
		if (TonyStark.abilityReady(player, suitId, abilityId)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.on_cooldown",
				String.format(java.util.Locale.ROOT, "%.1f",
						TonyStark.abilityCooldownRemaining(player, suitId, abilityId) / 20.0f)), true);
		return false;
	}

	private static void triggerCooldown(ServerPlayer player, String suitId, String abilityId, int ticks) {
		TonyStark.triggerCooldown(player, suitId, abilityId, ticks);
	}

	private static void noEnergy(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_energy"), true);
	}

	/**
	 * "Not enough suit energy" feedback that also tells the player exactly how much the ability needs
	 * ("changes 15") -- called instead of {@link #noEnergy(ServerPlayer)} whenever a spend is refused
	 * for a known cost.
	 */
	static void noEnergy(ServerPlayer player, float required) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.not_enough_energy",
				Math.round(required)).withStyle(net.minecraft.ChatFormatting.RED), true);
	}

	/**
	 * Spend {@code base} energy scaled by the suit's cost multiplier ("changes 16" -- Mark 6/7 are
	 * cheaper to run). On failure, message the real (scaled) cost and return false.
	 */
	private static boolean pay(ServerPlayer player, IronManSuit suit, float base) {
		float cost = base * suit.energyCostMultiplier();
		if (IronManEnergy.spend(player, suit.id(), cost)) {
			return true;
		}
		noEnergy(player, cost);
		return false;
	}

	/** Non-spending "can afford" check, cost scaled by the suit's multiplier. */
	private static boolean canPay(ServerPlayer player, IronManSuit suit, float base) {
		return IronManEnergy.has(player, suit.id(), base * suit.energyCostMultiplier());
	}

	private static void reject(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects")
				.withStyle(net.minecraft.ChatFormatting.RED), true);
	}
}
