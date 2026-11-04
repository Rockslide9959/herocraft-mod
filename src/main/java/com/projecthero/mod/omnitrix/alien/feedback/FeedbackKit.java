package com.projecthero.mod.omnitrix.alien.feedback;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

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
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "build a feedback alien please, do research on his powers and abilites in the show and base his moves on that please,
 * add him to the mistransformation list"). Feedback, a Conductoid (prototype Omnitrix, a hidden alien): a lanky black alien whose two
 * antenna cables, tail and fingertips end in plugs. He ABSORBS energy -- lightning, fireballs, energy blasts, shots fired at him while
 * he braces -- and drains it from power sources he plugs into (powered redstone, lightning rods, beacons, Upgrade's machines), banks it
 * as <b>Charge</b> (0-100) and redirects it as blasts and discharges. Agile, and he whips with his tail and cables. Spec:
 * {@code docs/OMNITRIX_REVAMP_FEEDBACK.md}.
 *
 * <pre>
 *   R          Feedback Blast     a fingertip bolt at the crosshair (28; 10 Charge): 14; with less, a Static Spark (6)
 *   Shift+R    Rapid Fire         five bolts from alternating hands over a second (25 Charge): 6 each
 *   G          Absorb             3 s brace: shots, energy, fire and blasts hitting him become Charge (x4) instead of damage
 *   Shift+G    Redirect           sends back what he absorbed in the last 15 s (15 Charge): 12 + 1.5x absorbed, 30 at most
 *   X          Conductoid Leap    a long agile leap where you look; no fall damage 2 s
 *   Shift+X    Cable Swing        an antenna plugs into the block you look at (24) and swings / yanks you to it
 *   Z          Plug Whip          the tail lashes ahead (4 blocks, 140 deg): 12, knocked back; +4 shock with 5 Charge
 *   Shift+Z    Plug In            cables plug into what you look at (8) or the nearest power within 6: powered redstone, a lightning
 *                                 rod, a beacon, a charged anchor (drained) -> Charge for 2 s; a creature is drained (3 every 0.5 s,
 *                                 +6 Charge; machines x1.5 and stunned) and reeled in
 *   V          Power Feed         25 Charge into 6 health; clears slowness and weakness
 *   Shift+V    Overclock          30 Charge: 10 s of speed, higher leaps and blasts x1.25
 *   C          Full Discharge     ULTIMATE (40+ Charge, spends it all): a burst round him, 14-30 within 3-8 by Charge, stunned 1 s
 *   Shift+C    Feedback Cannon    50 Charge: a piercing beam (32): 24 to everything on it, knocked back
 *   Attack     Plug Fingers       +3 shock a punch, +3 Charge
 *   Passives   Conductoid (lightning: +40 Charge, never hurts); Energy Sponge (energy hits do half, the rest is Charge); Storm
 *              Feeder (thunder under open sky: +1 Charge a second); Machine Drainer (shocks x1.5 to machines)
 *   Weakness   Overcharge (absorbing past full shocks him: 2 and slowed); Grounded (in water his Charge leaks, 2 a second)
 * </pre>
 */
public final class FeedbackKit extends AlienKit {
	public static final String ID = "feedback";
	/** His energy: a pale electric yellow-white, like the gold of his plugs lit up. */
	public static final int COLOUR = 0xF2E25A;
	public static final int WHITE = 0xFFFBE0;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_ABSORB = 1, F_PLUG = 2, F_SWING = 4, F_OVERCLOCK = 8, F_LEAP = 16;
	public static final int ANIM_BLAST = 1, ANIM_RAPID = 2, ANIM_ABSORB = 3, ANIM_REDIRECT = 4, ANIM_LEAP = 5, ANIM_SWING = 6, ANIM_WHIP = 7,
			ANIM_PLUG = 8, ANIM_FEED = 9, ANIM_OVERCLOCK = 10, ANIM_DISCHARGE = 11, ANIM_CANNON = 12, ANIM_PUNCH = 13, ANIM_TAKE = 14;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.feedback.<id>)
	public static final String BLAST = "feedback_blast", RAPID = "rapid_fire", ABSORB = "absorb", REDIRECT = "redirect", LEAP = "conductoid_leap",
			SWING = "cable_swing", WHIP = "plug_whip", PLUG = "plug_in", FEED = "power_feed", OVERCLOCK = "overclock", DISCHARGE = "full_discharge",
			CANNON = "feedback_cannon", FINGERS = "plug_fingers", CONDUCTOID = "conductoid", SPONGE = "energy_sponge", STORM = "storm_feeder",
			MACHINES = "machine_drainer", OVERCHARGE = "overcharge", GROUNDED = "grounded";

	// ---------------------------------------------------------------- Charge
	public static final int MAX_CHARGE = 100;
	public static final String CHARGE = "charge", ABSORB_UNTIL = "absorb_until", PLUG_UNTIL = "plug_until", PLUG_BLOCK = "plug_block",
			PLUG_ENT = "plug_ent", SWING_X = "swing_x", SWING_Y = "swing_y", SWING_Z = "swing_z", SWING_UNTIL = "swing_until", OVERCLOCK_UNTIL = "overclock_until", LEAP_UNTIL = "leap_until";

	// ---------------------------------------------------------------- numbers (the prototype's scale: quick ~9-14, normal 14-22, big 22-28)
	public static final float BLAST_DAMAGE = 14f, SPARK_DAMAGE = 6f, RAPID_DAMAGE = 6f, REDIRECT_BASE = 12f, REDIRECT_PER = 1.5f, REDIRECT_MAX = 30f,
			WHIP_DAMAGE = 12f, WHIP_SHOCK = 4f, DRAIN_DAMAGE = 3f, FEED_HEAL = 6f, DISCHARGE_MIN = 14f, DISCHARGE_MAX = 30f, CANNON_DAMAGE = 24f,
			FINGER_DAMAGE = 3f, OVERCHARGE_DAMAGE = 2f;
	public static final float MACHINE_BONUS = 1.5f, OVERCLOCK_BONUS = 1.25f, ABSORB_RATE = 4f;
	public static final int BLAST_COST = 10, RAPID_COST = 25, REDIRECT_COST = 15, WHIP_COST = 5, FEED_COST = 25, OVERCLOCK_COST = 30, DISCHARGE_MIN_CHARGE = 40,
			CANNON_COST = 50, FINGER_GAIN = 3, DRAIN_GAIN = 6, LIGHTNING_GAIN = 40, ABSORB_MIN = 4;
	public static final int ABSORB_TICKS = 60, PLUG_TICKS = 40, SWING_TICKS = 16, OVERCLOCK_TICKS = 200, LEAP_TICKS = 40, RAPID_SHOTS = 5, RAPID_EVERY = 3,
			BANK_TICKS = 300, DRAIN_EVERY = 10, DISCHARGE_STUN = 20, CANNON_STUN = 10;
	public static final double BLAST_RANGE = 28, PLUG_RANGE = 8, PLUG_NEAR = 6, SWING_RANGE = 24, WHIP_RANGE = 4, CANNON_RANGE = 32;

	// ---------------------------------------------------------------- sounds (the sounds test: volume <= 0.75, pitch >= 0.5)
	public static final OmnitrixSfx.Cue SND_ZAP = OmnitrixSfx.cue("feedback_zap")
			.layer(SoundEvents.TRIDENT_THUNDER, 0.08f, 1.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.3f, 1.4f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.15f, 1.6f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_BIG = OmnitrixSfx.cue("feedback_big")
			.layer(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35f, 1.2f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.4f, 1.4f)
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.12f, 1.5f, 1)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_ABSORB = OmnitrixSfx.cue("feedback_absorb")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.35f, 1.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.2f)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_PLUG = OmnitrixSfx.cue("feedback_plug")
			.layer(SoundEvents.CHAIN_PLACE, 0.6f, 1.4f)
			.layer(SoundEvents.COMPARATOR_CLICK, 0.6f, 0.9f)
			.layer(SoundEvents.BEACON_AMBIENT, 0.5f, 1.6f, 2);
	public static final OmnitrixSfx.Cue SND_WHIP = OmnitrixSfx.cue("feedback_whip")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.3f)
			.layer(SoundEvents.CHAIN_HIT, 0.5f, 1.1f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_FIZZ = OmnitrixSfx.cue("feedback_fizz")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.6f)
			.gap(8);

	// ---------------------------------------------------------------- server-only state
	/** What Absorb / Energy Sponge took in lately, for Redirect: the amount, who sent it, when it was last fed. */
	private record Bank(float amount, @Nullable UUID from, long at) {
	}

	private record Rapid(int left, long next) {
	}

	private static final Map<UUID, Bank> BANKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Rapid> RAPIDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_LIGHTNING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_OVERCHARGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> SWING_TO = new ConcurrentHashMap<>();

	private FeedbackKit() {
	}

	public static void register() {
		Aliens.register(new FeedbackKit());
	}

	public static void clearSessionState() {
		BANKS.clear();
		RAPIDS.clear();
		LAST_TICK.clear();
		LAST_LIGHTNING.clear();
		LAST_OVERCHARGE.clear();
		SWING_TO.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Feedback right now. */
	public static boolean isFeedback(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// lanky and a little taller than Ben, quick and springy; all his punch is in the energy he banks
		return AlienStats.builder().health(32f).speed(1.2f).scale(1.05f).armor(8f, 0f).attack(3f).jump(0.35f).safeFall(6f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BLAST, AbilitySlot.SLOT_1, 12), AlienMove.shift(RAPID, AbilitySlot.SLOT_1, 140),
				AlienMove.tap(ABSORB, AbilitySlot.SLOT_2, 160), AlienMove.shift(REDIRECT, AbilitySlot.SLOT_2, 100),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 40), AlienMove.shift(SWING, AbilitySlot.SLOT_3, 60),
				AlienMove.tap(WHIP, AbilitySlot.SLOT_4, 50), AlienMove.shift(PLUG, AbilitySlot.SLOT_4, 200),
				AlienMove.tap(FEED, AbilitySlot.SLOT_5, 100), AlienMove.shift(OVERCLOCK, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(DISCHARGE, AbilitySlot.SLOT_6, 900), AlienMove.shift(CANNON, AbilitySlot.SLOT_6, 300),
				AlienMove.vanilla(FINGERS, "Attack", 0), AlienMove.vanilla(CONDUCTOID, "Passive", 0), AlienMove.vanilla(SPONGE, "Passive", 0),
				AlienMove.vanilla(STORM, "Passive", 0), AlienMove.vanilla(MACHINES, "Passive", 0), AlienMove.vanilla(OVERCHARGE, "Weakness", 0),
				AlienMove.vanilla(GROUNDED, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> flag(p, F_ABSORB);
			case SLOT_4 -> flag(p, F_PLUG);
			case SLOT_5 -> flag(p, F_OVERCLOCK);
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
			n.timers.put(key(CHARGE), (long) c);
		}
		Omnitrix.save(p, n);
	}

	/** Test hook / the tune command: sets his Charge outright. */
	public void fill(ServerPlayer p, int c) {
		setCharge(p, c);
	}

	/**
	 * Adds {@code points} of Charge. Past full the rest is Overcharge: from an absorbed hit ({@code absorbed}) it shocks him (2 and
	 * slowed, once a second). Returns what went in.
	 */
	public int gain(ServerPlayer p, int points, boolean absorbed) {
		int c = charge(p);
		int add = Math.min(points, MAX_CHARGE - c);
		setCharge(p, c + add);
		if (absorbed && points > add) {
			overcharge(p);
		}
		return add;
	}

	/** Spends {@code points} if he has them; false (and the message) if not. */
	private boolean spend(ServerPlayer p, int points) {
		int c = charge(p);
		if (c < points) {
			say(p, "low_charge", points);
			return false;
		}
		setCharge(p, c - points);
		return true;
	}

	private void overcharge(ServerPlayer p) {
		long now = p.level().getGameTime();
		Long last = LAST_OVERCHARGE.get(p.getUUID());
		if (last != null && now - last < 20) {
			return;
		}
		LAST_OVERCHARGE.put(p.getUUID(), now);
		int keep = p.invulnerableTime;
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), OVERCHARGE_DAMAGE);
		p.invulnerableTime = keep;
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.4, 0.6, 0.4, 0.3);
		OmnitrixSfx.play(level(p), p.position(), SND_FIZZ, 1f, 0.8f);
		say(p, "overcharged");
	}

	/** Test hook: what Redirect would send back right now. */
	public static float banked(ServerPlayer p) {
		Bank b = BANKS.get(p.getUUID());
		return b == null || p.level().getGameTime() - b.at > BANK_TICKS ? 0f : b.amount;
	}

	private static void bank(ServerPlayer p, float amount, @Nullable Entity from) {
		long now = p.level().getGameTime();
		Bank b = BANKS.get(p.getUUID());
		float had = b == null || now - b.at > BANK_TICKS ? 0f : b.amount;
		UUID src = from != null && from != p ? from.getUUID() : b == null ? null : b.from;
		BANKS.put(p.getUUID(), new Bank(had + amount, src, now));
	}

	// ---------------------------------------------------------------- shocks

	/** A machine to his electricity: Upgrade's machines (golems, mechanical mobs). */
	public static boolean machine(LivingEntity e) {
		return UpgradeKit.machine(e);
	}

	/** An energy hit on {@code e}: {@code amount}, half again on a machine, a quarter more while Overclocked. */
	public boolean shock(ServerPlayer p, LivingEntity e, float amount) {
		float k = (machine(e) ? MACHINE_BONUS : 1f) * (flag(p, F_OVERCLOCK) ? OVERCLOCK_BONUS : 1f);
		return hurt(p, e, amount * k, false);
	}

	/** A bolt drawn to {@code end}: from his hand (from null), or from entity {@code from}. Lasts {@code life} ticks. */
	public static void arc(ServerPlayer p, Vec3 end, @Nullable Entity from, int life, boolean big) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, big ? "feedback_big" : "feedback_arc");
		a.moveTo(end.x, end.y, end.z, 0f, 0f);
		double len = from == null ? p.getEyePosition().distanceTo(end) : from.position().distanceTo(end);
		a.colour(from == null ? 0 : from.getId() + 1).size((float) Math.max(0.6, len / 3.0)).held(true).speed(0).life(life).damage(0f).spawn();
	}

	/** An energy stream pulled INTO him from {@code from} (an absorbed hit, a drained creature): the reversed-arc carrier. */
	public static void suck(ServerPlayer p, Vec3 from, int life) {
		AlienProjectileEntity a = AlienProjectileEntity.of(p, "feedback_suck");
		a.moveTo(from.x, from.y, from.z, 0f, 0f);
		a.size((float) Math.max(0.6, p.getEyePosition().distanceTo(from) / 3.0)).held(true).speed(0).life(life).damage(0f).spawn();
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

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.feedback." + key, args).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- what counts as energy

	/** An energy hit (Energy Sponge takes half): lightning, fireballs, wither skulls, the Warden's boom, dragon breath, magic bolts, alien blasts. */
	public static boolean energy(DamageSource s) {
		if (s.is(DamageTypes.LIGHTNING_BOLT) || s.is(DamageTypes.FIREBALL) || s.is(DamageTypes.UNATTRIBUTED_FIREBALL) || s.is(DamageTypes.WITHER_SKULL)
				|| s.is(DamageTypes.SONIC_BOOM) || s.is(DamageTypes.DRAGON_BREATH) || s.is(DamageTypes.INDIRECT_MAGIC)) {
			return true;
		}
		Entity d = s.getDirectEntity();
		return d instanceof AlienProjectileEntity || d instanceof AbstractHurtingProjectile || d instanceof ShulkerBullet;
	}

	/** What Absorb drinks: energy, every shot, fire and blasts. */
	public static boolean absorbable(DamageSource s) {
		return energy(s) || s.is(DamageTypeTags.IS_PROJECTILE) || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.IS_FIRE);
	}

	/** Takes {@code amount} of {@code s} in as Charge (and into the Redirect bank), with the suck-in visual. */
	private void drink(ServerPlayer p, DamageSource s, float amount) {
		gain(p, Math.max(ABSORB_MIN, Math.round(amount * ABSORB_RATE)), true);
		bank(p, amount, s.getEntity());
		Entity from = s.getDirectEntity() != null ? s.getDirectEntity() : s.getEntity();
		if (from != null && from != p && from.distanceToSqr(p) < 40 * 40) {
			suck(p, centre(from), 6);
		}
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.1, p.getZ(), 10, 0.35, 0.5, 0.35, 0.12);
		OmnitrixSfx.play(level(p), p.position(), SND_ABSORB, 0.8f, 1.2f);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_PLUG, 0.8f, 1.1f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		UUID id = p.getUUID();
		BANKS.remove(id);
		RAPIDS.remove(id);
		LAST_TICK.remove(id);
		LAST_LIGHTNING.remove(id);
		LAST_OVERCHARGE.remove(id);
		SWING_TO.remove(id);
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { CHARGE, ABSORB_UNTIL, PLUG_UNTIL, PLUG_BLOCK, PLUG_ENT, SWING_X, SWING_Y, SWING_Z, SWING_UNTIL, OVERCLOCK_UNTIL, LEAP_UNTIL }) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.LIGHTNING_BOLT)) {
			// Conductoid: a strike is a feast, never a wound (once a second: a bolt hits for several ticks)
			long now = p.level().getGameTime();
			Long last = LAST_LIGHTNING.get(p.getUUID());
			if (last == null || now - last > 20) {
				LAST_LIGHTNING.put(p.getUUID(), now);
				gain(p, LIGHTNING_GAIN, true);
				bank(p, 10f, null);
				OmnitrixSfx.play(level(p), p.position(), SND_ABSORB, 1f, 0.8f);
				level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
			}
			p.clearFire();
			return false;
		}
		if ((flag(p, F_LEAP) || flag(p, F_SWING)) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		if (flag(p, F_ABSORB) && absorbable(source)) {
			// Absorb: it goes into his plugs and fingertips instead
			drink(p, source, amount);
			anim(p, ANIM_TAKE);
			if (source.is(DamageTypeTags.IS_FIRE)) {
				p.clearFire();
			}
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (energy(source) && !source.is(DamageTypes.LIGHTNING_BOLT)) {
			// Energy Sponge: half of any energy hit is drunk, even unbraced
			float taken = amount * 0.5f;
			drink(p, source, taken);
			return amount - taken;
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
		if (flag(p, F_ABSORB) && now >= timer(p, ABSORB_UNTIL)) {
			setFlag(p, F_ABSORB, false);
		}
		if (flag(p, F_LEAP) && now >= timer(p, LEAP_UNTIL)) {
			setFlag(p, F_LEAP, false);
		}
		if (flag(p, F_OVERCLOCK)) {
			if (now >= timer(p, OVERCLOCK_UNTIL)) {
				setFlag(p, F_OVERCLOCK, false);
			} else if (now % 10 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15, 1, false, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.JUMP, 15, 1, false, false, false));
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.3, 0.6, 0.3, 0.05);
			}
		}
		// Storm Feeder: a thunderstorm over his head is a slow meal
		if (now % 20 == 0 && l.isThundering() && l.canSeeSky(p.blockPosition().above())) {
			gain(p, 1, false);
		}
		// Grounded: water bleeds his Charge away
		if (now % 10 == 0 && p.isInWater() && charge(p) > 0) {
			setCharge(p, charge(p) - 1);
			if (now % 20 == 0) {
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.3, 0.1, 0.3, 0.1);
				OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.4f, 1.2f);
			}
		}
		// Rapid Fire's remaining shots
		Rapid r = RAPIDS.get(p.getUUID());
		if (r != null && now >= r.next) {
			rapidShot(p, r.left);
			if (r.left <= 1) {
				RAPIDS.remove(p.getUUID());
			} else {
				RAPIDS.put(p.getUUID(), new Rapid(r.left - 1, now + RAPID_EVERY));
			}
		}
		if (flag(p, F_PLUG)) {
			plugTick(p, now);
		}
		if (flag(p, F_SWING)) {
			swingTick(p, now);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					rapidFire(p);
				} else {
					blast(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					redirect(p);
				} else {
					absorb(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					cableSwing(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					plugIn(p);
				} else {
					whip(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					overclock(p);
				} else {
					feed(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					cannon(p);
				} else {
					discharge(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (!(target instanceof LivingEntity e)) {
			return false;
		}
		// Plug Fingers: the plug-tipped fingers bite with a shock and sip the creature's energy; vanilla's swing still lands (+3 attack)
		anim(p, ANIM_PUNCH);
		float k = p.getAttackStrengthScale(0.5f);
		if (k > 0.5f) {
			e.invulnerableTime = 0;
			shock(p, e, FINGER_DAMAGE * k);
			gain(p, FINGER_GAIN, false);
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 5, 0.15, 0.2, 0.15, 0.1);
		}
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: a fingertip bolt to the creature under the crosshair, else where it points; a weak Static Spark with under 10 Charge. */
	public boolean blast(ServerPlayer p) {
		if (!use(p, BLAST)) {
			return false;
		}
		boolean charged = charge(p) >= BLAST_COST;
		if (charged) {
			setCharge(p, charge(p) - BLAST_COST);
		}
		anim(p, ANIM_BLAST);
		LivingEntity t = lookTarget(p, BLAST_RANGE);
		if (t != null) {
			if (shock(p, t, charged ? BLAST_DAMAGE : SPARK_DAMAGE) && charged) {
				shove(t, p.position(), 0.4, 0.15);
			}
			arc(p, centre(t), null, 6, charged);
		} else {
			arc(p, aimPoint(p, BLAST_RANGE), null, 6, charged);
		}
		OmnitrixSfx.playOn(p, charged ? SND_BIG : SND_ZAP, charged ? 0.7f : 0.8f, charged ? 1.3f : 1.2f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: five quick bolts from alternating hands. */
	public boolean rapidFire(ServerPlayer p) {
		if (charge(p) < RAPID_COST) {
			say(p, "low_charge", RAPID_COST);
			return false;
		}
		if (!use(p, RAPID)) {
			return false;
		}
		setCharge(p, charge(p) - RAPID_COST);
		anim(p, ANIM_RAPID);
		rapidShot(p, RAPID_SHOTS);
		RAPIDS.put(p.getUUID(), new Rapid(RAPID_SHOTS - 1, p.level().getGameTime() + RAPID_EVERY));
		return true;
	}

	private void rapidShot(ServerPlayer p, int left) {
		LivingEntity t = lookTarget(p, BLAST_RANGE);
		if (t != null) {
			t.invulnerableTime = 0;
			shock(p, t, RAPID_DAMAGE);
			arc(p, centre(t), null, 4, false);
		} else {
			arc(p, aimPoint(p, BLAST_RANGE), null, 4, false);
		}
		OmnitrixSfx.playOn(p, SND_ZAP, 0.7f, 1.1f + left * 0.08f, SoundSource.PLAYERS);
	}

	/** Test hook: Rapid Fire shots still to come. */
	public static int rapidLeft(ServerPlayer p) {
		Rapid r = RAPIDS.get(p.getUUID());
		return r == null ? 0 : r.left;
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** G: three seconds braced, plugs and fingers out: what hits him becomes Charge. */
	public boolean absorb(ServerPlayer p) {
		if (!use(p, ABSORB)) {
			return false;
		}
		anim(p, ANIM_ABSORB);
		setTimer(p, ABSORB_UNTIL, p.level().getGameTime() + ABSORB_TICKS);
		setFlag(p, F_ABSORB, true);
		QuirkFx.ring(level(p), p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(1.6f).thickness(0.15f).life(8).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_ABSORB, 0.9f, 1f);
		return true;
	}

	/** Shift+G: what he absorbed lately, sent back -- at the crosshair, else at whoever sent it. */
	public boolean redirect(ServerPlayer p) {
		float banked = banked(p);
		if (banked <= 0f) {
			say(p, "nothing_absorbed");
			return false;
		}
		LivingEntity t = lookTarget(p, BLAST_RANGE);
		if (t == null) {
			Bank b = BANKS.get(p.getUUID());
			if (b != null && b.from != null && level(p).getEntity(b.from) instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < BLAST_RANGE * BLAST_RANGE) {
				t = le;
			}
		}
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		if (charge(p) < REDIRECT_COST) {
			say(p, "low_charge", REDIRECT_COST);
			return false;
		}
		if (!use(p, REDIRECT)) {
			return false;
		}
		setCharge(p, charge(p) - REDIRECT_COST);
		anim(p, ANIM_REDIRECT);
		float dmg = Math.min(REDIRECT_MAX, REDIRECT_BASE + banked * REDIRECT_PER);
		t.invulnerableTime = 0;
		if (shock(p, t, dmg)) {
			shove(t, p.position(), 0.8, 0.25);
		}
		arc(p, centre(t), null, 9, true);
		BANKS.remove(p.getUUID());
		Vec3 c = centre(t);
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.35);
		QuirkFx.blast(level(p), c).colours(WHITE, COLOUR).size(1.6f).life(8).spawn();
		OmnitrixSfx.playOn(p, SND_BIG, 0.75f, 1.0f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- X / Shift+X

	/** X: a long springy leap where he looks. */
	public boolean leap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 look = p.getLookAngle();
		double k = flag(p, F_OVERCLOCK) ? 1.3 : 1.0;
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.scale(1.35 * k).add(0, (0.55 + Math.max(0, look.y) * 0.5) * k, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.25, 0.05, 0.25, 0.08);
		OmnitrixSfx.play(level(p), p.position(), SND_WHIP, 0.6f, 1.6f);
		return true;
	}

	/** Shift+X: an antenna cable plugs into the block he looks at and yanks him to it. False (no cooldown) with nothing in reach. */
	public boolean cableSwing(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(SWING_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK) {
			say(p, "no_anchor");
			return false;
		}
		if (!use(p, SWING)) {
			return false;
		}
		anim(p, ANIM_SWING);
		Vec3 at = hit.getLocation();
		SWING_TO.put(p.getUUID(), at);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy(); // raw bits: a negative coordinate must survive (setTimer drops values <= 0)
		n.timers.put(key(SWING_X), Double.doubleToLongBits(at.x));
		n.timers.put(key(SWING_Y), Double.doubleToLongBits(at.y));
		n.timers.put(key(SWING_Z), Double.doubleToLongBits(at.z));
		n.timers.put(key(SWING_UNTIL), now + SWING_TICKS);
		Omnitrix.save(p, n);
		setFlag(p, F_SWING, true);
		p.setDeltaMovement(p.getDeltaMovement().add(0, 0.35, 0));
		p.hurtMarked = true;
		OmnitrixSfx.play(level(p), at, SND_PLUG, 0.8f, 1.3f);
		return true;
	}

	/** Where the swinging cable is plugged in, or null; client-safe. */
	@Nullable
	public static Vec3 swingAnchor(Player p) {
		var t = Omnitrix.state(p).timers;
		Long x = t.get(ID + "." + SWING_X), y = t.get(ID + "." + SWING_Y), z = t.get(ID + "." + SWING_Z);
		return x == null || y == null || z == null ? null : new Vec3(Double.longBitsToDouble(x), Double.longBitsToDouble(y), Double.longBitsToDouble(z));
	}

	private void swingTick(ServerPlayer p, long now) {
		Vec3 to = SWING_TO.get(p.getUUID());
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0);
		if (to == null || now >= timer(p, SWING_UNTIL) || at.distanceToSqr(to) < 2.2 * 2.2) {
			endSwing(p);
			return;
		}
		Vec3 d = to.subtract(at).normalize();
		Vec3 v = p.getDeltaMovement().scale(0.55).add(d.scale(1.05));
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.resetFallDistance();
	}

	private void endSwing(ServerPlayer p) {
		SWING_TO.remove(p.getUUID());
		setFlag(p, F_SWING, false);
		// a short grace for the landing
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		p.resetFallDistance();
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Z: the tail lashes across in front of him; with 5 Charge the plug discharges into what it hits. */
	public boolean whip(ServerPlayer p) {
		if (!use(p, WHIP)) {
			return false;
		}
		anim(p, ANIM_WHIP);
		boolean charged = charge(p) >= WHIP_COST;
		boolean any = false;
		for (LivingEntity e : RecalKitMath.cone(p, WHIP_RANGE, 70)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, WHIP_DAMAGE, false)) {
				shove(e, p.position(), 0.9, 0.3);
				any = true;
				if (charged) {
					e.invulnerableTime = 0;
					shock(p, e, WHIP_SHOCK);
					arc(p, centre(e), null, 4, false);
				}
			}
		}
		if (charged && any) {
			setCharge(p, charge(p) - WHIP_COST);
		}
		Vec3 front = p.position().add(0, 1.0, 0).add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.8));
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0, 0, 0, 0);
		OmnitrixSfx.play(level(p), p.position(), SND_WHIP, 1f, 1f);
		return true;
	}

	/**
	 * What {@code s} at {@code pos} gives him per tick when he plugs into it (0 = no power): powered redstone and its parts 1, a
	 * redstone block, a beacon or a charged respawn anchor 2, a powered lightning rod 3, a lit redstone lamp / furnace 1.
	 */
	public static int power(Level level, BlockPos pos, BlockState s) {
		if (s.isAir()) {
			return 0;
		}
		if (s.is(Blocks.LIGHTNING_ROD)) {
			return s.getValue(BlockStateProperties.POWERED) ? 3 : 0;
		}
		if (s.is(Blocks.REDSTONE_BLOCK) || s.is(Blocks.BEACON)) {
			return 2;
		}
		if (s.is(Blocks.RESPAWN_ANCHOR)) {
			return s.getValue(RespawnAnchorBlock.CHARGE) > 0 ? 2 : 0;
		}
		if (s.is(Blocks.REDSTONE_WIRE)) {
			return s.getValue(BlockStateProperties.POWER) > 0 ? 1 : 0;
		}
		if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT)) {
			return 1;
		}
		if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED)) {
			return 1;
		}
		if (s.isSignalSource()) {
			for (Direction d : Direction.values()) {
				if (s.getSignal(level, pos, d) > 0) {
					return 1;
				}
			}
		}
		return 0;
	}

	/** The power source he plugs into: the block he looks at (8) if powered, else the nearest within 6; or null. */
	@Nullable
	public static BlockPos powerAhead(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(PLUG_RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.BLOCK && power(p.level(), hit.getBlockPos(), p.level().getBlockState(hit.getBlockPos())) > 0) {
			return hit.getBlockPos();
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos c = BlockPos.containing(eye);
		int r = (int) PLUG_NEAR;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			double d = Vec3.atCenterOf(q).distanceToSqr(eye);
			if (d <= PLUG_NEAR * PLUG_NEAR && d < bestD && power(p.level(), q, p.level().getBlockState(q)) > 0) {
				bestD = d;
				best = q.immutable();
			}
		}
		return best;
	}

	/** Shift+Z: cables plug into a creature in the crosshair (drain it) or a power source (drink it). False with nothing to plug into. */
	public boolean plugIn(ServerPlayer p) {
		LivingEntity t = lookTarget(p, PLUG_RANGE);
		BlockPos b = t == null ? powerAhead(p) : null;
		if (t == null && b == null) {
			say(p, "no_power");
			return false;
		}
		if (!use(p, PLUG)) {
			return false;
		}
		anim(p, ANIM_PLUG);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(PLUG_BLOCK));
		n.timers.remove(key(PLUG_ENT));
		if (t != null) {
			n.timers.put(key(PLUG_ENT), (long) t.getId() + 1);
		} else {
			n.timers.put(key(PLUG_BLOCK), b.asLong());
		}
		n.timers.put(key(PLUG_UNTIL), now + PLUG_TICKS);
		Omnitrix.save(p, n);
		setFlag(p, F_PLUG, true);
		OmnitrixSfx.play(level(p), t != null ? t.position() : Vec3.atCenterOf(b), SND_PLUG, 1f, 1f);
		return true;
	}

	/** Plugged-in creature id + 1 (0 = none); client-safe. */
	public static int plugEntity(Player p) {
		return (int) Omnitrix.state(p).readyAt(ID + "." + PLUG_ENT);
	}

	/** The plugged-in block, or null; client-safe. */
	@Nullable
	public static BlockPos plugBlock(Player p) {
		var t = Omnitrix.state(p).timers;
		Long v = t.get(ID + "." + PLUG_BLOCK);
		return v == null ? null : BlockPos.of(v);
	}

	private void endPlug(ServerPlayer p, @Nullable LivingEntity reel) {
		setFlag(p, F_PLUG, false);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(PLUG_BLOCK));
		n.timers.remove(key(PLUG_ENT));
		n.timers.remove(key(PLUG_UNTIL));
		Omnitrix.save(p, n);
		if (reel != null && reel.isAlive() && !AlienStatus.isBoss(reel)) {
			// the cables reel it in
			Vec3 d = p.position().subtract(reel.position());
			double res = 1.0 - Math.min(1.0, reel.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
			reel.push(d.x * 0.25 * res, 0.3 * res, d.z * 0.25 * res);
			reel.hurtMarked = true;
		}
	}

	private void plugTick(ServerPlayer p, long now) {
		ServerLevel l = level(p);
		boolean over = now >= timer(p, PLUG_UNTIL) || charge(p) >= MAX_CHARGE;
		int ent = plugEntity(p);
		if (ent > 0) {
			Entity e = l.getEntity(ent - 1);
			if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceToSqr(p) > (PLUG_RANGE + 2) * (PLUG_RANGE + 2)) {
				endPlug(p, null);
				return;
			}
			long start = timer(p, PLUG_UNTIL) - PLUG_TICKS;
			if ((now - start) % DRAIN_EVERY == DRAIN_EVERY - 1) {
				t.invulnerableTime = 0;
				shock(p, t, DRAIN_DAMAGE);
				boolean m = machine(t);
				gain(p, m ? Math.round(DRAIN_GAIN * MACHINE_BONUS) : DRAIN_GAIN, false);
				if (m) {
					AlienStatus.stun(p, t, DRAIN_EVERY + 4);
				}
				suck(p, centre(t), 5);
				l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
			}
			if (now >= timer(p, PLUG_UNTIL)) {
				endPlug(p, t);
			}
			return;
		}
		BlockPos b = plugBlock(p);
		if (b == null) {
			endPlug(p, null);
			return;
		}
		BlockState s = l.getBlockState(b);
		int rate = power(l, b, s);
		if (rate <= 0 || over || Vec3.atCenterOf(b).distanceToSqr(p.getEyePosition()) > (PLUG_RANGE + 2) * (PLUG_RANGE + 2)) {
			endPlug(p, null);
			return;
		}
		gain(p, rate, false);
		if (s.is(Blocks.RESPAWN_ANCHOR) && now % 20 == 0) {
			// drained: an anchor gives up a charge a second
			l.setBlockAndUpdate(b, s.setValue(RespawnAnchorBlock.CHARGE, s.getValue(RespawnAnchorBlock.CHARGE) - 1));
		}
		if (now % 4 == 0) {
			Vec3 c = Vec3.atCenterOf(b);
			l.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.05);
		}
		if (now % 10 == 0) {
			suck(p, Vec3.atCenterOf(b), 5);
		}
	}

	// ---------------------------------------------------------------- V / Shift+V

	/** V: banked energy into his own body. */
	public boolean feed(ServerPlayer p) {
		if (charge(p) < FEED_COST) {
			say(p, "low_charge", FEED_COST);
			return false;
		}
		if (!use(p, FEED)) {
			return false;
		}
		setCharge(p, charge(p) - FEED_COST);
		anim(p, ANIM_FEED);
		p.heal(FEED_HEAL);
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		p.removeEffect(MobEffects.WEAKNESS);
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.3, 0.6, 0.3, 0.08);
		level(p).sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.4, p.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_ABSORB, 0.8f, 1.5f);
		return true;
	}

	/** Shift+V: 10 s supercharged -- faster, higher leaps, blasts a quarter harder. */
	public boolean overclock(ServerPlayer p) {
		if (flag(p, F_OVERCLOCK)) {
			return false;
		}
		if (charge(p) < OVERCLOCK_COST) {
			say(p, "low_charge", OVERCLOCK_COST);
			return false;
		}
		if (!use(p, OVERCLOCK)) {
			return false;
		}
		setCharge(p, charge(p) - OVERCLOCK_COST);
		anim(p, ANIM_OVERCLOCK);
		setTimer(p, OVERCLOCK_UNTIL, p.level().getGameTime() + OVERCLOCK_TICKS);
		setFlag(p, F_OVERCLOCK, true);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15, 1, false, false, false));
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(2.5f).thickness(0.3f).life(10).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_BIG, 0.9f, 1.4f);
		return true;
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C (ultimate): everything he holds, let go at once round him. */
	public boolean discharge(ServerPlayer p) {
		int c = charge(p);
		if (c < DISCHARGE_MIN_CHARGE) {
			say(p, "low_charge", DISCHARGE_MIN_CHARGE);
			return false;
		}
		if (!use(p, DISCHARGE)) {
			return false;
		}
		setCharge(p, 0);
		anim(p, ANIM_DISCHARGE);
		float k = c / (float) MAX_CHARGE;
		double radius = 3 + 5 * k;
		float dmg = Mth.lerp(k, DISCHARGE_MIN, DISCHARGE_MAX);
		ServerLevel l = level(p);
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0);
		int arcs = 0;
		for (LivingEntity e : around(p, at, radius)) {
			e.invulnerableTime = 0;
			if (shock(p, e, dmg)) {
				AlienStatus.stun(p, e, machine(e) ? DISCHARGE_STUN * 3 : DISCHARGE_STUN);
				shove(e, p.position(), 1.0, 0.4);
			}
			if (arcs++ < 10) {
				arc(p, centre(e), null, 8, true);
			}
		}
		l.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 60, radius * 0.4, 0.8, radius * 0.4, 0.5);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size((float) radius).thickness(0.6f).life(14).ground().spawn();
		OmnitrixSfx.play(l, at, SND_BIG, 1.5f, 0.8f);
		return true;
	}

	/** Shift+C: a piercing beam of banked energy. */
	public boolean cannon(ServerPlayer p) {
		if (charge(p) < CANNON_COST) {
			say(p, "low_charge", CANNON_COST);
			return false;
		}
		if (!use(p, CANNON)) {
			return false;
		}
		setCharge(p, charge(p) - CANNON_COST);
		anim(p, ANIM_CANNON);
		Vec3 look = p.getLookAngle();
		Vec3 end = RecalKitMath.beamEnd(p, look, CANNON_RANGE);
		double len = end.distanceTo(p.getEyePosition());
		for (LivingEntity e : RecalKitMath.line(p, look, len, 1.0)) {
			e.invulnerableTime = 0;
			if (shock(p, e, CANNON_DAMAGE)) {
				AlienStatus.stun(p, e, CANNON_STUN);
				shove(e, p.position(), 1.1, 0.3);
			}
		}
		ServerLevel l = level(p);
		Vec3 from = p.getEyePosition().add(look.scale(0.6)).subtract(0, 0.25, 0);
		QuirkFx.ribbon(l, from, end).style(QuirkFx.RIBBON_LIGHT).colours(WHITE, COLOUR).size(0.5f).life(10).spawn();
		arc(p, end, null, 10, true);
		l.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 30, 0.5, 0.5, 0.5, 0.4);
		QuirkFx.blast(l, end).colours(WHITE, COLOUR).size(1.4f).life(8).spawn();
		OmnitrixSfx.playOn(p, SND_BIG, 0.75f, 0.9f, SoundSource.PLAYERS);
		return true;
	}
}
