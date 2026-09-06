package com.projecthero.mod.worldgen;

import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * "Mjolnir Crater" -- a rare, above-ground-only impact site with the real, obtainable Mjolnir
 * resting at its center. Deliberately a single procedural {@link MjolnirCraterPiece} rather than a
 * jigsaw/NBT-template structure: the crater has to adapt to whatever real terrain height is under
 * it (see the piece's carving code), which a fixed template can't do on its own, and there is no
 * in-game structure-block session available to author a template with in this environment anyway.
 *
 * <p>Biome restriction, spacing/rarity and the generation step all live in
 * {@code data/projecthero/worldgen/structure/mjolnir_crater.json} and
 * {@code data/projecthero/worldgen/structure_set/mjolnir_crater.json} -- ordinary datapack JSON, the
 * same mechanism vanilla's own structures use, so none of that needs a game restart or a code change
 * to retune later. Only the {@link StructureType} that JSON references has to exist in Java; see
 * {@link ModStructureTypes}.
 */
public class MjolnirCraterStructure extends Structure {
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("mjolnir_crater"));

	public static final MapCodec<MjolnirCraterStructure> CODEC = simpleCodec(MjolnirCraterStructure::new);

	public MjolnirCraterStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		// Chunk-center, world-gen surface estimate -- exactly the pattern vanilla's own single-piece
		// surface structures (swamp hut, desert pyramid, ...) use. The piece re-samples the REAL
		// height per-column once actual terrain exists (see MjolnirCraterPiece#postProcess); this
		// estimate is only used to size the piece's bounding box.
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> generatePieces(builder, context));
	}

	private static void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int estimatedY = context.chunkGenerator().getFirstOccupiedHeight(
				chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(),
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		builder.addPiece(new MjolnirCraterPiece(chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.MJOLNIR_CRATER;
	}
}
