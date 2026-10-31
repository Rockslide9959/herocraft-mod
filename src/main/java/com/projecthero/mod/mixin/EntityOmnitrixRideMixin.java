package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.OmnitrixRiding;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: the seats of {@link OmnitrixRiding} -- astride Wildmutt's back, Grey Matter on a player's shoulder -- the rider turned
 * with the one carrying them, and the Wildmutt rider's look kept within reach of his facing. Both sides (the seat must match).
 */
@Mixin(Entity.class)
public abstract class EntityOmnitrixRideMixin {
	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void projecthero$omnitrixSeat(Entity passenger, EntityDimensions dims, float scale, CallbackInfoReturnable<Vec3> cir) {
		Vec3 seat = OmnitrixRiding.seat((Entity) (Object) this, passenger, dims);
		if (seat != null) {
			cir.setReturnValue(seat);
		}
	}

	@Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("TAIL"))
	private void projecthero$omnitrixCarryRider(Entity passenger, Entity.MoveFunction move, CallbackInfo ci) {
		if ((Object) this instanceof Player host) {
			OmnitrixRiding.carryRider(host, passenger);
		}
	}

	@Inject(method = "onPassengerTurned", at = @At("HEAD"))
	private void projecthero$omnitrixClampRiderLook(Entity passenger, CallbackInfo ci) {
		if ((Object) this instanceof Player host) {
			OmnitrixRiding.clampLook(host, passenger);
		}
	}
}
