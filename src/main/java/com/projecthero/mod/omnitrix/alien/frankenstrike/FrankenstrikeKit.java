package com.projecthero.mod.omnitrix.alien.frankenstrike;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Frankenstrike, a Transylian (prototype
 * Omnitrix, a hidden alien found by a mistransform): a huge Frankenstein-like brute whose shoulder bolts are electrodes. He soaks up
 * electricity -- lightning, power, the air of a storm -- banks it as <b>Charge</b> (0-100, like Feedback's) and throws it back as
 * shocks, bolts and storms. Very strong.
 *
 * <pre>
 *   R          Shock Punch        a charged haymaker (4 blocks): 8, then the current jumps to the nearest other creature within 6: 6. +6 Charge on a hit
 *   Shift+R    Lightning Bolt     a bolt from his hand at the crosshair (28; 15 Charge): 15; with less, a Static Bolt (7)
 *   G          Conductor Slam     both fists into the ground: 20 Charge -- 17 to everything within 5.5, thrown up; with less, 9 within 3.5
 *   X          Electro Leap       a huge leap where he looks; landing with 10+ Charge shocks round him (10 Charge): 10 within 3.5. No fall damage
 *   Z          Overcharge Storm   ULTIMATE (50 Charge): for 3 s, eight lightning strikes on the creatures round him (14 blocks): 24 each, stunned 1 s.
 *                                 The bolts are visual only: no fire, no block damage
 *   C          Grab &amp; Shock      grabs the creature ahead (4) and holds it up for 2 s, 4 every half second, then throws it: 4 (20 in all)
 *   V          Lightning Rod      (v0.17.4: was Shift+V) calls a bolt down onto his own bolts: +50 Charge, +4 health, 8 to everything within 3
 *   Hold V     Recharge           channelled for as long as V is held: +6 Charge a second, x2 by power (lightning rod, powered redstone,
 *                                 beacon within 6), x3 in a storm under open sky; slowed
 *   v0.17.4 (docs/OMNITRIX_PROTOTYPE_REVAMP_0174.md): Quick Recharge (tap V) is gone, beside Hold V it was pointless. New:
 *   Hold R     Arc Stream         a crackling arc at the crosshair while held (14 blocks, up to 3 s): 3 every 0.25 s, jumping to one more
 *                                 creature; 4 Charge a second
 *   Shift+G    Tesla Field        two electrodes planted round him: a 4-block field for 5 s, 3 every 0.5 s to creatures inside (20 Charge)
 *   Hold G     Charged Slam       wind up to 1.5 s: 17 within 5.5 uncharged to 22 within 8 at full (25 Charge)
 *   Shift+X    Lightning Ride     he becomes a bolt and strikes to a spot up to 12 blocks along his aim: 8 to things on the line (15 Charge)
 *   Shift+Z    Defibrillate       30 Charge: heals 8 and shakes off Slowness, Weakness and Poison; allies within 4 heal 4
 *   Shift+V    Static Armour      6 s of crackling skin (15 Charge): whatever hits him up close takes 4 and is knocked back
 *   Shift+C    Monster Roar       a thunderous roar (10-block cone): 6, Weakness and Slowness 3 s
 *   Attack     Galvanic Fists     +4 Charge a punch; with 50+ Charge the punch shocks for 3 more
 *   Passives   Transylian (lightning never hurts: +50 Charge and +6 health); Brute Strength (attack 9, heavy, hard to knock back);
 *              Living Dynamo (shocks hit machines x1.5)
 *   Weakness   Grounded (water leaks his Charge, 2 a second); Lumbering (slow, can't sprint-jump far)
 * </pre>
 */
public final class FrankenstrikeKit extends AlienKit {
	public static final String ID = "frankenstrike";
	/** His electricity: a pale, almost white blue (Feedback's is a deep blue, Buzzshock's green). */
	public static final int COLOUR = 0x9FE6FF;
	public static final int WHITE = 0xF2FCFF;
	public static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.7f, 0.92f, 1.0f), 0.55f);

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_RECHARGE = 1, F_LEAP = 2, F_GRAB = 4, F_STORM = 8, F_ARC = 16, F_TESLA = 32, F_CSLAM = 64, F_RIDE = 128, F_STATIC = 256;
	public static final int ANIM_PUNCH = 1, ANIM_BOLT = 2, ANIM_SLAM = 3, ANIM_LEAP = 4, ANIM_LAND = 5, ANIM_STORM = 6, ANIM_GRAB = 7, ANIM_THROW = 8,
			ANIM_RECHARGE = 9, ANIM_RECHARGE_END = 10, ANIM_ROD = 11, ANIM_JAB = 13, ANIM_ARC = 14, ANIM_TESLA = 15, ANIM_CSLAM = 16, ANIM_RIDE = 17,
			ANIM_DEFIB = 18, ANIM_STATIC = 19, ANIM_ROAR = 20;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.frankenstrike.<id>)
	public static final String PUNCH = "shock_punch", BOLT = "lightning_bolt", SLAM = "conductor_slam", LEAP = "electro_leap", STORM = "overcharge_storm",
			GRAB = "grab_and_shock", RECHARGE = "recharge", ROD = "lightning_rod", ARC = "arc_stream", TESLA = "tesla_field", CSLAM = "charged_slam",
			RIDE = "lightning_ride", DEFIB = "defibrillate", STATIC = "static_armour", ROAR = "monster_roar", FISTS = "galvanic_fists", TRANSYLIAN = "transylian",
			BRUTE = "brute_strength", DYNAMO = "living_dynamo", GROUNDED = "grounded", LUMBERING = "lumbering";

	// ---------------------------------------------------------------- Charge
	public static final int MAX_CHARGE = 100;
	public static final String CHARGE = "charge", LEAP_UNTIL = "leap_until", STORM_UNTIL = "storm_until", ARC_AT = "arc_at", TESLA_UNTIL = "tesla_until",
			TESLA_X = "tesla_x", TESLA_Y = "tesla_y", TESLA_Z = "tesla_z", TESLA_YAW = "tesla_yaw", CSLAM_START = "cslam_start", STATIC_UNTIL = "static_until",
			RIDE_AT = "ride_at";
	private static final String[] TIMERS = { CHARGE, LEAP_UNTIL, STORM_UNTIL, ARC_AT, TESLA_UNTIL, TESLA_X, TESLA_Y, TESLA_Z, TESLA_YAW, CSLAM_START,
			STATIC_UNTIL, RIDE_AT };

	// ---------------------------------------------------------------- numbers (basic 5-8, specials 12-17, big 17-20, ultimates 21-36)
	public static final float PUNCH_DAMAGE = 8f, CHAIN_DAMAGE = 6f, BOLT_DAMAGE = 15f, SPARK_DAMAGE = 7f, SLAM_DAMAGE = 17f, SLAM_WEAK = 9f, LAND_DAMAGE = 10f,
			STRIKE_DAMAGE = 24f, GRAB_DAMAGE = 4f, THROW_DAMAGE = 4f, ROD_DAMAGE = 8f, FIST_SHOCK = 3f, ARC_DAMAGE = 3f, TESLA_DAMAGE = 3f, CSLAM_DMG_MIN = 17f,
			CSLAM_DMG_MAX = 22f, RIDE_DAMAGE = 8f, DEFIB_HEAL = 8f, DEFIB_ALLY_HEAL = 4f, STATIC_DAMAGE = 4f, ROAR_DAMAGE = 6f;
	public static final float MACHINE_BONUS = 1.5f;
	public static final int BOLT_COST = 15, SLAM_COST = 20, LAND_COST = 10, STORM_COST = 50, PUNCH_GAIN = 6, ROD_GAIN = 50, LIGHTNING_GAIN = 50,
			FIST_GAIN = 4, FIST_SHOCK_MIN = 50, TESLA_COST = 20, CSLAM_COST = 25, RIDE_COST = 15, DEFIB_COST = 30, STATIC_COST = 15;
	/** v0.17.4 holds and timed moves (ticks): Arc Stream's run and pulse (it drains 1 Charge a pulse: 4 a second), the field, the slam's wind-up. */
	public static final int ARC_MAX = 60, ARC_EVERY = 5, TESLA_TICKS = 100, TESLA_EVERY = 10, CSLAM_FULL = 30, CSLAM_MAX = 70, STATIC_TICKS = 120,
			ROAR_TICKS = 60;
	public static final int STORM_TICKS = 60, STORM_STRIKES = 8, GRAB_TICKS = 40, GRAB_EVERY = 10, LEAP_TICKS = 60, RECHARGE_EVERY = 10, STRIKE_STUN = 20;
	public static final double PUNCH_RANGE = 4, CHAIN_RANGE = 6, BOLT_RANGE = 28, SLAM_RADIUS = 5.5, SLAM_WEAK_RADIUS = 3.5, LAND_RADIUS = 3.5,
			STORM_RANGE = 14, GRAB_RANGE = 4, ROD_RADIUS = 3, POWER_NEAR = 6, ARC_RANGE = 14, ARC_JUMP = 6, TESLA_RADIUS = 4, TESLA_ELECTRODE = 2.2,
			CSLAM_R_MAX = 8, RIDE_RANGE = 12, DEFIB_ALLY_RADIUS = 4, STATIC_REACH = 4, ROAR_RANGE = 10, ROAR_HALF_ANGLE = 40;

	// ---------------------------------------------------------------- sounds (the sounds test: volume <= 0.75, pitch >= 0.5)
	public static final OmnitrixSfx.Cue SND_ZAP = OmnitrixSfx.cue("frankenstrike_zap")
			.layer(SoundEvents.TRIDENT_THUNDER, 0.1f, 1.6f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.2f, 1.4f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_PUNCH = OmnitrixSfx.cue("frankenstrike_punch")
			.layer(SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 0.7f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.08f, 1.9f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_BIG = OmnitrixSfx.cue("frankenstrike_big")
			.layer(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4f, 1.0f)
			.layer(SoundEvents.GENERIC_EXPLODE.value(), 0.25f, 1.4f)
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.12f, 1.2f, 1)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_CHARGE = OmnitrixSfx.cue("frankenstrike_charge")
			.layer(SoundEvents.BEACON_AMBIENT, 0.6f, 1.5f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 0.8f)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_SLAM = OmnitrixSfx.cue("frankenstrike_slam")
			.layer(SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 0.8f)
			.layer(SoundEvents.ANVIL_LAND, 0.25f, 0.6f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.12f, 1.4f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_FIZZ = OmnitrixSfx.cue("frankenstrike_fizz")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.4f)
			.gap(8);
	/** v0.17.4 Monster Roar. */
	public static final OmnitrixSfx.Cue SND_ROAR = OmnitrixSfx.cue("frankenstrike_roar")
			.layer(SoundEvents.RAVAGER_ROAR, 0.7f, 0.7f)
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.1f, 1.4f, 2)
			.layer(SoundEvents.ZOMBIE_AMBIENT, 0.5f, 0.5f)
			.gap(10);

	// ---------------------------------------------------------------- server-only state
	/** Who he holds up (Grab &amp; Shock), from when. */
	private record Grab(UUID target, long start) {
	}

	private static final Map<UUID, Grab> GRABS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> STRIKES_LEFT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_LIGHTNING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private FrankenstrikeKit() {
	}

	public static void register() {
		Aliens.register(new FrankenstrikeKit());
	}

	public static void clearSessionState() {
		GRABS.clear();
		STRIKES_LEFT.clear();
		LAST_LIGHTNING.clear();
		LAST_TICK.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Frankenstrike right now (client-safe). */
	public static boolean isFrankenstrike(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// a head and more taller than Ben, broad and heavy; all muscle
		return AlienStats.builder().health(40f).speed(0.95f).scale(1.2f).armor(12f, 2f).knockbackResist(0.5f).attack(9f).jump(0.1f).safeFall(4f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(PUNCH, AbilitySlot.SLOT_1, 16), AlienMove.shift(BOLT, AbilitySlot.SLOT_1, 30), AlienMove.hold(ARC, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(SLAM, AbilitySlot.SLOT_2, 140), AlienMove.shift(TESLA, AbilitySlot.SLOT_2, 320), AlienMove.hold(CSLAM, AbilitySlot.SLOT_2, 180),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 100), AlienMove.shift(RIDE, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(STORM, AbilitySlot.SLOT_4, 900), AlienMove.shift(DEFIB, AbilitySlot.SLOT_4, 500),
				AlienMove.tap(ROD, AbilitySlot.SLOT_5, 900), AlienMove.shift(STATIC, AbilitySlot.SLOT_5, 280), AlienMove.hold(RECHARGE, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(GRAB, AbilitySlot.SLOT_6, 240), AlienMove.shift(ROAR, AbilitySlot.SLOT_6, 280),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(TRANSYLIAN, "Passive", 0), AlienMove.vanilla(BRUTE, "Passive", 0),
				AlienMove.vanilla(DYNAMO, "Passive", 0), AlienMove.vanilla(GROUNDED, "Weakness", 0), AlienMove.vanilla(LUMBERING, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_ARC);
			case SLOT_2 -> flag(p, F_TESLA) || flag(p, F_CSLAM);
			case SLOT_3 -> flag(p, F_LEAP) || flag(p, F_RIDE);
			case SLOT_4 -> flag(p, F_STORM);
			case SLOT_5 -> flag(p, F_RECHARGE) || flag(p, F_STATIC);
			case SLOT_6 -> flag(p, F_GRAB);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- Charge

	/** His Charge, 0..100 (client-safe: a synced timer). */
	public static int charge(Player p) {
		return (int) Mth.clamp(Omnitrix.state(p).readyAt(ID + "." + CHARGE), 0, MAX_CHARGE);
	}

	private void setCharge(ServerPlayer p, int c) {
		c = Mth.clamp(c, 0, MAX_CHARGE);
		if (c == charge(p)) {
			return;
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		if (c <= 0) {
			n.timers.remove(key(CHARGE));
		} else {
			n.timers.put(key(CHARGE), (long) c); // (a raw long: setTimer drops values <= 0 and means game times)
		}
		Omnitrix.save(p, n);
	}

	/** Test hook / harness: sets his Charge outright. */
	public void fill(ServerPlayer p, int c) {
		setCharge(p, c);
	}

	public int gain(ServerPlayer p, int points) {
		int c = charge(p);
		int add = Math.min(points, MAX_CHARGE - c);
		setCharge(p, c + add);
		return add;
	}

	private boolean spend(ServerPlayer p, int points) {
		int c = charge(p);
		if (c < points) {
			return false;
		}
		setCharge(p, c - points);
		return true;
	}

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.frankenstrike." + key, args).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- shocks

	public static boolean machine(LivingEntity e) {
		return UpgradeKit.machine(e);
	}

	/** An electric hit on {@code e}, half again on a machine (Living Dynamo). */
	public boolean shock(ServerPlayer p, LivingEntity e, float amount) {
		return hurt(p, e, amount * (machine(e) ? MACHINE_BONUS : 1f), false);
	}

	/** A bolt drawn to {@code end}: from his hand (from null) or from entity {@code from}; {@code big} the thick one. */
	public static void arc(ServerPlayer p, Vec3 end, @Nullable Entity from, int life, boolean big) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, big ? "frankenstrike_big" : "frankenstrike_arc");
		a.moveTo(end.x, end.y, end.z, 0f, 0f);
		double len = from == null ? p.getEyePosition().distanceTo(end) : from.position().distanceTo(end);
		a.colour(from == null ? 0 : from.getId() + 1).size((float) Math.max(0.6, len / 3.0)).held(true).speed(0).life(life).damage(0f).spawn();
	}

	private static Vec3 centre(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	private static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** The nearest living creature within {@code range} of {@code at} other than {@code not}, or null. */
	@Nullable
	private static LivingEntity nearest(ServerPlayer p, Vec3 at, double range, @Nullable LivingEntity not) {
		LivingEntity best = null;
		double bd = Double.MAX_VALUE;
		for (LivingEntity e : around(p, at, range)) {
			if (e == not) {
				continue;
			}
			double d = e.distanceToSqr(at);
			if (d < bd) {
				bd = d;
				best = e;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.8f, 0.8f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		GRABS.remove(id);
		STRIKES_LEFT.remove(id);
		LAST_LIGHTNING.remove(id);
		LAST_TICK.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : TIMERS) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.LIGHTNING_BOLT)) {
			// Transylian: a strike recharges him (once a second: a bolt hits for several ticks)
			long now = p.level().getGameTime();
			Long last = LAST_LIGHTNING.get(p.getUUID());
			if (last == null || now - last > 20) {
				LAST_LIGHTNING.put(p.getUUID(), now);
				gain(p, LIGHTNING_GAIN);
				p.heal(6f);
				OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 1f, 1f);
				level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.4, p.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
			}
			p.clearFire();
			return false;
		}
		if ((flag(p, F_LEAP) || flag(p, F_RIDE)) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		// v0.17.4 Static Armour: a blow up close earths itself through the one who threw it
		if (flag(p, F_STATIC) && source.getEntity() instanceof LivingEntity a && a == source.getDirectEntity() && a != p
				&& a.distanceToSqr(p) <= STATIC_REACH * STATIC_REACH) {
			a.invulnerableTime = 0;
			if (shock(p, a, STATIC_DAMAGE)) {
				shove(a, p.position(), 0.9, 0.3);
			}
			arc(p, centre(a), null, 4, false);
			level(p).sendParticles(SPARK, a.getX(), a.getY() + a.getBbHeight() * 0.5, a.getZ(), 10, 0.25, 0.3, 0.25, 0.2);
			OmnitrixSfx.play(level(p), p.position(), SND_ZAP, 0.6f, 1.3f);
		}
		return amount;
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		ServerLevel l = level(p);
		if (flag(p, F_RECHARGE)) {
			rechargeTick(p, l, now);
		}
		if (flag(p, F_LEAP)) {
			leapTick(p, l, now);
		}
		if (flag(p, F_GRAB)) {
			grabTick(p, l, now);
		}
		if (flag(p, F_STORM)) {
			stormTick(p, l, now);
		}
		if (flag(p, F_ARC)) {
			arcTick(p, l, now);
		}
		if (flag(p, F_TESLA)) {
			teslaTick(p, l, now);
		}
		if (flag(p, F_CSLAM)) {
			cslamTick(p, l, now);
		}
		if (flag(p, F_RIDE) && now - timer(p, RIDE_AT) > 3 && (p.onGround() || p.isInWater() || now - timer(p, RIDE_AT) > 60)) {
			setFlag(p, F_RIDE, false);
			setTimer(p, RIDE_AT, 0L);
		}
		if (flag(p, F_STATIC)) {
			if (now >= timer(p, STATIC_UNTIL)) {
				setFlag(p, F_STATIC, false);
				setTimer(p, STATIC_UNTIL, 0L);
				OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.5f, 1.2f);
			} else if (now % 4 == 0) {
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), 4, 0.4, 0.6, 0.4, 0.1);
			}
		}
		// Grounded: water bleeds his Charge away
		if (now % 10 == 0 && p.isInWater() && charge(p) > 0) {
			setCharge(p, charge(p) - 1);
			if (now % 20 == 0) {
				l.sendParticles(SPARK, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.3, 0.1, 0.3, 0.1);
				OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.4f, 1.2f);
			}
		}
		// Lumbering: no sprint-jumping across the map
		if (p.isSprinting() && !p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() > 0.16 && !flag(p, F_LEAP)) {
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.92, 1, 0.92));
			p.hurtMarked = true;
		}
		// sparks crawling off his bolts when he is full of it
		if (now % 8 == 0 && charge(p) >= 50) {
			Vec3 up = p.position().add(0, p.getBbHeight() * 0.92, 0);
			l.sendParticles(SPARK, up.x, up.y, up.z, 2, 0.35, 0.1, 0.35, 0.05);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_GRAB) && slot != AbilitySlot.SLOT_6) {
			return; // his hands are full
		}
		if (flag(p, F_RECHARGE) && slot != AbilitySlot.SLOT_5) {
			endRecharge(p); // any other move stops the channel
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					lightningBolt(p);
				} else {
					shockPunch(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					teslaField(p);
				} else {
					conductorSlam(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					lightningRide(p);
				} else {
					electroLeap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					defibrillate(p);
				} else {
					overchargeStorm(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					staticArmour(p);
				} else {
					lightningRod(p);
				}
			}
			case SLOT_6 -> {
				if (shift && !flag(p, F_GRAB)) {
					monsterRoar(p);
				} else {
					grab(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_5;
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (flag(p, F_GRAB)) {
			return; // his hands are full
		}
		if (flag(p, F_RECHARGE) && slot != AbilitySlot.SLOT_5) {
			endRecharge(p);
		}
		switch (slot) {
			case SLOT_1 -> startArc(p);
			case SLOT_2 -> startChargedSlam(p);
			case SLOT_5 -> startRecharge(p);
			default -> {
			}
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		switch (slot) {
			case SLOT_1 -> endArc(p);
			case SLOT_2 -> releaseChargedSlam(p);
			case SLOT_5 -> endRecharge(p);
			default -> {
			}
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (!(target instanceof LivingEntity e)) {
			return false;
		}
		// Galvanic Fists: every punch feeds his bolts; a full man's punches crackle
		anim(p, ANIM_JAB);
		float k = p.getAttackStrengthScale(0.5f);
		if (k > 0.5f) {
			gain(p, FIST_GAIN);
			if (charge(p) >= FIST_SHOCK_MIN) {
				e.invulnerableTime = 0;
				shock(p, e, FIST_SHOCK * k);
				level(p).sendParticles(SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.25, 0.2, 0.12);
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: a haymaker into the creature ahead; the current jumps on to the nearest other one. */
	public boolean shockPunch(ServerPlayer p) {
		if (!use(p, PUNCH)) {
			return false;
		}
		anim(p, ANIM_PUNCH);
		LivingEntity t = lookTarget(p, PUNCH_RANGE);
		if (t == null) {
			// a swing at the air still shows the fist crackle
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(1.6));
			level(p).sendParticles(SPARK, front.x, front.y, front.z, 8, 0.2, 0.2, 0.2, 0.15);
			OmnitrixSfx.playOn(p, SND_ZAP, 0.5f, 1.3f, SoundSource.PLAYERS);
			return true;
		}
		t.invulnerableTime = 0;
		if (shock(p, t, PUNCH_DAMAGE)) {
			shove(t, p.position(), 0.7, 0.2);
			gain(p, PUNCH_GAIN);
		}
		Vec3 c = centre(t);
		level(p).sendParticles(SPARK, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.25);
		level(p).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.2);
		LivingEntity second = nearest(p, t.position(), CHAIN_RANGE, t);
		if (second != null) {
			second.invulnerableTime = 0;
			shock(p, second, CHAIN_DAMAGE);
			arc(p, centre(second), t, 7, false);
		}
		OmnitrixSfx.playOn(p, SND_PUNCH, 0.75f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: a bolt from his hand at the crosshair; a weak static bolt with under 15 Charge. */
	public boolean lightningBolt(ServerPlayer p) {
		if (!use(p, BOLT)) {
			return false;
		}
		boolean charged = spend(p, BOLT_COST);
		anim(p, ANIM_BOLT);
		LivingEntity t = lookTarget(p, BOLT_RANGE);
		Vec3 end = t != null ? centre(t) : aimPoint(p, BOLT_RANGE);
		if (t != null) {
			t.invulnerableTime = 0;
			if (shock(p, t, charged ? BOLT_DAMAGE : SPARK_DAMAGE) && charged) {
				shove(t, p.position(), 0.5, 0.2);
			}
		}
		arc(p, end, null, charged ? 9 : 6, charged);
		level(p).sendParticles(SPARK, end.x, end.y, end.z, charged ? 20 : 8, 0.3, 0.3, 0.3, 0.25);
		OmnitrixSfx.playOn(p, charged ? SND_BIG : SND_ZAP, charged ? 0.6f : 0.7f, charged ? 1.3f : 1.1f, SoundSource.PLAYERS);
		if (!charged) {
			say(p, "weak", BOLT_COST);
		}
		return true;
	}

	// ---------------------------------------------------------------- G

	/** G: both fists into the ground and the current out through it. */
	public boolean conductorSlam(ServerPlayer p) {
		if (!p.onGround() && !p.isInWater()) {
			say(p, "need_ground");
			return false;
		}
		if (!use(p, SLAM)) {
			return false;
		}
		boolean charged = spend(p, SLAM_COST);
		anim(p, ANIM_SLAM);
		double r = charged ? SLAM_RADIUS : SLAM_WEAK_RADIUS;
		ServerLevel l = level(p);
		for (LivingEntity e : around(p, p.position(), r)) {
			if (Math.abs(e.getY() - p.getY()) > 2.5) {
				continue;
			}
			e.invulnerableTime = 0;
			if (shock(p, e, charged ? SLAM_DAMAGE : SLAM_WEAK)) {
				shove(e, p.position(), charged ? 0.6 : 0.35, charged ? 0.55 : 0.3);
			}
			if (charged) {
				arc(p, centre(e), null, 6, false);
			}
		}
		l.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0, 0, 0, 0);
		l.sendParticles(SPARK, p.getX(), p.getY() + 0.2, p.getZ(), charged ? 60 : 20, r * 0.4, 0.1, r * 0.4, 0.2);
		BlockState under = l.getBlockState(p.blockPosition().below());
		if (!under.isAir()) {
			l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 40, r * 0.35, 0.1,
					r * 0.35, 0.2);
		}
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size((float) r).thickness(0.25f).life(9).ground().spawn();
		OmnitrixSfx.play(l, p.position(), SND_SLAM, 0.75f, charged ? 0.9f : 1.1f);
		return true;
	}

	// ---------------------------------------------------------------- X

	/** X: a huge leap; the landing shocks round him with 10+ Charge. */
	public boolean electroLeap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.scale(1.25).add(0, 0.95 + Math.max(0, look.y) * 0.4, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.4, 0.05, 0.4, 0.15);
		OmnitrixSfx.play(level(p), p.position(), SND_ZAP, 0.6f, 0.9f);
		return true;
	}

	private void leapTick(ServerPlayer p, ServerLevel l, long now) {
		long until = timer(p, LEAP_UNTIL);
		boolean landed = (p.onGround() || p.isInWater()) && now - (until - LEAP_TICKS) > 4;
		if (!landed && now < until) {
			if (now % 2 == 0) {
				l.sendParticles(SPARK, p.getX(), p.getY() + 1.2, p.getZ(), 2, 0.3, 0.4, 0.3, 0.05);
			}
			return;
		}
		setFlag(p, F_LEAP, false);
		setTimer(p, LEAP_UNTIL, 0L);
		p.resetFallDistance();
		if (!landed) {
			return;
		}
		anim(p, ANIM_LAND);
		if (spend(p, LAND_COST)) {
			for (LivingEntity e : around(p, p.position(), LAND_RADIUS)) {
				e.invulnerableTime = 0;
				if (shock(p, e, LAND_DAMAGE)) {
					shove(e, p.position(), 0.5, 0.3);
				}
			}
			l.sendParticles(SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 40, 1.2, 0.1, 1.2, 0.2);
			QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size((float) LAND_RADIUS).thickness(0.2f).life(8).ground().spawn();
			OmnitrixSfx.play(l, p.position(), SND_SLAM, 0.6f, 1.2f);
		} else {
			OmnitrixSfx.play(l, p.position(), SND_SLAM, 0.35f, 1.4f);
		}
	}

	// ---------------------------------------------------------------- Z

	/** Z: ULTIMATE -- the sky answers him: eight strikes on the creatures round him over three seconds. */
	public boolean overchargeStorm(ServerPlayer p) {
		if (flag(p, F_STORM)) {
			return false;
		}
		if (charge(p) < STORM_COST) {
			say(p, "low_charge", STORM_COST);
			return false;
		}
		if (!use(p, STORM)) {
			return false;
		}
		setCharge(p, charge(p) - STORM_COST);
		anim(p, ANIM_STORM);
		setFlag(p, F_STORM, true);
		setTimer(p, STORM_UNTIL, p.level().getGameTime() + STORM_TICKS);
		STRIKES_LEFT.put(p.getUUID(), STORM_STRIKES);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STORM_TICKS, 2, false, false, true));
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(3f).thickness(0.25f).life(12).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.9f, 0.6f);
		return true;
	}

	private void stormTick(ServerPlayer p, ServerLevel l, long now) {
		long until = timer(p, STORM_UNTIL);
		int left = STRIKES_LEFT.getOrDefault(p.getUUID(), 0);
		if (now >= until || left <= 0) {
			setFlag(p, F_STORM, false);
			setTimer(p, STORM_UNTIL, 0L);
			STRIKES_LEFT.remove(p.getUUID());
			return;
		}
		// a strike every STORM_TICKS / STRIKES ticks, the first straight away
		long start = until - STORM_TICKS;
		int due = STORM_STRIKES - (int) ((now - start) * STORM_STRIKES / STORM_TICKS) - 1;
		if (left <= due) {
			return;
		}
		STRIKES_LEFT.put(p.getUUID(), left - 1);
		List<LivingEntity> targets = around(p, p.position(), STORM_RANGE);
		Vec3 at;
		if (!targets.isEmpty()) {
			LivingEntity t = targets.get(p.getRandom().nextInt(targets.size()));
			at = t.position();
		} else {
			double a = p.getRandom().nextDouble() * Math.PI * 2, d = 3 + p.getRandom().nextDouble() * 6;
			at = p.position().add(Math.cos(a) * d, 0, Math.sin(a) * d);
			BlockPos top = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
			at = new Vec3(at.x, top.getY(), at.z);
		}
		strike(p, l, at);
	}

	/** One storm bolt at {@code at}: visual lightning (no fire, no blocks), his own damage to what stands under it. */
	private void strike(ServerPlayer p, ServerLevel l, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			l.addFreshEntity(bolt);
		}
		for (LivingEntity e : around(p, at, 2.5)) {
			e.invulnerableTime = 0;
			if (shock(p, e, STRIKE_DAMAGE)) {
				AlienStatus.stun(p, e, STRIKE_STUN);
			}
		}
		l.sendParticles(SPARK, at.x, at.y + 0.3, at.z, 24, 0.6, 0.3, 0.6, 0.25);
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** Hold V: channelling -- arms up, drinking the current out of the air (more by power, more again in a storm). */
	public boolean startRecharge(ServerPlayer p) {
		if (flag(p, F_RECHARGE)) {
			return false;
		}
		if (!ready(p, RECHARGE)) {
			return use(p, RECHARGE);
		}
		anim(p, ANIM_RECHARGE);
		setFlag(p, F_RECHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.8f, 1f);
		return true;
	}

	/** How fast Recharge fills him here: 1 normally, 2 by power, 3 in a storm under open sky (the better of the two). */
	public static int rechargeRate(ServerPlayer p) {
		ServerLevel l = level(p);
		if (l.isThundering() && l.canSeeSky(p.blockPosition().above())) {
			return 3;
		}
		BlockPos c = p.blockPosition();
		int r = (int) POWER_NEAR;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 3, r))) {
			BlockState s = l.getBlockState(q);
			if (s.is(Blocks.LIGHTNING_ROD) || s.is(Blocks.BEACON) || s.is(Blocks.REDSTONE_BLOCK)
					|| (s.hasProperty(BlockStateProperties.POWER) && s.getValue(BlockStateProperties.POWER) > 0)
					|| (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED))) {
				return 2;
			}
		}
		return 1;
	}

	private void rechargeTick(ServerPlayer p, ServerLevel l, long now) {
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
		if (now % RECHARGE_EVERY != 0) {
			return;
		}
		int rate = rechargeRate(p);
		gain(p, 3 * rate); // 6 a second
		Vec3 top = p.position().add(0, p.getBbHeight() + 0.2, 0);
		l.sendParticles(SPARK, top.x, top.y, top.z, 4 * rate, 0.7, 0.4, 0.7, 0.05);
		if (now % (RECHARGE_EVERY * 3) == 0) {
			OmnitrixSfx.play(l, p.position(), SND_CHARGE, 0.35f + 0.1f * rate, 0.9f + 0.15f * rate);
		}
	}

	public void endRecharge(ServerPlayer p) {
		if (!flag(p, F_RECHARGE)) {
			return;
		}
		setFlag(p, F_RECHARGE, false);
		anim(p, ANIM_RECHARGE_END);
		cooldown(p, RECHARGE, -1);
	}

	/** V (v0.17.4: was Shift+V): he calls a bolt down onto his own electrodes -- +50 Charge, a little health, and a burst round him. */
	public boolean lightningRod(ServerPlayer p) {
		if (!use(p, ROD)) {
			return false;
		}
		anim(p, ANIM_ROD);
		ServerLevel l = level(p);
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
		if (bolt != null) {
			bolt.moveTo(p.getX(), p.getY(), p.getZ());
			bolt.setVisualOnly(true);
			l.addFreshEntity(bolt);
		}
		LAST_LIGHTNING.put(p.getUUID(), p.level().getGameTime()); // (his own bolt: the passive does not count it twice)
		gain(p, ROD_GAIN);
		p.heal(4f);
		for (LivingEntity e : around(p, p.position(), ROD_RADIUS)) {
			e.invulnerableTime = 0;
			if (shock(p, e, ROD_DAMAGE)) {
				shove(e, p.position(), 0.6, 0.25);
			}
		}
		l.sendParticles(SPARK, p.getX(), p.getY() + 1.4, p.getZ(), 40, 0.6, 0.9, 0.6, 0.3);
		OmnitrixSfx.play(l, p.position(), SND_BIG, 0.75f, 0.9f);
		return true;
	}

	// ---------------------------------------------------------------- C

	/** C: grabs the creature ahead and holds it up, shocking it, then throws it. */
	public boolean grab(ServerPlayer p) {
		if (flag(p, F_GRAB)) {
			throwHeld(p); // C again lets go early with the throw
			return true;
		}
		LivingEntity t = lookTarget(p, GRAB_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (!use(p, GRAB)) {
			return false;
		}
		anim(p, ANIM_GRAB);
		GRABS.put(p.getUUID(), new Grab(t.getUUID(), p.level().getGameTime()));
		setFlag(p, F_GRAB, true);
		if (AlienStatus.isBoss(t)) {
			AlienStatus.stun(p, t, GRAB_TICKS); // a boss is too big to lift: pinned where it stands instead
		}
		OmnitrixSfx.playOn(p, SND_PUNCH, 0.6f, 0.8f, SoundSource.PLAYERS);
		return true;
	}

	@Nullable
	private static LivingEntity held(ServerPlayer p) {
		Grab g = GRABS.get(p.getUUID());
		return g != null && level(p).getEntity(g.target) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	private void grabTick(ServerPlayer p, ServerLevel l, long now) {
		Grab g = GRABS.get(p.getUUID());
		LivingEntity t = held(p);
		if (g == null || t == null || t.distanceToSqr(p) > 64) {
			GRABS.remove(p.getUUID());
			setFlag(p, F_GRAB, false);
			return;
		}
		long age = now - g.start;
		if (!AlienStatus.isBoss(t)) {
			// held up at arm's length in front of him
			Vec3 look = p.getLookAngle();
			Vec3 h = new Vec3(look.x, 0, look.z);
			h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
			Vec3 at = p.position().add(h.scale(1.3 + t.getBbWidth() * 0.5)).add(0, p.getBbHeight() * 0.55, 0);
			t.teleportTo(at.x, at.y, at.z);
			t.setDeltaMovement(Vec3.ZERO);
			t.fallDistance = 0f;
			t.hurtMarked = true;
		}
		if (age > 0 && age % GRAB_EVERY == 0) {
			t.invulnerableTime = 0;
			shock(p, t, GRAB_DAMAGE);
			gain(p, 2);
			Vec3 c = centre(t);
			l.sendParticles(SPARK, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.2);
			arc(p, c, null, 5, false);
			OmnitrixSfx.playOn(p, SND_ZAP, 0.6f, 1f, SoundSource.PLAYERS);
		}
		if (age >= GRAB_TICKS) {
			throwHeld(p);
		}
	}

	private void throwHeld(ServerPlayer p) {
		LivingEntity t = held(p);
		GRABS.remove(p.getUUID());
		setFlag(p, F_GRAB, false);
		anim(p, ANIM_THROW);
		if (t == null) {
			return;
		}
		t.invulnerableTime = 0;
		shock(p, t, THROW_DAMAGE);
		Vec3 look = p.getLookAngle();
		double res = 1.0 - Math.min(1.0, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
		t.setDeltaMovement(look.x * 1.6 * res, 0.45 + Math.max(0, look.y) * 0.5, look.z * 1.6 * res);
		t.hurtMarked = true;
		OmnitrixSfx.playOn(p, SND_PUNCH, 0.7f, 0.7f, SoundSource.PLAYERS);
	}

	// ---------------------------------------------------------------- v0.17.4: Hold R Arc Stream

	/** Hold R: a crackling arc from his hand at the crosshair while R is held (up to 3 s), jumping on to one more creature; 4 Charge a second. */
	public boolean startArc(ServerPlayer p) {
		if (flag(p, F_ARC)) {
			return false;
		}
		if (!ready(p, ARC)) {
			return use(p, ARC);
		}
		if (charge(p) <= 0) {
			say(p, "low_charge", 1);
			return false;
		}
		anim(p, ANIM_ARC);
		setTimer(p, ARC_AT, p.level().getGameTime());
		setFlag(p, F_ARC, true);
		OmnitrixSfx.playOn(p, SND_ZAP, 0.7f, 0.9f, SoundSource.PLAYERS);
		return true;
	}

	private void arcTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, ARC_AT);
		if (age >= ARC_MAX || p.isInWater()) {
			endArc(p);
			return;
		}
		Vec3 look = p.getLookAngle();
		com.projecthero.mod.beamclash.BeamClash.report(p, p.getEyePosition(), look, ARC_RANGE, 1f, COLOUR); // every live tick: it can clash
		if (com.projecthero.mod.beamclash.BeamClash.inClash(p)) {
			return; // locked in a clash: the clash does the damage
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 0, false, false, false));
		if (age % ARC_EVERY != 0) {
			return;
		}
		if (!spend(p, 1)) {
			say(p, "low_charge", 1);
			endArc(p);
			return;
		}
		LivingEntity t = lookTarget(p, ARC_RANGE);
		Vec3 end = t != null ? centre(t) : aimPoint(p, ARC_RANGE);
		arc(p, end, null, ARC_EVERY + 1, false);
		if (t != null) {
			t.invulnerableTime = 0;
			shock(p, t, ARC_DAMAGE);
			l.sendParticles(SPARK, end.x, end.y, end.z, 6, 0.2, 0.3, 0.2, 0.15);
			LivingEntity next = nearest(p, t.position(), ARC_JUMP, t);
			if (next != null) {
				next.invulnerableTime = 0;
				shock(p, next, ARC_DAMAGE);
				arc(p, centre(next), t, ARC_EVERY + 1, false);
			}
		} else {
			l.sendParticles(SPARK, end.x, end.y, end.z, 4, 0.15, 0.15, 0.15, 0.1);
		}
		if (age % (ARC_EVERY * 2) == 0) {
			OmnitrixSfx.playOn(p, SND_ZAP, 0.5f, 1.0f + l.random.nextFloat() * 0.3f, SoundSource.PLAYERS);
		}
	}

	public void endArc(ServerPlayer p) {
		if (!flag(p, F_ARC)) {
			return;
		}
		setFlag(p, F_ARC, false);
		setTimer(p, ARC_AT, 0L);
		cooldown(p, ARC, -1);
		anim(p, ANIM_JAB);
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+G Tesla Field / Hold G Charged Slam

	/** Shift+G: two electrodes planted either side of him -- a 4-block field for 5 s, 3 every half second to everything inside (20 Charge). */
	public boolean teslaField(ServerPlayer p) {
		if (flag(p, F_TESLA)) {
			return false;
		}
		if (charge(p) < TESLA_COST) {
			if (cooldownLeft(p, TESLA) > 0) {
				return use(p, TESLA);
			}
			say(p, "low_charge", TESLA_COST);
			return false;
		}
		if (!use(p, TESLA)) {
			return false;
		}
		spend(p, TESLA_COST);
		anim(p, ANIM_TESLA);
		ServerLevel l = level(p);
		Vec3 c = p.position();
		float yaw = p.getYRot();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(TESLA_UNTIL), p.level().getGameTime() + TESLA_TICKS);
		n.timers.put(key(TESLA_X), Math.round(c.x * 1000));
		n.timers.put(key(TESLA_Y), Math.round(c.y * 1000));
		n.timers.put(key(TESLA_Z), Math.round(c.z * 1000));
		n.timers.put(key(TESLA_YAW), (long) Math.round(yaw * 1000));
		n.kitFlags |= F_TESLA;
		Omnitrix.save(p, n);
		for (Vec3 e : electrodes(c, yaw)) {
			QuirkFx.spike(l, e, new Vec3(0, 1, 0)).block(Blocks.LIGHTNING_ROD.defaultBlockState()).colours(WHITE, COLOUR).size(1.6f).size2(0.25f).life(TESLA_TICKS)
					.spawn();
			l.sendParticles(SPARK, e.x, e.y + 0.5, e.z, 16, 0.2, 0.5, 0.2, 0.2);
		}
		QuirkFx.ring(l, c.add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size((float) TESLA_RADIUS).thickness(0.25f).life(12).ground().spawn();
		OmnitrixSfx.play(l, c, SND_SLAM, 0.6f, 1.3f);
		OmnitrixSfx.play(l, c, SND_CHARGE, 0.7f, 1.1f);
		return true;
	}

	/** Where the Tesla Field's two electrodes stand: either side of {@code c}, across {@code yaw}. */
	public static Vec3[] electrodes(Vec3 c, float yaw) {
		double a = Math.toRadians(yaw);
		Vec3 side = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(TESLA_ELECTRODE);
		return new Vec3[] { c.add(side), c.subtract(side) };
	}

	/** The Tesla Field's middle and yaw (client-safe), or null while there is none: {x, y, z, yaw}. */
	@Nullable
	public static double[] tesla(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & F_TESLA) == 0) {
			return null;
		}
		Long x = s.timers.get(ID + "." + TESLA_X), y = s.timers.get(ID + "." + TESLA_Y), z = s.timers.get(ID + "." + TESLA_Z), w = s.timers.get(ID + "." + TESLA_YAW);
		return x == null || y == null || z == null || w == null ? null : new double[] { x / 1000.0, y / 1000.0, z / 1000.0, w / 1000.0 };
	}

	private void teslaTick(ServerPlayer p, ServerLevel l, long now) {
		double[] t = tesla(p);
		if (t == null || now >= timer(p, TESLA_UNTIL)) {
			OmnitrixState n = Omnitrix.state(p).copy();
			for (String k : new String[] { TESLA_UNTIL, TESLA_X, TESLA_Y, TESLA_Z, TESLA_YAW }) {
				n.timers.remove(key(k));
			}
			n.kitFlags &= ~F_TESLA;
			Omnitrix.save(p, n);
			OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.6f, 1f);
			return;
		}
		if ((timer(p, TESLA_UNTIL) - now) % TESLA_EVERY != 0) {
			return;
		}
		Vec3 c = new Vec3(t[0], t[1], t[2]);
		Vec3[] el = electrodes(c, (float) t[3]);
		int i = 0;
		for (LivingEntity e : around(p, c.add(0, 1, 0), TESLA_RADIUS)) {
			e.invulnerableTime = 0;
			shock(p, e, TESLA_DAMAGE);
			Vec3 from = el[i++ % 2].add(0, 1.4, 0);
			QuirkFx.ribbon(l, from, centre(e)).colours(WHITE, COLOUR).size(0.06f).life(4).spawn();
			l.sendParticles(SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.2, 0.3, 0.2, 0.15);
		}
		OmnitrixSfx.play(l, c, SND_ZAP, 0.45f, 0.9f + l.random.nextFloat() * 0.3f);
	}

	/** Hold G: Charged Slam -- both fists raised, winding up while G is held (up to 1.5 s to full; 25 Charge); letting go slams. */
	public boolean startChargedSlam(ServerPlayer p) {
		if (flag(p, F_CSLAM)) {
			return false;
		}
		if (!ready(p, CSLAM)) {
			return use(p, CSLAM);
		}
		if (charge(p) < CSLAM_COST) {
			say(p, "low_charge", CSLAM_COST);
			return false;
		}
		spend(p, CSLAM_COST);
		anim(p, ANIM_CSLAM);
		setTimer(p, CSLAM_START, p.level().getGameTime());
		setFlag(p, F_CSLAM, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.8f, 0.8f);
		return true;
	}

	/** 0..1: how far the Charged Slam has wound up (client-safe). */
	public static float slamCharge(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & F_CSLAM) == 0) {
			return 0f;
		}
		return Mth.clamp((p.level().getGameTime() + partial - s.readyAt(ID + "." + CSLAM_START)) / CSLAM_FULL, 0f, 1f);
	}

	private void cslamTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, CSLAM_START);
		if (age >= CSLAM_MAX) {
			releaseChargedSlam(p);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
		if (now % 3 == 0) {
			Vec3 up = p.position().add(0, p.getBbHeight() + 0.3, 0);
			l.sendParticles(SPARK, up.x, up.y, up.z, 2 + (int) (6 * Math.min(1.0, age / (double) CSLAM_FULL)), 0.4, 0.2, 0.4, 0.1);
		}
		if (age == CSLAM_FULL) {
			OmnitrixSfx.play(l, p.position(), SND_ZAP, 0.7f, 0.7f);
		}
	}

	/** The Charged Slam lands: 17 within 5.5 uncharged up to 22 within 8 at full. Returns how many it hit (-1 if it was not winding up). */
	public int releaseChargedSlam(ServerPlayer p) {
		if (!flag(p, F_CSLAM)) {
			return -1;
		}
		float k = Mth.clamp((p.level().getGameTime() - timer(p, CSLAM_START)) / (float) CSLAM_FULL, 0f, 1f);
		setFlag(p, F_CSLAM, false);
		setTimer(p, CSLAM_START, 0L);
		cooldown(p, CSLAM, -1);
		anim(p, ANIM_SLAM);
		double r = Mth.lerp(k, SLAM_RADIUS, CSLAM_R_MAX);
		float dmg = Mth.lerp(k, CSLAM_DMG_MIN, CSLAM_DMG_MAX);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : around(p, p.position(), r)) {
			if (Math.abs(e.getY() - p.getY()) > 3.0) {
				continue;
			}
			e.invulnerableTime = 0;
			if (shock(p, e, dmg)) {
				shove(e, p.position(), 0.7 + 0.3 * k, 0.6 + 0.2 * k);
				n++;
			}
			arc(p, centre(e), null, 6, k > 0.6f);
		}
		l.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.5, 0, 0.5, 0);
		l.sendParticles(SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 80, r * 0.4, 0.1, r * 0.4, 0.25);
		BlockState under = l.getBlockState(p.blockPosition().below());
		if (!under.isAir()) {
			l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 60, r * 0.35, 0.1,
					r * 0.35, 0.25);
		}
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size((float) r).thickness(0.35f).life(10).ground().spawn();
		QuirkFx.decal(l, p.position().add(0, 0.03, 0)).style(QuirkFx.DECAL_CRACK).size((float) r * 0.6f).life(120).spawn();
		OmnitrixSfx.play(l, p.position(), SND_SLAM, 0.75f, 0.8f - 0.1f * k);
		OmnitrixSfx.play(l, p.position(), SND_BIG, 0.4f + 0.3f * k, 1.1f);
		return n;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+X Lightning Ride

	/** Shift+X: he becomes a bolt and strikes to a spot up to 12 blocks along his aim: 8 to everything on the line (15 Charge). */
	public boolean lightningRide(ServerPlayer p) {
		if (charge(p) < RIDE_COST) {
			if (cooldownLeft(p, RIDE) > 0) {
				return use(p, RIDE);
			}
			say(p, "low_charge", RIDE_COST);
			return false;
		}
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 eye = p.getEyePosition();
		Vec3 end = com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath.beamEnd(p, look, RIDE_RANGE);
		double dist = Math.max(0, end.distanceTo(eye) - 0.7);
		Vec3 feet = null;
		// back off along the line until his body fits
		for (double d = dist; d >= 1.5; d -= 0.5) {
			Vec3 at = eye.add(look.scale(d)).subtract(0, p.getEyeHeight(), 0);
			if (l.noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
				feet = at;
				break;
			}
		}
		if (feet == null) {
			say(p, "no_room");
			return false;
		}
		if (!use(p, RIDE)) {
			return false;
		}
		spend(p, RIDE_COST);
		Vec3 from = p.position();
		for (LivingEntity e : com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath.line(p, look, from.distanceTo(feet) + 0.5, 1.0)) {
			e.invulnerableTime = 0;
			if (shock(p, e, RIDE_DAMAGE)) {
				AlienStatus.stun(p, e, 10);
			}
			l.sendParticles(SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.2, 0.3, 0.2, 0.2);
		}
		l.sendParticles(SPARK, from.x, from.y + 1, from.z, 30, 0.4, 0.8, 0.4, 0.2);
		l.sendParticles(ParticleTypes.FLASH, from.x, from.y + 1, from.z, 1, 0, 0, 0, 0);
		p.teleportTo(feet.x, feet.y, feet.z);
		p.setDeltaMovement(look.scale(0.3));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, RIDE_AT, p.level().getGameTime());
		setFlag(p, F_RIDE, true);
		anim(p, ANIM_RIDE);
		// the bolt he rode, from where he stood to where he is
		arc(p, from.add(0, 1.0, 0), null, 8, true);
		l.sendParticles(SPARK, feet.x, feet.y + 1, feet.z, 30, 0.4, 0.8, 0.4, 0.2);
		QuirkFx.ring(l, feet.add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(2.5f).thickness(0.25f).life(8).ground().spawn();
		OmnitrixSfx.play(l, feet, SND_BIG, 0.6f, 1.3f);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+Z Defibrillate

	/** Shift+Z: a jolt through his own heart (30 Charge): heals 8 and shakes off Slowness, Weakness and Poison; allies within 4 heal 4. */
	public boolean defibrillate(ServerPlayer p) {
		if (charge(p) < DEFIB_COST) {
			if (cooldownLeft(p, DEFIB) > 0) {
				return use(p, DEFIB);
			}
			say(p, "low_charge", DEFIB_COST);
			return false;
		}
		if (!use(p, DEFIB)) {
			return false;
		}
		spend(p, DEFIB_COST);
		anim(p, ANIM_DEFIB);
		ServerLevel l = level(p);
		p.heal(DEFIB_HEAL);
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		p.removeEffect(MobEffects.WEAKNESS);
		p.removeEffect(MobEffects.POISON);
		Vec3 chest = p.position().add(0, p.getBbHeight() * 0.62, 0);
		arc(p, chest, null, 6, true);
		l.sendParticles(SPARK, chest.x, chest.y, chest.z, 30, 0.3, 0.4, 0.3, 0.25);
		l.sendParticles(ParticleTypes.HEART, chest.x, chest.y + 0.9, chest.z, 4, 0.4, 0.2, 0.4, 0);
		for (Player ally : l.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(DEFIB_ALLY_RADIUS))) {
			if (ally != p && ally.isAlive() && com.projecthero.mod.squad.Squads.areAllies(p, ally)) {
				ally.heal(DEFIB_ALLY_HEAL);
				arc(p, ally.position().add(0, ally.getBbHeight() * 0.6, 0), null, 6, false);
				l.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(), 2, 0.3, 0.1, 0.3, 0);
			}
		}
		OmnitrixSfx.play(l, p.position(), SND_BIG, 0.5f, 1.5f);
		OmnitrixSfx.play(l, p.position(), SND_CHARGE, 0.7f, 1.4f);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+V Static Armour

	/** Shift+V: 6 s of crackling skin (15 Charge): whatever hits him up close takes 4 and is knocked back. */
	public boolean staticArmour(ServerPlayer p) {
		if (flag(p, F_STATIC)) {
			return false;
		}
		if (charge(p) < STATIC_COST) {
			if (cooldownLeft(p, STATIC) > 0) {
				return use(p, STATIC);
			}
			say(p, "low_charge", STATIC_COST);
			return false;
		}
		if (!use(p, STATIC)) {
			return false;
		}
		spend(p, STATIC_COST);
		anim(p, ANIM_STATIC);
		setTimer(p, STATIC_UNTIL, p.level().getGameTime() + STATIC_TICKS);
		setFlag(p, F_STATIC, true);
		ServerLevel l = level(p);
		l.sendParticles(SPARK, p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.5, 0.9, 0.5, 0.2);
		QuirkFx.ring(l, p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SOFT).colours(WHITE, COLOUR).size(1.8f).thickness(0.3f).life(10).spawn();
		OmnitrixSfx.play(l, p.position(), SND_CHARGE, 0.8f, 1.3f);
		return true;
	}

	/** {@code p}'s Static Armour is up (client-safe). */
	public static boolean staticUp(Player p) {
		return isFrankenstrike(p) && (Omnitrix.state(p).kitFlags & F_STATIC) != 0;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+C Monster Roar

	/** Shift+C: a thunderous roar (a 10-block cone): 6, Weakness and Slowness 3 s. Returns how many it hit. */
	public int monsterRoar(ServerPlayer p) {
		if (!use(p, ROAR)) {
			return -1;
		}
		anim(p, ANIM_ROAR);
		ServerLevel l = level(p);
		int n = 0;
		for (LivingEntity e : com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath.cone(p, ROAR_RANGE, ROAR_HALF_ANGLE)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, ROAR_DAMAGE, false)) {
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_TICKS, 0), p);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROAR_TICKS, 1), p);
				shove(e, p.position(), 0.6, 0.2);
				n++;
			}
		}
		Vec3 m = p.getEyePosition().add(p.getLookAngle().scale(0.5)).add(0, -0.2, 0);
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < 4; i++) {
			double d = 1.5 + i * 2.2;
			QuirkFx.ring(l, m.add(look.scale(d))).flags(QuirkFx.F_VERTICAL).vec(look).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR)
					.size((float) (0.6 + d * Math.tan(Math.toRadians(ROAR_HALF_ANGLE)))).thickness(0.2f).life(6 + i * 2).spawn();
		}
		l.sendParticles(SPARK, m.x, m.y, m.z, 20, 0.4, 0.3, 0.4, 0.2);
		l.sendParticles(SPARK, p.getX(), p.getY() + p.getBbHeight() * 0.92, p.getZ(), 16, 0.4, 0.1, 0.4, 0.2);
		OmnitrixSfx.play(l, m, SND_ROAR, 1f, 1f);
		return n;
	}

	/** Test hook: whoever he holds right now. */
	@Nullable
	public static LivingEntity holding(ServerPlayer p) {
		return held(p);
	}
}
