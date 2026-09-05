package com.herocraft.mod.hero.item;

import com.herocraft.mod.HeroCraftMod;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;

/** Data components for HeroPack items. Separate from Thor's {@code ModDataComponents}. */
public final class HeroPackComponents {
	/** On a research note: the power key path it documents (e.g. {@code power_01_super_strength}). */
	public static final DataComponentType<String> RESEARCH_POWER = register("research_power",
			DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build());

	private HeroPackComponents() {
	}

	public static void initialize() {
	}

	private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
		ResourceKey<DataComponentType<?>> key = ResourceKey.create(Registries.DATA_COMPONENT_TYPE, HeroCraftMod.id(path));
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, type);
	}
}
