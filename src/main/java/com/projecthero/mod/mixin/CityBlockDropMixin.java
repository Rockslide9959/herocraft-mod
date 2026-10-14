package com.projecthero.mod.mixin;

import com.projecthero.mod.city.CityRepair;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.16.2 City part B (anti-dupe): a block a Repair Worker rebuilt drops nothing if it is broken again within
 * {@code repair.noDropTicks}. Player breaking and explosions both pop their drops through here.
 */
@Mixin(Block.class)
public abstract class CityBlockDropMixin {
	@Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), cancellable = true)
	private static void projecthero$cityNoDrop(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		if (CityRepair.suppressDrop(level, pos)) {
			ci.cancel();
		}
	}
}
