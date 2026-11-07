package com.projecthero.mod.omnitrix.alien.feedback;

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
import net.minecraft.core.Direction;
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
 *   Shift+R    Feedback Beam      hold: a beam from his hand for as long as the keys are down and Charge lasts (24 blocks): 6 every 5 ticks, 5 Charge a second; 6 s cooldown from release
 *   G          Storm Burst        25 Charge: electricity out in a 20-block cone, 20 to everything in it
 *   Shift+G    Chain Surge        30 Charge: a bolt at the crosshair that jumps to five more creatures within 10 of each other, 16 each
 *   X          Conductoid Leap    a long agile leap where you look; no fall damage 2 s
 *   Shift+X    Surge Flight       hold: energy shot out behind him drives him where he aims (22 b/s, 36 sprinting), 5 Charge a second
 *   Z          Full Discharge     ULTIMATE (40+ Charge, spends it all): a burst round him, 35 within 3-8 by Charge, stunned 1 s
 *   Shift+Z    Feedback Cannon    50 Charge: 1.5 s charging, slowed, then a piercing beam (32): 100 to everything on it, knocked back
 *   V          Quick Absorb       tap: a 1 s brace (below)
 *   Hold V     Absorb             braced for as long as V is held: shots, energy, fire and blasts hitting him become Charge (x4)
 *   Shift+V    Redirect           sends back what he absorbed in the last 15 s (15 Charge): 12 + 1.5x absorbed, 30 at most
 *   C          Plug Whip          the tail lashes ahead (4 blocks, 140 deg): 12, knocked back; +4 shock with 5 Charge
 *   Shift+C    Plug In            cables plug into what you look at (8) or the nearest power within 6: powered redstone, a lightning
 *                                 rod, a beacon, a charged anchor (drained) -> Charge for 2 s; a creature is drained (3 every 0.5 s,
 *                                 +6 Charge; machines x1.5 and stunned) and reeled in
 *   Attack     Plug Fingers       +3 shock a punch, +3 Charge
 *   Passives   Conductoid (lightning: +40 Charge, never hurts); Energy Sponge (energy hits do half, the rest is Charge); Storm
 *              Feeder (thunder under open sky: +1 Charge a second); Machine Drainer (shocks x1.5 to machines)
 *   Weakness   Overcharge (absorbing past full shocks him: 2 and slowed); Grounded (in water his Charge leaks, 2 a second)
 * </pre>
 */
public final class FeedbackKit extends AlienKit {
	public static final String ID = "feedback";
	/** v0.17.2 round 3 (user: "make his lightning blue, his lightning and powers are blue in the show"): his energy. */
	public static final int COLOUR = 0x2F9BFF;
	public static final int WHITE = 0xD8F0FF;
	/** The sparks off him: vanilla's electric spark is white, so a blue mote does the job instead. */
	public static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.35f, 0.75f, 1.0f), 0.5f);

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_ABSORB = 1, F_PLUG = 2, F_FLY = 4, F_BEAM = 8, F_LEAP = 16, F_LANDING = 32, F_CANNON = 64;
	public static final int ANIM_BEAM_END = 30, ANIM_CANNON_CHARGE = 31;
	public static final int ANIM_BLAST = 1, ANIM_BEAM = 2, ANIM_ABSORB = 3, ANIM_REDIRECT = 4, ANIM_LEAP = 5, ANIM_SURGE = 6, ANIM_WHIP = 7,
			ANIM_PLUG = 8, ANIM_CONE = 9, ANIM_CHAIN = 10, ANIM_DISCHARGE = 11, ANIM_CANNON = 12, ANIM_PUNCH = 13, ANIM_TAKE = 14, ANIM_ABSORB_END = 15, ANIM_LAND = 16, ANIM_PLUG_END = 17;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.feedback.<id>)
	public static final String BLAST = "feedback_blast", BEAM = "feedback_beam", ABSORB = "absorb", QUICK_ABSORB = "quick_absorb", REDIRECT = "redirect",
			LEAP = "conductoid_leap", FLIGHT = "surge_flight", WHIP = "plug_whip", PLUG = "plug_in", CONE = "storm_burst", CHAIN = "chain_surge", DISCHARGE = "full_discharge",
			CANNON = "feedback_cannon", FINGERS = "plug_fingers", CONDUCTOID = "conductoid", SPONGE = "energy_sponge", STORM = "storm_feeder",
			MACHINES = "machine_drainer", OVERCHARGE = "overcharge", GROUNDED = "grounded";

	// ---------------------------------------------------------------- Charge
	public static final int MAX_CHARGE = 100;
	public static final String CHARGE = "charge", ABSORB_UNTIL = "absorb_until", PLUG_UNTIL = "plug_until", PLUG_BLOCK = "plug_block",
			PLUG_ENT = "plug_ent", CANNON_UNTIL = "cannon_until", LEAP_UNTIL = "leap_until";

	// ---------------------------------------------------------------- numbers (the prototype's scale: quick ~9-14, normal 14-22, big 22-28)
	public static final float BLAST_DAMAGE = 14f, SPARK_DAMAGE = 6f, BEAM_DAMAGE = 6f, CONE_DAMAGE = 20f, CHAIN_DAMAGE = 16f, REDIRECT_BASE = 12f, REDIRECT_PER = 1.5f, REDIRECT_MAX = 30f,
			WHIP_DAMAGE = 12f, WHIP_SHOCK = 4f, DRAIN_DAMAGE = 3f, DISCHARGE_DAMAGE = 35f, CANNON_DAMAGE = 100f,
			FINGER_DAMAGE = 3f, OVERCHARGE_DAMAGE = 2f;
	public static final float MACHINE_BONUS = 1.5f, ABSORB_RATE = 4f;
	public static final int BLAST_COST = 10, REDIRECT_COST = 15, WHIP_COST = 5, CONE_COST = 25, CHAIN_COST = 30, DISCHARGE_MIN_CHARGE = 40,
			CANNON_COST = 50, FINGER_GAIN = 3, DRAIN_GAIN = 6, LIGHTNING_GAIN = 40, ABSORB_MIN = 4, FLIGHT_MIN_CHARGE = 5, BEAM_MIN_CHARGE = 5;
	/** Surge Flight spends 1 Charge every this many ticks: 5 a second. */
	public static final int FLIGHT_EVERY = 4;
	/** The Feedback Beam likewise: 1 Charge every this many ticks. */
	public static final int BEAM_DRAIN_EVERY = 4;
	public static final int QUICK_ABSORB_TICKS = 20, ABSORB_COOLDOWN = 80, PLUG_TICKS = 40, LEAP_TICKS = 40, BEAM_EVERY = 5, CHAIN_HITS = 6, CANNON_CHARGE_TICKS = 30,
			BANK_TICKS = 300, DRAIN_EVERY = 10, DISCHARGE_STUN = 20, CANNON_STUN = 10, CHAIN_STUN = 10;
	public static final double BLAST_RANGE = 28, PLUG_RANGE = 8, PLUG_NEAR = 6, WHIP_RANGE = 4, CANNON_RANGE = 32, BEAM_RANGE = 24, CONE_RANGE = 20, CHAIN_RANGE = 10;
	/** Storm Burst's half-angle: cos 35 degrees, a 70-degree cone. */
	public static final double CONE_COS = 0.82;
	public static final float FLY_SPEED = 0.06f;

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
	/** Surge Flight's jets, over and over while the keys are down. */
	public static final OmnitrixSfx.Cue SND_SURGE = OmnitrixSfx.cue("feedback_surge")
			.layer(SoundEvents.BEACON_AMBIENT, 0.5f, 1.8f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.1f, 1.7f)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_FIZZ = OmnitrixSfx.cue("feedback_fizz")
			.layer(SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.6f)
			.gap(8);

	// ---------------------------------------------------------------- server-only state
	/** What Absorb / Energy Sponge took in lately, for Redirect: the amount, who sent it, when it was last fed. */
	private record Bank(float amount, @Nullable UUID from, long at) {
	}

	private static final Map<UUID, Bank> BANKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_LIGHTNING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_OVERCHARGE = new ConcurrentHashMap<>();

	private FeedbackKit() {
	}

	public static void register() {
		Aliens.register(new FeedbackKit());
	}

	public static void clearSessionState() {
		BANKS.clear();
		LAST_TICK.clear();
		LAST_LIGHTNING.clear();
		LAST_OVERCHARGE.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is flying on a held Shift+X right now (client-safe: a synced flag). */
	public static boolean holdFlying(Player p) {
		return isFeedback(p) && (Omnitrix.state(p).kitFlags & F_FLY) != 0;
	}

	/** {@code p} is Feedback right now. */
	public static boolean isFeedback(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// lanky and a little taller than Ben, quick and springy; all his punch is in the energy he banks
		return AlienStats.builder().health(32f).speed(1.2f).scale(1.05f).armor(8f, 0f).attack(6f).jump(0.35f).safeFall(6f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BLAST, AbilitySlot.SLOT_1, 12), AlienMove.shift(BEAM, AbilitySlot.SLOT_1, 120),
				// v0.17.2 round 3 (user: "swap c and shift C abilities with z and shift z abilities please", "swap G and shift G abilities with v
				// and shift V abilities please")
				AlienMove.tap(CONE, AbilitySlot.SLOT_2, 100), AlienMove.shift(CHAIN, AbilitySlot.SLOT_2, 160),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 40), AlienMove.shift(FLIGHT, AbilitySlot.SLOT_3, 20),
				AlienMove.tap(DISCHARGE, AbilitySlot.SLOT_4, 900), AlienMove.shift(CANNON, AbilitySlot.SLOT_4, 300),
				AlienMove.tap(QUICK_ABSORB, AbilitySlot.SLOT_5, ABSORB_COOLDOWN), AlienMove.hold(ABSORB, AbilitySlot.SLOT_5, ABSORB_COOLDOWN),
				AlienMove.shift(REDIRECT, AbilitySlot.SLOT_5, 100),
				AlienMove.tap(WHIP, AbilitySlot.SLOT_6, 50), AlienMove.shift(PLUG, AbilitySlot.SLOT_6, 200),
				AlienMove.vanilla(FINGERS, "Attack", 0), AlienMove.vanilla(CONDUCTOID, "Passive", 0), AlienMove.vanilla(SPONGE, "Passive", 0),
				AlienMove.vanilla(STORM, "Passive", 0), AlienMove.vanilla(MACHINES, "Passive", 0), AlienMove.vanilla(OVERCHARGE, "Weakness", 0),
				AlienMove.vanilla(GROUNDED, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_BEAM);
			case SLOT_3 -> flag(p, F_FLY);
			case SLOT_4 -> flag(p, F_CANNON);
			case SLOT_5 -> flag(p, F_ABSORB);
			case SLOT_6 -> flag(p, F_PLUG);
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
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 24, 0.4, 0.6, 0.4, 0.3);
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

	/** An energy hit on {@code e}: {@code amount}, half again on a machine. */
	public boolean shock(ServerPlayer p, LivingEntity e, float amount) {
		return hurt(p, e, amount * (machine(e) ? MACHINE_BONUS : 1f), false);
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
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.1, p.getZ(), 10, 0.35, 0.5, 0.35, 0.12);
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
		LAST_TICK.remove(id);
		LAST_LIGHTNING.remove(id);
		LAST_OVERCHARGE.remove(id);
		if (flag(p, F_FLY)) {
			RecalKitMath.groundWings(p);
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { CHARGE, ABSORB_UNTIL, PLUG_UNTIL, PLUG_BLOCK, PLUG_ENT, CANNON_UNTIL, LEAP_UNTIL }) {
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
				level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
			}
			p.clearFire();
			return false;
		}
		if ((flag(p, F_LEAP) || flag(p, F_FLY) || flag(p, F_LANDING)) && source.is(DamageTypeTags.IS_FALL)) {
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
		if (flag(p, F_ABSORB)) {
			long until = timer(p, ABSORB_UNTIL); // 0: held, so it lasts as long as V
			if (until > 0L && now >= until) {
				endAbsorb(p);
			} else if (now % 24 == 0) {
				OmnitrixSfx.play(l, p.position(), SND_ABSORB, 0.3f, 0.8f);
			}
		}
		if (flag(p, F_LANDING) && (p.onGround() || p.isInWater())) {
			setFlag(p, F_LANDING, false);
		}
		if (flag(p, F_FLY)) {
			flyTick(p, l, now);
		}
		if (flag(p, F_LEAP) && now >= timer(p, LEAP_UNTIL)) {
			setFlag(p, F_LEAP, false);
		}
		if (flag(p, F_BEAM)) {
			beamTick(p, l, now);
		}
		if (flag(p, F_CANNON)) {
			cannonTick(p, l, now);
		}
		// Storm Feeder: a thunderstorm over his head is a slow meal
		if (now % 20 == 0 && l.isThundering() && l.canSeeSky(p.blockPosition().above())) {
			gain(p, 1, false);
		}
		// Grounded: water bleeds his Charge away
		if (now % 10 == 0 && p.isInWater() && charge(p) > 0) {
			setCharge(p, charge(p) - 1);
			if (now % 20 == 0) {
				l.sendParticles(SPARK, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.3, 0.1, 0.3, 0.1);
				OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.4f, 1.2f);
			}
		}
		if (flag(p, F_PLUG)) {
			plugTick(p, now);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CANNON)) {
			return; // the Cannon's charge has his hands full
		}
		if (flag(p, F_BEAM) && !(slot == AbilitySlot.SLOT_1 && shift)) {
			endBeam(p); // any other move stops the beam
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					startBeam(p);
				} else {
					blast(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					chainSurge(p);
				} else {
					stormBurst(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					surgeFlight(p);
				} else {
					leap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					cannon(p);
				} else {
					discharge(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					redirect(p);
				} else {
					quickAbsorb(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					plugIn(p);
				} else {
					whip(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_5; // v0.17.2 round 3: Absorb lasts as long as V
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_5) {
			absorb(p);
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot == AbilitySlot.SLOT_5) {
			endAbsorb(p);
		}
	}

	@Override
	public void release(ServerPlayer p, AbilitySlot slot, boolean shift, int heldTicks) {
		if (slot == AbilitySlot.SLOT_3 && shift) {
			endFlight(p); // Surge Flight lasts as long as the keys
		}
		if (slot == AbilitySlot.SLOT_1 && shift) {
			endBeam(p); // so does the beam
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
			level(p).sendParticles(SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 5, 0.15, 0.2, 0.15, 0.1);
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

	/**
	 * Shift+R: v0.17.2 (user: "Make his shift+R move a beam ability where player shoots a beam as long as they hold the ability
	 * keybind"; "feedbacks feedback beam ability should drain charge"). No time limit but his Charge, 5 a second; the cooldown starts when
	 * he lets go or runs dry.
	 */
	public boolean startBeam(ServerPlayer p) {
		if (flag(p, F_BEAM)) {
			return false;
		}
		if (!ready(p, BEAM)) {
			return use(p, BEAM); // the usual "not ready" line
		}
		if (charge(p) < BEAM_MIN_CHARGE) {
			say(p, "low_charge", BEAM_MIN_CHARGE);
			return false;
		}
		setFlag(p, F_BEAM, true);
		anim(p, ANIM_BEAM);
		OmnitrixSfx.playOn(p, SND_BIG, 0.5f, 1.5f, SoundSource.PLAYERS);
		return true;
	}

	private void beamTick(ServerPlayer p, ServerLevel l, long now) {
		if (!p.isShiftKeyDown()) {
			endBeam(p); // the Shift half came up first
			return;
		}
		if (charge(p) <= 0) {
			endBeam(p);
			say(p, "beam_empty");
			OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.8f, 1.1f);
			return;
		}
		if (now % BEAM_DRAIN_EVERY == 0) {
			setCharge(p, charge(p) - 1);
		}
		com.projecthero.mod.beamclash.BeamClash.report(p, p.getEyePosition(), p.getLookAngle(), BEAM_RANGE, 1.1f, 0x4FC8FF); // v0.17.3
		if (now % BEAM_EVERY != 0) {
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 end = RecalKitMath.beamEnd(p, look, BEAM_RANGE);
		for (LivingEntity e : RecalKitMath.line(p, look, end.distanceTo(p.getEyePosition()), 0.7)) {
			e.invulnerableTime = 0;
			shock(p, e, BEAM_DAMAGE);
		}
		l.sendParticles(SPARK, end.x, end.y, end.z, 8, 0.25, 0.25, 0.25, 0.2);
		if (now % (BEAM_EVERY * 2) == 0) {
			OmnitrixSfx.playOn(p, SND_ZAP, 0.6f, 0.9f, SoundSource.PLAYERS);
		}
	}

	/** The beam stops: his arm comes down and the cooldown runs from now. */
	public void endBeam(ServerPlayer p) {
		if (!flag(p, F_BEAM)) {
			return;
		}
		setFlag(p, F_BEAM, false);
		anim(p, ANIM_BEAM_END);
		cooldown(p, BEAM, -1);
	}

	// ---------------------------------------------------------------- V / Shift+V

	/**
	 * Hold V: v0.17.2 round 3 (user: "remove time limit of absorb, player can hold v as long as they want to absorb energy"). Braced,
	 * plugs and fingers out, for as long as V is down: what hits him becomes Charge. The cooldown starts when he lets go.
	 */
	public boolean absorb(ServerPlayer p) {
		return brace(p, 0L);
	}

	/** V, tapped: the same brace for a second (the key still has to do something on a tap, and a timed tap catches one shot). */
	public boolean quickAbsorb(ServerPlayer p) {
		return brace(p, p.level().getGameTime() + QUICK_ABSORB_TICKS);
	}

	/** Absorb on, until game time {@code until} (0 = until {@link #endAbsorb}). */
	private boolean brace(ServerPlayer p, long until) {
		if (flag(p, F_ABSORB)) {
			return false;
		}
		if (!ready(p, ABSORB)) {
			return use(p, ABSORB); // the usual "not ready" line
		}
		anim(p, ANIM_ABSORB);
		setTimer(p, ABSORB_UNTIL, until);
		setFlag(p, F_ABSORB, true);
		QuirkFx.ring(level(p), p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(1.6f).thickness(0.15f).life(8).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_ABSORB, 0.9f, 1f);
		return true;
	}

	/** The brace stops: his arms come down and both of V's moves cool down from now. */
	private void endAbsorb(ServerPlayer p) {
		if (!flag(p, F_ABSORB)) {
			return;
		}
		setFlag(p, F_ABSORB, false);
		setTimer(p, ABSORB_UNTIL, 0L);
		anim(p, ANIM_ABSORB_END);
		cooldown(p, ABSORB, -1);
		cooldown(p, QUICK_ABSORB, -1);
	}

	/** Shift+V: what he absorbed lately, sent back -- at the crosshair, else at whoever sent it. */
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
		level(p).sendParticles(SPARK, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.35);
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
		double k = 1.0;
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.scale(1.35 * k).add(0, (0.55 + Math.max(0, look.y) * 0.5) * k, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.25, 0.05, 0.25, 0.08);
		OmnitrixSfx.play(level(p), p.position(), SND_WHIP, 0.6f, 1.6f);
		return true;
	}

	/**
	 * Shift+X down: v0.17.2 round 3 (user: "replace the shift+X move with an ability that uses charge and flies, as long as player holds
	 * shift+X they fly in the direction they are aiming by shooting out energy behind them"). {@code DirectionalFlight} drives him along
	 * his aim until the keys come up ({@link #release}) or his Charge runs out; the jets are {@code FeedbackFx}, drawn from the synced flag.
	 * False (no cooldown) with too little Charge to lift off.
	 */
	public boolean surgeFlight(ServerPlayer p) {
		if (flag(p, F_FLY)) {
			return false;
		}
		if (charge(p) < FLIGHT_MIN_CHARGE) {
			say(p, "low_charge", FLIGHT_MIN_CHARGE);
			return false;
		}
		if (!ready(p, FLIGHT)) {
			return use(p, FLIGHT); // the usual "not ready" line
		}
		setFlag(p, F_FLY, true);
		setFlag(p, F_LANDING, false);
		RecalKitMath.wings(p, true, FLY_SPEED);
		anim(p, ANIM_SURGE);
		p.setDeltaMovement(p.getDeltaMovement().add(0, p.onGround() ? 0.4 : 0.1, 0));
		p.hurtMarked = true;
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 18, 0.35, 0.1, 0.35, 0.1);
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size(2.2f).thickness(0.2f).life(8).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_BIG, 0.5f, 1.5f);
		return true;
	}

	/** One tick of Surge Flight: both keys down, Charge left, the energy going out behind him. */
	private void flyTick(ServerPlayer p, ServerLevel l, long now) {
		if (!p.isShiftKeyDown()) {
			endFlight(p); // "as long as player holds shift+X": the Shift half came up first
			return;
		}
		if (charge(p) <= 0) {
			endFlight(p);
			say(p, "surge_empty");
			OmnitrixSfx.play(l, p.position(), SND_FIZZ, 0.8f, 1.1f);
			return;
		}
		if (!p.getAbilities().flying) {
			RecalKitMath.wings(p, true, FLY_SPEED); // the client landed him for a tick (a scrape along the floor)
		}
		p.resetFallDistance();
		if (now % FLIGHT_EVERY == 0) {
			setCharge(p, charge(p) - 1);
		}
		if (now % 2 == 0) {
			Vec3 tail = p.position().add(0, p.getBbHeight() * 0.45, 0).subtract(p.getLookAngle().scale(1.2));
			l.sendParticles(SPARK, tail.x, tail.y, tail.z, 3, 0.15, 0.15, 0.15, 0.04);
		}
		if (now % 8 == 0) {
			OmnitrixSfx.play(l, p.position(), SND_SURGE, 0.7f, 1f);
		}
	}

	/** Surge Flight stops: the wings go, the cooldown starts, and no fall can hurt him until he lands. */
	private void endFlight(ServerPlayer p) {
		if (!flag(p, F_FLY)) {
			return;
		}
		setFlag(p, F_FLY, false);
		setFlag(p, F_LANDING, !p.onGround());
		RecalKitMath.groundWings(p);
		anim(p, ANIM_LAND);
		cooldown(p, FLIGHT, -1);
	}

	// ---------------------------------------------------------------- C / Shift+C

	/** C: the tail lashes across in front of him; with 5 Charge the plug discharges into what it hits. */
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

	/** Shift+C: cables plug into a creature in the crosshair (drain it) or a power source (drink it). False with nothing to plug into. */
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
		anim(p, ANIM_PLUG_END);
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
				l.sendParticles(SPARK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
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
			l.sendParticles(SPARK, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.05);
		}
		if (now % 10 == 0) {
			suck(p, Vec3.atCenterOf(b), 5);
		}
	}

	// ---------------------------------------------------------------- G / Shift+G

	/** G: v0.17.2 (user: "Make his G move a 20 block cone ability where he shoots out electricity dealing 20 damage"). */
	public boolean stormBurst(ServerPlayer p) {
		if (charge(p) < CONE_COST) {
			say(p, "low_charge", CONE_COST);
			return false;
		}
		if (!use(p, CONE)) {
			return false;
		}
		setCharge(p, charge(p) - CONE_COST);
		anim(p, ANIM_CONE);
		ServerLevel l = level(p);
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		int arcs = 0;
		for (LivingEntity e : around(p, eye, CONE_RANGE)) {
			Vec3 to = centre(e).subtract(eye);
			if (to.length() > CONE_RANGE || to.normalize().dot(look) < CONE_COS || !p.hasLineOfSight(e)) {
				continue;
			}
			e.invulnerableTime = 0;
			if (shock(p, e, CONE_DAMAGE)) {
				shove(e, p.position(), 0.5, 0.2);
			}
			if (arcs++ < 8) {
				arc(p, centre(e), null, 7, true);
			}
		}
		// the fan itself, wherever there was nothing to hit
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		for (int i = 0; i < 9; i++) {
			double a = (i - 4) * 0.155, b = ((i * 5) % 3 - 1) * 0.14;
			Vec3 dir = look.add(right.scale(a)).add(up.scale(b)).normalize();
			Vec3 end = RecalKitMath.beamEnd(p, dir, CONE_RANGE * (0.7 + (i * 7 % 4) * 0.1));
			arc(p, end, null, 6, i % 2 == 0);
		}
		OmnitrixSfx.playOn(p, SND_BIG, 0.75f, 1.2f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+G: v0.17.2 (user: "Make his shift+G move a different attack move"): a bolt that leaps from creature to creature. */
	public boolean chainSurge(ServerPlayer p) {
		LivingEntity first = lookTarget(p, BLAST_RANGE);
		if (first == null) {
			say(p, "no_target");
			return false;
		}
		if (charge(p) < CHAIN_COST) {
			say(p, "low_charge", CHAIN_COST);
			return false;
		}
		if (!use(p, CHAIN)) {
			return false;
		}
		setCharge(p, charge(p) - CHAIN_COST);
		anim(p, ANIM_CHAIN);
		ServerLevel l = level(p);
		java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
		LivingEntity from = null, at = first;
		while (at != null && hit.size() < CHAIN_HITS) {
			hit.add(at);
			at.invulnerableTime = 0;
			if (shock(p, at, CHAIN_DAMAGE)) {
				AlienStatus.stun(p, at, CHAIN_STUN);
			}
			arc(p, centre(at), from, 8, true);
			Vec3 c = centre(at);
			l.sendParticles(SPARK, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.2);
			from = at;
			LivingEntity next = null;
			double best = CHAIN_RANGE * CHAIN_RANGE;
			for (LivingEntity e : around(p, c, CHAIN_RANGE)) {
				double d = e.distanceToSqr(at);
				if (!hit.contains(e) && d < best) {
					best = d;
					next = e;
				}
			}
			at = next;
		}
		OmnitrixSfx.playOn(p, SND_BIG, 0.7f, 1.5f, SoundSource.PLAYERS);
		return true;
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	/** Z (ultimate): everything he holds, let go at once round him. */
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
		double radius = 3 + 5 * (c / (float) MAX_CHARGE);
		ServerLevel l = level(p);
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0);
		int arcs = 0;
		for (LivingEntity e : around(p, at, radius)) {
			e.invulnerableTime = 0;
			if (shock(p, e, DISCHARGE_DAMAGE)) {
				AlienStatus.stun(p, e, machine(e) ? DISCHARGE_STUN * 3 : DISCHARGE_STUN);
				shove(e, p.position(), 1.0, 0.4);
			}
			if (arcs++ < 10) {
				arc(p, centre(e), null, 8, true);
			}
		}
		l.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		l.sendParticles(SPARK, at.x, at.y, at.z, 60, radius * 0.4, 0.8, radius * 0.4, 0.5);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(WHITE, COLOUR).size((float) radius).thickness(0.6f).life(14).ground().spawn();
		OmnitrixSfx.play(l, at, SND_BIG, 1.5f, 0.8f);
		return true;
	}

	/**
	 * Shift+Z: v0.17.2 (user: "increase feedback cannon damage to 100 and add a chargeup animation to it please"). The Charge and the
	 * cooldown go at the press; he slows for {@link #CANNON_CHARGE_TICKS} while the shot builds between his hands, then it fires.
	 */
	public boolean cannon(ServerPlayer p) {
		if (charge(p) < CANNON_COST) {
			say(p, "low_charge", CANNON_COST);
			return false;
		}
		if (!use(p, CANNON)) {
			return false;
		}
		setCharge(p, charge(p) - CANNON_COST);
		anim(p, ANIM_CANNON_CHARGE);
		setTimer(p, CANNON_UNTIL, p.level().getGameTime() + CANNON_CHARGE_TICKS);
		setFlag(p, F_CANNON, true);
		OmnitrixSfx.play(level(p), p.position(), SND_ABSORB, 0.9f, 0.7f);
		return true;
	}

	private void cannonTick(ServerPlayer p, ServerLevel l, long now) {
		long until = timer(p, CANNON_UNTIL);
		if (now >= until) {
			setFlag(p, F_CANNON, false);
			setTimer(p, CANNON_UNTIL, 0L);
			fireCannon(p);
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false, false));
		if (now % 6 == 0) {
			float k = 1f - (until - now) / (float) CANNON_CHARGE_TICKS;
			OmnitrixSfx.playOn(p, SND_ZAP, 0.5f + 0.25f * k, 0.8f + k, SoundSource.PLAYERS);
		}
	}

	/** How far the Cannon's charge has built, 0..1 (client-safe: a synced timer; 0 when it isn't charging). */
	public static float cannonCharge(Player p, float partial) {
		AlienKit kit = Aliens.kit(ID);
		long until = kit == null ? 0L : kit.timer(p, CANNON_UNTIL);
		return until == 0L ? 0f : Mth.clamp(1f - (until - p.level().getGameTime() - partial) / CANNON_CHARGE_TICKS, 0f, 1f);
	}

	private void fireCannon(ServerPlayer p) {
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
		l.sendParticles(SPARK, end.x, end.y, end.z, 30, 0.5, 0.5, 0.5, 0.4);
		QuirkFx.blast(l, end).colours(WHITE, COLOUR).size(1.4f).life(8).spawn();
		OmnitrixSfx.playOn(p, SND_BIG, 0.75f, 0.9f, SoundSource.PLAYERS);
	}
}
