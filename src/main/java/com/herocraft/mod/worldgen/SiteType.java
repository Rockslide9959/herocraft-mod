package com.herocraft.mod.worldgen;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.hero.device.ModDevices;

import com.mojang.serialization.Codec;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

/** The six rare above-ground research structures (spec section 6). */
public enum SiteType implements StringRepresentable {
	RESEARCH_FACILITY("research_facility", Blocks.LIGHT_GRAY_CONCRETE, Blocks.IRON_BARS, "experimental_light_projector"),
	METEOR_IMPACT("meteor_impact", Blocks.BASALT, Blocks.MAGMA_BLOCK, null),
	POWER_STATION("power_station", Blocks.CUT_COPPER, Blocks.COPPER_BLOCK, "overloaded_redstone_coil"),
	GEOLOGICAL_SITE("geological_site", Blocks.COBBLED_DEEPSLATE, Blocks.CALCITE, "geological_resonance_chamber"),
	GOVERNMENT_SITE("government_site", Blocks.GRAY_CONCRETE, Blocks.CHISELED_STONE_BRICKS, "molecular_compression_chamber"),
	HYDROSTATIC_FACILITY("hydrostatic_facility", Blocks.CYAN_TERRACOTTA, Blocks.PRISMARINE_BRICKS, "hydrostatic_test_tank");

	public static final Codec<SiteType> CODEC = StringRepresentable.fromEnum(SiteType::values);

	private final String id;
	private final Block primary;
	private final Block accent;
	private final String deviceId;

	SiteType(String id, Block primary, Block accent, String deviceId) {
		this.id = id;
		this.primary = primary;
		this.accent = accent;
		this.deviceId = deviceId;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public Block primary() {
		return primary;
	}

	public Block accent() {
		return accent;
	}

	/** The lab-device block this site houses, or {@code null} (meteor sites have no device). */
	public Block device() {
		return deviceId == null ? null : ModDevices.block(deviceId);
	}

	public ResourceKey<LootTable> lootTable() {
		return ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
				ResourceLocation.fromNamespaceAndPath(HeroCraftMod.MOD_ID, "chests/" + id));
	}
}
