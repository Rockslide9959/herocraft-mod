package com.projecthero.mod.loot;

import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;

import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5 loot framework: the five loot rarities. An item is "of" a rarity when its stack carries the
 * {@link #COMPONENT} ({@code projecthero:hero_rarity}); its name is then drawn in the rarity's colour everywhere
 * (see {@code ItemStackHeroRarityMixin}) and the client adds a "&lt;Rarity&gt;" line under the name.
 *
 * <p>Give an item class a default rarity with {@link #props(HeroRarity)} (or
 * {@code new Item.Properties().component(HeroRarity.COMPONENT, HeroRarity.RARE)}); loot tables can stamp it on any
 * stack with {@code minecraft:set_components} ({@code "projecthero:hero_rarity": "epic"}).
 */
public enum HeroRarity implements StringRepresentable {
	COMMON(0, "common", ChatFormatting.WHITE),
	UNCOMMON(1, "uncommon", ChatFormatting.GREEN),
	RARE(2, "rare", ChatFormatting.AQUA),
	EPIC(3, "epic", ChatFormatting.LIGHT_PURPLE),
	LEGENDARY(4, "legendary", ChatFormatting.GOLD);

	public static final Codec<HeroRarity> CODEC = StringRepresentable.fromEnum(HeroRarity::values);
	private static final IntFunction<HeroRarity> BY_ID = ByIdMap.continuous(r -> r.id, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
	public static final StreamCodec<ByteBuf, HeroRarity> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, r -> r.id);

	/** {@code projecthero:hero_rarity} -- persistent and synced, so names colour on the client too. */
	public static final DataComponentType<HeroRarity> COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("hero_rarity"),
			DataComponentType.<HeroRarity>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build());

	private final int id;
	private final String name;
	private final ChatFormatting color;

	HeroRarity(int id, String name, ChatFormatting color) {
		this.id = id;
		this.name = name;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public ChatFormatting color() {
		return color;
	}

	/** {@code projecthero.rarity.<name>} -- "Common", "Uncommon", ... */
	public String translationKey() {
		return "projecthero.rarity." + name;
	}

	/** The coloured "Rare" label (the tooltip line). */
	public MutableComponent label() {
		return Component.translatable(translationKey()).withStyle(color);
	}

	/** The stack's rarity, or {@code null} when it has none (plain vanilla items). */
	@Nullable
	public static HeroRarity of(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(COMPONENT);
	}

	/** The stack's rarity, falling back to {@link #COMMON}. */
	public static HeroRarity orCommon(ItemStack stack) {
		HeroRarity r = of(stack);
		return r == null ? COMMON : r;
	}

	/** {@code Item.Properties} that give every stack of the item this rarity by default. */
	public static Item.Properties props(HeroRarity rarity) {
		return new Item.Properties().component(COMPONENT, rarity);
	}

	/** Same, on top of existing properties. */
	public static Item.Properties with(Item.Properties props, HeroRarity rarity) {
		return props.component(COMPONENT, rarity);
	}

	/** Called from {@link LootMod#initialize()} so the component is registered before any item is built. */
	static void bootstrap() {
	}
}
