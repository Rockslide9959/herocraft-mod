package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkRiding;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** v0.13.14: a squad-mate riding the Hulk sits on his back, not on top of his head. Runs on both sides (the seat must match). */
@Mixin(Entity.class)
public abstract class EntityHulkRideMixin {
	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void projecthero$hulkBackSeat(Entity passenger, EntityDimensions dims, float scale, CallbackInfoReturnable<Vec3> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof Player player && Hulk.isHulk(player)) {
			cir.setReturnValue(HulkRiding.seat(self, dims));
		}
	}
}
