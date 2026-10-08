package com.projecthero.mod.client.flight;

import com.projecthero.mod.client.greenlantern.HardLightRibbon;
import com.projecthero.mod.client.maxsteel.TurboDraw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;

/**
 * v0.15.15: a flier's body glow -- a thin skin of light over the player's own posed model: head, body, arms and legs redrawn
 * a little inflated (an inner shell, and a fainter, slowly breathing outer one), additive, very transparent. Built for
 * Green Lantern's Ring Flight ({@code GreenLanternFlightGlow}) and shared with Nova (user: "give Nova the body glow aswell
 * similar to green lantern, make it his colours tho"). Call from a player render layer (so everyone sees it); it is
 * skipped for the wearer in first person.
 */
public final class BodyGlow {
	/** Inflation of the inner / outer shells, px per side -- outside every suit layer it is drawn over (Nova's helmet +0.54). */
	private static final float INNER = 0.8f;
	private static final float OUTER = 1.7f;

	/** inner = the close shell's colour, outer = the fainter outer shell's. */
	public record Palette(int inner, int outer) {
	}

	public static final Palette GREEN_LANTERN = new Palette(0x5CFF8E, 0x35F075);
	/** Nova: gold, the outer shell faintly tinted toward his cyan. */
	public static final Palette NOVA = new Palette(0xF2C230, 0xC8D890);

	private BodyGlow() {
	}

	/**
	 * Draws the glow. {@code strength} 0..1 (the caller's eased fade), {@code boost} 0..1 (brighter while sprint-flying),
	 * {@code forceWide}: draw wide arms whatever the skin (a suit model that is always wide-armed).
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
			float ageInTicks, float strength, float boost, Palette c, boolean forceWide) {
		if (player.isInvisible() || strength <= 0.01f) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		float pulse = 0.5f + 0.5f * Mth.sin(ageInTicks * 0.15f);
		float a1 = strength * (0.1f + 0.04f * pulse) * (1f + 0.25f * boost);
		float a2 = strength * (0.045f + 0.025f * pulse);
		boolean slim = !forceWide && player.getSkin().model() == PlayerSkin.Model.SLIM;
		VertexConsumer add = HardLightRibbon.additive(buffers);
		for (int shell = 0; shell < 2; shell++) {
			float g = shell == 0 ? INNER : OUTER + 0.25f * pulse;
			float a = shell == 0 ? a1 : a2;
			int rgb = shell == 0 ? c.inner() : c.outer();
			part(add, pose, model.head, -4, -8, -4, 4, 0, 4, g, rgb, a);
			part(add, pose, model.body, -4, 0, -2, 4, 12, 2, g, rgb, a);
			part(add, pose, model.rightArm, slim ? -2 : -3, -2, -2, 1, 10, 2, g, rgb, a);
			part(add, pose, model.leftArm, -1, -2, -2, slim ? 2 : 3, 10, 2, g, rgb, a);
			part(add, pose, model.rightLeg, -2, 0, -2, 2, 12, 2, g, rgb, a);
			part(add, pose, model.leftLeg, -2, 0, -2, 2, 12, 2, g, rgb, a);
		}
	}

	/** One inflated box (px extents in the part's space) round a posed model part. */
	private static void part(VertexConsumer vc, PoseStack pose, ModelPart p, float x0, float y0, float z0, float x1, float y1, float z1,
			float grow, int rgb, float alpha) {
		if (!p.visible) {
			return;
		}
		pose.pushPose();
		p.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		pose.translate((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f);
		TurboDraw.box(vc, pose.last(), (x1 - x0) / 2f + grow, (y1 - y0) / 2f + grow, (z1 - z0) / 2f + grow, rgb, alpha);
		pose.popPose();
	}
}
