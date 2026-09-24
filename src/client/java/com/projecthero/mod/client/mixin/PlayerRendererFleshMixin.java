package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.wolverine.Wolverine;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.12.14: when a Wolverine dies his skin is replaced by the raw flesh-and-bone creature texture for
 * the death animation -- the adamantium-and-healing-factor body giving out. The flesh model is a
 * standard 64x64 player layout, so swapping the skin texture is the whole model swap; every viewer
 * sees it because it keys off the synced Wolverine state and the (synced) dying flag.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererFleshMixin {
	private static final ResourceLocation FLESH = ProjectHeroMod.id("textures/entity/wolverine_flesh.png");

	@Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$wolverineFlesh(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
		if (Wolverine.hasPower(player) && com.projecthero.mod.client.wolverine.WolverineFlesh.active(player, 0.0f)) {
			cir.setReturnValue(FLESH);
		}
	}
}
