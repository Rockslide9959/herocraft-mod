package com.projecthero.mod.hideout;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hideout.mob.HideoutSoldier;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.17.7: <b>Oath-bound patrols</b> -- the Oathbreaker faction's presence in the world (and what a cleared Oathbreaker
 * Keep calms, like the Symbiote Hive calms Carnage's meteor). Every {@link #INTERVAL} ticks, at night, each survival
 * player standing in the Keep's biomes (mountains / windswept hills: {@code #projecthero:hideout/oathbreaker_keep}) has
 * a 1 in {@link #CHANCE} chance that a patrol of three Oath-bound Knights (a knight, an arbalist, a squire) rides out
 * 24-40 blocks away. Normal mobs: they despawn like any monster, drop nothing special, and are not hideout guards.
 * Never on Peaceful, with mob spawning off, with knights already within 64 blocks, or within the calm of a cleared Keep.
 * No static state.
 */
public final class OathPatrols {
	public static final int INTERVAL = 2400;
	public static final int CHANCE = 6;
	public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, ProjectHeroMod.id("hideout/oathbreaker_keep"));

	private OathPatrols() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(OathPatrols::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || !level.isNight()) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isCreative() || p.isSpectator() || level.random.nextInt(CHANCE) != 0) {
				continue;
			}
			if (eligible(level, p.blockPosition())) {
				spawnPatrol(level, p.blockPosition());
			}
		}
	}

	/** Whether a patrol may ride out near {@code pos} (biome, calm, no knights about). */
	public static boolean eligible(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || !level.getBiome(pos).is(BIOMES)) {
			return false;
		}
		if (HideoutCalm.calmed(level, HideoutFaction.OATH, pos)) {
			return false;
		}
		return level.getEntitiesOfClass(HideoutSoldier.class, new net.minecraft.world.phys.AABB(pos).inflate(64), s -> !s.kind().crimson()).isEmpty();
	}

	/** Spawns a patrol 24-40 blocks from {@code near}. Returns how many knights appeared (test hook too). */
	public static int spawnPatrol(ServerLevel level, BlockPos near) {
		double a = level.random.nextDouble() * Math.PI * 2;
		int r = 24 + level.random.nextInt(17);
		int x = near.getX() + (int) Math.round(Math.cos(a) * r);
		int z = near.getZ() + (int) Math.round(Math.sin(a) * r);
		if (!level.isLoaded(new BlockPos(x, near.getY(), z))) {
			return 0;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos at = new BlockPos(x, y, z);
		if (!level.getFluidState(at.below()).isEmpty()) {
			return 0;
		}
		int n = 0;
		HideoutSoldier.Kind[] kinds = { HideoutSoldier.Kind.KNIGHT, HideoutSoldier.Kind.ARBALIST, HideoutSoldier.Kind.SQUIRE };
		for (int i = 0; i < kinds.length; i++) {
			HideoutSoldier s = HideoutEntityTypes.OATH_KNIGHT.create(level);
			if (s == null) {
				continue;
			}
			BlockPos spot = at.offset(i - 1, 0, (i % 2));
			spot = new BlockPos(spot.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ()), spot.getZ());
			s.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
			s.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.PATROL, null);
			s.setKind(kinds[i]);
			level.addFreshEntity(s);
			n++;
		}
		return n;
	}

	/** True for a patrol knight (not a hideout guard). */
	public static boolean patrolKnight(Mob m) {
		return m instanceof HideoutSoldier s && !s.kind().crimson() && !HideoutGuards.isGuard(m);
	}
}
