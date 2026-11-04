package com.projecthero.mod.client.omnitrix;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2 (user: "this watch doesnt use dial icons it displays holograms of the aliens which can just be see through green models that
 * show up as the models"): the Recalibrated Omnitrix's dial. While it is primed, the selected alien stands over the face as a small
 * hologram -- its own body (the wide player model in its skin), tinted green, see-through and glowing -- on a cone of green light rising
 * from the face. Round 2 (user: "Shrink the holograms ... dont make the holograms slowly turn when showing just make the front of the
 * hologram face the player ... when turning the Dial make the alien hologram flicker out and then the next one flicker in"): it always
 * turns its front to whoever is looking, and a dial turn strobes the old figure out, then the new one in. Drawn by {@link OmnitrixWatch}
 * in the arm's space (x out of the face), first and third person alike.
 */
public final class RecalHologram {
	/** A plain white 64x64 sheet: the hologram's solid "volume" pass is the model's shape in flat green. */
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/omnitrix/holo_white.png");
	/** How tall the figure stands (blocks) and how high over the face its feet float. */
	private static final float HEIGHT = 0.2f, FLOAT = 0.025f;
	/** A dial turn: the old figure strobes out over OUT ticks, then the new one strobes in over IN ticks. */
	private static final float OUT = 4f, IN = 5f;
	private static PlayerModel<LivingEntity> model;
	/** Per wearer: the figure shown, the one before it and when it changed (client game time). Weak: gone with the player entity. */
	private static final Map<Player, Shown> SHOWN = new WeakHashMap<>();

	private static final class Shown {
		String id = "", prev = "";
		float changedAt = -99f;
	}

	private RecalHologram() {
	}

	private static PlayerModel<LivingEntity> model() {
		if (model == null) {
			model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		}
		return model;
	}

	/**
	 * Draws {@code alienId}'s hologram for {@code p}, standing on the face centred at ({@code faceX}, {@code cy}, 0) px, faded by
	 * {@code alpha} (0..1, the watch's priming). {@code time} is the client game time (with the partial tick).
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, Player p, String alienId, float faceX, float cy, float alpha, float time) {
		if (alpha <= 0.01f) {
			return;
		}
		Shown sh = SHOWN.computeIfAbsent(p, k -> new Shown());
		if (!alienId.equals(sh.id)) {
			sh.prev = sh.id;
			sh.id = alienId;
			sh.changedAt = sh.prev.isEmpty() ? -99f : time;
		}
		float dt = time - sh.changedAt;
		String draw = sh.id;
		float k = 1f;
		if (dt >= 0f && dt < OUT + IN && !sh.prev.isEmpty()) {
			// the strobe: on / off every ~1.5 ticks, dimming out, then brightening in
			boolean on = ((int) (dt * 1.4f)) % 2 == 0;
			if (dt < OUT) {
				draw = sh.prev;
				k = (1f - dt / OUT) * (on ? 1f : 0.15f);
			} else {
				k = ((dt - OUT) / IN) * (on ? 1f : 0.25f);
			}
		}
		int lit = LightTexture.FULL_BRIGHT;
		float shimmer = 0.85f + 0.15f * Mth.sin(time * 0.9f);
		cone(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), faceX / 16f, cy / 16f, alpha * (0.6f + 0.4f * k));
		AlienClientKit kit = AlienClients.of(draw);
		ResourceLocation skin = kit == null || kit.customBody() ? null : kit.skin();
		float a = alpha * k * shimmer;
		if (skin == null || a <= 0.01f) {
			return;
		}
		PlayerModel<LivingEntity> m = model();
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg,
				m.rightPants, m.leftPants }) {
			part.resetPose();
			part.visible = true;
		}
		m.crouching = false;
		m.young = false;
		m.riding = false;
		m.attackTime = 0f;
		m.rightArm.zRot = 0.12f;
		m.leftArm.zRot = -0.12f;
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftSleeve.copyFrom(m.leftArm);
		pose.pushPose();
		// stand on the face: the figure's up (+y) is the face's normal (+x); a slight bob
		pose.translate(faceX / 16f + FLOAT + 0.006f * Mth.sin(time * 0.12f), cy / 16f, 0f);
		pose.mulPose(Axis.ZP.rotationDegrees(-90f));
		// the front (-z after the model's flip) toward the WEARER's eyes -- v0.17.2 (user: "the hologram should be facing the player
		// instead of outwards in 3rd person"): the pose's space has the camera at its origin, so his eyes are at (eye - camera)
		Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
		net.minecraft.world.phys.Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		net.minecraft.world.phys.Vec3 eye = p.getEyePosition(Mth.frac(time));
		Vector4f cam = inv.transform(new Vector4f((float) (eye.x - camPos.x), (float) (eye.y - camPos.y), (float) (eye.z - camPos.z), 1f));
		if (cam.x * cam.x + cam.z * cam.z > 1.0e-8f) {
			pose.mulPose(Axis.YP.rotation((float) Math.atan2(-cam.x, -cam.z)));
		}
		float s = HEIGHT / 1.875f;
		pose.scale(s, s, s);
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		int a1 = Math.round(70 * a), a2 = Math.round(175 * a);
		// the solid green volume, then the skin's detail in green over it
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), lit, OverlayTexture.NO_OVERLAY, (a1 << 24) | 0x6CFF7A);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(skin)), lit, OverlayTexture.NO_OVERLAY, (a2 << 24) | 0xC8FFC0);
		pose.popPose();
	}

	/**
	 * v0.17.2 (user: "for the recalibrated master control H alien selector wheel dont show the alien icons show the holograms instead"):
	 * {@code alienId}'s hologram flat on a screen -- the same see-through green figure, {@code height} GUI px tall, its feet at
	 * ({@code x}, {@code feetY}), front on, over a short beam of green light; {@code alpha} 0..1.
	 */
	public static void renderGui(net.minecraft.client.gui.GuiGraphics g, String alienId, float x, float feetY, float height, float alpha, float time) {
		AlienClientKit kit = AlienClients.of(alienId);
		ResourceLocation skin = kit == null || kit.customBody() ? null : kit.skin();
		if (alpha <= 0.01f) {
			return;
		}
		// the light it stands in: a beam rising from a bright base line, fading out by the head
		int beamA = Math.round(90 * alpha), baseA = Math.round(200 * alpha);
		int hw = Math.round(height * 0.32f);
		g.fillGradient(Math.round(x - hw), Math.round(feetY - height * 1.05f), Math.round(x + hw), Math.round(feetY), 0x005AFF6E, (beamA << 24) | 0x5AFF6E);
		g.fill(Math.round(x - hw - 1), Math.round(feetY), Math.round(x + hw + 1), Math.round(feetY) + 1, (baseA << 24) | 0x9CFF9A);
		if (skin == null) {
			return;
		}
		PlayerModel<LivingEntity> m = model();
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg,
				m.rightPants, m.leftPants }) {
			part.resetPose();
			part.visible = true;
		}
		m.crouching = false;
		m.young = false;
		m.riding = false;
		m.attackTime = 0f;
		m.rightArm.zRot = 0.12f;
		m.leftArm.zRot = -0.12f;
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftSleeve.copyFrom(m.leftArm);
		float shimmer = 0.85f + 0.15f * Mth.sin(time * 0.9f);
		float a = alpha * shimmer;
		PoseStack pose = g.pose();
		pose.pushPose();
		// model space is y-down like the screen: the head's top is y -0.5, the feet y 1.5 (blocks); z flipped so the face looks out
		float s = height / 2f;
		pose.translate(x, feetY - 1.5f * s, 150f);
		pose.scale(s, s, -s);
		int a1 = Math.round(70 * a), a2 = Math.round(175 * a);
		var buffers = g.bufferSource();
		int lit = LightTexture.FULL_BRIGHT;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), lit, OverlayTexture.NO_OVERLAY, (a1 << 24) | 0x6CFF7A);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(skin)), lit, OverlayTexture.NO_OVERLAY, (a2 << 24) | 0xC8FFC0);
		g.flush();
		pose.popPose();
	}

	/**
	 * v0.17.2 (user: "for the recalibrate omnitrix menu dont use alien icons, use the alien models instead since we dont have alien
	 * icons for them"): {@code alienId}'s body in full colour -- the wide player model in its skin, lit like a mob in an inventory, turned
	 * a little to show its depth -- fitted head to feet in a {@code size}-px square at ({@code x}, {@code y}). False (nothing drawn) for an
	 * alien without a player-model skin, so the caller can fall back to its icon.
	 */
	public static boolean renderGuiModel(net.minecraft.client.gui.GuiGraphics g, String alienId, int x, int y, int size) {
		AlienClientKit kit = AlienClients.of(alienId);
		ResourceLocation skin = kit == null || kit.customBody() ? null : kit.skin();
		if (skin == null) {
			return false;
		}
		PlayerModel<LivingEntity> m = model();
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.jacket, m.rightArm, m.leftArm, m.rightSleeve, m.leftSleeve, m.rightLeg, m.leftLeg,
				m.rightPants, m.leftPants }) {
			part.resetPose();
			part.visible = true;
		}
		m.crouching = false;
		m.young = false;
		m.riding = false;
		m.attackTime = 0f;
		m.rightArm.zRot = 0.1f;
		m.leftArm.zRot = -0.1f;
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftSleeve.copyFrom(m.leftArm);
		PoseStack pose = g.pose();
		pose.pushPose();
		// model space: the head's top at y -0.5, the feet at 1.5 (blocks), y down like the screen -- 2 blocks fit the square's height
		float s = size / 2.05f;
		pose.translate(x + size / 2f, y + size / 2f, 150f);
		pose.scale(s, s, -s);
		pose.mulPose(Axis.XP.rotationDegrees(-8f));
		pose.mulPose(Axis.YP.rotationDegrees(-25f));
		pose.translate(0f, -0.5f, 0f);
		com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
		var buffers = g.bufferSource();
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		g.flush();
		com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
		pose.popPose();
		return true;
	}

	/** A cone of light from the face (radius 0.8 px) widening to 2.2 px at the figure's head, fading out as it rises. */
	private static void cone(PoseStack pose, VertexConsumer vc, float x0, float cy, float alpha) {
		PoseStack.Pose p = pose.last();
		int seg = 20;
		float r0 = 0.6f / 16f, r1 = 2.0f / 16f, x1 = x0 + HEIGHT + 0.04f;
		int lit = LightTexture.FULL_BRIGHT;
		int aBot = Math.round(110 * alpha), aTop = 0;
		for (int i = 0; i < seg; i++) {
			double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
			v(vc, p, x0, cy + c0 * r0, s0 * r0, 0f, 0f, aBot, c0, s0, lit);
			v(vc, p, x0, cy + c1 * r0, s1 * r0, 1f, 0f, aBot, c1, s1, lit);
			v(vc, p, x1, cy + c1 * r1, s1 * r1, 1f, 1f, aTop, c1, s1, lit);
			v(vc, p, x1, cy + c0 * r1, s0 * r1, 0f, 1f, aTop, c0, s0, lit);
		}
	}

	private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float vv, int a, float ny, float nz, int light) {
		vc.addVertex(p, x, y, z).setColor(90, 255, 110, a).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, ny, nz);
	}
}
