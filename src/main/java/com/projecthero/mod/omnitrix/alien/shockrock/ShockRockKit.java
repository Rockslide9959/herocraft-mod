package com.projecthero.mod.omnitrix.alien.shockrock;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Shock Rock (Ben 10 reboot, 2016; a hidden
 * prototype alien found by a mistransform): a Fulmini, a being of living energy held in a suit of rock. He fires blasts of it, shapes it
 * into constructs -- a blade, a shield -- and his rock chunks can fly apart and back together. Very durable. His energy (the user's skin:
 * a bright cyan) is drawn full-bright and the rock matte; in Overload the energy burns brighter still.
 * <p>
 * v0.17.4 revamp (docs/OMNITRIX_PROTOTYPE_REVAMP_0174.md): nothing removed -- the kit made sense, it only lacked Shift and hold moves.
 *
 * <pre>
 *   R / Shift+R / Hold R   Energy Blast (8) / Rapid Bolts (five of 5) / Charged Bolt (charge up to 1.5 s: a piercing bolt, 10 to 20)
 *   G / Shift+G / Hold G   Energy Blade (10 s / 6 hits of 13) / Blade Wave (a piercing crescent: 14; 18 with the blade up, which spends 2 of
 *                          its hits) / Energy Lash (a tether on the creature you look at, up to 2 s: 3 every 0.25 s, dragged toward you)
 *   X / Shift+X / Hold X   Energy Dash (6 to things passed) / Updraft Jet (10 blocks straight up) / Power Glide (jet forward low, up to 2 s:
 *                          6 once to things passed)
 *   Z / Shift+Z / Hold Z   Rock Burst (18 within 6) / Rock Barrage (six plates of 4 in a cone) / Core Overload Burst (charge up to 2 s: 18
 *                          within 6 to 28 within 9)
 *   V / Shift+V / Hold V   Overload Mode (+35% damage while Energy drains) / Armour Reform (8 absorption for 10 s) / Energy Channel (+10
 *                          Energy a second, slowed)
 *   C / Shift+C / Hold C   Shield Construct (5 s front shield) / Energy Dome (4 s: shots and blasts from outside stopped) / Reflect (the shield
 *                          up to 3 s: shots fly back at the shooter)
 *   Attack     Rock Fists (+6)
 *   Passives   Rock Armour (shots hurt a third less; hard to knock back); Fulmini Core (lightning heals him and fills his Energy)
 *   Weakness   Short Circuit (in water his Energy drains and Overload cuts out)
 * </pre>
 */
public final class ShockRockKit extends AlienKit {
	public static final String ID = "shock_rock";
	/** His energy: the user's skin paints it a bright cyan. */
	public static final int ENERGY = 0x23DADA, ENERGY_HOT = 0xC9F8F8, ROCK = 0x343042;
	public static final DustParticleOptions SPARK = new DustParticleOptions(new org.joml.Vector3f(0.4f, 1.0f, 1.0f), 0.6f);

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_BLADE = 1, F_SHIELD = 2, F_OVERLOAD = 4, F_DASH = 8, F_RAPID = 16, F_CHARGE = 32, F_LASH = 64, F_JET = 128, F_GLIDE = 256,
			F_CORE = 512, F_CHANNEL = 1024, F_DOME = 2048, F_REFLECT = 4096;
	public static final int ANIM_BLAST = 1, ANIM_RAPID = 2, ANIM_BLADE = 3, ANIM_BURST = 4, ANIM_DASH = 5, ANIM_SHIELD = 6, ANIM_OVERLOAD = 7, ANIM_SLASH = 8,
			ANIM_PUNCH = 9, ANIM_SHIELD_END = 10, ANIM_CHARGE = 11, ANIM_CHARGED = 12, ANIM_WAVE = 13, ANIM_LASH = 14, ANIM_UPDRAFT = 15, ANIM_GLIDE = 16,
			ANIM_BARRAGE = 17, ANIM_CORE = 18, ANIM_REFORM = 19, ANIM_CHANNEL = 20, ANIM_DOME = 21, ANIM_REFLECT = 22;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.shock_rock.<id>)
	public static final String BLAST = "energy_blast", RAPID = "rapid_bolts", CHARGED = "charged_bolt", BLADE = "energy_blade", WAVE = "blade_wave",
			LASH = "energy_lash", DASH = "energy_dash", UPDRAFT = "updraft_jet", GLIDE = "power_glide", BURST = "rock_burst", BARRAGE = "rock_barrage",
			CORE_BURST = "core_overload_burst", OVERLOAD = "overload_mode", REFORM = "armour_reform", CHANNEL = "energy_channel", SHIELD = "shield_construct",
			DOME = "energy_dome", REFLECT = "reflect", FISTS = "rock_fists", ARMOUR = "rock_armour", CORE = "fulmini_core", SHORT = "short_circuit";
	public static final String ENERGY_KEY = "energy", BLADE_UNTIL = "blade_until", BLADE_HITS = "blade_hits", SHIELD_UNTIL = "shield_until", DASH_UNTIL = "dash_until",
			RAPID_START = "rapid_start", CHARGE_START = "charge_start", LASH_AT = "lash_at", LASH_TARGET = "lash_target", GLIDE_AT = "glide_at",
			CORE_START = "core_start", DOME_UNTIL = "dome_until", DOME_X = "dome_x", DOME_Y = "dome_y", DOME_Z = "dome_z", REFLECT_AT = "reflect_at";
	private static final String[] TIMERS = { ENERGY_KEY, BLADE_UNTIL, BLADE_HITS, SHIELD_UNTIL, DASH_UNTIL, RAPID_START, CHARGE_START, LASH_AT, LASH_TARGET,
			GLIDE_AT, CORE_START, DOME_UNTIL, DOME_X, DOME_Y, DOME_Z, REFLECT_AT };

	// ---------------------------------------------------------------- numbers (quick 5-8, normal 12-17, big 17-20, ultimate 21+)
	public static final float BLAST_DAMAGE = 8f, BOLT_DAMAGE = 5f, CHARGED_MIN = 10f, CHARGED_MAX = 20f, BLADE_DAMAGE = 13f, BLADE_CLEAVE = 6f, WAVE_DAMAGE = 14f,
			WAVE_BLADE_DAMAGE = 18f, LASH_DAMAGE = 3f, BURST_DAMAGE = 18f, BARRAGE_DAMAGE = 4f, CORE_DMG_MIN = 18f, CORE_DMG_MAX = 28f, DASH_DAMAGE = 6f, GLIDE_DAMAGE = 6f,
			OVERLOAD_BONUS = 1.35f, SHOT_TAKEN = 0.67f, REFORM_ABSORB = 8f;
	public static final int MAX_ENERGY = 100, OVERLOAD_MIN = 25, BLADE_TICKS = 200, BLADE_MAX_HITS = 6, WAVE_BLADE_COST = 2, SHIELD_TICKS = 100, DASH_TICKS = 6,
			BOLTS = 5, BOLT_EVERY = 2, BURST_TICKS = 24, BARRAGE_SHARDS = 6, REFORM_TICKS = 200, DOME_TICKS = 80;
	/** The holds: the charge to full, the longest run, the pulse gaps (ticks). */
	public static final int CHARGE_FULL = 30, CHARGE_MAX = 70, LASH_MAX = 40, LASH_EVERY = 5, GLIDE_MAX = 40, CORE_FULL = 40, CORE_MAX = 100, REFLECT_MAX = 60,
			CHANNEL_EVERY = 2, WAVE_TICKS = 10;
	/** Overload spends 1 Energy every this many ticks (about 7 a second); it comes back 1 every {@link #REFILL_EVERY} (3 a second). */
	public static final int DRAIN_EVERY = 3, REFILL_EVERY = 7, WATER_DRAIN_EVERY = 5;
	public static final double BURST_RADIUS = 6, CORE_R_MIN = 6, CORE_R_MAX = 9, BLADE_CLEAVE_RADIUS = 1.8, DASH_SPEED = 1.9, SHIELD_COS = 0.34,
			CHARGED_RANGE = 28, WAVE_SPEED = 1.6, WAVE_HIT = 1.4, LASH_RANGE = 16, UPDRAFT_SPEED = 1.25, GLIDE_SPEED = 1.0, BARRAGE_HALF_ANGLE = 20,
			DOME_RADIUS = 4;

	// ---------------------------------------------------------------- sounds (the sounds test: volume <= 0.75, pitch >= 0.5)
	public static final OmnitrixSfx.Cue SND_BLAST = OmnitrixSfx.cue("shock_rock_blast")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.35f, 1.8f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.2f, 1.5f)
			.layer(SoundEvents.STONE_HIT, 0.4f, 0.8f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_BOLT = OmnitrixSfx.cue("shock_rock_bolt")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.25f, 2.0f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.3f, 1.6f)
			.gap(1);
	public static final OmnitrixSfx.Cue SND_FORM = OmnitrixSfx.cue("shock_rock_construct")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.4f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 0.9f)
			.layer(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.3f, 1.4f, 2)
			.gap(4);
	public static final OmnitrixSfx.Cue SND_SLASH = OmnitrixSfx.cue("shock_rock_slash")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.2f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.25f, 1.9f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_BURST = OmnitrixSfx.cue("shock_rock_burst")
			.layer(SoundEvents.STONE_BREAK, 0.7f, 0.6f)
			.layer(SoundEvents.DEEPSLATE_BREAK, 0.6f, 0.7f, 1)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.4f)
			.layer(SoundEvents.STONE_PLACE, 0.7f, 0.6f, 12)
			.gap(8);
	public static final OmnitrixSfx.Cue SND_DASH = OmnitrixSfx.cue("shock_rock_dash")
			.layer(SoundEvents.BEACON_AMBIENT, 0.6f, 1.9f)
			.layer(SoundEvents.FIRECHARGE_USE, 0.25f, 1.2f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_BLOCK = OmnitrixSfx.cue("shock_rock_block")
			.layer(SoundEvents.SHIELD_BLOCK, 0.6f, 1.3f)
			.layer(SoundEvents.AMETHYST_BLOCK_HIT, 0.5f, 1.2f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_OVERLOAD = OmnitrixSfx.cue("shock_rock_overload")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.5f, 1.2f)
			.layer(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5f, 0.9f)
			.layer(SoundEvents.TRIDENT_THUNDER, 0.08f, 1.8f, 3)
			.gap(6);
	public static final OmnitrixSfx.Cue SND_FADE = OmnitrixSfx.cue("shock_rock_fade")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.45f, 1.3f)
			.gap(6);
	/** v0.17.4: the hum of a charge building (Charged Bolt, Core Overload Burst, Energy Channel). */
	public static final OmnitrixSfx.Cue SND_CHARGE = OmnitrixSfx.cue("shock_rock_charge")
			.layer(SoundEvents.BEACON_AMBIENT, 0.6f, 1.4f)
			.layer(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.3f, 1.2f)
			.gap(8);
	/** v0.17.4: rock plates thrown (Rock Barrage) or snapping back on (Armour Reform). */
	public static final OmnitrixSfx.Cue SND_ROCK = OmnitrixSfx.cue("shock_rock_plates")
			.layer(SoundEvents.STONE_BREAK, 0.6f, 0.9f)
			.layer(SoundEvents.DEEPSLATE_HIT, 0.6f, 0.8f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.2f, 1.7f)
			.gap(3);

	// ---------------------------------------------------------------- server-only state
	/** Blade Wave in flight: its drawn carrier, its way, when it left, what it has cut, its damage. */
	private record Wave(AlienProjectileEntity carrier, Vec3 dir, long start, Set<Integer> hit, float damage) {
	}

	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<Integer>> DASHED = new ConcurrentHashMap<>();
	private static final Map<UUID, Wave> WAVES = new ConcurrentHashMap<>();

	private ShockRockKit() {
	}

	public static void register() {
		Aliens.register(new ShockRockKit());
	}

	public static void clearSessionState() {
		LAST_TICK.clear();
		DASHED.clear();
		WAVES.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Shock Rock right now (client-safe). */
	public static boolean isShockRock(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// a big, heavy rock body: slow to move and slower to knock down
		return AlienStats.builder().health(44f).speed(1.0f).scale(1.1f).armor(14f, 4f).knockbackResist(0.4f).attack(6f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(BLAST, AbilitySlot.SLOT_1, 12), AlienMove.shift(RAPID, AbilitySlot.SLOT_1, 70), AlienMove.hold(CHARGED, AbilitySlot.SLOT_1, 80),
				AlienMove.tap(BLADE, AbilitySlot.SLOT_2, 240), AlienMove.shift(WAVE, AbilitySlot.SLOT_2, 120), AlienMove.hold(LASH, AbilitySlot.SLOT_2, 200),
				AlienMove.tap(DASH, AbilitySlot.SLOT_3, 50), AlienMove.shift(UPDRAFT, AbilitySlot.SLOT_3, 100), AlienMove.hold(GLIDE, AbilitySlot.SLOT_3, 120),
				AlienMove.tap(BURST, AbilitySlot.SLOT_4, 360), AlienMove.shift(BARRAGE, AbilitySlot.SLOT_4, 240), AlienMove.hold(CORE_BURST, AbilitySlot.SLOT_4, 500),
				AlienMove.tap(OVERLOAD, AbilitySlot.SLOT_5, 40), AlienMove.shift(REFORM, AbilitySlot.SLOT_5, 600), AlienMove.hold(CHANNEL, AbilitySlot.SLOT_5, 0),
				AlienMove.tap(SHIELD, AbilitySlot.SLOT_6, 200), AlienMove.shift(DOME, AbilitySlot.SLOT_6, 400), AlienMove.hold(REFLECT, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(ARMOUR, "Passive", 0), AlienMove.vanilla(CORE, "Passive", 0),
				AlienMove.vanilla(SHORT, "Weakness", 0));
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot.ordinal() <= AbilitySlot.SLOT_6.ordinal();
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> flag(p, F_CHARGE);
			case SLOT_2 -> flag(p, F_BLADE) || flag(p, F_LASH);
			case SLOT_3 -> flag(p, F_GLIDE);
			case SLOT_4 -> flag(p, F_CORE);
			case SLOT_5 -> flag(p, F_OVERLOAD) || flag(p, F_CHANNEL);
			case SLOT_6 -> flag(p, F_SHIELD) || flag(p, F_DOME) || flag(p, F_REFLECT);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- Energy

	/** His Energy, 0..100 (client-safe: a synced timer). */
	public static int energy(Player p) {
		return (int) Mth.clamp(Omnitrix.state(p).readyAt(ID + "." + ENERGY_KEY), 0, MAX_ENERGY);
	}

	private void setEnergy(ServerPlayer p, int e) {
		e = Mth.clamp(e, 0, MAX_ENERGY);
		if (e == energy(p)) {
			return;
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		if (e <= 0) {
			n.timers.remove(key(ENERGY_KEY)); // (setTimer drops values <= 0 anyway; an empty meter is no entry)
		} else {
			n.timers.put(key(ENERGY_KEY), (long) e);
		}
		Omnitrix.save(p, n);
	}

	/** Test hook / tune: sets his Energy outright. */
	public void fill(ServerPlayer p, int e) {
		setEnergy(p, e);
	}

	/** The multiplier Overload puts on his damage right now. */
	public float power(Player p) {
		return flag(p, F_OVERLOAD) ? OVERLOAD_BONUS : 1f;
	}

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.shock_rock." + key, args).withStyle(ChatFormatting.GRAY), true);
	}

	private static Vec3 hand(ServerPlayer p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.7)).add(0, -0.35, 0);
	}

	private static Vec3 centre(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		setEnergy(p, MAX_ENERGY);
		OmnitrixSfx.play(level(p), p.position(), SND_FORM, 0.7f, 0.9f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		LAST_TICK.remove(p.getUUID());
		DASHED.remove(p.getUUID());
		Wave w = WAVES.remove(p.getUUID());
		if (w != null) {
			w.carrier().discard();
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : TIMERS) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	/** The shield faces where he faces: {@code from} is in front of him. */
	public static boolean inFront(Player p, Vec3 from) {
		Vec3 d = from.subtract(p.position());
		Vec3 h = new Vec3(d.x, 0, d.z);
		if (h.lengthSqr() < 1e-4) {
			return true;
		}
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
		return h.normalize().dot(f) >= SHIELD_COS;
	}

	/** The Energy Dome's middle (client-safe), or null while there is none. */
	@Nullable
	public static Vec3 dome(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & F_DOME) == 0) {
			return null;
		}
		Long x = s.timers.get(ID + "." + DOME_X), y = s.timers.get(ID + "." + DOME_Y), z = s.timers.get(ID + "." + DOME_Z);
		return x == null || y == null || z == null ? null : new Vec3(x / 1000.0, y / 1000.0, z / 1000.0);
	}

	/** Whether {@code at} is inside {@code p}'s Energy Dome. */
	public static boolean inDome(Player p, Vec3 at) {
		Vec3 c = dome(p);
		return c != null && c.distanceToSqr(at) <= DOME_RADIUS * DOME_RADIUS;
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypes.LIGHTNING_BOLT)) {
			// Fulmini Core: lightning is a meal
			p.heal(4f);
			setEnergy(p, energy(p) + 30);
			p.clearFire();
			level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.7, 0.4, 0.2);
			return false;
		}
		if ((flag(p, F_DASH) || flag(p, F_JET) || flag(p, F_GLIDE)) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING);
		Vec3 from = source.getSourcePosition();
		// the Energy Dome: shots and blasts from outside stop at its shell
		if (!bypass && from != null && flag(p, F_DOME) && inDome(p, p.position().add(0, 1, 0)) && !inDome(p, from)
				&& (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION) || source.getDirectEntity() instanceof Projectile)) {
			level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.4, p.getZ(), 8, 0.4, 0.4, 0.4, 0.1);
			OmnitrixSfx.play(level(p), p.position(), SND_BLOCK, 0.6f, 1.4f);
			return false;
		}
		if (!bypass && from != null && (flag(p, F_SHIELD) || flag(p, F_REFLECT)) && inFront(p, from)) {
			Vec3 hit = p.position().add(0, 1.0, 0).add(from.subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.9));
			level(p).sendParticles(SPARK, hit.x, hit.y, hit.z, 10, 0.2, 0.3, 0.2, 0.15);
			OmnitrixSfx.play(level(p), hit, SND_BLOCK, 0.9f, flag(p, F_REFLECT) ? 1.3f : 1f);
			if (source.getDirectEntity() instanceof Projectile pr) {
				if (flag(p, F_REFLECT)) {
					reflect(p, pr);
				} else {
					pr.setDeltaMovement(pr.getDeltaMovement().scale(-0.4));
				}
			}
			return false;
		}
		return true;
	}

	/** Reflect: a shot that hit the held shield flies straight back at whoever shot it (his now). */
	private void reflect(ServerPlayer p, Projectile pr) {
		Entity shooter = pr.getOwner();
		double speed = Math.max(1.2, pr.getDeltaMovement().length());
		Vec3 dir = shooter != null && shooter != p ? centre(shooter).subtract(pr.position()).normalize() : pr.getDeltaMovement().scale(-1).normalize();
		pr.setOwner(p);
		pr.setDeltaMovement(dir.scale(speed));
		pr.hurtMarked = true;
		level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, pr.getX(), pr.getY(), pr.getZ(), 10, 0.1, 0.1, 0.1, 0.2);
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return amount * SHOT_TAKEN; // Rock Armour
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
		boolean wet = p.isInWater();
		if (flag(p, F_OVERLOAD)) {
			if (now % DRAIN_EVERY == 0) {
				setEnergy(p, energy(p) - 1);
			}
			if (energy(p) <= 0 || wet) {
				endOverload(p);
				say(p, wet ? "shorted" : "drained");
			} else if (now % 4 == 0) {
				l.sendParticles(SPARK, p.getX(), p.getY() + 1.1, p.getZ(), 2, 0.4, 0.6, 0.4, 0.05);
			}
		} else if (wet) {
			if (now % WATER_DRAIN_EVERY == 0) {
				setEnergy(p, energy(p) - 1); // Short Circuit
				if (now % 20 == 0) {
					l.sendParticles(ParticleTypes.BUBBLE_POP, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
				}
			}
		} else if (flag(p, F_CHANNEL)) {
			channelTick(p, l, now);
		} else if (now % REFILL_EVERY == 0) {
			setEnergy(p, energy(p) + 1);
		}
		if (flag(p, F_BLADE) && (now >= timer(p, BLADE_UNTIL) || bladeHits(p) <= 0)) {
			endBlade(p);
		}
		if (flag(p, F_SHIELD) && now >= timer(p, SHIELD_UNTIL)) {
			setFlag(p, F_SHIELD, false);
			setTimer(p, SHIELD_UNTIL, 0L);
			anim(p, ANIM_SHIELD_END);
			OmnitrixSfx.play(l, p.position(), SND_FADE, 0.7f, 1.1f);
		}
		if (flag(p, F_DASH)) {
			dashTick(p, l, now);
		}
		if (flag(p, F_RAPID)) {
			rapidTick(p, now);
		}
		if (flag(p, F_CHARGE)) {
			chargeTick(p, l, now);
		}
		if (flag(p, F_LASH)) {
			lashTick(p, l, now);
		}
		if (flag(p, F_JET) && now - timer(p, GLIDE_AT) > 4 && (p.onGround() || p.isInWater())) {
			setFlag(p, F_JET, false);
		}
		if (flag(p, F_GLIDE)) {
			glideTick(p, l, now);
		}
		if (flag(p, F_CORE)) {
			coreTick(p, l, now);
		}
		if (flag(p, F_DOME)) {
			domeTick(p, l, now);
		}
		if (flag(p, F_REFLECT) && now - timer(p, REFLECT_AT) >= REFLECT_MAX) {
			endReflect(p);
		}
		if (WAVES.containsKey(p.getUUID())) {
			waveTick(p, l, now);
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CHANNEL) && slot != AbilitySlot.SLOT_5) {
			endChannel(p); // any other move stops the channel
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					rapidBolts(p);
				} else {
					energyBlast(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					bladeWave(p);
				} else {
					energyBlade(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					updraft(p);
				} else {
					energyDash(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					rockBarrage(p);
				} else {
					rockBurst(p);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					armourReform(p);
				} else {
					overload(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					energyDome(p);
				} else {
					shield(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (flag(p, F_CHANNEL) && slot != AbilitySlot.SLOT_5) {
			endChannel(p);
		}
		switch (slot) {
			case SLOT_1 -> startCharge(p);
			case SLOT_2 -> startLash(p);
			case SLOT_3 -> startGlide(p);
			case SLOT_4 -> startCore(p);
			case SLOT_5 -> startChannel(p);
			case SLOT_6 -> startReflect(p);
			default -> {
			}
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		switch (slot) {
			case SLOT_1 -> releaseCharge(p);
			case SLOT_2 -> endLash(p);
			case SLOT_3 -> endGlide(p);
			case SLOT_4 -> releaseCore(p);
			case SLOT_5 -> endChannel(p);
			case SLOT_6 -> endReflect(p);
			default -> {
			}
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (!(target instanceof LivingEntity e)) {
			return false;
		}
		if (!flag(p, F_BLADE)) {
			anim(p, ANIM_PUNCH); // Rock Fists: vanilla's punch (+6 attack)
			return false;
		}
		// the Energy Blade: the hit is a slash of its own, and it cuts what stands beside the target too
		float k = Math.max(0.3f, p.getAttackStrengthScale(0.5f));
		anim(p, ANIM_SLASH);
		e.invulnerableTime = 0;
		hurt(p, e, BLADE_DAMAGE * k * power(p), false);
		shove(e, p.position(), 0.5, 0.15);
		for (LivingEntity o : around(p, e.position(), BLADE_CLEAVE_RADIUS)) {
			if (o != e) {
				o.invulnerableTime = 0;
				hurt(p, o, BLADE_CLEAVE * k * power(p), false);
			}
		}
		p.resetAttackStrengthTicker();
		spendBlade(p, 1);
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level(p).sendParticles(SPARK, c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.1);
		OmnitrixSfx.play(level(p), c, SND_SLASH, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- R / Shift+R / Hold R

	private AlienProjectileEntity bolt(ServerPlayer p, float damage, float size) {
		return AlienProjectileEntity.of(p, "shock_rock_bolt").colour(ENERGY).size(size).damage(damage * power(p)).speed(2.0).life(40);
	}

	/** R: a bolt of energy from his fist. */
	public boolean energyBlast(ServerPlayer p) {
		if (!use(p, BLAST)) {
			return false;
		}
		anim(p, ANIM_BLAST);
		bolt(p, BLAST_DAMAGE, 0.45f).spawn();
		OmnitrixSfx.playOn(p, SND_BLAST, 0.9f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** Shift+R: five bolts in half a second (the first on the press). */
	public boolean rapidBolts(ServerPlayer p) {
		if (flag(p, F_RAPID) || !use(p, RAPID)) {
			return false;
		}
		anim(p, ANIM_RAPID);
		setTimer(p, RAPID_START, p.level().getGameTime());
		setFlag(p, F_RAPID, true);
		bolt(p, BOLT_DAMAGE, 0.3f).spawn();
		OmnitrixSfx.playOn(p, SND_BOLT, 0.8f, 1f, SoundSource.PLAYERS);
		return true;
	}

	private void rapidTick(ServerPlayer p, long now) {
		long since = now - timer(p, RAPID_START);
		if (since >= (long) BOLTS * BOLT_EVERY - 1 || since < 0) {
			setFlag(p, F_RAPID, false);
			setTimer(p, RAPID_START, 0L);
			return;
		}
		if (since > 0 && since % BOLT_EVERY == 0) {
			bolt(p, BOLT_DAMAGE, 0.3f).spawn();
			OmnitrixSfx.playOn(p, SND_BOLT, 0.7f, 1.05f, SoundSource.PLAYERS);
		}
	}

	/** Hold R: the fist draws energy in (up to 1.5 s to full); letting go fires {@link #releaseCharge}. */
	public boolean startCharge(ServerPlayer p) {
		if (flag(p, F_CHARGE)) {
			return false;
		}
		if (!ready(p, CHARGED)) {
			return use(p, CHARGED);
		}
		anim(p, ANIM_CHARGE);
		setTimer(p, CHARGE_START, p.level().getGameTime());
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.playOn(p, SND_CHARGE, 0.7f, 1f, SoundSource.PLAYERS);
		return true;
	}

	/** 0..1: how charged the Charged Bolt is (client-safe). */
	public static float boltCharge(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & F_CHARGE) == 0) {
			return 0f;
		}
		return Mth.clamp((p.level().getGameTime() + partial - s.readyAt(ID + "." + CHARGE_START)) / CHARGE_FULL, 0f, 1f);
	}

	private void chargeTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, CHARGE_START);
		if (age >= CHARGE_MAX) {
			releaseCharge(p); // held far past full: it goes off by itself
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 0, false, false, false));
		if (now % 3 == 0) {
			Vec3 h = hand(p);
			double r = 0.8 * (1.0 - Math.min(1.0, age / (double) CHARGE_FULL)) + 0.2;
			l.sendParticles(SPARK, h.x, h.y, h.z, 3, r, r, r, 0.02);
		}
		if (age == CHARGE_FULL) {
			OmnitrixSfx.playOn(p, SND_FORM, 0.6f, 1.6f, SoundSource.PLAYERS); // the full-charge ping
		}
	}

	/** The Charged Bolt: a piercing streak along his aim (28 blocks): 10 uncharged to 20 at full, through everything on it. Returns hits. */
	public int releaseCharge(ServerPlayer p) {
		if (!flag(p, F_CHARGE)) {
			return -1;
		}
		float k = Mth.clamp((p.level().getGameTime() - timer(p, CHARGE_START)) / (float) CHARGE_FULL, 0f, 1f);
		setFlag(p, F_CHARGE, false);
		setTimer(p, CHARGE_START, 0L);
		cooldown(p, CHARGED, -1);
		return chargedBolt(p, k);
	}

	/** The Charged Bolt at charge {@code k} (0..1). Returns how many it hit. */
	public int chargedBolt(ServerPlayer p, float k) {
		anim(p, ANIM_CHARGED);
		ServerLevel l = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 end = RecalKitMath.beamEnd(p, look, CHARGED_RANGE);
		float dmg = Mth.lerp(k, CHARGED_MIN, CHARGED_MAX) * power(p);
		int n = 0;
		for (LivingEntity e : RecalKitMath.line(p, look, end.distanceTo(p.getEyePosition()), 0.8)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, dmg, false)) {
				shove(e, p.position(), 0.4 + 0.5 * k, 0.15);
				n++;
			}
			Vec3 c = centre(e);
			l.sendParticles(SPARK, c.x, c.y, c.z, 10, 0.2, 0.3, 0.2, 0.15);
		}
		Vec3 h = hand(p);
		QuirkFx.ribbon(l, h, end).colours(ENERGY_HOT, ENERGY).size(0.12f + 0.16f * k).life(7).spawn();
		QuirkFx.ring(l, h.add(look.scale(0.6))).flags(QuirkFx.F_VERTICAL).vec(look).colours(ENERGY_HOT, ENERGY).size(0.6f + 0.6f * k).thickness(0.15f).life(7).spawn();
		l.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		l.sendParticles(SPARK, end.x, end.y, end.z, 16, 0.3, 0.3, 0.3, 0.2);
		OmnitrixSfx.playOn(p, SND_BLAST, 1f, 0.8f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, SND_OVERLOAD, 0.4f + 0.4f * k, 1.4f, SoundSource.PLAYERS);
		return n;
	}

	// ---------------------------------------------------------------- G / Shift+G / Hold G

	/** Hits the blade has left (client-safe). */
	public static int bladeHits(Player p) {
		return (int) Omnitrix.state(p).readyAt(ID + "." + BLADE_HITS);
	}

	private void spendBlade(ServerPlayer p, int hits) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(BLADE_HITS), (long) Math.max(0, bladeHits(p) - hits));
		Omnitrix.save(p, n);
	}

	/** G: a blade of energy forms on his arm for 10 s or 6 hits. */
	public boolean energyBlade(ServerPlayer p) {
		if (flag(p, F_BLADE) || !use(p, BLADE)) {
			return false;
		}
		anim(p, ANIM_BLADE);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(BLADE_UNTIL), p.level().getGameTime() + BLADE_TICKS);
		n.timers.put(key(BLADE_HITS), (long) BLADE_MAX_HITS);
		Omnitrix.save(p, n);
		setFlag(p, F_BLADE, true);
		Vec3 hand = p.position().add(0, 1.0, 0).add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.5));
		level(p).sendParticles(SPARK, hand.x, hand.y, hand.z, 16, 0.2, 0.3, 0.2, 0.1);
		OmnitrixSfx.play(level(p), p.position(), SND_FORM, 1f, 1.1f);
		return true;
	}

	private void endBlade(ServerPlayer p) {
		setFlag(p, F_BLADE, false);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(BLADE_UNTIL));
		n.timers.remove(key(BLADE_HITS));
		Omnitrix.save(p, n);
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
		OmnitrixSfx.play(level(p), p.position(), SND_FADE, 0.7f, 1.3f);
	}

	/**
	 * Shift+G: Blade Wave -- a crescent of energy thrown along his aim (16 blocks), cutting through everything on its way: 14. With the
	 * Energy Blade up it is the blade's own edge thrown: 18, and it spends 2 of the blade's hits.
	 */
	public boolean bladeWave(ServerPlayer p) {
		if (WAVES.containsKey(p.getUUID()) || !use(p, WAVE)) {
			return false;
		}
		anim(p, ANIM_WAVE);
		boolean blade = flag(p, F_BLADE) && bladeHits(p) > 0;
		if (blade) {
			spendBlade(p, WAVE_BLADE_COST);
		}
		Vec3 look = p.getLookAngle();
		Vec3 from = p.getEyePosition().add(look.scale(0.8)).add(0, -0.3, 0);
		AlienProjectileEntity c = AlienProjectileEntity.of(p, "shock_rock_wave");
		c.moveTo(from.x, from.y, from.z, p.getYRot(), p.getXRot());
		c.colour(blade ? ENERGY_HOT : ENERGY).size(blade ? 1.3f : 1f).held(true).speed(0).life(WAVE_TICKS + 4).damage(0f).spawn();
		WAVES.put(p.getUUID(), new Wave(c, look, p.level().getGameTime(), ConcurrentHashMap.newKeySet(), (blade ? WAVE_BLADE_DAMAGE : WAVE_DAMAGE) * power(p)));
		OmnitrixSfx.playOn(p, SND_SLASH, 1f, 0.8f, SoundSource.PLAYERS);
		OmnitrixSfx.playOn(p, SND_BLAST, 0.6f, 1.2f, SoundSource.PLAYERS);
		return true;
	}

	private void waveTick(ServerPlayer p, ServerLevel l, long now) {
		Wave w = WAVES.get(p.getUUID());
		if (!w.carrier().isAlive() || now - w.start() > WAVE_TICKS || w.carrier().level() != p.level()) {
			endWave(p, w);
			return;
		}
		Vec3 at = w.carrier().position();
		Vec3 next = at.add(w.dir().scale(WAVE_SPEED));
		HitResult block = l.clip(new ClipContext(at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		boolean stop = block.getType() != HitResult.Type.MISS;
		if (stop) {
			next = block.getLocation();
		}
		w.carrier().setPos(next.x, next.y, next.z);
		for (LivingEntity e : around(p, at.lerp(next, 0.5).subtract(0, 0.6, 0), WAVE_SPEED * 0.5 + WAVE_HIT)) {
			if (w.hit().add(e.getId())) {
				e.invulnerableTime = 0;
				if (hurt(p, e, w.damage(), false)) {
					shove(e, p.position(), 0.6, 0.2);
				}
				Vec3 c = centre(e);
				l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				l.sendParticles(SPARK, c.x, c.y, c.z, 10, 0.2, 0.3, 0.2, 0.15);
			}
		}
		l.sendParticles(SPARK, next.x, next.y, next.z, 3, 0.4, 0.15, 0.4, 0.02);
		if (stop) {
			l.sendParticles(SPARK, next.x, next.y, next.z, 16, 0.3, 0.3, 0.3, 0.2);
			endWave(p, w);
		}
	}

	private void endWave(ServerPlayer p, Wave w) {
		WAVES.remove(p.getUUID());
		w.carrier().discard();
	}

	/** Hold G: Energy Lash -- a tether of energy on the creature you look at while G is held (up to 2 s): 3 every 0.25 s, dragged in. */
	public boolean startLash(ServerPlayer p) {
		if (flag(p, F_LASH)) {
			return false;
		}
		if (!ready(p, LASH)) {
			return use(p, LASH);
		}
		LivingEntity t = lookTarget(p, LASH_RANGE);
		if (t == null) {
			say(p, "no_target");
			return false;
		}
		anim(p, ANIM_LASH);
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(LASH_AT), now);
		n.timers.put(key(LASH_TARGET), (long) t.getId() + 1);
		n.kitFlags |= F_LASH;
		Omnitrix.save(p, n);
		OmnitrixSfx.playOn(p, SND_FORM, 0.7f, 1.4f, SoundSource.PLAYERS);
		return true;
	}

	/** The creature {@code p}'s Energy Lash holds (client-safe), or null. */
	@Nullable
	public static Entity lashed(Player p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & F_LASH) == 0) {
			return null;
		}
		long id = s.readyAt(ID + "." + LASH_TARGET) - 1;
		return id < 0 ? null : p.level().getEntity((int) id);
	}

	private void lashTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, LASH_AT);
		Entity e = lashed(p);
		if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceToSqr(p) > (LASH_RANGE + 6) * (LASH_RANGE + 6) || age >= LASH_MAX) {
			endLash(p);
			return;
		}
		if (age % LASH_EVERY == 0) {
			t.invulnerableTime = 0;
			hurt(p, t, LASH_DAMAGE * power(p), false);
			if (!AlienStatus.isBoss(t) && t.distanceToSqr(p) > 2.5 * 2.5) {
				Vec3 to = p.position().subtract(t.position()).normalize();
				double res = 1.0 - Math.min(1.0, t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
				t.setDeltaMovement(to.scale(0.55 * Math.max(0.35, res)).add(0, 0.12, 0));
				t.hurtMarked = true;
			}
			Vec3 c = centre(t);
			l.sendParticles(SPARK, c.x, c.y, c.z, 6, 0.2, 0.3, 0.2, 0.1);
			OmnitrixSfx.play(l, c, SND_BOLT, 0.5f, 1.2f);
		}
	}

	public void endLash(ServerPlayer p) {
		if (!flag(p, F_LASH)) {
			return;
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(key(LASH_AT));
		n.timers.remove(key(LASH_TARGET));
		n.kitFlags &= ~F_LASH;
		Omnitrix.save(p, n);
		cooldown(p, LASH, -1);
		anim(p, ANIM_PUNCH);
	}

	// ---------------------------------------------------------------- X / Shift+X / Hold X

	/** X: a burst of energy drives him where he looks. */
	public boolean energyDash(ServerPlayer p) {
		if (flag(p, F_DASH) || !use(p, DASH)) {
			return false;
		}
		anim(p, ANIM_DASH);
		DASHED.put(p.getUUID(), ConcurrentHashMap.newKeySet());
		setTimer(p, DASH_UNTIL, p.level().getGameTime() + DASH_TICKS);
		setFlag(p, F_DASH, true);
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.scale(DASH_SPEED).add(0, 0.1, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		QuirkFx.ring(level(p), p.position().add(0, 1.0, 0)).flags(QuirkFx.F_VERTICAL).vec(look).colours(ENERGY_HOT, ENERGY).size(1.4f).thickness(0.15f).life(8).spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_DASH, 1f, 1f);
		return true;
	}

	private void dashTick(ServerPlayer p, ServerLevel l, long now) {
		if (now >= timer(p, DASH_UNTIL) + 20 || now >= timer(p, DASH_UNTIL) && p.onGround()) {
			setFlag(p, F_DASH, false);
			setTimer(p, DASH_UNTIL, 0L);
			DASHED.remove(p.getUUID());
			return;
		}
		p.resetFallDistance();
		if (now < timer(p, DASH_UNTIL)) {
			Vec3 look = p.getLookAngle();
			p.setDeltaMovement(look.scale(DASH_SPEED).add(0, 0.05, 0));
			p.hurtMarked = true;
			l.sendParticles(SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
			passThrough(p, DASH_DAMAGE);
		}
	}

	/** Whatever he passes through takes {@code damage} once (the dash's / the glide's own hit list). */
	private void passThrough(ServerPlayer p, float damage) {
		Set<Integer> hit = DASHED.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
		for (LivingEntity e : around(p, p.position().add(0, 1.0, 0), 1.6)) {
			if (hit.add(e.getId())) {
				e.invulnerableTime = 0;
				hurt(p, e, damage * power(p), false);
				shove(e, p.position(), 0.7, 0.3);
			}
		}
	}

	/** Shift+X: Updraft Jet -- a blast of energy under him throws him about 10 blocks straight up; no fall damage till he lands. */
	public boolean updraft(ServerPlayer p) {
		if (!use(p, UPDRAFT)) {
			return false;
		}
		anim(p, ANIM_UPDRAFT);
		setTimer(p, GLIDE_AT, p.level().getGameTime());
		setFlag(p, F_JET, true);
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(v.x * 0.3, UPDRAFT_SPEED, v.z * 0.3);
		p.hurtMarked = true;
		p.resetFallDistance();
		ServerLevel l = level(p);
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(ENERGY_HOT, ENERGY).size(2.2f).thickness(0.3f).life(10).ground().spawn();
		l.sendParticles(SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 30, 0.4, 0.1, 0.4, 0.2);
		l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.5, 0.05, 0.5, 0.05);
		OmnitrixSfx.play(l, p.position(), SND_DASH, 1f, 0.8f);
		OmnitrixSfx.play(l, p.position(), SND_BLAST, 0.6f, 0.7f);
		return true;
	}

	/** Hold X: Power Glide -- he jets forward low over the ground while X is held (up to 2 s); whatever he passes takes 6 once. */
	public boolean startGlide(ServerPlayer p) {
		if (flag(p, F_GLIDE)) {
			return false;
		}
		if (!ready(p, GLIDE)) {
			return use(p, GLIDE);
		}
		anim(p, ANIM_GLIDE);
		DASHED.put(p.getUUID(), ConcurrentHashMap.newKeySet());
		setTimer(p, GLIDE_AT, p.level().getGameTime());
		setFlag(p, F_GLIDE, true);
		p.setDeltaMovement(p.getDeltaMovement().add(0, 0.25, 0));
		p.hurtMarked = true;
		OmnitrixSfx.play(level(p), p.position(), SND_DASH, 1f, 1.2f);
		return true;
	}

	private void glideTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, GLIDE_AT);
		if (age >= GLIDE_MAX || p.isInWater()) {
			endGlide(p);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
		double vy = p.getDeltaMovement().y;
		p.setDeltaMovement(h.x * GLIDE_SPEED, Math.max(vy, -0.04) + (p.onGround() ? 0.12 : 0.0), h.z * GLIDE_SPEED);
		p.hurtMarked = true;
		p.resetFallDistance();
		l.sendParticles(SPARK, p.getX(), p.getY() + 0.3, p.getZ(), 3, 0.25, 0.1, 0.25, 0.02);
		if (age % 2 == 0) {
			l.sendParticles(ParticleTypes.CLOUD, p.getX() - h.x, p.getY() + 0.2, p.getZ() - h.z, 1, 0.1, 0.05, 0.1, 0.01);
		}
		if (age % 8 == 0) {
			OmnitrixSfx.play(l, p.position(), SND_DASH, 0.5f, 1.4f);
		}
		passThrough(p, GLIDE_DAMAGE);
	}

	public void endGlide(ServerPlayer p) {
		if (!flag(p, F_GLIDE)) {
			return;
		}
		setFlag(p, F_GLIDE, false);
		setFlag(p, F_JET, true); // no fall damage on the way down
		setTimer(p, GLIDE_AT, p.level().getGameTime());
		DASHED.remove(p.getUUID());
		cooldown(p, GLIDE, -1);
		anim(p, ANIM_PUNCH);
	}

	// ---------------------------------------------------------------- Z / Shift+Z / Hold Z

	/** Z: the rock armour flies apart and slams back. Returns how many it hit (-1 if not ready). */
	public int rockBurst(ServerPlayer p) {
		if (!use(p, BURST)) {
			return -1;
		}
		return burst(p, BURST_RADIUS, BURST_DAMAGE, 0f);
	}

	/** A Rock Burst of {@code radius} and {@code damage} ({@code k}: how overcharged, for the flash). Returns how many it hit. */
	private int burst(ServerPlayer p, double radius, float damage, float k) {
		anim(p, ANIM_BURST);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : around(p, c, radius)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, damage * power(p), false)) {
				shove(e, p.position(), 1.3 + 0.4 * k, 0.45 + 0.2 * k);
				n++;
			}
		}
		// his rock flies out and comes back together (the shards reassemble), and the bared core sends out a cyan pulse -- no fireball
		// (QuirkFx.blast's flipbook is fire-coloured and turned his energy yellow)
		QuirkFx.shards(l, c).block(Blocks.DEEPSLATE.defaultBlockState()).size((float) radius * 0.7f).count(26).life(BURST_TICKS).flags(QuirkFx.F_REASSEMBLE).spawn();
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(ENERGY_HOT, ENERGY).size((float) radius).thickness(0.4f).life(12).ground().spawn();
		QuirkFx.ring(l, c).style(QuirkFx.RING_SOFT).colours(ENERGY_HOT, ENERGY).size((float) radius * 0.8f).thickness(1.0f).life(10).spawn();
		if (k > 0.5f) {
			QuirkFx.ring(l, c).flags(QuirkFx.F_VERTICAL).vec(new Vec3(0, 1, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, ENERGY).size((float) radius).thickness(0.5f)
					.life(14).spawn();
			l.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
		l.sendParticles(SPARK, c.x, c.y, c.z, (int) (40 + 30 * k), radius * 0.2, 0.8, radius * 0.2, 0.3);
		OmnitrixSfx.play(l, c, SND_BURST, 1.2f, 1f - 0.15f * k);
		return n;
	}

	/** Shift+Z: Rock Barrage -- he flings his armour plates in a cone (12 blocks): six shards of 4. */
	public boolean rockBarrage(ServerPlayer p) {
		if (!use(p, BARRAGE)) {
			return false;
		}
		anim(p, ANIM_BARRAGE);
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < BARRAGE_SHARDS; i++) {
			double yaw = (i - (BARRAGE_SHARDS - 1) / 2.0) * (BARRAGE_HALF_ANGLE * 2 / (BARRAGE_SHARDS - 1));
			Vec3 dir = RecalKitMath.turned(look, yaw).add(0, (i % 2 == 0 ? 0.04 : -0.03), 0);
			AlienProjectileEntity.of(p, "shock_rock_shard").colour(ROCK).size(0.35f).damage(BARRAGE_DAMAGE * power(p)).speed(1.7).life(9).gravity(0.02).direction(dir)
					.spawn();
		}
		ServerLevel l = level(p);
		Vec3 chest = p.position().add(0, 1.1, 0).add(look.scale(0.4));
		QuirkFx.shards(l, chest).block(Blocks.DEEPSLATE.defaultBlockState()).size(1.2f).count(8).life(10).spawn();
		l.sendParticles(SPARK, chest.x, chest.y, chest.z, 16, 0.3, 0.4, 0.3, 0.15);
		OmnitrixSfx.play(l, chest, SND_ROCK, 1f, 1f);
		return true;
	}

	/** Hold Z: the core overloads (up to 2 s to full, slowed); letting go bursts it: {@link #releaseCore}. */
	public boolean startCore(ServerPlayer p) {
		if (flag(p, F_CORE)) {
			return false;
		}
		if (!ready(p, CORE_BURST)) {
			return use(p, CORE_BURST);
		}
		anim(p, ANIM_CORE);
		setTimer(p, CORE_START, p.level().getGameTime());
		setFlag(p, F_CORE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.9f, 0.8f);
		return true;
	}

	/** 0..1: how far the Core Overload Burst has charged (client-safe). */
	public static float coreCharge(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & F_CORE) == 0) {
			return 0f;
		}
		return Mth.clamp((p.level().getGameTime() + partial - s.readyAt(ID + "." + CORE_START)) / CORE_FULL, 0f, 1f);
	}

	private void coreTick(ServerPlayer p, ServerLevel l, long now) {
		long age = now - timer(p, CORE_START);
		if (age >= CORE_MAX) {
			releaseCore(p);
			return;
		}
		float k = Mth.clamp(age / (float) CORE_FULL, 0f, 1f);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
		if (now % 4 == 0) {
			QuirkFx.ring(l, p.position().add(0, 1.0, 0)).style(QuirkFx.RING_SOFT).colours(ENERGY_HOT, ENERGY).size(3.5f - 2.2f * k).thickness(0.15f).life(5).spawn();
			l.sendParticles(SPARK, p.getX(), p.getY() + 1.1, p.getZ(), 2 + (int) (6 * k), 0.4, 0.6, 0.4, 0.05 + 0.1 * k);
		}
		if (age == CORE_FULL) {
			OmnitrixSfx.play(l, p.position(), SND_OVERLOAD, 0.7f, 1.3f);
		}
	}

	/** The core bursts: 18 within 6 uncharged up to 28 within 9 at full. Returns how many it hit (-1 if it was not charging). */
	public int releaseCore(ServerPlayer p) {
		if (!flag(p, F_CORE)) {
			return -1;
		}
		float k = Mth.clamp((p.level().getGameTime() - timer(p, CORE_START)) / (float) CORE_FULL, 0f, 1f);
		setFlag(p, F_CORE, false);
		setTimer(p, CORE_START, 0L);
		cooldown(p, CORE_BURST, -1);
		return burst(p, Mth.lerp(k, CORE_R_MIN, CORE_R_MAX), Mth.lerp(k, CORE_DMG_MIN, CORE_DMG_MAX), k);
	}

	// ---------------------------------------------------------------- V / Shift+V / Hold V

	/** V: Overload on (with 25+ Energy), or off again. */
	public boolean overload(ServerPlayer p) {
		if (flag(p, F_OVERLOAD)) {
			endOverload(p);
			return true;
		}
		if (energy(p) < OVERLOAD_MIN) {
			say(p, "low_energy", OVERLOAD_MIN);
			return false;
		}
		if (p.isInWater()) {
			say(p, "shorted");
			return false;
		}
		if (!use(p, OVERLOAD)) {
			return false;
		}
		anim(p, ANIM_OVERLOAD);
		setFlag(p, F_OVERLOAD, true);
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(ENERGY_HOT, ENERGY).size(2.6f).thickness(1.0f).life(12).ground().spawn();
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(ENERGY_HOT, ENERGY).size(2.2f).thickness(0.25f).life(10).ground().spawn();
		level(p).sendParticles(SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.25);
		OmnitrixSfx.play(level(p), p.position(), SND_OVERLOAD, 1f, 1f);
		return true;
	}

	private void endOverload(ServerPlayer p) {
		if (!flag(p, F_OVERLOAD)) {
			return;
		}
		setFlag(p, F_OVERLOAD, false);
		cooldown(p, OVERLOAD, -1);
		OmnitrixSfx.play(level(p), p.position(), SND_FADE, 0.9f, 0.9f);
	}

	/** {@code p}'s Overload is on (client-safe). */
	public static boolean overloaded(Player p) {
		return isShockRock(p) && (Omnitrix.state(p).kitFlags & F_OVERLOAD) != 0;
	}

	/** Shift+V: Armour Reform -- he rebuilds his plates: 8 absorption health for 10 s. */
	public boolean armourReform(ServerPlayer p) {
		if (!use(p, REFORM)) {
			return false;
		}
		anim(p, ANIM_REFORM);
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, REFORM_TICKS, (int) (REFORM_ABSORB / 4) - 1, false, false, true));
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		QuirkFx.shards(l, c).block(Blocks.DEEPSLATE.defaultBlockState()).size(2.4f).count(18).life(16).flags(QuirkFx.F_REASSEMBLE).spawn();
		l.sendParticles(SPARK, c.x, c.y, c.z, 20, 0.4, 0.7, 0.4, 0.1);
		OmnitrixSfx.play(l, c, SND_ROCK, 1f, 0.8f);
		OmnitrixSfx.play(l, c, SND_FORM, 0.6f, 0.8f);
		return true;
	}

	/** Hold V: Energy Channel -- he draws energy in while V is held: +10 Energy a second, slowed. No cooldown. */
	public boolean startChannel(ServerPlayer p) {
		if (flag(p, F_CHANNEL)) {
			return false;
		}
		if (p.isInWater()) {
			say(p, "shorted");
			return false;
		}
		anim(p, ANIM_CHANNEL);
		setFlag(p, F_CHANNEL, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.8f, 1.1f);
		return true;
	}

	private void channelTick(ServerPlayer p, ServerLevel l, long now) {
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
		if (now % CHANNEL_EVERY == 0) {
			setEnergy(p, energy(p) + 1);
		}
		if (now % 3 == 0) {
			// motes of light drawn in to his core from all round
			double a = l.random.nextDouble() * Math.PI * 2;
			Vec3 from = p.position().add(Math.cos(a) * 2.2, 0.3 + l.random.nextDouble() * 1.8, Math.sin(a) * 2.2);
			Vec3 to = p.position().add(0, 1.1, 0).subtract(from).scale(0.12);
			l.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, to.x, to.y, to.z, 1.0);
			l.sendParticles(SPARK, p.getX(), p.getY() + 1.1, p.getZ(), 2, 0.25, 0.3, 0.25, 0.02);
		}
		if (now % 30 == 0) {
			OmnitrixSfx.play(l, p.position(), SND_CHARGE, 0.4f, 1.2f + energy(p) / 200f);
		}
	}

	public void endChannel(ServerPlayer p) {
		if (!flag(p, F_CHANNEL)) {
			return;
		}
		setFlag(p, F_CHANNEL, false);
		anim(p, ANIM_SHIELD_END);
	}

	// ---------------------------------------------------------------- C / Shift+C / Hold C

	/** C: 5 s of an energy shield in front of him. */
	public boolean shield(ServerPlayer p) {
		if (flag(p, F_SHIELD) || flag(p, F_REFLECT) || !use(p, SHIELD)) {
			return false;
		}
		anim(p, ANIM_SHIELD);
		setTimer(p, SHIELD_UNTIL, p.level().getGameTime() + SHIELD_TICKS);
		setFlag(p, F_SHIELD, true);
		OmnitrixSfx.play(level(p), p.position(), SND_FORM, 1f, 0.9f);
		return true;
	}

	/** Shift+C: Energy Dome -- a 4-block dome round him for 4 s: shots and blasts from outside stop at it, for everyone inside. */
	public boolean energyDome(ServerPlayer p) {
		if (flag(p, F_DOME) || !use(p, DOME)) {
			return false;
		}
		anim(p, ANIM_DOME);
		Vec3 c = p.position();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(DOME_UNTIL), p.level().getGameTime() + DOME_TICKS);
		n.timers.put(key(DOME_X), Math.round(c.x * 1000));
		n.timers.put(key(DOME_Y), Math.round(c.y * 1000));
		n.timers.put(key(DOME_Z), Math.round(c.z * 1000));
		n.kitFlags |= F_DOME;
		Omnitrix.save(p, n);
		QuirkFx.ring(level(p), c.add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(ENERGY_HOT, ENERGY).size((float) DOME_RADIUS).thickness(0.3f).life(12).ground().spawn();
		OmnitrixSfx.play(level(p), c, SND_FORM, 1f, 0.7f);
		OmnitrixSfx.play(level(p), c, SND_OVERLOAD, 0.5f, 1.4f);
		return true;
	}

	private void domeTick(ServerPlayer p, ServerLevel l, long now) {
		Vec3 c = dome(p);
		if (c == null || now >= timer(p, DOME_UNTIL)) {
			OmnitrixState n = Omnitrix.state(p).copy();
			for (String k : new String[] { DOME_UNTIL, DOME_X, DOME_Y, DOME_Z }) {
				n.timers.remove(key(k));
			}
			n.kitFlags &= ~F_DOME;
			Omnitrix.save(p, n);
			OmnitrixSfx.play(l, p.position(), SND_FADE, 0.8f, 0.9f);
			return;
		}
		// shots from outside bounce off the shell (his own and his allies' fly out freely)
		for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(DOME_RADIUS + 2.5))) {
			Vec3 at = pr.position();
			double d = at.distanceTo(c);
			if (d < DOME_RADIUS - 0.6 || d > DOME_RADIUS + 2.0) {
				continue;
			}
			Entity owner = pr.getOwner();
			if (owner != null && (owner == p || inDome(p, owner.position()) || com.projecthero.mod.squad.Squads.areAllies(p, owner))) {
				continue;
			}
			Vec3 v = pr.getDeltaMovement();
			Vec3 out = at.subtract(c).normalize();
			if (v.dot(out) < 0) {
				// heading in: turned back off the shell
				pr.setDeltaMovement(v.subtract(out.scale(2 * v.dot(out))).scale(0.5));
				pr.hurtMarked = true;
				l.sendParticles(SPARK, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.1);
				OmnitrixSfx.play(l, at, SND_BLOCK, 0.6f, 1.4f);
			}
		}
		if (now % 10 == 0) {
			double a = l.random.nextDouble() * Math.PI * 2, b = l.random.nextDouble() * Math.PI * 0.5;
			Vec3 q = c.add(Math.cos(a) * Math.cos(b) * DOME_RADIUS, Math.sin(b) * DOME_RADIUS, Math.sin(a) * Math.cos(b) * DOME_RADIUS);
			l.sendParticles(SPARK, q.x, q.y, q.z, 3, 0.1, 0.1, 0.1, 0.02);
		}
	}

	/** Hold C: Reflect -- the shield held up while C is held (up to 3 s): shots that hit it fly back at whoever shot them. */
	public boolean startReflect(ServerPlayer p) {
		if (flag(p, F_REFLECT)) {
			return false;
		}
		if (!ready(p, REFLECT)) {
			return use(p, REFLECT);
		}
		anim(p, ANIM_REFLECT);
		setTimer(p, REFLECT_AT, p.level().getGameTime());
		setFlag(p, F_REFLECT, true);
		OmnitrixSfx.play(level(p), p.position(), SND_FORM, 1f, 1.2f);
		return true;
	}

	public void endReflect(ServerPlayer p) {
		if (!flag(p, F_REFLECT)) {
			return;
		}
		setFlag(p, F_REFLECT, false);
		setTimer(p, REFLECT_AT, 0L);
		cooldown(p, REFLECT, -1);
		anim(p, ANIM_SHIELD_END);
		OmnitrixSfx.play(level(p), p.position(), SND_FADE, 0.7f, 1.2f);
	}
}
