package com.projecthero.mod.quirk.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (revamp, user: "engine should have an ability where they engage the engines to run faster and not just like 2
 * short dash moves", then "give it 1 dash ability and the rest should be attacks and a mode ability", "for engine using v
 * to shift up and down gears makes more sense"): Engine is built around RUNNING and KICKING.
 * <ul>
 *   <li><b>The gearbox</b> (V up, Shift+V down, gears 0..3): a sustained sprint -- v0.16.7 (user) 20 / 30 / 40 blocks a
 *       second flat out -- that builds with <b>momentum</b> while he runs, heats the engines faster the higher the gear,
 *       slides on sharp turns and bowls mobs over at Gear 2+. V at Gear 3 just says "Max gear".</li>
 *   <li><b>The mode -- Recipro Turbo</b> (C; v0.16.7 user, replacing Recipro Burst / Extend): 30 s at double the gear's
 *       speed (80 b/s at Gear 3), no heat at all, every hit +50%, the calf engines blazing blue; then the engines stall for
 *       a full minute (no gears, no moves, a slow walk, steam and sputtering). C again ends it early.</li>
 *   <li><b>The one dash</b>: X, Recipro Dash -- in Turbo it is the <b>Turbo Strike</b> (the old ultimate's straight-line
 *       rush through everything in the path).</li>
 *   <li><b>Everything else is a kick</b>: Recipro Kick (speed-scaled), Spin Kick, Kick Barrage, Recipro Extend Kick, Rising
 *       Kick, Axe Kick, Sweep Kick and the finishing combo.</li>
 * </ul>
 * Heat cools on its own while he walks or stands (fastest at Gear 0); at 100 he stalls. Wearing the Ingenium suit it
 * builds half as fast. Every public move is called by {@link EngineKit#handle} once its cooldown is ready, and by the
 * gametests directly.
 *
 * <p>Movement: the run itself is an ADD_MULTIPLIED_BASE movement-speed modifier like Super Speed's (synced to the player's
 * own client, which simulates the run -- the server only authorises it; the client adds the drift of a sliding turn and
 * carries the run through jumps, {@code EngineRunClient}); the dash and the Turbo Strike are
 * {@link AbilityHelpers#launchSelf} impulses. The server measures his speed from his position
 * each tick. The sessions are static, keyed by player, hold entity ids never entities, and are dropped by
 * {@link #forget} / {@link #clearSessionState} (chained from {@code ServerStateReset} through {@code QuirkKits}).
 */
public final class EngineMoves {
	public static final ResourceLocation GEAR_ID = PowerToggles.id("quirk_engine_gear_speed");
	public static final ResourceLocation STEP_ID = PowerToggles.id("quirk_engine_step");
	/** Recipro Turbo's +50% on his plain melee too (the moves get it in {@link EngineCombat#strike}). */
	public static final ResourceLocation TURBO_DAMAGE_ID = PowerToggles.id("quirk_engine_turbo_damage");
	/** The pre-revamp always-on Speed I (taken off if a player still has it). */
	public static final ResourceLocation LEGACY_PASSIVE_ID = PowerToggles.id("quirk_engine_passive_speed");

	static final class Session {
		boolean legacyChecked;
		// the gearbox
		int gear;
		float momentum;
		int momentumSynced = -1;
		// his run, measured from his position
		@Nullable Vec3 lastPos;
		Vec3 lastDir = Vec3.ZERO;
		/** Blocks a tick, smoothed. */
		double speed;
		long lastSlide = -100L;
		final Map<Integer, Long> bowled = new HashMap<>();
		// Recipro Turbo was running last tick (its end stalls the engines)
		boolean turboOn;
		long nextSputter;
		// the straight-line moves (Recipro Dash, the Turbo Strike): start, from, direction, length
		long dashAt = -1L;
		Vec3 dashFrom = Vec3.ZERO;
		Vec3 dashDir = Vec3.ZERO;
		double dashLength;
		long strikeAt = -1L;
		Vec3 strikeFrom = Vec3.ZERO;
		Vec3 strikeDir = Vec3.ZERO;
		double strikeLength;
		final Set<Integer> strikeHit = new HashSet<>();
		// the timed kicks
		long axeAt = -1L;
		long barrageAt = -1L;
		int barrageHits;
		long finisherAt = -1L;
		int finisherStep;
		long extendKickAt = -1L;
		/** His speed when the charged kick started (it keeps the run's power). */
		double extendKickSpeed;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private EngineMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void sound(ServerPlayer p, net.minecraft.core.Holder<SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** The engines rev: a piston thump, a blast of exhaust and a rising whine, higher the higher {@code level} (0..3+). */
	private static void rev(ServerPlayer p, float level) {
		sound(p, SoundEvents.PISTON_EXTEND, 0.7f, 0.6f + level * 0.25f);
		sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.45f + level * 0.1f, 0.6f + level * 0.2f);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.3f, 1.4f + level * 0.15f);
	}

	private static long val(ServerPlayer p, String key) {
		return Quirks.state(p).val(key);
	}

	private static boolean running(ServerPlayer p, String key) {
		return val(p, key) > now(p);
	}

	// ================================================================ reads (server; tests)

	/** The gear engaged, 0..3. */
	public static int gear(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.gear;
	}

	/** The run's momentum, 0..1. */
	public static float momentum(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0f : s.momentum;
	}

	/** Test hook / harness: set the momentum (synced on the next tick). */
	public static void setMomentum(ServerPlayer p, float m) {
		session(p).momentum = Mth.clamp(m, 0f, 1f);
	}

	/** His measured speed, blocks a second. */
	public static double speedBps(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0.0 : s.speed * 20.0;
	}

	/** Test hook / harness: pretend he is running this fast (blocks a second) the way he faces. */
	public static void setSpeed(ServerPlayer p, double bps) {
		Session s = session(p);
		s.speed = bps / 20.0;
		s.lastDir = flatLook(p);
	}

	/** The run's flat-ground sprint speed (blocks a second) in a gear at a momentum, with Recipro Turbo ({@code mult} 1 = none). */
	public static double runBps(int gear, float momentum, double mult) {
		int g = Mth.clamp(gear, 0, EngineConfig.MAX_GEAR);
		double built = EngineConfig.MOMENTUM_FLOOR + (1.0 - EngineConfig.MOMENTUM_FLOOR) * Mth.clamp(momentum, 0f, 1f);
		return EngineConfig.GEAR_TOP_BPS[g] * built * Math.max(1.0, mult);
	}

	/**
	 * The movement-speed bonus (ADD_MULTIPLIED_BASE, like Super Speed's modes) that sprints at {@link #runBps}: vanilla
	 * sprinting is 0.1 x 1.3 x 43.17 = 5.6 b/s, so the bonus is {@code bps / 5.6 - 1} (Gear 3 flat out: 40 b/s = +6.13).
	 * Never below normal.
	 */
	public static double speedBonus(int gear, float momentum, double mult) {
		return Math.max(0.0, runBps(gear, momentum, mult) / EngineConfig.VANILLA_SPRINT_BPS - 1.0);
	}

	/** Recipro Kick's damage at a speed (blocks a second). */
	public static float kickDamage(double bps) {
		return (float) Math.min(EngineConfig.KICK_DAMAGE_MAX, EngineConfig.KICK_DAMAGE + EngineConfig.KICK_DAMAGE_PER_SPEED * Math.max(0.0, bps));
	}

	/**
	 * Adds heat; at 100 the engines stall. In Recipro Turbo heat doesn't build at all; in the Ingenium suit it builds
	 * {@link EngineConfig#SUIT_HEAT_MULT} as fast.
	 */
	public static void heat(ServerPlayer p, float amount) {
		if (amount > 0f) {
			if (running(p, Engine.V_TURBO)) {
				return;
			}
			if (Engine.suitWorn(p)) {
				amount *= EngineConfig.SUIT_HEAT_MULT;
			}
		}
		if (Quirks.addMeter(p, amount) >= 100f && !Quirks.overloaded(p)) {
			stall(p, EngineConfig.STALL_TICKS);
		}
	}

	/** Stall: Slowness III, no moves, back to gear 0 (everything running stops). */
	public static void stall(ServerPlayer p, int ticks) {
		Quirks.overload(p, ticks); // -> stopAll: gear 0, Turbo off, modifiers off
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, EngineConfig.STALL_SLOW_AMPLIFIER, false, true, true));
		Quirks.say(p, "message.projecthero.quirk.overloaded.engine", ChatFormatting.RED);
		Quirks.setAnim(p, Engine.ANIM_STALL);
		sound(p, SoundEvents.PISTON_CONTRACT, 1.0f, 0.5f);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.6f);
		sound(p, SoundEvents.IRON_GOLEM_DAMAGE, 0.5f, 0.6f);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.4, p.getZ(), 14, 0.3, 0.2, 0.3, 0.02);
		steam(level, p, 8);
		session(p).nextSputter = now(p) + 10;
	}

	/** A stalled engine coughing: a sputter of steam and a choked, uneven engine noise. */
	private static void sputter(ServerPlayer p, Session s, long now) {
		var r = p.getRandom();
		s.nextSputter = now + EngineConfig.SPUTTER_EVERY / 2 + r.nextInt(EngineConfig.SPUTTER_EVERY);
		ServerLevel level = p.serverLevel();
		steam(level, p, 4 + r.nextInt(4));
		int coughs = 1 + r.nextInt(3);
		for (int i = 0; i < coughs; i++) {
			sound(p, SoundEvents.PISTON_CONTRACT, 0.35f, 0.45f + r.nextFloat() * 0.25f);
		}
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.4f, 0.7f + r.nextFloat() * 0.5f);
		if (r.nextInt(3) == 0) {
			// a backfire: a pop and a puff of black smoke out of one calf
			Vec3 c = EngineCombat.calf(p, r.nextBoolean() ? 1 : -1);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 3, 0.05, 0.05, 0.05, 0.02);
			sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.35f, 0.5f + r.nextFloat() * 0.2f);
		}
	}

	// ================================================================ tick: the gearbox, the meter, running moves

	static void tick(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		QuirkState st = Quirks.state(p);
		if (!s.legacyChecked) {
			s.legacyChecked = true;
			if (st.vals.containsKey(Engine.LEGACY_GEAR) || st.vals.containsKey(Engine.LEGACY_KICK) || st.vals.containsKey(Engine.LEGACY_CARRY)
					|| st.vals.containsKey(Engine.LEGACY_STEAM) || st.vals.containsKey(Engine.LEGACY_BURST) || st.vals.containsKey(Engine.LEGACY_EXTEND)) {
				long carried = st.val(Engine.LEGACY_CARRY);
				if (carried != 0L && p.level().getEntity((int) carried) instanceof LivingEntity le && le.getVehicle() == p) {
					le.stopRiding(); // the old Rescue Carry: set it down
					syncPassengers(p);
				}
				Quirks.edit(p, n -> {
					n.vals.remove(Engine.LEGACY_GEAR);
					n.vals.remove(Engine.LEGACY_KICK);
					n.vals.remove(Engine.LEGACY_CARRY);
					n.vals.remove(Engine.LEGACY_STEAM);
					n.vals.remove(Engine.LEGACY_BURST);
					n.vals.remove(Engine.LEGACY_EXTEND);
				});
			}
			s.gear = (int) Mth.clamp(st.val(Engine.V_GEAR_NO), 0L, EngineConfig.MAX_GEAR);
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, LEGACY_PASSIVE_ID);
			st = Quirks.state(p);
		}
		boolean stalled = Quirks.overloaded(p);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, STEP_ID, EngineConfig.STEP_BONUS, AttributeModifier.Operation.ADD_VALUE);

		// --- his run, from his position
		Vec3 pos = p.position();
		double raw = 0.0;
		Vec3 dir = s.lastDir;
		if (s.lastPos != null) {
			double dx = pos.x - s.lastPos.x, dz = pos.z - s.lastPos.z;
			raw = Math.sqrt(dx * dx + dz * dz);
			if (raw > 4.0) {
				raw = 0.0; // a teleport
			} else if (raw > 0.05) {
				dir = new Vec3(dx / raw, 0, dz / raw);
			}
		}
		if (s.lastPos != null) {
			s.speed = s.speed * 0.5 + raw * 0.5;
		}
		s.lastPos = pos;
		boolean moving = s.speed > 0.08;

		boolean turbo = st.val(Engine.V_TURBO) > now;
		// --- Recipro Turbo always ends in the minute-long stall (as in the anime)
		if (s.turboOn && !turbo && !stalled) {
			s.turboOn = false;
			turboEnds(p);
			stalled = true;
		}
		s.turboOn = turbo;
		if (turbo) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, TURBO_DAMAGE_ID, EngineConfig.TURBO_DAMAGE_MULT - 1.0,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, TURBO_DAMAGE_ID);
		}
		boolean recipro = turbo;

		// --- momentum
		if (stalled || s.gear <= 0) {
			s.momentum = 0f;
		} else if (moving) {
			float rate = EngineConfig.MOMENTUM_BUILD[s.gear] * (recipro ? EngineConfig.TURBO_BUILD_MULT : 1f);
			if (p.isSprinting() || recipro) {
				s.momentum = Math.min(1f, s.momentum + rate);
			} else if (s.momentum < EngineConfig.MOMENTUM_WALK_CAP) {
				s.momentum = Math.min(EngineConfig.MOMENTUM_WALK_CAP, s.momentum + rate * 0.5f);
			} else {
				s.momentum = Math.max(EngineConfig.MOMENTUM_WALK_CAP, s.momentum - 0.01f);
			}
		} else if (p.onGround()) {
			s.momentum = Math.max(0f, s.momentum - EngineConfig.MOMENTUM_BLEED);
		}

		// --- sliding turns: the run swings round sharply at speed
		if (!stalled && s.gear > 0 && raw > 0.25 && p.onGround() && s.momentum >= EngineConfig.SLIDE_MIN_MOMENTUM && s.lastDir.lengthSqr() > 0.5) {
			double cos = Mth.clamp(s.lastDir.dot(dir), -1.0, 1.0);
			// the client holds the turn to its grip a tick, so a turn right at the grip IS the slide (high gears never reach 28)
			double slideAt = Math.min(EngineConfig.SLIDE_ANGLE, EngineConfig.grip(s.gear, s.momentum, recipro) * 0.85);
			if (Math.toDegrees(Math.acos(cos)) >= slideAt && now - s.lastSlide > 6) {
				slide(p, s, dir, now);
			}
		}
		s.lastDir = dir;

		// --- the run's speed
		if (stalled) {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID);
		} else {
			double mult = turbo ? EngineConfig.TURBO_MULT : 1.0;
			double bonus = speedBonus(s.gear, s.momentum, mult);
			bonus = Math.round(bonus / EngineConfig.SPEED_STEP) * EngineConfig.SPEED_STEP;
			if (bonus > 0.0) {
				PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			} else {
				PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID);
			}
		}

		// --- bowling mobs over (swept along this tick's whole path: at 80 b/s he covers 4 blocks a tick)
		if (!stalled && (s.gear >= EngineConfig.BOWL_MIN_GEAR || recipro) && s.momentum >= EngineConfig.BOWL_MIN_MOMENTUM && s.speed > 0.35) {
			bowl(p, s, dir, raw, now);
		}

		// --- the meter
		float perSecond;
		if (stalled) {
			perSecond = -EngineConfig.STALL_COOL_PER_SECOND;
		} else if (turbo) {
			perSecond = 0f;
		} else if (s.gear > 0) {
			// sprinting in gear heats the engines; walking or standing in gear cools them slowly (passive cooling)
			perSecond = moving && p.isSprinting() ? EngineConfig.GEAR_HEAT_PER_SECOND[s.gear] : -EngineConfig.IDLE_COOL_PER_SECOND;
		} else {
			perSecond = -EngineConfig.COOL_PER_SECOND;
		}
		if (perSecond < 0f && p.isInWaterOrRain()) {
			perSecond *= EngineConfig.WATER_COOL_MULT;
		}
		if (perSecond > 0f) {
			heat(p, perSecond / 20f);
		} else if (perSecond < 0f) {
			Quirks.addMeter(p, perSecond / 20f);
		}

		// --- running moves
		if (s.dashAt >= 0L) {
			dashStep(p, s, now - s.dashAt);
		}
		if (s.strikeAt >= 0L) {
			strikeStep(p, s, now - s.strikeAt);
		}
		if (s.axeAt >= 0L && now - s.axeAt >= EngineConfig.AXE_DELAY) {
			s.axeAt = -1L;
			axeLand(p, s);
		}
		if (s.barrageAt >= 0L) {
			barrageStep(p, s, now);
		}
		if (s.finisherAt >= 0L) {
			finisherStep(p, s, now - s.finisherAt);
		}
		if (s.extendKickAt >= 0L) {
			extendKickStep(p, s, now - s.extendKickAt);
		}
		if (stalled) {
			if (p.tickCount % 3 == 0) {
				steam(p.serverLevel(), p, 2);
			}
			if (now >= s.nextSputter || s.nextSputter - now > 100) {
				sputter(p, s, now);
			}
		} else if (turbo && p.tickCount % 2 == 0) {
			// the calf engines blaze: blue fire licking off both calves (the run's own trails are drawn client side)
			EngineCombat.exhaust(p.serverLevel(), p, 1, 2);
		}

		// --- mirror the gearbox to the client (HUD, FX, sound, poses)
		syncGearbox(p, s);
	}

	private static void syncGearbox(ServerPlayer p, Session s) {
		QuirkState st = Quirks.state(p);
		if (st.val(Engine.V_GEAR_NO) != s.gear) {
			Quirks.setVal(p, Engine.V_GEAR_NO, s.gear);
		}
		int m = Math.round(s.momentum * 100f);
		if (m != s.momentumSynced && (Math.abs(m - s.momentumSynced) >= 4 || m == 0 || m == 100)) {
			s.momentumSynced = m;
			if (st.val(Engine.V_MOMENTUM) != m) {
				Quirks.setVal(p, Engine.V_MOMENTUM, m);
			}
		}
	}

	private static void slide(ServerPlayer p, Session s, Vec3 dir, long now) {
		s.lastSlide = now;
		s.momentum *= 1f - EngineConfig.SLIDE_MOMENTUM_LOSS;
		Quirks.setVal(p, Engine.V_SLIDE, now);
		ServerLevel level = p.serverLevel();
		Vec3 back = s.lastDir;
		for (int i = 0; i < 6; i++) {
			Vec3 at = p.position().subtract(back.scale(0.2 * i)).add(0, 0.05, 0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.15, 0.02, 0.15, 0.02);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.1, at.z, 2, 0.2, 0.05, 0.2, 0.15);
		}
		level.sendParticles(EngineCombat.EXHAUST, p.getX(), p.getY() + 0.3, p.getZ(), 8, 0.3, 0.1, 0.3, 0.0);
		sound(p, SoundEvents.GRINDSTONE_USE, 0.55f, 1.7f);
		sound(p, SoundEvents.SAND_BREAK, 0.8f, 0.7f);
	}

	private static void bowl(ServerPlayer p, Session s, Vec3 dir, double travelled, long now) {
		AABB box = p.getBoundingBox().expandTowards(dir.scale(0.9)).expandTowards(dir.scale(-Math.min(4.5, travelled))).inflate(0.25, 0.0, 0.25);
		float dmg = (float) Math.min(EngineConfig.BOWL_DAMAGE_MAX, EngineConfig.BOWL_DAMAGE + EngineConfig.BOWL_DAMAGE_PER_SPEED * bps(s));
		boolean any = false;
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && HeroTargets.isHostile(p, e))) {
			if (e.getVehicle() == p) {
				continue;
			}
			Long last = s.bowled.get(e.getId());
			if (last != null && now - last < EngineConfig.BOWL_REHIT && now >= last) {
				continue;
			}
			s.bowled.put(e.getId(), now);
			if (EngineCombat.strike(p, e, e.position().subtract(dir), dmg, EngineConfig.BOWL_KNOCKBACK, EngineConfig.BOWL_LIFT)) {
				any = true;
				s.momentum = Math.max(0f, s.momentum - (EngineCombat.isBoss(e) ? 1f : EngineConfig.BOWL_MOMENTUM_LOSS));
			}
		}
		if (any) {
			sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
			sound(p, SoundEvents.ANVIL_LAND, 0.25f, 1.8f);
		}
		if (s.bowled.size() > 32) {
			s.bowled.values().removeIf(t -> now - t > EngineConfig.BOWL_REHIT);
		}
	}


	// ================================================================ V: Gear Up / Shift+V: Gear Down (the gearbox)

	/**
	 * V: into the next gear (the run keeps its speed; momentum builds the rest). At Gear 3 nothing more happens -- "Max
	 * gear" (v0.16.7: Recipro Burst / Extend are gone; Recipro Turbo, C, is the overdrive now). Returns the new gear, or -1
	 * if nothing changed.
	 */
	public static int shiftUp(ServerPlayer p) {
		Session s = session(p);
		Quirks.setCooldown(p, EngineConfig.SHIFT_UP, EngineConfig.SHIFT_COOLDOWN);
		if (s.gear >= EngineConfig.MAX_GEAR) {
			Quirks.say(p, "message.projecthero.quirk.engine.top_gear", ChatFormatting.AQUA);
			return -1;
		}
		changeGear(p, s, s.gear + 1);
		heat(p, EngineConfig.SHIFT_UP_HEAT);
		Quirks.setAnim(p, Engine.ANIM_SHIFT_UP);
		rev(p, s.gear);
		EngineCombat.exhaust(p.serverLevel(), p, 4 + 3 * s.gear, 2 + s.gear);
		return s.gear;
	}

	/**
	 * Shift+V: down a gear (engine braking keeps most of the momentum). In Recipro Turbo it can't drop below Gear 1.
	 * Returns the new gear, or -1 if nothing changed.
	 */
	public static int shiftDown(ServerPlayer p) {
		Session s = session(p);
		Quirks.setCooldown(p, EngineConfig.SHIFT_DOWN, EngineConfig.SHIFT_COOLDOWN);
		if (s.gear <= (running(p, Engine.V_TURBO) ? 1 : 0)) {
			return -1;
		}
		changeGear(p, s, s.gear - 1);
		s.momentum *= EngineConfig.DOWNSHIFT_KEEP;
		Quirks.setAnim(p, Engine.ANIM_SHIFT_DOWN);
		sound(p, SoundEvents.PISTON_CONTRACT, 0.7f, 0.7f + s.gear * 0.15f);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.35f, 1.6f);
		return s.gear;
	}

	/** Changes gear keeping the run's speed: the momentum is re-scaled so this gear's speed matches the last one's. */
	private static void changeGear(ServerPlayer p, Session s, int gear) {
		double was = runBps(s.gear, s.momentum, 1.0);
		s.gear = Mth.clamp(gear, 0, EngineConfig.MAX_GEAR);
		if (s.gear == 0) {
			s.momentum = 0f;
		} else {
			double top = EngineConfig.GEAR_TOP_BPS[s.gear];
			double f = (was / top - EngineConfig.MOMENTUM_FLOOR) / (1.0 - EngineConfig.MOMENTUM_FLOOR);
			s.momentum = (float) Mth.clamp(f, 0.0, 1.0);
		}
		Quirks.setVal(p, Engine.V_GEAR_NO, s.gear);
		s.momentumSynced = -1;
	}

	/** Test hook / harness: straight into a gear. */
	public static void setGear(ServerPlayer p, int gear) {
		changeGear(p, session(p), gear);
	}

	// ================================================================ C: Recipro Turbo (the mode)

	/**
	 * C: Recipro Turbo -- 30 s at double the gear's speed (into Gear 1 first if he's in neutral), heat frozen, every hit
	 * +50%. C again while it runs ends it early. Either way the engines then stall for a minute. Returns true if Turbo is
	 * now running.
	 */
	public static boolean turbo(ServerPlayer p) {
		Session s = session(p);
		if (running(p, Engine.V_TURBO)) {
			Quirks.setVal(p, Engine.V_TURBO, now(p)); // ends next tick -- into the stall
			return false;
		}
		if (s.gear == 0) {
			changeGear(p, s, 1);
		}
		s.dashAt = -1L;
		Quirks.setVal(p, Engine.V_TURBO, now(p) + EngineConfig.TURBO_TICKS);
		s.turboOn = true;
		Quirks.setAnim(p, Engine.ANIM_TURBO_ON);
		Quirks.setCooldown(p, EngineConfig.TURBO, EngineConfig.TURBO_COOLDOWN);
		Quirks.say(p, "message.projecthero.quirk.engine.turbo", ChatFormatting.AQUA);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 0.9, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y - 0.5, c.z, 1, 0, 0, 0, 0);
		reciproFlare(p, 26);
		rev(p, 5f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.6f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.5f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
		return true;
	}

	/** Recipro Turbo is over: the engines seize for a full minute (no gears, no moves, a slow walk, steam, sputtering). */
	private static void turboEnds(ServerPlayer p) {
		Quirks.setVal(p, Engine.V_TURBO, 0L);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, TURBO_DAMAGE_ID);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 20, 0.5, 0.15, 0.5, 0.06);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.5f);
		stall(p, EngineConfig.TURBO_STALL_TICKS);
	}

	/** The blue Recipro flames bursting out of the calves. */
	private static void reciproFlare(ServerPlayer p, int count) {
		ServerLevel level = p.serverLevel();
		EngineCombat.exhaust(level, p, count, 4);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 0.3, p.getZ(), count, 0.3, 0.1, 0.3, 0.08);
		level.sendParticles(EngineCombat.EXHAUST_WHITE, p.getX(), p.getY() + 0.3, p.getZ(), count, 0.35, 0.15, 0.35, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), count / 2, 0.5, 0.02, 0.5, 0.05);
	}

	// ================================================================ shared: straight-line paths, front hits

	/** How far he can go along {@code dir} (up to {@code max}) before a wall. */
	private static double clearPath(ServerPlayer p, Vec3 dir, double max) {
		double len = 0.0;
		AABB bb = p.getBoundingBox();
		for (double d = 0.5; d <= max; d += 0.5) {
			Vec3 off = dir.scale(d);
			if (!p.level().noCollision(p, bb.move(off)) && !p.level().noCollision(p, bb.move(off.add(0, 1.05, 0)))) {
				break;
			}
			len = d;
		}
		return len;
	}

	/** Every target in a cone in front of him ({@code minDot} of the flat look) within {@code reach} is struck. Returns how many. */
	private static int frontHits(ServerPlayer p, double reach, double minDot, float dmg, double kb, double lift) {
		Vec3 f = flatLook(p);
		Vec3 c = p.position().add(0, 0.8, 0);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(f.scale(reach * 0.4)), reach + 1.0)) {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			if (to.lengthSqr() > 0.04 && to.normalize().dot(f) < minDot) {
				continue;
			}
			if (Math.sqrt(AbilityHelpers.distanceSqToBox(e, c)) > reach) {
				continue;
			}
			if (EngineCombat.strike(p, e, p.position(), dmg, kb, lift)) {
				n++;
			}
		}
		return n;
	}

	/** His speed for the speed-scaled damage, blocks a second (capped: {@link EngineConfig#DAMAGE_BPS_CAP}). */
	private static double bps(Session s) {
		return Math.min(EngineConfig.DAMAGE_BPS_CAP, s.speed * 20.0);
	}

	// ================================================================ X: Recipro Dash (the one dash) / Shift+X: Rising Kick

	/**
	 * One burst dash, farther in higher gears -- v0.17.1 (user): exactly where he is aiming, up and down included. No damage (it's the dash), it just bumps mobs aside. In
	 * Recipro Turbo it is the Turbo Strike instead ({@link #strike}).
	 */
	public static void dash(ServerPlayer p) {
		if (running(p, Engine.V_TURBO)) {
			strike(p);
			return;
		}
		Session s = session(p);
		Vec3 dir = p.getLookAngle().normalize(); // v0.17.1 (user): where he aims, not just forward
		s.dashAt = now(p);
		s.dashFrom = p.position();
		s.dashDir = dir;
		s.dashLength = clearPath(p, dir, dashDistance(s.gear));
		Quirks.setVal(p, Engine.V_DASH, now(p) + EngineConfig.DASH_TICKS + 2);
		Quirks.setAnim(p, Engine.ANIM_DASH);
		Quirks.setCooldown(p, EngineConfig.DASH, EngineConfig.DASH_COOLDOWN);
		heat(p, EngineConfig.DASH_HEAT);
		reciproFlare(p, 10);
		rev(p, 2f + s.gear * 0.5f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.1f);
		dashStep(p, s, 0L);
	}

	/** How far Recipro Dash goes in a gear (before walls). */
	public static double dashDistance(int gear) {
		return EngineConfig.DASH_DISTANCE + EngineConfig.DASH_PER_GEAR * Mth.clamp(gear, 0, EngineConfig.MAX_GEAR);
	}

	/** Test hook: how far the last dash was planned to go. */
	public static double dashLength(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0.0 : s.dashLength;
	}

	private static void dashStep(ServerPlayer p, Session s, long k) {
		double step = dashDistance(s.gear) / EngineConfig.DASH_TICKS;
		ServerLevel level = p.serverLevel();
		if (k < EngineConfig.DASH_TICKS && k * step < s.dashLength) {
			AbilityHelpers.launchSelf(p, s.dashDir.scale(step)); // the whole burst follows the aim, vertical included
			p.resetFallDistance();
			EngineCombat.exhaust(level, p, 5, 4);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.3, 0.02, 0.3, 0.02);
			for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().expandTowards(s.dashDir.scale(step)).inflate(0.4),
					e -> e != p && e.isAlive() && HeroTargets.isHostile(p, e) && !EngineCombat.isBoss(e))) {
				Vec3 side = new Vec3(-s.dashDir.z, 0, s.dashDir.x);
				if (side.dot(e.position().subtract(p.position())) < 0) {
					side = side.scale(-1);
				}
				e.setDeltaMovement(e.getDeltaMovement().add(side.scale(0.6)).add(0, 0.2, 0));
				e.hurtMarked = true;
			}
		} else {
			// the dash ends: brake so he keeps a run-on, not a skate
			Vec3 keep = s.dashDir.scale(0.25 + 0.1 * s.gear);
			AbilityHelpers.launchSelf(p, new Vec3(keep.x, Math.min(keep.y, 0.3), keep.z));
			s.dashAt = -1L;
		}
	}

	/** An engine uppercut kick: the target in front is launched straight up; he hops after it. Returns the target, or null. */
	public static @Nullable LivingEntity risingKick(ServerPlayer p) {
		Session s = session(p);
		LivingEntity t = EngineCombat.aimed(p, EngineConfig.RISING_REACH, 35.0, EngineCombat::isTarget);
		Quirks.setAnim(p, Engine.ANIM_RISING);
		Quirks.setCooldown(p, EngineConfig.RISING, EngineConfig.RISING_COOLDOWN);
		heat(p, EngineConfig.RISING_HEAT);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().multiply(0.5, 0, 0.5).add(0, EngineConfig.RISING_SELF_HOP, 0));
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.2, 0.05, 0.2, 0.06);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
		if (t == null) {
			return null;
		}
		float dmg = (float) (EngineConfig.RISING_DAMAGE + EngineConfig.RISING_DAMAGE_PER_SPEED * bps(s));
		if (!EngineCombat.strike(p, t, p.position(), dmg, 0.2, 0.0)) {
			return null;
		}
		if (!EngineCombat.isBoss(t)) {
			t.setDeltaMovement(t.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, EngineConfig.RISING_LAUNCH, 0));
			t.hurtMarked = true;
			t.hasImpulse = true;
		}
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.3, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(EngineCombat.EXHAUST_WHITE, c.x, c.y, c.z, 10, 0.2, 0.6, 0.2, 0.0);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		return t;
	}

	// ================================================================ R: Recipro Kick / Shift+R: Spin Kick

	/** One engine-driven kick at what is in front of him: the faster he runs, the harder it lands. Returns how many it hit. */
	public static int kick(ServerPlayer p) {
		Session s = session(p);
		double bps = bps(s);
		float dmg = kickDamage(bps);
		double kb = EngineConfig.KICK_KNOCKBACK + EngineConfig.KICK_KNOCKBACK_PER_SPEED * bps;
		int n = frontHits(p, EngineConfig.KICK_REACH, 0.35, dmg, kb, 0.25 + Math.min(0.3, bps * 0.01));
		Quirks.setAnim(p, Engine.ANIM_KICK);
		Quirks.setCooldown(p, EngineConfig.KICK, EngineConfig.KICK_COOLDOWN);
		heat(p, EngineConfig.KICK_HEAT);
		Vec3 at = p.position().add(0, 0.8, 0).add(flatLook(p).scale(1.4));
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		EngineCombat.exhaust(level, p, 4 + (int) (bps / 4), 3);
		if (bps > 12) {
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
		sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.6f, 1.1f + (float) Math.min(0.6, bps * 0.02));
		return n;
	}

	/** A 360 kick: everything around him is kicked away. Returns how many were hit. */
	public static int spin(ServerPlayer p) {
		Session s = session(p);
		Quirks.setAnim(p, Engine.ANIM_SPIN);
		Quirks.setCooldown(p, EngineConfig.SPIN, EngineConfig.SPIN_COOLDOWN);
		heat(p, EngineConfig.SPIN_HEAT);
		return spinHit(p, (float) (EngineConfig.SPIN_DAMAGE + EngineConfig.SPIN_DAMAGE_PER_SPEED * bps(s)));
	}

	private static int spinHit(ServerPlayer p, float dmg) {
		Vec3 c = p.position().add(0, 0.7, 0);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, EngineConfig.SPIN_RADIUS + 1.0)) {
			if (Math.sqrt(AbilityHelpers.distanceSqToBox(e, c)) > EngineConfig.SPIN_RADIUS) {
				continue;
			}
			if (EngineCombat.strike(p, e, p.position(), dmg, EngineConfig.SPIN_KNOCKBACK, EngineConfig.SPIN_LIFT)) {
				n++;
			}
		}
		ServerLevel level = p.serverLevel();
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0;
			double x = p.getX() + Math.cos(a) * 1.6;
			double z = p.getZ() + Math.sin(a) * 1.6;
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, p.getY() + 0.5, z, 2, 0.05, 0.05, 0.05, 0.02);
			level.sendParticles(EngineCombat.EXHAUST, x, p.getY() + 0.5, z, 2, 0.1, 0.05, 0.1, 0.0);
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 0.7, p.getZ(), 3, 0.8, 0.1, 0.8, 0);
		rev(p, 1.5f);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
		return n;
	}

	// ================================================================ G: Kick Barrage / Shift+G: Recipro Extend Kick

	/** A rapid barrage of kicks (both legs, the engines firing each one) on what is in front of him. */
	public static void barrage(ServerPlayer p) {
		Session s = session(p);
		s.barrageAt = now(p);
		s.barrageHits = 0;
		Quirks.setVal(p, Engine.V_BARRAGE, now(p) + (long) EngineConfig.BARRAGE_HITS * EngineConfig.BARRAGE_INTERVAL + 2);
		Quirks.setAnim(p, Engine.ANIM_BARRAGE);
		Quirks.setCooldown(p, EngineConfig.BARRAGE, EngineConfig.BARRAGE_COOLDOWN);
		heat(p, EngineConfig.BARRAGE_HEAT);
		rev(p, 1.5f);
		barrageStep(p, s, now(p));
	}

	private static void barrageStep(ServerPlayer p, Session s, long now) {
		long k = now - s.barrageAt;
		if (k % EngineConfig.BARRAGE_INTERVAL != 0) {
			return;
		}
		s.barrageHits++;
		boolean last = s.barrageHits >= EngineConfig.BARRAGE_HITS;
		float dmg = (float) (EngineConfig.BARRAGE_DAMAGE + EngineConfig.BARRAGE_DAMAGE_PER_SPEED * bps(s));
		frontHits(p, EngineConfig.BARRAGE_REACH, 0.3, dmg, last ? EngineConfig.BARRAGE_FINAL_KNOCKBACK : EngineConfig.BARRAGE_KNOCKBACK, last ? 0.3 : 0.0);
		Vec3 f = flatLook(p);
		Vec3 at = p.position().add(0, 0.6 + 0.25 * (s.barrageHits % 3), 0).add(f.scale(1.3));
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.2);
		level.sendParticles(EngineCombat.EXHAUST, at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.0);
		EngineCombat.exhaust(level, p, 2, 3);
		sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1.2f + s.barrageHits * 0.06f);
		if (last) {
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.9f);
			s.barrageAt = -1L;
		}
	}

	/** The engines wind up (he slows down), then one heavy charged kick that blasts the target away. */
	public static void extendKick(ServerPlayer p) {
		Session s = session(p);
		s.extendKickAt = now(p);
		s.extendKickSpeed = bps(s);
		Quirks.setVal(p, Engine.V_EXTEND_KICK, now(p) + EngineConfig.EXTEND_KICK_CHARGE);
		Quirks.setAnim(p, Engine.ANIM_EXTEND_KICK);
		Quirks.setCooldown(p, EngineConfig.EXTEND_KICK, EngineConfig.EXTEND_KICK_COOLDOWN);
		heat(p, EngineConfig.EXTEND_KICK_HEAT);
		sound(p, SoundEvents.PISTON_EXTEND, 0.9f, 0.5f);
	}

	/** The charged kick's damage at a speed (blocks a second). */
	public static float extendKickDamage(double bps) {
		return (float) Math.min(EngineConfig.EXTEND_KICK_DAMAGE_MAX,
				EngineConfig.EXTEND_KICK_DAMAGE + EngineConfig.EXTEND_KICK_DAMAGE_PER_SPEED * Math.max(0.0, bps));
	}

	private static void extendKickStep(ServerPlayer p, Session s, long k) {
		ServerLevel level = p.serverLevel();
		if (k < EngineConfig.EXTEND_KICK_CHARGE) {
			// winding up: the engines scream, flames building at the calves
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.6, 1.0, 0.6));
			p.hurtMarked = true;
			EngineCombat.exhaust(level, p, 2 + (int) k / 2, 2);
			if (k % 3 == 0) {
				sound(p, SoundEvents.BLAZE_SHOOT, 0.4f, 1.0f + k * 0.08f);
			}
			return;
		}
		s.extendKickAt = -1L;
		Quirks.setVal(p, Engine.V_EXTEND_KICK, 0L);
		frontHits(p, EngineConfig.EXTEND_KICK_REACH, 0.4, extendKickDamage(s.extendKickSpeed), EngineConfig.EXTEND_KICK_KNOCKBACK,
				EngineConfig.EXTEND_KICK_LIFT);
		Vec3 at = p.position().add(0, 0.9, 0).add(flatLook(p).scale(1.6));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(EngineCombat.EXHAUST_WHITE, at.x, at.y, at.z, 12, 0.4, 0.4, 0.4, 0.0);
		reciproFlare(p, 10);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.7f);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
		AbilityHelpers.launchSelf(p, flatLook(p).scale(0.5).add(0, Math.min(0.0, p.getDeltaMovement().y), 0));
	}

	// ================================================================ Z: Axe Kick / Shift+Z: Sweep Kick

	/** The heel goes up -- and comes crashing down {@link EngineConfig#AXE_DELAY} ticks later on the target in front. */
	public static void axeKick(ServerPlayer p) {
		Session s = session(p);
		s.axeAt = now(p);
		Quirks.setAnim(p, Engine.ANIM_AXE);
		Quirks.setCooldown(p, EngineConfig.AXE, EngineConfig.AXE_COOLDOWN);
		heat(p, EngineConfig.AXE_HEAT);
		EngineCombat.exhaust(p.serverLevel(), p, 6, 3);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.6f, 1.4f);
	}

	/** The heel lands. Returns how many it hit (test hook: {@link #axeLandNow}). */
	private static int axeLand(ServerPlayer p, Session s) {
		LivingEntity t = EngineCombat.aimed(p, EngineConfig.AXE_REACH, 35.0, EngineCombat::isTarget);
		Vec3 c = t != null ? t.position() : p.position().add(flatLook(p).scale(1.6));
		float dmg = (float) (EngineConfig.AXE_DAMAGE + EngineConfig.AXE_DAMAGE_PER_SPEED * bps(s));
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, EngineConfig.AXE_RADIUS + 1.0)) {
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, c.add(0, 0.5, 0)));
			if (e != t && d > EngineConfig.AXE_RADIUS) {
				continue;
			}
			if (EngineCombat.strike(p, e, c, e == t ? dmg : dmg * 0.6f, 0.0, 0.0)) {
				n++;
				if (!EngineCombat.isBoss(e)) {
					e.setDeltaMovement(e.getDeltaMovement().multiply(0.4, 0, 0.4).add(0, -EngineConfig.AXE_SLAM, 0));
					e.hurtMarked = true;
				}
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, EngineConfig.AXE_SLOW_TICKS, 1);
			}
		}
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 14, 0.8, 0.05, 0.8, 0.06);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.5, c.z, 10, 0.4, 0.3, 0.4, 0.2);
		level.sendParticles(EngineCombat.EXHAUST, c.x, c.y + 0.3, c.z, 8, 0.5, 0.2, 0.5, 0.0);
		sound(p, SoundEvents.ANVIL_LAND, 0.5f, 0.7f);
		sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
		return n;
	}

	/** Test hook: the Axe Kick's heel lands now. */
	public static int axeLandNow(ServerPlayer p) {
		Session s = session(p);
		s.axeAt = -1L;
		return axeLand(p, s);
	}

	/** A low sweeping kick through the half circle in front: everything caught is tripped (lifted off its feet, slowed). */
	public static int sweepKick(ServerPlayer p) {
		Session s = session(p);
		float dmg = (float) (EngineConfig.SWEEP_DAMAGE + EngineConfig.SWEEP_DAMAGE_PER_SPEED * bps(s));
		Vec3 f = flatLook(p);
		Vec3 c = p.position().add(0, 0.3, 0);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, EngineConfig.SWEEP_RADIUS + 1.0)) {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			if (to.lengthSqr() > 0.04 && to.normalize().dot(f) < -0.05) {
				continue;
			}
			if (Math.sqrt(AbilityHelpers.distanceSqToBox(e, c)) > EngineConfig.SWEEP_RADIUS) {
				continue;
			}
			if (EngineCombat.strike(p, e, p.position(), dmg, EngineConfig.SWEEP_KNOCKBACK, EngineConfig.SWEEP_LIFT)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, EngineConfig.SWEEP_SLOW_TICKS, 1);
				n++;
			}
		}
		Quirks.setAnim(p, Engine.ANIM_SWEEP);
		Quirks.setCooldown(p, EngineConfig.SWEEP, EngineConfig.SWEEP_COOLDOWN);
		heat(p, EngineConfig.SWEEP_HEAT);
		ServerLevel level = p.serverLevel();
		for (int i = -4; i <= 4; i++) {
			double a = Math.atan2(f.z, f.x) + i * Math.PI / 9.0;
			double x = p.getX() + Math.cos(a) * 1.8, z = p.getZ() + Math.sin(a) * 1.8;
			level.sendParticles(ParticleTypes.CLOUD, x, p.getY() + 0.15, z, 1, 0.05, 0.02, 0.05, 0.02);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, p.getY() + 0.25, z, 1, 0.05, 0.02, 0.05, 0.01);
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
		sound(p, SoundEvents.SAND_BREAK, 0.8f, 0.8f);
		return n;
	}

	// ================================================================ X in Recipro Turbo: the Turbo Strike / Shift+C: Ingenium Finisher

	/** X in Turbo: a blinding straight-line rush along the look; everything in the path is struck (+50%, it's Turbo). */
	public static void strike(ServerPlayer p) {
		Session s = session(p);
		s.dashAt = -1L;
		Vec3 dir = flatLook(p);
		s.strikeAt = now(p);
		s.strikeFrom = p.position();
		s.strikeDir = dir;
		s.strikeLength = clearPath(p, dir, EngineConfig.STRIKE_DISTANCE);
		s.strikeHit.clear();
		Quirks.setVal(p, Engine.V_STRIKE, now(p) + EngineConfig.STRIKE_TICKS + 3);
		Quirks.setAnim(p, Engine.ANIM_STRIKE);
		Quirks.setCooldown(p, EngineConfig.DASH, EngineConfig.STRIKE_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 0.9, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		reciproFlare(p, 26);
		rev(p, 5f);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.4f, 1.5f);
		sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.2f, 1.2f);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.8f);
		strikeStep(p, s, 0L);
	}

	private static void strikeStep(ServerPlayer p, Session s, long k) {
		double step = EngineConfig.STRIKE_DISTANCE / EngineConfig.STRIKE_TICKS;
		double a = Math.min(s.strikeLength, Math.max(0.0, (k - 1) * step));
		double b = Math.min(s.strikeLength, (k + 1) * step);
		Vec3 from = s.strikeFrom.add(s.strikeDir.scale(a));
		Vec3 to = s.strikeFrom.add(s.strikeDir.scale(b));
		EngineCombat.alongPath(p, from, to, EngineConfig.STRIKE_WIDTH, EngineConfig.STRIKE_DAMAGE, EngineConfig.STRIKE_KNOCKBACK, EngineConfig.STRIKE_LIFT,
				s.strikeHit, EngineCombat::isTarget);
		ServerLevel level = p.serverLevel();
		// the afterimage: a white-blue streak down the path
		for (double d = a; d <= b; d += 0.6) {
			Vec3 at = s.strikeFrom.add(s.strikeDir.scale(d)).add(0, 0.9, 0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.15, 0.4, 0.15, 0.0);
			level.sendParticles(EngineCombat.EXHAUST_WHITE, at.x, at.y - 0.4, at.z, 2, 0.15, 0.3, 0.15, 0.0);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y - 0.6, at.z, 1, 0.1, 0.05, 0.1, 0.01);
		}
		if (k < EngineConfig.STRIKE_TICKS && k * step < s.strikeLength) {
			AbilityHelpers.launchSelf(p, new Vec3(s.strikeDir.x * step, Math.min(0.0, p.getDeltaMovement().y), s.strikeDir.z * step));
			p.resetFallDistance();
		} else {
			// the strike ends: he keeps a fast run-on (Turbo is still running)
			AbilityHelpers.launchSelf(p, new Vec3(s.strikeDir.x * 0.8, Math.min(0.0, p.getDeltaMovement().y), s.strikeDir.z * 0.8));
			s.strikeAt = -1L;
			Vec3 c = p.position().add(0, 0.9, 0);
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y - 0.8, c.z, 16, 0.6, 0.1, 0.6, 0.08);
			Quirks.setVal(p, Engine.V_STRIKE, 0L);
		}
	}

	/** Test hook: everything the running (or last) Turbo Strike has hit. */
	public static Set<Integer> strikeHit(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? Set.of() : Set.copyOf(s.strikeHit);
	}

	/** The finishing combo: a Recipro Kick, a spin kick, then an afterburner kick that launches the target far. */
	public static void finisher(ServerPlayer p) {
		Session s = session(p);
		s.finisherAt = now(p);
		s.finisherStep = 0;
		Quirks.setVal(p, Engine.V_FINISHER, now(p) + EngineConfig.FINISHER_LAUNCH_AT + 4);
		Quirks.setAnim(p, Engine.ANIM_FINISHER);
		Quirks.setCooldown(p, EngineConfig.FINISHER, EngineConfig.FINISHER_COOLDOWN);
		heat(p, EngineConfig.FINISHER_HEAT);
		finisherStep(p, s, 0L);
	}

	private static void finisherStep(ServerPlayer p, Session s, long k) {
		double bonus = EngineConfig.FINISHER_DAMAGE_PER_SPEED * bps(s);
		ServerLevel level = p.serverLevel();
		if (s.finisherStep == 0) {
			s.finisherStep = 1;
			frontHits(p, EngineConfig.KICK_REACH, 0.35, (float) (EngineConfig.FINISHER_KICK_DAMAGE + bonus * 0.5), 0.3, 0.1);
			Vec3 at = p.position().add(0, 0.9, 0).add(flatLook(p).scale(1.3));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.1f);
			EngineCombat.exhaust(level, p, 4, 3);
		} else if (s.finisherStep == 1 && k >= EngineConfig.FINISHER_SPIN_AT) {
			s.finisherStep = 2;
			spinHit(p, (float) (EngineConfig.FINISHER_SPIN_DAMAGE + bonus * 0.5));
		} else if (s.finisherStep == 2 && k >= EngineConfig.FINISHER_LAUNCH_AT) {
			s.finisherStep = 3;
			s.finisherAt = -1L;
			LivingEntity t = EngineCombat.aimed(p, EngineConfig.FINISHER_REACH, 40.0, EngineCombat::isTarget);
			reciproFlare(p, 12);
			sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.8f);
			if (t != null && EngineCombat.strike(p, t, p.position(), (float) (EngineConfig.FINISHER_LAUNCH_DAMAGE + bonus), 0.0, 0.0)) {
				if (!EngineCombat.isBoss(t)) {
					t.setDeltaMovement(flatLook(p).scale(EngineConfig.FINISHER_LAUNCH).add(0, EngineConfig.FINISHER_LIFT, 0));
					t.hurtMarked = true;
					t.hasImpulse = true;
				}
				Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.08);
				sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
			}
		}
	}

	static void steam(ServerLevel level, ServerPlayer p, int count) {
		for (int side = -1; side <= 1; side += 2) {
			Vec3 at = EngineCombat.calf(p, side);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, Math.max(1, count / 2), 0.08, 0.1, 0.08, 0.03);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y + 0.2, at.z, count, 0.15, 0.2, 0.15, 0.02);
		}
	}

	private static void syncPassengers(ServerPlayer p) {
		// vanilla never tells a player about passengers on themselves (see SuperSpeedHandlers.syncPassengersToSelf)
		if (p.connection != null) {
			p.connection.send(new ClientboundSetPassengersPacket(p));
		}
	}

	// ================================================================ the end of everything

	/** Every running move ends, the gearbox drops to 0, the attribute modifiers come off. */
	static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.gear = 0;
			s.momentum = 0f;
			s.momentumSynced = -1;
			s.turboOn = false;
			s.dashAt = -1L;
			s.strikeAt = -1L;
			s.axeAt = -1L;
			s.barrageAt = -1L;
			s.finisherAt = -1L;
			s.extendKickAt = -1L;
		}
		QuirkState st = Quirks.state(p);
		String[] keys = { Engine.V_TURBO, Engine.V_STRIKE, Engine.V_DASH, Engine.V_EXTEND_KICK, Engine.V_BARRAGE, Engine.V_FINISHER,
				Engine.V_GEAR_NO, Engine.V_MOMENTUM };
		boolean any = false;
		for (String k : keys) {
			any |= st.val(k) != 0L;
		}
		if (any) {
			Quirks.edit(p, n -> {
				for (String k : keys) {
					n.vals.remove(k);
				}
			});
		}
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, GEAR_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, LEGACY_PASSIVE_ID);
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, STEP_ID);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, TURBO_DAMAGE_ID);
	}

	/** The holder still has Engine (stopAll leaves the power; revoke clears everything). */
	static boolean holds(ServerPlayer p) {
		return Quirks.holds(p, Quirk.ENGINE);
	}
}
