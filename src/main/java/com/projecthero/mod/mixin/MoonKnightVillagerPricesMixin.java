package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight Phase 5 -- Steven Grant haggles: when a villager works out its prices for a trading player
 * ({@code updateSpecialPrices}, right after the reputation and Hero of the Village discounts), a transformed Steven
 * gets his own discount on top. Vanilla resets special prices when the trade screen closes, so nothing sticks.
 */
@Mixin(Villager.class)
public abstract class MoonKnightVillagerPricesMixin {
	@Inject(method = "updateSpecialPrices", at = @At("TAIL"))
	private void projecthero$stevenHaggles(Player player, CallbackInfo ci) {
		com.projecthero.mod.moonknight.ability.MoonKnightAlters.applyTradeDiscount((Villager) (Object) this, player);
	}
}
