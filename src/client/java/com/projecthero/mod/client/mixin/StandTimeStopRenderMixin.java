package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.projecthero.mod.client.stand.TimeStopClient;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.2 Star Platinum: The World -- a frozen entity renders at partial tick 0: its limb swing, swing arm, idle sway and
 * age-driven animations all hold their last tick instead of creeping forward and snapping back every frame.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class StandTimeStopRenderMixin {
	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float projecthero$timeStopPartial(float partialTick, Entity entity) {
		return TimeStopClient.frozen(entity) ? 0f : partialTick;
	}
}
