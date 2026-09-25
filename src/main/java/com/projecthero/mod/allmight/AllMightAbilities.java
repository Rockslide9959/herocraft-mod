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
	public static final String COWL = "full_cowl";
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

	private AllMightAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		MOVES.clear();
		NEW_HAMPSHIRE_LANDED.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		MOVES.remove(id);
		NEW_HAMPSHIRE_LANDED.remove(id);
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
		if (!s.hasPower || !p.isAlive() || p.isSpectator()) {
			return false;
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
			Vec3 look = p.getLookAngle();
			// nearly-level: a dash may angle a little up or down, never dive into the floor or shoot into the sky
			Vec3 dir = new Vec3(look.x, Math.max(-0.25, Math.min(0.35, look.y)), look.z).normalize();
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

	// ---------------------------------------------------------------- C -- Full Cowl

	public static void fullCowl(ServerPlayer p) {
		AllMightState s = AllMight.state(p);
		if (s.hasPower && s.cowlUntil > p.level().getGameTime()) {
			AllMight.say(p, "message.projecthero.all_might.cowl_active", ChatFormatting.GRAY);
			return; // no duplicate buff, and no OFA is spent on a press that does nothing
		}
		if (!begin(p, COWL, AllMightConfig.COWL_OFA_COST, AllMightConfig.COWL_COOLDOWN_TICKS, 12, AllMightState.ANIM_COWL)) {
			return;
		}
		AllMightState n = AllMight.state(p).copy();
		n.cowlUntil = p.level().getGameTime() + AllMightConfig.COWL_DURATION_TICKS;
		AllMight.save(p, n);
		AllMight.reconcile(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.6f);
		AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 45, 0.6, 0.3);
		AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.4f), c, 25, 0.6, 0.05);
		AllMightShockwave.ring(level, ParticleTypes.END_ROD, p.position().add(0, 0.2, 0), 1.6, 16);
		p.displayClientMessage(Component.translatable("message.projecthero.all_might.cowl").withStyle(ChatFormatting.GREEN), true);
	}

	// ---------------------------------------------------------------- V -- United States of Smash

	public static void unitedStates(ServerPlayer p) {
		int windup = AllMightConfig.UNITED_STATES_WINDUP;
		int total = windup + AllMightConfig.UNITED_STATES_SECONDARY_DELAY + AllMightConfig.UNITED_STATES_SECONDARY_EXPAND_TICKS + 8;
		if (!begin(p, UNITED_STATES, AllMightConfig.UNITED_STATES_COST, AllMightConfig.UNITED_STATES_COOLDOWN, total,
				AllMightState.ANIM_UNITED_STATES)) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		// Phase 1 -- preparation: a growing aura, building wind, the charge sound
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, windup + 4, 3, false, false, false));
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, 0.8f);
		for (int t = 0; t < windup; t += 4) {
			final int tt = t;
			schedule(p, t, () -> {
				if (!alive(p)) {
					return;
				}
				float grow = 0.4f + 0.6f * tt / (float) windup;
				Vec3 c = p.position().add(0, 1.0, 0);
				AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, (int) (8 + 14 * grow), 0.5 + 0.5 * grow, 0.3);
				AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.2f + grow), c, (int) (6 + 10 * grow), 0.6, 0.05);
				AllMightShockwave.ring(level, ParticleTypes.CLOUD, p.position().add(0, 0.15, 0), 3.0 - 2.2 * grow, 16);
				if (tt % 8 == 0) {
					AbilityHelpers.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 0.6f + 0.5f * grow);
				}
				AllMightShockwave.shake(level, p.position(), 0.08f + 0.12f * grow, 6);
			});
		}
		// Phase 2 -- the attack, on the animation's impact frame
		schedule(p, windup, () -> {
			if (!alive(p)) {
				return;
			}
			p.swing(InteractionHand.MAIN_HAND, true);
			Vec3 origin = p.position();
			Vec3 look = p.getLookAngle();
			Vec3 flat = flatLook(p);
			Vec3 fist = origin.add(0, 1.3, 0).add(flat.scale(2.5));
			var primary = new AllMightShockwave.Wave(AllMightConfig.UNITED_STATES_DAMAGE, AllMightConfig.UNITED_STATES_KNOCKBACK,
					AllMightConfig.UNITED_STATES_LIFT);
			AllMightShockwave.sweep(p, origin, look, 0.0, AllMightConfig.UNITED_STATES_RANGE, AllMightConfig.UNITED_STATES_WIDTH,
					AllMightConfig.UNITED_STATES_HEIGHT, primary);
			AllMightShockwave.windLine(level, origin.add(0, 1.2, 0), look, AllMightConfig.UNITED_STATES_RANGE, AllMightConfig.UNITED_STATES_WIDTH * 0.8, 3);
			AllMightShockwave.burst(level, ParticleTypes.EXPLOSION_EMITTER, fist, 1, 0.0, 0.0);
			AllMightShockwave.burst(level, ParticleTypes.CLOUD, fist, 40, 1.4, 0.3);
			AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, fist, 40, 1.2, 0.5);
			level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.7f);
			level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.8f, 0.4f);
			level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4f, 0.8f);
			AllMightShockwave.shake(level, fist, 1.0f, 24);
			// Phase 4 (part) -- the crater
			Vec3 crater = origin.add(flat.scale(5.0));
			AllMightShockwave.breakBlocks(p, crater, AllMightConfig.UNITED_STATES_BLOCK_RADIUS, AllMightConfig.UNITED_STATES_BLOCK_MAX,
					AllMightConfig.UNITED_STATES_BLOCK_HARDNESS);

			// Phase 3 -- the larger surrounding wave, expanding outward from where he stood
			var secondary = new AllMightShockwave.Wave(AllMightConfig.UNITED_STATES_DAMAGE * AllMightConfig.UNITED_STATES_SECONDARY_DAMAGE_FRACTION,
					AllMightConfig.UNITED_STATES_SECONDARY_KNOCKBACK, 0.5);
			secondary.hit.addAll(primary.hit); // whoever the punch itself struck is not hit again
			int expand = AllMightConfig.UNITED_STATES_SECONDARY_EXPAND_TICKS;
			for (int k = 1; k <= expand; k++) {
				final int kk = k;
				schedule(p, AllMightConfig.UNITED_STATES_SECONDARY_DELAY + k, () -> {
					if (!alive(p)) {
						return;
					}
					double r = AllMightConfig.UNITED_STATES_SECONDARY_RANGE * kk / expand;
					AllMightShockwave.radial(p, origin, r, secondary, false);
					AllMightShockwave.ring(level, ParticleTypes.CLOUD, origin.add(0, 0.25, 0), r, 24);
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
		});
	}

	// ---------------------------------------------------------------- N -- All Might Leap

	public static void leap(ServerPlayer p) {
		if (!begin(p, LEAP, AllMightConfig.LEAP_COST, AllMightConfig.LEAP_COOLDOWN, 8, AllMightState.ANIM_LEAP)) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		double vy = verticalSpeedForHeight(AllMightConfig.LEAP_HEIGHT);
		Vec3 fwd = flatLook(p).scale(AllMightConfig.LEAP_FORWARD_SPEED);
		AbilityHelpers.launchSelf(p, new Vec3(fwd.x, vy, fwd.z));
		grantNoFall(p);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
		AllMightShockwave.ring(level, ParticleTypes.CLOUD, p.position().add(0, 0.2, 0), 1.8, 20);
		AllMightShockwave.burst(level, ParticleTypes.POOF, p.position().add(0, 0.2, 0), 14, 0.5, 0.15);
		// the landing shockwave is the generic hard-landing impact in AllMight (a 17-block leap is a heavy landing)
	}

	// ---------------------------------------------------------------- per-tick

	public static void tick(ServerPlayer p) {
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
			boolean blocked = !level.hasChunkAt(ahead) || !level.noCollision(p, p.getBoundingBox().move(vel));
			if (blocked || now >= m.until) {
				AbilityHelpers.launchSelf(p, m.dir.scale(0.2)); // stop, do not fling
				if (blocked) {
					AllMightShockwave.breakBlocks(p, p.position().add(m.dir.scale(1.5)).add(0, 1.0, 0), AllMightConfig.CAROLINA_BLOCK_RADIUS,
							AllMightConfig.CAROLINA_BLOCK_MAX, AllMightConfig.CAROLINA_BLOCK_HARDNESS);
					AllMightShockwave.burst(level, ParticleTypes.CLOUD, center.add(m.dir.scale(1.2)), 12, 0.5, 0.1);
				}
				return false;
			}
			AbilityHelpers.launchSelf(p, vel);
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
