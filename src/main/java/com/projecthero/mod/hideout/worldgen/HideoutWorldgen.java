package com.projecthero.mod.hideout.worldgen;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * v0.17.5: the villain-hideout structure type and piece type (registered here rather than in {@code ModStructureTypes}
 * so the hideouts stay in their own package).
 */
public final class HideoutWorldgen {
	public static final StructureType<HideoutStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id("villain_hideout")), () -> HideoutStructure.CODEC);

	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			ResourceKey.create(Registries.STRUCTURE_PIECE, ProjectHeroMod.id("villain_hideout")), HideoutPiece::new);

	private HideoutWorldgen() {
	}

	public static void initialize() {
		// referencing the class registers both
	}
}
