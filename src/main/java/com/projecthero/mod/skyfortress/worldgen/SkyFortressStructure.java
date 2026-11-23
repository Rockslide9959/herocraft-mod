package com.projecthero.mod.skyfortress.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.hideout.worldgen.HideoutCanvas;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.17.7: the Sky Fortress ({@code "type": "projecthero:sky_fortress"}). Two pieces: the fortress at a fixed height
 * ({@link #DECK_Y}, its lower deck floor; the whole ship spans about y 188-221) centred on the chunk, in a random rotation,
 * and the Launch Pad post on the ground straight under its lift hatch -- on piles at sea level when the hatch is over
 * water. Skipped where the ground under the hatch is higher than {@link #MAX_GROUND} (it would crowd the hull). The
 * biome test uses the ground under the hatch. Placement and biomes are datapack JSON
 * ({@code worldgen/structure(_set)/sky_fortress.json}).
 */
public class SkyFortressStructure extends Structure {
	public static final MapCodec<SkyFortressStructure> CODEC = simpleCodec(SkyFortressStructure::new);
	public static final int DECK_Y = 196;
	public static final int MAX_GROUND = 150;

	public SkyFortressStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		BlockPos origin = new BlockPos(chunk.getMiddleBlockX(), DECK_Y, chunk.getMiddleBlockZ());
		Rotation rot = Rotation.getRandom(context.random());
		HideoutCanvas probe = new HideoutCanvas(null, null, origin, rot, 0L, null);
		BlockPos hatch = probe.at(SkyFortressBuilder.HATCH.getX(), 0, SkyFortressBuilder.HATCH.getZ());
		int surface = context.chunkGenerator().getFirstOccupiedHeight(hatch.getX(), hatch.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
				context.heightAccessor(), context.randomState());
		int floor = context.chunkGenerator().getFirstOccupiedHeight(hatch.getX(), hatch.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
				context.heightAccessor(), context.randomState());
		if (surface > MAX_GROUND || DECK_Y + 30 > context.heightAccessor().getMaxBuildHeight()) {
			return Optional.empty();
		}
		int sea = context.chunkGenerator().getSeaLevel();
		boolean water = surface - floor > 0 || surface < sea - 1;
		int padY = water ? sea : surface;
		BlockPos pad = new BlockPos(hatch.getX(), padY, hatch.getZ());
		return Optional.of(new GenerationStub(pad, builder -> {
			builder.addPiece(new SkyFortressPiece(origin, rot));
			builder.addPiece(new LaunchPadPiece(pad, rot, water));
		}));
	}

	@Override
	public StructureType<?> type() {
		return SkyFortressWorldgen.TYPE;
	}
}
