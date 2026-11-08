package com.projecthero.mod.client.andy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.AndyParts;
import com.projecthero.mod.andy.data.AndyState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: Andy's two beams, drawn for every viewer from the synced flags: Dead Line (C), a thin pressurised jet of blood from
 * the fingertip, and Victor's Dead Road, a thick spiral of blood. Hidden while a beam clash draws the struggle instead.
 */
public final class AndyEffectsRenderer {
	private AndyEffectsRenderer() {
	}

	static void render(WorldRenderContext context) {
		PixelCubes.render(context);
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		for (Player player : mc.level.players()) {
			AndyState s = Andy.peek(player);
			if (s == null || !s.hasPower || (!s.flag(AndyState.FLAG_DEAD_LINE) && !s.flag(AndyState.FLAG_DEAD_ROAD))) {
				continue;
			}
			if (com.projecthero.mod.client.beamclash.BeamClashClient.hides(player)) {
				continue;
			}
			boolean road = s.flag(AndyState.FLAG_DEAD_ROAD);
			Vec3[] seg = beam(player, partial, road ? AndyConfig.DEAD_ROAD_RANGE : AndyConfig.DEAD_LINE_RANGE);
			float age = mc.level.getGameTime() - s.holdStart + partial;
			pose.pushPose();
			pose.translate(seg[0].x - cam.x, seg[0].y - cam.y, seg[0].z - cam.z);
			Matrix4f m = pose.last().pose();
			Vec3 d = seg[1].subtract(seg[0]);
			float len = (float) d.length();
			if (len > 0.05f) {
				Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
				Vector3f a = new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
				Vector3f b = new Vector3f(dir).cross(a).normalize();
				float grow = Mth.clamp(age / 3f, 0f, 1f);
				float reach = len * grow;
				VertexConsumer core = context.consumers().getBuffer(RenderType.debugQuads());
				VertexConsumer glow = context.consumers().getBuffer(RenderType.lightning());
				if (!road) {
					tube(core, m, dir, a, b, reach, 0.035f, 6, 0.42f, 0.0f, 0.03f, 0.97f);
					tube(glow, m, dir, a, b, reach, 0.085f, 6, 0.9f, 0.08f, 0.08f, 0.45f);
				} else {
					tube(core, m, dir, a, b, reach, 0.22f, 10, 0.5f, 0.0f, 0.04f, 0.9f);
					tube(glow, m, dir, a, b, reach, 0.42f, 10, 0.95f, 0.1f, 0.1f, 0.4f);
					// the spiral: three strands winding round the core
					for (int k = 0; k < 3; k++) {
						helix(core, m, dir, a, b, reach, 0.5f, age * 0.6f + k * Mth.TWO_PI / 3f, 0.7f, 0.04f, 0.06f, 0.95f);
					}
				}
			}
			pose.popPose();
		}
	}

	/** Where the beam runs this frame: the fingertip to the first block or creature (as the server works it out). */
	static Vec3[] beam(Player p, float partial, double range) {
		Vec3 pos = p.getPosition(partial);
		Vec3 eye = pos.add(0, p.getEyeHeight(), 0);
		Vec3 look = p.getViewVector(partial);
		Vec3 r = AndyParts.right(p);
		double side = Andy.mainArm(p) == AndyState.RIGHT_ARM ? 1 : -1;
		Vec3 from = eye.add(r.scale(0.32 * side)).add(0, -0.32, 0).add(look.scale(0.55));
		Vec3 far = eye.add(look.scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1.0),
				e -> e instanceof LivingEntity && e.isPickable() && !(e instanceof com.projecthero.mod.andy.entity.AndyCloneEntity), from.distanceToSqr(end));
		if (ehr != null) {
			end = ehr.getLocation();
		}
		return new Vec3[] { from, end };
	}

	private static void tube(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, int sides, float cr, float cg,
			float cb, float ca) {
		for (int i = 0; i < sides; i++) {
			float t0 = i * Mth.TWO_PI / sides, t1 = (i + 1) * Mth.TWO_PI / sides;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r));
			Vector3f e = new Vector3f(dir).mul(len);
			quad(vc, m, p0, p1, new Vector3f(p1).add(e), new Vector3f(p0).add(e), cr, cg, cb, ca);
		}
	}

	private static void helix(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, float phase, float cr, float cg,
			float cb, float ca) {
		int steps = Math.max(4, (int) (len * 6));
		float w = 0.07f;
		for (int i = 0; i < steps; i++) {
			float s0 = i / (float) steps * len, s1 = (i + 1) / (float) steps * len;
			float t0 = phase + s0 * 1.6f, t1 = phase + s1 * 1.6f;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r)).add(new Vector3f(dir).mul(s0));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r)).add(new Vector3f(dir).mul(s1));
			Vector3f off0 = new Vector3f(a).mul(Mth.cos(t0)).add(new Vector3f(b).mul(Mth.sin(t0))).mul(w);
			Vector3f off1 = new Vector3f(a).mul(Mth.cos(t1)).add(new Vector3f(b).mul(Mth.sin(t1))).mul(w);
			quad(vc, m, new Vector3f(p0).sub(off0), new Vector3f(p1).sub(off1), new Vector3f(p1).add(off1), new Vector3f(p0).add(off0), cr, cg, cb, ca);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float r, float g, float b, float a) {
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
	}
}
