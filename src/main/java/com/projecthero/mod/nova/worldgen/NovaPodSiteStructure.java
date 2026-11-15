package com.projecthero.mod.nova.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.worldgen.ModStructureTypes;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.15.13: the rare Crashed Nova Corps Pod -- found the way the Kryptonite crater and Mjolnir are (a surface structure,
 * spacing / rarity / biomes in {@code worldgen/structure[_set]/nova_pod_site.json}). One procedural
 * {@link NovaPodSitePiece}.
 */
public class NovaPodSiteStructure extends Structure {
	public static final MapCodec<NovaPodSiteStructure> CODEC = simpleCodec(NovaPodSiteStructure::new);

	public NovaPodSiteStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
			ChunkPos chunk = context.chunkPos();
			int y = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
					Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			NovaPodSitePiece sitePiece = new NovaPodSitePiece(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ());
			builder.addPiece(sitePiece);
			// v0.17.5 Tier 2 Sites: the annex (vault room + guard squad) beside the original site
			com.projecthero.mod.loot.sites.SiteUpgrade.addAnnex(builder, context, sitePiece.getBoundingBox(), com.projecthero.mod.loot.sites.SiteTheme.NOVA_POD_SITE);
		});
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.NOVA_POD_SITE;
	}
}
