package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.2 round 4: a player in Alien X's hall stands inside his frozen body and is not drawn at all -- no body, held item, nametag or
 * shadow -- by anyone, himself included: every client sees only the body. Read off the synced Omnitrix state.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class AlienXHallHideMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void projecthero$hideInHall(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (AlienXKit.inHall(entity)) {
			cir.setReturnValue(false);
		}
	}
}
