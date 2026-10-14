package com.projecthero.mod.city;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/**
 * v0.16.2 City part A: the City structure ({@code projecthero:city}). It is a vanilla jigsaw structure in every way
 * that matters -- {@link JigsawPlacement#addPieces} builds it out of {@code PoolElementStructurePiece}s from the
 * {@code projecthero:city/...} template pools, chunk by chunk like a village -- with two additions vanilla's
 * {@code minecraft:jigsaw} type can't express:
 * <ul>
 * <li><b>A terrain check.</b> A 240-block city doesn't belong on a mountainside or in a lake, so the start is
 * rejected when the downtown core or the whole footprint is too uneven, or when the core is under water (sampled on a
 * coarse grid from the noise generator -- a few dozen height lookups per candidate, and candidates are 160 chunks
 * apart).</li>
 * <li><b>A biome-matched village edge.</b> The outer ring is real vanilla village: its connectors point at the alias
 * pools {@code projecthero:city/village/houses}, {@code .../streets} and {@code projecthero:city/villagers}, which
 * this type redirects to {@code minecraft:village/savanna/...} when the city's centre is in a savanna and to
 * {@code minecraft:village/plains/...} otherwise. (The alias pools' own JSON is the plains set, for {@code /place
 * jigsaw} and tests.)</li>
 * </ul>
 * Everything else is data: {@code worldgen/structure/city.json}, {@code worldgen/structure_set/city.json} and the
 * pools/templates under {@code projecthero:city/}. See {@code docs/CITY_REFERENCE.md}.
 */
public class CityStructure extends Structure {
	public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("projecthero", "city"));

	public static final MapCodec<CityStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
			Codec.intRange(0, JigsawStructure.MAX_DEPTH).fieldOf("size").forGetter(s -> s.maxDepth),
			Codec.intRange(1, JigsawStructure.MAX_TOTAL_STRUCTURE_RANGE).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
			Codec.intRange(0, 256).optionalFieldOf("max_core_height_variation", 14).forGetter(s -> s.maxCoreVariation),
			Codec.intRange(0, 256).optionalFieldOf("max_height_variation", 36).forGetter(s -> s.maxVariation),
			Codec.intRange(0, 100).optionalFieldOf("max_water_percent", 25).forGetter(s -> s.maxWaterPercent))
			.apply(i, CityStructure::new));

	/** Half-width of the downtown core (the four city blocks reach 55 blocks from the centre). */
	public static final int CORE_RADIUS = 48;
	/** Half-width of the sampled footprint (the ring road is at 96; vanilla houses spill a little further). */
	public static final int FOOTPRINT_RADIUS = 104;

	public static final ResourceLocation ALIAS_HOUSES = ResourceLocation.fromNamespaceAndPath("projecthero", "city/village/houses");
	public static final ResourceLocation ALIAS_STREETS = ResourceLocation.fromNamespaceAndPath("projecthero", "city/village/streets");
	public static final ResourceLocation ALIAS_VILLAGERS = ResourceLocation.fromNamespaceAndPath("projecthero", "city/villagers");

	private final Holder<StructureTemplatePool> startPool;
	private final int maxDepth;
	private final int maxDistanceFromCenter;
	private final int maxCoreVariation;
	private final int maxVariation;
	private final int maxWaterPercent;

	public CityStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, int maxDistanceFromCenter,
			int maxCoreVariation, int maxVariation, int maxWaterPercent) {
		super(settings);
		this.startPool = startPool;
		this.maxDepth = maxDepth;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.maxCoreVariation = maxCoreVariation;
		this.maxVariation = maxVariation;
		this.maxWaterPercent = maxWaterPercent;
	}

	/** The village style the edge uses: "savanna" in savanna biomes, "plains" everywhere else. */
	public static String villageStyle(Holder<Biome> biome) {
		return biome.is(BiomeTags.HAS_VILLAGE_SAVANNA) ? "savanna" : "plains";
	}

	/** The pool alias lookup for a village style (unmapped keys pass through unchanged). */
	public static PoolAliasLookup aliasesFor(String style) {
		Map<ResourceKey<StructureTemplatePool>, ResourceKey<StructureTemplatePool>> map = new HashMap<>();
		map.put(pool(ALIAS_HOUSES), pool(ResourceLocation.withDefaultNamespace("village/" + style + "/houses")));
		map.put(pool(ALIAS_STREETS), pool(ResourceLocation.withDefaultNamespace("village/" + style + "/streets")));
		map.put(pool(ALIAS_VILLAGERS), pool(ResourceLocation.withDefaultNamespace("village/" + style + "/villagers")));
		return key -> map.getOrDefault(key, key);
	}

	private static ResourceKey<StructureTemplatePool> pool(ResourceLocation id) {
		return ResourceKey.create(Registries.TEMPLATE_POOL, id);
	}

	/**
	 * Terrain check result for a candidate centre: null when the site is fine, otherwise why it was rejected (kept as
	 * a string so tests and the debug log can say what happened).
	 */
	public String siteProblem(GenerationContext context, int cx, int cz) {
		// the downtown core first (3 x 3 over +-48), then the rest of the footprint (5 x 5 over +-104), bailing out at
		// the first broken limit: one noise column per sample, so a rejected site costs only a few lookups
		int coreMin = Integer.MAX_VALUE;
		int coreMax = Integer.MIN_VALUE;
		for (int dx = -CORE_RADIUS; dx <= CORE_RADIUS; dx += CORE_RADIUS) {
			for (int dz = -CORE_RADIUS; dz <= CORE_RADIUS; dz += CORE_RADIUS) {
				int[] h = heights(context, cx + dx, cz + dz);
				if (h[0] - h[1] > 1) {
					return "the downtown core is under water";
				}
				coreMin = Math.min(coreMin, h[1]);
				coreMax = Math.max(coreMax, h[1]);
				if (coreMax - coreMin > maxCoreVariation) {
					return "the downtown core varies by more than " + maxCoreVariation + " blocks";
				}
			}
		}
		int min = coreMin;
		int max = coreMax;
		int water = 0;
		int step = FOOTPRINT_RADIUS / 2;
		for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx += step) {
			for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz += step) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int[] h = heights(context, cx + dx, cz + dz);
				if (h[0] - h[1] > 1 && ++water * 100 > maxWaterPercent * 24) {
					return water + " outer samples are water (max " + maxWaterPercent + "%)";
				}
				min = Math.min(min, h[1]);
				max = Math.max(max, h[1]);
				if (max - min > maxVariation) {
					return "the footprint varies by more than " + maxVariation + " blocks";
				}
			}
		}
		return null;
	}

	/** {surface, floor} of a column (top non-air block, top block that isn't fluid), from one noise column. */
	private static int[] heights(GenerationContext context, int x, int z) {
		var column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
		int minY = context.heightAccessor().getMinBuildHeight();
		int surface = minY;
		int floor = minY;
		for (int y = context.heightAccessor().getMaxBuildHeight() - 1; y >= minY; y--) {
			var state = column.getBlock(y);
			if (state.isAir()) {
				continue;
			}
			if (surface == minY) {
				surface = y;
			}
			if (state.getFluidState().isEmpty()) {
				floor = y;
				break;
			}
		}
		return new int[] {surface, floor};
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		// the start piece (15 x 15) is placed from the chunk's corner; its centre is within 7 blocks of it
		int cx = chunkPos.getMinBlockX();
		int cz = chunkPos.getMinBlockZ();
		// cheapest test first: the biome at sea level (no noise column needed). Vanilla checks the biome only after
		// the start is built, and /locate and the City Map trade evaluate every candidate this way.
		Holder<Biome> biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(64), QuartPos.fromBlock(cz),
				context.randomState().sampler());
		if (!context.validBiome().test(biome) || siteProblem(context, cx, cz) != null) {
			return Optional.empty();
		}
		return JigsawPlacement.addPieces(context, startPool, Optional.empty(), maxDepth, new BlockPos(cx, 0, cz), true,
				Optional.of(Heightmap.Types.WORLD_SURFACE_WG), maxDistanceFromCenter, aliasesFor(villageStyle(biome)),
				JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
	}

	@Override
	public StructureType<?> type() {
		return CityWorldgen.CITY_TYPE;
	}
}
