package com.projecthero.mod.omnitrix.alien.benwolf;

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
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "make aliens based on their abilities and stuff they do in the show please"). Benwolf -- Blitzwolfer, a Loboan (Ben 10
 * classic, a hidden prototype alien found by a mistransform): a werewolf with a werewolf's strength and spring, claws, and ears and a nose
 * that miss nothing. His jaw splits into four to fire a sonic howl, the blast that knocks everything in front of him flat. The four-way
 * jaw opens on the model for every howl ({@link #jawOpen}), and the howls send rings of sound out ahead of him.
 *
 * <pre>
 *   R          Claw Swipe       a slash in front of him (3.5 blocks, 120 deg): 8
 *   Shift+R    Pounce           a leap at the creature in the crosshair (16 blocks) or ahead: landing on it hits everything there
 *                               for 14 and pins it for 1 s
 *   G          Sonic Howl       the jaw splits open: a 14-block cone of sound, 12 to everything in it and thrown back hard
 *   Shift+G    Disorienting Howl (v0.17.4; Focused Howl, the third howl in one kit, is gone) a low warbling howl round him (10 blocks): 4,
 *                               Nausea, and the mobs it catches attack each other for 4 s
 *   Hold Z     Howl Shockwave   charge it up to 2 s, then a howl in every direction: 14 within 4 blocks uncharged, up to 22
 *                               within 9 fully charged, thrown back and up (a tap fires it uncharged)
 *   X          Wolf Leap        a long bounding leap where he looks; no fall damage
 *   Shift+X    Wall Bound       (v0.17.4) kick off the wall he faces (or stands beside) for a high bound away from it, 3 before landing
 *   Hold X     Hunt Sprint      (v0.17.4) down on all fours while held: Speed III, steps up blocks, no attacks
 *   Shift+Z    Moon Howl        (v0.17.4) 10 s of Strength II and Speed I, and Strength I for his tamed wolves within 16
 *   Shift+V    Scent Mark       (v0.17.4) the creature he looks at stays outlined for him at any range for 30 s and leaves a scent trail
 *   Shift+C    Maul             (v0.17.4) seize the creature ahead and slam it down: 16, pinned 1.5 s (a boss: 16, no pin)
 *   C          Frenzy           a flurry of six claw slashes over 1.2 s (3 blocks ahead): 5 each
 *   V          Tracking Senses  15 s: he hears and smells everything within 32 blocks -- every creature and player outlined (his
 *                               eyes only) -- and sees in the dark
 *   Attack     Loboan Claws     his punches are claws (+7)
 *   Passives   Loboan Strength (a heavy, springy body that shrugs off falls); Keen Senses (night vision in the dark)
 *   Weakness   Sensitive Ears (explosions and sonic booms hit him half again and leave him reeling)
 * </pre>
 */
public final class BenwolfKit extends AlienKit {
	public static final String ID = "benwolf";
	/** His howl: a pale blue-white, the colour the show draws its sound rings. */
	public static final int SONIC = 0xBFE6FF, SONIC_DEEP = 0x5AA9FF;

	// ---------------------------------------------------------------- synced flags / poses
	public static final int F_HOWL = 1, F_CHARGE = 2, F_POUNCE = 4, F_SENSES = 8, F_FRENZY = 16, F_LEAP = 32, F_SPRINT = 64, F_BOUND = 128;
	public static final int ANIM_CLAW = 1, ANIM_POUNCE = 2, ANIM_HOWL = 3, ANIM_CHARGE = 5, ANIM_SHOCKWAVE = 6, ANIM_LEAP = 7, ANIM_FRENZY = 8,
			ANIM_SENSES = 9, ANIM_LAND = 10, ANIM_PUNCH = 11, ANIM_DISORIENT = 12, ANIM_BOUND = 13, ANIM_SPRINT = 14, ANIM_MOON = 15, ANIM_SCENT = 16, ANIM_MAUL = 17;

	// ---------------------------------------------------------------- moves (lang projecthero.omnitrix.benwolf.<id>)
	public static final String CLAW = "claw_swipe", POUNCE = "pounce", HOWL = "sonic_howl", DISORIENT = "disorienting_howl", SHOCKWAVE = "howl_shockwave",
			MOON = "moon_howl", LEAP = "wolf_leap", BOUND = "wall_bound", SPRINT = "hunt_sprint", FRENZY = "frenzy", MAUL = "maul", SENSES = "tracking_senses",
			SCENT = "scent_mark", CLAWS = "loboan_claws", STRENGTH = "loboan_strength", KEEN = "keen_senses",
			EARS = "sensitive_ears";
	public static final String HOWL_UNTIL = "howl_until", CHARGE_START = "charge_start", POUNCE_UNTIL = "pounce_until", SENSES_UNTIL = "senses_until",
			FRENZY_UNTIL = "frenzy_until", LEAP_UNTIL = "leap_until", SCENT_ID = "scent_id", SCENT_UNTIL = "scent_until", BOUNDS = "bounds", BOUND_AT = "bound_at";
	private static final String[] TIMERS = { HOWL_UNTIL, CHARGE_START, POUNCE_UNTIL, SENSES_UNTIL, FRENZY_UNTIL, LEAP_UNTIL, SCENT_ID, SCENT_UNTIL, BOUNDS,
			BOUND_AT };

	// ---------------------------------------------------------------- numbers (quick 5-8, normal 12-17, big 17-20, ultimate 21+)
	public static final float CLAW_DAMAGE = 8f, POUNCE_DAMAGE = 14f, HOWL_DAMAGE = 12f, DISORIENT_DAMAGE = 4f, SHOCK_MIN = 14f, SHOCK_MAX = 22f, FRENZY_DAMAGE = 5f,
			MAUL_DAMAGE = 16f, EARS_TAKEN = 1.5f;
	public static final double CLAW_RANGE = 3.5, CLAW_HALF_ANGLE = 60, POUNCE_RANGE = 16, POUNCE_HIT = 2.4, HOWL_RANGE = 14, HOWL_HALF_ANGLE = 30,
			DISORIENT_RADIUS = 10, SHOCK_R_MIN = 4, SHOCK_R_MAX = 9, FRENZY_RANGE = 3.0, SENSE_RANGE = 32, MAUL_RANGE = 3.5, SCENT_RANGE = 32, MOON_WOLVES = 16;
	/** v0.17.4: Disorienting Howl's turn and Nausea, Wall Bound's chain, Moon Howl, Scent Mark, Maul's pin (ticks / counts). */
	public static final int DISORIENT_TICKS = 80, DISORIENT_NAUSEA = 100, BOUND_CHAIN = 3, MOON_TICKS = 200, SCENT_TICKS = 600, MAUL_PIN = 30;
	public static final float SPRINT_STEP = 1.1f;
	public static final int HOWL_TICKS = 10, CHARGE_FULL = 40, POUNCE_TICKS = 30, POUNCE_PIN = 20, FRENZY_HITS = 6, FRENZY_EVERY = 4, SENSES_TICKS = 300,
			LEAP_TICKS = 40;

	// ---------------------------------------------------------------- sounds (the sounds test: volume <= 0.75, pitch >= 0.5)
	public static final OmnitrixSfx.Cue SND_HOWL = OmnitrixSfx.cue("benwolf_howl")
			.layer(SoundEvents.WOLF_HOWL, 0.75f, 0.7f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.25f, 1.6f, 2)
			.layer(SoundEvents.WOLF_HOWL, 0.5f, 1.1f, 3)
			.gap(6);
	public static final OmnitrixSfx.Cue SND_SHOCKWAVE = OmnitrixSfx.cue("benwolf_shockwave")
			.layer(SoundEvents.WOLF_HOWL, 0.75f, 0.55f)
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.9f, 1)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.3f, 0.8f, 2)
			.gap(8);
	public static final OmnitrixSfx.Cue SND_CHARGE = OmnitrixSfx.cue("benwolf_charge")
			.layer(SoundEvents.WOLF_GROWL, 0.6f, 0.6f)
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.25f, 1.4f)
			.gap(10);
	public static final OmnitrixSfx.Cue SND_CLAW = OmnitrixSfx.cue("benwolf_claw")
			.layer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 1.2f)
			.layer(SoundEvents.WOLF_GROWL, 0.3f, 1.1f)
			.gap(2);
	public static final OmnitrixSfx.Cue SND_LEAP = OmnitrixSfx.cue("benwolf_leap")
			.layer(SoundEvents.WOLF_GROWL, 0.45f, 0.9f)
			.layer(SoundEvents.GOAT_LONG_JUMP, 0.6f, 0.8f)
			.gap(3);
	public static final OmnitrixSfx.Cue SND_LAND = OmnitrixSfx.cue("benwolf_land")
			.layer(SoundEvents.RAVAGER_ATTACK, 0.45f, 1.1f)
			.layer(SoundEvents.PLAYER_ATTACK_CRIT, 0.5f, 0.8f)
			.gap(3);
	/** v0.17.4 Disorienting Howl: a low howl wavering up and down. */
	public static final OmnitrixSfx.Cue SND_WARBLE = OmnitrixSfx.cue("benwolf_warble")
			.layer(SoundEvents.WOLF_HOWL, 0.7f, 0.5f)
			.layer(SoundEvents.WOLF_HOWL, 0.4f, 0.65f, 4)
			.layer(SoundEvents.WOLF_HOWL, 0.4f, 0.55f, 9)
			.layer(SoundEvents.WARDEN_SONIC_CHARGE, 0.2f, 0.8f, 2)
			.gap(10);
	/** v0.17.4 Moon Howl: the long howl at the sky. */
	public static final OmnitrixSfx.Cue SND_MOON = OmnitrixSfx.cue("benwolf_moon")
			.layer(SoundEvents.WOLF_HOWL, 0.75f, 0.85f)
			.layer(SoundEvents.WOLF_HOWL, 0.5f, 1.0f, 6)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4f, 0.9f, 2)
			.gap(12);
	public static final OmnitrixSfx.Cue SND_SNIFF = OmnitrixSfx.cue("benwolf_sniff")
			.layer(SoundEvents.SNIFFER_SNIFFING, 0.6f, 1.3f)
			.layer(SoundEvents.WOLF_PANT, 0.5f, 0.8f, 6)
			.gap(6);

	// ---------------------------------------------------------------- server-only state
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> POUNCE_TARGET = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> FRENZY_LEFT = new ConcurrentHashMap<>();
	/** v0.17.4: the scent trail's colour (seen by him alone). */
	public static final net.minecraft.core.particles.DustParticleOptions SCENT_DUST = new net.minecraft.core.particles.DustParticleOptions(
			new org.joml.Vector3f(1.0f, 0.85f, 0.35f), 0.9f);

	private BenwolfKit() {
	}

	public static void register() {
		Aliens.register(new BenwolfKit());
	}

	public static void clearSessionState() {
		LAST_TICK.clear();
		POUNCE_TARGET.clear();
		FRENZY_LEFT.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	/** {@code p} is Benwolf right now (client-safe). */
	public static boolean isBenwolf(Player p) {
		return Omnitrix.hasPower(p) && Omnitrix.state(p).transformed() && ID.equals(Omnitrix.state(p).active);
	}

	@Override
	public AlienStats stats() {
		// a tall, rangy werewolf: strong, quick and springy
		return AlienStats.builder().health(40f).speed(1.3f).scale(1.12f).armor(10f, 2f).knockbackResist(0.2f).attack(7f).jump(0.45f).safeFall(10f).step(0.5f)
				.build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(CLAW, AbilitySlot.SLOT_1, 12), AlienMove.shift(POUNCE, AbilitySlot.SLOT_1, 120),
				AlienMove.tap(HOWL, AbilitySlot.SLOT_2, 100), AlienMove.shift(DISORIENT, AbilitySlot.SLOT_2, 360),
				AlienMove.tap(LEAP, AbilitySlot.SLOT_3, 40), AlienMove.shift(BOUND, AbilitySlot.SLOT_3, 20), AlienMove.hold(SPRINT, AbilitySlot.SLOT_3, 0),
				AlienMove.hold(SHOCKWAVE, AbilitySlot.SLOT_4, 360), AlienMove.shift(MOON, AbilitySlot.SLOT_4, 800),
				AlienMove.tap(SENSES, AbilitySlot.SLOT_5, 500), AlienMove.shift(SCENT, AbilitySlot.SLOT_5, 300),
				AlienMove.tap(FRENZY, AbilitySlot.SLOT_6, 200), AlienMove.shift(MAUL, AbilitySlot.SLOT_6, 240),
				AlienMove.vanilla(CLAWS, "Attack", 0), AlienMove.vanilla(STRENGTH, "Passive", 0), AlienMove.vanilla(KEEN, "Passive", 0),
				AlienMove.vanilla(EARS, "Weakness", 0));
	}

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> flag(p, F_SPRINT);
			case SLOT_4 -> flag(p, F_CHARGE);
			case SLOT_5 -> flag(p, F_SENSES);
			case SLOT_6 -> flag(p, F_FRENZY);
			default -> false;
		};
	}

	@Override
	public boolean holdable(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 || slot == AbilitySlot.SLOT_4;
	}

	/** v0.17.4 Hunt Sprint: down on all fours he steps up whole blocks (both sides: a synced flag). */
	@Override
	public float stepHeight(Player p) {
		return (Omnitrix.state(p).kitFlags & F_SPRINT) != 0 ? SPRINT_STEP : -1f;
	}

	// ---------------------------------------------------------------- the jaw (client-safe)

	/**
	 * How far his four-way jaw is split open right now, 0..1 (client-safe: synced flags, pose and its start): wide for a howl, opening as
	 * the Shockwave charges, a snarl for the pounce.
	 */
	public static float jawOpen(Player p, float ticks) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & F_CHARGE) != 0) {
			long start = s.readyAt(ID + "." + CHARGE_START);
			float held = start > 0 ? p.level().getGameTime() - start : ticks;
			return 0.25f + 0.55f * Mth.clamp(held / CHARGE_FULL, 0f, 1f);
		}
		return switch (s.animId) {
			case ANIM_HOWL -> bump(ticks, 2f, 14f);
			case ANIM_DISORIENT -> bump(ticks, 2f, 22f) * (0.75f + 0.25f * Mth.sin(ticks * 0.9f));
			case ANIM_MOON -> bump(ticks, 3f, 30f);
			case ANIM_SHOCKWAVE -> bump(ticks, 1f, 16f);
			case ANIM_POUNCE, ANIM_FRENZY, ANIM_MAUL -> bump(ticks, 2f, 10f) * 0.3f;
			default -> 0f;
		};
	}

	private static float bump(float t, float in, float end) {
		return Mth.clamp(Math.min(t / in, (end - t) / 3f), 0f, 1f);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 0.5f, 1.1f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		LAST_TICK.remove(p.getUUID());
		POUNCE_TARGET.remove(p.getUUID());
		FRENZY_LEFT.remove(p.getUUID());
		com.projecthero.mod.omnitrix.alien.AlienConfusion.clear(p);
		if (flag(p, F_SENSES)) {
			p.removeEffect(MobEffects.NIGHT_VISION);
		}
		if (flag(p, F_SPRINT)) {
			p.removeEffect(MobEffects.MOVEMENT_SPEED);
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String t : TIMERS) {
			n.timers.remove(key(t));
		}
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		if ((flag(p, F_LEAP) || flag(p, F_POUNCE) || flag(p, F_BOUND)) && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		return true;
	}

	@Override
	public float incomingDamage(ServerPlayer p, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(net.minecraft.world.damagesource.DamageTypes.SONIC_BOOM)) {
			// Sensitive Ears: the noise of it hurts as much as the blast
			p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, true));
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
			return amount * EARS_TAKEN;
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
		if (flag(p, F_HOWL)) {
			howlTick(p, l, now);
		}
		if (flag(p, F_CHARGE)) {
			chargeTick(p, l, now);
		}
		if (flag(p, F_POUNCE)) {
			pounceTick(p, l, now);
		}
		if (flag(p, F_FRENZY)) {
			frenzyTick(p, l, now);
		}
		if (flag(p, F_LEAP) && (now >= timer(p, LEAP_UNTIL) || p.onGround() && now >= timer(p, LEAP_UNTIL) - LEAP_TICKS + 4)) {
			setFlag(p, F_LEAP, false);
			setTimer(p, LEAP_UNTIL, 0L);
		}
		if (flag(p, F_SPRINT)) {
			sprintTick(p, l, now);
		}
		if ((flag(p, F_BOUND) || timer(p, BOUNDS) > 0) && now - timer(p, BOUND_AT) > 3 && (p.onGround() || p.isInWater())) {
			// landed: the wall-bound chain starts again
			setFlag(p, F_BOUND, false);
			setTimer(p, BOUNDS, 0L);
		}
		if (timer(p, SCENT_UNTIL) > 0) {
			scentTick(p, l, now);
		}
		com.projecthero.mod.omnitrix.alien.AlienConfusion.tick(p);
		if (flag(p, F_SENSES)) {
			if (now >= timer(p, SENSES_UNTIL)) {
				setFlag(p, F_SENSES, false);
				setTimer(p, SENSES_UNTIL, 0L);
				p.removeEffect(MobEffects.NIGHT_VISION);
			} else if (!p.hasEffect(MobEffects.NIGHT_VISION)) {
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, (int) (timer(p, SENSES_UNTIL) - now) + 10, 0, false, false, true));
			}
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (flag(p, F_CHARGE) && slot != AbilitySlot.SLOT_4) {
			return; // mid-charge his jaw is busy
		}
		if (flag(p, F_SPRINT) && slot != AbilitySlot.SLOT_3 && slot != AbilitySlot.SLOT_5) {
			return; // on all fours: running, not fighting
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					pounce(p);
				} else {
					clawSwipe(p);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					disorientingHowl(p);
				} else {
					sonicHowl(p);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					wallBound(p);
				} else if (!flag(p, F_SPRINT)) {
					wolfLeap(p);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					moonHowl(p);
				} else if (ready(p, SHOCKWAVE)) {
					shockwave(p, 0f); // a tap: uncharged
				} else {
					use(p, SHOCKWAVE); // the "not ready" line
				}
			}
			case SLOT_5 -> {
				if (shift) {
					scentMark(p);
				} else {
					trackingSenses(p);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					maul(p);
				} else {
					frenzy(p);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void holdStart(ServerPlayer p, AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_4 && !flag(p, F_SPRINT)) {
			startCharge(p);
		} else if (slot == AbilitySlot.SLOT_3 && !flag(p, F_CHARGE)) {
			startSprint(p);
		}
	}

	@Override
	public void holdEnd(ServerPlayer p, AbilitySlot slot, int heldTicks) {
		if (slot == AbilitySlot.SLOT_3) {
			endSprint(p);
		}
		if (slot == AbilitySlot.SLOT_4 && flag(p, F_CHARGE)) {
			long start = timer(p, CHARGE_START);
			float k = start > 0 ? Mth.clamp((p.level().getGameTime() - start) / (float) CHARGE_FULL, 0f, 1f) : 0f;
			setFlag(p, F_CHARGE, false);
			setTimer(p, CHARGE_START, 0L);
			shockwave(p, k);
		}
	}

	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		if (flag(p, F_SPRINT)) {
			return true; // Hunt Sprint: all four paws are running
		}
		if (target instanceof LivingEntity) {
			anim(p, ANIM_PUNCH); // Loboan Claws: vanilla's hit (+7 attack), swung as a claw
		}
		return false;
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: a claw slash in front of him. Returns how many it hit (-1 if not ready). */
	public int clawSwipe(ServerPlayer p) {
		if (!use(p, CLAW)) {
			return -1;
		}
		anim(p, ANIM_CLAW);
		int n = 0;
		for (LivingEntity e : RecalKitMath.cone(p, CLAW_RANGE, CLAW_HALF_ANGLE)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, CLAW_DAMAGE, false)) {
				shove(e, p.position(), 0.4, 0.15);
				n++;
			}
		}
		Vec3 front = p.position().add(0, 1.1 * p.getScale(), 0).add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y, front.z, 1, 0, 0, 0, 0);
		OmnitrixSfx.play(level(p), p.position(), SND_CLAW, 1f, 1f);
		return n;
	}

	/** Shift+R: a leap at the creature in the crosshair (or straight ahead), landing on it. */
	public boolean pounce(ServerPlayer p) {
		if (flag(p, F_POUNCE) || !use(p, POUNCE)) {
			return false;
		}
		anim(p, ANIM_POUNCE);
		LivingEntity t = lookTarget(p, POUNCE_RANGE);
		Vec3 v;
		if (t != null) {
			POUNCE_TARGET.put(p.getUUID(), t.getId());
			Vec3 d = t.position().subtract(p.position());
			double h = Math.sqrt(d.x * d.x + d.z * d.z);
			// a flat arc that lands on it: the flight time follows the distance (0.08 gravity a tick), and the air's drag (x0.91 a tick)
			// is paid back up front so he does not fall short
			double ticks = Mth.clamp(h / 1.0, 5, 14);
			double reach = Math.max(0.5, h - 0.6);
			double speed = reach * (1 - 0.91) / (1 - Math.pow(0.91, ticks));
			Vec3 dir = h < 1e-4 ? Vec3.ZERO : new Vec3(d.x / h, 0, d.z / h);
			v = dir.scale(speed).add(0, Mth.clamp(d.y / ticks + 0.042 * ticks, 0.3, 1.1), 0);
		} else {
			POUNCE_TARGET.remove(p.getUUID());
			Vec3 look = p.getLookAngle();
			Vec3 h = new Vec3(look.x, 0, look.z);
			h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize();
			v = h.scale(1.3).add(0, 0.5, 0);
		}
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, POUNCE_UNTIL, p.level().getGameTime() + POUNCE_TICKS);
		setFlag(p, F_POUNCE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 1f, 1.1f);
		return true;
	}

	private void pounceTick(ServerPlayer p, ServerLevel l, long now) {
		long until = timer(p, POUNCE_UNTIL);
		boolean airborne = now - (until - POUNCE_TICKS) < 3;
		Integer id = POUNCE_TARGET.get(p.getUUID());
		Entity t = id == null ? null : l.getEntity(id);
		boolean onIt = t instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < POUNCE_HIT * POUNCE_HIT;
		if (!(onIt || !airborne && p.onGround() || now >= until)) {
			return;
		}
		setFlag(p, F_POUNCE, false);
		setTimer(p, POUNCE_UNTIL, 0L);
		POUNCE_TARGET.remove(p.getUUID());
		anim(p, ANIM_LAND);
		Vec3 at = p.position().add(0, 0.5, 0);
		for (LivingEntity e : around(p, at, POUNCE_HIT)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, POUNCE_DAMAGE, false)) {
				AlienStatus.root(p, e, POUNCE_PIN, 10);
			}
		}
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 0.5, 0.2));
		p.hurtMarked = true;
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, 0x9AA4B8).size(2.6f).thickness(0.2f).life(8).ground().spawn();
		l.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.5, 0.4, 0.5, 0.2);
		OmnitrixSfx.play(l, p.position(), SND_LAND, 1f, 1f);
	}

	// ---------------------------------------------------------------- G / Shift+G: the howls

	/** Where the howl leaves his split jaw. */
	public static Vec3 jaw(Player p) {
		return p.getEyePosition().add(p.getLookAngle().scale(0.45 * p.getScale())).add(0, -0.1 * p.getScale(), 0);
	}

	/** G: the jaw splits into four and a cone of sound flattens everything ahead. Returns how many it hit. */
	public int sonicHowl(ServerPlayer p) {
		if (!use(p, HOWL)) {
			return -1;
		}
		anim(p, ANIM_HOWL);
		setTimer(p, HOWL_UNTIL, p.level().getGameTime() + HOWL_TICKS);
		setFlag(p, F_HOWL, true);
		int n = 0;
		for (LivingEntity e : RecalKitMath.cone(p, HOWL_RANGE, HOWL_HALF_ANGLE)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, HOWL_DAMAGE, false)) {
				double fall = 1.0 - 0.5 * Math.min(1.0, e.distanceTo(p) / HOWL_RANGE);
				shove(e, p.position(), 1.7 * fall, 0.45);
				n++;
			}
		}
		// the sound itself is rings, not a flame cone: the first leaves the split jaw now, howlTick rolls the rest out along the cone
		Vec3 m = jaw(p);
		QuirkFx.ring(level(p), m.add(p.getLookAngle().scale(0.4))).flags(QuirkFx.F_VERTICAL).vec(p.getLookAngle()).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, SONIC)
				.size(0.5f).thickness(0.25f).life(6).spawn();
		level(p).sendParticles(ParticleTypes.SONIC_BOOM, m.x + p.getLookAngle().x * 2, m.y + p.getLookAngle().y * 2, m.z + p.getLookAngle().z * 2, 1, 0, 0, 0, 0);
		OmnitrixSfx.playOn(p, SND_HOWL, 1.2f, 1f, SoundSource.PLAYERS);
		return n;
	}

	/** The howl's rings of sound, one a tick, rolling out along his aim (the cone's own reach). */
	private void howlTick(ServerPlayer p, ServerLevel l, long now) {
		long until = timer(p, HOWL_UNTIL);
		if (now >= until) {
			setFlag(p, F_HOWL, false);
			setTimer(p, HOWL_UNTIL, 0L);
			return;
		}
		int i = (int) (HOWL_TICKS - (until - now));
		double d = 1.0 + i * (HOWL_RANGE - 1.0) / HOWL_TICKS;
		Vec3 look = p.getLookAngle();
		Vec3 at = jaw(p).add(look.scale(d));
		float r = (float) (0.6 + d * Math.tan(Math.toRadians(HOWL_HALF_ANGLE)));
		QuirkFx.ring(l, at).flags(QuirkFx.F_VERTICAL).vec(look).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, SONIC).size(r).thickness(0.22f).life(7).spawn();
	}

	/**
	 * Shift+G (v0.17.4): Disorienting Howl -- a low, wavering howl round him (10 blocks): 4 and Nausea, and every mob it catches turns on
	 * the mobs round it for 4 s (never a boss). Returns how many it hit.
	 */
	public int disorientingHowl(ServerPlayer p) {
		if (!use(p, DISORIENT)) {
			return -1;
		}
		anim(p, ANIM_DISORIENT);
		ServerLevel l = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : around(p, c, DISORIENT_RADIUS)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, DISORIENT_DAMAGE, false)) {
				n++;
			}
			e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DISORIENT_NAUSEA, 0), p);
			com.projecthero.mod.omnitrix.alien.AlienConfusion.turn(p, e, DISORIENT_TICKS);
		}
		// the sound wobbling out round him: soft rings swelling at different rates, purple-blue
		for (int i = 0; i < 4; i++) {
			QuirkFx.ring(l, c.add(0, (i - 1.5) * 0.35, 0)).style(QuirkFx.RING_SOFT).colours(0xE6D8FF, 0x7A6CFF).size((float) DISORIENT_RADIUS * (0.4f + i * 0.2f))
					.thickness(0.25f).life(10 + i * 4).spawn();
		}
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xE6D8FF, 0x7A6CFF).size((float) DISORIENT_RADIUS).thickness(0.3f).life(16)
				.ground().spawn();
		l.sendParticles(ParticleTypes.WITCH, c.x, c.y, c.z, 30, DISORIENT_RADIUS * 0.3, 0.6, DISORIENT_RADIUS * 0.3, 0.02);
		OmnitrixSfx.playOn(p, SND_WARBLE, 1.1f, 1f, SoundSource.PLAYERS);
		return n;
	}

	// ---------------------------------------------------------------- Z: Howl Shockwave

	private boolean startCharge(ServerPlayer p) {
		if (flag(p, F_CHARGE)) {
			return false;
		}
		if (!ready(p, SHOCKWAVE)) {
			use(p, SHOCKWAVE); // the "not ready" line
			return false;
		}
		anim(p, ANIM_CHARGE);
		setTimer(p, CHARGE_START, p.level().getGameTime());
		setFlag(p, F_CHARGE, true);
		OmnitrixSfx.play(level(p), p.position(), SND_CHARGE, 1f, 1f);
		return true;
	}

	/** Test hook: starts the Shockwave's charge as a held Z would. */
	public boolean beginCharge(ServerPlayer p) {
		return startCharge(p);
	}

	private void chargeTick(ServerPlayer p, ServerLevel l, long now) {
		long start = timer(p, CHARGE_START);
		float k = Mth.clamp((now - start) / (float) CHARGE_FULL, 0f, 1f);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 1, false, false, false));
		if (now % 5 == 0) {
			// air drawn in: a ring closing on him, tighter as it builds
			QuirkFx.ring(l, p.position().add(0, 1.0 * p.getScale(), 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, SONIC).size(3.5f - 1.5f * k).thickness(0.12f).life(5)
					.spawn();
		}
		if (now - start > CHARGE_FULL + 60) {
			// held far past full: it goes off by itself
			setFlag(p, F_CHARGE, false);
			setTimer(p, CHARGE_START, 0L);
			shockwave(p, 1f);
		}
	}

	/** The howl in every direction; {@code k} = how charged (0..1). Returns how many it hit (-1 if not ready). */
	public int shockwave(ServerPlayer p, float k) {
		cooldown(p, SHOCKWAVE, -1);
		anim(p, ANIM_SHOCKWAVE);
		ServerLevel l = level(p);
		double r = Mth.lerp(k, SHOCK_R_MIN, SHOCK_R_MAX);
		float dmg = Mth.lerp(k, SHOCK_MIN, SHOCK_MAX);
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : around(p, c, r)) {
			e.invulnerableTime = 0;
			if (hurt(p, e, dmg, false)) {
				shove(e, p.position(), 1.2 + 0.8 * k, 0.5 + 0.3 * k);
				n++;
			}
		}
		for (int i = 0; i < 3; i++) {
			QuirkFx.ring(l, c.add(0, (i - 1) * 0.6, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, SONIC).size((float) r * (0.6f + i * 0.2f)).thickness(0.3f).life(10 + i * 2)
					.spawn();
		}
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, SONIC_DEEP).size((float) r).thickness(0.4f).life(12).ground().spawn();
		l.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		OmnitrixSfx.play(l, c, SND_SHOCKWAVE, 1.0f + 0.4f * k, 1f);
		return n;
	}

	// ---------------------------------------------------------------- X: Wolf Leap

	/** X: a long bounding leap where he looks. */
	public boolean wolfLeap(ServerPlayer p) {
		if (!use(p, LEAP)) {
			return false;
		}
		anim(p, ANIM_LEAP);
		Vec3 look = p.getLookAngle();
		Vec3 h = new Vec3(look.x, 0, look.z);
		h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize();
		p.setDeltaMovement(h.scale(1.5).add(0, 0.65 + Math.max(0, look.y) * 0.55, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		setTimer(p, LEAP_UNTIL, p.level().getGameTime() + LEAP_TICKS);
		setFlag(p, F_LEAP, true);
		level(p).sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- C: Frenzy

	/** C: a flurry of six claw slashes over 1.2 s (the first on the press). */
	public boolean frenzy(ServerPlayer p) {
		if (flag(p, F_FRENZY) || !use(p, FRENZY)) {
			return false;
		}
		anim(p, ANIM_FRENZY);
		setTimer(p, FRENZY_UNTIL, p.level().getGameTime());
		setFlag(p, F_FRENZY, true);
		FRENZY_LEFT.put(p.getUUID(), FRENZY_HITS - 1);
		slash(p, level(p));
		return true;
	}

	private void frenzyTick(ServerPlayer p, ServerLevel l, long now) {
		long since = now - timer(p, FRENZY_UNTIL);
		int left = FRENZY_LEFT.getOrDefault(p.getUUID(), 0);
		if (left <= 0 || since > (long) FRENZY_HITS * FRENZY_EVERY + 2) {
			setFlag(p, F_FRENZY, false);
			setTimer(p, FRENZY_UNTIL, 0L);
			FRENZY_LEFT.remove(p.getUUID());
			return;
		}
		if (since > 0 && since % FRENZY_EVERY == 0) {
			FRENZY_LEFT.put(p.getUUID(), left - 1);
			slash(p, l);
		}
	}

	/** One of the Frenzy's slashes. */
	private void slash(ServerPlayer p, ServerLevel l) {
		for (LivingEntity e : RecalKitMath.cone(p, FRENZY_RANGE, 70)) {
			e.invulnerableTime = 0;
			hurt(p, e, FRENZY_DAMAGE, false);
			e.setDeltaMovement(e.getDeltaMovement().multiply(0.4, 1, 0.4)); // held in the flurry, not knocked clear of it
		}
		Vec3 front = p.position().add(0, 1.1 * p.getScale(), 0).add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.4));
		l.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y + (l.random.nextDouble() - 0.5) * 0.6, front.z, 1, 0.2, 0.1, 0.2, 0);
		OmnitrixSfx.play(l, p.position(), SND_CLAW, 0.7f, 1.1f + l.random.nextFloat() * 0.2f);
	}

	// ---------------------------------------------------------------- V: Tracking Senses

	/** V: 15 s of everything within 32 blocks outlined (for him alone) and night vision. */
	public boolean trackingSenses(ServerPlayer p) {
		if (flag(p, F_SENSES) || !use(p, SENSES)) {
			return false;
		}
		anim(p, ANIM_SENSES);
		setTimer(p, SENSES_UNTIL, p.level().getGameTime() + SENSES_TICKS);
		setFlag(p, F_SENSES, true);
		p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SENSES_TICKS + 10, 0, false, false, true));
		QuirkFx.ring(level(p), p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xC8D2D2).size(6f).thickness(0.1f).life(16).ground().spawn();
		OmnitrixSfx.play(level(p), p.position(), SND_SNIFF, 1f, 1f);
		return true;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+X Wall Bound / Hold X Hunt Sprint

	/** The way out from the wall he faces (or stands beside), or null with no wall there. */
	@org.jetbrains.annotations.Nullable
	private static Vec3 wallNormal(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
		Vec3[] dirs = { f, new Vec3(-f.z, 0, f.x), new Vec3(f.z, 0, -f.x), f.scale(-1) };
		double reach = p.getBbWidth() * 0.5 + 0.45;
		for (Vec3 d : dirs) {
			for (double y : new double[] { 0.3, 1.1 }) {
				net.minecraft.core.BlockPos q = net.minecraft.core.BlockPos.containing(p.getX() + d.x * reach, p.getY() + y * p.getScale(), p.getZ() + d.z * reach);
				if (!p.level().getBlockState(q).getCollisionShape(p.level(), q).isEmpty()) {
					return d.scale(-1);
				}
			}
		}
		return null;
	}

	/** Shift+X: he kicks off the wall he faces (or is beside) for a high bound away from it -- up to 3 before he lands again. */
	public boolean wallBound(ServerPlayer p) {
		Vec3 out = wallNormal(p);
		if (out == null) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.benwolf.no_wall")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (timer(p, BOUNDS) >= BOUND_CHAIN) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.benwolf.bounds_spent")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, BOUND)) {
			return false;
		}
		anim(p, ANIM_BOUND);
		long now = p.level().getGameTime();
		setTimer(p, BOUNDS, timer(p, BOUNDS) + 1);
		setTimer(p, BOUND_AT, now);
		setFlag(p, F_BOUND, true);
		Vec3 look = p.getLookAngle();
		Vec3 along = new Vec3(look.x, 0, look.z).normalize().scale(0.25);
		p.setDeltaMovement(out.scale(0.95).add(along).add(0, 0.85, 0));
		p.hurtMarked = true;
		p.resetFallDistance();
		ServerLevel l = level(p);
		Vec3 kick = p.position().subtract(out.scale(0.5)).add(0, 0.6, 0);
		net.minecraft.world.level.block.state.BlockState wall = l.getBlockState(net.minecraft.core.BlockPos.containing(kick.subtract(out.scale(0.4))));
		if (!wall.isAir()) {
			l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, wall), kick.x, kick.y, kick.z, 14, 0.2, 0.4, 0.2, 0.1);
		}
		l.sendParticles(ParticleTypes.POOF, kick.x, kick.y, kick.z, 4, 0.1, 0.2, 0.1, 0.02);
		OmnitrixSfx.play(l, p.position(), SND_LEAP, 0.8f, 1.2f + 0.1f * timer(p, BOUNDS));
		return true;
	}

	/** Hold X: Hunt Sprint -- down on all fours while X is held: Speed III, steps up whole blocks, no attacks. No cooldown. */
	public boolean startSprint(ServerPlayer p) {
		if (flag(p, F_SPRINT)) {
			return false;
		}
		anim(p, ANIM_SPRINT);
		setFlag(p, F_SPRINT, true);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12, 2, false, false, true));
		OmnitrixSfx.play(level(p), p.position(), SND_LEAP, 0.6f, 0.8f);
		return true;
	}

	private void sprintTick(ServerPlayer p, ServerLevel l, long now) {
		if (now % 5 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12, 2, false, false, true));
		}
		if (p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() > 0.02 && now % 3 == 0) {
			net.minecraft.world.level.block.state.BlockState under = l.getBlockState(p.blockPosition().below());
			if (!under.isAir()) {
				l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3,
						0.05);
			}
		}
	}

	public void endSprint(ServerPlayer p) {
		if (!flag(p, F_SPRINT)) {
			return;
		}
		setFlag(p, F_SPRINT, false);
		p.removeEffect(MobEffects.MOVEMENT_SPEED);
		anim(p, ANIM_PUNCH);
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+Z Moon Howl

	/** Shift+Z: a long howl at the sky -- 10 s of Strength II and Speed I for him, Strength I for his tamed wolves within 16. Returns wolves. */
	public int moonHowl(ServerPlayer p) {
		if (!use(p, MOON)) {
			return -1;
		}
		anim(p, ANIM_MOON);
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MOON_TICKS, 1, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MOON_TICKS, 0, false, true, true));
		ServerLevel l = level(p);
		int wolves = 0;
		for (net.minecraft.world.entity.animal.Wolf w : l.getEntitiesOfClass(net.minecraft.world.entity.animal.Wolf.class, p.getBoundingBox().inflate(MOON_WOLVES))) {
			if (w.isTame() && w.isOwnedBy(p) && w.isAlive()) {
				w.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MOON_TICKS, 0, false, true, true));
				l.sendParticles(ParticleTypes.END_ROD, w.getX(), w.getY() + w.getBbHeight() + 0.2, w.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
				wolves++;
			}
		}
		Vec3 head = p.getEyePosition();
		QuirkFx.ring(l, p.position().add(0, 0.1, 0)).style(QuirkFx.RING_SOFT).colours(0xFFFFFF, 0xD8E4FF).size(5f).thickness(0.4f).life(20).ground().spawn();
		for (int i = 0; i < 3; i++) {
			QuirkFx.ring(l, head.add(0, 0.6 + i * 0.8, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, SONIC).size(0.6f + i * 0.5f).thickness(0.15f).life(10 + i * 3)
					.spawn();
		}
		l.sendParticles(ParticleTypes.END_ROD, head.x, head.y + 0.4, head.z, 24, 0.3, 1.2, 0.3, 0.05);
		OmnitrixSfx.playOn(p, SND_MOON, 1.2f, 1f, SoundSource.PLAYERS);
		return wolves;
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+V Scent Mark

	/** Shift+V: the creature he looks at (32) carries his mark for 30 s -- outlined for him at any range, and a scent trail behind it. */
	public boolean scentMark(ServerPlayer p) {
		LivingEntity t = lookTarget(p, SCENT_RANGE);
		if (t == null) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.benwolf.no_target")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, SCENT)) {
			return false;
		}
		anim(p, ANIM_SCENT);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(SCENT_ID), (long) t.getId() + 1);
		n.timers.put(key(SCENT_UNTIL), p.level().getGameTime() + SCENT_TICKS);
		Omnitrix.save(p, n);
		level(p).sendParticles(p, SCENT_DUST, true, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
		OmnitrixSfx.play(level(p), p.position(), SND_SNIFF, 1f, 1.1f);
		return true;
	}

	/** Whether {@code e} carries {@code viewer}'s Scent Mark right now (client-safe). */
	public static boolean scented(Player viewer, @Nullable Entity e) {
		if (e == null || !isBenwolf(viewer)) {
			return false;
		}
		OmnitrixState s = Omnitrix.state(viewer);
		return s.readyAt(ID + "." + SCENT_ID) == e.getId() + 1L && s.readyAt(ID + "." + SCENT_UNTIL) > viewer.level().getGameTime();
	}

	private void scentTick(ServerPlayer p, ServerLevel l, long now) {
		if (now >= timer(p, SCENT_UNTIL)) {
			OmnitrixState n = Omnitrix.state(p).copy();
			n.timers.remove(key(SCENT_ID));
			n.timers.remove(key(SCENT_UNTIL));
			Omnitrix.save(p, n);
			return;
		}
		if (now % 6 != 0) {
			return;
		}
		Entity e = l.getEntity((int) (timer(p, SCENT_ID) - 1));
		if (e instanceof LivingEntity t && t.isAlive()) {
			// the trail: a few motes where it walks, for his nose (eyes) alone
			l.sendParticles(p, SCENT_DUST, true, t.getX(), t.getY() + 0.15, t.getZ(), 3, 0.25, 0.05, 0.25, 0.0);
		}
	}

	// ---------------------------------------------------------------- v0.17.4: Shift+C Maul

	/** Shift+C: he seizes the creature ahead (3.5) and slams it down: 16, pinned 1.5 s (a boss takes the 16 but is not pinned). */
	public boolean maul(ServerPlayer p) {
		LivingEntity t = lookTarget(p, MAUL_RANGE);
		if (t == null) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.omnitrix.benwolf.no_target")
					.withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		if (!use(p, MAUL)) {
			return false;
		}
		anim(p, ANIM_MAUL);
		t.invulnerableTime = 0;
		hurt(p, t, MAUL_DAMAGE, false);
		if (!AlienStatus.isBoss(t)) {
			t.setDeltaMovement(0, -0.8, 0);
			t.hurtMarked = true;
			AlienStatus.root(p, t, MAUL_PIN, 10);
		}
		ServerLevel l = level(p);
		Vec3 at = t.position();
		net.minecraft.world.level.block.state.BlockState under = l.getBlockState(t.blockPosition().below());
		if (!under.isAir()) {
			l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 24, 0.4, 0.1, 0.4, 0.15);
		}
		l.sendParticles(ParticleTypes.CRIT, at.x, at.y + t.getBbHeight() * 0.5, at.z, 14, 0.4, 0.3, 0.4, 0.25);
		QuirkFx.ring(l, at.add(0, 0.1, 0)).style(QuirkFx.RING_SHOCK).colours(0xFFFFFF, 0x9AA4B8).size(2.0f).thickness(0.2f).life(8).ground().spawn();
		OmnitrixSfx.play(l, at, SND_LAND, 1f, 0.85f);
		OmnitrixSfx.play(l, at, SND_CLAW, 0.8f, 0.8f);
		return true;
	}

	/** Whether {@code viewer}'s Tracking Senses pick out {@code e} (client-safe). */
	public static boolean senses(Player viewer, @Nullable Entity e) {
		return e instanceof LivingEntity && e != viewer && isBenwolf(viewer) && (Omnitrix.state(viewer).kitFlags & F_SENSES) != 0
				&& e.distanceToSqr(viewer) < SENSE_RANGE * SENSE_RANGE && !e.isSpectator();
	}
}
