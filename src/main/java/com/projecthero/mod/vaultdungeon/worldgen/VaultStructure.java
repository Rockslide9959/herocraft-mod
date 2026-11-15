package com.projecthero.mod.vaultdungeon.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.17.5: The Vault ({@code "type": "projecthero:vault_dungeon"}). One {@link VaultPiece} at the chunk centre on the
 * generator's surface, in a random rotation. Never in water (the surface must be dry and above sea level - 1) and never
 * where the deepest floor would sink below {@code minBuildHeight + 8}. Placement and biomes are datapack JSON
 * ({@code worldgen/structure(_set)/the_vault.json}).
 */
public class VaultStructure extends Structure {
	public static final MapCodec<VaultStructure> CODEC = simpleCodec(VaultStructure::new);

	public VaultStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
				context.heightAccessor(), context.randomState());
		int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
				context.heightAccessor(), context.randomState());
		if (surface <= context.chunkGenerator().getSeaLevel() - 1 || surface - floor > 0) {
			return Optional.empty(); // never in the sea, a lake or a river
		}
		if (surface + VaultBuilder.INSTANCE.minY < context.heightAccessor().getMinBuildHeight() + 8) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, surface, z);
		Rotation rot = Rotation.getRandom(context.random());
		return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new VaultPiece(origin, rot))));
	}

	@Override
	public StructureType<?> type() {
		return VaultWorldgen.TYPE;
	}
}
