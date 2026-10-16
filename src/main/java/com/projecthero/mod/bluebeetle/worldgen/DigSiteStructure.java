package com.projecthero.mod.bluebeetle.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.worldgen.ModStructureTypes;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.16.5: the Blue Beetle dig site -- a surface structure in deserts and badlands (spacing / biomes in
 * {@code worldgen/structure[_set]/dig_site.json}), one procedural {@link DigSitePiece}.
 */
public class DigSiteStructure extends Structure {
	public static final MapCodec<DigSiteStructure> CODEC = simpleCodec(DigSiteStructure::new);

	public DigSiteStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
			ChunkPos chunk = context.chunkPos();
			int y = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
					Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			builder.addPiece(new DigSitePiece(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ()));
		});
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.DIG_SITE;
	}
}
