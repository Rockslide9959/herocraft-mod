package com.projecthero.mod.client.omnitrix.alien.alienx;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.alien.AlienXClient;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncil;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Celestial Council (user: "the player gets transported to that dimension where he talks to the two heads"): the council
 * drawn on the client, only while he is in {@code projecthero:celestial_council} -- two enormous floating heads in his Alien X skin
 * ({@link #HEAD_SCALE} times a player's head, horns and all), Serena tinted a soft blue with pale eyes and a halo, Bellicus tinted
 * red with burning eyes and hard, angry brows. They drift, turn to look at him, and the one speaking (synced:
 * {@code AlienXKit.speaker}) nods, its eyes flare and rings of its colour ripple out from it. Under him a faint ring of light marks
 * the invisible floor, and far nebulae glow in the dark among the vanilla night sky's stars. Additive glows, ribbons and bands
 * ({@link PyroDraw}): no particles.
 */
public final class AlienXFx {
	public static final float HEAD_SCALE = 14f;
	private static final int SERENA_TINT = 0xFFC4E2FF, BELLICUS_TINT = 0xFFFFB4A8;
	private static final int SERENA_EYE = 0xFFBFF4FF, BELLICUS_EYE = 0xFFFF6A30, VIOLET = 0xFF8A4DFF, STAR_BLUE = 0xFF9FC4FF, NEBULA = 0xFF3A2A9A;
	private static boolean registered;
	private static PlayerModel<AbstractClientPlayer> model;

	private AlienXFx() {
	}

	public static void init() {
		if (!registered) {
			registered = true;
			WorldRenderEvents.AFTER_ENTITIES.register(AlienXFx::render);
		}
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || context.consumers() == null || context.matrixStack() == null
				|| mc.level.dimension() != AlienXCouncil.DIMENSION) {
			return;
		}
		try {
			council(context, mc);
		} catch (RuntimeException e) {
			// a draw must never take the frame down
		}
	}

	private static void council(WorldRenderContext context, Minecraft mc) {
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		BASE.set(pose.last().pose());
		MultiBufferSource buffers = context.consumers();
		long now = mc.level.getGameTime();
		float time = now + partial;
		Vec3 o = AlienXKit.pos(mc.player, AlienXKit.P_COUNCIL);
		if (o == null) {
			Vec3 p = mc.player.position();
			o = new Vec3(Math.round(p.x / AlienXCouncil.SLOT_SPACING) * (double) AlienXCouncil.SLOT_SPACING, AlienXCouncil.FLOOR_Y, 0);
		}
		Vec3 centre = o.add(0.5, 0, 0.5);
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		// the floor: a faint ring of light, a slowly turning band
		pose.pushPose();
		Vec3 f = centre.add(0, 1.02, 0).subtract(cam);
		pose.translate(f.x, f.y, f.z);
		PyroDraw.annulus(pose, add, 3.6f, 4.4f, PyroDraw.dim(STAR_BLUE, 0.35f), PyroDraw.dim(NEBULA, 0.05f), 0f);
		PyroDraw.annulus(pose, add, 0f, 3.6f, PyroDraw.dim(NEBULA, 0.10f), PyroDraw.dim(NEBULA, 0.22f), 0f);
		pose.mulPose(Axis.YP.rotation(time * 0.01f));
		PyroDraw.band(pose, add, 4.0f, 0.03f, PyroDraw.dim(VIOLET, 0.6f), 0xFF000000);
		pose.popPose();
		// far nebulae
		float[][] neb = { { -60, 30, -90, 26, 0 }, { 70, 10, -110, 32, 1 }, { 10, 60, -140, 40, 2 }, { -90, -20, 40, 30, 1 }, { 80, 40, 60, 28, 0 } };
		for (float[] n : neb) {
			int col = n[4] == 0 ? VIOLET : n[4] == 1 ? STAR_BLUE : NEBULA;
			PyroDraw.glow(pose, glow, centre.add(n[0], n[1], n[2]).subtract(cam), n[3], PyroDraw.dim(col, 0.18f));
		}
		int speaker = AlienXKit.speaker(mc.player);
		float since = now - AlienXKit.speakAt(mc.player) + partial;
		Vec3 eye = mc.player.getEyePosition(partial);
		head(pose, buffers, add, glow, cam, centre.add(AlienXCouncil.SERENA_HEAD), eye, time, false,
				speaker == AlienXCouncil.WHO_SERENA && since >= 0 && since < 50 ? since : -1f);
		head(pose, buffers, add, glow, cam, centre.add(AlienXCouncil.BELLICUS_HEAD), eye, time, true,
				speaker == AlienXCouncil.WHO_BELLICUS && since >= 0 && since < 50 ? since : -1f);
	}

	private static PlayerModel<AbstractClientPlayer> model() {
		if (model == null) {
			model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		}
		return model;
	}

	/** One head: {@code talking} = ticks since it began to speak, or -1. */
	private static void head(PoseStack pose, MultiBufferSource buffers, VertexConsumer add, VertexConsumer glow, Vec3 cam, Vec3 at, Vec3 look, float time,
			boolean bellicus, float talking) {
		float phase = bellicus ? 1.7f : 0f;
		Vec3 c = at.add(0, Mth.sin(time * 0.03f + phase) * 0.6, 0);
		Vec3 d = look.subtract(c);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		float S = HEAD_SCALE;
		float talk = talking >= 0 ? Mth.clamp(1f - talking / 50f, 0f, 1f) : 0f;
		ModelPart head = model().head;
		ModelPart hat = model().hat;
		head.resetPose();
		head.setPos(0, 0, 0);
		head.xRot = pitch * Mth.DEG_TO_RAD + (talking >= 0 ? 0.07f * Mth.sin(talking * 0.9f) * talk : 0f);
		head.yRot = 0f;
		head.zRot = (bellicus ? -0.06f : 0.06f) + 0.03f * Mth.sin(time * 0.02f + phase);
		hat.copyFrom(head);
		// the aura behind
		PyroDraw.glow(pose, glow, c.subtract(cam), S * 0.75f, PyroDraw.dim(bellicus ? 0xFF8A1A10 : 0xFF1A3A8A, 0.5f + 0.3f * talk));
		pose.pushPose();
		Vec3 r = c.subtract(cam);
		pose.translate(r.x, r.y, r.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		pose.scale(-S, -S, S);
		pose.translate(0, 0.25, 0);
		Matrix m = new Matrix(pose, head);
		int tint = bellicus ? BELLICUS_TINT : SERENA_TINT;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(AlienXClient.SKIN));
		head.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
		hat.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
		// the stars in his skin shine in the dark (an additive pass of the same texture, in the head's colour)
		VertexConsumer stars = buffers.getBuffer(RenderType.eyes(AlienXClient.SKIN));
		head.render(pose, stars, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, PyroDraw.dim(tint, 0.55f + 0.3f * talk));
		AlienXClient.horns(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(AlienXClient.SKIN)), LightTexture.FULL_BRIGHT, head);
		pose.popPose();
		// the eyes (in the head's space, pixels: the front face is z = -4)
		int eyeCol = bellicus ? BELLICUS_EYE : SERENA_EYE;
		float eyeR = S * (bellicus ? 0.05f : 0.045f) * (1f + 0.6f * talk);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 e = m.at(side * 2.0f, -3.5f, -4.3f);
			PyroDraw.glow(pose, glow, e, eyeR, PyroDraw.dim(eyeCol, 0.85f + 0.15f * talk));
			PyroDraw.glow(pose, glow, e, eyeR * 2.4f, PyroDraw.dim(eyeCol, 0.25f + 0.3f * talk));
			if (bellicus) {
				// hard brows, the inner ends low: anger
				List<Vec3> brow = new ArrayList<>();
				brow.add(m.at(side * 0.6f, -4.5f, -4.4f));
				brow.add(m.at(side * 2.2f, -5.3f, -4.4f));
				brow.add(m.at(side * 3.7f, -6.0f, -4.4f));
				PyroDraw.ribbon(pose, add, brow, u -> S * 0.035, u -> PyroDraw.dim(0xFFFF3020, 0.9f));
			}
		}
		if (!bellicus) {
			// a soft halo
			pose.pushPose();
			Vec3 h = m.at(0f, -10.5f, 0f);
			pose.translate(h.x, h.y, h.z);
			pose.mulPose(Axis.YP.rotation(time * 0.02f));
			PyroDraw.band(pose, add, S * 0.38f, 0.12f, PyroDraw.dim(SERENA_EYE, 0.55f + 0.3f * talk), 0xFF000000);
			pose.popPose();
		}
		if (talking >= 0 && talking < 50) {
			// rings ripple out from the one speaking
			for (int k = 0; k < 3; k++) {
				float u = ((talking + k * 12f) % 36f) / 36f;
				pose.pushPose();
				Vec3 mouth = m.at(0f, -2.0f, -4.5f);
				pose.translate(mouth.x, mouth.y, mouth.z);
				PyroDraw.faceCamera(pose);
				pose.mulPose(Axis.XP.rotationDegrees(90));
				PyroDraw.band(pose, add, S * (0.2f + 0.5f * u), 0.08f, PyroDraw.dim(eyeCol, (1f - u) * 0.6f * talk), 0xFF000000);
				pose.popPose();
			}
		}
	}

	/** The head's transform, to place things on its face (camera-relative world points from pixel coordinates). */
	private static final class Matrix {
		private final org.joml.Matrix4f m;

		Matrix(PoseStack pose, ModelPart head) {
			PoseStack copy = new PoseStack();
			copy.last().pose().set(pose.last().pose());
			head.translateAndRotate(copy);
			this.m = new org.joml.Matrix4f(copy.last().pose());
		}

		Vec3 at(float px, float py, float pz) {
			Vector4f v = new Vector4f(px / 16f, py / 16f, pz / 16f, 1f);
			m.transform(v);
			// the world render's pose already holds the camera rotation: undo it to get camera-relative world coordinates
			Vector4f w = new Vector4f(v);
			org.joml.Matrix4f inv = new org.joml.Matrix4f(BASE).invert();
			inv.transform(w);
			return new Vec3(w.x, w.y, w.z);
		}
	}

	/** The world pose's base matrix this frame (the camera rotation), set before the heads draw. */
	private static final org.joml.Matrix4f BASE = new org.joml.Matrix4f();
}
