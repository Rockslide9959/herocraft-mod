package com.projecthero.mod.quirk.explosion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.config.VersionedConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "i want the explosion quirk to actually explode things and break blocks, it just makes sense"): Bakugo's
 * blasts really blow holes in the terrain, scaled by the move -- TNT-like, but every block goes through a PLAYER-caused
 * break path, never {@code level.explode}:
 * <ul>
 *   <li>{@link Tier#CHIP}: the Auto-Cannon's small pops nick a block or two;</li>
 *   <li>{@link Tier#POCKET}: an ordinary blast (the Punch, Blast Rush, Crush Grip...) breaks a small pocket;</li>
 *   <li>{@link #tunnel}: the AP Shot (and Pierce) bores a short tunnel back along its line from where it hit;</li>
 *   <li>{@link Tier#CRATER}: the big moves (Howitzer Impact, the Uppercut, the landing blast, the Gauntlet's end) blow a
 *       proper bowl;</li>
 *   <li>{@link Tier#HUGE}: Howitzer Impact -- Cluster's finishing blast carves a big one;</li>
 *   <li>the Stun Grenade is only a flash: no block damage.</li>
 * </ul>
 * Rules: the server toggle ({@code config/projecthero_quirk_explosion.json}, {@code terrain.blockDamage}, default on) and the
 * mod-wide ability terrain-damage switch; only where he may break blocks himself -- adventure mode, spawn protection, claims
 * and every mod guard on {@link PlayerBlockBreakEvents#BEFORE} (Green Lantern constructs, conjured structures, the Hulk's
 * mindscape...) are respected; never air, fluids, block entities (containers!), bedrock / unbreakable or wither-immune blocks
 * (barriers, portals, command blocks); each tier has a hardness limit, so obsidian survives everything but the ultimate's
 * HUGE tier does not reach it either (obsidian is never in range). A share of the broken blocks drops as items, like TNT.
 */
public final class ExplosionTerrain {
	/** How big a hole, how many blocks at most, how hard a block may be. */
	public enum Tier {
		CHIP(0.9, 2, 1.5f), POCKET(1.6, 8, 3.0f), CRATER(2.8, 34, 3.5f), HUGE(5.2, 150, 6.0f);

		public final double radius;
		public final int max;
		public final float maxHardness;

		Tier(double radius, int max, float maxHardness) {
			this.radius = radius;
			this.max = max;
			this.maxHardness = maxHardness;
		}
	}

	// ---------------------------------------------------------------- the file
	public static final VersionedConfig<TerrainFile> SPEC = VersionedConfig
			.builder(TerrainFile.class, "projecthero_quirk_explosion.json", TerrainFile::new).build();

	private static TerrainFile file = new TerrainFile();

	public static final class TerrainFile {
		public Integer configVersion;
		public Terrain terrain = new Terrain();
	}

	public static final class Terrain {
		/** Explosion's blasts break blocks (where he may break them). */
		public boolean blockDamage = true;
		/** The share of broken blocks that drop as items (TNT-like). */
		public float dropChance = 0.3f;
	}

	public static Terrain terrain() {
		return file.terrain;
	}

	static void load() {
		file = SPEC.load();
	}

	/** Test hook: switch the block damage. */
	public static void setBlockDamageForTest(boolean on) {
		file.terrain.blockDamage = on;
	}

	private ExplosionTerrain() {
	}

	/** Block damage is on (the toggle and the mod-wide ability terrain switch). */
	public static boolean enabled() {
		return file.terrain.blockDamage && AbilityHelpers.canGrief();
	}

	/** {@code owner} may blow up the block at {@code pos} with a blast this hard. */
	public static boolean canBreak(ServerPlayer owner, ServerLevel level, BlockPos pos, float maxHardness) {
		if (!level.isLoaded(pos) || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		BlockState st = level.getBlockState(pos);
		if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.is(BlockTags.WITHER_IMMUNE)) {
			return false;
		}
		float h = st.getDestroySpeed(level, pos);
		if (h < 0f || h > maxHardness) {
			return false;
		}
		if (!owner.mayBuild() || !level.mayInteract(owner, pos) || owner.blockActionRestricted(level, pos, owner.gameMode.getGameModeForPlayer())) {
			return false;
		}
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, owner, pos, st, null);
	}

	/** A blast's hole at {@code c}: a roughly spherical bowl (a little ragged, shallower above), nearest first. Returns the blocks broken. */
	public static int blow(ServerPlayer owner, Vec3 c, Tier tier) {
		return blow(owner, c, tier.radius, tier.max, tier.maxHardness);
	}

	public static int blow(ServerPlayer owner, Vec3 c, double radius, int max, float maxHardness) {
		if (!enabled() || max <= 0) {
			return 0;
		}
		ServerLevel level = owner.serverLevel();
		int r = (int) Math.ceil(radius);
		BlockPos centre = BlockPos.containing(c);
		long seed = centre.asLong() * 31L + level.getGameTime();
		List<BlockPos> hits = new ArrayList<>();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos p = centre.offset(x, y, z);
					// ragged edge: each block's reach varies a little; the top of the sphere is shallower (blasts dig down and out)
					double rr = radius * (0.82 + 0.3 * hash(seed, x, y, z));
					double yy = y > 0 ? y * 1.35 : y;
					if (x * x + yy * yy + z * z > rr * rr) {
						continue;
					}
					if (canBreak(owner, level, p, maxHardness)) {
						hits.add(p);
					}
				}
			}
		}
		return breakAll(owner, level, c, hits, max);
	}

	/**
	 * The AP Shot's tunnel: a 1-2 block bore {@code length} blocks back along {@code dir} from where the shot hit (it drills
	 * into the wall it met). Returns the blocks broken.
	 */
	public static int tunnel(ServerPlayer owner, Vec3 hit, Vec3 dir, double length, double width, float maxHardness) {
		if (!enabled()) {
			return 0;
		}
		ServerLevel level = owner.serverLevel();
		Vec3 d = dir.normalize();
		List<BlockPos> hits = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (double s = -0.5; s <= length; s += 0.4) {
			Vec3 at = hit.add(d.scale(s));
			int w = (int) Math.ceil(width);
			for (int x = -w; x <= w; x++) {
				for (int y = -w; y <= w; y++) {
					for (int z = -w; z <= w; z++) {
						if (x * x + y * y + z * z > width * width) {
							continue;
						}
						BlockPos p = BlockPos.containing(at.x + x, at.y + y, at.z + z);
						if (seen.add(p.asLong()) && canBreak(owner, level, p, maxHardness)) {
							hits.add(p);
						}
					}
				}
			}
		}
		return breakAll(owner, level, hit, hits, 40);
	}

	private static int breakAll(ServerPlayer owner, ServerLevel level, Vec3 c, List<BlockPos> hits, int max) {
		hits.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(c)));
		float drop = file.terrain.dropChance;
		var rnd = level.getRandom();
		int n = 0;
		for (BlockPos p : hits) {
			if (n >= max) {
				break;
			}
			BlockState st = level.getBlockState(p);
			if (level.destroyBlock(p, !owner.isCreative() && rnd.nextFloat() < drop, owner)) {
				if (n < 4) {
					QuirkFx.shards(level, Vec3.atCenterOf(p)).block(st).size(1.6f).count(8).life(26).spawn();
				}
				if (n % 3 == 0) {
					Vec3 a = Vec3.atCenterOf(p);
					level.sendParticles(QuirkParticles.DUST_CHUNK, a.x, a.y, a.z, 3, 0.3, 0.3, 0.3, 0.15);
				}
				n++;
			}
		}
		return n;
	}

	private static double hash(long seed, int x, int y, int z) {
		long h = seed * 6364136223846793005L + x * 1442695040888963407L + y * 2862933555777941757L + z * 3202034522624059733L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}
}
