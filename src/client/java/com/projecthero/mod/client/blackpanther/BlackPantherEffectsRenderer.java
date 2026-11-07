package com.projecthero.mod.client.blackpanther;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.data.BlackPantherState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.17.3: the kinetic shockwave (Pulse, Overload, Drop Strike landing): a thick purple ring racing out to the blast's radius,
 * fading as it goes, with a thinner white-violet ring just inside it. Plain translucent quads, both windings (additive light washes out to white over a bright sky).
 */
public final class BlackPantherEffectsRenderer {
	private static final float LIFE = 11f;
	private static final int SIDES = 48;

	private BlackPantherEffectsRenderer() {
	}

	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		PoseStack pose = context.matrixStack();
		Vec3 cam = context.camera().getPosition();
		for (Player player : mc.level.players()) {
			BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
			if (s == null || s.pulseStart == 0L || s.pulsePos.size() != 3) {
				continue;
			}
			float age = now - s.pulseStart + partial;
			if (age < 0f || age > LIFE) {
				continue;
			}
			float t = age / LIFE;
			float r = s.pulseRadius * (1f - (1f - t) * (1f - t));
			float fade = 1f - t;
			pose.pushPose();
			pose.translate(s.pulsePos.get(0) - cam.x, s.pulsePos.get(1) - cam.y, s.pulsePos.get(2) - cam.z);
			Matrix4f m = pose.last().pose();
			VertexConsumer vc = context.consumers().getBuffer(RenderType.debugQuads());
			ring(vc, m, Math.max(0.1f, r - 1.1f), r, 0.5f, 0.12f, 0.95f, 0.8f * fade);
			ring(vc, m, Math.max(0.05f, r - 0.22f), r, 0.82f, 0.62f, 1.0f, 0.9f * fade);
			pose.popPose();
		}
	}

	private static void ring(VertexConsumer vc, Matrix4f m, float inner, float outer, float r, float g, float b, float a) {
		for (int i = 0; i < SIDES; i++) {
			float a0 = i * Mth.TWO_PI / SIDES;
			float a1 = (i + 1) * Mth.TWO_PI / SIDES;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
			vc.addVertex(m, c0 * inner, 0f, s0 * inner).setColor(r, g, b, a * 0.2f);
			vc.addVertex(m, c0 * outer, 0f, s0 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c1 * outer, 0f, s1 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c1 * inner, 0f, s1 * inner).setColor(r, g, b, a * 0.2f);
			vc.addVertex(m, c1 * inner, 0f, s1 * inner).setColor(r, g, b, a * 0.2f);
			vc.addVertex(m, c1 * outer, 0f, s1 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c0 * outer, 0f, s0 * outer).setColor(r, g, b, a);
			vc.addVertex(m, c0 * inner, 0f, s0 * inner).setColor(r, g, b, a * 0.2f);
		}
	}
}
