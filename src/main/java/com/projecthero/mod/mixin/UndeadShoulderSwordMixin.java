package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.projecthero.mod.undead.UndeadShoulderSword;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3 Undead's shoulder sword: with a sword stuck in his shoulder and nothing in his hand, the weapon a bare-handed hit uses is
 * that sword ({@link UndeadShoulderSword#weapon}) -- so its enchantments add their damage, knockback and after-hit effects (fire
 * aspect...) and the hit wears it, exactly as if it were held. If that hit wears it out, vanilla would clear "the other hand"
 * (it was not the main hand's stack): that is skipped, so the off hand keeps what it holds.
 */
@Mixin(Player.class)
public abstract class UndeadShoulderSwordMixin {
	@Inject(method = "getWeaponItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$shoulderSwordWeapon(CallbackInfoReturnable<ItemStack> cir) {
		Player self = (Player) (Object) this;
		if (self.isAutoSpinAttack()) {
			return;
		}
		ItemStack s = UndeadShoulderSword.weapon(self);
		if (s != null) {
			cir.setReturnValue(s);
		}
	}

	@WrapWithCondition(method = "attack", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"))
	private boolean projecthero$keepOffHandWhenShoulderSwordBreaks(Player self, InteractionHand hand, ItemStack stack) {
		return !(hand == InteractionHand.OFF_HAND && UndeadShoulderSword.justBroke(self));
	}
}
