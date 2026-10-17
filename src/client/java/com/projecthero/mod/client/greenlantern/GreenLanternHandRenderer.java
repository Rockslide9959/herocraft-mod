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
 *   suit's gauntlet and follows slim (Alex) arms. v0.16.6: a real ring from {@link HandRing} (octagonal band, square
 *   bezel, the raised Lantern emblem), drawn opaque, with the halo now a thin sheet of light over the emblem.</li>
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
		// v0.15.15: 0.55 px shell + the usual lift (0.63)
		ring(pose, buffers, player, suited == 1 ? 0.63f : HandRing.SKIN_GAP, ageInTicks);
		com.projecthero.mod.greenlantern.data.GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX,
				com.projecthero.mod.greenlantern.data.GreenLanternFx.EMPTY);
		if (fx.has(com.projecthero.mod.greenlantern.data.GreenLanternFx.CH_GATLING)) {
			gatling(pose, buffers, slim == 1 ? -0.5f : -1.0f, ageInTicks);
		}

		int hand = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, 0);
		if ((hand & GreenLanternConstructs.HAND_SNIPER) != 0) {
			sniper(pose, buffers, player, fx, slim == 1 ? -0.5f : -1.0f, ageInTicks);
		}
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
		if (gap > 0.3f || (HandRing.firstPerson && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof GreenLanternArmorItem)) {
			// v0.15.15: the suit's gauntlet (3rd person) and first-person sleeve are always the wide 4 px arm, even over a
			// slim skin -- place the ring for a wide arm so it sits on the glove instead of inside it
			HandRing.slimArm = false;
		}
		// v0.16.6: the ring itself goes in HandRing's opaque buffer, then the translucent halo (re-fetched after it)
		HandRing.draw(pose, HandRing.buffer(buffers), gap, forming > 0.05f ? BLAZING : HandRing.GREEN_LANTERN);
		vc = HardLightDraw.buffer(buffers);
		// the halo: breathes softly, flares while the ring is working
		float[] g = HandRing.gemCentre(gap);
		// v0.16.6: a thin sheet of light lying over the emblem (and a wider, fainter one), not a glass cube round the whole
		// ring -- the old 1.4 px box read as "a block on the hand"
		float halo = busy ? 0.95f : 0.68f + 0.04f * breathe;
		float haloAlpha = busy ? 0.42f : 0.14f + 0.1f * breathe;
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		HandRing.place(pose, HandRing.GREEN_LANTERN, gap);
		HandRing.box(vc, pose, g[0], g[1], g[2] - 0.06f, halo, halo, 0.05f, 0x5CFF8E, haloAlpha);
		HandRing.box(vc, pose, g[0], g[1], g[2] - 0.12f, halo * 1.3f, halo * 1.3f, 0.03f, 0x35F075, haloAlpha * 0.35f);
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

	/**
	 * v0.16.1: the Sniper Rifle -- a long hard-light rifle built round the ring fist, in the arm's pixel space (y runs down
	 * the arm, so the barrel runs on past the knuckles; -z is the top of the arm once it is raised to aim, +z the
	 * underside). A stock back along the underside of the forearm, the grip in the fist, a receiver and magazine, a long
	 * shrouded barrel with a muzzle brake, and a scope on top. The energy cells along the barrel and the muzzle brighten as
	 * a shot charges ({@link GreenLanternFx#ANIM_SNIPER_CHARGE}); a shot kicks the whole rifle back.
	 */
	/** v0.16.1 first-person rifle placement (degrees / view-space blocks from the fist). Tuned on screenshots. */
	public static float FP_SNIPER_YAW = 3f, FP_SNIPER_PITCH = 2f, FP_SNIPER_X = -0.06f, FP_SNIPER_Y = 0.1f, FP_SNIPER_Z = 0.0f,
			FP_SNIPER_SCALE = 0.85f;

	private static void sniper(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player,
			com.projecthero.mod.greenlantern.data.GreenLanternFx fx, float cx, float age) {
		com.mojang.blaze3d.vertex.VertexConsumer vc = HardLightDraw.buffer(buffers);
		long now = player.level().getGameTime();
		float partial = age - (float) Math.floor(age);
		float since = now - fx.animStart() + partial;
		float charge = 0f;
		if (fx.anim() == com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_SNIPER_CHARGE) {
			charge = Math.min(1f, since / com.projecthero.mod.greenlantern.GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS);
		}
		float kick = 0f;
		if (fx.anim() == com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_SNIPER_SHOT && since < 6f) {
			kick = 1f - since / 6f;
		}
		boolean firstPerson = HandRing.firstPerson && player == net.minecraft.client.Minecraft.getInstance().player;
		if (firstPerson && com.projecthero.mod.client.greenlantern.GreenLanternSniperClient.scopedFirstPerson()) {
			return; // looking down the scope: the overlay is the rifle
		}
		pose.pushPose();
		if (firstPerson) {
			// first person: vanilla's empty-hand pose points the arm up and across the screen, which would stand the rifle
			// on end -- keep the fist where it is but level the rifle along the view (barrel forward, scope up), turned a
			// touch towards the crosshair
			org.joml.Vector3f fist = pose.last().pose().transformPosition(new org.joml.Vector3f(cx / 16f, 10f / 16f, 0f));
			org.joml.Matrix3f rot = new org.joml.Matrix3f(
					-1f, 0f, 0f,   // column 0: local x -> view -x
					0f, 0f, -1f,   // column 1: local y (down the arm) -> view forward (-z)
					0f, -1f, 0f);  // column 2: local z (underside) -> view down
			rot.rotateLocal(Axis.YP.rotationDegrees(FP_SNIPER_YAW));
			rot.rotateLocal(Axis.XP.rotationDegrees(FP_SNIPER_PITCH));
			// the first-person hand pass looks down +z here (the stack is turned half round), so flip into it
			rot.rotateLocal(Axis.YP.rotationDegrees(180f));
			pose.last().pose().identity().translate(fist.x - FP_SNIPER_X, fist.y + FP_SNIPER_Y, fist.z - FP_SNIPER_Z)
					.mul(new org.joml.Matrix4f(rot));
			pose.last().normal().set(rot);
			pose.scale(FP_SNIPER_SCALE / 16f, FP_SNIPER_SCALE / 16f, FP_SNIPER_SCALE / 16f);
			pose.translate(0f, -1.6f * kick - 10f, 0f);
		} else {
			pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
			pose.translate(cx, -1.6f * kick, 0f);
		}
		// stock along the underside of the forearm, with a butt pad at the elbow
		if (!firstPerson) { // in first person it would run back into the camera
			HardLightDraw.part(vc, pose, 0f, 4.6f, 2.7f, 0.7f, 3.8f, 0.8f, 0.85f);
			HardLightDraw.part(vc, pose, 0f, 0.9f, 2.9f, 0.9f, 0.8f, 1.3f, 0.85f);
		}
		// grip in the fist, receiver, magazine underneath
		HardLightDraw.part(vc, pose, 0f, 9.6f, 0.9f, 0.8f, 1.2f, 1.3f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, 13.2f, 0f, 1.2f, 3.0f, 1.45f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, 12.4f, 2.3f, 0.6f, 1.1f, 0.85f, 0.85f);
		// barrel shroud, barrel, muzzle brake
		HardLightDraw.part(vc, pose, 0f, 19.4f, 0f, 0.8f, 3.2f, 0.8f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, 27.0f, 0f, 0.42f, 4.6f, 0.42f, 0.95f);
		HardLightDraw.part(vc, pose, 0f, 32.0f, 0f, 0.72f, 0.7f, 0.72f, 0.95f);
		// scope on top: tube, eyepiece and objective bells, two mounts, a glowing lens
		HardLightDraw.part(vc, pose, 0f, 13.6f, -2.75f, 0.62f, 3.6f, 0.62f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, 10.0f, -2.75f, 0.85f, 0.5f, 0.85f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, 17.4f, -2.75f, 0.95f, 0.6f, 0.95f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, 11.8f, -1.7f, 0.3f, 0.35f, 0.5f, 0.8f);
		HardLightDraw.part(vc, pose, 0f, 15.6f, -1.7f, 0.3f, 0.35f, 0.5f, 0.8f);
		HardLightDraw.glow(vc, pose, 0f, 18.05f, -2.75f, 0.6f, 0.08f, 0.6f, 0.8f);
		// energy cells along the shroud and the muzzle: dim at rest, blazing as the shot charges
		float breathe = 0.5f + 0.5f * net.minecraft.util.Mth.sin(age * 0.2f);
		for (int i = 0; i < 3; i++) {
			HardLightDraw.glow(vc, pose, 0f, 17.4f + i * 2.0f, 0f, 0.45f, 0.22f, 0.86f, 0.25f + 0.15f * breathe + 0.6f * charge);
		}
		if (charge > 0f || kick > 0f) {
			float s = 0.35f + 0.6f * Math.max(charge, kick);
			HardLightDraw.glow(vc, pose, 0f, 33.2f, 0f, s, s, s, Math.max(charge, kick));
		}
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
