package com.projecthero.mod.omnitrix;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.HolderLookup;

/**
 * v0.17.1 (user: "release everything in this update besides ben 10, ben 10 isnt done yet"): the Omnitrix ships in the jar
 * but stays out of reach in a public install -- no natural meteor, no recipes (the {@code projecthero:omnitrix_enabled}
 * load condition), no creative-tab / JEI entries, no guide chapter and no admin grant. It is on in the dev environment
 * (runClient, the gametests) and wherever {@code config/projecthero-ben10-preview.txt} exists (the test modpack).
 * Registries are untouched, so the items, blocks and entities still exist and a world that had the watch loads fine.
 */
public final class OmnitrixFeature {
	public static final String PREVIEW_FILE = "projecthero-ben10-preview.txt";

	private static Boolean enabled;

	private OmnitrixFeature() {
	}

	public static boolean enabled() {
		if (enabled == null) {
			FabricLoader loader = FabricLoader.getInstance();
			enabled = loader.isDevelopmentEnvironment() || java.nio.file.Files.exists(loader.getConfigDir().resolve(PREVIEW_FILE));
		}
		return enabled;
	}

	/** Gametests only: force the switch on or off ({@code null} = read it again). */
	public static void forceForTests(@Nullable Boolean on) {
		enabled = on;
	}

	/** The recipe load condition: the Omnitrix recipes only load while the feature is on. */
	public record Condition() implements ResourceCondition {
		public static final MapCodec<Condition> CODEC = MapCodec.unit(Condition::new);
		public static final ResourceConditionType<Condition> TYPE = ResourceConditionType.create(ProjectHeroMod.id("omnitrix_enabled"), CODEC);

		@Override
		public ResourceConditionType<?> getType() {
			return TYPE;
		}

		@Override
		public boolean test(@Nullable HolderLookup.Provider registryLookup) {
			return enabled();
		}
	}

	static void initialize() {
		ResourceConditions.register(Condition.TYPE);
	}
}
