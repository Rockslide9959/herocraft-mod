package com.projecthero.mod.client.greenlantern;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.GreenLanternBattery;
import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15, user request: the Power Battery is held by the handle on top -- it hangs from the fist and swings like a
 * real lantern, in third and first person -- and it glows while the ring is being charged against it.
 * <ul>
 *   <li><b>Hanging</b>: the battery is drawn from its handle (model y = 17.5 px) at the bottom of the fist, with the
 *   arm's own rotation taken back off so it always hangs toward the ground, whatever the arm is doing (third person:
 *   {@code ItemInHandLayerPowerBatteryMixin}; first person: {@code ItemInHandRendererPowerBatteryMixin}).</li>
 *   <li><b>Sway</b>: a damped pendulum per holder, driven by the acceleration of the hand in the body's frame (so it
 *   swings back when you set off, forward when you stop, outward when you turn) plus a little air drag at speed and a
 *   walking bob -- integrated once a client tick, interpolated per frame.</li>
 *   <li><b>Charging</b> ({@link GreenLanternFx#CH_CHARGE}): full-bright, a white-hot core and additive halos pulsing
 *   brighter as the Oath goes on, flaring as it completes ({@code ANIM_CHARGED}).</li>
 * </ul>
 */
public final class PowerBatteryHeldRenderer {
	/** Model y (block units, after the item renderer's -0.5 centring) of the handle's grip bar. */
	private static final float GRIP_Y = 17.5f / 16f - 0.5f;
	/** Model y of the glowing core's centre. */
	private static final float CORE_Y = 6.5f / 16f - 0.5f;
	/** Lantern size in third person / first person. */
	public static final float SCALE_3P = 0.5f;
	public static final float SCALE_1P = 0.62f;
	/** Pendulum: gravity term (rad/tick^2 per rad), damping per tick, the lantern's swing length (blocks). */
	private static final float GRAVITY = 0.07f;
	private static final float DAMPING = 0.14f;
	private static final float LENGTH = 0.35f;
	private static final float DRAG = 0.09f;
	private static final float MAX_ANGLE = 1.1f;

	private static final class Sway {
		float pitch, pitchV, roll, rollV;
		float pitchPrev, rollPrev;
		Vec3 p1, p2;
		int seen;
	}

	private static final Map<Integer, Sway> SWAY = new HashMap<>();

	private PowerBatteryHeldRenderer() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(PowerBatteryHeldRenderer::tick);
	}

	public static boolean isBattery(ItemStack stack) {
		return stack.is(GreenLanternBlocks.POWER_BATTERY_ITEM);
	}

	/** Charging at the battery (synced) -- and how far into it, 0..1 (or -1 if not). */
	public static float charge(Player player, float partial) {
		GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
		if (!fx.has(GreenLanternFx.CH_CHARGE) || fx.chargeStart() == 0L) {
			return -1f;
		}
		return Mth.clamp((player.level().getGameTime() - fx.chargeStart() + partial) / GreenLanternBattery.CHARGE_TICKS, 0f, 1f);
	}

	/** 0..1 flash just after the charge completed. */
	public static float flash(Player player, float partial) {
		GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
		if (fx.anim() != GreenLanternFx.ANIM_CHARGED) {
			return 0f;
		}
		float t = player.level().getGameTime() - fx.animStart() + partial;
		return t < 0f || t > 14f ? 0f : 1f - t / 14f;
	}

	// ---------------------------------------------------------------- the pendulum

	private static boolean holds(LivingEntity e) {
		return isBattery(e.getMainHandItem()) || isBattery(e.getOffhandItem());
	}

	/** World position of the hand holding the battery (approximate: shoulder height minus the arm, out to the side). */
	private static Vec3 handPoint(LivingEntity e) {
		boolean right = isBattery(e.getMainHandItem()) == (e instanceof Player p && p.getMainArm() == HumanoidArm.RIGHT)
				|| !(e instanceof Player);
		float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 sideRight = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		return e.position().add(0, 0.75, 0).add(sideRight.scale(right ? 0.37 : -0.37));
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			SWAY.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		for (Player p : mc.level.players()) {
			if (!holds(p)) {
				continue;
			}
			Sway s = SWAY.computeIfAbsent(p.getId(), k -> new Sway());
			s.seen = 0;
			Vec3 now = handPoint(p);
			s.pitchPrev = s.pitch;
			s.rollPrev = s.roll;
			float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
			Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
			float accF = 0f, accR = 0f, velF = 0f, velR = 0f;
			if (s.p1 != null && s.p2 != null) {
				Vec3 v = now.subtract(s.p1);
				Vec3 a = v.subtract(s.p1.subtract(s.p2));
				accF = (float) a.dot(fwd);
				accR = (float) a.dot(right);
				velF = (float) v.dot(fwd);
				velR = (float) v.dot(right);
			}
			s.p2 = s.p1;
			s.p1 = now;
			// walking bob: the hand swings with the stride
			float stride = p.walkAnimation.position() * 0.6662f;
			float bob = Mth.sin(stride) * Math.min(1f, p.walkAnimation.speed()) * 0.03f;
			// pitch: + = the bottom swings forward. Accelerating forward / moving forward leaves it trailing back.
			float pa = -GRAVITY * Mth.sin(s.pitch) - DAMPING * s.pitchV - accF / LENGTH * 0.6f - DRAG * velF + bob;
			// roll: + = the bottom swings out to the right
			float ra = -GRAVITY * Mth.sin(s.roll) - DAMPING * s.rollV - accR / LENGTH * 0.6f - DRAG * velR;
			s.pitchV = Mth.clamp(s.pitchV + pa, -0.4f, 0.4f);
			s.rollV = Mth.clamp(s.rollV + ra, -0.4f, 0.4f);
			s.pitch = Mth.clamp(s.pitch + s.pitchV, -MAX_ANGLE, MAX_ANGLE);
			s.roll = Mth.clamp(s.roll + s.rollV, -MAX_ANGLE, MAX_ANGLE);
		}
		for (Iterator<Map.Entry<Integer, Sway>> it = SWAY.entrySet().iterator(); it.hasNext();) {
			Sway s = it.next().getValue();
			if (++s.seen > 40) {
				it.remove();
			}
		}
	}

	private static float pitch(LivingEntity e, float partial) {
		Sway s = SWAY.get(e.getId());
		return s == null ? 0f : Mth.lerp(partial, s.pitchPrev, s.pitch);
	}

	private static float roll(LivingEntity e, float partial) {
		Sway s = SWAY.get(e.getId());
		return s == null ? 0f : Mth.lerp(partial, s.rollPrev, s.roll);
	}

	// ---------------------------------------------------------------- third person

	/**
	 * Draws the held battery hanging from {@code arm}'s fist. {@code pose} is the layer's pose (body model space, before
	 * the arm). Returns normally; the caller cancels the vanilla held-item draw.
	 */
	public static void renderThirdPerson(LivingEntity entity, HumanoidModel<?> model, HumanoidArm side, ItemStack stack,
			PoseStack pose, MultiBufferSource buffers, int light) {
		Minecraft mc = Minecraft.getInstance();
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		float f = side == HumanoidArm.RIGHT ? -1f : 1f;
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the bottom of the fist, a touch inside it (the handle is gripped)
		pose.translate(f * 1f / 16f, 9.6f / 16f, 0f);
		// take the arm's own rotation back off: the lantern hangs toward the ground whatever the arm does
		pose.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).conjugate());
		// body model space is y-down / x-mirrored: turn it right way up (+y up, +x = the holder's right, -z = forward)
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		Player player = entity instanceof Player p ? p : null;
		float ch = player == null ? -1f : charge(player, partial);
		float fl = player == null ? 0f : flash(player, partial);
		float swayP = pitch(entity, partial);
		float swayR = roll(entity, partial);
		if (ch >= 0f) {
			swayP *= 0.25f; // held still against the ring
			swayR *= 0.25f;
		}
		drawHanging(pose, buffers, light, stack, entity, swayP, swayR, SCALE_3P, ch, fl, entity.tickCount + partial);
		pose.popPose();
	}

	/**
	 * At the grip point (+y up, -z forward): applies the swing and draws the battery hanging below it, plus the charge
	 * glow.
	 */
	public static void drawHanging(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack, LivingEntity holder,
			float pitch, float roll, float scale, float charge, float flash, float time) {
		Minecraft mc = Minecraft.getInstance();
		pose.pushPose();
		// pitch +: the bottom swings forward (-z); roll +: out to the right (+x)
		pose.mulPose(Axis.XP.rotation(pitch));
		pose.mulPose(Axis.ZP.rotation(roll));
		pose.scale(scale, scale, scale);
		pose.translate(0f, -GRIP_Y, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(90f)); // the handle bar runs front-to-back through the fist
		boolean lit = charge >= 0f || flash > 0f;
		mc.getItemRenderer().renderStatic(holder, stack, ItemDisplayContext.NONE, false, pose, buffers, holder.level(),
				lit ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, holder.getId());
		if (lit) {
			glow(pose, buffers, Math.max(0f, charge), flash, time);
		}
		pose.popPose();
	}

	/** The charge glow round the battery's core: white-hot core, additive halos, growing as the Oath goes on. */
	private static void glow(PoseStack pose, MultiBufferSource buffers, float k, float flash, float time) {
		float pulse = 0.5f + 0.5f * Mth.sin(time * (0.5f + 0.6f * k));
		float s = 0.35f + 0.65f * k;
		pose.pushPose();
		pose.translate(0f, CORE_Y, 0f);
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		TurboDraw.box(vc, pose.last(), 0.17f, 0.2f, 0.17f, 0xF4FFF6, Math.min(1f, 0.6f + 0.4f * s + flash));
		TurboDraw.box(vc, pose.last(), 0.27f, 0.3f, 0.27f, 0x5CFF8E, 0.35f * s + 0.3f * flash);
		VertexConsumer add = HardLightRibbon.additive(buffers);
		TurboDraw.sphere(add, pose, 0.45f + 0.08f * pulse + 0.6f * flash, 0xB8FFCC, (0.35f + 0.25f * pulse) * s + flash * 0.8f);
		TurboDraw.sphere(add, pose, 0.85f + 0.15f * pulse + 1.4f * flash, 0x35F075, (0.18f + 0.12f * pulse) * s + flash * 0.5f);
		vc = HardLightDraw.buffer(buffers);
		// a ring of light pulsing out of it, faster as the charge builds
		float wave = (time * (0.08f + 0.1f * k)) % 1f;
		TurboDraw.sphere(vc, pose, 0.5f + wave * 1.3f, 0x9CFFB8, 0.16f * (1f - wave) * s);
		pose.popPose();
	}

	// ---------------------------------------------------------------- first person

	/**
	 * The vanilla first-person arm chain ({@code ItemInHandRenderer#renderPlayerArm}) as a matrix, ending at the bottom of
	 * the fist -- where the battery's handle is held. Applied to {@code pose}'s current matrix; returns the point in the
	 * space {@code pose} was in.
	 */
	public static Vector3f firstPersonFist(PoseStack pose, Matrix4f start, float equip, float swing, HumanoidArm side) {
		boolean right = side != HumanoidArm.LEFT;
		float f = right ? 1f : -1f;
		float g = Mth.sqrt(swing);
		float h = -0.3f * Mth.sin(g * Mth.PI);
		float i = 0.4f * Mth.sin(g * Mth.TWO_PI);
		float j = -0.4f * Mth.sin(swing * Mth.PI);
		Matrix4f m = new Matrix4f(start);
		m.translate(f * (h + 0.64000005f), i - 0.6f + equip * -0.6f, j - 0.71999997f);
		m.rotate(Axis.YP.rotationDegrees(f * 45f));
		float k = Mth.sin(swing * swing * Mth.PI);
		float l = Mth.sin(g * Mth.PI);
		m.rotate(Axis.YP.rotationDegrees(f * l * 70f));
		m.rotate(Axis.ZP.rotationDegrees(f * k * -20f));
		m.translate(f * -1f, 3.6f, 3.5f);
		m.rotate(Axis.ZP.rotationDegrees(f * 120f));
		m.rotate(Axis.XP.rotationDegrees(200f));
		m.rotate(Axis.YP.rotationDegrees(f * -135f));
		m.translate(f * 5.6f, 0f, 0f);
		// the arm bone: pivot (-5 / 5, 2) px, fist bottom 10 px down it, its centre 1 px toward the outside
		m.translate((right ? -6f : 6f) / 16f, 11.6f / 16f, 0f);
		Vector3f out = new Vector3f();
		m.getTranslation(out);
		// back into the pose's own space
		Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
		inv.transformPosition(out);
		return out;
	}

	/**
	 * While charging, an extra offset on a first-person arm chain: the battery hand comes in toward the middle and up,
	 * holding the battery out in front; the ring hand comes across to press its fist to it. Eases in over 8 ticks.
	 */
	public static void chargeArmPose(PoseStack pose, HumanoidArm side, boolean batteryHand, Player player, float partial) {
		float in = Mth.clamp((charge(player, partial) * GreenLanternBattery.CHARGE_TICKS) / 8f, 0f, 1f);
		in = in * in * (3f - 2f * in);
		float f = side == HumanoidArm.RIGHT ? 1f : -1f;
		if (batteryHand) {
			pose.translate(-f * BAT_IN * in, BAT_UP * in, -BAT_FWD * in);
			pose.mulPose(Axis.ZP.rotationDegrees(-f * BAT_ROLL * in));
		} else {
			float push = 0.02f * Mth.sin((player.tickCount + partial) * 0.4f); // pressing in, a little tremble
			pose.translate(-f * (RING_IN + push) * in, RING_UP * in, -RING_FWD * in);
			pose.mulPose(Axis.YP.rotationDegrees(f * RING_YAW * in));
			pose.mulPose(Axis.ZP.rotationDegrees(-f * RING_ROLL * in));
		}
	}

	/** First-person charge pose offsets (blocks / degrees), tuned on screenshots. */
	public static float BAT_IN = 0.34f, BAT_UP = 0.16f, BAT_FWD = 0.1f, BAT_ROLL = 8f;
	public static float RING_IN = 0.36f, RING_UP = 0.02f, RING_FWD = 0.05f, RING_YAW = 15f, RING_ROLL = 10f;

	/** Draws the first-person battery hanging from {@code fist} (a point in {@code pose}'s space). */
	public static void drawFirstPerson(Player player, ItemStack stack, Vector3f fist, float camPitch, float partial, PoseStack pose,
			MultiBufferSource buffers, int light, boolean charging) {
		pose.pushPose();
		pose.translate(fist.x, fist.y, fist.z);
		// hang toward the real ground: world-down in camera space tilts with the view pitch
		pose.mulPose(Axis.XP.rotationDegrees(camPitch));
		float ch = charge(player, partial);
		float fl = flash(player, partial);
		float swayP = pitch(player, partial);
		float swayR = roll(player, partial);
		if (charging) {
			swayP *= 0.2f;
			swayR *= 0.2f;
		}
		drawHanging(pose, buffers, light, stack, player, swayP, swayR, SCALE_1P, ch, fl, player.tickCount + partial);
		pose.popPose();
	}
}
