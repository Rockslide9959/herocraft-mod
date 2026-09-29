package com.projecthero.mod.moonknight.temple;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.worldgen.ModStructureTypes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * Moon Knight Phase 7: the Temple of Khonshu -- a rare desert-only sandstone temple whose roof is open to the moon
 * above the Altar of Khonshu, with a hidden chamber beneath holding the Scarab of Khonshu. One procedural
 * {@link TempleOfKhonshuPiece}; placement is datapack-driven ({@code worldgen/structure_set/temple_of_khonshu.json},
 * biomes {@code #projecthero:has_structure/temple_of_khonshu}), the same convention as every other mod structure.
 * The id {@code projecthero:temple_of_khonshu} is what {@code /moonknight locate_temple} looks up.
 */
public class TempleOfKhonshuStructure extends Structure {
	public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE, ProjectHeroMod.id("temple_of_khonshu"));

	public static final MapCodec<TempleOfKhonshuStructure> CODEC = simpleCodec(TempleOfKhonshuStructure::new);

	public TempleOfKhonshuStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> generatePieces(builder, context));
	}

	private static void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int y = context.chunkGenerator().getFirstOccupiedHeight(chunkPos.getMiddleBlockX(), chunkPos.getMiddleBlockZ(),
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		builder.addPiece(new TempleOfKhonshuPiece(chunkPos.getMiddleBlockX(), y, chunkPos.getMiddleBlockZ()));
	}

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.TEMPLE_OF_KHONSHU;
	}
}
