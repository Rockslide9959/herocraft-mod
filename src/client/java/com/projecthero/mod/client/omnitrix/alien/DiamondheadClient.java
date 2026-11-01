package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Diamondhead's client kit -- the faceted green Petrosapien skin, a pointed crystal head (a stepped diamond turned 45
 * degrees, cheek fins), four faceted crystal spikes out of his back (two long, two short). A pose for every move. Also hands
 * his crystal block to the translucent render layer.
 *
 * <p>v0.17.2 revamp: Combat Mode's two long faceted crystal BLADES out of both forearms (third and first person; they grow in
 * and shatter away), and the held Crystal SHIELD -- a faceted green tower shield over the left forearm, kept upright and facing
 * ahead whatever the arm does, cracking as it takes damage (third person; in first person it is held up on the left of the view).
 */
public final class DiamondheadClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead_extras.png");

	private static final float[] CRYSTAL = { 0f, 0f, 0.5f, 0.5f };
	private static final float[] DARK = { 0.5f, 0f, 0.75f, 0.25f };
	private static final float[] GLOW = { 0.75f, 0f, 1f, 0.25f };
	private static final float[] PALE = { 0.5f, 0.25f, 0.75f, 0.5f };

	private static final float[][] VOLLEY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0.6f, -0.3f, -0.3f, -0.6f, 0.3f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.6f, -0.35f, 0, -1.6f, 0.35f, 0, 0.2f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] SPEAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.9f, 0.3f, 0.3f, -0.8f, 0, -0.3f, -0.25f, 0.5f, 0.2f, 0, 0, -0.3f, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -1.2f, -0.2f, 0, 0.4f, 0, 0, 0.4f, -0.4f, -0.3f, 0, 0, 0.4f, 0, 0, 0.2f), QuirkKeyframes.rest(15) };
	// the Shatter Line: both blades raised overhead, then driven down into the ground in a crouch
	private static final float[][] SHATTER = { QuirkKeyframes.restC(0), QuirkKeyframes.fc(3, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f, 0),
			QuirkKeyframes.fc(6, -0.5f, 0, 0.1f, -0.5f, 0, -0.1f, 0.7f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.4f, 0.8f),
			QuirkKeyframes.fc(14, -0.4f, 0, 0.1f, -0.4f, 0, -0.1f, 0.6f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f, 0.7f), QuirkKeyframes.restC(20) };
	private static final float[][] SLIDE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0),
			QuirkKeyframes.f(18, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0), QuirkKeyframes.rest(22) };
	private static final float[][] VAULT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -3.0f, 0, 0.25f, -3.0f, 0, -0.25f, -0.2f, 0, 0.2f, 0, 0.1f, 0.2f, 0, -0.1f, -0.5f),
			QuirkKeyframes.f(14, -2.8f, 0, 0.3f, -2.8f, 0, -0.3f, -0.15f, 0, 0.3f, 0, 0.1f, 0.3f, 0, -0.1f, -0.4f), QuirkKeyframes.rest(20) };
	private static final float[][] WALL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, -0.3f, 0, -1.5f, 0.3f, 0, 0.3f, 0, -0.5f, 0, 0, 0.5f, 0, 0, 0),
			QuirkKeyframes.f(10, -1.55f, -0.35f, 0, -1.55f, 0.35f, 0, 0.3f, 0, -0.5f, 0, 0, 0.5f, 0, 0, 0), QuirkKeyframes.rest(15) };
	private static final float[][] BARRAGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.1f, 0, 0.1f, -0.3f, 0, -0.4f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.6f),
			QuirkKeyframes.f(12, -3.1f, 0, 0.1f, -0.3f, 0, -0.4f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.6f),
			QuirkKeyframes.f(16, -1.4f, 0, 0, -0.3f, 0, -0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(24) };
	private static final float[][] PRISON = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.0f, 0.9f, -0.4f, -1.0f, -0.9f, 0.4f, 0.35f, 0, -0.2f, 0, 0.2f, -0.2f, 0, -0.2f, 0.4f),
			QuirkKeyframes.f(8, -0.4f, 0, 0.6f, -0.4f, 0, -0.6f, -0.1f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.3f), QuirkKeyframes.rest(16) };
	private static final float[][] SLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.9f, 0.9f, 0.4f, 0.2f, 0, 0, 0, 0.5f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.2f, -1.0f, -0.2f, 0.3f, 0, 0, 0.2f, -0.5f, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] PLACE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.1f, 0, 0, 0, 0, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(8) };
	// v0.17.2 (user: "Make Z an aoe spike ability"): the Crystal Eruption -- both fists hauled up overhead, then a dropping
	// slam into the ground on one knee-deep crouch, held while the rings burst out
	private static final float[][] ERUPT = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(4, -3.0f, 0.15f, 0.35f, -3.0f, -0.15f, -0.35f, -0.25f, 0, 0.1f, 0, 0, 0.1f, 0, 0, -0.35f, 0.1f),
			QuirkKeyframes.fc(7, -0.35f, 0, 0.15f, -0.35f, 0, -0.15f, 0.75f, 0, -0.7f, 0, 0.15f, 0.2f, 0, -0.1f, 0.45f, 1.0f),
			QuirkKeyframes.fc(17, -0.3f, 0, 0.15f, -0.3f, 0, -0.15f, 0.7f, 0, -0.65f, 0, 0.15f, 0.2f, 0, -0.1f, 0.4f, 0.95f), QuirkKeyframes.restC(24) };
	// ---- v0.17.2 revamp
	// Combat Mode on: the forearms crossed before the chest as the crystal grows, then flung out to the sides, blades bared
	private static final float[][] COMBAT_ON = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.35f, -0.95f, 0, -1.35f, 0.95f, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.f(9, -0.55f, 0.35f, 0.55f, -0.55f, -0.35f, -0.55f, -0.05f, 0, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, -0.1f), QuirkKeyframes.rest(14) };
	// off: a shake of both arms as the blades shatter away
	private static final float[][] COMBAT_OFF = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0, 0.35f, -0.9f, 0, -0.35f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.f(6, -0.4f, 0, 0.15f, -0.4f, 0, -0.15f, 0, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	// the shield going up: a brace behind it (the left arm is held by heldShield after)
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0, 0, 0, -1.1f, 0.55f, 0, 0.15f, 0.1f, -0.25f, 0, 0, 0.25f, 0, 0, 0),
			QuirkKeyframes.rest(7) };
	// the Shield Bash: lunging forward low behind it
	private static final float[][] BASH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -0.5f, 0, 0.25f, -1.35f, 0.3f, 0, 0.35f, 0.15f, 0.55f, 0, 0, -0.7f, 0, 0, -0.2f),
			QuirkKeyframes.f(10, -0.5f, 0, 0.25f, -1.35f, 0.3f, 0, 0.35f, 0.15f, -0.6f, 0, 0, 0.6f, 0, 0, -0.2f), QuirkKeyframes.rest(14) };
	// Blade Flurry: the right blade sweeping right to left, the left blade back left to right, then the right blade's thrust
	private static final float[][] FLURRY_1 = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 1.0f, 0.7f, -0.4f, 0, 0, 0.05f, 0.45f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.45f, -1.0f, -0.25f, -0.4f, 0, 0, 0.15f, -0.45f, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] FLURRY_2 = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.4f, 0, 0, -1.7f, -1.0f, -0.7f, 0.05f, -0.45f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -0.4f, 0, 0, -1.45f, 1.0f, 0.25f, 0.15f, 0.45f, -0.2f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] STAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0.2f, 0.2f, -0.5f, 0, -0.2f, -0.05f, 0.5f, 0.2f, 0, 0, -0.2f, 0, 0, 0),
			QuirkKeyframes.f(6, -1.6f, -0.1f, 0, -0.3f, 0, -0.3f, 0.3f, -0.35f, -0.55f, 0, 0, 0.55f, 0, 0, 0.1f),
			QuirkKeyframes.f(11, -1.55f, -0.1f, 0, -0.3f, 0, -0.3f, 0.28f, -0.35f, -0.5f, 0, 0, 0.5f, 0, 0, 0.1f), QuirkKeyframes.rest(15) };
	// the Crystal Cyclone: both blades flung out level and swept round as the body whips through the turn
	private static final float[][] CYCLONE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -1.5f, 1.2f, 0.4f, -1.5f, 1.2f, -0.4f, 0.1f, 0.8f, 0, 0, 0.15f, 0, 0, -0.15f, 0),
			QuirkKeyframes.f(5, -1.5f, -1.1f, 0.4f, -1.5f, -1.1f, -0.4f, 0.1f, -0.8f, 0, 0, 0.15f, 0, 0, -0.15f, 0),
			QuirkKeyframes.f(8, -1.5f, 1.0f, 0.4f, -1.5f, 1.0f, -0.4f, 0.1f, 0.7f, 0, 0, 0.15f, 0, 0, -0.15f, 0), QuirkKeyframes.rest(13) };
	// the shield shattering: knocked back a step, the arm flung wide
	private static final float[][] SHIELD_BREAK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 0.4f, -0.6f, 0, -1.1f, -0.25f, 0, -0.3f, 0, 0, 0.3f, 0, 0, -0.3f),
			QuirkKeyframes.rest(12) };

	private static final FirstPersonClip FP_VOLLEY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 15, -15, 0, -0.1f, -0.1f, 0.1f),
			FirstPersonClip.f(5, -25, 25, 0, 0.2f, 0.12f, -0.25f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPEAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 0, 15, 0.05f, 0.5f, 0.25f),
			FirstPersonClip.f(7, -10, 25, 0, 0.15f, 0.05f, -0.3f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(6, 35, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUSH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 30, 0, 0.2f, 0.15f, -0.3f),
			FirstPersonClip.f(10, -30, 30, 0, 0.2f, 0.15f, -0.3f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RAISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -70, -20, 20, -0.1f, 0.45f, 0.1f),
			FirstPersonClip.f(14, -70, -20, 20, -0.1f, 0.45f, 0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SURF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 10, -45, 40, -0.35f, 0.05f, 0.1f),
			FirstPersonClip.f(18, 10, -45, 40, -0.35f, 0.05f, 0.1f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -40, -40, -20, -0.2f, 0.25f, 0),
			FirstPersonClip.f(5, -10, 50, 20, 0.35f, 0.0f, -0.25f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PLACE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 15, 0, 0.1f, 0.0f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_FLEX = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, 40, 0, 0.25f, 0.15f, 0.05f),
			FirstPersonClip.f(8, -20, -30, 30, -0.2f, 0.1f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	/** v0.17.2: the Crystal Eruption's slam -- both fists up out of view, then driven down hard past the bottom of the screen. */
	private static final FirstPersonClip FP_ERUPT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -95, 5, 0, 0.05f, 0.6f, 0.25f),
			FirstPersonClip.f(7, 50, 10, 0, 0.05f, -0.5f, -0.2f), FirstPersonClip.f(17, 45, 10, 0, 0.05f, -0.45f, -0.18f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	// ---- v0.17.2 revamp
	private static final FirstPersonClip FP_COMBAT_ON = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -50, 35, 0, 0.18f, 0.25f, 0),
			FirstPersonClip.f(9, -20, -20, 0, -0.1f, 0.05f, 0), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_COMBAT_OFF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 20, 0, 0.1f, 0.1f, 0),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	/** The slashes swing wide across the view; the second comes back the other way. */
	private static final FirstPersonClip FP_FLURRY_1 = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -45, -50, -25, -0.25f, 0.3f, 0),
			FirstPersonClip.f(5, -15, 60, 25, 0.45f, 0.0f, -0.3f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_FLURRY_2 = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -35, 60, 30, 0.4f, 0.25f, -0.1f),
			FirstPersonClip.f(5, -15, -45, -25, -0.25f, 0.0f, -0.3f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	/** The stab: drawn back, then driven straight into the screen. */
	private static final FirstPersonClip FP_STAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 10, 0, 0, 0, -0.05f, 0.25f),
			FirstPersonClip.f(6, -25, 8, 0, 0.12f, 0.12f, -0.65f), FirstPersonClip.f(11, -24, 8, 0, 0.12f, 0.12f, -0.6f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CYCLONE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, -55, -35, -0.35f, 0.1f, 0),
			FirstPersonClip.f(6, -25, 65, 35, 0.45f, 0.1f, -0.2f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 20, 0, 0.1f, 0.08f, -0.35f),
			FirstPersonClip.f(10, -25, 20, 0, 0.1f, 0.08f, -0.35f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SHIELD_BREAK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -15, 0, -0.12f, -0.2f, 0.2f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();

	public DiamondheadClient() {
		// his conjured crystal is see-through
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(DiamondheadBlocks.CRYSTAL, RenderType.translucent());
		// v0.17.2 (user: "for diamondhead make spike projectiles for his spike projectile abilities instead of just glowing orbs")
		OmnitrixRenderers.projectileLook("diamondhead_shard", (e, partial, pose, buffers) -> shardShot(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("diamondhead_spear", (e, partial, pose, buffers) -> shardShot(e, partial, pose, buffers, true));
	}

	// ---------------------------------------------------------------- v0.17.2: the shard and spear projectiles

	private static final ResourceLocation GLOW_TEX = ProjectHeroMod.id("textures/entity/omnitrix/glow.png");
	/** A flung shard's profile along its flight, tail (0) to point (1): {t, radius scale} -- a short butt, the widest facet ring, the long point. */
	private static final float[][] SHARD_PROFILE = { { 0f, 0f }, { 0.1f, 0.72f }, { 0.28f, 1.0f }, { 0.55f, 0.82f }, { 1f, 0f } };
	/** The spear's: a long, slender shaft between its butt and its blade. */
	private static final float[][] SPEAR_PROFILE = { { 0f, 0f }, { 0.06f, 0.55f }, { 0.4f, 0.62f }, { 0.58f, 1.0f }, { 0.72f, 0.85f }, { 1f, 0f } };

	/**
	 * A Diamondhead shard / Crystal Spear in flight: a faceted hexagonal crystal (see-through green-teal, every facet shaded by
	 * which way it faces) pointed along its velocity and turning slowly about it, a glowing core inside, and a glint that
	 * flashes across it now and then. The spear is a long slender crystal with three smaller shards flaring off its butt.
	 */
	private static void shardShot(com.projecthero.mod.omnitrix.entity.AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers,
			boolean spear) {
		net.minecraft.world.phys.Vec3 v = e.getDeltaMovement();
		if (v.lengthSqr() < 1.0e-6) {
			v = net.minecraft.world.phys.Vec3.directionFromRotation(e.getXRot(), e.getYRot());
		}
		net.minecraft.world.phys.Vec3 dir = v.normalize();
		float age = e.tickCount + partial;
		float s = e.size();
		// grows out of his hand over the first two ticks rather than popping in whole
		float grow = Mth.clamp(0.45f + age * 0.28f, 0.45f, 1f);
		float len = (spear ? s * 3.9f : s * 3.4f) * grow;
		float r = (spear ? s * 0.32f : s * 0.42f) * grow;
		int block = Math.max(10, LightTexture.block(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(e, partial)));
		int light = LightTexture.pack(block, 15);
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		// the glint: a four-point star flashing across the crystal (camera-facing; drawn before the crystal turns the pose)
		float flash = Mth.sin(age * (spear ? 0.35f : 0.55f) + e.getId());
		if (flash > 0.55f) {
			float k = (flash - 0.55f) / 0.45f;
			float along = (spear ? 0.22f : 0.12f) * len;
			pose.pushPose();
			pose.translate(dir.x * along, dir.y * along, dir.z * along);
			glint(pose, buffers, (spear ? 0.75f : 0.38f) * k * grow, (int) (230 * k));
			pose.popPose();
		}
		pose.mulPose(Axis.YP.rotation((float) Math.atan2(dir.x, dir.z)));
		pose.mulPose(Axis.XP.rotation((float) -Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
		pose.mulPose(Axis.ZP.rotation(age * (spear ? 0.12f : 0.35f) + e.getId() * 0.7f));
		float tail = -len * (spear ? 0.55f : 0.32f);
		float[][] profile = spear ? SPEAR_PROFILE : SHARD_PROFILE;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		// the crystal itself, a touch flattened so it reads as a cut shard, not a cone
		facetted(pose.last(), vc, profile, tail, len, r, 0.78f, 0xC8D8FFF2, light, true);
		if (spear) {
			// three small shards flaring back off the butt like fletching
			for (int k = 0; k < 3; k++) {
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotation(k * Mth.TWO_PI / 3f));
				pose.translate(0, r * 0.45f, tail + len * 0.16f);
				// pointing back and out
				pose.mulPose(Axis.XP.rotation(Mth.PI + 0.5f));
				facetted(pose.last(), vc, SHARD_PROFILE, -len * 0.08f, len * 0.26f, r * 0.42f, 0.8f, 0xC8D8FFF2, light, true);
				pose.popPose();
			}
		}
		// the bright core inside (additive, so it glows through the see-through facets)
		float pulse = 0.75f + 0.25f * Mth.sin(age * 0.6f);
		int c = (int) (255 * pulse);
		int core = 0xFF000000 | ((c * 0x8C / 255) << 16) | (c << 8) | (c * 0xE0 / 255);
		facetted(pose.last(), buffers.getBuffer(RenderType.eyes(EXTRAS)), profile, tail * 0.8f, len * 0.86f, r * 0.45f, 0.78f, core, LightTexture.FULL_BRIGHT,
				false);
		pose.popPose();
	}

	/**
	 * A crystal along +Z from {@code z0}, {@code len} long, {@code r} wide ({@code flat} times as deep): six facets round each
	 * span of {@code profile}, each wound outward and shaded from its facing (pale lit, base, dark shadowed texels) unless
	 * {@code shaded} is false (the core: one glowing texel).
	 */
	private static void facetted(PoseStack.Pose p, VertexConsumer vc, float[][] profile, float z0, float len, float r, float flat, int argb, int light,
			boolean shaded) {
		int n = 6;
		float[][][] ring = new float[profile.length][n][3];
		for (int k = 0; k < profile.length; k++) {
			float rs = profile[k][1] * r;
			double twist = k * 0.12;
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + twist;
				ring[k][i][0] = (float) Math.cos(a) * rs;
				ring[k][i][1] = (float) Math.sin(a) * rs * flat;
				ring[k][i][2] = z0 + profile[k][0] * len;
			}
		}
		for (int k = 0; k < ring.length - 1; k++) {
			for (int i = 0; i < n; i++) {
				float[] a = ring[k][i], b = ring[k][(i + 1) % n], c = ring[k + 1][(i + 1) % n], d = ring[k + 1][i];
				// the facet's normal from its two diagonals (sound even where a ring closes to a point)
				float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
				float wx = d[0] - b[0], wy = d[1] - b[1], wz = d[2] - b[2];
				float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
				float nl = Math.max(1e-7f, Mth.sqrt(nx * nx + ny * ny + nz * nz));
				nx /= nl;
				ny /= nl;
				nz /= nl;
				// outward: away from the crystal's axis
				float mx = (a[0] + b[0] + c[0] + d[0]) * 0.25f, my = (a[1] + b[1] + c[1] + d[1]) * 0.25f;
				boolean flip = nx * mx + ny * my < 0;
				if (flip) {
					nx = -nx;
					ny = -ny;
					nz = -nz;
				}
				float[] uv;
				if (shaded) {
					// lit from above and ahead: the facets catch the light as the shard turns
					float lit = ny * 0.6f + nx * 0.25f + nz * 0.3f;
					uv = lit > 0.25f ? PALE : lit > -0.3f ? CRYSTAL : DARK;
				} else {
					uv = GLOW;
				}
				if (flip) {
					AlienParts.quad(vc, p, argb, light, a[0], a[1], a[2], d[0], d[1], d[2], c[0], c[1], c[2], b[0], b[1], b[2], nx, ny, nz, uv[0], uv[1], uv[2], uv[3]);
				} else {
					AlienParts.quad(vc, p, argb, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], nx, ny, nz, uv[0], uv[1], uv[2], uv[3]);
				}
			}
		}
	}

	/** A camera-facing four-point star of light: two crossed thin streaks and a small round bloom. */
	private static void glint(PoseStack pose, MultiBufferSource buffers, float size, int alpha) {
		pose.pushPose();
		pose.mulPose(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW_TEX));
		int argb = (alpha << 24) | ((0xE8 * alpha / 255) << 16) | ((0xFF * alpha / 255) << 8) | (0xF6 * alpha / 255);
		PoseStack.Pose p = pose.last();
		float thin = size * 0.12f;
		pose.mulPose(Axis.ZP.rotation(0.6f));
		p = pose.last();
		AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, -size, -thin, 0, size, -thin, 0, size, thin, 0, -size, thin, 0, 0, 0, 1, 0, 0, 1, 1);
		AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, -thin, -size, 0, thin, -size, 0, thin, size, 0, -thin, size, 0, 0, 0, 1, 0, 0, 1, 1);
		float b = size * 0.35f;
		AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, -b, -b, 0, b, -b, 0, b, b, 0, -b, b, 0, 0, 0, 1, 0, 0, 1, 1);
		pose.popPose();
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// a crystal's profile along its length: {t, radius scale} -- a base, a facet bulge a third of the way up, the long taper
	private static final float[][] CRYSTAL_PROFILE = { { 0f, 0.85f }, { 0.12f, 1.0f }, { 0.38f, 1.12f }, { 0.78f, 0.55f }, { 1f, 0f } };

	/**
	 * A faceted crystal spike: a flattened hexagonal prism from {@code (x, y, z)} pointing up its local -Y (tipped by {@code rx}
	 * then {@code rz}), {@code r} wide ({@code flat} times as deep), {@code len} long, every facet shaded by which way it faces.
	 */
	private static void crystal(PoseStack pose, VertexConsumer vc, float x, float y, float z, float rx, float rz, float r, float flat, float len, int light) {
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		pose.mulPose(Axis.XP.rotation(rx));
		pose.mulPose(Axis.ZP.rotation(rz));
		PoseStack.Pose p = pose.last();
		int n = 6;
		float[][][] ring = new float[CRYSTAL_PROFILE.length][n][3];
		for (int k = 0; k < CRYSTAL_PROFILE.length; k++) {
			float t = CRYSTAL_PROFILE[k][0], rs = CRYSTAL_PROFILE[k][1] * r;
			// each ring a little twisted against the last: the facets read as cut, not lathed
			double twist = k * 0.18;
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + twist;
				ring[k][i][0] = (float) Math.cos(a) * rs / 16f;
				ring[k][i][1] = -t * len / 16f;
				ring[k][i][2] = (float) Math.sin(a) * rs * flat / 16f;
			}
		}
		for (int k = 0; k < ring.length - 1; k++) {
			for (int i = 0; i < n; i++) {
				float[] a = ring[k][i], b = ring[k][(i + 1) % n], c = ring[k + 1][(i + 1) % n], d = ring[k + 1][i];
				// the facet's outward normal (from the lower edge and the side), and its shade from that: light from the top / front
				float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2];
				float fx = d[0] - a[0], fy = d[1] - a[1], fz = d[2] - a[2];
				float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
				float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl < 1e-7f) {
					// the tip ring is one point: take the side edge against the upper edge instead
					fx = c[0] - b[0];
					fy = c[1] - b[1];
					fz = c[2] - b[2];
					nx = ey * fz - ez * fy;
					ny = ez * fx - ex * fz;
					nz = ex * fy - ey * fx;
					nl = Math.max(1e-7f, Mth.sqrt(nx * nx + ny * ny + nz * nz));
				}
				nx /= nl;
				ny /= nl;
				nz /= nl;
				float lit = -ny * 0.5f + nx * 0.35f - nz * 0.15f;
				float[] uv = lit > 0.08f ? PALE : lit > -0.4f ? CRYSTAL : DARK;
				AlienParts.quad(vc, p, 0xFFFFFFFF, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], nx, ny, nz, uv[0], uv[1], uv[2],
						uv[3]);
			}
		}
		pose.popPose();
	}

	/**
	 * The glowing heart of a crystal: the same hexagonal profile as {@link #crystal} (pointing up its local -Y, here straight
	 * along the bone), unshaded and additive, brightness {@code pulse}.
	 */
	private static void crystalCore(PoseStack pose, VertexConsumer vc, float x, float y, float z, float r, float flat, float len, float pulse) {
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		pose.mulPose(Axis.XP.rotation(Mth.PI));
		PoseStack.Pose p = pose.last();
		int n = 6;
		int a = (int) (220 * pulse);
		int argb = (a << 24) | 0xFFFFFF;
		for (int k = 0; k + 1 < CRYSTAL_PROFILE.length; k++) {
			float t0 = CRYSTAL_PROFILE[k][0], r0 = CRYSTAL_PROFILE[k][1] * r, t1 = CRYSTAL_PROFILE[k + 1][0], r1 = CRYSTAL_PROFILE[k + 1][1] * r;
			for (int i = 0; i < n; i++) {
				double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
				float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
				AlienParts.quad(vc, p, argb, LightTexture.FULL_BRIGHT, c0 * r0 / 16f, -t0 * len / 16f, s0 * r0 * flat / 16f, c1 * r0 / 16f, -t0 * len / 16f,
						s1 * r0 * flat / 16f, c1 * r1 / 16f, -t1 * len / 16f, s1 * r1 * flat / 16f, c0 * r1 / 16f, -t1 * len / 16f, s0 * r1 * flat / 16f, c0, 0,
						s0, GLOW[0], GLOW[1], GLOW[2], GLOW[3]);
			}
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- v0.17.2 revamp: state read on the client

	private static DiamondheadKit kit() {
		return Aliens.kit(DiamondheadKit.ID) instanceof DiamondheadKit k ? k : null;
	}

	/** Ticks since the synced pose {@code animId} started, or -1 if another pose is playing. */
	private static float since(AbstractClientPlayer p, int animId, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		return s.animId == animId ? p.level().getGameTime() + partial - s.animStart : -1f;
	}

	private static boolean shieldShown(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & (DiamondheadKit.F_SHIELD | DiamondheadKit.F_BASH)) != 0;
	}

	/** The shield's size now: it grows over the arm in its first 4 ticks. */
	private static float shieldGrow(AbstractClientPlayer p, float partial) {
		float t = since(p, DiamondheadKit.ANIM_SHIELD, partial);
		return t < 0 ? 1f : Mth.clamp(0.25f + t / 4f, 0.25f, 1f);
	}

	/** The blades' size now: 0 without Combat Mode, growing in over 6 ticks, shrinking away over 5 as it ends. */
	private static float bladeGrow(AbstractClientPlayer p, float partial) {
		if ((Omnitrix.state(p).kitFlags & DiamondheadKit.F_COMBAT) == 0) {
			float off = since(p, DiamondheadKit.ANIM_COMBAT_OFF, partial);
			return off >= 0 && off < 5 ? 1f - off / 5f : 0f;
		}
		float on = since(p, DiamondheadKit.ANIM_COMBAT_ON, partial);
		return on < 0 ? 1f : Mth.clamp(on / 6f, 0.05f, 1f);
	}

	private static float shieldHealth(AbstractClientPlayer p) {
		DiamondheadKit k = kit();
		return k == null ? 1f : k.shieldHp(p) / DiamondheadKit.SHIELD_HP;
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		// v0.17.1 (user: "remove the spikes on diamondheads head and shoulders"): no crystal crest, cheek or shoulder shards -- the
		// skin's own head; only the four back crystals (and the blades / shield) are drawn
		// v0.17.1 (user: "he has 4 spikes coming out of his back, 2 longer ones and 2 smaller ones"): two long faceted crystals
		// rising from his shoulder blades in a V past his head, two short chunky ones under them
		AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				crystal(pose, vc, side * 2.1f, 2.6f, 1.6f, -0.42f, side * 0.5f, 1.7f, 1.1f, 17f, light);
				crystal(pose, vc, side * 1.7f, 7.6f, 1.7f, -0.95f, side * 0.75f, 1.35f, 1.0f, 4.6f, light);
			}
		});
		float blades = bladeGrow(p, partial);
		boolean shield = shieldShown(p);
		float grow = shield ? shieldGrow(p, partial) : 0f;
		float hp = shield ? shieldHealth(p) : 1f;
		if (blades > 0f) {
			AlienParts.on(pose, model.rightArm, () -> blade(pose, vc, -1f, blades, light));
			AlienParts.on(pose, model.leftArm, () -> blade(pose, vc, 1f, blades, light));
		}
		if (shield) {
			AlienParts.on(pose, model.leftArm, () -> {
				uprightOnForearm(pose, model.leftArm);
				shield(pose, vc, grow, hp, light);
			});
		}
		// last: the glowing cores fetch another buffer type, which ends the translucent one
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float pulse = 0.75f + 0.25f * Mth.sin(age * 0.2f);
		if (blades > 0f) {
			AlienParts.on(pose, model.rightArm, () -> bladeCore(pose, glow, -1f, blades, pulse));
			AlienParts.on(pose, model.leftArm, () -> bladeCore(pose, glow, 1f, blades, pulse));
		}
		if (shield) {
			AlienParts.on(pose, model.leftArm, () -> {
				uprightOnForearm(pose, model.leftArm);
				shieldCore(pose, glow, grow, hp, pulse);
			});
		}
	}

	/**
	 * From the left arm's bone space to a frame on its forearm, a little in front of it, turned back upright and facing where
	 * the body faces (the arm's own turn undone): the tower shield stands straight whatever the arm swings through.
	 */
	private static void uprightOnForearm(PoseStack pose, ModelPart arm) {
		pose.translate(1f / 16f, 8.5f / 16f, 0f);
		pose.mulPose(Axis.XP.rotation(-arm.xRot));
		pose.mulPose(Axis.YP.rotation(-arm.yRot));
		pose.mulPose(Axis.ZP.rotation(-arm.zRot));
		// out in front of the forearm, turned a touch outward (to his left)
		pose.translate(0.6f / 16f, -0.5f / 16f, -3.4f / 16f);
		pose.mulPose(Axis.YP.rotation(-0.18f));
	}

	// ---------------------------------------------------------------- the blades

	/**
	 * One forearm blade (arm-bone space, pixels; {@code side} -1 the right arm, +1 the left): a long faceted crystal running
	 * down the forearm from just above the wrist and out past the fist, broad front-to-back and thin across, {@code g} grown.
	 */
	private static void blade(PoseStack pose, VertexConsumer vc, float side, float g, int light) {
		pose.pushPose();
		pose.translate(side / 16f, 0f, 0f);
		// the crystal's broad side turned to face out of the arm
		pose.mulPose(Axis.YP.rotation(Mth.HALF_PI));
		crystal(pose, vc, 0f, 4.5f, 0f, Mth.PI, 0f, 2.3f * (0.45f + 0.55f * g), 0.32f, 19f * g, light);
		// a second, shorter spur along the back of the forearm
		crystal(pose, vc, 1.4f, 6.5f, 0f, Mth.PI - 0.32f, 0f, 1.0f * g, 0.4f, 7f * g, light);
		pose.popPose();
	}

	/** The blade's glowing heart (additive). */
	private static void bladeCore(PoseStack pose, VertexConsumer glow, float side, float g, float pulse) {
		pose.pushPose();
		pose.translate(side / 16f, 0f, 0f);
		pose.mulPose(Axis.YP.rotation(Mth.HALF_PI));
		crystalCore(pose, glow, 0f, 5.5f, 0f, 0.7f * g, 0.3f, 16.5f * g, pulse);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the Crystal Shield

	/** The shield's outline (pixels, its own plane: x across, y down), from the top point round clockwise. */
	private static final float[][] RIM = { { 0f, -10.5f }, { 5.5f, -7f }, { 6.2f, 0.5f }, { 4.2f, 7.5f }, { 0f, 10.5f }, { -4.2f, 7.5f }, { -6.2f, 0.5f },
			{ -5.5f, -7f } };
	/** How far each part of the face stands out (-z): the centre boss, the inner ring of facets, the rim; and the back. */
	private static final float BOSS = -2.6f, INNER = -1.8f, EDGE = -0.5f, BACK = 0.6f;
	/** The cracks, worst last: polylines on the face (pixels), each appears as the shield's health drops past a quarter. */
	private static final float[][][] CRACKS = {
			{ { 0.4f, -0.8f, 2.6f, -4.2f }, { 2.6f, -4.2f, 3.3f, -7.4f }, { 2.6f, -4.2f, 4.8f, -3.4f } },
			{ { -0.5f, 0.5f, -3.0f, 2.6f }, { -3.0f, 2.6f, -4.2f, 6.2f }, { -3.0f, 2.6f, -5.4f, 1.4f } },
			{ { 0f, 0f, 1.6f, 4.2f }, { 1.6f, 4.2f, 0.8f, 8.8f }, { 0f, 0f, -2.1f, -3.6f }, { -2.1f, -3.6f, -1.4f, -8.4f }, { 1.6f, 4.2f, 4.0f, 5.2f } } };

	/** The face's depth (-z) at a point of the plane: the boss falling to the inner ring, then to the rim. */
	private static float faceZ(float x, float y) {
		float r = Math.max(Math.abs(x) / 6.2f, Math.abs(y) / 10.5f);
		return r < 0.5f ? Mth.lerp(r / 0.5f, BOSS, INNER) : Mth.lerp(Math.min(1f, (r - 0.5f) / 0.5f), INNER, EDGE);
	}

	/**
	 * The tower shield (its frame: x across, y down, the face toward -z; pixels): a faceted gem face -- a centre boss, a ring
	 * of eight facets round it and eight more out to the rim, each shaded by which way it faces -- a flat back, the edge band,
	 * and the cracks of the damage it has taken. {@code g} grown, {@code hp} 0..1.
	 */
	private static void shield(PoseStack pose, VertexConsumer vc, float g, float hp, int light) {
		pose.pushPose();
		float s = g / 16f;
		pose.scale(s, s, s);
		PoseStack.Pose p = pose.last();
		int n = RIM.length;
		int tint = 0xE8FFFFFF;
		float[] c = { 0f, 0f, BOSS };
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float[] ri = { RIM[i][0], RIM[i][1], EDGE }, rj = { RIM[j][0], RIM[j][1], EDGE };
			float[] ii = { RIM[i][0] * 0.5f, RIM[i][1] * 0.5f, INNER }, ij = { RIM[j][0] * 0.5f, RIM[j][1] * 0.5f, INNER };
			// the inner facet (a triangle from the boss) and the outer one (the band from the inner ring to the rim)
			facet(vc, p, tint, light, c, ii, ij, ij, true);
			facet(vc, p, tint, light, ii, ri, rj, ij, true);
			// the back and the edge band
			float[] bi = { RIM[i][0], RIM[i][1], BACK }, bj = { RIM[j][0], RIM[j][1], BACK };
			float[] bc = { 0f, 0f, BACK };
			AlienParts.quad(vc, p, 0xF0B0C8C0, light, bc[0], bc[1], bc[2], bj[0], bj[1], bj[2], bi[0], bi[1], bi[2], bi[0], bi[1], bi[2], 0, 0, 1, DARK[0], DARK[1],
					DARK[2], DARK[3]);
			float ex = rj[1] - ri[1], ey = -(rj[0] - ri[0]);
			float el = Math.max(1e-5f, Mth.sqrt(ex * ex + ey * ey));
			AlienParts.quad(vc, p, tint, light, ri[0], ri[1], ri[2], rj[0], rj[1], rj[2], bj[0], bj[1], bj[2], bi[0], bi[1], bi[2], ex / el, ey / el, 0,
					CRYSTAL[0], CRYSTAL[1], CRYSTAL[2], CRYSTAL[3]);
		}
		// the cracks, a hair in front of the face
		int shown = hp < 0.25f ? 3 : hp < 0.5f ? 2 : hp < 0.75f ? 1 : 0;
		for (int k = 0; k < shown; k++) {
			for (float[] seg : CRACKS[k]) {
				crack(vc, p, light, seg[0], seg[1], seg[2], seg[3]);
			}
		}
		pose.popPose();
	}

	/** One facet a..d (d may repeat c for a triangle), its normal turned out of the face (-z) and its shade from that. */
	private static void facet(VertexConsumer vc, PoseStack.Pose p, int argb, int light, float[] a, float[] b, float[] c, float[] d, boolean front) {
		float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
		float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
		float nl = Math.max(1e-7f, Mth.sqrt(nx * nx + ny * ny + nz * nz));
		nx /= nl;
		ny /= nl;
		nz /= nl;
		if (front == nz > 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		// lit from above (-y) and ahead (-z), a little from his right
		float lit = -ny * 0.55f - nz * 0.35f - nx * 0.25f;
		float[] uv = lit > 0.42f ? PALE : lit > 0.18f ? CRYSTAL : DARK;
		AlienParts.quad(vc, p, argb, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], nx, ny, nz, uv[0], uv[1], uv[2], uv[3]);
	}

	/** A crack from (x0, y0) to (x1, y1) on the face: a thin dark sliver following the facets' depth. */
	private static void crack(VertexConsumer vc, PoseStack.Pose p, int light, float x0, float y0, float x1, float y1) {
		float dx = x1 - x0, dy = y1 - y0;
		float l = Math.max(1e-5f, Mth.sqrt(dx * dx + dy * dy));
		float w = 0.28f;
		float ox = -dy / l * w, oy = dx / l * w;
		float z0 = faceZ(x0, y0) - 0.12f, z1 = faceZ(x1, y1) - 0.12f;
		AlienParts.quad(vc, p, 0xFF263A33, light, x0 - ox, y0 - oy, z0, x1 - ox, y1 - oy, z1, x1 + ox, y1 + oy, z1, x0 + ox, y0 + oy, z0, 0, 0, -1, DARK[0],
				DARK[1], DARK[2], DARK[3]);
	}

	/** The glowing heart behind the boss, and a faint light along the cracks (they let the core's light out). */
	private static void shieldCore(PoseStack pose, VertexConsumer glow, float g, float hp, float pulse) {
		pose.pushPose();
		float s = g / 16f;
		pose.scale(s, s, s);
		PoseStack.Pose p = pose.last();
		int a = (int) (200 * pulse * (0.55f + 0.45f * hp));
		int argb = (a << 24) | 0xFFFFFF;
		float z = BOSS + 0.25f;
		AlienParts.quad(glow, p, argb, LightTexture.FULL_BRIGHT, 0f, -3.2f, z, 2.2f, 0f, z, 0f, 3.2f, z, -2.2f, 0f, z, 0, 0, -1, GLOW[0], GLOW[1], GLOW[2], GLOW[3]);
		pose.popPose();
	}

	// ---------------------------------------------------------------- first person

	@Override
	public boolean bothHandsInFirstPerson(AbstractClientPlayer p) {
		return shieldShown(p) || bladeGrow(p, partial()) > 0f;
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		float partial = partial();
		float blades = bladeGrow(p, partial);
		boolean shield = !right && shieldShown(p);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		float side = right ? -1f : 1f;
		if (blades > 0f) {
			blade(pose, vc, side, blades, light);
		}
		float grow = shield ? shieldGrow(p, partial) : 0f;
		float hp = shield ? shieldHealth(p) : 1f;
		if (shield) {
			pose.pushPose();
			firstPersonShield(pose);
			shield(pose, vc, grow, hp, light);
			pose.popPose();
		}
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float pulse = 0.75f + 0.25f * Mth.sin((p.tickCount + partial) * 0.2f);
		if (blades > 0f) {
			bladeCore(pose, glow, side, blades, pulse);
		}
		if (shield) {
			pose.pushPose();
			firstPersonShield(pose);
			shieldCore(pose, glow, grow, hp, pulse);
			pose.popPose();
		}
	}

	/**
	 * The shield held up on the left of the view: placed from the hand's starting pose (camera space with vanilla's bob and
	 * sway, {@code OmnitrixFirstPerson.HAND_BASE}) rather than the arm, so it stands upright facing the eye, its face turned a
	 * little toward the centre.
	 */
	private static void firstPersonShield(PoseStack pose) {
		pose.last().pose().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE);
		pose.last().normal().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE_NORMAL);
		pose.translate(-0.42f, -0.36f, -0.78f);
		pose.mulPose(Axis.YP.rotationDegrees(22f));
		// the shield's frame (y down, face toward -z) turned to the camera's (y up, the eye at +z)
		pose.mulPose(Axis.XP.rotation(Mth.PI));
		pose.scale(0.62f, 0.62f, 0.62f);
	}

	// ---------------------------------------------------------------- poses

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case DiamondheadKit.ANIM_VOLLEY -> VOLLEY;
			case DiamondheadKit.ANIM_SPEAR, DiamondheadKit.ANIM_BEAM -> SPEAR;
			case DiamondheadKit.ANIM_COMBAT_ON -> COMBAT_ON;
			case DiamondheadKit.ANIM_COMBAT_OFF -> COMBAT_OFF;
			case DiamondheadKit.ANIM_SHATTER -> SHATTER;
			case DiamondheadKit.ANIM_SLIDE -> SLIDE;
			case DiamondheadKit.ANIM_VAULT -> VAULT;
			case DiamondheadKit.ANIM_WALL -> WALL;
			case DiamondheadKit.ANIM_BARRAGE -> BARRAGE;
			case DiamondheadKit.ANIM_PRISON -> PRISON;
			case DiamondheadKit.ANIM_SLASH -> SLASH;
			case DiamondheadKit.ANIM_PLACE -> PLACE;
			case DiamondheadKit.ANIM_ERUPT -> ERUPT;
			case DiamondheadKit.ANIM_SHIELD -> SHIELD;
			case DiamondheadKit.ANIM_BASH -> BASH;
			case DiamondheadKit.ANIM_FLURRY_1 -> FLURRY_1;
			case DiamondheadKit.ANIM_FLURRY_2 -> FLURRY_2;
			case DiamondheadKit.ANIM_STAB -> STAB;
			case DiamondheadKit.ANIM_CYCLONE -> CYCLONE;
			case DiamondheadKit.ANIM_SHIELD_BREAK -> SHIELD_BREAK;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		int flags = Omnitrix.state(p).kitFlags;
		if (!played && (flags & DiamondheadKit.F_SLIDING) != 0) {
			// v0.17.2: the surfer's crouch held for the whole ride
			return QuirkKeyframes.play(m, SLIDE, 10f, 1f);
		}
		if ((flags & DiamondheadKit.F_SHIELD) != 0 && animId != DiamondheadKit.ANIM_BASH) {
			// the shield held up across the front: the left forearm raised and turned in (whatever else his right arm does)
			float k = animId == DiamondheadKit.ANIM_SHIELD ? Mth.clamp(ticks / 3f, 0f, 1f) : 1f;
			m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, -1.1f);
			m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.55f);
			m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, 0f);
			played = true;
		}
		if (!played && (flags & DiamondheadKit.F_COMBAT) != 0) {
			// a fighter's guard: both blades held a little forward and out from the body
			m.rightArm.xRot = m.rightArm.xRot * 0.4f - 0.55f;
			m.rightArm.zRot += 0.22f;
			m.leftArm.xRot = m.leftArm.xRot * 0.4f - 0.45f;
			m.leftArm.zRot -= 0.22f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case DiamondheadKit.ANIM_VOLLEY, DiamondheadKit.ANIM_BARRAGE -> FP_VOLLEY;
			case DiamondheadKit.ANIM_SPEAR, DiamondheadKit.ANIM_BEAM -> FP_SPEAR;
			case DiamondheadKit.ANIM_COMBAT_ON -> FP_COMBAT_ON;
			case DiamondheadKit.ANIM_COMBAT_OFF -> FP_COMBAT_OFF;
			case DiamondheadKit.ANIM_SHATTER -> FP_SLAM;
			case DiamondheadKit.ANIM_WALL -> FP_PUSH;
			case DiamondheadKit.ANIM_VAULT -> FP_RAISE;
			case DiamondheadKit.ANIM_SLIDE -> FP_SURF;
			case DiamondheadKit.ANIM_SLASH -> FP_SLASH;
			case DiamondheadKit.ANIM_PLACE -> FP_PLACE;
			case DiamondheadKit.ANIM_PRISON -> FP_FLEX;
			case DiamondheadKit.ANIM_ERUPT -> FP_ERUPT;
			case DiamondheadKit.ANIM_BASH -> FP_BASH;
			case DiamondheadKit.ANIM_FLURRY_1 -> FP_FLURRY_1;
			case DiamondheadKit.ANIM_FLURRY_2 -> FP_FLURRY_2;
			case DiamondheadKit.ANIM_STAB -> FP_STAB;
			case DiamondheadKit.ANIM_CYCLONE -> FP_CYCLONE;
			case DiamondheadKit.ANIM_SHIELD_BREAK -> FP_SHIELD_BREAK;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- HUD: the shield meter

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		DiamondheadKit k = kit();
		if (k == null) {
			return;
		}
		boolean broken = k.shieldBroken(p);
		float hp = k.shieldHp(p);
		if (!broken && hp >= DiamondheadKit.SHIELD_HP && !shieldShown(p)) {
			return; // full and lowered: nothing to show
		}
		float frac;
		String value;
		int colour;
		if (broken) {
			int left = k.cooldownLeft(p, DiamondheadKit.SHIELD);
			frac = 1f - left / (float) DiamondheadKit.SHIELD_BROKEN_TICKS;
			value = String.format(java.util.Locale.ROOT, "%.0fs", Math.ceil(left / 20f));
			colour = 0xFF7A8C88;
		} else {
			frac = hp / DiamondheadKit.SHIELD_HP;
			value = String.valueOf(Math.round(hp));
			colour = 0xFF000000 | DiamondheadKit.COLOUR;
		}
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6,
				net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.diamondhead.shield"), frac, colour, value, 0xFFFFFFFF);
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	/** The Crystal Slide's eased ride velocity (CryokinesisClient.ride). */
	private static final double[] RIDE = { Double.NaN, 0 };

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		if (r.nextInt(6) == 0) {
			p.level().addParticle(ParticleTypes.WAX_OFF, p.getX() + (r.nextDouble() - 0.5) * 0.8, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if (p instanceof net.minecraft.client.player.LocalPlayer me) {
			// v0.17.2 (user: "work similar to cryokinesis ice slide"): the Ice Slide's ride, on his own client
			com.projecthero.mod.client.mutation.CryokinesisClient.ride(me, (flags & DiamondheadKit.F_SLIDING) != 0, RIDE);
		}
		if ((flags & DiamondheadKit.F_SLIDING) != 0 && p.onGround()) {
			p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, DiamondheadBlocks.CRYSTAL.defaultBlockState()), p.getX(), p.getY() + 0.1,
					p.getZ(), 0, 0.1, 0);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), high on his left breast
		// v0.17.2 (user: "its not covering the badge thats on the skin and also make the badge slightly bigger"): the symbol is
		// jacket texels 25..26 (the paint-out had cleared 26..27, a texel to its right), so 1 px in; 3 px across (was 2.4)
		return new float[] { 2.0f, 2.0f, -2.06f, 3.0f };
	}
}
