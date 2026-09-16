package com.projecthero.mod.greenlantern.worldgen;

import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.worldgen.ModStructurePieceTypes;
import com.projecthero.mod.worldgen.ModStructureTypes;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * "Fallen Lantern Site" -- a rare, damaged alien landing crater holding the Dormant Power Ring
 * pedestal. Same shape as {@code SteelCrashSiteStructure}: a single procedural piece (not NBT/jigsaw)
 * so it follows real terrain everywhere, with biome/spacing/rarity in ordinary datapack JSON
 * ({@code data/projecthero/worldgen/structure(_set)/fallen_lantern_site.json}). {@code random_spread}
 * placement -- not world-unique -- so multiple players on a server can each find their own instance.
 */
public class FallenLanternSiteStructure extends Structure {
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("fallen_lantern_site"));

	public static final MapCodec<FallenLanternSiteStructure> CODEC = simpleCodec(FallenLanternSiteStructure::new);

	public FallenLanternSiteStructure(StructureSettings settings) {
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
		builder.addPiece(new FallenLanternSitePiece(chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.FALLEN_LANTERN_SITE;
	}

	public static net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType pieceType() {
		return ModStructurePieceTypes.FALLEN_LANTERN_SITE;
	}
}
