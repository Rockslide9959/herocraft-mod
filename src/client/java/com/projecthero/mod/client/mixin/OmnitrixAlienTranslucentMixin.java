package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.1 Omnitrix framework ({@link AlienClientKit#bodyAlpha}): a see-through alien body (Ghostfreak). The same two argument
 * rewrites as Density Manipulation's {@code LivingEntityPhaseMixin}: the translucent flag handed to {@code getRenderType}
 * (vanilla's own translucent entity type) and the alpha of the colour handed to {@code renderToBuffer}. The alpha is stashed at
 * the head of the very {@code render} call (entity rendering is single-threaded) and cleared at its return.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OmnitrixAlienTranslucentMixin {
	@Unique
	private static int projecthero$alienAlpha = 255;

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void projecthero$alienAlphaBegin(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		projecthero$alienAlpha = 255;
		if (entity instanceof AbstractClientPlayer p) {
			AlienClientKit kit = AlienClients.shownKit(p);
			if (kit != null) {
				projecthero$alienAlpha = Math.max(0, Math.min(255, kit.bodyAlpha(p)));
			}
		}
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void projecthero$alienAlphaEnd(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		projecthero$alienAlpha = 255;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;"),
			index = 2)
	private boolean projecthero$alienTranslucentType(boolean translucent) {
		return translucent || projecthero$alienAlpha < 255;
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
			index = 4)
	private int projecthero$alienAlpha(int color) {
		if (projecthero$alienAlpha >= 255) {
			return color;
		}
		int a = Math.min((color >>> 24) & 0xFF, projecthero$alienAlpha);
		return (a << 24) | (color & 0x00FFFFFF);
	}
}
