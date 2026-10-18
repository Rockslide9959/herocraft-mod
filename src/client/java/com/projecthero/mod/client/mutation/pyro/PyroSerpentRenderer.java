package com.projecthero.mod.client.mutation.pyro;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.hero.power.p08.PyroSerpentEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Pyrokinesis, hold G: the Flame Serpent's model -- a chain of molten segments laid along the path its head has
 * flown ({@link PyroSerpentEntity#trail()}), tapering to the tail, writhing side to side in a travelling wave, each with a
 * crest of real fire along its back; a glowing spine ribbon runs through them. The head is a stretched molten skull with
 * swept-back horns, a jaw that snaps open and shut and burning eyes.
 */
public class PyroSerpentRenderer extends EntityRenderer<PyroSerpentEntity> {
	private static final int SEGMENTS = 18;
	private static final double SPACING = 0.34;

	public PyroSerpentRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(PyroSerpentEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		return true; // the body trails far outside its box
	}

	/** Points {@code SPACING} apart along the trail, head first (world space). */
	private static List<Vec3> spine(PyroSerpentEntity e, Vec3 head) {
		List<Vec3> raw = new ArrayList<>();
		raw.add(head);
		for (Iterator<Vec3> it = e.trail().iterator(); it.hasNext();) {
			raw.add(it.next());
		}
		List<Vec3> out = new ArrayList<>();
		out.add(head);
		double need = SPACING;
		Vec3 prev = head;
		for (int i = 1; i < raw.size() && out.size() < SEGMENTS; i++) {
			Vec3 cur = raw.get(i);
			double seg = cur.distanceTo(prev);
			while (seg >= need && out.size() < SEGMENTS) {
				Vec3 p = prev.lerp(cur, need / seg);
				out.add(p);
				seg -= need;
				prev = p;
				need = SPACING;
			}
			need -= seg;
			prev = cur;
		}
		// a fresh serpent: the rest of the body is still pouring out of the hand
		while (out.size() < 4) {
			out.add(out.get(out.size() - 1).add(0, -0.01, 0));
		}
		return out;
	}

	@Override
	public void render(PyroSerpentEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		Vec3 head = e.getPosition(partial);
		Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
		List<Vec3> pts = spine(e, head);
		int n = pts.size();
		// the writhe: a wave running down the body, across and up-and-down
		List<Vec3> body = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			Vec3 a = pts.get(Math.max(0, i - 1));
			Vec3 b = pts.get(Math.min(n - 1, i + 1));
			Vec3 tan = a.subtract(b);
			tan = tan.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : tan.normalize();
			Vec3 side = tan.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 up = side.cross(tan).normalize();
			double amp = 0.28 * Math.min(1.0, i / 3.0);
			double w = i * 0.75 - t * 0.55;
			body.add(pts.get(i).add(side.scale(Math.sin(w) * amp)).add(up.scale(Math.cos(w * 0.8) * amp * 0.45)));
		}
		// draw in camera-relative space (the ribbon faces the camera from there)
		pose.pushPose();
		pose.translate(cam.x - head.x, cam.y - head.y, cam.z - head.z);
		List<Vec3> rel = new ArrayList<>(n);
		for (Vec3 p : body) {
			rel.add(p.subtract(cam));
		}
		float in = Math.min(1.0f, t / 4.0f);
		VertexConsumer add = PyroDraw.additive(buffers);
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		VertexConsumer glow = PyroDraw.glowBuffer(buffers);
		VertexConsumer fire = PyroDraw.fireBuffer(buffers);
		// the spine of light through it
		PyroDraw.ribbon(pose, add, rel, u -> 0.55 * (1.0 - 0.75 * u) * in, u -> PyroDraw.dim(0xFFFF7A20, (float) (0.9 - 0.5 * u)));
		PyroDraw.ribbon(pose, add, rel, u -> 0.18 * (1.0 - 0.7 * u) * in, u -> PyroDraw.dim(0xFFFFF0C0, (float) (1.0 - 0.6 * u)));
		for (int i = n - 1; i >= 1; i--) {
			float u = i / (float) (SEGMENTS - 1);
			float r = (0.3f * (1.0f - 0.78f * u) + 0.04f) * in;
			Vec3 p = rel.get(i);
			pose.pushPose();
			pose.translate(p.x, p.y, p.z);
			float pulse = 1.0f + 0.08f * Mth.sin(t * 0.9f - i * 0.6f);
			PyroDraw.sphere(pose, solid, r * 0.6f * pulse, 0xFFFFE6A0, 0xFFE05018, 0.12f, t * 1.4f + i, PyroDraw.FULL);
			PyroDraw.sphere(pose, add, r * pulse, 0xFFFF9A30, 0xFF5A1000, 0.26f, -t * 1.1f + i * 1.7f, PyroDraw.FULL);
			if (i % 2 == 0) {
				PyroDraw.glow(pose, glow, Vec3.ZERO, r * 2.6f, PyroDraw.dim(0xFFFF6A10, 0.55f * (1.0f - 0.5f * u)));
			}
			// the crest: a tongue of fire licking up off its back
			float fh = r * (2.0f + 0.6f * Mth.sin(t * 0.7f + i * 1.3f));
			PyroDraw.fireCross(pose, fire, r * 1.5f, fh, (int) (t / 2) + i, i * 0.7f);
			pose.popPose();
		}
		// the head
		Vec3 h = rel.get(0);
		Vec3 fwd = rel.get(0).subtract(rel.get(1));
		fwd = fwd.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 side = fwd.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(fwd).normalize();
		float hr = 0.34f * in;
		// the skull: three overlapping molten balls, longest along the way it flies
		for (int j = 0; j < 3; j++) {
			Vec3 at = h.add(fwd.scale(-0.12 + j * 0.2));
			float rr = hr * (1.0f - j * 0.2f);
			pose.pushPose();
			pose.translate(at.x, at.y, at.z);
			PyroDraw.sphere(pose, solid, rr * 0.72f, 0xFFFFF0B8, 0xFFE86A20, 0.1f, t * 1.6f + j, PyroDraw.FULL);
			PyroDraw.sphere(pose, add, rr, 0xFFFFA040, 0xFF6A1400, 0.22f, -t * 1.3f + j * 2.0f, PyroDraw.FULL);
			pose.popPose();
		}
		// the jaws: an upper and a lower fang-cone, snapping open and shut
		float open = 0.25f + 0.25f * Math.max(0.0f, Mth.sin(t * 0.55f));
		for (int j = -1; j <= 1; j += 2) {
			Vec3 dir = fwd.add(up.scale(j * open)).normalize();
			Vec3 at = h.add(fwd.scale(0.25)).add(up.scale(j * 0.06));
			pose.pushPose();
			pose.translate(at.x, at.y, at.z);
			PyroDraw.alignY(pose, dir);
			PyroDraw.cone(pose, add, hr * 0.55f, hr * 1.9f, 0xFFFFC060, 0xFF000000);
			pose.popPose();
		}
		// the horns, swept back and up
		for (int j = -1; j <= 1; j += 2) {
			Vec3 dir = fwd.scale(-1.0).add(up.scale(0.7)).add(side.scale(j * 0.45)).normalize();
			Vec3 at = h.add(up.scale(hr * 0.6)).add(side.scale(j * hr * 0.5));
			pose.pushPose();
			pose.translate(at.x, at.y, at.z);
			PyroDraw.alignY(pose, dir);
			PyroDraw.cone(pose, add, hr * 0.28f, hr * 2.4f, 0xFFFFE0A0, 0xFF200400);
			pose.popPose();
		}
		// burning eyes
		for (int j = -1; j <= 1; j += 2) {
			Vec3 at = h.add(fwd.scale(0.1)).add(up.scale(hr * 0.45)).add(side.scale(j * hr * 0.55));
			PyroDraw.glow(pose, glow, at, hr * 0.42f, 0xFFFFF6C8);
		}
		PyroDraw.glow(pose, glow, h, hr * 3.2f, PyroDraw.dim(0xFFFF7A20, 0.7f));
		pose.popPose();
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(PyroSerpentEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
