package com.projecthero.mod.client.titanshifter;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.entity.TitanLimbChunkEntity;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * v0.16.7 (user, Titan fixes): draws a severed Titan limb ({@link TitanLimbChunkEntity}) -- only that arm's or leg's bones
 * of the Titan's own model, at the Titan's scale, in their rest pose. It leaves the joint hanging as it hung on the Titan
 * and tumbles end over end through the air; on landing it slews over onto its side (with a little overshoot, so it lands
 * heavily) and lies there. When the dissolve starts the pixels that go fly off on one steady wind per chunk in their own
 * colour, like the corpse's (same size and rate, see {@link TitanCorpseRenderer#DUST_PER_TICK}).
 */
public class TitanLimbChunkRenderer extends GeoEntityRenderer<TitanLimbChunkEntity> {
	/** Settling onto its side after landing (ticks). */
	private static final float SETTLE_TICKS = 7.0f;

	private boolean mainPass;
	private TitanTexels tx;
	private TitanFx.State fx;
	private NativeImage colours;
	private int limbBit;
	private double windX;
	private double windZ;
	private Vec3 centre = Vec3.ZERO;

	public TitanLimbChunkRenderer(EntityRendererProvider.Context context) {
		super(context, new TitanLimbChunkModel());
		this.shadowRadius = 0.0f;
	}

	@Override
	public void preRender(PoseStack poseStack, TitanLimbChunkEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (isReRender) {
			return;
		}
		mainPass = true;
		TitanType type = animatable.titanType();
		tx = TitanTexels.get(type.modelName());
		fx = TitanFx.state(animatable);
		TitanFx.fxTick(animatable);
		limbBit = animatable.limb();
		colours = TitanSkinFrames.source(type.texture(), animatable.isEmergency());
		long seed = animatable.getUUID().getLeastSignificantBits() ^ animatable.getUUID().getMostSignificantBits();
		double ang = (seed & 0xFFFF) / 65536.0 * Math.PI * 2.0;
		windX = Math.cos(ang);
		windZ = Math.sin(ang);
		centre = animatable.position().add(0.0, animatable.getBbHeight() * 0.5, 0.0);
		float d = animatable.dissolveProgress(partialTick);
		Minecraft mc = Minecraft.getInstance();
		if (tx != null && d > 0.0f && !mc.isPaused()) {
			int slot = TitanTexels.limbSlot(limbBit);
			float t = TitanLimbChunkModel.limbTime(tx, limbBit, d);
			float last = fx.lastDissolve < 0.0f || fx.lastDissolve > t ? t : fx.lastDissolve;
			float span = Math.max(1, animatable.life() - 10 - animatable.dissolveStart());
			float ticks = (t - last) / Math.max(1.0e-4f, tx.limbDissolveMax[slot] - tx.limbDissolveMin[slot]) * span;
			fx.window.prepare(tx, tx.limbByDissolve[slot], tx.limbDissolveSorted[slot], last, t,
					Math.round(TitanCorpseRenderer.DUST_PER_TICK * 0.6f * ticks * TitanFx.budget() + 0.5f));
			fx.lastDissolve = t;
		} else {
			fx.window.clear();
		}
	}

	/** The tumble, the landing and the limb centred on the chunk (replaces the usual entity yaw). */
	@Override
	protected void applyRotations(TitanLimbChunkEntity e, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
			float nativeScale) {
		TitanType type = e.titanType();
		TitanTexels t = TitanTexels.get(type.modelName());
		float scale = e.titanHeight() / type.modelHeight();
		float age = e.chunkAge(partialTick);
		long seed = e.getUUID().getMostSignificantBits();
		float r1 = (seed & 0xFF) / 255.0f, r2 = (seed >> 8 & 0xFF) / 255.0f, r3 = (seed >> 16 & 0xFF) / 255.0f;
		float baseYaw = 180.0f - e.getYRot();
		// tumbling end over end about a mostly horizontal axis, faster for a smaller body
		double th = r1 * Math.PI * 2.0;
		Vector3f axis = new Vector3f((float) Math.cos(th), (r2 - 0.5f) * 0.6f, (float) Math.sin(th)).normalize();
		float rate = (float) Math.toRadians(11.0f + 6.0f * r3) * (float) Math.sqrt(15.0f / Math.max(4.0f, e.titanHeight()));
		int landed = e.landedAt();
		float flyAge = landed < 0 ? age : Math.min(age, landed);
		Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(baseYaw)).mul(new Quaternionf().rotateAxis(rate * flyAge, axis));
		int slot = t == null ? 0 : TitanTexels.limbSlot(e.limb());
		float thick = t == null ? 4.0f : t.limbSize[slot][2];
		float lift = e.getBbHeight() * 0.5f;
		if (landed >= 0) {
			// on its side, along a direction of its own
			Quaternionf rest = new Quaternionf().rotateY((float) Math.toRadians(baseYaw + (r2 - 0.5f) * 120.0f))
					.rotateX((float) Math.toRadians(r1 < 0.5f ? 90.0f : -90.0f));
			float k = Math.min(1.0f, (age - landed) / SETTLE_TICKS);
			// ease out with a slight overshoot: it lands heavily and rocks back
			float c = 1.6f;
			float s = 1.0f + (c + 1.0f) * (float) Math.pow(k - 1.0f, 3) + c * (float) Math.pow(k - 1.0f, 2);
			q.slerp(rest, Math.max(0.0f, s));
			float lying = thick * 0.5f * scale / 16.0f + 0.02f;
			lift = lift + (lying - lift) * Math.min(1.0f, k);
		}
		poseStack.translate(0.0f, lift, 0.0f);
		poseStack.mulPose(q);
		poseStack.scale(scale, scale, scale);
		if (t != null) {
			float[] c = t.limbCentre[slot];
			poseStack.translate(c[0] / 16.0f, -c[1] / 16.0f, -c[2] / 16.0f);
		}
	}

	@Override
	public void applyRenderLayers(PoseStack poseStack, TitanLimbChunkEntity animatable, BakedGeoModel model, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		mainPass = false;
		super.applyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		if (TitanTexels.limbBitOf(bone.getName()) != limbBit) {
			return; // only the severed limb itself (its parents still carry the pose down to it)
		}
		super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
		if (!mainPass || tx == null || fx == null || Minecraft.getInstance().isPaused()) {
			return;
		}
		Integer idx = tx.boneIndex.get(bone.getName());
		ClientLevel level = Minecraft.getInstance().level;
		IntArrayList list = idx == null ? null : fx.window.of(idx);
		if (list == null || level == null || colours == null) {
			return;
		}
		Matrix4f pose = poseStack.last().pose();
		RandomSource r = level.random;
		Minecraft mc = Minecraft.getInstance();
		for (int k = 0; k < list.size(); k++) {
			int t = list.getInt(k);
			int pix = tx.index[t];
			int abgr = colours.getPixelRGBA(pix % tx.w, pix / tx.w);
			if ((abgr >>> 24) == 0) {
				continue;
			}
			Vec3 at = TitanFx.world(pose, tx.px[t], tx.py[t], tx.pz[t]);
			Vec3 out = at.subtract(centre);
			double len = Math.max(0.01, out.length());
			double push = 0.015 + r.nextDouble() * 0.025;
			double vx = out.x / len * push + windX * 0.04, vz = out.z / len * push + windZ * 0.04;
			double vy = 0.02 + r.nextDouble() * 0.03;
			float cr = (abgr & 0xFF) / 255.0f, cg = (abgr >> 8 & 0xFF) / 255.0f, cb = (abgr >> 16 & 0xFF) / 255.0f;
			float size = TitanCorpseRenderer.DUST_SIZE + r.nextFloat() * TitanCorpseRenderer.DUST_SIZE;
			mc.particleEngine.add(new TitanDustParticle(level, at.x, at.y, at.z, vx, vy, vz, cr, cg, cb, windX, windZ, size, 40 + r.nextInt(40)));
		}
	}
}
