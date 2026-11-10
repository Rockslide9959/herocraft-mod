package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.undead.UndeadCarry;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** v0.17.3: Undead's head carried by another player sits in front of their chest, not on top of their head (both sides). */
@Mixin(Entity.class)
public abstract class EntityUndeadCarryMixin {
	@Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
	private void projecthero$undeadHeadSeat(Entity passenger, EntityDimensions dims, float scale, CallbackInfoReturnable<Vec3> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof Player && UndeadCarry.carriable(passenger)) {
			cir.setReturnValue(UndeadCarry.seat(self));
		}
	}
}
