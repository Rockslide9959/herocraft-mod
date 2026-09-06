package com.projecthero.mod.symbiote.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * Shared contract for a structure piece that {@link SymbioteWorldgen} places a one-time
 * {@link com.projecthero.mod.symbiote.entity.SymbioteEntity} inside: it just has to say where.
 *
 * <p>Implementations recompute the point fresh from their own persisted bounding box + the world
 * heightmap -- <b>never called from inside a chunk-load callback</b> ({@code getHeight} re-enters the
 * chunk system and deadlocks), only from {@link SymbioteWorldgen}'s deferred next-tick processing.
 */
public interface SymbioteSpawnPiece {
	BlockPos symbiotePos(LevelReader level);
}
