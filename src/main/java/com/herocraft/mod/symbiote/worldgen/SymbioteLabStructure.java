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
 * "Symbiote Lab" -- a small half-buried containment facility built by someone who found a Symbiote
 * first and tried to study it. A {@link com.herocraft.mod.symbiote.entity.SymbioteEntity} sits
 * in the tinted-glass cell at its centre; a Spider-Man can bond with it.
 *
 * <p>Single procedural {@link SymbioteLabPiece}, datapack-driven placement, one Java {@link StructureType}
 * -- same convention as the other HeroCraft structures.
 */
public class SymbioteLabStructure extends Structure {
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, HeroCraftMod.id("symbiote_lab"));

	public static final MapCodec<SymbioteLabStructure> CODEC = simpleCodec(SymbioteLabStructure::new);

	public SymbioteLabStructure(StructureSettings settings) {
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
		builder.addPiece(new SymbioteLabPiece(chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.SYMBIOTE_LAB;
	}

	public static net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType pieceType() {
		return ModStructurePieceTypes.SYMBIOTE_LAB;
	}
}
