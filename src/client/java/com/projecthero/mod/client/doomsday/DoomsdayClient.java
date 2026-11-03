package com.projecthero.mod.client.doomsday;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doomsday.entity.CasketEntity;
import com.projecthero.mod.doomsday.entity.DoomsdayAttackEntity;
import com.projecthero.mod.doomsday.entity.DoomsdayEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** v0.17.2: Doomsday's client side -- his renderer, the Calatonian casket and his moves' bone geometry. */
public final class DoomsdayClient {
	static final ResourceLocation CASKET = ProjectHeroMod.id("textures/entity/doomsday/casket.png");

	private DoomsdayClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(DoomsdayEntityTypes.DOOMSDAY, DoomsdayRenderer::new);
		EntityRendererRegistry.register(DoomsdayEntityTypes.CASKET, Casket::new);
		EntityRendererRegistry.register(DoomsdayEntityTypes.ATTACK, Attack::new);
	}

	/** The burial vault: a long, cabled, gunmetal-green coffin. The lid bulges with every blow, then flies off. */
	static final class Casket extends EntityRenderer<CasketEntity> {
		Casket(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.shadowRadius = 1.2f;
		}

		@Override
		public void render(CasketEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
			float age = e.tickCount + partialTick;
			if (e.state() == CasketEntity.FALLING) {
				pose.mulPose(Axis.XP.rotationDegrees(-35f));
				pose.mulPose(Axis.ZP.rotationDegrees(age * 9f));
			}
			int lit = e.state() == CasketEntity.FALLING ? 0xF000F0 : light;
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CASKET));
			int color = 0xFFFFFFFF;
			float w = 0.75f, len = 1.6f, h = 0.95f;
			// the vault
			DoomsdayMesh.box(vc, pose.last(), lit, color, -w, 0f, -len, w, h * 0.7f, len);
			// the lid: jolts on every blow, gone once he is out
			if (e.state() != CasketEntity.OPEN) {
				float jolt = 0f;
				if (e.state() == CasketEntity.LANDED && e.dents() > 0) {
					jolt = Math.max(0f, 0.12f - ((e.tickCount % 9) + partialTick) * 0.02f) * Math.min(3f, 1f + e.dents() * 0.1f);
				}
				DoomsdayMesh.box(vc, pose.last(), lit, color, -w - 0.05f, h * 0.7f + jolt, -len - 0.05f, w + 0.05f, h + jolt, len + 0.05f);
				// a fist-shaped bulge that grows with the dents
				float bulge = Math.min(0.35f, e.dents() * 0.03f);
				if (bulge > 0f) {
					DoomsdayMesh.box(vc, pose.last(), lit, color, -0.25f, h + jolt, -0.6f, 0.25f, h + jolt + bulge, -0.1f);
				}
			} else {
				// the lid thrown aside, leaning on the vault
				pose.pushPose();
				pose.translate(w + 0.6f, 0f, 0f);
				pose.mulPose(Axis.ZP.rotationDegrees(70f));
				DoomsdayMesh.box(vc, pose.last(), light, color, 0f, -0.15f, -len, h * 0.9f, 0.1f, len);
				pose.popPose();
			}
			// the burial cables: three bands round the vault
			for (int i = -1; i <= 1; i++) {
				float z = i * len * 0.6f;
				DoomsdayMesh.box(vc, pose.last(), lit, 0xFF3E5A40, -w - 0.08f, -0.02f, z - 0.08f, w + 0.08f, h + 0.06f, z + 0.08f);
			}
			pose.popPose();
			super.render(e, yaw, partialTick, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(CasketEntity e) {
			return CASKET;
		}
	}

	/** A bone spike out of the ground, or a thrown bone shard. The ground wave is particles only. */
	static final class Attack extends EntityRenderer<DoomsdayAttackEntity> {
		Attack(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(DoomsdayAttackEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(DoomsdayRenderer.BONE));
			float age = e.tickCount + partialTick;
			if (e.kind() == DoomsdayAttackEntity.SPIKE) {
				float up = age - e.warn();
				if (up < 0f) {
					return;
				}
				float grow = up < 3f ? up / 3f : up > 18f ? Math.max(0f, 1f - (up - 18f) / 6f) : 1f;
				if (grow <= 0f) {
					return;
				}
				// a cluster: one big spike and two smaller leaning ones
				DoomsdayMesh.spike(vc, pose.last(), light, 0xFFFFFFFF, new Vector3f(0, -0.2f, 0), new Vector3f(0.1f, 1, 0.05f), 0.32f, 2.4f * grow);
				DoomsdayMesh.spike(vc, pose.last(), light, 0xFFFFFFFF, new Vector3f(0.3f, -0.2f, 0.15f), new Vector3f(0.6f, 1, 0.3f), 0.18f, 1.3f * grow);
				DoomsdayMesh.spike(vc, pose.last(), light, 0xFFFFFFFF, new Vector3f(-0.25f, -0.2f, -0.2f), new Vector3f(-0.5f, 1, -0.4f), 0.16f,
						1.1f * grow);
			} else if (e.kind() == DoomsdayAttackEntity.SHARD) {
				Vec3 v = e.getDeltaMovement();
				if (v.lengthSqr() < 1.0e-4) {
					v = new Vec3(0, 0, 1);
				}
				Vector3f dir = v.normalize().toVector3f();
				Vector3f back = new Vector3f(dir).mul(-0.45f);
				pose.pushPose();
				pose.mulPose(Axis.of(dir).rotationDegrees(age * 25f));
				DoomsdayMesh.spike(vc, pose.last(), light, 0xFFFFFFFF, back, dir, 0.11f, 0.9f);
				pose.popPose();
			}
			super.render(e, yaw, partialTick, pose, buffers, light);
		}

		@Override
		public boolean shouldRender(DoomsdayAttackEntity e, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
			return e.kind() != DoomsdayAttackEntity.RING && super.shouldRender(e, frustum, x, y, z);
		}

		@Override
		public ResourceLocation getTextureLocation(DoomsdayAttackEntity e) {
			return DoomsdayRenderer.BONE;
		}
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3 - 2 * t);
	}
}
