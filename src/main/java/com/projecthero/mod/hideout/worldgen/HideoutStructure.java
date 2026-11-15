package com.projecthero.mod.hideout.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.hideout.HideoutFaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * v0.17.5: a Tier 3 villain hideout ({@code "type": "projecthero:villain_hideout"}, {@code "faction": "..."}). One
 * {@link HideoutPiece} at the chunk centre, on the generator's surface height, in a random rotation. Placement and
 * biomes are datapack JSON ({@code worldgen/structure(_set)/<hideout>.json}).
 */
public class HideoutStructure extends Structure {
	public static final MapCodec<HideoutStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			HideoutFaction.CODEC.fieldOf("faction").forGetter(s -> s.faction)).apply(i, HideoutStructure::new));

	private final HideoutFaction faction;

	public HideoutStructure(StructureSettings settings, HideoutFaction faction) {
		super(settings);
		this.faction = faction;
	}

	public HideoutFaction faction() {
		return faction;
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
				context.heightAccessor(), context.randomState());
		if (surface <= context.chunkGenerator().getSeaLevel() - 2) {
			return Optional.empty(); // never in the sea
		}
		HideoutBuilder b = HideoutBuilder.of(faction);
		BlockPos origin = new BlockPos(x, b.floorY(surface), z);
		Rotation rot = Rotation.getRandom(context.random());
		return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new HideoutPiece(faction, origin, rot))));
	}

	@Override
	public StructureType<?> type() {
		return HideoutWorldgen.TYPE;
	}
}
