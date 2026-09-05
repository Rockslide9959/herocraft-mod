package com.herocraft.mod.worldgen;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructurePieceTypes {
	/** {@link StructurePieceType} is a functional interface ({@code load(context, tag)}), so the
	 * piece's own NBT-deserializing constructor can be registered directly as a method reference. */
	public static final StructurePieceType MJOLNIR_CRATER = register("mjolnir_crater", MjolnirCraterPiece::new);
	public static final StructurePieceType RESEARCH_SITE = register("research_site", ResearchSitePiece::new);
	public static final StructurePieceType GRAVEYARD = register("graveyard", GraveyardPiece::new);
	public static final StructurePieceType STEEL_CRASH_SITE =
			register("steel_crash_site", com.herocraft.mod.maxsteel.worldgen.SteelCrashSitePiece::new);
	public static final StructurePieceType VIGILANTE_SAFEHOUSE =
			register("vigilante_safehouse", com.herocraft.mod.punisher.worldgen.VigilanteSafehousePiece::new);
	public static final StructurePieceType SYMBIOTE_METEOR =
			register("symbiote_meteor", com.herocraft.mod.symbiote.worldgen.SymbioteMeteorPiece::new);
	public static final StructurePieceType SYMBIOTE_LAB =
			register("symbiote_lab", com.herocraft.mod.symbiote.worldgen.SymbioteLabPiece::new);

	private ModStructurePieceTypes() {
	}

	public static void initialize() {
	}

	private static StructurePieceType register(String path, StructurePieceType type) {
		ResourceKey<StructurePieceType> key = ResourceKey.create(Registries.STRUCTURE_PIECE, HeroCraftMod.id(path));
		return Registry.register(BuiltInRegistries.STRUCTURE_PIECE, key, type);
	}
}
