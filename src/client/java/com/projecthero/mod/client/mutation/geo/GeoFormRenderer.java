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
		return entity.kind() == GeoFormEntity.QUAKE || super.shouldRender(entity, frustum, x, y, z);
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
