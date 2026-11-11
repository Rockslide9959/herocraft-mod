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
 *   V          Quick Recharge     tap: draws the static out of the air, +8 Charge
 *   Hold V     Recharge           channelled for as long as V is held: +6 Charge a second, x2 by power (lightning rod, powered redstone,
 *                                 beacon within 6), x3 in a storm under open sky; slowed
 *   Shift+V    Lightning Rod      calls a bolt down onto his own bolts: +50 Charge, +4 health, 8 to everything within 3
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
	public static final int F_RECHARGE = 1, F_LEAP = 2, F_GRAB = 4, F_STORM = 8;
	public static final int ANIM_PUNCH = 1, ANIM_BOLT = 2, ANIM_SLAM = 3, ANIM_LEAP = 4, ANIM_LAND = 5, ANIM_STORM = 6, ANIM_GRAB = 7, ANIM_THROW = 8,
			ANIM_RECHARGE = 9, ANIM_RECHARGE_END = 10, ANIM_ROD = 11, ANIM_QUICK = 12, ANIM_JAB = 13;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.frankenstrike.<id>)
	public static final String PUNCH = "shock_punch", BOLT = "lightning_bolt", SLAM = "conductor_slam", LEAP = "electro_leap", STORM = "overcharge_storm",
			GRAB = "grab_and_shock", QUICK = "quick_recharge", RECHARGE = "recharge", ROD = "lightning_rod", FISTS = "galvanic_fists", TRANSYLIAN = "transylian",
			BRUTE = "brute_strength", DYNAMO = "living_dynamo", GROUNDED = "grounded", LUMBERING = "lumbering";

	// ---------------------------------------------------------------- Charge
	public static final int MAX_CHARGE = 100;
	public static final String CHARGE = "charge", LEAP_UNTIL = "leap_until", STORM_UNTIL = "storm_until";

	// ---------------------------------------------------------------- numbers (basic 5-8, specials 12-17, big 17-20, ultimates 21-36)
	public static final float PUNCH_DAMAGE = 8f, CHAIN_DAMAGE = 6f, BOLT_DAMAGE = 15f, SPARK_DAMAGE = 7f, SLAM_DAMAGE = 17f, SLAM_WEAK = 9f, LAND_DAMAGE = 10f,
			STRIKE_DAMAGE = 24f, GRAB_DAMAGE = 4f, THROW_DAMAGE = 4f, ROD_DAMAGE = 8f, FIST_SHOCK = 3f;
	public static final float MACHINE_BONUS = 1.5f;
	public static final int BOLT_COST = 15, SLAM_COST = 20, LAND_COST = 10, STORM_COST = 50, PUNCH_GAIN = 6, QUICK_GAIN = 8, ROD_GAIN = 50, LIGHTNING_GAIN = 50,
			FIST_GAIN = 4, FIST_SHOCK_MIN = 50;
	public static final int STORM_TICKS = 60, STORM_STRIKES = 8, GRAB_TICKS = 40, GRAB_EVERY = 10, LEAP_TICKS = 60, RECHARGE_EVERY = 10, STRIKE_STUN = 20;
	public static final double PUNCH_RANGE = 4, CHAIN_RANGE = 6, BOLT_RANGE = 28, SLAM_RADIUS = 5.5, SLAM_WEAK_RADIUS = 3.5, LAND_RADIUS = 3.5,
			STORM_RANGE = 14, GRAB_RANGE = 4, ROD_RADIUS = 3, POWER_NEAR = 6;

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
		return List.of(AlienMove.tap(PUNCH, AbilitySlot.SLOT_1, 16), AlienMove.shift(BOLT, AbilitySlot.SLOT_1, 30),
				AlienMove.tap(SLAM, AbilitySlot.SLOT_2, 140),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 100),
				AlienMove.tap(STORM, AbilitySlot.SLOT_4, 900),
				AlienMove.tap(QUICK, AbilitySlot.SLOT_5, 60), AlienMove.hold(RECHARGE, AbilitySlot.SLOT_5, 40), AlienMove.shift(ROD, AbilitySlot.SLOT_5, 900),
				AlienMove.tap(GRAB, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(TRANSYLIAN, "Passive", 0), AlienMove.vanilla(BRUTE, "Passive", 0),
				AlienMove.vanilla(DYNAMO, "Passive", 0), AlienMove.vanilla(GROUNDED, "Weakness", 0), AlienMove.vanilla(LUMBERING, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_LEAP);
			case SLOT_4 -> flag(p, F_STORM);
			case SLOT_5 -> flag(p, F_RECHARGE);
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
		for (String t : new String[] { CHARGE, LEAP_UNTIL, STORM_UNTIL }) {
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
		if ((flag(p, F_LEAP)) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		return true;
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
			case SLOT_2 -> conductorSlam(p);
			case SLOT_3 -> electroLeap(p);
			case SLOT_4 -> overchargeStorm(p);
			case SLOT_5 -> {
				if (shift) {
					lightningRod(p);
				} else {
					quickRecharge(p);
				}
			}
			case SLOT_6 -> grab(p);
			default -> {
			}
		}
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5;
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_5) {
			startRecharge(p);
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot == AbilitySlot.SLOT_5) {
			endRecharge(p);
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

	/** V, tapped: the static off the air, +8 Charge. */
	public boolean quickRecharge(ServerPlayer p) {
		if (!use(p, QUICK)) {
			return false;
		}
		anim(p, ANIM_QUICK);
		gain(p, QUICK_GAIN);
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 2.0, p.getZ(), 14, 0.6, 0.3, 0.6, 0.1);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.6f, 1.3f);
		return true;
	}

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
		cooldown(p, QUICK, -1);
	}

	/** Shift+V: he calls a bolt down onto his own electrodes -- +50 Charge, a little health, and a burst round him. */
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

	/** Test hook: whoever he holds right now. */
	@Nullable
	public static LivingEntity holding(ServerPlayer p) {
		return held(p);
	}
}
