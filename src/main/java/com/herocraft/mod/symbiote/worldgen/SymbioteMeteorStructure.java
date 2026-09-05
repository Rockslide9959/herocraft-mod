package com.herocraft.mod.symbiote.worldgen;

import java.util.Optional;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.worldgen.ModStructurePieceTypes;
import com.herocraft.mod.worldgen.ModStructureTypes;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * "Symbiote Meteor" -- a rare above-ground impact crater where a chunk of alien rock came down with a
 * Symbiote riding it. A free-floating {@link com.herocraft.mod.symbiote.entity.SymbioteEntity}
 * writhes at its centre; a Spider-Man who finds it can bond.
 *
 * <p>Same shape as the Mjolnir Crater and the Steel Crash Site: one procedural
 * {@link SymbioteMeteorPiece} (not a jigsaw template) so the crater follows real terrain, with
 * biome / spacing / rarity in ordinary datapack JSON. Only this {@link StructureType} needs to be Java.
 */
public class SymbioteMeteorStructure extends Structure {
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, HeroCraftMod.id("symbiote_meteor"));

	public static final MapCodec<SymbioteMeteorStructure> CODEC = simpleCodec(SymbioteMeteorStructure::new);

	public SymbioteMeteorStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
				builder -> generatePieces(builder, context));
	}

	private static void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int estimatedY = context.chunkGenerator().getFirstOccupiedHeight(
				chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(),
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		builder.addPiece(new SymbioteMeteorPiece(chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.SYMBIOTE_METEOR;
	}

	public static net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType pieceType() {
		return ModStructurePieceTypes.SYMBIOTE_METEOR;
	}
}
