package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.loot.HeroRarity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5 loot framework: a stack with a {@code projecthero:hero_rarity} component shows its name in the rarity's
 * colour -- tooltips, the held-item popup, item frames, chat. The colour sits on the name component itself, so it wins
 * over the vanilla {@code Rarity} colour that callers wrap around it.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackHeroRarityMixin {
	@Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
	private void projecthero$heroRarityColour(CallbackInfoReturnable<Component> cir) {
		HeroRarity rarity = HeroRarity.of((ItemStack) (Object) this);
		if (rarity != null) {
			cir.setReturnValue(cir.getReturnValue().copy().withStyle(rarity.color()));
		}
	}
}
