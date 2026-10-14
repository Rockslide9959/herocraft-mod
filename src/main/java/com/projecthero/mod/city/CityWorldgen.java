package com.projecthero.mod.city;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/**
 * v0.16.2 City part A: the Java half of the City worldgen. The {@code projecthero:city} structure type
 * ({@link CityStructure}), the structure tags, and the cartographer's City Map trade. The structure, structure set,
 * template pools and NBT templates are all data under {@code data/projecthero/} (see {@code docs/CITY_REFERENCE.md}).
 */
public final class CityWorldgen {
	public static final StructureType<CityStructure> CITY_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			ResourceKey.create(Registries.STRUCTURE_TYPE, ProjectHeroMod.id("city")), () -> CityStructure.CODEC);

	/** {@code #projecthero:city}: every City structure (for {@code /locate structure #projecthero:city}). */
	public static final TagKey<Structure> CITY_TAG = TagKey.create(Registries.STRUCTURE, ProjectHeroMod.id("city"));
	/** {@code #projecthero:on_city_maps}: what a City Map points at (an explorer-map destination tag, like vanilla's). */
	public static final TagKey<Structure> ON_CITY_MAPS = TagKey.create(Registries.STRUCTURE, ProjectHeroMod.id("on_city_maps"));

	/** The City Map's item-name translation key (vanilla's explorer maps use the same {@code filled_map.*} scheme). */
	public static final String CITY_MAP_NAME = "filled_map.projecthero.city";
	/** Cartographer level (3 = Journeyman, the same tier as vanilla's woodland explorer map). */
	public static final int CITY_MAP_LEVEL = 3;
	public static final int CITY_MAP_EMERALDS = 16;

	private CityWorldgen() {
	}

	/** The City Map trade: emeralds + a compass for a map to the nearest City, marked like a village. */
	public static VillagerTrades.ItemListing cityMapTrade() {
		return new VillagerTrades.TreasureMapForEmeralds(CITY_MAP_EMERALDS, ON_CITY_MAPS, CITY_MAP_NAME, MapDecorationTypes.PLAINS_VILLAGE, 12, 10);
	}

	public static void initialize() {
		// cartographers everywhere (including the ones in a City's office tower and station) sell City Maps
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, CITY_MAP_LEVEL, factories -> factories.add(cityMapTrade()));
	}
}
