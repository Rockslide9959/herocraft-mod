package com.projecthero.mod.client.titanshifter;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.entity.TitanCorpseEntity;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Titan's abandoned body (v0.16.7, user): it sinks to its knees and bows its head over the ground, the nape
 * torn open where the shifter climbed out and steaming, then it dissolves -- skin, muscle, skeleton -- and the pixels do
 * not just vanish: each frame, the texels that just went (sampled down to the particle budget) fly off the posed body in
 * their own colour on one steady wind per corpse, lifting a little and fading as they drift away.
 */
public class TitanCorpseRenderer extends GeoEntityRenderer<TitanCorpseEntity> {
	/**
	 * Dust pixels per tick at the All particle setting (Decreased 40%, Minimal 10%). v0.16.7 (user, Titan fixes): half the
	 * pixels (30 -> 15) at 30% of their old size, so the dust no longer hides the dissolve itself -- you see the skin, then
	 * the flesh, then the bone coming away pixel by pixel. Shared by the severed-limb chunks.
	 */
	public static float DUST_PER_TICK = 15.0f; // not final: a debug / comparison capture may set the old 30
	/** The dust pixels' size: {@code DUST_SIZE * (1 + random)} blocks (was 0.07). */
	public static float DUST_SIZE = 0.021f; // (old: 0.07)

	private boolean mainPass;
	private TitanTexels tx;
	private TitanFx.State fx;
	private NativeImage colours;
	private double windX;
	private double windZ;
	private float dissolve;
	private Vec3 centre = Vec3.ZERO;

	public TitanCorpseRenderer(EntityRendererProvider.Context context) {
		super(context, new TitanCorpseModel());
		this.shadowRadius = 0.0f;
	}

	@Override
	public void preRender(PoseStack poseStack, TitanCorpseEntity animatable, BakedGeoModel model,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
				packedOverlay, colour);
		if (isReRender) {
			return;
		}
		float scale = animatable.getBbHeight() / animatable.titanType().modelHeight();
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
		// the baked model is shared with the living Titans (whose first-person rider hides the head)
		model.getBone("head").ifPresent(b -> b.setHidden(false));
		mainPass = true;
		TitanType type = animatable.titanType();
		tx = TitanTexels.get(type.modelName());
		fx = TitanFx.state(animatable);
		TitanFx.fxTick(animatable);
		colours = TitanSkinFrames.source(type.corpseTexture(), animatable.isEmergency());
		// one steady wind per corpse
		long seed = animatable.getUUID().getLeastSignificantBits() ^ animatable.getUUID().getMostSignificantBits();
		double ang = (seed & 0xFFFF) / 65536.0 * Math.PI * 2.0;
		windX = Math.cos(ang);
		windZ = Math.sin(ang);
		centre = animatable.position().add(0.0, animatable.getBbHeight() * 0.3, 0.0);
		dissolve = TitanFx.dissolveProgress(animatable, partialTick);
		Minecraft mc = Minecraft.getInstance();
		if (tx != null && dissolve > 0.0f && !mc.isPaused()) {
			float last = fx.lastDissolve < 0.0f || fx.lastDissolve > dissolve ? dissolve : fx.lastDissolve;
			float span = Math.max(1, TitanCorpseEntity.dissolveTicks() - 10 - TitanFx.KNEEL_TICKS);
			float ticks = (dissolve - last) * span;
			fx.window.prepare(tx, tx.byDissolve, tx.dissolveSorted, last, dissolve, Math.round(DUST_PER_TICK * ticks * TitanFx.budget() + 0.5f));
			fx.lastDissolve = dissolve;
		} else {
			fx.window.clear();
		}
	}

	@Override
	public void applyRenderLayers(PoseStack poseStack, TitanCorpseEntity animatable, BakedGeoModel model, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		mainPass = false;
		super.applyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
	}

	@Override
	public void renderRecursively(PoseStack poseStack, TitanCorpseEntity animatable, GeoBone bone, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour) {
		if (bone.getName().endsWith("_stump") || bone.getName().startsWith("crystal_") || bone.getName().equals("boulder")) {
			return; // stumps belong to a living Titan's severed limbs
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, colour);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
		if (!mainPass || tx == null || fx == null || bone.isHidden() || Minecraft.getInstance().isPaused()) {
			return;
		}
		Integer idx = tx.boneIndex.get(bone.getName());
		ClientLevel level = Minecraft.getInstance().level;
		if (idx == null || level == null) {
			return;
		}
		Matrix4f pose = poseStack.last().pose();
		RandomSource r = level.random;
		IntArrayList list = fx.window.of(idx);
		if (list != null && colours != null) {
			Minecraft mc = Minecraft.getInstance();
			for (int k = 0; k < list.size(); k++) {
				int t = list.getInt(k);
				int pix = tx.index[t];
				int abgr = colours.getPixelRGBA(pix % tx.w, pix / tx.w);
				if ((abgr >>> 24) == 0) {
					continue; // the open nape: nothing there to blow away
				}
				Vec3 at = TitanFx.world(pose, tx.px[t], tx.py[t], tx.pz[t]);
				Vec3 out = at.subtract(centre);
				double len = Math.max(0.01, out.length());
				double push = 0.02 + r.nextDouble() * 0.03;
				double vx = out.x / len * push + windX * 0.04, vz = out.z / len * push + windZ * 0.04;
				double vy = 0.015 + r.nextDouble() * 0.03;
				float cr = (abgr & 0xFF) / 255.0f, cg = (abgr >> 8 & 0xFF) / 255.0f, cb = (abgr >> 16 & 0xFF) / 255.0f;
				float size = DUST_SIZE + r.nextFloat() * DUST_SIZE;
				mc.particleEngine.add(new TitanDustParticle(level, at.x, at.y, at.z, vx, vy, vz, cr, cg, cb, windX, windZ, size,
						45 + r.nextInt(45)));
			}
		}
		// the open nape steams until the flesh round it is gone
		if (fx.fxTick && bone.getName().equals("body") && dissolve < 0.62f) {
			float[] a = tx.anchors.get("nape");
			if (a != null) {
				Vec3 at = TitanFx.world(pose, a[1], a[2], a[3]);
				float k = 1.0f - dissolve / 0.62f;
				if (r.nextFloat() < 0.9f * k + 0.1f) {
					level.addParticle(ParticleTypes.CLOUD, at.x + (r.nextDouble() - 0.5) * 0.6, at.y, at.z + (r.nextDouble() - 0.5) * 0.6,
							windX * 0.02, 0.09 + r.nextDouble() * 0.05, windZ * 0.02);
				}
				if (r.nextInt(5) == 0) {
					level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, windX * 0.01, 0.05, windZ * 0.01);
				}
			}
		}
	}
}
