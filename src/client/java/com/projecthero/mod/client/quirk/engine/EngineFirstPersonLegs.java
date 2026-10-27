package com.projecthero.mod.client.quirk.engine;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.fpbody.FirstPersonBody;
import com.projecthero.mod.client.quirk.QuirkCostume;
import com.projecthero.mod.quirk.engine.Engine;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user: the calf engines show "in first person when looking down"): vanilla never draws your own body in first
 * person, so for an Engine holder this draws <b>the legs</b> (no torso, head or arms: right under the eyes they would
 * fill the view) -- skin, trousers layer, a worn Quirk suit's leggings / boots and the calf engines -- where the body
 * really stands, posed by the same model code everyone else sees (strides, the Engine run, kicks). Like the full-body
 * view ({@link FirstPersonBody}, which wins whenever it is on) it sits a little behind the eyes along the body's facing,
 * further the more you look down, so looking down the legs and feet come into view in their natural place and nothing
 * crosses the lens; looking ahead they are below the view. The hands are untouched. Other armour is not drawn here
 * (vanilla has no first-person armour either). It also keeps the nozzles' positions current for the exhaust in first
 * person ({@link EngineCalves#nozzles}).
 */
public final class EngineFirstPersonLegs {
	private static final double BACK_AHEAD = 0.20;
	private static final double BACK_LOOK_DOWN = 0.16;
	private static final double BACK_SNEAK = 0.40;

	private EngineFirstPersonLegs() {
	}

	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		if (mc.options.getCameraType() != CameraType.FIRST_PERSON || mc.getCameraEntity() != p || FirstPersonBody.active()) {
			return;
		}
		if (!Engine.hasEngines(p) || p.isInvisible() || p.isSpectator() || p.isSleeping() || p.isPassenger() || p.isVisuallySwimming()
				|| p.isFallFlying() || p.isAutoSpinAttack()) {
			return;
		}
		if (!(mc.getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer renderer)) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		PlayerModel<AbstractClientPlayer> model = renderer.getModel();

		// pose the shared player model for this player, legs only (every other part hidden; the hand / next player resets it)
		model.setAllVisible(false);
		model.rightLeg.visible = true;
		model.leftLeg.visible = true;
		model.rightPants.visible = p.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
		model.leftPants.visible = p.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
		model.crouching = p.isCrouching();
		model.riding = false;
		model.young = false;
		model.attackTime = p.getAttackAnim(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float headYaw = Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot) - bodyYaw;
		float pitch = p.getViewXRot(partial);
		float limbPos = p.walkAnimation.position(partial);
		float limbSpeed = Math.min(1f, p.walkAnimation.speed(partial));
		model.prepareMobModel(p, limbPos, limbSpeed, partial);
		model.setupAnim(p, limbPos, limbSpeed, p.tickCount + partial, headYaw, pitch);
		// v0.17.1: the kit's poses (the run, the kicks) on these legs too -- the shared pose hook skips the local player's
		// first-person model, so without this a kick never reached the legs drawn here
		EnginePose.apply(p, model);
		model.head.visible = false;
		model.hat.visible = false;
		// no torso: right under the eyes, its top face fills the view whenever you look down (and the run's lean tips it in)
		model.body.visible = false;
		model.jacket.visible = false;
		model.rightArm.visible = false;
		model.leftArm.visible = false;
		model.rightSleeve.visible = false;
		model.leftSleeve.visible = false;
		model.rightPants.copyFrom(model.rightLeg);
		model.leftPants.copyFrom(model.leftLeg);

		// where the body stands, set back along its facing (as the full-body view does)
		Vec3 cam = ctx.camera().getPosition();
		Vec3 feet = p.getPosition(partial);
		float yr = bodyYaw * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr));
		double back = p.isCrouching() ? BACK_SNEAK : BACK_AHEAD + BACK_LOOK_DOWN * Mth.clamp(pitch / 90f, 0f, 1f);
		Vec3 at = feet.subtract(fwd.scale(back)).add(0, p.isCrouching() ? -0.125 : 0.0, 0);
		// v0.17.1 first-person kicks: while a kick plays, the legs are carried up and forward so the kicking leg swings up into
		// the bottom of the view (it would stay under the camera otherwise) -- the higher the kick, the higher it comes
		float lift = kickLift(p, model);
		if (lift > 0f) {
			// a chest-high kick rises into the lower view; an overhead one (the axe kick) is carried further out so the leg
			// sweeps up across the view instead of filling it
			float up = -Math.min(model.rightLeg.xRot, model.leftLeg.xRot);
			double ahead = 0.6 + 0.8 * Math.max(0f, up - 1.7f);
			at = at.add(fwd.scale(ahead * lift)).add(0, (up > 2.2f ? 0.1 : 0.42) * lift, 0);
		}

		PoseStack pose = ctx.matrixStack();
		// the context's matrix -> world, for the exhaust's nozzle capture
		Matrix4f toWorld = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(new Matrix4f(pose.last().pose()).invert());
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
		pose.scale(-1f, -1f, 1f);
		float s = 0.9375f * p.getScale();
		pose.scale(s, s, s);
		pose.translate(0f, -1.501f, 0f);

		int light = mc.getEntityRenderDispatcher().getPackedLightCoords(p, partial);
		int overlay = LivingEntityRenderer.getOverlayCoords(p, 0f);
		VertexConsumer skin = ctx.consumers().getBuffer(model.renderType(p.getSkin().texture()));
		model.rightLeg.render(pose, skin, light, overlay);
		model.leftLeg.render(pose, skin, light, overlay);
		if (model.rightPants.visible) {
			model.rightPants.render(pose, skin, light, overlay);
		}
		if (model.leftPants.visible) {
			model.leftPants.render(pose, skin, light, overlay);
		}
		QuirkCostume.renderLegs(pose, ctx.consumers(), light, p, model);
		EngineCalves.render(pose, ctx.consumers(), light, p, model, toWorld, partial);
		pose.popPose();
	}

	/** The kick anims (whose raised leg is carried into the first-person view). */
	private static boolean kickAnim(int id) {
		return switch (id) {
			case Engine.ANIM_KICK, Engine.ANIM_BARRAGE, Engine.ANIM_EXTEND_KICK, Engine.ANIM_RISING, Engine.ANIM_AXE, Engine.ANIM_STRIKE,
					Engine.ANIM_FINISHER, Engine.ANIM_CHARGED, Engine.ANIM_CHAIN, Engine.ANIM_MACH, Engine.ANIM_SPIN, Engine.ANIM_SWEEP -> true;
			default -> false;
		};
	}

	/** 0..1: how far a playing kick raises the legs into view (from the posed legs: a chest-high kick is 1). */
	static float kickLift(AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model) {
		com.projecthero.mod.quirk.data.QuirkState s = com.projecthero.mod.quirk.Quirks.peek(p);
		boolean charging = Engine.val(p, Engine.V_CHARGE) != 0L;
		if (s == null || !charging && (!kickAnim(s.animId) || p.level().getGameTime() - s.animStart > 24)) {
			return 0f;
		}
		float up = -Math.min(model.rightLeg.xRot, model.leftLeg.xRot);
		float side = Math.max(Math.abs(model.rightLeg.zRot), Math.abs(model.leftLeg.zRot));
		return Mth.clamp((Math.max(up, side * 1.2f) - 0.7f) / 0.9f, 0f, 1f);
	}
}
