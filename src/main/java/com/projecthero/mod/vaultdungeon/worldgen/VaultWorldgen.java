package com.projecthero.mod.vaultdungeon.worldgen;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** v0.17.5: The Vault's structure type and piece type (kept in its own package, like the hideouts'). */
public final class VaultWorldgen {
	public static final StructureType<VaultStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id("vault_dungeon")), () -> VaultStructure.CODEC);

	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			ResourceKey.create(Registries.STRUCTURE_PIECE, ProjectHeroMod.id("vault_dungeon")), VaultPiece::new);

	private VaultWorldgen() {
	}

	public static void initialize() {
		// referencing the class registers both
	}
}
