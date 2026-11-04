package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.wildvine.WildvineFx;
import com.projecthero.mod.client.omnitrix.alien.wildvine.WildvineLineClient;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Wildvine's client kit (user: "Use this skin: https://www.planetminecraft.com/skin/wildvine-ben-10-4704618/ remove the pixel
 * omnitrix from his skin model and replace it with our omnitrix badge please"). The user's skin on the player body at 1.1 -- a lanky
 * plant with his one eye in the bulb of his head -- with loose, swaying vine arms and a pose per move. His vines, seed pods, coils,
 * the Overgrowth thicket and the burrow's mound are drawn by {@link WildvineFx}; the swing / zip moves him on his own client
 * ({@link WildvineLineClient}).
 */
public final class WildvineClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/wildvine.png");

	public static void register() {
		AlienClients.register(Aliens.WILDVINE.id(), new WildvineClient());
		WildvineFx.register();
		WildvineLineClient.register();
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body x)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	private static final float[][] LASH = { QuirkKeyframes.rest(0), f(2, -2.7f, 0, 0.3f, 0.2f, 0, -0.1f, -0.1f), f(4, -1.4f, -0.2f, 0, 0.3f, 0, -0.1f, 0.25f),
			f(7, -1.4f, -0.2f, 0, 0.3f, 0, -0.1f, 0.2f), QuirkKeyframes.rest(11) };
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), f(2, -1.6f, -0.1f, 0, 0.2f, 0, -0.1f, 0.15f), f(10, -1.6f, -0.1f, 0, 0.2f, 0, -0.1f, 0.15f),
			f(13, -0.8f, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(16) };
	private static final float[][] THROW = { QuirkKeyframes.rest(0), f(3, -2.9f, 0, 0.2f, -0.4f, 0, -0.2f, -0.2f), f(6, -0.9f, -0.2f, 0, 0.2f, 0, -0.1f, 0.3f),
			QuirkKeyframes.rest(11) };
	private static final float[][] PLANT = { QuirkKeyframes.rest(0), f(3, -0.9f, 0, 0.15f, -0.9f, 0, -0.15f, 0.6f), f(10, -0.9f, 0, 0.15f, -0.9f, 0, -0.15f, 0.6f),
			QuirkKeyframes.rest(14) };
	private static final float[][] WRAP = { QuirkKeyframes.rest(0), f(3, -1.5f, 0.3f, 0, -1.5f, -0.3f, 0, 0.1f), f(56, -1.5f, 0.3f, 0, -1.5f, -0.3f, 0, 0.1f),
			QuirkKeyframes.rest(62) };
	private static final float[][] SNARE = { QuirkKeyframes.rest(0), f(3, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, 0.2f), f(9, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, 0.2f),
			QuirkKeyframes.rest(14) };
	private static final float[][] REGROW = { QuirkKeyframes.rest(0), f(4, -2.7f, 0, 0.6f, -2.7f, 0, -0.6f, -0.2f), f(14, -2.7f, 0, 0.6f, -2.7f, 0, -0.6f, -0.2f),
			QuirkKeyframes.rest(20) };
	private static final float[][] OVERGROWTH = { QuirkKeyframes.rest(0), f(4, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.2f), f(8, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.7f),
			f(20, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.65f), QuirkKeyframes.rest(26) };
	private static final float[][] EMERGE = { f(0, -2.9f, 0, 0.3f, -2.9f, 0, -0.3f, 0), f(5, -2.4f, 0, 0.6f, -2.4f, 0, -0.6f, 0), QuirkKeyframes.rest(12) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.2f, 0, 0, 0, 0, 0.15f), QuirkKeyframes.rest(7) };

	private static final FirstPersonClip FP_LASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -40, 10, 0, 0.05f, 0.3f, 0.1f),
			FirstPersonClip.f(4, 10, 20, 0, 0.1f, -0.05f, -0.35f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 0, 10, 0, 0.05f, 0.05f, -0.4f),
			FirstPersonClip.f(12, 0, 10, 0, 0.05f, 0.05f, -0.4f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.35f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		// where the skin painted its little Omnitrix: the middle of the top of his chest (texels 23..24 x 21..22, painted out),
		// just proud of the jacket layer that covers it
		return new float[] { 0f, 2.0f, -2.3f, 2.6f };
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & (WildvineKit.F_SWING | WildvineKit.F_ZIP)) != 0) {
			// hanging off his vine: the right arm straight up the line, the legs trailing
			m.rightArm.xRot = -2.95f;
			m.rightArm.zRot = 0.1f;
			m.leftArm.xRot = -0.3f;
			m.leftArm.zRot = -0.5f;
			m.rightLeg.xRot = 0.35f;
			m.leftLeg.xRot = -0.2f;
			return true;
		}
		float[][] clip = switch (animId) {
			case WildvineKit.ANIM_LASH -> LASH;
			case WildvineKit.ANIM_GRAB -> GRAB;
			case WildvineKit.ANIM_THROW -> THROW;
			case WildvineKit.ANIM_PLANT -> PLANT;
			case WildvineKit.ANIM_WRAP -> WRAP;
			case WildvineKit.ANIM_SNARE -> SNARE;
			case WildvineKit.ANIM_REGROW -> REGROW;
			case WildvineKit.ANIM_OVERGROWTH -> OVERGROWTH;
			case WildvineKit.ANIM_EMERGE -> EMERGE;
			case WildvineKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// loose vine arms: they hang a little out from his thin body and sway like stems
			m.rightArm.zRot += 0.12f + Mth.sin(age * 0.09f) * 0.05f;
			m.leftArm.zRot -= 0.12f + Mth.sin(age * 0.09f + 1.3f) * 0.05f;
			m.rightArm.xRot += Mth.sin(age * 0.07f) * 0.04f;
			m.leftArm.xRot += Mth.sin(age * 0.07f + 2f) * 0.04f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case WildvineKit.ANIM_LASH, WildvineKit.ANIM_THROW -> FP_LASH;
			case WildvineKit.ANIM_GRAB, WildvineKit.ANIM_WRAP -> FP_REACH;
			case WildvineKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the burrow: a mound moving under the ground

	private static boolean burrowed(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & WildvineKit.F_BURROW) != 0;
	}

	@Override
	public boolean customBody(AbstractClientPlayer p) {
		return burrowed(p);
	}

	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		WildvineFx.mound(p, partial, pose, buffers, light);
	}

	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		return burrowed(p); // under the ground: no arms to show
	}
}
