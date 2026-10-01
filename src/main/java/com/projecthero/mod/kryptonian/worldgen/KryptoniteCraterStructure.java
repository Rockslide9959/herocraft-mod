package com.projecthero.mod.kryptonian.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.worldgen.ModStructureTypes;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.14.13: the Kryptonite Meteor is a rare world-generated crater now, found the way Mjolnir is -- no more meteors
 * falling out of the night sky. One procedural {@link KryptoniteCraterPiece} (a scorched bowl with the Meteor Core at
 * the bottom, ringed by kryptonite ore). Rarity, spacing and biomes are datapack JSON
 * ({@code worldgen/structure[_set]/kryptonite_crater.json}).
 */
public class KryptoniteCraterStructure extends Structure {
	public static final MapCodec<KryptoniteCraterStructure> CODEC = simpleCodec(KryptoniteCraterStructure::new);

	public KryptoniteCraterStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
			ChunkPos chunk = context.chunkPos();
			int y = context.chunkGenerator().getFirstOccupiedHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(),
					Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			builder.addPiece(new KryptoniteCraterPiece(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ()));
		});
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.KRYPTONITE_CRATER;
	}
}
