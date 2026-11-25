package com.projecthero.mod.thoracquire;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModCreativeTab;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.thoracquire.entity.DestroyerBoss;
import com.projecthero.mod.thoracquire.forge.StarForgeBlock;
import com.projecthero.mod.thoracquire.forge.StarForgeBlockEntity;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * v0.17.7 <b>how players get Thor and Stormbreaker</b> -- registration and wiring for: {@link Valor} (the worthiness meter),
 * Mjolnir's Crater ({@code worldgen/MjolnirCraterPiece}, {@link CraterGuard}), the {@link TrialOfAsgard} and its guardian
 * {@link DestroyerBoss} (the Destroyer), the lift cutscene ({@link MjolnirLift}), the Star Forge
 * ({@link com.projecthero.mod.thoracquire.forge.StarForge}) and the {@link LivingBranch}. See {@code docs/THOR_REFERENCE.md}
 * "v0.17.7: how players get Thor and Stormbreaker".
 */
public final class ThorAcquire {
	public static final StarForgeBlock STAR_FORGE = Registry.register(BuiltInRegistries.BLOCK,
			ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("star_forge")),
			new StarForgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops()
					.strength(8.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)
					.lightLevel(s -> s.getValue(StarForgeBlock.HEAT) == 2 ? 13 : s.getValue(StarForgeBlock.HEAT) == 1 ? 6 : 2)));
	public static final BlockItem STAR_FORGE_ITEM = Registry.register(BuiltInRegistries.ITEM,
			ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("star_forge")),
			new BlockItem(STAR_FORGE, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
	public static final BlockEntityType<StarForgeBlockEntity> STAR_FORGE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ProjectHeroMod.id("star_forge"), FabricBlockEntityTypeBuilder.create(StarForgeBlockEntity::new, STAR_FORGE).build());

	public static final Item LIVING_BRANCH = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("living_branch"),
			new LivingBranch.BranchItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16)));

	/**
	 * The Trial of Asgard's guardian. v0.17.7 (user): the original "Stormwarden" became the Destroyer and its id went from
	 * {@code stormwarden} to {@code destroyer} (never released, but test worlds may hold one): {@code LegacyItemIds} points the
	 * old entity and spawn egg ids at these, so a saved one loads as the Destroyer. Its Storm Spirit summon is gone.
	 */
	public static final EntityType<DestroyerBoss> DESTROYER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("destroyer")),
			EntityType.Builder.<DestroyerBoss>of(DestroyerBoss::new, MobCategory.MONSTER).sized(1.4f, 3.6f).eyeHeight(3.2f)
					.fireImmune().clientTrackingRange(12).build("destroyer"));
	public static final Item DESTROYER_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("destroyer_spawn_egg"),
			new SpawnEggItem(DESTROYER, 0x9A9EA6, 0xFF7A1E, new Item.Properties()));

	private ThorAcquire() {
	}

	public static void initialize() {
		ThorAcquireAttachments.initialize();
		FabricDefaultAttributeRegistry.register(DESTROYER, DestroyerBoss.createAttributes());
		PayloadTypeRegistry.playS2C().register(CraterCloudPayload.TYPE, CraterCloudPayload.CODEC);
		Valor.initialize();
		Storms.initialize();
		CraterGuard.initialize();
		MjolnirLift.initialize();
		TrialOfAsgard.initialize();
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> {
			entries.addAfter(ModItems.UNFORGED_STORMBREAKER, STAR_FORGE_ITEM, LIVING_BRANCH);
			entries.accept(DESTROYER_SPAWN_EGG);
		});
		// a small chance of a Living Branch in jungle temple chests (1 roll, 1 in 12)
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (source.isBuiltin() && key.equals(BuiltInLootTables.JUNGLE_TEMPLE)) {
				builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
						.add(LootItem.lootTableItem(LIVING_BRANCH).setWeight(1))
						.add(EmptyLootItem.emptyItem().setWeight(11)));
			}
		});
	}

	/** Static state to forget when the server stops ({@code ServerStateReset}). */
	public static void clearSessionState() {
		MjolnirLift.clearSessionState();
		TrialOfAsgard.clearSessionState();
		Storms.forced = null;
		LivingBranch.chanceOverride = null;
	}
}
