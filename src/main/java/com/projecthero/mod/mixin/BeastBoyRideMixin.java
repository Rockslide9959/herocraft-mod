package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.beastboy.BeastBoyRiding;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

/** v0.17.3 Beast Boy: riders on the whale's back (four in a row) and astride the pterodactyl. Both sides (the seat must match). */
@Mixin(Entity.class)
public abstract class BeastBoyRideMixin {
	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void projecthero$beastSeat(Entity passenger, EntityDimensions dims, float scale, CallbackInfoReturnable<Vec3> cir) {
		Vec3 seat = BeastBoyRiding.seat((Entity) (Object) this, passenger);
		if (seat != null) {
			cir.setReturnValue(seat);
		}
	}
}
