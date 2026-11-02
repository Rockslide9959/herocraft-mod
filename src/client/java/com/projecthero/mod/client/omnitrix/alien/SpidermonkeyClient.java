package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.spidermonkey.SpidermonkeyFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.spidermonkey.SpidermonkeyKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (Recalibrated Omnitrix, basic version): Spidermonkey's client kit -- the player body at 0.85 with his generated blue-furred
 * skin, plus extras from {@code spidermonkey_extras.png}: a second, lower pair of arms (they punch in turn with the top pair in the
 * Quad Flurry) and a long tail curling up behind him with the white web spinneret at its tip. A pose and a first-person clip for
 * every move, a wall-climbing pose; his webs are {@link SpidermonkeyFx}.
 */
public final class SpidermonkeyClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/spidermonkey.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/spidermonkey_extras.png");
	private static final float[] FUR = uv(0, 0, 16, 16), DARK = uv(16, 0, 16, 16), SPINNERET = uv(32, 0, 8, 8);

	private static float[] uv(int x, int y, int w, int h) {
		return new float[] { x / 64f, y / 64f, (x + w) / 64f, (y + h) / 64f };
	}

	public static void register() {
		AlienClients.register(Aliens.SPIDERMONKEY.id(), new SpidermonkeyClient());
		SpidermonkeyFx.register();
	}

	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, 0, 0, 0, 0, 0, 0, hx);
	}

	// ---------------------------------------------------------------- poses
	private static final float[][] SHOT = { QuirkKeyframes.rest(0), f(2, -1.65f, 0.1f, 0, 0.3f, 0, 0, 0.05f, 0), QuirkKeyframes.rest(9) };
	private static final float[][] NET = { QuirkKeyframes.rest(0), f(2, -2.0f, -0.3f, 0, -2.0f, 0.3f, 0, -0.1f, 0), f(5, -1.5f, -0.5f, 0, -1.5f, 0.5f, 0, 0.15f, 0),
			QuirkKeyframes.rest(12) };
	private static final float[][] FLURRY = { QuirkKeyframes.rest(0), f(1, -1.7f, 0.2f, 0, 0.3f, 0, 0, 0.15f, 0), f(3, 0.3f, 0, 0, -1.7f, -0.2f, 0, 0.15f, 0),
			f(5, -1.7f, 0.2f, 0, 0.3f, 0, 0, 0.15f, 0), f(7, 0.3f, 0, 0, -1.7f, -0.2f, 0, 0.15f, 0), QuirkKeyframes.rest(11) };
	private static final float[][] YANK = { QuirkKeyframes.rest(0), f(2, -1.7f, 0, 0, 0, 0, 0, 0.1f, 0), f(6, 0.6f, 0, 0.2f, 0, 0, 0, -0.15f, 0),
			QuirkKeyframes.rest(12) };
	private static final float[][] ZIP = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -2.7f, 0, 0.1f, 0.4f, 0, -0.3f, 0.1f, 0, -0.5f, 0, 0.1f, 0.3f, 0, -0.1f, -0.2f),
			QuirkKeyframes.f(10, -2.7f, 0, 0.1f, 0.4f, 0, -0.3f, 0.1f, 0, -0.5f, 0, 0.1f, 0.3f, 0, -0.1f, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] SWING = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -3.0f, 0, 0.15f, -0.4f, 0, -0.6f, 0, 0, -0.7f, 0, 0.1f, -0.2f, 0, -0.1f, -0.2f),
			QuirkKeyframes.f(70, -3.0f, 0, 0.15f, -0.4f, 0, -0.6f, 0, 0, -0.7f, 0, 0.1f, -0.2f, 0, -0.1f, -0.2f), QuirkKeyframes.rest(74) };
	/** The tail whip: one full spin with the arms flung out. */
	private static final float[][] WHIP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0.1f, 1.6f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0.1f, 4.7f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(8, 0, 0, 0.6f, 0, 0, -0.6f, 0, 6.2832f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(11) };
	private static final float[][] TRAP = { QuirkKeyframes.rest(0), f(3, -0.6f, 0, 0.3f, -0.6f, 0, -0.3f, 0.55f, 0.3f), f(8, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.5f, 0.3f),
			QuirkKeyframes.rest(14) };
	private static final float[][] CRAWL = { QuirkKeyframes.rest(0), f(2, -2.0f, 0.4f, 0, -1.2f, -0.4f, 0, 0, -0.2f), f(4, -1.2f, 0.4f, 0, -2.0f, -0.4f, 0, 0, -0.2f),
			f(6, -2.0f, 0.4f, 0, -1.2f, -0.4f, 0, 0, -0.2f), QuirkKeyframes.rest(11) };
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0), f(3, -1.4f, -0.7f, 0, -1.4f, 0.7f, 0, 0, 0), f(9, -1.45f, -0.6f, 0, -1.45f, 0.6f, 0, 0, 0),
			QuirkKeyframes.rest(14) };
	private static final float[][] COCOON = { QuirkKeyframes.rest(0), f(3, -1.6f, -0.4f, 0.2f, -1.9f, 0.4f, -0.2f, 0.1f, 0),
			f(7, -1.9f, 0.4f, 0.2f, -1.6f, -0.4f, -0.2f, 0.1f, 0), f(11, -1.6f, -0.4f, 0.2f, -1.9f, 0.4f, -0.2f, 0.1f, 0), QuirkKeyframes.rest(18) };
	private static final float[][] BARRAGE = { QuirkKeyframes.rest(0), f(1, -1.6f, 0.3f, 0, -0.6f, 0, 0, 0.1f, 0), f(3, -0.6f, 0, 0, -1.6f, -0.3f, 0, 0.1f, 0),
			f(5, -1.6f, -0.2f, 0, -0.6f, 0, 0, 0.1f, 0), QuirkKeyframes.rest(10) };

	// ---------------------------------------------------------------- first person
	private static final FirstPersonClip FP_SHOT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 10, 0, 0.06f, 0.15f, -0.3f),
			FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -22, 15, 0, 0.1f, 0.15f, -0.3f),
			FirstPersonClip.f(8, -18, 12, 0, 0.1f, 0.12f, -0.25f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, -10, 8, 0, 0.05f, 0.1f, -0.4f),
			FirstPersonClip.f(3, 0, 0, 0, 0, -0.05f, 0.05f), FirstPersonClip.f(5, -10, 8, 0, 0.05f, 0.1f, -0.4f), FirstPersonClip.f(7, 0, 0, 0, 0, -0.05f, 0.05f),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PULL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 10, 0, 0.06f, 0.15f, -0.35f),
			FirstPersonClip.f(6, 20, 5, 0, 0.0f, -0.2f, 0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -70, 10, 0, 0.05f, 0.55f, 0.0f),
			FirstPersonClip.f(10, -72, 10, 0, 0.05f, 0.56f, 0.0f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_LOW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, 12, 0, 0.05f, -0.35f, 0.1f),
			FirstPersonClip.f(9, 30, 12, 0, 0.05f, -0.35f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, -20, 15, -0.1f, 0.2f, -0.1f),
			FirstPersonClip.f(9, -25, -20, 15, -0.1f, 0.2f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** Pressed flat to a wall right now: the wall-crawl pose. */
	private static boolean climbing(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & SpidermonkeyKit.F_CRAWL) == 0 || p.onGround() || p.isPassenger() || p.isInWater()
				|| (s.kitFlags & SpidermonkeyKit.F_SWING) != 0) {
			return false;
		}
		if (p instanceof net.minecraft.client.player.LocalPlayer lp) {
			return lp.horizontalCollision && lp.onClimbable();
		}
		BlockPos at = BlockPos.containing(p.getX(), p.getY() + 0.5, p.getZ());
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = at.relative(d);
			if (p.level().getBlockState(n).isFaceSturdy(p.level(), n, d.getOpposite())
					&& Math.abs((d.getAxis() == Direction.Axis.X ? p.getX() - (at.getX() + 0.5) : p.getZ() - (at.getZ() + 0.5)) * 2 - d.getAxisDirection().getStep()) < 0.9) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		int white = 0xFFFFFFFF;
		OmnitrixState s = Omnitrix.state(p);
		boolean flurry = s.animId == SpidermonkeyKit.ANIM_FLURRY && p.level().getGameTime() + partial - s.animStart < 11f;
		AlienParts.on(pose, model.body, () -> {
			// the lower pair of arms, under the top pair: they swing opposite the top arms (and punch in turn in the flurry)
			for (int side = -1; side <= 1; side += 2) {
				boolean right = side < 0;
				float topX = right ? model.rightArm.xRot : model.leftArm.xRot;
				float otherX = right ? model.leftArm.xRot : model.rightArm.xRot;
				float x = flurry ? otherX : topX * 0.7f - 0.2f;
				pose.pushPose();
				pose.translate(side * 4.6f / 16f, 6.0f / 16f, 0);
				pose.mulPose(Axis.XP.rotation(x));
				pose.mulPose(Axis.ZP.rotation(side * 0.25f));
				AlienParts.box(pose, vc, -1.4f, 0f, -1.4f, 1.4f, 7.5f, 1.4f, FUR[0], FUR[1], FUR[2], FUR[3], white, light);
				AlienParts.box(pose, vc, -1.6f, 7.5f, -1.6f, 1.6f, 9.5f, 1.6f, DARK[0], DARK[1], DARK[2], DARK[3], white, light);
				pose.popPose();
			}
			// the tail: three segments curling up behind him, the spinneret at the tip
			float sway = Mth.sin(age * 0.12f) * 0.25f;
			pose.pushPose();
			pose.translate(0, 10.5f / 16f, 2f / 16f);
			pose.mulPose(Axis.XP.rotation(0.9f));
			pose.mulPose(Axis.ZP.rotation(sway));
			float[] len = { 5f, 5f, 4.5f };
			float[] thick = { 1.3f, 1.1f, 0.9f };
			for (int i = 0; i < 3; i++) {
				float th = thick[i];
				AlienParts.box(pose, vc, -th, 0f, -th, th, len[i], th, i == 1 ? DARK[0] : FUR[0], i == 1 ? DARK[1] : FUR[1], i == 1 ? DARK[2] : FUR[2],
						i == 1 ? DARK[3] : FUR[3], white, light);
				pose.translate(0, len[i] / 16f, 0);
				pose.mulPose(Axis.XP.rotation(-0.75f));
				pose.mulPose(Axis.ZP.rotation(sway * 0.6f));
			}
			AlienParts.box(pose, vc, -1.2f, 0f, -1.2f, 1.2f, 2.2f, 1.2f, SPINNERET[0], SPINNERET[1], SPINNERET[2], SPINNERET[3], white, light);
			pose.popPose();
		});
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		float[][] clip = switch (animId) {
			case SpidermonkeyKit.ANIM_SHOT -> SHOT;
			case SpidermonkeyKit.ANIM_NET -> NET;
			case SpidermonkeyKit.ANIM_FLURRY -> FLURRY;
			case SpidermonkeyKit.ANIM_YANK -> YANK;
			case SpidermonkeyKit.ANIM_ZIP -> ZIP;
			case SpidermonkeyKit.ANIM_SWING -> SWING;
			case SpidermonkeyKit.ANIM_WHIP -> WHIP;
			case SpidermonkeyKit.ANIM_TRAP -> TRAP;
			case SpidermonkeyKit.ANIM_CRAWL -> CRAWL;
			case SpidermonkeyKit.ANIM_SHIELD -> SHIELD;
			case SpidermonkeyKit.ANIM_COCOON -> COCOON;
			case SpidermonkeyKit.ANIM_BARRAGE -> BARRAGE;
			default -> null;
		};
		if (animId == SpidermonkeyKit.ANIM_SWING && (s.kitFlags & SpidermonkeyKit.F_SWING) == 0 && ticks > 4) {
			clip = null; // let go of the strand
		}
		boolean climbing = clip == null && climbing(p);
		if (climbing) {
			float sw = Mth.sin(age * 0.5f) * 0.4f;
			m.rightArm.xRot = -2.6f + sw;
			m.leftArm.xRot = -2.6f - sw;
			m.rightArm.zRot = 0.3f;
			m.leftArm.zRot = -0.3f;
			m.rightLeg.xRot = -0.5f - sw;
			m.leftLeg.xRot = -0.5f + sw;
			m.head.xRot = -0.4f;
			return true;
		}
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// a monkey's crouch: arms hanging a little forward and out
			m.rightArm.xRot -= 0.15f;
			m.leftArm.xRot -= 0.15f;
			m.rightArm.zRot += 0.12f;
			m.leftArm.zRot -= 0.12f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case SpidermonkeyKit.ANIM_SHOT, SpidermonkeyKit.ANIM_BARRAGE -> FP_SHOT;
			case SpidermonkeyKit.ANIM_NET, SpidermonkeyKit.ANIM_COCOON -> FP_BOTH;
			case SpidermonkeyKit.ANIM_FLURRY, SpidermonkeyKit.ANIM_CRAWL -> FP_PUNCH;
			case SpidermonkeyKit.ANIM_YANK -> FP_PULL;
			case SpidermonkeyKit.ANIM_ZIP, SpidermonkeyKit.ANIM_SWING -> FP_UP;
			case SpidermonkeyKit.ANIM_WHIP, SpidermonkeyKit.ANIM_TRAP -> FP_LOW;
			case SpidermonkeyKit.ANIM_SHIELD -> FP_WIDE;
			default -> null;
		};
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.1f, 3.0f };
	}
}
