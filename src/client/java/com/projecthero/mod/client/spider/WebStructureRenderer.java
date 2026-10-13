package com.projecthero.mod.client.spider;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spider.web.WebGeometry;
import com.projecthero.mod.spider.web.WebStructureEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: draws Spider-Man's web structures. Strands (tightropes, a woven web's radials and spiral) are camera-facing
 * silk ribbons with a sag toward the middle on long spans; a web wall is a double-sided orb-web panel. Everything fades
 * out over the last ten seconds of the web's life. Pure render -- the shape comes from the synced {@link WebGeometry}.
 */
public class WebStructureRenderer extends EntityRenderer<WebStructureEntity> {
	private static final ResourceLocation STRAND = ProjectHeroMod.id("textures/entity/spider_web_strand.png");
	private static final ResourceLocation WALL = ProjectHeroMod.id("textures/entity/spider_web_wall.png");

	public WebStructureRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(WebStructureEntity entity, Frustum frustum, double x, double y, double z) {
		WebGeometry g = entity.geometry();
		return g != null && frustum.isVisible(g.bounds);
	}

	@Override
	public void render(WebStructureEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers,
			int light) {
		WebGeometry g = entity.geometry();
		Minecraft mc = Minecraft.getInstance();
		if (g == null || mc.level == null) {
			return;
		}
		double now = mc.level.getGameTime() + partialTicks;
		float alpha = (float) Math.max(0.0, Math.min(1.0, (entity.expiresAt() - now) / 200.0));
		alpha *= (float) Math.min(1.0, (now - entity.bornAt()) / 4.0 + 0.3);
		if (alpha <= 0.01f) {
			return;
		}
		Vec3 origin = entity.getPosition(partialTicks);
		Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
		pose.pushPose();
		var last = pose.last();
		if (g.wallBox != null) {
			drawWall(last, buffers.getBuffer(RenderType.entityTranslucent(WALL)), g, origin, lightAt(entity, g.wallBox.getCenter()), alpha);
		}
		if (g.segmentCount() > 0) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(STRAND));
			int radials = g.kind == WebGeometry.WEAVE ? countRadials(g) : g.segmentCount();
			for (int i = 0; i < g.segmentCount(); i++) {
				Vec3 a = g.a(i);
				Vec3 b = g.b(i);
				boolean radial = i < radials;
				double width = g.kind == WebGeometry.TIGHTROPE ? 0.10 : radial ? 0.085 : 0.055;
				// a tightrope and a radial sag a little; the spiral threads are short and taut
				double sag = radial ? Math.min(0.35, a.distanceTo(b) * 0.012) : 0.0;
				int segs = radial ? Math.max(2, (int) Math.min(12, a.distanceTo(b) / 1.5)) : 1;
				int li = lightAt(entity, a.lerp(b, 0.5));
				Vec3 prev = a;
				for (int k = 1; k <= segs; k++) {
					double t = k / (double) segs;
					Vec3 p = a.lerp(b, t).subtract(0, sag * 4.0 * t * (1.0 - t), 0);
					ribbon(last, vc, prev.subtract(origin), p.subtract(origin), cam.subtract(origin), width, li, alpha,
							(float) (a.distanceTo(b) * (t - 1.0 / segs)), (float) (a.distanceTo(b) * t));
					prev = p;
				}
			}
		}
		pose.popPose();
	}

	/** Radial strands come first in a woven web's segment list; the spiral rings after them are shorter chords. */
	private static int countRadials(WebGeometry g) {
		if (g.segmentCount() == 0) {
			return 0;
		}
		Vec3 centre = g.a(0);
		int n = 0;
		while (n < g.segmentCount() && g.a(n).distanceToSqr(centre) < 1.0e-4) {
			n++;
		}
		return n;
	}

	private static int lightAt(WebStructureEntity e, Vec3 p) {
		BlockPos pos = BlockPos.containing(p);
		int block = e.level().getBrightness(LightLayer.BLOCK, pos);
		int sky = e.level().getBrightness(LightLayer.SKY, pos);
		return LightTexture.pack(Math.max(block, 4), sky);
	}

	/** One camera-facing quad from {@code a} to {@code b} (entity-local), {@code width} wide. */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, double width, int light,
			float alpha, float u0, float u1) {
		Vec3 dir = b.subtract(a);
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 toCam = cam.subtract(mid);
		Vec3 side = dir.cross(toCam);
		if (side.lengthSqr() < 1.0e-8) {
			side = dir.cross(new Vec3(0, 1, 0));
			if (side.lengthSqr() < 1.0e-8) {
				side = new Vec3(1, 0, 0);
			}
		}
		// keep the ribbon at least ~1.2 px wide however far away it is
		double dist = toCam.length();
		double w = Math.max(width, dist * 0.0022);
		side = side.normalize().scale(w * 0.5);
		float uu0 = u0 * 0.5f;
		float uu1 = u1 * 0.5f;
		vertex(pose, vc, a.subtract(side), uu0, 0f, light, alpha);
		vertex(pose, vc, b.subtract(side), uu1, 0f, light, alpha);
		vertex(pose, vc, b.add(side), uu1, 1f, light, alpha);
		vertex(pose, vc, a.add(side), uu0, 1f, light, alpha);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float u, float v, int light, float alpha) {
		vc.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
				.setColor(0.93f, 0.95f, 1.0f, 0.95f * alpha)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0f, 1f, 0f);
	}

	private static void drawWall(PoseStack.Pose pose, VertexConsumer vc, WebGeometry g, Vec3 origin, int light, float alpha) {
		AABB b = g.wallBox;
		Vec3 c = b.getCenter().subtract(origin);
		double hw = g.wallAxis == 0 ? b.getZsize() * 0.5 : b.getXsize() * 0.5;
		double hh = b.getYsize() * 0.5;
		Vec3 right = g.wallAxis == 0 ? new Vec3(0, 0, hw) : new Vec3(hw, 0, 0);
		Vec3 up = new Vec3(0, hh, 0);
		Vec3 n = g.wallAxis == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
		Vec3 p0 = c.subtract(right).subtract(up);
		Vec3 p1 = c.add(right).subtract(up);
		Vec3 p2 = c.add(right).add(up);
		Vec3 p3 = c.subtract(right).add(up);
		for (Vec3 off : new Vec3[] {n.scale(0.01), n.scale(-0.01)}) {
			wallVertex(pose, vc, p0.add(off), 0f, 1f, light, alpha, n);
			wallVertex(pose, vc, p1.add(off), 1f, 1f, light, alpha, n);
			wallVertex(pose, vc, p2.add(off), 1f, 0f, light, alpha, n);
			wallVertex(pose, vc, p3.add(off), 0f, 0f, light, alpha, n);
		}
	}

	private static void wallVertex(PoseStack.Pose pose, VertexConsumer vc, Vec3 p, float u, float v, int light, float alpha,
			Vec3 n) {
		vc.addVertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
				.setColor(1f, 1f, 1f, alpha)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}

	@Override
	public ResourceLocation getTextureLocation(WebStructureEntity entity) {
		return STRAND;
	}
}
