package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.symbiote.SymbioteBareHands;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Two per-block hooks:
 * <ul>
 *   <li>Super Speed: you mine as fast as you move. {@code getDestroySpeed} is multiplied by the same
 *       factor Super Speed's movement is (×3 in Speed Mode, ×6 in Overdrive).</li>
 *   <li>Symbiote: a bonded host's empty hand mines and harvests like a wooden pickaxe / axe / shovel
 *       ({@link SymbioteBareHands}).</li>
 * </ul>
 * Inert for every other player/state.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void projecthero$speedMining(BlockState state, CallbackInfoReturnable<Float> cir) {
		float factor = SuperSpeedHandlers.speedFactor((Player) (Object) this);
		if (factor > 1.01f) {
			cir.setReturnValue(cir.getReturnValue() * factor);
		}
	}

	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void projecthero$symbioteBareHands(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		if (SymbioteBareHands.applies(self)) {
			float boosted = SymbioteBareHands.miningSpeed(state, cir.getReturnValue());
			if (boosted > cir.getReturnValue()) {
				cir.setReturnValue(boosted);
			}
		}
	}

	@Inject(method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$symbioteHarvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		Player self = (Player) (Object) this;
		if (SymbioteBareHands.applies(self) && SymbioteBareHands.correctToolForDrops(state)) {
			cir.setReturnValue(true);
		}
	}
}
