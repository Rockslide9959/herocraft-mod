package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p18.DensityManipulationHandlers;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Density Manipulation: a phased body renders <em>see-through</em> rather than solid (v0.10.10).
 *
 * <p>Walking through a wall while looking exactly as solid as everyone else reads as clipping through
 * the world rather than as a power; and full invisibility would be worse, since it hides the one thing
 * that tells other players what is happening. Half-transparent is the readable middle.
 *
 * <p>Two arguments of vanilla's own draw are rewritten rather than replacing any of the render logic:
 * <ol>
 *   <li>the {@code translucent} flag handed to {@code getRenderType}, which is what makes vanilla pick
 *       {@code RenderType.itemEntityTranslucentCull} — the same render type the Invisibility potion uses
 *       for a teammate who can see friendly invisibles, so no new pipeline is involved; and</li>
 *   <li>the packed ARGB colour handed to {@code renderToBuffer}, from opaque white to
 *       {@link #PHASE_COLOR}. Vanilla passes {@code 0x27FFFFFF} (15% alpha) for a true invisible; this
 *       is deliberately far more opaque so the phased player is plainly visible, just ghostly.</li>
 * </ol>
 *
 * <p>Whether the entity being drawn is phasing has to be stashed rather than read in the two
 * {@code @ModifyArg}s, since neither is handed the entity. Entity rendering is single-threaded and the
 * flag is set at the head of the very {@code render} call the two argument rewrites live inside, so
 * this cannot interleave with another entity's draw.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityPhaseMixin {
	/** Packed ARGB: 60% alpha, white tint. */
	@Unique
	private static final int PHASE_COLOR = 0x99FFFFFF;

	@Unique
	private static boolean projecthero$phasingNow;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void projecthero$beginPhaseCheck(LivingEntity entity, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		projecthero$phasingNow = entity instanceof Player player && DensityManipulationHandlers.phasing(player);
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$endPhaseCheck(LivingEntity entity, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		projecthero$phasingNow = false;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"),
			index = 2)
	private boolean projecthero$phaseUsesTranslucentType(boolean translucent) {
		return translucent || projecthero$phasingNow;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
			index = 4)
	private int projecthero$phaseAlpha(int color) {
		return projecthero$phasingNow ? PHASE_COLOR : color;
	}
}
