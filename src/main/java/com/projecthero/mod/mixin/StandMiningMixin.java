package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.stand.Stands;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.3 (user: "if player tries to mine a block with an empty hand while star platinum is active star platinum helps and breaks
 * the block, star platinum can break blocks as if their hands were iron tools, the blocks must also dropped when broken"): with the
 * Stand out and the main hand empty ({@link Stands#standMines}) the bare hand mines at the best of an iron pickaxe, axe and shovel,
 * and harvests whatever any of them could -- so the normal drops fall. Unbreakable blocks stay unbreakable (their hardness is -1,
 * whatever the tool), and the break is the player's own, so claims and spawn protection still apply. Both sides (the client predicts
 * the break progress); the Stand's punches are drawn from the swing ({@code StandSwing}).
 */
@Mixin(Player.class)
public abstract class StandMiningMixin {
	@Unique
	private static ItemStack[] projecthero$ironTools;

	@Unique
	private static ItemStack[] projecthero$tools() {
		if (projecthero$ironTools == null) {
			projecthero$ironTools = new ItemStack[] { new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SHOVEL) };
		}
		return projecthero$ironTools;
	}

	@WrapOperation(method = "getDestroySpeed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
	private float projecthero$standMinesLikeIron(Inventory inv, BlockState state, Operation<Float> original) {
		float base = original.call(inv, state);
		if (!Stands.standMines((Player) (Object) this)) {
			return base;
		}
		float best = base;
		for (ItemStack tool : projecthero$tools()) {
			best = Math.max(best, tool.getDestroySpeed(state));
		}
		return best;
	}

	@WrapOperation(method = "hasCorrectToolForDrops", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;isCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean projecthero$standHarvestsLikeIron(ItemStack stack, BlockState state, Operation<Boolean> original) {
		if (original.call(stack, state)) {
			return true;
		}
		if (!Stands.standMines((Player) (Object) this)) {
			return false;
		}
		for (ItemStack tool : projecthero$tools()) {
			if (tool.isCorrectToolForDrops(state)) {
				return true;
			}
		}
		return false;
	}
}
