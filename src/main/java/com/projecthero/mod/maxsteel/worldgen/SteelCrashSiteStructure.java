package com.projecthero.mod.maxsteel.worldgen;

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
 * "Steel Crash Site" -- a rare above-ground impact crater where a Steel Ultralink came down, with the
 * {@link com.projecthero.mod.maxsteel.entity.SteelEntity Steel} entity floating at its centre.
 *
 * <p>Same shape as the Mjolnir Crater: a single procedural {@link SteelCrashSitePiece} (not a
 * jigsaw/NBT template) so the crater follows real terrain height, with biome / spacing / rarity all in
 * ordinary datapack JSON ({@code data/projecthero/worldgen/structure(_set)/steel_crash_site.json}). Only
 * this {@link StructureType} has to exist in Java.
 *
 * <p><b>Not world-unique.</b> The {@code random_spread} placement means many crash sites generate
 * across a world, so multiple players on a server can each find and bond with a Steel.
 */
public class SteelCrashSiteStructure extends Structure {
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("steel_crash_site"));

	public static final MapCodec<SteelCrashSiteStructure> CODEC = simpleCodec(SteelCrashSiteStructure::new);

	public SteelCrashSiteStructure(StructureSettings settings) {
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
		builder.addPiece(new SteelCrashSitePiece(chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.STEEL_CRASH_SITE;
	}

	/** Referenced by {@link SteelCrashSitePiece}'s registration; keeps the piece-type id in one place. */
	public static net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType pieceType() {
		return ModStructurePieceTypes.STEEL_CRASH_SITE;
	}
}
