package com.projecthero.mod.client.titanshifter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders a Titan form through GeckoLib. In first person the rider's own Titan hides its head (the camera sits inside
 * it) so the view is clear; everyone else, and the rider in third person, see the whole Titan.
 * <p>
 * v0.16.7 (user, Titan revamp): every body is three layers (skeleton, muscle, skin) of the same rig. During the shift the
 * texture builds texel by texel ({@link TitanFormModel}) and this renderer throws yellow sparks off each new bone texel
 * and steam off the forming flesh. A severed limb ({@link TitanFormEntity#severedLimbs()}) is not drawn; a red,
 * steaming stump is. When the bit clears, the limb regrows over {@link TitanFx#REGROW_TICKS}: it scales up from the
 * joint while its bone, muscle and skin come back texel by texel. The corpse-only nape flaps are never drawn here.
 */
public class TitanFormRenderer extends GeoEntityRenderer<TitanFormEntity> {
	private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.2f), 0.9f);
	private static final String[] LIMBS = { "right_arm", "left_arm", "right_leg", "left_leg" };
	private static final String[] STUMPS = { "right_arm_stump", "left_arm_stump", "right_leg_stump", "left_leg_stump" };

	private boolean mainPass;
	private TitanTexels tx;
	private TitanFx.State fx;
	private float[] regrow = new float[] { Float.NaN, Float.NaN, Float.NaN, Float.NaN };
	private int severed;
	private boolean building;
	private boolean rage;
	/** v0.16.7 (user, Titan fixes): the Emergency Titan's rushed build data (null for a normal Titan). */
	private TitanTexels.Emergency em;
	private float buildP;

	public TitanFormRenderer(EntityRendererProvider.Context context) {
		super(context, new TitanFormModel());
		this.shadowRadius = 3.0f;
		addRenderLayer(new TitanGlowLayer<>(this));
	}

	@Override
	public void preRender(PoseStack poseStack, TitanFormEntity animatable, BakedGeoModel model,
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
		Minecraft mc = Minecraft.getInstance();
		boolean firstPersonRider = mc.options.getCameraType().isFirstPerson() && mc.player != null
				&& mc.player.getVehicle() == animatable;
		model.getBone("head").ifPresent(bone -> bone.setHidden(firstPersonRider));

		mainPass = true;
		// v0.16.7: lock the footstep clock to the clip GeckoLib is actually playing
		var manager = animatable.getAnimatableInstanceCache().getManagerForId(animatable.getId());
		var main = manager == null ? null : manager.getAnimationControllers().get("main");
		var current = main == null ? null : main.getCurrentAnimation();
		TitanFootstepsClient.observe(animatable, current == null ? null : current.animation().name());
		tx = TitanTexels.get(animatable.titanType().modelName());
		fx = TitanFx.state(animatable);
		TitanFx.fxTick(animatable);
		severed = animatable.severedLimbs();
		rage = animatable.hasMoveFlag(TitanFormEntity.FLAG_RAGING);
		regrow = TitanFx.regrowProgress(animatable, partialTick);
		float p = TitanFx.buildProgress(animatable, partialTick);
		building = p >= 0.0f && p < 1.0f;
		buildP = p;
		em = animatable.isEmergency() && tx != null ? tx.emergency() : null;
		if (building && em != null) {
			shudder(poseStack, animatable, p, partialTick);
		}
		if (building && tx != null && !mc.isPaused()) {
			float last = fx.lastBuild < 0.0f || fx.lastBuild > p ? Math.max(0.0f, p - 0.01f) : fx.lastBuild;
			float ticks = (p - last) * Math.max(1, animatable.formStateTicks());
			if (em != null) {
				// the rushed build throws more: ~7 a tick, in its own (flickering, blotchy) order
				fx.window.prepare(tx, em.byAppear, em.appearSorted, last, p, Math.round(7.0f * ticks * TitanFx.budget() + 0.5f));
			} else {
				// ~4 particles a tick at All
				fx.window.prepare(tx, tx.byBuild, tx.buildSorted, last, p, Math.round(4.0f * ticks * TitanFx.budget() + 0.5f));
			}
			fx.lastBuild = p;
		} else {
			fx.window.clear();
			fx.lastBuild = -1.0f;
		}
	}

	/**
	 * v0.16.7 (user, Titan fixes): the Emergency Titan shudders while it forms -- a constant tremor and, every few ticks, a
	 * violent jolt as the body convulses (strongest early, settling as the build ends).
	 */
	private static void shudder(PoseStack poseStack, TitanFormEntity e, float p, float partialTick) {
		int t = e.tickCount;
		float calm = 1.0f - 0.7f * p;
		float tremor = 0.035f * calm;
		float jx = (com.projecthero.mod.titanshifter.TitanEmergencyBuild.hash01(t * 3 + e.getId()) - 0.5f) * 2.0f;
		float jz = (com.projecthero.mod.titanshifter.TitanEmergencyBuild.hash01(t * 7 + 11 + e.getId()) - 0.5f) * 2.0f;
		float jolt = com.projecthero.mod.titanshifter.TitanEmergencyBuild.hash01((t / 3) * 13 + e.getId()) < 0.3f ? 1.0f : 0.0f;
		float roll = (com.projecthero.mod.titanshifter.TitanEmergencyBuild.hash01((t / 3) * 29 + 5 + e.getId()) - 0.5f) * 7.0f * jolt * calm;
		poseStack.translate(jx * tremor, 0.0f, jz * tremor);
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(roll + jx * 0.6f * calm));
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(jz * 0.6f * calm + jolt * calm * 2.5f));
	}

	/** v0.16.7: Rage flushes the whole body red. */
	@Override
	public software.bernie.geckolib.util.Color getRenderColor(TitanFormEntity animatable, float partialTick, int packedLight) {
		software.bernie.geckolib.util.Color base = super.getRenderColor(animatable, partialTick, packedLight);
		if (!animatable.hasMoveFlag(TitanFormEntity.FLAG_RAGING)) {
			return base;
		}
		float pulse = 0.5f + 0.5f * (float) Math.sin((animatable.tickCount + partialTick) * 0.35f);
		return software.bernie.geckolib.util.Color.ofRGBA(1.0f, 0.74f - 0.08f * pulse, 0.68f - 0.08f * pulse, base.getAlphaFloat());
	}

	@Override
	public void applyRenderLayers(PoseStack poseStack, TitanFormEntity animatable, BakedGeoModel model, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		mainPass = false;
		super.applyRenderLayers(poseStack, animatable, model, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
	}

	@Override
	public void renderRecursively(PoseStack poseStack, TitanFormEntity animatable, GeoBone bone, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour) {
		String name = bone.getName();
		if (name.startsWith("nape_flap")) {
			return; // only the corpse has its nape torn open
		}
		if (name.startsWith("crystal_") && !animatable.isCrystal()) {
			return; // the Female Titan's crystal fists / shins / nape, only while Crystal Fists runs
		}
		if (name.equals("boulder")) {
			// v0.16.7 (user): the Beast's boulder / handful of stones, only while it is in the hand -- the same block-built
			// rock models as the flying ones (TitanRockRenderer), centred on the bone's cube so they follow the clip
			boolean big = animatable.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_BOULDER);
			if (mainPass && !isReRender && (big || animatable.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_ROCKS))) {
				renderHeldRock(poseStack, animatable, bone, bufferSource, packedLight, big);
			}
			return;
		}
		for (int slot = 0; slot < 4; slot++) {
			if (name.equals(STUMPS[slot])) {
				boolean show = (severed & (1 << slot)) != 0 || (!Float.isNaN(regrow[slot]) && regrow[slot] < 0.35f);
				if (show) {
					super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
							packedLight, packedOverlay, colour);
				}
				return;
			}
			if (name.equals(LIMBS[slot])) {
				if ((severed & (1 << slot)) != 0) {
					return; // gone
				}
				float p = regrow[slot];
				if (!Float.isNaN(p)) {
					// grows back out of the joint
					float e = 1.0f - (1.0f - p) * (1.0f - p);
					float g = 0.12f + 0.88f * e;
					float sx = bone.getScaleX(), sy = bone.getScaleY(), sz = bone.getScaleZ();
					bone.updateScale(sx * g, sy * g, sz * g);
					super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
							packedLight, packedOverlay, colour);
					bone.updateScale(sx, sy, sz);
					return;
				}
			}
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, colour);
	}

	/**
	 * v0.16.7 (user): the rock in the Beast's right hand, drawn from block models at the {@code boulder} bone's cube (its
	 * centre and size, read from the baked vertices): a boulder, or a smaller handful of stones. The stone is the ground the
	 * Titan stands on, as the server picks it ({@code TitanBoulders#groundState}). Block models go to the fixed block-sheet
	 * buffers, so the Titan's own buffer is not interrupted.
	 */
	private void renderHeldRock(PoseStack poseStack, TitanFormEntity form, GeoBone bone, MultiBufferSource buffers, int light, boolean big) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (var cube : bone.getCubes()) {
			for (var quad : cube.quads()) {
				if (quad == null) {
					continue;
				}
				for (var v : quad.vertices()) {
					Vector3f p = v.position();
					minX = Math.min(minX, p.x);
					minY = Math.min(minY, p.y);
					minZ = Math.min(minZ, p.z);
					maxX = Math.max(maxX, p.x);
					maxY = Math.max(maxY, p.y);
					maxZ = Math.max(maxZ, p.z);
				}
			}
		}
		if (minX > maxX) {
			return;
		}
		float size = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
		poseStack.pushPose();
		software.bernie.geckolib.util.RenderUtil.prepMatrixForBone(poseStack, bone);
		poseStack.translate((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f);
		var state = heldStone(form);
		if (big) {
			TitanRockRenderer.boulder(poseStack, buffers, light, state, form.getId(), size * 1.6f);
		} else {
			TitanRockRenderer.handful(poseStack, buffers, light, state, form.getId(), size * 1.5f);
		}
		poseStack.popPose();
	}

	/** The ground under the Titan when it is plain natural stuff, else cobblestone (mirrors TitanBoulders#groundState). */
	private static net.minecraft.world.level.block.state.BlockState heldStone(TitanFormEntity form) {
		var level = form.level();
		var below = net.minecraft.core.BlockPos.containing(form.getX(), form.getY() - 0.5, form.getZ());
		var s = level.getBlockState(below);
		if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || !s.isCollisionShapeFullBlock(level, below)) {
			return net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
		}
		float h = s.getDestroySpeed(level, below);
		return h >= 0.3f && h <= 3.0f ? s : net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
		if (!mainPass || tx == null || fx == null || bone.isHidden() || Minecraft.getInstance().isPaused()) {
			return;
		}
		Integer idx = tx.boneIndex.get(bone.getName());
		if (idx == null) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Matrix4f pose = poseStack.last().pose();
		RandomSource r = level.random;
		IntArrayList list = fx.window.of(idx);
		if (list != null) {
			for (int k = 0; k < list.size(); k++) {
				int t = list.getInt(k);
				Vec3 at = TitanFx.world(pose, tx.px[t], tx.py[t], tx.pz[t]);
				if (em != null && tx.layer[t] == 0) {
					// the rushed skeleton: arcs jumping off each bone, faster and wilder
					for (int s = 0; s < 2; s++) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.3) * 0.5,
								(r.nextDouble() - 0.5) * 0.7);
					}
					if (r.nextInt(2) == 0) {
						level.addParticle(SPARK, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.1, 0.08, (r.nextDouble() - 0.5) * 0.1);
					}
				} else if (em != null && tx.layer[t] == 1 && r.nextInt(3) == 0) {
					// raw flesh hisses as it lands
					level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.06, 0.07 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.06);
				} else if (tx.layer[t] == 0) {
					// lightning crawling over each new bone
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
					if (r.nextInt(3) == 0) {
						level.addParticle(SPARK, at.x, at.y, at.z, 0.0, 0.05, 0.0);
					}
				} else if (r.nextInt(tx.layer[t] == 1 ? 2 : 4) == 0) {
					// the flesh steams as it forms
					level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.04, 0.05 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.04);
				}
			}
		}
		// v0.16.7 (user, Titan fixes): the Emergency Titan's unformed skin patches vent steam (from 70% through the build on)
		if (em != null && fx.fxTick && (!building || buildP > 0.7f) && em.vents[idx] != null) {
			for (int v : em.vents[idx]) {
				if (r.nextFloat() < 0.12f * TitanFx.budget() + 0.03f) {
					Vec3 at = TitanFx.world(pose, tx.px[v], tx.py[v], tx.pz[v]);
					level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.04, 0.06 + r.nextDouble() * 0.06,
							(r.nextDouble() - 0.5) * 0.04);
					if (r.nextInt(6) == 0) {
						level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 0.0, 0.03, 0.0);
					}
				}
			}
		}
		// a severed limb's stump (or a regrowing one) steams
		if (fx.fxTick && bone.getName().endsWith("_stump") && level.getGameTime() % 3 == 0) {
			float[] a = tx.anchors.get(bone.getName());
			if (a != null) {
				Vec3 at = TitanFx.world(pose, a[1], a[2], a[3]);
				level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.06, 0.08, (r.nextDouble() - 0.5) * 0.06);
				if (r.nextInt(3) == 0) {
					level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 0.0, 0.04, 0.0);
				}
				if (r.nextInt(4) == 0) {
					level.addParticle(new DustParticleOptions(new Vector3f(0.6f, 0.05f, 0.05f), 1.6f), at.x, at.y - 0.3, at.z, 0.0, -0.1, 0.0);
				}
			}
		}
		if (fx.fxTick && rage && bone.getName().equals("body")) {
			// Rage: steam boiling off the shoulders and back, flecked red
			for (float sx : new float[] { -5.0f, 5.0f }) {
				Vec3 at = TitanFx.world(pose, sx, 23.0f, 2.0f);
				level.addParticle(ParticleTypes.CLOUD, at.x + (r.nextDouble() - 0.5) * 0.8, at.y, at.z + (r.nextDouble() - 0.5) * 0.8, 0.0, 0.12, 0.0);
			}
			if (r.nextInt(2) == 0) {
				Vec3 at = TitanFx.world(pose, (r.nextFloat() - 0.5f) * 8.0f, 14.0f + r.nextFloat() * 10.0f, 2.5f);
				level.addParticle(new DustParticleOptions(new Vector3f(0.85f, 0.1f, 0.08f), 2.0f), at.x, at.y, at.z, 0.0, 0.05, 0.0);
			}
		}
		if (fx.fxTick && bone.getName().startsWith("crystal_") && r.nextInt(4) == 0) {
			Vec3 at = TitanFx.world(pose, -bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
			level.addParticle(ParticleTypes.END_ROD, at.x + (r.nextDouble() - 0.5), at.y + r.nextDouble(), at.z + (r.nextDouble() - 0.5), 0.0, 0.02, 0.0);
		}
		if (fx.fxTick && building && bone.getName().equals("body") && level.getGameTime() % 2 == 0) {
			// steam pouring off the whole forming body
			Vec3 at = TitanFx.world(pose, 0.0f, 20.0f, 0.0f);
			level.addParticle(ParticleTypes.CLOUD, at.x + (r.nextDouble() - 0.5) * 3, at.y + (r.nextDouble() - 0.5) * 4, at.z + (r.nextDouble() - 0.5) * 3, 0.0, 0.08, 0.0);
		}
	}
}
