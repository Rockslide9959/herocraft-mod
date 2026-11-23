package com.projecthero.mod.temple.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.17.7: the Ancient Temple ({@code "type": "projecthero:ancient_temple"}). One {@link TemplePiece} at the chunk centre
 * on the generator's surface, in a random rotation; jungle biomes get the mossy-stone look, anything else (the desert) the
 * sandstone one. Never in water, never where the surface varies more than {@link #MAX_SLOPE} over the platform, and never
 * where the throne room would sink below {@code minBuildHeight + 8}. Placement and biomes are datapack JSON
 * ({@code worldgen/structure(_set)/ancient_temple.json}).
 */
public class TempleStructure extends Structure {
	public static final MapCodec<TempleStructure> CODEC = simpleCodec(TempleStructure::new);
	public static final int MAX_SLOPE = 10;

	public TempleStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		var gen = context.chunkGenerator();
		int surface = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		int floor = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
		if (surface <= gen.getSeaLevel() - 1 || surface - floor > 0) {
			return Optional.empty();
		}
		int lo = surface, hi = surface;
		for (int[] d : new int[][] { { -16, -16 }, { 16, -16 }, { -16, 16 }, { 16, 16 } }) {
			int h = gen.getFirstOccupiedHeight(x + d[0], z + d[1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			lo = Math.min(lo, h);
			hi = Math.max(hi, h);
		}
		if (hi - lo > MAX_SLOPE) {
			return Optional.empty();
		}
		if (surface + TempleBuilder.INSTANCE.minY < context.heightAccessor().getMinBuildHeight() + 8) {
			return Optional.empty();
		}
		Holder<Biome> biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z),
				context.randomState().sampler());
		boolean desert = !biome.is(BiomeTags.IS_JUNGLE);
		BlockPos origin = new BlockPos(x, surface, z);
		Rotation rot = Rotation.getRandom(context.random());
		return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new TemplePiece(origin, rot, desert))));
	}

	@Override
	public StructureType<?> type() {
		return TempleWorldgen.TYPE;
	}
}
