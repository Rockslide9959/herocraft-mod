package com.projecthero.mod.client.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;

/** v0.14.12: the texture a texture shard binds (see {@link RenderTypeCompositeInvoker}). */
@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface TextureShardInvoker {
	@Invoker("cutoutTexture")
	Optional<ResourceLocation> projecthero$texture();
}
