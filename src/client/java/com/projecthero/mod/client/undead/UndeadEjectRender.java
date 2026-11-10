package com.projecthero.mod.client.undead;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.UndeadEjectConfig;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * v0.17.3: the pieces Undead's ejections leave behind ({@code UndeadEject}), in his own skin -- only the rows that came off, with
 * a raw cut face where they came away:
 * <ul>
 * <li>a hand with half its forearm / a foot with half its calf ({@link UndeadPartEntity#STYLE_EJECT_END}): it tumbles as it
 * drops and comes to rest lying on its side;</li>
 * <li>the Combustion Punch's lower two thirds of the torso ({@link UndeadPartEntity#STYLE_EJECT_TORSO}): standing where he was
 * with the legs and the other arm, then falling back with them.</li>
 * </ul>
 * Both sink into the ground over their last {@link UndeadEjectConfig#PIECE_FADE} ticks.
 */
public final class UndeadEjectRender {
	private static final int MARROW = 0x9C2A22, CAP_BONE = 0xE8DCC0, CAP_FLESH = 0x8E1418, CAP_RIM = 0x5A0A0D;

	private UndeadEjectRender() {
	}

	/** Draws {@code e} (one of the eject styles), the pose at the entity's feet. */
	static void piece(UndeadPartEntity e, float partial, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim) {
		float age = e.tickCount + partial;
		float fade = Mth.clamp((UndeadEjectConfig.PIECE_LIFE - age) / UndeadEjectConfig.PIECE_FADE, 0f, 1f);
		if (fade <= 0f) {
			return;
		}
		pose.pushPose();
		if (e.style() == UndeadPartEntity.STYLE_EJECT_TORSO) {
			torso(e, age, fade, pose, buffers, light, tex, slim);
		} else {
			end(e, age, fade, pose, buffers, light, tex, slim);
		}
		pose.popPose();
	}

	private static void end(UndeadPartEntity e, float age, float fade, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim) {
		int seg = e.bodyPart();
		int limb = UndeadState.limbOf(seg);
		if (limb < UndeadState.RIGHT_ARM || limb > UndeadState.LEFT_LEG) {
			return;
		}
		float from = UndeadEjectConfig.EJECT_ROWS, to = 12f;
		float sink = (1f - fade) * 0.22f;
		pose.translate(0f, e.getBbHeight() * 0.35f - sink, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot()));
		// it drops off the stump end first and tumbles, then settles lying along the ground with a little rock
		float spin = (e.getId() % 2 == 0 ? 1f : -1f);
		float t = Mth.clamp(age / 9f, 0f, 1f);
		float settle = t * t * (3f - 2f * t);
		float tumble = (1f - settle) * age * 38f * spin;
		float rock = t >= 1f ? Mth.sin(Mth.clamp((age - 9f) / 5f, 0f, 1f) * Mth.PI) * 6f : 0f;
		pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(settle, 0f, 90f) + tumble + rock));
		pose.mulPose(Axis.ZP.rotationDegrees((e.getId() * 37 % 50) - 25f));
		pose.scale(-0.9375f, -0.9375f, 0.9375f);
		float[] b = UndeadTexels.box(limb, slim);
		pose.translate(-(b[0] + b[3] * 0.5f) / 16f, -(b[1] + (from + to) * 0.5f) / 16f, -(b[2] + b[5] * 0.5f) / 16f);
		rows(pose, buffers, light, tex, slim, limb, from, to);
		cap(pose, buffers, light, slim, limb, from);
	}

	private static void torso(UndeadPartEntity e, float age, float fade, PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex,
			boolean slim) {
		// as UndeadPartRenderer draws the standing pieces: still a moment, then toppling back about the feet
		float wait = 5f, len = 9f;
		float t = Mth.clamp((age - wait) / len, 0f, 1f);
		float fall = t * t;
		float settle = t >= 1f ? Mth.sin(Mth.clamp((age - wait - len) / 4f, 0f, 1f) * Mth.PI) * 4f : 0f;
		pose.translate(0f, -(1f - fade) * 0.3f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot()));
		pose.mulPose(Axis.XP.rotationDegrees(84f * fall - settle));
		pose.mulPose(UndeadRender.standing(net.minecraft.world.phys.Vec3.ZERO, 180f, slim, UndeadState.BODY));
		float from = UndeadEjectConfig.COMBUSTION_BODY_ROWS;
		rows(pose, buffers, light, tex, slim, UndeadState.BODY, from, 12f);
		cap(pose, buffers, light, slim, UndeadState.BODY, from);
	}

	// ---------------------------------------------------------------- drawing

	/** A texel's row from the joint (0 at the shoulder / hip / neck, 12 at the far end). */
	private static float row(int part, boolean slim, UndeadTexels.Texel t) {
		float[] box = UndeadTexels.box(part, slim);
		if (t.ny < -0.5f) {
			return 0f;
		}
		if (t.ny > 0.5f) {
			return 12f;
		}
		return Math.max(0f, Math.min(12f, t.y - box[1]));
	}

	/** The part's skin texels in rows [from, to). */
	static void rows(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float from, float to) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		for (UndeadTexels.Texel t : UndeadTexels.of(part, slim)) {
			float r = row(part, slim, t);
			boolean in = r <= 0.001f ? from <= 0.001f : r >= 11.999f ? to >= 11.999f : r >= from && r < to;
			if (!in || !SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			float[][] p = UndeadRender.cornersPublic(t, 0f, 0f);
			float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
			for (int i = 0; i < 4; i++) {
				vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(255, 255, 255, 255).setUv((i == 1 || i == 2) ? u1 : u0, i >= 2 ? v1 : v0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, t.nx, t.ny, t.nz);
			}
		}
	}

	/** The raw face where the piece came away, at {@code at} rows: marrow, a bone ring, flesh, a dark rim (the body: a spine). */
	static void cap(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float at) {
		float[] box = UndeadTexels.box(part, slim);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		PoseStack.Pose last = pose.last();
		float y = box[1] + at;
		int w = (int) box[3], d = (int) box[5];
		float cx = (w - 1) * 0.5f, cz = (d - 1) * 0.5f;
		float half = Math.min(w, d) * 0.5f;
		boolean body = part == UndeadState.BODY;
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				float rx = Math.abs(x - cx), rz = Math.abs(z - cz);
				float r = Math.max(rx, rz) / Math.max(0.5f, half - 0.5f);
				int rgb;
				if (!body && r < 0.3f) {
					rgb = MARROW;
				} else if (!body && r < 0.65f) {
					rgb = CAP_BONE;
				} else if (body && rx < 0.6f && rz < cz * 0.6f + 0.1f) {
					rgb = (x + z) % 2 == 0 ? CAP_BONE : MARROW; // the spine
				} else {
					rgb = r > 0.95f ? CAP_RIM : CAP_FLESH;
				}
				float x0 = box[0] + x, z0 = box[2] + z;
				UndeadRegrow.flatBox(vc, last, x0 + 0.02f, y - 0.06f, z0 + 0.02f, x0 + 0.98f, y + 0.06f, z0 + 0.98f, rgb, light);
			}
		}
	}

}
