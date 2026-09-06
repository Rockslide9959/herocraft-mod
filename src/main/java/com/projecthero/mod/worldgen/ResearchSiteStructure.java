package com.projecthero.mod.worldgen;

import java.util.Optional;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * A rare above-ground research structure. One code class covers all six kinds (spec section 6); the
 * kind is a datapack field ({@code site_type}) so each of the six {@code structure} JSONs picks its
 * own kind, biomes and rarity without new code. Deliberately a single procedural
 * {@link ResearchSitePiece} (same rationale as {@link MjolnirCraterStructure} -- it must sit on real,
 * uneven terrain that does not exist at placement time, and there is no structure-block session here
 * to author an NBT template with). No recursion, no structure-near-structure logic.
 */
public class ResearchSiteStructure extends Structure {
	public static final ResourceKey<StructureType<?>> TYPE_KEY =
			ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id("research_site"));

	public static final MapCodec<ResearchSiteStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			settingsCodec(instance),
			SiteType.CODEC.fieldOf("site_type").forGetter(s -> s.siteType)
	).apply(instance, ResearchSiteStructure::new));

	private final SiteType siteType;

	public ResearchSiteStructure(StructureSettings settings, SiteType siteType) {
		super(settings);
		this.siteType = siteType;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> generatePieces(builder, context));
	}

	private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int estimatedY = context.chunkGenerator().getFirstOccupiedHeight(
				chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(),
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		builder.addPiece(new ResearchSitePiece(chunkPos.getMiddleBlockX(), estimatedY, chunkPos.getMiddleBlockZ(), siteType));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.RESEARCH_SITE;
	}
}
