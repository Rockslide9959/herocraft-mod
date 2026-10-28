package com.projecthero.mod.client.omnitrix;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1: one Omnitrix alien's client side -- its "basic model" (user: "create very basic models for the time being"): the
 * player's own body with the alien's 64x64 wide skin ({@link #skin()}), sized by the kit's {@code scale} stat (vanilla's scale
 * attribute does it), plus whatever simple extra cubes {@link #renderExtras} draws onto the model's bones (Four Arms' second
 * pair of arms, Heatblast's flame head; Stinkfly's wings, Ripjaws' tail, XLR8's visor... are the same idea). The framework
 * swaps the skin (third person and the first-person arm), always uses the wide arm model, hides armour and the cape, draws the
 * Omnitrix badge and the timeout flash, and calls the hooks below. Register with {@link AlienClients#register}.
 */
public interface AlienClientKit {
	/** The alien's 64x64 wide-arm player skin. */
	ResourceLocation skin();

	/** Extra cubes on the body (the model's bones are posed already). Use {@link AlienParts} helpers. */
	default void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
	}

	/** Extra geometry on the first-person arm ({@code arm} posed already; {@code right} = the main-hand arm). */
	default void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
	}

	/**
	 * Third-person pose for the kit's synced {@code animId}, {@code ticks} after it started (also called with the running state
	 * every frame: read {@code OmnitrixClientState}). Usually one {@code QuirkKeyframes.play}. Return true if a pose was applied.
	 */
	default boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		return false;
	}

	/**
	 * A first-person clip for {@code animId} ({@link FirstPersonClip}), or null for none. The framework plays it on the bare
	 * main-hand arm (and on the off-hand arm too when the clip is two-handed).
	 */
	@Nullable
	default FirstPersonClip firstPerson(int animId) {
		return null;
	}

	/** Every client tick for every transformed player in view (ambient particles, sounds). */
	default void tick(AbstractClientPlayer p) {
	}

	/**
	 * v0.17.1 (added for Ripjaws' hydration meter; optional): extra HUD rows for the transformed wearer, stacked over the
	 * alien's name in the bottom-right HUD. {@code x} is the HUD's left edge and {@code w} its width, {@code y} the bottom of the
	 * free space: draw in the {@link #hudHeight} px ABOVE it, ideally with {@code OmnitrixHud.meter} so the rows match.
	 */
	default void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
	}

	/** v0.17.1: how many px {@link #hud} draws above the name line (the Left-Alt move list stacks above it). */
	default int hudHeight() {
		return 0;
	}

	/**
	 * v0.17.1 wave A (Ghostfreak): the body's opacity, 0..255. Below 255 the player body is drawn see-through (vanilla's
	 * translucent entity type, {@code OmnitrixAlienTranslucentMixin}). Default opaque.
	 */
	default int bodyAlpha(AbstractClientPlayer p) {
		return 255;
	}

	/** Where the Omnitrix badge sits: in body-bone pixels (x right, y down from the neck, z forward = negative). */
	default float[] badge() {
		return new float[] { 0f, 4.5f, -2.05f, 3.2f };
	}
}
