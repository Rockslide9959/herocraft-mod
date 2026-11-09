package com.projecthero.mod.client.andy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyBoneSword;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.render.VanillaSkeleton;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * v0.17.3: the Bone Sword (Shift+V, {@link AndyBoneSword}) -- his own torn-off arm held as a sword. The blade is the arm's bone
 * (the vanilla skeleton's arm bone, the same one the regrowth pushes out of a stump, drawn longer) with jagged bone edges and a
 * point; dark blood streaks run along it and drip off the edge and the tip; the crossguard is a glossy, blobby lump of congealed
 * blood with drops hanging from it; the grip is wrapped in the arm's torn skin with raw flesh showing between, the knuckle of
 * the bone as the pommel.
 *
 * <p>Sword space: model px / 16 units, +y down, the grip round the origin and the blade running toward -z. Drawn in his hand
 * (third person, {@link #thirdPerson}), as a held item (first person, {@link #firstPerson}) and as the thrown Impale Throw
 * ({@link #mesh}, from {@link AndyPartRenderer}). Also the Rib Cage Guard's ribs ({@link #ribs}).
 */
public final class AndyBoneSwordRender {
	private static final RandomSource RND = RandomSource.create();

	// colours
	static final int BONE = 0xE6DFC8, BONE_SHADE = 0xCFC4A6, BONE_DARK = 0xA89A7C;
	static final int BLOOD = 0x6E0A12, BLOOD_DARK = 0x45040A, BLOOD_GLOSS = 0xC4222E, BLOOD_WET = 0x9A1018;
	static final int SKIN = 0xC48C6A, SKIN_DARK = 0x9E6A4E, FLESH = 0x8C1C22;

	/** The palette's plain white texel: everything here is tinted from it. */
	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	/** The blade's tip in sword space (px). */
	static final float TIP_Z = -24.2f;

	private AndyBoneSwordRender() {
	}

	// ---------------------------------------------------------------- the poses

	/**
	 * From {@link AndyPose#apply}: the sword arm while the Bone Sword is out (held up a little, as vanilla holds an item, the
	 * attack swing kept), the rip and the put-away, and each sword move. True if it set the pose.
	 */
	static boolean pose(Player player, AndyState s, net.minecraft.client.model.HumanoidModel<?> model, float now) {
		float age = now - s.animStart;
		int handPart = s.boneSword() ? AndyBoneSword.hand(s) : Andy.mainArm(player);
		net.minecraft.client.model.geom.ModelPart arm = handPart == AndyState.RIGHT_ARM ? model.rightArm : model.leftArm;
		float sd = handPart == AndyState.RIGHT_ARM ? 1f : -1f;
		float aim = model.head.xRot - Mth.HALF_PI;
		if (!s.boneSword()) {
			if (s.animId == AndyState.ANIM_SWORD_IMPALE && age >= 0f && age < 10f) {
				float t = ease(age / 4f);
				set(arm, fade(age, 10f), Mth.lerp(t, -2.9f, aim + 0.3f), model.head.yRot, 0f); // over the shoulder and thrown
				model.body.yRot -= 0.3f * sd * fade(age, 10f) * t;
				return true;
			}
			if (s.animId == AndyState.ANIM_SWORD_AWAY && age >= 0f && age < 8f) {
				set(arm, fade(age, 8f), -1.2f, 0.2f * sd, -0.1f * sd); // the hand opening as it bursts
				return true;
			}
			return false;
		}
		// held: up off the hip like a held item (added, so vanilla's attack swing stays)
		arm.xRot -= 0.3f;
		if (age < 0f) {
			return true;
		}
		switch (s.animId) {
			case AndyState.ANIM_SWORD_RIP -> {
				if (age < 12f) {
					// the hand grabs the other shoulder and tears the arm away
					float grab = ease(age / 4f), pull = ease((age - 4f) / 5f);
					float w = fade(age, 12f);
					set(arm, w, Mth.lerp(pull, -1.3f, -0.9f), Mth.lerp(pull, 1.0f * sd * grab, -0.5f * sd), 0f);
					model.body.yRot += Mth.lerp(pull, 0.35f, -0.25f) * sd * w;
				}
			}
			case AndyState.ANIM_SWORD_SLASH1, AndyState.ANIM_SWORD_SLASH2, AndyState.ANIM_SWORD_COUNTER -> {
				if (age < 8f) {
					float t = ease(age / 5f);
					float dir = s.animId == AndyState.ANIM_SWORD_SLASH2 ? -1f : 1f;
					set(arm, fade(age, 8f), -1.55f, Mth.lerp(t, -1.0f, 1.0f) * sd * dir, 0f);
					model.body.yRot += Mth.lerp(t, -0.25f, 0.25f) * sd * dir * fade(age, 8f);
				}
			}
			case AndyState.ANIM_SWORD_SLASH3 -> {
				if (age < 12f) {
					float t = ease(age / 7f);
					set(arm, fade(age, 12f), -1.65f, Mth.lerp(t, -1.5f, 1.4f) * sd, 0f);
					model.body.yRot += Mth.lerp(t, -0.6f, 0.6f) * sd * fade(age, 12f);
				}
			}
			case AndyState.ANIM_SWORD_THRUST, AndyState.ANIM_SWORD_DASH -> {
				if (age < 10f) {
					float w = fade(age, 10f);
					set(arm, w, aim, model.head.yRot, 0f);
					model.body.xRot += 0.3f * w;
					model.body.yRot -= 0.3f * sd * w;
				}
			}
			case AndyState.ANIM_SWORD_EDGE -> {
				if (age < 12f) {
					float t = ease(age / 6f);
					set(arm, fade(age, 12f), Mth.lerp(t, -3.0f, -0.45f), model.head.yRot, 0f);
					model.body.xRot += 0.25f * t * fade(age, 12f);
				}
			}
			case AndyState.ANIM_SWORD_GUARD, AndyState.ANIM_SWORD_PARRY -> {
				float len = s.animId == AndyState.ANIM_SWORD_GUARD ? AndyBoneSword.GUARD_TICKS : AndyBoneSword.PARRY_TICKS + 4f;
				if (age < len) {
					set(arm, Math.min(ease(age / 2f), fade(age, len)), -1.45f, 0.75f * sd, 0.3f * sd); // the blade across the chest
				}
			}
			case AndyState.ANIM_SWORD_SPLINTER -> {
				if (age < 10f) {
					set(arm, fade(age, 10f), aim + Mth.sin(age * 3f) * 0.08f, model.head.yRot, 0f);
				}
			}
			default -> {
			}
		}
		return true;
	}

	private static void set(net.minecraft.client.model.geom.ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}

	// ---------------------------------------------------------------- where it is drawn

	/** In his hand (from {@link AndyRender#renderLayer}), plus the Rib Cage Guard. */
	static void thirdPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, AndyState s, float now) {
		float age = now - s.animStart;
		if (s.animId == AndyState.ANIM_SWORD_GUARD && age >= 0f && age < AndyBoneSword.GUARD_TICKS) {
			pose.pushPose();
			model.body.translateAndRotate(pose);
			ribs(pose, buffers, light, age);
			pose.popPose();
		}
		if (!s.boneSword()) {
			return;
		}
		int hand = AndyBoneSword.hand(s);
		if (s.broken(hand)) {
			return;
		}
		boolean slim = AndyRender.slim(player);
		pose.pushPose();
		AndyRender.part(model, hand).translateAndRotate(pose);
		float cx = (hand == AndyState.RIGHT_ARM ? -1f : 1f) * (slim ? 0.5f : 1f);
		pose.translate(cx / 16f, 10.4f / 16f, -0.2f / 16f);
		pose.mulPose(Axis.XP.rotationDegrees(-8f)); // a little up off the line of the arm, like a held sword
		float grow = s.animId == AndyState.ANIM_SWORD_RIP && age >= 0f && age < 8f ? 0.45f + 0.55f * Mth.clamp(age / 8f, 0f, 1f) : 1f;
		if (grow < 1f) {
			pose.scale(grow, grow, grow);
		}
		mesh(pose, buffers, light, now, true);
		drips(pose, player, now);
		pose.popPose();
	}

	/**
	 * First person, from {@link AndyFirstPerson#arms}: the sword where vanilla holds a sword, swinging with the attack and with
	 * each sword move. True if it drew (the caller cancels vanilla's hand).
	 */
	static boolean firstPerson(AbstractClientPlayer player, AndyState s, float partial, InteractionHand hand, PoseStack pose, MultiBufferSource buffers,
			int light) {
		if (!s.boneSword()) {
			return false;
		}
		int arm = AndyBoneSword.hand(s);
		boolean mainHand = arm == Andy.mainArm(player);
		if ((hand == InteractionHand.MAIN_HAND) != mainHand) {
			return false; // the other hand (the torn-off arm's) is vanilla's / the missing-hand rule's
		}
		float now = player.level().getGameTime() + partial;
		float age = now - s.animStart;
		float sd = arm == AndyState.RIGHT_ARM ? 1f : -1f;
		float sw = player.swingingArm == hand ? player.getAttackAnim(partial) : 0f;
		float sq = Mth.sqrt(sw);
		pose.pushPose();
		// vanilla's held-item swing and arm placement
		pose.translate(sd * -0.4f * Mth.sin(sq * Mth.PI), 0.2f * Mth.sin(sq * Mth.TWO_PI), -0.2f * Mth.sin(sw * Mth.PI));
		float draw = s.animId == AndyState.ANIM_SWORD_RIP && age >= 0f && age < 10f ? 1f - Mth.clamp(age / 10f, 0f, 1f) : 0f;
		pose.translate(sd * 0.52f, -0.5f - draw * 0.5f, -0.72f);
		float f = Mth.sin(sw * sw * Mth.PI);
		pose.mulPose(Axis.YP.rotationDegrees(sd * (45f + f * -20f)));
		float g = Mth.sin(sq * Mth.PI);
		pose.mulPose(Axis.ZP.rotationDegrees(sd * g * -20f));
		pose.mulPose(Axis.XP.rotationDegrees(g * -80f));
		pose.mulPose(Axis.YP.rotationDegrees(sd * -45f));
		// the sword moves
		moveOffsets(pose, s.animId, age, sd);
		// the blade up and forward from the fist
		pose.mulPose(Axis.YP.rotationDegrees(sd * 8f));
		pose.mulPose(Axis.XP.rotationDegrees(38f));
		pose.mulPose(Axis.ZP.rotationDegrees(180f)); // sword space is +y down
		pose.scale(0.62f, 0.62f, 0.62f);
		mesh(pose, buffers, light, now, true);
		drips(pose, player, now);
		pose.popPose();
		return true;
	}

	/** First person: each sword move's swing on top of the held pose (blended in and out over its length). */
	private static void moveOffsets(PoseStack pose, int anim, float age, float sd) {
		if (age < 0f) {
			return;
		}
		switch (anim) {
			case AndyState.ANIM_SWORD_SLASH1, AndyState.ANIM_SWORD_SLASH2, AndyState.ANIM_SWORD_COUNTER -> {
				if (age < 7f) {
					float t = ease(age / 5f);
					float w = fade(age, 7f);
					float dir = anim == AndyState.ANIM_SWORD_SLASH2 ? -1f : 1f;
					pose.translate(0f, 0.05f * w, -0.1f * w);
					pose.mulPose(Axis.YP.rotationDegrees(sd * dir * Mth.lerp(t, -55f, 60f) * w));
					pose.mulPose(Axis.ZP.rotationDegrees(sd * dir * 35f * w));
				}
			}
			case AndyState.ANIM_SWORD_SLASH3, AndyState.ANIM_SWORD_EDGE -> {
				if (age < 10f) {
					float t = ease(age / 7f);
					float w = fade(age, 10f);
					if (anim == AndyState.ANIM_SWORD_EDGE) {
						pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(t, 70f, -60f) * w)); // over the head and down
					} else {
						pose.mulPose(Axis.YP.rotationDegrees(sd * Mth.lerp(t, -90f, 100f) * w)); // a wide flat sweep
						pose.mulPose(Axis.ZP.rotationDegrees(sd * 70f * w));
					}
				}
			}
			case AndyState.ANIM_SWORD_THRUST, AndyState.ANIM_SWORD_DASH -> {
				if (age < 10f) {
					float t = ease(age / 3f);
					float w = fade(age, 10f);
					pose.translate(-sd * 0.15f * w, 0.1f * w, -0.35f * t * w);
					pose.mulPose(Axis.XP.rotationDegrees(-30f * w));
					pose.mulPose(Axis.YP.rotationDegrees(sd * 30f * w));
				}
			}
			case AndyState.ANIM_SWORD_GUARD, AndyState.ANIM_SWORD_PARRY -> {
				float len = anim == AndyState.ANIM_SWORD_GUARD ? AndyBoneSword.GUARD_TICKS : AndyBoneSword.PARRY_TICKS + 4f;
				if (age < len) {
					float w = Math.min(ease(age / 3f), fade(age, len));
					// the blade held across in front
					pose.translate(-sd * 0.3f * w, 0.12f * w, 0f);
					pose.mulPose(Axis.ZP.rotationDegrees(sd * 80f * w));
					pose.mulPose(Axis.XP.rotationDegrees(-10f * w));
				}
			}
			case AndyState.ANIM_SWORD_SPLINTER -> {
				if (age < 8f) {
					float w = fade(age, 8f);
					pose.mulPose(Axis.XP.rotationDegrees((-25f + Mth.sin(age * 3f) * 6f) * w));
					pose.translate(0f, 0f, -0.15f * w);
				}
			}
			default -> {
			}
		}
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static float fade(float age, float len) {
		return Mth.clamp((len - age) / 3f, 0f, 1f);
	}

	/** Now and then a drop falls off the tip (in the world). The pose is at sword space. */
	private static void drips(PoseStack pose, Player player, float now) {
		if (Minecraft.getInstance().isPaused() || RND.nextInt(70) != 0) {
			return;
		}
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		Matrix4f m = pose.last().pose();
		boolean guard = RND.nextInt(3) == 0;
		Vector4f at = m.transform(new Vector4f(0f, (guard ? 6.4f : 1.4f) / 16f, (guard ? -2f : TIP_Z + 0.8f) / 16f, 1f));
		BloodFx.drip(new Vec3(at.x() + cam.x, at.y() + cam.y, at.z() + cam.z));
	}

	// ---------------------------------------------------------------- the mesh

	/** The whole sword in sword space ({@code light} for everything; {@code wet}: the tip drop swells and falls). */
	static void mesh(PoseStack pose, MultiBufferSource buffers, int light, float now, boolean wet) {
		// the blade: the arm's bone, drawn long (the vanilla skeleton's 2 x 12 x 2 arm bone, stretched to 19 px)
		pose.pushPose();
		pose.translate(0f, 0f, -5.8f / 16f);
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		pose.scale(0.8f, 1.6f, 1.0f);
		VanillaSkeleton.draw(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(VanillaSkeleton.TEXTURE)), VanillaSkeleton.RIGHT_ARM, -2f, 10f,
				light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose p = pose.last();
		// the jagged bone edges, top and bottom, tapering to the point (alternate segments stand proud: splintered)
		float z = -2.7f;
		for (int k = 0; k < 9; k++) {
			float len = 2.1f;
			float taper = 1f - k / 10f;
			float jag = (k % 2 == 0) ? 0.35f : 0f;
			float jag2 = (k % 3 == 1) ? 0.3f : 0f;
			int c = k % 2 == 0 ? BONE : BONE_SHADE;
			box(vc, p, -0.32f, -1.0f - (1.2f * taper + jag), z - len, 0.32f, -0.95f, z, c, light);
			box(vc, p, -0.32f, 0.95f, z - len, 0.32f, 1.0f + (0.95f * taper + jag2), z, k % 2 == 0 ? BONE_SHADE : BONE, light);
			// a notch of darker bone where the segments meet
			box(vc, p, -0.34f, -1.0f - 0.25f, z - 0.25f, 0.34f, -0.95f, z, BONE_DARK, light);
			z -= len;
		}
		// the point
		box(vc, p, -0.38f, -0.85f, -23.0f, 0.38f, 0.85f, -21.6f, BONE_SHADE, light);
		box(vc, p, -0.26f, -0.45f, TIP_Z, 0.26f, 0.45f, -22.9f, BONE, light);
		// blood: streaks along both flats (just proud of the bone), darker at their ends
		float[][] streaks = { { 1, -3.2f, -9.5f, -0.25f, 0.25f }, { -1, -4.5f, -12.5f, 0.05f, 0.6f }, { 1, -11.5f, -16.5f, -0.75f, -0.3f },
				{ -1, -14f, -20.5f, -0.4f, 0.05f }, { 1, -17.5f, -22.2f, 0.2f, 0.55f } };
		for (float[] st : streaks) {
			float x = st[0] * 0.82f;
			float x0 = st[0] > 0 ? x - 0.05f : x - 0.06f, x1 = st[0] > 0 ? x + 0.06f : x + 0.05f;
			box(vc, p, x0, st[3], st[2], x1, st[4], st[1], BLOOD, light);
			box(vc, p, x0 - 0.01f, st[3] - 0.08f, st[1] - 0.8f, x1 + 0.01f, st[4] + 0.08f, st[1], BLOOD_WET, light);
			box(vc, p, x0 - 0.01f, st[3] + 0.05f, st[2], x1 + 0.01f, st[4] - 0.05f, st[2] + 0.6f, BLOOD_DARK, light);
		}
		// runs of blood down the bottom edge, hanging off it
		float[][] runs = { { -4.2f, 1.6f }, { -9.8f, 1.1f }, { -15.4f, 1.4f } };
		for (float[] r : runs) {
			float edge = 1.0f + 0.95f * (1f - (-r[0] - 2.7f) / 2.1f / 10f);
			box(vc, p, -0.36f, edge - 0.2f, r[0] - 0.7f, 0.36f, edge + 0.15f, r[0] + 0.5f, BLOOD, light);
			box(vc, p, -0.2f, edge, r[0] - 0.2f, 0.2f, edge + r[1], r[0] + 0.2f, BLOOD_WET, light);
			box(vc, p, -0.28f, edge + r[1] - 0.1f, r[0] - 0.28f, 0.28f, edge + r[1] + 0.4f, r[0] + 0.28f, BLOOD_DARK, light);
		}
		// the tip: blood over the point and a drop that swells and falls
		box(vc, p, -0.4f, -0.5f, -23.2f, 0.4f, 0.9f, -22.2f, BLOOD, light);
		float drop = wet ? (now % 50f) / 50f : 0.5f;
		float dl = 0.4f + drop * 1.4f;
		box(vc, p, -0.16f, 0.6f, TIP_Z + 0.6f, 0.16f, 0.6f + dl, TIP_Z + 0.95f, BLOOD_WET, light);
		box(vc, p, -0.24f, 0.6f + dl - 0.1f, TIP_Z + 0.5f, 0.24f, 0.6f + dl + 0.38f, TIP_Z + 1.05f, BLOOD_DARK, light);

		// the crossguard: congealed blood, blobby, glossy, a few drops hanging under it
		box(vc, p, -1.15f, -4.0f, -2.75f, 1.15f, 4.0f, -1.35f, BLOOD, light);
		box(vc, p, -1.6f, -1.7f, -3.05f, 1.6f, 1.7f, -1.05f, BLOOD_DARK, light); // the middle swells round the blade
		box(vc, p, -1.45f, -5.1f, -2.95f, 1.45f, -3.3f, -1.15f, BLOOD, light); // the two ends: lumps
		box(vc, p, -1.45f, 3.3f, -2.95f, 1.45f, 5.1f, -1.15f, BLOOD, light);
		box(vc, p, -1.2f, -5.6f, -2.7f, 1.2f, -4.9f, -1.4f, BLOOD_WET, light);
		box(vc, p, -1.2f, 4.9f, -2.7f, 1.2f, 5.5f, -1.4f, BLOOD_WET, light);
		box(vc, p, -1.0f, -2.4f, -3.2f, 1.0f, 2.4f, -2.9f, BLOOD_WET, light); // a glossy lip on the blade side
		// the gloss: bright wet highlights on the top and the faces
		box(vc, p, -0.5f, -5.68f, -2.4f, 0.3f, -5.55f, -1.7f, BLOOD_GLOSS, light);
		box(vc, p, 1.46f, -4.6f, -2.5f, 1.52f, -3.6f, -2.0f, BLOOD_GLOSS, light);
		box(vc, p, -1.52f, 3.7f, -2.6f, -1.46f, 4.5f, -2.1f, BLOOD_GLOSS, light);
		box(vc, p, 1.61f, -0.9f, -2.7f, 1.67f, 0.4f, -2.3f, BLOOD_GLOSS, light);
		box(vc, p, -1.67f, -0.2f, -2.5f, -1.61f, 0.9f, -2.1f, BLOOD_GLOSS, light);
		// drops hanging from the guard
		box(vc, p, -0.22f, 5.5f, -2.25f, 0.22f, 6.7f, -1.85f, BLOOD_WET, light);
		box(vc, p, -0.3f, 6.5f, -2.32f, 0.3f, 7.05f, -1.78f, BLOOD_DARK, light);
		box(vc, p, 0.5f, 1.7f, -2.0f, 0.85f, 2.9f, -1.6f, BLOOD_WET, light);
		box(vc, p, 0.45f, 2.75f, -2.05f, 0.9f, 3.2f, -1.55f, BLOOD_DARK, light);

		// the grip: the arm's skin wrapped round, torn, raw flesh between
		box(vc, p, -0.85f, -0.85f, -1.4f, 0.85f, 0.85f, 3.0f, SKIN, light);
		box(vc, p, -0.95f, -0.95f, -0.3f, 0.95f, 0.95f, 0.35f, FLESH, light);
		box(vc, p, -0.95f, -0.95f, 1.5f, 0.95f, 0.95f, 2.0f, FLESH, light);
		box(vc, p, -0.9f, 0.6f, 0.35f, 0.9f, 0.92f, 1.5f, SKIN_DARK, light); // a fold of skin
		box(vc, p, 0.86f, -0.3f, 2.0f, 0.96f, 0.5f, 2.9f, BLOOD, light);
		// the pommel: the knuckle end of the bone, flesh still clinging
		box(vc, p, -1.05f, -1.05f, 3.0f, 1.05f, 1.05f, 4.1f, BONE_SHADE, light);
		box(vc, p, -0.75f, -0.75f, 4.1f, 0.75f, 0.75f, 4.5f, BONE, light);
		box(vc, p, -1.12f, 0.4f, 2.9f, 1.12f, 1.12f, 3.5f, FLESH, light);
	}

	/**
	 * Rib Cage Guard: curved ribs of bone springing up round his chest (the pose at the body: model px, +y down, front -z),
	 * snapping out over the first ticks and crumbling back at the end.
	 */
	static void ribs(PoseStack pose, MultiBufferSource buffers, int light, float age) {
		float out = Mth.clamp(age / 4f, 0f, 1f);
		float in = Mth.clamp((AndyBoneSword.GUARD_TICKS - age) / 5f, 0f, 1f);
		float k = Math.min(out * out * (3f - 2f * out), in);
		if (k <= 0.01f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose p = pose.last();
		for (int r = 0; r < 6; r++) {
			float y = -1f + r * 2.3f;
			float rad = (8.5f - Math.abs(r - 2.5f) * 0.6f) * (0.5f + 0.5f * k);
			int segs = 9;
			for (int i = 0; i < segs; i++) {
				float a0 = Mth.lerp(i / (float) segs, -1.25f, 1.25f) * k;
				float a1 = Mth.lerp((i + 1) / (float) segs, -1.25f, 1.25f) * k;
				float x0 = Mth.sin(a0) * rad, z0 = -Mth.cos(a0) * rad;
				float x1 = Mth.sin(a1) * rad, z1 = -Mth.cos(a1) * rad;
				float droop = (i - segs * 0.5f) * (i - segs * 0.5f) * 0.03f;
				box(vc, p, Math.min(x0, x1) - 0.35f, y + droop - 0.45f, Math.min(z0, z1) - 0.35f, Math.max(x0, x1) + 0.35f, y + droop + 0.45f,
						Math.max(z0, z1) + 0.35f, (i + r) % 3 == 0 ? BONE_SHADE : BONE, light);
			}
			// blood where the rib broke out of him
			box(vc, p, -0.7f, y - 0.6f, -2.6f, 0.7f, y + 0.6f, -2.0f, BLOOD, light);
		}
		// the sternum down the middle, in front
		float zs = -8.6f * (0.5f + 0.5f * k);
		box(vc, p, -0.7f, -1.4f, zs - 0.5f, 0.7f, 11.5f, zs + 0.5f, BONE_SHADE, light);
	}

	/** A shard of the Splinter Volley, pointing along +z (the pose at the shard's middle). */
	static void splinter(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose p = pose.last();
		box(vc, p, -0.45f, -0.45f, -1.6f, 0.45f, 0.45f, 0.8f, BONE, light);
		box(vc, p, -0.28f, -0.28f, 0.8f, 0.28f, 0.28f, 1.9f, BONE_SHADE, light);
		box(vc, p, -0.5f, -0.5f, -2.1f, 0.5f, 0.5f, -1.5f, BLOOD, light);
	}

	// ---------------------------------------------------------------- boxes

	private static final int[][] FACES = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
	private static final float[][] NORMALS = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

	/** A box in model px tinted {@code rgb} (shaded a little per face so the blobs read in flat light). */
	static void box(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
		Matrix4f m = pose.pose();
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f(((i & 1) == 0 ? x0 : x1) / 16f, ((i & 2) == 0 ? y0 : y1) / 16f, ((i & 4) == 0 ? z0 : z1) / 16f);
		}
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		for (int f = 0; f < 6; f++) {
			int[] fi = FACES[f];
			float[] n = NORMALS[f];
			float shade = f == 2 ? 1.08f : f == 3 ? 0.8f : 1f;
			int rr = Math.min(255, (int) (r * shade)), gg = Math.min(255, (int) (g * shade)), bb = Math.min(255, (int) (b * shade));
			for (int k = 0; k < 4; k++) {
				Vector3f v = c[fi[k]];
				vc.addVertex(m, v.x, v.y, v.z).setColor(rr, gg, bb, 255).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}
}
