package com.projecthero.mod.wardrobe;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModCreativeTab;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.16.5 (user): the Mannequin (a player-shaped armour stand) and the Wardrobe (a 3-wide, 2-tall wardrobe holding ten
 * suits). Registration only; see {@link MannequinEntity} and {@link WardrobeBlock}. Both craftable and in the Superheroes
 * tab.
 */
public final class WardrobeMod {
	public static final EntityType<MannequinEntity> MANNEQUIN = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("mannequin"), EntityType.Builder.<MannequinEntity>of(MannequinEntity::new, MobCategory.MISC)
					.sized(0.5f, 1.975f).eyeHeight(1.7775f).clientTrackingRange(10).build("mannequin"));
	public static final Item MANNEQUIN_ITEM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("mannequin"),
			new MannequinItem(new Item.Properties().stacksTo(16)));

	public static final WardrobeBlock WARDROBE = Registry.register(BuiltInRegistries.BLOCK, ProjectHeroMod.id("wardrobe"),
			new WardrobeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5f).sound(SoundType.WOOD)
					.ignitedByLava().noOcclusion().pushReaction(PushReaction.BLOCK)));
	public static final BlockEntityType<WardrobeBlockEntity> WARDROBE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ProjectHeroMod.id("wardrobe"), BlockEntityType.Builder.of(WardrobeBlockEntity::new, WARDROBE).build(null));
	public static final Item WARDROBE_ITEM = registerWardrobeItem();

	private WardrobeMod() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(MANNEQUIN, ArmorStand.createAttributes());
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(WardrobeBlock::sneakUse);
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> {
			entries.accept(MANNEQUIN_ITEM);
			entries.accept(WARDROBE_ITEM);
		});
	}

	private static Item registerWardrobeItem() {
		BlockItem item = new BlockItem(WARDROBE, new Item.Properties()) {
			@Override
			public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
				tooltip.add(Component.translatable("block.projecthero.wardrobe.desc").withStyle(ChatFormatting.GRAY));
			}
		};
		Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("wardrobe"), item);
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}
}
