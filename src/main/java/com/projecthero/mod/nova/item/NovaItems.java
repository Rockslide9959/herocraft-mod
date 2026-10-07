package com.projecthero.mod.nova.item;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.nova.entity.NovaCenturionEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

/**
 * v0.15.13: Nova's things -- the Nova Corps Helmet (grants the power), the dying Centurion who hands it over, and his
 * spawn egg (creative only; in survival he is found in a Crashed Nova Corps Pod).
 */
public final class NovaItems {
	public static Item NOVA_CORPS_HELMET;
	public static Item CENTURION_SPAWN_EGG;
	public static EntityType<NovaCenturionEntity> CENTURION;

	private NovaItems() {
	}

	public static void initialize() {
		CENTURION = Registry.register(BuiltInRegistries.ENTITY_TYPE,
				ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("nova_centurion")),
				EntityType.Builder.<NovaCenturionEntity>of(NovaCenturionEntity::new, MobCategory.MISC)
						.sized(0.6f, 1.3f)
						.eyeHeight(1.1f)
						.clientTrackingRange(10)
						.fireImmune()
						.build("nova_centurion"));
		FabricDefaultAttributeRegistry.register(CENTURION, NovaCenturionEntity.createAttributes());
		NOVA_CORPS_HELMET = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("nova_corps_helmet"),
				new NovaCorpsHelmetItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		CENTURION_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("nova_centurion_spawn_egg"),
				new SpawnEggItem(CENTURION, 0x12313D, 0xFFC83C, new Item.Properties()));
	}

	/** Appended to the {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(NOVA_CORPS_HELMET);
		output.accept(CENTURION_SPAWN_EGG);
	}
}
