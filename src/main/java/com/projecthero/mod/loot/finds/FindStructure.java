package com.projecthero.mod.loot.finds;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * v0.17.5 Tier 1 "Finds": the Crashed Drone, the Hidden Cache and the Abandoned Hero Outpost. One structure class,
 * one {@link StructureType} per kind (so each has its own {@code "type"} in {@code worldgen/structure/<id>.json}),
 * one procedural piece each. Placement is datapack-driven ({@code worldgen/structure_set/<id>.json}), the same
 * convention as every other mod structure, but much denser: Finds are the common start of the progression chain.
 *
 * <p>Never on water (the surface and the ocean floor heights must match) and never on a steep slope (the corners of
 * the footprint may differ by at most {@link Kind#maxSlope}).
 */
public class FindStructure extends Structure {
	public enum Kind {
		CRASHED_DRONE("crashed_drone", 6, 6),
		HIDDEN_CACHE("hidden_cache", 3, 2),
		HERO_OUTPOST("hero_outpost", 6, 4);

		public final String id;
		final int half;
		final int maxSlope;

		Kind(String id, int half, int maxSlope) {
			this.id = id;
			this.half = half;
			this.maxSlope = maxSlope;
		}
	}

	public static MapCodec<FindStructure> codec(Kind kind) {
		return simpleCodec(settings -> new FindStructure(settings, kind));
	}

	private final Kind kind;

	public FindStructure(StructureSettings settings, Kind kind) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int surface = height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
		// water (or lava) on top: the ocean-floor height is lower than the surface
		if (surface != height(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG) || surface <= context.chunkGenerator().getSeaLevel() - 2) {
			return Optional.empty();
		}
		int min = surface, max = surface;
		int h = kind.half;
		for (int[] o : new int[][] { { -h, -h }, { h, -h }, { -h, h }, { h, h } }) {
			int y = height(context, x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG);
			min = Math.min(min, y);
			max = Math.max(max, y);
		}
		if (max - min > kind.maxSlope) {
			return Optional.empty();
		}
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> generatePieces(builder, x, surface, z));
	}

	private static int height(GenerationContext context, int x, int z, Heightmap.Types type) {
		return context.chunkGenerator().getFirstOccupiedHeight(x, z, type, context.heightAccessor(), context.randomState());
	}

	private void generatePieces(StructurePiecesBuilder builder, int x, int surface, int z) {
		builder.addPiece(createPiece(kind, x, surface, z));
	}

	/** A piece of the given kind centred on (x, z) with the first air block above the ground at {@code surface}. */
	public static FindPiece createPiece(Kind kind, int x, int surface, int z) {
		return switch (kind) {
			case CRASHED_DRONE -> new CrashedDronePiece(x, surface, z);
			case HIDDEN_CACHE -> new HiddenCachePiece(x, surface, z);
			case HERO_OUTPOST -> new HeroOutpostPiece(x, surface, z);
		};
	}

	@Override
	public StructureType<?> type() {
		return switch (kind) {
			case CRASHED_DRONE -> FindsMod.CRASHED_DRONE_TYPE;
			case HIDDEN_CACHE -> FindsMod.HIDDEN_CACHE_TYPE;
			case HERO_OUTPOST -> FindsMod.HERO_OUTPOST_TYPE;
		};
	}
}
