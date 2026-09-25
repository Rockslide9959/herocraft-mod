package com.projecthero.mod.allmight;

import java.util.HashSet;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The reusable air-pressure / shockwave utility every All Might attack goes through (v0.12.33): a directional
 * slab (Detroit, Texas), a radial wave (landing impacts, the United States of Smash's outer wave) and a single
 * strike (Carolina / New Hampshire flight hits), plus knockback, boss capping, block impacts, particles and
 * camera shake. No ability copies entity-query or damage logic -- they configure a {@link Wave} and call in.
 *
 * <p>Everything is server-side, bounding-box first and then a cheap directional/radial distance test, so a big
 * cinematic attack never does more than one small entity query per call.
 */
public final class AllMightShockwave {
	/** Set while an ability is applying damage, so the passive-punch hook does not treat it as a melee hit. */
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private AllMightShockwave() {
	}

	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	/** One attack's numbers and the set of entities it has already hit (so a travelling wave never hits twice). */
	public static final class Wave {
		public final float damage;
		public final double knockback;
		public final double lift;
		public final Set<Integer> hit = new HashSet<>();

		public Wave(float damage, double knockback, double lift) {
			this.damage = damage;
			this.knockback = knockback;
			this.lift = lift;
		}
	}

	// ---------------------------------------------------------------- entities

	/**
	 * Hits every enemy inside the slab {@code from..to} blocks along {@code dir} (horizontally), {@code width} blocks
	 * wide, from a block below {@code origin} up to {@code height} above it. Returns how many were hit.
	 */
	public static int sweep(ServerPlayer owner, Vec3 origin, Vec3 dir, double from, double to, double width, double height, Wave wave) {
		Vec3 d = new Vec3(dir.x, 0.0, dir.z);
		if (d.lengthSqr() < 1.0e-6) {
			d = Vec3.directionFromRotation(0, owner.getYRot());
		}
		d = d.normalize();
		double reach = Math.max(to, from) + 1.0;
		Vec3 mid = origin.add(d.scale((from + to) * 0.5));
		double half = (to - from) * 0.5 + width * 0.5 + 1.5;
		AABB box = new AABB(mid.x - half, origin.y - 1.5, mid.z - half, mid.x + half, origin.y + height + 1.0, mid.z + half);
		ServerLevel level = (ServerLevel) owner.level();
		int n = 0;
		for (LivingEntity e : candidates(owner, level, box)) {
			if (wave.hit.contains(e.getId())) {
				continue;
			}
			double rx = e.getX() - origin.x;
			double rz = e.getZ() - origin.z;
			double along = rx * d.x + rz * d.z;
			double lateral = Math.abs(rx * d.z - rz * d.x);
			double r = e.getBbWidth() * 0.5;
			if (along + r < from || along - r > to || along > reach + r || lateral > width * 0.5 + r) {
				continue;
			}
			if (e.getBoundingBox().maxY < origin.y - 1.5 || e.getBoundingBox().minY > origin.y + height) {
				continue;
			}
			if (strike(owner, e, origin, wave)) {
				n++;
			}
		}
		return n;
	}

	/** Hits every enemy within {@code radius} of {@code center}; damage falls off to 60% at the edge if {@code falloff}. */
	public static int radial(ServerPlayer owner, Vec3 center, double radius, Wave wave, boolean falloff) {
		ServerLevel level = (ServerLevel) owner.level();
		AABB box = new AABB(center.x - radius, center.y - Math.min(radius, 6.0), center.z - radius,
				center.x + radius, center.y + Math.min(radius, 8.0), center.z + radius);
		int n = 0;
		for (LivingEntity e : candidates(owner, level, box)) {
			if (wave.hit.contains(e.getId())) {
				continue;
			}
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, center));
			if (dist > radius) {
				continue;
			}
			float scale = falloff ? (float) (1.0 - 0.4 * (dist / radius)) : 1.0f;
			if (strike(owner, e, center, wave, scale)) {
				n++;
			}
		}
		return n;
	}

	private static List<LivingEntity> candidates(ServerPlayer owner, ServerLevel level, AABB box) {
		boolean pvp = owner.getServer() != null && owner.getServer().isPvpAllowed();
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive()
				&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
				&& (!(e instanceof net.minecraft.world.entity.player.Player) || pvp));
	}

	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, Wave wave) {
		return strike(owner, target, origin, wave, 1.0f);
	}

	/** Damage (boss-capped, form/cowl-scaled) plus knockback and lift away from {@code origin}. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, Wave wave, float scale) {
		if (!wave.hit.add(target.getId())) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = wave.damage * AllMight.smashMultiplier(owner) * scale;
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * AllMightConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		boolean landed;
		ABILITY_HIT.set(true);
		try {
			landed = AbilityHelpers.hurtLands(owner, target, Math.max(1.0f, dmg));
		} finally {
			ABILITY_HIT.set(false);
		}
		if (landed && wave.knockback > 0.0 && !boss) {
			AbilityHelpers.knockbackFrom(target, origin, wave.knockback * scale);
			if (wave.lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, wave.lift, 0.0));
			}
		}
		if (landed) {
			AllMight.markCombat(owner);
			Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
			((ServerLevel) owner.level()).sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, particles(6), 0.3, 0.3, 0.3, 0.3);
		}
		return landed;
	}

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= AllMightConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	// ---------------------------------------------------------------- blocks

	/**
	 * A local impact: breaks up to {@code max} weak blocks within {@code radius} of {@code center}, nearest first, never
	 * bedrock or any unbreakable block, never anything the player may not modify, nothing harder than {@code hardness}.
	 * Drops are suppressed (a crater must not turn into a loot fountain or a lag spike).
	 */
	public static int breakBlocks(ServerPlayer owner, Vec3 center, double radius, int max, float hardness) {
		if (!AllMightConfig.BLOCK_DESTRUCTION || !AbilityHelpers.canGrief() || max <= 0 || radius <= 0.0) {
			return 0;
		}
		ServerLevel level = (ServerLevel) owner.level();
		int r = (int) Math.ceil(radius);
		BlockPos c = BlockPos.containing(center);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > radius * radius) {
						continue;
					}
					pos.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!level.isLoaded(pos)) {
						continue;
					}
					BlockState st = level.getBlockState(pos);
					if (st.isAir() || !st.getFluidState().isEmpty()) {
						continue;
					}
					float h = st.getDestroySpeed(level, pos);
					if (h < 0.0f || h > hardness || !level.mayInteract(owner, pos)) {
						continue;
					}
					candidates.add(pos.immutable());
				}
			}
		}
		candidates.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(c)));
		int n = 0;
		for (BlockPos p : candidates) {
			if (n >= max) {
				break;
			}
			BlockState st = level.getBlockState(p);
			if (n % 6 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5,
						particles(3), 0.3, 0.3, 0.3, 0.05);
			}
			level.destroyBlock(p, false, owner);
			n++;
		}
		return n;
	}

	// ---------------------------------------------------------------- particles / shake

	/** A particle count scaled by the configured intensity (never negative). */
	public static int particles(int base) {
		return Math.max(0, Math.round(base * AllMightConfig.PARTICLE_INTENSITY));
	}

	public static void burst(ServerLevel level, ParticleOptions p, Vec3 c, int count, double spread, double speed) {
		int n = particles(count);
		if (n > 0) {
			level.sendParticles(p, c.x, c.y, c.z, n, spread, spread, spread, speed);
		}
	}

	/** A flat ring of particles (at most 24 points -- ring size scales the radius, not the count). */
	public static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double radius, int points) {
		int n = Math.min(24, particles(points));
		for (int i = 0; i < n; i++) {
			double a = (Math.PI * 2 * i) / Math.max(1, n);
			level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.0, 0.05, 0.0, 0.02);
		}
	}

	/** A cone of wind particles along a direction (capped). */
	public static void windLine(ServerLevel level, Vec3 from, Vec3 dir, double length, double width, int perBlock) {
		Vec3 d = dir.normalize();
		Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
		int steps = Math.min(24, Math.max(1, (int) (length * perBlock * AllMightConfig.PARTICLE_INTENSITY)));
		for (int i = 0; i < steps; i++) {
			double t = length * i / steps;
			double off = (level.random.nextDouble() - 0.5) * width;
			Vec3 p = from.add(d.scale(t)).add(side.scale(off));
			level.sendParticles(i % 3 == 0 ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
		}
	}

	/** Camera shake for players near {@code at}; reuses the Titan tremor packet (purely cosmetic). */
	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks) {
		if (!AllMightConfig.SCREEN_SHAKE) {
			return;
		}
		double r2 = AllMightConfig.SHAKE_RADIUS * AllMightConfig.SHAKE_RADIUS;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2) {
				float falloff = (float) (1.0 - Math.sqrt(d2 / r2) * 0.8);
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * falloff, ticks));
			}
		}
	}
}
