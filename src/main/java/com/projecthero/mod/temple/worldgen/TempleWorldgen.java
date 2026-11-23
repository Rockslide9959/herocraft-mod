package com.projecthero.mod.temple.worldgen;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** v0.17.7: the Ancient Temple's structure type and piece type (in its own package, like the Vault's). */
public final class TempleWorldgen {
	public static final StructureType<TempleStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id("ancient_temple")), () -> TempleStructure.CODEC);

	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			ResourceKey.create(Registries.STRUCTURE_PIECE, ProjectHeroMod.id("ancient_temple")), TemplePiece::new);

	private TempleWorldgen() {
	}

	public static void initialize() {
		// referencing the class registers both
	}
}
