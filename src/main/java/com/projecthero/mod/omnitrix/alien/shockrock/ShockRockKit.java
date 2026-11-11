package com.projecthero.mod.omnitrix.alien.shockrock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Shock Rock (Ben 10 reboot, 2016; a hidden
 * prototype alien found by a mistransform): a Fulmini, a being of living energy held in a suit of rock. He fires blasts of it, shapes it
 * into constructs -- a blade, a shield -- and his rock chunks can fly apart and back together. Very durable. His energy (the user's skin:
 * a bright cyan) is drawn full-bright and the rock matte; in Overload the energy burns brighter still.
 *
 * <pre>
 *   R          Energy Blast     a bolt of energy from his fist (8)
 *   Shift+R    Rapid Bolts      five bolts in half a second (5 each)
 *   G          Energy Blade     a blade of energy forms on his arm for 10 s or 6 hits: his punches become 13-damage slashes that
 *                               also cut whatever stands close beside the target (6)
 *   Z          Rock Burst       the rock armour flies apart and slams back: 18 to everything within 6 blocks, thrown back, with
 *                               an energy pulse from his exposed core
 *   X          Energy Dash      a burst of energy drives him where he looks; whatever he passes through takes 6
 *   C          Shield Construct 5 s of an energy shield in front of him: anything from in front (blows, shots, blasts) is stopped
 *   V          Overload Mode    the energy burns brighter: his moves hit a third harder while the Energy meter drains (about 7 a
 *                               second; needs 25 to start; V again ends it). Energy refills at 3 a second when he is not overloaded
 *   Attack     Rock Fists       stone knuckles (+6)
 *   Passives   Rock Armour (shots hurt him a third less; hard to knock back); Fulmini Core (lightning heals him and fills his Energy)
 *   Weakness   Short Circuit (in water his Energy drains and Overload cuts out)
 * </pre>
 */
public final class ShockRockKit extends AlienKit {
	public static final String ID = "shock_rock";
	/** His energy: the user's skin paints it a bright cyan. */
	public static final int ENERGY = 0x23DADA, ENERGY_HOT = 0xC9F8F8, ROCK = 0x343042;
	public static final DustParticleOptions SPARK = new DustParticleOptions(new org.joml.Vector3f(0.4f, 1.0f, 1.0f), 0.6f);

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_BLADE = 1, F_SHIELD = 2, F_OVERLOAD = 4, F_DASH = 8, F_RAPID = 16;
	public static final int ANIM_BLAST = 1, ANIM_RAPID = 2, ANIM_BLADE = 3, ANIM_BURST = 4, ANIM_DASH = 5, ANIM_SHIELD = 6, ANIM_OVERLOAD = 7, ANIM_SLASH = 8,
			ANIM_PUNCH = 9, ANIM_SHIELD_END = 10;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.shock_rock.<id>)
	public static final String BLAST = "energy_blast", RAPID = "rapid_bolts", BLADE = "energy_blade", BURST = "rock_burst", DASH = "energy_dash",
			SHIELD = "shield_construct", OVERLOAD = "overload_mode", FISTS = "rock_fists", ARMOUR = "rock_armour", CORE = "fulmini_core",
			SHORT = "short_circuit";
	public static final String ENERGY_KEY = "energy", BLADE_UNTIL = "blade_until", BLADE_HITS = "blade_hits", SHIELD_UNTIL = "shield_until", DASH_UNTIL = "dash_until",
			RAPID_START = "rapid_start";

	// ---------------------------------------------------------------- numbers (quick 5-8, normal 12-17, big 17-20)
	public static final float BLAST_DAMAGE = 8f, BOLT_DAMAGE = 5f, BLADE_DAMAGE = 13f, BLADE_CLEAVE = 6f, BURST_DAMAGE = 18f, DASH_DAMAGE = 6f, OVERLOAD_BONUS = 1.35f,
			SHOT_TAKEN = 0.67f;
	public static final int MAX_ENERGY = 100, OVERLOAD_MIN = 25, BLADE_TICKS = 200, BLADE_MAX_HITS = 6, SHIELD_TICKS = 100, DASH_TICKS = 6, BOLTS = 5, BOLT_EVERY = 2,
			BURST_TICKS = 24;
	/** Overload spends 1 Energy every this many ticks (about 7 a second); it comes back 1 every {@link #REFILL_EVERY} (3 a second). */
	public static final int DRAIN_EVERY = 3, REFILL_EVERY = 7, WATER_DRAIN_EVERY = 5;
	public static final double BURST_RADIUS = 6, BLADE_CLEAVE_RADIUS = 1.8, DASH_SPEED = 1.9, SHIELD_COS = 0.34;

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

	// ---------------------------------------------------------------- server-only state
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, java.util.Set<Integer>> DASHED = new ConcurrentHashMap<>();

	private ShockRockKit() {
	}

	public static void register() {
		Aliens.register(new ShockRockKit());
	}

	public static void clearSessionState() {
		LAST_TICK.clear();
		DASHED.clear();
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
		return List.of(AlienMove.tap(BLAST, AbilitySlot.SLOT_1, 12), AlienMove.shift(RAPID, AbilitySlot.SLOT_1, 70),
				AlienMove.tap(BLADE, AbilitySlot.SLOT_2, 240),
				AlienMove.tap(DASH, AbilitySlot.SLOT_3, 50),
				AlienMove.tap(BURST, AbilitySlot.SLOT_4, 360),
				AlienMove.tap(OVERLOAD, AbilitySlot.SLOT_5, 40),
				AlienMove.tap(SHIELD, AbilitySlot.SLOT_6, 200),
				AlienMove.vanilla(FISTS, "Attack", 0), AlienMove.vanilla(ARMOUR, "Passive", 0), AlienMove.vanilla(CORE, "Passive", 0),
				AlienMove.vanilla(SHORT, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> flag(p, F_BLADE);
			case SLOT_5 -> flag(p, F_OVERLOAD);
			case SLOT_6 -> flag(p, F_SHIELD);
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
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : new String[] { ENERGY_KEY, BLADE_UNTIL, BLADE_HITS, SHIELD_UNTIL, DASH_UNTIL, RAPID_START }) {
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
		if ((flag(p, F_DASH)) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		if (flag(p, F_SHIELD) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_DROWNING)) {
			Vec3 from = source.getSourcePosition();
			if (from != null && inFront(p, from)) {
				Vec3 hit = p.position().add(0, 1.0, 0).add(from.subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.9));
				level(p).sendParticles(SPARK, hit.x, hit.y, hit.z, 10, 0.2, 0.3, 0.2, 0.15);
				OmnitrixSfx.play(level(p), hit, SND_BLOCK, 0.9f, 1f);
				if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile pr) {
					pr.setDeltaMovement(pr.getDeltaMovement().scale(-0.4));
				}
				return false;
			}
		}
		return true;
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
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					rapidBolts(p);
				} else {
					energyBlast(p);
				}
			}
			case SLOT_2 -> energyBlade(p);
			case SLOT_3 -> energyDash(p);
			case SLOT_4 -> rockBurst(p);
			case SLOT_5 -> overload(p);
			case SLOT_6 -> shield(p);
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
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(BLADE_HITS), (long) (bladeHits(p) - 1));
		Omnitrix.save(p, n);
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level(p).sendParticles(SPARK, c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.1);
		OmnitrixSfx.play(level(p), c, SND_SLASH, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- R / Shift+R

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

	// ---------------------------------------------------------------- G: Energy Blade

	/** Hits the blade has left (client-safe). */
	public static int bladeHits(Player p) {
		return (int) Omnitrix.state(p).readyAt(ID + "." + BLADE_HITS);
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

	// ---------------------------------------------------------------- Z: Rock Burst

	/** Z: the rock armour flies apart and slams back. Returns how many it hit (-1 if not ready). */
	public int rockBurst(ServerPlayer p) {
		if (!use(p, BURST)) {
			return -1;
		}
		anim(p, ANIM_BURST);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : around(p, c, BURST_RADIUS)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, BURST_DAMAGE * power(p), false)) {
				shove(e, p.position(), 1.3, 0.45);
				n++;
			}
		}
		// his rock flies out and comes back together (the shards reassemble), and the bared core sends out a cyan pulse -- no fireball
		// (QuirkFx.blast's flipbook is fire-coloured and turned his energy yellow)
		QuirkFx.shards(l, c).block(Blocks.DEEPSLATE.defaultBlockState()).size((float) BURST_RADIUS * 0.7f).count(26).life(BURST_TICKS).flags(QuirkFx.F_REASSEMBLE)
				.spawn();
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(ENERGY_HOT, ENERGY).size((float) BURST_RADIUS).thickness(0.4f).life(12).ground()
				.spawn();
		QuirkFx.ring(l, c).style(QuirkFx.RING_SOFT).colours(ENERGY_HOT, ENERGY).size((float) BURST_RADIUS * 0.8f).thickness(1.0f).life(10).spawn();
		l.sendParticles(SPARK, c.x, c.y, c.z, 40, 1.2, 0.8, 1.2, 0.3);
		OmnitrixSfx.play(l, c, SND_BURST, 1.2f, 1f);
		return n;
	}

	// ---------------------------------------------------------------- X: Energy Dash

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
			java.util.Set<Integer> hit = DASHED.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet());
			for (LivingEntity e : around(p, p.position().add(0, 1.0, 0), 1.6)) {
				if (hit.add(e.getId())) {
					e.invulnerableTime = 0;
					hurt(p, e, DASH_DAMAGE * power(p), false);
					shove(e, p.position(), 0.7, 0.3);
				}
			}
		}
	}

	// ---------------------------------------------------------------- C: Shield Construct

	/** C: 5 s of an energy shield in front of him. */
	public boolean shield(ServerPlayer p) {
		if (flag(p, F_SHIELD) || !use(p, SHIELD)) {
			return false;
		}
		anim(p, ANIM_SHIELD);
		setTimer(p, SHIELD_UNTIL, p.level().getGameTime() + SHIELD_TICKS);
		setFlag(p, F_SHIELD, true);
		OmnitrixSfx.play(level(p), p.position(), SND_FORM, 1f, 0.9f);
		return true;
	}

	// ---------------------------------------------------------------- V: Overload Mode

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
}
