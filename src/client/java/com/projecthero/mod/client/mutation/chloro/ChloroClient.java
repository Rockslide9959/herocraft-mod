package com.projecthero.mod.client.mutation.chloro;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.MutationOverlays;
import com.projecthero.mod.client.mutation.MutationPose;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.hero.power.p22.PlantEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.17.1 Chlorokinesis revamp -- the client: casting poses, the overlays (Verdant Form's bark skin and leaf crown, the
 * Overgrowth gather's green eyes, the Leaf Glide canopy), the effect / Treant renderers and the particles.
 */
public final class ChloroClient {
	private static final ResourceLocation BARK = ProjectHeroMod.id("textures/entity/mutation/p22_bark.png");
	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private ChloroClient() {
	}

	/** From {@code RevampClientE.init()}. */
	public static void init() {
		registerPoses();
		registerOverlays();
		EntityRendererRegistry.register(PlantEntities.FX, ChloroFxRenderer::new);
		EntityRendererRegistry.register(PlantEntities.TREANT, TreantRenderer::new);
		ChloroParticleClient.register();
	}

	// ================================================================== poses

	private static float[] at(int tick, float[] pose) {
		float[] f = pose.clone();
		f[0] = tick;
		return f;
	}

	private static float[] zero(int tick) {
		return at(tick, Z);
	}

	/** {rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX} -- see {@link MutationPose}. */
	private static float[] p(float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY, float rL, float lL, float h) {
		return new float[] { 0, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static void registerPoses() {
		// R: Thorn Volley -- a flick of the right hand along the look
		float[] flickBack = p(0.4f, 0.3f, 0.25f, -0.3f, 0, -0.2f, 0.05f, 0.35f, 0.2f, -0.2f, 0);
		float[] flick = p(AIM, AIM, 0, -0.5f, 0, -0.35f, 0.08f, -0.25f, -0.2f, 0.2f, 0);
		MutationPose.register("p22.volley", new float[][] { zero(0), at(2, flickBack), at(4, flick), at(9, flick), zero(13) });
		// Shift+R: Vine Lash -- the arm comes over the top and cracks forward, the body twisting through it
		float[] lashUp = p(-2.9f, 0.2f, 0.35f, -0.2f, 0, -0.4f, -0.1f, 0.45f, 0.25f, -0.25f, -0.1f);
		float[] lashDown = p(-1.0f, -0.55f, 0.05f, 0.3f, 0, -0.3f, 0.25f, -0.5f, -0.3f, 0.3f, 0.1f);
		MutationPose.register("p22.lash", new float[][] { zero(0), at(3, lashUp), at(6, lashDown), at(11, lashDown), zero(15) });
		// G: Root Snare -- both palms heave up from the ground
		float[] heaveLow = p(0.35f, 0, 0.55f, 0.35f, 0, -0.55f, 0.35f, 0, 0.35f, -0.25f, 0.25f);
		float[] heaveUp = p(-1.5f, 0, 0.45f, -1.5f, 0, -0.45f, -0.12f, 0, -0.1f, 0.1f, -0.1f);
		MutationPose.register("p22.snare", new float[][] { zero(0), at(4, heaveLow), at(8, heaveUp), at(14, heaveUp), zero(18) });
		// X: Vine Grapple -- the hand thrown out along the look, the other arm swept back
		float[] grapple = p(AIM, AIM, 0, 0.55f, 0, -0.35f, -0.12f, -0.2f, 0.35f, -0.3f, -0.1f);
		MutationPose.register("p22.grapple", new float[][] { zero(0), at(2, grapple), at(12, grapple), zero(16) });
		// Shift+X: Leaf Glide -- both hands up, holding the canopy (a stance loop)
		float[] glide = p(-2.85f, 0, 0.4f, -2.85f, 0, -0.4f, 0.0f, 0, 0.12f, -0.12f, 0);
		float[] glide2 = p(-2.75f, 0, 0.46f, -2.75f, 0, -0.46f, 0.02f, 0, 0.18f, -0.06f, 0);
		MutationPose.registerLoop("p22.glide", 4, new float[][] { zero(0), at(4, glide), at(16, glide2), at(28, glide) });
		// Z: Overgrowth -- the slam that breaks the gather
		float[] raise = p(-2.9f, 0, 0.3f, -2.9f, 0, -0.3f, -0.15f, 0, 0, 0, -0.2f);
		float[] slam = p(-0.4f, 0, 0.35f, -0.4f, 0, -0.35f, 0.55f, 0, -0.5f, 0.35f, 0.35f);
		MutationPose.register("p22.slam", new float[][] { zero(0), at(2, raise), at(5, slam), at(14, slam), zero(19) });
		// Shift+Z / hold G: a summon -- the hand sweeps up from the ground
		float[] sumLow = p(-0.5f, 0, 0.35f, 0.25f, 0, -0.3f, 0.3f, 0, 0.3f, -0.2f, 0.3f);
		float[] sumHigh = p(-2.4f, -0.2f, 0.3f, -0.4f, 0, -0.5f, -0.1f, 0, 0, 0, -0.15f);
		MutationPose.register("p22.summon", new float[][] { zero(0), at(3, sumLow), at(8, sumHigh), at(13, sumHigh), zero(17) });
		// V: Bloom Burst -- arms flung up and wide
		float[] bloom = p(-2.5f, 0, 0.75f, -2.5f, 0, -0.75f, -0.18f, 0, 0, 0, -0.25f);
		MutationPose.register("p22.bloom", new float[][] { zero(0), at(4, bloom), at(14, bloom), zero(19) });
		// Shift+V: Living Architecture (wall / ladder) -- both palms out
		float[] shape = p(AIM, AIM, 0, AIM, AIM, 0, 0.12f, 0, -0.2f, 0.2f, 0);
		MutationPose.register("p22.shape", new float[][] { zero(0), at(3, shape), at(12, shape), zero(16) });
		// Shift+C: Root Sense -- down on one knee, a palm on the ground
		float[] kneel = p(-0.45f, 0, 0.15f, 0.2f, 0, -0.25f, 0.55f, 0, -1.25f, 0.35f, 0.45f);
		MutationPose.register("p22.sense", new float[][] { zero(0), at(4, kneel), at(22, kneel), zero(28) });
		// C: Verdant Form -- a flex as the bark spreads
		float[] flex = p(0.25f, 0, 0.95f, 0.25f, 0, -0.95f, -0.15f, 0, 0.1f, -0.1f, -0.2f);
		MutationPose.register("p22.verdant", new float[][] { zero(0), at(4, flex), at(10, flex), zero(15) });
	}

	// ================================================================== overlays

	private static void registerOverlays() {
		// Verdant Form: the bark-and-leaf skin, a crown of leaves, leaves on the shoulders
		MutationOverlays.register("p22.blessing", ctx -> MutationRender.shell(ctx, BARK, MutationRender.Shell.THIN, 0xFFFFFFFF, false));
		MutationOverlays.register("p22.blessing", ChloroClient::verdantLeaves);
		// Overgrowth gather: green eyes and the bark pulsing in
		MutationOverlays.register("p22.overgrowth", ctx -> {
			MutationRender.eyes(ctx, MutationRender.argb(255, 120, 255, 90));
			int a = (int) (80 + 120 * MutationRender.pulse(ctx, 20));
			MutationRender.shell(ctx, BARK, MutationRender.Shell.THIN, MutationRender.argb(a, 255, 255, 255), false);
		});
		// Leaf Glide: the canopy
		MutationOverlays.register("p22.glide", ChloroClient::canopy);
	}

	private static void verdantLeaves(MutationOverlays.Context ctx) {
		PoseStack pose = ctx.pose();
		VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.LEAF));
		RandomSource r = RandomSource.create(4242L);
		float sway = Mth.sin((ctx.ageInTicks() + ctx.partialTick()) * 0.1f) * 0.04f;
		pose.pushPose();
		ctx.model().head.translateAndRotate(pose);
		PoseStack.Pose last = pose.last();
		// model space: y points down; the top of the head is y = -0.5, the front z = -0.25
		int n = 9;
		for (int i = 0; i < n; i++) {
			float a = Mth.TWO_PI * i / n + r.nextFloat() * 0.3f;
			Vector3f out = new Vector3f(Mth.cos(a), 0, Mth.sin(a));
			Vector3f base = new Vector3f(out).mul(0.2f).add(0, -0.47f, 0);
			Vector3f along = new Vector3f(out).mul(0.75f).add(sway, -0.8f, 0).normalize();
			float len = 0.26f + r.nextFloat() * 0.1f;
			ChloroMesh.leaf(vc, last, base, along, new Vector3f(-out.z, 0, out.x), len, len * 0.75f, 255, 255, 255, 255, ctx.light());
		}
		pose.popPose();
		for (int s = 0; s < 2; s++) {
			pose.pushPose();
			(s == 0 ? ctx.model().rightArm : ctx.model().leftArm).translateAndRotate(pose);
			PoseStack.Pose arm = pose.last();
			float sx = s == 0 ? -1 : 1;
			for (int i = 0; i < 2; i++) {
				Vector3f base = new Vector3f(sx * 0.14f, -0.08f + i * 0.12f, 0.06f * (i * 2 - 1));
				Vector3f along = new Vector3f(sx * 0.7f, -0.9f, (i * 2 - 1) * 0.3f).normalize();
				ChloroMesh.leaf(vc, arm, base, along, new Vector3f(0, 0, 1), 0.22f, 0.17f, 255, 255, 255, 255, ctx.light());
			}
			pose.popPose();
		}
	}

	/** Two wings of big overlapping leaves spread from the shoulder blades -- over the back, so above you in a glide. */
	private static void canopy(MutationOverlays.Context ctx) {
		PoseStack pose = ctx.pose();
		VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.LEAF));
		float t = ctx.ageInTicks() + ctx.partialTick();
		pose.pushPose();
		ctx.model().body.translateAndRotate(pose);
		PoseStack.Pose last = pose.last();
		// body space: y down from the neck, +z is the back
		for (int s = -1; s <= 1; s += 2) {
			for (int i = 0; i < 8; i++) {
				float th = Mth.lerp(i / 7f, -0.35f, 1.2f); // from a little below level to steeply up
				float flap = Mth.sin(t * 0.25f + i * 0.4f) * 0.05f;
				Vector3f base = new Vector3f(s * 0.1f, 0.14f + i * 0.012f, 0.22f + i * 0.004f);
				Vector3f along = new Vector3f(s * Mth.cos(th), -Mth.sin(th) + flap, 0.18f).normalize();
				Vector3f side = new Vector3f(0, 0.15f, 1).cross(along).normalize();
				float len = 1.9f - Math.abs(i - 3) * 0.14f;
				int shade = 205 + i * 6;
				ChloroMesh.leaf(vc, last, base, along, side, len, 0.95f, shade, 255, shade, 255, ctx.light());
			}
		}
		// a centre leaf standing up off the back
		ChloroMesh.leaf(vc, last, new Vector3f(0, 0.1f, 0.24f), new Vector3f(0, -1, 0.25f), new Vector3f(1, 0, 0), 1.5f, 0.9f, 230, 255, 230,
				255, ctx.light());
		pose.popPose();
	}
}
