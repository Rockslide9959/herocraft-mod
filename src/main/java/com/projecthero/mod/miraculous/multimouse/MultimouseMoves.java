package com.projecthero.mod.miraculous.multimouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.multimouse.entity.MiniMouseEntity;
import com.projecthero.mod.miraculous.multimouse.entity.MultimouseEntityTypes;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.1: Multimouse's moves (see {@code docs/MIRACULOUS_MULTIMOUSE.md}) -- everything but Multitude ({@link Multitude}).
 * Every public move is called by {@link MultimouseKit#handle} once its cooldown is ready, and by the gametests directly.
 * Moves that run over time keep a per-player {@link Session} (entity ids, never entities), dropped by
 * {@link MultimouseKit#forget} / {@link MultimouseKit#clearSessionState}. Every hit of hers goes through {@link #hit}: the
 * mod-wide targeting rules, bosses capped at {@link MultimouseConfig#BOSS_MAX_FRACTION_PER_HIT} and never knocked
 * about, and half damage while she is split into the Multitude.
 *
 * <p>Her size (Scurry and Multitude) is the vanilla {@code generic.scale} attribute, one transient modifier
 * ({@link #applySize}, reconciled every tick from the synced state and cleared by {@link #endAll}).
 */
public final class MultimouseMoves {
	/** Her pink: the rope, the sparkle. */
	public static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(0.88f, 0.33f, 0.62f), 1.1f);
	public static final DustParticleOptions PINK_BIG = new DustParticleOptions(new Vector3f(0.98f, 0.55f, 0.78f), 1.8f);
	public static final DustParticleOptions SILVER = new DustParticleOptions(new Vector3f(0.82f, 0.84f, 0.9f), 1.0f);

	static final ResourceLocation SCALE_ID = PowerToggles.id("multimouse_scale");
	static final ResourceLocation SPEED_ID = PowerToggles.id("multimouse_speed");

	static final class Session {
		// Rope Lash
		int comboStep;
		long comboLast;
		long hitAt;
		int hitStep;
		boolean queued;
		// Rope Twirl
		long twirlNext;
		// Rope Swing
		Vec3 hook;
		long swingAt;
		// Rope Bind
		int bindId = -1;
		Vec3 bindAt;
		// Mouse Swarm
		final List<Integer> swarm = new ArrayList<>();
		// Mouse Trap: [x, y, z, until]
		final List<double[]> traps = new ArrayList<>();
		// snared by a trap: entity id -> [x, y, z, until]
		final Map<Integer, double[]> snared = new ConcurrentHashMap<>();
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private MultimouseMoves() {
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
		return e.getMaxHealth() >= MultimouseConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	/** Her moves' damage: half while she is split into the Multitude. */
	public static float scaled(ServerPlayer owner, float damage) {
		return Multimouse.multitude(owner) ? damage * MultimouseConfig.MINI_DAMAGE_FACTOR : damage;
	}

	/** One hit of hers: damage (halved while split, boss-capped), knockback away from {@code origin} and {@code lift}. */
	public static boolean hit(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!HeroTargets.canHarm(owner, target) || target instanceof MiniMouseEntity) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = scaled(owner, damage);
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * MultimouseConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
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
		ServerLevel level = (ServerLevel) owner.level();
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.2);
		level.sendParticles(PINK, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	/**
	 * A mini copy's bite: {@code damage} (never halved; boss-capped), dealt as HER damage so a boss's threat table counts
	 * it as hers. True if it landed.
	 */
	public static boolean bite(ServerPlayer owner, MiniMouseEntity mini, LivingEntity target, float damage) {
		if (!HeroTargets.canHarm(owner, target) || target instanceof MiniMouseEntity) {
			return false;
		}
		float dmg = isBoss(target) ? Math.min(damage, target.getMaxHealth() * MultimouseConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), dmg)) {
			return false;
		}
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 c = mini.position().add(target.position().subtract(mini.position()).scale(0.6)).add(0, 0.2, 0);
		level.sendParticles(PINK, c.x, c.y, c.z, 2, 0.08, 0.08, 0.08, 0.0);
		level.playSound(null, mini.getX(), mini.getY(), mini.getZ(), SoundEvents.FOX_BITE, SoundSource.PLAYERS, 0.35f, 1.9f);
		return true;
	}

	/** A puff of pink sparkle (a copy popping, Multitude splitting / recombining). */
	public static void sparkle(ServerLevel level, Vec3 c, int n) {
		level.sendParticles(PINK_BIG, c.x, c.y, c.z, n, 0.18, 0.18, 0.18, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, Math.max(1, n / 3), 0.12, 0.12, 0.12, 0.04);
		level.sendParticles(ParticleTypes.WAX_OFF, c.x, c.y, c.z, Math.max(1, n / 4), 0.15, 0.15, 0.15, 0.5);
	}

	static void stun(LivingEntity e, int ticks) {
		if (isBoss(e)) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
		} else {
			PunisherControl.stun(e, ticks);
		}
	}

	static void ring(ServerLevel level, ParticleOptions particle, Vec3 c, double r, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(particle, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	static @Nullable LivingEntity nearestInFront(ServerPlayer p, double reach) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, chest(p), reach + 1.0)) {
			if (e instanceof MiniMouseEntity) {
				continue;
			}
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, chest(p)));
			if (d > reach || !inFront(p, e.position(), MultimouseConfig.FRONT_DOT)) {
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
		if (direct != null && !(direct instanceof MiniMouseEntity) && HeroTargets.canHarm(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && !(e instanceof MiniMouseEntity) && HeroTargets.canHarm(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
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

	// ================================================================ R: Rope Lash / Shift+R: Lasso Yank

	/** One press = one swing of a three-press whip combo, as Cat Noir's Baton Strike. Returns the swing (1..3), 0 = queued. */
	public static int lash(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (s.comboStep >= MultimouseConfig.LASH_HITS || (s.comboStep > 0 && now - s.comboLast > MultimouseConfig.LASH_WINDOW)) {
			s.comboStep = 0;
		}
		if (s.comboStep > 0 && now - s.comboLast < MultimouseConfig.LASH_GAP) {
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
		s.hitAt = now + MultimouseConfig.LASH_WINDUP;
		s.hitStep = s.comboStep;
		int step = s.comboStep;
		Miraculous.setVal(p, Multimouse.V_COMBO, now + (step >= MultimouseConfig.LASH_HITS ? MultimouseConfig.LASH_SWING_TICKS : MultimouseConfig.LASH_WINDOW));
		Miraculous.setAnim(p, step == 1 ? Multimouse.ANIM_LASH : step == 2 ? Multimouse.ANIM_LASH_2 : Multimouse.ANIM_LASH_3);
		if (step >= MultimouseConfig.LASH_HITS) {
			Miraculous.setCooldown(p, MultimouseConfig.LASH, MultimouseConfig.LASH_COOLDOWN);
		}
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 0.7f, step >= MultimouseConfig.LASH_HITS ? 0.8f : 1.2f + 0.15f * step);
		return step;
	}

	private static int land(ServerPlayer p, Session s) {
		boolean finisher = s.hitStep >= MultimouseConfig.LASH_HITS;
		s.hitAt = 0L;
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, finisher ? 0.9f : 1.3f + 0.15f * s.hitStep);
		sound(p, SoundEvents.LEASH_KNOT_PLACE, 0.8f, finisher ? 0.8f : 1.3f);
		ServerLevel level = p.serverLevel();
		Vec3 f = flatLook(p);
		Vec3 from = chest(p);
		for (int i = 1; i <= 10; i++) {
			double d = MultimouseConfig.LASH_REACH * i / 10.0;
			double side = Math.sin(i / 10.0 * Math.PI) * (s.hitStep == 2 ? -0.6 : 0.6);
			Vec3 q = from.add(f.scale(d)).add(new Vec3(-f.z, 0, f.x).scale(side));
			level.sendParticles(PINK, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
		int n = 0;
		for (LivingEntity e : com.projecthero.mod.miraculous.catnoir.CatNoirCombat.cone(p, MultimouseConfig.LASH_REACH, MultimouseConfig.LASH_CONE)) {
			if (hit(p, e, p.position(), MultimouseConfig.LASH_DAMAGE, finisher ? MultimouseConfig.LASH_FINISHER_KNOCKBACK : MultimouseConfig.LASH_KNOCKBACK,
					finisher ? MultimouseConfig.LASH_FINISHER_LIFT : 0.0)) {
				n++;
			}
		}
		if (n > 0) {
			sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, finisher ? 0.9f : 1.2f);
		}
		return n;
	}

	private static void tickLash(ServerPlayer p, Session s, long now) {
		if (s.hitAt > 0L && now >= s.hitAt) {
			land(p, s);
		}
		if (s.queued && s.comboStep > 0 && s.comboStep < MultimouseConfig.LASH_HITS && now - s.comboLast >= MultimouseConfig.LASH_GAP) {
			swing(p, s, now);
		}
		if (s.comboStep > 0 && s.hitAt == 0L && !s.queued
				&& (s.comboStep >= MultimouseConfig.LASH_HITS || now - s.comboLast > MultimouseConfig.LASH_WINDOW)) {
			s.comboStep = 0;
		}
	}

	/** Lasso the creature she aims at (16 blocks) and yank it to her feet: damage and a short stun. Returns it. */
	public static @Nullable LivingEntity lasso(ServerPlayer p) {
		LivingEntity t = aimed(p, MultimouseConfig.LASSO_RANGE, MultimouseConfig.LASSO_ASSIST_DEGREES);
		if (t == null) {
			Miraculous.say(p, "message.projecthero.multimouse.no_target", ChatFormatting.LIGHT_PURPLE);
			return null;
		}
		Miraculous.setAnim(p, Multimouse.ANIM_LASSO);
		Miraculous.setCooldown(p, MultimouseConfig.LASSO, MultimouseConfig.LASSO_COOLDOWN);
		Miraculous.setVal(p, Multimouse.V_LASSO, now(p) + 12);
		Miraculous.setVal(p, Multimouse.V_LASSO_ID, t.getId() + 1L);
		AbilityHelpers.line(p.serverLevel(), chest(p), t.position().add(0, t.getBbHeight() * 0.5, 0), PINK, 4.0);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.8f);
		sound(p, SoundEvents.LEASH_KNOT_PLACE, 1.0f, 1.0f);
		if (!hit(p, t, p.position(), MultimouseConfig.LASSO_DAMAGE, 0.0, 0.0)) {
			return t;
		}
		if (isBoss(t)) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, MultimouseConfig.LASSO_STUN_TICKS * 2, 2);
			return t;
		}
		stun(t, MultimouseConfig.LASSO_STUN_TICKS);
		// the yank: toward her feet, a little up so it clears the ground
		Vec3 d = p.position().add(flatLook(p).scale(1.2)).subtract(t.position());
		Vec3 flat = new Vec3(d.x, 0, d.z);
		double dist = flat.length();
		if (dist > 0.3) {
			double speed = Math.min(2.4, 0.16 * dist + 0.3);
			t.setDeltaMovement(flat.scale(speed / dist).add(0, 0.35 + Math.min(0.3, Math.max(0.0, d.y) * 0.1), 0));
			t.hurtMarked = true;
			t.hasImpulse = true;
		}
		return t;
	}

	// ================================================================ G: Rope Twirl / Shift+G: Skipping Trip

	/** Spin the rope round her for {@link MultimouseConfig#TWIRL_TICKS}: pulses of damage near her, arrows bounce off. */
	public static void twirl(ServerPlayer p) {
		Session s = session(p);
		Miraculous.setVal(p, Multimouse.V_TWIRL, now(p) + MultimouseConfig.TWIRL_TICKS);
		Miraculous.setAnim(p, Multimouse.ANIM_TWIRL);
		Miraculous.setCooldown(p, MultimouseConfig.TWIRL, MultimouseConfig.TWIRL_COOLDOWN);
		s.twirlNext = now(p);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 1.4f);
		twirlPulse(p, s, now(p));
	}

	/** One pulse of the spinning rope: everything within {@link MultimouseConfig#TWIRL_RADIUS}. Returns how many. */
	static int twirlPulse(ServerPlayer p, Session s, long now) {
		s.twirlNext = now + MultimouseConfig.TWIRL_EVERY;
		double r = MultimouseConfig.TWIRL_RADIUS;
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(0, 0.6, 0), r)) {
			if (e instanceof MiniMouseEntity || AbilityHelpers.distanceSqToBox(e, p.position().add(0, 0.6, 0)) > r * r) {
				continue;
			}
			if (hit(p, e, p.position(), MultimouseConfig.TWIRL_DAMAGE, 0.25, 0.0)) {
				n++;
			}
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.45f, 1.7f);
		return n;
	}

	private static void tickTwirl(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(Multimouse.V_TWIRL);
		if (until == 0L) {
			return;
		}
		if (now >= until) {
			Miraculous.setVal(p, Multimouse.V_TWIRL, 0L);
			return;
		}
		if (s != null && now >= s.twirlNext) {
			twirlPulse(p, s, now);
		}
		ServerLevel level = p.serverLevel();
		double r = MultimouseConfig.TWIRL_RADIUS;
		// arrows from any side bounce off the spinning rope
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0), Entity::isAlive)) {
			if (proj.getOwner() == p) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			Vec3 c = chest(p);
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
			level.sendParticles(PINK, at.x, at.y, at.z, 6, 0.12, 0.12, 0.12, 0.05);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.7f, 1.6f);
		}
		if (now % 2 == 0) {
			double a = now * 0.9;
			Vec3 c = p.position().add(0, p.getBbHeight() * 0.55, 0);
			for (int i = 0; i < 3; i++) {
				double b = a + i * (Math.PI * 2 / 3);
				level.sendParticles(PINK, c.x + Math.cos(b) * r * 0.75, c.y, c.z + Math.sin(b) * r * 0.75, 1, 0, 0, 0, 0);
			}
		}
	}

	/** A low sweep along the ground in a {@link MultimouseConfig#TRIP_RADIUS}-block arc: damage, and everything hit is tripped. */
	public static int trip(ServerPlayer p) {
		Miraculous.setAnim(p, Multimouse.ANIM_TRIP);
		Miraculous.setCooldown(p, MultimouseConfig.TRIP, MultimouseConfig.TRIP_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		Vec3 f = flatLook(p);
		double r = MultimouseConfig.TRIP_RADIUS;
		for (int i = -9; i <= 9; i++) {
			double a = Math.atan2(f.z, f.x) + i * Math.toRadians(10.0);
			level.sendParticles(PINK, c.x + Math.cos(a) * r * 0.7, c.y + 0.15, c.z + Math.sin(a) * r * 0.7, 1, 0, 0.02, 0, 0);
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 1.0f);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 0.5, 0), r)) {
			if (e instanceof MiniMouseEntity || !inFront(p, e.position(), MultimouseConfig.TRIP_DOT) || Math.abs(e.getY() - c.y) > 2.0) {
				continue;
			}
			if (hit(p, e, c, MultimouseConfig.TRIP_DAMAGE, 0.0, 0.0)) {
				n++;
				stun(e, MultimouseConfig.TRIP_TICKS);
				if (!isBoss(e)) {
					e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1.0, 0.2).add(0, -0.3, 0));
					e.hurtMarked = true;
				}
				level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 0.2, e.getZ(), 6, 0.3, 0.05, 0.3, 0.1);
			}
		}
		return n;
	}

	// ================================================================ X: Rope Swing / Shift+X: Scurry

	/** The rope hooks the block she aims at (30 blocks) and swings / pulls her to it. Returns the hook, or null. */
	public static @Nullable Vec3 ropeSwing(ServerPlayer p) {
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, MultimouseConfig.SWING_RANGE);
		if (bhr.getType() == HitResult.Type.MISS) {
			Miraculous.say(p, "message.projecthero.multimouse.no_hook", ChatFormatting.LIGHT_PURPLE);
			return null;
		}
		Vec3 hook = bhr.getLocation();
		Session s = session(p);
		s.hook = hook;
		s.swingAt = now(p);
		Miraculous.setVec(p, Multimouse.X_SWING, hook);
		Miraculous.setVal(p, Multimouse.V_SWING, now(p) + MultimouseConfig.SWING_TICKS);
		Miraculous.setAnim(p, Multimouse.ANIM_SWING);
		Miraculous.setCooldown(p, MultimouseConfig.SWING, MultimouseConfig.SWING_COOLDOWN);
		AbilityHelpers.line(p.serverLevel(), chest(p), hook, PINK, 2.0);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.7f);
		p.level().playSound(null, hook.x, hook.y, hook.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
		pull(p, s);
		return hook;
	}

	private static void pull(ServerPlayer p, Session s) {
		Vec3 to = s.hook.subtract(p.position().add(0, p.getBbHeight() * 0.5, 0));
		double d = to.length();
		if (d < 1.0e-3) {
			return;
		}
		// the rope pulls her toward the hook while her own momentum carries on: an arc rather than a straight zip
		Vec3 v = p.getDeltaMovement().scale(0.92).add(to.scale(MultimouseConfig.SWING_PULL / d));
		double speed = v.length();
		if (speed > MultimouseConfig.SWING_SPEED) {
			v = v.scale(MultimouseConfig.SWING_SPEED / speed);
		}
		AbilityHelpers.launchSelf(p, v);
	}

	private static void tickSwing(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(Multimouse.V_SWING);
		if (until == 0L) {
			return;
		}
		boolean there = s == null || s.hook == null || p.position().add(0, p.getBbHeight() * 0.5, 0).distanceTo(s.hook) < MultimouseConfig.SWING_ARRIVE;
		if (now >= until || there || (s != null && now - s.swingAt > 4 && p.horizontalCollision)) {
			Miraculous.setVal(p, Multimouse.V_SWING, 0L);
			Miraculous.setVec(p, Multimouse.X_SWING, null);
			if (there && s != null && s.hook != null) {
				// a little hop up over the edge at the end of the rope
				AbilityHelpers.launchSelf(p, p.getDeltaMovement().multiply(0.4, 0.0, 0.4).add(0, 0.45, 0));
			}
			if (s != null) {
				s.hook = null;
			}
			return;
		}
		pull(p, s);
		if (now % 2 == 0) {
			AbilityHelpers.line(p.serverLevel(), chest(p), s.hook, PINK, 0.6);
		}
	}

	/** Shrink to mouse size (or grow back). True: now small. */
	public static boolean scurry(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		if (st.val(Multimouse.V_MULTI) != 0L) {
			Miraculous.say(p, "message.projecthero.multimouse.already_small", ChatFormatting.LIGHT_PURPLE);
			return false;
		}
		if (st.val(Multimouse.V_SCURRY) != 0L) {
			return !growBack(p, true);
		}
		Miraculous.setVal(p, Multimouse.V_SCURRY, now(p) + MultimouseConfig.SCURRY_TICKS);
		Miraculous.setAnim(p, Multimouse.ANIM_SCURRY);
		applySize(p);
		sparkle(p.serverLevel(), p.position().add(0, 0.3, 0), 14);
		sound(p, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 1.8f);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.9f);
		return true;
	}

	/** Scurry ends: full size again if there is room (else she stays small until there is). True if she grew. */
	static boolean growBack(ServerPlayer p, boolean pressed) {
		if (!roomToGrow(p)) {
			if (pressed) {
				Miraculous.say(p, "message.projecthero.multimouse.no_room", ChatFormatting.LIGHT_PURPLE);
			}
			return false;
		}
		Miraculous.setVal(p, Multimouse.V_SCURRY, 0L);
		Miraculous.setCooldown(p, MultimouseConfig.SCURRY, MultimouseConfig.SCURRY_COOLDOWN);
		applySize(p);
		sparkle(p.serverLevel(), p.position().add(0, 0.9, 0), 12);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
		return true;
	}

	/** Is there room for her full size where she stands? */
	public static boolean roomToGrow(ServerPlayer p) {
		double w = 0.3;
		AABB full = new AABB(p.getX() - w, p.getY(), p.getZ() - w, p.getX() + w, p.getY() + 1.8, p.getZ() + w).deflate(1.0e-3);
		return p.level().noCollision(p, full);
	}

	/** The size the synced state asks for: Multitude a quarter, Scurry a third, else her own. */
	public static double wantedScale(ServerPlayer p) {
		if (!Miraculous.transformedAs(p, Kwami.MULLO)) {
			return 1.0;
		}
		MiraculousState st = Miraculous.state(p);
		if (st.val(Multimouse.V_MULTI) != 0L) {
			return MultimouseConfig.MULTITUDE_SCALE;
		}
		if (st.val(Multimouse.V_SCURRY) != 0L) {
			return MultimouseConfig.SCURRY_SCALE;
		}
		return 1.0;
	}

	/** Puts her generic.scale (and the speed of being small) where the state says. */
	public static void applySize(ServerPlayer p) {
		double scale = wantedScale(p);
		if (scale >= 0.999) {
			PowerToggles.clearModifier(p, Attributes.SCALE, SCALE_ID);
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
			return;
		}
		PowerToggles.modifier(p, Attributes.SCALE, SCALE_ID, scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		double speed = scale <= MultimouseConfig.MULTITUDE_SCALE + 1.0e-3 ? MultimouseConfig.MULTITUDE_SPEED : MultimouseConfig.SCURRY_SPEED;
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Her full size back, no matter what (detransforming, death, logout, revoke). */
	static void clearSize(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.SCALE, SCALE_ID);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPEED_ID);
	}

	// ================================================================ Z: Mouse Swarm (Shift+Z: Multitude)

	/** Three tiny copies of her dart at her target for {@link MultimouseConfig#SWARM_TICKS}. Returns the copies. */
	public static List<MiniMouseEntity> swarm(ServerPlayer p) {
		LivingEntity t = aimed(p, MultimouseConfig.SWARM_RANGE, 10.0);
		if (t == null) {
			t = nearestInFront(p, 8.0);
		}
		Session s = session(p);
		s.swarm.clear();
		List<MiniMouseEntity> out = new ArrayList<>();
		ServerLevel level = p.serverLevel();
		Vec3 f = flatLook(p);
		Vec3 side = new Vec3(-f.z, 0, f.x);
		for (int i = 0; i < MultimouseConfig.SWARM_COUNT; i++) {
			MiniMouseEntity m = MultimouseEntityTypes.MINI.create(level);
			if (m == null) {
				continue;
			}
			Vec3 at = p.position().add(f.scale(0.6)).add(side.scale((i - 1) * 0.45));
			m.moveTo(at.x, p.getY(), at.z, p.getYRot(), 0f);
			m.setup(p, MiniMouseEntity.SWARM, MultimouseConfig.SWARM_TICKS, MultimouseConfig.SWARM_BITE, t);
			if (t != null) {
				m.setTarget(t);
			}
			level.addFreshEntity(m);
			s.swarm.add(m.getId());
			out.add(m);
			sparkle(level, at.add(0, 0.2, 0), 5);
		}
		Miraculous.setVal(p, Multimouse.V_SWARM, now(p) + MultimouseConfig.SWARM_TICKS);
		Miraculous.setAnim(p, Multimouse.ANIM_SWARM);
		Miraculous.setCooldown(p, MultimouseConfig.SWARM, MultimouseConfig.SWARM_COOLDOWN);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
		sound(p, SoundEvents.SILVERFISH_AMBIENT, 0.6f, 1.6f);
		return out;
	}

	// ================================================================ V: Mouse Hearing (toggle) / Shift+V: Gnaw

	/** Toggles her hearing (the rings are drawn by her own client). True: now on. */
	public static boolean hearing(ServerPlayer p) {
		boolean on = Miraculous.state(p).val(Multimouse.V_HEARING) == 0L;
		Miraculous.setVal(p, Multimouse.V_HEARING, on ? 1L : 0L);
		if (on) {
			Miraculous.setAnim(p, Multimouse.ANIM_HEARING);
			p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 1.9f);
		} else {
			Miraculous.setCooldown(p, MultimouseConfig.HEARING, MultimouseConfig.HEARING_COOLDOWN);
			p.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4f, 1.9f);
		}
		return on;
	}

	/** May Gnaw chew blocks here at all (the mod's terrain-damage config and the mobGriefing rule, as CATACLYSM)? */
	public static boolean canChew(ServerLevel level) {
		return AbilityHelpers.canGrief() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/**
	 * One block Gnaw can chew: v0.17.1 (user) any breakable block, stone included -- never bedrock, barriers or anything
	 * else unbreakable / wither-proof, never a block entity (a chest...) or a fluid, only where she may build (spawn
	 * protection, claims) and inside the world border.
	 */
	public static boolean chewable(ServerLevel level, ServerPlayer p, BlockPos pos) {
		if (!level.isLoaded(pos) || !p.mayBuild() || !level.mayInteract(p, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		BlockState st = level.getBlockState(pos);
		if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.is(BlockTags.WITHER_IMMUNE)) {
			return false;
		}
		return st.getDestroySpeed(level, pos) >= 0.0f;
	}

	/**
	 * Chew straight through what is in front of her: a 1-wide, 2-high hole up to {@link MultimouseConfig#GNAW_DEPTH} deep
	 * along the way she faces (straight down or up when she looks steeply that way: a 1x1 shaft). A column stops at the
	 * first block it cannot chew. The blocks drop as if mined. Returns how many were chewed.
	 */
	public static int gnaw(ServerPlayer p) {
		Miraculous.setAnim(p, Multimouse.ANIM_GNAW);
		Miraculous.setCooldown(p, MultimouseConfig.GNAW, MultimouseConfig.GNAW_COOLDOWN);
		ServerLevel level = p.serverLevel();
		sound(p, SoundEvents.SILVERFISH_STEP, 1.0f, 1.4f);
		if (!canChew(level)) {
			Miraculous.say(p, "message.projecthero.multimouse.gnaw_off", ChatFormatting.LIGHT_PURPLE);
			return 0;
		}
		float pitch = p.getXRot();
		BlockPos feet = p.blockPosition();
		List<BlockPos[]> columns = new ArrayList<>();
		if (pitch > 60f) {
			BlockPos[] c = new BlockPos[MultimouseConfig.GNAW_DEPTH];
			for (int i = 0; i < c.length; i++) {
				c[i] = feet.below(i + 1);
			}
			columns.add(c);
		} else if (pitch < -60f) {
			BlockPos top = BlockPos.containing(p.getX(), p.getY() + p.getBbHeight() + 0.01, p.getZ());
			BlockPos[] c = new BlockPos[MultimouseConfig.GNAW_DEPTH];
			for (int i = 0; i < c.length; i++) {
				c[i] = top.above(i);
			}
			columns.add(c);
		} else {
			Direction d = p.getDirection();
			BlockPos[] low = new BlockPos[MultimouseConfig.GNAW_DEPTH];
			BlockPos[] high = new BlockPos[MultimouseConfig.GNAW_DEPTH];
			for (int i = 0; i < low.length; i++) {
				low[i] = feet.relative(d, i + 1);
				high[i] = low[i].above();
			}
			columns.add(low);
			columns.add(high);
		}
		int n = 0;
		for (BlockPos[] col : columns) {
			for (BlockPos pos : col) {
				BlockState st = level.getBlockState(pos);
				if (st.isAir()) {
					continue;
				}
				if (!chewable(level, p, pos)) {
					break; // bedrock, a barrier, a chest...: the hole stops here
				}
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
				if (level.destroyBlock(pos, true, p)) {
					n++;
				}
			}
		}
		if (n > 0) {
			sound(p, SoundEvents.SILVERFISH_HURT, 0.8f, 1.6f);
			sound(p, SoundEvents.GRAVEL_BREAK, 0.8f, 1.5f);
		}
		return n;
	}

	// ================================================================ C: Rope Bind / Shift+C: Mouse Trap

	/** Throw the rope round the creature she aims at: tied up (rooted, unable to attack) for {@link MultimouseConfig#BIND_TICKS}. */
	public static @Nullable LivingEntity bind(ServerPlayer p) {
		LivingEntity t = aimed(p, MultimouseConfig.BIND_RANGE, MultimouseConfig.BIND_ASSIST_DEGREES);
		if (t == null) {
			Miraculous.say(p, "message.projecthero.multimouse.no_target", ChatFormatting.LIGHT_PURPLE);
			return null;
		}
		Session s = session(p);
		s.bindId = t.getId();
		s.bindAt = t.position();
		Miraculous.setVal(p, Multimouse.V_BIND, now(p) + MultimouseConfig.BIND_TICKS);
		Miraculous.setVal(p, Multimouse.V_BIND_ID, t.getId() + 1L);
		Miraculous.setAnim(p, Multimouse.ANIM_BIND);
		Miraculous.setCooldown(p, MultimouseConfig.BIND, MultimouseConfig.BIND_COOLDOWN);
		if (isBoss(t)) {
			AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, MultimouseConfig.BIND_TICKS, 2);
		} else {
			PunisherControl.stun(t, MultimouseConfig.BIND_TICKS);
		}
		AbilityHelpers.line(p.serverLevel(), chest(p), t.position().add(0, t.getBbHeight() * 0.5, 0), PINK, 4.0);
		sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.9f);
		sound(p, SoundEvents.LEASH_KNOT_PLACE, 1.0f, 0.8f);
		return t;
	}

	/** Gametests: is {@code e} tied up by her Rope Bind right now? */
	public static boolean bound(ServerPlayer p, LivingEntity e) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.bindId == e.getId() && Miraculous.state(p).val(Multimouse.V_BIND) > now(p);
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

	private static void tickBind(ServerPlayer p, MiraculousState st, Session s, long now) {
		long until = st.val(Multimouse.V_BIND);
		if (until == 0L) {
			return;
		}
		Entity e = s == null || s.bindId < 0 ? null : p.serverLevel().getEntity(s.bindId);
		if (now >= until || !(e instanceof LivingEntity t) || !t.isAlive()) {
			Miraculous.setVal(p, Multimouse.V_BIND, 0L);
			Miraculous.setVal(p, Multimouse.V_BIND_ID, 0L);
			if (s != null) {
				s.bindId = -1;
			}
			return;
		}
		if (!isBoss(t) && s.bindAt != null) {
			root(t, s.bindAt);
		}
		if (now % 4 == 0) {
			ServerLevel level = p.serverLevel();
			double h = t.getBbHeight();
			double r = t.getBbWidth() * 0.65 + 0.1;
			for (int i = 0; i < 4; i++) {
				double a = now * 0.3 + i * Math.PI / 2;
				level.sendParticles(PINK, t.getX() + Math.cos(a) * r, t.getY() + h * (0.3 + 0.15 * i), t.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
	}

	/** Set a rope snare on the ground where she looks (6 blocks) or at her feet; up to three (the oldest goes). Returns where. */
	public static Vec3 trap(ServerPlayer p) {
		Session s = session(p);
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, MultimouseConfig.TRAP_PLACE_RANGE);
		Vec3 at;
		if (bhr.getType() != HitResult.Type.MISS && bhr.getDirection() == Direction.UP) {
			at = bhr.getLocation();
		} else {
			at = p.position();
		}
		if (s.traps.size() >= MultimouseConfig.TRAP_MAX) {
			s.traps.remove(0);
		}
		s.traps.add(new double[] { at.x, at.y, at.z, now(p) + MultimouseConfig.TRAP_LIFE });
		syncTraps(p, s);
		Miraculous.setAnim(p, Multimouse.ANIM_TRAP);
		Miraculous.setCooldown(p, MultimouseConfig.TRAP, MultimouseConfig.TRAP_COOLDOWN);
		ring(p.serverLevel(), PINK, at.add(0, 0.08, 0), 0.45, 12);
		p.level().playSound(null, at.x, at.y, at.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0f, 1.2f);
		return at;
	}

	public static int traps(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.traps.size();
	}

	private static void syncTraps(ServerPlayer p, Session s) {
		for (int i = 0; i < MultimouseConfig.TRAP_MAX; i++) {
			double[] t = i < s.traps.size() ? s.traps.get(i) : null;
			Miraculous.setVec(p, Multimouse.X_TRAP + i, t == null ? null : new Vec3(t[0], t[1], t[2]));
		}
	}

	private static void tickTraps(ServerPlayer p, Session s, long now) {
		if (s.traps.isEmpty() && s.snared.isEmpty()) {
			return;
		}
		ServerLevel level = p.serverLevel();
		boolean changed = false;
		for (int i = s.traps.size() - 1; i >= 0; i--) {
			double[] t = s.traps.get(i);
			if (now >= (long) t[3]) {
				s.traps.remove(i);
				changed = true;
				continue;
			}
			Vec3 at = new Vec3(t[0], t[1], t[2]);
			AABB box = new AABB(at, at).inflate(MultimouseConfig.TRAP_TRIGGER, 1.0, MultimouseConfig.TRAP_TRIGGER);
			LivingEntity caught = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e instanceof Mob && !(e instanceof MiniMouseEntity) && !s.snared.containsKey(e.getId()) && HeroTargets.canHarm(p, e))) {
				double dx = e.getX() - at.x, dz = e.getZ() - at.z;
				if (dx * dx + dz * dz <= MultimouseConfig.TRAP_TRIGGER * MultimouseConfig.TRAP_TRIGGER && Math.abs(e.getY() - at.y) < 1.0) {
					caught = e;
					break;
				}
			}
			if (caught != null) {
				s.traps.remove(i);
				changed = true;
				spring(p, s, caught, at, now);
			} else if (now % 20 == 0) {
				ring(level, PINK, at.add(0, 0.06, 0), 0.4, 8);
			}
		}
		if (changed) {
			syncTraps(p, s);
		}
		// the snared stay put
		for (var it = s.snared.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			double[] v = en.getValue();
			Entity e = level.getEntity(en.getKey());
			if (now >= (long) v[3] || !(e instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			if (!isBoss(le)) {
				root(le, new Vec3(v[0], v[1], v[2]));
			}
		}
	}

	private static void spring(ServerPlayer p, Session s, LivingEntity e, Vec3 at, long now) {
		hit(p, e, at, MultimouseConfig.TRAP_DAMAGE, 0.0, 0.0);
		if (isBoss(e)) {
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, MultimouseConfig.TRAP_SNARE_TICKS, 2);
		} else {
			PunisherControl.stun(e, MultimouseConfig.TRAP_SNARE_TICKS);
			s.snared.put(e.getId(), new double[] { e.getX(), e.getY(), e.getZ(), now + MultimouseConfig.TRAP_SNARE_TICKS });
		}
		ServerLevel level = p.serverLevel();
		ring(level, PINK_BIG, at.add(0, 0.2, 0), 0.5, 14);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	/** Gametests: is {@code e} held by one of her snares? */
	public static boolean snared(ServerPlayer p, LivingEntity e) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.snared.containsKey(e.getId());
	}

	// ================================================================ incoming damage, falls

	/** The kit's {@code modifyIncomingDamage}: small (Scurry / Multitude), she is hard to hit -- a mob's hit or a projectile may miss. */
	public static float incoming(ServerPlayer p, DamageSource source, float amount) {
		if (!Multimouse.small(p)) {
			return amount;
		}
		boolean dodgeable = source.getEntity() instanceof LivingEntity || source.is(DamageTypes.ARROW) || source.is(DamageTypes.MOB_PROJECTILE);
		if (dodgeable && source.getEntity() != p && p.getRandom().nextFloat() < MultimouseConfig.SMALL_DODGE_CHANCE) {
			p.serverLevel().sendParticles(PINK, p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), 4, 0.15, 0.1, 0.15, 0.02);
			return 0f;
		}
		return amount;
	}

	/** Light on her feet: a quarter of an ordinary fall's damage, and never a killing fall. */
	public static float fall(ServerPlayer p, float amount) {
		float d = amount * MultimouseConfig.FALL_DAMAGE_FACTOR;
		if (d >= p.getHealth()) {
			d = Math.max(0f, p.getHealth() - 1f);
		}
		return d;
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		long now = now(p);
		MiraculousState st = Miraculous.state(p);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			tickLash(p, s, now);
		}
		if (st.val(Multimouse.V_COMBO) != 0L && now >= st.val(Multimouse.V_COMBO) && (s == null || (s.hitAt == 0L && !s.queued))) {
			Miraculous.setVal(p, Multimouse.V_COMBO, 0L);
		}
		for (String key : new String[] { Multimouse.V_LASSO, Multimouse.V_SWARM }) {
			long v = Miraculous.state(p).val(key);
			if (v != 0L && now >= v) {
				Miraculous.setVal(p, key, 0L);
				if (key.equals(Multimouse.V_LASSO)) {
					Miraculous.setVal(p, Multimouse.V_LASSO_ID, 0L);
				}
			}
		}
		tickTwirl(p, Miraculous.state(p), s, now);
		tickSwing(p, Miraculous.state(p), s, now);
		tickBind(p, Miraculous.state(p), s, now);
		if (s != null) {
			tickTraps(p, s, now);
		}
		// Scurry runs out (she grows back once there is room)
		long scurry = Miraculous.state(p).val(Multimouse.V_SCURRY);
		if (scurry != 0L && now >= scurry) {
			growBack(p, false);
		}
		// quiet as a mouse: crouching, mobs farther than 6 blocks lose track of her
		if (p.isCrouching() && now % 5 == 0) {
			double r = MultimouseConfig.QUIET_SCAN;
			double q = MultimouseConfig.QUIET_RANGE;
			for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), m -> m.getTarget() == p)) {
				if (m.distanceToSqr(p) > q * q) {
					PunisherControl.suppressTarget(m, 20);
				}
			}
		}
		applySize(p);
	}

	/** Detransformation / death / logout: every running move ends, the snares and the swarm go, her size comes back. */
	static void endAll(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			for (int id : s.swarm) {
				if (p.serverLevel().getEntity(id) instanceof MiniMouseEntity m) {
					m.pop();
				}
			}
		}
		clearSize(p);
		Miraculous.edit(p, n -> {
			n.vals.keySet().removeIf(k -> k.startsWith("mm_"));
			n.vecs.keySet().removeIf(k -> k.startsWith("mm_"));
		});
		SESSIONS.remove(p.getUUID());
	}

	/** Gametests: whether she holds Mullo and is transformed (shorthand). */
	public static boolean active(Player p) {
		return Miraculous.transformedAs(p, Kwami.MULLO);
	}
}
