package com.projecthero.mod.client.beastboy.model;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's shark, HQ round: a blocky great white (about 3 blocks nose to tail) in his greens -- dark back over a jagged seam to
 * a pale belly, gill slits, a stepped dorsal fin, swept pectorals, keeled tail stock and a tall crescent tail; a hinged lower jaw with
 * rows of teeth top and bottom, eyes with a catch-light, blink lids and the white nictitating membrane that rolls up when it bites.
 * Swims with a side-to-side wave running down head, body, tail and caudal fin (faster and wider with speed), bends and banks into
 * turns, pitches with the swim; out of water it lies on its belly gasping and flops now and then.
 * Moves: bite / strike (lunge, snout up, jaw wide, eyes roll white, snap, head shake).
 */
public final class SharkModel {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/beastboy/art/shark.png");
	private static ModelPart root;
	private static Map<String, ModelPart> P;

	private SharkModel() {
	}

	private static ModelPart p(String n) {
		return P.get(n);
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, int tint, float flash, BeastPose s, SwimRig.State st) {
		if (root == null) {
			root = SeaShapes.shark();
			P = SeaShapes.sharkParts(root);
		}
		SeaArt.reset(root);
		animate(s, st);
		pose.pushPose();
		SeaArt.toModel(pose);
		SeaArt.draw(root, TEX, pose, buffers, light, tint, flash);
		pose.popPose();
	}

	static void animate(BeastPose s, SwimRig.State st) {
		float t = st.time;
		float wet = st.wetness;
		float dry = 1f - wet;
		float spd = st.speed01();
		ModelPart body = p("body"), head = p("head"), jaw = p("jaw"), t1 = p("tail1"), t2 = p("tail2"), cd = p("caudal");

		// ---- the swim wave: a side-to-side stroke travelling from the head to the tail fin
		float phase = t * 5.0f + st.dist * 2.2f;
		float amp = Mth.lerp(spd, 0.11f, 0.3f);
		float bite = s.isAny("bite", "strike") ? s.prog : -1f;
		float lunge = 0f, open = 0f, shake = 0f, roll = 0f;
		if (bite >= 0f) {
			lunge = bite < 0.4f ? SeaArt.smooth(bite / 0.4f) : 1f - SeaArt.smooth(SeaArt.span(bite, 0.55f, 1f));
			open = bite < 0.35f ? SeaArt.smooth(bite / 0.35f) : 1f - SeaArt.smooth(SeaArt.span(bite, 0.35f, 0.45f));
			float sh = SeaArt.span(bite, 0.45f, 0.88f);
			shake = sh > 0f && sh < 1f ? Mth.sin(sh * 22f) * (1f - sh) : 0f;
			roll = Mth.sin(SeaArt.span(bite, 0.15f, 0.75f) * Mth.PI);
			amp += lunge * 0.22f;
			phase += lunge * 6f;
		}
		float turnBend = Mth.clamp(st.turn * 0.22f, -0.35f, 0.35f) * wet;
		float w1 = Mth.sin(phase - 0.9f), w2 = Mth.sin(phase - 1.8f), w3 = Mth.sin(phase - 2.7f);

		// ---- land: lying on the belly, a flop every few seconds
		float flop = 0f, flopSide = 1f;
		if (dry > 0.01f) {
			float per = 2.8f;
			float ft = (t % per) / per;
			int n = (int) Math.floor(t / per);
			flopSide = (n % 2 == 0) ? 1f : -1f;
			flop = ft < 0.32f ? Mth.sin(ft / 0.32f * Mth.PI) : 0f;
		}
		float landAmp = 0.12f + flop * 0.55f;
		float landPh = t * 13f;
		amp = Mth.lerp(dry, amp, landAmp);
		if (dry > 0.01f) {
			w1 = Mth.lerp(dry, w1, Mth.sin(landPh - 0.6f));
			w2 = Mth.lerp(dry, w2, Mth.sin(landPh - 1.2f));
			w3 = Mth.lerp(dry, w3, Mth.sin(landPh - 1.8f));
		}

		// whole body: pitch with the swim, bank into turns, lunge forward; on land roll onto one side and hop on a flop
		float pitch = st.pitch * 0.9f * wet; // v0.17.4: the swim's pitch only (looking up or down no longer tilts a hovering shark)
		body.xRot = pitch - lunge * 0.12f;
		body.zRot = Mth.clamp(st.turn * 0.2f, -0.55f, 0.55f) * wet + dry * (0.22f * flopSide + flop * 0.25f * flopSide) + shake * 0.12f;
		body.yRot = amp * 0.22f * Mth.sin(phase) + shake * 0.2f; // v0.17.4: the body follows the look itself now
		body.z -= lunge * 6f;
		body.y -= wet * Mth.sin(t * 1.6f) * 0.4f + dry * flop * 3.5f;
		body.y += dry * 0.6f; // rest on the belly
		float breathe = Mth.sin(t * 2.6f);
		body.xScale = 1f + breathe * 0.012f;
		body.yScale = 1f + breathe * 0.012f;

		// head counters the body swing a little and looks where he looks
		head.yRot = -amp * 0.25f * Mth.sin(phase + 0.4f) - turnBend * 0.55f - s.yaw(0.5f) * 0.35f * wet + shake * 0.45f;
		head.xRot = -open * 0.32f - dry * flop * 0.25f + s.pitch(0.4f) * 0.25f * wet;

		// tail segments
		t1.yRot = amp * 0.55f * w1 + turnBend * 0.45f;
		t2.yRot = amp * 0.85f * w2 + turnBend * 0.55f;
		cd.yRot = amp * 1.15f * w3 + turnBend * 0.35f;
		float tailLift = dry * flop * 0.3f;
		t1.xRot = -pitch * 0.12f + tailLift;
		t2.xRot = -pitch * 0.12f + tailLift * 0.8f;
		cd.xRot = tailLift * 0.5f;

		// jaw: gill-pumping in water, gasping on land, the bite
		float gasp = dry * (0.12f + Math.max(0f, Mth.sin(t * 3.1f)) * 0.22f);
		jaw.xRot = 0.03f + wet * (0.03f + Math.max(0f, breathe) * 0.04f) + gasp + open * 0.85f;

		// fins: pectorals glide and trim, the inside fin lifts in a turn; flat on the ground on land
		float trim = Mth.sin(phase * 0.5f) * 0.06f * wet;
		ModelPart fl = p("finL"), fr = p("finR");
		fl.zRot = trim - turnBend * 0.6f + dry * 0.18f + pitch * 0.2f;
		fr.zRot = -trim - turnBend * 0.6f - dry * 0.18f - pitch * 0.2f;
		fl.yRot = -0.08f * spd * wet;
		fr.yRot = 0.08f * spd * wet;
		p("finTipL").zRot = 0.08f * Mth.sin(phase * 0.5f - 0.6f) * wet;
		p("finTipR").zRot = -0.08f * Mth.sin(phase * 0.5f - 0.6f) * wet;
		p("dorsal").zRot = -amp * 0.15f * w1; // the fin flexes against the stroke

		// eyes: an idle blink, the white membrane rolling up on the bite
		float blink = SwimRig.blink(t, 3);
		for (String sd : new String[] { "L", "R" }) {
			ModelPart lid = p("lid" + sd), nic = p("nic" + sd);
			lid.yScale = Math.max(0.01f, blink);
			lid.visible = blink > 0.02f;
			nic.yScale = Math.max(0.01f, roll);
			nic.visible = roll > 0.02f;
		}
	}
}
