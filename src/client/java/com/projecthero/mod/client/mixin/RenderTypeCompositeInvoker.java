package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.RenderType;

/** v0.14.12: reads a composite render type's state (its texture), for the Super Speed after-images' ghost armour. */
@Mixin(targets = "net.minecraft.client.renderer.RenderType$CompositeRenderType")
public interface RenderTypeCompositeInvoker {
	@Invoker("state")
	RenderType.CompositeState projecthero$state();
}
