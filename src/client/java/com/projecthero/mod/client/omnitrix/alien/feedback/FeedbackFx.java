package com.projecthero.mod.client.omnitrix.alien.feedback;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.FeedbackCables;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.feedback.FeedbackKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Feedback's rendered energy, on Brainstorm's lightning ({@link BrainstormFx#bolt}: jagged additive ribbons with a white-hot
 * core) in his electric blue (v0.17.2 round 3, user: "make his lightning blue, his lightning and powers are blue in the show"): his fingertip bolts and the thick Redirect / Cannon / Discharge ones (from his hand, or from
 * an entity the carrier names), and the "suck" -- energy pulled INTO him, drawn from what he absorbs or drains to the nearest of his
 * plugs. Round 3 adds Surge Flight's jets (energy shot out of his palms behind him) and the thin arcs that crawl in to his plugs while he
 * braces Absorb.
 */
public final class FeedbackFx {
	private FeedbackFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("feedback_arc", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("feedback_big", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("feedback_suck", FeedbackFx::suck);
		WorldRenderEvents.AFTER_ENTITIES.register(FeedbackFx::world);
	}

	// ---------------------------------------------------------------- Surge Flight and the Absorb brace

	/** Called from the kit's body pass with the bones posed: a tapering jet out of each palm, along the arm. */
	public static void jets(PoseStack pose, MultiBufferSource buffers, PlayerModel<?> model, float age) {
		jet(pose, buffers, model.rightArm, -1f / 16f, age);
		jet(pose, buffers, model.leftArm, 1f / 16f, age + 3f);
	}

	private static void jet(PoseStack pose, MultiBufferSource buffers, ModelPart arm, float x, float age) {
		AlienParts.on(pose, arm, () -> {
			pose.translate(x, 10.5f / 16f, 0f);
			float len = 1.9f + Mth.sin(age * 1.9f) * 0.25f + Mth.sin(age * 3.1f) * 0.15f;
			VertexConsumer vc = PyroDraw.additive(buffers);
			PyroDraw.cone(pose, vc, 0.2f, len, PyroDraw.dim(FeedbackKit.COLOUR, 0.75f), 0x000000);
			PyroDraw.cone(pose, vc, 0.09f, len * 0.62f, PyroDraw.dim(FeedbackKit.WHITE, 0.95f), PyroDraw.dim(FeedbackKit.COLOUR, 0.25f));
			PyroDraw.sphere(pose, vc, 0.13f, 0xFFE8F6FF, 0xFF1E5EC0, 0.1f, age, PyroDraw.FULL);
		});
	}

	private static void world(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (var pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || !FeedbackKit.isFeedback(p) || p.isInvisible()) {
				continue;
			}
			int flags = Omnitrix.state(p).kitFlags;
			if ((flags & FeedbackKit.F_FLY) != 0) {
				surge(context.matrixStack(), context.consumers(), p, partial);
			}
			if ((flags & FeedbackKit.F_ABSORB) != 0) {
				drawIn(context.matrixStack(), context.consumers(), p, partial);
			}
		}
	}

	/** Bolts torn out of the jets behind him; in his own view, thin arcs streaming back past the edges of the screen. */
	private static void surge(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, float partial) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 look = p.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		Vec3 up = right.cross(look).normalize();
		Random rnd = new Random(p.getId() * 13L + p.level().getGameTime() / 2);
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			Vec3 eye = p.getEyePosition(partial);
			for (int i = 0; i < 4; i++) {
				float a = rnd.nextFloat() * Mth.TWO_PI;
				Vec3 side = right.scale(Mth.cos(a) * 1.8).add(up.scale(Mth.sin(a) * 1.2));
				BrainstormFx.bolt(pose, buffers, eye.add(look.scale(3.5)).add(side), eye.subtract(look.scale(0.5)).add(side), rnd.nextLong(), 0.022f, 0.5f, FeedbackKit.COLOUR);
			}
			return;
		}
		Vec3 c = p.getPosition(partial).add(0, p.getBbHeight() * 0.45, 0);
		for (int s = -1; s <= 1; s += 2) {
			Vec3 from = c.subtract(look.scale(0.5)).add(right.scale(s * 0.42));
			for (int i = 0; i < 2; i++) {
				Vec3 to = from.subtract(look.scale(2.4 + rnd.nextDouble() * 1.6)).add(right.scale((rnd.nextDouble() - 0.5) * 0.9)).add(up.scale((rnd.nextDouble() - 0.5) * 0.9));
				BrainstormFx.bolt(pose, buffers, from, to, rnd.nextLong(), 0.05f, 0.85f, FeedbackKit.COLOUR);
			}
		}
	}

	/** While he braces: faint arcs crawling in from the air round him to his plugs (his hands, in his own view). */
	private static void drawIn(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer p, float partial) {
		Minecraft mc = Minecraft.getInstance();
		boolean own = p == mc.player && mc.options.getCameraType().isFirstPerson();
		Random rnd = new Random(p.getId() * 29L + p.level().getGameTime() / 3);
		Vec3 c = p.getPosition(partial).add(0, p.getBbHeight() * 0.6, 0);
		Vec3 look = p.getViewVector(partial);
		for (int r = 0; r < 3; r++) {
			Vec3 to = own ? BrainstormFx.hand(p, partial) : FeedbackCables.tip(p, r, partial);
			if (to == null) {
				continue;
			}
			double a = rnd.nextDouble() * Math.PI * 2;
			double out = own ? 1.0 : 1.5;
			Vec3 from = (own ? to.add(look.scale(1.6)) : c).add(Math.cos(a) * out, (rnd.nextDouble() - 0.4) * out, Math.sin(a) * out);
			BrainstormFx.bolt(pose, buffers, from, to, rnd.nextLong(), own ? 0.012f : 0.018f, 0.45f, FeedbackKit.COLOUR);
		}
	}

	private static float fade(AlienProjectileEntity e, float partial, float life) {
		return Mth.clamp(1f - (e.tickCount + partial) / life, 0f, 1f);
	}

	/** A bolt ending here; colour 0 = from the owner's hand, n = from entity n - 1. */
	private static void arc(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean big) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 end = e.getPosition(partial);
		Vec3 start = null;
		int c = e.colour();
		if (c > 0) {
			Entity from = mc.level.getEntity(c - 1);
			if (from != null) {
				start = from.getPosition(partial).add(0, from.getBbHeight() * 0.55, 0);
			}
		} else if (e.getOwner() instanceof Player owner) {
			start = BrainstormFx.hand(owner, partial);
		}
		if (start == null) {
			return;
		}
		float k = 0.55f + 0.45f * fade(e, partial, big ? 12f : 7f);
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		long seed = e.getId() * 7919L + (e.tickCount / 2);
		BrainstormFx.bolt(pose, buffers, start, end, seed, big ? 0.26f : 0.1f, k, FeedbackKit.COLOUR);
		BrainstormFx.bolt(pose, buffers, start, end, seed + 5, big ? 0.12f : 0.04f, k * 0.7f, 0xBFE6FF);
		pose.popPose();
	}

	/** Energy pulled from the carrier's spot into the owner's nearest plug (his chest if the cords aren't simulated yet). */
	private static void suck(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		if (!(e.getOwner() instanceof AbstractClientPlayer owner)) {
			return;
		}
		Vec3 from = e.getPosition(partial);
		Vec3 to = null;
		double best = Double.MAX_VALUE;
		for (int r = 0; r < 3; r++) {
			Vec3 t = FeedbackCables.tip(owner, r, partial);
			if (t != null && t.distanceToSqr(from) < best) {
				best = t.distanceToSqr(from);
				to = t;
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (to == null || (owner == mc.player && mc.options.getCameraType().isFirstPerson())) {
			to = BrainstormFx.hand(owner, partial);
		}
		float k = 0.5f + 0.5f * fade(e, partial, 6f);
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		long seed = e.getId() * 104729L + (e.tickCount / 2);
		BrainstormFx.bolt(pose, buffers, from, to, seed, 0.08f, k, FeedbackKit.COLOUR);
		BrainstormFx.bolt(pose, buffers, from, to, seed + 3, 0.03f, k * 0.8f, 0xFFFFFF);
		pose.popPose();
	}
}
