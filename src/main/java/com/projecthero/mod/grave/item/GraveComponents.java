package com.projecthero.mod.grave.item;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;

/**
 * Data components for the Zombie Raid's items. Separate from Thor's {@code ModDataComponents} and the
 * HeroPack's {@code HeroPackComponents}, matching how the rest of the mod isolates each feature's
 * registrations.
 *
 * <p>All of these are {@code persistent} and network-synchronised, which is what makes the charge
 * counts and cooldowns on these items survive relog, death, and moving between inventories, chests
 * and dimensions -- the item itself carries its state, so there is nothing external to keep in sync
 * and nothing to duplicate.
 */
public final class GraveComponents {
	/**
	 * On a boss trophy head or a Power Fragment: the Experimental Power key of the boss it came from
	 * (e.g. {@code power_05_geokinesis}). This is what makes the item remember its power.
	 */
	public static final DataComponentType<String> POWER_KEY = register("power_key",
			DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build());

	/**
	 * v0.14.4: on a boss trophy head -- who took it and on which day. Copied onto the placed head block's block entity
	 * and back onto the item when it is broken (see the head blocks' loot tables).
	 */
	public static final DataComponentType<TrophyRecord> TROPHY_RECORD = register("trophy_record",
			DataComponentType.<TrophyRecord>builder()
					.persistent(TrophyRecord.CODEC)
					.networkSynchronized(TrophyRecord.STREAM_CODEC)
					.build());

	/**
	 * Undying Totem charges remaining. Stored on the stack rather than anywhere external so it cannot
	 * be reset by relogging, cannot be duplicated by moving the item, and travels with the item when
	 * it is traded or stored.
	 */
	public static final DataComponentType<Integer> TOTEM_CHARGES = register("totem_charges",
			DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Necrotic Blade: current undead-kill stacks, and the game time they all expire at. */
	public static final DataComponentType<Integer> BLADE_STACKS = register("blade_stacks",
			DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	public static final DataComponentType<Long> BLADE_EXPIRES_AT = register("blade_expires_at",
			DataComponentType.<Long>builder()
					.persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG)
					.build());

	/**
	 * v0.14.23: an Oathbound Necrotic Blade -- one upgraded at a smithing table with a Broken Oath (see
	 * {@link OathboundBladeRecipe}). Present = upgraded; it can only be applied once.
	 */
	public static final DataComponentType<net.minecraft.util.Unit> OATHBOUND = register("oathbound",
			DataComponentType.<net.minecraft.util.Unit>builder()
					.persistent(net.minecraft.util.Unit.CODEC)
					.networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE))
					.build());

	private GraveComponents() {
	}

	public static void initialize() {
	}

	private static <T> DataComponentType<T> register(String path, DataComponentType<T> type) {
		ResourceKey<DataComponentType<?>> key = ResourceKey.create(Registries.DATA_COMPONENT_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, type);
	}
}
