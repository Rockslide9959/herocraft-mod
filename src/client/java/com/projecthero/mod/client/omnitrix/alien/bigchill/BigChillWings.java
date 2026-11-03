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
 * hinged at his spine. Each is three textured panels joined by hinges along the span, so the same wing is either
 * <ul>
 *   <li><b>wrapped</b> -- the panels turned round his back, his side and across his front like a cloak, at the cloak's size, or</li>
 *   <li><b>spread</b> -- the panels opened out nearly flat (and grown to the full wing): the uppers sweep up and out to sharp
 *       points well above his head, the lowers angle down and out to points by his knees.</li>
 * </ul>
 * When the kit's synced {@link BigChillKit#F_OPEN} comes on (the skin swaps to the uncloaked one) the wings start wrapped -- the
 * cloak -- and unfurl over 0.6 s: first uncurling off his body, then rising into the spread. When it goes off they fold back
 * into the cloak and vanish into the cloaked skin.
 * <p>
 * v0.17.2 (user: "make big chills flying animations look better please, make them look exactly how they look like in the reference
 * images I sent and also try to match the colours to the colours of his cloaked skin model version please"): the reference shape
 * (long curved wings, ragged scalloped trailing edges, black outline, darker shading along his body, light-cyan eye-spots, every
 * colour sampled from {@code big_chill_cloaked.png}); in flight deep, slow beats (one per wing-beat sound, 24 ticks) -- the uppers
 * leading, sweeping up / forward and down / back with the tips lagging the stroke, the lowers a beat behind -- the body bobbing
 * on each downstroke; flying fast he leans forward a little ({@link #bodyTilt}) with the wings swept back.
 * {@code big_chill_wings.png}, scratchpad/recal_v0172/big_chill/gen_big_chill_wings.js.
 */
public final class BigChillWings {
	private BigChillWings() {
	}

	private static final ResourceLocation WINGS = ProjectHeroMod.id("textures/entity/omnitrix/big_chill_wings.png");
	/** Ticks to unfurl / fold: 0.6 s. */
	private static final float UNFURL_TICKS = 12f;

	/**
	 * Panel lengths (px, root -> tip) and chords (px) of the upper / lower wings, wrapped (the cloak's size, round his body) and
	 * spread (the full wings of the reference: the uppers' tips about 1.4x his height apart, the lowers down by his knees).
	 */
	private static final float[] UPPER_WRAP = { 8.6f, 5.6f, 8.2f }, LOWER_WRAP = { 8.3f, 5.0f, 6.4f };
	private static final float[] UPPER_SPREAD = { 11.5f, 9.0f, 11.5f }, LOWER_SPREAD = { 8.5f, 6.0f, 7.0f };
	private static final float UPPER_CHORD_WRAP = 15.5f, LOWER_CHORD_WRAP = 10.5f, UPPER_CHORD = 17.5f, LOWER_CHORD = 12.5f;
	private static final float UPPER_Y = -0.4f, LOWER_Y = 8.6f, UPPER_Z = 2.75f, LOWER_Z = 2.45f;
	/** The wing-beat's angular speed: one full beat per 24 ticks, the kit's wing-beat sound. */
	private static final float BEAT = Mth.TWO_PI / 24f;
	/** About his flying speed at a cruise (blocks per tick), for the lean and the sweep. */
	private static final float CRUISE = 0.75f;

	/**
	 * Per player {previous, now} of: the unfurl (0 wrapped .. 1 spread), the flight's strength (0 .. 1), the forward lean
	 * (degrees), the speed (0 hovering .. 1 fast) and the bank (degrees).
	 */
	private static final Map<AbstractClientPlayer, float[]> STATE = new WeakHashMap<>();
	private static final int UNF = 0, FLY = 2, LEAN = 4, SPEED = 6, BANK = 8;

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

	private static boolean flying(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & BigChillKit.F_FLYING) != 0 && !p.onGround();
	}

	public static void tick(AbstractClientPlayer p) {
		float[] s = STATE.computeIfAbsent(p, k -> new float[10]);
		for (int i = 0; i < 10; i += 2) {
			s[i] = s[i + 1];
		}
		s[UNF + 1] = open(p) ? Math.min(1f, s[UNF + 1] + 1f / UNFURL_TICKS) : Math.max(0f, s[UNF + 1] - 1f / UNFURL_TICKS);
		boolean fly = flying(p);
		s[FLY + 1] += ((fly ? 1f : 0f) - s[FLY + 1]) * 0.15f;
		// v0.17.2: the attitude from how far he actually moved (other players' velocity is not synced) -- forward leans him in a
		// little (up to 28 degrees), backward tips him back a touch, a climb stands him up, a turn or a sideways drift banks him
		double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		float fwd = (float) (-Mth.sin(yaw) * dx + Mth.cos(yaw) * dz);
		float side = (float) (-Mth.cos(yaw) * dx - Mth.sin(yaw) * dz);
		float horiz = (float) Math.sqrt(dx * dx + dz * dz);
		float lean = fly ? Mth.clamp(fwd / CRUISE * (fwd >= 0f ? 22f : 10f) - (float) dy * 18f, -10f, 28f) : 0f;
		float turn = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
		float bank = fly ? Mth.clamp(-turn * 1.4f - side / CRUISE * 12f, -14f, 14f) : 0f;
		s[LEAN + 1] += (lean - s[LEAN + 1]) * 0.15f;
		s[SPEED + 1] += ((fly ? Mth.clamp(horiz / CRUISE, 0f, 1f) : 0f) - s[SPEED + 1]) * 0.15f;
		s[BANK + 1] += (bank - s[BANK + 1]) * 0.15f;
	}

	private static float get(AbstractClientPlayer p, int what, float partial) {
		float[] s = STATE.get(p);
		return s == null ? 0f : Mth.lerp(partial, s[what], s[what + 1]);
	}

	/** The unfurl now, 0 wrapped .. 1 spread (eased). */
	public static float unfurl(AbstractClientPlayer p, float partial) {
		float[] s = STATE.get(p);
		return s == null ? (open(p) ? 1f : 0f) : smooth(Mth.lerp(partial, s[UNF], s[UNF + 1]));
	}

	/** How much he is flying (0 .. 1, eased). */
	public static float flight(AbstractClientPlayer p, float partial) {
		return get(p, FLY, partial);
	}

	/** How fast he is flying (0 hovering .. 1 cruising or faster, eased). */
	public static float speed(AbstractClientPlayer p, float partial) {
		return get(p, SPEED, partial);
	}

	/** The forward lean now (degrees, eased), scaled by how open the wings are. */
	public static float lean(AbstractClientPlayer p, float partial) {
		return get(p, LEAN, partial) * unfurl(p, partial);
	}

	/** The wing beat's phase: the uppers' roll follows sin(phase) (up at +1), the lowers lag it a beat. */
	public static float phase(float age) {
		return age * BEAT;
	}

	/** Whether the uncloaked skin (and the chest badge) shows: once the cloak has peeled a third of the way off him. */
	public static boolean uncloaked(AbstractClientPlayer p) {
		float[] s = STATE.get(p);
		return s == null ? open(p) : s[UNF + 1] > 0.34f;
	}

	/**
	 * v0.17.2 (user: "make them look exactly how they look like in the reference images"): the whole-body attitude for
	 * {@code StinkflyFlightPoseMixin} -- {pitch forward, roll} in degrees and the hover bob (blocks) -- or null when he is not a
	 * flying Big Chill. Upright while he hovers, rising a little on each downstroke; a small forward lean into his speed.
	 */
	public static float[] bodyTilt(AbstractClientPlayer p, float partial) {
		if (!(AlienClients.shownKit(p) instanceof BigChillClient) || (Omnitrix.state(p).kitFlags & BigChillKit.F_DIVING) != 0) {
			return null;
		}
		float fly = flight(p, partial) * unfurl(p, partial);
		if (fly < 0.02f) {
			return null;
		}
		float age = p.tickCount + partial;
		float sp = speed(p, partial);
		// the downstroke (the uppers falling) lifts him; the lift lands a moment after the stroke
		float bob = -Mth.sin(phase(age) - 0.7f) * 0.07f * (1f - sp * 0.7f) * fly;
		return new float[] { get(p, LEAN, partial) * fly, get(p, BANK, partial) * fly, bob };
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age, int alpha) {
		float[] s = STATE.get(p);
		if (s == null || (s[UNF] <= 0f && s[UNF + 1] <= 0f && !open(p))) {
			return; // wrapped and closed: the cloaked skin is the cloak
		}
		float e = smooth(Mth.lerp(partial, s[UNF], s[UNF + 1]));
		float fly = Mth.lerp(partial, s[FLY], s[FLY + 1]);
		float sp = Mth.lerp(partial, s[SPEED], s[SPEED + 1]);
		// first the cloak uncurls off his body, then the wings rise and sweep into the spread
		float uncurl = smooth(e * 1.5f);
		float rise = smooth((e - 0.25f) / 0.75f);
		float ph = phase(age);
		int argb = (Mth.clamp(alpha, 0, 255) << 24) | 0xFFFFFF;
		VertexConsumer vc = buffers.getBuffer(alpha < 255 ? RenderType.entityTranslucent(WINGS) : RenderType.entityCutoutNoCull(WINGS));
		AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				// the lower pair first, a hair inside the upper (the cloak's two layers)
				wing(pose, vc, light, argb, side, false, uncurl, rise, fly, sp, ph, age);
				wing(pose, vc, light, argb, side, true, uncurl, rise, fly, sp, ph, age);
			}
		});
	}

	private static void wing(PoseStack pose, VertexConsumer vc, int light, int argb, int side, boolean upper, float uncurl, float rise, float fly,
			float sp, float ph, float age) {
		float[] wrap = upper ? UPPER_WRAP : LOWER_WRAP, spread = upper ? UPPER_SPREAD : LOWER_SPREAD;
		float[] lens = new float[3];
		float total = 0f;
		for (int k = 0; k < 3; k++) {
			lens[k] = Mth.lerp(rise, wrap[k], spread[k]);
			total += lens[k];
		}
		float chord = upper ? Mth.lerp(rise, UPPER_CHORD_WRAP, UPPER_CHORD) : Mth.lerp(rise, LOWER_CHORD_WRAP, LOWER_CHORD);
		// the beat: deep and slow in flight (the lowers a beat behind and shallower), a faint breath while he stands with them open
		float p = upper ? ph : ph - 1.1f;
		float depth = (upper ? 0.45f : 0.28f) * (1f - sp * 0.35f);
		float beat = Mth.sin(p) * depth * fly + Mth.sin(age * 0.05f) * 0.04f * (1f - fly);
		// the stroke's speed: the wings swing forward on the downstroke and back on the upstroke, the tips lagging behind
		float stroke = Mth.cos(p) * fly;
		// spread: the uppers raised up and out, the lowers out and down, nearly flat behind him like the references; flying fast,
		// swept back along him and carried a little lower. The panels flex at the hinges, the tips trailing the stroke.
		float roll = rise * (upper ? 0.8f - sp * 0.25f + beat : -0.72f + sp * 0.15f + beat);
		float sweep = rise * ((upper ? 0.12f : 0.2f) + sp * 0.75f + stroke * (upper ? 0.18f : 0.1f));
		float wrapHinge = side * Mth.HALF_PI;
		float hinge = Mth.lerp(uncurl, wrapHinge, -side * (0.08f + sp * 0.08f - stroke * (upper ? 0.14f : 0.08f)));
		pose.pushPose();
		pose.translate(side * 0.6f / 16f, (upper ? UPPER_Y : LOWER_Y) / 16f, (upper ? UPPER_Z : LOWER_Z) / 16f);
		pose.mulPose(Axis.YP.rotation(-side * sweep));
		pose.mulPose(Axis.ZP.rotation(-side * roll));
		float cum = 0f;
		float cellV = upper ? 0f : 0.5f;
		for (int k = 0; k < 3; k++) {
			if (k > 0) {
				pose.translate(side * lens[k - 1] / 16f, 0f, 0f);
				pose.mulPose(Axis.YP.rotation(hinge));
			}
			float x1 = side * lens[k] / 16f, y1 = chord / 16f;
			float u0 = cum / total * 0.5f, u1 = (cum + lens[k]) / total * 0.5f;
			float v0 = cellV, v1 = cellV + 0.5f;
			PoseStack.Pose last = pose.last();
			float off = 0.0025f;
			// back (+z: behind him spread, outward wrapped) -- the cloak's outside
			AlienParts.quad(vc, last, argb, light, 0f, 0f, off, x1, 0f, off, x1, y1, off, 0f, y1, off, 0, 0, 1, u0, v0, u1, v1);
			// front (-z) -- the face the reference shows, the eye-spots bright
			AlienParts.quad(vc, last, argb, light, 0f, 0f, -off, x1, 0f, -off, x1, y1, -off, 0f, y1, -off, 0, 0, -1, u0 + 0.5f, v0, u1 + 0.5f, v1);
			cum += lens[k];
		}
		pose.popPose();
	}
}
