package com.projecthero.mod.hulk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.TitanShakePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.14: the Hulk's shared hitting and wrecking code, used by every ability, the Charge, the thrown mobs and
 * boulders and the rampage. Targets never include the Hulk himself, whoever is riding his back, his squad-mates (v0.14.3: except on a rampage) or
 * armour stands; other players only with PvP on. Bosses take the damage but are never shoved.
 */
public final class HulkCombat {
	private HulkCombat() {
	}

	/** One attack's "already hit" set, so a moving hit (a charge, a sweep) never lands twice on the same target. */
	public static final class Hit {
		public final float damage;
		public final double knockback;
		public final double lift;
		public final Set<Integer> hit = new HashSet<>();

		public Hit(float damage, double knockback, double lift) {
			this.damage = damage;
			this.knockback = knockback;
			this.lift = lift;
		}
	}

	/** Everything the Hulk may hurt inside {@code box}. */
	public static List<LivingEntity> targets(ServerPlayer hulk, AABB box) {
		ServerLevel level = (ServerLevel) hulk.level();
		// v0.14.20: the shared rule 1 (HeroTargets#canHarm) -- squad protection via Squads.shields, so a rampage
		// still breaks it; never what he carries or rides, his pets, or (PvP off) a player
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> com.projecthero.mod.combat.HeroTargets.canHarm(hulk, e));
	}

	static boolean ally(ServerPlayer hulk, LivingEntity e) {
		if (!(e instanceof ServerPlayer other) || hulk.getServer() == null) {
			return false;
		}
		return com.projecthero.mod.squad.Squads.shields(hulk, other); // v0.14.3: not while he rampages
	}

	/** Damage + shove away from {@code origin}. False if this hit already landed on the target. */
	public static boolean strike(ServerPlayer hulk, LivingEntity target, Vec3 origin, Hit hit, float scale) {
		if (!hit.hit.add(target.getId())) {
			return false;
		}
		if (!AbilityHelpers.hurtLands(hulk, target, hit.damage * scale)) {
			return false;
		}
		if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(target)) {
			AbilityHelpers.knockbackFrom(target, origin, hit.knockback * scale);
			if (hit.lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0, hit.lift * scale, 0));
			}
		}
		return true;
	}

	/** Hits everything within {@code radius} of {@code center}; damage falls to 60% at the edge if {@code falloff}. */
	public static int radial(ServerPlayer hulk, Vec3 center, double radius, Hit hit, boolean falloff) {
		AABB box = new AABB(center.x - radius, center.y - Math.min(radius, 6.0), center.z - radius,
				center.x + radius, center.y + Math.min(radius, 8.0), center.z + radius);
		int n = 0;
		for (LivingEntity e : targets(hulk, box)) {
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, center));
			if (dist > radius) {
				continue;
			}
			float scale = falloff ? (float) (1.0 - 0.4 * (dist / radius)) : 1.0f;
			if (strike(hulk, e, center, hit, scale)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Hits everything in a slab {@code range} blocks long and {@code width} wide along {@code dir} (horizontal), from a
	 * block below {@code origin} to {@code height} above it.
	 */
	public static int sweep(ServerPlayer hulk, Vec3 origin, Vec3 dir, double range, double width, double height, Hit hit) {
		Vec3 d = new Vec3(dir.x, 0.0, dir.z);
		if (d.lengthSqr() < 1.0e-6) {
			d = Vec3.directionFromRotation(0, hulk.getYRot());
		}
		d = d.normalize();
		Vec3 mid = origin.add(d.scale(range * 0.5));
		double half = range * 0.5 + width;
		AABB box = new AABB(mid.x - half, origin.y - 1.5, mid.z - half, mid.x + half, origin.y + height + 1.0, mid.z + half);
		int n = 0;
		for (LivingEntity e : targets(hulk, box)) {
			double rx = e.getX() - origin.x;
			double rz = e.getZ() - origin.z;
			double along = rx * d.x + rz * d.z;
			double lateral = Math.abs(rx * d.z - rz * d.x);
			double r = e.getBbWidth() * 0.5;
			if (along + r < 0.0 || along - r > range || lateral > width * 0.5 + r) {
				continue;
			}
			if (e.getBoundingBox().maxY < origin.y - 1.5 || e.getBoundingBox().minY > origin.y + height) {
				continue;
			}
			if (strike(hulk, e, origin, hit, 1.0f)) {
				n++;
			}
		}
		return n;
	}

	public static boolean canBreakBlocks(ServerLevel level) {
		HulkConfig.World w = HulkConfig.world();
		return w.blockBreaking && (!w.respectMobGriefing || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
	}

	/** True if the Hulk may smash this block (not air / fluid / a block entity / unbreakable / harder than the config limit). */
	public static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
			return true; // v0.13.17: webs are soft to the Hulk (hardness 4 would otherwise keep them out)
		}
		float h = state.getDestroySpeed(level, pos);
		return h >= 0.0f && h <= HulkConfig.world().maxBreakableHardness;
	}

	/** A bowl-shaped crater of up to {@code max} blocks round {@code center}. Returns how many broke. */
	public static int crater(ServerPlayer hulk, Vec3 center, double radius, int max) {
		ServerLevel level = (ServerLevel) hulk.level();
		if (!canBreakBlocks(level) || radius <= 0.0) {
			return 0;
		}
		int r = (int) Math.ceil(radius);
		BlockPos c = BlockPos.containing(center);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		java.util.List<BlockPos> hits = new java.util.ArrayList<>();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= 1; y++) {
				for (int z = -r; z <= r; z++) {
					// flattened sphere: wide and shallow
					double d = (x * x + z * z) / (radius * radius) + (y * y) / (radius * radius * 0.35);
					if (d > 1.0) {
						continue;
					}
					pos.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!level.isLoaded(pos) || !level.mayInteract(hulk, pos)) {
						continue;
					}
					if (breakable(level, pos, level.getBlockState(pos))) {
						hits.add(pos.immutable());
					}
				}
			}
		}
		hits.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(c)));
		int n = 0;
		for (BlockPos p : hits) {
			if (n >= max) {
				break;
			}
			BlockState st = level.getBlockState(p);
			if (n % 5 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.7, p.getZ() + 0.5,
						4, 0.3, 0.3, 0.3, 0.08);
			}
			level.destroyBlock(p, HulkConfig.world().dropBrokenBlocks && n % 3 == 0, hulk);
			n++;
		}
		return n;
	}

	public static void shake(ServerLevel level, Vec3 at, float intensity, int ticks, double radius) {
		if (!HulkConfig.world().screenShake) {
			return;
		}
		double r2 = radius * radius;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2) {
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * (float) (1.0 - Math.sqrt(d2 / r2) * 0.8), ticks));
			}
		}
	}

	/** A ring of particles on the ground. */
	public static void ring(ServerLevel level, net.minecraft.core.particles.ParticleOptions p, Vec3 c, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.1, 0.05, 0.1, 0.01);
		}
	}
}
