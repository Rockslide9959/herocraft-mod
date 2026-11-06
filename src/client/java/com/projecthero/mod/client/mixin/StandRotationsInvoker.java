package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/** v0.17.2: the Stand copies its user: the body rotations their renderer gives them (swimming, an elytra, whatever a power adds). */
@Mixin(LivingEntityRenderer.class)
public interface StandRotationsInvoker {
	@Invoker("setupRotations")
	void projecthero$setupRotations(LivingEntity entity, PoseStack pose, float bob, float bodyYaw, float partial, float scale);
}
