package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Wildvine's client kit -- the green plant body (vine spirals on the limbs) with a PINK FLOWER-BULB HEAD: a ring of
 * petals around the face, cupped back and swaying, and a yellow centre with two eyes; tendrils curling off the forearms and
 * leaves on the shoulders. Hidden as a bush (sneaking still), a mound of leaves is drawn over him. A pose for every move.
 */
public final class WildvineClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/wildvine.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/wildvine_extras.png");

	private static final float[] PETAL = { 0, 0, 16, 16 };
	private static final float[] CENTRE = { 16, 0, 32, 16 };
	private static final float[] VINE = { 32, 0, 40, 8 };
	private static final float[] LEAF = { 40, 0, 48, 8 };
	private static final float[] BUSH = { 0, 16, 32, 48 };

	private static final float[][] SEED = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.8f, 0, 0.2f, 0.3f, 0, 0, -0.15f, 0.3f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(6, -1.1f, 0, 0, 0.5f, 0, 0, 0.25f, -0.25f, 0.3f, 0, 0, -0.3f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] WHIP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.5f, -1.0f, 0, 0.2f, 0, 0, 0.05f, -0.5f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(6, -1.4f, 1.0f, 0, 0.2f, 0, 0, 0.05f, 0.5f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] BIND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.55f, -0.2f, 0, -1.55f, 0.2f, 0, 0.1f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0),
			QuirkKeyframes.f(14, -1.5f, -0.15f, 0, -1.5f, 0.15f, 0, 0.1f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(20) };
	private static final float[][] POLLEN = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.4f, -0.3f, 0, -1.4f, -0.2f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.5f),
			QuirkKeyframes.rest(16) };
	private static final float[][] GRAPPLE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.4f, 0.1f, 0, 0.3f, 0, 0, -0.1f, 0, 0.3f, 0, 0, -0.2f, 0, 0, -0.3f),
			QuirkKeyframes.f(36, -2.4f, 0.1f, 0, 0.3f, 0, 0, -0.1f, 0, 0.3f, 0, 0, -0.2f, 0, 0, -0.3f), QuirkKeyframes.rest(40) };
	private static final float[][] CLIMB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			QuirkKeyframes.f(40, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.4f), QuirkKeyframes.rest(46) };
	private static final float[][] WALL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.8f, 0, 0.3f, -2.8f, 0, -0.3f, -0.3f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.4f),
			QuirkKeyframes.f(6, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.7f, 0, -0.8f, 0, 0.2f, -0.8f, 0, -0.2f, 0.5f), QuirkKeyframes.rest(16) };
	private static final float[][] REGROW = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.6f, 0, 0.6f, -2.6f, 0, -0.6f, -0.15f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.55f),
			QuirkKeyframes.f(30, -2.65f, 0, 0.65f, -2.65f, 0, -0.65f, -0.15f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.55f), QuirkKeyframes.rest(36) };
	private static final float[][] OVERGROW = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.7f, 0, 0.3f, -0.7f, 0, -0.3f, 0.65f, 0, -0.7f, 0, 0.1f, -0.7f, 0, -0.1f, 0.4f),
			QuirkKeyframes.f(16, -0.7f, 0, 0.3f, -0.7f, 0, -0.3f, 0.65f, 0, -0.7f, 0, 0.1f, -0.7f, 0, -0.1f, 0.4f), QuirkKeyframes.rest(22) };
	private static final float[][] ERUPT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.6f, 0, 0.2f, 0.6f, 0, -0.2f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.f(6, -2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.25f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.4f), QuirkKeyframes.rest(14) };
	private static final float[][] LASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 0.25f, 0, 0.4f, 0, 0, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] TOUCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.8f, 0, 0, 0.2f, 0, 0, 0.45f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			QuirkKeyframes.rest(10) };

	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 0, 10, 0, 0.35f, 0.15f),
			FirstPersonClip.f(6, -10, 20, 0, 0.1f, 0.05f, -0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WHIP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -30, -40, 0, -0.1f, 0.15f, -0.1f),
			FirstPersonClip.f(6, -25, 50, 0, 0.35f, 0.1f, -0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 18, 0, 0.12f, 0.15f, -0.2f),
			FirstPersonClip.f(14, -24, 18, 0, 0.12f, 0.15f, -0.2f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -45, 10, 0, 0.05f, 0.3f, -0.25f),
			FirstPersonClip.f(36, -45, 10, 0, 0.05f, 0.3f, -0.25f), FirstPersonClip.f(40, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -60, -10, 15, -0.05f, 0.45f, 0.1f),
			FirstPersonClip.f(30, -62, -10, 15, -0.05f, 0.45f, 0.1f), FirstPersonClip.f(36, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(6, 35, 10, 0, 0.05f, -0.3f, -0.1f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -35, -35, 25, -0.2f, 0.2f, 0),
			FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.25f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0] / 64f, uv[1] / 64f, uv[2] / 64f, uv[3] / 64f, 0xFFFFFFFF, light);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & WildvineKit.F_BUSH) != 0) {
			// hidden: a mound of leaves over him (he is crouched under it)
			float rustle = Mth.sin(age * 0.07f) * 0.15f;
			box(pose, vc, -9f, 2f + rustle, -9f, 9f, 25f, 9f, BUSH, light);
			box(pose, vc, -7f, -3f + rustle, -6.5f, 6.5f, 4f, 7f, BUSH, light);
			box(pose, vc, -4f, 6f, -10.5f, 5f, 15f, -8.5f, BUSH, light);
			return;
		}
		// the flower head: a ring of petals around the face, cupped back, swaying; the raised yellow centre
		boolean open = s.animId == WildvineKit.ANIM_POLLEN || s.animId == WildvineKit.ANIM_REGROW;
		float t = p.level().getGameTime() + partial - s.animStart;
		float bloom = open && t < 30 ? Mth.sin(Math.min(1f, t / 6f) * Mth.HALF_PI) * (t > 20 ? (30 - t) / 10f : 1f) : 0f;
		AlienParts.on(pose, model.head, () -> {
			for (int i = 0; i < 8; i++) {
				float a = i * Mth.TWO_PI / 8f + 0.2f;
				pose.pushPose();
				pose.translate(0, -4.2f / 16f, -3.9f / 16f);
				pose.mulPose(Axis.ZP.rotation(a));
				pose.mulPose(Axis.XP.rotation(-0.6f + bloom * 0.5f + Mth.sin(age * 0.06f + i) * 0.05f)); // tips wrap back round the head
				box(pose, vc, -1.9f, -9.5f, -0.4f, 1.9f, -3.2f, 0.4f, PETAL, light);
				pose.popPose();
			}
			box(pose, vc, -2.6f, -6.8f, -4.7f, 2.6f, -1.6f, -3.9f, CENTRE, light);
		});
		// leaves on the shoulders, tendrils curling off the forearms
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
			float sx = right ? -1f : 1f;
			float curl = Mth.sin(age * 0.09f + (right ? 0 : 2f)) * 0.25f;
			AlienParts.on(pose, arm, () -> {
				pose.pushPose();
				pose.translate((right ? -1f : 1f) / 16f, -2.2f / 16f, 0);
				pose.mulPose(Axis.ZP.rotation(sx * -0.6f));
				box(pose, vc, -2.5f, -0.3f, -2.5f, 2.5f, 0.3f, 2.5f, LEAF, light);
				pose.popPose();
				pose.pushPose();
				pose.translate((right ? -3f : 3f) / 16f, 7f / 16f, 0.5f / 16f);
				pose.mulPose(Axis.ZP.rotation(sx * (0.5f + curl)));
				box(pose, vc, -0.4f, 0f, -0.4f, 0.4f, 6f, 0.4f, VINE, light);
				pose.translate(0, 6f / 16f, 0);
				pose.mulPose(Axis.ZP.rotation(sx * (-0.9f - curl)));
				box(pose, vc, -0.35f, 0f, -0.35f, 0.35f, 3.5f, 0.35f, VINE, light);
				pose.popPose();
			});
		}
		// a trailing vine down the back
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(1.5f / 16f, 0.5f / 16f, 2.2f / 16f);
			pose.mulPose(Axis.ZP.rotation(0.15f + Mth.sin(age * 0.05f) * 0.1f));
			box(pose, vc, -0.45f, 0f, -0.45f, 0.45f, 11f, 0.45f, VINE, light);
			pose.translate(0, 5f / 16f, 0);
			box(pose, vc, -2f, -0.25f, 0f, 2f, 0.25f, 3.5f, LEAF, light);
			pose.popPose();
		});
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & WildvineKit.F_BUSH) != 0) {
			// all of him under the leaves
			m.head.visible = false;
			m.hat.visible = false;
			m.body.visible = false;
			m.rightArm.visible = false;
			m.leftArm.visible = false;
			m.rightLeg.visible = false;
			m.leftLeg.visible = false;
			if (m instanceof PlayerModel<?> pm) {
				pm.jacket.visible = false;
				pm.rightSleeve.visible = false;
				pm.leftSleeve.visible = false;
				pm.rightPants.visible = false;
				pm.leftPants.visible = false;
			}
			return true;
		}
		float[][] clip = switch (animId) {
			case WildvineKit.ANIM_SEED -> SEED;
			case WildvineKit.ANIM_WHIP -> WHIP;
			case WildvineKit.ANIM_BIND -> BIND;
			case WildvineKit.ANIM_POLLEN -> POLLEN;
			case WildvineKit.ANIM_GRAPPLE -> GRAPPLE;
			case WildvineKit.ANIM_CLIMB -> CLIMB;
			case WildvineKit.ANIM_WALL -> WALL;
			case WildvineKit.ANIM_REGROW -> REGROW;
			case WildvineKit.ANIM_OVERGROW -> OVERGROW;
			case WildvineKit.ANIM_ERUPT -> ERUPT;
			case WildvineKit.ANIM_LASH -> LASH;
			case WildvineKit.ANIM_TOUCH -> TOUCH;
			default -> null;
		};
		if (animId == WildvineKit.ANIM_GRAPPLE && (s.kitFlags & WildvineKit.F_GRAPPLE) == 0 && ticks > 6) {
			clip = null;
		}
		if (animId == WildvineKit.ANIM_CLIMB && (s.kitFlags & WildvineKit.F_CLIMB) == 0 && ticks > 6) {
			clip = null;
		}
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// vine arms: a lazy sway
			m.rightArm.zRot += 0.06f + Mth.sin(age * 0.07f) * 0.04f;
			m.leftArm.zRot -= 0.06f + Mth.sin(age * 0.07f + 1f) * 0.04f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case WildvineKit.ANIM_SEED -> FP_THROW;
			case WildvineKit.ANIM_WHIP -> FP_WHIP;
			case WildvineKit.ANIM_BIND -> FP_POINT;
			case WildvineKit.ANIM_GRAPPLE, WildvineKit.ANIM_TOUCH -> FP_REACH;
			case WildvineKit.ANIM_CLIMB, WildvineKit.ANIM_REGROW, WildvineKit.ANIM_ERUPT -> FP_UP;
			case WildvineKit.ANIM_WALL, WildvineKit.ANIM_OVERGROW -> FP_SLAM;
			case WildvineKit.ANIM_POLLEN -> FP_WIDE;
			case WildvineKit.ANIM_LASH -> FP_PUNCH;
			default -> null;
		};
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		float sx = right ? -1f : 1f;
		pose.pushPose();
		pose.translate((right ? -3f : 3f) / 16f, 6f / 16f, 0.5f / 16f);
		pose.mulPose(Axis.ZP.rotation(sx * 0.5f));
		box(pose, vc, -0.4f, 0f, -0.4f, 0.4f, 6f, 0.4f, VINE, light);
		pose.popPose();
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & WildvineKit.F_BUSH) != 0) {
			return; // a still bush gives nothing away
		}
		if (r.nextInt(30) == 0) {
			p.level().addParticle(ParticleTypes.CHERRY_LEAVES, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0, 0);
		}
		if ((s.kitFlags & WildvineKit.F_REGROW) != 0 && r.nextInt(3) == 0) {
			p.level().addParticle(ParticleTypes.HAPPY_VILLAGER, p.getX() + (r.nextDouble() - 0.5) * 1.2, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
		}
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 4.0f, -2.05f, 3.0f };
	}
}
