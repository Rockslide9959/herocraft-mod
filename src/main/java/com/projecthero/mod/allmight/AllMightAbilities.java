package com.projecthero.mod.allmight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The seven All Might abilities (v0.12.33). Every one is server-validated by {@link #begin}: power, alive, not locked by
 * a wind-up or the transformation, cooldown ready, OFA available -- in that order -- and only then is OFA spent and the
 * cooldown started. Timings are driven by a small per-player task queue so each hit lands on its animation's impact
 * frame ({@code AllMightState#animStart} + the wind-up) and is dropped if the player dies, logs out or loses the power.
 * The gameplay itself goes through {@link AllMightShockwave}; nothing here queries entities directly.
 */
public final class AllMightAbilities {
	public static final String DETROIT = "detroit_smash";
	public static final String TEXAS = "texas_smash";
	public static final String CAROLINA = "carolina_smash";
	public static final String NEW_HAMPSHIRE = "new_hampshire_smash";
	public static final String PLUS_ULTRA = "plus_ultra";
	public static final String UNITED_STATES = "united_states_of_smash";
	public static final String LEAP = "all_might_leap";

	private record Task(long due, Runnable run) {
	}

	private enum Kind {
		DASH, AIR
	}

	/** A continuing movement attack (Carolina's dash, New Hampshire's flight). */
	private static final class Move {
		final Kind kind;
		final Vec3 dir;
		final long start;
		final long until;
		final AllMightShockwave.Wave wave;
		int step;

		Move(Kind kind, Vec3 dir, long start, long until, AllMightShockwave.Wave wave) {
			this.kind = kind;
			this.dir = dir;
			this.start = start;
			this.until = until;
			this.wave = wave;
		}
	}

	private static final Map<UUID, List<Task>> TASKS = new HashMap<>();
	private static final Map<UUID, Move> MOVES = new HashMap<>();
	/** Game time a New Hampshire landing burst last fired, so the generic landing impact does not double it. */
	private static final Map<UUID, Long> NEW_HAMPSHIRE_LANDED = new HashMap<>();
	/** Players holding Z for the United States of Smash: game time the charge began. */
	private static final Map<UUID, Long> CHARGING = new HashMap<>();

	private AllMightAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		MOVES.clear();
		NEW_HAMPSHIRE_LANDED.clear();
		CHARGING.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		MOVES.remove(id);
		NEW_HAMPSHIRE_LANDED.remove(id);
		CHARGING.remove(id);
	}

	/** True while a New Hampshire flight is under way (or just landed): it makes its own, bigger landing burst. */
	static boolean suppressLanding(ServerPlayer p) {
		if (MOVES.containsKey(p.getUUID())) {
			return true;
		}
		Long landed = NEW_HAMPSHIRE_LANDED.get(p.getUUID());
		return landed != null && p.level().getGameTime() - landed <= 3L;
	}

	// ---------------------------------------------------------------- plumbing

	private static void schedule(ServerPlayer p, int delayTicks, Runnable run) {
		TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Task(p.level().getGameTime() + Math.max(0, delayTicks), run));
	}

	/** True while the player can still act as All Might (a queued hit is dropped otherwise). */
	private static boolean alive(ServerPlayer p) {
		return p.isAlive() && !p.isRemoved() && AllMight.hasPower(p);
	}

	/**
	 * The common gate. Returns true (and has already spent the OFA, started the cooldown, locked the player for
	 * {@code lockTicks} and set the animation) only if every check passed.
	 */
	private static boolean begin(ServerPlayer p, String id, float cost, int cooldown, int lockTicks, int anim) {
		AllMightState s = AllMight.state(p);
		if (!s.hasPower || !s.fullPower || !p.isAlive() || p.isSpectator()) {
			return false; // the Base Form cannot use abilities
		}
		long now = p.level().getGameTime();
		if (now < s.busyUntil || now < s.transformUntil) {
			return false; // mid-wind-up / mid-transformation: silently ignored so a held key cannot stack abilities
		}
		Long ready = s.abilityReadyAt.get(id);
		if (ready != null && now < ready) {
			AllMight.say(p, "message.projecthero.all_might.cooldown", ChatFormatting.RED,
					Component.translatable("projecthero.all_might.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", (ready - now) / 20.0));
			return false;
		}
		if (s.ofa + 1.0e-3f < cost) {
			AllMight.say(p, "message.projecthero.all_might.low_ofa", ChatFormatting.RED, (int) Math.ceil(cost), (int) Math.floor(s.ofa));
			return false;
		}
		AllMightState n = s.copy();
		n.ofa = Math.max(0f, s.ofa - cost);
		n.abilityReadyAt.put(id, now + cooldown);
		n.busyUntil = now + Math.max(lockTicks, AllMightConfig.GLOBAL_LOCK_TICKS);
		n.animId = anim;
		n.animStart = now;
		AllMight.save(p, n);
		return true;
	}

	private static void grantNoFall(ServerPlayer p) {
		AllMightState s = AllMight.state(p);
		AllMightState n = s.copy();
		n.noFallUntil = p.level().getGameTime() + AllMightConfig.LAUNCH_NO_FALL_TICKS;
		AllMight.save(p, n);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
	}

	/** The upward launch speed that reaches roughly {@code height} blocks (vanilla gravity 0.08, drag 0.98). */
	static double verticalSpeedForHeight(double height) {
		double lo = 0.3;
		double hi = 5.0;
		for (int i = 0; i < 30; i++) {
			double mid = (lo + hi) * 0.5;
			double y = 0.0;
			double vy = mid;
			double max = 0.0;
			for (int t = 0; t < 200 && (vy > 0.0 || t == 0); t++) {
				y += vy;
				max = Math.max(max, y);
				vy = (vy - 0.08) * 0.98;
			}
			if (max < height) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) * 0.5;
	}

	// ---------------------------------------------------------------- R -- Detroit Smash

	public static void detroit(ServerPlayer p) {
		if (!begin(p, DETROIT, AllMightConfig.DETROIT_COST, AllMightConfig.DETROIT_COOLDOWN,
				AllMightConfig.DETROIT_WINDUP + 6, AllMightState.ANIM_DETROIT)) {
			return;
		}
		AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_THROW, 1.0f, 0.7f);
		schedule(p, AllMightConfig.DETROIT_WINDUP, () -> {
			if (!alive(p)) {
				return;
			}
			ServerLevel level = (ServerLevel) p.level();
			p.swing(InteractionHand.MAIN_HAND, true);
			Vec3 origin = p.position();
			Vec3 look = p.getLookAngle();
			Vec3 fist = origin.add(0, 1.3, 0).add(flatLook(p).scale(2.4));
			var wave = new AllMightShockwave.Wave(AllMightConfig.DETROIT_DAMAGE, AllMightConfig.DETROIT_KNOCKBACK, AllMightConfig.DETROIT_LIFT);
			int hits = AllMightShockwave.sweep(p, origin, look, 0.0, AllMightConfig.DETROIT_RANGE, AllMightConfig.DETROIT_WIDTH,
					AllMightConfig.DETROIT_HEIGHT, wave);
			AllMightShockwave.windLine(level, origin.add(0, 1.2, 0), look, AllMightConfig.DETROIT_RANGE, 1.4, 3);
			AllMightShockwave.burst(level, ParticleTypes.EXPLOSION, fist, 1, 0.1, 0.0);
			AllMightShockwave.burst(level, ParticleTypes.CLOUD, fist, 14, 0.5, 0.12);
			level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.4f, 0.6f);
			level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.5f);
			AllMightShockwave.breakBlocks(p, fist, AllMightConfig.DETROIT_BLOCK_RADIUS, AllMightConfig.DETROIT_BLOCK_MAX,
					AllMightConfig.DETROIT_BLOCK_HARDNESS);
			if (hits > 0) {
				AllMightShockwave.shake(level, fist, 0.25f, 6);
			}
		});
	}

	// ---------------------------------------------------------------- G -- Texas Smash

	public static void texas(ServerPlayer p) {
		if (!begin(p, TEXAS, AllMightConfig.TEXAS_COST, AllMightConfig.TEXAS_COOLDOWN,
				AllMightConfig.TEXAS_WINDUP + 8, AllMightState.ANIM_TEXAS)) {
			return;
		}
		AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_THROW, 1.2f, 0.5f);
		schedule(p, AllMightConfig.TEXAS_WINDUP, () -> {
			if (!alive(p)) {
				return;
			}
			ServerLevel level = (ServerLevel) p.level();
			p.swing(InteractionHand.MAIN_HAND, true);
			Vec3 origin = p.position();
			Vec3 look = p.getLookAngle();
			var wave = new AllMightShockwave.Wave(AllMightConfig.TEXAS_DAMAGE, AllMightConfig.TEXAS_KNOCKBACK, AllMightConfig.TEXAS_LIFT);
			level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.2f);
			level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.4f, 0.5f);
			int steps = (int) Math.ceil(AllMightConfig.TEXAS_RANGE / AllMightConfig.TEXAS_WAVE_SPEED);
			for (int i = 0; i < steps; i++) {
				final int step = i;
				schedule(p, i, () -> {
					if (!alive(p)) {
						return;
					}
					double from = step * AllMightConfig.TEXAS_WAVE_SPEED;
					double to = from + AllMightConfig.TEXAS_WAVE_SPEED + 0.5;
					AllMightShockwave.sweep(p, origin, look, from, to, AllMightConfig.TEXAS_WIDTH, AllMightConfig.TEXAS_HEIGHT, wave);
					Vec3 seg = origin.add(0, 1.1, 0).add(flatLook(p).scale(from + 1.0));
					AllMightShockwave.windLine(level, seg, look, AllMightConfig.TEXAS_WAVE_SPEED, AllMightConfig.TEXAS_WIDTH, 5);
					AllMightShockwave.burst(level, ParticleTypes.CLOUD, seg, 6, AllMightConfig.TEXAS_WIDTH * 0.35, 0.1);
					if (step % 2 == 0) {
						AllMightShockwave.burst(level, ParticleTypes.SWEEP_ATTACK, seg, 2, AllMightConfig.TEXAS_WIDTH * 0.3, 0.0);
					}
					if (step == 1 || step == steps - 1) {
						AllMightShockwave.breakBlocks(p, seg, AllMightConfig.TEXAS_BLOCK_RADIUS, AllMightConfig.TEXAS_BLOCK_MAX / 2,
								AllMightConfig.TEXAS_BLOCK_HARDNESS);
					}
					if (step == steps - 1) {
						AllMightShockwave.shake(level, seg, 0.4f, 8);
					}
				});
			}
		});
	}

	// ---------------------------------------------------------------- Z -- Carolina Smash

	public static void carolina(ServerPlayer p) {
		int dashTicks = (int) Math.ceil(AllMightConfig.CAROLINA_DISTANCE / AllMightConfig.CAROLINA_SPEED);
		if (!begin(p, CAROLINA, AllMightConfig.CAROLINA_COST, AllMightConfig.CAROLINA_COOLDOWN,
				AllMightConfig.CAROLINA_WINDUP + dashTicks + 4, AllMightState.ANIM_CAROLINA)) {
			return;
		}
		AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_THROW, 1.0f, 1.2f);
		schedule(p, AllMightConfig.CAROLINA_WINDUP, () -> {
			if (!alive(p)) {
				return;
			}
			// v0.12.38: a SLIDE -- level along the ground in the direction he was looking, gravity still applies
			Vec3 dir = flatLook(p);
			long now = p.level().getGameTime();
			grantNoFall(p);
			MOVES.put(p.getUUID(), new Move(Kind.DASH, dir, now, now + dashTicks,
					new AllMightShockwave.Wave(AllMightConfig.CAROLINA_DAMAGE, AllMightConfig.CAROLINA_KNOCKBACK, 0.3)));
			ServerLevel level = (ServerLevel) p.level();
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 1.6f);
			AllMightShockwave.burst(level, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 12, 0.5, 0.15);
		});
	}

	// ---------------------------------------------------------------- X -- New Hampshire Smash

	public static void newHampshire(ServerPlayer p) {
		if (!begin(p, NEW_HAMPSHIRE, AllMightConfig.NEW_HAMPSHIRE_COST, AllMightConfig.NEW_HAMPSHIRE_COOLDOWN,
				AllMightConfig.NEW_HAMPSHIRE_WINDUP + 6, AllMightState.ANIM_NEW_HAMPSHIRE)) {
			return;
		}
		AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_THROW, 1.1f, 0.6f);
		schedule(p, AllMightConfig.NEW_HAMPSHIRE_WINDUP, () -> {
			if (!alive(p)) {
				return;
			}
			ServerLevel level = (ServerLevel) p.level();
			double vy = verticalSpeedForHeight(AllMightConfig.NEW_HAMPSHIRE_HEIGHT) * (p.onGround() ? 1.0 : 0.8);
			Vec3 fwd = flatLook(p).scale(AllMightConfig.NEW_HAMPSHIRE_FORWARD_SPEED);
			AbilityHelpers.launchSelf(p, new Vec3(fwd.x, vy, fwd.z));
			grantNoFall(p);
			long now = level.getGameTime();
			MOVES.put(p.getUUID(), new Move(Kind.AIR, flatLook(p), now, now + 120,
					new AllMightShockwave.Wave(AllMightConfig.NEW_HAMPSHIRE_DAMAGE, AllMightConfig.NEW_HAMPSHIRE_KNOCKBACK, 0.5)));
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.1f);
			AllMightShockwave.burst(level, ParticleTypes.EXPLOSION, p.position(), 1, 0.1, 0.0);
			AllMightShockwave.ring(level, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 2.0, 20);
		});
	}

	// ---------------------------------------------------------------- C -- Plus Ultra

	/** C: toggles Plus Ultra. On: OFA drains and every ability hits 30% harder. Off: a 20 s cooldown starts. */
	public static void plusUltra(ServerPlayer p) {
		AllMightState s = AllMight.state(p);
		if (!s.hasPower || !s.fullPower || !p.isAlive()) {
			return;
		}
		long now = p.level().getGameTime();
		ServerLevel level = (ServerLevel) p.level();
		if (s.plusUltra) {
			AllMightState n = s.copy();
			n.plusUltra = false;
			n.abilityReadyAt.put(PLUS_ULTRA, now + AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS);
			AllMight.save(p, n);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
			AllMight.steam(level, p, 6);
			p.displayClientMessage(Component.translatable("message.projecthero.all_might.plus_ultra_off").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		Long ready = s.abilityReadyAt.get(PLUS_ULTRA);
		if (ready != null && now < ready) {
			AllMight.say(p, "message.projecthero.all_might.cooldown", ChatFormatting.RED,
					Component.translatable("projecthero.all_might.ability." + PLUS_ULTRA), String.format(java.util.Locale.ROOT, "%.1f", (ready - now) / 20.0));
			return;
		}
		if (now < s.busyUntil || s.ofa <= 0f) {
			return;
		}
		AllMightState n = s.copy();
		n.plusUltra = true;
		n.animId = AllMightState.ANIM_COWL;
		n.animStart = now;
		AllMight.save(p, n);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.6f);
		AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 45, 0.6, 0.3);
		AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.4f), c, 25, 0.6, 0.05);
		AllMightShockwave.ring(level, ParticleTypes.END_ROD, p.position().add(0, 0.2, 0), 1.6, 16);
		p.displayClientMessage(Component.translatable("message.projecthero.all_might.plus_ultra").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
	}

	// ---------------------------------------------------------------- V -- United States of Smash

	/** Z pressed: begin the 5 s charge (OFA is spent now and refunded if he lets go early). */
	public static void unitedStatesPress(ServerPlayer p) {
		if (CHARGING.containsKey(p.getUUID())) {
			return;
		}
		int charge = AllMightConfig.UNITED_STATES_CHARGE_TICKS;
		int total = charge + AllMightConfig.UNITED_STATES_SECONDARY_EXPAND_TICKS + 8;
		AllMightState gate = AllMight.state(p);
		if (gate.hasPower && gate.fullPower && gate.ofa + 1.0e-3f < AllMightConfig.UNITED_STATES_COST) {
			AllMight.say(p, "message.projecthero.all_might.low_ofa", ChatFormatting.RED,
					AllMightConfig.UNITED_STATES_COST, (int) Math.floor(gate.ofa));
			return;
		}
		// v0.12.38: nothing is spent and no cooldown starts until the punch is actually cast (begin with cost 0 / cooldown 0)
		if (!begin(p, UNITED_STATES, 0f, 0, total, AllMightState.ANIM_UNITED_STATES)) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		long start = level.getGameTime();
		AllMightState n = AllMight.state(p).copy();
		n.animStart = start + charge - AllMightConfig.UNITED_STATES_POSE_LEAD; // the punch pose only plays at the end of the charge
		n.chargeStart = start;
		AllMight.save(p, n);
		CHARGING.put(p.getUUID(), start);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, charge + 4, 3, false, false, false));
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, 0.8f);
		for (int t = 0; t < charge; t += 4) {
			final int tt = t;
			schedule(p, t, () -> {
				if (!charging(p, start)) {
					return;
				}
				float grow = 0.4f + 0.6f * tt / (float) charge;
				Vec3 c = p.position().add(0, p.getBbHeight() * 0.55, 0);
				AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, (int) (8 + 14 * grow), 0.5 + 0.5 * grow, 0.3);
				AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.2f + grow), c, (int) (6 + 10 * grow), 0.6, 0.05);
				AllMightShockwave.ring(level, ParticleTypes.CLOUD, p.position().add(0, 0.15, 0), 3.0 - 2.2 * grow, 16);
				if (tt % 8 == 0) {
					AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 0.6f + 0.5f * grow);
				}
				AllMightShockwave.shake(level, p.position(), 0.08f + 0.12f * grow, 6);
			});
		}
		schedule(p, charge, () -> {
			if (!charging(p, start)) {
				return;
			}
			CHARGING.remove(p.getUUID());
			fireUnitedStates(p);
		});
	}

	private static boolean charging(ServerPlayer p, long start) {
		Long c = CHARGING.get(p.getUUID());
		return c != null && c == start;
	}

	/** Z released: an unfinished charge is cancelled for free. */
	public static void unitedStatesRelease(ServerPlayer p) {
		if (CHARGING.containsKey(p.getUUID())) {
			cancelCharge(p, true);
			p.displayClientMessage(Component.translatable("message.projecthero.all_might.charge_cancelled").withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** Ends a charge in progress. Nothing was spent and no cooldown started, so there is nothing to refund (v0.12.38). */
	static void cancelCharge(ServerPlayer p, boolean refund) {
		if (CHARGING.remove(p.getUUID()) == null) {
			return;
		}
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		AllMightState s = AllMight.state(p);
		AllMightState n = s.copy();
		n.chargeStart = 0L;
		n.busyUntil = p.level().getGameTime();
		n.animId = AllMightState.ANIM_NONE;
		AllMight.save(p, n);
	}

	private static void fireUnitedStates(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		// v0.12.38: the cost and the 75 s cooldown are paid now, when the punch is cast
		AllMightState st = AllMight.state(p).copy();
		st.ofa = Math.max(0f, st.ofa - AllMightConfig.UNITED_STATES_COST);
		st.abilityReadyAt.put(UNITED_STATES, now + AllMightConfig.UNITED_STATES_COOLDOWN);
		st.chargeStart = 0L;
		AllMight.save(p, st);
		grantNoFall(p);
		p.swing(InteractionHand.MAIN_HAND, true);
		Vec3 origin = p.position();
		Vec3 flat = flatLook(p);
		Vec3 fist = origin.add(0, 1.3, 0).add(flat.scale(2.5));
		AllMightShockwave.burst(level, ParticleTypes.EXPLOSION_EMITTER, fist, 1, 0.0, 0.0);
		AllMightShockwave.burst(level, ParticleTypes.CLOUD, fist, 40, 1.4, 0.3);
		AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, fist, 40, 1.2, 0.5);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.7f);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.8f, 0.4f);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4f, 0.8f);
		AllMightShockwave.shake(level, fist, 1.0f, 24);
		// the crater, all around him
		AllMightShockwave.breakBlocks(p, origin.add(0, -1.5, 0), AllMightConfig.UNITED_STATES_BLOCK_RADIUS, AllMightConfig.UNITED_STATES_BLOCK_MAX,
				AllMightConfig.UNITED_STATES_BLOCK_HARDNESS);
		// an AoE: one full-damage shockwave expanding from where he stood to the full range, hitting everything once
		var wave = new AllMightShockwave.Wave(AllMightConfig.UNITED_STATES_DAMAGE, AllMightConfig.UNITED_STATES_KNOCKBACK,
				AllMightConfig.UNITED_STATES_LIFT);
		int expand = AllMightConfig.UNITED_STATES_SECONDARY_EXPAND_TICKS;
		for (int k = 1; k <= expand; k++) {
			final int kk = k;
			schedule(p, k - 1, () -> {
				if (!alive(p)) {
					return;
				}
				double r = AllMightConfig.UNITED_STATES_RANGE * kk / expand;
				AllMightShockwave.radial(p, origin, r, wave, false);
				AllMightShockwave.ring(level, ParticleTypes.CLOUD, origin.add(0, 0.25, 0), r, 28);
				if (kk % 2 == 0) {
					AllMightShockwave.ring(level, ParticleTypes.SWEEP_ATTACK, origin.add(0, 1.0, 0), r, 16);
				}
				if (kk == 1 || kk == expand) {
					level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.6f, 0.7f);
				}
				if (kk == expand) {
					AllMightShockwave.shake(level, origin, 0.6f, 16);
				}
			});
		}
	}

	// ---------------------------------------------------------------- X -- Leap

	public static void leap(ServerPlayer p) {
		if (!begin(p, LEAP, AllMightConfig.LEAP_COST, AllMightConfig.LEAP_COOLDOWN, 8, AllMightState.ANIM_LEAP)) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		// v0.12.38: launched along the look direction (always with some lift)
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.max(look.y, AllMightConfig.LEAP_MIN_LIFT), look.z).normalize();
		AbilityHelpers.launchSelf(p, dir.scale(AllMightConfig.LEAP_SPEED));
		grantNoFall(p);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
		AllMightShockwave.ring(level, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 1.8, 20);
		AllMightShockwave.burst(level, ParticleTypes.POOF, p.position().add(0, 0.2, 0), 14, 0.5, 0.15);
		// the landing shockwave is the generic hard-landing impact in AllMight (a 17-block leap is a heavy landing)
	}

	// ---------------------------------------------------------------- per-tick

	public static void tick(ServerPlayer p) {
		if (CHARGING.containsKey(p.getUUID()) && (!p.isAlive() || !AllMight.isFullPower(p))) {
			cancelCharge(p, false);
		}
		List<Task> tasks = TASKS.get(p.getUUID());
		if (tasks != null && !tasks.isEmpty()) {
			long now = p.level().getGameTime();
			for (Task t : new ArrayList<>(tasks)) {
				if (t.due() <= now) {
					tasks.remove(t);
					if (alive(p)) {
						t.run().run();
					}
				}
			}
			if (tasks.isEmpty()) {
				TASKS.remove(p.getUUID());
			}
		}
		Move m = MOVES.get(p.getUUID());
		if (m != null) {
			if (!tickMove(p, m)) {
				MOVES.remove(p.getUUID());
			}
		}
	}

	/** Advances a movement attack one tick; false when it is over. */
	private static boolean tickMove(ServerPlayer p, Move m) {
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		if (!p.isAlive() || p.isSpectator() || p.isPassenger() || now > m.until + 2) {
			return false;
		}
		m.step++;
		Vec3 center = p.position().add(0, 0.9, 0);
		if (m.kind == Kind.DASH) {
			Vec3 vel = m.dir.scale(AllMightConfig.CAROLINA_SPEED);
			BlockPos ahead = BlockPos.containing(p.position().add(vel.scale(1.5)));
			// a low ledge (up to a step) does not stop the slide, a wall does
			boolean blocked = !level.hasChunkAt(ahead) || !level.noCollision(p, p.getBoundingBox().move(vel.x, 0.6, vel.z));
			if (blocked || now >= m.until) {
				AbilityHelpers.launchSelf(p, m.dir.scale(0.2)); // stop, do not fling
				if (blocked) {
					AllMightShockwave.breakBlocks(p, p.position().add(m.dir.scale(1.5)).add(0, 1.0, 0), AllMightConfig.CAROLINA_BLOCK_RADIUS,
							AllMightConfig.CAROLINA_BLOCK_MAX, AllMightConfig.CAROLINA_BLOCK_HARDNESS);
					AllMightShockwave.burst(level, ParticleTypes.CLOUD, center.add(m.dir.scale(1.2)), 12, 0.5, 0.1);
				}
				return false;
			}
			double vy = p.onGround() ? -0.05 : Math.max(p.getDeltaMovement().y - 0.08, -1.0);
			AbilityHelpers.launchSelf(p, new Vec3(vel.x, vy, vel.z));
			AllMightShockwave.radial(p, center, 2.2, m.wave, false);
			AllMightShockwave.windLine(level, center.subtract(m.dir.scale(0.4)), m.dir.scale(-1.0), 5.0, 1.2, 2);
			if (m.step % 2 == 0) {
				AllMightShockwave.burst(level, ParticleTypes.SWEEP_ATTACK, center.subtract(m.dir.scale(1.0)), 1, 0.3, 0.0);
			}
			return true;
		}
		// AIR: New Hampshire -- strike what he flies through, then crash down
		AllMightShockwave.radial(p, center, 2.4, m.wave, false);
		if (m.step % 2 == 0) {
			AllMightShockwave.burst(level, ParticleTypes.CLOUD, center, 3, 0.4, 0.05);
			AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, center, 2, 0.4, 0.1);
		}
		boolean landed = m.step > 6 && (p.onGround() || p.isInWater());
		if (landed || now >= m.until) {
			Vec3 at = p.position();
			AllMightShockwave.radial(p, at, AllMightConfig.NEW_HAMPSHIRE_LANDING_RADIUS, m.wave, true);
			AllMightShockwave.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), AllMightConfig.NEW_HAMPSHIRE_LANDING_RADIUS * 0.6, 24);
			AllMightShockwave.ring(level, ParticleTypes.SWEEP_ATTACK, at.add(0, 0.6, 0), AllMightConfig.NEW_HAMPSHIRE_LANDING_RADIUS * 0.8, 16);
			AllMightShockwave.burst(level, ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 2, 0.8, 0.0);
			AllMightShockwave.breakBlocks(p, at.add(0, -0.5, 0), AllMightConfig.NEW_HAMPSHIRE_BLOCK_RADIUS, AllMightConfig.NEW_HAMPSHIRE_BLOCK_MAX,
					AllMightConfig.NEW_HAMPSHIRE_BLOCK_HARDNESS);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, 0.8f);
			AllMightShockwave.shake(level, at, 0.7f, 14);
			NEW_HAMPSHIRE_LANDED.put(p.getUUID(), now);
			p.resetFallDistance();
			return false;
		}
		return true;
	}
}
