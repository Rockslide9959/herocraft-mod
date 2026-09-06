package com.projecthero.mod.hero.power;

import java.util.List;
import java.util.function.Predicate;

import com.projecthero.mod.hero.HeroConfig;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared, server-side helpers for experimental ability handlers: raycasting, config-gated damage,
 * knockback, self-launch, particle drawing. Deliberately does not touch any Thor class or state.
 *
 * <p>Everything that can affect other players is gated: {@link #hurt} respects
 * {@link HeroConfig#abilityPvpDamage}, and {@link #applyControl} halves crowd-control durations
 * against players (and skips them entirely if
 * {@link HeroConfig#abilityHardCrowdControlOnPlayers} is off).
 */
public final class AbilityHelpers {
	public static final double DEFAULT_RANGE = 24.0;

	private AbilityHelpers() {
	}

	// ---------------- targeting ----------------

	public static LivingEntity raycastEntity(ServerPlayer player, double range) {
		HitResult hit = ProjectileUtil.getHitResultOnViewVector(player,
				e -> e != player && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand), range);
		return hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le ? le : null;
	}

	public static BlockHitResult raycastBlock(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		return player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
	}

	/** Where the player is aiming: an entity, a block face, or the max-range point. */
	public static Vec3 aimPoint(ServerPlayer player, double range) {
		LivingEntity e = raycastEntity(player, range);
		if (e != null) {
			return e.position().add(0, e.getBbHeight() * 0.5, 0);
		}
		BlockHitResult bhr = raycastBlock(player, range);
		if (bhr.getType() != HitResult.Type.MISS) {
			return bhr.getLocation();
		}
		return player.getEyePosition().add(player.getLookAngle().scale(range));
	}

	public static List<LivingEntity> living(ServerLevel level, Vec3 center, double radius, Predicate<LivingEntity> filter) {
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
				center.x - radius, center.y - radius, center.z - radius,
				center.x + radius, center.y + radius, center.z + radius);
		return level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && e.distanceToSqr(center) <= radius * radius && filter.test(e));
	}

	public static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
		boolean pvp = player.getServer() != null && player.getServer().isPvpAllowed() && HeroConfig.get().abilityPvpDamage;
		return living(level(player), center, radius, e -> e != player
				&& !(e instanceof ArmorStand)
				&& (!(e instanceof Player) || pvp));
	}

	// ---------------- damage / control ----------------

	public static boolean hurt(ServerPlayer source, LivingEntity target, float amount) {
		return hurt(source, target, source.level().damageSources().playerAttack(source), amount);
	}

	public static boolean hurt(ServerPlayer source, LivingEntity target, DamageSource damageSource, float amount) {
		if (target == source || !target.isAlive()) {
			return false;
		}
		if (target instanceof Player) {
			if (!HeroConfig.get().abilityPvpDamage
					|| source.getServer() == null || !source.getServer().isPvpAllowed()) {
				return false;
			}
		}
		return target.hurt(damageSource, amount);
	}

	public static DamageSource fire(ServerPlayer source) {
		return source.level().damageSources().source(DamageTypes.IN_FIRE, source);
	}

	public static DamageSource freeze(ServerPlayer source) {
		return source.level().damageSources().source(DamageTypes.FREEZE, source);
	}

	public static DamageSource kinetic(ServerPlayer source) {
		return source.level().damageSources().source(DamageTypes.PLAYER_ATTACK, source);
	}

	public static void knockbackFrom(LivingEntity target, Vec3 origin, double strength) {
		double dx = target.getX() - origin.x;
		double dz = target.getZ() - origin.z;
		double dist = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
		target.knockback(strength, -dx / dist, -dz / dist);
		target.hurtMarked = true;
	}

	public static void push(LivingEntity target, Vec3 velocity) {
		target.setDeltaMovement(target.getDeltaMovement().add(velocity));
		target.hurtMarked = true;
	}

	/** Slow a target: Slowness III for 7 seconds (softened against players by {@link #applyControl}). */
	public static void slow7s(LivingEntity target) {
		applyControl(target, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 140, 2);
	}

	/**
	 * A visible aura around the player's whole body, ~4 times a second, to signal an active stance
	 * (fire body, rock armor, aquatic mode, ...). Cheap: call it unconditionally from a toggle tick.
	 */
	public static void modeAura(ServerPlayer player, ParticleOptions particle, int count) {
		if (player.tickCount % 6 != 0) {
			return;
		}
		ServerLevel level = level(player);
		double h = player.getBbHeight();
		level.sendParticles(particle, player.getX(), player.getY() + h * 0.5, player.getZ(),
				count, 0.45, h * 0.5, 0.45, 0.02);
	}

	/** Apply a crowd-control effect, softened against players per config. Returns false if skipped. */
	public static boolean applyControl(LivingEntity target, net.minecraft.core.Holder<MobEffect> effect, int ticks, int amp) {
		if (target instanceof Player) {
			if (!HeroConfig.get().abilityHardCrowdControlOnPlayers) {
				return false;
			}
			ticks = Math.max(10, ticks / 2);
		}
		target.addEffect(new MobEffectInstance(effect, ticks, amp, false, true, true));
		return true;
	}

	// ---------------- self movement ----------------

	public static void launchSelf(ServerPlayer player, Vec3 velocity) {
		player.setDeltaMovement(velocity);
		player.hurtMarked = true;
		player.hasImpulse = true;
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
		player.resetFallDistance();
	}

	public static void addImpulse(ServerPlayer player, Vec3 velocity) {
		launchSelf(player, player.getDeltaMovement().add(velocity));
	}

	// ---------------- particles / sound ----------------

	public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions particle, double perBlock) {
		double length = a.distanceTo(b);
		int steps = Math.max(1, (int) (length * perBlock));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.lerp(b, (double) i / steps);
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	public static void burst(ServerLevel level, Vec3 c, ParticleOptions particle, int count, double spread) {
		level.sendParticles(particle, c.x, c.y, c.z, count, spread, spread, spread, 0.05);
	}

	public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static void sound(ServerPlayer player, net.minecraft.core.Holder<SoundEvent> sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	// ---------------- misc ----------------

	public static ServerLevel level(ServerPlayer player) {
		return (ServerLevel) player.level();
	}

	public static boolean canGrief() {
		return HeroConfig.get().abilityTerrainDamage;
	}

	public static Vec3 lookDir(Player player) {
		return player.getLookAngle();
	}

	public static boolean isValidGrabTarget(Entity target, ServerPlayer grabber) {
		if (!(target instanceof LivingEntity living) || !living.isAlive() || target == grabber) {
			return false;
		}
		if (target instanceof ArmorStand || living.getMaxHealth() > 200.0f) {
			return false; // no armor stands, no bosses (Warden/Ender Dragon/Wither-scale health)
		}
		if (target instanceof Player) {
			return HeroConfig.get().abilityHardCrowdControlOnPlayers;
		}
		return true;
	}
}
