package com.projecthero.mod.carnage;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonMeteorEntity;
import com.projecthero.mod.symbiote.Symbiote;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: Carnage's natural arrival -- modelled on the Behemoth's spawner: a slow per-player roll (once a minute, at
 * night, in the Overworld, never on Peaceful), at most one Carnage per dimension, and a cooldown after a meteor lands
 * and after he dies. The cooldown map holds only a game time per dimension key and is cleared by
 * {@code ServerStateReset} with the rest of the mod's static server state.
 */
public final class CarnageSpawner {
	private static final Map<ResourceKey<Level>, Long> COOLDOWN_UNTIL = new HashMap<>();

	private CarnageSpawner() {
	}

	public static void clearSessionState() {
		COOLDOWN_UNTIL.clear();
	}

	public static void onKilled(ServerLevel level) {
		COOLDOWN_UNTIL.put(level.dimension(), level.getGameTime() + CarnageConfig.get().cooldownAfterKillMinutes * 1200L);
	}

	public static void tick(MinecraftServer server) {
		CarnageConfig cfg = CarnageConfig.get();
		if (!cfg.naturalSpawnEnabled || server.getTickCount() % 1200 != 600) {
			return;
		}
		ServerLevel level = server.overworld();
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL || !level.isNight()) {
			return;
		}
		Long cd = COOLDOWN_UNTIL.get(level.dimension());
		if (cd != null && level.getGameTime() < cd) {
			return;
		}
		if (!level.getEntitiesOfClass(CarnageEntity.class, new AABB(-3.0e7, level.getMinBuildHeight(), -3.0e7, 3.0e7, level.getMaxBuildHeight(), 3.0e7)).isEmpty()) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative()) {
				continue;
			}
			double chance = Symbiote.hasSymbiote(p) ? cfg.symbioteHostChancePerMinute : cfg.spawnChancePerMinute;
			if (level.random.nextDouble() < chance && dropNear(level, p, 24, 40)) {
				COOLDOWN_UNTIL.put(level.dimension(), level.getGameTime() + cfg.cooldownAfterSpawnMinutes * 1200L);
				com.projecthero.mod.ProjectHeroMod.LOGGER.info("[Carnage] A crimson meteor is falling near {}", p.getName().getString());
				return;
			}
		}
	}

	/** Sends a meteor down {@code min}-{@code max} blocks from {@code player}. False if no open ground was found. */
	public static boolean dropNear(ServerLevel level, ServerPlayer player, int min, int max) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double d = min + level.random.nextDouble() * (max - min);
			int x = Mth.floor(player.getX() + Math.cos(a) * d);
			int z = Mth.floor(player.getZ() + Math.sin(a) * d);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (!level.isLoaded(top) || !level.getFluidState(top.below()).isEmpty() || Math.abs(top.getY() - player.getY()) > 16) {
				continue;
			}
			if (CrimsonMeteorEntity.launch(level, Vec3.atBottomCenterOf(top)) != null) {
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(x, top.getY(), z) < 160 * 160) {
						p.displayClientMessage(Component.translatable("event.projecthero.carnage.meteor").withStyle(ChatFormatting.DARK_RED), true);
					}
				}
				return true;
			}
		}
		return false;
	}
}
