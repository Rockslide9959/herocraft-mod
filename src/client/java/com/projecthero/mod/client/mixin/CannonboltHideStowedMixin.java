package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltStow;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.2 round 3 (user: "just make the squadmates disappear so you cant see them"): a mate inside Cannonbolt's ball is not drawn at
 * all -- no body, armour, held item, nametag or shadow -- by anyone.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class CannonboltHideStowedMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void projecthero$hideStowed(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (CannonboltStow.stowed(entity)) {
			cir.setReturnValue(false);
		}
	}
}
