package com.projecthero.mod.hulk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): Shift+C -- Earth Wall. He tears a wall of earth up out of the ground in front of him: each column is
 * built from the ground it rises out of (the surface block on top, what lies under it below), row by row from the bottom.
 * It is cover -- real blocks -- and after {@code earthWallTicks} it crumbles: every block that is still the one he raised
 * goes back to exactly what was there before (air, grass, flowers). Only air and replaceable plants are ever replaced;
 * never fluids or block entities. A server stopping with a wall up puts the ground back first.
 */
public final class HulkEarthWall {
	private static final class Wall {
		final ResourceKey<Level> dim;
		/** Planned blocks, bottom row first: position -> the state he raises there. */
		final List<Map.Entry<BlockPos, BlockState>> plan = new ArrayList<>();
		final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
		final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
		final int rowSize;
		int next;
		long age;
		final int life;

		Wall(ResourceKey<Level> dim, int rowSize, int life) {
			this.dim = dim;
			this.rowSize = rowSize;
			this.life = life;
		}
	}

	private static final List<Wall> WALLS = new ArrayList<>();
	/** Ticks between rows as it rises. */
	private static final int ROW_TICKS = 2;

	static {
		ServerTickEvents.END_SERVER_TICK.register(HulkEarthWall::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(HulkEarthWall::restoreAll);
	}

	private HulkEarthWall() {
	}

	public static void clearSessionState() {
		WALLS.clear();
	}

	/** How many walls are standing (tests). */
	public static int standing() {
		return WALLS.size();
	}

	/** Raises a wall in front of {@code player}. False if there was no ground to tear up. */
	static boolean raise(ServerPlayer player) {
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 fwd = HulkAbilities.flatLook(player);
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 center = player.position().add(fwd.scale(cfg.earthWallDistance + player.getBbWidth() * 0.5));
		int half = Math.max(1, cfg.earthWallWidth) / 2;
		int height = Math.max(1, cfg.earthWallHeight);
		List<BlockPos> bases = new ArrayList<>();
		List<BlockState[]> mats = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (int i = -half; i <= half; i++) {
			Vec3 c = center.add(side.scale(i));
			BlockPos surface = GroundCracks.surface(level, c.x, player.getY() + 1.0, c.z);
			if (surface == null || Math.abs(surface.getY() - (player.getY() - 1)) > 3 || !seen.add(surface.asLong())) {
				continue;
			}
			BlockState cap = level.getBlockState(surface);
			BlockState body = level.getBlockState(surface.below());
			if (cap.hasBlockEntity() || !cap.isCollisionShapeFullBlock(level, surface)) {
				cap = Blocks.DIRT.defaultBlockState();
			}
			if (body.isAir() || body.hasBlockEntity() || !body.isCollisionShapeFullBlock(level, surface.below())) {
				body = cap.is(Blocks.GRASS_BLOCK) || cap.is(Blocks.MYCELIUM) || cap.is(Blocks.PODZOL) ? Blocks.DIRT.defaultBlockState() : cap;
			}
			bases.add(surface);
			mats.add(new BlockState[] { cap, body });
		}
		if (bases.isEmpty()) {
			return false;
		}
		Wall wall = new Wall(level.dimension(), bases.size(), cfg.earthWallTicks + height * ROW_TICKS);
		for (int h = 1; h <= height; h++) {
			for (int k = 0; k < bases.size(); k++) {
				BlockState state = h == height ? mats.get(k)[0] : mats.get(k)[1];
				wall.plan.add(Map.entry(bases.get(k).above(h), state));
			}
		}
		// the strip of ground he tore it out of cracks open
		GroundCracks.line(level, center.subtract(side.scale(half + 0.5)), side, half * 2 + 1.0, 1.0, 9, cfg.earthWallTicks);
		GroundCracks.line(level, player.position(), fwd, cfg.earthWallDistance, 1.0, 6, cfg.earthWallTicks / 2);
		WALLS.add(wall);
		Vec3 m = center;
		level.playSound(null, m.x, m.y, m.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, m.x, m.y, m.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.6f, 0.6f);
		level.playSound(null, m.x, m.y, m.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.5f, 0.5f);
		HulkCombat.shake(level, m, 0.45f, 12, 18.0);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (WALLS.isEmpty()) {
			return;
		}
		Iterator<Wall> it = WALLS.iterator();
		while (it.hasNext()) {
			Wall w = it.next();
			ServerLevel level = server.getLevel(w.dim);
			if (level == null) {
				it.remove();
				continue;
			}
			w.age++;
			if (w.next < w.plan.size() && w.age % ROW_TICKS == 0) {
				riseRow(level, w);
			}
			if (w.age >= w.life) {
				crumble(level, w, true);
				it.remove();
			}
		}
	}

	private static void riseRow(ServerLevel level, Wall w) {
		int end = Math.min(w.plan.size(), w.next + w.rowSize);
		for (int i = w.next; i < end; i++) {
			BlockPos pos = w.plan.get(i).getKey();
			BlockState state = w.plan.get(i).getValue();
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState was = level.getBlockState(pos);
			if (!(was.isAir() || was.canBeReplaced()) || !was.getFluidState().isEmpty() || was.hasBlockEntity()) {
				continue;
			}
			// shove anything standing there out of the way first
			AABB box = new AABB(pos);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
				e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
				e.hurtMarked = true;
			}
			w.original.put(pos, was);
			w.placed.put(pos, state);
			level.setBlock(pos, state, 3);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					6, 0.4, 0.4, 0.4, 0.15);
			if (i % 3 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
						3, 0.3, 0.0, 0.3, 0.2);
			}
		}
		w.next = end;
		BlockPos mid = w.plan.get(Math.min(w.plan.size() - 1, end - 1)).getKey();
		level.playSound(null, mid, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
	}

	private static void crumble(ServerLevel level, Wall w, boolean effects) {
		List<BlockPos> top = new ArrayList<>(w.placed.keySet());
		java.util.Collections.reverse(top); // top rows first
		int n = 0;
		for (BlockPos pos : top) {
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState placed = w.placed.get(pos);
			if (level.getBlockState(pos) != placed) {
				continue; // someone dug it out or built over it: leave it be
			}
			level.setBlock(pos, w.original.getOrDefault(pos, Blocks.AIR.defaultBlockState()), 3);
			if (effects && n++ % 2 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, placed), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						8, 0.4, 0.4, 0.4, 0.05);
			}
		}
		if (effects && !top.isEmpty()) {
			BlockPos mid = top.get(top.size() / 2);
			level.playSound(null, mid, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.6f, 0.7f);
			level.playSound(null, mid, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		}
	}

	private static void restoreAll(MinecraftServer server) {
		for (Wall w : WALLS) {
			ServerLevel level = server.getLevel(w.dim);
			if (level != null) {
				crumble(level, w, false);
			}
		}
		WALLS.clear();
	}
}
