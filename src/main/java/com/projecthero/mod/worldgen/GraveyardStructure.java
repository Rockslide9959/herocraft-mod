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
 * The rare above-ground Graveyard: one source of the Gravebound Curse.
 *
 * <p>Placed with {@link #onTopOfChunkCenter} on the {@code WORLD_SURFACE_WG} heightmap, exactly like
 * {@link ResearchSiteStructure}, which is what guarantees it generates on the surface rather than
 * inside terrain or hanging in the air. Its {@code structure} and {@code structure_set} JSON live in
 * the datapack alongside every other structure this mod adds, so rarity and biome list are tunable
 * without touching code -- and because it is a real registered structure with a
 * {@code structure_set}, {@code /locate structure projecthero:graveyard} works for free.
 */
public class GraveyardStructure extends Structure {
	public static final ResourceKey<StructureType<?>> TYPE_KEY =
			ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id("graveyard"));

	/** The structure's own registry key -- used by {@link GraveyardTracker} to spot generated ones. */
	public static final ResourceKey<Structure> KEY =
			ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("graveyard"));

	public static final MapCodec<GraveyardStructure> CODEC = simpleCodec(GraveyardStructure::new);

	public GraveyardStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
				builder -> generatePieces(builder, context));
	}

	private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int estimatedY = context.chunkGenerator().getFirstOccupiedHeight(
				chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(),
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		builder.addPiece(new GraveyardPiece(chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.GRAVEYARD;
	}
}
