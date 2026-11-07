package com.projecthero.mod.miraculous.ryuko;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3: Ryuko's moves. Every public move is called by {@link RyukoKit#handle} once its cooldown is ready, and by the
 * gametests directly. Moves that run over time keep a per-player {@link Session} (entity ids, never entities), dropped by
 * {@link RyukoKit#forget} / {@link RyukoKit#clearSessionState}. Every hit goes through {@link #hit}: the mod-wide targeting
 * rules, bosses capped at {@link RyukoConfig#BOSS_MAX_FRACTION_PER_HIT} and never knocked about.
 */
public final class RyukoMoves {
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.86f, 0.1f, 0.1f), 1.1f);
	public static final DustParticleOptions WIND = new DustParticleOptions(new Vector3f(0.85f, 0.97f, 0.93f), 1.3f);
	public static final DustParticleOptions WATER = new DustParticleOptions(new Vector3f(0.25f, 0.66f, 1.0f), 1.3f);
	public static final DustParticleOptions BOLT = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.35f), 1.3f);

	static final class Session {
		long lungeEnd;
		Vec3 lungeDir;
		final Set<Integer> lungeHit = new HashSet<>();
		long slideEnd;
		final Set<Integer> slideHit = new HashSet<>();
		Vec3 sword;
		Vec3 swordDir;
		double swordTravelled;
		boolean swordBack;
		final Set<Integer> swordHit = new HashSet<>();
		boolean flying;
		boolean wasInvisible;
		final Map<Integer, Long> formHit = new HashMap<>();
		int parried = -1;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private RyukoMoves() {
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

	private static Vec3 flatLook(Player p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static Vec3 chest(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static ParticleOptions dust(int element) {
		return element == Ryuko.WATER ? WATER : element == Ryuko.LIGHTNING ? BOLT : WIND;
	}

	static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= RyukoConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	public static boolean hit(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!HeroTargets.canHarm(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * RyukoConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
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
		owner.serverLevel().sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 5, 0.25, 0.3, 0.25, 0.2);
		owner.serverLevel().sendParticles(RED, c.x, c.y, c.z, 4, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	/** Everything she may hurt within {@code radius} of {@code at}, excluding herself. */
	private static List<LivingEntity> around(ServerPlayer p, Vec3 at, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), le -> le != p && le.isAlive())) {
			if (mid(e).distanceToSqr(at) <= radius * radius && HeroTargets.canHarm(p, e)) {
				out.add(e);
			}
		}
		return out;
	}

	/** Within {@code range} and in a cone in front of her. */
	private static List<LivingEntity> ahead(ServerPlayer p, double range, double dot) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : around(p, eye, range + 1.0)) {
			Vec3 to = mid(e).subtract(eye);
			if (to.lengthSqr() < 1.0 || to.normalize().dot(look) >= dot) {
				out.add(e);
			}
		}
		return out;
	}

	private static void zap(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	private static void spray(ServerLevel level, ParticleOptions o, Vec3 from, Vec3 dir, double range, int perBlock) {
		for (double d = 1.0; d < range; d += 1.0) {
			Vec3 q = from.add(dir.scale(d));
			double w = 0.15 + d * 0.12;
			level.sendParticles(o, q.x, q.y, q.z, perBlock, w, w, w, 0.0);
		}
	}

	// ================================================================ R: the blade

	/** One swing of the combo: returns how many it hit. */
	public static int strike(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		long now = now(p);
		int step = st.val(Ryuko.V_COMBO_UNTIL) > now ? (int) st.val(Ryuko.V_COMBO) : 0;
		boolean last = step >= 2;
		Miraculous.setAnim(p, last ? Ryuko.ANIM_STRIKE_3 : step == 1 ? Ryuko.ANIM_STRIKE_2 : Ryuko.ANIM_STRIKE);
		Miraculous.setVal(p, Ryuko.V_COMBO, last ? 0L : step + 1L);
		Miraculous.setVal(p, Ryuko.V_COMBO_UNTIL, last ? 0L : now + RyukoConfig.STRIKE_WINDOW);
		Miraculous.setCooldown(p, RyukoConfig.STRIKE, last ? RyukoConfig.STRIKE_COOLDOWN : RyukoConfig.STRIKE_STEP_COOLDOWN);
		int n = 0;
		for (LivingEntity e : ahead(p, RyukoConfig.STRIKE_REACH, 0.45)) {
			if (hit(p, e, p.position(), last ? RyukoConfig.STRIKE_FINISHER_DAMAGE : RyukoConfig.STRIKE_DAMAGE, last ? 0.7 : 0.15, 0.0)) {
				n++;
			}
		}
		Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(1.8));
		p.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y - 0.3, c.z, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.3f + step * 0.15f);
		return n;
	}

	public static void lunge(ServerPlayer p) {
		Session s = session(p);
		s.lungeDir = flatLook(p);
		s.lungeEnd = now(p) + RyukoConfig.LUNGE_TICKS;
		s.lungeHit.clear();
		Miraculous.setVal(p, Ryuko.V_LUNGE, s.lungeEnd);
		Miraculous.setAnim(p, Ryuko.ANIM_LUNGE);
		Miraculous.setCooldown(p, RyukoConfig.LUNGE, RyukoConfig.LUNGE_COOLDOWN);
		Miraculous.safeLanding(p);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.4f);
	}

	private static void tickLunge(ServerPlayer p, Session s, long now) {
		if (now >= s.lungeEnd) {
			s.lungeDir = null;
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().scale(0.25));
			return;
		}
		AbilityHelpers.launchSelf(p, new Vec3(s.lungeDir.x * RyukoConfig.LUNGE_SPEED, Math.min(0.05, p.getDeltaMovement().y), s.lungeDir.z * RyukoConfig.LUNGE_SPEED));
		p.serverLevel().sendParticles(RED, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.2, 0.3, 0.2, 0.0);
		for (LivingEntity e : around(p, mid(p).add(s.lungeDir.scale(1.2)), 2.0)) {
			if (s.lungeHit.add(e.getId())) {
				hit(p, e, p.position(), RyukoConfig.LUNGE_DAMAGE, 0.5, 0.1);
			}
		}
	}

	// ================================================================ G: the element's strike and burst

	public static void elementStrike(ServerPlayer p) {
		int el = Ryuko.element(p);
		ServerLevel level = p.serverLevel();
		Miraculous.setAnim(p, Ryuko.ANIM_ELEMENT);
		Miraculous.setCooldown(p, RyukoConfig.ELEMENT_STRIKE, RyukoConfig.STRIKE_ELEMENT_COOLDOWN);
		Vec3 eye = p.getEyePosition().add(0, -0.25, 0);
		Vec3 look = p.getLookAngle();
		switch (el) {
			case Ryuko.WATER -> {
				for (LivingEntity e : ahead(p, RyukoConfig.WATER_STRIKE_RANGE, 0.6)) {
					if (hit(p, e, p.position(), RyukoConfig.WATER_STRIKE_DAMAGE, 0.4, 0.0)) {
						e.clearFire();
						AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, RyukoConfig.WATER_SLOW_TICKS, 1);
					}
				}
				spray(level, WATER, eye, look, RyukoConfig.WATER_STRIKE_RANGE, 3);
				spray(level, ParticleTypes.SPLASH, eye, look, RyukoConfig.WATER_STRIKE_RANGE, 3);
				sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.9f, 1.3f);
			}
			case Ryuko.LIGHTNING -> {
				LivingEntity first = AbilityHelpers.raycastEntity(p, RyukoConfig.LIGHTNING_RANGE);
				Vec3 end = first != null ? mid(first) : AbilityHelpers.aimPoint(p, RyukoConfig.LIGHTNING_RANGE);
				AbilityHelpers.line(level, eye, end, BOLT, 3.0);
				AbilityHelpers.line(level, eye, end, ParticleTypes.ELECTRIC_SPARK, 2.0);
				if (first != null && HeroTargets.canHarm(p, first)) {
					hit(p, first, eye, RyukoConfig.LIGHTNING_STRIKE_DAMAGE, 0.2, 0.0);
					Set<Integer> done = new HashSet<>(Set.of(first.getId()));
					LivingEntity from = first;
					for (int i = 0; i < 2 && from != null; i++) {
						LivingEntity next = null;
						double best = Double.MAX_VALUE;
						for (LivingEntity e : around(p, mid(from), RyukoConfig.LIGHTNING_CHAIN_RANGE)) {
							double d = e.distanceToSqr(from);
							if (!done.contains(e.getId()) && d < best) {
								best = d;
								next = e;
							}
						}
						if (next != null) {
							done.add(next.getId());
							AbilityHelpers.line(level, mid(from), mid(next), BOLT, 3.0);
							hit(p, next, mid(from), RyukoConfig.LIGHTNING_CHAIN_DAMAGE, 0.1, 0.0);
						}
						from = next;
					}
				}
				sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.7f);
			}
			default -> {
				for (LivingEntity e : ahead(p, RyukoConfig.WIND_STRIKE_RANGE, 0.75)) {
					hit(p, e, p.position(), RyukoConfig.WIND_STRIKE_DAMAGE, 1.5, 0.35);
				}
				spray(level, WIND, eye, look, RyukoConfig.WIND_STRIKE_RANGE, 3);
				spray(level, ParticleTypes.CLOUD, eye, look, RyukoConfig.WIND_STRIKE_RANGE, 1);
				sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
			}
		}
	}

	public static void elementBurst(ServerPlayer p) {
		int el = Ryuko.element(p);
		ServerLevel level = p.serverLevel();
		Vec3 c = mid(p);
		long now = now(p);
		Miraculous.setAnim(p, Ryuko.ANIM_BURST);
		Miraculous.setCooldown(p, RyukoConfig.ELEMENT_BURST, RyukoConfig.BURST_COOLDOWN);
		switch (el) {
			case Ryuko.WATER -> {
				for (LivingEntity e : around(p, c, RyukoConfig.BURST_RADIUS + 1.0)) {
					if (hit(p, e, c, RyukoConfig.WATER_BURST_DAMAGE, 1.9, 0.3)) {
						e.clearFire();
					}
				}
				for (int i = 0; i < 40; i++) {
					double a = i * Math.PI * 2 / 40;
					double r = RyukoConfig.BURST_RADIUS * 0.7;
					level.sendParticles(WATER, c.x + Math.cos(a) * r, p.getY() + 0.3, c.z + Math.sin(a) * r, 3, 0.3, 0.3, 0.3, 0.0);
					level.sendParticles(ParticleTypes.SPLASH, c.x + Math.cos(a) * r, p.getY() + 0.3, c.z + Math.sin(a) * r, 4, 0.3, 0.3, 0.3, 0.1);
				}
				sound(p, SoundEvents.GENERIC_SPLASH, 1.2f, 0.8f);
			}
			case Ryuko.LIGHTNING -> {
				List<LivingEntity> near = around(p, c, RyukoConfig.BURST_RADIUS);
				for (LivingEntity e : near) {
					hit(p, e, c, RyukoConfig.LIGHTNING_BURST_DAMAGE, 0.6, 0.2);
					zap(level, e.position());
				}
				if (near.isEmpty()) {
					zap(level, p.position().add(flatLook(p).scale(3.0)));
				}
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 2.2, 1.0, 2.2, 0.1);
				sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4f, 1.5f);
			}
			default -> {
				Miraculous.setVal(p, Ryuko.V_WHIRL, now + RyukoConfig.WHIRL_TICKS);
				Miraculous.setVec(p, Ryuko.X_WHIRL, p.position());
				sound(p, SoundEvents.ELYTRA_FLYING, 0.7f, 1.2f);
			}
		}
	}

	/** Whirlwind: everything near is dragged to its middle and lifted, then flung. */
	private static void tickWhirl(ServerPlayer p, MiraculousState st, long now) {
		long until = st.val(Ryuko.V_WHIRL);
		if (until == 0L) {
			return;
		}
		Vec3 c = Miraculous.vec(st, Ryuko.X_WHIRL);
		if (c == null) {
			Miraculous.setVal(p, Ryuko.V_WHIRL, 0L);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 eye = c.add(0, 1.2, 0);
		if (now >= until) {
			Miraculous.setVal(p, Ryuko.V_WHIRL, 0L);
			for (LivingEntity e : around(p, eye, RyukoConfig.BURST_RADIUS + 1.0)) {
				hit(p, e, eye, RyukoConfig.WIND_BURST_DAMAGE, 1.2, 0.5);
			}
			level.sendParticles(ParticleTypes.CLOUD, eye.x, eye.y, eye.z, 30, 1.5, 1.0, 1.5, 0.1);
			return;
		}
		for (LivingEntity e : around(p, eye, RyukoConfig.BURST_RADIUS)) {
			if (!isBoss(e)) {
				Vec3 in = eye.subtract(mid(e));
				AbilityHelpers.push(e, in.normalize().scale(0.16).add(0, 0.05, 0));
			}
		}
		for (int i = 0; i < 6; i++) {
			double a = now * 0.5 + i * Math.PI / 3;
			double r = 1.0 + (i % 3) * 1.1;
			level.sendParticles(i % 2 == 0 ? WIND : ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + i * 0.5, c.z + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	// ================================================================ X: the element's dash; Shift+X: Storm Rise

	public static void elementDash(ServerPlayer p) {
		int el = Ryuko.element(p);
		ServerLevel level = p.serverLevel();
		Miraculous.setCooldown(p, RyukoConfig.ELEMENT_DASH, RyukoConfig.DASH_COOLDOWN);
		Miraculous.safeLanding(p);
		Vec3 f = flatLook(p);
		switch (el) {
			case Ryuko.WATER -> {
				Session s = session(p);
				s.slideEnd = now(p) + RyukoConfig.SLIDE_TICKS;
				s.slideHit.clear();
				Miraculous.setVal(p, Ryuko.V_SLIDE, s.slideEnd);
				sound(p, SoundEvents.PLAYER_SPLASH, 0.9f, 1.3f);
			}
			case Ryuko.LIGHTNING -> {
				Vec3 from = p.position().add(0, 0.5, 0);
				Vec3 far = from.add(f.scale(RyukoConfig.BLINK_RANGE));
				var clip = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				Vec3 to = clip.getType() == HitResult.Type.MISS ? far : clip.getLocation().subtract(f.scale(0.6));
				for (LivingEntity e : around(p, from.lerp(to, 0.5), from.distanceTo(to) * 0.5 + 1.5)) {
					Vec3 d = mid(e).subtract(from);
					double along = d.dot(f);
					if (along > -0.5 && d.subtract(f.scale(along)).lengthSqr() < 3.0) {
						hit(p, e, from, RyukoConfig.BLINK_DAMAGE, 0.2, 0.0);
					}
				}
				AbilityHelpers.line(level, from.add(0, 0.6, 0), to.add(0, 0.6, 0), BOLT, 3.0);
				p.teleportTo(level, to.x, p.getY(), to.z, p.getYRot(), p.getXRot());
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y + 0.6, to.z, 18, 0.3, 0.5, 0.3, 0.15);
				sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.8f);
			}
			default -> {
				AbilityHelpers.launchSelf(p, new Vec3(f.x * 1.15, 0.95, f.z * 1.15));
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 14, 0.4, 0.1, 0.4, 0.05);
				sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.5f);
			}
		}
	}

	private static void tickSlide(ServerPlayer p, Session s, long now) {
		if (now >= s.slideEnd) {
			s.slideEnd = 0L;
			return;
		}
		double speed = p.isInWater() ? 1.6 : 1.05;
		Vec3 f = flatLook(p);
		AbilityHelpers.launchSelf(p, new Vec3(f.x * speed, Math.min(0.0, p.getDeltaMovement().y), f.z * speed));
		p.clearFire();
		p.serverLevel().sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.4, 0.05, 0.4, 0.1);
		p.serverLevel().sendParticles(WATER, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.3, 0.1, 0.3, 0.0);
		for (LivingEntity e : around(p, mid(p), 1.8)) {
			if (s.slideHit.add(e.getId())) {
				hit(p, e, p.position(), RyukoConfig.SLIDE_DAMAGE, 0.9, 0.2);
			}
		}
	}

	public static void rise(ServerPlayer p) {
		Miraculous.setCooldown(p, RyukoConfig.RISE, RyukoConfig.RISE_COOLDOWN);
		Miraculous.setVal(p, Ryuko.V_GLIDE, now(p) + RyukoConfig.GLIDE_TICKS);
		Miraculous.safeLanding(p);
		AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, RyukoConfig.RISE_SPEED, p.getDeltaMovement().z));
		p.serverLevel().sendParticles(dust(Ryuko.element(p)), p.getX(), p.getY(), p.getZ(), 20, 0.5, 0.2, 0.5, 0.0);
		sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 1.2f);
	}

	// ================================================================ Z: Sword Throw; Shift+Z: DRAGON FORM

	public static void swordThrow(ServerPlayer p) {
		Session s = session(p);
		s.sword = chest(p);
		s.swordDir = p.getLookAngle();
		s.swordTravelled = 0.0;
		s.swordBack = false;
		s.swordHit.clear();
		long now = now(p);
		Miraculous.setVal(p, Ryuko.V_THROW, now + 100L);
		Miraculous.setVec(p, Ryuko.X_SWORD, s.sword);
		Miraculous.setAnim(p, Ryuko.ANIM_THROW);
		Miraculous.setCooldown(p, RyukoConfig.THROW, RyukoConfig.THROW_COOLDOWN);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.3f);
	}

	private static void tickSword(ServerPlayer p, Session s) {
		ServerLevel level = p.serverLevel();
		if (s.swordBack) {
			Vec3 to = chest(p).subtract(s.sword);
			if (to.length() < 1.6) {
				endSword(p, s);
				sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.5f);
				return;
			}
			s.sword = s.sword.add(to.normalize().scale(RyukoConfig.THROW_SPEED * 1.3));
		} else {
			Vec3 next = s.sword.add(s.swordDir.scale(RyukoConfig.THROW_SPEED));
			var clip = level.clip(new ClipContext(s.sword, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			s.sword = clip.getType() == HitResult.Type.MISS ? next : clip.getLocation();
			s.swordTravelled += RyukoConfig.THROW_SPEED;
			if (clip.getType() != HitResult.Type.MISS || s.swordTravelled >= RyukoConfig.THROW_RANGE) {
				s.swordBack = true;
			}
			for (LivingEntity e : around(p, s.sword, 1.5)) {
				if (s.swordHit.add(e.getId()) && hit(p, e, s.sword, RyukoConfig.THROW_DAMAGE, 0.4, 0.0)) {
					s.swordBack = true;
				}
			}
		}
		Miraculous.setVec(p, Ryuko.X_SWORD, s.sword);
	}

	private static void endSword(ServerPlayer p, Session s) {
		s.sword = null;
		Miraculous.setVal(p, Ryuko.V_THROW, 0L);
		Miraculous.setVec(p, Ryuko.X_SWORD, null);
	}

	/** Her special: for 8 s she is the element itself, flying, untouchable, hurting what she passes through. */
	public static void dragonForm(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		Miraculous.setVal(p, Ryuko.V_FORM, now + RyukoConfig.FORM_TICKS);
		Miraculous.setAnim(p, Ryuko.ANIM_FORM);
		Miraculous.setCooldown(p, RyukoConfig.FORM, RyukoConfig.FORM_COOLDOWN);
		Miraculous.startCountdown(p);
		Miraculous.say(p, "message.projecthero.ryuko.form." + Ryuko.element(p), ChatFormatting.RED);
		s.formHit.clear();
		s.wasInvisible = p.hasEffect(MobEffects.INVISIBILITY);
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, RyukoConfig.FORM_TICKS + 5, 0, false, false, false));
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			s.flying = true;
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.getAbilities().setFlyingSpeed(RyukoConfig.FORM_FLY_SPEED);
			p.onUpdateAbilities();
		}
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.5, 0));
		Vec3 c = mid(p);
		p.serverLevel().sendParticles(dust(Ryuko.element(p)), c.x, c.y, c.z, 50, 0.7, 1.0, 0.7, 0.05);
		sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.5f, 1.6f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
	}

	public static void endForm(ServerPlayer p) {
		if (Miraculous.state(p).val(Ryuko.V_FORM) == 0L) {
			return;
		}
		Miraculous.setVal(p, Ryuko.V_FORM, 0L);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			if (!s.wasInvisible) {
				p.removeEffect(MobEffects.INVISIBILITY);
			}
			if (s.flying) {
				s.flying = false;
				if (!p.getAbilities().instabuild && !p.isSpectator()) {
					p.getAbilities().mayfly = false;
					p.getAbilities().flying = false;
					p.getAbilities().setFlyingSpeed(0.05f);
					p.onUpdateAbilities();
				}
			}
		}
		p.resetFallDistance();
		Miraculous.safeLanding(p);
		Vec3 c = mid(p);
		p.serverLevel().sendParticles(dust(Ryuko.element(p)), c.x, c.y, c.z, 30, 0.6, 0.8, 0.6, 0.05);
	}

	private static void tickForm(ServerPlayer p, MiraculousState st, Session s, long now) {
		if (now >= st.val(Ryuko.V_FORM)) {
			endForm(p);
			return;
		}
		if (s.flying) {
			p.getAbilities().mayfly = true;
		}
		p.resetFallDistance();
		int el = Ryuko.element(p);
		Vec3 c = mid(p);
		ServerLevel level = p.serverLevel();
		level.sendParticles(dust(el), c.x, c.y, c.z, 8, 0.5, 0.7, 0.5, 0.0);
		for (LivingEntity e : around(p, c, RyukoConfig.FORM_RADIUS)) {
			Long last = s.formHit.get(e.getId());
			if ((last == null || now - last >= RyukoConfig.FORM_HIT_EVERY) && hit(p, e, c, RyukoConfig.FORM_DAMAGE, 0.5, 0.2)) {
				s.formHit.put(e.getId(), now);
				if (el == Ryuko.WATER) {
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
				}
			}
		}
	}

	// ================================================================ V: Switch Element; Shift+V: Storm Guard

	public static void switchElement(ServerPlayer p) {
		int next = (Ryuko.element(p) + 1) % 3;
		Miraculous.setVal(p, Ryuko.V_ELEMENT, next);
		Miraculous.setCooldown(p, RyukoConfig.SWITCH, RyukoConfig.SWITCH_COOLDOWN);
		Miraculous.say(p, "message.projecthero.ryuko.element." + next, next == Ryuko.WATER ? ChatFormatting.AQUA : next == Ryuko.LIGHTNING ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
		Vec3 c = mid(p);
		p.serverLevel().sendParticles(dust(next), c.x, c.y, c.z, 16, 0.4, 0.6, 0.4, 0.0);
		sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.0f + next * 0.25f);
	}

	public static void guard(ServerPlayer p) {
		Miraculous.setVal(p, Ryuko.V_GUARD, now(p) + RyukoConfig.GUARD_TICKS);
		Miraculous.setVal(p, Ryuko.V_GUARD_ELEMENT, Ryuko.element(p));
		Miraculous.setCooldown(p, RyukoConfig.GUARD, RyukoConfig.GUARD_COOLDOWN);
		sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
	}

	private static void tickGuard(ServerPlayer p, MiraculousState st, long now) {
		int el = (int) st.val(Ryuko.V_GUARD_ELEMENT);
		ServerLevel level = p.serverLevel();
		Vec3 c = mid(p);
		for (int i = 0; i < 3; i++) {
			double a = now * 0.35 + i * Math.PI * 2 / 3;
			level.sendParticles(dust(el), c.x + Math.cos(a) * 1.1, c.y + Math.sin(now * 0.2 + i), c.z + Math.sin(a) * 1.1, 1, 0.03, 0.03, 0.03, 0.0);
		}
		if (el == Ryuko.WATER) {
			p.clearFire();
			if (now % 20 == 0) {
				p.heal(1f);
			}
		} else if (el == Ryuko.WIND) {
			double r = RyukoConfig.GUARD_RADIUS;
			for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0), Entity::isAlive)) {
				Vec3 v = proj.getDeltaMovement();
				if (proj.getOwner() == p || proj.position().distanceTo(c) > r + 0.5 && proj.position().add(v).distanceTo(c) > r + 0.5
						|| v.lengthSqr() > 1.0e-4 && v.dot(c.subtract(proj.position())) <= 0.0) {
					continue;
				}
				Vec3 at = proj.position();
				proj.deflect(ProjectileDeflection.REVERSE, p, p, true);
				proj.setOwner(p);
				if (proj instanceof AbstractArrow arrow) {
					arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
				}
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.05);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, 1.8f);
			}
		}
	}

	// ================================================================ C: Parry; Shift+C: Riposte

	public static void parry(ServerPlayer p) {
		Miraculous.setVal(p, Ryuko.V_PARRY, now(p) + RyukoConfig.PARRY_TICKS);
		Miraculous.setAnim(p, Ryuko.ANIM_PARRY);
		Miraculous.setCooldown(p, RyukoConfig.PARRY, RyukoConfig.PARRY_COOLDOWN);
		sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.6f);
	}

	/** True if a riposte landed. */
	public static boolean riposte(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		long now = now(p);
		if (st.val(Ryuko.V_RIPOSTE) <= now) {
			Miraculous.say(p, "message.projecthero.ryuko.no_parry", ChatFormatting.GRAY);
			return false;
		}
		Session s = session(p);
		Entity e = s.parried >= 0 ? p.level().getEntity(s.parried) : null;
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(p) > RyukoConfig.RIPOSTE_REACH) {
			Miraculous.say(p, "message.projecthero.ryuko.no_target", ChatFormatting.GRAY);
			return false;
		}
		Miraculous.setVal(p, Ryuko.V_RIPOSTE, 0L);
		Miraculous.setAnim(p, Ryuko.ANIM_RIPOSTE);
		Miraculous.setCooldown(p, RyukoConfig.RIPOSTE, RyukoConfig.RIPOSTE_COOLDOWN);
		Vec3 to = mid(target).subtract(mid(p));
		if (to.length() > 2.5) {
			AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(1.8, to.length() * 0.45)));
		}
		sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.9f);
		return hit(p, target, p.position(), RyukoConfig.RIPOSTE_DAMAGE, 1.1, 0.2);
	}

	// ================================================================ what reaches her

	static float incoming(ServerPlayer p, DamageSource source, float amount) {
		if (!Ryuko.is(p)) {
			return amount;
		}
		if (Ryuko.inForm(p)) {
			return 0f;
		}
		Entity from = source.getEntity();
		if (Ryuko.parrying(p) && from != null && inFront(p, source)) {
			long now = now(p);
			Miraculous.setVal(p, Ryuko.V_PARRY, 0L);
			Miraculous.setVal(p, Ryuko.V_RIPOSTE, now + RyukoConfig.RIPOSTE_WINDOW);
			session(p).parried = from.getId();
			Vec3 c = mid(p).add(flatLook(p).scale(0.8));
			p.serverLevel().sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.2, 0.3, 0.2, 0.3);
			sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 1.5f);
			return 0f;
		}
		if (Ryuko.guarding(p) && Miraculous.state(p).val(Ryuko.V_GUARD_ELEMENT) == Ryuko.LIGHTNING && from instanceof LivingEntity attacker && attacker != p) {
			hit(p, attacker, mid(p), RyukoConfig.GUARD_SHOCK_DAMAGE, 0.4, 0.0);
			p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 12, 0.3, 0.5, 0.3, 0.15);
		}
		return amount;
	}

	private static boolean inFront(ServerPlayer p, DamageSource source) {
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		if (from == null) {
			return false;
		}
		Vec3 to = new Vec3(from.getX() - p.getX(), 0, from.getZ() - p.getZ());
		return to.lengthSqr() < 1.0e-4 || to.normalize().dot(flatLook(p)) >= 0.25;
	}

	// ================================================================ tick and end

	static void tick(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		long now = now(p);
		Session s = SESSIONS.get(p.getUUID());
		tickWhirl(p, st, now);
		if (st.val(Ryuko.V_GUARD) > now) {
			tickGuard(p, st, now);
		}
		if (st.val(Ryuko.V_GLIDE) != 0L) {
			p.resetFallDistance();
			if (p.onGround() && now > st.val(Ryuko.V_GLIDE) - RyukoConfig.GLIDE_TICKS + 10 || st.val(Ryuko.V_GLIDE) <= now) {
				Miraculous.setVal(p, Ryuko.V_GLIDE, 0L);
			}
		}
		if (s == null) {
			return;
		}
		if (s.lungeDir != null) {
			tickLunge(p, s, now);
		}
		if (s.slideEnd != 0L) {
			tickSlide(p, s, now);
		}
		if (s.sword != null) {
			tickSword(p, s);
		}
		if (st.val(Ryuko.V_FORM) != 0L) {
			tickForm(p, st, s, now);
		}
	}

	static void endAll(ServerPlayer p) {
		endForm(p);
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.lungeDir = null;
			s.slideEnd = 0L;
			s.sword = null;
		}
		for (String key : new String[] { Ryuko.V_COMBO, Ryuko.V_COMBO_UNTIL, Ryuko.V_LUNGE, Ryuko.V_WHIRL, Ryuko.V_SLIDE, Ryuko.V_GLIDE, Ryuko.V_THROW,
				Ryuko.V_GUARD, Ryuko.V_PARRY, Ryuko.V_RIPOSTE }) {
			Miraculous.setVal(p, key, 0L);
		}
		Miraculous.setVec(p, Ryuko.X_SWORD, null);
		Miraculous.setVec(p, Ryuko.X_WHIRL, null);
	}
}
