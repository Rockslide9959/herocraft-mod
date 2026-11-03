package com.projecthero.mod.client.doomsday;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doomsday.DoomsdayAdaptation;
import com.projecthero.mod.doomsday.entity.DoomsdayEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Doomsday -- the player rig (wide arms) in the user's HD skin (NovaSkin post 4845083152, "Batman The Devastator";
 * 128 x 128, so the standard UVs at twice the resolution), posed from his synced action. On top: his red eyes at full
 * brightness, bone spikes that grow longer and more numerous every life, his Calatonian burial cables while he is still
 * bound, grey stone flecks for every damage type he has adapted to, the heat-vision beam, and the Phantom Zone pane that
 * swallows him.
 */
public final class DoomsdayRenderer extends HumanoidMobRenderer<DoomsdayEntity, DoomsdayRenderer.Model> {
	static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/doomsday/doomsday.png");
	static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/doomsday/doomsday_glowmask.png");
	static final ResourceLocation STONE = ProjectHeroMod.id("textures/entity/doomsday/doomsday_stone.png");
	static final ResourceLocation BONE = ProjectHeroMod.id("textures/entity/doomsday/bone.png");
	static final ResourceLocation CABLE = ProjectHeroMod.id("textures/entity/doomsday/cable.png");

	public DoomsdayRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.6f);
		addLayer(new Stone(this));
		addLayer(new Glow(this));
		addLayer(new Spikes(this));
	}

	@Override
	public ResourceLocation getTextureLocation(DoomsdayEntity entity) {
		return SKIN;
	}

	@Override
	protected void scale(DoomsdayEntity e, PoseStack pose, float partialTick) {
		super.scale(e, pose, partialTick);
		pose.scale(0.9375f, 0.9375f, 0.9375f);
		if (e.action() == DoomsdayEntity.ACTION_BANISH) {
			// pulled up into the pane, shrinking to nothing
			float t = Mth.clamp((e.tickCount - e.actionStartTick + partialTick) / DoomsdayEntity.BANISH_TICKS, 0f, 1f);
			float s = 1f - t * t * (3 - 2 * t);
			pose.scale(Math.max(0.01f, s), Math.max(0.01f, s), Math.max(0.01f, s));
		}
	}

	@Override
	protected void setupRotations(DoomsdayEntity e, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
		super.setupRotations(e, pose, bob, yBodyRot, partialTick, scale);
		byte a = e.action();
		float elapsed = e.tickCount - e.actionStartTick + partialTick;
		float lie = 0f;
		if (a == DoomsdayEntity.ACTION_CORPSE || a == DoomsdayEntity.ACTION_BANISH) {
			lie = 1f;
		} else if (a == DoomsdayEntity.ACTION_RISE) {
			float t = Mth.clamp(elapsed / (DoomsdayEntity.RISE_TICKS * 0.75f), 0f, 1f);
			lie = 1f - t * t * (3 - 2 * t);
		}
		if (lie > 0f) {
			// face down, sprawled (the spine still twitches as he comes back)
			float twitch = a == DoomsdayEntity.ACTION_CORPSE && e.reviveFraction() > 0.5f
					? Mth.sin(elapsed * 1.7f) * 4f * (e.reviveFraction() - 0.5f) * 2f : 0f;
			pose.translate(0, 0.25f * lie, 0);
			pose.mulPose(Axis.XP.rotationDegrees(-90f * lie + twitch));
			pose.translate(0, -0.9f * lie, 0);
		}
	}

	@Override
	public void render(DoomsdayEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		super.render(e, yaw, partialTick, pose, buffers, light);
		float elapsed = e.tickCount - e.actionStartTick + partialTick;
		if (e.action() == DoomsdayEntity.ACTION_HEAT && elapsed >= DoomsdayEntity.HEAT_CHARGE && elapsed <= DoomsdayEntity.HEAT_END + 1) {
			heatBeam(e, partialTick, pose, buffers);
		}
		if (e.action() == DoomsdayEntity.ACTION_BANISH) {
			phantomPane(e, elapsed, pose, buffers);
		}
	}

	private void heatBeam(DoomsdayEntity e, float partialTick, PoseStack pose, MultiBufferSource buffers) {
		Vec3 target = e.beamTarget();
		if (target.lengthSqr() < 1.0e-4) {
			return;
		}
		Vec3 origin = e.getPosition(partialTick);
		float yawRad = -Mth.lerp(partialTick, e.yHeadRotO, e.yHeadRot) * Mth.DEG_TO_RAD;
		Vec3 eye = new Vec3(Math.sin(yawRad) * e.getBbWidth() * 0.3, e.getEyeHeight(), Math.cos(yawRad) * e.getBbWidth() * 0.3);
		Vec3 end = target.subtract(origin);
		Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(origin);
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		PoseStack.Pose last = pose.last();
		float flicker = 0.85f + 0.15f * Mth.sin((e.tickCount + partialTick) * 2.3f);
		for (int side = -1; side <= 1; side += 2) {
			// one beam from each eye
			Vec3 off = new Vec3(Math.cos(yawRad), 0, -Math.sin(yawRad)).scale(side * 0.09 * e.getScale());
			Vector3f a = eye.add(off).toVector3f();
			Vector3f b = end.add(off.scale(0.3)).toVector3f();
			Vector3f toCam = cam.subtract(eye).toVector3f();
			DoomsdayMesh.ribbon(vc, last, a, b, toCam, 0.32f * flicker, 0x70FF1A0A);
			DoomsdayMesh.ribbon(vc, last, a, b, toCam, 0.14f, 0xC0FF8040);
			DoomsdayMesh.ribbon(vc, last, a, b, toCam, 0.05f, 0xF0FFF0D0);
		}
	}

	private void phantomPane(DoomsdayEntity e, float elapsed, PoseStack pose, MultiBufferSource buffers) {
		float t = Mth.clamp(elapsed / DoomsdayEntity.BANISH_TICKS, 0f, 1f);
		float open = Mth.clamp(t * 3f, 0f, 1f) * (t > 0.85f ? (1f - t) / 0.15f : 1f);
		float size = 3.2f * open;
		if (size <= 0.01f) {
			return;
		}
		pose.pushPose();
		pose.translate(0, 1.6 + t * 1.5, 0);
		pose.mulPose(Axis.YP.rotationDegrees(elapsed * 6f));
		pose.mulPose(Axis.XP.rotationDegrees(70f));
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		PoseStack.Pose last = pose.last();
		float h = size;
		DoomsdayMesh.flat(vc, last, new Vector3f(-h, 0, -h), new Vector3f(-h, 0, h), new Vector3f(h, 0, h), new Vector3f(h, 0, -h), 0x50B8F4FF);
		DoomsdayMesh.flat(vc, last, new Vector3f(h, 0, -h), new Vector3f(h, 0, h), new Vector3f(-h, 0, h), new Vector3f(-h, 0, -h), 0x50B8F4FF);
		float w = 0.12f;
		int edge = 0xE0F0FFFF;
		Vector3f up = new Vector3f(0, 1, 0);
		DoomsdayMesh.ribbon(vc, last, new Vector3f(-h, 0, -h), new Vector3f(h, 0, -h), up, w, edge);
		DoomsdayMesh.ribbon(vc, last, new Vector3f(h, 0, -h), new Vector3f(h, 0, h), up, w, edge);
		DoomsdayMesh.ribbon(vc, last, new Vector3f(h, 0, h), new Vector3f(-h, 0, h), up, w, edge);
		DoomsdayMesh.ribbon(vc, last, new Vector3f(-h, 0, h), new Vector3f(-h, 0, -h), up, w, edge);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the model and its poses

	public static final class Model extends PlayerModel<DoomsdayEntity> {
		Model(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(DoomsdayEntity e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
			byte action = e.action();
			float el = age - e.actionStartTick;
			this.crouching = action == DoomsdayEntity.ACTION_LEAP_CROUCH || action == DoomsdayEntity.ACTION_LAND
					|| action == DoomsdayEntity.ACTION_SHATTER && el >= DoomsdayEntity.SHATTER_SLAM;
			body.z = 0f;
			head.z = 0f;
			super.setupAnim(e, limbSwing, limbSwingAmount, age, headYaw, headPitch);
			// a heavy, hunched brute: shoulders forward, arms hanging out from the body, breathing hard
			float breathe = Mth.sin(age * 0.12f);
			body.xRot += 0.18f;
			head.xRot += 0.1f;
			head.z = -1.2f;
			rightArm.zRot += 0.18f + breathe * 0.03f;
			leftArm.zRot -= 0.18f + breathe * 0.03f;
			rightArm.xRot += -0.15f;
			leftArm.xRot += -0.15f;
			head.zRot = 0f;
			body.zRot = 0f;
			switch (action) {
				case DoomsdayEntity.ACTION_HAMMER -> {
					// alternating overhead hammer blows, every 8 ticks
					float p = (el % 8f) / 8f;
					boolean right = ((int) (el / 8f)) % 2 == 0 || e.bound();
					ModelPart arm = right ? rightArm : leftArm;
					arm.xRot = p < 0.45f ? Mth.lerp(p / 0.45f, -0.6f, -3.0f) : Mth.lerp((p - 0.45f) / 0.55f, -3.0f, -0.5f);
					arm.zRot = right ? 0.1f : -0.1f;
					body.yRot = right ? 0.25f : -0.25f;
				}
				case DoomsdayEntity.ACTION_GUARD -> {
					rightArm.xRot = -1.6f + Mth.sin(age * 0.8f) * 0.2f;
					rightArm.yRot = -0.6f;
					rightArm.zRot = 0.6f;
				}
				case DoomsdayEntity.ACTION_LEAP_CROUCH -> {
					rightArm.xRot = 0.9f;
					leftArm.xRot = 0.9f;
					rightArm.zRot = 0.35f;
					leftArm.zRot = -0.35f;
					head.xRot = -0.3f;
				}
				case DoomsdayEntity.ACTION_LEAP_AIR -> {
					rightArm.xRot = -2.7f;
					leftArm.xRot = -2.7f;
					rightArm.zRot = 0.4f;
					leftArm.zRot = -0.4f;
					rightLeg.xRot = -0.8f;
					leftLeg.xRot = 0.3f;
				}
				case DoomsdayEntity.ACTION_LAND -> {
					rightArm.xRot = -0.35f;
					leftArm.xRot = -0.35f;
					rightArm.zRot = 0.5f;
					leftArm.zRot = -0.5f;
				}
				case DoomsdayEntity.ACTION_SHATTER -> {
					if (el < DoomsdayEntity.SHATTER_SLAM) {
						float t = Math.min(1f, el / 10f);
						rightArm.xRot = Mth.lerp(t, -0.4f, -3.1f);
						leftArm.xRot = Mth.lerp(t, -0.4f, -3.1f);
						rightArm.zRot = -0.25f;
						leftArm.zRot = 0.25f;
						head.xRot = -0.5f;
					} else {
						float t = Math.min(1f, (el - DoomsdayEntity.SHATTER_SLAM) / 3f);
						rightArm.xRot = Mth.lerp(t, -3.1f, -0.75f);
						leftArm.xRot = Mth.lerp(t, -3.1f, -0.75f);
						rightArm.zRot = 0.05f;
						leftArm.zRot = -0.05f;
					}
				}
				case DoomsdayEntity.ACTION_ERUPT -> {
					float t = Math.min(1f, el / DoomsdayEntity.ERUPT_BURST);
					rightArm.zRot = Mth.lerp(t, 0.2f, 1.25f);
					leftArm.zRot = -Mth.lerp(t, 0.2f, 1.25f);
					rightArm.xRot = -0.3f;
					leftArm.xRot = -0.3f;
					head.xRot = Mth.lerp(t, 0.1f, -0.6f);
					body.xRot = Mth.lerp(t, 0.18f, -0.1f);
					if (el < DoomsdayEntity.ERUPT_BURST) {
						body.zRot = Mth.sin(age * 2.1f) * 0.04f;
					}
				}
				case DoomsdayEntity.ACTION_VOLLEY -> {
					if (el < DoomsdayEntity.VOLLEY_THROW) {
						rightArm.xRot = Mth.lerp(Math.min(1f, el / 8f), -0.3f, 0.9f);
						rightArm.zRot = 0.5f;
						body.yRot = -0.4f;
					} else {
						rightArm.xRot = -1.6f;
						rightArm.yRot = 0.3f;
						body.yRot = 0.3f;
					}
				}
				case DoomsdayEntity.ACTION_HEAT -> {
					body.xRot = 0.05f;
					rightArm.xRot = 0.25f;
					leftArm.xRot = 0.25f;
					rightArm.zRot = 0.45f;
					leftArm.zRot = -0.45f;
				}
				case DoomsdayEntity.ACTION_GRAB -> {
					rightArm.xRot = -1.55f + Mth.sin(age * 0.3f) * 0.05f;
					rightArm.yRot = -0.1f;
					rightArm.zRot = 0f;
					if (!e.bound()) {
						leftArm.xRot = -0.6f;
						leftArm.zRot = -0.5f;
					}
				}
				case DoomsdayEntity.ACTION_BREATH -> {
					if (el < DoomsdayEntity.BREATH_INHALE) {
						head.xRot = -0.55f;
						body.xRot = -0.08f;
						rightArm.zRot = 0.8f;
						leftArm.zRot = -0.8f;
					} else {
						head.xRot = 0.1f;
						body.xRot = 0.3f;
						rightArm.xRot = 0.5f;
						leftArm.xRot = 0.5f;
					}
				}
				case DoomsdayEntity.ACTION_BLITZ -> {
					body.xRot = 0.65f;
					head.xRot = -0.4f;
					rightArm.xRot = 0.9f;
					leftArm.xRot = 0.9f;
					rightLeg.xRot = Mth.sin(age * 1.6f) * 1.1f;
					leftLeg.xRot = -Mth.sin(age * 1.6f) * 1.1f;
				}
				case DoomsdayEntity.ACTION_ROAR -> {
					float t = Math.min(1f, el / DoomsdayEntity.ROAR_AT);
					head.xRot = Mth.lerp(t, 0.1f, -0.75f);
					body.xRot = Mth.lerp(t, 0.18f, -0.15f);
					rightArm.xRot = 0.35f;
					leftArm.xRot = 0.35f;
					rightArm.zRot = Mth.lerp(t, 0.2f, 0.85f);
					leftArm.zRot = -Mth.lerp(t, 0.2f, 0.85f);
					if (el > DoomsdayEntity.ROAR_AT) {
						head.zRot = Mth.sin(age * 1.9f) * 0.06f;
					}
				}
				case DoomsdayEntity.ACTION_BREAK_FREE -> {
					if (el < DoomsdayEntity.BREAK_AT) {
						float s = Mth.sin(age * 2.6f) * 0.08f;
						rightArm.xRot = -1.0f + s;
						rightArm.yRot = 0.6f;
						leftArm.xRot = -1.0f - s;
						leftArm.yRot = -0.6f;
						body.zRot = s * 0.5f;
					} else {
						float t = Math.min(1f, (el - DoomsdayEntity.BREAK_AT) / 4f);
						rightArm.zRot = Mth.lerp(t, 0.2f, 1.7f);
						leftArm.zRot = -Mth.lerp(t, 0.2f, 1.7f);
						rightArm.xRot = -0.4f;
						leftArm.xRot = -0.4f;
						head.xRot = -0.6f;
					}
				}
				case DoomsdayEntity.ACTION_EMERGE -> {
					rightArm.xRot = -2.8f + Mth.sin(age * 0.5f) * 0.25f;
					leftArm.xRot = -2.8f - Mth.sin(age * 0.5f) * 0.25f;
					head.xRot = -0.4f;
				}
				case DoomsdayEntity.ACTION_CORPSE, DoomsdayEntity.ACTION_BANISH -> {
					rightArm.xRot = -2.9f;
					leftArm.xRot = -2.7f;
					rightArm.zRot = 0.4f;
					leftArm.zRot = -0.5f;
					rightLeg.xRot = 0.1f;
					leftLeg.xRot = -0.1f;
					rightLeg.zRot = 0.12f;
					leftLeg.zRot = -0.12f;
					head.xRot = 0.2f;
					head.yRot = 0.6f;
					head.z = 0f;
					body.xRot = 0f;
				}
				case DoomsdayEntity.ACTION_RISE -> {
					float t = Math.min(1f, el / DoomsdayEntity.RISE_TICKS);
					rightArm.xRot = Mth.lerp(t, -2.9f, -0.2f);
					leftArm.xRot = Mth.lerp(t, -2.7f, -0.2f);
					head.xRot = -0.5f * Mth.sin(t * Mth.PI);
				}
				default -> {
				}
			}
			// still bound: the left arm is strapped across his chest by the burial cables
			if (e.bound() && action != DoomsdayEntity.ACTION_BREAK_FREE && action != DoomsdayEntity.ACTION_CORPSE) {
				leftArm.xRot = -1.15f;
				leftArm.yRot = 0.75f;
				leftArm.zRot = 0.1f;
			}
			leftSleeve.copyFrom(leftArm);
			rightSleeve.copyFrom(rightArm);
			leftPants.copyFrom(leftLeg);
			rightPants.copyFrom(rightLeg);
			jacket.copyFrom(body);
			hat.copyFrom(head);
		}
	}

	// ---------------------------------------------------------------- layers

	/** His red eyes, always lit; brighter while heat vision charges. */
	static final class Glow extends RenderLayer<DoomsdayEntity, Model> {
		Glow(RenderLayerParent<DoomsdayEntity, Model> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, DoomsdayEntity e, float limbSwing, float limbSwingAmount,
				float partialTick, float age, float headYaw, float headPitch) {
			if (e.action() == DoomsdayEntity.ACTION_CORPSE && e.reviveFraction() < 0.7f) {
				return; // dark eyes while he lies dead; they light again just before he rises
			}
			VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
			getParentModel().renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			if (e.action() == DoomsdayEntity.ACTION_HEAT) {
				getParentModel().renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			}
		}
	}

	/** Grey stone flecks over his skin, heavier the more he has adapted (the adaptation mask). */
	static final class Stone extends RenderLayer<DoomsdayEntity, Model> {
		Stone(RenderLayerParent<DoomsdayEntity, Model> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, DoomsdayEntity e, float limbSwing, float limbSwingAmount,
				float partialTick, float age, float headYaw, float headPitch) {
			int mask = e.adaptMask();
			int total = 0;
			for (DoomsdayAdaptation.Bucket b : DoomsdayAdaptation.BUCKETS) {
				int lv = DoomsdayAdaptation.levelIn(mask, b);
				total += lv >= DoomsdayAdaptation.IMMUNE ? 4 : lv;
			}
			if (total <= 0) {
				return;
			}
			float alpha = Mth.clamp(0.25f + total * 0.06f, 0f, 0.95f);
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(STONE));
			getParentModel().renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color((int) (alpha * 255), 255, 255, 255));
		}
	}

	/** Bone spikes and (while bound) the burial cables, drawn on the posed parts. */
	static final class Spikes extends RenderLayer<DoomsdayEntity, Model> {
		/** x, y, z (pixels, part space), direction, half-width, length (pixels), first life it appears in. */
		private static final float[][] ARM = {
				{ -3.0f, 0.0f, 0.0f, -0.5f, -1.0f, 0.1f, 1.1f, 3.2f, 1 }, // shoulder
				{ -3.0f, 5.0f, 0.5f, -1.0f, -0.35f, 0.25f, 1.0f, 3.6f, 1 }, // forearm
				{ -3.0f, 7.5f, -0.8f, -1.0f, 0.1f, -0.3f, 0.8f, 2.8f, 2 },
				{ -1.0f, 4.0f, 2.0f, -0.3f, -0.4f, 1.0f, 0.8f, 2.6f, 2 }, // elbow
				{ -2.0f, 10.0f, -2.0f, -0.2f, 0.25f, -1.0f, 0.45f, 1.6f, 2 }, // knuckles
				{ 0.0f, 10.0f, -2.0f, 0.0f, 0.25f, -1.0f, 0.45f, 1.6f, 2 },
				{ -3.0f, 2.5f, -1.2f, -1.0f, -0.6f, -0.2f, 0.7f, 2.4f, 3 },
		};
		private static final float[][] BODY = {
				{ 0.0f, 1.5f, 2.0f, 0.0f, -0.45f, 1.0f, 1.0f, 3.0f, 1 }, // spine
				{ 0.0f, 5.0f, 2.0f, 0.0f, -0.3f, 1.0f, 1.0f, 3.4f, 1 },
				{ 0.0f, 8.5f, 2.0f, 0.0f, -0.2f, 1.0f, 0.9f, 2.8f, 2 },
				{ 3.0f, 1.0f, 2.0f, 0.5f, -0.6f, 1.0f, 0.9f, 3.0f, 2 }, // shoulder blades
				{ -3.0f, 1.0f, 2.0f, -0.5f, -0.6f, 1.0f, 0.9f, 3.0f, 2 },
				{ 2.5f, 3.0f, -2.0f, 0.4f, -0.2f, -1.0f, 0.6f, 1.8f, 3 }, // chest
				{ -2.5f, 3.0f, -2.0f, -0.4f, -0.2f, -1.0f, 0.6f, 1.8f, 3 },
		};
		private static final float[][] LEG = {
				{ 0.0f, 5.0f, -2.0f, 0.0f, -0.3f, -1.0f, 0.8f, 2.4f, 2 }, // knee
				{ -2.0f, 8.0f, 0.0f, -1.0f, 0.0f, 0.2f, 0.6f, 1.8f, 3 },
		};
		private static final float[][] HEAD = {
				{ 3.0f, -6.5f, -3.0f, 0.75f, -0.55f, -0.4f, 0.8f, 2.6f, 1 }, // brow horns
				{ -3.0f, -6.5f, -3.0f, -0.75f, -0.55f, -0.4f, 0.8f, 2.6f, 1 },
				{ 2.0f, -8.0f, 1.0f, 0.3f, -1.0f, 0.4f, 0.7f, 2.2f, 2 },
				{ -2.0f, -8.0f, 1.0f, -0.3f, -1.0f, 0.4f, 0.7f, 2.2f, 2 },
		};

		Spikes(RenderLayerParent<DoomsdayEntity, Model> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, DoomsdayEntity e, float limbSwing, float limbSwingAmount,
				float partialTick, float age, float headYaw, float headPitch) {
			Model m = getParentModel();
			int life = Math.max(1, e.life());
			float grow = 1f + 0.35f * Math.min(3, life - 1);
			if (e.action() == DoomsdayEntity.ACTION_ERUPT) {
				float el = age - e.actionStartTick;
				grow *= el < DoomsdayEntity.ERUPT_BURST ? 1f + 0.5f * el / DoomsdayEntity.ERUPT_BURST : 1.5f;
			}
			int color = 0xFFFFFFFF;
			VertexConsumer bone = buffers.getBuffer(RenderType.entityCutoutNoCull(BONE));
			part(pose, bone, light, color, m.rightArm, ARM, false, life, grow);
			part(pose, bone, light, color, m.leftArm, ARM, true, life, grow);
			part(pose, bone, light, color, m.body, BODY, false, life, grow);
			part(pose, bone, light, color, m.rightLeg, LEG, false, life, grow);
			part(pose, bone, light, color, m.leftLeg, LEG, true, life, grow);
			part(pose, bone, light, color, m.head, HEAD, false, life, grow);
			if (e.bound()) {
				cables(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CABLE)), light, m);
			}
		}

		private static void part(PoseStack pose, VertexConsumer vc, int light, int color, ModelPart part, float[][] spikes, boolean mirror, int life,
				float grow) {
			pose.pushPose();
			part.translateAndRotate(pose);
			PoseStack.Pose last = pose.last();
			float s = 1f / 16f;
			for (float[] k : spikes) {
				if (life < k[8]) {
					continue;
				}
				float mx = mirror ? -1f : 1f;
				Vector3f base = new Vector3f(k[0] * mx * s, k[1] * s, k[2] * s);
				Vector3f dir = new Vector3f(k[3] * mx, k[4], k[5]);
				DoomsdayMesh.spike(vc, last, light, color, base, dir, k[6] * s, k[7] * grow * s);
			}
			pose.popPose();
		}

		/** Bands round his chest and gut, and round the strapped left forearm. */
		private static void cables(PoseStack pose, VertexConsumer vc, int light, Model m) {
			float s = 1f / 16f;
			int color = 0xFFFFFFFF;
			pose.pushPose();
			m.body.translateAndRotate(pose);
			DoomsdayMesh.box(vc, pose.last(), light, color, -4.4f * s, 3.0f * s, -2.4f * s, 4.4f * s, 4.4f * s, 2.4f * s);
			DoomsdayMesh.box(vc, pose.last(), light, color, -4.4f * s, 8.0f * s, -2.4f * s, 4.4f * s, 9.2f * s, 2.4f * s);
			// a diagonal strap over the right shoulder
			pose.pushPose();
			pose.translate(0, 4.0f * s, 0);
			pose.mulPose(Axis.ZP.rotationDegrees(38f));
			DoomsdayMesh.box(vc, pose.last(), light, color, -0.7f * s, -6.0f * s, -2.45f * s, 0.7f * s, 6.0f * s, 2.45f * s);
			pose.popPose();
			pose.popPose();
			pose.pushPose();
			m.leftArm.translateAndRotate(pose);
			DoomsdayMesh.box(vc, pose.last(), light, color, -1.4f * s, 5.0f * s, -2.4f * s, 3.4f * s, 6.4f * s, 2.4f * s);
			DoomsdayMesh.box(vc, pose.last(), light, color, -1.4f * s, 8.4f * s, -2.4f * s, 3.4f * s, 9.6f * s, 2.4f * s);
			pose.popPose();
			pose.pushPose();
			m.rightArm.translateAndRotate(pose);
			DoomsdayMesh.box(vc, pose.last(), light, color, -3.4f * s, 8.6f * s, -2.4f * s, 1.4f * s, 9.8f * s, 2.4f * s); // a broken shackle
			pose.popPose();
		}
	}
}
