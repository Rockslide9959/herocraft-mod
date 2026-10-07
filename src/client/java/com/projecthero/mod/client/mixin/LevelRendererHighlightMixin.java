package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * v0.15.11, explicit user request: the Iron Man mob highlight covers a full SPHERE around the wearer. The highlight
 * decision itself was always 3D, but vanilla never draws an entity standing in a chunk section the renderer has not
 * compiled -- and a section is only compiled once the section-occlusion graph can see it. A mob down in a sealed cave
 * (or up inside a solid cliff) therefore never rendered, so it never got its outline: in practice only mobs at roughly
 * the wearer's own level lit up.
 *
 * <p>This lets an entity the LOCAL wearer's own highlight outlines ({@code IronManHighlight#outlines}) render even in an
 * uncompiled section, so its outline shows through the rock. Everything else keeps vanilla's rule. Client-only and
 * decided from the local player's own state -- nothing is sent anywhere (the v0.15.4 privacy rule is untouched).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererHighlightMixin {
	/** The entity the render loop is looking at right now (set by its {@code blockPosition()} call just before the check). */
	@Unique
	private static Entity projecthero$loopEntity;

	@WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"))
	private BlockPos projecthero$rememberLoopEntity(Entity entity, Operation<BlockPos> original) {
		projecthero$loopEntity = entity;
		return original.call(entity);
	}

	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
	private boolean projecthero$highlightSeesThroughRock(boolean compiled) {
		Entity e = projecthero$loopEntity;
		projecthero$loopEntity = null;
		if (compiled || e == null || !com.projecthero.mod.ironman.IronManHighlight.mayDecide(e)) {
			return compiled;
		}
		var viewer = Minecraft.getInstance().player;
		// v0.15.13: Nova's Worldmind sphere sees through rock the same way
		return viewer != null && (com.projecthero.mod.ironman.IronManHighlight.outlines(viewer, e)
				|| com.projecthero.mod.client.nova.NovaWorldmindClient.outlines(viewer, e));
	}
}
