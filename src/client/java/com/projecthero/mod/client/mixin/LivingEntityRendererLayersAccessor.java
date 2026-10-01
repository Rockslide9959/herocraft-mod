package com.projecthero.mod.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** v0.14.12: a living renderer's layers, so the Super Speed after-images can draw the armour layer on their copies. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererLayersAccessor {
	@Accessor("layers")
	List<RenderLayer<?, ?>> projecthero$layers();
}
