package com.projecthero.mod.client.blackpanther;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.client.moonknight.MoonKnightCapeLayer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the comics Black Panther's high standing collar, a model now instead of paint (user: "Remove the cape and and high collar
 * from the skin and make it a model that reacts with the players movement").
 *
 * <p>A stiff curved wall round the back and sides of the neck, open at the front like the collar on the skin: tallest behind the head
 * (5 px), lower over the shoulders, down to 2 px at its two front ends, each flaring outward toward the top. It stands on a yoke over
 * the shoulders, and behind the back a short skirt runs down over the cape's top edge so the cape hangs from under it. Same cloth as
 * the cape (purple-grey with stiff ribs) with a gold edge along the top like the suit's trims ({@code black_panther_collar.png}).
 *
 * <p>It lives in the body's frame (so it follows the torso's turn and the crouch) and reacts: it turns with the head
 * a little (its inside clears the head's corners, so a look round never cuts through it), leans back when he looks up (up to 35 deg), and flares out with the same lagging
 * swing as the cape -- walking, running and jumping push it back, the cape's side sway twists it a little, a fast drop flares it
 * wide. v0.17.7: it builds pixel by pixel ({@link BlackPantherClothReveal#collar}) in step with the suit's spread reaching the
 * shoulders ({@link BlackPantherRender#collarProgress}) and comes apart the same way, backwards, when the suit drains away.
 * Like every player layer it is not drawn in first person.
 */
public class BlackPantherCollarLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/black_panther/black_panther_collar.png");
	/** Half the collar's sweep round the neck (deg from straight behind); the gap left at the front is the opening. */
	private static final float SWEEP = 110f;
	private static final int SEGMENTS = 20;
	/** Radius of the wall's base (px): its inside (minus THICK) is just outside the suit head's corners (5.7 px from the neck), so the head never cuts through it however it turns. */
	private static final float RADIUS = 6.35f;
	private static final float INNER_YOKE = 2.8f;
	private static final float THICK = 0.55f;
	/** Height of the wall's base above the top of the torso (px, y down): above the cape's own collar strip and the necklace. */
	private static final float BASE_Y = -0.75f;
	private static final float BACK_HEIGHT = 5.0f;
	private static final float END_HEIGHT = 2.0f;
	private static final float SKIRT = 1.9f;
	/** The wall's outward lean at rest (deg). */
	private static final float FLARE = 14f;

	// texture bands (v, of 32)
	private static final float V_OUT0 = 0f, V_OUT1 = 12f / 32f, V_IN0 = 12f / 32f, V_IN1 = 24f / 32f;
	private static final float V_RIM0 = 24f / 32f, V_RIM1 = 28f / 32f, V_YOKE0 = 28f / 32f, V_YOKE1 = 1f;

	public BlackPantherCollarLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !BlackPanther.hasPower(player)) {
			return;
		}
		BlackPantherRender.Anim anim = BlackPantherRender.anim(player, partialTick);
		// v0.17.7 (user: "make the cape build itself pixel by pixel aswell"): the collar stands at full height and builds texel by texel
		// (front ends over the shoulders first, round to the back of the neck) as the spread from the necklace reaches the shoulders
		float built = BlackPantherRender.collarProgress(anim.cover());
		if (built <= 0f) {
			return;
		}
		float rise = 1f;
		// the reactions: the cape's lagging swing (walk, run, jump, crouch) flares it, its side sway twists it
		float[] cloak = MoonKnightCapeLayer.cloakSwing(player, partialTick);
		float flare = FLARE + Mth.clamp((cloak[0] - 6f) * 0.12f, -4f, 16f);
		if (!player.onGround() && player.getY() - player.yo < -0.45) {
			flare += 10f; // a fast drop: the wind opens it wide
		}
		float yaw = Mth.clamp(netHeadYaw * 0.3f, -20f, 20f) + cloak[1] * 0.3f;
		float pitch = headPitch < 0f ? Math.max(headPitch * 0.6f, -35f) : headPitch * 0.1f;

		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BlackPantherClothReveal.collar(TEXTURE, built)));
		draw(vc, pose.last(), light, rise, flare);
		pose.popPose();
	}

	/** The wall's height at {@code a} deg round from straight behind: tallest at the back, tapering to the open front. */
	private static float height(float a) {
		float t = Math.abs(a) / SWEEP;
		return END_HEIGHT + (BACK_HEIGHT - END_HEIGHT) * (float) Math.pow(1f - t, 1.3);
	}

	/** A point at {@code a} deg round the neck, {@code r} px out, {@code y} px (down), in blocks. */
	private static Vec3 at(float a, float r, float y) {
		float rad = a * Mth.DEG_TO_RAD;
		return new Vec3(r * Mth.sin(rad) / 16.0, y / 16.0, r * Mth.cos(rad) / 16.0);
	}

	private static void draw(VertexConsumer vc, PoseStack.Pose pose, int light, float rise, float flareDeg) {
		float flare = flareDeg * Mth.DEG_TO_RAD;
		float sin = Mth.sin(flare), cos = Mth.cos(flare);
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = -SWEEP + 2f * SWEEP * i / SEGMENTS;
			float a1 = -SWEEP + 2f * SWEEP * (i + 1) / SEGMENTS;
			float u0 = (float) i / SEGMENTS, u1 = (float) (i + 1) / SEGMENTS;
			float h0 = height(a0) * rise, h1 = height(a1) * rise;
			// the wall leans out: its top is further round and higher
			float rt0 = RADIUS + h0 * sin, rt1 = RADIUS + h1 * sin;
			float yt0 = BASE_Y - h0 * cos, yt1 = BASE_Y - h1 * cos;
			// the skirt behind the back, down over the cape's top edge (none over the shoulders)
			float s0 = SKIRT * rise * (float) Math.pow(Math.max(0f, Mth.cos(a0 * Mth.DEG_TO_RAD)), 1.5);
			float s1 = SKIRT * rise * (float) Math.pow(Math.max(0f, Mth.cos(a1 * Mth.DEG_TO_RAD)), 1.5);

			Vec3 ot0 = at(a0, rt0, yt0), ot1 = at(a1, rt1, yt1);
			Vec3 ob0 = at(a0, RADIUS - s0 * 1.4f, BASE_Y + s0), ob1 = at(a1, RADIUS - s1 * 1.4f, BASE_Y + s1);
			Vec3 it0 = at(a0, rt0 - THICK, yt0), it1 = at(a1, rt1 - THICK, yt1);
			Vec3 ib0 = at(a0, RADIUS - THICK, BASE_Y), ib1 = at(a1, RADIUS - THICK, BASE_Y);
			// outside (normal outward), inside (normal inward), the rim along the top
			MoonKnightCapeLayer.quad(vc, pose, light, ot0, ot1, ob1, ob0, u0, V_OUT0, u1, V_OUT1);
			MoonKnightCapeLayer.quad(vc, pose, light, it1, it0, ib0, ib1, u1, V_IN0, u0, V_IN1);
			MoonKnightCapeLayer.quad(vc, pose, light, it0, it1, ot1, ot0, u0, V_RIM0, u1, V_RIM1);
			// the yoke on the shoulders, from near the neck out to the wall
			Vec3 y0 = at(a0, INNER_YOKE, BASE_Y + 0.02f), y1 = at(a1, INNER_YOKE, BASE_Y + 0.02f);
			Vec3 w0 = at(a0, RADIUS, BASE_Y + 0.02f), w1 = at(a1, RADIUS, BASE_Y + 0.02f);
			MoonKnightCapeLayer.quad(vc, pose, light, y1, y0, w0, w1, u1, V_YOKE0, u0, V_YOKE1);
		}
		// the two open front ends
		for (int side = -1; side <= 1; side += 2) {
			float a = side * SWEEP;
			float h = height(a) * rise;
			float rt = RADIUS + h * sin, yt = BASE_Y - h * cos;
			Vec3 ot = at(a, rt, yt), ob = at(a, RADIUS, BASE_Y), it = at(a, rt - THICK, yt), ib = at(a, RADIUS - THICK, BASE_Y);
			MoonKnightCapeLayer.quad(vc, pose, light, ot, it, ib, ob, 0f, V_RIM0, 0.05f, V_RIM1);
		}
	}
}
