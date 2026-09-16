package com.projecthero.mod.worldgen;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Java-side half of a datapack-driven structure: {@code Structure}/{@code StructureSet} entries
 * themselves live in {@code data/projecthero/worldgen/structure(_set)/mjolnir_crater.json} (dynamic
 * registries, loaded from datapack JSON), but the {@link StructureType} that JSON references by
 * {@code "type": "projecthero:mjolnir_crater"} has to be a real Java object registered into
 * {@link BuiltInRegistries#STRUCTURE_TYPE} before that JSON is parsed -- exactly the same split
 * vanilla itself uses for e.g. {@code minecraft:desert_pyramid}.
 */
public final class ModStructureTypes {
	public static final StructureType<MjolnirCraterStructure> MJOLNIR_CRATER =
			register("mjolnir_crater", MjolnirCraterStructure.CODEC);

	public static final StructureType<ResearchSiteStructure> RESEARCH_SITE =
			register("research_site", ResearchSiteStructure.CODEC);

	/** The Zombie Raid Graveyard (see {@link GraveyardStructure}). */
	public static final StructureType<GraveyardStructure> GRAVEYARD =
			register("graveyard", GraveyardStructure.CODEC);

	/** Max Steel: the rare above-ground crash crater with a Steel Ultralink floating at its centre. */
	public static final StructureType<com.projecthero.mod.maxsteel.worldgen.SteelCrashSiteStructure> STEEL_CRASH_SITE =
			register("steel_crash_site", com.projecthero.mod.maxsteel.worldgen.SteelCrashSiteStructure.CODEC);

	/** Punisher: the rare buried bunker holding the Vigilante Training Manual. */
	public static final StructureType<com.projecthero.mod.punisher.worldgen.VigilanteSafehouseStructure> VIGILANTE_SAFEHOUSE =
			register("vigilante_safehouse", com.projecthero.mod.punisher.worldgen.VigilanteSafehouseStructure.CODEC);

	/** Symbiote: the rare impact crater a Symbiote rode down in. */
	public static final StructureType<com.projecthero.mod.symbiote.worldgen.SymbioteMeteorStructure> SYMBIOTE_METEOR =
			register("symbiote_meteor", com.projecthero.mod.symbiote.worldgen.SymbioteMeteorStructure.CODEC);

	/** Symbiote: the half-buried containment lab with a Symbiote in its cell. */
	public static final StructureType<com.projecthero.mod.symbiote.worldgen.SymbioteLabStructure> SYMBIOTE_LAB =
			register("symbiote_lab", com.projecthero.mod.symbiote.worldgen.SymbioteLabStructure.CODEC);

	/** Green Lantern: the rare damaged crater holding the Dormant Power Ring pedestal. */
	public static final StructureType<com.projecthero.mod.greenlantern.worldgen.FallenLanternSiteStructure> FALLEN_LANTERN_SITE =
			register("fallen_lantern_site", com.projecthero.mod.greenlantern.worldgen.FallenLanternSiteStructure.CODEC);

	private ModStructureTypes() {
	}

	public static void initialize() {
		// Classes are loaded (and the type registered) simply by referencing this class; this method
		// exists so ProjectHeroMod has an explicit, readable init call.
	}

	private static <S extends Structure> StructureType<S> register(String path, MapCodec<S> codec) {
		ResourceKey<StructureType<?>> key = ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.STRUCTURE_TYPE, key, () -> codec);
	}
}
