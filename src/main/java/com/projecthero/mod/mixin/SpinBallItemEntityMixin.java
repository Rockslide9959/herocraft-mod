package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.spin.SpinBalls;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 round 2: a dropped Steel Ball ({@link SpinBalls}) -- only its owner picks it up, it never despawns, a stale copy vanishes,
 * and where it lies is remembered for the recall.
 */
@Mixin(ItemEntity.class)
public abstract class SpinBallItemEntityMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$spinBallTick(CallbackInfo ci) {
		if (SpinBalls.onItemEntityTick((ItemEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void projecthero$spinBallOwnerOnly(Player player, CallbackInfo ci) {
		if (!SpinBalls.mayPickUp((ItemEntity) (Object) this, player)) {
			ci.cancel();
		}
	}
}
