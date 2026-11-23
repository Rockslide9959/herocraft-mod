package com.projecthero.mod.loot.finds;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * v0.17.5 Tier 1 "Finds": the Crashed Drone, the Hidden Cache and the Abandoned Hero Outpost; v0.17.7 adds the small
 * world places (Ruined Hero Base, Crashed Parademon Pod, Abandoned Workshop, Smugglers' Den, Tech Meteor). One structure class,
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
		HERO_OUTPOST("hero_outpost", 6, 4),
		// v0.17.7 small world places
		RUINED_HERO_BASE("ruined_hero_base", 7, 4),
		PARADEMON_POD("crashed_parademon_pod", 7, 6),
		ABANDONED_WORKSHOP("abandoned_workshop", 7, 3),
		SMUGGLERS_DEN("smugglers_den", 3, 99),
		TECH_METEOR("tech_meteor", 6, 5);

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
		if (kind == Kind.SMUGGLERS_DEN) {
			return denPoint(context, x, surface, z);
		}
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

	/**
	 * v0.17.7 Smugglers' Den: only at the foot of a cliff. The four directions are tried in a seed order; one is taken when
	 * the ground rises at least 3 blocks within 5 blocks that way and stays 4-5 up over the room behind it (3 at its
	 * sides), while the ground in front is within 3 of the entrance and dry. Local north of the piece then points into the cliff.
	 */
	private Optional<GenerationStub> denPoint(GenerationContext context, int x, int surface, int z) {
		if (surface != height(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG) || surface <= context.chunkGenerator().getSeaLevel() - 2) {
			return Optional.empty();
		}
		int[][] dirs = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } }; // the world way local north points for rotation 0..3
		int start = (int) Math.floorMod(context.chunkPos().toLong() * 0x9E3779B97F4A7C15L >>> 40, 4L);
		for (int k = 0; k < 4; k++) {
			int r = (start + k) % 4;
			int ux = dirs[r][0], uz = dirs[r][1];
			// local (lx, lz) -> world: local north (-z) is (ux, uz); local east (+x) is that turned clockwise (-uz, ux)
			java.util.function.BiFunction<Integer, Integer, Integer> hAt = (lx, lz) -> height(context, x + (-uz) * lx + ux * -lz,
					z + ux * lx + uz * -lz, Heightmap.Types.OCEAN_FLOOR_WG);
			int front = hAt.apply(0, 3);
			if (Math.abs(front - surface) > 3 || hAt.apply(0, -5) < surface + 3 || hAt.apply(0, -8) < surface + 5 || hAt.apply(0, -11) < surface + 4
					|| hAt.apply(-4, -8) < surface + 3 || hAt.apply(4, -8) < surface + 3) {
				continue;
			}
			int fx = x + (-uz) * 0 + ux * -3, fz = z + ux * 0 + uz * -3;
			if (height(context, fx, fz, Heightmap.Types.WORLD_SURFACE_WG) != front) {
				continue; // water in front
			}
			int faceZ = -5;
			for (int lz = 1; lz >= -5; lz--) {
				if (hAt.apply(0, lz) >= surface + 4) {
					faceZ = lz;
					break;
				}
			}
			int cliffTop = hAt.apply(0, faceZ) - 1;
			boolean store = hAt.apply(8, -6) >= surface + 4 && hAt.apply(10, -6) >= surface + 3;
			int rot = r;
			int fFace = faceZ;
			return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
				SmugglersDenPiece piece = new SmugglersDenPiece(x, surface, z, fFace, cliffTop);
				piece.force(piece.variant(), store ? piece.layout() : 0, rot);
				builder.addPiece(piece);
			});
		}
		return Optional.empty();
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
			case RUINED_HERO_BASE -> new RuinedHeroBasePiece(x, surface, z);
			case PARADEMON_POD -> new CrashedParademonPodPiece(x, surface, z);
			case ABANDONED_WORKSHOP -> new AbandonedWorkshopPiece(x, surface, z);
			case SMUGGLERS_DEN -> new SmugglersDenPiece(x, surface, z);
			case TECH_METEOR -> new TechMeteorPiece(x, surface, z);
		};
	}

	@Override
	public StructureType<?> type() {
		return switch (kind) {
			case CRASHED_DRONE -> FindsMod.CRASHED_DRONE_TYPE;
			case HIDDEN_CACHE -> FindsMod.HIDDEN_CACHE_TYPE;
			case HERO_OUTPOST -> FindsMod.HERO_OUTPOST_TYPE;
			case RUINED_HERO_BASE -> FindsMod.RUINED_HERO_BASE_TYPE;
			case PARADEMON_POD -> FindsMod.PARADEMON_POD_TYPE;
			case ABANDONED_WORKSHOP -> FindsMod.ABANDONED_WORKSHOP_TYPE;
			case SMUGGLERS_DEN -> FindsMod.SMUGGLERS_DEN_TYPE;
			case TECH_METEOR -> FindsMod.TECH_METEOR_TYPE;
		};
	}
}
