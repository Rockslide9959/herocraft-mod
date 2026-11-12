package com.projecthero.mod.starfire;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Starfire, Shift+V Glorious Radiance: her body lights the area. The dynamic light is the Green Lantern ring light's trick
 * ({@code GreenLanternRingLight}): one invisible vanilla {@code minecraft:light} block (level 15) in the open air cell at her head
 * (or feet), moved as she moves, only ever placed into air (or a water source) and only removed while it is still ours. A soft green
 * shimmer of particles round her. {@link #LIGHTS} holds dimension keys and positions only, and is cleared by {@code ServerStateReset}
 * (via {@link Starfire#clearSessionState}); every light is taken down on {@code SERVER_STOPPING}, so none is saved into the world.
 */
public final class StarfireRadiance {
	private record Placed(ResourceKey<Level> dimension, BlockPos pos, boolean waterlogged) {
	}

	private static final Map<UUID, Placed> LIGHTS = new ConcurrentHashMap<>();

	private StarfireRadiance() {
	}

	static void initialize() {
		ServerLifecycleEvents.SERVER_STOPPING.register(StarfireRadiance::removeAll);
	}

	static void clearSessionState() {
		LIGHTS.clear();
	}

	/** Where her light is now, or null (tests). */
	public static BlockPos lightPos(UUID owner) {
		Placed p = LIGHTS.get(owner);
		return p == null ? null : p.pos();
	}

	static void tick(ServerPlayer player) {
		follow(player);
		if (player.tickCount % 3 == 0) {
			Vec3 c = player.position().add(0, 1.0, 0);
			player.serverLevel().sendParticles(Starfire.dust(0xB8FF9A, 0.8f), c.x, c.y, c.z, 2, 0.4, 0.7, 0.4, 0.0);
		}
	}

	static void stop(UUID owner, MinecraftServer server) {
		Placed placed = LIGHTS.remove(owner);
		if (placed != null && server != null) {
			remove(server.getLevel(placed.dimension()), placed);
		}
	}

	private static void follow(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		BlockPos head = BlockPos.containing(player.getEyePosition());
		BlockPos feet = player.blockPosition();
		Placed current = LIGHTS.get(player.getUUID());
		boolean sameLevel = current != null && current.dimension() == level.dimension();
		if (sameLevel && (current.pos().equals(head) || current.pos().equals(feet)) && isOurs(level, current)) {
			return;
		}
		BlockPos target = null;
		boolean water = false;
		for (BlockPos cand : new BlockPos[] { head, feet }) {
			if (sameLevel && cand.equals(current.pos())) {
				target = cand;
				water = current.waterlogged();
				break;
			}
			BlockState s = level.getBlockState(cand);
			if (s.isAir()) {
				target = cand;
				break;
			}
			if (s.is(Blocks.WATER) && s.getFluidState().isSource()) {
				target = cand;
				water = true;
				break;
			}
		}
		if (current != null && (target == null || !current.pos().equals(target) || !sameLevel)) {
			remove(sameLevel ? level : level.getServer().getLevel(current.dimension()), current);
			LIGHTS.remove(player.getUUID());
		}
		if (target == null) {
			return;
		}
		Placed placed = new Placed(level.dimension(), target.immutable(), water);
		if (!isOurs(level, placed)) {
			BlockState here = level.getBlockState(target);
			if (!here.isAir() && !(here.is(Blocks.WATER) && here.getFluidState().isSource())) {
				return;
			}
			level.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15)
					.setValue(BlockStateProperties.WATERLOGGED, water), Block.UPDATE_ALL);
		}
		LIGHTS.put(player.getUUID(), placed);
	}

	private static boolean isOurs(ServerLevel level, Placed placed) {
		return level != null && level.isLoaded(placed.pos()) && level.getBlockState(placed.pos()).is(Blocks.LIGHT);
	}

	private static void remove(ServerLevel level, Placed placed) {
		if (!isOurs(level, placed)) {
			return;
		}
		BlockState back = placed.waterlogged() ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState();
		level.setBlock(placed.pos(), back, Block.UPDATE_ALL);
	}

	private static void removeAll(MinecraftServer server) {
		for (Placed placed : LIGHTS.values()) {
			remove(server.getLevel(placed.dimension()), placed);
		}
		LIGHTS.clear();
	}
}
