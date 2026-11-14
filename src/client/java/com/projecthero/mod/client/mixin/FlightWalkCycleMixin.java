package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.projecthero.mod.client.FlightPoseHelper;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 (user: "the green lantern flight is bugged ... when the player moves around his arms move as if hes walking"):
 * vanilla's walk cycle ({@code walkAnimation}) is fed by the player's horizontal speed whether or not his feet touch
 * anything, so every power flight swung the arms and legs like a walk as soon as it moved. The flight poses only ever
 * lerped part of the way off it (a cruise leans 25 degrees, which pinned the arms by a quarter), so the swing showed
 * through.
 *
 * <p>The fix is at the source: while {@link FlightPoseHelper#airborne} says the player is flying (any power, eased in at
 * take-off and out on landing), the limb-swing amount the renderer hands the model -- and every layer, armour and
 * GeckoLib suit, since they all get the same number -- is scaled down to nothing. The swing position is left alone, and
 * each flight's own pose and the shared flight feel (dangling / trailing legs) still play on top.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FlightWalkCycleMixin {
	@ModifyExpressionValue(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;speed(F)F"))
	private float projecthero$noWalkCycleInFlight(float speed, LivingEntity entity, float yaw, float partialTick,
			PoseStack pose, MultiBufferSource buffers, int light) {
		if (!(entity instanceof Player player) || !FlightPoseHelper.WALK_FIX_ENABLED) {
			return speed;
		}
		float air = FlightPoseHelper.airborne(player, partialTick);
		return air <= 0.0f ? speed : speed * (1.0f - air);
	}
}
