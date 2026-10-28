package com.projecthero.mod.omnitrix.pod;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** v0.17.1: the Omnitrix Containment Pod (a two-block-tall machine) and its block entity. */
public final class OmnitrixPodBlocks {
	public static OmnitrixPodBlock POD;
	public static Item POD_ITEM;
	public static BlockEntityType<OmnitrixPodBlockEntity> POD_BE;

	private OmnitrixPodBlocks() {
	}

	public static void initialize() {
		POD = Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("omnitrix_containment_pod")),
				new OmnitrixPodBlock(BlockBehaviour.Properties.of()
						.mapColor(MapColor.METAL)
						.strength(8.0f, 1200.0f)
						.requiresCorrectToolForDrops()
						.sound(SoundType.NETHERITE_BLOCK)
						.noOcclusion()
						.lightLevel(s -> 7)
						.pushReaction(PushReaction.BLOCK)));
		POD_ITEM = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("omnitrix_containment_pod")),
				new BlockItem(POD, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()) {
					@Override
					public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext ctx,
							java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
						tooltip.add(net.minecraft.network.chat.Component.translatable("block.projecthero.omnitrix_containment_pod.hint")
								.withStyle(net.minecraft.ChatFormatting.GRAY));
						tooltip.add(net.minecraft.network.chat.Component.translatable("block.projecthero.omnitrix_containment_pod.warning")
								.withStyle(net.minecraft.ChatFormatting.RED));
					}
				});
		POD_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("omnitrix_containment_pod"),
				BlockEntityType.Builder.of(OmnitrixPodBlockEntity::new, POD).build(null));
	}
}
