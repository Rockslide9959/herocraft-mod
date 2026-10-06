package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.ironman.IronManSounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.4 (explicit user spec): the <b>modern kit</b> the Mark 6 and the Mark 7 share. Slot layout -- R(1) G(2) X(3)
 * Z(4) V(5) C(6):
 * <pre>
 *   R        Repulsor (builder data); Shift+R Repulsor Dash
 *   G        fire the weapon selected on the V wheel:
 *              Shoulder Barrage  6 homing shoulder missiles
 *              Micro-Missiles    the suit's shared micro-missile volley (builder data)
 *              Wrist Laser       a 3 s cutting beam
 *              Flamethrower      held stream (builder heat / burn data)
 *              Rocket            one heavy AoE rocket
 *   Shift+G  Sonic Clap
 *   X        Flares -- or, pressed while flying, a supersonic boost instead
 *   Shift+X  JARVIS scan
 *   Z        Mark 6: Unibeam (hold).  Mark 7: red laser (hold) -- see {@link IronManMark7}; Shift+Z = Unibeam (hold)
 *   V        hold: the weapon wheel (pick on release);  Shift+V hold: the 360-degree Repulsor Shield (energy shield)
 *   C        store suit
 * </pre>
 * Passives (builder data + {@link #tickRegeneration}): Resistance II with the chestplate on, auto-feed, water breathing,
 * targeting + auto-aim, +7 melee, worn integrity repair 1/s, and Regeneration I while the wearer is below full health
 * (chestplate on), which drains 3 energy a second for as long as it is applied. The Arc Reactor Surge is gone (v0.15.4).
 *
 * <p>Each suit keeps its own wheel pick under {@code <suitId>/mk6_weapon_choice} in {@link TonyStarkState#abilityReadyAt}
 * (synced, persistent, no codec change); cooldowns are per suit. Every energy figure here is a base cost -- the suit's
 * {@link IronManSuit#energyCostMultiplier()} (0.6) applies on top, except the flat 3/s Regeneration drain.
 */
public final class IronManMark6 {
	public static final String SUIT_ID = "mark_6";
	/** The suits that run this kit. */
	public static final String[] KIT_SUITS = { SUIT_ID, IronManMark7.SUIT_ID, IronManMark7.MARK_8_ID };

	// slot ability ids (shared by both suits; the Mark 7 swaps its own Z in -- IronManMark7.LASER)
	public static final String WEAPON = "mk6_weapon";
	public static final String FLARES = "mk6_flares";
	public static final String UNIBEAM = "mk6_unibeam";
	public static final String WHEEL = "mk6_wheel";

	// weapon-wheel options (also their per-suit cooldown keys)
	public static final String BARRAGE = "mk6_barrage";
	public static final String[] WEAPONS = { BARRAGE, IronManAbilities.MICRO_MISSILES, IronManAbilities.WRIST_LASER,
			IronManAbilities.FLAMETHROWER, IronManAbilities.ROCKET };

	/** The S2C {@code IronManWeaponWheelPayload} prefix that opens this kit's wheel: {@code "mk6:<suitId>"}. */
	public static final String OPEN_WHEEL = "mk6";

	// ---- shared tuning ----
	public static final float SONIC_CLAP_ENERGY = 50f;
	public static final int SONIC_CLAP_COOLDOWN = 8 * 20;
	public static final int FLARE_COOLDOWN = 10 * 20;
	/** X while flying: a supersonic boost of this long instead of the flares. */
	public static final int BOOST_TICKS = 20 * 20;
	public static final float BOOST_ENERGY = 50f;

	public static final int BARRAGE_COUNT = 6;
	public static final float BARRAGE_ENERGY = 180f;
	public static final int BARRAGE_COOLDOWN = 14 * 20;
	private static final int BARRAGE_STAGGER = 3;

	public static final float ROCKET_ENERGY = 120f;
	public static final int ROCKET_COOLDOWN = 10 * 20;

	public static final int LASER_TICKS = 3 * 20;
	public static final float LASER_ENERGY = 150f;
	public static final int LASER_COOLDOWN = 20 * 20;
	public static final double LASER_RANGE = 40.0;

	/** Regeneration I while hurt: re-applied for this long whenever it runs out (so vanilla's every-50-ticks heal fires). */
	public static final int REGEN_EFFECT_TICKS = 200;

	// ---- the Mark 6's own numbers ----
	public static final float UNIBEAM_DAMAGE = 24f;
	public static final float UNIBEAM_ENERGY_PER_TICK = 90f / 20f; // 90 energy/s
	public static final int UNIBEAM_COOLDOWN = 18 * 20;
	public static final double UNIBEAM_RANGE = 30.0;
	public static final IronManHeldBeam.Spec BEAM = new IronManHeldBeam.Spec(SUIT_ID, UNIBEAM, UNIBEAM_DAMAGE,
			UNIBEAM_ENERGY_PER_TICK, UNIBEAM_COOLDOWN, UNIBEAM_RANGE);

	/** Per-suit hit numbers of the kit's own weapons. */
	public record Tuning(String suitId, float sonicClapDamage, float barrageDamage, float rocketDamage, float rocketSplash,
			float rocketRadius, float laserDamage) {
	}

	public static final Tuning MARK_6_TUNING = new Tuning(SUIT_ID, 22f, 20f, 36f, 25f, 3.0f, 13f);
	public static final Tuning MARK_7_TUNING = new Tuning(IronManMark7.SUIT_ID, 24f, 22f, 40f, 28f, 3.5f, 15f);
	/** v0.15.9: the Mark 8 hits like the Mark 7 ("same abilities as mark 7"). */
	public static final Tuning MARK_8_TUNING = new Tuning(IronManMark7.MARK_8_ID, 24f, 22f, 40f, 28f, 3.5f, 15f);

	/** The kit tuning for a suit, or null if it does not run this kit. */
	public static Tuning tuning(String suitId) {
		if (SUIT_ID.equals(suitId)) {
			return MARK_6_TUNING;
		}
		if (IronManMark7.SUIT_ID.equals(suitId)) {
			return MARK_7_TUNING;
		}
		if (IronManMark7.MARK_8_ID.equals(suitId)) {
			return MARK_8_TUNING;
		}
		return null;
	}

	public static boolean isKitSuit(String suitId) {
		return tuning(suitId) != null;
	}

	private static final Map<UUID, int[]> BARRAGE_RT = new HashMap<>(); // {pending, total}
	private static final Map<UUID, Long> BARRAGE_NEXT = new HashMap<>();
	private static final Map<UUID, String> BARRAGE_SUIT = new HashMap<>();
	private static final Map<UUID, Long> LASER_UNTIL = new HashMap<>();
	private static final Map<UUID, String> LASER_SUIT = new HashMap<>();
	/** Players whose Regeneration this kit put on (so only OUR effect is ever removed). */
	private static final Set<UUID> REGEN_APPLIED = new HashSet<>();

	private IronManMark6() {
	}

	public static void clearSessionState() {
		BARRAGE_RT.clear();
		BARRAGE_NEXT.clear();
		BARRAGE_SUIT.clear();
		LASER_UNTIL.clear();
		LASER_SUIT.clear();
		REGEN_APPLIED.clear();
	}

	// ------------------------------------------------------------------ weapon selection

	private static String weaponKey(String suitId) {
		return suitId + "/mk6_weapon_choice";
	}

	/** The weapon G fires on this kit suit, read from a (server or client-synced) state. Default: the Shoulder Barrage. */
	public static String selectedWeapon(TonyStarkState state, String suitId) {
		if (state == null) {
			return BARRAGE;
		}
		long idx = state.abilityReadyAt.getOrDefault(weaponKey(suitId), 0L);
		return idx >= 0 && idx < WEAPONS.length ? WEAPONS[(int) idx] : BARRAGE;
	}

	public static boolean isWeapon(String id) {
		for (String w : WEAPONS) {
			if (w.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** A wheel pick for the kit suit the player is wearing. Returns false when they aren't wearing one (or it's junk). */
	public static boolean selectWeapon(ServerPlayer player, String weapon) {
		String suitId = IronManArmor.wornSuitId(player);
		if (!isKitSuit(suitId)) {
			return false;
		}
		for (int i = 0; i < WEAPONS.length; i++) {
			if (!WEAPONS[i].equals(weapon)) {
				continue;
			}
			if (weapon.equals(selectedWeapon(TonyStark.state(player), suitId))) {
				return true;
			}
			TonyStark.state(player).flamethrowerHeld = false; // swapping away mid-stream stops it
			TonyStarkState s = TonyStark.state(player).copy();
			s.abilityReadyAt.put(weaponKey(suitId), (long) i);
			player.setAttached(ModAttachments.TONY_STARK_STATE, s);
			IronManSounds.play(player, IronManSounds.WEAPON_SELECT, 0.7f, 1.0f);
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk3.weapon_selected",
					Component.translatable("hud.projecthero.ironman.ability." + weapon)).withStyle(ChatFormatting.GOLD), true);
			return true;
		}
		return false;
	}

	/** The wheel-open payload string for a kit suit. */
	public static String openWheelPayload(String suitId) {
		return OPEN_WHEEL + ":" + suitId;
	}

	/** The kit suit a wheel-open payload string is for, or null if it isn't one of this kit's. */
	public static String wheelSuit(String payloadAbility) {
		if (payloadAbility != null && payloadAbility.startsWith(OPEN_WHEEL + ":")) {
			String id = payloadAbility.substring(OPEN_WHEEL.length() + 1);
			return isKitSuit(id) ? id : null;
		}
		return null;
	}

	/** The cooldown key the HUD strip should read for a slot (V shows the shield's, X the flares'). */
	public static String hudCooldownId(String abilityId) {
		if (WHEEL.equals(abilityId)) {
			return IronManAbilities.REPULSOR_BARRIER;
		}
		if (FLARES.equals(abilityId)) {
			return IronManAbilities.FLARE;
		}
		return abilityId;
	}

	// ------------------------------------------------------------------ dispatch

	/** Called from {@link IronManAbilities#trigger} for the kit's slot ids. */
	public static void trigger(ServerPlayer player, IronManSuit suit, String ability, boolean pressed) {
		Tuning t = tuning(suit.id());
		if (t == null) {
			return;
		}
		boolean sneak = player.isShiftKeyDown();
		switch (ability) {
			case WEAPON -> {
				if (pressed && sneak) {
					IronManSonicClap.fire(player, t.sonicClapDamage(), SONIC_CLAP_ENERGY, SONIC_CLAP_COOLDOWN);
					return;
				}
				fireWeapon(player, suit, t, selectedWeapon(TonyStark.state(player), suit.id()), pressed);
			}
			case FLARES -> {
				if (!pressed) {
					return;
				}
				if (sneak) {
					IronManJarvisScan.run(player, suit);
				} else if (IronManFlight.isFlying(player)) {
					IronManFlares.supersonicBoost(player, BOOST_TICKS, BOOST_ENERGY);
				} else {
					IronManFlares.fire(player, true, FLARE_COOLDOWN);
				}
			}
			case UNIBEAM -> {
				if (pressed) {
					IronManHeldBeam.start(player, suit, BEAM);
				} else {
					IronManHeldBeam.stop(player, true);
				}
			}
			case WHEEL -> {
				if (!pressed) {
					IronManAbilities.stopBarrier(player, suit, true);
					return;
				}
				if (sneak) {
					IronManAbilities.startBarrier(player, suit);
				} else if (requireHelmet(player, suit.id())) {
					ServerPlayNetworking.send(player, new com.projecthero.mod.network.IronManWeaponWheelPayload(openWheelPayload(suit.id())));
				}
			}
			default -> {
			}
		}
	}

	/** G (no Shift): the weapon picked on the wheel. Public so the gametests can drive one directly. */
	public static void fireWeapon(ServerPlayer player, IronManSuit suit, Tuning t, String weapon, boolean pressed) {
		switch (weapon) {
			case IronManAbilities.MICRO_MISSILES -> {
				if (pressed) {
					IronManAbilities.microMissiles(player, suit);
				}
			}
			case IronManAbilities.WRIST_LASER -> {
				if (pressed) {
					startLaser(player, suit, t);
				}
			}
			case IronManAbilities.FLAMETHROWER -> IronManAbilities.flamethrowerKey(player, suit, pressed);
			case IronManAbilities.ROCKET -> {
				if (pressed) {
					rocket(player, suit, t);
				}
			}
			default -> {
				if (pressed) {
					barrage(player, suit, t);
				}
			}
		}
	}

	// ------------------------------------------------------------------ Shoulder Barrage

	private static void barrage(ServerPlayer player, IronManSuit suit, Tuning t) {
		int[] r = BARRAGE_RT.get(player.getUUID());
		if (r != null && r[0] > 0) {
			return; // still launching
		}
		if (!requireHelmet(player, suit.id()) || !IronManAbilities.cooldownReady(player, suit.id(), BARRAGE) || !pay(player, suit, BARRAGE_ENERGY)) {
			return;
		}
		BARRAGE_RT.put(player.getUUID(), new int[] { BARRAGE_COUNT, BARRAGE_COUNT });
		BARRAGE_NEXT.put(player.getUUID(), player.level().getGameTime());
		BARRAGE_SUIT.put(player.getUUID(), suit.id());
		IronManAbilityFx.play(player, IronManAbilityFx.MISSILES, 24);
		IronManSounds.move(player, IronManSounds.MISSILE_POD, 1.0f, 0.95f);
		TonyStark.triggerCooldown(player, suit.id(), BARRAGE, BARRAGE_COOLDOWN);
		tickBarrage(player); // the first one leaves right away
	}

	/** Missiles still to launch in the current barrage (0 = none). */
	public static int pendingBarrage(ServerPlayer player) {
		int[] r = BARRAGE_RT.get(player.getUUID());
		return r == null ? 0 : r[0];
	}

	private static void tickBarrage(ServerPlayer player) {
		int[] r = BARRAGE_RT.get(player.getUUID());
		if (r == null || r[0] <= 0) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < BARRAGE_NEXT.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		Tuning t = tuning(BARRAGE_SUIT.getOrDefault(player.getUUID(), SUIT_ID));
		float damage = t == null ? MARK_6_TUNING.barrageDamage() : t.barrageDamage();
		ServerLevel level = (ServerLevel) player.level();
		int index = r[1] - r[0];
		Vec3 look = player.getLookAngle();
		Vec3 side = IronManAbilities.rightOf(player, look);
		double s = index % 2 == 0 ? 1 : -1;
		Vec3 shoulder = player.position().add(0, player.getBbHeight() * 0.85, 0).add(side.scale(0.45 * s));
		Vec3 dir = look.add(side.scale(0.5 * s)).add(0, 0.4, 0).normalize();
		IronManMissileEntity missile = new IronManMissileEntity(level, player, dir.scale(0.9))
				.withDamage(damage, damage * 0.4f)
				.withBlastRadius(1.5f)
				.withHoming();
		LivingEntity target = pickSpreadTarget(player, index);
		if (target != null) {
			missile.withTarget(target);
		}
		missile.setPos(shoulder.x + dir.x * 0.3, shoulder.y + dir.y * 0.3, shoulder.z + dir.z * 0.3);
		level.addFreshEntity(missile);
		IronManSounds.loop(player, IronManSounds.MISSILE_LAUNCH, 0.8f, 0.95f + (player.tickCount % 3) * 0.05f);
		r[0]--;
		BARRAGE_NEXT.put(player.getUUID(), now + BARRAGE_STAGGER);
	}

	// ------------------------------------------------------------------ Rocket

	private static void rocket(ServerPlayer player, IronManSuit suit, Tuning t) {
		if (!requireHelmet(player, suit.id()) || !IronManAbilities.cooldownReady(player, suit.id(), IronManAbilities.ROCKET)
				|| !pay(player, suit, ROCKET_ENERGY)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 shoulder = player.getEyePosition().add(0, 0.15, 0);
		Vec3 dir = IronManTargeting.aim(player, shoulder, player.getLookAngle(), 100);
		IronManMissileEntity missile = new IronManMissileEntity(level, player, dir.scale(1.5))
				.withDamage(t.rocketDamage(), t.rocketSplash())
				.withBlastRadius(t.rocketRadius())
				.withBreaksBlocks();
		missile.setPos(shoulder.x + dir.x, shoulder.y + dir.y, shoulder.z + dir.z);
		level.addFreshEntity(missile);
		IronManAbilityFx.play(player, IronManAbilityFx.ROCKET, 14);
		IronManSounds.move(player, IronManSounds.ROCKET_LAUNCH, 1.1f, 1.0f);
		IronManSounds.loop(player, IronManSounds.THRUSTER, 0.6f, 1.2f);
		TonyStark.triggerCooldown(player, suit.id(), IronManAbilities.ROCKET, ROCKET_COOLDOWN);
	}

	// ------------------------------------------------------------------ Wrist Laser (wheel)

	public static boolean laserFiring(ServerPlayer player) {
		return LASER_UNTIL.containsKey(player.getUUID());
	}

	private static void startLaser(ServerPlayer player, IronManSuit suit, Tuning t) {
		if (LASER_UNTIL.containsKey(player.getUUID())) {
			return;
		}
		if (!IronManArmor.hasChestplate(player, suit.id())) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return;
		}
		if (!IronManAbilities.cooldownReady(player, suit.id(), IronManAbilities.WRIST_LASER) || !pay(player, suit, LASER_ENERGY)) {
			return;
		}
		LASER_UNTIL.put(player.getUUID(), player.level().getGameTime() + LASER_TICKS);
		LASER_SUIT.put(player.getUUID(), suit.id());
		IronManSounds.move(player, IronManSounds.LASER_START, 1.0f, 1.0f);
		IronManSounds.play(player, IronManSounds.REPULSOR_ZAP, 0.8f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk7.laser_firing")
				.withStyle(ChatFormatting.RED), true);
	}

	private static void stopLaser(ServerPlayer player, boolean cooldown) {
		if (LASER_UNTIL.remove(player.getUUID()) == null) {
			return;
		}
		String suitId = LASER_SUIT.remove(player.getUUID());
		if (cooldown && suitId != null) {
			TonyStark.triggerCooldown(player, suitId, IronManAbilities.WRIST_LASER, LASER_COOLDOWN);
		}
		IronManSounds.play(player, IronManSounds.LASER_END, 0.9f, 1.0f);
	}

	private static void tickLaser(ServerPlayer player, IronManSuit suit) {
		long until = LASER_UNTIL.getOrDefault(player.getUUID(), 0L);
		long now = player.level().getGameTime();
		if (now >= until || !suit.id().equals(LASER_SUIT.get(player.getUUID())) || !IronManArmor.hasChestplate(player, suit.id())) {
			stopLaser(player, true);
			return;
		}
		Tuning t = tuning(suit.id());
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		Vec3 origin = IronManHeldBeam.laserOrigin(player);
		LivingEntity locked = IronManTargeting.lockedWithin(player, LASER_RANGE);
		LivingEntity target = locked != null ? locked : AbilityHelpers.raycastEntity(player, LASER_RANGE);
		var blockHit = AbilityHelpers.raycastBlock(player, LASER_RANGE);
		Vec3 end;
		if (target != null) {
			end = target.position().add(0, target.getBbHeight() * 0.5, 0);
		} else if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		} else {
			end = origin.add(look.scale(LASER_RANGE));
		}
		IronManAbilityFx.hold(player, IronManAbilityFx.LASER);
		IronManAbilities.broadcastBeam(player, origin, end, 3); // kind 3 = thin red cutting laser
		if (target != null) {
			AbilityHelpers.hurt(player, target, t == null ? MARK_6_TUNING.laserDamage() : t.laserDamage());
			target.igniteForSeconds(1);
		} else if (now % 4 == 0 && blockHit.getType() == HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
			BlockPos bp = blockHit.getBlockPos();
			float speed = level.getBlockState(bp).getDestroySpeed(level, bp);
			if (speed >= 0f && speed < 3.0f) {
				level.destroyBlock(bp, false, player);
			}
		}
		if (now % 2 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, end.x, end.y, end.z, 2, 0.08, 0.08, 0.08, 0.01);
		}
		if (now % 4 == 0) {
			IronManSounds.play(player, IronManSounds.LASER, 0.7f, 0.95f + (player.tickCount % 3) * 0.05f);
		}
	}

	// ------------------------------------------------------------------ Regeneration while hurt

	/**
	 * Regeneration I while the wearer (chestplate on) is below full health, paid {@code hurtRegenEnergyPerSecond} a
	 * second (flat -- the suit's cost multiplier doesn't apply) for exactly as long as it is being applied. At full
	 * health, out of energy or without the chestplate it comes off (only the one this kit put on).
	 */
	public static void tickRegeneration(ServerPlayer player, IronManSuit suit) {
		boolean hurt = player.isAlive() && player.getHealth() < player.getMaxHealth();
		if (!suit.hurtRegeneration() || !hurt || !IronManArmor.hasChestplate(player, suit.id())) {
			clearRegeneration(player);
			return;
		}
		MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
		boolean ours = REGEN_APPLIED.contains(player.getUUID()) && cur != null && isOurs(cur);
		if (cur != null && !ours) {
			REGEN_APPLIED.remove(player.getUUID());
			return; // someone else's Regeneration (a potion, a beacon) is running -- no charge, nothing to add
		}
		if (!IronManEnergy.spend(player, suit.id(), suit.hurtRegenEnergyPerSecond() / 20f)) {
			clearRegeneration(player);
			return;
		}
		if (cur == null) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_EFFECT_TICKS, 0, true, false, false));
			REGEN_APPLIED.add(player.getUUID());
		}
	}

	/** True while this kit's Regeneration is on the player. */
	public static boolean regenerating(ServerPlayer player) {
		MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
		return REGEN_APPLIED.contains(player.getUUID()) && cur != null && isOurs(cur);
	}

	private static boolean isOurs(MobEffectInstance e) {
		return e.getAmplifier() == 0 && e.isAmbient() && !e.isVisible() && e.getDuration() <= REGEN_EFFECT_TICKS;
	}

	private static void clearRegeneration(ServerPlayer player) {
		if (!REGEN_APPLIED.remove(player.getUUID())) {
			return;
		}
		MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
		if (cur != null && isOurs(cur)) {
			player.removeEffect(MobEffects.REGENERATION);
		}
	}

	// ------------------------------------------------------------------ ticking / shutdown

	/** Per-tick from {@link com.projecthero.mod.ironman.IronManSuitTicker} while a powered suit is worn. */
	public static void tick(ServerPlayer player, IronManSuit suit) {
		if (!isKitSuit(suit.id())) {
			shutDown(player);
			return;
		}
		tickRegeneration(player, suit);
		IronManHeldBeam.tick(player, suit);
		if (LASER_UNTIL.containsKey(player.getUUID())) {
			tickLaser(player, suit);
		}
		if (pendingBarrage(player) > 0) {
			if (IronManArmor.hasHelmet(player, suit.id()) && suit.id().equals(BARRAGE_SUIT.get(player.getUUID()))) {
				tickBarrage(player);
			} else {
				BARRAGE_RT.remove(player.getUUID());
			}
		}
	}

	/** Everything off (suit removed, depleted, power lost). Cheap when nothing is running. */
	public static void shutDown(ServerPlayer player) {
		for (String id : KIT_SUITS) {
			IronManHeldBeam.stopFor(player, id, true);
		}
		stopLaser(player, true);
		BARRAGE_RT.remove(player.getUUID());
		clearRegeneration(player);
	}

	/** Test hook: one tick of the kit without the rest of the suit ticker (no recharge). */
	public static void tickForTest(ServerPlayer player, IronManSuit suit) {
		tick(player, suit);
	}

	// ------------------------------------------------------------------ helpers (shared with the Mark 7)

	static boolean requireHelmet(ServerPlayer player, String suitId) {
		if (IronManArmor.hasHelmet(player, suitId)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_helmet"), true);
		return false;
	}

	private static boolean pay(ServerPlayer player, IronManSuit suit, float base) {
		float cost = base * suit.energyCostMultiplier();
		if (IronManEnergy.spend(player, suit.id(), cost)) {
			return true;
		}
		IronManAbilities.noEnergy(player, cost);
		return false;
	}

	/** The lock, else the crosshair target, else the index-th nearest hostile around the player (spreads a volley). */
	static LivingEntity pickSpreadTarget(ServerPlayer player, int index) {
		LivingEntity lock = IronManTargeting.locked(player);
		if (lock != null) {
			return lock;
		}
		LivingEntity aimed = IronManAbilities.homingTarget(player);
		if (aimed != null) {
			return aimed;
		}
		java.util.List<LivingEntity> near = com.projecthero.mod.combat.HeroTargets.hostiles(player.level(), player,
				com.projecthero.mod.combat.HeroTargets.around(player.position(), 32.0));
		if (near.isEmpty()) {
			return null;
		}
		near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		return near.get(index % near.size());
	}
}
