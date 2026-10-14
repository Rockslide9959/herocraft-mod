package com.projecthero.mod.mixin;

import com.projecthero.mod.city.CityDamageTracker;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.16.2 City part B: marks a player's block placement, so the City damage tracker can tell a renovation (a player's
 * own block in a damaged slot -- never reverted) from anything else.
 */
@Mixin(BlockItem.class)
public abstract class CityBlockItemMixin {
	@Inject(method = "place", at = @At("HEAD"))
	private void projecthero$cityPlaceStart(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() != null && !context.getLevel().isClientSide()) {
			CityDamageTracker.playerPlacing(true);
		}
	}

	@Inject(method = "place", at = @At("RETURN"))
	private void projecthero$cityPlaceEnd(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() != null && !context.getLevel().isClientSide()) {
			CityDamageTracker.playerPlacing(false);
		}
	}
}
