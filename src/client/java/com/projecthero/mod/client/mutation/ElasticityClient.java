package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p17.ElasticityHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Elasticity rework -- everything the rubber body looks like, on every client, from the synced {@code p17.*}
 * visual values ({@link ElasticityHandlers#registerVisuals}):
 * <ul>
 * <li>the arms and legs really stretch: the limb's own {@link ModelPart} is lengthened along itself (in the player's own
 * skin, sleeves and armour following), with the top kept at the shoulder and a wobble as it snaps back
 * ({@link #applyLimbs}, from {@code HumanoidModelMixin});</li>
 * <li>the coil: while R / Z is held the arm is drawn as a stack of slices of its own skin, twisting further the longer the
 * coil;</li>
 * <li>the balloon (Rubber Shield) and the canopy (Parachute) are the body itself, inflated / flattened;</li>
 * <li>the Rubber Ball and the Shape Shift block replace the player render outright ({@link #renderReplacement}, from
 * {@code ElasticityPlayerRendererMixin});</li>
 * <li>the Giant Hammer Fist's boulder fist, the Gatling's blurred arms and the Wrap &amp; Squeeze coils.</li>
 * </ul>
 */
public final class ElasticityClient {
	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	/** The coil slices of the right arm (+ sleeve): [wide][12], [slim][12]. */
	private static ModelPart[][] coilSlices;
	private static ModelPart[][] coilSleeves;
	/** A whole right arm (wide, slim) and left arm (wide, slim), for the Gatling's after-images. */
	private static ModelPart[] ghostArms;
	/** The boulder fist: [right wide, right slim, left wide, left slim]. */
	private static ModelPart[] fists;

	/** The skin's main colour, per skin texture (read back from the GPU once). */
	private static final Map<ResourceLocation, Integer> SKIN_COLOUR = new HashMap<>();
	/** Per-player rolling of the ball (entity id -> orientation) and the Shape Shift wobble. */
	private static final Map<Integer, Quaternionf> ROLL = new HashMap<>();
	private static final Map<Integer, float[]> WOBBLE = new HashMap<>();
	private static Vec3 ballPrevVel = Vec3.ZERO;

	private ElasticityClient() {
	}

	/** From {@code RevampClientE.init()}. */
	public static void init() {
		// black-and-gray keys / name; the orange Stretch Hairline is a registered meter
		com.projecthero.mod.client.gui.AbilityHudExtras.mono(ElasticityHandlers.KEY);
		registerPoses();
		// v0.16.5: the Slingshot's no-drag flight, on the local player
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null) {
				slingFlightTick(mc.player);
			}
		});
		MutationOverlays.register("p17.arm", ElasticityClient::armExtras);
		MutationOverlays.register("p17.coiling", ElasticityClient::renderCoil);
		MutationOverlays.register("p17.gatling", ElasticityClient::gatlingGhosts);
		// v0.16.5 (user): the canopy is the stretched torso itself now -- no grey membrane between the limbs
		WorldRenderEvents.AFTER_ENTITIES.register(ElasticityClient::renderWraps);
		ClientTickEvents.END_CLIENT_TICK.register(ElasticityClient::clientTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ROLL.clear();
			WOBBLE.clear();
		});
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
	private static float[] p(float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY, float rL, float lL,
			float h) {
		return new float[] { 0, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static void registerPoses() {
		// the coil: the arm drawn back and the body turned away, a little tremble
		float[] wind = p(0.95f, 0.3f, 0.2f, -1.2f, 0.2f, 0, 0.1f, 0.55f, 0.25f, -0.25f, 0);
		float[] wind2 = p(1.1f, 0.35f, 0.25f, -1.25f, 0.2f, 0, 0.12f, 0.6f, 0.25f, -0.25f, 0);
		MutationPose.registerLoop("p17.windup", 4, new float[][] { zero(0), at(4, wind), at(9, wind2), at(14, wind) });
		float[] stretchR = p(AIM, AIM, 0, 0.35f, 0.2f, -0.1f, 0.15f, -0.4f, -0.35f, 0.35f, 0);
		MutationPose.register("p17.stretch_right", new float[][] { zero(0), at(2, stretchR), at(13, stretchR), zero(17) });
		float[] stretchB = p(AIM, AIM, 0, AIM, AIM, 0, 0.2f, 0, -0.3f, 0.3f, 0);
		MutationPose.register("p17.stretch_both", new float[][] { zero(0), at(2, stretchB), at(13, stretchB), zero(17) });
		float[] hammerUp = p(-3.0f, 0, 0.2f, 0.4f, 0, -0.2f, -0.2f, 0, 0.15f, -0.15f, -0.3f);
		MutationPose.register("p17.hammer", new float[][] { zero(0), at(3, hammerUp), at(6, stretchR), at(18, stretchR), zero(23) });
		float[] reach = p(AIM, AIM, 0, 0.3f, 0, -0.25f, 0.1f, -0.25f, -0.2f, 0.2f, 0);
		float[] reach2 = p(AIM, AIM, 0, 0.35f, 0, -0.3f, 0.12f, -0.3f, -0.2f, 0.2f, 0);
		MutationPose.registerLoop("p17.grab_hold", 3, new float[][] { zero(0), at(3, reach), at(10, reach2), at(17, reach) });
		float[] sling = p(AIM, AIM, 0, AIM, AIM, 0, -0.35f, 0, 0.55f, -0.4f, 0);
		float[] sling2 = p(AIM, AIM, 0, AIM, AIM, 0, -0.4f, 0, 0.6f, -0.45f, 0);
		MutationPose.registerLoop("p17.sling", 2, new float[][] { zero(0), at(2, sling), at(8, sling2), at(14, sling) });
		float[] wrap = p(AIM, AIM, 0, AIM, AIM, 0, 0.25f, 0, -0.2f, 0.2f, 0.1f);
		float[] wrap2 = p(AIM, AIM, 0, AIM, AIM, 0, 0.3f, 0, -0.2f, 0.2f, 0.15f);
		MutationPose.registerLoop("p17.wrap", 3, new float[][] { zero(0), at(3, wrap), at(10, wrap2), at(17, wrap) });
		// the Gatling: both arms thrown forward, alternating
		float[] gatA = p(AIM, AIM, 0, -1.2f, 0.3f, 0, 0.2f, -0.35f, -0.3f, 0.3f, 0);
		float[] gatB = p(-1.2f, -0.3f, 0, AIM, AIM, 0, 0.2f, 0.35f, 0.3f, -0.3f, 0);
		MutationPose.registerLoop("p17.gatling", 1, new float[][] { zero(0), at(1, gatA), at(3, gatB), at(5, gatA) });
		// Spring Stomp: arms out for balance on the stilts
		float[] stilts = p(-0.2f, 0, 1.2f, -0.2f, 0, -1.2f, -0.05f, 0, 0, 0, 0.2f);
		MutationPose.registerLoop("p17.stilts", 3, new float[][] { zero(0), at(3, stilts), at(8, stilts), at(13, stilts) });
		float[] inflate = p(-0.3f, 0, 1.3f, -0.3f, 0, -1.3f, -0.12f, 0, -0.25f, 0.25f, -0.1f);
		float[] inflate2 = p(-0.35f, 0, 1.38f, -0.35f, 0, -1.38f, -0.15f, 0, -0.28f, 0.28f, -0.12f);
		MutationPose.registerLoop("p17.inflate", 4, new float[][] { zero(0), at(4, inflate), at(12, inflate2), at(20, inflate) });
		float[] glide = p(-0.1f, 0, 1.5f, -0.1f, 0, -1.5f, 0, 0, 0, 0, 0);
		float[] glide2 = p(-0.15f, 0, 1.45f, -0.15f, 0, -1.45f, 0, 0, 0, 0, 0);
		MutationPose.registerLoop("p17.glide", 4, new float[][] { zero(0), at(4, glide), at(14, glide2), at(24, glide) });
		// limp: arms dangling, slumped, head down
		float[] limp = p(-0.25f, 0.1f, 0.15f, -0.25f, -0.1f, -0.15f, 0.45f, 0, 0.3f, 0.2f, 0.45f);
		float[] limp2 = p(-0.3f, 0.12f, 0.12f, -0.2f, -0.12f, -0.18f, 0.5f, 0, 0.3f, 0.2f, 0.5f);
		MutationPose.registerLoop("p17.limp", 4, new float[][] { zero(0), at(4, limp), at(14, limp2), at(24, limp) });
	}

	// ================================================================== the limbs themselves

	private static boolean firstPersonHand() {
		return com.projecthero.mod.client.ironman.IronManFlightPose.firstPersonHand;
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** Age (ticks, fractional) of the stretch that started at synced tick {@code t0} (game time mod 8192). */
	private static float age(Player p, float t0) {
		long gt = p.level().getGameTime();
		return (float) Math.floorMod(gt % 8192L - (long) t0, 8192L) + partial();
	}

	/** How far out (0..1) a stretch of {@code mode} is at {@code age}: out in 3 ticks, held, back over 5. */
	static float extension(int mode, float age) {
		float ext;
		if (mode == ElasticityHandlers.ARM_HOLD || mode == ElasticityHandlers.ARM_HOLD_BOTH || mode == ElasticityHandlers.ARM_GATLING) {
			ext = Math.min(1.0f, age / 3.0f);
		} else if (age < 3.0f) {
			ext = age / 3.0f;
		} else if (age < 9.0f) {
			ext = 1.0f;
		} else {
			ext = Math.max(0.0f, 1.0f - (age - 9.0f) / 5.0f);
		}
		return ext * ext * (3.0f - 2.0f * ext);
	}

	/** The rubbery wobble as an arm snaps back (0 while out). */
	private static float wobble(int mode, float age) {
		if (mode == ElasticityHandlers.ARM_HOLD || mode == ElasticityHandlers.ARM_HOLD_BOTH || mode == ElasticityHandlers.ARM_GATLING
				|| age < 9.0f || age > 22.0f) {
			return 0.0f;
		}
		float t = age - 9.0f;
		return Mth.sin(t * 1.9f) * 0.2f * (1.0f - t / 13.0f);
	}

	/**
	 * Lengthens {@code arm} along itself so its hand reaches {@code lengthPx} pixels from the shoulder pivot (normally 10),
	 * keeping the top of the arm at the shoulder.
	 */
	static void stretchArm(ModelPart arm, float lengthPx, float girth) {
		float s = Math.max(0.2f, lengthPx / 10.0f);
		arm.yScale *= s;
		arm.xScale *= girth;
		arm.zScale *= girth;
		// the cube runs y -2..10 from the pivot: scaling about the pivot would push its top 2*s up past the shoulder
		Vector3f along = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(new Vector3f(0, 1, 0));
		float shift = 2.0f * s - 2.0f;
		arm.x += along.x * shift;
		arm.y += along.y * shift;
		arm.z += along.z * shift;
	}

	/** From {@code HumanoidModelMixin} after the mutation poses: stretches / reshapes the limbs from the synced state. */
	public static void applyLimbs(Player p, HumanoidModel<?> m) {
		MutationVisualState s = MutationVisuals.state(p);
		if (s.flags().isEmpty() && s.values().isEmpty()) {
			return;
		}
		boolean fp = firstPersonHand();
		float t = p.tickCount + partial();

		if (s.has("p17.balloon")) {
			float breathe = 1.0f + 0.05f * Mth.sin(t * 0.45f);
			m.body.xScale *= 2.6f * breathe;
			m.body.zScale *= 3.4f * breathe;
			m.body.yScale *= 1.05f;
			m.head.xScale *= 1.1f;
			m.head.yScale *= 1.1f;
			m.head.zScale *= 1.1f;
			m.hat.copyFrom(m.head);
			if (!fp) {
				m.rightArm.x -= 6.5f;
				m.leftArm.x += 6.5f;
			}
			for (ModelPart limb : new ModelPart[] { m.rightArm, m.leftArm, m.rightLeg, m.leftLeg }) {
				limb.xScale *= 1.45f * breathe;
				limb.zScale *= 1.45f * breathe;
			}
		}

		if (s.has("p17.canopy") && !fp) {
			// v0.16.5 (user: "stretch the player torso to take up the space between the arms and legs"): the torso itself
			// is the canopy -- pressed thin and stretched wide and long into a sheet of rubber in the shirt's own colours,
			// the arms reaching out from its top corners and the legs from its bottom corners
			float flutter = Mth.sin(t * 0.9f) * 0.04f;
			float wide = 4.4f + Mth.sin(t * 0.9f) * 0.08f; // 8 px -> ~35 px across
			float tall = 2.2f; // 12 px -> ~26 px from the neck to the hem
			m.body.xScale *= wide;
			m.body.yScale *= tall;
			m.body.zScale *= 0.3f;
			float edge = 4.0f * wide; // half the sheet's width, in model pixels
			m.rightArm.xRot = -0.1f;
			m.rightArm.yRot = 0.0f;
			m.rightArm.zRot = 1.45f + flutter;
			m.leftArm.xRot = -0.1f;
			m.leftArm.yRot = 0.0f;
			m.leftArm.zRot = -1.45f - flutter;
			m.rightArm.x = -edge;
			m.leftArm.x = edge;
			m.rightArm.y = 1.0f;
			m.leftArm.y = 1.0f;
			stretchArm(m.rightArm, 12.0f, 0.9f);
			stretchArm(m.leftArm, 12.0f, 0.9f);
			m.rightLeg.xRot = 0.0f;
			m.leftLeg.xRot = 0.0f;
			m.rightLeg.zRot = 0.75f;
			m.leftLeg.zRot = -0.75f;
			m.rightLeg.x = -edge * 0.7f;
			m.leftLeg.x = edge * 0.7f;
			m.rightLeg.y = 12.0f * tall - 1.0f;
			m.leftLeg.y = 12.0f * tall - 1.0f;
			// look ahead, not at the ground
			m.head.xRot = Math.max(-1.45f, m.head.xRot - 1.3f);
			m.hat.copyFrom(m.head);
		}

		if (s.has("p17.limp")) {
			// the arms go slack and droop longer
			stretchArm(m.rightArm, 12.5f + Mth.sin(t * 0.3f) * 0.5f, 0.92f);
			stretchArm(m.leftArm, 12.5f + Mth.cos(t * 0.3f) * 0.5f, 0.92f);
		}

		if (s.has("p17.stilts") && !fp) {
			float ground = groundBelow(p);
			float legPx = 12.0f + ground * 16.0f;
			m.rightLeg.xRot = 0.0f;
			m.leftLeg.xRot = 0.0f;
			m.rightLeg.zRot = 0.04f;
			m.leftLeg.zRot = -0.04f;
			m.rightLeg.yScale *= legPx / 12.0f;
			m.leftLeg.yScale *= legPx / 12.0f;
		}

		// v0.16.5 Slingshot: both arms grip the locked anchor, wherever the head turns (before the stretch, which lengthens
		// them along their rotation)
		if (s.has("p17.sling_anchor")) {
			Vec3 anchor = new Vec3(s.value("p17.sling_ax", 0f), s.value("p17.sling_ay", 0f), s.value("p17.sling_az", 0f));
			Vec3 shoulder = p.getPosition(partial()).add(0, 1.4, 0);
			Vec3 d = anchor.subtract(shoulder);
			double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
			float yawTo = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
			float pitchTo = (float) -(Mth.atan2(d.y, horiz) * Mth.RAD_TO_DEG);
			float bodyYaw = Mth.rotLerp(partial(), p.yBodyRotO, p.yBodyRot);
			float rel = Mth.wrapDegrees(yawTo - bodyYaw) * Mth.DEG_TO_RAD;
			for (ModelPart arm : new ModelPart[] { m.rightArm, m.leftArm }) {
				arm.xRot = pitchTo * Mth.DEG_TO_RAD - Mth.HALF_PI;
				arm.yRot = rel;
				arm.zRot = 0.0f;
			}
		}

		// v0.16.5 Long Reach (user: "make their arm actually reach out and grab it"): the right arm turns to the item / block
		// it reaches for instead of stretching straight along the look
		if (s.has("p17.arm_aim") && !s.has("p17.sling_anchor")) {
			Vec3 d = new Vec3(s.value("p17.aim_dx", 0f), s.value("p17.aim_dy", 0f), s.value("p17.aim_dz", 0f));
			double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
			float yawTo = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
			float pitchTo = (float) -(Mth.atan2(d.y, horiz) * Mth.RAD_TO_DEG);
			float bodyYaw = Mth.rotLerp(partial(), p.yBodyRotO, p.yBodyRot);
			m.rightArm.xRot = pitchTo * Mth.DEG_TO_RAD - Mth.HALF_PI;
			m.rightArm.yRot = Mth.wrapDegrees(yawTo - bodyYaw) * Mth.DEG_TO_RAD;
			m.rightArm.zRot = 0.0f;
		}

		// the stretched arms
		float len = s.value("p17.arm_len", 0.0f);
		float t0 = s.value("p17.arm_t", 0.0f) - 1.0f;
		int mode = Math.round(s.value("p17.arm_mode", 0.0f)) - 1;
		if (len > 0.05f && t0 >= 0.0f && mode >= 0 && !fp) {
			float age = age(p, t0);
			if (age < 400.0f) {
				float ext = extension(mode, age);
				float wob = wobble(mode, age);
				float px = 10.0f + ext * Math.max(0.0f, len * 16.0f - 10.0f);
				boolean both = mode == ElasticityHandlers.ARM_BOTH || mode == ElasticityHandlers.ARM_HOLD_BOTH;
				if (mode == ElasticityHandlers.ARM_GATLING) {
					// the flurry: each arm darts in and out out of step with the other
					float a = 0.5f + 0.5f * Mth.sin(t * 2.6f);
					float b = 0.5f + 0.5f * Mth.sin(t * 2.6f + Mth.PI);
					stretchArm(m.rightArm, 10.0f + a * (len * 16.0f - 10.0f), 1.0f);
					stretchArm(m.leftArm, 10.0f + b * (len * 16.0f - 10.0f), 1.0f);
				} else if (ext > 0.01f || wob != 0.0f) {
					stretchArm(m.rightArm, px * (1.0f + wob * 0.6f), 1.0f - wob);
					if (both) {
						stretchArm(m.leftArm, px * (1.0f - wob * 0.6f), 1.0f + wob);
					}
				}
			}
		}

		// the coil: the real arm is hidden and drawn as twisting slices (armExtras)
		if (s.value("p17.coil_kind", 0.0f) > 0.5f && !fp) {
			m.rightArm.visible = false;
			if (m instanceof PlayerModel<?> pm) {
				pm.rightSleeve.visible = false;
			}
		}
	}

	/**
	 * Whether {@code p}'s right (or left) arm is stretched out right now -- first person then draws that real arm
	 * ({@code ElasticityFirstPersonArmMixin}). Single-arm moves stretch the right arm; Double-Fist / held two-arm moves and
	 * the Gatling Barrage stretch both.
	 */
	public static boolean stretching(Player p, boolean right) {
		return firstPersonReach(p, right) > 0f;
	}

	/**
	 * v0.16.5 (user): the full third-person body is drawn in first person while an Elasticity move reshapes it -- a
	 * stretched or coiled arm, the balloon, the canopy, the stilts. Not the Rubber Ball or Shape Shift (the camera would be
	 * inside them): those keep the normal first-person view.
	 */
	public static boolean showsBodyInFirstPerson(Player p) {
		MutationVisualState s = MutationVisuals.state(p);
		if (s.flags().isEmpty() && s.values().isEmpty()) {
			return false;
		}
		if (s.has("p17.ball") || s.value("p17.shape", 0.0f) > 0.5f) {
			return false;
		}
		return stretching(p, true) || stretching(p, false) || s.value("p17.coil_kind", 0.0f) > 0.5f || s.has("p17.balloon")
				|| s.has("p17.canopy") || s.has("p17.stilts");
	}

	/**
	 * First person: how far (blocks) {@code p}'s right (or left) arm reaches out right now, 0 if it isn't stretched --
	 * the same length the third-person model gets, with the Gatling's out-of-step jabs and the snap-back wobble.
	 */
	public static float firstPersonReach(Player p, boolean right) {
		MutationVisualState s = MutationVisuals.state(p);
		float len = s.value("p17.arm_len", 0.0f);
		float t0 = s.value("p17.arm_t", 0.0f) - 1.0f;
		int mode = Math.round(s.value("p17.arm_mode", 0.0f)) - 1;
		if (len <= 0.05f || t0 < 0.0f || mode < 0) {
			return 0f;
		}
		float age = age(p, t0);
		float ext = extension(mode, age);
		float wob = wobble(mode, age);
		if (age >= 400.0f || (ext <= 0.01f && wob == 0.0f)) {
			return 0f;
		}
		boolean both = mode == ElasticityHandlers.ARM_BOTH || mode == ElasticityHandlers.ARM_HOLD_BOTH || mode == ElasticityHandlers.ARM_GATLING;
		if (!right && !both) {
			return 0f;
		}
		float px;
		if (mode == ElasticityHandlers.ARM_GATLING) {
			float t = p.tickCount + partial();
			float a = 0.5f + 0.5f * Mth.sin(t * 2.6f + (right ? 0f : Mth.PI));
			px = 10.0f + a * (len * 16.0f - 10.0f);
		} else {
			px = (10.0f + ext * Math.max(0.0f, len * 16.0f - 10.0f)) * (1.0f + wob * 0.6f * (right ? 1f : -1f));
		}
		float blocks = px / 16.0f;
		return blocks > 0.9f ? blocks : 0f; // a hair past arm length: leave the normal hand
	}

	/** Blocks of air under the player's feet (for the stilts), at most 12. */
	private static float groundBelow(Player p) {
		Vec3 pos = p.getPosition(partial());
		BlockPos.MutableBlockPos c = BlockPos.containing(pos.x, pos.y - 0.01, pos.z).mutable();
		for (int i = 0; i < 13; i++) {
			var state = p.level().getBlockState(c);
			var shape = state.getCollisionShape(p.level(), c);
			if (!shape.isEmpty()) {
				return (float) Math.max(0.0, pos.y - (c.getY() + shape.max(net.minecraft.core.Direction.Axis.Y)));
			}
			c.move(0, -1, 0);
		}
		return 12.0f;
	}

	/**
	 * From the {@code PlayerModel.translateToHand} mixin: the held item rides at the end of a stretched arm at its normal
	 * size, instead of being stretched with it.
	 */
	public static void unstretchHand(ModelPart arm, PoseStack pose) {
		if (Math.abs(arm.yScale - 1.0f) < 1.0e-3 && Math.abs(arm.xScale - 1.0f) < 1.0e-3) {
			return;
		}
		pose.scale(1.0f / arm.xScale, 1.0f / arm.yScale, 1.0f / arm.zScale);
		pose.translate(0.0f, 10.0f * (arm.yScale - 1.0f) / 16.0f, 0.0f);
	}

	// ================================================================== overlays

	private static boolean slim(MutationOverlays.Context ctx) {
		return "slim".equals(ctx.player().getSkin().model().id());
	}

	private static void bakeParts() {
		if (coilSlices != null) {
			return;
		}
		coilSlices = new ModelPart[2][12];
		coilSleeves = new ModelPart[2][12];
		for (int i = 0; i < 12; i++) {
			coilSlices[0][i] = MutationRender.bake(CubeListBuilder.create().texOffs(40, 16 + i).addBox(-3.0f, 0.0f, -2.0f, 4.0f, 1.0f, 4.0f), 64, 64);
			coilSlices[1][i] = MutationRender.bake(CubeListBuilder.create().texOffs(40, 16 + i).addBox(-2.0f, 0.0f, -2.0f, 3.0f, 1.0f, 4.0f), 64, 64);
			coilSleeves[0][i] = MutationRender.bake(CubeListBuilder.create().texOffs(40, 32 + i)
					.addBox(-3.0f, 0.0f, -2.0f, 4.0f, 1.0f, 4.0f, new CubeDeformation(0.25f)), 64, 64);
			coilSleeves[1][i] = MutationRender.bake(CubeListBuilder.create().texOffs(40, 32 + i)
					.addBox(-2.0f, 0.0f, -2.0f, 3.0f, 1.0f, 4.0f, new CubeDeformation(0.25f)), 64, 64);
		}
		ghostArms = new ModelPart[] {
				MutationRender.bake(CubeListBuilder.create().texOffs(40, 16).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f), 64, 64),
				MutationRender.bake(CubeListBuilder.create().texOffs(40, 16).addBox(-2.0f, -2.0f, -2.0f, 3.0f, 12.0f, 4.0f), 64, 64),
				MutationRender.bake(CubeListBuilder.create().texOffs(32, 48).addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f), 64, 64),
				MutationRender.bake(CubeListBuilder.create().texOffs(32, 48).addBox(-1.0f, -2.0f, -2.0f, 3.0f, 12.0f, 4.0f), 64, 64) };
		fists = new ModelPart[] {
				MutationRender.bake(CubeListBuilder.create().texOffs(40, 24).addBox(-2.2f, -2.2f, -2.2f, 4.4f, 4.4f, 4.4f), 64, 64),
				MutationRender.bake(CubeListBuilder.create().texOffs(40, 24).addBox(-1.7f, -2.2f, -2.2f, 3.4f, 4.4f, 4.4f), 64, 64),
				MutationRender.bake(CubeListBuilder.create().texOffs(32, 56).addBox(-2.2f, -2.2f, -2.2f, 4.4f, 4.4f, 4.4f), 64, 64),
				MutationRender.bake(CubeListBuilder.create().texOffs(32, 56).addBox(-1.7f, -2.2f, -2.2f, 3.4f, 4.4f, 4.4f), 64, 64) };
	}

	/** The part's pivot + rotation, without its scale. */
	private static void pivot(PoseStack pose, ModelPart part) {
		pose.translate(part.x / 16.0f, part.y / 16.0f, part.z / 16.0f);
		pose.mulPose(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
	}

	/** On the stretched-arm flag: the boulder fist of the Hammer Fist, and the coil twist while R / Z is held. */
	private static void armExtras(MutationOverlays.Context ctx) {
		bakeParts();
		MutationVisualState s = ctx.state();
		int mode = Math.round(s.value("p17.arm_mode", 0.0f)) - 1;
		if (mode == ElasticityHandlers.ARM_HAMMER) {
			float t0 = s.value("p17.arm_t", 0.0f) - 1.0f;
			float ext = t0 < 0 ? 0.0f : extension(mode, age(ctx.player(), t0));
			if (ext > 0.02f) {
				float size = s.value("p17.hammer_size", 1.0f);
				float k = 1.0f + (1.6f + 1.4f * (size - 1.0f)) * ext;
				ModelPart arm = ctx.model().rightArm;
				PoseStack pose = ctx.pose();
				pose.pushPose();
				pivot(pose, arm);
				pose.translate(-1.0f / 16.0f, 10.0f * arm.yScale / 16.0f, 0.0f);
				pose.scale(k, k, k);
				fists[slim(ctx) ? 1 : 0].render(pose, ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(ctx.player().getSkin().texture())),
						ctx.light(), OverlayTexture.NO_OVERLAY, -1);
				pose.popPose();
			}
		}
	}

	/** On the coiling flag: the twisting slices of the coiled arm (the real one is hidden by {@link #applyLimbs}). */
	private static void renderCoil(MutationOverlays.Context ctx) {
		MutationVisualState s = ctx.state();
		if (s.value("p17.coil_kind", 0.0f) < 0.5f) {
			return;
		}
		bakeParts();
		float c = Mth.clamp(s.value("p17.coil", 0.0f), 0.0f, 1.0f);
		float t = ctx.ageInTicks() + ctx.partialTick();
		ModelPart arm = ctx.model().rightArm;
		int idx = slim(ctx) ? 1 : 0;
		float cx = idx == 0 ? -1.0f / 16.0f : -0.5f / 16.0f;
		float h = 1.0f - 0.3f * c; // the coiled arm bunches up shorter
		VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(ctx.player().getSkin().texture()));
		PoseStack pose = ctx.pose();
		pose.pushPose();
		pivot(pose, arm);
		for (int i = 0; i < 12; i++) {
			float twist = c * (0.15f + c * 0.38f) * i * (1.0f + 0.12f * Mth.sin(t * 1.3f)) + c * 0.15f * Mth.sin(t * 2.1f + i);
			float bulge = 1.0f + 0.22f * c * Mth.sin(i * 0.9f + t * 0.6f);
			pose.pushPose();
			pose.translate(cx, (-2.0f + i * h) / 16.0f, 0.0f);
			pose.mulPose(Axis.YP.rotation(twist));
			pose.scale(bulge, h, bulge);
			pose.translate(-cx, 0.0f, 0.0f);
			coilSlices[idx][i].render(pose, vc, ctx.light(), OverlayTexture.NO_OVERLAY, -1);
			if (ctx.player().isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE)) {
				coilSleeves[idx][i].render(pose, vc, ctx.light(), OverlayTexture.NO_OVERLAY, -1);
			}
			pose.popPose();
		}
		pose.popPose();
	}

	/** The Gatling Barrage: half a dozen blurred after-image arms flickering out into the cone in front. */
	private static void gatlingGhosts(MutationOverlays.Context ctx) {
		bakeParts();
		float t = ctx.ageInTicks() + ctx.partialTick();
		int tick = (int) t;
		boolean slim = slim(ctx);
		VertexConsumer vc = ctx.buffers().getBuffer(RenderType.entityTranslucent(ctx.player().getSkin().texture()));
		PoseStack pose = ctx.pose();
		for (int i = 0; i < 7; i++) {
			long seed = (tick * 31L + i * 977L) * 0x9E3779B97F4A7C15L;
			float r1 = ((seed >>> 11) & 1023) / 1023.0f;
			float r2 = ((seed >>> 23) & 1023) / 1023.0f;
			float r3 = ((seed >>> 37) & 1023) / 1023.0f;
			boolean right = i % 2 == 0;
			ModelPart arm = right ? ctx.model().rightArm : ctx.model().leftArm;
			float sub = (t - tick + i * 0.37f) % 1.0f;
			int alpha = (int) (150 * (1.0f - sub));
			if (alpha < 12) {
				continue;
			}
			pose.pushPose();
			pose.translate(arm.x / 16.0f, arm.y / 16.0f, arm.z / 16.0f);
			float xr = ctx.model().head.xRot - Mth.HALF_PI + (r1 - 0.5f) * 0.7f;
			float yr = ctx.model().head.yRot + (r2 - 0.5f) * 0.9f;
			pose.mulPose(new Quaternionf().rotationZYX(0.0f, yr, xr));
			float lengthPx = 30.0f + r3 * 80.0f * (0.6f + 0.4f * sub);
			float sc = lengthPx / 10.0f;
			pose.translate(0.0f, (2.0f * sc - 2.0f) / 16.0f, 0.0f);
			pose.scale(1.0f, sc, 1.0f);
			ghostArms[(right ? 0 : 2) + (slim ? 1 : 0)].render(pose, vc, ctx.light(), OverlayTexture.NO_OVERLAY,
					MutationRender.argb(alpha, 255, 255, 255));
			pose.popPose();
		}
	}

	// ================================================================== whole-body replacements

	/**
	 * From {@code ElasticityPlayerRendererMixin} at the top of {@code PlayerRenderer.render}: the Rubber Ball or the Shape
	 * Shift block is drawn instead of the player. Returns true when it drew one (the player render is cancelled).
	 */
	public static boolean renderReplacement(AbstractClientPlayer p, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		MutationVisualState s = MutationVisuals.state(p);
		float shape = s.value("p17.shape", 0.0f);
		if (shape > 0.5f) {
			BlockState state = Block.stateById(Math.round(shape) - 1);
			float[] w = WOBBLE.getOrDefault(p.getId(), new float[2]);
			float t = p.tickCount + partial;
			float wob = Math.min(1.0f, w[0]);
			pose.pushPose();
			float sq = 0.07f * wob * Mth.sin(t * 1.6f);
			pose.translate(0.0f, 0.0f, 0.0f);
			pose.scale(1.0f + sq, 1.0f - sq, 1.0f + sq);
			pose.mulPose(Axis.ZP.rotation(0.05f * wob * Mth.sin(t * 1.1f)));
			pose.translate(-0.5f, 0.0f, -0.5f);
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			return true;
		}
		if (s.has("p17.ball")) {
			renderBall(p, partial, pose, buffers, light);
			return true;
		}
		return false;
	}

	/** Ball radius in blocks. */
	private static final float BALL_R = 0.55f;

	private static void renderBall(AbstractClientPlayer p, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		int rgb = skinColour(p.getSkin().texture());
		Quaternionf q = ROLL.computeIfAbsent(p.getId(), k -> new Quaternionf());
		pose.pushPose();
		pose.translate(0.0f, BALL_R, 0.0f);
		pose.mulPose(q);
		VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(MutationRender.WHITE));
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		int rings = 12;
		int segs = 20;
		for (int i = 0; i < rings; i++) {
			float a0 = Mth.PI * i / rings - Mth.HALF_PI;
			float a1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
			// a darker band round the middle and a lighter cap, so the roll shows
			float shade = (i == rings / 2 - 1 || i == rings / 2) ? 0.72f : (i == rings - 1 ? 1.15f : 1.0f);
			for (int j = 0; j < segs; j++) {
				float b0 = Mth.TWO_PI * j / segs;
				float b1 = Mth.TWO_PI * (j + 1) / segs;
				float stripe = (j == 0 || j == segs / 2) ? 0.85f : 1.0f;
				int r = Math.min(255, (int) (((rgb >> 16) & 255) * shade * stripe));
				int g = Math.min(255, (int) (((rgb >> 8) & 255) * shade * stripe));
				int b = Math.min(255, (int) ((rgb & 255) * shade * stripe));
				ballVertex(vc, m, last, a0, b0, r, g, b, light);
				ballVertex(vc, m, last, a1, b0, r, g, b, light);
				ballVertex(vc, m, last, a1, b1, r, g, b, light);
				ballVertex(vc, m, last, a0, b1, r, g, b, light);
			}
		}
		pose.popPose();
	}

	private static void ballVertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose last, float lat, float lon, int r, int g, int b, int light) {
		float cl = Mth.cos(lat);
		float nx = cl * Mth.cos(lon);
		float ny = Mth.sin(lat);
		float nz = cl * Mth.sin(lon);
		vc.addVertex(m, nx * BALL_R, ny * BALL_R, nz * BALL_R).setColor(r, g, b, 255).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
	}

	/** From {@code PlayerRenderer.setupRotations}: the canopy lies flat, face down, into the glide. */
	public static void applyBodyRotation(Player p, PoseStack pose) {
		if (!MutationVisuals.hasFlag(p, "p17.canopy") || p.isFallFlying()) {
			return; // in elytra flight vanilla already lays the body along the flight path
		}
		pose.translate(0.0f, 0.9f, 0.0f);
		pose.mulPose(Axis.XP.rotationDegrees(-80.0f));
		pose.translate(0.0f, -0.9f, 0.0f);
	}

	// ================================================================== the skin's main colour

	/** The average colour of the torso + arms of {@code skin}, read back from the GPU once and cached. */
	public static int skinColour(ResourceLocation skin) {
		Integer c = SKIN_COLOUR.get(skin);
		if (c != null) {
			return c;
		}
		int out = 0xE07A3A; // fallback: rubber orange
		try {
			AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(skin);
			RenderSystem.bindTexture(tex.getId());
			int w = GlStateManager._getTexLevelParameter(0x0DE1, 0, 0x1000); // GL_TEXTURE_2D, GL_TEXTURE_WIDTH
			int h = GlStateManager._getTexLevelParameter(0x0DE1, 0, 0x1001); // GL_TEXTURE_HEIGHT
			if (w >= 64 && h >= 32) {
				try (NativeImage img = new NativeImage(w, h, false)) {
					img.downloadTexture(0, false);
					float sx = w / 64.0f;
					float sy = h / 64.0f;
					if (h < w) {
						sy = h / 32.0f;
					}
					long rs = 0, gs = 0, bs = 0, n = 0;
					// the torso front (20..28 x 20..32) and the right arm front (44..48 x 20..32)
					int[][] boxes = { { 20, 20, 28, 32 }, { 44, 20, 48, 32 } };
					for (int[] bx : boxes) {
						for (int y = (int) (bx[1] * sy); y < (int) (bx[3] * sy); y++) {
							for (int x = (int) (bx[0] * sx); x < (int) (bx[2] * sx); x++) {
								int abgr = img.getPixelRGBA(x, y);
								if (((abgr >>> 24) & 255) < 128) {
									continue;
								}
								rs += abgr & 255;
								gs += (abgr >> 8) & 255;
								bs += (abgr >> 16) & 255;
								n++;
							}
						}
					}
					if (n > 0) {
						out = (int) (rs / n) << 16 | (int) (gs / n) << 8 | (int) (bs / n);
					}
				}
			}
		} catch (RuntimeException ex) {
			// keep the fallback
		}
		SKIN_COLOUR.put(skin, out);
		return out;
	}

	// ================================================================== Wrap & Squeeze coils (world render)

	private static void renderWraps(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		for (Player pl : mc.level.players()) {
			float id = MutationVisuals.state(pl).value("p17.wrap", 0.0f);
			if (id < 0.5f) {
				continue;
			}
			Entity t = mc.level.getEntity(Math.round(id));
			if (t == null) {
				continue;
			}
			int rgb = skinColour(((AbstractClientPlayer) pl).getSkin().texture());
			Vec3 at = t.getPosition(partial);
			float age = pl.tickCount + partial;
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			VertexConsumer vc = context.consumers().getBuffer(RenderType.entitySolid(MutationRender.WHITE));
			int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, t.blockPosition());
			int bands = Math.max(3, (int) (t.getBbHeight() / 0.45f));
			float squeeze = 0.9f + 0.1f * Mth.sin(age * 0.6f);
			for (int i = 0; i < bands; i++) {
				float y = (i + 0.5f) * t.getBbHeight() / bands;
				float rad = (t.getBbWidth() * 0.62f + 0.06f) * squeeze;
				float tilt = 0.25f * Mth.sin(i * 1.7f);
				ring(vc, pose, y, rad, 0.13f, tilt, age * 0.05f * (i % 2 == 0 ? 1 : -1), rgb, light);
			}
			pose.popPose();
		}
	}

	/** One rubber band (a short tube of quads) round the y axis at height {@code y}. */
	private static void ring(VertexConsumer vc, PoseStack pose, float y, float r, float half, float tilt, float spin, int rgb, int light) {
		pose.pushPose();
		pose.translate(0.0f, y, 0.0f);
		pose.mulPose(Axis.YP.rotation(spin));
		pose.mulPose(Axis.XP.rotation(tilt));
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
		int segs = 18;
		for (int j = 0; j < segs; j++) {
			float a0 = Mth.TWO_PI * j / segs;
			float a1 = Mth.TWO_PI * (j + 1) / segs;
			float x0 = Mth.cos(a0), z0 = Mth.sin(a0), x1 = Mth.cos(a1), z1 = Mth.sin(a1);
			int sh = j % 2 == 0 ? 255 : 220;
			int rr = cr * sh / 255, gg = cg * sh / 255, bb = cb * sh / 255;
			// outside face
			vc.addVertex(m, x0 * r, -half, z0 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, x0, 0, z0);
			vc.addVertex(m, x0 * r, half, z0 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, x0, 0, z0);
			vc.addVertex(m, x1 * r, half, z1 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, x1, 0, z1);
			vc.addVertex(m, x1 * r, -half, z1 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, x1, 0, z1);
			// inside face (the other winding)
			vc.addVertex(m, x1 * r, -half, z1 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, -x1, 0, -z1);
			vc.addVertex(m, x1 * r, half, z1 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, -x1, 0, -z1);
			vc.addVertex(m, x0 * r, half, z0 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, -x0, 0, -z0);
			vc.addVertex(m, x0 * r, -half, z0 * r).setColor(rr, gg, bb, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, -x0, 0, -z0);
		}
		pose.popPose();
	}

	// ================================================================== client tick

	private static void clientTick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		// the ball's roll and the Shape Shift wobble, for every player in view
		for (Player pl : mc.level.players()) {
			MutationVisualState s = MutationVisuals.state(pl);
			int id = pl.getId();
			if (s.has("p17.ball")) {
				double dx = pl.getX() - pl.xo;
				double dz = pl.getZ() - pl.zo;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 1.0e-4) {
					// roll about the horizontal axis at right angles to the motion
					Vector3f axis = new Vector3f((float) (dz / d), 0.0f, (float) (-dx / d));
					ROLL.computeIfAbsent(id, k -> new Quaternionf()).premul(new Quaternionf().rotationAxis((float) (d / BALL_R), axis));
				}
			} else {
				ROLL.remove(id);
			}
			if (s.value("p17.shape", 0.0f) > 0.5f) {
				float[] w = WOBBLE.computeIfAbsent(id, k -> new float[2]);
				float turn = Math.abs(Mth.wrapDegrees(pl.getYRot() - w[1])) + Math.abs(pl.getXRot() - pl.xRotO);
				w[1] = pl.getYRot();
				w[0] = w[0] * 0.82f + Math.min(1.5f, turn * 0.03f);
			} else {
				WOBBLE.remove(id);
			}
		}

		LocalPlayer p = mc.player;
		if (p == null || !ElasticityHandlers.owns(p)) {
			return;
		}
		// v0.16.5: the Parachute is a custom elytra now (EntityElytraEvents.CUSTOM in ElasticityHandlers) -- vanilla elytra
		// physics steer it, so there is no hand-made glide here any more
		// the Rubber Ball bounces off walls
		if (ElasticityHandlers.balled(p)) {
			Vec3 v = p.getDeltaMovement();
			if (p.horizontalCollision) {
				double nx = Math.abs(v.x) < Math.abs(ballPrevVel.x) * 0.5 ? -ballPrevVel.x * 0.95 : v.x;
				double nz = Math.abs(v.z) < Math.abs(ballPrevVel.z) * 0.5 ? -ballPrevVel.z * 0.95 : v.z;
				p.setDeltaMovement(nx, Math.max(v.y, 0.25), nz);
				v = p.getDeltaMovement();
			}
			ballPrevVel = v;
		}
	}

	/**
	 * From {@code LocalPlayerMixin} on a landing after a real fall (peak fall speed {@code fall}): how hard the rubber body
	 * springs back up (0 = no bounce). Holding Shift lands dead; the ball bounces higher.
	 */
	/**
	 * v0.16.5 Slingshot flight (user: "dont make them slow down in the air or lose momentum ... till they land"): each
	 * client tick the local player's horizontal speed is put back to the launch speed, so air drag never bleeds it off;
	 * gravity still brings him down. It lets go on landing, on hitting a wall or in water (the server ends it too).
	 */
	public static void slingFlightTick(LocalPlayer self) {
		MutationVisualState s = MutationVisuals.state(self);
		if (!s.has("p17.sling_fly")) {
			slingBroken = false;
			return;
		}
		if (slingBroken || self.isInWater() || self.horizontalCollision || (self.onGround() && self.getDeltaMovement().y <= 0.0)) {
			slingBroken = true; // a wall or the ground: let go until the server ends this flight
			return;
		}
		float vx = s.value("p17.sling_vx", 0f);
		float vz = s.value("p17.sling_vz", 0f);
		if (vx * vx + vz * vz < 1.0e-4f) {
			return;
		}
		self.setDeltaMovement(vx, self.getDeltaMovement().y, vz);
		self.resetFallDistance();
	}

	private static boolean slingBroken;

	public static double landingBounce(LocalPlayer self, double fall) {
		if (self.isShiftKeyDown() || fall <= 0.55) {
			return 0.0;
		}
		if (ElasticityHandlers.balled(self)) {
			return Math.min(1.3, fall * 0.75);
		}
		return Math.min(1.1, fall * 0.55);
	}
}
