package com.projecthero.mod.blackpanther;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.blackpanther.entity.VibraniumDaggerEntity;
import com.projecthero.mod.blackpanther.network.PantherSensesPayload;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Black Panther's suited moves. See {@link BlackPantherAbilityManager} for the keys.
 *
 * <p>v0.17.7 suited kit (user-approved): plain keys are free and fill a little Kinetic when they land on a living target; the
 * Shift keys spend Kinetic for a charged violet version. Short on Kinetic, a Shift move does nothing, spends nothing, plays a
 * refusal sound and flashes the HUD's Kinetic bar ({@link #denyKinetic}). Every number is in {@link BlackPantherConfig}.
 * <pre>
 *   R  Vibranium Combo (+Kinetic per hit)      Shift+R  Kinetic Combo (cost; forward shockwave on the finisher)
 *   G  Pounce (+Kinetic on hit)                Shift+G  Kinetic Pounce (cost; ground burst on landing)
 *   X  Panther Leap                            Shift+X  Kinetic Dash (cost; i-frames, follows the aim, pitch too)
 *   Z  Vibranium Dagger (+Kinetic on hit)      Shift+Z  Kinetic Overload (the whole bar, only when full)
 *   V  Stalk                                   Shift+V  Kinetic Shroud (cost; damage cut + thorns)
 *   C  Absorb Stance (hold, fills)             Shift+C  Kinetic Return (spends)
 * </pre>
 * The timed moves are small per-player runs stepped from {@link #tick}; every map here is dropped by {@link #clear} and
 * {@link #clearSessionState} (UUID keys and plain values only: no worlds or entities are held).
 */
public final class BlackPantherAbilities {
	public static final String VIBRANIUM_COMBO = "vibranium_combo";
	public static final String KINETIC_COMBO = "kinetic_combo";
	public static final String POUNCE = "pounce";
	public static final String KINETIC_POUNCE = "kinetic_pounce";
	public static final String LEAP = "panther_leap";
	public static final String DASH = "kinetic_dash";
	public static final String DAGGER = "vibranium_dagger";
	public static final String OVERLOAD = "kinetic_overload";
	public static final String STALK = "stalk";
	public static final String SHROUD = "kinetic_shroud";
	public static final String ABSORB = "absorb_stance";
	public static final String RETURN = "kinetic_return";
	/** Synced timers kept in {@code abilityReadyAt} (no codec change): the Shroud is up until, the HUD's "not enough" flash until. */
	public static final String SHROUD_ACTIVE = "kinetic_shroud_active";
	public static final String KINETIC_DENIED = "kinetic_denied";

	/** A pounce in flight or pinning: the target, when it started, when it landed (0 = still flying), charged or not. */
	private record Pounce(int target, long start, long landedAt, boolean kinetic) {
	}

	/** A Kinetic Dash: the direction (pitch included), when it started, the creatures already struck. */
	private record Dash(Vec3 dir, long start, Set<Integer> struck) {
	}

	/** Vibranium / Kinetic Combo: game time of the first strike, the next strike to land (1 or 2), 1 if it is the charged one. */
	private static final Map<UUID, long[]> COMBO_RUNS = new ConcurrentHashMap<>();
	private static final Map<UUID, Pounce> POUNCES = new ConcurrentHashMap<>();
	/** Panther Leap: until when he can still catch a ledge. */
	private static final Map<UUID, Long> LEAPS = new ConcurrentHashMap<>();
	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	/** Kinetic Dash: no damage until this game time. */
	private static final Map<UUID, Long> DASH_IFRAMES = new ConcurrentHashMap<>();
	/** Absorb Stance: when it was raised. */
	private static final Map<UUID, Long> STANCES = new ConcurrentHashMap<>();
	/** Hunter's Tracking (unsuited, Shift+V): per player, the feet of each creature over the last few seconds. */
	private static final Map<UUID, Map<Integer, Deque<Vec3>>> TRACKS = new ConcurrentHashMap<>();

	private BlackPantherAbilities() {
	}

	static void clearSessionState() {
		COMBO_RUNS.clear();
		POUNCES.clear();
		LEAPS.clear();
		DASHES.clear();
		DASH_IFRAMES.clear();
		STANCES.clear();
		TRACKS.clear();
	}

	/** Ends every running move (the suit going, a death, a world change, leaving). */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		COMBO_RUNS.remove(id);
		POUNCES.remove(id);
		LEAPS.remove(id);
		DASHES.remove(id);
		DASH_IFRAMES.remove(id);
		STANCES.remove(id);
		TRACKS.remove(id);
		BlackPantherState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.BLACK_PANTHER_STATE, null);
		if (s != null && (s.absorbing || s.stalking || s.abilityReadyAt.containsKey(SHROUD_ACTIVE))) {
			BlackPantherState n = s.copy();
			n.absorbing = false;
			n.stalking = false;
			n.abilityReadyAt.remove(SHROUD_ACTIVE);
			BlackPanther.save(p, n);
			BlackPanther.reconcile(p);
		}
	}

	// ---------------------------------------------------------------- the gate

	private static boolean begin(ServerPlayer p, String id) {
		if (!BlackPanther.canAct(p)) {
			return false;
		}
		int cd = BlackPanther.cooldownRemaining(p, id);
		if (cd > 0) {
			BlackPanther.say(p, "message.projecthero.black_panther.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.black_panther.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	/**
	 * A Shift move's price: spends {@code cost} and returns true, or -- too little stored -- spends nothing, plays the refusal
	 * sound and flashes the HUD's Kinetic bar ({@link #denyKinetic}).
	 */
	private static boolean pay(ServerPlayer p, float cost) {
		if (BlackPanther.spend(p, cost)) {
			return true;
		}
		denyKinetic(p, cost);
		return false;
	}

	/** Not enough Kinetic: nothing fires, nothing is spent; a dull refusal (to him only) and the Meter bar flashes red / white. */
	static void denyKinetic(ServerPlayer p, float needed) {
		BlackPantherState n = BlackPanther.state(p).copy();
		n.abilityReadyAt.put(KINETIC_DENIED, p.level().getGameTime() + BlackPantherConfig.KINETIC_DENIED_FLASH_TICKS);
		BlackPanther.save(p, n);
		p.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.9f, 0.6f);
		p.playNotifySound(SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 0.5f);
		BlackPanther.say(p, "message.projecthero.black_panther.low_kinetic", ChatFormatting.DARK_PURPLE, (int) Math.ceil(needed),
				(int) Math.floor(BlackPanther.kinetic(p)));
	}

	/** The HUD's Kinetic bar is flashing (a Shift move was refused for want of Kinetic). Client-safe. */
	public static boolean kineticDenied(Player p) {
		return BlackPanther.cooldownRemaining(p, KINETIC_DENIED) > 0;
	}

	private static void cooldown(ServerPlayer p, String id, int ticks) {
		BlackPantherState n = BlackPanther.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		BlackPanther.save(p, n);
	}

	private static void anim(ServerPlayer p, int id) {
		BlackPantherState n = BlackPanther.state(p).copy();
		n.animId = id;
		n.animStart = p.level().getGameTime();
		BlackPanther.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** The ground shockwave ring (drawn by {@code BlackPantherEffectsRenderer}); Overload adds its dome on top. */
	private static void shockwave(ServerPlayer p, Vec3 at, float radius) {
		BlackPantherState n = BlackPanther.state(p).copy();
		n.pulseStart = p.level().getGameTime();
		n.pulseRadius = radius;
		n.pulsePos = new ArrayList<>(List.of(at.x, at.y, at.z));
		BlackPanther.save(p, n);
	}

	/** A hit by one of the new moves: the boss cap, no tier cap (v0.17.7: the user sets the numbers himself). */
	private static boolean hit(ServerPlayer p, LivingEntity e, Vec3 origin, float damage, double knockback, double lift) {
		return BlackPantherCombat.strike(p, e, origin, damage, knockback, lift, false);
	}

	/** A plain move landed on a living target: a little Kinetic into the bar. */
	private static void gain(ServerPlayer p, float amount) {
		if (amount > 0f) {
			BlackPanther.store(p, amount, false);
		}
	}

	// ---------------------------------------------------------------- R: Vibranium Combo / Shift+R: Kinetic Combo

	public static void vibraniumCombo(ServerPlayer p) {
		if (COMBO_RUNS.containsKey(p.getUUID()) || !begin(p, VIBRANIUM_COMBO)) {
			return;
		}
		cooldown(p, VIBRANIUM_COMBO, BlackPantherConfig.VIBRANIUM_COMBO_COOLDOWN);
		anim(p, BlackPantherState.ANIM_CLAW);
		long now = p.level().getGameTime();
		// strike 1 lands on the press: the server first steps the run a tick later
		strike(p, 0, false);
		COMBO_RUNS.put(p.getUUID(), new long[] { now, 1L, 0L });
	}

	public static void kineticCombo(ServerPlayer p) {
		if (COMBO_RUNS.containsKey(p.getUUID()) || !begin(p, KINETIC_COMBO) || !pay(p, BlackPantherConfig.KINETIC_COMBO_COST)) {
			return;
		}
		cooldown(p, KINETIC_COMBO, BlackPantherConfig.KINETIC_COMBO_COOLDOWN);
		anim(p, BlackPantherState.ANIM_KINETIC_COMBO);
		long now = p.level().getGameTime();
		strike(p, 0, true);
		COMBO_RUNS.put(p.getUUID(), new long[] { now, 1L, 1L });
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);
	}

	private static void tickCombo(ServerPlayer p, long[] run, long now) {
		boolean kinetic = run[2] == 1L;
		int step = kinetic ? BlackPantherConfig.KINETIC_COMBO_STEP : BlackPantherConfig.VIBRANIUM_COMBO_STEP;
		int next = (int) run[1];
		if (now - run[0] >= (long) next * step) {
			strike(p, next, kinetic);
			if (next >= 2) {
				COMBO_RUNS.remove(p.getUUID());
			} else {
				run[1] = next + 1;
			}
		}
	}

	/** Damage of strike {@code n} (0..2) of the Vibranium Combo (plain) or the Kinetic Combo (charged). */
	public static float comboDamage(int n, boolean kinetic) {
		if (kinetic) {
			return BlackPantherConfig.KINETIC_COMBO_DAMAGE;
		}
		return n == 0 ? BlackPantherConfig.VIBRANIUM_COMBO_HIT_1 : n == 1 ? BlackPantherConfig.VIBRANIUM_COMBO_HIT_2 : BlackPantherConfig.VIBRANIUM_COMBO_HIT_3;
	}

	/** One claw strike (0..2). Plain: each one that lands adds Kinetic, the third leaves a violet slash mark. Charged: the finisher's shockwave. */
	private static void strike(ServerPlayer p, int n, boolean kinetic) {
		boolean last = n == 2;
		float damage = comboDamage(n, kinetic);
		double reach = kinetic ? BlackPantherConfig.KINETIC_COMBO_REACH : BlackPantherConfig.VIBRANIUM_COMBO_REACH;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 c = eye.add(look.scale(reach * 0.6));
		Set<Integer> struck = new HashSet<>();
		for (LivingEntity e : BlackPantherCombat.within(p, c, reach * 0.7, e -> BlackPantherCombat.isTarget(p, e))) {
			if (hit(p, e, p.position(), damage, last ? 0.5 : 0.1, 0.0)) {
				struck.add(e.getId());
				if (!kinetic) {
					gain(p, BlackPantherConfig.VIBRANIUM_COMBO_KINETIC_PER_HIT);
					if (last) {
						slashMark(p, e);
					}
				}
			}
		}
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y - 0.3, c.z, 1, 0, 0, 0, 0);
		if (kinetic) {
			level(p).sendParticles(BlackPanther.PURPLE, c.x, c.y - 0.2, c.z, 10, 0.5, 0.3, 0.5, 0.0);
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.25f + n * 0.12f);
			sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 1.2f + n * 0.15f);
			if (last) {
				coneWave(p, struck);
			}
		} else {
			sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.5f + n * 0.15f);
		}
	}

	/** Three parallel violet claw marks raked diagonally across the target, hanging in the air a moment. */
	private static void slashMark(ServerPlayer p, LivingEntity e) {
		Vec3 f = flatLook(p);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0).subtract(f.scale(e.getBbWidth() * 0.55));
		double h = Math.max(0.6, e.getBbHeight() * 0.45);
		for (int i = -1; i <= 1; i++) {
			Vec3 off = side.scale(i * 0.16).add(0, i * 0.06, 0);
			Vec3 a = c.add(side.scale(0.55)).add(0, h * 0.5, 0).add(off);
			Vec3 b = c.subtract(side.scale(0.55)).subtract(0, h * 0.5, 0).add(off);
			AbilityHelpers.line(level(p), a, b, BlackPanther.PURPLE, 12.0);
		}
		level(p).sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 4, 0.15, 0.25, 0.15, 0.01);
		level(p).playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.5f);
	}

	/** The Kinetic Combo's finisher: a short violet shockwave in a cone ahead of him; whoever the slash itself missed takes it. */
	private static void coneWave(ServerPlayer p, Set<Integer> alreadyStruck) {
		Vec3 o = p.position().add(0, 0.9, 0);
		Vec3 f = flatLook(p);
		double range = BlackPantherConfig.KINETIC_COMBO_WAVE_RANGE;
		for (LivingEntity e : BlackPantherCombat.within(p, o, range, e -> BlackPantherCombat.isTarget(p, e))) {
			if (alreadyStruck.contains(e.getId()) || !inCone(o, f, e, BlackPantherConfig.KINETIC_COMBO_WAVE_ARC)) {
				continue;
			}
			hit(p, e, o, BlackPantherConfig.KINETIC_COMBO_WAVE_DAMAGE, 0.9, 0.2);
		}
		for (int i = 1; i <= 8; i++) {
			double d = range * i / 8.0;
			double w = d * 0.6;
			Vec3 at = o.add(f.scale(d));
			level(p).sendParticles(BlackPanther.PURPLE_BIG, at.x, at.y - 0.3, at.z, 3 + i, w * 0.5, 0.2, w * 0.5, 0.0);
		}
		level(p).sendParticles(ParticleTypes.SONIC_BOOM, o.x + f.x * 1.6, o.y, o.z + f.z * 1.6, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
	}

	private static boolean inCone(Vec3 origin, Vec3 flatDir, LivingEntity e, double arc) {
		Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
		Vec3 flat = new Vec3(to.x, 0, to.z);
		return flat.lengthSqr() < 0.25 || flat.normalize().dot(flatDir) >= arc;
	}

	// ---------------------------------------------------------------- G: Pounce / Shift+G: Kinetic Pounce

	public static void pounce(ServerPlayer p) {
		startPounce(p, false);
	}

	public static void kineticPounce(ServerPlayer p) {
		startPounce(p, true);
	}

	private static void startPounce(ServerPlayer p, boolean kinetic) {
		String id = kinetic ? KINETIC_POUNCE : POUNCE;
		if (POUNCES.containsKey(p.getUUID()) || !begin(p, id)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(p, BlackPantherConfig.POUNCE_RANGE);
		if (target == null || !BlackPantherCombat.isTarget(p, target)) {
			BlackPanther.say(p, "message.projecthero.black_panther.pounce_nothing", ChatFormatting.GRAY);
			return; // nothing aimed at: nothing spent
		}
		if (kinetic && !pay(p, BlackPantherConfig.KINETIC_POUNCE_COST)) {
			return;
		}
		cooldown(p, id, kinetic ? BlackPantherConfig.KINETIC_POUNCE_COOLDOWN : BlackPantherConfig.POUNCE_COOLDOWN);
		anim(p, BlackPantherState.ANIM_POUNCE);
		POUNCES.put(p.getUUID(), new Pounce(target.getId(), p.level().getGameTime(), 0L, kinetic));
		sound(p, SoundEvents.PHANTOM_SWOOP, 1.0f, kinetic ? 1.0f : 1.3f);
		if (kinetic) {
			sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.1f);
		}
	}

	private static void tickPounce(ServerPlayer p, Pounce run, long now) {
		Entity e = p.level().getEntity(run.target());
		if (!(e instanceof LivingEntity target) || !target.isAlive()) {
			POUNCES.remove(p.getUUID());
			return;
		}
		if (run.landedAt() == 0L) {
			p.resetFallDistance();
			if (run.kinetic()) {
				level(p).sendParticles(BlackPanther.PURPLE, p.getX(), p.getY() + 0.8, p.getZ(), 3, 0.2, 0.3, 0.2, 0.0);
			}
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.position());
			if (to.length() <= Math.max(2.0, target.getBbWidth() + 1.2)) {
				AbilityHelpers.launchSelf(p, Vec3.ZERO);
				land(p, target, run.kinetic());
				POUNCES.put(p.getUUID(), new Pounce(run.target(), run.start(), now, run.kinetic()));
			} else if (now - run.start() > BlackPantherConfig.POUNCE_MAX_FLIGHT_TICKS) {
				POUNCES.remove(p.getUUID());
			} else {
				AbilityHelpers.launchSelf(p, to.normalize().scale(BlackPantherConfig.POUNCE_SPEED));
			}
			return;
		}
		if (now - run.landedAt() >= BlackPantherConfig.POUNCE_PIN_TICKS) {
			POUNCES.remove(p.getUUID()); // the pin wears off
		}
	}

	/** The landing: plain, the claws (+Kinetic on a hit) and a pin; charged, a violet ground burst round him instead, and the pin. */
	private static void land(ServerPlayer p, LivingEntity target, boolean kinetic) {
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, BlackPantherConfig.POUNCE_PIN_TICKS, 6);
		if (!kinetic) {
			anim(p, BlackPantherState.ANIM_POUNCE_STRIKE);
			if (hit(p, target, p.position(), BlackPantherConfig.POUNCE_LAND_DAMAGE, 0.0, 0.0)) {
				gain(p, BlackPantherConfig.POUNCE_KINETIC_GAIN);
			}
			sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
			return;
		}
		anim(p, BlackPantherState.ANIM_KINETIC_BURST);
		Vec3 c = new Vec3(target.getX(), Math.min(p.getY(), target.getY()) + 0.3, target.getZ());
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.KINETIC_POUNCE_RADIUS, e -> BlackPantherCombat.isTarget(p, e))) {
			hit(p, e, c, BlackPantherConfig.KINETIC_POUNCE_DAMAGE, e == target ? 0.3 : BlackPantherConfig.KINETIC_POUNCE_KNOCKBACK,
					e == target ? 0.0 : 0.4);
		}
		shockwave(p, c, (float) BlackPantherConfig.KINETIC_POUNCE_RADIUS);
		level(p).sendParticles(BlackPanther.PURPLE_BIG, c.x, c.y, c.z, 30, 1.2, 0.2, 1.2, 0.0);
		level(p).sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
		sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 0.7f);
	}

	// ---------------------------------------------------------------- X: Panther Leap / Shift+X: Kinetic Dash

	public static void leap(ServerPlayer p) {
		if (!begin(p, LEAP)) {
			return;
		}
		cooldown(p, LEAP, BlackPantherConfig.LEAP_COOLDOWN);
		anim(p, BlackPantherState.ANIM_LEAP);
		Vec3 f = flatLook(p).scale(BlackPantherConfig.LEAP_FORWARD);
		AbilityHelpers.launchSelf(p, new Vec3(f.x, BlackPantherConfig.LEAP_SPEED, f.z));
		LEAPS.put(p.getUUID(), p.level().getGameTime() + BlackPantherConfig.LEDGE_TICKS);
		level(p).sendParticles(BlackPanther.PURPLE, p.getX(), p.getY(), p.getZ(), 14, 0.3, 0.1, 0.3, 0.0);
		sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 1.6f);
	}

	/** Falling past a wall with open air on top of it: he catches the ledge and pulls himself up. */
	private static void tickLeap(ServerPlayer p, long until, long now) {
		if (now > until || p.onGround()) {
			LEAPS.remove(p.getUUID());
			return;
		}
		if (p.getDeltaMovement().y > 0.1) {
			return;
		}
		Vec3 f = flatLook(p);
		BlockPos wall = BlockPos.containing(p.getX() + f.x * 0.9, p.getEyeY(), p.getZ() + f.z * 0.9);
		ServerLevel level = level(p);
		boolean solid = !level.getBlockState(wall).getCollisionShape(level, wall).isEmpty();
		boolean clear = level.getBlockState(wall.above()).getCollisionShape(level, wall.above()).isEmpty()
				&& level.getBlockState(wall.above(2)).getCollisionShape(level, wall.above(2)).isEmpty();
		if (solid && clear) {
			AbilityHelpers.launchSelf(p, new Vec3(f.x * 0.35, 0.72, f.z * 0.35));
			sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.9f, 1.2f);
			LEAPS.remove(p.getUUID());
		}
	}

	/**
	 * Shift+X: a violet afterimage dash straight where he aims (pitch included), {@link BlackPantherConfig#DASH_DISTANCE} blocks
	 * over {@link BlackPantherConfig#DASH_TICKS} ticks. He is moved by the server in small collision-checked steps, so he stops
	 * flush at a wall and never ends inside a block; no damage reaches him while it runs (plus a couple of ticks); every creature he
	 * passes through takes {@link BlackPantherConfig#DASH_DAMAGE}; no fall damage from it. The first step lands on the press.
	 */
	public static void kineticDash(ServerPlayer p) {
		if (DASHES.containsKey(p.getUUID()) || !begin(p, DASH) || !pay(p, BlackPantherConfig.DASH_COST)) {
			return;
		}
		cooldown(p, DASH, BlackPantherConfig.DASH_COOLDOWN);
		anim(p, BlackPantherState.ANIM_DASH);
		LEAPS.remove(p.getUUID());
		long now = p.level().getGameTime();
		Dash d = new Dash(p.getLookAngle().normalize(), now, new HashSet<>());
		DASHES.put(p.getUUID(), d);
		DASH_IFRAMES.put(p.getUUID(), now + BlackPantherConfig.DASH_TICKS + BlackPantherConfig.DASH_IFRAME_EXTRA_TICKS);
		sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 2.0f);
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
		stepDash(p, d, now);
	}

	/** He is untouchable: a Kinetic Dash is running (or just ended). */
	public static boolean dashInvulnerable(ServerPlayer p) {
		Long until = DASH_IFRAMES.get(p.getUUID());
		return until != null && p.level().getGameTime() <= until;
	}

	private static void stepDash(ServerPlayer p, Dash d, long now) {
		double per = BlackPantherConfig.DASH_DISTANCE / BlackPantherConfig.DASH_TICKS;
		ServerLevel level = level(p);
		Vec3 start = p.position();
		Vec3 pos = start;
		AABB box = p.getBoundingBox();
		boolean blocked = false;
		int sub = Math.max(1, (int) Math.ceil(per / 0.25));
		Vec3 inc = d.dir().scale(per / sub);
		for (int i = 0; i < sub; i++) {
			Vec3 moved = pos.add(inc).subtract(start);
			if (!level.noCollision(p, box.move(moved))) {
				blocked = true;
				break;
			}
			pos = pos.add(inc);
		}
		if (pos.distanceToSqr(start) > 1.0e-6) {
			p.teleportTo(pos.x, pos.y, pos.z);
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		p.resetFallDistance();
		// the afterimage trail and everyone passed through
		AbilityHelpers.line(level, start.add(0, 1.0, 0), pos.add(0, 1.0, 0), BlackPanther.PURPLE, 4.0);
		AABB swept = box.move(0, 0, 0).expandTowards(pos.subtract(start)).inflate(BlackPantherConfig.DASH_HIT_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, swept, e -> e != p && BlackPantherCombat.isTarget(p, e))) {
			if (d.struck().add(e.getId())) {
				hit(p, e, start, BlackPantherConfig.DASH_DAMAGE, 0.4, 0.15);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.7f);
			}
		}
		if (blocked || now - d.start() >= BlackPantherConfig.DASH_TICKS - 1) {
			DASHES.remove(p.getUUID());
			if (blocked) {
				level.sendParticles(BlackPanther.PURPLE_BIG, pos.x, pos.y + 1.0, pos.z, 10, 0.3, 0.4, 0.3, 0.0);
				sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 0.9f, 0.8f);
			}
		}
	}

	// ---------------------------------------------------------------- Z: Vibranium Dagger / Shift+Z: Kinetic Overload

	public static void dagger(ServerPlayer p) {
		if (!begin(p, DAGGER)) {
			return;
		}
		cooldown(p, DAGGER, BlackPantherConfig.DAGGER_COOLDOWN);
		anim(p, BlackPantherState.ANIM_THROW);
		VibraniumDaggerEntity.shoot(p);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.9f, 1.5f);
	}

	/**
	 * Shift+Z, the ultimate: only on a FULL bar, which it empties. He leaps a little and slams the ground: a 10-block blast,
	 * {@link BlackPantherConfig#OVERLOAD_DAMAGE} at the centre falling to {@link BlackPantherConfig#OVERLOAD_EDGE_DAMAGE} at the
	 * edge, under a big violet dome. Short of full: refused like any Shift move (nothing spent, the bar flashes).
	 */
	public static void overload(ServerPlayer p) {
		if (!begin(p, OVERLOAD)) {
			return;
		}
		if (BlackPanther.kinetic(p) < BlackPantherConfig.KINETIC_MAX - 0.01f) {
			denyKinetic(p, BlackPantherConfig.KINETIC_MAX);
			BlackPanther.say(p, "message.projecthero.black_panther.overload_full", ChatFormatting.DARK_PURPLE);
			return;
		}
		BlackPanther.setKinetic(p, 0f);
		cooldown(p, OVERLOAD, BlackPantherConfig.OVERLOAD_COOLDOWN);
		anim(p, BlackPantherState.ANIM_OVERLOAD);
		Vec3 c = p.position().add(0, 0.2, 0);
		shockwave(p, c, (float) BlackPantherConfig.OVERLOAD_RADIUS);
		for (LivingEntity e : BlackPantherCombat.within(p, c, BlackPantherConfig.OVERLOAD_RADIUS, e -> BlackPantherCombat.isTarget(p, e))) {
			hit(p, e, c, overloadDamage(Math.sqrt(e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(c))), 2.2, 0.7);
		}
		level(p).sendParticles(BlackPanther.PURPLE_BIG, c.x, c.y + 0.5, c.z, 120, 3.0, 1.2, 3.0, 0.0);
		level(p).sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level(p).sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1.0, c.z, 80, 4.0, 2.0, 4.0, 0.2);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 0.7f);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
		sound(p, SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.6f);
	}

	/** Kinetic Overload at {@code distance} from the slam: full at the centre, falling straight to the edge value at the radius. */
	public static float overloadDamage(double distance) {
		double t = Math.min(1.0, Math.max(0.0, distance / BlackPantherConfig.OVERLOAD_RADIUS));
		return (float) (BlackPantherConfig.OVERLOAD_DAMAGE + (BlackPantherConfig.OVERLOAD_EDGE_DAMAGE - BlackPantherConfig.OVERLOAD_DAMAGE) * t);
	}

	// ---------------------------------------------------------------- V: Stalk / Shift+V: Kinetic Shroud

	public static void toggleStalk(ServerPlayer p) {
		if (BlackPanther.stalking(p)) {
			endStalk(p);
			return;
		}
		if (!begin(p, STALK)) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.stalking = true;
		BlackPanther.save(p, n);
		sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.3f, 1.7f);
	}

	public static void endStalk(ServerPlayer p) {
		BlackPantherState s = BlackPanther.state(p);
		if (!s.stalking) {
			return;
		}
		BlackPantherState n = s.copy();
		n.stalking = false;
		n.abilityReadyAt.put(STALK, p.level().getGameTime() + BlackPantherConfig.STALK_COOLDOWN);
		BlackPanther.save(p, n);
	}

	/** Sneaking, out of a mob's short sight: it loses him. Bosses keep their threat table. */
	private static void tickStalk(ServerPlayer p) {
		if (!p.isShiftKeyDown()) {
			return;
		}
		double seen = BlackPantherConfig.STALK_SEEN_WITHIN;
		for (LivingEntity e : BlackPantherCombat.within(p, p.position(), 32.0, e -> e instanceof Mob m && m.getTarget() == p)) {
			if (!BlackPantherCombat.isBoss(e) && e.distanceTo(p) > seen) {
				((Mob) e).setTarget(null);
			}
		}
	}

	/** Shift+V: for a few seconds the suit takes less from every hit and throws a little back at whoever lands one. */
	public static void shroud(ServerPlayer p) {
		if (!begin(p, SHROUD) || !pay(p, BlackPantherConfig.SHROUD_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		BlackPantherState n = BlackPanther.state(p).copy();
		n.abilityReadyAt.put(SHROUD, now + BlackPantherConfig.SHROUD_COOLDOWN);
		n.abilityReadyAt.put(SHROUD_ACTIVE, now + BlackPantherConfig.SHROUD_TICKS);
		n.animId = BlackPantherState.ANIM_SHROUD;
		n.animStart = now;
		BlackPanther.save(p, n);
		level(p).sendParticles(BlackPanther.PURPLE_BIG, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.8, 0.5, 0.0);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.8f);
	}

	/** The Kinetic Shroud is up. Client-safe (a synced timer). */
	public static boolean shrouded(Player p) {
		return BlackPanther.suited(p) && BlackPanther.cooldownRemaining(p, SHROUD_ACTIVE) > 0;
	}

	// ---------------------------------------------------------------- Hunter's Tracking's prints (unsuited Shift+V, BlackPantherUnsuited)

	private static void sendTracking(ServerPlayer p) {
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : BlackPantherCombat.within(p, p.position(), BlackPantherConfig.TRACKING_RANGE, e -> e instanceof Mob)) {
			ids.add(e.getId());
		}
		// a fresh list twice a second, so a creature walking into range shows too
		ServerPlayNetworking.send(p, new PantherSensesPayload(ids.size() > 512 ? ids.subList(0, 512) : ids, 15));
	}

	private static void tickTracking(ServerPlayer p, long now) {
		if (now % 10 != 0) {
			return;
		}
		sendTracking(p);
		Map<Integer, Deque<Vec3>> tracks = TRACKS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		for (LivingEntity e : BlackPantherCombat.within(p, p.position(), BlackPantherConfig.TRACKING_RANGE, e -> e instanceof Mob)) {
			Deque<Vec3> feet = tracks.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
			if (feet.isEmpty() || feet.peekLast().distanceToSqr(e.position()) > 0.25) {
				feet.addLast(e.position());
				if (feet.size() > BlackPantherConfig.FOOTPRINTS) {
					feet.pollFirst();
				}
			}
			// only he sees the prints
			for (Vec3 f : feet) {
				level(p).sendParticles(p, BlackPanther.PURPLE, true, f.x, f.y + 0.1, f.z, 2, 0.1, 0.0, 0.1, 0.0);
			}
		}
	}

	// ---------------------------------------------------------------- C: the suit takes it

	public static void raiseStance(ServerPlayer p) {
		if (BlackPanther.absorbing(p) || !begin(p, ABSORB)) {
			return;
		}
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = true;
		n.absorbed = 0f;
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
		STANCES.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 1.5f);
	}

	public static void lowerStance(ServerPlayer p) {
		if (!BlackPanther.absorbing(p)) {
			return;
		}
		STANCES.remove(p.getUUID());
		BlackPantherState n = BlackPanther.state(p).copy();
		n.absorbing = false;
		n.abilityReadyAt.put(ABSORB, p.level().getGameTime() + BlackPantherConfig.ABSORB_COOLDOWN);
		BlackPanther.save(p, n);
		BlackPanther.reconcile(p);
	}

	/** The stance has taken all it can. */
	static void breakStance(ServerPlayer p) {
		lowerStance(p);
		sound(p, SoundEvents.SHIELD_BREAK, 0.9f, 1.2f);
	}

	public static void kineticReturn(ServerPlayer p) {
		if (!begin(p, RETURN)) {
			return;
		}
		BlackPantherDamage.LastShot shot = BlackPantherDamage.lastShot(p);
		long now = p.level().getGameTime();
		Entity shooter = shot == null || now - shot.at() > BlackPantherConfig.RETURN_WINDOW ? null : level(p).getEntity(shot.shooter());
		if (!(shooter instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(p) > BlackPantherConfig.RETURN_RANGE
				|| !BlackPantherCombat.isTarget(p, target)) {
			BlackPanther.say(p, "message.projecthero.black_panther.return_nothing", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, RETURN, BlackPantherConfig.RETURN_COOLDOWN);
		anim(p, BlackPantherState.ANIM_RETURN);
		// the shot comes back harder, and the stored bar rides along with it
		float spent = Math.min(BlackPantherConfig.RETURN_MAX_SPEND, BlackPanther.kinetic(p));
		if (spent > 0f) {
			BlackPanther.setKinetic(p, BlackPanther.kinetic(p) - spent);
		}
		float damage = returnDamage(shot.damage(), spent);
		Vec3 from = p.getEyePosition().add(0, -0.3, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
		AbilityHelpers.line(level(p), from, to, spent > 0f ? BlackPanther.PURPLE_BIG : BlackPanther.PURPLE, 3.0);
		BlackPantherCombat.strike(p, target, from, damage, 0.6 + spent / 80.0, 0.0);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.2f);
	}

	/** Kinetic Return: max(8, the shot x1.25), plus 0.25 per Kinetic point spent (up to 40 spent); v0.17.7: never over 20. */
	public static float returnDamage(float shotDamage, float kineticSpent) {
		return Math.min(BlackPantherConfig.MAX_HIT, Math.max(BlackPantherConfig.RETURN_MIN_DAMAGE, shotDamage * BlackPantherConfig.RETURN_FACTOR)
				+ Math.min(BlackPantherConfig.RETURN_MAX_SPEND, Math.max(0f, kineticSpent)) * BlackPantherConfig.RETURN_PER_KINETIC);
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();
		long[] combo = COMBO_RUNS.get(id);
		if (combo != null) {
			tickCombo(p, combo, now);
		}
		Pounce run = POUNCES.get(id);
		if (run != null) {
			tickPounce(p, run, now);
		}
		Long leap = LEAPS.get(id);
		if (leap != null) {
			tickLeap(p, leap, now);
		}
		Dash dash = DASHES.get(id);
		if (dash != null && now > dash.start()) {
			stepDash(p, dash, now);
		}
		Long iframes = DASH_IFRAMES.get(id);
		if (iframes != null && now > iframes) {
			DASH_IFRAMES.remove(id);
		}
		Long stance = STANCES.get(id);
		if (stance != null && now - stance >= BlackPantherConfig.ABSORB_MAX_TICKS) {
			breakStance(p);
		}
		BlackPantherState s = BlackPanther.state(p);
		if (s.stalking && s.suited && now % 5 == 0) {
			tickStalk(p);
		}
		if (s.suited && now % 4 == 0 && shrouded(p)) {
			level(p).sendParticles(BlackPanther.PURPLE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.35, 0.6, 0.35, 0.0);
		}
		if (s.senses && !s.suited) { // Hunter's Tracking (unsuited Shift+V)
			tickTracking(p, now);
		} else if (TRACKS.containsKey(id)) {
			TRACKS.remove(id);
		}
	}
}
