package com.projecthero.mod.client.ironman;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ironman.gantry.GantryTimeline;
import com.projecthero.mod.ironman.suit.PortableForm;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.16.7 (user) Era II stage 2: the <b>backpack</b> prop the Mk VII Silver Centurion ({@code PortableForms.MK7_BACKPACK})
 * and the Mk VIII Space ({@code MK8_THRUSTER_PACK}) share -- one class, per-suit colours and parts ({@link Style}). All of
 * it is drawn on the body (player-layer model space, px: +y down from the neck, +z the back, -x the wearer's right), off
 * the deploy clock (a fold plays the same frames backwards):
 * <ol>
 *   <li>0 .. dropFrom: packed on the back -- the core, the shoulder straps, the blue strip down the middle, four shell
 *       plates (the Mk VIII: twin white O2 tanks with gold caps and blue nozzles under the shell);</li>
 *   <li>dropFrom .. openTo: the straps slide back over the shoulders into the pack and the four shell plates bloom open,
 *       each swinging out on its outer hinge and splaying up / down like petals;</li>
 *   <li>openTo .. sealAt: plates stream out of the open core and travel round the sides of the body to wherever the suit is
 *       building at that moment (chest, the arms, the legs, the helmet) -- {@link #renderChips}, a pure function of the
 *       frame, so on a fold they fly back into the pack;</li>
 *   <li>closeFrom .. total: the shell closes again while the emptied pack folds flat into the suit's back -- the Mk VIII's
 *       O2 tanks slide in and stay on, flush on the suit ({@link #renderWornTanks} draws them from then on).</li>
 * </ol>
 * {@link #renderPacked} draws the closed pack on a player who simply carries it ({@code IronManPortableClient.BodyLayer}).
 */
final class BackpackProp implements IronManPortableClient.PortableProp {
	static final ResourceLocation TEXTURE = com.projecthero.mod.ProjectHeroMod.id("textures/entity/iron_man_pack.png");

	/** Swatch cells of {@link #TEXTURE} (4x4 each, a 64x16 strip). */
	static final int RED = 0, RED2 = 1, SILVER = 2, STRAP = 3, GLOW = 4, WHITE = 5, GOLD = 6, GREY = 7, SILVER2 = 8, WHITE2 = 9;
	/** v0.16.7 (user): the O2 tanks' contrast swatches (navy band, bright gold, the gauge's green glass, gunmetal). */
	static final int NAVY = 10, GOLD_BRIGHT = 11, GAUGE = 12, GUNMETAL = 13;

	/** A pack's look. */
	record Style(int core, int coreSide, int plateTop, int plateBottom, int chipA, int chipB, boolean tanks) {
	}

	static final Style MK7 = new Style(SILVER, SILVER2, RED, SILVER, RED, SILVER, false);
	static final Style MK8 = new Style(WHITE, WHITE2, WHITE, WHITE2, WHITE, GOLD, true);

	/** The shell plates' outer face (px, +z behind the back): just over the core, or over the tanks on the Mk VIII. */
	static final float CORE_Z0 = 2.2f, CORE_Z1 = 5.2f;
	/** How far the Mk VIII's tanks slide in to sit flush on the suit (px). */
	static final float TANK_SLIDE = 2.9f;
	/** Ticks one plate takes from the pack to the body, and how often one leaves. */
	static final float CHIP_FLIGHT = 9f, CHIP_EVERY = 0.35f;

	private final Style style;

	BackpackProp(Style style) {
		this.style = style;
	}

	// ======================================================================== the passes

	@Override
	public void renderBody(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			PlayerModel<AbstractClientPlayer> model, PortableForm form, float frame) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		PortableForm.Beats b = form.beats();
		float bloom = smooth((frame - b.dropFrom()) / (float) (b.openTo() - b.dropFrom()));
		float close = smooth((frame - b.closeFrom()) / (float) (b.total() - 3 - b.closeFrom()));
		float straps = 1f - smooth((frame - b.dropFrom()) / (float) (form.firstAppear(GantryTimeline.TORSO_TOP) + 4 - b.dropFrom()));
		pose.pushPose();
		model.body.translateAndRotate(pose);
		drawPack(pose, vc, light, bloom, close, straps, frame);
		pose.popPose();
		if (frame > b.openTo() - 1 && frame < b.sealAt() + 1) {
			renderChips(pose, vc, light, model, form, frame);
		}
	}

	/** The closed pack on a player who carries it (not deploying); {@code out} pushes it back over a worn chestplate. */
	void renderPacked(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<AbstractClientPlayer> model, float out) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		pose.pushPose();
		model.body.translateAndRotate(pose);
		pose.translate(0f, 0f, out / 16f);
		drawPack(pose, vc, light, 0f, 0f, 1f, 0f);
		pose.popPose();
	}

	/**
	 * v0.16.7 (user) fix, FIRST person: your own carried pack can't be seen on your back, so its two shoulder straps are
	 * drawn where they run down your chest -- over the shoulders and down the front, with the suit's colour on the chest
	 * buckles and the pack's glow on the left strap's status light. {@code pose} is in body space (as the body layer's,
	 * +y down from the neck, +z the back); only the front half is drawn (the rest is behind the camera anyway).
	 */
	void renderOwnStraps(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 2.5f;
			// over the shoulder (front half only) and down the chest to the bottom of the ribs
			box(pose, vc, light, x - 0.65f, -0.4f, -2.55f, x + 0.65f, 0.15f, 0.2f, STRAP);
			box(pose, vc, light, x - 0.65f, -0.4f, -2.55f, x + 0.65f, 8.6f, -2.15f, STRAP);
			// the buckle: a small plate in the pack's colour with a darker clasp
			box(pose, vc, light, x - 0.85f, 4.6f, -2.75f, x + 0.85f, 5.9f, -2.45f, style.core);
			box(pose, vc, light, x - 0.35f, 4.9f, -2.85f, x + 0.35f, 5.6f, -2.7f, style.tanks ? GOLD : RED2);
		}
		// a sternum strap across, and the pack's status light on the left strap
		box(pose, vc, light, -2.0f, 6.6f, -2.45f, 2.0f, 7.2f, -2.2f, STRAP);
		box(pose, vc, LightTexture.FULL_BRIGHT, 2.15f, 2.2f, -2.75f, 2.85f, 2.9f, -2.5f, GLOW);
	}

	/** The Mk VIII's O2 tanks left on the suit after a pack deploy (flush on the back). */
	static void renderWornTanks(PoseStack pose, MultiBufferSource buffers, int light, PlayerModel<AbstractClientPlayer> model) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		pose.pushPose();
		model.body.translateAndRotate(pose);
		drawTanks(pose, vc, light, -TANK_SLIDE + WORN_TANK_RAISE, 0f);
		pose.popPose();
	}

	/** v0.16.7 (user): the worn tanks sit a touch proud of the suit's back so they read on the white plate. */
	static final float WORN_TANK_RAISE = 0.35f;

	// ======================================================================== the pack

	/**
	 * The pack in body space. {@code bloom} 0..1 opens the shell, {@code close} 0..1 folds the emptied pack flat into the
	 * back (and closes the shell again), {@code straps} 1..0 slides the straps back into the pack.
	 */
	private void drawPack(PoseStack pose, VertexConsumer vc, int light, float bloom, float close, float straps, float frame) {
		float flat = 1f - close; // the core's depth left
		float z1 = Mth.lerp(flat, CORE_Z0 + 0.15f, CORE_Z1);
		float shrink = 1f - 0.12f * close;
		boolean coreGone = close >= 0.999f;
		// --- straps: the front strap pulls up over the shoulder, then the loop pulls back into the pack
		if (straps > 0.001f) {
			float front = smooth((straps - 0.5f) * 2f); // the front strap goes in the first half
			float loop = smooth(straps * 2f); // the loop in the second
			for (int s = -1; s <= 1; s += 2) {
				float x = s * 2.5f;
				float zFront = Mth.lerp(loop, CORE_Z0, -2.45f);
				box(pose, vc, light, x - 0.6f, -0.35f, zFront, x + 0.6f, 1.15f, 2.4f, STRAP);
				if (front > 0.001f) {
					box(pose, vc, light, x - 0.6f, -0.35f, -2.5f, x + 0.6f, Mth.lerp(front, -0.3f, 8.5f), -2.15f, STRAP);
				}
			}
		}
		// --- the core
		if (!coreGone) {
			box(pose, vc, light, -3.4f * shrink, 1.2f, CORE_Z0, 3.4f * shrink, 10.8f, z1, style.core);
			// side rails a shade darker
			box(pose, vc, light, -3.5f * shrink, 2f, CORE_Z0 + 0.4f, -3.2f * shrink, 10f, z1 - 0.3f, style.coreSide);
			box(pose, vc, light, 3.2f * shrink, 2f, CORE_Z0 + 0.4f, 3.5f * shrink, 10f, z1 - 0.3f, style.coreSide);
		}
		// --- the Mk VIII's tanks (slide in to the back as the pack folds flat)
		float tankBack = style.tanks ? -TANK_SLIDE * close : 0f;
		if (style.tanks) {
			drawTanks(pose, vc, light, tankBack, bloom * (1f - close));
		}
		float z0 = (style.tanks ? 7.6f : 5.4f);
		float zPlate = style.tanks ? z0 + tankBack : Mth.lerp(flat, CORE_Z0 + 0.2f, z0);
		// --- the glow strip down the middle (brightens as it opens)
		if (!coreGone) {
			float gz1 = style.tanks ? Mth.lerp(flat, CORE_Z0 + 0.3f, CORE_Z1 + 0.3f) : zPlate + 0.85f;
			int gl = bloom > 0.05f && close < 0.95f ? LightTexture.FULL_BRIGHT : Math.max(light, LightTexture.pack(12, 12));
			box(pose, vc, gl, -0.3f, 3f, z1 - 0.2f, 0.3f, 9f, Math.max(gz1, z1 + 0.1f), GLOW);
		}
		// --- the four shell plates: they bloom open on their outer hinges and close again as the pack folds flat
		float open = bloom * (1f - close);
		float plateScale = 1f - smooth((close - 0.55f) / 0.45f); // the closed shell melts into the back at the very end
		if (plateScale > 0.001f) {
			for (int row = 0; row < 2; row++) {
				for (int s = -1; s <= 1; s += 2) {
					drawPlate(pose, vc, light, s, row == 0, zPlate, open, plateScale, frame);
				}
			}
		}
	}

	/** One shell plate. {@code s} -1 = the wearer's right half (-x), +1 the left; {@code top} the upper row. */
	private void drawPlate(PoseStack pose, VertexConsumer vc, int light, int s, boolean top, float z0, float open, float scale,
			float frame) {
		float x0 = s * 0.3f, x1 = s * 3.6f; // inner / outer edge
		float y0 = top ? 1.0f : 6.1f, y1 = top ? 5.9f : 11.0f;
		float hingeY = (y0 + y1) * 0.5f;
		// a small wobble while open (the plates are live), none closed
		float wob = open * 0.05f * Mth.sin((frame + (top ? 0f : 3f) + s * 1.7f) * 0.6f);
		float angle = (float) Math.toRadians(112f) * open + wob; // swing out on the outer hinge
		float splay = (float) Math.toRadians(32f) * open; // top plates splay up, bottom ones down
		pose.pushPose();
		pose.translate(x1 / 16f, hingeY / 16f, (z0 + 0.6f * open) / 16f);
		pose.mulPose(Axis.YP.rotation(s * angle));
		pose.mulPose(Axis.ZP.rotation((top ? 1 : -1) * s * splay));
		pose.scale(scale, scale, scale);
		float lx0 = Math.min(x0, x1) - x1, lx1 = Math.max(x0, x1) - x1;
		box(pose, vc, light, lx0, y0 - hingeY, 0f, lx1, y1 - hingeY, 0.9f, top ? style.plateTop : style.plateBottom);
		// a darker inner face strip so the open plate reads as a shell
		box(pose, vc, light, lx0 + 0.3f, y0 - hingeY + 0.3f, -0.15f, lx1 - 0.3f, y1 - hingeY - 0.3f, 0f, GREY);
		pose.popPose();
	}

	/**
	 * The twin O2 tanks; {@code dz} slides them toward the back, {@code vent} 0..1 lights the nozzles. v0.16.7 (user): they
	 * read on the white Mk VIII now -- a cooler silver body a shade off the suit's white, two navy contrast bands, bright
	 * gold domed caps, a gunmetal mounting bracket tying the pair together and a small glowing gauge on the right tank.
	 */
	static void drawTanks(PoseStack pose, VertexConsumer vc, int light, float dz, float vent) {
		int lit = Math.max(light, LightTexture.pack(12, 12));
		for (int s = -1; s <= 1; s += 2) {
			float cx = s * 2.05f;
			float z0 = 5.1f + dz, z1 = 7.75f + dz;
			box(pose, vc, light, cx - 1.42f, 1.8f, z0, cx + 1.42f, 10.4f, z1, SILVER); // the body
			box(pose, vc, light, cx - 1.48f, 3.4f, z0 - 0.05f, cx + 1.48f, 4.3f, z1 + 0.06f, NAVY); // contrast bands
			box(pose, vc, light, cx - 1.48f, 8.1f, z0 - 0.05f, cx + 1.48f, 9.0f, z1 + 0.06f, NAVY);
			box(pose, vc, light, cx - 1.15f, 0.9f, z0 + 0.25f, cx + 1.15f, 1.85f, z1 - 0.25f, GOLD_BRIGHT); // the domed cap
			box(pose, vc, light, cx - 0.7f, 0.35f, z0 + 0.6f, cx + 0.7f, 0.95f, z1 - 0.6f, GOLD_BRIGHT);
			box(pose, vc, light, cx - 0.35f, -0.05f, z0 + 0.95f, cx + 0.35f, 0.4f, z1 - 0.95f, GUNMETAL); // the valve
			box(pose, vc, light, cx - 1.0f, 10.4f, z0 + 0.35f, cx + 1.0f, 11.9f, z1 - 0.35f, GUNMETAL); // the collar
			box(pose, vc, vent > 0.05f ? LightTexture.FULL_BRIGHT : lit, cx - 0.6f, 11.9f, z0 + 0.75f, cx + 0.6f, 12.25f,
					z1 - 0.9f, GLOW); // the nozzle
		}
		// the mounting bracket across both tanks
		box(pose, vc, light, -3.2f, 5.5f, 4.9f + dz, 3.2f, 6.2f, 7.3f + dz, GUNMETAL);
		// the gauge: a small glowing window on the right tank, pulsing slowly
		float pulse = 0.75f + 0.25f * Mth.sin((float) (net.minecraft.Util.getMillis() % 100000L) / 420f);
		int g = Mth.clamp((int) (15 * pulse), 9, 15);
		box(pose, vc, light, -2.05f - 0.75f, 5.75f, 7.75f + dz, -2.05f + 0.75f, 7.15f, 7.95f + dz, GUNMETAL); // the bezel
		box(pose, vc, LightTexture.pack(g, g), -2.05f - 0.5f, 6.0f, 7.9f + dz, -2.05f + 0.5f, 6.9f, 8.05f + dz, GAUGE);
	}

	// ======================================================================== the plates in flight

	/**
	 * Small plates stream out of the open pack, swing round the side of the body and land wherever the suit is building
	 * as they arrive (the stage windows of the form): the chest, then the arms, the legs, the helmet. Each is a pure
	 * function of the frame (a fold flies them back in).
	 */
	private void renderChips(PoseStack pose, VertexConsumer vc, int light, PlayerModel<AbstractClientPlayer> model,
			PortableForm form, float frame) {
		PortableForm.Beats b = form.beats();
		int first = b.openTo() - 2;
		int last = b.sealAt() - 4;
		int n = (int) ((last - first) / CHIP_EVERY);
		Vector3f start = new Vector3f(), end = new Vector3f(), ctl = new Vector3f();
		for (int i = 0; i < n; i++) {
			float launch = first + i * CHIP_EVERY;
			float t = (frame - launch) / CHIP_FLIGHT;
			if (t <= 0f || t >= 1f) {
				continue;
			}
			float r1 = hash(i, 1), r2 = hash(i, 2), r3 = hash(i, 3), r4 = hash(i, 4);
			if (!target(form, model, launch + CHIP_FLIGHT, r1, r2, r3, r4, end)) {
				continue;
			}
			// out of the open core ...
			bodyPoint(model, (r1 - 0.5f) * 5f, 2.5f + r2 * 7f, 6.2f, start);
			// ... round the side the target is on
			float side = Math.abs(end.x) > 0.8f ? Math.signum(end.x) : (r3 < 0.5f ? -1f : 1f);
			ctl.set(side * (8.5f + 2.5f * r4), Math.min(start.y, end.y) - 3f - 2f * r2, (start.z + end.z) * 0.5f + 3f);
			float e = t * t * (3f - 2f * t);
			float u = 1f - e;
			float x = u * u * start.x + 2 * u * e * ctl.x + e * e * end.x;
			float y = u * u * start.y + 2 * u * e * ctl.y + e * e * end.y;
			float z = u * u * start.z + 2 * u * e * ctl.z + e * e * end.z;
			float spin = (1f - e); // they tumble in flight and settle flat as they land
			float sz = 1f - 0.45f * e;
			int col = (i % 3) == 1 ? style.chipB : style.chipA;
			pose.pushPose();
			pose.translate(x / 16f, y / 16f, z / 16f);
			pose.mulPose(Axis.YP.rotationDegrees((360f * r3 + 300f * t) * spin));
			pose.mulPose(Axis.XP.rotationDegrees((180f * r1 + 260f * t) * spin));
			box(pose, vc, light, -1.7f * sz, -1.2f * sz, -0.35f, 1.7f * sz, 1.2f * sz, 0.35f, col);
			pose.popPose();
		}
	}

	/**
	 * Where a plate landing at {@code frame} goes (model px, root space): a point on the part building then -- one of the
	 * stages whose window holds {@code frame} (by {@code r1}), its spot by the stage's own progress (top-down).
	 */
	private static boolean target(PortableForm form, PlayerModel<AbstractClientPlayer> model, float frame, float r1, float r2,
			float r3, float r4, Vector3f out) {
		int[] live = new int[GantryTimeline.STAGES];
		int count = 0;
		for (int st = 0; st < GantryTimeline.STAGES; st++) {
			if (form.uses(st)) {
				int[] w = form.stageWindows()[st];
				if (frame >= w[0] && frame <= w[1]) {
					live[count++] = st;
				}
			}
		}
		if (count == 0) {
			return false;
		}
		int st = live[Math.min(count - 1, (int) (r1 * count))];
		int[] w = form.stageWindows()[st];
		float p = Mth.clamp((frame - w[0]) / Math.max(1f, w[1] - w[0]), 0f, 1f);
		float rx = r2 * 2f - 1f;
		switch (st) {
			case GantryTimeline.TORSO_TOP, GantryTimeline.TORSO_BOTTOM, GantryTimeline.JACKET -> {
				float y = st == GantryTimeline.TORSO_TOP ? 0.5f + p * 5f : st == GantryTimeline.TORSO_BOTTOM ? 6f + p * 5.5f : 0.5f + p * 11f;
				if (r3 < 0.75f) {
					partPoint(model.body, rx * 3.4f, y, -2.4f, out); // the chest front
				} else {
					partPoint(model.body, (r4 < 0.5f ? -1f : 1f) * 4.2f, y, rx * 1.6f, out); // the sides
				}
			}
			case GantryTimeline.R_ARM, GantryTimeline.L_ARM, GantryTimeline.R_SLEEVE, GantryTimeline.L_SLEEVE,
					GantryTimeline.R_GAUNTLET, GantryTimeline.L_GAUNTLET -> {
				boolean right = st == GantryTimeline.R_ARM || st == GantryTimeline.R_SLEEVE || st == GantryTimeline.R_GAUNTLET;
				ModelPart arm = right ? model.rightArm : model.leftArm;
				float y = st == GantryTimeline.R_GAUNTLET || st == GantryTimeline.L_GAUNTLET ? 3f + p * 6.5f
						: st == GantryTimeline.R_ARM || st == GantryTimeline.L_ARM ? 3f - p * 5f : -2f + p * 11.5f;
				float cx = right ? -1f : 1f;
				float a = r3 * Mth.TWO_PI;
				partPoint(arm, cx + 2.2f * Mth.cos(a), y, 2.2f * Mth.sin(a), out);
			}
			case GantryTimeline.THIGH_TOP, GantryTimeline.THIGH_BOTTOM, GantryTimeline.PANTS, GantryTimeline.R_BOOT,
					GantryTimeline.L_BOOT -> {
				boolean right = st == GantryTimeline.R_BOOT || (st != GantryTimeline.L_BOOT && r4 < 0.5f);
				ModelPart leg = right ? model.rightLeg : model.leftLeg;
				float y = st == GantryTimeline.THIGH_TOP ? p * 4f : st == GantryTimeline.THIGH_BOTTOM ? 4f + p * 4.5f
						: st == GantryTimeline.PANTS ? p * 12f : 8f + p * 4f;
				float a = r3 * Mth.TWO_PI;
				partPoint(leg, 2.2f * Mth.cos(a), y, 2.2f * Mth.sin(a), out);
			}
			case GantryTimeline.FACEPLATE -> partPoint(model.head, rx * 3.4f, -7.5f + p * 7f, -4.3f, out);
			default -> { // the helmet: over the top, the sides and the back of the head
				float a = (float) Math.PI * (0.15f + 0.7f * r3);
				partPoint(model.head, 4.3f * Mth.cos(a) * (r4 < 0.5f ? 1f : -1f), -8.3f + p * 7.5f * (r3 < 0.3f ? 0.2f : 1f),
						4.3f * Mth.sin(a), out);
			}
		}
		return true;
	}

	/** A point given in {@code part}'s local px, in root model space px (the part's pivot + rotation, as rendered). */
	static void partPoint(ModelPart part, float x, float y, float z, Vector3f out) {
		out.set(x, y, z);
		new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(out);
		out.add(part.x, part.y, part.z);
	}

	private static void bodyPoint(PlayerModel<AbstractClientPlayer> model, float x, float y, float z, Vector3f out) {
		partPoint(model.body, x, y, z, out);
	}

	// ======================================================================== drawing

	/** An axis-aligned box (px, min / max corners), every face one swatch cell of {@link #TEXTURE}. */
	static void box(PoseStack pose, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1,
			int cell) {
		float a = 1f / 16f;
		x0 *= a; y0 *= a; z0 *= a; x1 *= a; y1 *= a; z1 *= a;
		float u0 = (cell * 4 + 0.5f) / 64f, u1 = (cell * 4 + 3.5f) / 64f, v0 = 0.5f / 16f, v1 = 3.5f / 16f;
		PoseStack.Pose p = pose.last();
		// each face: normal, then 4 corners (top-left, top-right, bottom-right, bottom-left as seen from outside; -y is up)
		quad(p, vc, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, v0, u1, v1);
		quad(p, vc, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1);
		quad(p, vc, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v0, u1, v1);
		quad(p, vc, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, v0, u1, v1);
		quad(p, vc, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u0 + 0.0001f, v0 + 0.0001f);
		quad(p, vc, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v1, u0 + 0.0001f, v1);
	}

	private static void quad(PoseStack.Pose p, VertexConsumer vc, int light, float nx, float ny, float nz, float ax, float ay,
			float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float u0,
			float v0, float u1, float v1) {
		vc.addVertex(p, ax, ay, az).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, bx, by, bz).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, cx, cy, cz).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, dx, dy, dz).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(p, nx, ny, nz);
	}

	private static float hash(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** Is the Mk VIII chestplate worn by {@code player} (for the worn tanks)? */
	static boolean wearsMk8Chest(AbstractClientPlayer player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem a
				&& "mark_vii".equals(a.suitId());
	}
}
