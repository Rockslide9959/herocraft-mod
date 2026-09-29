package com.projecthero.mod.hero.revamp.d;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.22 revamp, batch D: the entities and the one item the six batch-D powers add -- Mirror Images (Light, V),
 * Shadow Servants (Shadow, N) and the Hard-Light Blade (Light, H) -- plus the world rules that keep conjured
 * things from ever becoming real loot.
 */
public final class BatchDContent {
	public static final EntityType<MirrorImageEntity> MIRROR_IMAGE = register("mirror_image",
			EntityType.Builder.<MirrorImageEntity>of(MirrorImageEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.8f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.noSave()
					.build("mirror_image"));

	public static final EntityType<ShadowServantEntity> SHADOW_SERVANT = register("shadow_servant",
			EntityType.Builder.<ShadowServantEntity>of(ShadowServantEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.9f)
					.eyeHeight(1.7f)
					.clientTrackingRange(10)
					.noSave()
					.build("shadow_servant"));

	public static HardLightBladeItem HARD_LIGHT_BLADE;

	private BatchDContent() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(MIRROR_IMAGE, MirrorImageEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SHADOW_SERVANT, ShadowServantEntity.createAttributes());
		HARD_LIGHT_BLADE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("hard_light_blade"),
				new HardLightBladeItem());

		// A conjured blade or a decoy's illusion gear never exists as an item in the world.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && (HardLightBladeItem.isBlade(item.getItem())
					|| MirrorImageEntity.isIllusion(item.getItem()))) {
				item.discard();
			}
		});
		// Dying takes the blade with it before vanilla drops (or keepInventory keeps) the inventory.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Player p) {
				HardLightBladeItem.purge(p);
			}
			return true;
		});
		// No hanging the blade in an item frame, dressing an armour stand or handing it to an allay.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (HardLightBladeItem.isBlade(player.getItemInHand(hand))
					&& (entity instanceof net.minecraft.world.entity.decoration.ItemFrame
							|| entity instanceof net.minecraft.world.entity.decoration.ArmorStand
							|| entity instanceof net.minecraft.world.entity.animal.allay.Allay)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}
}
