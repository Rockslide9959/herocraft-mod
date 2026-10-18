package com.projecthero.mod.quirk.explosion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Bakugo's explosions -- all custom, never {@code level.explode}. Damage and knockback in a radius worked out
 * here, through the mod-wide targeting rules ({@link HeroTargets#canHarm}: never himself, a squadmate or a friendly pet;
 * players only with PvP on); bosses take at most {@link ExplosionConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a
 * hit and are never knocked about. Blocks are only touched with mobGriefing (and the terrain-damage config) on, and then
 * only a few soft ones, never a block entity (no containers).
 */
public final class ExplosionBlast {
	/** His colours: the orange-yellow of a palm spark and the deep orange of a blast. */
	public static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.25f), 0.9f);
	public static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.08f), 1.4f);
	public static final DustParticleOptions FLASH_WHITE = new DustParticleOptions(new Vector3f(1.0f, 1.0f, 0.92f), 2.0f);

	public static final int SMALL = 0;
	public static final int MEDIUM = 1;
	public static final int BIG = 2;

	private ExplosionBlast() {
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= ExplosionConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && HeroTargets.canHarm(owner, e);
	}

	public static DamageSource source(ServerPlayer owner) {
		return owner.level().damageSources().explosion(owner, owner);
	}

	/**
	 * One explosion at {@code c}: everything valid within {@code radius} (to the nearest point of its box) takes
	 * {@code damage} falling to {@link ExplosionConfig#EDGE_FALLOFF} at the edge and is thrown away from the centre
	 * ({@code knockback} sideways, {@code lift} up). {@code skip}: ids already hit by this move (added to). Plays the
	 * blast's particles / sound for {@code size} and (mobGriefing only) breaks a few soft blocks. Returns how many it hit.
	 */
	public static int blast(ServerPlayer owner, Vec3 c, double radius, float damage, double knockback, double lift, int size,
			@Nullable Set<Integer> skip) {
		return blast(owner, c, radius, damage, knockback, lift, size, skip, size >= MEDIUM);
	}

	/** {@link #blast} with the terrain choice explicit ({@code grief}: may break a few soft blocks if mobGriefing allows). */
	public static int blast(ServerPlayer owner, Vec3 c, double radius, float damage, double knockback, double lift, int size,
			@Nullable Set<Integer> skip, boolean grief) {
		ServerLevel level = owner.serverLevel();
		fx(level, c, radius, size);
		int n = 0;
		AABB box = new AABB(c, c).inflate(radius + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> isTarget(owner, e))) {
			if (skip != null && skip.contains(e.getId())) {
				continue;
			}
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, c));
			if (d > radius) {
				continue;
			}
			float f = (float) (1.0 - (1.0 - ExplosionConfig.EDGE_FALLOFF) * Math.min(1.0, d / Math.max(0.1, radius)));
			if (strike(owner, e, c, damage * f, knockback * (0.5 + 0.5 * f), lift * f)) {
				n++;
				if (skip != null) {
					skip.add(e.getId());
				}
			}
		}
		if (grief) {
			grief(owner, c, size == BIG ? ExplosionConfig.GRIEF_BIG_BLOCKS : ExplosionConfig.GRIEF_MEDIUM_BLOCKS,
					size == BIG ? Math.min(2.5, radius * 0.4) : 1.2);
		}
		return n;
	}

	/** One hit on one target: boss-capped damage, then (not bosses) knockback away from {@code origin} and lift. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * ExplosionConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		boolean landed = AbilityHelpers.hurtBurst(owner, target, source(owner), Math.max(0.5f, dmg));
		if (!landed && !AbilityHelpers.hurtLands(owner, target, dmg)) {
			return false;
		}
		if (!boss && (knockback > 0.0 || lift > 0.0)) {
			Vec3 away = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(origin);
			Vec3 flat = new Vec3(away.x, 0, away.z);
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			target.setDeltaMovement(target.getDeltaMovement().multiply(0.3, 0.3, 0.3)
					.add(flat.scale(knockback)).add(0, Math.max(0.15 * Math.min(1.0, knockback), lift), 0));
			target.hurtMarked = true;
		}
		return true;
	}

	// ---------------------------------------------------------------- effects

	/** The particles and sound of one blast. */
	public static void fx(ServerLevel level, Vec3 c, double radius, int size) {
		var rnd = level.getRandom();
		switch (size) {
			case BIG -> {
				double s = radius * 0.4;
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 10, s, s * 0.7, s, 0);
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 50, s, s * 0.7, s, 0.25);
				level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 14, s * 0.6, s * 0.4, s * 0.6, 0);
				level.sendParticles(ORANGE, c.x, c.y, c.z, 30, s, s * 0.7, s, 0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 36, s, s * 0.8, s, 0.08);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 0.5, c.z, 8, s * 0.6, 0.4, s * 0.6, 0.03);
				play(level, c, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.7f + rnd.nextFloat() * 0.15f);
				play(level, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.5f, 0.6f + rnd.nextFloat() * 0.2f);
			}
			case MEDIUM -> {
				double s = Math.max(0.4, radius * 0.3);
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, s, s, s, 0);
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 18, s, s, s, 0.12);
				level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 4, s * 0.5, s * 0.5, s * 0.5, 0);
				level.sendParticles(SPARK, c.x, c.y, c.z, 14, s, s, s, 0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 12, s, s, s, 0.04);
				play(level, c, SoundEvents.GENERIC_EXPLODE, 1.4f, 1.05f + rnd.nextFloat() * 0.3f);
				play(level, c, SoundEvents.FIREWORK_ROCKET_BLAST, 1.6f, 0.8f + rnd.nextFloat() * 0.3f);
			}
			default -> {
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0);
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 7, 0.25, 0.25, 0.25, 0.06);
				level.sendParticles(SPARK, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0);
				level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0.02);
				play(level, c, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.55f + rnd.nextFloat() * 0.4f);
				play(level, c, SoundEvents.FIREWORK_ROCKET_BLAST, 0.9f, 1.2f + rnd.nextFloat() * 0.4f);
			}
		}
	}

	/** A crackle of sparks off a palm (no sound). */
	public static void palm(ServerLevel level, Vec3 c, int count) {
		level.sendParticles(SPARK, c.x, c.y, c.z, count, 0.12, 0.12, 0.12, 0);
		level.sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, Math.max(1, count / 2), 0.08, 0.08, 0.08, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, Math.max(1, count / 3), 0.08, 0.08, 0.08, 0.01);
	}

	/** A smoke trail from {@code a} to {@code b} with orange sparks in it (a shot's path). */
	public static void trail(ServerLevel level, Vec3 a, Vec3 b, double perBlock) {
		double len = a.distanceTo(b);
		int steps = Math.max(1, (int) (len * perBlock));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.lerp(b, (double) i / steps);
			level.sendParticles(i % 2 == 0 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
			if (i % 3 == 0) {
				level.sendParticles(SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
	}

	public static void play(ServerLevel level, Vec3 c, SoundEvent s, float vol, float pitch) {
		level.playSound(null, c.x, c.y, c.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	public static void play(ServerLevel level, Vec3 c, Holder<SoundEvent> s, float vol, float pitch) {
		level.playSound(null, c.x, c.y, c.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	// ---------------------------------------------------------------- terrain

	/** mobGriefing and the mod's terrain-damage config both on. */
	public static boolean canGrief(ServerLevel level) {
		return AbilityHelpers.canGrief() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** A soft block a blast may break: not air / fluid / a block entity (containers!) / unbreakable / harder than the limit. */
	public static boolean breakable(ServerLevel level, BlockPos pos, BlockState st) {
		if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.is(BlockTags.WITHER_IMMUNE)) {
			return false;
		}
		float h = st.getDestroySpeed(level, pos);
		return h >= 0.0f && h <= ExplosionConfig.GRIEF_MAX_HARDNESS;
	}

	/** Breaks at most {@code max} soft blocks within {@code radius} of {@code c} (nearest first). Returns how many. */
	public static int grief(ServerPlayer owner, Vec3 c, int max, double radius) {
		ServerLevel level = owner.serverLevel();
		if (max <= 0 || !canGrief(level)) {
			return 0;
		}
		int r = (int) Math.ceil(radius);
		BlockPos centre = BlockPos.containing(c);
		List<BlockPos> hits = new ArrayList<>();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + y * y + z * z > radius * radius) {
						continue;
					}
					BlockPos p = centre.offset(x, y, z);
					if (level.isLoaded(p) && level.mayInteract(owner, p) && breakable(level, p, level.getBlockState(p))) {
						hits.add(p);
					}
				}
			}
		}
		hits.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(c)));
		int n = 0;
		for (BlockPos p : hits) {
			if (n >= max) {
				break;
			}
			level.destroyBlock(p, n % 2 == 0, owner);
			n++;
		}
		return n;
	}

	/** The first block hit (or the end of the range) along {@code dir} from {@code from}, stopping at the first valid creature. */
	public static Vec3 rayEnd(ServerPlayer owner, Vec3 from, Vec3 dir, double range, double entityPad, @Nullable LivingEntity[] hitOut) {
		ServerLevel level = owner.serverLevel();
		Vec3 to = from.add(dir.scale(range));
		var bhr = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, owner));
		Vec3 end = bhr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to : bhr.getLocation();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		AABB sweep = new AABB(from, end).inflate(entityPad + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> isTarget(owner, e))) {
			var clip = e.getBoundingBox().inflate(entityPad).clip(from, end);
			if (clip.isPresent()) {
				double d = from.distanceToSqr(clip.get());
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
		}
		if (best != null) {
			if (hitOut != null && hitOut.length > 0) {
				hitOut[0] = best;
			}
			return from.add(dir.scale(Math.sqrt(bestD)));
		}
		// pull back off the wall face so the blast sits in the air in front of it
		return bhr.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : end.subtract(dir.scale(0.3));
	}
}
