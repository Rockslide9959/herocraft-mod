package com.projecthero.mod.hulk.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.worldgen.ModStructureTypes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * v0.13.12 (Hulk Phase 4): the Gamma Lab -- a rare overworld ruin where a gamma experiment went wrong. Its reactor
 * still glows at the centre and a chest holds the Gamma Serum. One procedural {@link GammaLabPiece}, datapack-driven
 * placement ({@code worldgen/structure_set/gamma_lab.json}) -- the same convention as every other mod structure.
 */
public class GammaLabStructure extends Structure {
	public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("gamma_lab"));

	public static final MapCodec<GammaLabStructure> CODEC = simpleCodec(GammaLabStructure::new);

	public GammaLabStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> generatePieces(builder, context));
	}

	private static void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int y = context.chunkGenerator().getFirstOccupiedHeight(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(),
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		builder.addPiece(new GammaLabPiece(chunkPos.getMiddleBlockX(), y, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.GAMMA_LAB;
	}
}
