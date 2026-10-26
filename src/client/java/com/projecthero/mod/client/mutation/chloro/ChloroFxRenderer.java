package com.projecthero.mod.client.mutation.chloro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.hero.power.p22.ChloroFxEntity;
import com.projecthero.mod.hero.power.p22.PlantManipulationHandlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Chlorokinesis effect models ({@link ChloroFxEntity}), all procedural ({@link ChloroMesh}):
 * <ul>
 * <li>Vine -- a tapering, leafy vine from the caster's hand that shoots out, sags or whips (the lash grows thorns), then
 * snaps back;</li>
 * <li>Roots -- six roots tear up out of the ground and coil round the victim, sinking back as it is let go;</li>
 * <li>Spores -- soft yellow-green puffs drifting and turning in the cloud;</li>
 * <li>Bloom -- a ring of flowers on stems that grow, open and glow, then droop and fold;</li>
 * <li>Overgrowth -- roots spiralling up the caster during the gather, then a wave of root spikes racing out across the
 * radius;</li>
 * <li>Root Sense -- a pale green ring running out along the ground, roots chasing it.</li>
 * </ul>
 */
public class ChloroFxRenderer extends EntityRenderer<ChloroFxEntity> {
	/** Per-effect cached ground heights (the Overgrowth spikes / bloom flowers sit on the real ground). */
	private static final Map<ChloroFxEntity, float[]> GROUND = new WeakHashMap<>();

	private static final int[][] FLOWER_COLOURS = { { 255, 150, 200 }, { 255, 250, 245 }, { 255, 214, 70 }, { 190, 130, 255 },
			{ 255, 96, 96 }, { 130, 190, 255 } };

	public ChloroFxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public ResourceLocation getTextureLocation(ChloroFxEntity e) {
		return ChloroMesh.VINE;
	}

	@Override
	public void render(ChloroFxEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partial;
		Vec3 origin = e.getPosition(partial);
		switch (e.kind()) {
			case ChloroFxEntity.VINE -> vine(e, age, partial, origin, pose, buffers, light);
			case ChloroFxEntity.ROOTS -> roots(e, age, partial, origin, pose, buffers, light);
			case ChloroFxEntity.SPORES -> spores(e, age, pose, buffers);
			case ChloroFxEntity.BLOOM -> bloom(e, age, origin, pose, buffers, light);
			case ChloroFxEntity.OVERGROWTH -> overgrowth(e, age, origin, pose, buffers, light);
			case ChloroFxEntity.SENSE -> sense(e, age, pose, buffers, light);
			default -> {
			}
		}
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	private static Vector3f local(Vec3 world, Vec3 origin) {
		return new Vector3f((float) (world.x - origin.x), (float) (world.y - origin.y), (float) (world.z - origin.z));
	}

	/** Where a caster's casting hand is (raised along the look), in world space. */
	public static Vec3 handOf(Entity owner, float partial) {
		Vec3 pos = owner.getPosition(partial);
		float yaw = owner instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : owner.getYRot();
		double rad = Math.toRadians(yaw);
		Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
		Vec3 look = owner.getViewVector(partial);
		double h = owner instanceof Player p && p.isFallFlying() ? 0.4 : (owner.isCrouching() ? 1.15 : 1.38);
		return pos.add(0, h, 0).add(right.scale(0.36)).add(look.scale(0.62));
	}

	/** The ground height (world y of the top face) near {@code x, z}, searched from {@code y + 1.5} down 4 blocks. */
	private static float groundY(Level level, double x, double y, double z) {
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
		for (int dy = 2; dy >= -3; dy--) {
			bp.set(Mth.floor(x), Mth.floor(y) + dy, Mth.floor(z));
			var st = level.getBlockState(bp);
			if (!st.getCollisionShape(level, bp).isEmpty() && level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) {
				return (float) (bp.getY() + st.getCollisionShape(level, bp).max(net.minecraft.core.Direction.Axis.Y));
			}
		}
		return (float) y;
	}

	// ---------------- Vine ----------------

	private static void vine(ChloroFxEntity e, float age, float partial, Vec3 origin, PoseStack pose, MultiBufferSource buffers, int light) {
		Entity owner = e.followId() == 0 ? null : e.level().getEntity(e.followId());
		if (owner == null) {
			return;
		}
		Vec3 start = handOf(owner, partial);
		Vec3 end;
		Entity t = e.targetId() == 0 ? null : e.level().getEntity(e.targetId());
		if (t != null) {
			end = t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
		} else {
			Vector3f p = e.endPoint();
			end = new Vec3(p.x, p.y, p.z);
		}
		boolean lash = e.style() == ChloroFxEntity.STYLE_LASH;
		int life = e.maxLife();
		float grow = ChloroMesh.smooth(age / (lash ? 2.5f : 3.0f));
		float retract = ChloroMesh.smooth((age - (life - 4)) / 4f);
		float tipF = grow * (1 - retract);
		if (tipF <= 0.01f) {
			return;
		}
		Vec3 d = end.subtract(start);
		double len = d.length();
		if (len < 0.2) {
			return;
		}
		Vec3 dir = d.normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		int n = Math.max(10, Math.min(40, (int) (len * 2.2)));
		List<Vector3f> pts = new ArrayList<>();
		List<Vec3> world = new ArrayList<>();
		float sag = lash ? 0 : (float) Math.min(1.2, len * 0.06) * (1 - grow * 0.6f);
		for (int i = 0; i <= n; i++) {
			float f = (float) i / n;
			if (f > tipF) {
				break;
			}
			Vec3 p = start.add(d.scale(f));
			float belly = 4 * f * (1 - f);
			if (lash) {
				// a travelling whip wave that dies down as the lash connects
				float amp = (float) Math.min(1.1, len * 0.08) * (1 - ChloroMesh.smooth(age / 8f));
				p = p.add(side.scale(Mth.sin(f * Mth.PI * 2.5f - age * 1.9f) * amp * belly))
						.add(up.scale(Mth.cos(f * Mth.PI * 1.5f - age * 1.4f) * amp * 0.5f * belly));
			} else {
				p = p.add(0, -sag * belly, 0).add(side.scale(Mth.sin(f * 9f + age * 0.3f) * 0.05f * belly));
			}
			world.add(p);
			pts.add(local(p, origin));
		}
		if (pts.size() < 2) {
			return;
		}
		PoseStack.Pose last = pose.last();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.VINE));
		ChloroMesh.tube(vc, last, pts, lash ? 0.075f : 0.065f, 0.028f, 6, 1.5f, 255, 255, 255, 255, light);
		// leaves (and thorns on the lash) along it
		VertexConsumer lv = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.LEAF));
		RandomSource r = RandomSource.create(e.getId() * 977L);
		for (int i = 2; i < pts.size() - 1; i += 2) {
			Vector3f a = pts.get(i);
			Vector3f tan = new Vector3f(pts.get(i + 1)).sub(pts.get(i - 1)).normalize();
			float sgn = (i / 2) % 2 == 0 ? 1 : -1;
			Vector3f out = new Vector3f((float) side.x, (float) side.y, (float) side.z).mul(sgn).add(new Vector3f((float) up.x, (float) up.y,
					(float) up.z).mul(0.6f)).normalize();
			Vector3f along = new Vector3f(out).add(new Vector3f(tan).mul(0.7f)).normalize();
			if (lash && i % 4 == 0) {
				// a thorn: a short dark spike
				ChloroMesh.leaf(lv, last, a, new Vector3f(out).mul(-1), tan, 0.18f, 0.07f, 70, 60, 30, 255, light);
			}
			float s = 0.22f + r.nextFloat() * 0.12f;
			ChloroMesh.leaf(lv, last, a, along, tan, s, s * 0.75f, 255, 255, 255, 255, light);
		}
	}

	// ---------------- Roots ----------------

	private static void roots(ChloroFxEntity e, float age, float partial, Vec3 origin, PoseStack pose, MultiBufferSource buffers, int light) {
		Entity victim = e.targetId() == 0 ? null : e.level().getEntity(e.targetId());
		float height = victim == null ? 1.3f : Math.max(0.8f, victim.getBbHeight() * 0.8f);
		float hug = e.radius();
		Vec3 base = victim == null ? origin : new Vec3(victim.getPosition(partial).x, origin.y, victim.getPosition(partial).z);
		Vector3f off = local(base, origin);
		int life = e.maxLife();
		float grow = ChloroMesh.smooth(age / 6f);
		float sink = ChloroMesh.smooth((age - (life - 8)) / 8f);
		float f = grow * (1 - sink);
		if (f <= 0.01f) {
			return;
		}
		PoseStack.Pose last = pose.last();
		RandomSource r = RandomSource.create(e.getId() * 131L);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.ROOT));
		List<List<Vector3f>> all = new ArrayList<>();
		int roots = 6;
		for (int k = 0; k < roots; k++) {
			float a0 = Mth.TWO_PI * k / roots + r.nextFloat() * 0.5f;
			float spin = (r.nextBoolean() ? 1 : -1) * (1.4f + r.nextFloat() * 0.8f);
			float rOut = hug + 0.55f + r.nextFloat() * 0.35f;
			float h = height * (0.7f + r.nextFloat() * 0.45f);
			List<Vector3f> pts = new ArrayList<>();
			int n = 14;
			for (int i = 0; i <= n; i++) {
				float t = (float) i / n;
				if (t > f) {
					break;
				}
				float ang = a0 + t * spin + Mth.sin(age * 0.15f + k) * 0.04f;
				float rad = Mth.lerp(ChloroMesh.smooth(t * 1.4f), rOut, hug * 0.95f);
				float y = -0.25f + t * (h + 0.25f);
				pts.add(new Vector3f(off.x + Mth.cos(ang) * rad, off.y + y, off.z + Mth.sin(ang) * rad));
			}
			if (pts.size() >= 2) {
				ChloroMesh.tube(vc, last, pts, 0.13f, 0.035f, 6, 1.2f, 255, 255, 255, 255, light);
				all.add(pts);
			}
		}
		VertexConsumer lv = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.LEAF));
		for (List<Vector3f> pts : all) {
			for (int i = 3; i < pts.size() - 1; i += 4) {
				Vector3f a = pts.get(i);
				Vector3f tan = new Vector3f(pts.get(i + 1)).sub(pts.get(i - 1)).normalize();
				Vector3f out = new Vector3f(a.x - off.x, 0, a.z - off.z).normalize().add(0, 0.5f, 0).normalize();
				ChloroMesh.leaf(lv, last, a, out, tan, 0.2f, 0.15f, 210, 240, 190, 255, light);
			}
		}
	}

	// ---------------- Spores ----------------

	private static void spores(ChloroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers) {
		int life = e.maxLife();
		float fade = ChloroMesh.smooth(age / 10f) * (1 - ChloroMesh.smooth((age - (life - 20)) / 20f));
		if (fade <= 0.01f) {
			return;
		}
		Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
		VertexConsumer vc = buffers.getBuffer(ChloroMesh.glow());
		PoseStack.Pose last = pose.last();
		float rad = e.radius();
		RandomSource r = RandomSource.create(e.getId() * 53L);
		for (int i = 0; i < 34; i++) {
			float a0 = r.nextFloat() * Mth.TWO_PI;
			float d = (0.2f + r.nextFloat() * 0.8f) * rad * 0.8f;
			float speed = (0.01f + r.nextFloat() * 0.02f) * (r.nextBoolean() ? 1 : -1);
			float ang = a0 + age * speed;
			float y = -0.6f + r.nextFloat() * 1.6f + Mth.sin(age * 0.05f + i) * 0.25f;
			float size = 0.9f + r.nextFloat() * 1.1f;
			float pulse = 0.85f + 0.15f * Mth.sin(age * 0.1f + i * 1.7f);
			int g = 200 + r.nextInt(50);
			int a = (int) (70 * fade * pulse);
			ChloroMesh.billboard(vc, last, cam, new Vector3f(Mth.cos(ang) * d, y, Mth.sin(ang) * d), size * pulse, age * 0.01f + i, 215, g, 95, a,
					ChloroMesh.FULL_BRIGHT);
		}
	}

	// ---------------- Bloom ----------------

	private static void bloom(ChloroFxEntity e, float age, Vec3 origin, PoseStack pose, MultiBufferSource buffers, int light) {
		int life = e.maxLife();
		int count = 30;
		RandomSource r = RandomSource.create(e.getId() * 7919L);
		float rad = e.radius();
		float[] gx = new float[count], gz = new float[count], gy = new float[count], stem = new float[count], grow = new float[count];
		float[] wilt = new float[count];
		int[] col = new int[count];
		float[] spin = new float[count];
		for (int i = 0; i < count; i++) {
			float ang = Mth.TWO_PI * i / count + r.nextFloat() * 0.25f;
			float d = rad * (i % 2 == 0 ? 0.92f + r.nextFloat() * 0.08f : 0.55f + r.nextFloat() * 0.2f);
			gx[i] = Mth.cos(ang) * d;
			gz[i] = Mth.sin(ang) * d;
			gy[i] = groundY(e.level(), origin.x + gx[i], origin.y, origin.z + gz[i]) - (float) origin.y;
			stem[i] = 0.4f + r.nextFloat() * 0.45f;
			float delay = (i % 6) * 1.6f;
			grow[i] = ChloroMesh.smooth((age - delay) / 9f);
			wilt[i] = ChloroMesh.smooth((age - (life - 22) - (i % 4)) / 18f);
			col[i] = r.nextInt(FLOWER_COLOURS.length);
			spin[i] = r.nextFloat() * Mth.TWO_PI;
		}
		PoseStack.Pose last = pose.last();
		// stems
		VertexConsumer sv = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.VINE));
		for (int i = 0; i < count; i++) {
			float h = stem[i] * grow[i] * (1 - 0.55f * wilt[i]);
			if (h < 0.02f) {
				continue;
			}
			List<Vector3f> pts = new ArrayList<>();
			for (int k = 0; k <= 4; k++) {
				float t = k / 4f;
				float bend = wilt[i] * t * t * 0.25f;
				pts.add(new Vector3f(gx[i] + Mth.cos(spin[i]) * bend, gy[i] + t * h, gz[i] + Mth.sin(spin[i]) * bend));
			}
			ChloroMesh.tube(sv, last, pts, 0.045f, 0.03f, 5, 2f, 255, 255, 255, 255, light);
		}
		// two leaves at the foot of each stem
		VertexConsumer lv = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.LEAF));
		for (int i = 0; i < count; i++) {
			float g = grow[i] * (1 - 0.6f * wilt[i]);
			if (g < 0.05f) {
				continue;
			}
			for (int k = 0; k < 2; k++) {
				float a = spin[i] + k * Mth.PI;
				Vector3f out = new Vector3f(Mth.cos(a), 0.55f, Mth.sin(a));
				ChloroMesh.leaf(lv, last, new Vector3f(gx[i], gy[i] + 0.04f, gz[i]), out, new Vector3f(-Mth.sin(a), 0, Mth.cos(a)), 0.34f * g,
						0.24f * g, 255, 255, 255, 255, light);
			}
		}
		// the flower heads: glowing petals opening, then drooping and folding
		VertexConsumer pv = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.PETAL));
		for (int i = 0; i < count; i++) {
			float g = grow[i];
			if (g < 0.1f) {
				continue;
			}
			float h = stem[i] * g * (1 - 0.55f * wilt[i]);
			float open = ChloroMesh.smooth((g - 0.4f) / 0.6f) * (1 - wilt[i]) + wilt[i] * 1.45f;
			float size = 0.36f * g * (1 - 0.45f * wilt[i]);
			int[] c = FLOWER_COLOURS[col[i]];
			float dim = 1 - 0.35f * wilt[i];
			Vector3f head = new Vector3f(gx[i] + Mth.cos(spin[i]) * wilt[i] * 0.25f, gy[i] + h, gz[i] + Mth.sin(spin[i]) * wilt[i] * 0.25f);
			ChloroMesh.flower(pv, last, head, 5, open, size, spin[i] + age * 0.01f, (int) (c[0] * dim), (int) (c[1] * dim), (int) (c[2] * dim),
					255, ChloroMesh.FULL_BRIGHT);
			// the yellow heart
			ChloroMesh.flower(pv, last, new Vector3f(head).add(0, 0.01f, 0), 4, 1.0f, size * 0.32f, spin[i], 255, 220, 70, 255,
					ChloroMesh.FULL_BRIGHT);
		}
		// a faint green glow on the ground under the ring
		float ringA = ChloroMesh.smooth(age / 8f) * (1 - ChloroMesh.smooth((age - (life - 15)) / 15f));
		if (ringA > 0.01f) {
			VertexConsumer gv = buffers.getBuffer(ChloroMesh.glow());
			ChloroMesh.flatRing(gv, last, 0.06f, rad * 0.45f, rad * 1.08f, 48, 150, 255, 120, (int) (4 * ringA), (int) (34 * ringA),
					ChloroMesh.FULL_BRIGHT);
		}
	}

	// ---------------- Overgrowth ----------------

	private static void overgrowth(ChloroFxEntity e, float age, Vec3 origin, PoseStack pose, MultiBufferSource buffers, int light) {
		int gather = PlantManipulationHandlers.OVERGROWTH_GATHER;
		PoseStack.Pose last = pose.last();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.ROOT));
		List<Vector3f[]> leafAt = new ArrayList<>();
		if (e.followId() != 0 && age < gather + 2) {
			// the gather: roots spiral up the caster
			float g = ChloroMesh.smooth(age / gather);
			for (int k = 0; k < 5; k++) {
				float a0 = Mth.TWO_PI * k / 5;
				List<Vector3f> pts = new ArrayList<>();
				for (int i = 0; i <= 16; i++) {
					float t = i / 16f;
					if (t > g) {
						break;
					}
					float ang = a0 + t * 3.2f + age * 0.05f;
					float rad = Mth.lerp(t, 1.25f, 0.5f);
					pts.add(new Vector3f(Mth.cos(ang) * rad, -0.2f + t * 2.0f, Mth.sin(ang) * rad));
				}
				if (pts.size() >= 2) {
					ChloroMesh.tube(vc, last, pts, 0.11f, 0.03f, 6, 1.2f, 255, 255, 255, 255, light);
					Vector3f tip = pts.get(pts.size() - 1);
					leafAt.add(new Vector3f[] { tip, new Vector3f(tip.x, 0, tip.z).normalize().add(0, 0.8f, 0) });
				}
			}
		} else {
			// the burst: rings of root spikes erupt as the wave passes, then sink
			float t0 = gather;
			float rMax = e.radius();
			float speed = 1.5f;
			float[] ground = GROUND.get(e);
			RandomSource r = RandomSource.create(e.getId() * 4099L);
			List<float[]> spikes = new ArrayList<>();
			for (float rk = 1.2f; rk <= rMax; rk += 1.15f) {
				int n = Math.max(6, Math.round(Mth.TWO_PI * rk / 1.35f));
				for (int i = 0; i < n; i++) {
					float ang = Mth.TWO_PI * i / n + r.nextFloat() * 0.4f;
					float rr = rk + (r.nextFloat() - 0.5f) * 0.6f;
					spikes.add(new float[] { Mth.cos(ang) * rr, Mth.sin(ang) * rr, rr, 0.8f + r.nextFloat() * 0.9f, r.nextFloat() });
				}
			}
			if (ground == null || ground.length != spikes.size()) {
				ground = new float[spikes.size()];
				for (int i = 0; i < ground.length; i++) {
					float[] s = spikes.get(i);
					ground[i] = groundY(e.level(), origin.x + s[0], origin.y, origin.z + s[1]) - (float) origin.y;
				}
				GROUND.put(e, ground);
			}
			for (int i = 0; i < spikes.size(); i++) {
				float[] s = spikes.get(i);
				float at = t0 + s[2] / speed;
				float up = ChloroMesh.smooth((age - at) / 2.5f);
				float down = ChloroMesh.smooth((age - at - 11 - s[4] * 6) / 8f);
				float h = s[3] * up * (1 - down);
				if (h < 0.03f) {
					continue;
				}
				Vector3f outDir = new Vector3f(s[0], 0, s[1]).normalize();
				List<Vector3f> pts = new ArrayList<>();
				for (int k = 0; k <= 4; k++) {
					float t = k / 4f;
					pts.add(new Vector3f(s[0] + outDir.x * t * t * 0.35f * h, ground[i] - 0.15f + t * h, s[1] + outDir.z * t * t * 0.35f * h));
				}
				ChloroMesh.tube(vc, last, pts, 0.17f * (0.7f + s[3] * 0.4f), 0.015f, 5, 1.0f, 255, 255, 255, 255, light);
				if (s[4] > 0.55f) {
					leafAt.add(new Vector3f[] { pts.get(2), new Vector3f(outDir).add(0, 0.7f, 0) });
				}
			}
			// the wavefront: a dirt-green ring on the ground
			float front = (age - t0) * speed;
			if (front > 0 && front < rMax + 1.5f) {
				VertexConsumer gv = buffers.getBuffer(ChloroMesh.glow());
				float fa = 1 - front / (rMax + 1.5f);
				ChloroMesh.flatRing(gv, last, 0.08f, Math.max(0, front - 1.2f), front + 0.4f, 64, 120, 230, 80, 0, (int) (85 * fa),
						ChloroMesh.FULL_BRIGHT);
			}
		}
		VertexConsumer lv = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.LEAF));
		for (Vector3f[] l : leafAt) {
			Vector3f side = new Vector3f(-l[1].z, 0, l[1].x);
			if (side.lengthSquared() < 1.0e-4f) {
				side.set(1, 0, 0);
			}
			ChloroMesh.leaf(lv, last, l[0], l[1], side, 0.26f, 0.2f, 255, 255, 255, 255, light);
		}
	}

	// ---------------- Root Sense ----------------

	private static void sense(ChloroFxEntity e, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		int life = e.maxLife();
		float f = ChloroMesh.smooth(age / (life * 0.8f));
		float rr = f * e.radius();
		float a = 1 - ChloroMesh.smooth((age - life * 0.5f) / (life * 0.5f));
		if (a <= 0.01f) {
			return;
		}
		PoseStack.Pose last = pose.last();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ChloroMesh.ROOT));
		for (int k = 0; k < 10; k++) {
			float ang = Mth.TWO_PI * k / 10 + 0.2f;
			List<Vector3f> pts = new ArrayList<>();
			int n = 12;
			for (int i = 0; i <= n; i++) {
				float t = i / (float) n;
				float d = t * Math.min(rr, 9f);
				float w = Mth.sin(t * 7 + k) * 0.25f * t;
				pts.add(new Vector3f(Mth.cos(ang) * d - Mth.sin(ang) * w, 0.04f, Mth.sin(ang) * d + Mth.cos(ang) * w));
			}
			ChloroMesh.tube(vc, last, pts, 0.07f * a, 0.01f, 4, 1.2f, 255, 255, 255, 255, light);
		}
		VertexConsumer gv = buffers.getBuffer(ChloroMesh.glow());
		ChloroMesh.flatRing(gv, last, 0.07f, Math.max(0, rr - 1.4f), rr, 72, 140, 255, 110, 0, (int) (150 * a), ChloroMesh.FULL_BRIGHT);
	}
}
