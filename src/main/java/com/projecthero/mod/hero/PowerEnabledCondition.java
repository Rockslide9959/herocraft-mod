package com.projecthero.mod.hero;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import net.minecraft.core.HolderLookup;

/**
 * v0.14.8: the {@code projecthero:power_enabled} resource condition -- true while the experimental power {@code power}
 * is in {@link Powers#ENABLED}. Every {@code *_reagent.json} recipe carries it under {@code fabric:load_conditions}, so
 * a disabled power's reagent recipe simply does not load (the JSON stays, re-enabling needs no data change).
 */
public record PowerEnabledCondition(String power) implements ResourceCondition {
	public static final MapCodec<PowerEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("power").forGetter(PowerEnabledCondition::power)).apply(i, PowerEnabledCondition::new));

	public static final ResourceConditionType<PowerEnabledCondition> TYPE =
			ResourceConditionType.create(ProjectHeroMod.id("power_enabled"), CODEC);

	public static void initialize() {
		ResourceConditions.register(TYPE);
	}

	@Override
	public ResourceConditionType<?> getType() {
		return TYPE;
	}

	@Override
	public boolean test(@Nullable HolderLookup.Provider registryLookup) {
		return Powers.isEnabled(power);
	}
}
