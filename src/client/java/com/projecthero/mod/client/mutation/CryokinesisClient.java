package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.cryo.FrostShellRenderer;
import com.projecthero.mod.client.mutation.cryo.IceBoltRenderer;
import com.projecthero.mod.client.mutation.cryo.IceDraw;
import com.projecthero.mod.client.mutation.cryo.IceSpikeRenderer;
import com.projecthero.mod.hero.power.p09.CryoEntities;
import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Cryokinesis rework -- how the ice looks, on every client, from the synced {@code p09.*} visual values
 * ({@link CryokinesisHandlers#registerVisuals}) and the power's own entities:
 * <ul>
 * <li>the Ice Bolt lance, Ice Shards and Shatter debris ({@link IceBoltRenderer}), the erupting Glacier Spikes / Absolute
 * Zero crown ({@link IceSpikeRenderer}), the Frost pips and the frozen-solid ice block ({@link FrostShellRenderer});</li>
 * <li>the Freeze Beam: a glowing core wrapped in a spiral of ice crystals with a frost bloom where it lands;</li>
 * <li>Frozen Armor: ice plates over the chest, back, shoulders, forearms and shins and an ice circlet;</li>
 * <li>the Ice Blade on the right forearm, in third and first person;</li>
 * <li>the Ice Slide's ice sled under the feet (and the ride itself, driven here on the local player);</li>
 * <li>cast poses for every move.</li>
 * </ul>
 */
public final class CryokinesisClient {
	private CryokinesisClient() {
	}

	/** From {@code RevampClientB.init()}. */
	public static void init() {
		com.projecthero.mod.client.gui.AbilityHudExtras.mono(CryokinesisHandlers.KEY);
		EntityRendererRegistry.register(CryoEntities.ICE_BOLT, IceBoltRenderer::new);
		EntityRendererRegistry.register(CryoEntities.FROST_SHELL, FrostShellRenderer::new);
		EntityRendererRegistry.register(CryoEntities.ICE_SPIKE, IceSpikeRenderer::new);
		// v0.16.6 hold moves
		EntityRendererRegistry.register(CryoEntities.ICE_JAVELIN, com.projecthero.mod.client.mutation.cryo.IceJavelinRenderer::new);
		EntityRendererRegistry.register(CryoEntities.CRYO_FIELD, com.projecthero.mod.client.mutation.cryo.CryoFieldRenderer::new);
		EntityRendererRegistry.register(CryoEntities.ICE_GOLEM, com.projecthero.mod.client.mutation.cryo.IceGolemRenderer::new);
		registerPoses();
		MutationOverlays.register("p09.armor", CryokinesisClient::armor);
		MutationOverlays.register("p09.blade", ctx -> blade(ctx.pose(), ctx.buffers(), ctx.light(), ctx.model().rightArm,
				ctx.ageInTicks() + ctx.partialTick(), false));
		MutationOverlays.register("p09.slide", CryokinesisClient::sled);
		MutationOverlays.register("p09.wings", CryokinesisClient::wings); // v0.16.6 hold-X
		WorldRenderEvents.AFTER_ENTITIES.register(CryokinesisClient::renderBeams);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null && !mc.isPaused()) {
				slideRide(mc.player);
			}
		});
	}

	// ================================================================== poses

	private static float[] f(int tick, float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY, float rL,
			float lL, float h) {
		return new float[] { tick, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static float[] z(int tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	private static void registerPoses() {
		registerHoldPoses();
		// R: the bolt -- right arm thrust out along the look, the body turning into it
		MutationPose.register("p09.cast", new float[][] { z(0),
				f(2, -0.6f, 0.4f, 0.1f, 0.2f, 0, -0.1f, 0, -0.25f, 0, 0, 0),
				f(4, AIM, AIM, 0, 0.35f, 0.2f, -0.15f, 0.08f, 0.3f, -0.2f, 0.2f, 0),
				f(10, AIM, AIM, 0, 0.35f, 0.2f, -0.15f, 0.08f, 0.3f, -0.2f, 0.2f, 0), z(14) });
		// Shift+R: both palms flung forward, fanning the shards
		MutationPose.register("p09.shards", new float[][] { z(0),
				f(3, -0.4f, 0.7f, 0.2f, -0.4f, -0.7f, -0.2f, -0.1f, 0, 0.15f, -0.15f, 0),
				f(6, AIM, AIM, 0.15f, AIM, AIM, -0.15f, 0.15f, 0, -0.3f, 0.3f, 0),
				f(12, AIM, AIM, 0.15f, AIM, AIM, -0.15f, 0.15f, 0, -0.3f, 0.3f, 0), z(16) });
		// G: the beam -- right arm locked on the target, the left hand bracing the wrist
		float[] beam = f(0, AIM, AIM, 0, -1.25f, 0.55f, 0, 0.12f, -0.25f, -0.25f, 0.25f, 0);
		float[] beam2 = f(0, AIM, AIM, 0, -1.3f, 0.6f, 0, 0.14f, -0.28f, -0.25f, 0.25f, 0);
		MutationPose.registerLoop("p09.beam", 3, new float[][] { z(0), at(3, beam), at(8, beam2), at(13, beam) });
		// Shift+G: reach out, then close the fist and pull -- the prison snaps shut
		MutationPose.register("p09.prison", new float[][] { z(0),
				f(3, AIM, AIM, 0, AIM, AIM, 0, 0.1f, 0, -0.2f, 0.2f, 0),
				f(8, AIM, AIM, 0, AIM, AIM, 0, 0.12f, 0, -0.2f, 0.2f, 0),
				f(11, -0.9f, 0.5f, 0, -0.9f, -0.5f, 0, -0.1f, 0, 0.1f, -0.1f, -0.1f), z(16) });
		// X: the Iceman slide -- crouched, one arm forward, one back
		MutationPose.registerLoop("p09.slide", 4, new float[][] { z(0),
				f(4, -0.9f, 0, 0.25f, 0.9f, 0.2f, -0.3f, 0.35f, 0.25f, -0.5f, 0.55f, -0.2f),
				f(14, -0.95f, 0, 0.3f, 0.95f, 0.25f, -0.32f, 0.38f, 0.27f, -0.55f, 0.6f, -0.2f),
				f(24, -0.9f, 0, 0.25f, 0.9f, 0.2f, -0.3f, 0.35f, 0.25f, -0.5f, 0.55f, -0.2f) });
		// Shift+X: crouch, then thrown up arms-high off the pillar
		MutationPose.register("p09.pillar", new float[][] { z(0),
				f(2, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.45f, 0, -0.6f, -0.6f, 0.2f),
				f(5, -2.9f, 0, 0.25f, -2.9f, 0, -0.25f, -0.15f, 0, 0.15f, -0.15f, -0.3f),
				f(16, -2.8f, 0, 0.35f, -2.8f, 0, -0.35f, -0.1f, 0, 0.3f, -0.1f, -0.2f), z(22) });
		// Z: drag the right hand up and slam it down -- the spikes race out from it
		MutationPose.register("p09.spikes", new float[][] { z(0),
				f(3, -2.7f, 0, 0.3f, 0.3f, 0, -0.2f, -0.2f, 0, 0, 0, -0.2f),
				f(6, -0.5f, 0, 0.1f, 0.4f, 0, -0.3f, 0.55f, 0, -0.5f, 0.3f, 0.35f),
				f(14, -0.45f, 0, 0.1f, 0.4f, 0, -0.3f, 0.5f, 0, -0.5f, 0.3f, 0.35f), z(19) });
		// Shift+Z: both arms high, then both fists down -- Absolute Zero
		MutationPose.register("p09.zero", new float[][] { z(0),
				f(4, -2.9f, 0, 0.4f, -2.9f, 0, -0.4f, -0.2f, 0, 0, 0, -0.35f),
				f(7, 0.3f, 0, 0.6f, 0.3f, 0, -0.6f, 0.5f, 0, -0.55f, -0.55f, 0.4f),
				f(18, 0.3f, 0, 0.65f, 0.3f, 0, -0.65f, 0.48f, 0, -0.55f, -0.55f, 0.4f), z(24) });
		// V: both arms sweep up from low -- the wall rises with them
		MutationPose.register("p09.wall", new float[][] { z(0),
				f(3, 0.5f, 0, 0.2f, 0.5f, 0, -0.2f, 0.25f, 0, -0.3f, 0.2f, 0.1f),
				f(9, -1.9f, 0, 0.15f, -1.9f, 0, -0.15f, -0.1f, 0, -0.3f, 0.2f, -0.1f),
				f(15, -1.8f, 0, 0.2f, -1.8f, 0, -0.2f, -0.1f, 0, -0.3f, 0.2f, -0.1f), z(20) });
		// Shift+V: the armour forms -- arms crossed over the chest, then flexed out
		MutationPose.register("p09.armor", new float[][] { z(0),
				f(4, -1.4f, -0.9f, 0, -1.4f, 0.9f, 0, 0.2f, 0, 0, 0, 0.15f),
				f(10, -0.2f, 0, 0.9f, -0.2f, 0, -0.9f, -0.1f, 0, 0, 0, -0.1f),
				f(16, -0.2f, 0, 0.85f, -0.2f, 0, -0.85f, -0.1f, 0, 0, 0, -0.1f), z(21) });
		// C: the blade grows -- a rising cross-body sweep
		MutationPose.register("p09.blade_draw", new float[][] { z(0),
				f(4, 0.4f, -0.9f, 0.2f, 0.1f, 0, -0.1f, 0.1f, -0.4f, 0, 0, 0),
				f(8, -2.4f, 0.4f, 0.3f, 0.1f, 0, -0.2f, -0.1f, 0.3f, 0, 0, -0.1f),
				f(13, -2.3f, 0.4f, 0.3f, 0.1f, 0, -0.2f, -0.1f, 0.3f, 0, 0, -0.1f), z(18) });
		// Shift+C: Shatter -- clench both fists in, then fling them open
		MutationPose.register("p09.shatter", new float[][] { z(0),
				f(4, -1.2f, -0.6f, 0, -1.2f, 0.6f, 0, 0.3f, 0, 0, 0, 0.25f),
				f(7, -1.0f, 0.6f, 1.1f, -1.0f, -0.6f, -1.1f, -0.2f, 0, 0, 0, -0.25f),
				f(14, -0.9f, 0.5f, 1.0f, -0.9f, -0.5f, -1.0f, -0.15f, 0, 0, 0, -0.2f), z(19) });
	}

	/** v0.16.6: the hold moves' poses. */
	private static void registerHoldPoses() {
		// hold R: the javelin forms in the raised right hand, drawn back over the shoulder -- then the throw
		MutationPose.register("p09.javelin", new float[][] { z(0),
				f(3, -2.7f, -0.3f, 0.35f, -0.6f, 0.3f, -0.2f, 0.0f, 0.35f, 0.15f, -0.15f, 0),
				f(10, -2.9f, -0.3f, 0.4f, -0.8f, 0.35f, -0.25f, -0.08f, 0.45f, 0.2f, -0.2f, -0.05f),
				f(12, AIM, AIM, 0.0f, 0.4f, 0.2f, -0.2f, 0.25f, -0.35f, -0.35f, 0.3f, 0.1f),
				f(18, AIM, AIM, 0.0f, 0.4f, 0.2f, -0.2f, 0.2f, -0.3f, -0.3f, 0.3f, 0.1f), z(24) });
		// hold G: both hands up, then both palms flat down onto the ground -- the ice spreads from them
		MutationPose.register("p09.ground", new float[][] { z(0),
				f(3, -2.6f, 0, 0.5f, -2.6f, 0, -0.5f, -0.2f, 0, 0, 0, -0.3f),
				f(7, -0.25f, 0, 0.9f, -0.25f, 0, -0.9f, 0.6f, 0, -0.7f, -0.7f, 0.45f),
				f(18, -0.2f, 0, 0.95f, -0.2f, 0, -0.95f, 0.55f, 0, -0.7f, -0.7f, 0.45f), z(24) });
		// hold X: on the wings -- arms swept back and out, legs trailing
		MutationPose.registerLoop("p09.wings", 6, new float[][] { z(0),
				f(6, 0.45f, 0, 0.55f, 0.45f, 0, -0.55f, 0.15f, 0, 0.25f, 0.15f, -0.15f),
				f(16, 0.5f, 0, 0.6f, 0.5f, 0, -0.6f, 0.17f, 0, 0.2f, 0.2f, -0.15f),
				f(26, 0.45f, 0, 0.55f, 0.45f, 0, -0.55f, 0.15f, 0, 0.25f, 0.15f, -0.15f) });
		// hold Z: both arms flung up at the sky, then pointed at the storm
		MutationPose.register("p09.blizzard", new float[][] { z(0),
				f(4, -2.9f, 0.2f, 0.45f, -2.9f, -0.2f, -0.45f, -0.25f, 0, 0, 0, -0.4f),
				f(9, -2.6f, 0.3f, 0.7f, -2.6f, -0.3f, -0.7f, -0.25f, 0, 0, 0, -0.4f),
				f(13, AIM, AIM, 0.1f, AIM, AIM, -0.1f, 0.1f, 0, -0.2f, 0.2f, 0),
				f(22, AIM, AIM, 0.1f, AIM, AIM, -0.1f, 0.1f, 0, -0.2f, 0.2f, 0), z(28) });
		// hold V: arms sweep up from the sides and meet overhead -- the dome closes with them
		MutationPose.register("p09.fortress", new float[][] { z(0),
				f(3, 0.2f, 0, 1.2f, 0.2f, 0, -1.2f, 0.2f, 0, -0.3f, 0.3f, 0.1f),
				f(9, -2.4f, 0, 0.9f, -2.4f, 0, -0.9f, -0.05f, 0, -0.3f, 0.3f, -0.15f),
				f(13, -2.9f, 0, 0.25f, -2.9f, 0, -0.25f, -0.1f, 0, -0.3f, 0.3f, -0.25f),
				f(18, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.1f, 0, -0.3f, 0.3f, -0.25f), z(24) });
		// hold C: a fist down to the ground, then the palm turned up and raised -- the golem rises with it
		MutationPose.register("p09.golem", new float[][] { z(0),
				f(4, 0.3f, 0, 0.2f, 0.2f, 0, -0.15f, 0.5f, 0.3f, -0.6f, 0.4f, 0.3f),
				f(10, -1.6f, 0.4f, 0.2f, 0.2f, 0, -0.15f, 0.0f, 0.3f, -0.2f, 0.2f, -0.1f),
				f(16, -1.9f, 0.45f, 0.25f, 0.2f, 0, -0.15f, -0.05f, 0.3f, -0.2f, 0.2f, -0.15f), z(22) });
	}

	private static float[] at(int tick, float[] frame) {
		float[] c = frame.clone();
		c[0] = tick;
		return c;
	}

	// ================================================================== Frozen Armor

	private static void armor(MutationOverlays.Context ctx) {
		var m = ctx.model();
		PoseStack pose = ctx.pose();
		MultiBufferSource buf = ctx.buffers();
		int light = ctx.light();
		int plate = 0xE8F4FBFF;
		int clear = 0xB4E8F6FF;
		float px = 1.0f / 16.0f;
		boolean fp = com.projecthero.mod.client.ironman.IronManFlightPose.firstPersonHand;
		if (!fp) {
			// chest and back plates, an ice belt
			pose.pushPose();
			m.body.translateAndRotate(pose);
			IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -4.6f * px, 0.4f * px, -3.0f * px, 4.6f * px, 7.4f * px, -2.0f * px, 0.5f, plate, light);
			IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)), -1.0f * px, 1.0f * px, -3.4f * px, 1.0f * px, 6.6f * px, -2.9f * px, 0.5f, 0xF0FFFFFF, light);
			IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -4.6f * px, 0.4f * px, 2.0f * px, 4.6f * px, 9.0f * px, 3.0f * px, 0.5f, plate, light);
			IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), -4.4f * px, 7.6f * px, -2.7f * px, 4.4f * px, 11.0f * px, 2.7f * px, 0.5f, clear, light);
			// a ridge of crystals down the back
			for (int i = 0; i < 3; i++) {
				pose.pushPose();
				pose.translate(0, (1.5f + i * 2.6f) * px, 3.0f * px);
				pose.mulPose(Axis.XP.rotation(1.3f));
				IceDraw.crystal(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 1.6f * px, (4.0f - i) * px, 0, 0xE6F2FBFF, light);
				pose.popPose();
			}
			pose.popPose();
			// the circlet: a band with five crystals rising from it
			pose.pushPose();
			m.head.translateAndRotate(pose);
			IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -4.5f * px, -7.3f * px, -4.5f * px, 4.5f * px, -6.0f * px, 4.5f * px, 0.5f, plate, light);
			for (int i = -2; i <= 2; i++) {
				pose.pushPose();
				pose.translate(i * 1.8f * px, -7.2f * px, -4.4f * px);
				pose.mulPose(Axis.ZP.rotation(i * 0.18f));
				IceDraw.crystal(pose, i == 0 ? buf.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)) : buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 1.4f * px, (i == 0 ? 5.0f : 3.4f - Math.abs(i) * 0.6f) * px, 0, 0xF0F6FFFF, light);
				pose.popPose();
			}
			pose.popPose();
			// shin guards with a knee crystal
			for (ModelPart leg : new ModelPart[] { m.rightLeg, m.leftLeg }) {
				pose.pushPose();
				leg.translateAndRotate(pose);
				IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -2.4f * px, 5.0f * px, -2.7f * px, 2.4f * px, 11.0f * px, -1.8f * px, 0.5f, plate, light);
				pose.translate(0, 5.2f * px, -2.6f * px);
				pose.mulPose(Axis.XP.rotation(-1.2f));
				IceDraw.crystal(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 1.5f * px, 2.4f * px, 0, 0xE6F2FBFF, light);
				pose.popPose();
			}
		}
		// pauldrons and bracers (also on the first-person arm, see renderFirstPersonArm)
		pose.pushPose();
		m.rightArm.translateAndRotate(pose);
		armPlates(pose, buf, light, true, fp);
		pose.popPose();
		if (!fp) {
			pose.pushPose();
			m.leftArm.translateAndRotate(pose);
			armPlates(pose, buf, light, false, false);
			pose.popPose();
		}
	}

	/** A pauldron with icicles and a forearm bracer, in an arm's own frame (right arm: x -3..1; left: -1..3). */
	private static void armPlates(PoseStack pose, MultiBufferSource buf, int light,
			boolean right, boolean bracerOnly) {
		float px = 1.0f / 16.0f;
		float cx = right ? -1.0f : 1.0f;
		int plate = 0xE8F4FBFF;
		if (!bracerOnly) {
			IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), (cx - 2.9f) * px, -2.7f * px, -2.8f * px, (cx + 2.9f) * px, 1.6f * px, 2.8f * px, 0.5f, plate, light);
			for (int i = 0; i < 3; i++) {
				pose.pushPose();
				pose.translate((cx + (right ? -1.5f : 1.5f) + (i - 1) * 0.6f) * px, -2.6f * px, (i - 1) * 1.7f * px);
				pose.mulPose(Axis.ZP.rotation(right ? 0.55f : -0.55f));
				pose.mulPose(Axis.XP.rotation(Mth.PI));
				IceDraw.crystal(pose, i == 1 ? buf.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)) : buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 1.4f * px, (i == 1 ? 4.6f : 3.2f) * px, 0, 0xF0F6FFFF, light);
				pose.popPose();
			}
		}
		IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), (cx - 2.5f) * px, 5.2f * px, -2.5f * px, (cx + 2.5f) * px, 9.6f * px, 2.5f * px, 0.5f, plate, light);
	}

	// ================================================================== Ice Blade

	/**
	 * The Ice Blade in an arm's frame: a wrist mount of packed ice and a long, flat, faceted blade of clear ice running on
	 * past the hand, its edge faintly glowing.
	 */
	public static void blade(PoseStack pose, MultiBufferSource buf, int light, ModelPart arm, float time, boolean firstPerson) {
		float px = 1.0f / 16.0f;
		int lit = LightTexture.pack(Math.max(LightTexture.block(light), 7), LightTexture.sky(light));
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the mount round the wrist
		IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -3.4f * px, 6.0f * px, -2.4f * px, 1.4f * px, 9.6f * px, 2.4f * px, 0.5f, 0xF0F4FBFF, lit);
		// the blade: a flattened crystal from the wrist, along the arm and past the fist
		pose.translate(-1.0f * px, 7.0f * px, 0);
		pose.scale(0.32f, 1.0f, 1.0f);
		IceDraw.crystal(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 4.2f * px, 20.0f * px, 13.0f * px, 0xD8EAF8FF, lit);
		float shimmer = 0.6f + 0.4f * Mth.sin(time * 0.25f);
		IceDraw.crystal(pose, buf.getBuffer(RenderType.eyes(IceDraw.WHITE)), 1.2f * px, 19.0f * px, 13.0f * px, ((int) (120 * shimmer) << 24) | 0x9EE2FF, LightTexture.FULL_BRIGHT);
		pose.popPose();
	}

	/** From {@code CryoFirstPersonArmMixin}: the blade and the bracer on the arm you see in first person. */
	public static void renderFirstPersonArm(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer player, ModelPart arm,
			boolean right) {
		if (!right) {
			return;
		}
		MutationVisualState s = MutationVisuals.state(player);
		if (s.has("p09.blade")) {
			blade(pose, buf, light, arm, player.tickCount, true);
		}
		if (s.has("p09.armor")) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			armPlates(pose, buf, light, true, true);
			pose.popPose();
		}
	}

	// ================================================================== Ice Wings (v0.16.6 hold-X)

	/**
	 * Wings of ice on the back, beating: each a long blue-ice spar sweeping out and up from the shoulder blade with five
	 * clear-ice feathers hanging off it (a faint glowing vein down each), the whole wing swinging forward and back with
	 * every beat and flexing at the root.
	 */
	private static void wings(MutationOverlays.Context ctx) {
		if (com.projecthero.mod.client.ironman.IronManFlightPose.firstPersonHand) {
			return;
		}
		PoseStack pose = ctx.pose();
		MultiBufferSource buf = ctx.buffers();
		float px = 1.0f / 16.0f;
		float t = ctx.ageInTicks() + ctx.partialTick();
		int light = LightTexture.pack(Math.max(LightTexture.block(ctx.light()), 9), LightTexture.sky(ctx.light()));
		float beat = Mth.sin(t * 0.55f);
		pose.pushPose();
		ctx.model().body.translateAndRotate(pose);
		// a frozen harness between the shoulder blades
		IceDraw.box(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -2.5f * px, 1.0f * px, 2.0f * px, 2.5f * px, 6.0f * px, 3.0f * px, 0.5f, 0xF0FFFFFF, light);
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 1.8f * px, 2.5f * px, 2.6f * px);
			pose.scale(1.6f, 1.6f, 1.6f);
			// the beat: the wing swings back and forth about the spine, and lifts a little on the upstroke
			pose.mulPose(Axis.YP.rotation(-s * (0.55f + 0.5f * beat)));
			pose.mulPose(Axis.ZP.rotation(s * 0.18f * beat));
			// the spar: out and up
			float sparAng = -s * 1.95f;
			float sx = -Mth.sin(sparAng);
			float sy = Mth.cos(sparAng);
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotation(sparAng));
			IceDraw.crystal(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)), 2.6f * px, 19.0f * px, 4.0f * px, 0xF4FFFFFF, light);
			pose.popPose();
			// the feathers hanging off it, longest in the middle, flexing behind the beat
			float[] along = { 3.0f, 6.5f, 10.0f, 13.5f, 16.5f };
			float[] len = { 11.0f, 13.5f, 15.0f, 14.0f, 11.0f };
			for (int i = 0; i < along.length; i++) {
				float d = along[i] * px;
				float out = 0.12f + i * 0.17f + 0.1f * Mth.sin(t * 0.55f - 0.6f - i * 0.25f);
				float ang = (float) Mth.atan2(-s * out, 1.0f);
				pose.pushPose();
				pose.translate(sx * d, sy * d, 0.2f * px * i);
				pose.mulPose(Axis.ZP.rotation(ang));
				pose.mulPose(Axis.YP.rotation(0.4f));
				IceDraw.crystal(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 2.4f * px, len[i] * px, 2.0f * px, 0xE2EAF8FF, light);
				IceDraw.crystal(pose, buf.getBuffer(RenderType.eyes(IceDraw.WHITE)), 0.5f * px, len[i] * 0.85f * px, 2.0f * px, 0x707FD8FF, LightTexture.FULL_BRIGHT);
				pose.popPose();
			}
			pose.popPose();
		}
		pose.popPose();
	}

	// ================================================================== Ice Slide

	/** The ice sled under the feet: a board of packed ice with an upturned clear-ice prow and a glowing keel. */
	private static void sled(MutationOverlays.Context ctx) {
		PoseStack pose = ctx.pose();
		pose.pushPose();
		// layer space is the model's (y down, x mirrored, feet at y = +1.5): flip back to world orientation at the feet
		pose.translate(0.0, 1.5, 0.0);
		pose.scale(-1.0f, -1.0f, 1.0f);
		float t = ctx.ageInTicks() + ctx.partialTick();
		pose.translate(0, -0.13 + Mth.sin(t * 0.6f) * 0.015f, 0);
		int light = LightTexture.pack(Math.max(LightTexture.block(ctx.light()), 8), LightTexture.sky(ctx.light()));
		IceDraw.box(pose, ctx.buffers().getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), -0.42f, 0.0f, -0.75f, 0.42f, 0.11f, 0.85f, 0.5f, 0xF2FFFFFF, light);
		IceDraw.box(pose, ctx.buffers().getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), -0.3f, -0.05f, -0.7f, 0.3f, 0.0f, 0.8f, 0.5f, 0xA0E0F6FF, light);
		// the prow, curling up at the front (-Z)
		pose.pushPose();
		pose.translate(0, 0.05, -0.75);
		pose.mulPose(Axis.XP.rotation(-1.0f));
		IceDraw.box(pose, ctx.buffers().getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), -0.38f, -0.05f, -0.32f, 0.38f, 0.06f, 0.0f, 0.5f, 0xD0EAF8FF, light);
		pose.popPose();
		// fins of ice spraying off the back
		for (int i = -1; i <= 1; i += 2) {
			pose.pushPose();
			pose.translate(i * 0.36f, 0.06f, 0.8f);
			pose.mulPose(Axis.XP.rotation(1.9f));
			pose.mulPose(Axis.ZP.rotation(i * 0.4f));
			IceDraw.crystal(pose, ctx.buffers().getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.12f, 0.38f + Mth.sin(t * 0.9f + i) * 0.06f, 0, 0xC0EAF8FF, light);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The eased ride velocity {x, z} (x NaN: not riding) -- one per slide, so one slide standing idle never resets another. */
	private static final double[] CRYO_RIDE = { Double.NaN, 0 };

	private static void slideRide(LocalPlayer p) {
		ride(p, MutationVisuals.hasFlag(p, "p09.slide"), CRYO_RIDE);
	}

	/**
	 * The ride, on the local player: forward along the look at {@link CryokinesisHandlers#SLIDE_SPEED}, climbing when you
	 * look up (the server lays the ramp under you), dropping when you look down. v0.17.2: for any slide that is {@code on}
	 * (also Diamondhead's Crystal Slide), easing in {@code state}.
	 */
	public static void ride(LocalPlayer p, boolean on, double[] state) {
		if (!on || p.getAbilities().flying || p.isPassenger()) {
			state[0] = Double.NaN;
			return;
		}
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		double speed = CryokinesisHandlers.SLIDE_SPEED;
		Vec3 v = p.getDeltaMovement();
		// v0.16.5 (user: "make it smoother"): ease into the speed and the turns instead of snapping to them. The eased
		// velocity is kept here, not read back off the player, so ground friction never drags the ride down.
		if (Double.isNaN(state[0])) {
			state[0] = v.x;
			state[1] = v.z;
		}
		state[0] = Mth.lerp(0.35, state[0], -Mth.sin(yaw) * speed);
		state[1] = Mth.lerp(0.35, state[1], Mth.cos(yaw) * speed);
		double dx = state[0];
		double dz = state[1];
		double vy = v.y;
		float pitch = p.getXRot();
		if (pitch < -12.0f) {
			double climb = Math.min(0.42, speed * Mth.sin(-pitch * Mth.DEG_TO_RAD));
			vy = Math.max(vy, climb);
		} else if (pitch > CryokinesisHandlers.DESCEND_PITCH) {
			// look down to come down: no ice is laid under you, you ride down the slope you look along
			double want = -Math.min(0.9, speed * Mth.sin(pitch * Mth.DEG_TO_RAD) * 1.2);
			vy = p.onGround() ? vy : Mth.lerp(0.3, vy, want);
		} else if (vy < 0 && !p.onGround()) {
			vy = Math.max(vy, -0.12); // glide down gently onto the ice
		}
		if (p.horizontalCollision && pitch <= CryokinesisHandlers.DESCEND_PITCH) {
			vy = Math.max(vy, 0.42); // a wall too tall to step: hop it instead of grinding to a stop
		}
		if (p.isInWater()) {
			vy = Math.max(vy, 0.1);
		}
		p.setDeltaMovement(dx, vy, dz);
		p.fallDistance = 0.0f;
	}

	// ================================================================== Freeze Beam

	private static void renderBeams(WorldRenderContext wctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || wctx.consumers() == null) {
			return;
		}
		float partial = wctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = wctx.camera().getPosition();
		for (Player pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || p.distanceToSqr(cam) > 96 * 96) {
				continue;
			}
			MutationVisualState s = MutationVisuals.state(p);
			if (!s.has("p09.beam")) {
				continue;
			}
			float len = s.value("p09.beam_len", 0.0f);
			if (len < 0.3f) {
				len = (float) CryokinesisHandlers.BEAM_RANGE;
			}
			Vec3 look = p.getViewVector(partial);
			Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
			boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
			Vec3 eye = p.getEyePosition(partial);
			Vec3 start = firstPerson ? eye.add(look.scale(1.3)).add(right.scale(0.42)).add(0, -0.34, 0)
					: eye.add(look.scale(0.55)).add(right.scale(0.36)).add(0, -0.38, 0);
			Vec3 end = eye.add(look.scale(len));
			drawBeam(wctx.matrixStack(), wctx.consumers(), start.subtract(cam), end.subtract(cam), p.tickCount + partial);
		}
	}

	private static void drawBeam(PoseStack pose, MultiBufferSource buf, Vec3 a, Vec3 b, float t) {
		Vec3 d = b.subtract(a);
		float len = (float) d.length();
		if (len < 0.1f) {
			return;
		}
		Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
		pose.pushPose();
		pose.translate(a.x, a.y, a.z);
		pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
		int full = LightTexture.FULL_BRIGHT;
		// the core and its sheath
		float pulse = 1.0f + 0.15f * Mth.sin(t * 1.3f);
		float c = 0.035f * pulse;
		IceDraw.box(pose, buf.getBuffer(RenderType.eyes(IceDraw.WHITE)), -c, 0, -c, c, len, c, 1.0f, 0xFFE8FAFF, full);
		float sh = 0.09f * pulse;
		IceDraw.box(pose, buf.getBuffer(RenderType.eyes(IceDraw.WHITE)), -sh, 0, -sh, sh, len, sh, 1.0f, 0x608ED8FF, full);
		// the spiral of ice crystals travelling along it
		int n = (int) (len * 3.0f);
		for (int i = 0; i < n; i++) {
			float along = ((i / 3.0f) + t * 0.45f) % len;
			float ang = i * 1.05f + t * 0.35f;
			pose.pushPose();
			pose.translate(Mth.cos(ang) * 0.16f, along, Mth.sin(ang) * 0.16f);
			pose.mulPose(Axis.YP.rotation(-ang));
			pose.mulPose(Axis.ZP.rotation(0.6f));
			float fade = Math.min(1.0f, Math.min(along, len - along) * 2.0f);
			IceDraw.shard(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.06f, 0.22f * fade, ((int) (220 * fade) << 24) | 0xE6F6FF, full);
			pose.popPose();
		}
		// a frost ring every 2.5 blocks
		for (float r = 1.0f + (t * 0.3f) % 2.5f; r < len; r += 2.5f) {
			pose.pushPose();
			pose.translate(0, r, 0);
			pose.mulPose(Axis.YP.rotation(t * 0.2f + r));
			for (int k = 0; k < 6; k++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotation(k * Mth.TWO_PI / 6));
				pose.translate(0.2f, 0, 0);
				pose.mulPose(Axis.ZP.rotation(-Mth.HALF_PI));
				IceDraw.crystal(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.05f, 0.12f, 0, 0xC0E6F6FF, full);
				pose.popPose();
			}
			pose.popPose();
		}
		// the frost bloom where it lands
		pose.translate(0, len, 0);
		for (int k = 0; k < 7; k++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotation(k * Mth.TWO_PI / 7 + t * 0.1f));
			pose.mulPose(Axis.ZP.rotation(1.2f + 0.25f * Mth.sin(t * 0.8f + k)));
			pose.mulPose(Axis.XP.rotation(Mth.PI));
			IceDraw.crystal(pose, buf.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.09f, 0.32f + 0.08f * Mth.sin(t + k), 0, 0xE0EEFAFF, full);
			pose.popPose();
		}
		IceDraw.shard(pose, buf.getBuffer(RenderType.eyes(IceDraw.WHITE)), 0.16f * pulse, 0.3f * pulse, 0xC0BDEFFF, full);
		pose.popPose();
	}
}
