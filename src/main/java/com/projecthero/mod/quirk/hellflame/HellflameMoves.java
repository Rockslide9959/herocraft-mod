package com.projecthero.mod.quirk.hellflame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Hellflame's twelve moves, the Heat meter and Overheat, and the per-player sessions of the moves that run over
 * time (the beam, the jets, the Prominence charge, the Flashfire dash, the Spider Net, the Sun and its fireballs, the
 * Cloak). {@link HellflameKit#handle} calls the public moves once their cooldown is ready; the gametests call them
 * directly. Sessions hold entity ids, never entities, and are dropped by {@link #forget} / {@link #clearSessionState}.
 *
 * <p><b>Heat</b> rises with every fire move ({@link #heat}); once no fire has burned for {@link HellflameConfig#VENT_DELAY}
 * it vents 4 / s (2.5x in water or rain). At 100: <b>Overheat</b> ({@link #overheat}).
 */
public final class HellflameMoves {
	/** A Hellflame Sun fireball falling from {@code from} onto entity {@code target}. */
	private record Fall(Vec3 from, int target, long at) {
	}

	static final class Session {
		long lastFireAt = Long.MIN_VALUE / 2;
		// the beam
		long beamAt;
		boolean beam;
		// the jets
		boolean boost;
		boolean floating;
		// the Prominence charge
		boolean charge;
		long chargeAt;
		// the Flashfire dash
		long dashUntil;
		boolean uppercut;
		Vec3 dashDir = Vec3.ZERO;
		// the Spider Net
		long netUntil;
		Vec3 netCenter = Vec3.ZERO;
		final Set<Integer> netted = new HashSet<>();
		// the Sun
		long sunUntil;
		final List<Fall> falls = new ArrayList<>();
		// the Cloak
		boolean cloak;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private HellflameMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	/** Where the flames leave his hand. */
	private static Vec3 hand(ServerPlayer p) {
		return AbilityHelpers.handPosition(p);
	}

	/** The first block on the way {@code from} + {@code dir} * {@code range}, else the full length. */
	private static Vec3 clip(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	// ================================================================ Heat / Overheat

	/** Adds Heat from a fire move (venting pauses); Overheat at 100. Returns the new Heat. */
	public static float heat(ServerPlayer p, float amount) {
		session(p).lastFireAt = now(p);
		float h = Quirks.addMeter(p, amount);
		if (h >= 100f && !Quirks.overloaded(p)) {
			overheat(p);
		}
		return h;
	}

	/** 100 Heat: every move ends, Slowness II, a short self-burn, no fire moves for 8 s (Shift+C Vent still works). */
	public static void overheat(ServerPlayer p) {
		boolean airborne = !p.onGround() && boosting(p);
		Quirks.overload(p, HellflameConfig.OVERHEAT_TICKS); // runs stopAll
		Quirks.setMeter(p, 100f);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HellflameConfig.OVERHEAT_TICKS, 1, false, true, true));
		if (airborne || !p.onGround()) {
			// the jets cut out mid-air: he drifts down instead of dropping like a stone
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
		}
		p.igniteForSeconds(HellflameConfig.OVERHEAT_SELF_BURN_SECONDS);
		Quirks.setAnim(p, Hellflame.ANIM_OVERHEAT);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.4, 0.7, 0.4, 0.04);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 8, 0.3, 0.5, 0.3, 0.0);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.6f);
		sound(p, SoundEvents.LAVA_EXTINGUISH, 0.8f, 0.8f);
		Quirks.say(p, "message.projecthero.quirk.overloaded.hellflame", ChatFormatting.RED);
	}

	private static boolean wet(ServerPlayer p) {
		return p.isInWaterOrRain();
	}

	// ================================================================ R: Hell Spider / Shift+R: Hell Spider Net

	/** Thin flame threads fanned out along the look, {@link HellflameConfig#SPIDER_DAMAGE} to everything they cross. */
	public static int hellSpider(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 from = hand(p);
		Set<Integer> hit = new HashSet<>();
		int n = 0;
		int threads = HellflameConfig.SPIDER_THREADS;
		for (int i = 0; i < threads; i++) {
			double off = (i - (threads - 1) / 2.0) * HellflameConfig.SPIDER_SPREAD_DEGREES;
			// alternate threads tilt a little up and down: a fan, not a flat sheet
			float pitchOff = (i % 2 == 0 ? -1.5f : 1.5f) * (i == threads / 2 ? 0f : 1f);
			Vec3 dir = Vec3.directionFromRotation(p.getXRot() + pitchOff, (float) (p.getYRot() + off));
			Vec3 end = clip(p, from, dir, HellflameConfig.SPIDER_RANGE);
			HellflameCombat.streak(level, from, end, 2.5, true);
			for (LivingEntity e : HellflameCombat.alongPath(p, from, end, HellflameConfig.SPIDER_THREAD_RADIUS, hit)) {
				if (HellflameCombat.strike(p, e, from, HellflameConfig.SPIDER_DAMAGE, HellflameConfig.SPIDER_BURN_SECONDS, 0.3, 0.0, true)) {
					n++;
				}
			}
			level.sendParticles(ParticleTypes.SMALL_FLAME, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.02);
		}
		Quirks.setAnim(p, Hellflame.ANIM_SPIDER);
		Quirks.setCooldown(p, HellflameConfig.SPIDER, HellflameConfig.SPIDER_COOLDOWN);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 1.6f);
		sound(p, SoundEvents.FIRECHARGE_USE, 0.7f, 1.8f);
		heat(p, HellflameConfig.SPIDER_HEAT);
		return n;
	}

	/** Threads woven into a web round the aimed point: everything inside is rooted 3 s and burns. */
	public static int spiderNet(ServerPlayer p) {
		Session s = session(p);
		LivingEntity lock = HellflameCombat.aimed(p, HellflameConfig.NET_RANGE, 8.0);
		Vec3 center = lock != null ? lock.position() : AbilityHelpers.aimPoint(p, HellflameConfig.NET_RANGE);
		s.netCenter = center;
		s.netUntil = now(p) + HellflameConfig.NET_ROOT_TICKS;
		s.netted.clear();
		for (LivingEntity e : HellflameCombat.around(p, center.add(0, 1.0, 0), HellflameConfig.NET_RADIUS)) {
			s.netted.add(e.getId());
			if (!HellflameCombat.isBoss(e)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, HellflameConfig.NET_ROOT_TICKS, 9);
			}
			HellflameCombat.strike(p, e, center, HellflameConfig.NET_BURN_DAMAGE, 3, 0.0, 0.0, true);
		}
		// the threads shoot out from the hand to the web
		ServerLevel level = p.serverLevel();
		Vec3 from = hand(p);
		for (int i = 0; i < 6; i++) {
			double a = i / 6.0 * Math.PI * 2.0;
			Vec3 rim = center.add(Math.cos(a) * HellflameConfig.NET_RADIUS, 0.6, Math.sin(a) * HellflameConfig.NET_RADIUS);
			HellflameCombat.streak(level, from, rim, 1.2, false);
		}
		netFx(level, s);
		Quirks.setAnim(p, Hellflame.ANIM_NET);
		Quirks.setCooldown(p, HellflameConfig.NET, HellflameConfig.NET_COOLDOWN);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 1.2f);
		soundAt(level, center, SoundEvents.FIRECHARGE_USE, 1.0f, 0.9f);
		heat(p, HellflameConfig.NET_HEAT);
		return s.netted.size();
	}

	private static void netFx(ServerLevel level, Session s) {
		Vec3 c = s.netCenter.add(0, 0.15, 0);
		double r = HellflameConfig.NET_RADIUS;
		for (int i = 0; i < 8; i++) {
			double a = i / 8.0 * Math.PI * 2.0;
			Vec3 rim = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
			HellflameCombat.streak(level, c, rim, 1.6, (i & 1) == 0);
		}
		for (double rr : new double[] { r * 0.5, r }) {
			int pts = (int) (rr * 7);
			for (int i = 0; i < pts; i++) {
				double a = i / (double) pts * Math.PI * 2.0;
				level.sendParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * rr, c.y + 0.1, c.z + Math.sin(a) * rr, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}

	private static void netTick(ServerPlayer p, Session s, long now) {
		if (s.netUntil == 0L) {
			return;
		}
		if (now >= s.netUntil) {
			s.netUntil = 0L;
			s.netted.clear();
			return;
		}
		ServerLevel level = p.serverLevel();
		boolean burn = (s.netUntil - now) % HellflameConfig.NET_BURN_INTERVAL == 0;
		for (Iterator<Integer> it = s.netted.iterator(); it.hasNext();) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			if (!HellflameCombat.isBoss(le)) {
				// rooted: no walking, no jumping out of it
				Vec3 v = le.getDeltaMovement();
				le.setDeltaMovement(0, Math.min(0.0, v.y), 0);
				le.hurtMarked = true;
				if (le instanceof Mob m) {
					m.getNavigation().stop();
				}
			}
			if (burn) {
				HellflameCombat.strike(p, le, s.netCenter, HellflameConfig.NET_BURN_DAMAGE, 2, 0.0, 0.0, true);
			}
		}
		if ((s.netUntil - now) % 5 == 0) {
			netFx(level, s);
		}
	}

	/** Test read: entities in the running Spider Net. */
	public static Set<Integer> netted(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || s.netUntil == 0L ? Set.of() : Set.copyOf(s.netted);
	}

	// ================================================================ G: Jet Burn / Shift+G: Hellflame Beam

	/** A short flame blast in a cone: {@link HellflameConfig#JET_DAMAGE}, set alight, pushed back. */
	public static int jetBurn(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double cos = Math.cos(Math.toRadians(HellflameConfig.JET_HALF_ANGLE));
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, eye, HellflameConfig.JET_RANGE + 1.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > HellflameConfig.JET_RANGE + e.getBbWidth() * 0.5 || (d > 1.0 && to.scale(1.0 / d).dot(look) < cos)) {
				continue;
			}
			if (HellflameCombat.strike(p, e, p.position(), HellflameConfig.JET_DAMAGE, HellflameConfig.JET_BURN_SECONDS, HellflameConfig.JET_KNOCKBACK, 0.1, true)) {
				n++;
			}
		}
		spray(level, hand(p), look, 50, 0.32, 0.75);
		Quirks.setAnim(p, Hellflame.ANIM_JET);
		Quirks.setCooldown(p, HellflameConfig.JET, HellflameConfig.JET_COOLDOWN);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.9f);
		sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 1.1f);
		heat(p, HellflameConfig.JET_HEAT);
		return n;
	}

	/** {@code count} flame particles flying out along {@code dir} with {@code spread} (radians-ish) at {@code speed}. */
	static void spray(ServerLevel level, Vec3 from, Vec3 dir, int count, double spread, double speed) {
		var rnd = level.getRandom();
		for (int i = 0; i < count; i++) {
			Vec3 d = dir.add((rnd.nextDouble() - 0.5) * spread * 2, (rnd.nextDouble() - 0.5) * spread * 2, (rnd.nextDouble() - 0.5) * spread * 2).normalize();
			double sp = speed * (0.55 + rnd.nextDouble() * 0.6);
			ParticleOptions type = i % 5 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
			level.sendParticles(type, from.x, from.y, from.z, 0, d.x, d.y, d.z, sp);
		}
	}

	/** Hold: a concentrated flame beam along the look (drawn client side); damage {@link #beamTick}. */
	public static boolean startBeam(ServerPlayer p) {
		Session s = session(p);
		if (s.beam) {
			return false;
		}
		s.beam = true;
		s.beamAt = now(p);
		Quirks.setVal(p, Hellflame.V_BEAM, now(p));
		Quirks.setAnim(p, Hellflame.ANIM_BEAM);
		sound(p, SoundEvents.GHAST_SHOOT, 0.9f, 0.6f);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f);
		session(p).lastFireAt = now(p);
		return true;
	}

	public static void endBeam(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.beam) {
			return;
		}
		s.beam = false;
		Quirks.setVal(p, Hellflame.V_BEAM, 0L);
		Quirks.setCooldown(p, HellflameConfig.BEAM, HellflameConfig.BEAM_COOLDOWN);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.4f);
	}

	/** The beam's segment now: from the hand to the first block (or full range). */
	public static Vec3[] beamSegment(ServerPlayer p) {
		Vec3 from = hand(p);
		return new Vec3[] { from, clip(p, from, p.getLookAngle(), HellflameConfig.BEAM_RANGE) };
	}

	/** One beam tick; returns how many were burned this tick. */
	static int beamTick(ServerPlayer p, Session s, long now) {
		long age = now - s.beamAt;
		if (age >= HellflameConfig.BEAM_MAX_TICKS) {
			endBeam(p);
			return 0;
		}
		int n = 0;
		Vec3[] seg = beamSegment(p);
		ServerLevel level = p.serverLevel();
		if (age % HellflameConfig.BEAM_HIT_INTERVAL == 0) {
			for (LivingEntity e : HellflameCombat.alongPath(p, seg[0], seg[1], HellflameConfig.BEAM_RADIUS, null)) {
				if (HellflameCombat.strike(p, e, seg[0], HellflameConfig.BEAM_DAMAGE, 3, 0.15, 0.0, true)) {
					n++;
				}
			}
		}
		if (age % 2 == 0) {
			HellflameCombat.flare(level, seg[1], 6, 0.3);
		}
		if (age % 10 == 0) {
			sound(p, SoundEvents.BLAZE_BURN, 0.9f, 0.7f);
			soundAt(level, seg[1], SoundEvents.FIRE_AMBIENT, 1.0f, 1.0f);
		}
		heat(p, HellflameConfig.BEAM_HEAT_PER_TICK);
		return n;
	}

	/** Test hook: one beam tick now. */
	public static int beamTickForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || !s.beam ? 0 : beamTick(p, s, now(p));
	}

	// ================================================================ X: Flame Boost / Shift+X: Burst Dodge

	/** Hold: jets from the feet -- flight (the owning client moves him, {@code HellflameFlightClient}). */
	public static boolean startBoost(ServerPlayer p) {
		Session s = session(p);
		if (s.boost) {
			return false;
		}
		s.boost = true;
		Quirks.setVal(p, Hellflame.V_BOOST, now(p));
		Quirks.setAnim(p, Hellflame.ANIM_BOOST);
		floatTolerance(p, s, true);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.55, 0));
		}
		ServerLevel level = p.serverLevel();
		spray(level, p.position(), new Vec3(0, -1, 0), 30, 0.35, 0.4);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY(), p.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.7f, 0.6f);
		s.lastFireAt = now(p);
		return true;
	}

	public static void endBoost(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.boost) {
			return;
		}
		s.boost = false;
		Quirks.setVal(p, Hellflame.V_BOOST, 0L);
		Quirks.setCooldown(p, HellflameConfig.BOOST, HellflameConfig.BOOST_COOLDOWN);
		floatTolerance(p, s, false);
		p.resetFallDistance();
	}

	public static boolean boosting(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.boost;
	}

	private static void boostTick(ServerPlayer p, Session s, long now) {
		floatTolerance(p, s, true);
		p.resetFallDistance();
		if (p.getAbilities().flying && !p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		if (now % 8 == 0) {
			sound(p, SoundEvents.BLAZE_BURN, 0.5f, 0.8f + p.getRandom().nextFloat() * 0.2f);
		}
		heat(p, HellflameConfig.BOOST_HEAT_PER_TICK);
	}

	/**
	 * Vanilla kicks a player who floats with no downward motion ("flying is not enabled"); the jets look just like that.
	 * {@code mayfly} for the duration, {@code flying} kept off, handed back after unless another power still flies him.
	 */
	private static void floatTolerance(ServerPlayer p, Session s, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
				s.floating = true;
			}
			return;
		}
		if (s.floating) {
			s.floating = false;
			if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p) && !com.projecthero.mod.power.ThorPowers.isFlying(p)
					&& !p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false) && !p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
		}
	}

	/** A flame blast throws him ~8 blocks the way he is moving (standing still: backwards); it scorches what is next to him. */
	public static Vec3 burstDodge(ServerPlayer p) {
		Vec3 moved = new Vec3(p.getX() - p.xo, 0, p.getZ() - p.zo);
		Vec3 dir = moved.lengthSqr() > 0.0025 ? moved.normalize() : flatLook(p).scale(-1.0);
		Vec3 origin = p.position();
		Vec3 v = AbilityHelpers.ballisticLaunch(dir, HellflameConfig.DODGE_DISTANCE, p.onGround());
		AbilityHelpers.launchSelf(p, v);
		ServerLevel level = p.serverLevel();
		for (LivingEntity e : HellflameCombat.around(p, origin.add(0, 0.9, 0), HellflameConfig.DODGE_RADIUS)) {
			HellflameCombat.strike(p, e, origin, HellflameConfig.DODGE_DAMAGE, 3, 0.6, 0.1, true);
		}
		HellflameCombat.flare(level, origin.add(0, 0.6, 0), 20, 0.5);
		spray(level, origin.add(0, 0.5, 0), dir.scale(-1).add(0, -0.2, 0), 30, 0.4, 0.5);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y + 0.3, origin.z, 8, 0.4, 0.1, 0.4, 0.02);
		Quirks.setAnim(p, Hellflame.ANIM_DODGE);
		Quirks.setCooldown(p, HellflameConfig.DODGE, HellflameConfig.DODGE_COOLDOWN);
		sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
		sound(p, SoundEvents.FIRECHARGE_USE, 0.9f, 1.0f);
		heat(p, HellflameConfig.DODGE_HEAT);
		return v;
	}

	// ================================================================ Z: Flashfire Fist / Shift+Z: Flashfire Uppercut

	/** He rockets forward on flame; the first creature in reach takes the burning punch (the uppercut launches it). */
	public static void flashfire(ServerPlayer p, boolean uppercut) {
		Session s = session(p);
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.5, 0.5), look.z).normalize();
		s.dashDir = dir;
		s.uppercut = uppercut;
		s.dashUntil = now(p) + HellflameConfig.FIST_DASH_TICKS;
		Quirks.setVal(p, Hellflame.V_DASH, s.dashUntil);
		Quirks.setAnim(p, uppercut ? Hellflame.ANIM_UPPERCUT : Hellflame.ANIM_FIST);
		Quirks.setCooldown(p, uppercut ? HellflameConfig.UPPERCUT : HellflameConfig.FIST, uppercut ? HellflameConfig.UPPERCUT_COOLDOWN : HellflameConfig.FIST_COOLDOWN);
		ServerLevel level = p.serverLevel();
		spray(level, p.position().add(0, 0.8, 0), dir.scale(-1), 30, 0.3, 0.6);
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
		sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 0.8f);
		heat(p, uppercut ? HellflameConfig.UPPERCUT_HEAT : HellflameConfig.FIST_HEAT);
		if (s.dashUntil == 0L) {
			return; // overheated on the spot
		}
		if (!dashHit(p, s)) {
			AbilityHelpers.launchSelf(p, dir.scale(HellflameConfig.FIST_DASH_SPEED));
		}
	}

	/** Test read: the dash is still running. */
	public static boolean dashing(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.dashUntil > now(p);
	}

	/** Punch whatever is in reach now; true if something was (the dash ends). */
	private static boolean dashHit(ServerPlayer p, Session s) {
		Vec3 fist = p.position().add(0, 1.0, 0).add(s.dashDir.scale(0.8));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : HellflameCombat.around(p, fist, HellflameConfig.FIST_REACH)) {
			double d = AbilityHelpers.distanceSqToBox(e, fist);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			return false;
		}
		punch(p, s, best);
		return true;
	}

	private static void punch(ServerPlayer p, Session s, LivingEntity target) {
		ServerLevel level = p.serverLevel();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		if (s.uppercut) {
			HellflameCombat.strike(p, target, p.position(), HellflameConfig.UPPERCUT_DAMAGE, HellflameConfig.FIST_BURN_SECONDS, 0.4,
					HellflameConfig.UPPERCUT_LIFT, true);
			spray(level, c.add(0, -0.4, 0), new Vec3(0, 1, 0), 40, 0.25, 0.8);
			AbilityHelpers.launchSelf(p, new Vec3(0, 0.45, 0));
		} else {
			HellflameCombat.strike(p, target, p.position(), HellflameConfig.FIST_DAMAGE, HellflameConfig.FIST_BURN_SECONDS, HellflameConfig.FIST_KNOCKBACK, 0.25,
					true);
			spray(level, c, s.dashDir, 40, 0.3, 0.8);
			AbilityHelpers.launchSelf(p, s.dashDir.scale(0.15));
		}
		HellflameCombat.flare(level, c, 24, 0.5);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		soundAt(level, c, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 0.6f);
		soundAt(level, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		soundAt(level, c, SoundEvents.FIRECHARGE_USE, 0.8f, 0.8f);
		s.dashUntil = 0L;
		Quirks.setVal(p, Hellflame.V_DASH, 0L);
		p.resetFallDistance();
	}

	private static void dashTick(ServerPlayer p, Session s, long now) {
		if (s.dashUntil == 0L) {
			return;
		}
		if (now >= s.dashUntil) {
			s.dashUntil = 0L;
			Quirks.setVal(p, Hellflame.V_DASH, 0L);
			p.resetFallDistance();
			return;
		}
		p.resetFallDistance();
		ServerLevel level = p.serverLevel();
		Vec3 back = p.position().add(0, 0.9, 0).subtract(s.dashDir.scale(0.6));
		level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 8, 0.2, 0.3, 0.2, 0.02);
		level.sendParticles(HellflameCombat.ORANGE, back.x, back.y, back.z, 4, 0.25, 0.3, 0.25, 0.0);
		if (!dashHit(p, s)) {
			AbilityHelpers.launchSelf(p, s.dashDir.scale(HellflameConfig.FIST_DASH_SPEED));
		}
	}

	// ================================================================ V: Prominence Burn / Shift+V: Hellflame Sun

	/** Hold: the whole body gathers flame; release ({@link #releaseProminence}) for the column. */
	public static boolean startCharge(ServerPlayer p) {
		Session s = session(p);
		if (s.charge) {
			return false;
		}
		s.charge = true;
		s.chargeAt = now(p);
		s.lastFireAt = now(p);
		Quirks.setVal(p, Hellflame.V_CHARGE, now(p));
		Quirks.setAnim(p, Hellflame.ANIM_CHARGE);
		sound(p, SoundEvents.BLAZE_AMBIENT, 1.0f, 0.5f);
		sound(p, SoundEvents.FIRECHARGE_USE, 0.6f, 0.5f);
		return true;
	}

	/** Prominence charge 0..1 right now (0 if not charging). */
	public static float chargeNow(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.charge) {
			return 0f;
		}
		return Mth.clamp((now(p) - s.chargeAt) / (float) HellflameConfig.PROMINENCE_CHARGE_TICKS, 0f, 1f);
	}

	/** Releases the charge as a flame column around him; returns how many were hit (-1 if not charging). */
	public static int releaseProminence(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !s.charge) {
			return -1;
		}
		return prominence(p, chargeNow(p));
	}

	/** The column itself at charge {@code c} (0..1). */
	public static int prominence(ServerPlayer p, float c) {
		Session s = session(p);
		s.charge = false;
		Quirks.setVal(p, Hellflame.V_CHARGE, 0L);
		double r = HellflameConfig.PROMINENCE_RADIUS + HellflameConfig.PROMINENCE_RADIUS_EXTRA * c;
		float dmg = HellflameConfig.PROMINENCE_DAMAGE + HellflameConfig.PROMINENCE_DAMAGE_EXTRA * c;
		ServerLevel level = p.serverLevel();
		Vec3 base = p.position();
		AABB column = new AABB(base.x - r, base.y - 1.0, base.z - r, base.x + r, base.y + HellflameConfig.PROMINENCE_HEIGHT, base.z + r);
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, column, e -> e != p && e.isAlive() && HellflameCombat.isTarget(p, e))) {
			double dx = e.getX() - base.x;
			double dz = e.getZ() - base.z;
			if (Math.sqrt(dx * dx + dz * dz) > r + e.getBbWidth() * 0.5) {
				continue;
			}
			if (HellflameCombat.strike(p, e, base, dmg, HellflameConfig.PROMINENCE_BURN_SECONDS, 0.8 + c, HellflameConfig.PROMINENCE_LIFT * (0.6 + 0.4 * c), true)) {
				n++;
			}
		}
		// the column: rings of flame rising round him, the white-hot core up the middle
		for (int y = 0; y <= (int) HellflameConfig.PROMINENCE_HEIGHT; y++) {
			double rr = r * (0.55 + 0.45 * (1.0 - y / HellflameConfig.PROMINENCE_HEIGHT));
			int pts = 10 + (int) (rr * 3);
			for (int i = 0; i < pts; i++) {
				double a = i / (double) pts * Math.PI * 2.0 + y * 0.35;
				level.sendParticles(ParticleTypes.FLAME, base.x + Math.cos(a) * rr, base.y + y, base.z + Math.sin(a) * rr, 0, -Math.sin(a) * 0.2, 0.35,
						Math.cos(a) * 0.2, 0.5);
			}
			level.sendParticles(HellflameCombat.WHITE_HOT, base.x, base.y + y, base.z, 4, 0.3, 0.4, 0.3, 0.0);
			level.sendParticles(HellflameCombat.BIG_ORANGE, base.x, base.y + y, base.z, 3, rr * 0.4, 0.4, rr * 0.4, 0.0);
		}
		level.sendParticles(ParticleTypes.LAVA, base.x, base.y + 1, base.z, 12 + (int) (12 * c), r * 0.5, 0.5, r * 0.5, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, base.x, base.y + 1, base.z, 1, 0, 0, 0, 0);
		Quirks.setAnim(p, Hellflame.ANIM_PROMINENCE);
		Quirks.setCooldown(p, HellflameConfig.PROMINENCE, HellflameConfig.PROMINENCE_COOLDOWN);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.4f, 0.6f);
		sound(p, SoundEvents.GHAST_SHOOT, 1.2f, 0.5f);
		sound(p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.4f);
		heat(p, HellflameConfig.PROMINENCE_HEAT + HellflameConfig.PROMINENCE_HEAT_EXTRA * c);
		return n;
	}

	private static void chargeTick(ServerPlayer p, Session s, long now) {
		s.lastFireAt = now;
		long age = now - s.chargeAt;
		if (age >= HellflameConfig.PROMINENCE_CHARGE_TICKS + HellflameConfig.PROMINENCE_AUTO_FIRE_EXTRA) {
			prominence(p, 1f);
			return;
		}
		if (age == HellflameConfig.PROMINENCE_CHARGE_TICKS) {
			sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 1.6f);
		}
		if (age % 10 == 0) {
			sound(p, SoundEvents.BLAZE_BURN, 0.6f, 0.5f + age / 120f);
		}
	}

	/** A small sun overhead rains fireballs on the threats near him for 6 s. */
	public static void hellflameSun(ServerPlayer p) {
		Session s = session(p);
		s.sunUntil = now(p) + HellflameConfig.SUN_TICKS;
		s.falls.clear();
		Quirks.setVal(p, Hellflame.V_SUN, s.sunUntil);
		Quirks.setAnim(p, Hellflame.ANIM_SUN);
		Quirks.setCooldown(p, HellflameConfig.SUN, HellflameConfig.SUN_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 sun = sunPos(p);
		HellflameCombat.flare(level, sun, 30, 0.6);
		HellflameCombat.streak(level, hand(p), sun, 2.0, true);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.5f);
		sound(p, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.6f);
		heat(p, HellflameConfig.SUN_HEAT);
	}

	public static Vec3 sunPos(Entity p) {
		return p.position().add(0, p.getBbHeight() + HellflameConfig.SUN_HEIGHT - 1.8, 0);
	}

	public static boolean sunUp(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.sunUntil > now(p);
	}

	private static void sunTick(ServerPlayer p, Session s, long now) {
		ServerLevel level = p.serverLevel();
		Vec3 sun = sunPos(p);
		if (s.sunUntil > now) {
			s.lastFireAt = now;
			long age = HellflameConfig.SUN_TICKS - (s.sunUntil - now);
			if (age % HellflameConfig.SUN_INTERVAL == 0) {
				List<LivingEntity> near = AbilityHelpers.hostilesAround(p, p.position(), HellflameConfig.SUN_RANGE);
				near.removeIf(e -> !e.isAlive());
				if (!near.isEmpty()) {
					LivingEntity t = near.get(level.getRandom().nextInt(near.size()));
					s.falls.add(new Fall(sun, t.getId(), now));
					soundAt(level, sun, SoundEvents.BLAZE_SHOOT, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.3f);
				}
			}
			if (age % 20 == 0) {
				soundAt(level, sun, SoundEvents.FIRE_AMBIENT, 1.2f, 0.6f);
			}
		} else if (s.sunUntil != 0L && s.falls.isEmpty()) {
			s.sunUntil = 0L;
			Quirks.setVal(p, Hellflame.V_SUN, 0L);
			return;
		}
		for (Iterator<Fall> it = s.falls.iterator(); it.hasNext();) {
			Fall f = it.next();
			Entity e = level.getEntity(f.target());
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0);
			float t = (now - f.at()) / (float) HellflameConfig.SUN_FALL_TICKS;
			if (t >= 1f) {
				it.remove();
				for (LivingEntity v : HellflameCombat.around(p, to, HellflameConfig.SUN_SPLASH)) {
					HellflameCombat.strike(p, v, to.add(0, 2, 0), HellflameConfig.SUN_DAMAGE, HellflameConfig.SUN_BURN_SECONDS, 0.3, 0.0, true);
				}
				HellflameCombat.flare(level, to, 14, 0.5);
				level.sendParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 3, 0.3, 0.2, 0.3, 0.0);
				soundAt(level, to, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.5f);
				continue;
			}
			Vec3 at = f.from().lerp(to, t);
			level.sendParticles(HellflameCombat.WHITE_HOT, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
			level.sendParticles(HellflameCombat.YELLOW, at.x, at.y, at.z, 3, 0.12, 0.12, 0.12, 0.0);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.01);
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	// ================================================================ C: Flame Cloak / Shift+C: Vent

	/** Toggles the Cloak; returns whether it is on now. */
	public static boolean toggleCloak(ServerPlayer p) {
		Session s = session(p);
		s.cloak = !s.cloak;
		Quirks.setVal(p, Hellflame.V_CLOAK, s.cloak ? 1L : 0L);
		if (s.cloak) {
			Quirks.setAnim(p, Hellflame.ANIM_CLOAK);
			HellflameCombat.flare(p.serverLevel(), p.position().add(0, 1.0, 0), 30, 0.5);
			sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
			sound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 0.5f);
			s.lastFireAt = now(p);
		} else {
			Quirks.setCooldown(p, HellflameConfig.CLOAK, HellflameConfig.CLOAK_COOLDOWN);
			sound(p, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.2f);
		}
		return s.cloak;
	}

	public static boolean cloaked(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.cloak;
	}

	private static void cloakTick(ServerPlayer p, Session s, long now) {
		if (p.getRemainingFireTicks() > 0) {
			p.clearFire();
		}
		if (now % 20 == 0) {
			sound(p, SoundEvents.FIRE_AMBIENT, 0.6f, 0.9f);
		}
		heat(p, HellflameConfig.CLOAK_HEAT_PER_TICK);
	}

	/** Melee while cloaked: the target catches fire. */
	public static void cloakMelee(ServerPlayer p, LivingEntity target) {
		if (!cloaked(p) || !HellflameCombat.isTarget(p, target)) {
			return;
		}
		target.igniteForSeconds(HellflameConfig.CLOAK_IGNITE_SECONDS);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		p.serverLevel().sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 10, 0.25, 0.3, 0.25, 0.05);
		p.serverLevel().sendParticles(HellflameCombat.ORANGE, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.0);
		soundAt(p.serverLevel(), c, SoundEvents.FIRECHARGE_USE, 0.5f, 1.4f);
	}

	/** Damage about to hit him: the Cloak makes him fire-proof and sets close attackers alight. */
	public static float incoming(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!cloaked(p)) {
			return amount;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
			return 0f;
		}
		if (source.getEntity() instanceof LivingEntity attacker && attacker != p && attacker.distanceTo(p) <= HellflameConfig.CLOAK_RETALIATE_RANGE
				&& HellflameCombat.isTarget(p, attacker)) {
			attacker.igniteForSeconds(HellflameConfig.CLOAK_IGNITE_SECONDS);
			Vec3 c = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0);
			p.serverLevel().sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.05);
		}
		return amount;
	}

	/** Dumps all Heat in a radial flame burst (damage and radius scale with Heat); ends an Overheat. Returns the hit count. */
	public static int vent(ServerPlayer p) {
		float h = Quirks.meter(p);
		float dmg = HellflameConfig.VENT_BASE_DAMAGE + HellflameConfig.VENT_DAMAGE_PER_HEAT * h;
		double r = HellflameConfig.VENT_BASE_RADIUS + HellflameConfig.VENT_RADIUS_PER_HEAT * h;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		int n = 0;
		for (LivingEntity e : HellflameCombat.around(p, c, r)) {
			if (HellflameCombat.strike(p, e, p.position(), dmg, HellflameConfig.VENT_BURN_SECONDS, HellflameConfig.VENT_KNOCKBACK, 0.3, true)) {
				n++;
			}
		}
		Quirks.setMeter(p, 0f);
		boolean wasOverheated = Quirks.overloaded(p);
		if (wasOverheated) {
			Quirks.edit(p, st -> st.overloadUntil = 0L);
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
		p.clearFire();
		// the burst: rings of flame flying outward, smoke and steam
		for (int ring = 0; ring < 3; ring++) {
			double y = c.y - 0.6 + ring * 0.6;
			int pts = 28;
			for (int i = 0; i < pts; i++) {
				double a = i / (double) pts * Math.PI * 2.0 + ring * 0.2;
				level.sendParticles(ParticleTypes.FLAME, c.x, y, c.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.25 + r * 0.06);
			}
		}
		HellflameCombat.flare(level, c, 20 + (int) (h * 0.3), 0.6);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.5, c.z, 20, 0.5, 0.8, 0.5, 0.06);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 12, 0.6, 0.6, 0.6, 0.04);
		Quirks.setAnim(p, Hellflame.ANIM_VENT);
		Quirks.setCooldown(p, HellflameConfig.VENT, HellflameConfig.VENT_COOLDOWN);
		sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.7f);
		sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.5f);
		sound(p, SoundEvents.GHAST_SHOOT, 0.8f, 0.7f);
		session(p).lastFireAt = now(p) - HellflameConfig.VENT_DELAY;
		return n;
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (Quirks.overloaded(p)) {
			Quirks.addMeter(p, -HellflameConfig.OVERHEAT_VENT_PER_TICK);
			if (now % 4 == 0) {
				p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.6, p.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
			}
			if (now % 20 == 0) {
				sound(p, SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.6f);
			}
			return;
		}
		// each of these can overheat him, which ends everything: re-check the session as we go
		if (s.beam) {
			beamTick(p, s, now);
		}
		if (s.boost && !Quirks.overloaded(p)) {
			boostTick(p, s, now);
		}
		if (s.charge && !Quirks.overloaded(p)) {
			chargeTick(p, s, now);
		}
		dashTick(p, s, now);
		netTick(p, s, now);
		if (s.sunUntil != 0L) {
			sunTick(p, s, now);
		}
		if (s.cloak && !Quirks.overloaded(p)) {
			cloakTick(p, s, now);
		}
		if (!Quirks.overloaded(p) && now - s.lastFireAt > HellflameConfig.VENT_DELAY) {
			float rate = HellflameConfig.HEAT_VENT_PER_TICK * (wet(p) ? HellflameConfig.HEAT_VENT_WET_MULT : 1f);
			if (Quirks.meter(p) > 0f) {
				Quirks.addMeter(p, -rate);
			}
		}
	}

	/** Test hook: pretend the last fire move was long ago (so the Heat starts venting now). */
	public static void coolForTest(ServerPlayer p) {
		session(p).lastFireAt = now(p) - 1000L;
	}

	/** Every running move ends and goes (revoke / death / logout / Erased / Overheat). The Heat stays. */
	static void stopAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		if (s.beam) {
			s.beam = false;
			if (Quirks.holds(p, Quirk.HELLFLAME)) {
				Quirks.setCooldown(p, HellflameConfig.BEAM, HellflameConfig.BEAM_COOLDOWN);
			}
		}
		if (s.boost) {
			s.boost = false;
			p.resetFallDistance();
		}
		floatTolerance(p, s, false);
		s.charge = false;
		s.dashUntil = 0L;
		s.netUntil = 0L;
		s.netted.clear();
		s.sunUntil = 0L;
		s.falls.clear();
		s.cloak = false;
		if (Quirks.holds(p, Quirk.HELLFLAME)) {
			Quirks.edit(p, n -> {
				n.vals.remove(Hellflame.V_BEAM);
				n.vals.remove(Hellflame.V_BOOST);
				n.vals.remove(Hellflame.V_CHARGE);
				n.vals.remove(Hellflame.V_DASH);
				n.vals.remove(Hellflame.V_SUN);
				n.vals.remove(Hellflame.V_CLOAK);
			});
		}
	}

	/** Test read. */
	public static @Nullable Vec3 netCenter(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || s.netUntil == 0L ? null : s.netCenter;
	}
}
