package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroBoltRenderer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.mutation.pyro.PyroFireSkin;
import com.projecthero.mod.client.mutation.pyro.PyroFxRenderer;
import com.projecthero.mod.client.mutation.pyro.PyroSerpentRenderer;
import com.projecthero.mod.hero.power.p08.PyroEntities;
import com.projecthero.mod.hero.power.p08.PyroFxEntity;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis rework -- everything the fire looks like, on every client, from the synced {@code p08.*} visual
 * values ({@link PyrokinesisHandlers#registerVisuals}):
 * <ul>
 * <li>the body: a heat glow that brightens with Heat (glowing eyes from 80), Flame Body's living-fire figure (an emissive
 * flame skin plus real fire licking up off every limb), jet flames at the hands and feet, flames streaming off a Flame
 * Dash, the Meteor swelling over your head while you charge it, a white-hot flash for Cauterize, the dull red smoulder of
 * an overheat;</li>
 * <li>in the world: the Fire Whip (a tapering lash along an S-curve that cracks and recoils), the Fire Lasso (spinning rings
 * of fire round the target and a burning rope to your hand) and the Flamethrower's roaring cone;</li>
 * <li>the entity models: {@link PyroBoltRenderer} (Fireball, Meteor) and {@link PyroFxRenderer} (Flame Wall, novas, Heat
 * Wave, the Meteor blast);</li>
 * <li>the cast poses ({@link MutationPose});</li>
 * <li>v0.16.6 hold moves: the Blue Flame swelling in the hand, rings of fire down the arm for the Flame Serpent
 * ({@link PyroSerpentRenderer}), the Phoenix's wings / tail / crest, the Solar Flare's white-hot body and its screen
 * flash.</li>
 * </ul>
 */
public final class PyrokinesisClient {
	private static final ResourceLocation FLAME = ProjectHeroMod.id("textures/entity/mutation/p08_flame_body.png");
	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private PyrokinesisClient() {
	}

	/** From {@code RevampClientB.init()}. */
	public static void init() {
		com.projecthero.mod.client.gui.AbilityHudExtras.mono(PyrokinesisHandlers.KEY);
		registerPoses();
		MutationOverlays.register("p08.glow", PyrokinesisClient::heatGlow);
		MutationOverlays.register("p08.flame_body", PyrokinesisClient::flameBody);
		MutationOverlays.register("p08.dash", PyrokinesisClient::dashFlames);
		MutationOverlays.register("p08.jet", PyrokinesisClient::jetFlames);
		MutationOverlays.register("p08.charging", PyrokinesisClient::meteorOverhead);
		MutationOverlays.register("p08.cauterize", PyrokinesisClient::cauterizeFlash);
		MutationOverlays.register("p08.locked", PyrokinesisClient::smoulder);
		WorldRenderEvents.AFTER_ENTITIES.register(PyrokinesisClient::renderWorld);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PyroFireSkin::tick);
		EntityRendererRegistry.register(PyroEntities.BOLT, PyroBoltRenderer::new);
		EntityRendererRegistry.register(PyroEntities.FX, PyroFxRenderer::new);
		// v0.16.6 hold moves
		EntityRendererRegistry.register(PyroEntities.SERPENT, PyroSerpentRenderer::new);
		MutationOverlays.register("p08.blue_charge", PyrokinesisClient::blueCharge);
		MutationOverlays.register("p08.serpent_cast", PyrokinesisClient::serpentCast);
		MutationOverlays.register("p08.phoenix", PyrokinesisClient::phoenixBody);
		MutationOverlays.register("p08.flare", PyrokinesisClient::flareBody);
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(PyrokinesisClient::flash);
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
		// R Fireball: wind the right arm up and back, hurl it forward along the aim
		float[] wind = p(-2.7f, 0.3f, 0.35f, 0.4f, 0, -0.25f, -0.08f, 0.45f, 0.15f, -0.15f, 0);
		float[] hurl = p(AIM, AIM, 0, 0.5f, 0, -0.2f, 0.18f, -0.4f, -0.25f, 0.25f, 0);
		MutationPose.register("p08.throw", new float[][] { zero(0), at(2, wind), at(5, hurl), at(10, hurl), zero(14) });
		// Shift+R Flamethrower: both palms out along the aim, braced, a little recoil shake
		float[] spray = p(AIM, AIM, 0, AIM, AIM, 0, 0.12f, 0, 0.3f, -0.3f, 0);
		float[] spray2 = p(AIM, AIM, 0, AIM, AIM, 0, 0.15f, 0, 0.32f, -0.32f, 0.02f);
		MutationPose.registerLoop("p08.thrower", 3, new float[][] { zero(0), at(3, spray), at(6, spray2), at(9, spray) });
		// G Fire Whip: arm up over the shoulder, then the crack straight down the aim and a follow-through
		float[] whipUp = p(-3.0f, 0.2f, 0.45f, 0.3f, 0, -0.2f, -0.12f, 0.35f, 0.15f, -0.15f, -0.1f);
		float[] whipCrack = p(AIM, AIM, 0, 0.45f, 0, -0.2f, 0.25f, -0.25f, -0.3f, 0.3f, 0.05f);
		float[] whipThrough = p(-0.7f, -0.7f, -0.1f, 0.4f, 0, -0.15f, 0.2f, -0.45f, -0.25f, 0.25f, 0);
		MutationPose.register("p08.whip", new float[][] { zero(0), at(2, whipUp), at(4, whipCrack), at(8, whipThrough), zero(13) });
		// Shift+G Fire Lasso: a whirl overhead, the throw, then both hands hauling on the rope, leaning back
		float[] whirlA = p(-2.9f, 0, 0.7f, -0.6f, 0, -0.1f, 0, 0, 0, 0, 0);
		float[] whirlB = p(-2.9f, 0.6f, -0.1f, -0.6f, 0, -0.1f, 0, 0, 0, 0, 0);
		float[] haul = p(AIM, AIM, 0, AIM, AIM, 0, -0.28f, 0, 0.45f, -0.35f, 0);
		float[] haul2 = p(AIM, AIM, 0, AIM, AIM, 0, -0.33f, 0, 0.5f, -0.38f, 0.03f);
		MutationPose.registerLoop("p08.lasso", 9, new float[][] { zero(0), at(2, whirlA), at(4, whirlB), at(6, hurl), at(9, haul),
				at(15, haul2), at(21, haul) });
		// X Flame Dash: thrown forward, arms swept back like a sprinter's launch
		float[] dash = p(1.05f, 0, 0.3f, 1.05f, 0, -0.3f, 0.55f, 0, -0.75f, 0.6f, -0.45f);
		MutationPose.register("p08.dash", new float[][] { zero(0), at(1, dash), at(6, dash), zero(10) });
		// Shift+X Jet Flight: arms down at the sides, palms down to the jets, legs together; a slight bob
		float[] jet = p(0.12f, 0, 0.32f, 0.12f, 0, -0.32f, 0.08f, 0, 0.05f, 0.05f, 0);
		float[] jet2 = p(0.16f, 0, 0.36f, 0.16f, 0, -0.36f, 0.1f, 0, 0.08f, 0.02f, 0);
		MutationPose.registerLoop("p08.jet", 3, new float[][] { zero(0), at(3, jet), at(11, jet2), at(19, jet) });
		// Z Meteor: both arms raised high holding the swelling ball, braced; then slammed forward
		float[] lift = p(-2.95f, 0, 0.32f, -2.95f, 0, -0.32f, -0.12f, 0, 0.2f, -0.2f, -0.2f);
		float[] lift2 = p(-3.05f, 0, 0.36f, -3.05f, 0, -0.36f, -0.15f, 0, 0.22f, -0.22f, -0.25f);
		MutationPose.registerLoop("p08.meteor_charge", 4, new float[][] { zero(0), at(4, lift), at(10, lift2), at(16, lift) });
		float[] slam = p(AIM, AIM, 0, AIM, AIM, 0, 0.3f, 0, -0.35f, 0.35f, 0.05f);
		MutationPose.register("p08.meteor_throw", new float[][] { at(0, lift), at(3, slam), at(10, slam), zero(15) });
		// Shift+Z Inferno / the overheat nova: curl in, then throw every limb outward
		float[] curl = p(-0.7f, -0.6f, 0.1f, -0.7f, 0.6f, -0.1f, 0.4f, 0, 0.3f, 0.2f, 0.35f);
		float[] burst = p(-0.4f, 0, 1.6f, -0.4f, 0, -1.6f, -0.25f, 0, -0.2f, 0.2f, -0.45f);
		MutationPose.register("p08.nova", new float[][] { zero(0), at(4, curl), at(7, burst), at(14, burst), zero(20) });
		// V Flame Wall: a low sweep of the right arm across the front
		float[] sweepA = p(-1.3f, 0.9f, 0, 0.3f, 0, -0.3f, 0.25f, 0.45f, 0.2f, -0.2f, 0.1f);
		float[] sweepB = p(-1.3f, -0.9f, 0, 0.3f, 0, -0.3f, 0.25f, -0.45f, -0.2f, 0.2f, 0.1f);
		MutationPose.register("p08.wall", new float[][] { zero(0), at(3, sweepA), at(7, sweepB), at(11, sweepB), zero(15) });
		// Shift+V Flame Body: fists clenched at the sides, chest out, head back as the fire takes
		float[] ignite = p(0.25f, 0, 0.55f, 0.25f, 0, -0.55f, -0.18f, 0, 0, 0, -0.35f);
		MutationPose.register("p08.ignite", new float[][] { zero(0), at(3, ignite), at(11, ignite), zero(15) });
		// C Heat Wave: both palms shoved out
		float[] pushIn = p(-0.9f, 0.3f, 0.2f, -0.9f, -0.3f, -0.2f, -0.1f, 0, 0.2f, -0.2f, 0);
		float[] pushOut = p(-1.6f, -0.1f, 0, -1.6f, 0.1f, 0, 0.3f, 0, -0.45f, 0.45f, 0.1f);
		MutationPose.register("p08.wave", new float[][] { zero(0), at(3, pushIn), at(6, pushOut), at(12, pushOut), zero(17) });
		// Shift+C Cauterize: right hand pressed to the chest, head bowed
		float[] sear = p(-1.25f, -0.95f, 0, 0.1f, 0, -0.1f, 0.22f, 0, 0, 0, 0.35f);
		MutationPose.register("p08.cauterize", new float[][] { zero(0), at(3, sear), at(15, sear), zero(19) });
		// overheated: hunched, arms hanging, head down, smoking
		float[] spent = p(-0.25f, 0.1f, 0.15f, -0.25f, -0.1f, -0.15f, 0.45f, 0, 0.3f, 0.2f, 0.45f);
		float[] spent2 = p(-0.3f, 0.12f, 0.12f, -0.2f, -0.12f, -0.18f, 0.5f, 0, 0.3f, 0.2f, 0.5f);
		MutationPose.registerLoop("p08.overheat", 4, new float[][] { zero(0), at(4, spent), at(14, spent2), at(24, spent) });

		// ---- v0.16.6 hold moves
		// hold R Blue Flame: the right hand drawn back by the ear cradling the ball, the left aimed at the mark
		float[] blueA = p(-2.35f, 0.55f, 0.25f, AIM, AIM, 0, -0.06f, 0.55f, 0.15f, -0.15f, 0);
		float[] blueB = p(-2.45f, 0.6f, 0.3f, AIM, AIM, 0, -0.08f, 0.6f, 0.17f, -0.17f, 0.02f);
		MutationPose.registerLoop("p08.blue_charge", 3, new float[][] { zero(0), at(3, blueA), at(7, blueB), at(11, blueA) });
		// hold G Flame Serpent: a whirl of the arm overhead, then thrust out along the aim as the serpent pours off it
		float[] coilUp = p(-2.9f, 0.5f, 0.6f, 0.3f, 0, -0.2f, -0.1f, 0.4f, 0.15f, -0.15f, -0.1f);
		float[] coilOut = p(AIM, AIM, 0, 0.5f, 0, -0.25f, 0.2f, -0.35f, -0.3f, 0.3f, 0);
		MutationPose.register("p08.serpent", new float[][] { zero(0), at(3, coilUp), at(7, coilOut), at(14, coilOut), zero(18) });
		// hold X Phoenix Dive: arms flung wide and beating like wings on the way up, swept back hard in the dive
		float[] wingsUp = p(-0.35f, 0, 1.75f, -0.35f, 0, -1.75f, -0.15f, 0, 0.1f, -0.1f, -0.3f);
		float[] wingsDown = p(-0.1f, 0, 1.15f, -0.1f, 0, -1.15f, 0.05f, 0, 0.15f, -0.05f, -0.2f);
		MutationPose.registerLoop("p08.phoenix_rise", 2, new float[][] { zero(0), at(2, wingsUp), at(6, wingsDown), at(10, wingsUp) });
		float[] diveA = p(1.25f, 0, 0.45f, 1.25f, 0, -0.45f, 0.6f, 0, 0.3f, 0.3f, -0.6f);
		float[] diveB = p(1.3f, 0, 0.5f, 1.3f, 0, -0.5f, 0.62f, 0, 0.32f, 0.28f, -0.62f);
		MutationPose.registerLoop("p08.phoenix_dive", 2, new float[][] { zero(0), at(2, diveA), at(5, diveB), at(8, diveA) });
		// hold Z Meteor Shower: both arms to the sky, calling them down, then a sweep down along the aim
		float[] call = p(-3.0f, 0, 0.45f, -3.0f, 0, -0.45f, -0.2f, 0, 0.2f, -0.2f, -0.45f);
		MutationPose.register("p08.shower", new float[][] { zero(0), at(3, call), at(9, call), at(13, slam), at(18, slam), zero(23) });
		// hold V Ring of Fire: a crouch with both hands sweeping out low and round
		float[] ringLow = p(-0.5f, 0, 1.1f, -0.5f, 0, -1.1f, 0.35f, 0, 0.45f, -0.25f, 0.2f);
		float[] ringOut = p(-0.15f, 0, 1.5f, -0.15f, 0, -1.5f, 0.0f, 0, 0.0f, 0.0f, -0.1f);
		MutationPose.register("p08.ring", new float[][] { zero(0), at(3, ringLow), at(8, ringOut), at(14, ringOut), zero(18) });
		// hold C Solar Flare: curl in, then everything flung up and out, head thrown back
		float[] flareOut = p(-2.6f, 0, 0.9f, -2.6f, 0, -0.9f, -0.3f, 0, -0.1f, 0.1f, -0.55f);
		MutationPose.register("p08.flare", new float[][] { zero(0), at(3, curl), at(6, flareOut), at(14, flareOut), zero(20) });
	}

	// ================================================================== body overlays

	private static float heat(MutationOverlays.Context ctx) {
		return Mth.clamp(ctx.state().value("p08.heat", 0f), 0f, 1f);
	}

	private static float time(MutationOverlays.Context ctx) {
		return ctx.ageInTicks() + ctx.partialTick();
	}

	/** Heat glow: the skin starts to shine from 20 Heat, brighter and whiter as it climbs; the eyes burn from 80. */
	private static void heatGlow(MutationOverlays.Context ctx) {
		float h = heat(ctx);
		if (ctx.state().has("p08.flame_body")) {
			return; // the fire figure has its own glow
		}
		float k = Mth.clamp((h - 0.15f) / 0.85f, 0f, 1f);
		float pulse = 0.85f + 0.15f * MutationRender.pulse(ctx, 18f);
		// glowing ember veins (the flame skin, dim) over a faint all-over warmth
		int c = PyroDraw.dim(PyroDraw.lerpRgb(0xFFC03000, 0xFFFFA040, k), (0.3f + 0.6f * k) * pulse);
		MutationRender.shell(ctx, FLAME, MutationRender.Shell.THIN, c, true);
		MutationRender.shell(ctx, MutationRender.WHITE, MutationRender.Shell.THIN, PyroDraw.dim(0xFF802000, 0.12f + 0.18f * k), true);
		if (h >= 0.8f) {
			MutationRender.eyes(ctx, 0xFFFFE6A0);
			whiteHotHands(ctx.pose(), ctx.buffers(), ctx.model(), pulse);
		}
	}

	/**
	 * The hands glowing white-hot (high Heat). v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"):
	 * public, shared with Heatblast's Heat Aura ({@code HeatblastClient}); {@code pulse} sizes the glows.
	 */
	public static void whiteHotHands(PoseStack pose, MultiBufferSource buffers, net.minecraft.client.model.HumanoidModel<?> m, float pulse) {
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		for (boolean right : new boolean[] { true, false }) {
			pose.pushPose();
			ModelPart arm = right ? m.rightArm : m.leftArm;
			arm.translateAndRotate(pose);
			pose.translate((right ? -1.0 : 1.0) / 16.0, 10.0 / 16.0, 0.0);
			PyroDraw.glow(pose, glow, Vec3.ZERO, 0.32f * pulse, PyroDraw.dim(0xFFFF8030, 0.8f));
			pose.popPose();
		}
	}

	/** Where (model space, blocks) the middle of {@code part}'s cube is, given its local middle in pixels. */
	private static Vector3f centre(ModelPart part, float lx, float ly, float lz) {
		Vector3f off = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(lx, ly, lz));
		return new Vector3f((part.x + off.x) / 16.0f, (part.y + off.y) / 16.0f, (part.z + off.z) / 16.0f);
	}

	/** Real fire licking up off every limb (world-up, whatever the pose). */
	private static void limbFlames(MutationOverlays.Context ctx, float scale, float lean) {
		limbFlames(ctx.pose(), ctx.buffers(), ctx.model(), "slim".equals(ctx.player().getSkin().model().id()), time(ctx), scale, lean);
	}

	/**
	 * v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the limb flames on any posed player model
	 * -- shared with Heatblast's Heat Shield and Jet Dash ({@code HeatblastClient}). {@code pose} is the model's render space,
	 * {@code t} the age in ticks; {@code lean} tips the flames back (a dash).
	 */
	public static void limbFlames(PoseStack pose, MultiBufferSource buffers, net.minecraft.client.model.HumanoidModel<?> m, boolean slim, float t,
			float scale, float lean) {
		float ax = slim ? 0.5f : 1.0f;
		Object[][] parts = {
				{ m.body, new float[] { 0, 9, 0 }, 0.75f, 0.85f },
				{ m.body, new float[] { 0, 2, 0 }, 0.6f, 0.6f },
				{ m.head, new float[] { 0, -7, 0 }, 0.6f, 0.7f },
				{ m.rightArm, new float[] { -ax, 5, 0 }, 0.42f, 0.6f },
				{ m.leftArm, new float[] { ax, 5, 0 }, 0.42f, 0.6f },
				{ m.rightLeg, new float[] { 0, 7, 0 }, 0.45f, 0.55f },
				{ m.leftLeg, new float[] { 0, 7, 0 }, 0.45f, 0.55f } };
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		for (int i = 0; i < parts.length; i++) {
			ModelPart part = (ModelPart) parts[i][0];
			if (!part.visible) {
				continue;
			}
			float[] l = (float[]) parts[i][1];
			Vector3f c = centre(part, l[0], l[1], l[2]);
			float flick = 0.8f + 0.2f * Mth.sin(t * 0.55f + i * 2.1f);
			pose.pushPose();
			pose.translate(c.x, c.y, c.z + lean * 0.15f);
			pose.scale(1.0f, -1.0f, 1.0f); // model space is y-down: flames rise
			if (lean != 0) {
				pose.mulPose(new Quaternionf().rotationX(-lean));
			}
			float w = (float) parts[i][2] * scale;
			float h = (float) parts[i][3] * scale * flick;
			pose.translate(0, -h * 0.35f, 0);
			PyroDraw.fireCross(pose, fire, w, h, (int) (t / 3) + i, i * 0.8f + t * 0.02f);
			pose.popPose();
		}
	}

	/**
	 * Flame Body: the living-fire figure. The body itself wears the user's flame skin ({@code PyroFlameSkinMixin}); over every
	 * part of it the live fire layer climbs and flickers (with a fainter, looser copy just outside it), the eyes burn and
	 * real fire licks up off the limbs.
	 */
	private static void flameBody(MutationOverlays.Context ctx) {
		float h = heat(ctx);
		if (PyroFireSkin.ready()) {
			MutationRender.shell(ctx, PyroFireSkin.FIRE, MutationRender.Shell.THIN, 0xFFFFFFFF, true);
			MutationRender.shell(ctx, PyroFireSkin.FIRE, MutationRender.Shell.THICK, PyroDraw.dim(0xFFFFFFFF, 0.35f + 0.25f * h), true);
		}
		MutationRender.eyes(ctx, 0xFFFFF0B0);
		limbFlames(ctx, 1.1f + 0.3f * h, 0.0f);
	}

	/** The whole figure wreathed in fire: big crossed sheets from the feet to above the head, turned with the body. */
	private static void bodyFire(MutationOverlays.Context ctx, float scale) {
		bodyFire(ctx.pose(), ctx.buffers(), time(ctx), scale);
	}

	/**
	 * v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the fire wreath in any player model's
	 * render space ({@code t} the age in ticks) -- shared with Heatblast's Jet Dash ({@code HeatblastClient}).
	 */
	public static void bodyFire(PoseStack pose, MultiBufferSource buffers, float t, float scale) {
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		for (int i = 0; i < 2; i++) {
			float flick = 0.85f + 0.15f * Mth.sin(t * 0.4f + i * 2.0f);
			pose.pushPose();
			pose.translate(0.0f, 1.5f, 0.0f); // the feet (model space is y-down, feet at +1.5)
			pose.scale(1.0f, -1.0f, 1.0f);
			PyroDraw.fireCross(pose, fire, 0.95f * scale, 1.95f * scale * flick, (int) (t / 3) + i * 7, Mth.HALF_PI * 0.5f + i * 0.4f);
			pose.popPose();
		}
	}

	/** Flame Dash: the body bursts into flame that streams back off it. */
	private static void dashFlames(MutationOverlays.Context ctx) {
		MutationRender.shell(ctx, FLAME, MutationRender.Shell.THIN, 0xFFFFA040, true);
		bodyFire(ctx, 1.1f);
		limbFlames(ctx, 1.6f, 0.9f);
	}

	/** Jet Flight: roaring jets out of both palms and both soles. */
	private static void jetFlames(MutationOverlays.Context ctx) {
		jetFlames(ctx.pose(), ctx.buffers(), ctx.model(), "slim".equals(ctx.player().getSkin().model().id()), time(ctx), 1.0f);
	}

	/**
	 * v0.17.2 (user: "give him the same flaming fire flight vfx as the pyrokinesis power"): the Jet Flight flames on any posed
	 * player model -- shared with Heatblast's Fire Jet ({@code HeatblastClient}). {@code pose} is the model's render space (a
	 * render layer's), {@code t} the age in ticks, {@code scale} sizes every jet (1 = Pyrokinesis').
	 */
	public static void jetFlames(PoseStack outer, MultiBufferSource buffers, net.minecraft.client.model.HumanoidModel<?> m, boolean slim, float t,
			float scale) {
		float ax = slim ? 0.5f : 1.0f;
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		Object[][] ends = { { m.rightArm, -ax, 10.0f, 0.75f }, { m.leftArm, ax, 10.0f, 0.75f }, { m.rightLeg, 0.0f, 12.0f, 1.0f },
				{ m.leftLeg, 0.0f, 12.0f, 1.0f } };
		for (int i = 0; i < ends.length; i++) {
			ModelPart part = (ModelPart) ends[i][0];
			float k = (float) ends[i][3] * scale;
			float flick = 0.8f + 0.25f * Mth.sin(t * 2.4f + i * 1.7f) + 0.1f * Mth.sin(t * 5.1f + i);
			PoseStack pose = outer;
			pose.pushPose();
			part.translateAndRotate(pose);
			pose.translate((float) ends[i][1] / 16.0f, (float) ends[i][2] / 16.0f + 0.01f, 0.0f);
			PyroDraw.cone(pose, add, 0.11f * k, 0.55f * k * flick, 0xFFFFF4D0, 0xFF000000);
			PyroDraw.cone(pose, add, 0.2f * k, 0.95f * k * flick, 0xFFFF7A20, 0xFF000000);
			PyroDraw.fireCross(pose, fire, 0.42f * k, 0.75f * k * flick, (int) (t / 2) + i, t * 0.3f + i);
			PyroDraw.glow(pose, glow, new Vec3(0, 0.12, 0), 0.38f * k, PyroDraw.dim(0xFFFF9040, 0.8f));
			pose.popPose();
		}
	}

	/** Meteor charging: the ball of fire swelling between the raised hands. */
	private static void meteorOverhead(MutationOverlays.Context ctx) {
		float c = Mth.clamp(ctx.state().value("p08.charge", 0f), 0f, 1f);
		float t = time(ctx);
		float r = 0.28f + 0.55f * c;
		PoseStack pose = ctx.pose();
		pose.pushPose();
		// the hands meet ~10 px above the shoulders when both arms are straight up
		pose.translate(0.0f, -0.62f - r * 0.85f, 0.0f);
		PyroDraw.fireball(pose, ctx.buffers(), r, t, new Vec3(0, 1, 0), true);
		pose.popPose();
	}

	/** Cauterize: a white-hot flash over the chest and the hand pressed to it. */
	private static void cauterizeFlash(MutationOverlays.Context ctx) {
		float k = 0.6f + 0.4f * MutationRender.pulse(ctx, 6f);
		MutationRender.shell(ctx, FLAME, MutationRender.Shell.THIN, PyroDraw.dim(0xFFFFE0A0, k), true);
		MutationRender.shell(ctx, MutationRender.WHITE, MutationRender.Shell.THICK, PyroDraw.dim(0xFFFF9040, 0.45f * k), true);
		PoseStack pose = ctx.pose();
		pose.pushPose();
		pose.translate(0.0f, 0.3f, -0.18f);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(ctx.buffers()), Vec3.ZERO, 0.55f * k, 0xFFFFE0A0);
		pose.popPose();
	}

	/** Overheated: a dull, pulsing red smoulder. */
	private static void smoulder(MutationOverlays.Context ctx) {
		float k = 0.5f + 0.5f * MutationRender.pulse(ctx, 14f);
		MutationRender.shell(ctx, MutationRender.WHITE, MutationRender.Shell.THIN, PyroDraw.dim(0xFF801000, 0.35f + 0.25f * k), true);
	}

	// ================================================================== v0.16.6 hold moves (body)

	/** Where the right hand is in model space (after the arm's rotation), for things held in it. */
	private static void toRightHand(MutationOverlays.Context ctx) {
		boolean slim = "slim".equals(ctx.player().getSkin().model().id());
		ctx.model().rightArm.translateAndRotate(ctx.pose());
		ctx.pose().translate((slim ? -0.5 : -1.0) / 16.0, 10.5 / 16.0, 0.0);
	}

	/** Hold R, the wind-up: a blue-white fireball swelling in the drawn-back hand. */
	private static void blueCharge(MutationOverlays.Context ctx) {
		float t = time(ctx);
		PoseStack pose = ctx.pose();
		pose.pushPose();
		toRightHand(ctx);
		float r = 0.3f + 0.04f * Mth.sin(t * 1.3f);
		PyroDraw.fireball(pose, ctx.buffers(), r, t, new Vec3(0, -1, 0), false, true);
		pose.popPose();
		MutationRender.shell(ctx, MutationRender.WHITE, MutationRender.Shell.THIN, PyroDraw.dim(0xFF2050C0, 0.25f), true);
	}

	/** Hold G, the cast: rings of fire spiral down the right arm as the serpent pours off it. */
	private static void serpentCast(MutationOverlays.Context ctx) {
		float t = time(ctx);
		PoseStack pose = ctx.pose();
		VertexConsumer add = PyroDraw.additive(ctx.buffers());
		pose.pushPose();
		ctx.model().rightArm.translateAndRotate(pose);
		pose.translate(-1.0 / 16.0, 0, 0);
		for (int i = 0; i < 4; i++) {
			float y = (((t * 0.9f + i * 3.0f) % 12.0f) / 12.0f) * 0.75f;
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.mulPose(new Quaternionf().rotationX(0.25f * Mth.sin(t * 0.4f + i)));
			PyroDraw.band(pose, add, 0.2f, 0.05f, 0xFFFFB050, 0xFF401000);
			pose.popPose();
		}
		PyroDraw.glow(pose, PyroDraw.glowBuffer(ctx.buffers()), new Vec3(0, 0.66, 0), 0.4f, PyroDraw.dim(0xFFFF8030, 0.9f));
		pose.popPose();
	}

	/** Hold C: the caster is the heart of the sun for a moment -- white-hot all over. */
	private static void flareBody(MutationOverlays.Context ctx) {
		MutationRender.shell(ctx, FLAME, MutationRender.Shell.THIN, 0xFFFFF4D0, true);
		MutationRender.shell(ctx, MutationRender.WHITE, MutationRender.Shell.THICK, PyroDraw.dim(0xFFFFE0A0, 0.6f), true);
		MutationRender.eyes(ctx, 0xFFFFFFFF);
	}

	/**
	 * Hold X: the Phoenix -- the caster wrapped in a firebird: two great wings of glowing feathers off the shoulder blades
	 * (beating while it rises, swept back in the dive), a fanned tail, a flame crest on the head and the body ablaze.
	 */
	private static void phoenixBody(MutationOverlays.Context ctx) {
		float t = time(ctx);
		boolean dive = ctx.state().has("p08.phoenix_dive");
		MutationRender.shell(ctx, FLAME, MutationRender.Shell.THIN, 0xFFFFB050, true);
		limbFlames(ctx, dive ? 1.4f : 1.0f, dive ? 0.9f : 0.0f);
		MutationRender.eyes(ctx, 0xFFFFF0B0);
		PoseStack pose = ctx.pose();
		VertexConsumer add = PyroDraw.additive(ctx.buffers());
		VertexConsumer fire = PyroDraw.fireBuffer(ctx.buffers());
		VertexConsumer glow = PyroDraw.glowBuffer(ctx.buffers());
		pose.pushPose();
		ctx.model().body.translateAndRotate(pose);
		// model space: y down, the back is +z, the player's left is +x
		float lift = dive ? 0.18f + 0.04f * Mth.sin(t * 2.2f) : 0.3f + 0.6f * Mth.sin(t * 0.75f);
		float sweep = dive ? 1.05f : 0.3f;
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 0.1f, 0.16f, 0.16f);
			pose.mulPose(new Quaternionf().rotationY(-s * sweep));
			pose.mulPose(new Quaternionf().rotationZ(-s * lift));
			wing(pose, add, fire, glow, s, t);
			pose.popPose();
		}
		// the tail: a fan of long feathers off the small of the back
		for (int i = -2; i <= 2; i++) {
			float sway = 0.06f * Mth.sin(t * 0.5f + i);
			Vec3 root = new Vec3(i * 0.05, 0.7, 0.14);
			Vec3 tip = new Vec3(i * 0.32 + sway, 1.6 - Math.abs(i) * 0.12, dive ? 1.1 : 0.75);
			Vec3 bow = root.lerp(tip, 0.5).add(0, 0, 0.18);
			PyroDraw.blade(pose, add, root, bow, tip, new Vec3(1, 0, 0), 0.12f, 0.3f, 0xFFFFE0A0, PyroDraw.dim(0xFFC02800, 0.8f));
		}
		pose.popPose();
		// the crest on the head: three flame plumes swept back
		pose.pushPose();
		ctx.model().head.translateAndRotate(pose);
		for (int i = -1; i <= 1; i++) {
			Vec3 root = new Vec3(i * 0.1, -0.5, -0.05);
			Vec3 tip = new Vec3(i * 0.18, -0.9 + Math.abs(i) * 0.12, 0.55);
			PyroDraw.blade(pose, add, root, root.lerp(tip, 0.5).add(0, -0.12, 0), tip, new Vec3(1, 0, 0), 0.1f, 0.02f, 0xFFFFF0C0,
					PyroDraw.dim(0xFFFF5010, 0.7f));
		}
		pose.popPose();
	}

	/** One wing in its own frame: the leading edge runs out along x = {@code s}, the feathers hang down (+y) and back (+z). */
	private static void wing(PoseStack pose, VertexConsumer add, VertexConsumer fire, VertexConsumer glow, int s, float t) {
		float span = 2.1f;
		int feathers = 9;
		Vec3 prevEdge = null;
		for (int i = 0; i < feathers; i++) {
			float u = i / (float) (feathers - 1);
			// the leading edge arches up to the wrist and droops to the tip
			Vec3 edge = new Vec3(s * span * u, -0.38 * Mth.sin(Mth.PI * Math.min(1.0f, u * 1.25f)) + 0.12 * u * u, 0.05 * u);
			float len = 0.55f + 0.95f * u * (1.1f - 0.3f * u);
			Vec3 dir = new Vec3(s * (0.15 + 0.9 * u * u), 0.85 * (1.0 - u) + 0.25, 0.35).normalize();
			float ripple = 0.05f * Mth.sin(t * 1.4f - u * 5.0f);
			Vec3 tip = edge.add(dir.scale(len)).add(0, ripple, 0);
			Vec3 bow = edge.lerp(tip, 0.5).add(0, 0, 0.12);
			Vec3 side = dir.cross(new Vec3(0, 0, 1));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			// a dim, wide underlayer of heat and the bright feather over it
			PyroDraw.blade(pose, add, edge, bow, tip, side, 0.42f, 0.22f, PyroDraw.dim(0xFFB03000, 0.6f), 0xFF000000);
			PyroDraw.blade(pose, add, edge, bow, tip, side, 0.24f, 0.07f, 0xFFFFF0B0, PyroDraw.dim(0xFFE04010, 0.9f));
			if (prevEdge != null) {
				// the covert: a band of fire along the leading edge between this feather and the last
				PyroDraw.blade(pose, add, prevEdge, prevEdge.lerp(edge, 0.5).add(0, -0.03, 0), edge, new Vec3(0, 1, 0.3).normalize(), 0.22f,
						0.22f, 0xFFFFC060, 0xFFFFC060);
			}
			if (i % 2 == 1) {
				pose.pushPose();
				pose.translate(edge.x, edge.y, edge.z);
				pose.scale(1.0f, -1.0f, 1.0f); // model space is y-down: flames rise
				PyroDraw.fireCross(pose, fire, 0.35f, 0.5f + 0.15f * Mth.sin(t * 0.8f + i), (int) (t / 2) + i, i * 0.9f);
				pose.popPose();
			}
			prevEdge = edge;
		}
		PyroDraw.glow(pose, glow, new Vec3(s * span * 0.55, 0.35, 0.2), 1.3f, PyroDraw.dim(0xFFFF7020, 0.55f));
	}

	// ================================================================== v0.16.6 the Solar Flare's flash

	/** A white flash over the screen of everyone near a Solar Flare (softer for its caster). */
	private static void flash(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float best = 0f;
		for (PyroFxEntity fx : mc.level.getEntitiesOfClass(PyroFxEntity.class, mc.player.getBoundingBox().inflate(PyrokinesisHandlers.FLARE_RADIUS + 4))) {
			if (fx.kind() != PyroFxEntity.FLARE) {
				continue;
			}
			float age = fx.age() + partial;
			double d = fx.position().distanceTo(mc.player.getEyePosition(partial));
			float k = (float) Math.max(0.0, 1.0 - Math.max(0.0, d - PyrokinesisHandlers.FLARE_RADIUS * 0.6) / (PyrokinesisHandlers.FLARE_RADIUS * 0.6));
			float a = age < 2.0f ? age / 2.0f : Math.max(0.0f, 1.0f - (age - 2.0f) / 16.0f);
			float own = fx.ownerEntityId() == mc.player.getId() ? 0.4f : 0.95f;
			best = Math.max(best, a * a * k * own);
		}
		if (best > 0.01f) {
			int alpha = Math.round(255 * Math.min(1f, best));
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | 0xFFF8E8);
		}
	}

	// ================================================================== world: whip, lasso, flamethrower

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** Age (ticks, fractional) since synced tick stamp {@code t0} (game time mod 8192 + 1). */
	private static float age(Player p, float t0, float partial) {
		long gt = p.level().getGameTime();
		return (float) Math.floorMod(gt % 8192L - (long) (t0 - 1.0f), 8192L) + partial;
	}

	private static boolean firstPerson(Player p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	private static Vec3 rightOf(Vec3 look) {
		Vec3 r = look.cross(new Vec3(0, 1, 0));
		return r.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : r.normalize();
	}

	/** The right hand, world space, of an arm aimed along the look (or both hands together for {@code both}). */
	private static Vec3 hand(Player p, float partial, boolean both) {
		Vec3 look = p.getViewVector(partial);
		Vec3 right = rightOf(look);
		Vec3 up = right.cross(look).normalize();
		if (firstPerson(p)) {
			Vec3 eye = p.getEyePosition(partial);
			return eye.add(look.scale(0.6)).add(right.scale(both ? 0.05 : 0.3)).subtract(up.scale(0.3));
		}
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 bodyRight = new Vec3(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
		float sc = p.getScale();
		Vec3 shoulder = p.getPosition(partial).add(0, (p.isCrouching() ? 1.15 : 1.38) * sc, 0).add(bodyRight.scale(both ? 0.0 : 0.36 * sc));
		return shoulder.add(look.scale(0.7 * sc));
	}

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player pl : mc.level.players()) {
			MutationVisualState s = MutationVisuals.state(pl);
			if (s.flags().isEmpty() && s.values().isEmpty()) {
				continue;
			}
			if (s.has("p08.whip")) {
				whip(pl, s, partial, cam, pose, buffers);
			}
			float lasso = s.value("p08.lasso", 0f);
			if (lasso > 0.5f) {
				Entity t = mc.level.getEntity(Math.round(lasso));
				if (t != null) {
					lasso(pl, t, partial, cam, pose, buffers);
				}
			}
			if (s.has("p08.thrower")) {
				thrower(pl, partial, cam, pose, buffers);
			}
		}
	}

	/** The Fire Whip: out along an S-curve in 3 ticks, cracks, then recoils and dies away. */
	private static void whip(Player pl, MutationVisualState s, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float t0 = s.value("p08.whip_t", 0f);
		float len = s.value("p08.whip_len", 0f);
		if (t0 < 0.5f || len < 0.5f) {
			return;
		}
		float a = age(pl, t0, partial);
		if (a > PyrokinesisHandlers.WHIP_DRAW_TICKS + 1) {
			return;
		}
		float out = a < 3.0f ? smooth(a / 3.0f) : a < 5.0f ? 1.0f : Math.max(0.15f, 1.0f - (a - 5.0f) / 7.0f);
		float fade = a < 8.0f ? 1.0f : Math.max(0.0f, 1.0f - (a - 8.0f) / 5.0f);
		Vec3 look = pl.getViewVector(partial);
		Vec3 right = rightOf(look);
		Vec3 up = right.cross(look).normalize();
		Vec3 h = hand(pl, partial, false).subtract(cam);
		List<Vec3> pts = new ArrayList<>();
		int n = 30;
		for (int i = 0; i <= n; i++) {
			double u = i / (double) n;
			double sway = Math.sin(u * Math.PI * 2.0 - a * 1.15) * 0.6 * u * (1.0 - 0.7 * out);
			double arc = Math.sin(u * Math.PI) * (0.7 * (1.0 - out) + 0.2);
			double droop = a > 5.0f ? u * u * 0.5 * Math.min(1.0, (a - 5.0) / 4.0) : 0.0;
			pts.add(h.add(look.scale(len * out * u)).add(right.scale(sway)).add(up.scale(arc - droop)));
		}
		VertexConsumer add = PyroDraw.additive(buffers);
		PyroDraw.ribbon(pose, add, pts, u -> 0.5 * (1.0 - 0.5 * u), u -> PyroDraw.dim(0xFF6A1A00, 0.8f * fade));
		PyroDraw.ribbon(pose, add, pts, u -> 0.2 * (1.0 - 0.6 * u), u -> PyroDraw.dim(PyroDraw.lerpRgb(0xFFFF9030, 0xFFD03800, (float) u), fade));
		PyroDraw.ribbon(pose, add, pts, u -> 0.075 * (1.0 - 0.6 * u), u -> PyroDraw.dim(0xFFFFF0C0, fade));
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		if (fade > 0.3f) {
			for (int i = 3; i <= n; i += 4) {
				double u = i / (double) n;
				float fh = (float) (0.45 * (1.0 - 0.4 * u)) * fade;
				PyroDraw.fireBillboard(pose, fire, pts.get(i).subtract(0, fh * 0.35, 0), Vec3.ZERO, fh * 0.8f, fh, (int) (a / 2) + i);
			}
		}
		if (a > 2.5f && a < 6.5f) {
			// the crack at the tip
			float k = 1.0f - Math.abs(a - 4.0f) / 2.5f;
			PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), pts.get(n), 0.5f + 0.5f * k, PyroDraw.dim(0xFFFFE0A0, k));
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** The Fire Lasso: two spinning rings of fire round the target and a burning rope back to the hands. */
	private static void lasso(Player pl, Entity t, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float time = pl.tickCount + partial;
		Vec3 base = t.getPosition(partial).subtract(cam);
		float rr = t.getBbWidth() * 0.7f + 0.28f;
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		float[] heights = { 0.62f, 0.32f };
		for (int k = 0; k < heights.length; k++) {
			pose.pushPose();
			pose.translate(base.x, base.y + t.getBbHeight() * heights[k], base.z);
			pose.mulPose(new Quaternionf().rotationY(time * (k == 0 ? 0.25f : -0.3f)));
			pose.mulPose(new Quaternionf().rotationX(0.12f * Mth.sin(time * 0.2f + k)));
			float squeeze = 1.0f - 0.05f * Mth.sin(time * 0.5f + k);
			PyroDraw.band(pose, add, rr * squeeze * 1.05f, 0.2f, PyroDraw.dim(0xFFB03000, 0.8f), 0xFF000000);
			PyroDraw.band(pose, add, rr * squeeze, 0.08f, 0xFFFFC060, 0xFF401000);
			for (int i = 0; i < 7; i++) {
				float ang = Mth.TWO_PI * i / 7;
				pose.pushPose();
				pose.translate(Mth.cos(ang) * rr * squeeze, -0.05f, Mth.sin(ang) * rr * squeeze);
				PyroDraw.fireCross(pose, fire, 0.32f, 0.42f + 0.1f * Mth.sin(time * 0.7f + i), (int) (time / 3) + i + k, ang);
				pose.popPose();
			}
			pose.popPose();
		}
		// the rope: a sagging, rippling line from the hands to the top ring
		Vec3 h = hand(pl, partial, true).subtract(cam);
		Vec3 ring = base.add(0, t.getBbHeight() * 0.62, 0);
		Vec3 toHand = h.subtract(ring).multiply(1, 0, 1);
		ring = ring.add(toHand.lengthSqr() < 1.0e-4 ? Vec3.ZERO : toHand.normalize().scale(rr));
		double dist = h.distanceTo(ring);
		List<Vec3> pts = new ArrayList<>();
		int n = 24;
		for (int i = 0; i <= n; i++) {
			double u = i / (double) n;
			Vec3 p = h.lerp(ring, u);
			double sag = Math.sin(u * Math.PI) * (0.12 * dist * 0.25 + 0.1);
			double ripple = Math.sin(u * 12.0 - time * 0.8) * 0.05 * Math.sin(u * Math.PI);
			pts.add(p.add(0, -sag + ripple, 0));
		}
		PyroDraw.ribbon(pose, add, pts, u -> 0.28, u -> PyroDraw.dim(0xFF702000, 0.8f));
		PyroDraw.ribbon(pose, add, pts, u -> 0.09, u -> 0xFFFFB050);
	}

	/** The Flamethrower: a roaring cone of fire from both palms out to 8 blocks (or the first block in the way). */
	private static void thrower(Player pl, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		flamethrower(pl, partial, cam, pose, buffers, PyrokinesisHandlers.THROWER_RANGE);
	}

	/**
	 * v0.17.2 (user: "change his ability vfx to be similar to the pyrokinesis power"): the Flamethrower's cone from
	 * {@code pl}'s palms out to {@code maxRange} blocks (or the first block in the way), in a world render pass ({@code pose}
	 * camera-relative at {@code cam}) -- shared with Heatblast's Fire Stream ({@code HeatblastClient}).
	 */
	public static void flamethrower(Player pl, float partial, Vec3 cam, PoseStack pose, MultiBufferSource buffers, double maxRange) {
		float time = pl.tickCount + partial;
		Vec3 look = pl.getViewVector(partial);
		Vec3 right = rightOf(look);
		Vec3 up = right.cross(look).normalize();
		Vec3 from = hand(pl, partial, true);
		double range = maxRange;
		HitResult hit = pl.pick(range, partial, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			range = Math.max(1.0, hit.getLocation().distanceTo(pl.getEyePosition(partial)));
		}
		Vec3 h = from.subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		// the white-hot core of the jet near the hands
		List<Vec3> core = new ArrayList<>();
		for (int i = 0; i <= 8; i++) {
			core.add(h.add(look.scale(Math.min(range, 3.2) * i / 8.0)));
		}
		PyroDraw.ribbon(pose, add, core, u -> 0.12 + 0.55 * u, u -> PyroDraw.dim(0xFFFFE0A0, (float) (1.0 - u * 0.85)));
		int n = 34;
		for (int i = 0; i < n; i++) {
			// each puff of fire scrolls out along the cone and grows as it goes (bunched toward the hands)
			double u0 = (i * 0.6180339 + time * 0.14) % 1.0;
			double u = u0 * u0 * 0.6 + u0 * 0.4;
			double d = 0.35 + u * range;
			double jx = Math.sin(i * 12.9898) * 0.5;
			double jy = Math.sin(i * 78.233) * 0.5;
			double spread = 0.04 + d * 0.08;
			Vec3 p = h.add(look.scale(d)).add(right.scale(jx * spread * 2)).add(up.scale(jy * spread * 2));
			float size = (float) (0.22 + u * 1.25);
			float k = (float) (u > 0.85 ? (1.0 - u) / 0.15 : 1.0);
			if (i % 2 == 0) {
				PyroDraw.glow(pose, glow, p, size * 1.05f, PyroDraw.dim(PyroDraw.lerpRgb(0xFFFFB050, 0xFFB02800, (float) u), 0.55f * k));
			}
			if (k > 0.3f) {
				PyroDraw.fireBillboard(pose, fire, p.subtract(0, size * 0.4, 0), Vec3.ZERO, size * 0.85f * k, size * k, (int) (time / 2) + i);
			}
		}
	}
}
