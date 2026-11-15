package com.projecthero.mod.sentry.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.5 Sentry's blocks: the Void's void matter (temporary), the burnt shadow it leaves of what it erases (fades), and the sealed
 * O.X.E. serum vat behind Warden Kross's arena in The Vault (the Sentry Serum's only source).
 */
public final class SentryBlocks {
	public static VoidMatterBlock VOID_MATTER;
	public static BurntShadowBlock BURNT_SHADOW;
	public static SerumVatBlock SERUM_VAT;
	public static BlockEntityType<SerumVatBlockEntity> SERUM_VAT_BE;
	public static BlockItem SERUM_VAT_ITEM;

	private SentryBlocks() {
	}

	public static void initialize() {
		VOID_MATTER = block("void_matter", new VoidMatterBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).sound(SoundType.SCULK).lightLevel(s -> 3)
				.pushReaction(PushReaction.BLOCK).noLootTable()));
		BURNT_SHADOW = block("burnt_shadow", new BurntShadowBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLACK).instabreak().noCollission().noOcclusion().sound(SoundType.SAND)
				.pushReaction(PushReaction.DESTROY).noLootTable().replaceable()));
		SERUM_VAT = block("oxe_serum_vat", new SerumVatBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.GOLD).strength(-1.0f, 3600000.0f).sound(SoundType.GLASS).lightLevel(s -> 9)
				.noOcclusion().pushReaction(PushReaction.BLOCK).noLootTable()));
		SERUM_VAT_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("oxe_serum_vat"),
				BlockEntityType.Builder.of(SerumVatBlockEntity::new, SERUM_VAT).build(null));
		SERUM_VAT_ITEM = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("oxe_serum_vat")),
				new BlockItem(SERUM_VAT, new Item.Properties()));
	}

	private static <B extends Block> B block(String path, B block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}
}
