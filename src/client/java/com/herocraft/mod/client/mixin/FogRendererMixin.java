package com.herocraft.mod.client.mixin;

import com.herocraft.mod.client.gui.RaidSkyTint;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The dark-purple raid sky: once the base fog colour has been worked out for the frame, blend it
 * toward a deep violet by how far the local player is into a Zombie Raid area
 * ({@link RaidSkyTint#strength()}). This tints the horizon, the void gradient and the general
 * ambient wash, which is what reads as "the sky has gone purple" -- the same idea a vanilla Pillager
 * raid uses to recolour the world while it is running.
 *
 * <p>v0.9.2: the colour tint alone left the sky purple but the horizon still reading blue, because
 * a bright daytime horizon band shows through a partial blend. Two changes fix that -- the colour
 * blend is pushed much closer to full violet, and {@code setupFog} is also hooked to pull the
 * atmospheric fog distance in, so the raid area gains a genuine purple haze that swallows the blue
 * horizon rather than only recolouring the dome.
 *
 * <p>v0.9.9: there was still a blue band <em>between</em> the (purple) sky dome and the (purple)
 * horizon haze. That band is the raw GL clear colour showing through the gap where the sky-dome mesh
 * ends -- and vanilla's {@code setupColor} sets that clear colour from {@code fogRed/Green/Blue}
 * <em>before</em> this TAIL injection recolours them, so the clear stayed on the daytime blue. The
 * fix is to re-issue {@link RenderSystem#clearColor} with the tinted values at the end of the hook.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	@Shadow
	private static float fogRed;
	@Shadow
	private static float fogGreen;
	@Shadow
	private static float fogBlue;

	@Inject(method = "setupColor", at = @At("TAIL"))
	private static void herocraft$raidSky(Camera camera, float partialTick, ClientLevel level,
			int renderDistanceChunks, float darkenWorldAmount, CallbackInfo ci) {
		float s = RaidSkyTint.strength();
		if (s <= 0.0f) {
			return;
		}
		s = Math.min(1.0f, s); // at the centre of the raid, no daytime colour is left at all
		fogRed = Mth.lerp(s, fogRed, 0.13f);
		fogGreen = Mth.lerp(s, fogGreen, 0.02f);
		fogBlue = Mth.lerp(s, fogBlue, 0.21f);
		// Re-issue the GL clear colour: vanilla already set it from the pre-tint fog colour earlier in
		// setupColor, which is the blue band that was showing between the sky dome and the horizon.
		RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0f);
	}

	/**
	 * Pull the atmospheric fog in over the raid area so the (already purple-tinted) fog colour
	 * actually reads as a thick haze on the horizon. Runs after vanilla has set its own fog distance,
	 * and lerps from "no change" at the fade edge to a dense ~90-block murk at the centre.
	 */
	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void herocraft$raidFog(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance,
			boolean shouldCreateFog, float partialTick, CallbackInfo ci) {
		float s = RaidSkyTint.strength();
		if (s <= 0.0f) {
			return;
		}
		float end = Mth.lerp(s, 320.0f, 90.0f);
		RenderSystem.setShaderFogStart(end * 0.22f);
		RenderSystem.setShaderFogEnd(end);
	}
}
