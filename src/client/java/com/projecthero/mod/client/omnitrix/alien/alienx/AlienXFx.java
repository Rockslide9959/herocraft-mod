package com.projecthero.mod.client.omnitrix.alien.alienx;

import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncil;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Celestial Council: the two enormous floating heads ({@link #HEAD_SCALE} times a player's head, head and hat layer) and the
 * point of light in {@link AlienXHall} (v0.17.2 round 4: the hall took over the old dimension's sky, floor and nebulae). v0.17.2 (user:
 * "use this for bellicus and serenas faces: [the planetminecraft Bellicus and Serena skin] ... use the one side of the head for
 * bellicus and other side for serena, split it into 2 head models"): that skin carries Bellicus' face on one side of the head and
 * Serena's on the other; each head here is its own skin with that face moved to the front
 * ({@code scratchpad/gen_alienx_council_heads_v0172.js}). They drift, turn to look at him, blink now and then, and the one speaking
 * (synced: {@code AlienXKit.speaker}) nods, its mouth moves, its eyes flare and green rings ripple out from it. Additive glows and
 * bands ({@link PyroDraw}): no particles.
 */
public final class AlienXFx {
	public static final float HEAD_SCALE = 14f;
	private static final int SERENA_EYE = 0xFFC8FFB0, BELLICUS_EYE = 0xFF8CFF30, VIOLET = 0xFF8A4DFF, STAR_BLUE = 0xFF9FC4FF;
	/** [bellicus ? 1 : 0][blink + 2 * talk]: the four face variants of each head. */
	private static final ResourceLocation[][] SKINS = new ResourceLocation[2][4];
	static {
		String[] suffix = { "", "_blink", "_talk", "_blink_talk" };
		for (int k = 0; k < 4; k++) {
			SKINS[0][k] = ProjectHeroMod.id("textures/entity/omnitrix/alien_x_serena" + suffix[k] + ".png");
			SKINS[1][k] = ProjectHeroMod.id("textures/entity/omnitrix/alien_x_bellicus" + suffix[k] + ".png");
		}
	}
	private static PlayerModel<AbstractClientPlayer> model;

	private AlienXFx() {
	}

	/**
	 * The heads and the point of light. {@code pose} holds the view rotation, as a world pass's does, and everything is drawn relative
	 * to {@code cam}. {@code light}: how strongly the point of light shows (it steps back while he uses it).
	 */
	static void council(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 look, Vec3 serena, Vec3 bellicus, Vec3 console, float time, float partial,
			float light) {
		Minecraft mc = Minecraft.getInstance();
		BASE.set(pose.last().pose());
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		int speaker = AlienXKit.speaker(mc.player);
		long now = mc.level.getGameTime();
		float since = now - AlienXKit.speakAt(mc.player) + partial;
		head(pose, buffers, add, glow, cam, serena, look, time, false, speaker == AlienXCouncil.WHO_SERENA && since >= 0 && since < 50 ? since : -1f);
		head(pose, buffers, add, glow, cam, bellicus, look, time, true, speaker == AlienXCouncil.WHO_BELLICUS && since >= 0 && since < 50 ? since : -1f);
		// the point of light, turning over its plinth
		Vec3 p = console.add(0.0, 1.45 + 0.08 * Mth.sin(time * 0.06f), 0.0);
		PyroDraw.glow(pose, glow, p.subtract(cam), 1.5f, PyroDraw.dim(STAR_BLUE, 0.55f * light));
		PyroDraw.glow(pose, glow, p.subtract(cam), 0.42f, PyroDraw.dim(0xFFFFFFFF, 0.95f * light));
		pose.pushPose();
		Vec3 r = p.subtract(cam);
		pose.translate(r.x, r.y, r.z);
		pose.mulPose(Axis.YP.rotation(time * 0.05f));
		pose.mulPose(Axis.ZP.rotationDegrees(62f));
		PyroDraw.band(pose, add, 0.62f, 0.025f, PyroDraw.dim(VIOLET, light), 0xFF000000);
		pose.mulPose(Axis.XP.rotationDegrees(70f));
		PyroDraw.band(pose, add, 0.5f, 0.02f, PyroDraw.dim(STAR_BLUE, light), 0xFF000000);
		pose.popPose();
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
		PyroDraw.glow(pose, glow, c.subtract(cam), S * 0.75f, PyroDraw.dim(bellicus ? 0xFF2A5A08 : 0xFF1A5A3A, 0.45f + 0.3f * talk));
		pose.pushPose();
		Vec3 r = c.subtract(cam);
		pose.translate(r.x, r.y, r.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		pose.scale(-S, -S, S);
		pose.translate(0, 0.25, 0);
		Matrix m = new Matrix(pose, head);
		// blinks now and then (Serena more often); the mouth moves while it speaks
		int blinkEvery = bellicus ? 157 : 113;
		boolean blink = (time + (bellicus ? 40f : 0f)) % blinkEvery < 4f;
		boolean mouth = talking >= 0 && talking < 40 && Mth.sin(talking * 0.9f) > -0.2f;
		ResourceLocation skin = SKINS[bellicus ? 1 : 0][(blink ? 1 : 0) + (mouth ? 2 : 0)];
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(skin));
		head.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		hat.render(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		pose.popPose();
		// the eyes flare when it speaks (in the head's space, pixels)
		int eyeCol = bellicus ? BELLICUS_EYE : SERENA_EYE;
		if (talk > 0f && !blink) {
			for (int side = -1; side <= 1; side += 2) {
				Vec3 e = m.at(side * 2.0f, -3.5f, -4.3f);
				PyroDraw.glow(pose, glow, e, S * 0.05f * talk, PyroDraw.dim(eyeCol, 0.8f * talk));
				PyroDraw.glow(pose, glow, e, S * 0.12f * talk, PyroDraw.dim(eyeCol, 0.3f * talk));
			}
		}
		if (talking >= 0 && talking < 50) {
			// rings ripple out from the one speaking
			for (int k = 0; k < 3; k++) {
				float u = ((talking + k * 12f) % 36f) / 36f;
				pose.pushPose();
				Vec3 mo = m.at(0f, -1.5f, -4.5f);
				pose.translate(mo.x, mo.y, mo.z);
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
