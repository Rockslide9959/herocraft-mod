package com.projecthero.mod.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;

/** v0.17.1: the wide / slim player renderers (an Omnitrix alien always uses the wide one). */
@Mixin(EntityRenderDispatcher.class)
public interface OmnitrixRenderDispatcherAccessor {
	@Accessor("playerRenderers")
	Map<PlayerSkin.Model, EntityRenderer<? extends Player>> projecthero$playerRenderers();
}
