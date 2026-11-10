package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.undead.UndeadMotion;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Undead's whole-body motion ({@link UndeadMotion}): the flight's torpedo line, the rolling head, the hand walk lowered
 * onto its hands, the hops and the limp. Every viewer (it reads only the synced state and the player's movement).
 */
@Mixin(PlayerRenderer.class)
public abstract class UndeadBodyRotationMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
			at = @At("TAIL"))
	private void projecthero$undeadMotion(AbstractClientPlayer player, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks,
			float scale, CallbackInfo ci) {
		UndeadMotion.apply(player, poseStack, partialTicks, scale);
	}

	/** Flying while sneaking (the flight is Shift + X): no sneak drop of the whole body, eased out with the flight pose. */
	@Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"),
			cancellable = true)
	private void projecthero$undeadFlightNoSneakDrop(AbstractClientPlayer player, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
		float fly = UndeadMotion.flightBlend(player, partialTicks);
		if (fly > 0.001f && player.isCrouching()) {
			Vec3 v = cir.getReturnValue();
			float e = fly * fly * (3f - 2f * fly);
			cir.setReturnValue(new Vec3(v.x, v.y * (1f - e), v.z));
		}
	}
}
