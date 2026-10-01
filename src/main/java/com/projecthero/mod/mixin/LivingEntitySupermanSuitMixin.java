package com.projecthero.mod.mixin;

import com.projecthero.mod.kryptonian.SupermanSuit;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.14.9: only a Kryptonian can wear the Superman Suit. For any other player the "equipment slot for this item" of a
 * suit piece is the main hand, so the inventory's armour slots refuse it ({@code ArmorSlot.mayPlace} compares against
 * this), shift-click and the creative screen leave it in the inventory, and nothing routes it to an armour slot. Runs
 * on both sides (the Kryptonian power is synced) so the client never predicts an equip the server refuses. See
 * {@link SupermanSuit}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySupermanSuitMixin {
	@Inject(method = "getEquipmentSlotForItem(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/EquipmentSlot;",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$supermanSuitKryptoniansOnly(ItemStack stack, CallbackInfoReturnable<EquipmentSlot> cir) {
		if ((Object) this instanceof Player player && SupermanSuit.isSuit(stack) && !SupermanSuit.mayWear(player)) {
			cir.setReturnValue(EquipmentSlot.MAINHAND);
		}
	}
}
