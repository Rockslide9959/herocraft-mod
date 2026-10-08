package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.render.HandRing;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.item.GreenLanternArmorItem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.13.21: everything a Green Lantern wears or shapes on the right hand, drawn in both views -- third person through
 * {@code PowerRingLayer}, first person through {@code GreenLanternRingHandMixin} (the arm the player sees).
 * <ul>
 *   <li><b>The Power Ring</b>: v0.14.3 redesign (still finger-sized, per v0.14.1's "not a bracelet"): a slim band round
 *   the ring finger at the front / outer corner of the knuckles, a dark raised bezel, a white-hot gem set in it and a
 *   soft halo of green light that breathes -- and flares whenever the ring is used. It sits a touch prouder over the
 *   suit's gauntlet and follows slim (Alex) arms.</li>
 *   <li><b>The Emerald Gatling</b> (v0.14.3, Shift+X held): six spinning barrels of light built onto the fist.</li>
 *   <li><b>The Energy Blade</b> and <b>Mining Drill</b> while switched on (see
 *   {@link ModAttachments#GREEN_LANTERN_HAND_CONSTRUCTS}): a translucent full-bright blade out of the fist, or a
 *   stepped spinning drill bit -- the constructs used to be nothing but a few particles.</li>
 * </ul>
 * Geometry is in the arm bone's own pixel space (y runs down the arm, the fist ends at y = 10), built once in code
 * with {@link LayerDefinition#bakeRoot()} -- no model-layer registration needed.
 */
public final class GreenLanternHandRenderer {
	private static final ResourceLocation LIGHT_TEXTURE = ProjectHeroMod.id("textures/entity/green_lantern/hard_light_construct.png");

	private static final ModelPart[] BLADES = new ModelPart[2];
	private static final ModelPart[][] DRILL_TIERS = new ModelPart[2][];
	private static boolean baked;

	private GreenLanternHandRenderer() {
	}

	/** Anything to draw on this player's right hand at all? */
	public static boolean hasAnything(AbstractClientPlayer player) {
		return GreenLantern.hasPower(player) && !player.isInvisible();
	}

	/**
	 * Draws the ring (+ any switched-on hand construct) on {@code arm}, which must be the right arm, posed exactly as it
	 * was just drawn. {@code pose} is the stack the arm itself was rendered with (not yet transformed into the arm).
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm,
			float ageInTicks) {
		if (!hasAnything(player)) {
			return;
		}
		bake();
		int slim = player.getSkin().model() == PlayerSkin.Model.SLIM ? 1 : 0;
		int suited = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof GreenLanternArmorItem ? 1 : 0;
		// v0.15.15: John Stewart's suit leaves the hands bare, so the ring sits on the skin
		com.projecthero.mod.greenlantern.data.GreenLanternState st = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (st != null && st.suitStyle == com.projecthero.mod.greenlantern.GreenLanternSuitStyle.STEWART.ordinal()) {
			suited = 0;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the suit's gauntlet is always the wide 4 px arm (0.55 px proud of it), even over a slim skin
		ring(pose, buffers, player, suited == 1 ? 0.62f : 0.08f, ageInTicks);
		com.projecthero.mod.greenlantern.data.GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX,
				com.projecthero.mod.greenlantern.data.GreenLanternFx.EMPTY);
		if (fx.has(com.projecthero.mod.greenlantern.data.GreenLanternFx.CH_GATLING)) {
			gatling(pose, buffers, slim == 1 ? -0.5f : -1.0f, ageInTicks);
		}

		int hand = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, 0);
		if ((hand & GreenLanternConstructs.HAND_BLADE) != 0) {
			BLADES[slim].render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHT_TEXTURE)),
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xDDFFFFFF);
		}
		if ((hand & GreenLanternConstructs.HAND_DRILL) != 0) {
			float cx = slim == 1 ? -0.5f / 16f : -1.0f / 16f;
			for (int i = 0; i < DRILL_TIERS[slim].length; i++) {
				pose.pushPose();
				pose.translate(cx, 0.0f, 0.0f);
				// alternate tiers counter-rotate a little so the bit reads as a twisting flute while it spins
				pose.mulPose(Axis.YP.rotationDegrees(ageInTicks * 36.0f + i * 22.5f));
				pose.translate(-cx, 0.0f, 0.0f);
				DRILL_TIERS[slim][i].render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(LIGHT_TEXTURE)),
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xDDFFFFFF);
				pose.popPose();
			}
		}
		pose.popPose();
	}

	private static void bake() {
		if (baked) {
			return;
		}
		baked = true;
		for (int slim = 0; slim < 2; slim++) {
			float xMin = slim == 1 ? -2f : -3f;
			float xMax = 1f;
			float cx = (xMin + xMax) / 2f;
			BLADES[slim] = blade(cx, xMin, xMax);
			DRILL_TIERS[slim] = drill(cx);
		}
	}

	/**
	 * The ring, drawn in the arm's pixel space. v0.14.13: the band / bezel / gem come from the shared {@link HandRing}
	 * (the Flash Ring uses the very same finger); the halo of green light stays the Lantern's own. {@code gap} lifts
	 * everything off the suit's gauntlet.
	 */
	private static void ring(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, float gap, float age) {
		com.mojang.blaze3d.vertex.VertexConsumer vc = HardLightDraw.buffer(buffers);
		com.projecthero.mod.greenlantern.data.GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX,
				com.projecthero.mod.greenlantern.data.GreenLanternFx.EMPTY);
		long now = player.level().getGameTime();
		boolean busy = fx.channels() != 0 || (fx.anim() != 0 && now - fx.animStart() < 12);
		float breathe = 0.5f + 0.5f * net.minecraft.util.Mth.sin(age * 0.12f);
		// v0.15.15: the ring blazes while the suit pours out of it (or back into it)
		float forming = GreenLanternSuitReveal.ringGlow(player, age - (float) Math.floor(age));
		HandRing.arm(player);
		HandRing.draw(pose, vc, gap, forming > 0.05f ? BLAZING : HandRing.GREEN_LANTERN);
		// the halo: breathes softly, flares while the ring is working
		float[] g = HandRing.gemCentre(gap);
		float halo = busy ? 1.1f : 0.7f + 0.1f * breathe;
		float haloAlpha = busy ? 0.4f : 0.1f + 0.1f * breathe;
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		HandRing.place(pose, HandRing.GREEN_LANTERN, gap);
		HandRing.box(vc, pose, g[0], g[1], g[2], halo, halo, halo * 0.6f, 0x35F075, haloAlpha);
		HandRing.box(vc, pose, g[0], g[1], g[2], halo * 1.35f, halo * 1.35f, halo * 0.8f, 0x35F075, haloAlpha * 0.3f);
		if (forming > 0f) {
			// a white-hot core, a big pulsing green glow round the whole fist, and rings of light pulsing out of it
			float pulse = 0.5f + 0.5f * net.minecraft.util.Mth.sin(age * 1.3f);
			HandRing.box(vc, pose, g[0], g[1], g[2], 0.45f, 0.45f, 0.35f, 0xF4FFF6, 0.95f * forming);
			pose.pushPose();
			pose.translate(g[0], g[1], g[2]);
			// additive (vanilla's lightning buffer), so the glow brightens whatever is behind it instead of tinting it dark
			com.mojang.blaze3d.vertex.VertexConsumer add = buffers.getBuffer(RenderType.lightning());
			com.projecthero.mod.client.maxsteel.TurboDraw.sphere(add, pose, 0.9f + 0.15f * pulse, 0xB8FFCC, (0.8f + 0.2f * pulse) * forming);
			com.projecthero.mod.client.maxsteel.TurboDraw.sphere(add, pose, 1.5f + 0.25f * pulse, 0x35F075, (0.45f + 0.15f * pulse) * forming);
			// re-fetch: asking the buffer source for another render type can end the batch the old consumer belonged to
			vc = HardLightDraw.buffer(buffers);
			com.projecthero.mod.client.maxsteel.TurboDraw.sphere(vc, pose, 1.0f + 0.15f * pulse, 0x9CFFB8, (0.35f + 0.15f * pulse) * forming);
			com.projecthero.mod.client.maxsteel.TurboDraw.sphere(vc, pose, 1.9f + 0.3f * pulse, 0x35F075, (0.18f + 0.08f * pulse) * forming);
			// a ring of light pulsing out of the gem every 8 ticks
			float wave = (age % 8f) / 8f;
			com.projecthero.mod.client.maxsteel.TurboDraw.sphere(vc, pose, 1.2f + wave * 2.6f, 0x9CFFB8, 0.18f * (1f - wave) * forming);
			pose.popPose();
		}
		pose.popPose();
	}

	/** v0.15.15: the ring while it is forming the suit -- band and bezel lit up bright green. */
	private static final HandRing.Palette BLAZING = new HandRing.Palette(HandRing.GREEN_LANTERN.id(), 0x5CFF8E, 0xE6FFEC, 0x2FD86A,
			0xFFFFFF, 0xB8FFCC);

	/** v0.14.3: the Emerald Gatling -- a housing round the fist and six spinning barrels running on past the knuckles. */
	private static void gatling(PoseStack pose, MultiBufferSource buffers, float cx, float age) {
		com.mojang.blaze3d.vertex.VertexConsumer vc = HardLightDraw.buffer(buffers);
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		pose.translate(cx, 0f, 0f);
		HardLightDraw.part(vc, pose, 0f, 10.6f, 0f, 2.6f, 1.0f, 2.6f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, 12.2f, 0f, 1.9f, 0.6f, 1.9f, 0.9f);
		pose.mulPose(Axis.YP.rotationDegrees(age * 42f));
		for (int i = 0; i < 6; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 60f));
			HardLightDraw.part(vc, pose, 1.15f, 16.0f, 0f, 0.38f, 4.2f, 0.38f, 0.95f);
			pose.popPose();
		}
		HardLightDraw.part(vc, pose, 0f, 19.6f, 0f, 1.75f, 0.35f, 1.75f, 0.9f);
		HardLightDraw.glow(vc, pose, 0f, 20.2f, 0f, 0.5f, 0.2f, 0.5f, 0.9f);
		pose.popPose();
	}

	/** A broad, flat blade of light out of the fist with a small crossguard, 14 px (almost a block) long. */
	private static ModelPart blade(float cx, float xMin, float xMax) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 0)
				.addBox(xMin - 0.6f, 9.6f, -2.6f, xMax - xMin + 1.2f, 0.8f, 5.2f)   // crossguard over the fist
				.addBox(cx - 0.4f, 10.4f, -1.5f, 0.8f, 12.0f, 3.0f)                  // blade
				.addBox(cx - 0.3f, 22.4f, -1.0f, 0.6f, 2.0f, 2.0f)                   // tapering tip
				.addBox(cx - 0.2f, 24.4f, -0.45f, 0.4f, 1.4f, 0.9f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32).bakeRoot();
	}

	/** Four stepped tiers narrowing to a point, each drawn separately so they can twist against each other. */
	private static ModelPart[] drill(float cx) {
		float[][] tiers = {{3.8f, 10.0f, 2.2f}, {2.8f, 12.2f, 2.2f}, {1.8f, 14.4f, 2.2f}, {0.8f, 16.6f, 1.8f}};
		ModelPart[] parts = new ModelPart[tiers.length];
		for (int i = 0; i < tiers.length; i++) {
			float w = tiers[i][0];
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("tier", CubeListBuilder.create().texOffs(0, 0)
					.addBox(cx - w / 2f, tiers[i][1], -w / 2f, w, tiers[i][2], w), PartPose.ZERO);
			parts[i] = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		return parts;
	}
}
