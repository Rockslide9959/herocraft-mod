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
		addLayer(new NovaAura(this));
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
		if (a == DoomsdayEntity.ACTION_BANISH) {
			lie = 1f;
		} else if (a == DoomsdayEntity.ACTION_CORPSE) {
			// v0.17.2 round 2: he topples over (knees first) instead of snapping flat
			float t = Mth.clamp(elapsed / 14f, 0f, 1f);
			lie = t * t;
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
			this.crouching = action == DoomsdayEntity.ACTION_LEAP_CROUCH || action == DoomsdayEntity.ACTION_LAND || action == DoomsdayEntity.ACTION_STAGGER
					|| action == DoomsdayEntity.ACTION_SHATTER && el >= DoomsdayEntity.SHATTER_SLAM
					|| action == DoomsdayEntity.ACTION_SKYFALL && el < DoomsdayEntity.SKY_CROUCH
					|| action == DoomsdayEntity.ACTION_SKYFALL_LAND && el < DoomsdayEntity.SKY_LAND_TICKS * 0.6f
					|| action == DoomsdayEntity.ACTION_CORPSE && el < 8f;
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
			// v0.17.2 round 2: a heavier walk -- longer strides, the whole upper body dropping onto each footfall and rolling
			// side to side; standing still, the shoulders and chest heave with each breath
			float stride = Math.min(1f, limbSwingAmount);
			float phase = limbSwing * 0.6662f;
			float bob = Math.abs(Mth.cos(phase)) * 1.4f * stride + breathe * 0.35f * (1f - stride);
			body.y += bob;
			head.y += bob;
			rightArm.y += bob;
			leftArm.y += bob;
			body.zRot = Mth.sin(phase) * 0.07f * stride;
			head.zRot = -body.zRot * 0.5f;
			rightLeg.xRot *= 1.25f;
			leftLeg.xRot *= 1.25f;
			body.xRot += breathe * 0.03f * (1f - stride);
			head.xRot += breathe * 0.04f * (1f - stride);
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
				// ---- v0.17.2 round 2 (user: "add more attacks and animations to doomsday please")
				case DoomsdayEntity.ACTION_RAMPAGE -> {
					int w = DoomsdayEntity.RAMPAGE_WINDUP, r = DoomsdayEntity.RAMPAGE_RUN;
					if (el < w) {
						// head down, shoulders forward, arms swept back -- pawing the ground with one foot
						float t = Math.min(1f, el / 8f);
						body.xRot = Mth.lerp(t, 0.2f, 0.6f);
						head.xRot = -0.35f;
						rightArm.xRot = 0.7f * t;
						leftArm.xRot = 0.7f * t;
						rightArm.zRot = 0.35f;
						leftArm.zRot = -0.35f;
						if (el > 4 && el < 15) {
							rightLeg.xRot = 0.5f + Mth.sin((el - 4) * 0.9f) * 0.55f;
						}
					} else if (el < w + r) {
						// the charge: low shoulder first, legs pounding
						body.xRot = 0.85f;
						head.xRot = -0.55f;
						rightArm.xRot = -0.5f;
						rightArm.yRot = -0.55f;
						rightArm.zRot = 0.2f;
						leftArm.xRot = 0.9f + Mth.sin(age * 1.4f) * 0.4f;
						rightLeg.xRot = Mth.sin(age * 1.4f) * 1.25f;
						leftLeg.xRot = -Mth.sin(age * 1.4f) * 1.25f;
					} else {
						float t = Math.min(1f, (el - w - r) / 8f);
						body.xRot = Mth.lerp(t, 0.85f, 0.2f);
						rightLeg.xRot = Mth.lerp(t, -0.9f, 0f);
						leftLeg.xRot = Mth.lerp(t, 0.6f, 0f);
					}
				}
				case DoomsdayEntity.ACTION_RAMPAGE_CRASH -> {
					// reeling from the wall: rocked back, arms hanging out, head lolling
					float k = 1f - Math.min(1f, el / DoomsdayEntity.CRASH_TICKS);
					body.xRot = -0.25f * k;
					head.xRot = 0.25f;
					head.zRot = Mth.sin(el * 0.8f) * 0.4f * k;
					body.zRot = Mth.sin(el * 0.5f) * 0.08f * k;
					rightArm.xRot = 0.25f;
					leftArm.xRot = 0.25f;
					rightArm.zRot = 0.75f * k + 0.15f;
					leftArm.zRot = -0.75f * k - 0.15f;
					rightLeg.zRot = 0.1f * k;
					leftLeg.zRot = -0.1f * k;
				}
				case DoomsdayEntity.ACTION_CLAP -> {
					float local = el < DoomsdayEntity.CLAP_AT + 6 || e.life() < 3 ? el : el - 18f;
					if (local < DoomsdayEntity.CLAP_AT) {
						float t = Math.min(1f, local / 10f);
						rightArm.xRot = -1.45f;
						leftArm.xRot = -1.45f;
						rightArm.yRot = -1.15f * t;
						leftArm.yRot = 1.15f * t;
						body.xRot = Mth.lerp(t, 0.18f, -0.08f);
						head.xRot = -0.3f * t;
					} else {
						float t = Math.min(1f, (local - DoomsdayEntity.CLAP_AT) / 2f);
						rightArm.xRot = -1.5f;
						leftArm.xRot = -1.5f;
						rightArm.yRot = Mth.lerp(t, -1.15f, 0.42f);
						leftArm.yRot = Mth.lerp(t, 1.15f, -0.42f);
						body.xRot = 0.3f;
					}
				}
				case DoomsdayEntity.ACTION_SKYFALL -> {
					int c = DoomsdayEntity.SKY_CROUCH, rise = DoomsdayEntity.SKY_RISE, hang = DoomsdayEntity.SKY_HANG;
					if (el < c) {
						rightArm.xRot = 1.0f;
						leftArm.xRot = 1.0f;
						rightArm.zRot = 0.3f;
						leftArm.zRot = -0.3f;
						head.xRot = -0.5f;
					} else if (el < c + rise) {
						// launching: arms thrown up, legs straight
						rightArm.xRot = -3.0f;
						leftArm.xRot = -3.0f;
						rightArm.zRot = -0.15f;
						leftArm.zRot = 0.15f;
						rightLeg.xRot = 0.15f;
						leftLeg.xRot = 0.15f;
						body.xRot = -0.05f;
					} else if (el < c + rise + hang) {
						// hanging at the top: curled, looking down at the mark
						rightLeg.xRot = -1.1f;
						leftLeg.xRot = -0.9f;
						rightArm.xRot = -2.2f;
						leftArm.xRot = -2.2f;
						head.xRot = 0.7f;
						body.xRot = 0.4f;
					} else {
						// the drop: fists raised to come down like hammers
						rightArm.xRot = -2.95f;
						leftArm.xRot = -2.95f;
						rightArm.zRot = 0.2f;
						leftArm.zRot = -0.2f;
						rightLeg.xRot = -0.3f;
						leftLeg.xRot = 0.2f;
						head.xRot = 0.5f;
					}
				}
				case DoomsdayEntity.ACTION_SKYFALL_LAND -> {
					// fists buried in the crater, then hauling himself up out of it
					float t = Math.min(1f, el / DoomsdayEntity.SKY_LAND_TICKS);
					rightArm.xRot = Mth.lerp(t, -0.35f, -0.15f);
					leftArm.xRot = Mth.lerp(t, -0.35f, -0.15f);
					rightArm.zRot = Mth.lerp(t, 0.35f, 0.18f);
					leftArm.zRot = -Mth.lerp(t, 0.35f, 0.18f);
					head.xRot = Mth.lerp(t, 0.5f, 0.1f);
					body.xRot = Mth.lerp(t, 0.6f, 0.18f);
				}
				case DoomsdayEntity.ACTION_HURL -> {
					if (el < DoomsdayEntity.HURL_RIP) {
						// bent double, fists driven into the ground, wrenching
						float s = Mth.sin(age * 1.8f) * 0.06f;
						body.xRot = 0.95f;
						head.xRot = 0.2f;
						rightArm.xRot = -0.85f + s;
						leftArm.xRot = -0.85f - s;
						rightArm.zRot = 0.25f;
						leftArm.zRot = -0.25f;
					} else if (el < DoomsdayEntity.HURL_THROW) {
						// heaving it up over his head
						float t = Math.min(1f, (el - DoomsdayEntity.HURL_RIP) / 8f);
						body.xRot = Mth.lerp(t, 0.95f, -0.15f);
						rightArm.xRot = Mth.lerp(t, -0.85f, -3.0f);
						leftArm.xRot = Mth.lerp(t, -0.85f, -3.0f);
						rightArm.zRot = 0.28f;
						leftArm.zRot = -0.28f;
						rightLeg.zRot = 0.12f;
						leftLeg.zRot = -0.12f;
						head.xRot = -0.3f;
					} else {
						// the throw follows through
						float t = Math.min(1f, (el - DoomsdayEntity.HURL_THROW) / 3f);
						body.xRot = Mth.lerp(t, -0.15f, 0.45f);
						rightArm.xRot = Mth.lerp(t, -3.0f, -1.2f);
						leftArm.xRot = Mth.lerp(t, -3.0f, -1.2f);
					}
				}
				case DoomsdayEntity.ACTION_NOVA -> {
					if (el < DoomsdayEntity.NOVA_AT) {
						// down on one knee, fists clenched, trembling with what he is holding in
						float s = Mth.sin(age * 2.4f) * 0.05f * (el / DoomsdayEntity.NOVA_AT);
						rightLeg.xRot = -1.4f;
						leftLeg.xRot = 0.45f;
						body.xRot = 0.35f + s;
						head.xRot = el < DoomsdayEntity.NOVA_AT - 8 ? 0.5f : -0.6f;
						rightArm.xRot = 0.1f;
						leftArm.xRot = 0.1f;
						rightArm.zRot = 0.4f + s;
						leftArm.zRot = -0.4f - s;
					} else {
						// unleashed: arms flung wide, head back
						float t = Math.min(1f, (el - DoomsdayEntity.NOVA_AT) / 3f);
						rightArm.zRot = Mth.lerp(t, 0.4f, 1.7f);
						leftArm.zRot = -Mth.lerp(t, 0.4f, 1.7f);
						rightArm.xRot = -0.3f;
						leftArm.xRot = -0.3f;
						head.xRot = -0.7f;
						body.xRot = -0.2f;
					}
				}
				case DoomsdayEntity.ACTION_STAGGER -> {
					// v0.17.2 stagger: slumped down on one knee, one fist on the ground, head hanging and swaying, breathing hard
					float sway = Mth.sin(age * 0.18f);
					body.xRot = 0.55f + breathe * 0.04f;
					body.zRot = sway * 0.05f;
					head.xRot = 0.6f + sway * 0.06f;
					head.zRot = sway * 0.12f;
					rightArm.xRot = -0.55f;
					rightArm.zRot = 0.25f;
					leftArm.xRot = 0.25f + breathe * 0.05f;
					leftArm.zRot = -0.35f;
					rightLeg.xRot = -1.35f;
					leftLeg.xRot = 0.45f;
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
			boolean down = action == DoomsdayEntity.ACTION_CORPSE || action == DoomsdayEntity.ACTION_BANISH;
			// v0.17.2 round 2: a hit makes him flinch -- torso and head snap back, arms jerk out
			if (e.hurtTime > 0 && !down) {
				float f = e.hurtTime / 10f;
				body.xRot -= 0.22f * f;
				head.xRot -= 0.35f * f;
				rightArm.zRot += 0.25f * f;
				leftArm.zRot -= 0.25f * f;
			}
			// v0.17.2 round 2: adapting -- a violent shudder as the stone spreads (24 ticks after the mask changes)
			int mask = e.adaptMask();
			if (mask != e.clientAdaptMask) {
				e.clientAdaptMask = mask;
				e.clientAdaptAt = mask == 0 ? -1000f : age;
			}
			float since = age - e.clientAdaptAt;
			if (since >= 0f && since < 24f && !down) {
				float k = 1f - since / 24f;
				body.zRot += Mth.sin(age * 3.1f) * 0.09f * k;
				head.zRot += Mth.sin(age * 3.7f) * 0.15f * k;
				head.xRot -= 0.4f * k;
				rightArm.zRot += 0.5f * k;
				leftArm.zRot -= 0.5f * k;
				rightArm.xRot -= 0.3f * k;
				leftArm.xRot -= 0.3f * k;
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
	/**
	 * v0.17.2 round 2: the Adaptive Nova -- a crawling energy shell in the colour of what he is channelling (fire orange,
	 * lightning blue-white, bone ivory, blast red, magic soul-teal, raw power pale cyan), brighter as the burst nears.
	 */
	static final class NovaAura extends RenderLayer<DoomsdayEntity, Model> {
		private static final ResourceLocation SWIRL = ProjectHeroMod.id("textures/entity/doomsday/nova_swirl.png");

		NovaAura(RenderLayerParent<DoomsdayEntity, Model> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, DoomsdayEntity e, float limbSwing, float limbSwingAmount,
				float partialTick, float age, float headYaw, float headPitch) {
			if (e.action() != DoomsdayEntity.ACTION_NOVA) {
				return;
			}
			float el = age - e.actionStartTick;
			float k = el < DoomsdayEntity.NOVA_AT ? 0.35f + 0.65f * el / DoomsdayEntity.NOVA_AT : Math.max(0f, 1f - (el - DoomsdayEntity.NOVA_AT) / 12f);
			if (k <= 0.01f) {
				return;
			}
			int rgb = switch (e.novaElement() == null ? DoomsdayAdaptation.Bucket.POWER : e.novaElement()) {
				case FIRE -> 0xFF7A1A;
				case ENERGY -> 0x9AD8FF;
				case PROJECTILE, MELEE -> 0xEDE2C4;
				case EXPLOSION -> 0xFF2A1A;
				case MAGIC -> 0x35E0D0;
				default -> 0xC8F4FF;
			};
			float f = age * 0.02f;
			VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(SWIRL, f % 1f, f * 0.6f % 1f));
			pose.pushPose();
			pose.scale(1.06f, 1.03f, 1.06f);
			getParentModel().renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color((int) (255 * k), rgb >> 16 & 255,
					rgb >> 8 & 255, rgb & 255));
			pose.popPose();
		}
	}

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
