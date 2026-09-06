package com.projecthero.mod.hero.mutation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

/**
 * One custom {@link Potion} per experimental power ("Experimental Adrenal Serum" etc.), each simply
 * an {@link ModMobEffects#UNSTABLE_MUTATION} instance whose amplifier is the power's index. Built
 * data-driven from {@link Powers} at init, so adding a power adds its serum automatically.
 */
public final class ModSerums {
	private static final Map<String, Holder<Potion>> BY_POWER_KEY = new LinkedHashMap<>();
	private static final List<Power> ORDER = new ArrayList<>();

	private ModSerums() {
	}

	public static void initialize() {
		int duration = Math.max(200, HeroConfig.get().unstableMutationDurationTicks);
		int index = 0;
		for (Power power : Powers.all()) {
			ORDER.add(power);
			MobEffectInstance effect = new MobEffectInstance(
					ModMobEffects.UNSTABLE_MUTATION, duration, index, false, true, true);
			Potion potion = new Potion(shortName(power), effect);
			Holder<Potion> holder = Registry.registerForHolder(BuiltInRegistries.POTION,
					ProjectHeroMod.id("serum_" + shortName(power)), potion);
			BY_POWER_KEY.put(power.key(), holder);
			index++;
		}
	}

	public static Holder<Potion> serum(Power power) {
		return BY_POWER_KEY.get(power.key());
	}

	public static Holder<Potion> serum(String powerKey) {
		return BY_POWER_KEY.get(powerKey);
	}

	/** The power an {@code UNSTABLE_MUTATION} amplifier refers to, or null if out of range. */
	public static Power powerForAmplifier(int amplifier) {
		return amplifier >= 0 && amplifier < ORDER.size() ? ORDER.get(amplifier) : null;
	}

	public static int amplifierFor(Power power) {
		return ORDER.indexOf(power);
	}

	/** {@code power_01_super_strength} -> {@code super_strength}. */
	public static String shortName(Power power) {
		String p = power.key();
		int us = p.indexOf('_', p.indexOf('_') + 1);
		return us > 0 ? p.substring(us + 1) : p;
	}

	/** Maps the catalogue's base-potion id string to a vanilla potion holder. */
	public static Holder<Potion> basePotion(String id) {
		return switch (id) {
			case "minecraft:strength" -> Potions.STRENGTH;
			case "minecraft:night_vision" -> Potions.NIGHT_VISION;
			case "minecraft:slow_falling" -> Potions.SLOW_FALLING;
			case "minecraft:swiftness" -> Potions.SWIFTNESS;
			case "minecraft:weakness" -> Potions.WEAKNESS;
			case "minecraft:fire_resistance" -> Potions.FIRE_RESISTANCE;
			case "minecraft:slowness" -> Potions.SLOWNESS;
			case "minecraft:regeneration" -> Potions.REGENERATION;
			case "minecraft:invisibility" -> Potions.INVISIBILITY;
			case "minecraft:leaping" -> Potions.LEAPING;
			case "minecraft:water_breathing" -> Potions.WATER_BREATHING;
			case "minecraft:awkward" -> Potions.AWKWARD;
			default -> Potions.AWKWARD;
		};
	}
}
