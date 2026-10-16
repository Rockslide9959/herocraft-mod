package com.projecthero.mod.client.mutation.cryo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p09.FrostShellEntity;
import com.projecthero.mod.hero.power.p09.FrostStacks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.5 Cryokinesis: what a frosted creature looks like.
 * <ul>
 * <li><b>Frost</b> -- five ice-crystal pips circling over its head, the earned ones bright and spinning, the rest faint
 * outlines, so you can read how close it is to freezing;</li>
 * <li><b>frozen solid</b> -- a block of clear ice around its whole body that snaps shut (grows in over 3 ticks), crusted
 * with icicles on its top edges, and shivers in its last half second before it cracks; Ice Prison's block is thicker,
 * of packed ice with blue-ice corner posts.</li>
 * </ul>
 * Drawn at the target's own interpolated position. Your own ice is not drawn over your camera in first person.
 */
public class FrostShellRenderer extends EntityRenderer<FrostShellEntity> {
	public FrostShellRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(FrostShellEntity e, Frustum frustum, double x, double y, double z) {
		Entity t = e.level().getEntity(e.targetId());
		return t != null && frustum.isVisible(t.getBoundingBox().inflate(1.0));
	}

	@Override
	public void render(FrostShellEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Entity te = e.level().getEntity(e.targetId());
		if (!(te instanceof LivingEntity t) || !t.isAlive()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean ownCamera = t == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
		double tx = Mth.lerp(partial, t.xo, t.getX()) - Mth.lerp(partial, e.xo, e.getX());
		double ty = Mth.lerp(partial, t.yo, t.getY()) - Mth.lerp(partial, e.yo, e.getY());
		double tz = Mth.lerp(partial, t.zo, t.getZ()) - Mth.lerp(partial, e.zo, e.getZ());
		pose.pushPose();
		pose.translate(tx, ty, tz);
		float age = e.freezeAge(partial);
		if (age >= 0 && age < e.freezeLength() + 2) {
			if (!ownCamera) {
				iceBlock(t, e, age, pose, buffers, light);
			}
		} else if (e.stacks() > 0 && !ownCamera) {
			pips(t, e.stacks(), e.tickCount + partial, pose, buffers);
		}
		pose.popPose();
	}

	/** The Frost indicator: {@link FrostStacks#MAX} crystal slots over the head. */
	private static void pips(LivingEntity t, int stacks, float time, PoseStack pose, MultiBufferSource buffers) {
		float y = t.getBbHeight() + 0.35f;
		float r = Math.max(0.32f, t.getBbWidth() * 0.45f);
		pose.pushPose();
		pose.translate(0, y, 0);
		pose.mulPose(Axis.YP.rotationDegrees(time * 3.0f));
		for (int i = 0; i < FrostStacks.MAX; i++) {
			boolean lit = i < stacks;
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 72.0f));
			pose.translate(r, lit ? Mth.sin(time * 0.25f + i) * 0.03f : 0, 0);
			pose.mulPose(Axis.YP.rotationDegrees(time * (lit ? 9 : 0)));
			float s = lit ? 1.0f : 0.7f;
			pose.scale(s, s, s);
			pose.translate(0, -0.11, 0);
			IceDraw.shard(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.ICE)), 0.12f, 0.3f, lit ? 0xF0E8FAFF : 0x50C8E8FF, LightTexture.FULL_BRIGHT);
			if (lit) {
				IceDraw.shard(pose, buffers.getBuffer(RenderType.eyes(IceDraw.WHITE)), 0.05f, 0.24f, 0xFF9EE2FF, LightTexture.FULL_BRIGHT);
			}
			pose.popPose();
		}
		pose.popPose();
	}

	private static void iceBlock(LivingEntity t, FrostShellEntity e, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		boolean prison = e.prison();
		int len = e.freezeLength();
		AABB bb = t.getBoundingBox();
		float hw = (float) (bb.getXsize() / 2) + (prison ? 0.28f : 0.18f);
		float hd = (float) (bb.getZsize() / 2) + (prison ? 0.28f : 0.18f);
		float h = (float) bb.getYsize() + (prison ? 0.32f : 0.2f);
		// snaps shut over 3 ticks; shivers in the last 10; cracks open on the last two
		float form = Mth.clamp(age / 3.0f, 0.0f, 1.0f);
		form = 1.0f - (1.0f - form) * (1.0f - form);
		float left = len - age;
		float shiver = left < 10 ? Mth.sin(age * 2.6f) * 0.025f * (1.0f - left / 10.0f) : 0.0f;
		float open = left < 2 ? Mth.clamp(1.0f - left / 2.0f, 0.0f, 1.0f) : 0.0f;
		float s = (0.55f + 0.45f * form) * (1.0f + open * 0.15f);
		int alpha = (int) ((prison ? 215 : 190) * (1.0f - open * 0.8f) * (0.4f + 0.6f * form));
		ResourceLocation tex = prison ? IceDraw.PACKED : IceDraw.ICE;
		int lit = LightTexture.pack(Math.max(LightTexture.block(light), 9), LightTexture.sky(light));
		pose.pushPose();
		pose.translate(shiver, 0, -shiver);
		pose.translate(0, h * 0.5f * (1 - s), 0);
		pose.scale(s, s, s);
		int col = (alpha << 24) | (prison ? 0xDCEBFF : 0xE6F6FF);
		IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), -hw, -0.05f, -hd, hw, h, hd, 1.0f, col, lit);
		// a second, inner skin gives the block depth
		IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), -hw + 0.08f, 0.02f, -hd + 0.08f, hw - 0.08f, h - 0.08f, hd - 0.08f, 1.0f,
				((alpha / 3) << 24) | 0xCDEBFF, lit);
		if (prison) {
			// blue-ice corner posts
			float p = 0.12f;
			for (int sx = -1; sx <= 1; sx += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					float cx = sx * hw;
					float cz = sz * hd;
					IceDraw.box(pose, buffers.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)), cx - p, -0.08f, cz - p, cx + p, h + 0.08f, cz + p, 0.5f, 0xF0FFFFFF, lit);
				}
			}
		}
		// icicles crusting the top edges (seeded per creature so they do not flicker)
		RandomSource rnd = RandomSource.create(t.getId() * 341873128712L);
		int n = 6 + (int) ((hw + hd) * 4);
		for (int i = 0; i < n; i++) {
			float edge = rnd.nextFloat();
			int side = rnd.nextInt(4);
			float x = side < 2 ? Mth.lerp(edge, -hw, hw) : (side == 2 ? -hw : hw);
			float z = side < 2 ? (side == 0 ? -hd : hd) : Mth.lerp(edge, -hd, hd);
			pose.pushPose();
			pose.translate(x * 0.92f, h - 0.05f, z * 0.92f);
			pose.mulPose(Axis.XP.rotation((z / Math.max(0.01f, hd)) * 0.45f));
			pose.mulPose(Axis.ZP.rotation(-(x / Math.max(0.01f, hw)) * 0.45f));
			float ch = (0.18f + rnd.nextFloat() * 0.32f) * form;
			IceDraw.crystal(pose, i % 3 == 0 ? buffers.getBuffer(RenderType.entityTranslucent(IceDraw.BLUE)) : buffers.getBuffer(RenderType.entityTranslucent(tex)), 0.1f + rnd.nextFloat() * 0.06f, ch, 0.0f, (alpha << 24) | 0xF4FBFF, lit);
			pose.popPose();
		}
		// hanging icicles along the bottom of the top rim
		for (int i = 0; i < 4; i++) {
			float x = (i % 2 == 0 ? -1 : 1) * hw * (0.4f + 0.15f * i);
			float z = (i < 2 ? -hd : hd);
			pose.pushPose();
			pose.translate(x, h * 0.75f, z);
			pose.mulPose(Axis.XP.rotationDegrees(180));
			IceDraw.crystal(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), 0.08f, 0.22f * form, 0.0f, (alpha << 24) | 0xF4FBFF, lit);
			pose.popPose();
		}
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(FrostShellEntity entity) {
		return IceDraw.ICE;
	}
}
