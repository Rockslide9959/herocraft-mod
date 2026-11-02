package com.projecthero.mod.client.omnitrix.alien.goop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, basic Goop): his rendered acid -- wobbling green blobs in flight (a glossy shell, a darker
 * core, a faint glow, stretched along the flight), the acid puddles (a translucent green pool with a darker rim and bubbles that
 * swell and pop) and the knocked-off anti-gravity projector lying where it landed, its light blinking.
 */
public final class GoopFx {
	private GoopFx() {
	}

	private static boolean registered;
	public static final ResourceLocation EXTRAS = com.projecthero.mod.ProjectHeroMod.id("textures/entity/omnitrix/goop_extras.png");
	static final float[] METAL = { 0f, 0f, 16 / 64f, 16 / 64f }, LIGHT = { 16 / 64f, 0f, 24 / 64f, 8 / 64f }, RIM = { 24 / 64f, 0f, 32 / 64f, 8 / 64f };

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("goop_spit", (e, partial, pose, buffers) -> blob(e, partial, pose, buffers, 0.5f));
		OmnitrixRenderers.projectileLook("goop_glob", (e, partial, pose, buffers) -> blob(e, partial, pose, buffers, 0.55f));
		OmnitrixRenderers.projectileLook("goop_cannon", (e, partial, pose, buffers) -> blob(e, partial, pose, buffers, 0.6f));
		OmnitrixRenderers.projectileLook("goop_puddle", GoopFx::puddle);
		OmnitrixRenderers.projectileLook("goop_projector", GoopFx::projector);
	}

	private static int light(AlienProjectileEntity e) {
		return LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.position().add(0, 0.3, 0)));
	}

	/** A blob of acid slime, stretched along its flight and wobbling. */
	private static void blob(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, float k) {
		float t = e.tickCount + partial;
		Vec3 v = e.getDeltaMovement();
		float r = e.size() * k;
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		PyroDraw.alignY(pose, v.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : v);
		float stretch = 1.15f + 0.12f * Mth.sin(t * 1.3f);
		pose.scale(1f / Mth.sqrt(stretch), stretch, 1f / Mth.sqrt(stretch));
		PyroDraw.sphere(pose, PyroDraw.solidBuffer(buffers), r, 0xFF9CF060, 0xFF3A9A1A, 0.18f, t * 2.5f, light(e));
		PyroDraw.sphere(pose, PyroDraw.additive(buffers), r * 1.35f, 0xFF1E4A0C, 0xFF0A1A04, 0.1f, t, PyroDraw.FULL);
		pose.popPose();
	}

	/** An acid puddle (radius = size; colour = its life in ticks): a translucent green pool, a darker rim, bubbles popping. */
	private static void puddle(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float life = Math.max(20, e.colour());
		float in = Mth.clamp(t / 5f, 0f, 1f) * Mth.clamp((life - t) / 20f, 0f, 1f);
		if (in <= 0.01f) {
			return;
		}
		float r = e.size() * (0.6f + 0.4f * Mth.clamp(t / 6f, 0f, 1f));
		int a = (int) (190 * in);
		pose.pushPose();
		pose.translate(0, 0.03, 0);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		PyroDraw.annulus(pose, vc, 0f, r * 0.85f, (a << 24) | 0x5CC42A, (a << 24) | 0x3A9A1A, 0f);
		PyroDraw.annulus(pose, vc, r * 0.85f, r, (a << 24) | 0x2E7A14, ((a / 4) << 24) | 0x2E7A14, 0.005f);
		pose.popPose();
		// bubbles: little spheres that swell and pop, each on its own clock
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		int light = light(e);
		int n = (int) Math.min(10, 2 + r * 1.5f);
		for (int i = 0; i < n; i++) {
			float ph = ((t * 0.06f + i * 0.37f) % 1f);
			float h1 = Mth.frac(Mth.sin(i * 12.9898f + e.getId()) * 43758.545f), h2 = Mth.frac(Mth.sin(i * 78.233f + e.getId()) * 12345.678f);
			double ang = h1 * Math.PI * 2, d = Math.sqrt(h2) * r * 0.8;
			float br = 0.05f + 0.12f * ph * in;
			pose.pushPose();
			pose.translate(Math.cos(ang) * d, 0.04, Math.sin(ang) * d);
			PyroDraw.sphere(pose, solid, br, 0xFFB8F890, 0xFF5CC42A, 0.05f, t, light);
			pose.popPose();
		}
	}

	/** The knocked-off projector: the silver saucer tipped on the ground, its green light blinking. */
	private static void projector(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		pose.pushPose();
		pose.translate(0, 0.15, 0);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.mulPose(Axis.XP.rotationDegrees(18f));
		pose.scale(16f / 16f, 16f / 16f, 16f / 16f);
		drawSaucer(pose, buffers, light(e), (int) (t / 6) % 2 == 0 ? 1f : 0.25f);
		pose.popPose();
	}

	/** The projector saucer at the pose origin (pixels: 9 wide), {@code glow} its light 0..1. */
	public static void drawSaucer(PoseStack pose, MultiBufferSource buffers, int light, float glow) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		AlienParts.box(pose, vc, -4.5f, -0.75f, -4.5f, 4.5f, 0.75f, 4.5f, METAL[0], METAL[1], METAL[2], METAL[3], 0xFFFFFFFF, light);
		AlienParts.box(pose, vc, -5.2f, -0.35f, -5.2f, 5.2f, 0.35f, 5.2f, RIM[0], RIM[1], RIM[2], RIM[3], 0xFFFFFFFF, light);
		AlienParts.box(pose, vc, -2.5f, -1.75f, -2.5f, 2.5f, -0.75f, 2.5f, METAL[0], METAL[1], METAL[2], METAL[3], 0xFFFFFFFF, light);
		AlienParts.box(pose, vc, -2.0f, 0.75f, -2.0f, 2.0f, 1.6f, 2.0f, LIGHT[0], LIGHT[1], LIGHT[2], LIGHT[3], 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
		if (glow > 0.05f) {
			VertexConsumer add = PyroDraw.additive(buffers);
			AlienParts.box(pose, add, -2.6f, 0.6f, -2.6f, 2.6f, 2.0f, 2.6f, 0f, 0f, 1f, 1f, PyroDraw.dim(0x5AE028, glow * 0.8f), LightTexture.FULL_BRIGHT);
		}
	}
}
