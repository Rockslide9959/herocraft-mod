package com.projecthero.mod.client.omnitrix.alien.bigchill;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.alien.BigChillClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "uncloaked i think the badge position is perfect but give him his wings please, make the cloak unfurl from around
 * his body to make his wings since his wings are his cloak"): Big Chill's four moth wings -- two big upper, two smaller lower,
 * hinged at his spine. Each is three textured panels joined by hinges along the chord, so the same wing is either
 * <ul>
 *   <li><b>wrapped</b> -- the panels turned round his back, his side and across his front like a cloak (the cloak's blue outside,
 *       the ragged hem), or</li>
 *   <li><b>spread</b> -- the panels flat in one plane behind him, the uppers raised up and out, the lowers out and down, swept
 *       back a little (the navy inside with cyan veins showing from the front, the blue outside from behind).</li>
 * </ul>
 * When the kit's synced {@link BigChillKit#F_OPEN} comes on (the skin swaps to the uncloaked one) the wings start wrapped -- the
 * cloak -- and unfurl over 0.6 s: first uncurling off his body, then rising into the spread. When it goes off they fold back
 * into the cloak and vanish into the cloaked skin. Slow wing beats while he flies. {@code big_chill_wings.png},
 * scratchpad/recal_v0172/big_chill/gen_big_chill_wings.js. No antennae (the user's skins only, plus the wings he asked for).
 */
public final class BigChillWings {
	private BigChillWings() {
	}

	private static final ResourceLocation WINGS = ProjectHeroMod.id("textures/entity/omnitrix/big_chill_wings.png");
	/** Ticks to unfurl / fold: 0.6 s. */
	private static final float UNFURL_TICKS = 12f;

	/** Panel lengths (px, root -> tip) and chord (px) of the upper / lower wings; root (body px) and how far behind his back. */
	private static final float[] UPPER = { 8.6f, 5.6f, 8.2f }, LOWER = { 8.3f, 5.0f, 6.4f };
	private static final float UPPER_CHORD = 15.5f, LOWER_CHORD = 10.5f;
	private static final float UPPER_Y = -0.4f, LOWER_Y = 8.6f, UPPER_Z = 2.75f, LOWER_Z = 2.45f;

	/** Per player {previous, now} of the unfurl (0 wrapped .. 1 spread) and of the flight beat's strength. */
	private static final Map<AbstractClientPlayer, float[]> STATE = new WeakHashMap<>();

	public static void prune(Minecraft mc) {
		if (mc.level == null) {
			STATE.clear();
			return;
		}
		STATE.keySet().removeIf(p -> p.isRemoved() || p.level() != mc.level || !(AlienClients.shownKit(p) instanceof BigChillClient));
	}

	private static boolean open(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & BigChillKit.F_OPEN) != 0;
	}

	public static void tick(AbstractClientPlayer p) {
		float[] s = STATE.computeIfAbsent(p, k -> new float[4]);
		s[0] = s[1];
		s[1] = open(p) ? Math.min(1f, s[1] + 1f / UNFURL_TICKS) : Math.max(0f, s[1] - 1f / UNFURL_TICKS);
		s[2] = s[3];
		boolean flying = (Omnitrix.state(p).kitFlags & BigChillKit.F_FLYING) != 0;
		s[3] += ((flying ? 1f : 0f) - s[3]) * 0.15f;
	}

	/** The unfurl now, 0 wrapped .. 1 spread (eased). */
	public static float unfurl(AbstractClientPlayer p, float partial) {
		float[] s = STATE.get(p);
		return s == null ? (open(p) ? 1f : 0f) : smooth(Mth.lerp(partial, s[0], s[1]));
	}

	/** Whether the uncloaked skin (and the chest badge) shows: once the cloak has peeled a third of the way off him. */
	public static boolean uncloaked(AbstractClientPlayer p) {
		float[] s = STATE.get(p);
		return s == null ? open(p) : s[1] > 0.34f;
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age, int alpha) {
		float[] s = STATE.get(p);
		if (s == null || (s[0] <= 0f && s[1] <= 0f && !open(p))) {
			return; // wrapped and closed: the cloaked skin is the cloak
		}
		float e = smooth(Mth.lerp(partial, s[0], s[1]));
		float fly = Mth.lerp(partial, s[2], s[3]);
		// first the cloak uncurls off his body, then the wings rise and sweep into the spread
		float uncurl = smooth(e * 1.5f);
		float rise = smooth((e - 0.25f) / 0.75f);
		// slow beats in flight (about one every 2 s); a faint breath while he stands with them open
		float beat = Mth.sin(age * 0.16f) * 0.3f * fly + Mth.sin(age * 0.05f) * 0.04f * (1f - fly);
		int argb = (Mth.clamp(alpha, 0, 255) << 24) | 0xFFFFFF;
		VertexConsumer vc = buffers.getBuffer(alpha < 255 ? RenderType.entityTranslucent(WINGS) : RenderType.entityCutoutNoCull(WINGS));
		AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				// the lower pair first, a hair inside the upper (the cloak's two layers)
				wing(pose, vc, light, argb, side, false, uncurl, rise, beat);
				wing(pose, vc, light, argb, side, true, uncurl, rise, beat);
			}
		});
	}

	private static void wing(PoseStack pose, VertexConsumer vc, int light, int argb, int side, boolean upper, float uncurl, float rise, float beat) {
		float[] lens = upper ? UPPER : LOWER;
		float chord = upper ? UPPER_CHORD : LOWER_CHORD;
		float total = lens[0] + lens[1] + lens[2];
		// spread: the uppers raised up and out, the lowers out and down; both swept back; the panels cupped back a touch
		float roll = rise * (upper ? 0.55f + beat : -0.45f + beat * 0.6f);
		float sweep = rise * (upper ? 0.35f : 0.5f) - beat * 0.15f * rise;
		float wrapHinge = side * Mth.HALF_PI;
		float hinge = Mth.lerp(uncurl, wrapHinge, -side * 0.12f);
		pose.pushPose();
		pose.translate(side * 0.6f / 16f, (upper ? UPPER_Y : LOWER_Y) / 16f, (upper ? UPPER_Z : LOWER_Z) / 16f);
		pose.mulPose(Axis.YP.rotation(-side * sweep));
		pose.mulPose(Axis.ZP.rotation(-side * roll));
		float cum = 0f;
		float cellU = 0f, cellV = upper ? 0f : 0.5f;
		for (int k = 0; k < 3; k++) {
			if (k > 0) {
				pose.translate(side * lens[k - 1] / 16f, 0f, 0f);
				pose.mulPose(Axis.YP.rotation(hinge));
			}
			float x1 = side * lens[k] / 16f, y1 = chord / 16f;
			float u0 = cellU + cum / total * 0.5f, u1 = cellU + (cum + lens[k]) / total * 0.5f;
			float v0 = cellV, v1 = cellV + 0.5f;
			PoseStack.Pose last = pose.last();
			float off = 0.0025f;
			// outside (+z: behind him spread, outward wrapped) -- the cloak's blue
			AlienParts.quad(vc, last, argb, light, 0f, 0f, off, x1, 0f, off, x1, y1, off, 0f, y1, off, 0, 0, 1, u0, v0, u1, v1);
			// inside (-z) -- the navy with the cyan veins
			AlienParts.quad(vc, last, argb, light, 0f, 0f, -off, x1, 0f, -off, x1, y1, -off, 0f, y1, -off, 0, 0, -1, u0 + 0.5f, v0, u1 + 0.5f, v1);
			cum += lens[k];
		}
		pose.popPose();
	}
}
