package com.projecthero.mod.client.mutation.geo;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.MutationOverlays;
import com.projecthero.mod.client.mutation.MutationPose;
import com.projecthero.mod.hero.power.p05.GeoEntityTypes;
import com.projecthero.mod.hero.power.p05.GeokinesisHandlers;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.5 Geokinesis rework, client side: the mono HUD, the earthbending poses, the models drawn on the player (Earth
 * Armor's rock plates over the body, the Rock Surf slab under the feet), the renderers of the rock / boulder / raised-earth
 * entities, and Seismic Sense's outlines (this client only -- {@code GeoSeismicGlowMixin}, never the server glowing flag).
 */
public final class GeokinesisClient {
	private GeokinesisClient() {
	}

	/** From {@code RevampClientB.init()}. */
	public static void init() {
		com.projecthero.mod.client.gui.AbilityHudExtras.mono(GeokinesisHandlers.KEY);
		registerPoses();
		MutationOverlays.register("p05.armor", GeokinesisClient::armorPlates);
		MutationOverlays.register("p05.surf", GeokinesisClient::surfSlab);
		EntityRendererRegistry.register(GeoEntityTypes.GEO_ROCK, GeoRockRenderer::new);
		EntityRendererRegistry.register(GeoEntityTypes.GEO_BOULDER, GeoRockRenderer::new);
		EntityRendererRegistry.register(GeoEntityTypes.GEO_FORM, GeoFormRenderer::new);
	}

	/** Seismic Sense: whether this client draws {@code e} outlined (the local geokinetic's sense is running and it's in range). */
	public static boolean sensed(Entity e) {
		var self = Minecraft.getInstance().player;
		return self != null && GeokinesisHandlers.sensing(self) && GeokinesisHandlers.sensedBy(self, e);
	}

	// ================================================================== poses

	/** {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX} -- see {@link MutationPose}. */
	private static float[] f(int tick, float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY, float rL, float lL,
			float h) {
		return new float[] { tick, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static float[] z(int tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	private static void registerPoses() {
		// R: scoop low, then fling up along the aim as the rock flies
		MutationPose.register("p05.rock", new float[][] { z(0),
				f(2, 0.35f, 0.2f, 0.25f, -0.4f, 0, -0.15f, 0.3f, -0.3f, -0.3f, 0.3f, 0.2f),
				f(5, AIM, AIM, 0, 0.3f, 0, -0.2f, 0.05f, 0.25f, 0.2f, -0.2f, 0),
				f(10, AIM, AIM, 0, 0.3f, 0, -0.2f, 0.05f, 0.25f, 0.2f, -0.2f, 0), z(14) });
		// Shift+R held: both arms straight up under the boulder, back arched, knees braced
		MutationPose.registerLoop("p05.lift", 6, new float[][] { z(0),
				f(4, -2.2f, 0, 0.4f, -2.2f, 0, -0.4f, 0.35f, 0, -0.35f, 0.35f, 0.2f),
				f(10, -3.0f, 0, 0.35f, -3.0f, 0, -0.35f, -0.12f, 0, 0.15f, -0.15f, -0.25f),
				f(18, -2.95f, 0, 0.3f, -2.95f, 0, -0.3f, -0.15f, 0, 0.15f, -0.15f, -0.25f),
				f(26, -3.0f, 0, 0.35f, -3.0f, 0, -0.35f, -0.12f, 0, 0.15f, -0.15f, -0.25f) });
		// the throw: overhead, then both arms whip down and forward
		MutationPose.register("p05.heave", new float[][] { f(0, -3.0f, 0, 0.35f, -3.0f, 0, -0.35f, -0.12f, 0, 0.15f, -0.15f, -0.25f),
				f(3, -3.1f, 0, 0.3f, -3.1f, 0, -0.3f, -0.3f, 0, 0.2f, -0.2f, -0.3f),
				f(7, -1.3f, 0, 0.1f, -1.3f, 0, -0.1f, 0.45f, 0, -0.45f, 0.4f, 0.1f),
				f(12, -1.2f, 0, 0.1f, -1.2f, 0, -0.1f, 0.35f, 0, -0.4f, 0.4f, 0.1f), z(18) });
		// G: both palms thrust down at the target's feet, then clench
		MutationPose.register("p05.grip", new float[][] { z(0),
				f(3, -1.0f, 0.25f, 0.1f, -1.0f, -0.25f, -0.1f, 0.35f, 0, -0.25f, 0.25f, 0.15f),
				f(7, -0.75f, -0.35f, 0.05f, -0.75f, 0.35f, -0.05f, 0.4f, 0, -0.25f, 0.25f, 0.2f),
				f(14, -0.75f, -0.35f, 0.05f, -0.75f, 0.35f, -0.05f, 0.4f, 0, -0.25f, 0.25f, 0.2f), z(19) });
		// Shift+G: reach out, clench and drag the ground down
		MutationPose.register("p05.sinkhole", new float[][] { z(0),
				f(3, AIM, AIM, 0, 0.2f, 0, -0.2f, 0.1f, -0.2f, -0.1f, 0.1f, 0),
				f(8, AIM, AIM, 0, 0.2f, 0, -0.2f, 0.1f, -0.2f, -0.1f, 0.1f, 0),
				f(12, -0.2f, 0.2f, 0.2f, 0.3f, 0, -0.3f, 0.45f, -0.1f, -0.3f, 0.3f, 0.3f),
				f(16, -0.2f, 0.2f, 0.2f, 0.3f, 0, -0.3f, 0.45f, -0.1f, -0.3f, 0.3f, 0.3f), z(21) });
		// X: surfing the slab -- side-on, knees bent, arms out for balance
		MutationPose.registerLoop("p05.surf", 4, new float[][] { z(0),
				f(4, -0.3f, 0, 1.15f, -0.3f, 0, -1.15f, 0.22f, 0.65f, -0.45f, 0.4f, 0.1f),
				f(14, -0.4f, 0, 1.25f, -0.2f, 0, -1.05f, 0.26f, 0.7f, -0.5f, 0.45f, 0.1f),
				f(24, -0.3f, 0, 1.15f, -0.3f, 0, -1.15f, 0.22f, 0.65f, -0.45f, 0.4f, 0.1f) });
		// Shift+X at a target: both arms heave up from low, raising the pillar
		MutationPose.register("p05.pillar", new float[][] { z(0),
				f(3, 0.4f, 0, 0.3f, 0.4f, 0, -0.3f, 0.4f, 0, -0.4f, 0.4f, 0.2f),
				f(6, -2.7f, 0, 0.3f, -2.7f, 0, -0.3f, -0.15f, 0, 0.1f, -0.1f, -0.3f),
				f(13, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.15f, 0, 0.1f, -0.1f, -0.3f), z(18) });
		// Shift+X under yourself: crouch, then ride it up, arms flung wide
		MutationPose.register("p05.pillar_ride", new float[][] { z(0),
				f(2, 0.3f, 0, 0.4f, 0.3f, 0, -0.4f, 0.45f, 0, -0.6f, 0.6f, 0.2f),
				f(6, -0.6f, 0, 1.5f, -0.6f, 0, -1.5f, -0.1f, 0, 0.2f, -0.2f, -0.3f),
				f(20, -0.5f, 0, 1.4f, -0.5f, 0, -1.4f, -0.05f, 0, 0.1f, -0.1f, -0.2f), z(26) });
		// Z: both fists up -- and down into the ground
		MutationPose.register("p05.quake", new float[][] { z(0),
				f(3, -3.0f, 0, 0.25f, -3.0f, 0, -0.25f, -0.2f, 0, 0.1f, -0.1f, -0.2f),
				f(6, -0.55f, 0, 0.15f, -0.55f, 0, -0.15f, 0.7f, 0, -0.9f, 0.4f, 0.4f),
				f(13, -0.55f, 0, 0.15f, -0.55f, 0, -0.15f, 0.7f, 0, -0.9f, 0.4f, 0.4f), z(19) });
		// Shift+Z: a rising sweep of the right arm along the line of spikes
		MutationPose.register("p05.spikes", new float[][] { z(0),
				f(3, 0.5f, 0.2f, 0.2f, -0.4f, 0, -0.2f, 0.35f, -0.45f, -0.3f, 0.3f, 0.2f),
				f(7, -1.7f, 0, 0.1f, 0.3f, 0, -0.2f, 0.0f, 0.3f, 0.2f, -0.2f, 0),
				f(13, -1.7f, 0, 0.1f, 0.3f, 0, -0.2f, 0.0f, 0.3f, 0.2f, -0.2f, 0), z(18) });
		// V: palms up, lifting the wall out of the ground
		MutationPose.register("p05.wall", new float[][] { z(0),
				f(3, -0.2f, -0.3f, 0.2f, -0.2f, 0.3f, -0.2f, 0.35f, 0, -0.3f, 0.3f, 0.2f),
				f(8, -1.9f, 0.2f, 0.25f, -1.9f, -0.2f, -0.25f, -0.1f, 0, 0.1f, -0.1f, -0.1f),
				f(14, -1.85f, 0.2f, 0.25f, -1.85f, -0.2f, -0.25f, -0.1f, 0, 0.1f, -0.1f, -0.1f), z(19) });
		// Shift+V: the rock skin closes over -- arms flexed out, then fists clashed at the chest
		MutationPose.register("p05.armor_up", new float[][] { z(0),
				f(4, -0.6f, 0, 1.1f, -0.6f, 0, -1.1f, -0.15f, 0, 0.1f, -0.1f, -0.15f),
				f(9, -1.4f, -0.9f, 0, -1.4f, 0.9f, 0, 0.15f, 0, -0.1f, 0.1f, 0.1f),
				f(15, -1.4f, -0.9f, 0, -1.4f, 0.9f, 0, 0.15f, 0, -0.1f, 0.1f, 0.1f), z(20) });
		// C held: both arms driving forward along the bore, alternating
		MutationPose.registerLoop("p05.tunnel", 2, new float[][] { z(0),
				f(2, AIM, AIM, 0, -1.2f, 0.3f, 0, 0.3f, -0.2f, -0.3f, 0.3f, 0),
				f(5, -1.2f, -0.3f, 0, AIM, AIM, 0, 0.3f, 0.2f, 0.3f, -0.3f, 0),
				f(8, AIM, AIM, 0, -1.2f, 0.3f, 0, 0.3f, -0.2f, -0.3f, 0.3f, 0) });
		// Shift+C: crouch, a palm pressed flat to the ground, head down, listening
		MutationPose.register("p05.seismic", new float[][] { z(0),
				f(4, -0.25f, 0.1f, 0.1f, 0.2f, 0, -0.35f, 0.85f, 0, -1.0f, 0.5f, 0.5f),
				f(20, -0.25f, 0.1f, 0.1f, 0.2f, 0, -0.35f, 0.85f, 0, -1.0f, 0.5f, 0.5f), z(26) });
	}

	// ================================================================== Earth Armor: rock plates over the body

	private static BlockState blockOf(MutationOverlays.Context ctx, String value) {
		int id = (int) ctx.state().value(value, 0);
		BlockState s = id > 0 ? Block.stateById(id) : Blocks.STONE.defaultBlockState();
		return s.isAir() ? Blocks.STONE.defaultBlockState() : s;
	}

	/** One plate on a model part: a box in pixels (part-local, y down), turned a few degrees for a rough-hewn look. */
	private static void plate(MutationOverlays.Context ctx, ModelPart part, BlockState b, float x, float y, float z, float w, float h, float d,
			float tilt) {
		PoseStack pose = ctx.pose();
		pose.pushPose();
		part.translateAndRotate(pose);
		pose.translate((x + w / 2) / 16.0, (y + h / 2) / 16.0, (z + d / 2) / 16.0);
		pose.mulPose(Axis.ZP.rotationDegrees(tilt));
		pose.mulPose(Axis.XP.rotationDegrees(tilt * 0.6f));
		GeoModels.centred(pose, ctx.buffers(), ctx.light(), b, w / 16.0f, h / 16.0f, d / 16.0f);
		pose.popPose();
	}

	private static void armorPlates(MutationOverlays.Context ctx) {
		BlockState b = blockOf(ctx, "p05.armor_block");
		var m = ctx.model();
		boolean slim = "slim".equals(ctx.player().getSkin().model().id());
		float aw = slim ? 3.0f : 4.0f; // arm width
		// head: a heavy brow and a crown, the face left clear
		plate(ctx, m.head, b, -4.6f, -8.9f, -4.7f, 9.2f, 2.8f, 2.2f, 2);
		plate(ctx, m.head, b, -4.4f, -9.2f, -3.2f, 8.8f, 1.6f, 7.6f, -1.5f);
		plate(ctx, m.head, b, -4.7f, -6.5f, -1.0f, 1.4f, 4.5f, 5.0f, 3);
		plate(ctx, m.head, b, 3.3f, -6.5f, -1.0f, 1.4f, 4.5f, 5.0f, -3);
		// torso: two chest slabs, an abdomen slab, a back plate
		plate(ctx, m.body, b, -4.5f, 0.4f, -3.0f, 4.6f, 5.2f, 1.3f, 3);
		plate(ctx, m.body, b, -0.1f, 0.4f, -3.0f, 4.6f, 5.2f, 1.3f, -3);
		plate(ctx, m.body, b, -3.8f, 6.0f, -2.8f, 7.6f, 4.6f, 1.1f, 1.5f);
		plate(ctx, m.body, b, -4.5f, 0.5f, 1.8f, 9.0f, 9.5f, 1.3f, -1);
		// arms: a pauldron and a forearm bracer each
		plate(ctx, m.rightArm, b, -aw + 0.2f - 0.9f, -2.9f, -2.7f, aw + 1.8f, 3.4f, 5.4f, -6);
		plate(ctx, m.rightArm, b, -aw + 1.0f - 0.6f, 5.0f, -2.5f, aw + 1.2f, 4.4f, 5.0f, 2);
		plate(ctx, m.leftArm, b, -1.0f - 0.9f, -2.9f, -2.7f, aw + 1.8f, 3.4f, 5.4f, 6);
		plate(ctx, m.leftArm, b, -1.0f - 0.6f, 5.0f, -2.5f, aw + 1.2f, 4.4f, 5.0f, -2);
		// legs: a thigh plate and a shin guard each
		plate(ctx, m.rightLeg, b, -2.5f, 1.0f, -2.8f, 5.0f, 4.6f, 1.1f, 2);
		plate(ctx, m.rightLeg, b, -2.5f, 6.6f, -2.9f, 5.0f, 5.0f, 1.3f, -1.5f);
		plate(ctx, m.leftLeg, b, -2.5f, 1.0f, -2.8f, 5.0f, 4.6f, 1.1f, -2);
		plate(ctx, m.leftLeg, b, -2.5f, 6.6f, -2.9f, 5.0f, 5.0f, 1.3f, 1.5f);
	}

	// ================================================================== Rock Surf: the slab under the feet

	private static void surfSlab(MutationOverlays.Context ctx) {
		BlockState b = blockOf(ctx, "p05.surf_block");
		PoseStack pose = ctx.pose();
		float t = ctx.ageInTicks() + ctx.partialTick();
		pose.pushPose();
		// layer space is the model's (y down, x mirrored, feet at y = +1.5): flip back to world orientation at the feet
		pose.translate(0.0, 1.5, 0.0);
		pose.scale(-1.0f, -1.0f, 1.0f);
		float bob = (float) Math.sin(t * 0.5) * 0.03f;
		// the slab rides on the ground with the feet set into its top (the player stands on the real ground)
		pose.translate(0, 0.04 + bob, 0);
		pose.mulPose(Axis.XP.rotationDegrees(-4 + (float) Math.sin(t * 0.37) * 2));
		pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.23) * 3));
		// the slab: a thick plate of rock with a raised nose and a broken back edge
		GeoModels.centred(pose, ctx.buffers(), ctx.light(), b, 1.25f, 0.34f, 1.9f);
		pose.pushPose();
		pose.translate(0, 0.08, 0.92);
		pose.mulPose(Axis.XP.rotationDegrees(-18));
		GeoModels.centred(pose, ctx.buffers(), ctx.light(), b, 1.0f, 0.26f, 0.4f);
		pose.popPose();
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			pose.translate((i - 1) * 0.42, -0.06, -0.98 - GeoModels.rnd(ctx.player().getId(), i) * 0.1);
			pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(ctx.player().getId(), 10 + i) * 50));
			GeoModels.centred(pose, ctx.buffers(), ctx.light(), b, 0.38f, 0.24f, 0.3f);
			pose.popPose();
		}
		pose.popPose();
	}
}
