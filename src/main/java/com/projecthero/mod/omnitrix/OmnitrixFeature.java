package com.projecthero.mod.omnitrix;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import net.minecraft.core.HolderLookup;

/**
 * v0.17.1 (user: "release everything in this update besides ben 10, ben 10 isnt done yet") held the Omnitrix back behind this
 * switch. v0.17.2: it is public, so the switch is on everywhere; the checks stay in place, and the gametests still turn it off.
 */
public final class OmnitrixFeature {
	private static boolean enabled = true;

	private OmnitrixFeature() {
	}

	public static boolean enabled() {
		return enabled;
	}

	/** Gametests only: force the switch off ({@code null} = back on). */
	public static void forceForTests(@Nullable Boolean on) {
		enabled = on == null || on;
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
