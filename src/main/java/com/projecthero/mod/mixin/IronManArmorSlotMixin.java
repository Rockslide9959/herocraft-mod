package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.world.item.ItemStack;

/**
 * v0.14.30, explicit user request: Iron Man armour can't be put on by hand -- not by dragging it into an armour slot,
 * shift-clicking it there or hot-key swapping it in. It goes on only through C (suit up / auto-equip) or a suit deploy
 * (platform, call, pod, suitcase), which set the equipment slot directly and never pass through an {@code ArmorSlot}.
 * v0.14.31, explicit user request: and it can't be taken off by hand either (no picking it out of the slot, shift-clicking
 * it out or hot-key swapping it away) -- it comes off with C (suit down), a platform retrieve or a send-home. Creative
 * players (instabuild) can still move it.
 */
@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class IronManArmorSlotMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void projecthero$noManualIronManArmor(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() instanceof IronManArmorItem) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void projecthero$noManualIronManArmorRemoval(net.minecraft.world.entity.player.Player player,
			CallbackInfoReturnable<Boolean> cir) {
		if (((net.minecraft.world.inventory.Slot) (Object) this).getItem().getItem() instanceof IronManArmorItem
				&& !player.getAbilities().instabuild) {
			cir.setReturnValue(false);
		}
	}
}
