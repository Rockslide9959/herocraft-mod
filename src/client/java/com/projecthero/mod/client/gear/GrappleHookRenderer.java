package com.projecthero.mod.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.gear.GrappleHookEntity;
import com.projecthero.mod.gear.GrappleLineItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the Grapple Line's hook (a small camera-facing claw) and the taut cable from the owner's hand to it. The
 * first three ticks the hook flies out from the hand, so the shot reads even though the server hooks instantly.
 */
public class GrappleHookRenderer extends EntityRenderer<GrappleHookEntity> {
	private static final ResourceLocation HOOK = ProjectHeroMod.id("textures/entity/grapple_hook.png");
	private static final ResourceLocation CABLE = ProjectHeroMod.id("textures/entity/grapple_cable.png");
	private static final int FLY_TICKS = 3;

	public GrappleHookRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(GrappleHookEntity entity, Frustum frustum, double x, double y, double z) {
		return true; // the cable can be on screen while the hook itself is not
	}

	@Override
	public void render(GrappleHookEntity hook, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		if (hook.released()) {
			return; // the reel is over; the entity only steadies the climb onto the ledge
		}
		Player owner = hook.owner();
		Vec3 hookPos = hook.getPosition(partial);
		Vec3 hand = owner == null ? hookPos : handPos(owner, partial);
		float fly = Math.min(1f, (hook.tickCount + partial) / FLY_TICKS);
		Vec3 tip = hand.add(hookPos.subtract(hand).scale(fly));
		Vec3 rel = tip.subtract(hookPos); // where the hook is drawn, relative to the entity origin

		// the hook
		pose.pushPose();
		pose.translate(rel.x, rel.y, rel.z);
		pose.mulPose(entityRenderDispatcher.cameraOrientation());
		pose.mulPose(Axis.YP.rotationDegrees(180f));
		pose.scale(0.45f, 0.45f, 0.45f);
		PoseStack.Pose p = pose.last();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(HOOK));
		quadVertex(vc, p, light, -0.5f, -0.5f, 0, 1);
		quadVertex(vc, p, light, 0.5f, -0.5f, 1, 1);
		quadVertex(vc, p, light, 0.5f, 0.5f, 1, 0);
		quadVertex(vc, p, light, -0.5f, 0.5f, 0, 0);
		pose.popPose();

		if (owner == null) {
			return;
		}
		// the cable: a thin camera-facing ribbon from the hand to the hook
		Vec3 a = hand.subtract(hookPos);
		Vec3 b = rel;
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(hookPos);
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 side = dir.cross(cam.subtract(mid));
		if (side.lengthSqr() < 1.0e-8) {
			return;
		}
		side = side.normalize().scale(0.045);
		int color = hook.upgraded() ? 0xFFD9C6FF : 0xFFFFFFFF;
		VertexConsumer cv = buffers.getBuffer(RenderType.entityCutoutNoCull(CABLE));
		PoseStack.Pose cp = pose.last();
		float len = (float) dir.length();
		cableVertex(cv, cp, light, color, a.subtract(side), 0, 0);
		cableVertex(cv, cp, light, color, a.add(side), 1, 0);
		cableVertex(cv, cp, light, color, b.add(side), 1, len);
		cableVertex(cv, cp, light, color, b.subtract(side), 0, len);
		super.render(hook, yaw, partial, pose, buffers, light);
	}

	private static void quadVertex(VertexConsumer vc, PoseStack.Pose p, int light, float x, float y, float u, float v) {
		vc.addVertex(p, x, y, 0).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
	}

	private static void cableVertex(VertexConsumer vc, PoseStack.Pose p, int light, int color, Vec3 at, float u, float v) {
		vc.addVertex(p, (float) at.x, (float) at.y, (float) at.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, 0, 1, 0);
	}

	/** Where the hand holding the grapple is (the vanilla fishing-line maths, first and third person). */
	private Vec3 handPos(Player player, float partial) {
		int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		if (!(player.getMainHandItem().getItem() instanceof GrappleLineItem)) {
			side = -side;
		}
		Minecraft mc = Minecraft.getInstance();
		if (entityRenderDispatcher.options.getCameraType().isFirstPerson() && player == mc.player) {
			double fovScale = 960.0 / entityRenderDispatcher.options.fov().get();
			Vec3 v = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525f, -0.1f).scale(fovScale);
			return player.getEyePosition(partial).add(v);
		}
		float body = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(body), cos = Mth.cos(body);
		float scale = player.getScale();
		double sx = side * 0.35 * scale, fz = 0.8 * scale;
		double crouch = player.isCrouching() ? -0.1875 : 0.0;
		return player.getEyePosition(partial).add(-cos * sx - sin * fz, crouch - 0.45 * scale, -sin * sx + cos * fz);
	}

	@Override
	public ResourceLocation getTextureLocation(GrappleHookEntity entity) {
		return HOOK;
	}
}
