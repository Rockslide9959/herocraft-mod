package com.projecthero.mod.miraculous.queenbee;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.2: Queen Bee's moves (see {@code docs/MIRACULOUS_QUEEN_BEE.md}). Every public move is called by
 * {@link QueenBeeKit#handle} once its cooldown is ready, and by the gametests directly. Moves that run over time keep a
 * per-player {@link Session} (entity ids, never entities), dropped by {@link QueenBeeKit#forget} /
 * {@link QueenBeeKit#clearSessionState}. Every hit of hers goes through {@link #hit}: the mod-wide targeting rules, bosses
 * capped at {@link QueenBeeConfig#BOSS_MAX_FRACTION_PER_HIT} and never knocked about -- and an armed Venom is delivered by
 * whatever hit lands first (a move or a plain punch, {@link QueenBeeMod}).
 */
public final class QueenBeeMoves {
	/** Her gold and black: the string, the sparkle, Venom's glow. */
	public static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(0.98f, 0.82f, 0.16f), 1.1f);
	public static final DustParticleOptions GOLD_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.35f), 1.8f);
	public static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.08f, 0.07f, 0.06f), 1.0f);
	public static final DustParticleOptions VENOM = new DustParticleOptions(new Vector3f(1.0f, 0.95f, 0.3f), 1.4f);

	static final class Session {
		// Top Strike
		int comboStep;
		long comboLast;
		long hitAt;
		int hitStep;
		boolean queued;
		// Top Throw: where the top is, where it heads, who it has hit
		Vec3 top;
		Vec3 topAim;
		int topTarget = -1;
		int topHits;
		boolean topReturning;
		final Set<Integer> topHit = new HashSet<>();
		// Spinning Shield
		long shieldNext;
		// Top Tangle
		int tangleId = -1;
		Vec3 tangleAt;
		// Top Swing
		Vec3 hook;
		long swingAt;
		// Top Copter: she was given flight by it
		boolean flying;
		long copterAt;
		// Sting: the lunge's last tick, landed yet
		long stingUntil;
		boolean stingLanded;
		// Stinger Volley
		int volleyLeft;
		long volleyNext;
		// the glide: her last height
		double lastY = Double.NaN;
		// Venom's paralysed: entity id -> [x, y, z, until]
		final Map<Integer, double[]> paralysed = new ConcurrentHashMap<>();
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private QueenBeeMoves() {
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

	static Vec3 flatLook(Player p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	static Vec3 chest(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static void sound(ServerPlayer p, net.minecraft.core.Holder<SoundEvent> s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static boolean inFront(Player p, Vec3 at, double dot) {
		Vec3 d = at.subtract(p.position());
		Vec3 flatD = new Vec3(d.x, 0, d.z);
		if (flatD.lengthSqr() < 1.0e-4) {
			return true;
		}
		return flatD.normalize().dot(flatLook(p)) > dot;
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= QueenBeeConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	/**
	 * One hit of hers: damage (boss-capped), knockback away from {@code origin} and {@code lift}. An armed Venom goes in
	 * with it. True if it landed.
	 */
	public static boolean hit(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!HeroTargets.canHarm(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * QueenBeeConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(0.5f, dmg))) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
		ServerLevel level = owner.serverLevel();
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.2);
		level.sendParticles(GOLD, c.x, c.y, c.z, 4, 0.25, 0.3, 0.25, 0.0);
		if (target.isAlive()) {
			deliverVenom(owner, target);
		}
		return true;
	}

	static void stun(LivingEntity e, int ticks) {
		if (isBoss(e)) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
		} else {
			PunisherControl.stun(e, ticks);
		}
	}

	static void poison(LivingEntity e, int ticks) {
		e.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, 0));
	}

	static @Nullable LivingEntity nearestInFront(ServerPlayer p, double reach) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, chest(p), reach + 1.0)) {
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, chest(p)));
			if (d > reach || !inFront(p, e.position(), QueenBeeConfig.FRONT_DOT)) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** The creature under the crosshair, else the valid one nearest it within {@code degrees}, in sight. */
	static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && HeroTargets.canHarm(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && HeroTargets.canHarm(p, e))) {
			Vec3 to = mid(le).subtract(eye);
			double d = to.length();
			if (d < 0.3 || d > range) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	/** Roots a creature at {@code at} this tick (it may still turn its head). */
	private static void root(LivingEntity t, Vec3 at) {
		if (t.position().distanceToSqr(at) > 0.01) {
			t.teleportTo(at.x, at.y, at.z);
		}
		t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
		t.hurtMarked = true;
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
	}

	// ================================================================ R: Top Strike / Shift+R: Top Throw

	/** One press = one swing of a three-press spinning-top combo on its string. Returns the swing (1..3), 0 = queued. */
	public static int strike(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (s.comboStep >= QueenBeeConfig.STRIKE_HITS || (s.comboStep > 0 && now - s.comboLast > QueenBeeConfig.STRIKE_WINDOW)) {
			s.comboStep = 0;
		}
		if (s.comboStep > 0 && now - s.comboLast < QueenBeeConfig.STRIKE_GAP) {
			s.queued = true;
			return 0;
		}
		return swing(p, s, now);
	}

	public static int comboStep(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.comboStep;
	}

	private static int swing(ServerPlayer p, Session s, long now) {
		if (s.hitAt > 0L) {
			land(p, s);
		}
		s.comboStep++;
		s.comboLast = now;
		s.queued = false;
		s.hitAt = now + QueenBeeConfig.STRIKE_WINDUP;
		s.hitStep = s.comboStep;
		int step = s.comboStep;
		Miraculous.setVal(p, QueenBee.V_COMBO, now + (step >= QueenBeeConfig.STRIKE_HITS ? QueenBeeConfig.STRIKE_SWING_TICKS : QueenBeeConfig.STRIKE_WINDOW));
		Miraculous.setAnim(p, step == 1 ? QueenBee.ANIM_STRIKE : step == 2 ? QueenBee.ANIM_STRIKE_2 : QueenBee.ANIM_STRIKE_3);
		if (step >= QueenBeeConfig.STRIKE_HITS) {
			Miraculous.setCooldown(p, QueenBeeConfig.STRIKE, QueenBeeConfig.STRIKE_COOLDOWN);
		}
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.7f, step >= QueenBeeConfig.STRIKE_HITS ? 0.9f : 1.3f + 0.15f * step);
		return step;
	}

	private static int land(ServerPlayer p, Session s) {
		boolean finisher = s.hitStep >= QueenBeeConfig.STRIKE_HITS;
		s.hitAt = 0L;
		p.swing(InteractionHand.MAIN_HAND, true);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, finisher ? 1.0f : 1.4f + 0.15f * s.hitStep);
		sound(p, SoundEvents.BEE_LOOP_AGGRESSIVE, 0.4f, 1.6f);
		int n = 0;
		for (LivingEntity e : com.projecthero.mod.miraculous.catnoir.CatNoirCombat.cone(p, QueenBeeConfig.STRIKE_REACH, QueenBeeConfig.STRIKE_CONE)) {
			if (hit(p, e, p.position(), finisher ? QueenBeeConfig.STRIKE_FINISHER_DAMAGE : QueenBeeConfig.STRIKE_DAMAGE,
					finisher ? QueenBeeConfig.STRIKE_FINISHER_KNOCKBACK : QueenBeeConfig.STRIKE_KNOCKBACK, finisher ? QueenBeeConfig.STRIKE_FINISHER_LIFT : 0.0)) {
				n++;
			}
		}
		if (n > 0) {
			sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, finisher ? 1.0f : 1.3f);
		}
		return n;
	}

	private static void tickStrike(ServerPlayer p, Session s, long now) {
		if (s.hitAt > 0L && now >= s.hitAt) {
			land(p, s);
		}
		if (s.queued && s.comboStep > 0 && s.comboStep < QueenBeeConfig.STRIKE_HITS && now - s.comboLast >= QueenBeeConfig.STRIKE_GAP) {
			swing(p, s, now);
		}
		if (s.comboStep > 0 && s.hitAt == 0L && !s.queued
				&& (s.comboStep >= QueenBeeConfig.STRIKE_HITS || now - s.comboLast > QueenBeeConfig.STRIKE_WINDOW)) {
			s.comboStep = 0;
		}
	}

	/**
	 * Hurl the top: it flies at what she aims at (else straight ahead, 20 blocks), ricochets on to the next creature
	 * within 8 (up to 3, 14 each), then flies home. True if it went out.
	 */
	public static boolean topThrow(ServerPlayer p) {
		Session s = session(p);
		LivingEntity first = aimed(p, QueenBeeConfig.THROW_RANGE, 8.0);
		s.top = chest(p).add(flatLook(p).scale(0.6));
		s.topTarget = first == null ? -1 : first.getId();
		s.topHits = 0;
		s.topHit.clear();
		s.topReturning = false;
		// no one there: out to the end of the string's reach (or the first block) and back
		BlockHitResult wall = AbilityHelpers.raycastBlock(p, QueenBeeConfig.THROW_RANGE);
		Vec3 aim = wall.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(QueenBeeConfig.THROW_RANGE)) : wall.getLocation();
		Miraculous.setVal(p, QueenBee.V_THROW, now(p) + QueenBeeConfig.THROW_TICKS);
		Miraculous.setVec(p, QueenBee.X_TOP, s.top);
		Miraculous.setAnim(p, QueenBee.ANIM_THROW);
		Miraculous.setCooldown(p, QueenBeeConfig.THROW, QueenBeeConfig.THROW_COOLDOWN);
		sound(p, SoundEvents.TRIDENT_THROW, 0.9f, 1.4f);
		sound(p, SoundEvents.BEE_LOOP, 0.8f, 1.7f);
		s.topAim = aim;
		return true;
	}

	private static void tickThrow(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(QueenBee.V_THROW);
		if (until == 0L) {
			return;
		}
		if (s == null || s.top == null) {
			Miraculous.setVal(p, QueenBee.V_THROW, 0L);
			Miraculous.setVec(p, QueenBee.X_TOP, null);
			return;
		}
		ServerLevel level = p.serverLevel();
		if (now >= until) {
			s.topReturning = true;
		}
		Vec3 dest;
		if (s.topReturning) {
			dest = chest(p);
		} else if (s.topTarget >= 0 && level.getEntity(s.topTarget) instanceof LivingEntity t && t.isAlive()) {
			dest = mid(t);
		} else {
			dest = s.topAim == null ? chest(p) : s.topAim;
		}
		Vec3 d = dest.subtract(s.top);
		double dist = d.length();
		double step = QueenBeeConfig.THROW_SPEED;
		if (dist <= step) {
			s.top = dest;
		} else {
			s.top = s.top.add(d.scale(step / dist));
		}
		if (s.topReturning) {
			if (s.top.distanceTo(chest(p)) < 1.2 || now >= until + 40) {
				Miraculous.setVal(p, QueenBee.V_THROW, 0L);
				Miraculous.setVec(p, QueenBee.X_TOP, null);
				s.top = null;
				sound(p, SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
				return;
			}
			Miraculous.setVal(p, QueenBee.V_THROW, Math.max(until, now + 2));
		} else if (s.topTarget >= 0) {
			if (level.getEntity(s.topTarget) instanceof LivingEntity t && t.isAlive()) {
				if (AbilityHelpers.distanceSqToBox(t, s.top) < 0.9) {
					s.topHit.add(t.getId());
					s.topHits++;
					hit(p, t, s.top.subtract(d), QueenBeeConfig.THROW_DAMAGE, 0.6, 0.15);
					level.playSound(null, s.top.x, s.top.y, s.top.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 2.0f);
					LivingEntity next = s.topHits < QueenBeeConfig.THROW_TARGETS ? nextRicochet(p, s, t) : null;
					if (next != null) {
						s.topTarget = next.getId();
					} else {
						s.topTarget = -1;
						s.topReturning = true;
					}
				}
			} else {
				s.topTarget = -1;
				s.topReturning = true;
			}
		} else {
			// flying free: anything it brushes on the way is hit (and it may ricochet from there)
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, s.top, 1.2)) {
				if (s.topHit.contains(e.getId())) {
					continue;
				}
				s.topHit.add(e.getId());
				s.topHits++;
				hit(p, e, s.top, QueenBeeConfig.THROW_DAMAGE, 0.6, 0.15);
				LivingEntity next = s.topHits < QueenBeeConfig.THROW_TARGETS ? nextRicochet(p, s, e) : null;
				if (next != null) {
					s.topTarget = next.getId();
				} else {
					s.topReturning = true;
				}
				break;
			}
			if (!s.topReturning && (dist <= step || !level.noCollision(new net.minecraft.world.phys.AABB(s.top, s.top).inflate(0.15)))) {
				s.topReturning = true;
			}
		}
		Miraculous.setVec(p, QueenBee.X_TOP, s.top);
		if (now % 2 == 0) {
			level.sendParticles(GOLD, s.top.x, s.top.y, s.top.z, 1, 0.05, 0.05, 0.05, 0);
		}
	}

	private static @Nullable LivingEntity nextRicochet(ServerPlayer p, Session s, LivingEntity from) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, mid(from), QueenBeeConfig.THROW_HOP_RANGE)) {
			if (s.topHit.contains(e.getId()) || e == from) {
				continue;
			}
			double d = e.distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Gametests: how many creatures the thrown top has hit so far. */
	public static int topHits(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.topHits;
	}

	// ================================================================ G: Spinning Shield / Shift+G: Top Tangle

	/** Spin the top before her for {@link QueenBeeConfig#SHIELD_TICKS}: pulses of damage near her, projectiles turned back. */
	public static void shield(ServerPlayer p) {
		Session s = session(p);
		Miraculous.setVal(p, QueenBee.V_SHIELD, now(p) + QueenBeeConfig.SHIELD_TICKS);
		Miraculous.setAnim(p, QueenBee.ANIM_SHIELD);
		Miraculous.setCooldown(p, QueenBeeConfig.SHIELD, QueenBeeConfig.SHIELD_COOLDOWN);
		sound(p, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.0f, 1.3f);
		shieldPulse(p, s, now(p));
	}

	static int shieldPulse(ServerPlayer p, Session s, long now) {
		s.shieldNext = now + QueenBeeConfig.SHIELD_EVERY;
		double r = QueenBeeConfig.SHIELD_RADIUS;
		int n = 0;
		Vec3 c = p.position().add(0, 0.6, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c, r)) {
			if (AbilityHelpers.distanceSqToBox(e, c) > r * r) {
				continue;
			}
			if (hit(p, e, p.position(), QueenBeeConfig.SHIELD_DAMAGE, 0.3, 0.0)) {
				n++;
			}
		}
		return n;
	}

	private static void tickShield(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(QueenBee.V_SHIELD);
		if (until == 0L) {
			return;
		}
		if (now >= until) {
			Miraculous.setVal(p, QueenBee.V_SHIELD, 0L);
			return;
		}
		if (s != null && now >= s.shieldNext) {
			shieldPulse(p, s, now);
		}
		ServerLevel level = p.serverLevel();
		double r = QueenBeeConfig.SHIELD_RADIUS;
		Vec3 c = chest(p);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0), Entity::isAlive)) {
			if (proj.getOwner() == p) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			if (proj.position().distanceTo(c) > r + 0.5 && proj.position().add(v).distanceTo(c) > r + 0.5) {
				continue;
			}
			if (v.lengthSqr() > 1.0e-4 && v.dot(c.subtract(proj.position())) <= 0.0) {
				continue;
			}
			Vec3 at = proj.position();
			proj.deflect(ProjectileDeflection.REVERSE, p, p, true);
			proj.setOwner(p);
			if (proj instanceof AbstractArrow arrow) {
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			}
			level.sendParticles(GOLD_BIG, at.x, at.y, at.z, 6, 0.12, 0.12, 0.12, 0.05);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.5f);
		}
	}

	/** The string wraps the creature she aims at (14): damage, rooted and unable to act for {@link QueenBeeConfig#TANGLE_TICKS}. */
	public static @Nullable LivingEntity tangle(ServerPlayer p) {
		LivingEntity t = aimed(p, QueenBeeConfig.TANGLE_RANGE, QueenBeeConfig.TANGLE_ASSIST_DEGREES);
		if (t == null) {
			Miraculous.say(p, "message.projecthero.queen_bee.no_target", ChatFormatting.GOLD);
			return null;
		}
		Session s = session(p);
		s.tangleId = t.getId();
		s.tangleAt = t.position();
		Miraculous.setVal(p, QueenBee.V_TANGLE, now(p) + QueenBeeConfig.TANGLE_TICKS);
		Miraculous.setVal(p, QueenBee.V_TANGLE_ID, t.getId() + 1L);
		Miraculous.setAnim(p, QueenBee.ANIM_TANGLE);
		Miraculous.setCooldown(p, QueenBeeConfig.TANGLE, QueenBeeConfig.TANGLE_COOLDOWN);
		AbilityHelpers.line(p.serverLevel(), chest(p), mid(t), GOLD, 4.0);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 1.1f);
		sound(p, SoundEvents.LEASH_KNOT_PLACE, 1.0f, 1.0f);
		hit(p, t, p.position(), QueenBeeConfig.TANGLE_DAMAGE, 0.0, 0.0);
		stun(t, QueenBeeConfig.TANGLE_TICKS);
		return t;
	}

	/** Gametests: is {@code e} wrapped by her Top Tangle right now? */
	public static boolean tangled(ServerPlayer p, LivingEntity e) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.tangleId == e.getId() && Miraculous.state(p).val(QueenBee.V_TANGLE) > now(p);
	}

	private static void tickTangle(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(QueenBee.V_TANGLE);
		if (until == 0L) {
			return;
		}
		Entity e = s == null || s.tangleId < 0 ? null : p.serverLevel().getEntity(s.tangleId);
		if (now >= until || !(e instanceof LivingEntity t) || !t.isAlive()) {
			Miraculous.setVal(p, QueenBee.V_TANGLE, 0L);
			Miraculous.setVal(p, QueenBee.V_TANGLE_ID, 0L);
			if (s != null) {
				s.tangleId = -1;
			}
			return;
		}
		if (!isBoss(t) && s.tangleAt != null) {
			root(t, s.tangleAt);
		}
	}

	// ================================================================ X: Top Swing / Shift+X: Top Copter

	/** The string hooks the block she aims at (30) and swings / pulls her to it. Returns the hook, or null. */
	public static @Nullable Vec3 topSwing(ServerPlayer p) {
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, QueenBeeConfig.SWING_RANGE);
		if (bhr.getType() == HitResult.Type.MISS) {
			Miraculous.say(p, "message.projecthero.queen_bee.no_hook", ChatFormatting.GOLD);
			return null;
		}
		Vec3 hook = bhr.getLocation();
		Session s = session(p);
		s.hook = hook;
		s.swingAt = now(p);
		Miraculous.setVec(p, QueenBee.X_SWING, hook);
		Miraculous.setVal(p, QueenBee.V_SWING, now(p) + QueenBeeConfig.SWING_TICKS);
		Miraculous.setAnim(p, QueenBee.ANIM_SWING);
		Miraculous.setCooldown(p, QueenBeeConfig.SWING, QueenBeeConfig.SWING_COOLDOWN);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.8f);
		p.level().playSound(null, hook.x, hook.y, hook.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0f, 1.1f);
		pull(p, s);
		return hook;
	}

	private static void pull(ServerPlayer p, Session s) {
		Vec3 to = s.hook.subtract(p.position().add(0, p.getBbHeight() * 0.5, 0));
		double d = to.length();
		if (d < 1.0e-3) {
			return;
		}
		Vec3 v = p.getDeltaMovement().scale(0.92).add(to.scale(QueenBeeConfig.SWING_PULL / d));
		double speed = v.length();
		if (speed > QueenBeeConfig.SWING_SPEED) {
			v = v.scale(QueenBeeConfig.SWING_SPEED / speed);
		}
		AbilityHelpers.launchSelf(p, v);
	}

	private static void tickSwing(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(QueenBee.V_SWING);
		if (until == 0L) {
			return;
		}
		boolean there = s == null || s.hook == null || p.position().add(0, p.getBbHeight() * 0.5, 0).distanceTo(s.hook) < QueenBeeConfig.SWING_ARRIVE;
		if (now >= until || there || (s != null && now - s.swingAt > 4 && p.horizontalCollision)) {
			Miraculous.setVal(p, QueenBee.V_SWING, 0L);
			Miraculous.setVec(p, QueenBee.X_SWING, null);
			if (there && s != null && s.hook != null) {
				AbilityHelpers.launchSelf(p, p.getDeltaMovement().multiply(0.4, 0.0, 0.4).add(0, 0.45, 0));
			}
			if (s != null) {
				s.hook = null;
			}
			return;
		}
		pull(p, s);
	}

	/** The top spun overhead like a rotor: flight for up to {@link QueenBeeConfig#COPTER_TICKS}; again to land. True: now flying. */
	public static boolean copter(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		if (st.val(QueenBee.V_COPTER) != 0L) {
			endCopter(p);
			return false;
		}
		Session s = session(p);
		long now = now(p);
		s.copterAt = now;
		Miraculous.setVal(p, QueenBee.V_COPTER, now + QueenBeeConfig.COPTER_TICKS);
		Miraculous.setAnim(p, QueenBee.ANIM_COPTER);
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			s.flying = true;
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.getAbilities().setFlyingSpeed(QueenBeeConfig.COPTER_FLY_SPEED);
			p.onUpdateAbilities();
		}
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.45, 0));
		sound(p, SoundEvents.BEE_LOOP, 1.0f, 1.0f);
		sound(p, SoundEvents.ELYTRA_FLYING, 0.3f, 2.0f);
		return true;
	}

	/** The flight ends (again, the timer, touching down, detransforming): the cooldown starts now. */
	static void endCopter(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		boolean was = st.val(QueenBee.V_COPTER) != 0L;
		if (was) {
			Miraculous.setVal(p, QueenBee.V_COPTER, 0L);
			Miraculous.setCooldown(p, QueenBeeConfig.COPTER, QueenBeeConfig.COPTER_COOLDOWN);
		}
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.flying) {
			s.flying = false;
			if (!p.getAbilities().instabuild && !p.isSpectator()) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
				p.getAbilities().setFlyingSpeed(0.05f);
				p.onUpdateAbilities();
			}
			p.resetFallDistance();
			Miraculous.safeLanding(p);
		}
	}

	private static void tickCopter(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(QueenBee.V_COPTER);
		if (until == 0L) {
			return;
		}
		long at = s == null ? now : s.copterAt;
		if (now >= until || (now - at > QueenBeeConfig.COPTER_GROUND_GRACE && p.onGround())) {
			endCopter(p);
			return;
		}
		if (s != null && s.flying) {
			p.getAbilities().mayfly = true;
		}
		p.resetFallDistance();
		if (now % 8 == 0) {
			sound(p, SoundEvents.BEE_LOOP, 0.5f, 1.2f);
		}
	}

	// ================================================================ Z: Stinger Dive / Shift+Z: VENOM

	/** Leap up, then dive point-first: everything within {@link QueenBeeConfig#DIVE_RADIUS} of the landing is hit. */
	public static void dive(ServerPlayer p) {
		long now = now(p);
		Miraculous.setVal(p, QueenBee.V_DIVE, 1L);
		Miraculous.setVal(p, QueenBee.V_DIVE_AT, now);
		Miraculous.setAnim(p, QueenBee.ANIM_DIVE);
		Miraculous.setCooldown(p, QueenBeeConfig.DIVE, QueenBeeConfig.DIVE_COOLDOWN);
		Miraculous.safeLanding(p);
		AbilityHelpers.launchSelf(p, flatLook(p).scale(QueenBeeConfig.DIVE_LEAP_FORWARD).add(0, QueenBeeConfig.DIVE_LEAP_UP, 0));
		sound(p, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.0f, 1.0f);
	}

	private static void tickDive(ServerPlayer p, MiraculousState st, long now) {
		long phase = st.val(QueenBee.V_DIVE);
		if (phase == 0L) {
			return;
		}
		long at = st.val(QueenBee.V_DIVE_AT);
		if (now - at > QueenBeeConfig.DIVE_MAX_TICKS) {
			Miraculous.setVal(p, QueenBee.V_DIVE, 0L);
			return;
		}
		if (phase == 1L) {
			if (now - at >= QueenBeeConfig.DIVE_RISE_TICKS || (now - at > 3 && p.getDeltaMovement().y <= 0.05)) {
				Miraculous.setVal(p, QueenBee.V_DIVE, 2L);
				Vec3 dir = p.getLookAngle();
				Vec3 down = new Vec3(dir.x * 0.6, -1.0, dir.z * 0.6).normalize().scale(QueenBeeConfig.DIVE_SPEED);
				AbilityHelpers.launchSelf(p, down);
				sound(p, SoundEvents.PHANTOM_SWOOP, 0.8f, 1.5f);
			}
			return;
		}
		if (p.onGround() || now - at > 4 && p.getDeltaMovement().y > -0.05 && p.verticalCollisionBelow) {
			slam(p);
			return;
		}
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().multiply(1.0, 0.0, 1.0).add(0, -QueenBeeConfig.DIVE_SPEED, 0));
		ServerLevel level = p.serverLevel();
		level.sendParticles(GOLD, p.getX(), p.getY() + 0.5, p.getZ(), 2, 0.15, 0.3, 0.15, 0);
	}

	/** The dive lands. Returns how many were hit. */
	public static int slam(ServerPlayer p) {
		Miraculous.setVal(p, QueenBee.V_DIVE, 0L);
		p.resetFallDistance();
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 0.5, 0), QueenBeeConfig.DIVE_RADIUS)) {
			if (hit(p, e, c, QueenBeeConfig.DIVE_DAMAGE, QueenBeeConfig.DIVE_KNOCKBACK, QueenBeeConfig.DIVE_LIFT)) {
				n++;
			}
		}
		for (int i = 0; i < 24; i++) {
			double a = Math.PI * 2 * i / 24;
			level.sendParticles(GOLD_BIG, c.x + Math.cos(a) * 1.5, c.y + 0.1, c.z + Math.sin(a) * 1.5, 1, Math.cos(a) * 0.3, 0.05, Math.sin(a) * 0.3, 0.2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.MACE_SMASH_GROUND, 1.0f, 1.2f);
		sound(p, SoundEvents.BEE_STING, 1.0f, 0.7f);
		return n;
	}

	/**
	 * VENOM: the top glows -- her next hit (a move or a punch) within {@link QueenBeeConfig#VENOM_ARMED_TICKS} paralyses
	 * the target. Pressed again while armed: a touch on whatever is right in front of her. Starts the countdown.
	 */
	public static boolean venom(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		long now = now(p);
		if (st.val(QueenBee.V_VENOM) > now) {
			LivingEntity t = nearestInFront(p, QueenBeeConfig.VENOM_TOUCH_REACH);
			Miraculous.setAnim(p, QueenBee.ANIM_VENOM_TOUCH);
			p.swing(InteractionHand.MAIN_HAND, true);
			if (t == null) {
				Miraculous.say(p, "message.projecthero.queen_bee.no_target", ChatFormatting.GOLD);
				return false;
			}
			return hit(p, t, p.position(), 1f, 0.0, 0.0);
		}
		if (!Miraculous.ready(p, QueenBeeConfig.VENOM)) {
			return false;
		}
		Miraculous.setVal(p, QueenBee.V_VENOM, now + QueenBeeConfig.VENOM_ARMED_TICKS);
		Miraculous.setAnim(p, QueenBee.ANIM_VENOM);
		Miraculous.setCooldown(p, QueenBeeConfig.VENOM, QueenBeeConfig.VENOM_COOLDOWN);
		Miraculous.startCountdown(p);
		Miraculous.say(p, "message.projecthero.queen_bee.venom", ChatFormatting.GOLD);
		sound(p, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.0f, 0.8f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
		Vec3 c = chest(p);
		p.serverLevel().sendParticles(VENOM, c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.0);
		return true;
	}

	/** The armed Venom goes into {@code t} (called by every hit of hers). True if it was delivered. */
	public static boolean deliverVenom(ServerPlayer p, LivingEntity t) {
		MiraculousState st = Miraculous.state(p);
		long now = now(p);
		if (st.val(QueenBee.V_VENOM) <= now || !HeroTargets.canHarm(p, t)) {
			return false;
		}
		Miraculous.setVal(p, QueenBee.V_VENOM, 0L);
		ServerLevel level = p.serverLevel();
		Vec3 c = mid(t);
		level.sendParticles(VENOM, c.x, c.y, c.z, 30, t.getBbWidth() * 0.5, t.getBbHeight() * 0.4, t.getBbWidth() * 0.5, 0.0);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6f, 1.8f);
		boolean boss = isBoss(t);
		float dmg = boss ? t.getMaxHealth() * QueenBeeConfig.BOSS_MAX_FRACTION_PER_HIT : QueenBeeConfig.VENOM_DAMAGE;
		AbilityHelpers.hurtBurst(p, t, p.level().damageSources().playerAttack(p), dmg);
		if (boss) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, QueenBeeConfig.VENOM_BOSS_SLOW_TICKS, 2);
			return true;
		}
		if (!t.isAlive()) {
			return true;
		}
		Session s = session(p);
		long until = now + QueenBeeConfig.VENOM_PARALYSE_TICKS;
		s.paralysed.put(t.getId(), new double[] { t.getX(), t.getY(), t.getZ(), until });
		PunisherControl.stun(t, QueenBeeConfig.VENOM_PARALYSE_TICKS);
		if (t instanceof Mob m) {
			PunisherControl.suppressTarget(m, QueenBeeConfig.VENOM_PARALYSE_TICKS);
		}
		Miraculous.setVal(p, QueenBee.V_PARALYSED_ID, t.getId() + 1L);
		Miraculous.setVal(p, QueenBee.V_PARALYSED, until);
		return true;
	}

	/** Gametests: is {@code e} paralysed by her Venom right now? */
	public static boolean paralysed(ServerPlayer p, LivingEntity e) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.paralysed.containsKey(e.getId());
	}

	private static void tickParalysed(ServerPlayer p, Session s, long now) {
		if (s.paralysed.isEmpty()) {
			return;
		}
		ServerLevel level = p.serverLevel();
		for (var it = s.paralysed.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			double[] v = en.getValue();
			Entity e = level.getEntity(en.getKey());
			if (now >= (long) v[3] || !(e instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			root(le, new Vec3(v[0], v[1], v[2]));
			le.setXRot(le.getXRot());
			if (now % 10 == 0) {
				Vec3 c = mid(le);
				level.sendParticles(VENOM, c.x, c.y, c.z, 3, le.getBbWidth() * 0.4, le.getBbHeight() * 0.35, le.getBbWidth() * 0.4, 0.0);
			}
		}
		if (s.paralysed.isEmpty() && Miraculous.state(p).val(QueenBee.V_PARALYSED) != 0L) {
			Miraculous.setVal(p, QueenBee.V_PARALYSED, 0L);
			Miraculous.setVal(p, QueenBee.V_PARALYSED_ID, 0L);
		}
	}

	// ================================================================ V: Hive Sense (toggle) / Shift+V: Queen's Command

	/** Toggles Hive Sense (the outlines are drawn by her own client). True: now on. */
	public static boolean hive(ServerPlayer p) {
		boolean on = Miraculous.state(p).val(QueenBee.V_HIVE) == 0L;
		Miraculous.setVal(p, QueenBee.V_HIVE, on ? 1L : 0L);
		if (on) {
			Miraculous.setAnim(p, QueenBee.ANIM_HIVE);
			p.playNotifySound(SoundEvents.BEEHIVE_WORK, SoundSource.PLAYERS, 0.8f, 1.4f);
		} else {
			Miraculous.setCooldown(p, QueenBeeConfig.HIVE, QueenBeeConfig.HIVE_COOLDOWN);
			p.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4f, 1.9f);
		}
		return on;
	}

	/**
	 * "Bow before your queen!": hostile mobs within {@link QueenBeeConfig#COMMAND_RADIUS} drop their target and can't pick
	 * a new one for a while (bosses ignore it); her allies there (her tamed pets, players she may not harm) get Strength I.
	 * Returns [mobs calmed, allies rallied].
	 */
	public static int[] command(ServerPlayer p) {
		Miraculous.setAnim(p, QueenBee.ANIM_COMMAND);
		Miraculous.setCooldown(p, QueenBeeConfig.COMMAND, QueenBeeConfig.COMMAND_COOLDOWN);
		ServerLevel level = p.serverLevel();
		double r = QueenBeeConfig.COMMAND_RADIUS;
		int calmed = 0, rallied = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive() && e.distanceToSqr(p) <= r * r)) {
			boolean ally = (e instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())) || (e instanceof Player && !HeroTargets.canHarm(p, e));
			if (ally) {
				e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, QueenBeeConfig.COMMAND_STRENGTH_TICKS, 0));
				rallied++;
				Vec3 c = mid(e);
				level.sendParticles(GOLD, c.x, c.y + 0.6, c.z, 6, 0.3, 0.2, 0.3, 0.0);
			} else if (e instanceof Mob m && (e instanceof Enemy || m.getTarget() != null) && !isBoss(e) && HeroTargets.canHarm(p, e)) {
				PunisherControl.suppressTarget(m, QueenBeeConfig.COMMAND_TICKS);
				calmed++;
				level.sendParticles(BLACK, m.getX(), m.getEyeY() + 0.4, m.getZ(), 4, 0.2, 0.1, 0.2, 0.0);
			}
		}
		for (int i = 0; i < 28; i++) {
			double a = Math.PI * 2 * i / 28;
			level.sendParticles(GOLD, p.getX() + Math.cos(a) * 2.0, p.getY() + 0.2, p.getZ() + Math.sin(a) * 2.0, 1, Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4, 0.4);
		}
		sound(p, SoundEvents.BELL_BLOCK, 0.9f, 1.4f);
		sound(p, SoundEvents.BEEHIVE_EXIT, 1.0f, 0.9f);
		return new int[] { calmed, rallied };
	}

	// ================================================================ C: Sting / Shift+C: Stinger Volley

	/** A quick lunge, the top's point first: the first creature she reaches takes {@link QueenBeeConfig#STING_DAMAGE} + Poison. */
	public static void sting(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		s.stingUntil = now + QueenBeeConfig.STING_TICKS;
		s.stingLanded = false;
		Miraculous.setAnim(p, QueenBee.ANIM_STING);
		Miraculous.setCooldown(p, QueenBeeConfig.STING, QueenBeeConfig.STING_COOLDOWN);
		Miraculous.safeLanding(p);
		Vec3 look = p.getLookAngle();
		AbilityHelpers.launchSelf(p, new Vec3(look.x, Math.max(0.05, look.y * 0.5), look.z).normalize().scale(QueenBeeConfig.STING_LUNGE));
		sound(p, SoundEvents.BEE_LOOP_AGGRESSIVE, 0.8f, 1.8f);
		stingCheck(p, s);
	}

	/** Gametests / the tick: the jab lands on the nearest creature in reach. True if it did. */
	static boolean stingCheck(ServerPlayer p, Session s) {
		if (s.stingLanded) {
			return false;
		}
		LivingEntity t = nearestInFront(p, QueenBeeConfig.STING_REACH);
		if (t == null) {
			return false;
		}
		s.stingLanded = true;
		p.swing(InteractionHand.MAIN_HAND, true);
		if (hit(p, t, p.position(), QueenBeeConfig.STING_DAMAGE, 0.5, 0.1)) {
			poison(t, QueenBeeConfig.STING_POISON_TICKS);
			p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 1.0f, 1.0f);
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().scale(0.2));
			return true;
		}
		return false;
	}

	/** The top hovers over her shoulder and fires {@link QueenBeeConfig#VOLLEY_SHOTS} stingers at what she aims at. */
	public static void volley(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		s.volleyLeft = QueenBeeConfig.VOLLEY_SHOTS;
		s.volleyNext = now + 4;
		Miraculous.setVal(p, QueenBee.V_VOLLEY, now + 4 + (long) QueenBeeConfig.VOLLEY_SHOTS * QueenBeeConfig.VOLLEY_EVERY + 6);
		Miraculous.setAnim(p, QueenBee.ANIM_VOLLEY);
		Miraculous.setCooldown(p, QueenBeeConfig.VOLLEY, QueenBeeConfig.VOLLEY_COOLDOWN);
		sound(p, SoundEvents.BEEHIVE_EXIT, 1.0f, 1.3f);
	}

	/** Where the hovering top sits (above her right shoulder). */
	public static Vec3 volleyTop(Player p) {
		Vec3 f = flatLook(p);
		return p.position().add(0, p.getBbHeight() + 0.35, 0).add(new Vec3(-f.z, 0, f.x).scale(-0.55));
	}

	/** One stinger: at the aimed creature, else straight ahead. Returns the creature hit, or null. */
	static @Nullable LivingEntity fireStinger(ServerPlayer p) {
		Vec3 from = volleyTop(p);
		LivingEntity t = aimed(p, QueenBeeConfig.VOLLEY_RANGE, 6.0);
		Vec3 to = t != null ? mid(t) : p.getEyePosition().add(p.getLookAngle().scale(QueenBeeConfig.VOLLEY_RANGE));
		if (t == null) {
			BlockHitResult bhr = AbilityHelpers.raycastBlock(p, QueenBeeConfig.VOLLEY_RANGE);
			if (bhr.getType() != HitResult.Type.MISS) {
				to = bhr.getLocation();
			}
		}
		long now = now(p);
		Miraculous.setVec(p, QueenBee.X_STING, to);
		Miraculous.setVal(p, QueenBee.V_STING_AT, now);
		p.level().playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.6f, 1.8f);
		if (t != null && hit(p, t, from, QueenBeeConfig.VOLLEY_DAMAGE, 0.15, 0.0)) {
			poison(t, QueenBeeConfig.VOLLEY_POISON_TICKS);
			return t;
		}
		return null;
	}

	private static void tickVolley(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(QueenBee.V_VOLLEY);
		if (until == 0L) {
			return;
		}
		if (s != null && s.volleyLeft > 0 && now >= s.volleyNext) {
			s.volleyLeft--;
			s.volleyNext = now + QueenBeeConfig.VOLLEY_EVERY;
			fireStinger(p);
		}
		if (now >= until && (s == null || s.volleyLeft <= 0)) {
			Miraculous.setVal(p, QueenBee.V_VOLLEY, 0L);
			Miraculous.setVec(p, QueenBee.X_STING, null);
		}
	}

	// ================================================================ the passive glide, the tick

	/** The buzzing glide: while she drops slowly in the air (her client caps the fall while jump is held), no fall builds up. */
	private static void tickGlide(ServerPlayer p, Session s) {
		double y = p.getY();
		if (!Double.isNaN(s.lastY) && !p.onGround() && !p.isFallFlying() && !p.getAbilities().flying) {
			double drop = s.lastY - y;
			if (drop > 0.0 && drop < QueenBeeConfig.GLIDE_SAFE_DROP && p.fallDistance > 0f) {
				p.resetFallDistance();
			}
		}
		s.lastY = y;
	}

	static void tick(ServerPlayer p) {
		long now = now(p);
		MiraculousState st = Miraculous.state(p);
		Session s = session(p);
		tickStrike(p, s, now);
		if (st.val(QueenBee.V_COMBO) != 0L && now >= st.val(QueenBee.V_COMBO) && s.hitAt == 0L && !s.queued) {
			Miraculous.setVal(p, QueenBee.V_COMBO, 0L);
		}
		if (s.stingUntil > now) {
			stingCheck(p, s);
		}
		long venom = Miraculous.state(p).val(QueenBee.V_VENOM);
		if (venom != 0L && now >= venom) {
			Miraculous.setVal(p, QueenBee.V_VENOM, 0L);
			Miraculous.say(p, "message.projecthero.queen_bee.venom_faded", ChatFormatting.GOLD);
		} else if (venom != 0L && now % 4 == 0) {
			Vec3 c = chest(p).add(flatLook(p).scale(0.4));
			p.serverLevel().sendParticles(VENOM, c.x, c.y - 0.3, c.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		tickThrow(p, Miraculous.state(p), s, now);
		tickShield(p, Miraculous.state(p), s, now);
		tickTangle(p, Miraculous.state(p), s, now);
		tickSwing(p, Miraculous.state(p), s, now);
		tickCopter(p, Miraculous.state(p), s, now);
		tickDive(p, Miraculous.state(p), now);
		tickVolley(p, Miraculous.state(p), s, now);
		tickParalysed(p, s, now);
		tickGlide(p, s);
	}

	/** Detransformation / death / logout: every running move ends, the flight lands, the paralysed go free. */
	static void endAll(ServerPlayer p) {
		endCopter(p);
		Miraculous.edit(p, n -> {
			n.vals.keySet().removeIf(k -> k.startsWith("qb_"));
			n.vecs.keySet().removeIf(k -> k.startsWith("qb_"));
		});
		SESSIONS.remove(p.getUUID());
	}

	/** Gametests: whether she holds Pollen and is transformed (shorthand). */
	public static boolean active(Player p) {
		return Miraculous.transformedAs(p, Kwami.POLLEN);
	}
}
