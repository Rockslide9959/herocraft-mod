package com.projecthero.mod.targetdummy;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModCreativeTab;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/**
 * v0.17.5 (user: "my own target dummy that I can use for testing"): registration for the Target Dummy -- the entity, its
 * item (Superheroes tab, crafted from wool, hay, sticks and a fence), the hit payload and the melee-swing hook that lets
 * the dummy tell a sword swing (and a crit) from a power. See {@link TargetDummyEntity} and
 * {@code docs/TARGET_DUMMY_REFERENCE.md}.
 */
public final class TargetDummyMod {
	public static final EntityType<TargetDummyEntity> TARGET_DUMMY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("target_dummy"), EntityType.Builder.<TargetDummyEntity>of(TargetDummyEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.95f).eyeHeight(1.6f).clientTrackingRange(10).build("target_dummy"));
	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("target_dummy"),
			new TargetDummyItem(new Item.Properties().stacksTo(16)));

	private TargetDummyMod() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(TARGET_DUMMY, TargetDummyEntity.createAttributes());
		PayloadTypeRegistry.playS2C().register(DummyHitPayload.TYPE, DummyHitPayload.CODEC);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && entity instanceof TargetDummyEntity dummy && !player.isSpectator()) {
				dummy.markMelee(player);
			}
			return InteractionResult.PASS;
		});
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> entries.accept(ITEM));
	}
}
