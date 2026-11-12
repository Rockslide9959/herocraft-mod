package com.projecthero.mod.doctorfate.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * v0.17.3 Doctor Fate: the Tomb of Nabu -- a rare, weathered little sandstone pyramid in the desert with Nabu's burial chamber
 * beneath it, the Helmet of Fate on its pedestal and mummy-wrapped husks on guard. One procedural {@link TombOfNabuPiece}; placement
 * is datapack-driven ({@code worldgen/structure_set/tomb_of_nabu.json}, biomes {@code #projecthero:has_structure/tomb_of_nabu}).
 * The structure and piece types register here (not in {@code ModStructureTypes}) to keep the parallel builds' merges small.
 */
public class TombOfNabuStructure extends Structure {
	public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("tomb_of_nabu"));
	public static final MapCodec<TombOfNabuStructure> CODEC = simpleCodec(TombOfNabuStructure::new);

	public static StructureType<TombOfNabuStructure> TYPE;
	public static StructurePieceType PIECE;

	public TombOfNabuStructure(StructureSettings settings) {
		super(settings);
	}

	public static void initialize() {
		if (TYPE != null) {
			return;
		}
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, ProjectHeroMod.id("tomb_of_nabu"), () -> CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, ProjectHeroMod.id("tomb_of_nabu"), TombOfNabuPiece::new);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> generatePieces(builder, context));
	}

	private static void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int y = context.chunkGenerator().getFirstOccupiedHeight(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(),
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		builder.addPiece(new TombOfNabuPiece(chunkPos.getMiddleBlockX(), y, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
