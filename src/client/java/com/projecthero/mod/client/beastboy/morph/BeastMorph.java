package com.projecthero.mod.client.beastboy.morph;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.client.beastboy.BeastBodyRenderer;
import com.projecthero.mod.client.beastboy.BeastFormArt;
import com.projecthero.mod.client.beastboy.model.BeastPose;
import com.projecthero.mod.client.beastboy.model.QuickMorphs;
import com.projecthero.mod.client.beastboy.morph.MorphAnchors.Body;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.17.3 Beast Boy morph round: the transition every form change plays -- human to animal, animal to human, animal to animal (chains)
 * and the human-form quick morphs in and out. Both bodies are drawn at once through {@link MorphBuffers}: the old body's parts turn,
 * stretch and resize toward the new body's matching parts ({@link MorphAnchors}: the head lengthens into a muzzle, the torso tips onto
 * all fours or stretches long, arms swing into forelegs or out into wings, legs fuse into a tail) while it shrinks back into each part's
 * root, and the new body grows out of those roots along the same blended shape. Skin goes green, green energy runs along the seams
 * between parts, and the new body lands with a squash-and-stretch pop.
 *
 * <p><b>Timing:</b> {@link #TICKS} = 10 ticks (0.5 s) from the synced form change ({@code BeastBoyState.morph}: the form he left and the
 * game time), so every client watching plays the same transition; the form's stats and hitbox still switch at once on the server. A quick
 * morph that shows the whole animal (tiger pounce, snake strike) morphs in over its first 3 ticks and back out over its last 3; the
 * partial ones (gorilla arms, rhino / T-Rex head, elephant legs and trunk, tentacles) shrink the matching human parts into the growing
 * animal parts with the same green and glow.
 */
public final class BeastMorph {
	/** The whole transition, in ticks (0.5 s). */
	public static final int TICKS = 10;
	/** Quick morphs: the share of the quick morph spent morphing in (and, at the end, out) when the whole animal shows. */
	static final float QUICK_IN = 0.3f;

	private static final ResourceLocation PREVIEW_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
	private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.42f, 1.0f, 0.3f), 0.7f);
	private static final DustParticleOptions SPARK_BIG = new DustParticleOptions(new Vector3f(0.5f, 1.0f, 0.36f), 1.2f);

	/** Set while the vanilla player renderer is drawing his human body for a morph (the Beast Boy hooks then stand aside). */
	private static boolean humanPass;
	/** Per watched player: the last transition progress seen (for the landing puff). Client only; cleared on leaving a world. */
	private static final Map<Integer, Float> LAST_T = new HashMap<>();
	private static PlayerModel<AbstractClientPlayer> previewModel;

	private BeastMorph() {
	}

	public static boolean humanPass() {
		return humanPass;
	}

	public static void clear() {
		LAST_T.clear();
		SCALED.clear();
		MorphAnchors.clear();
		previewModel = null;
	}

	// ---------------------------------------------------------------- timing

	/** 0..1 through the form-change transition, or -1 when none is playing. */
	public static float progress(AbstractClientPlayer p, float partial) {
		BeastForm from = BeastBoy.morphFrom(p);
		if (from == null || from == BeastBoy.form(p)) {
			return -1f;
		}
		float age = BeastBoy.morphAge(p, partial);
		return age < 0f || age >= TICKS ? -1f : age / TICKS;
	}

	private static float s(float t) {
		return BeastPose.smooth(t);
	}

	private static float clamp01(float t) {
		return Mth.clamp(t, 0f, 1f);
	}

	/** Shape blend 0..1: the frames move from the old body's anchors to the new body's. */
	static float shape(float t) {
		return s(t / 0.68f);
	}

	/** Old body: the head draws back first (so two heads never stack), then the limbs and tail, the torso last. */
	private static final float[] SRC_DELAY = { -0.05f, 0.06f, 0f, 0f, 0f, 0f, 0f };
	/** New body: torso first, then head and limbs, the tail and wings last. */
	private static final float[] TGT_DELAY = { 0.02f, 0f, 0.03f, 0.03f, 0.03f, 0.03f, 0.05f };
	/** The new body's parts break out of the old one at this fraction of their size and swell to full. */
	static final float EMERGE = 0.4f;

	/**
	 * The old body first reshapes on its own (the shape blend runs from the start), then shrinks into its parts' roots between 0.36 and
	 * about 0.72 while the new body (its shape a step ahead) breaks out of it at 40% and swells to full size over the same stretch.
	 */
	static float srcK(float t, int i) {
		return 1f - s((t - 0.36f - SRC_DELAY[i]) / 0.3f);
	}

	static float tgtK(float t, int i) {
		float start = 0.34f + TGT_DELAY[i];
		return t < start ? 0f : Mth.lerp(s((t - start) / 0.3f), EMERGE, 1f);
	}

	/** Vertical squash/stretch: a small squash as it starts, a spring when the new body lands (exactly 1 at both ends). */
	static float stretch(float t) {
		if (t < 0.14f) {
			return 1f - 0.07f * Mth.sin(Mth.PI * t / 0.14f);
		}
		if (t > 0.72f) {
			float u = clamp01((t - 0.72f) / 0.28f);
			return 1f + 0.15f * Mth.sin(u * Mth.TWO_PI) * (1f - u) * (1f - u);
		}
		return 1f;
	}

	static float green(float t) {
		return s(t / 0.18f) * (1f - s((t - 0.7f) / 0.3f));
	}

	// ---------------------------------------------------------------- the core

	/** Draws one body into the buffers it is given. */
	@FunctionalInterface
	interface Draw {
		void draw(MultiBufferSource buffers);
	}

	/**
	 * Draws the morph from {@code bs} to {@code bt} at {@code t}. {@code base}/{@code nrm}: the pose at his feet turned to his body yaw
	 * (+Z front). Each side has a skin draw and a glow draw (the same body again; for the vanilla player only its model).
	 */
	static void blend(Matrix4f base, Matrix3f nrm, Body bs, Body bt, float t, Draw src, Draw srcGlow, Draw tgt, Draw tgtGlow,
			MultiBufferSource buffers) {
		float f = shape(t);
		float sy = stretch(t), sxz = 1f / Mth.sqrt(sy);
		float g = green(t);
		// a faint glow all over, a surge as the new body breaks out and a flick as it lands; brightest along the seams between parts
		float glowBase = 0.06f * g + 0.3f * Math.max(0f, 1f - Math.abs(t - 0.48f) / 0.14f) + 0.2f * Math.max(0f, 1f - Math.abs(t - 0.74f) / 0.08f);
		float glowSeam = 1.1f * g;
		// the old body
		MorphField fs = new MorphField(bs, bs, bt, f);
		boolean any = false;
		for (int i = 0; i < MorphAnchors.COUNT; i++) {
			fs.k[i] = srcK(t, i);
			any |= fs.k[i] > 0.002f;
		}
		if (any) {
			fs.sy = sy;
			fs.sxz = sxz;
			fs.green = g;
			fs.glowBase = glowBase;
			fs.glowSeam = glowSeam;
			src.draw(new MorphBuffers(buffers, fs, base, nrm, false));
			if (glowBase + glowSeam > 0.03f && srcGlow != null) {
				srcGlow.draw(new MorphBuffers(buffers, fs, base, nrm, true));
			}
		}
		// the new body
		MorphField ft = new MorphField(bt, bs, bt, shape(t + 0.1f)) /* the new body runs a little ahead into its own shape */;
		any = false;
		for (int i = 0; i < MorphAnchors.COUNT; i++) {
			ft.k[i] = tgtK(t, i);
			any |= ft.k[i] > 0.002f;
		}
		if (any) {
			ft.sy = sy;
			ft.sxz = sxz;
			ft.green = g;
			ft.glowBase = glowBase;
			ft.glowSeam = glowSeam;
			tgt.draw(new MorphBuffers(buffers, ft, base, nrm, false));
			if (glowBase + glowSeam > 0.03f && tgtGlow != null) {
				tgtGlow.draw(new MorphBuffers(buffers, ft, base, nrm, true));
			}
		}
	}

	/** Quick-morph partials: his body (the animal part rides on it, QuickMorphs) with green skin and a glow at the hand-overs. */
	static void partial(Matrix4f base, Matrix3f nrm, float green, float glow, Draw human, Draw humanGlow, MultiBufferSource buffers) {
		MorphField fh = new MorphField(MorphAnchors.human());
		fh.green = green;
		fh.glowBase = glow * 0.5f;
		fh.glowSeam = glow * 0.8f;
		human.draw(new MorphBuffers(buffers, fh, base, nrm, false));
		if (glow > 0.03f && humanGlow != null) {
			humanGlow.draw(new MorphBuffers(buffers, fh, base, nrm, true));
		}
	}

	/** Green over his skin while a partial is out (strongest at the hand-overs). */
	static float partialGreen(float q) {
		return Math.max(QuickMorphs.animalScale(q) > 0f ? 0.45f : 0f, Math.min(1f, QuickMorphs.glow(q)) * 0.85f);
	}

	/** Quick morphs that show the whole animal (morphed in and out) rather than a part of it on his body. */
	static boolean wholeQuick(BeastForm quick) {
		return quick == BeastForm.TIGER || quick == BeastForm.SNAKE;
	}

	/** The quick snake is the whole snake, 2.4x life size (QuickMorphs.snake). */
	static final float QUICK_SNAKE_SCALE = 2.4f;

	static Body quickBody(BeastForm quick) {
		Body b = MorphAnchors.of(quick);
		return quick == BeastForm.SNAKE ? b.scaled(QUICK_SNAKE_SCALE) : b;
	}

	// ---------------------------------------------------------------- in game

	/**
	 * Called at the top of the player renderer: draws the transition (or a quick morph) and returns true when it did (the caller then
	 * skips its own drawing). The human body is the vanilla player renderer itself, run again with bending buffers.
	 */
	public static boolean render(PlayerRenderer r, AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
			int light) {
		if (humanPass || p.isInvisible() || !BeastBoy.hasPower(p)) {
			return false;
		}
		BeastForm form = BeastBoy.form(p);
		float t = progress(p, partial);
		BeastForm quick = form == BeastForm.HUMAN ? BeastBoy.quickForm(p) : null;
		if (t < 0f && quick == null) {
			return false;
		}
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		Matrix4f base = new Matrix4f(pose.last().pose()).rotateY(-bodyYaw * Mth.DEG_TO_RAD);
		Matrix3f nrm = new Matrix3f(pose.last().normal()).rotateY(-bodyYaw * Mth.DEG_TO_RAD);
		Draw human = b -> {
			humanPass = true;
			try {
				r.render(p, yaw, partial, pose, b, light);
			} finally {
				humanPass = false;
			}
		};
		Draw humanGlow = b -> {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
			pose.scale(-1f, -1f, 1f);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
			pose.translate(0f, -1.501f, 0f);
			r.getModel().renderToBuffer(pose, b.getBuffer(RenderType.entityCutoutNoCull(p.getSkin().texture())), light, OverlayTexture.NO_OVERLAY, -1);
			pose.popPose();
		};
		if (t >= 0f) {
			BeastForm from = BeastBoy.morphFrom(p);
			String mv = BeastBoy.move(p);
			if ("morph_in".equals(mv) || "morph_out".equals(mv)) {
				mv = null; // this transition replaces the old pop-in
			}
			String move = mv;
			float prog = mv == null ? 0f : BeastBoy.moveProgress(p, partial);
			Draw src = from == BeastForm.HUMAN ? human : b -> BeastBodyRenderer.renderWith(from, p, partial, pose, b, light, null, 0f);
			Draw srcGlow = from == BeastForm.HUMAN ? humanGlow : src;
			Draw tgt = form == BeastForm.HUMAN ? human : b -> BeastBodyRenderer.renderWith(form, p, partial, pose, b, light, move, prog);
			Draw tgtGlow = form == BeastForm.HUMAN ? humanGlow : tgt;
			blend(base, nrm, MorphAnchors.of(from), MorphAnchors.of(form), t, src, srcGlow, tgt, tgtGlow, buffers);
			return true;
		}
		float q = BeastBoy.quickProgress(p, partial);
		if (wholeQuick(quick)) {
			String mv = BeastBoy.move(p);
			float mp = mv == null ? q : BeastBoy.moveProgress(p, partial);
			Draw animal = quick == BeastForm.SNAKE ? b -> quickSnake(pose, bodyYaw, b, light, q, p.tickCount + partial)
					: b -> BeastBodyRenderer.renderWith(quick, p, partial, pose, b, light, mv, mp);
			Body bq = quickBody(quick);
			if (q < QUICK_IN) {
				blend(base, nrm, MorphAnchors.human(), bq, q / QUICK_IN, human, humanGlow, animal, animal, buffers);
			} else if (q < 1f - QUICK_IN) {
				animal.draw(buffers);
			} else {
				blend(base, nrm, bq, MorphAnchors.human(), (q - (1f - QUICK_IN)) / QUICK_IN, animal, animal, human, humanGlow, buffers);
			}
			return true;
		}
		if (!QuickMorphs.partial(quick)) {
			return false;
		}
		// a partial: his own renderer draws him, the part hidden / swelling (adjustModel) and the animal part riding on it (BeastQuickLayer)
		partial(base, nrm, partialGreen(q), Math.min(1f, QuickMorphs.glow(q)), human, humanGlow, buffers);
		return true;
	}

	/** The quick snake (QuickMorphs.snake at full size: the morph does the growing), feet origin. */
	private static void quickSnake(PoseStack pose, float bodyYaw, MultiBufferSource b, int light, float q, float age) {
		BeastPose s = new BeastPose();
		s.age = age;
		s.move = "strike";
		s.prog = clamp01((q - 0.15f) / 0.7f);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
		pose.scale(QUICK_SNAKE_SCALE, QUICK_SNAKE_SCALE, QUICK_SNAKE_SCALE);
		BeastFormArt.renderPose(BeastForm.SNAKE, s, pose, b, light, BeastBodyRenderer.TINT);
		pose.popPose();
	}

	// ---------------------------------------------------------------- sparks and the landing puff

	/** Client tick: green sparks along the seams of every player mid-morph, and a puff when the new body lands. */
	public static void clientTick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		if (LAST_T.size() > 64) {
			LAST_T.clear();
		}
		RandomSource rnd = mc.level.random;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (!BeastBoy.hasPower(p) || p.isInvisible()) {
				continue;
			}
			float t = progress(p, 0f);
			if (t < 0f) {
				LAST_T.remove(p.getId());
				continue;
			}
			BeastForm from = BeastBoy.morphFrom(p), to = BeastBoy.form(p);
			Body bs = MorphAnchors.of(from), bt = MorphAnchors.of(to);
			float f = shape(t);
			float size = (float) Math.exp(Mth.lerp(f, Math.log(bs.size()), Math.log(bt.size())));
			Quaternionf yaw = new Quaternionf().rotationY(-p.yBodyRot * Mth.DEG_TO_RAD);
			int n = 2 + (int) (size * 1.5f);
			for (int i = 0; i < Math.min(n, 8); i++) {
				int a = rnd.nextInt(MorphAnchors.COUNT);
				Vector3f c = new Vector3f(bs.a()[a].c()).lerp(bt.a()[a].c(), f);
				c.add((rnd.nextFloat() - 0.5f) * size * 0.25f, (rnd.nextFloat() - 0.5f) * size * 0.2f, (rnd.nextFloat() - 0.5f) * size * 0.25f);
				yaw.transform(c);
				mc.level.addParticle(SPARK, p.getX() + c.x, p.getY() + c.y, p.getZ() + c.z, 0, 0.02, 0);
			}
			Float last = LAST_T.put(p.getId(), t);
			if ((last == null || last < 0.72f) && t >= 0.72f) {
				// the landing: a ring of green, and for a tiny body a proper puff it pops out of
				int ring = 14;
				float rad = Math.max(0.3f, bt.size() * 0.45f);
				for (int i = 0; i < ring; i++) {
					double ang = i * Math.PI * 2 / ring;
					mc.level.addParticle(SPARK_BIG, p.getX() + Math.cos(ang) * rad, p.getY() + 0.1 + bt.size() * 0.3, p.getZ() + Math.sin(ang) * rad,
							Math.cos(ang) * 0.05, 0.02, Math.sin(ang) * 0.05);
				}
				if (bt.size() < 0.7f || bs.size() < 0.7f) {
					for (int i = 0; i < 10; i++) {
						mc.level.addParticle(ParticleTypes.POOF, p.getX() + (rnd.nextFloat() - 0.5f) * 0.4, p.getY() + 0.15 + rnd.nextFloat() * 0.3,
								p.getZ() + (rnd.nextFloat() - 0.5f) * 0.4, (rnd.nextFloat() - 0.5f) * 0.06, 0.03, (rnd.nextFloat() - 0.5f) * 0.06);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- previews (harnesses, the Guidebook)

	/** The morph from {@code from} to {@code to} at {@code t} 0..1, pose stack at the feet with +Z front; the human is a default player. */
	public static void renderPreview(BeastForm from, BeastForm to, float t, PoseStack pose, MultiBufferSource buffers, int light, float age) {
		Matrix4f base = new Matrix4f(pose.last().pose());
		Matrix3f nrm = new Matrix3f(pose.last().normal());
		Draw src = previewDraw(from, pose, light, age, false);
		Draw tgt = previewDraw(to, pose, light, age, false);
		blend(base, nrm, MorphAnchors.of(from), MorphAnchors.of(to), clamp01(t), src, previewDraw(from, pose, light, age, true), tgt,
				previewDraw(to, pose, light, age, true), buffers);
	}

	/** Harness: the first-person gorilla arm at {@code q} on the preview model's right arm (pose stack where the game draws the hand). */
	public static void renderHandPreview(BeastForm quick, float q, PoseStack pose, MultiBufferSource buffers, int light) {
		PlayerModel<AbstractClientPlayer> m = previewModel();
		m.setAllVisible(true);
		QuickMorphs.resetHuman(m);
		m.rightArm.resetPose();
		m.rightArm.x = -5f;
		m.rightArm.y = 2f;
		QuickMorphs.adjustHuman(m, quick, q);
		m.rightArm.render(pose, buffers.getBuffer(RenderType.entitySolid(PREVIEW_SKIN)), light, OverlayTexture.NO_OVERLAY);
		m.rightSleeve.copyFrom(m.rightArm);
		m.rightSleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(PREVIEW_SKIN)), light, OverlayTexture.NO_OVERLAY);
		QuickMorphs.renderHand(quick, q, m.rightArm, true, pose, buffers, light);
		m.setAllVisible(true);
		QuickMorphs.resetHuman(m);
	}

	/** A quick morph preview at {@code q} 0..1 ({@code move} animates the whole-animal ones). */
	public static void renderQuickPreview(BeastForm quick, float q, String move, PoseStack pose, MultiBufferSource buffers, int light, float age) {
		Matrix4f base = new Matrix4f(pose.last().pose());
		Matrix3f nrm = new Matrix3f(pose.last().normal());
		Draw human = previewDraw(BeastForm.HUMAN, pose, light, age, false), humanGlow = previewDraw(BeastForm.HUMAN, pose, light, age, true);
		if (wholeQuick(quick)) {
			Draw animal = quick == BeastForm.SNAKE ? b -> quickSnake(pose, 0f, b, light, q, age) : b -> {
				BeastPose s = new BeastPose();
				s.age = age;
				s.move = move;
				s.prog = q;
				BeastFormArt.renderPose(quick, s, pose, b, light, 0xFFFFFFFF);
			};
			Body bq = quickBody(quick);
			if (q < QUICK_IN) {
				blend(base, nrm, MorphAnchors.human(), bq, q / QUICK_IN, human, humanGlow, animal, animal, buffers);
			} else if (q < 1f - QUICK_IN) {
				animal.draw(buffers);
			} else {
				blend(base, nrm, bq, MorphAnchors.human(), (q - (1f - QUICK_IN)) / QUICK_IN, animal, animal, human, humanGlow, buffers);
			}
			return;
		}
		Draw body = previewQuickHuman(quick, q, pose, light, age, false), glow = previewQuickHuman(quick, q, pose, light, age, true);
		partial(base, nrm, partialGreen(q), Math.min(1f, QuickMorphs.glow(q)), body, glow, buffers);
	}

	private static Draw previewDraw(BeastForm form, PoseStack pose, int light, float age, boolean glow) {
		if (form != BeastForm.HUMAN) {
			return b -> {
				BeastPose s = new BeastPose();
				s.age = age;
				BeastFormArt.renderPose(form, s, pose, b, light, 0xFFFFFFFF);
			};
		}
		return b -> {
			PlayerModel<AbstractClientPlayer> m = previewModel();
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				m.attackTime = 0f;
				m.crouching = false;
				m.setupAnim(mc.player, 0f, 0f, age, 0f, 0f);
			}
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(180f));
			pose.scale(-1f, -1f, 1f);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
			pose.translate(0f, -1.501f, 0f);
			m.renderToBuffer(pose, b.getBuffer(RenderType.entityCutoutNoCull(PREVIEW_SKIN)), light, OverlayTexture.NO_OVERLAY, -1);
			if (!glow) {
				// the in-game green tint layer over his skin (BeastTintLayer)
				m.renderToBuffer(pose, b.getBuffer(RenderType.entityTranslucent(PREVIEW_SKIN)), light, OverlayTexture.NO_OVERLAY, 0x8C46D232);
			}
			pose.popPose();
		};
	}

	/** Preview: the default player with the partial's part swelling / hidden and the animal part on it (the glow pass: his model only). */
	private static Draw previewQuickHuman(BeastForm quick, float q, PoseStack pose, int light, float age, boolean glow) {
		return b -> {
			PlayerModel<AbstractClientPlayer> m = previewModel();
			Minecraft mc = Minecraft.getInstance();
			m.setAllVisible(true);
			QuickMorphs.resetHuman(m);
			if (mc.player != null) {
				m.attackTime = 0f;
				m.crouching = false;
				m.setupAnim(mc.player, 0f, 0f, age, 0f, 0f);
			}
			QuickMorphs.adjustHuman(m, quick, q);
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(180f));
			pose.scale(-1f, -1f, 1f);
			pose.scale(0.9375f, 0.9375f, 0.9375f);
			pose.translate(0f, -1.501f, 0f);
			m.renderToBuffer(pose, b.getBuffer(RenderType.entityCutoutNoCull(PREVIEW_SKIN)), light, OverlayTexture.NO_OVERLAY, -1);
			if (!glow) {
				m.renderToBuffer(pose, b.getBuffer(RenderType.entityTranslucent(PREVIEW_SKIN)), light, OverlayTexture.NO_OVERLAY, 0x8C46D232);
				QuickMorphs.render(quick, q, m, pose, b, light, OverlayTexture.NO_OVERLAY, age);
			}
			pose.popPose();
			m.setAllVisible(true);
			QuickMorphs.resetHuman(m);
		};
	}

	/** Player models whose part scales a quick morph changed (shared by every player: put back on the next set-up). */
	private static final java.util.Set<PlayerModel<?>> SCALED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	/** From the player renderer once it has set his model's part visibility: the quick-morph part swelling / hidden. */
	public static void adjustModel(PlayerModel<?> m, AbstractClientPlayer p) {
		if (SCALED.remove(m)) {
			QuickMorphs.resetHuman(m);
		}
		if (!BeastBoy.hasPower(p) || BeastBoy.morphed(p)) {
			return;
		}
		BeastForm quick = BeastBoy.quickForm(p);
		if (quick == null) {
			return;
		}
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		if (QuickMorphs.adjustHuman(m, quick, BeastBoy.quickProgress(p, pt))) {
			SCALED.add(m);
		}
	}

	/** The quick layer: the animal part of a partial quick morph on his posed model (pose stack at the model root). */
	public static void renderQuickLayer(PlayerModel<?> m, AbstractClientPlayer p, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
			float partial) {
		if (!BeastBoy.hasPower(p) || BeastBoy.morphed(p) || p.isInvisible()) {
			return;
		}
		BeastForm quick = BeastBoy.quickForm(p);
		if (quick != null && QuickMorphs.partial(quick)) {
			QuickMorphs.render(quick, BeastBoy.quickProgress(p, partial), m, pose, buffers, light, overlay, p.tickCount + partial);
		}
	}

	/** First person: the animal arm in place of his hand while an arm quick morph is out. */
	public static void renderHand(PlayerModel<?> m, AbstractClientPlayer p, PoseStack pose, MultiBufferSource buffers, int light, boolean right) {
		if (!BeastBoy.hasPower(p) || BeastBoy.morphed(p)) {
			return;
		}
		BeastForm quick = BeastBoy.quickForm(p);
		if (quick != null) {
			float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
			QuickMorphs.renderHand(quick, BeastBoy.quickProgress(p, pt), right ? m.rightArm : m.leftArm, right, pose, buffers, light);
		}
	}

	private static PlayerModel<AbstractClientPlayer> previewModel() {
		if (previewModel == null) {
			previewModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
		}
		return previewModel;
	}
}
