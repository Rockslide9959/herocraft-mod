package com.projecthero.mod.quirk.explosion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.fx.QuirkSounds;
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
 * v0.16.7 (v0.17.1: drawn with the Quirk VFX toolkit): Bakugo's explosions -- all custom, never {@code level.explode}. Damage and knockback in a radius worked out
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
	 * blast's particles / sound for {@code size} and (v0.17.1) blows a hole in the terrain scaled by it ({@link ExplosionTerrain}).
	 * Returns how many it hit.
	 */
	public static int blast(ServerPlayer owner, Vec3 c, double radius, float damage, double knockback, double lift, int size,
			@Nullable Set<Integer> skip) {
		return blast(owner, c, radius, damage, knockback, lift, size, skip, true);
	}

	/** {@link #blast} with the terrain choice explicit ({@code grief}: blows its hole, {@link ExplosionTerrain}; false: none). */
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
			ExplosionTerrain.blow(owner, c, size == BIG ? ExplosionTerrain.Tier.CRATER : size == MEDIUM ? ExplosionTerrain.Tier.POCKET : ExplosionTerrain.Tier.CHIP);
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

	// ---------------------------------------------------------------- effects (v0.17.1: the Quirk VFX toolkit)

	/** Bakugo's colours: the white-hot flash, the orange-yellow core, the deep orange shell. */
	public static final int WHITE = 0xFFF0C8;
	public static final int CORE = 0xFFD860;
	public static final int SHELL = 0xFF6A10;

	/**
	 * The look and sound of one blast (v0.17.1, the headline fix: no more vanilla EXPLOSION / FLAME / GENERIC_EXPLODE): a
	 * {@code QuirkFx} BLAST -- white flash, flipbook fireballs, a ground shock ring, a smoke crown, sparks -- sized by the
	 * radius, with Bakugo's own crack-and-thump sound and a camera shake for everyone near (bigger blasts shake harder).
	 */
	public static void fx(ServerLevel level, Vec3 c, double radius, int size) {
		var rnd = level.getRandom();
		boolean grounded = nearGround(level, c, size == BIG ? 3.0 : 1.6);
		int flags = grounded ? 0 : QuirkFx.F_NO_RING;
		switch (size) {
			case BIG -> {
				QuirkFx.blast(level, c).colours(0xFFD890, SHELL).size((float) (radius * 0.85)).life(24).flags(flags).spawn();
				fireballs(level, c, radius * 0.55, 6, 1.6f + (float) radius * 0.12f);
				QuirkFx.ring(level, c).colours(0xFFE6B0, SHELL).size((float) (radius * 1.6)).thickness(0.7f).life(16).ground().spawn();
				if (grounded) {
					QuirkFx.decal(level, c).style(QuirkFx.DECAL_SCORCH).colour(0x1A120C).size((float) (radius * 0.7)).life(400).spawn();
				}
				level.sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, 40, radius * 0.25, radius * 0.2, radius * 0.25, 0.6);
				level.sendParticles(QuirkParticles.EMBER, c.x, c.y, c.z, 24, radius * 0.3, radius * 0.25, radius * 0.3, 0.12);
				QuirkSounds.play(level, c, ExplosionSounds.BLAST, 3.0f, 0.75f + rnd.nextFloat() * 0.1f);
				QuirkSounds.play(level, c, QuirkSounds.IMPACT_HEAVY, 2.0f, 0.8f);
				QuirkCamera.shakeAround(level, c, 28, 0.7f, 14);
			}
			case MEDIUM -> {
				QuirkFx.blast(level, c).colours(0xFFC860, SHELL).size((float) Math.max(1.4, radius * 0.9)).life(18).flags(flags).spawn();
				fireballs(level, c, radius * 0.45, 3, 1.1f);
				if (grounded) {
					QuirkFx.decal(level, c).style(QuirkFx.DECAL_SCORCH).colour(0x1A120C).size((float) Math.max(1.0, radius * 0.6)).life(240).spawn();
				}
				level.sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, 18, 0.3, 0.3, 0.3, 0.45);
				QuirkSounds.play(level, c, ExplosionSounds.BLAST, 1.6f, 1.0f + rnd.nextFloat() * 0.2f);
				QuirkCamera.shakeAround(level, c, 16, 0.35f, 8);
			}
			default -> {
				QuirkFx.blast(level, c).colours(0xFFC860, 0xFF6A10).size((float) Math.max(0.8, radius * 0.75)).life(12).flags(QuirkFx.F_NO_RING)
						.spawn();
				level.sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.35);
				QuirkSounds.play(level, c, ExplosionSounds.BLAST_SMALL, 1.0f, 1.0f + rnd.nextFloat() * 0.3f);
				QuirkCamera.shakeAround(level, c, 8, 0.15f, 5);
			}
		}
	}

	/** A few cartoon fireballs (flipbooks) bursting round a blast's centre: the orange body of an anime explosion. */
	public static void fireballs(ServerLevel level, Vec3 c, double spread, int n, float size) {
		var rnd = level.getRandom();
		for (int i = 0; i < n; i++) {
			Vec3 at = c.add((rnd.nextDouble() - 0.5) * 2 * spread, (rnd.nextDouble() - 0.3) * spread, (rnd.nextDouble() - 0.5) * 2 * spread);
			QuirkFx.flipbook(level, at).style(QuirkFx.BOOK_FIREBALL).size(size * (0.8f + 0.4f * rnd.nextFloat())).life(14 + rnd.nextInt(6)).spawn();
		}
	}

	/** Solid ground within {@code depth} under {@code c} (a ground ring / scorch makes sense). */
	public static boolean nearGround(ServerLevel level, Vec3 c, double depth) {
		var hit = level.clip(new net.minecraft.world.level.ClipContext(c, c.add(0, -depth, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/** A crackle of sparks off a palm (the sweat igniting; no sound). */
	public static void palm(ServerLevel level, Vec3 c, int count) {
		level.sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, count, 0.1, 0.1, 0.1, 0.18);
		level.sendParticles(QuirkParticles.EMBER, c.x, c.y, c.z, Math.max(1, count / 3), 0.06, 0.06, 0.06, 0.03);
	}

	/** A shot's path from {@code a} to {@code b}: a white-hot streak in an orange glow, embers along it. */
	public static void trail(ServerLevel level, Vec3 a, Vec3 b, double perBlock) {
		double len = a.distanceTo(b);
		if (len < 0.2) {
			return;
		}
		QuirkFx.cone(level, a, b.subtract(a)).style(QuirkFx.CONE_BEAM).colours(WHITE, 0xFF8A20).size(0.09f).length((float) len).life(7).spawn();
		int steps = Math.max(1, (int) (len * perBlock * 0.25));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.lerp(b, (double) i / steps);
			level.sendParticles(QuirkParticles.EMBER, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.01);
		}
	}

	/** The muzzle flash at a palm: a short flame cone along {@code dir} and a vertical shock ring. */
	public static void muzzle(ServerLevel level, Vec3 palm, Vec3 dir, float scale) {
		QuirkFx.cone(level, palm, dir).colours(WHITE, SHELL).size(0.45f * scale).length(1.6f * scale).life(6).spawn();
		QuirkFx.ring(level, palm.add(dir.normalize().scale(0.9))).flags(QuirkFx.F_VERTICAL).vec(dir).colours(WHITE, CORE).size(0.55f * scale)
				.thickness(0.12f).life(6).spawn();
		palm(level, palm, 6);
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
