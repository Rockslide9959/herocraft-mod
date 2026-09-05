package com.herocraft.mod.punisher.worldgen;

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
 * "Abandoned Vigilante Safehouse" -- a rare, mostly-buried bunker where a vigilante trained and
 * armed themselves (spec section 31). Contains a weapon workbench, ammunition and supply chests,
 * target boards, a small training area, and -- guaranteed -- the Vigilante Training Manual.
 *
 * <p>Same shape as the Steel Crash Site / Mjolnir Crater: one procedural {@link VigilanteSafehousePiece}
 * (not a jigsaw template) so it sits correctly against real terrain, with biome / spacing / rarity
 * all in ordinary datapack JSON. Only this {@link StructureType} exists in Java.
 */
public class VigilanteSafehouseStructure extends Structure {
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, HeroCraftMod.id("vigilante_safehouse"));

	public static final MapCodec<VigilanteSafehouseStructure> CODEC = simpleCodec(VigilanteSafehouseStructure::new);

	public VigilanteSafehouseStructure(StructureSettings settings) {
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
		builder.addPiece(new VigilanteSafehousePiece(
				chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.VIGILANTE_SAFEHOUSE;
	}

	public static net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType pieceType() {
		return ModStructurePieceTypes.VIGILANTE_SAFEHOUSE;
	}
}
