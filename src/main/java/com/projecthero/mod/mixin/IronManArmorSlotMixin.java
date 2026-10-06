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
 * Taking pieces off by hand still works.
 */
@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class IronManArmorSlotMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void projecthero$noManualIronManArmor(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() instanceof IronManArmorItem) {
			cir.setReturnValue(false);
		}
	}
}
