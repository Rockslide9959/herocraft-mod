package com.projecthero.mod.client.miraculous.multimouse;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.projecthero.mod.miraculous.multimouse.Multimouse;
import com.projecthero.mod.miraculous.multimouse.MultimouseConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Multimouse's world visuals ({@code AFTER_ENTITIES}), for every viewer -- the jump rope out of her right hand
 * (the lash's whip, the lasso, the swing's line to its hook, the bind, the twirl's spinning loop) and her snares on the
 * ground -- and, for HER client only, Mouse Hearing: every creature within {@link MultimouseConfig#HEARING_RANGE} shows
 * through walls as rings of sound pulsing with its footsteps (lines with the depth test off).
 */
public final class MultimouseWorldFx {
	private static final RenderType XRAY_LINES = new RenderType("projecthero_multimouse_hearing_lines",
			DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 4096, false, false,
			() -> {
				RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
				RenderSystem.lineWidth(2.0f);
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
				RenderSystem.disableDepthTest();
				RenderSystem.depthMask(false);
				RenderSystem.disableCull();
			},
			() -> {
				RenderSystem.enableCull();
				RenderSystem.depthMask(true);
				RenderSystem.enableDepthTest();
				RenderSystem.disableBlend();
				RenderSystem.lineWidth(1.0f);
			}) {
	};

	private MultimouseWorldFx() {
	}

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		MultiBufferSource buffers = context.consumers();
		PoseStack pose = context.matrixStack();
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		for (AbstractClientPlayer p : mc.level.players()) {
			if (Multimouse.is(p) && !p.isInvisible()) {
				ropes(pose, buffers, p, partial, p == mc.player && mc.options.getCameraType().isFirstPerson());
			}
		}
		if (mc.player != null && Multimouse.hearing(mc.player)) {
			hearing(pose, buffers, mc, partial);
		}
		pose.popPose();
		if (buffers instanceof MultiBufferSource.BufferSource source) {
			source.endBatch(XRAY_LINES);
		}
	}

	// ---------------------------------------------------------------- the rope

	/** Her right hand, in the world. */
	static Vec3 hand(Player p, float partial) {
		Vec3 pos = p.getPosition(partial);
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		float s = p.getScale();
		return pos.add(0, p.getBbHeight() * 0.62, 0).add(right.scale(0.36 * s)).add(fwd.scale(0.25 * s));
	}

	private static void ropes(PoseStack pose, MultiBufferSource buffers, Player p, float partial, boolean firstPerson) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		long now = p.level().getGameTime();
		float t = now + partial;
		Vec3 hand = firstPerson ? p.getEyePosition(partial).add(p.getViewVector(partial).scale(0.4)).add(0, -0.25, 0) : hand(p, partial);
		int light = LevelRenderer.getLightColor(p.level(), BlockPos.containing(hand));
		float thick = 0.035f * Math.max(0.5f, p.getScale());
		// the swing's line to its hook
		Vec3 hook = Multimouse.vec(p, Multimouse.X_SWING);
		if (hook != null && Multimouse.running(p, Multimouse.V_SWING)) {
			rope(vc, pose, hand, hook, 0.0, thick, light);
		}
		// the lasso / the bind: to the creature
		for (String[] k : new String[][] { { Multimouse.V_LASSO, Multimouse.V_LASSO_ID }, { Multimouse.V_BIND, Multimouse.V_BIND_ID } }) {
			if (Multimouse.running(p, k[0])) {
				Entity e = p.level().getEntity((int) Multimouse.val(p, k[1]) - 1);
				if (e != null) {
					Vec3 to = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
					rope(vc, pose, hand, to, 0.4, thick, light);
					if (k[0].equals(Multimouse.V_BIND)) {
						loop(vc, pose, e.getPosition(partial).add(0, e.getBbHeight() * 0.45, 0), e.getBbWidth() * 0.62 + 0.06, 10, thick, light, t * 0.05f);
						loop(vc, pose, e.getPosition(partial).add(0, e.getBbHeight() * 0.7, 0), e.getBbWidth() * 0.6 + 0.06, 10, thick, light, -t * 0.05f);
					}
				}
			}
		}
		// the lash: a whip crack out along her look while a swing plays
		float[] play = MultimousePose.playing(p, partial);
		if (play != null && play[0] >= Multimouse.ANIM_LASH && play[0] <= Multimouse.ANIM_LASH_3 && !firstPerson) {
			float age = play[1];
			float out = Mth.clamp(age / 4f, 0f, 1f) * Mth.clamp((MultimouseConfig.LASH_SWING_TICKS - age) / 4f, 0f, 1f);
			if (out > 0.02f) {
				Vec3 look = p.getViewVector(partial);
				Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
				double dir = play[0] == Multimouse.ANIM_LASH_2 ? -1 : 1;
				double reach = MultimouseConfig.LASH_REACH * 0.75 * p.getScale() * out;
				Vec3 prev = hand;
				for (int i = 1; i <= 8; i++) {
					double f = i / 8.0;
					double sweep = Math.sin(f * Math.PI) * 0.9 * (1 - Mth.clamp((age - 2) / 3f, 0f, 1f) * 2) * dir;
					Vec3 q = hand.add(look.scale(reach * f)).add(side.scale(sweep * p.getScale()))
							.add(0, play[0] == Multimouse.ANIM_LASH_3 ? -Math.sin(f * Math.PI) * 0.5 * p.getScale() : 0, 0);
					segment(vc, pose, prev, q, thick, MultimouseRender.PINK, light);
					prev = q;
				}
			}
		}
		// the twirl: a loop spinning round her
		if (Multimouse.twirling(p)) {
			Vec3 c = p.getPosition(partial).add(0, p.getBbHeight() * 0.95, 0);
			double r = MultimouseConfig.TWIRL_RADIUS * 0.5 * Math.max(0.35, p.getScale());
			loop(vc, pose, c, r, 18, thick, light, t * 0.9f);
			rope(vc, pose, hand, c.add(Math.cos(t * 0.9) * r, 0, Math.sin(t * 0.9) * r), 0.0, thick, light);
		}
		// the snares on the ground
		for (int i = 0; i < MultimouseConfig.TRAP_MAX; i++) {
			Vec3 trap = Multimouse.vec(p, Multimouse.X_TRAP + i);
			if (trap != null) {
				int tl = LevelRenderer.getLightColor(p.level(), BlockPos.containing(trap.add(0, 0.2, 0)));
				loop(vc, pose, trap.add(0, 0.05, 0), 0.38, 12, 0.03f, tl, 0f);
				loop(vc, pose, trap.add(0, 0.05, 0), 0.26, 10, 0.025f, tl, 0.3f);
				segment(vc, pose, trap.add(0.38, 0.05, 0), trap.add(0.62, 0.05, 0.18), 0.03f, MultimouseRender.PINK_L, tl);
			}
		}
	}

	/** A rope from a to b, sagging {@code sag} blocks at the middle. */
	private static void rope(VertexConsumer vc, PoseStack pose, Vec3 a, Vec3 b, double sag, float thick, int light) {
		int n = 10;
		Vec3 prev = a;
		for (int i = 1; i <= n; i++) {
			double f = i / (double) n;
			Vec3 q = a.lerp(b, f).add(0, -Math.sin(f * Math.PI) * sag, 0);
			segment(vc, pose, prev, q, thick, i % 2 == 0 ? MultimouseRender.PINK : MultimouseRender.PINK_L, light);
			prev = q;
		}
	}

	private static void loop(VertexConsumer vc, PoseStack pose, Vec3 c, double r, int n, float thick, int light, float phase) {
		Vec3 prev = null;
		for (int i = 0; i <= n; i++) {
			double a = phase + Math.PI * 2 * i / n;
			Vec3 q = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
			if (prev != null) {
				segment(vc, pose, prev, q, thick, i % 2 == 0 ? MultimouseRender.PINK : MultimouseRender.PINK_D, light);
			}
			prev = q;
		}
	}

	/** One piece of rope in world space (the pose is already camera-relative). */
	private static void segment(VertexConsumer vc, PoseStack pose, Vec3 a, Vec3 b, float thick, int rgb, int light) {
		MultimouseRender.segment(vc, pose, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, thick, rgb, light);
	}

	// ---------------------------------------------------------------- Mouse Hearing

	private static void hearing(PoseStack pose, MultiBufferSource buffers, Minecraft mc, float partial) {
		VertexConsumer lines = buffers.getBuffer(XRAY_LINES);
		double range = MultimouseConfig.HEARING_RANGE;
		float t = mc.level.getGameTime() + partial;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == mc.player || e instanceof ArmorStand || !le.isAlive() || e.distanceToSqr(mc.player) > range * range
					|| (e instanceof com.projecthero.mod.miraculous.multimouse.entity.MiniMouseEntity mini && mc.player.getUUID().equals(mini.getOwnerUUID()))) {
				continue;
			}
			boolean hostile = e instanceof Enemy;
			int rgb = hostile ? 0xFF4A7A : e instanceof Player ? 0xFFE0F0 : 0xF7A8D6;
			float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
			// the rings ride its footsteps: faster as it walks, a slow breath when it stands
			float steps = le.walkAnimation.position(partial) * 0.35f + t * 0.03f;
			Vec3 c = e.getPosition(partial).add(0, 0.05, 0);
			double base = e.getBbWidth() * 0.6 + 0.15;
			for (int k = 0; k < 3; k++) {
				float ph = (steps + k / 3f) % 1f;
				float a = (1f - ph) * 0.9f;
				double rad = base + ph * (0.9 + e.getBbWidth());
				int n = 20;
				for (int i = 0; i < n; i++) {
					double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
					float x0 = (float) (c.x + Math.cos(a0) * rad), z0 = (float) (c.z + Math.sin(a0) * rad);
					float x1 = (float) (c.x + Math.cos(a1) * rad), z1 = (float) (c.z + Math.sin(a1) * rad);
					float y = (float) c.y + ph * e.getBbHeight() * 0.3f;
					float nx = x1 - x0, nz = z1 - z0;
					float len = Mth.sqrt(nx * nx + nz * nz);
					lines.addVertex(pose.last(), x0, y, z0).setColor(r, g, b, a).setNormal(pose.last(), nx / len, 0, nz / len);
					lines.addVertex(pose.last(), x1, y, z1).setColor(r, g, b, a).setNormal(pose.last(), nx / len, 0, nz / len);
				}
			}
		}
	}
}
