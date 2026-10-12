package com.projecthero.mod.client.ultron.contagion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.block.FirewallBeaconBlock;
import com.projecthero.mod.ultron.contagion.block.FirewallBeaconBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Firewall Beacon's blue hex dome. When Ultron "probes" the protected radius (a corruption column or a Nexus
 * spawn stopped at the edge, synced by the block entity) a patch of glowing cyan hexagons flickers on the dome's surface
 * where he pushed, fading over 2.5 s. A lit beacon also has a soft column of light over its emitter.
 */
public class FirewallBeaconRenderer implements BlockEntityRenderer<FirewallBeaconBlockEntity> {
	public FirewallBeaconRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public void render(FirewallBeaconBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		if (be.getLevel() == null) {
			return;
		}
		long now = be.getLevel().getGameTime();
		float time = now + partialTick;
		Vec3 origin = Vec3.atCenterOf(be.getBlockPos());
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(origin);
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		PoseStack.Pose p = pose.last();
		boolean lit = be.getBlockState().hasProperty(FirewallBeaconBlock.LIT) && be.getBlockState().getValue(FirewallBeaconBlock.LIT);
		if (lit) {
			float f = 0.5f + 0.15f * Mth.sin(time * 0.15f);
			BeamDraw.beam(buffers.getBuffer(RenderType.lightning()), false, p, new Vec3(0, 0.5, 0), new Vec3(0, 3.5, 0), cam, 0.35f, 0x60C8FF, f);
		}
		float left = be.probeUntil() - time;
		if (left > 0) {
			float fade = Math.min(1f, left / 20f) * (0.75f + 0.25f * Mth.sin(time * 2.3f));
			drawHexPatch(buffers.getBuffer(RenderType.lightning()), p, cam, (float) ContagionConfig.beacon().radius, be.probeYaw(), fade, time);
		}
		pose.popPose();
	}

	/** Cyan hexagons on the dome (radius {@code r}) round the direction {@code yawDeg} (from +X towards +Z). */
	static void drawHexPatch(VertexConsumer vc, PoseStack.Pose p, Vec3 cam, float r, float yawDeg, float fade, float time) {
		double yaw = Math.toRadians(yawDeg);
		float cell = 2.6f;
		int rows = 7, cols = 9;
		double dLat = cell * 1.5 / r;
		double dLon = cell * Math.sqrt(3) / r;
		for (int i = 0; i < rows; i++) {
			double lat = 0.05 + i * dLat;
			for (int j = -cols / 2; j <= cols / 2; j++) {
				double lon = yaw + (j + (i % 2) * 0.5) * dLon / Math.max(0.3, Math.cos(lat));
				float edgeFade = fade * (1f - Math.abs(j) / (cols / 2f + 1f)) * (1f - i / (float) (rows + 1));
				if (edgeFade <= 0.02f) {
					continue;
				}
				Vec3 c = sph(r, lat, lon);
				Vec3 up = sph(r, lat + 0.01, lon).subtract(c).normalize();
				Vec3 out = c.normalize();
				Vec3 east = up.cross(out).normalize();
				Vec3 prev = null;
				for (int k = 0; k <= 6; k++) {
					double a = Math.PI / 3 * k + Math.PI / 6;
					Vec3 v = c.add(east.scale(Math.cos(a) * cell)).add(up.scale(Math.sin(a) * cell)).normalize().scale(r);
					if (prev != null) {
						float shimmer = 0.6f + 0.4f * Mth.sin(time * 0.5f + i * 1.3f + j * 0.7f);
						BeamDraw.segment(vc, p, prev, v, cam, 0.07f, 0.07f, 0x40D8FF, edgeFade * shimmer, edgeFade * shimmer);
					}
					prev = v;
				}
			}
		}
	}

	private static Vec3 sph(double r, double lat, double lon) {
		double c = Math.cos(lat);
		return new Vec3(c * Math.cos(lon) * r, Math.sin(lat) * r, c * Math.sin(lon) * r);
	}

	@Override
	public boolean shouldRenderOffScreen(FirewallBeaconBlockEntity be) {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 160;
	}

	/** The dome's extent (used by renderers that cull block entities by box; harmless otherwise). */
	public AABB getRenderBoundingBox(FirewallBeaconBlockEntity be) {
		return new AABB(be.getBlockPos()).inflate(ContagionConfig.beacon().radius + 2);
	}
}
