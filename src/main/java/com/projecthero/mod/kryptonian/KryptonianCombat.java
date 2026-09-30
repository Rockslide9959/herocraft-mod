package com.projecthero.mod.kryptonian;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Kryptonian's shared hit code (v0.14.8): who counts as a target (never himself, never a squad-mate, players only with
 * PvP on), boss capping ({@link KryptonianConfig#BOSS_MAX_FRACTION_PER_HIT} of max health, no knockback), cones, radial
 * waves, the small craters and the particles. Friendly fire is also vetoed centrally by the squad damage rule; skipping
 * squad-mates here keeps them from being shoved, frozen or grabbed too.
 */
public final class KryptonianCombat {
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private KryptonianCombat() {
	}

	/** Set while a move deals its damage, so the passive-punch knockback does not fire on top. */
	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= KryptonianConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	/** Something this Kryptonian's moves may affect. */
	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		if (e == owner || !e.isAlive() || e instanceof ArmorStand || e.isSpectator()) {
			return false;
		}
		if (e instanceof Player p) {
			if (p.isCreative() || com.projecthero.mod.squad.Squads.areAllies(owner, p)) {
				return false;
			}
			return owner.getServer() != null && owner.getServer().isPvpAllowed();
		}
		return true;
	}

	public static List<LivingEntity> targets(ServerPlayer owner, AABB box) {
		return ((ServerLevel) owner.level()).getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(owner, e));
	}

	/**
	 * One hit: damage (boss-capped), then knockback away from {@code origin} and {@code lift}. {@code burst} lets a one-shot
	 * move land through a recent hit's invulnerability window. True if it landed.
	 */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift,
			boolean burst) {
		return strike(owner, target, origin, damage, knockback, lift, burst, owner.level().damageSources().playerAttack(owner));
	}

	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift,
			boolean burst, DamageSource source) {
		boolean boss = isBoss(target);
		float dmg = damage;
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * KryptonianConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		boolean landed;
		ABILITY_HIT.set(true);
		try {
			landed = burst ? AbilityHelpers.hurtBurst(owner, target, source, Math.max(1.0f, dmg))
					: AbilityHelpers.hurt(owner, target, source, Math.max(1.0f, dmg));
			if (!landed && target instanceof ServerPlayer tp && tp.isAlive() && !tp.isCreative()) {
				// another power's cancel-and-reissue damage rules swallow the original hit: it still landed (see hurtLands)
				landed = owner.getServer() != null && owner.getServer().isPvpAllowed()
						&& com.projecthero.mod.hero.HeroConfig.get().abilityPvpDamage
						&& !com.projecthero.mod.squad.Squads.shields(owner, tp);
			}
		} finally {
			ABILITY_HIT.set(false);
		}
		if (landed && !boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		if (landed) {
			Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
			((ServerLevel) owner.level()).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.3);
		}
		return landed;
	}

	/** Everything within {@code radius} of {@code center}; damage falls off to 60% at the edge. Returns how many were hit. */
	public static int radial(ServerPlayer owner, Vec3 center, double radius, float damage, double knockback, double lift, Set<Integer> hit) {
		AABB box = new AABB(center, center).inflate(radius, Math.min(radius, 6.0), radius);
		int n = 0;
		for (LivingEntity e : targets(owner, box)) {
			if (hit != null && hit.contains(e.getId())) {
				continue;
			}
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, center));
			if (dist > radius) {
				continue;
			}
			float scale = (float) (1.0 - 0.4 * (dist / radius));
			if (strike(owner, e, center, damage * scale, knockback * scale, lift, true)) {
				n++;
				if (hit != null) {
					hit.add(e.getId());
				}
			}
		}
		return n;
	}

	/** The living targets inside a cone from {@code from} along {@code dir}: {@code range} long, {@code degrees} wide. */
	public static List<LivingEntity> cone(ServerPlayer owner, Vec3 from, Vec3 dir, double range, double degrees) {
		Vec3 d = dir.normalize();
		double cos = Math.cos(Math.toRadians(degrees * 0.5));
		AABB box = new AABB(from, from).inflate(range);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : targets(owner, box)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(from);
			double len = to.length();
			if (len > range + e.getBbWidth() * 0.5) {
				continue;
			}
			if (len < 1.2 || to.normalize().dot(d) >= cos) {
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * A small crater: breaks up to {@code max} blocks within {@code radius}, nearest first, no drops. Never block entities,
	 * never anything unbreakable or harder than obsidian-lite (hardness 5), never where the player may not build, and only
	 * when the server allows ability terrain damage.
	 */
	public static int crater(ServerPlayer owner, Vec3 center, double radius, int max) {
		if (!AbilityHelpers.canGrief() || max <= 0) {
			return 0;
		}
		ServerLevel level = (ServerLevel) owner.level();
		int r = (int) Math.ceil(radius);
		BlockPos c = BlockPos.containing(center);
		List<BlockPos> list = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, 0, r))) {
			if (p.distSqr(c) > radius * radius || !level.isLoaded(p)) {
				continue;
			}
			BlockState st = level.getBlockState(p);
			if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
				continue;
			}
			float h = st.getDestroySpeed(level, p);
			if (h < 0.0f || h > 5.0f || !level.mayInteract(owner, p)) {
				continue;
			}
			list.add(p.immutable());
		}
		list.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
		int n = 0;
		for (BlockPos p : list) {
			if (n >= max) {
				break;
			}
			BlockState st = level.getBlockState(p);
			if (n % 5 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5,
						3, 0.3, 0.3, 0.3, 0.05);
			}
			level.destroyBlock(p, false, owner);
			n++;
		}
		return n;
	}

	public static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double radius, int points) {
		int n = Math.min(32, points);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.0, 0.05, 0.0, 0.02);
		}
	}

	/** Camera shake for players near {@code at} (the Titan tremor packet, purely cosmetic). */
	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		double r2 = radius * radius;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2) {
				float falloff = (float) (1.0 - Math.sqrt(d2 / r2) * 0.8);
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * falloff, ticks));
			}
		}
	}
}
