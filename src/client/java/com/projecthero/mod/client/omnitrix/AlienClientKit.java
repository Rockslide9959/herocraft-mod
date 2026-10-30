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

	/**
	 * v0.17.1 (XLR8's visor): the skin for {@code p} right now -- a kit with a second look (the closed-mask skin while he runs)
	 * answers from synced state. Third person, the first-person arm and the speed after-images all draw this one.
	 */
	default ResourceLocation skin(AbstractClientPlayer p) {
		return skin();
	}

	/**
	 * v0.17.1 (Ghostfreak's Spirit Sight): whether {@code e} is outlined for {@code viewer}, the local wearer, right now. Asked by
	 * {@code OmnitrixGlowMixin} on this client only (nobody else sees it). Default never.
	 */
	default boolean glows(net.minecraft.client.player.LocalPlayer viewer, net.minecraft.world.entity.Entity e) {
		return false;
	}

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

	// ---------------------------------------------------------------- v0.17.1 (Wildmutt): a fully custom body
	// A "real" alien replaces the player model outright -- its own renderer (usually a GeckoLib GeoReplacedEntityRenderer, as the
	// Hulk's) draws the whole body, and the first-person arm is its own too. All default off: the player-model aliens are untouched.

	/** True: {@link #renderBody} draws this alien instead of the player model (and the player-model hooks above are skipped). */
	default boolean customBody() {
		return false;
	}

	/** (Re)builds the custom body's renderer from the player renderers' context (on every resource reload). */
	default void buildBody(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
	}

	/** Draws the alien's whole body in place of the player (armour, cape and held items are not drawn); the badge is its job too. */
	default void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
	}

	/**
	 * Draws the first-person arm ({@code right}: the right arm) in place of the player's, in the space vanilla's
	 * {@code renderRightHand} / {@code renderLeftHand} gets (the swing / equip / kit clip already applied). True if it drew.
	 */
	default boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		return false;
	}

	/** True: the off-hand arm is drawn in first person too whenever the off hand is empty (a beast on all fours). */
	default boolean bothHandsInFirstPerson(AbstractClientPlayer p) {
		return false;
	}

	/**
	 * Where the Omnitrix badge sits, in {@link #badgeBone}'s pixels: {x, y, z, size} facing forward (x right, y down, z forward =
	 * negative). v0.17.1: a fifth value of 1 turns it to face out of the bone's +x side (x is then that face, z the centre).
	 */
	default float[] badge() {
		return new float[] { 0f, 4.5f, -2.05f, 3.2f };
	}

	int BADGE_BODY = 0, BADGE_LEFT_ARM = 1, BADGE_HEAD = 2;

	/** v0.17.1: the bone {@link #badge} is on -- the body (default), the left arm (Four Arms) or the head (Stinkfly's face). */
	default int badgeBone() {
		return BADGE_BODY;
	}
}
