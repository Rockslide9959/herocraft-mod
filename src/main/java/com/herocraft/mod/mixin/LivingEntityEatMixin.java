package com.herocraft.mod.mixin;

import com.herocraft.mod.grave.GraveboundEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * How eating an Enchanted Golden Apple breaks the Gravebound Curse.
 *
 * <p>{@code LivingEntity#eat} is the point at which a consumable has actually been finished --
 * not started, not interrupted -- so hooking it here means a cancelled eat (moving off the item,
 * being interrupted, opening a screen) never spends the curse. Injected at {@code HEAD}, before the
 * stack is shrunk, so the item being checked is still the one the player consumed.
 *
 * <p>Note that this is the <em>only</em> consumption hook the curse has. Milk is deliberately not
 * handled: the curse is attachment data rather than a status effect, so
 * {@code LivingEntity#removeAllEffects} -- which is what milk calls -- cannot touch it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEatMixin {
	@Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD"))
	private void herocraft$breakCurseOnGoldenApple(Level level, ItemStack stack, FoodProperties food,
			CallbackInfoReturnable<ItemStack> callback) {
		if (!level.isClientSide() && (Object) this instanceof ServerPlayer player) {
			GraveboundEvents.onFinishedEating(player, stack);
		}
	}
}
