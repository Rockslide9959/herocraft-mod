package com.projecthero.mod.client.miraculous.ladybug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.LadybugConfig;
import com.projecthero.mod.miraculous.ladybug.LadybugKeys;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: the Miraculous Ladybug swarm (and the little burst of them with a Lucky Charm) -- a client-side flock of
 * tiny 3D ladybugs: a domed red body with a black head, two wings beating on each side. Every client starts the same
 * flock from the synced cast time ({@code lb_ml_at}) and point; the bugs spiral up and out across the area (about 40
 * blocks), dip toward the ground and fade. Nothing is synced per bug and nothing is kept once they are gone.
 */
public final class LadybugSwarm {
	private static final ResourceLocation BUG = ProjectHeroMod.id("textures/entity/miraculous/ladybug/swarm_bug.png");
	private static final int MAX = 1600;

	private static final class Bug {
		Vec3 pos, prev, vel;
		int age;
		final int life;
		final float phase, curl, size;

		Bug(Vec3 pos, Vec3 vel, int life, float phase, float curl, float size) {
			this.pos = pos;
			this.prev = pos;
			this.vel = vel;
			this.life = life;
			this.phase = phase;
			this.curl = curl;
			this.size = size;
		}
	}

	private static final List<Bug> BUGS = new ArrayList<>();
	/** player id -> the cast / charm start already flocked. */
	private static final Map<Integer, Long> SEEN_ML = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> SEEN_CHARM = new ConcurrentHashMap<>();

	private LadybugSwarm() {
	}

	static void reset() {
		BUGS.clear();
		SEEN_ML.clear();
		SEEN_CHARM.clear();
	}

	/** Bugs flying right now (the harness prints it). */
	public static int count() {
		return BUGS.size();
	}

	static void spawn(Vec3 at, int n, double speed, int life, long seed) {
		Random r = new Random(seed);
		for (int i = 0; i < n && BUGS.size() < MAX; i++) {
			double a = r.nextDouble() * Math.PI * 2, up = 0.15 + r.nextDouble() * 0.6;
			double sp = speed * (0.4 + r.nextDouble() * 0.8);
			Vec3 v = new Vec3(Math.cos(a) * sp, up * sp, Math.sin(a) * sp);
			Vec3 p = at.add((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6);
			BUGS.add(new Bug(p, v, life / 2 + r.nextInt(life / 2 + 1), r.nextFloat() * 6.28f, (r.nextFloat() - 0.5f) * 0.12f,
					0.7f + r.nextFloat() * 0.6f));
		}
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			reset();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			MiraculousState s = LadybugKeys.state(p);
			if (s == null) {
				continue;
			}
			long ml = s.val(LadybugKeys.V_ML_AT);
			Vec3 at = Miraculous.vec(s, LadybugKeys.P_ML);
			if (ml != 0L && at != null && !Long.valueOf(ml).equals(SEEN_ML.get(p.getId())) && now - ml < 40) {
				SEEN_ML.put(p.getId(), ml);
				spawn(at, 700, 0.55, LadybugConfig.SWARM_TICKS, ml);
			}
			if (s.animId == LadybugKeys.ANIM_CHARM && at != null && !Long.valueOf(s.animStart).equals(SEEN_CHARM.get(p.getId()))
					&& now - s.animStart >= 6 && now - s.animStart < 30) {
				SEEN_CHARM.put(p.getId(), s.animStart);
				spawn(at, 60, 0.22, 40, s.animStart);
			}
		}
		for (int i = BUGS.size() - 1; i >= 0; i--) {
			Bug b = BUGS.get(i);
			b.prev = b.pos;
			b.age++;
			if (b.age > b.life) {
				BUGS.remove(i);
				continue;
			}
			// curl round the vertical, a flutter, slowing; late in life they dip toward the ground
			double c = Math.cos(b.curl), sn = Math.sin(b.curl);
			Vec3 v = new Vec3(b.vel.x * c - b.vel.z * sn, b.vel.y, b.vel.x * sn + b.vel.z * c);
			float life = b.age / (float) b.life;
			v = v.scale(0.985).add(0.02 * Math.sin(b.age * 0.7 + b.phase), 0.015 * Math.cos(b.age * 0.9 + b.phase) - (life > 0.6 ? 0.012 : -0.002),
					0.02 * Math.cos(b.age * 0.6 + b.phase));
			b.vel = v;
			b.pos = b.pos.add(v);
		}
	}

	static void render(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Camera camera, float partial) {
		if (BUGS.isEmpty()) {
			return;
		}
		// two passes: a MultiBufferSource ends one buffer when another type is asked for, so never interleave them
		int light = LightTexture.FULL_BRIGHT;
		for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = buffers.getBuffer(pass == 0 ? RenderType.entityCutoutNoCull(BUG) : RenderType.entityTranslucent(BUG));
			for (Bug b : BUGS) {
				Vec3 at = b.prev.lerp(b.pos, partial);
				if (at.distanceToSqr(cam) > 96 * 96) {
					continue;
				}
				float life = (b.age + partial) / b.life;
				float s = 0.11f * b.size * Mth.clamp(life * 8f, 0f, 1f) * Mth.clamp((1f - life) * 6f, 0f, 1f);
				if (s <= 0.005f) {
					continue;
				}
				pose.pushPose();
				pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
				pose.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(b.vel.x, b.vel.z)));
				pose.mulPose(com.mojang.math.Axis.XP.rotation((float) -Math.atan2(b.vel.y, Math.sqrt(b.vel.x * b.vel.x + b.vel.z * b.vel.z)) * 0.6f));
				pose.scale(s, s, s);
				if (pass == 0) {
					// the body: a low box, the spotted back on top (u 0..0.5), dark sides; the head end at +z
					box(vc, pose.last(), light, -0.5f, 0f, -0.7f, 0.5f, 0.45f, 0.7f);
				} else {
					// the wings, beating
					float flap = 0.35f + 0.9f * (0.5f + 0.5f * Mth.sin((b.age + partial) * 2.6f + b.phase));
					for (int side = -1; side <= 1; side += 2) {
						pose.pushPose();
						pose.translate(side * 0.45f, 0.45f, 0f);
						pose.mulPose(com.mojang.math.Axis.ZP.rotation(side * flap));
						quad(vc, pose.last(), light, 0f, 0f, -0.55f, side * 1.3f, 0f, -0.55f, side * 1.3f, 0f, 0.45f, 0f, 0f, 0.45f, 0.5f, 1f, 0f, 1f);
						pose.popPose();
					}
				}
				pose.popPose();
			}
		}
	}

	private static void box(VertexConsumer vc, PoseStack.Pose p, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
		// top: the spotted back
		quad(vc, p, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 0.5f, 0f, 1f);
		// sides and ends: dark red / black from the head strip
		float hu0 = 0.05f, hu1 = 0.45f, hv0 = 0f, hv1 = 0.2f;
		quad(vc, p, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, hu0, hu1, hv0, hv1);
		quad(vc, p, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0.1f, 0.4f, 0.4f, 0.6f);
		quad(vc, p, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0.1f, 0.4f, 0.4f, 0.6f);
		quad(vc, p, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 0.1f, 0.4f, 0.4f, 0.6f);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, int light, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, float u0, float u1, float v0, float v1) {
		vc.addVertex(p.pose(), ax, ay, az).setColor(-1).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
		vc.addVertex(p.pose(), bx, by, bz).setColor(-1).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
		vc.addVertex(p.pose(), cx, cy, cz).setColor(-1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
		vc.addVertex(p.pose(), dx, dy, dz).setColor(-1).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
	}
}
