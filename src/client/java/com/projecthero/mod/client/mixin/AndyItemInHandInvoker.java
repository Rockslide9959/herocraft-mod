package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;

/** v0.17.3: Andy's first person draws a bare arm on either side (flight stumps, the arm drag, the hand walk). */
@Mixin(ItemInHandRenderer.class)
public interface AndyItemInHandInvoker {
	@Invoker("renderPlayerArm")
	void projecthero$renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipped, float swing, HumanoidArm side);
}
