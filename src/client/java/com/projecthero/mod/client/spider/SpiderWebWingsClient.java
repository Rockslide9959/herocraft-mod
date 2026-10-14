package com.projecthero.mod.client.spider;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.network.SpiderGlidePayload;
import com.projecthero.mod.spider.SpiderWebWings;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the client half of the Spider-Man Suit v2's Web Wings ({@link SpiderWebWings}).
 *
 * <ul>
 *   <li><b>Movement</b> ({@link #tick}, from {@code SpiderWebWingsMixin} at the top of the local player's
 *       {@code aiStep}, the same place the swing physics run): hold Jump while falling and, after
 *       {@link SpiderWebWings#HOLD_TICKS} ticks, the glide takes over his velocity every tick
 *       ({@link SpiderWebWings#step}); releasing Jump, landing, grabbing a wall or firing a line ends it with the
 *       momentum kept. The server is told on each edge and owns the synced flag; if it refuses, the glide stops.</li>
 *   <li><b>Looks</b> (every viewer, off the synced flag): the glide pose -- body pitched forward, arms spread, legs
 *       together, rolled into turns ({@link #applyBodyRotation}, {@link #applyPose}) -- and the webbed membranes between
 *       each arm and the torso ({@link #renderMembranes}, from {@code SpiderSuitLayer}).</li>
 * </ul>
 */
public final class SpiderWebWingsClient {
	public static final ResourceLocation WING_TEXTURE = ProjectHeroMod.id("textures/entity/spider_web_wing.png");

	// ---------------- local movement ----------------
	private static int held;
	private static boolean gliding;
	private static int glideTicks;
	private static Vec3 vel = Vec3.ZERO;
	/** After the server refused a glide, no retry before this player tick. */
	private static int retryAt;

	private SpiderWebWingsClient() {
	}

	/** This client is gliding (local simulation). */
	public static boolean localGliding() {
		return gliding;
	}

	public static void reset() {
		held = 0;
		gliding = false;
		glideTicks = 0;
		ANIM.clear();
	}

	/** Top of the local player's {@code aiStep}, before vanilla moves him. */
	public static void tick(LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		boolean jump = mc.options.keyJump.isDown() && mc.screen == null;
		held = jump ? Math.min(held + 1, 1000) : 0;
		if (!gliding) {
			if (player.tickCount >= retryAt && SpiderWebWings.canStart(player, player.getDeltaMovement().y, held)) {
				gliding = true;
				glideTicks = 0;
				vel = SpiderWebWings.entryVelocity(player.getDeltaMovement(), player.getYRot());
				ClientPlayNetworking.send(new SpiderGlidePayload(true));
			} else {
				return;
			}
		}
		if (!jump || !SpiderWebWings.canGlide(player) || (player.horizontalCollision && glideTicks > 2)) {
			end(player);
			return;
		}
		if (glideTicks > 10 && !SpiderWebWings.isGliding(player)) {
			end(player); // the server said no
			retryAt = player.tickCount + 40;
			return;
		}
		glideTicks++;
		vel = SpiderWebWings.step(vel, player.getYRot(), player.getXRot());
		player.setDeltaMovement(vel);
		player.resetFallDistance();
	}

	private static void end(LocalPlayer player) {
		gliding = false;
		// letting go (or firing a swing) keeps every bit of the glide's speed
		if (!player.onGround()) {
			player.setDeltaMovement(vel);
		}
		ClientPlayNetworking.send(new SpiderGlidePayload(false));
	}

	// ---------------- per-player look ----------------

	private static final class Anim {
		float amt;
		float prevAmt;
		float bank;
		float prevBank;
		float lastYaw;
	}

	private static final Map<Integer, Anim> ANIM = new HashMap<>();

	private static boolean glidingLook(Player p) {
		return (p == Minecraft.getInstance().player && gliding) || SpiderWebWings.isGliding(p);
	}

	/** End of every client tick: ease each player's wings in / out and follow his turns. */
	public static void clientTick(Minecraft mc) {
		if (mc.level == null) {
			ANIM.clear();
			if (mc.player == null) {
				held = 0;
				gliding = false;
			}
			return;
		}
		if (mc.level.getGameTime() % 100 == 0) {
			ANIM.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player));
		}
		for (Player p : mc.level.players()) {
			boolean on = glidingLook(p);
			Anim a = ANIM.get(p.getId());
			if (a == null) {
				if (!on) {
					continue;
				}
				a = new Anim();
				a.lastYaw = p.getYRot();
				ANIM.put(p.getId(), a);
			}
			a.prevAmt = a.amt;
			a.amt = on ? Math.min(1f, a.amt + 0.2f) : Math.max(0f, a.amt - 0.15f);
			float turn = Mth.wrapDegrees(p.getYRot() - a.lastYaw);
			a.lastYaw = p.getYRot();
			a.prevBank = a.bank;
			a.bank += (SpiderWebWings.bank(turn) - a.bank) * 0.25f;
			if (!on && a.amt <= 0f && a.prevAmt <= 0f) {
				ANIM.remove(p.getId());
			}
		}
	}

	/** How far into the glide look this player is (0..1). */
	public static float amount(Player p, float partial) {
		Anim a = ANIM.get(p.getId());
		return a == null ? 0f : Mth.lerp(partial, a.prevAmt, a.amt);
	}

	private static float bankOf(Player p, float partial) {
		Anim a = ANIM.get(p.getId());
		return a == null ? 0f : Mth.lerp(partial, a.prevBank, a.bank);
	}

	/** Degrees the body is pitched forward at full glide, for a look pitch. */
	private static float lean(float lookPitch) {
		return 72f + Mth.clamp(lookPitch, -30f, 60f) * 0.3f;
	}

	/** From {@code PlayerRenderer.setupRotations}: pitch the body forward into the glide and roll it into turns. */
	public static void applyBodyRotation(Player p, PoseStack pose, float partial) {
		float a = amount(p, partial);
		if (a <= 0f) {
			return;
		}
		float lean = lean(p.getViewXRot(partial)) * a;
		float roll = bankOf(p, partial) * 35f * a;
		pose.translate(0.0f, 0.9f, 0.0f);
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		pose.mulPose(Axis.XP.rotationDegrees(-lean));
		pose.translate(0.0f, -0.9f, 0.0f);
	}

	/** From {@code HumanoidModelMixin}: arms spread for the wings, legs together, head up to see where he goes. */
	public static void applyPose(Player p, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float a = amount(p, partial);
		if (a <= 0f) {
			return;
		}
		float leanRad = lean(p.getViewXRot(partial)) * Mth.DEG_TO_RAD;
		m.head.xRot = Mth.lerp(a, m.head.xRot, Math.max(-1.45f, m.head.xRot - leanRad * 0.9f));
		float flutter = Mth.sin((p.tickCount + partial) * 0.9f) * 0.03f;
		m.rightArm.xRot = Mth.lerp(a, m.rightArm.xRot, -0.15f);
		m.rightArm.yRot = Mth.lerp(a, m.rightArm.yRot, 0f);
		m.rightArm.zRot = Mth.lerp(a, m.rightArm.zRot, 1.38f + flutter);
		m.leftArm.xRot = Mth.lerp(a, m.leftArm.xRot, -0.15f);
		m.leftArm.yRot = Mth.lerp(a, m.leftArm.yRot, 0f);
		m.leftArm.zRot = Mth.lerp(a, m.leftArm.zRot, -1.38f - flutter);
		m.rightLeg.xRot = Mth.lerp(a, m.rightLeg.xRot, 0.08f);
		m.rightLeg.yRot = Mth.lerp(a, m.rightLeg.yRot, 0f);
		m.rightLeg.zRot = Mth.lerp(a, m.rightLeg.zRot, 0.1f);
		m.leftLeg.xRot = Mth.lerp(a, m.leftLeg.xRot, 0.08f);
		m.leftLeg.yRot = Mth.lerp(a, m.leftLeg.yRot, 0f);
		m.leftLeg.zRot = Mth.lerp(a, m.leftLeg.zRot, -0.1f);
	}

	// ---------------- the membranes ----------------

	private static Vector3f at(PoseStack pose, ModelPart part, float x, float y, float z) {
		pose.pushPose();
		part.translateAndRotate(pose);
		Matrix4f mat = pose.last().pose();
		Vector4f v = new Vector4f(x / 16f, y / 16f, z / 16f, 1f).mul(mat);
		pose.popPose();
		return new Vector3f(v.x, v.y, v.z);
	}

	/**
	 * The web membranes between each arm and his side (from {@code SpiderSuitLayer}, the parent model already posed): one
	 * translucent webbed quad per side -- armpit, wrist, a sagging point between the wrist and the hip, the hip --
	 * opening out as the glide eases in.
	 */
	public static void renderMembranes(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> m, Player p,
			float partial) {
		float a = amount(p, partial);
		if (a <= 0.01f || p.isInvisible() || !SpiderWebWings.wearingV2(p)) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(WING_TEXTURE));
		for (int side = 0; side < 2; side++) {
			boolean right = side == 0;
			float sx = right ? -1f : 1f;
			ModelPart arm = right ? m.rightArm : m.leftArm;
			Vector3f armpit = at(pose, m.body, sx * 3.9f, 1.2f, 0f);
			Vector3f hip = at(pose, m.body, sx * 3.9f, 11.0f, 0f);
			Vector3f wrist = at(pose, arm, sx * 1.0f, 9.5f, 0f); // the middle of the arm's end (a 4-wide arm, x -3..1 / -1..3)
			wrist = new Vector3f(armpit).lerp(wrist, a);
			Vector3f sag = new Vector3f(wrist).lerp(hip, 0.5f).lerp(new Vector3f(armpit).lerp(hip, 0.5f), 0.22f);
			sag = new Vector3f(armpit).lerp(sag, a);
			quad(vc, light, armpit, wrist, sag, hip);
		}
	}

	private static void quad(VertexConsumer vc, int light, Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
		Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a));
		if (n.lengthSquared() < 1.0e-8f) {
			return;
		}
		n.normalize();
		int col = 0xFFFFFFFF;
		vc.addVertex(a.x, a.y, a.z).setColor(col).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
		vc.addVertex(b.x, b.y, b.z).setColor(col).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
		vc.addVertex(c.x, c.y, c.z).setColor(col).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
		vc.addVertex(d.x, d.y, d.z).setColor(col).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
	}
}
