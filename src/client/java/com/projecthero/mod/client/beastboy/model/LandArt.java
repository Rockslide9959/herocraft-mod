package com.projecthero.mod.client.beastboy.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.beastboy.BeastForm;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * v0.17.3 hq round: the land mammals' entry point (wolf, cheetah, tiger, bear, mouse): blocky Minecraft-style box models
 * ({@link BoxBeast}) with their own pixel-art textures, posed by their animators. Called from {@code BeastFormArt.renderPose}.
 */
public final class LandArt {
	private LandArt() {
	}

	private static ResourceLocation tex(String n) {
		return ProjectHeroMod.id("textures/entity/beastboy/art/" + n + ".png");
	}

	private static final ResourceLocation WOLF_TEX = tex("wolf"), CHEETAH_TEX = tex("cheetah"), TIGER_TEX = tex("tiger"), BEAR_TEX = tex("bear"),
			MOUSE_TEX = tex("mouse");
	private static WolfModel wolf;
	private static CheetahModel cheetah;
	private static TigerModel tiger;
	private static BearModel bear;
	private static MouseModel mouse;

	/**
	 * Fix2: whole-model scale per form (geometry, so the pixel art stays crisp). The bear is 22% bigger (about 1.7 blocks to the top of
	 * the hump, 2.4 long), matching its 1.5 x 1.7 body box.
	 */
	static float size(BeastForm f) {
		return f == BeastForm.BEAR ? 1.22f : 1f;
	}

	public static boolean handles(BeastForm f) {
		return f == BeastForm.WOLF || f == BeastForm.CHEETAH || f == BeastForm.TIGER || f == BeastForm.BEAR || f == BeastForm.MOUSE;
	}

	/** Draws the form at the feet (+Z forward, blocks), with the morph grow / shrink and flash. */
	public static boolean render(BeastForm form, BeastPose s, PoseStack pose, MultiBufferSource buffers, int light, int tint) {
		BoxBeast m;
		ResourceLocation tex;
		switch (form) {
			case WOLF -> {
				m = wolf != null ? wolf : (wolf = new WolfModel());
				tex = WOLF_TEX;
			}
			case CHEETAH -> {
				m = cheetah != null ? cheetah : (cheetah = new CheetahModel());
				tex = CHEETAH_TEX;
			}
			case TIGER -> {
				m = tiger != null ? tiger : (tiger = new TigerModel());
				tex = TIGER_TEX;
			}
			case BEAR -> {
				m = bear != null ? bear : (bear = new BearModel());
				tex = BEAR_TEX;
			}
			case MOUSE -> {
				m = mouse != null ? mouse : (mouse = new MouseModel());
				tex = MOUSE_TEX;
			}
			default -> {
				return false;
			}
		}
		m.reset();
		m.pose(s);
		int r = (tint >> 16) & 255, g = (tint >> 8) & 255, b = tint & 255, a = (tint >>> 24) & 255;
		if (a == 0) {
			a = 255;
		}
		int color, overlay;
		if (r > g + 24 && r > b) {
			color = FastColor.ARGB32.color(a, 255, Math.max(70, g * 255 / Math.max(1, r)), Math.max(70, b * 255 / Math.max(1, r)));
			overlay = OverlayTexture.pack(0, OverlayTexture.v(true));
		} else {
			int k = Math.max(60, Math.max(r, Math.max(g, b)));
			color = FastColor.ARGB32.color(a, k, k, k);
			overlay = OverlayTexture.NO_OVERLAY;
		}
		float[] mo = s.morph();
		pose.pushPose();
		if (mo[0] != 1f) {
			float cy = form.height * 0.35f;
			pose.translate(0f, cy * (1f - mo[0]) * 0.3f, 0f);
			pose.scale(mo[0], mo[0], mo[0]);
		}
		// friendly (+Y up, +Z front) -> vanilla model space (Y down, front -Z); ModelPart divides by 16, so 0.5 makes 1 unit 1/32 block
		float sc = 0.5f * size(form);
		pose.scale(sc, -sc, -sc);
		m.render(pose, buffers.getBuffer(a < 250 ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex)), light, overlay, color);
		if (mo[1] > 0.003f) {
			float k = Math.min(1f, mo[1]) * a / 255f;
			m.render(pose, buffers.getBuffer(RenderType.eyes(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					FastColor.ARGB32.color(255, (int) (150 * k), (int) (255 * k), (int) (120 * k)));
		}
		pose.popPose();
		return true;
	}
}
