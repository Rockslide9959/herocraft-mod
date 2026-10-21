package com.projecthero.mod.ironman.crate;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.16.7 (user) ERA I: the <b>Suit Crate block</b> registry. The block is registered from {@code IronManItems} (its item,
 * the Suit Crate, is a {@code BlockItem} of it), so this class is touched -- and the block registered -- the first time
 * {@code IronManItems} initialises; nothing in here refers back to {@code IronManItems}.
 */
public final class SuitCrateBlocks {
	/** A heavy metal Stark crate: iron-block hardness, mined fastest with a pickaxe but drops with anything (never loses the suit). */
	public static final Block SUIT_CRATE = Registry.register(BuiltInRegistries.BLOCK,
			ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("suit_crate")),
			new SuitCrateBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL).strength(5.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK)
					.lightLevel(s -> 4) // the arc-reactor light on the front
					.pushReaction(PushReaction.BLOCK)
					.noOcclusion()));

	public static final BlockEntityType<SuitCrateBlockEntity> SUIT_CRATE_BE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("suit_crate"),
			BlockEntityType.Builder.of(SuitCrateBlockEntity::new, SUIT_CRATE).build(null));

	private SuitCrateBlocks() {
	}

	/**
	 * Hooks: Shift+right-click with an item in hand never reaches a block in vanilla (the item is used instead), so the
	 * crate's sneak-use -- the 30 s suit-up, or an ingot repair -- is caught here first.
	 */
	public static void initialize() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (player.isSpectator() || !player.isSecondaryUseActive()
					|| player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
				return InteractionResult.PASS; // the block's own use handles everything else
			}
			if (!(world.getBlockState(hit.getBlockPos()).getBlock() instanceof SuitCrateBlock)) {
				return InteractionResult.PASS;
			}
			return SuitCrateBlock.interact(world, hit.getBlockPos(), player, player.getItemInHand(hand));
		});
	}
}
