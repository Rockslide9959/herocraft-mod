package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.client.shield.ForceBubbleRenderer;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.data.GreenLanternAutoProtectFx;
import com.projecthero.mod.shield.ForceBubble;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Ring Auto-Protection, the client half: draws the ring's automatic saves on every Lantern from the synced
 * {@link ModAttachments#GREEN_LANTERN_AUTO_PROTECT_FX} -- purely a render, nothing to clean up.
 * <ul>
 * <li>Death Save: the hard-light bubble ({@link ForceBubbleRenderer}) pops out round the body in three ticks behind a white
 * flash, holds for the shell's second and fades.</li>
 * <li>Fall Cushion: a flat octagonal hard-light pad flashes on the ground under the Lantern and sinks back.</li>
 * <li>Air Bubble: the Atmosphere Bubble construct's look (two soft spheres, green and pale) round the head.</li>
 * <li>Heat Shell: a faint orange-tinted green shell round the body with an amber rim, breathing slowly.</li>
 * </ul>
 * The lasting shells are skipped for the wearer in first person (the camera is inside them).
 */
public final class GreenLanternAutoProtectRenderer {
	private static final int SHELL_TICKS = GreenLanternConfig.AUTO_DEATH_SAVE_SHELL_TICKS;
	private static final int SHELL_FADE = 6;
	private static final int CUSHION_TICKS = 14;
	private static final int HEAT_GREEN = 0xA6E04C;
	private static final int HEAT_AMBER = 0xFFAA3C;

	private GreenLanternAutoProtectRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(GreenLanternAutoProtectRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		Vec3 cam = context.camera().getPosition();
		float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		for (Player player : mc.level.players()) {
			GreenLanternAutoProtectFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_AUTO_PROTECT_FX, GreenLanternAutoProtectFx.EMPTY);
			if (GreenLanternAutoProtectFx.EMPTY.equals(fx) || player.isInvisible()) {
				continue;
			}
			boolean self = player == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3 at = player.getPosition(pt);
			float time = player.tickCount + pt;
			if (fx.has(GreenLanternAutoProtectFx.HEAT) && !self) {
				heat(pose, buffers, cam, at.add(0, player.getBbHeight() * 0.5, 0), time);
			}
			if (fx.has(GreenLanternAutoProtectFx.BUBBLE) && !self) {
				bubble(pose, buffers, cam, player.getEyePosition(pt).add(0, 0.02, 0), time);
			}
			if (fx.cushionStart() > 0L) {
				float age = now - fx.cushionStart() + pt;
				if (age >= 0f && age < CUSHION_TICKS) {
					cushion(pose, buffers, cam, new Vec3(at.x, fx.cushionY(), at.z), age);
				}
			}
			if (fx.shellStart() > 0L) {
				float age = now - fx.shellStart() + pt;
				if (age >= 0f && age < SHELL_TICKS + SHELL_FADE) {
					shell(pose, buffers, cam, at.add(0, player.getBbHeight() * 0.5, 0), age, time, self);
				}
			}
		}
	}

	/** The Death Save's burst shell: pops out behind a white flash, holds, fades. */
	private static void shell(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 center, float age, float time, boolean self) {
		float grow = Math.min(1f, age / 3f);
		double radius = 0.5 + 0.85 * (1 - (1 - grow) * (1 - grow)); // ease out to 1.35
		float alpha = age < SHELL_TICKS ? 1f : 1f - (age - SHELL_TICKS) / SHELL_FADE;
		ForceBubble.Style base = ForceBubble.Style.GREEN_LANTERN;
		ForceBubble.Style style = new ForceBubble.Style(0x5CFF7E, 0xEFFFF2, 0xFFFFFF, base.dust(), base.dustBig(), radius);
		// the white flash of the burst (first few ticks)
		if (age < 5f && !self) {
			float k = 1f - age / 5f;
			pose.pushPose();
			pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
			VertexConsumer vc = TurboDraw.buffer(buffers);
			TurboDraw.sphere(vc, pose, (float) radius * 0.98f, 0xF2FFF5, 0.45f * k);
			TurboDraw.sphere(vc, pose, (float) radius * 1.06f, HardLightDraw.GREEN, 0.25f * k);
			pose.popPose();
		}
		ForceBubbleRenderer.draw(pose, buffers, cam, center, style, time * 3f, Mth.clamp(alpha, 0f, 1f), self);
	}

	/** The Fall Cushion pad: a flat octagon of hard light on the ground, springing out then sinking away. */
	private static void cushion(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 ground, float age) {
		float grow = Math.min(1f, age / 3f);
		float r = 0.45f + 0.75f * grow;
		float alpha = age < CUSHION_TICKS - 6 ? 1f : 1f - (age - (CUSHION_TICKS - 6)) / 6f;
		// it gives a little under the landing (squash at tick 3-6), then settles
		float squash = 1f - 0.45f * Mth.sin(Mth.clamp((age - 2f) / 5f, 0f, 1f) * Mth.PI);
		pose.pushPose();
		pose.translate(ground.x - cam.x, ground.y - cam.y + 0.06, ground.z - cam.z);
		VertexConsumer vc = TurboDraw.buffer(buffers);
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 45f + age * 2f));
			HardLightDraw.part(vc, pose, 0f, 0f, 0f, r * 0.42f, 0.05f * squash, r, alpha * 0.85f);
			HardLightDraw.part(vc, pose, 0f, 0f, 0f, r, 0.05f * squash, r * 0.42f, alpha * 0.85f);
			pose.popPose();
		}
		// a bright rim of studs and the emblem lying on top
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 45f + 22.5f + age * 2f));
			HardLightDraw.glow(vc, pose, r * 1.02f, 0.03f, 0f, 0.05f, 0.04f, 0.05f, alpha);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(0f, 0.07f * squash, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		HardLightDraw.emblem(vc, pose, r * 0.9f, alpha * 0.8f);
		pose.popPose();
		pose.popPose();
	}

	/** The Air Bubble: the Atmosphere Bubble construct's two soft spheres, round the head. */
	private static void bubble(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 head, float time) {
		float r = 0.5f * (1f + 0.03f * Mth.sin(time * 0.25f));
		pose.pushPose();
		pose.translate(head.x - cam.x, head.y - cam.y, head.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(time * 1.5f));
		VertexConsumer vc = TurboDraw.buffer(buffers);
		TurboDraw.sphere(vc, pose, r, HardLightDraw.GREEN, 0.24f);
		TurboDraw.sphere(vc, pose, r * 1.05f, HardLightDraw.PALE, 0.13f);
		pose.popPose();
	}

	/** The Heat Shell: faint orange-tinted green with an amber rim, breathing. */
	private static void heat(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 center, float time) {
		float breathe = 0.85f + 0.15f * Mth.sin(time * 0.2f);
		float r = 1.15f + 0.03f * Mth.sin(time * 0.3f);
		pose.pushPose();
		pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(-time * 1.2f));
		VertexConsumer vc = TurboDraw.buffer(buffers);
		TurboDraw.sphere(vc, pose, r, HEAT_GREEN, 0.18f * breathe);
		TurboDraw.sphere(vc, pose, r * 1.04f, HEAT_AMBER, 0.10f * breathe);
		pose.popPose();
	}
}
