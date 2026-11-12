package com.projecthero.mod.client.doctorfate;

import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.data.FateState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate's world effects, for every viewer: the <b>Avatar of Order</b> (a 40-block golden projection of Doctor Fate
 * rising behind him, translucent and glowing, its right hand slamming down on each attack key), the <b>Ankh Shield</b> (a giant
 * golden ankh in front of him while G is held), and for the caster only, Sight of Nabu's health readouts over every creature it
 * shows. Everything else (bolts, gates, chains, storms, wards) is server particles, so every player sees the same thing.
 */
public final class FateWorldRender {
	private static final ResourceLocation AVATAR = ProjectHeroMod.id("textures/entity/doctor_fate/avatar.png");
	private static PlayerModel<AbstractClientPlayer> giant;

	private FateWorldRender() {
	}

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		PoseStack pose = ctx.matrixStack();
		Vec3 cam = ctx.camera().getPosition();
		MultiBufferSource buffers = ctx.consumers();
		for (Player p : mc.level.players()) {
			FateState s = DoctorFate.peek(p);
			if (s == null || !s.hasPower || p.isInvisible()) {
				continue;
			}
			if (DoctorFate.avatarActive(p)) {
				avatar(pose, buffers, cam, p, s, partial);
			}
			if (s.shieldUp) {
				shield(pose, buffers, cam, p, s.helmetOn, partial);
			}
		}
		labels(mc, pose, buffers, cam, ctx.camera().rotation(), partial);
	}

	// ---------------------------------------------------------------- the Avatar of Order

	private static void avatar(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Player p, FateState s, float partial) {
		long now = p.level().getGameTime();
		float age = now - (s.avatarUntil - DoctorFateConfig.AVATAR_TICKS) + partial;
		float left = s.avatarUntil - now - partial;
		float rise = Mth.clamp(age / 20f, 0f, 1f);
		float fade = Mth.clamp(left / 20f, 0f, 1f);
		float alpha = 0.55f * rise * fade;
		if (alpha <= 0.01f) {
			return;
		}
		if (giant == null) {
			giant = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
		}
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		Vec3 at = p.getPosition(partial);
		double yr = Math.toRadians(yaw);
		Vec3 back = new Vec3(Math.sin(yr), 0, -Math.cos(yr)); // behind him
		Vec3 base = at.add(back.scale(7.0)).add(0, -(1f - rise) * 22f, 0);
		float scale = 40f / 1.8f * (0.85f + 0.15f * rise);
		// the pose: arms out, the right hand slamming on each attack
		PlayerModel<AbstractClientPlayer> m = giant;
		resetPose(m);
		float breathe = Mth.sin(age * 0.08f) * 0.05f;
		m.head.xRot = Mth.clamp(p.getViewXRot(partial), -40f, 60f) * ((float) Math.PI / 180f) * 0.6f + 0.25f;
		m.leftArm.zRot = -0.45f - breathe;
		m.leftArm.xRot = -0.3f;
		m.rightArm.zRot = 0.45f + breathe;
		float slam = now - s.avatarSlamAt + partial;
		if (s.avatarSlamAt > 0L && slam < 14f) {
			float t = slam / 14f;
			m.rightArm.xRot = t < 0.35f ? Mth.lerp(t / 0.35f, -0.5f, -2.9f) : Mth.lerp(Math.min(1f, (t - 0.35f) / 0.25f), -2.9f, -1.1f);
			m.rightArm.zRot = 0.1f;
		} else {
			m.rightArm.xRot = -0.3f;
		}
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		pose.pushPose();
		pose.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		pose.scale(-scale, -scale, scale);
		pose.translate(0.0, -1.501, 0.0);
		int colour = (Math.round(255 * alpha) << 24) | 0xFFE6A0;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(AVATAR)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
		ModelPart helmet = FateSuitLayer.helmetModel().getChild("helmet");
		helmet.copyFrom(m.head);
		helmet.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(FateSuitLayer.HELMET)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, (Math.round(255 * Math.min(1f, alpha * 1.4f)) << 24) | 0xFFFFFF);
		pose.popPose();
	}

	private static void resetPose(PlayerModel<?> m) {
		for (ModelPart part : new ModelPart[] { m.head, m.body, m.leftArm, m.rightArm, m.leftLeg, m.rightLeg }) {
			part.resetPose();
		}
		m.crouching = false;
		m.attackTime = 0f;
		m.riding = false;
		m.young = false;
	}

	// ---------------------------------------------------------------- the Ankh Shield

	private static void shield(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Player p, boolean full, float partial) {
		Vec3 look = p.getViewVector(partial);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0e-4) {
			flat = new Vec3(0, 0, 1);
		}
		flat = flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0, flat.x);
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 c = p.getEyePosition(partial).add(look.scale(1.3)).add(0, -0.25, 0);
		float size = full ? 1.25f : 0.85f;
		float pulse = 0.75f + 0.15f * Mth.sin((p.tickCount + partial) * 0.4f);
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		float a = (full ? 0.75f : 0.5f) * pulse;
		// the loop
		int n = 16;
		for (int k = 0; k < n; k++) {
			double a0 = k * Math.PI * 2 / n, a1 = (k + 1) * Math.PI * 2 / n;
			Vec3 p0 = c.add(up.scale(size * (0.55 + Math.sin(a0) * 0.32))).add(right.scale(Math.cos(a0) * size * 0.26));
			Vec3 p1 = c.add(up.scale(size * (0.55 + Math.sin(a1) * 0.32))).add(right.scale(Math.cos(a1) * size * 0.26));
			bar(vc, m, p0, p1, flat, size * 0.07f, a);
		}
		// the bar and the stem
		bar(vc, m, c.add(up.scale(size * 0.15)).add(right.scale(-size * 0.5)), c.add(up.scale(size * 0.15)).add(right.scale(size * 0.5)), flat, size * 0.08f, a);
		bar(vc, m, c.add(up.scale(size * 0.2)), c.add(up.scale(-size * 0.95)), flat, size * 0.08f, a);
		pose.popPose();
	}

	/** A flat glowing bar from {@code a} to {@code b}, facing {@code normal}, both sides. */
	private static void bar(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 normal, float w, float alpha) {
		Vec3 d = b.subtract(a);
		Vec3 side = d.cross(normal);
		if (side.lengthSqr() < 1.0e-8) {
			return;
		}
		side = side.normalize().scale(w);
		Vec3[] q = { a.add(side), b.add(side), b.subtract(side), a.subtract(side) };
		int r = 255, g = 205, bl = 70, al = Math.round(255 * alpha);
		for (int i = 0; i < 4; i++) {
			vc.addVertex(m, (float) q[i].x, (float) q[i].y, (float) q[i].z).setColor(r, g, bl, al);
		}
		for (int i = 3; i >= 0; i--) {
			vc.addVertex(m, (float) q[i].x, (float) q[i].y, (float) q[i].z).setColor(r, g, bl, al);
		}
	}

	// ---------------------------------------------------------------- Sight of Nabu's health readouts

	private static void labels(Minecraft mc, PoseStack pose, MultiBufferSource buffers, Vec3 cam, org.joml.Quaternionf camRot, float partial) {
		Map<Integer, long[]> sight = FateFx.sightMap();
		if (sight.isEmpty()) {
			return;
		}
		Font font = mc.font;
		for (Integer id : sight.keySet()) {
			Entity e = mc.level.getEntity(id);
			if (!(e instanceof LivingEntity le) || !FateFx.sighted(e)) {
				continue;
			}
			Vec3 at = e.getPosition(partial).add(0, e.getBbHeight() + 0.6, 0);
			String text = "❤ " + Math.round(le.getHealth()) + " / " + Math.round(le.getMaxHealth());
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			pose.mulPose(camRot);
			float sc = 0.025f * (float) Math.max(1.0, at.distanceTo(cam) / 12.0);
			pose.scale(sc, -sc, sc);
			Matrix4f m = pose.last().pose();
			int col = 0xFF000000 | FateFx.colour(e);
			font.drawInBatch(text, -font.width(text) / 2f, 0, col, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0x50000000, LightTexture.FULL_BRIGHT);
			pose.popPose();
		}
	}
}
