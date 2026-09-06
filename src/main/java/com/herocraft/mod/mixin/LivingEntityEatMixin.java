package com.herocraft.mod.mixin;

import com.herocraft.mod.grave.GraveboundEvents;
import com.herocraft.mod.symbiote.SymbioteDiet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two unrelated hooks on {@code LivingEntity#eat} -- the point a consumable is actually finished (not
 * started, not interrupted):
 *
 * <ul>
 *   <li>Breaking the Gravebound Curse when an Enchanted Golden Apple is eaten (injected at
 *       {@code HEAD}, before the stack is shrunk, so the item is still the one that was consumed).</li>
 *   <li>The Symbiote host's iron stomach: an active Symbiote lets its host eat raw meat (and raw
 *       potato / kelp) as though it were cooked -- see {@link SymbioteDiet}. Implemented by swapping
 *       the {@link FoodProperties} argument for the cooked item's own before vanilla applies it, so
 *       nutrition, saturation and any effects all match the cooked version exactly.</li>
 * </ul>
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

	@ModifyVariable(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
			at = @At("HEAD"), argsOnly = true)
	private FoodProperties herocraft$symbioteCooksRawFood(FoodProperties food, Level level, ItemStack stack) {
		if (level.isClientSide() || !((Object) this instanceof ServerPlayer player)) {
			return food;
		}
		return SymbioteDiet.resolve(player, stack, food);
	}
}
