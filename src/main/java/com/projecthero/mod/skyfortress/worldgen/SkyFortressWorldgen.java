package com.projecthero.mod.skyfortress.worldgen;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** v0.17.7: the Sky Fortress's structure type and its two piece types (fortress, launch pad). */
public final class SkyFortressWorldgen {
	public static final StructureType<SkyFortressStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id("sky_fortress")), () -> SkyFortressStructure.CODEC);

	public static final StructurePieceType FORTRESS_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			ResourceKey.create(Registries.STRUCTURE_PIECE, ProjectHeroMod.id("sky_fortress")), SkyFortressPiece::new);

	public static final StructurePieceType PAD_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			ResourceKey.create(Registries.STRUCTURE_PIECE, ProjectHeroMod.id("sky_launch_pad")), LaunchPadPiece::new);

	private SkyFortressWorldgen() {
	}

	public static void initialize() {
	}
}
