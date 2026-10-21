package com.projecthero.mod.ironman.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Stark technology blueprint. Placed in the Stark Fabricator's blueprint slot to tell the
 * Fabricator which suit/component tier the player is trying to build (spec section 9).
 *
 * <p>Blueprints are consumed neither by inserting them nor by a successful fabrication -- one
 * blueprint unlocks that mark's whole recipe set for as long as it sits in the slot. Later marks'
 * blueprints are themselves fabricated (gated on the previous mark's technology level), which is what
 * enforces the Mark III -> V -> VII -> 42 -> 50 progression.
 */
public class BlueprintItem extends Item {
	/**
	 * v0.16.7 (user): Shift+right-click a Stark Workbench with a blueprint and it goes straight into the blueprint slot
	 * (the one already there comes back to you). Sneaking skips the block's own click handling, so the item does it.
	 */
	@Override
	public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
		net.minecraft.world.entity.player.Player player = ctx.getPlayer();
		if (player == null || !player.isSecondaryUseActive()
				|| !(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof com.projecthero.mod.ironman.fabricator.StarkFabricatorBlock)) {
			return super.useOn(ctx);
		}
		return com.projecthero.mod.ironman.fabricator.StarkFabricatorBlock.insertBlueprint(ctx.getLevel(), ctx.getClickedPos(), player, ctx.getHand());
	}

	private final int techLevel;
	private final String suitId;

	public BlueprintItem(Properties properties, int techLevel) {
		this(properties, techLevel, null);
	}

	public BlueprintItem(Properties properties, int techLevel, String suitId) {
		super(properties.stacksTo(1));
		this.techLevel = techLevel;
		this.suitId = suitId;
	}

	/** Fabricator recipes with {@code requiredTechLevel <= this} are visible while this blueprint is loaded. */
	public int techLevel() {
		return techLevel;
	}

	/**
	 * "changes 18": which suit this blueprint is for, or {@code null} for a non-suit blueprint. Drives
	 * the Fabricator's per-piece (helmet / chestplate / leggings / boots) picker.
	 */
	public String suitId() {
		return suitId;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.ironman.blueprint.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
