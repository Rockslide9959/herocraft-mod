package com.projecthero.mod.client.miraculous.queenbee;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.miraculous.queenbee.QueenBee;
import com.projecthero.mod.miraculous.queenbee.QueenBeeConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Queen Bee's world visuals ({@code AFTER_ENTITIES}), for every viewer: the top's string out of her right hand
 * (to the thrown top, the swing's hook, the tangled creature with its wraps), the thrown top itself, the top hovering
 * over her shoulder for the Stinger Volley and the stingers it fires (short black-and-gold darts), and Venom's paralysis
 * (slow rings of honey light round the paralysed creature).
 */
public final class QueenBeeWorldFx {
	private QueenBeeWorldFx() {
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
			if (QueenBee.is(p) && !p.isInvisible()) {
				draw(pose, buffers, p, partial, p == mc.player && mc.options.getCameraType().isFirstPerson());
			}
		}
		pose.popPose();
	}

	/** Her right hand, in the world. */
	static Vec3 hand(Player p, float partial) {
		Vec3 pos = p.getPosition(partial);
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		return pos.add(0, p.getBbHeight() * 0.62, 0).add(right.scale(0.36)).add(fwd.scale(0.25));
	}

	private static void draw(PoseStack pose, MultiBufferSource buffers, Player p, float partial, boolean firstPerson) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(com.projecthero.mod.client.maxsteel.TurboDraw.WHITE));
		long now = p.level().getGameTime();
		float t = now + partial;
		Vec3 hand = firstPerson ? p.getEyePosition(partial).add(p.getViewVector(partial).scale(0.4)).add(0, -0.25, 0) : hand(p, partial);
		int light = LevelRenderer.getLightColor(p.level(), BlockPos.containing(hand));
		float thick = 0.022f;
		// the thrown top and its string
		Vec3 top = QueenBee.vec(p, QueenBee.X_TOP);
		if (top != null && QueenBee.throwing(p)) {
			string(vc, pose, hand, top, 0.15, thick, light);
			topAt(pose, buffers, top, t * 1.5f, light(p, top), QueenBee.venomArmed(p) ? 1f : 0f, 1.0f);
		}
		// the swing's string to its hook
		Vec3 hook = QueenBee.vec(p, QueenBee.X_SWING);
		if (hook != null && QueenBee.running(p, QueenBee.V_SWING)) {
			string(vc, pose, hand, hook, 0.0, thick, light);
		}
		// the tangle: to the creature, wrapped round it
		if (QueenBee.running(p, QueenBee.V_TANGLE)) {
			Entity e = p.level().getEntity((int) QueenBee.val(p, QueenBee.V_TANGLE_ID) - 1);
			if (e != null) {
				Vec3 at = e.getPosition(partial);
				string(vc, pose, hand, at.add(0, e.getBbHeight() * 0.5, 0), 0.3, thick, light);
				int el = light(p, at.add(0, 0.5, 0));
				for (int k = 0; k < 3; k++) {
					loop(vc, pose, at.add(0, e.getBbHeight() * (0.3 + 0.2 * k), 0), e.getBbWidth() * 0.62 + 0.06, 12, thick, el,
							(k % 2 == 0 ? 1 : -1) * t * 0.05f, QueenBeeRender.STRING);
				}
			}
		}
		// the volley: the top hovering over her right shoulder, the stingers it fires
		if (QueenBee.volley(p)) {
			Vec3 at = com.projecthero.mod.miraculous.queenbee.QueenBeeMoves.volleyTop(p).add(p.getPosition(partial).subtract(p.position()));
			at = at.add(0, Math.sin(t * 0.3) * 0.05, 0);
			topAt(pose, buffers, at, t * 1.4f, light(p, at), 0f, 0.9f);
			Vec3 to = QueenBee.vec(p, QueenBee.X_STING);
			float age = t - QueenBee.val(p, QueenBee.V_STING_AT);
			if (to != null && age >= 0f && age < 4f) {
				float f0 = Mth.clamp(age / 3f, 0f, 1f);
				float f1 = Mth.clamp((age - 0.6f) / 3f, 0f, 1f);
				Vec3 a = at.lerp(to, f1), b = at.lerp(to, f0);
				Vec3 d = b.subtract(a);
				if (d.lengthSqr() > 1.0e-4) {
					Vec3 mid = a.lerp(b, 0.6);
					segment(vc, pose, a, mid, 0.03f, QueenBeeRender.GOLD, LightTexture.FULL_BRIGHT);
					segment(vc, pose, mid, b, 0.02f, QueenBeeRender.BLACK, LightTexture.FULL_BRIGHT);
				}
			}
		}
		// Venom's paralysis: honey rings turning slowly round the frozen creature
		if (QueenBee.running(p, QueenBee.V_PARALYSED)) {
			Entity e = p.level().getEntity((int) QueenBee.val(p, QueenBee.V_PARALYSED_ID) - 1);
			if (e != null) {
				Vec3 at = e.getPosition(partial);
				float pulse = 0.5f + 0.5f * Mth.sin(t * 0.25f);
				int col = pulse > 0.5f ? QueenBeeRender.VENOM : QueenBeeRender.GOLD_L;
				for (int k = 0; k < 4; k++) {
					double h = e.getBbHeight() * (0.15 + 0.25 * k);
					double r = e.getBbWidth() * 0.7 + 0.12 + 0.04 * Math.sin(t * 0.2 + k);
					loop(vc, pose, at.add(0, h, 0), r, 6, 0.028f, LightTexture.FULL_BRIGHT, t * 0.02f * (k % 2 == 0 ? 1 : -1) + k * 0.5f, col);
				}
			}
		}
		// Top Strike: the top out on its string along her look while a swing plays
		float[] play = QueenBeePose.playing(p, partial);
		if (play != null && play[0] >= QueenBee.ANIM_STRIKE && play[0] <= QueenBee.ANIM_STRIKE_3 && !firstPerson) {
			float age = play[1];
			float out = Mth.clamp(age / 3f, 0f, 1f) * Mth.clamp((QueenBeeConfig.STRIKE_SWING_TICKS - age) / 4f, 0f, 1f);
			if (out > 0.05f) {
				Vec3 look = p.getViewVector(partial);
				Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
				double dir = play[0] == QueenBee.ANIM_STRIKE_2 ? -1 : 1;
				double sweep = (1 - Mth.clamp((age - 1f) / 3f, 0f, 1f) * 2) * dir;
				Vec3 tip = hand.add(look.scale(QueenBeeConfig.STRIKE_REACH * 0.7 * out)).add(side.scale(sweep * 0.8))
						.add(0, play[0] == QueenBee.ANIM_STRIKE_3 ? -0.3 * out : 0, 0);
				string(vc, pose, hand, tip, 0.0, thick, light);
				topAt(pose, buffers, tip, t * 1.5f, light(p, tip), QueenBee.venomArmed(p) ? 1f : 0f, 0.8f);
			}
		}
	}

	private static int light(Player p, Vec3 at) {
		return LevelRenderer.getLightColor(p.level(), BlockPos.containing(at));
	}

	/** The top in the world at {@code at}, point down, spinning. */
	private static void topAt(PoseStack pose, MultiBufferSource buffers, Vec3 at, float spin, int light, float venom, float scale) {
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(14f));
		pose.scale(scale, scale, scale);
		QueenBeeRender.renderTop(pose, buffers, light, spin, venom);
		pose.popPose();
	}

	/** The top's string from a to b, sagging {@code sag} blocks at the middle. */
	private static void string(VertexConsumer vc, PoseStack pose, Vec3 a, Vec3 b, double sag, float thick, int light) {
		int n = 10;
		Vec3 prev = a;
		for (int i = 1; i <= n; i++) {
			double f = i / (double) n;
			Vec3 q = a.lerp(b, f).add(0, -Math.sin(f * Math.PI) * sag, 0);
			segment(vc, pose, prev, q, thick, QueenBeeRender.STRING, light);
			prev = q;
		}
	}

	private static void loop(VertexConsumer vc, PoseStack pose, Vec3 c, double r, int n, float thick, int light, float phase, int rgb) {
		Vec3 prev = null;
		for (int i = 0; i <= n; i++) {
			double a = phase + Math.PI * 2 * i / n;
			Vec3 q = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
			if (prev != null) {
				segment(vc, pose, prev, q, thick, rgb, light);
			}
			prev = q;
		}
	}

	private static void segment(VertexConsumer vc, PoseStack pose, Vec3 a, Vec3 b, float thick, int rgb, int light) {
		QueenBeeRender.segment(vc, pose, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, thick, rgb, light);
	}
}
