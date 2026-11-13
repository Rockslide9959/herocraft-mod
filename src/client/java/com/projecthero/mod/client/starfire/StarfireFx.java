package com.projecthero.mod.client.starfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireConfig;
import com.projecthero.mod.starfire.data.StarfireState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Starfire's world effects, every viewer, all soft additive glows ({@code pod_soft_glow.png}, camera-facing):
 * <ul>
 *   <li>the <b>starbolt</b> (a green ball with a white-hot core and a short comet tail) and the bigger <b>Star Burst</b> -- drawn
 *   for the shared projectile's kinds ({@code OmnitrixRenderers.projectileLook});</li>
 *   <li>the <b>Star Burst</b> ball growing between her hands while Shift+R is held;</li>
 *   <li>the <b>Supernova</b>: a sun swelling round her through the 2 s charge, then a sun-coloured shell racing out to 10 blocks;</li>
 *   <li><b>Comet Dive</b>'s green fire wrapped round her and <b>Star Dash</b>'s green streak behind her.</li>
 * </ul>
 */
public final class StarfireFx {
	static final ResourceLocation SOFT_GLOW = ProjectHeroMod.id("textures/entity/omnitrix/pod_soft_glow.png");

	private StarfireFx() {
	}

	/** A soft round camera-facing glow (additive), {@code size} its half width in blocks, at the pose's origin. */
	static void glow(PoseStack pose, MultiBufferSource buffers, float size, int rgb, int alpha) {
		pose.pushPose();
		pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		VertexConsumer vc = buffers.getBuffer(RenderType.eyes(SOFT_GLOW));
		alpha = Mth.clamp(alpha, 0, 255);
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		int argb = (alpha << 24) | ((r * alpha / 255) << 16) | ((g * alpha / 255) << 8) | (b * alpha / 255);
		AlienParts.quad(vc, pose.last(), argb, LightTexture.FULL_BRIGHT, -size, -size, 0, size, -size, 0, size, size, 0, -size, size, 0, 0, 0, 1, 0, 0, 1,
				1);
		pose.popPose();
	}

	/** The starbolt / Star Burst look (pose at the projectile's feet). */
	static void bolt(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float s = e.size();
		boolean burst = StarfireMovesKinds.BURST.equals(e.kind());
		Vec3 v = e.getDeltaMovement();
		// v0.17.3 animation round: the comet tail never reaches back past the hand it left (it grows out of the throw)
		double travelled = (e.tickCount + partial) * v.length();
		pose.pushPose();
		pose.translate(0, s * 0.5, 0);
		for (int k = 4; k >= 1; k--) {
			if (k * 0.35 * v.length() > travelled + 0.3) {
				continue;
			}
			pose.pushPose();
			pose.translate(-v.x * k * 0.35, -v.y * k * 0.35, -v.z * k * 0.35);
			glow(pose, buffers, s * (1.6f - k * 0.2f), Starfire.GREEN, 170 - k * 30);
			pose.popPose();
		}
		// her own bolt seen from first person starts hand-sized and swells as it leaves the hand (it spawns half a block from her eye;
		// the client does not know a projectile's owner, so this goes by how close the bolt still is to the camera)
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.getCameraType().isFirstPerson()) {
			double near = e.getPosition(partial).add(0, s * 0.5, 0).distanceTo(mc.gameRenderer.getMainCamera().getPosition());
			if (near < 2.5) {
				s *= (float) Mth.clamp((near - 0.3) / 2.2, 0.3, 1.0);
			}
		}
		float flick = 0.9f + 0.1f * Mth.sin((e.tickCount + partial) * 2.1f);
		glow(pose, buffers, s * (burst ? 3.2f : 2.6f) * flick, Starfire.GREEN, 220);
		glow(pose, buffers, s * 1.4f, 0x9CFF8A, 240);
		glow(pose, buffers, s * 0.7f, 0xF4FFF0, 255);
		pose.popPose();
	}

	/** Per frame, after the entities: the holds and the moves' glows for every Starfire in view. */
	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		if (level == null || pose == null || buffers == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		float time = level.getGameTime() + partial;
		for (Player p : level.players()) {
			StarfireState s = Starfire.peek(p);
			if (s == null || !s.hasPower || p.isInvisible()) {
				continue;
			}
			boolean self = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
			Vec3 at = p.getPosition(partial);
			long now = level.getGameTime();
			// v0.17.3 animation round: the release flash at the throwing hand, peaking on the throw's release frame (StarfirePose)
			for (int side = 1; side >= -1; side -= 2) {
				float fl = StarfirePose.handFlash(p, side, partial);
				if (fl > 0.01f) {
					Vec3 hand = self ? p.getEyePosition(partial).add(0, -0.35, 0).add(p.getViewVector(partial).scale(1.6))
							.add(rightOf(p, partial).scale(0.45 * side)) : handAt(p, side, partial);
					float sz = self ? 0.08f + 0.06f * fl : 0.3f + 0.15f * fl;
					at(pose, cam, hand, () -> {
						glow(pose, buffers, sz * 2.2f, Starfire.GREEN, (int) (200 * fl));
						glow(pose, buffers, sz, 0xF4FFF0, (int) (230 * fl));
					});
				}
			}
			float bf = StarfirePose.burstFlash(p, partial);
			if (bf > 0.01f) {
				Vec3 palms = p.getEyePosition(partial).add(0, self ? -0.3 : -0.45, 0).add(p.getViewVector(partial).scale(self ? 0.9 : 0.85));
				at(pose, cam, palms, () -> {
					glow(pose, buffers, 1.1f * bf, Starfire.GREEN, (int) (210 * bf));
					glow(pose, buffers, 0.5f * bf, 0xFFFFFF, (int) (240 * bf));
				});
			}
			// Star Burst: the ball between her hands
			if (s.chargeMove == StarfireState.CHARGE_BURST && s.chargeStart > 0) {
				float f = Mth.clamp((now - s.chargeStart + partial) / StarfireConfig.BURST_CHARGE_TICKS, 0f, 1f);
				Vec3 look = p.getViewVector(partial);
				Vec3 ball = p.getEyePosition(partial).add(0, self ? -0.35 : -0.55, 0).add(look.scale(self ? 0.9 : 0.6));
				float size = 0.15f + 0.35f * f;
				float flick = 0.9f + 0.1f * Mth.sin(time * 1.7f);
				at(pose, cam, ball, () -> {
					glow(pose, buffers, size * 2.6f * flick, Starfire.GREEN, 200);
					glow(pose, buffers, size * 1.3f, 0xA8FF90, 230);
					glow(pose, buffers, size * 0.6f, 0xFFFFFF, 255);
				});
			}
			// Supernova: the swelling sun, then the shell
			if (s.chargeMove == StarfireState.CHARGE_NOVA && s.chargeStart > 0) {
				float f = Mth.clamp((now - s.chargeStart + partial) / StarfireConfig.NOVA_CHARGE_TICKS, 0f, 1f);
				Vec3 c = at.add(0, 1.0, 0);
				float flick = 0.92f + 0.08f * Mth.sin(time * 2.3f);
				if (!self) {
					at(pose, cam, c, () -> {
						glow(pose, buffers, (1.2f + 3.0f * f) * flick, Starfire.SUN, 150 + (int) (90 * f));
						glow(pose, buffers, (0.8f + 1.6f * f), 0xFFF2C0, 200);
						glow(pose, buffers, 0.6f + 0.6f * f, 0xFFFFFF, 230);
					});
				}
			}
			if (s.novaAt > 0 && now - s.novaAt >= 0 && now - s.novaAt < 24) {
				float e = (now - s.novaAt + partial) / 24f;
				float r = (float) StarfireConfig.NOVA_RADIUS * Mth.sqrt(e);
				int a = (int) (255 * (1f - e));
				Vec3 c = at.add(0, 1.0, 0);
				at(pose, cam, c, () -> {
					glow(pose, buffers, r * 1.25f, Starfire.SUN, a);
					glow(pose, buffers, r * 0.8f, 0xFFF6D8, (int) (a * 0.8f));
					glow(pose, buffers, r * 0.45f, 0xFFFFFF, a);
				});
				// a ring of glows out at the shell's edge
				for (int i = 0; i < 16; i++) {
					double ang = i * Math.PI * 2 / 16 + e;
					Vec3 q = c.add(Math.cos(ang) * r, Math.sin(i * 1.7) * r * 0.25, Math.sin(ang) * r);
					int col = i2c(i);
					at(pose, cam, q, () -> glow(pose, buffers, 1.4f, col, (int) (a * 0.9f)));
				}
			}
			// Comet Dive: green fire round her
			if (s.diveUntil > now && !self) {
				Vec3 v = p.getDeltaMovement();
				Vec3 c = at.add(0, 0.9, 0);
				float flick = 0.85f + 0.15f * Mth.sin(time * 3.1f);
				at(pose, cam, c, () -> {
					for (int k = 6; k >= 1; k--) {
						pose.pushPose();
						pose.translate(-v.x * k * 0.5, -v.y * k * 0.5, -v.z * k * 0.5);
						glow(pose, buffers, (2.2f - k * 0.22f) * flick, k > 3 ? 0x1FA82E : Starfire.GREEN, 200 - k * 25);
						pose.popPose();
					}
					glow(pose, buffers, 1.7f * flick, Starfire.GREEN, 210);
					glow(pose, buffers, 0.9f, 0xE0FFD0, 220);
				});
			}
			// Star Dash: the streak
			if (s.dashUntil > now) {
				Vec3 v = p.getDeltaMovement();
				Vec3 c = at.add(0, 1.0, 0);
				at(pose, cam, c, () -> {
					for (int k = 8; k >= 1; k--) {
						pose.pushPose();
						pose.translate(-v.x * k * 0.4, -v.y * k * 0.4, -v.z * k * 0.4);
						glow(pose, buffers, 0.9f - k * 0.07f, Starfire.GREEN, 200 - k * 20);
						pose.popPose();
					}
				});
			}
		}
	}

	/** Her body's right (horizontal). */
	private static Vec3 rightOf(Player p, float partial) {
		float yaw = Mth.lerp(partial, p.yRotO, p.getYRot()) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	/** Where a thrown bolt leaves her hand: out along the look from that shoulder (the server spawns it at {@code Starfire.handPos}). */
	private static Vec3 handAt(Player p, int side, float partial) {
		return p.getEyePosition(partial).add(0, -0.45, 0).add(p.getViewVector(partial).scale(0.55)).add(rightOf(p, partial).scale(0.35 * side));
	}

	private static int i2c(int i) {
		return i % 2 == 0 ? Starfire.SUN : 0xFF9A3A;
	}

	private static void at(PoseStack pose, Vec3 cam, Vec3 where, Runnable draw) {
		pose.pushPose();
		pose.translate(where.x - cam.x, where.y - cam.y, where.z - cam.z);
		draw.run();
		pose.popPose();
	}

	/** The projectile kinds (kept here so the client does not reach into server-only code paths). */
	static final class StarfireMovesKinds {
		static final String BOLT = com.projecthero.mod.starfire.StarfireMoves.BOLT_KIND;
		static final String BURST = com.projecthero.mod.starfire.StarfireMoves.BURST_KIND;
	}
}
