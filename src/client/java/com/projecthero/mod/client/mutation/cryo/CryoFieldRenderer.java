package com.projecthero.mod.client.mutation.cryo;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p09.CryoFieldEntity;
import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.6 Cryokinesis: the two lasting hold-move fields.
 * <ul>
 * <li><b>Frozen Ground</b> -- a sheet of ice laid over the top of every block in the radius (it follows the ground: steps,
 * slopes, water), spreading out from the centre over 0.7 s, frost crystals scattered over it and a ring of taller
 * crystals leaning out round its rim; it fades and thins in its last second.</li>
 * <li><b>Blizzard</b> -- a swirling column of snow: two counter-rotating curtains of falling snow (the vanilla snowfall
 * texture, scrolling down and round), a hundred and fifty flakes spiralling inside, a churning grey-white cloud over it
 * and a snow drift on the ground; it builds up over its first second and blows out in its last.</li>
 * </ul>
 */
public class CryoFieldRenderer extends EntityRenderer<CryoFieldEntity> {
	private static final ResourceLocation SNOWFALL = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
	private static final ResourceLocation SNOW_BLOCK = ResourceLocation.withDefaultNamespace("textures/block/snow.png");

	/** One ground cell under the sheet (relative to the field's position). */
	private record Cell(float x, float y, float z, float dist, int light, int hash) {
	}

	public CryoFieldRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(CryoFieldEntity e, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(CryoFieldEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float age = e.tickCount + partial;
		float out = Mth.clamp((e.life() - age) / 20.0f, 0.0f, 1.0f);
		if (e.kind() == CryoFieldEntity.GROUND) {
			ground(e, age, out, pose, buffers);
		} else {
			blizzard(e, age, out, pose, buffers, light);
		}
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	// ================================================================ Frozen Ground

	@SuppressWarnings("unchecked")
	private static List<Cell> cells(CryoFieldEntity e) {
		if (e.renderCache instanceof List<?> l) {
			return (List<Cell>) l;
		}
		List<Cell> cells = new ArrayList<>();
		Level level = e.level();
		int r = Mth.ceil(e.radius());
		BlockPos base = BlockPos.containing(e.getX(), e.getY(), e.getZ());
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double cx = base.getX() + dx + 0.5;
				double cz = base.getZ() + dz + 0.5;
				double d = Math.sqrt((cx - e.getX()) * (cx - e.getX()) + (cz - e.getZ()) * (cz - e.getZ()));
				if (d > e.radius()) {
					continue;
				}
				for (int dy = 3; dy >= -4; dy--) {
					BlockPos pos = base.offset(dx, dy, dz);
					BlockState st = level.getBlockState(pos);
					VoxelShape shape = st.getCollisionShape(level, pos);
					boolean water = st.getFluidState().is(Fluids.WATER);
					if (shape.isEmpty() && !water) {
						continue;
					}
					BlockPos above = pos.above();
					if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
						break; // buried: no sheet here
					}
					double top = shape.isEmpty() ? pos.getY() + 0.9 : pos.getY() + shape.max(Direction.Axis.Y);
					int hash = (pos.getX() * 73856093) ^ (pos.getZ() * 19349663);
					cells.add(new Cell((float) (cx - e.getX()), (float) (top - e.getY()), (float) (cz - e.getZ()), (float) d,
							LevelRenderer.getLightColor(level, above), hash));
					break;
				}
			}
		}
		e.renderCache = cells;
		return cells;
	}

	private static void ground(CryoFieldEntity e, float age, float out, PoseStack pose, MultiBufferSource buffers) {
		float reach = e.radius() * Math.min(1.0f, age / 14.0f);
		for (Cell c : cells(e)) {
			if (c.dist() > reach) {
				continue;
			}
			// a frosty rim where the ice is still spreading
			float edge = Mth.clamp((reach - c.dist()) / 1.5f, 0.0f, 1.0f);
			int lit = LightTexture.pack(Math.max(LightTexture.block(c.light()), 6), LightTexture.sky(c.light()));
			int a = (int) (200 * out * (0.5f + 0.5f * edge));
			float thick = 0.06f * (0.3f + 0.7f * out);
			IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), c.x() - 0.5f, c.y() + 0.004f, c.z() - 0.5f, c.x() + 0.5f, c.y() + thick, c.z() + 0.5f, 1.0f,
					(a << 24) | 0xE4F6FF, lit);
			int h = Math.abs(c.hash());
			// scattered frost crystals
			if (h % 5 == 0 && out > 0.05f) {
				int n = 2 + h % 3;
				for (int i = 0; i < n; i++) {
					float ang = (h >> 3) % 360 + i * 120;
					float sh = (0.18f + ((h >> (i + 4)) & 7) * 0.04f) * edge * out;
					pose.pushPose();
					pose.translate(c.x() + Mth.cos(ang * Mth.DEG_TO_RAD) * 0.2f, c.y() + 0.02f, c.z() + Mth.sin(ang * Mth.DEG_TO_RAD) * 0.2f);
					pose.mulPose(Axis.YP.rotationDegrees(ang));
					pose.mulPose(Axis.ZP.rotation(0.25f + i * 0.2f));
					IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.09f, sh, 0, 0xE8F4FBFF, lit);
					pose.popPose();
				}
			}
			// the rim: a ring of taller crystals leaning outward
			if (c.dist() > e.radius() - 1.0f && h % 2 == 0 && out > 0.05f) {
				float ang = (float) Mth.atan2(c.z(), c.x());
				float sh = (0.45f + (h % 7) * 0.08f) * edge * out;
				pose.pushPose();
				pose.translate(c.x(), c.y() + 0.02f, c.z());
				pose.mulPose(Axis.YP.rotation(-ang));
				pose.mulPose(Axis.ZP.rotation(-0.45f));
				IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.PACKED)), 0.18f, sh, sh * 0.2f, 0xF0FFFFFF, lit);
				pose.popPose();
			}
		}
	}

	// ================================================================ Blizzard

	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
			float u0, float v0, float u1, float v1, int argb, int light) {
		// the top fades out into the cloud
		int top = ((int) (((argb >>> 24) & 0xFF) * 0.35f) << 24) | (argb & 0xFFFFFF);
		float nx = (x0 + x1) * 0.5f;
		float nz = (z0 + z1) * 0.5f;
		float len = Math.max(1.0e-3f, (float) Math.sqrt(nx * nx + nz * nz));
		nx /= len;
		nz /= len;
		vc.addVertex(p, x0, y0, z0).setColor(argb).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
		vc.addVertex(p, x0, y1, z0).setColor(top).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
		vc.addVertex(p, x1, y1, z1).setColor(top).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
		vc.addVertex(p, x1, y0, z1).setColor(argb).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
	}

	/** A ring of falling-snow quads, {@code r} round and {@code h} tall, its texture scrolling down and round. */
	private static void curtain(PoseStack pose, VertexConsumer vc, float r, float h, float spin, float fall, int segs, int argb, int light) {
		PoseStack.Pose p = pose.last();
		for (int i = 0; i < segs; i++) {
			float a0 = (i / (float) segs) * Mth.TWO_PI + spin;
			float a1 = ((i + 1) / (float) segs) * Mth.TWO_PI + spin;
			float x0 = Mth.cos(a0) * r;
			float z0 = Mth.sin(a0) * r;
			float x1 = Mth.cos(a1) * r;
			float z1 = Mth.sin(a1) * r;
			float u0 = i * 0.37f * 0.3f;
			float v0 = fall + i * 0.13f;
			// the snowfall texture at 4x vanilla scale, so the flakes read from a distance
			quad(vc, p, x0, 0, z0, x1, h, z1, u0, v0, u0 + 0.3f, v0 + h * 0.07f, argb, light);
		}
	}

	private static void blizzard(CryoFieldEntity e, float age, float out, PoseStack pose, MultiBufferSource buffers, int light) {
		float in = Mth.clamp(age / 20.0f, 0.0f, 1.0f);
		float k = in * out;
		if (k <= 0.01f) {
			return;
		}
		float r = e.radius();
		float h = (float) CryokinesisHandlers.BLIZZARD_HEIGHT;
		int lit = LightTexture.pack(Math.max(LightTexture.block(light), 9), Math.max(LightTexture.sky(light), 9));
		VertexConsumer snow = buffers.getBuffer(RenderType.entityTranslucent(SNOWFALL));
		// two counter-rotating curtains of driving snow
		curtain(pose, snow, r, h * k, age * 0.05f, -age * 0.045f, 28, ((int) (235 * k) << 24) | 0xFFFFFF, lit);
		curtain(pose, snow, r * 0.62f, h * k, -age * 0.08f, -age * 0.06f, 20, ((int) (210 * k) << 24) | 0xF4FAFF, lit);
		curtain(pose, snow, r * 0.3f, h * k, age * 0.12f, -age * 0.08f, 12, ((int) (180 * k) << 24) | 0xF4FAFF, lit);
		// a white-out haze inside the curtains
		VertexConsumer haze = buffers.getBuffer(RenderType.entityTranslucent(IceDraw.WHITE));
		curtain(pose, haze, r * 0.97f, h * k, 0, 0, 28, ((int) (55 * k) << 24) | 0xEEF4F8, lit);
		curtain(pose, haze, r * 0.5f, h * k, 0, 0, 18, ((int) (40 * k) << 24) | 0xF4F8FC, lit);
		// flakes spiralling round inside it
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(IceDraw.WHITE));
		for (int i = 0; i < 150; i++) {
			float seed = i * 12.9898f;
			float rr = r * (0.15f + 0.85f * ((Mth.sin(seed) * 43758.5453f) % 1.0f + 1.0f) % 1.0f);
			float speed = 0.06f + (i % 7) * 0.012f;
			float ang = i * 2.399963f + age * speed * (6.0f / Math.max(1.0f, rr));
			float y = ((i * 0.618f * h) - age * (0.05f + (i % 5) * 0.012f)) % h;
			if (y < 0) {
				y += h;
			}
			pose.pushPose();
			pose.translate(Mth.cos(ang) * rr, y * k, Mth.sin(ang) * rr);
			pose.mulPose(Axis.YP.rotation(-ang));
			pose.mulPose(Axis.XP.rotation(age * 0.3f + i));
			float s = 0.025f + (i % 3) * 0.012f;
			IceDraw.box(pose, glow, -s, -s, -0.005f, s, s, 0.005f, 1.0f, ((int) (230 * k) << 24) | 0xFFFFFF, LightTexture.FULL_BRIGHT);
			pose.popPose();
		}
		// the churning cloud over it
		VertexConsumer cloud = buffers.getBuffer(RenderType.entityTranslucent(IceDraw.WHITE));
		for (int i = 0; i < 9; i++) {
			float ang = i * Mth.TWO_PI / 9 + age * 0.02f;
			float cr = i == 0 ? 0 : r * 0.62f;
			float cx = i == 0 ? 0 : Mth.cos(ang) * cr;
			float cz = i == 0 ? 0 : Mth.sin(ang) * cr;
			float w = r * (i == 0 ? 0.8f : 0.5f) * (0.9f + 0.1f * Mth.sin(age * 0.07f + i));
			float bob = Mth.sin(age * 0.05f + i * 1.7f) * 0.2f;
			pose.pushPose();
			pose.translate(cx, h * k + bob, cz);
			pose.mulPose(Axis.YP.rotation(ang * 0.5f));
			int shade = i % 2 == 0 ? 0xC9D3DC : 0xDDE5EC;
			IceDraw.box(pose, cloud, -w, -0.45f, -w, w, 0.55f + (i % 3) * 0.2f, w, 8.0f, ((int) (215 * k) << 24) | shade, lit);
			pose.popPose();
		}
		// the drift on the ground
		VertexConsumer drift = buffers.getBuffer(RenderType.entityTranslucent(SNOW_BLOCK));
		float dr = r * Math.min(1.0f, age / 40.0f);
		PoseStack.Pose p = pose.last();
		int segs = 24;
		int argb = ((int) (200 * out) << 24) | 0xFFFFFF;
		for (int i = 0; i < segs; i++) {
			float a0 = i / (float) segs * Mth.TWO_PI;
			float a1 = (i + 1) / (float) segs * Mth.TWO_PI;
			float y = 0.06f;
			float x0 = Mth.cos(a0) * dr;
			float z0 = Mth.sin(a0) * dr;
			float x1 = Mth.cos(a1) * dr;
			float z1 = Mth.sin(a1) * dr;
			drift.addVertex(p, 0, y + 0.08f, 0).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0, 1, 0);
			drift.addVertex(p, 0, y + 0.08f, 0).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0, 1, 0);
			drift.addVertex(p, x1, y, z1).setColor(argb).setUv(x1 * 0.5f, z1 * 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0, 1, 0);
			drift.addVertex(p, x0, y, z0).setColor(argb).setUv(x0 * 0.5f, z0 * 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(p, 0, 1, 0);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(CryoFieldEntity entity) {
		return IceDraw.ICE;
	}
}
