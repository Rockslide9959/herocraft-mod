package com.projecthero.mod.client.mutation.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p05.GeoFormEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.5 Geokinesis: the raised earth -- every shape animated from the entity's age, in the ground's own block:
 * <ul>
 * <li>Earth Grip: two stone hands punch up out of the ground either side of the target, then their fingers curl shut
 * over its shins; they sink back at the end;</li>
 * <li>Tectonic Pillar: a jagged column shoots up 4 blocks in four ticks, a crown of rubble on top and a ring of broken
 * ground at its foot, then grinds back down;</li>
 * <li>Earth Spikes: a tapering, tilted spike stabs up and retracts;</li>
 * <li>Earthquake: a ring of heaving ground slabs running outward, lifting and dropping as the wave passes;</li>
 * <li>Sinkhole: the ground round the hole tipping inward and caving down into it.</li>
 * </ul>
 */
public class GeoFormRenderer extends EntityRenderer<GeoFormEntity> {
	public GeoFormRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.0f;
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}

	@Override
	public boolean shouldRender(GeoFormEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
		int k = entity.kind();
		return k == GeoFormEntity.QUAKE || k == GeoFormEntity.FISSURE || k == GeoFormEntity.QUICKSAND || k == GeoFormEntity.AVALANCHE
				|| super.shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public void render(GeoFormEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partial;
		int life = e.life();
		BlockState b = e.block();
		pose.pushPose();
		switch (e.kind()) {
			case GeoFormEntity.GRIP -> grip(e, pose, buffers, light, b, age, life);
			case GeoFormEntity.PILLAR -> pillar(e, pose, buffers, light, b, age, life);
			case GeoFormEntity.SPIKE -> spike(e, pose, buffers, light, b, age, life);
			case GeoFormEntity.QUAKE -> quake(e, pose, buffers, light, b, age, life);
			case GeoFormEntity.SINK -> sink(e, pose, buffers, light, b, age, life);
			case GeoFormEntity.BURROW -> burrow(e, pose, buffers, light, b, age, partial);
			case GeoFormEntity.FISSURE -> fissure(e, pose, buffers, light, b, age, life);
			case GeoFormEntity.QUICKSAND -> quicksand(e, pose, buffers, light, b, age, life);
			case GeoFormEntity.AVALANCHE -> avalanche(e, pose, buffers, light, b, age, life);
			default -> {
			}
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- Earth Grip

	private static void grip(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float rise = ease(age / 5.0f) * (1.0f - ease((age - (life - 8)) / 8.0f));
		float close = ease((age - 3.0f) / 5.0f);
		float half = e.size() * 0.5f + 0.12f;
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			pose.translate(side * half, -1.05 + rise * 1.05, 0);
			// the hand leans in toward the legs as it closes
			pose.mulPose(Axis.ZP.rotationDegrees(side * (8 + close * 14)));
			hand(pose, buf, light, b, side, close, e.getId() + side);
			pose.popPose();
		}
	}

	/** A stone hand rising palm-inward: a blocky palm, four fingers curling over the top, a thumb. */
	private static void hand(PoseStack pose, MultiBufferSource buf, int light, BlockState b, int side, float close, int seed) {
		// a thick forearm out of the ground, then a flat palm (the palm faces the target, along -side x)
		GeoModels.box(pose, buf, light, b, -0.15f, -0.2f, -0.17f, 0.3f, 0.55f, 0.34f);
		GeoModels.box(pose, buf, light, b, -0.08f, 0.32f, -0.27f, 0.16f, 0.46f, 0.54f);
		for (int f = 0; f < 4; f++) {
			float z = -0.255f + f * 0.14f;
			float len = f == 0 || f == 3 ? 0.36f : 0.44f;
			pose.pushPose();
			pose.translate(0.0, 0.76, z + 0.05);
			// open, then curling in two knuckles over the target's shins (the -side direction)
			pose.mulPose(Axis.ZP.rotationDegrees(side * (-10 + close * 75)));
			GeoModels.box(pose, buf, light, b, -0.06f, 0, -0.05f, 0.12f, len * 0.55f, 0.1f);
			pose.translate(0, len * 0.5, 0);
			pose.mulPose(Axis.ZP.rotationDegrees(side * close * 60));
			GeoModels.box(pose, buf, light, b, -0.055f, 0, -0.045f, 0.11f, len * 0.5f, 0.09f);
			pose.popPose();
		}
		// thumb, from the front edge of the palm, wrapping round the front of the legs
		pose.pushPose();
		pose.translate(0, 0.42, -0.3);
		pose.mulPose(Axis.YP.rotationDegrees(side * 30));
		pose.mulPose(Axis.XP.rotationDegrees(-(30 + close * 55)));
		GeoModels.box(pose, buf, light, b, -0.06f, 0, -0.06f, 0.12f, 0.34f, 0.12f);
		pose.popPose();
		// a lip of broken ground round the wrist
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			pose.translate((GeoModels.rnd(seed, i) - 0.5f) * 0.5f, 0.02, (i - 1) * 0.28);
			pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(seed, 5 + i) * 90));
			GeoModels.centred(pose, buf, light, b, 0.22f, 0.12f, 0.2f);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- Tectonic Pillar

	private static void pillar(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float height = 4.0f;
		float up = ease(age / 4.0f);
		float down = ease((age - (life - 16)) / 16.0f);
		float top = height * (up - down);
		float w = e.size();
		int seed = e.getId();
		pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(seed, 1) * 90));
		// the column: stacked, slightly staggered segments; the part below ground is hidden by the ground itself
		int segs = (int) Math.ceil(height);
		for (int i = 0; i < segs; i++) {
			float y0 = top - height + i;
			float jx = (GeoModels.rnd(seed, 10 + i) - 0.5f) * 0.12f;
			float jz = (GeoModels.rnd(seed, 20 + i) - 0.5f) * 0.12f;
			float ww = w * (1.0f - i * 0.04f);
			GeoModels.box(pose, buf, light, b, -ww / 2 + jx, y0, -ww / 2 + jz, ww, 1.0f, ww);
		}
		// the crown: jagged lumps on top
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			float a = i * 2.1f + GeoModels.rnd(seed, 30 + i);
			pose.translate(Math.cos(a) * w * 0.22, top + 0.05, Math.sin(a) * w * 0.22);
			pose.mulPose(Axis.ZP.rotationDegrees((GeoModels.rnd(seed, 40 + i) - 0.5f) * 40));
			GeoModels.box(pose, buf, light, b, -0.2f, 0, -0.2f, 0.4f, 0.25f + GeoModels.rnd(seed, 50 + i) * 0.3f, 0.4f);
			pose.popPose();
		}
		// the ring of broken ground at its foot (it stays up while the column stands)
		float ring = ease(age / 3.0f) * (1.0f - down);
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			float a = i / 8.0f * (float) Math.PI * 2;
			float r = w * 0.5f + 0.35f;
			pose.translate(Math.cos(a) * r, -0.15 + ring * 0.12, Math.sin(a) * r);
			pose.mulPose(Axis.YP.rotation(-a));
			pose.mulPose(Axis.ZP.rotationDegrees(ring * (20 + GeoModels.rnd(seed, 60 + i) * 20)));
			GeoModels.centred(pose, buf, light, b, 0.45f, 0.22f, 0.5f);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- Earth Spikes

	private static void spike(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float out = ease(age / 3.0f) * (1.0f - ease((age - (life - 7)) / 7.0f));
		float h = 1.9f * e.size();
		int seed = e.getId();
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.translate(0, -h + h * out, 0);
		pose.mulPose(Axis.XP.rotationDegrees((GeoModels.rnd(seed, 1) - 0.5f) * 24));
		pose.mulPose(Axis.ZP.rotationDegrees((GeoModels.rnd(seed, 2) - 0.5f) * 24));
		float[] widths = { 0.72f, 0.52f, 0.34f, 0.16f };
		float y = 0;
		for (int i = 0; i < widths.length; i++) {
			float seg = h / widths.length;
			float ww = widths[i] * e.size();
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.mulPose(Axis.YP.rotationDegrees(i * 23));
			GeoModels.box(pose, buf, light, b, -ww / 2, 0, -ww / 2, ww, seg + 0.05f, ww);
			pose.popPose();
			y += seg;
		}
	}

	// ---------------------------------------------------------------- Earthquake

	private static void quake(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float run = life * 0.7f;
		float front = Math.min(1.0f, age / run) * e.size();
		float fade = 1.0f - ease((age - run) / (life - run));
		int seed = e.getId();
		// two crests: the wave front, and a smaller one a block and a half behind it
		for (int crest = 0; crest < 2; crest++) {
			float r = front - crest * 1.6f;
			if (r < 0.6f) {
				continue;
			}
			float lift = (crest == 0 ? 0.55f : 0.3f) * fade;
			int n = Math.max(8, (int) (r * Math.PI * 2 / 0.75f));
			for (int i = 0; i < n; i++) {
				float a = (i + GeoModels.rnd(seed, i) * 0.4f) / n * (float) Math.PI * 2;
				float bump = lift * (0.75f + 0.5f * GeoModels.rnd(seed, 100 + i + crest * 300));
				pose.pushPose();
				pose.translate(Math.cos(a) * r, -0.32 + bump, Math.sin(a) * r);
				pose.mulPose(Axis.YP.rotation(-a));
				// tipped back against the direction of travel, like ground thrown up by the wave
				pose.mulPose(Axis.ZP.rotationDegrees(-(14 + GeoModels.rnd(seed, 200 + i) * 22) * fade));
				GeoModels.centred(pose, buf, light, groundAt(e, Math.cos(a) * r, Math.sin(a) * r, b), 0.62f, 0.4f, 0.78f);
				pose.popPose();
			}
		}
	}

	// ---------------------------------------------------------------- Sinkhole

	/** The ground block under a point {@code (dx, dz)} off the form (the ripple runs over grass, sand and stone alike). */
	private static BlockState groundAt(GeoFormEntity e, double dx, double dz, BlockState fallback) {
		net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(e.getX() + dx, e.getY() - 0.5, e.getZ() + dz);
		for (int i = 0; i < 3; i++) {
			BlockState s = e.level().getBlockState(p);
			if (!s.isAir() && s.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
				return s;
			}
			p = p.below();
		}
		return fallback;
	}

	private static void sink(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float t = ease(age / (life * 0.8f));
		float w = e.size();
		int seed = e.getId();
		for (int i = 0; i < 10; i++) {
			pose.pushPose();
			float a = i / 10.0f * (float) Math.PI * 2 + GeoModels.rnd(seed, i) * 0.3f;
			float r = w * 0.5f + 0.2f - t * 0.45f;
			pose.translate(Math.cos(a) * r, 0.05 - t * 0.9, Math.sin(a) * r);
			pose.mulPose(Axis.YP.rotation(-a));
			// tipping in toward the hole
			pose.mulPose(Axis.ZP.rotationDegrees(t * (55 + GeoModels.rnd(seed, 20 + i) * 30)));
			GeoModels.centred(pose, buf, light, b, 0.5f, 0.3f, 0.55f);
			pose.popPose();
		}
		// rubble pouring into the middle
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			float fall = Mth.clamp(t * 1.4f - i * 0.12f, 0, 1);
			pose.translate((GeoModels.rnd(seed, 40 + i) - 0.5f) * w * 0.6, 0.5 - fall * 1.6, (GeoModels.rnd(seed, 50 + i) - 0.5f) * w * 0.6);
			pose.mulPose(Axis.XP.rotationDegrees(age * 20 + i * 40));
			GeoModels.rock(pose, buf, light, b, seed + i, 0.3f);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- v0.16.6 hold X: Burrow

	/**
	 * The ground caving in round you as you sink (a ring of slabs tipping down into the hole), then the mound that runs along
	 * the surface over you: a hump of heaving lumps of earth, the front ones shoved up like a bow wave, crumbs tumbling off.
	 */
	private static void burrow(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, float partial) {
		float sunk = e instanceof com.projecthero.mod.hero.power.p05.GeoBurrowEntity be ? be.sunk(partial) : 1.0f;
		int seed = e.getId();
		BlockState ground = groundAt(e, 0, 0, b);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		// the hole caving in (only while you go down)
		float cave = 1.0f - ease((age - 9.0f) / 6.0f);
		if (cave > 0.01f) {
			for (int i = 0; i < 9; i++) {
				pose.pushPose();
				float a = i / 9.0f * (float) Math.PI * 2 + GeoModels.rnd(seed, i) * 0.3f;
				// a lip of broken ground heaved up round the hole, tipping in as you go down
				float r = 0.95f - sunk * 0.25f;
				pose.translate(Math.cos(a) * r, 0.06 + (float) Math.sin(sunk * Math.PI) * 0.2 - sunk * 0.12 - (1 - cave) * 0.45, Math.sin(a) * r);
				pose.mulPose(Axis.YP.rotation(-a));
				pose.mulPose(Axis.ZP.rotationDegrees(sunk * (40 + GeoModels.rnd(seed, 20 + i) * 25)));
				GeoModels.centred(pose, buf, light, ground, 0.6f, 0.3f, 0.62f);
				pose.popPose();
			}
		}
		// the mound over you
		float grow = ease((age - 4.0f) / 6.0f);
		if (grow <= 0.01f) {
			return;
		}
		for (int i = 0; i < 9; i++) {
			pose.pushPose();
			float along = -0.9f + i * 0.24f + (GeoModels.rnd(seed, 40 + i) - 0.5f) * 0.15f;
			float across = (GeoModels.rnd(seed, 50 + i) - 0.5f) * 0.8f;
			// the front of the hump is shoved up highest; every lump heaves as the earth is pushed through
			float bow = 0.12f + 0.3f * Mth.clamp((along + 0.9f) / 1.8f, 0, 1);
			float heave = (float) Math.sin(age * 0.9f + i * 1.7f) * 0.07f;
			pose.translate(across, -0.32 + grow * (bow + heave), along);
			pose.mulPose(Axis.XP.rotationDegrees(age * (6 + i) + i * 40));
			pose.mulPose(Axis.YP.rotationDegrees(i * 37));
			GeoModels.rock(pose, buf, light, i % 3 == 0 ? b : ground, seed + i, (0.46f + GeoModels.rnd(seed, 60 + i) * 0.26f) * grow);
			pose.popPose();
		}
		// crumbs tumbling off the sides and back
		for (int i = 0; i < 4; i++) {
			float t = ((age * 0.12f + GeoModels.rnd(seed, 70 + i)) % 1.0f);
			pose.pushPose();
			float side = i % 2 == 0 ? 1 : -1;
			pose.translate(side * (0.4 + t * 0.5), -0.05 + Math.sin(t * Math.PI) * 0.35 - t * 0.2, 0.3 - t * 1.0);
			pose.mulPose(Axis.XP.rotationDegrees(t * 360));
			GeoModels.rock(pose, buf, light, ground, seed * 3 + i, 0.16f * grow);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- v0.16.6 hold Z: Fissure

	/**
	 * The lips of the crack: slabs of the ground along both edges, heaved up and tipped in as the crack runs out from the
	 * hero (2 blocks a tick, like the server), rubble tumbling down into it, then the lips grinding back together at the close.
	 */
	private static void fissure(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float length = e.size();
		int seed = e.getId();
		float closeT = life - 12;
		float closing = ease((age - (closeT - 8)) / 8.0f);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		double yaw = Math.toRadians(e.getYRot());
		for (int i = 0; ; i++) {
			float d = 1.5f + i;
			if (d > length + 1.0f) {
				break;
			}
			float t0 = (d - 1.5f) / 2.0f;
			float open = ease((age - t0) / 3.0f) * (1.0f - closing);
			if (open <= 0.01f && age < t0) {
				continue;
			}
			float half = d > 4.0f && d < length - 2.5f ? 1.45f : 0.55f;
			// the ground block at this point of the crack (world offset from the form)
			double wx = -Math.sin(yaw) * d;
			double wz = Math.cos(yaw) * d;
			BlockState lip = groundAt(e, wx + Math.cos(yaw) * (half + 0.3), wz + Math.sin(yaw) * (half + 0.3), b);
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				float jitter = (GeoModels.rnd(seed, i * 2 + (side + 1) / 2) - 0.5f);
				// set out on the edge (the inner lip just overhangs the real crack), sliding in over it at the close
				pose.translate(side * (half + 0.42 - closing * (half + 0.1)), -0.16 + open * (0.16 + jitter * 0.08), d + jitter * 0.2);
				pose.mulPose(Axis.ZP.rotationDegrees(side * open * (26 + jitter * 14)));
				pose.mulPose(Axis.YP.rotationDegrees(jitter * 12));
				GeoModels.centred(pose, buf, light, lip, 0.75f, 0.3f, 1.08f);
				pose.popPose();
			}
			// rubble dropping in as it opens
			float fall = Mth.clamp((age - t0) / 9.0f, 0, 1);
			if (fall > 0 && fall < 1 && closing <= 0.0f) {
				for (int k = 0; k < 2; k++) {
					pose.pushPose();
					float f2 = Mth.clamp(fall * 1.3f - k * 0.25f, 0, 1);
					pose.translate((GeoModels.rnd(seed, 300 + i * 2 + k) - 0.5f) * half, 0.1 - f2 * f2 * 2.8, d + (k - 0.5f) * 0.4);
					pose.mulPose(Axis.XP.rotationDegrees(age * 25 + k * 70));
					GeoModels.rock(pose, buf, light, lip, seed + i * 7 + k, 0.22f + 0.1f * k);
					pose.popPose();
				}
			}
		}
	}

	// ---------------------------------------------------------------- v0.16.6 hold G: Quicksand

	/**
	 * The pool: a surface of churning sand over every cell of ground that gave way (it is drawn at the old ground level, so
	 * whatever stands in it sinks out of sight to the waist), lumps of sand swirling round toward the middle, a rim of churned
	 * ground; it settles flat as it hardens.
	 */
	private static void quicksand(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float r = e.size() * 0.5f;
		int seed = e.getId();
		float grow = ease(age / 6.0f);
		float settle = ease((age - (life - 18)) / 12.0f);
		float churn = 1.0f - settle;
		int bx = Mth.floor(e.getX());
		int by = Mth.floor(e.getY() + 0.01) - 1;
		int bz = Mth.floor(e.getZ());
		double ox = bx + 0.5 - e.getX();
		double oz = bz + 0.5 - e.getZ();
		int ri = (int) Math.ceil(r);
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				float dist = Mth.sqrt(dx * dx + dz * dz);
				if (dist > r * grow + 0.6f || !e.level().getBlockState(new net.minecraft.core.BlockPos(bx + dx, by, bz + dz)).isAir()) {
					continue;
				}
				for (int s = 0; s < 4; s++) {
					float sx = dx + ((s & 1) - 0.5f) * 0.5f;
					float sz = dz + ((s >> 1) - 0.5f) * 0.5f;
					float ang = (float) Math.atan2(sz, sx);
					float bob = (float) Math.sin(age * 0.28f + dist * 1.6f - ang * 2) * 0.05f * churn;
					pose.pushPose();
					pose.translate(ox + sx, -0.09 + bob, oz + sz);
					// a gentle rocking churn, not a spin: the surface reads as one sheet of sand heaving
					pose.mulPose(Axis.YP.rotationDegrees((float) Math.sin(age * 0.12f + GeoModels.rnd(seed, dx * 31 + dz * 7 + s) * 6.28f) * 12 * churn));
					pose.mulPose(Axis.XP.rotationDegrees(bob * 40));
					GeoModels.centred(pose, buf, light, b, 0.7f, 0.14f, 0.7f);
					pose.popPose();
				}
			}
		}
		// lumps of sand swirling round and in toward the middle
		for (int i = 0; i < 12; i++) {
			float base = GeoModels.rnd(seed, 100 + i);
			float rad = (0.4f + ((base + age * 0.006f * (1 + i % 3)) % 1.0f) * (r - 0.6f)) * grow;
			float a = base * 6.28f + age * 0.09f * churn * (2.0f / Math.max(0.6f, rad));
			pose.pushPose();
			pose.translate(Math.cos(a) * rad, 0.0 + Math.sin(age * 0.5f + i) * 0.04 * churn - settle * 0.12, Math.sin(a) * rad);
			pose.mulPose(Axis.YP.rotation(-a));
			pose.mulPose(Axis.ZP.rotationDegrees(age * 9 + i * 30));
			GeoModels.rock(pose, buf, light, b, seed + i, 0.24f + 0.08f * (i % 3));
			pose.popPose();
		}
		// the rim: churned-up ground tipping in
		BlockState rim = groundAt(e, r + 0.8, 0, b);
		for (int i = 0; i < 14; i++) {
			float a = i / 14.0f * (float) Math.PI * 2 + GeoModels.rnd(seed, 200 + i) * 0.2f;
			float rr = (r + 0.35f) * grow;
			pose.pushPose();
			pose.translate(Math.cos(a) * rr, -0.18 + 0.1 * churn, Math.sin(a) * rr);
			pose.mulPose(Axis.YP.rotation(-a));
			pose.mulPose(Axis.ZP.rotationDegrees(churn * (22 + GeoModels.rnd(seed, 220 + i) * 20)));
			GeoModels.centred(pose, buf, light, rim, 0.45f, 0.22f, 0.6f);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- v0.16.6 hold C: Avalanche

	/** The rubble heap: lumps of rock thumping down all over the target area over the 3 s, then sinking back into the ground. */
	private static void avalanche(GeoFormEntity e, PoseStack pose, MultiBufferSource buf, int light, BlockState b, float age, int life) {
		float r = e.size() * 0.5f;
		int seed = e.getId();
		float sink = ease((age - (life - 22)) / 20.0f);
		for (int i = 0; i < 28; i++) {
			float ti = 4 + GeoModels.rnd(seed, i) * (life - 44);
			float land = ease((age - ti) / 3.0f);
			if (land <= 0.0f) {
				continue;
			}
			float a = GeoModels.rnd(seed, 100 + i) * 6.283f;
			float rad = Mth.sqrt(GeoModels.rnd(seed, 200 + i)) * (r - 0.4f);
			float size = 0.35f + GeoModels.rnd(seed, 300 + i) * 0.3f;
			// a little bounce as it lands, stacked on the lumps before it near the middle
			float bounce = (float) Math.sin(Mth.clamp((age - ti - 3) / 5.0f, 0, 1) * Math.PI) * 0.18f;
			float stack = rad < r * 0.45f ? (i % 3) * 0.18f : 0;
			pose.pushPose();
			pose.translate(Math.cos(a) * rad, -0.12 + stack + (1 - land) * 2.5 + bounce - sink * (0.75 + stack), Math.sin(a) * rad);
			pose.mulPose(Axis.YP.rotationDegrees(GeoModels.rnd(seed, 400 + i) * 360));
			pose.mulPose(Axis.XP.rotationDegrees(GeoModels.rnd(seed, 500 + i) * 40 - 20));
			GeoModels.rock(pose, buf, light, i % 4 == 0 ? groundAt(e, Math.cos(a) * rad, Math.sin(a) * rad, b) : b, seed + i, size);
			pose.popPose();
		}
	}

	@Override
	public ResourceLocation getTextureLocation(GeoFormEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}

	/** For the client: forms never cast a shadow or show a name. */
	@Override
	protected boolean shouldShowName(GeoFormEntity entity) {
		return false;
	}

}
