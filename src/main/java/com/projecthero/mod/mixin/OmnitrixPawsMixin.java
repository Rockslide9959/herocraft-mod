package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.1 (Wildmutt rework 2, user: "make it so that the player cant use tools"): an alien with paws
 * ({@link AlienKit#pawsOnly}) mines like a bare hand whatever the hotbar holds -- the held tool's speed and its harvest
 * level are ignored. Both sides (the client predicts the break progress).
 */
@Mixin(Player.class)
public abstract class OmnitrixPawsMixin {
	@WrapOperation(method = "getDestroySpeed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
	private float projecthero$pawsMineBare(Inventory inv, BlockState state, Operation<Float> original) {
		return AlienKit.pawsOnlyNow((Player) (Object) this) ? 1.0f : original.call(inv, state);
	}

	@WrapOperation(method = "hasCorrectToolForDrops", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;isCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean projecthero$pawsHarvestBare(ItemStack stack, BlockState state, Operation<Boolean> original) {
		return !AlienKit.pawsOnlyNow((Player) (Object) this) && original.call(stack, state);
	}
}
