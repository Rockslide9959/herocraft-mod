package com.projecthero.mod.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.hero.PowerItems;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.14.8: loot never hands out a disabled experimental power's reagent, serum or research note
 * ({@link PowerItems#isDisabledPowerItem}). Every loot path (chests, mobs, archaeology, the lot) funnels through
 * {@code getRandomItemsRaw(LootContext, Consumer)}, so wrapping its output consumer covers them all while the loot
 * table JSON stays untouched -- re-enabling a power brings its loot straight back.
 */
@Mixin(LootTable.class)
public abstract class LootTableDisabledPowerMixin {
	@ModifyVariable(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true)
	private Consumer<ItemStack> projecthero$dropDisabledPowerItems(Consumer<ItemStack> output) {
		return stack -> {
			if (!PowerItems.isDisabledPowerItem(stack)) {
				output.accept(stack);
			}
		};
	}
}
