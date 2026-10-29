package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Four Arms' client kit -- the user's red Tetramand skin (four eyes, the black-and-white outfit), a SECOND PAIR OF ARMS (the skin's
 * own arm UVs, a touch thinner, under the first pair: they follow the upper arms with a lag and take their own swing in the
 * barrage, the clap, the pound and the grab), and a pose (third and first person) for every move.
 */
public final class FourArmsClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/four_arms.png");

	private final ModelPart lowerRight;
	private final ModelPart lowerLeft;

	private static final float[][] HAYMAKER = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -2.9f, 0.2f, 0.25f, -2.9f, -0.2f, -0.25f, -0.25f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(6, -1.1f, -0.2f, 0, -1.1f, 0.2f, 0, 0.45f, 0, 0.3f, 0, 0, -0.3f, 0, 0, 0.3f), QuirkKeyframes.rest(14) };
	private static final float[][] PUNCH_R = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0.3f, 0, 0.3f, 0, 0, 0.1f, -0.35f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(6) };
	private static final float[][] PUNCH_L = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.3f, 0, 0, -1.6f, -0.3f, 0, 0.1f, 0.35f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(6) };
	private static final float[][] CLAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.4f, 0.9f, 0, -1.4f, -0.9f, 0, -0.1f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.1f),
			QuirkKeyframes.f(6, -1.45f, -0.25f, 0, -1.45f, 0.25f, 0, 0.2f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0.1f), QuirkKeyframes.rest(14) };
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, -0.35f, 0, -1.5f, 0.35f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.2f),
			QuirkKeyframes.rest(10) };
	private static final float[][] THROW = { QuirkKeyframes.f(0, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(4, -1.0f, 0, 0, -1.0f, 0, 0, 0.4f, 0, 0.4f, 0, 0, -0.3f, 0, 0, 0.2f), QuirkKeyframes.rest(12) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.8f, 0, 0.4f, -2.8f, 0, -0.4f, -0.2f, 0, -0.6f, 0, 0, 0.5f, 0, 0, -0.4f),
			QuirkKeyframes.f(16, -2.6f, 0, 0.5f, -2.6f, 0, -0.5f, -0.1f, 0, -0.5f, 0, 0, 0.4f, 0, 0, -0.3f), QuirkKeyframes.rest(24) };
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.6f, 0, 0.2f, -0.9f, 0.4f, 0, 0.6f, 0.5f, 0.4f, 0, 0, -0.3f, 0, 0, -0.4f),
			QuirkKeyframes.f(14, 0.6f, 0, 0.2f, -0.9f, 0.4f, 0, 0.6f, 0.5f, -0.4f, 0, 0, 0.4f, 0, 0, -0.4f), QuirkKeyframes.rest(18) };
	private static final float[][] POUND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.0f, 0, 0.3f, -3.0f, 0, -0.3f, -0.3f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.4f),
			QuirkKeyframes.f(7, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.8f, 0, -0.9f, 0, 0.2f, -0.9f, 0, -0.2f, 0.5f), QuirkKeyframes.f(14, -0.3f, 0, 0.2f, -0.3f, 0, -0.2f, 0.7f, 0, -0.8f, 0, 0.2f, -0.8f, 0, -0.2f, 0.4f),
			QuirkKeyframes.rest(20) };
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.6f),
			QuirkKeyframes.f(30, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.6f), QuirkKeyframes.rest(34) };
	private static final float[][] ROAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.5f, 0, 1.3f, -0.5f, 0, -1.3f, -0.3f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.7f),
			QuirkKeyframes.f(22, -0.6f, 0, 1.4f, -0.6f, 0, -1.4f, -0.3f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.7f), QuirkKeyframes.rest(28) };
	private static final float[][] STANCE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.25f, 0, -0.3f, 0, 0.35f, -0.3f, 0, -0.35f, 0.2f),
			QuirkKeyframes.f(80, -1.3f, -0.8f, 0, -1.3f, 0.8f, 0, 0.25f, 0, -0.3f, 0, 0.35f, -0.3f, 0, -0.35f, 0.2f), QuirkKeyframes.rest(84) };

	private static final FirstPersonClip FP_HAYMAKER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -75, 10, 0, 0.05f, 0.45f, 0.2f),
			FirstPersonClip.f(6, 15, 20, 0, 0.12f, -0.05f, -0.25f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, 24, 0, 0.16f, 0.08f, -0.22f),
			FirstPersonClip.f(6, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CLAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -25, -25, 0, -0.15f, 0.15f, -0.05f),
			FirstPersonClip.f(6, -25, 35, 0, 0.3f, 0.15f, -0.2f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_POUND = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(7, 35, 10, 0, 0.05f, -0.3f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, -80, 0, 0, 0, 0.5f, 0.2f), FirstPersonClip.f(4, -10, 20, 0, 0.1f, 0.05f, -0.25f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -25, 40, 0, 0.25f, 0.15f, 0),
			FirstPersonClip.f(80, -25, 40, 0, 0.25f, 0.15f, 0), FirstPersonClip.f(84, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ROAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -40, 30, -0.25f, 0.2f, 0),
			FirstPersonClip.f(22, -20, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(28, 0, 0, 0, 0, 0, 0)).both();

	public FourArmsClient() {
		ModelPart root = AlienParts.bake(64, 64, r -> {
			// the second pair: the skin's own arm UVs (right arm at 40,16, left at 32,48) and their sleeve layer (40,32 / 48,48),
			// shoulder pivots under the first pair's
			r.addOrReplaceChild("lower_right", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(-0.25f))
						.texOffs(40, 32).addBox(-3, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(0f)),
					PartPose.ZERO);
			r.addOrReplaceChild("lower_left", CubeListBuilder.create().texOffs(32, 48).addBox(-1, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(-0.25f))
						.texOffs(48, 48).addBox(-1, -2, -2, 4, 11, 4, new net.minecraft.client.model.geom.builders.CubeDeformation(0f)),
					PartPose.ZERO);
		});
		lowerRight = root.getChild("lower_right");
		lowerLeft = root.getChild("lower_left");
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** The lower arms' extra swing for the move playing: {rightX, leftX, rightZ, leftZ} added on top of following the upper arms. */
	private static float[] lowerOffsets(AbstractClientPlayer p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		if ((s.kitFlags & FourArmsKit.F_HOLDING) != 0) {
			return new float[] { -2.0f, -2.0f, 0.3f, -0.3f };
		}
		return switch (s.animId) {
			case FourArmsKit.ANIM_PUNCH_L, FourArmsKit.ANIM_PUNCH_R -> t < 6 ? new float[] { 0.5f, 0.5f, 0, 0 } : null;
			case FourArmsKit.ANIM_BARRAGE -> t < 18 ? new float[] { (int) (t / 4) % 2 == 0 ? -1.5f : 0.2f, (int) (t / 4) % 2 == 0 ? 0.2f : -1.5f, 0, 0 } : null;
			case FourArmsKit.ANIM_CLAP -> t < 14 ? new float[] { -0.2f, -0.2f, t < 4 ? 0.5f : -0.3f, t < 4 ? -0.5f : 0.3f } : null;
			case FourArmsKit.ANIM_ROAR -> t < 28 ? new float[] { 0.6f, 0.6f, 0.4f, -0.4f } : null;
			default -> null;
		};
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
		float[] off = lowerOffsets(p, partial);
		// follow the upper arms with a lag (and a little wider): the walk swing shows as four arms, not two thick ones
		lowerRight.x = -5.6f;
		lowerRight.y = 7.6f;
		lowerRight.z = 1.0f;
		lowerRight.xRot = model.rightArm.xRot * 0.75f + (off == null ? 0 : off[0]);
		lowerRight.yRot = model.rightArm.yRot * 0.6f;
		lowerRight.zRot = model.rightArm.zRot * 0.4f + 0.8f + (off == null ? 0 : off[2]);
		lowerLeft.x = 5.6f;
		lowerLeft.y = 7.6f;
		lowerLeft.z = 1.0f;
		lowerLeft.xRot = model.leftArm.xRot * 0.75f + (off == null ? 0 : off[1]);
		lowerLeft.yRot = model.leftArm.yRot * 0.6f;
		lowerLeft.zRot = model.leftArm.zRot * 0.4f - 0.8f + (off == null ? 0 : off[3]);
		AlienParts.on(pose, model.body, () -> {
			// the body bone's pivot is the neck; the lower arms hang from the ribs
			lowerRight.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			lowerLeft.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		});
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// the lower arm of the same side, just under and behind the upper one
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
		ModelPart lower = right ? lowerRight : lowerLeft;
		lower.x = right ? -1.0f : 1.0f;
		lower.y = 2.5f;
		lower.z = 3.2f;
		lower.xRot = 0.1f;
		lower.yRot = 0f;
		lower.zRot = right ? 0.12f : -0.12f;
		float[] off = lowerOffsets(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (off != null) {
			lower.xRot += (right ? off[0] : off[1]) * 0.25f;
		}
		lower.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case FourArmsKit.ANIM_HAYMAKER -> HAYMAKER;
			case FourArmsKit.ANIM_PUNCH_L -> PUNCH_L;
			case FourArmsKit.ANIM_PUNCH_R -> PUNCH_R;
			case FourArmsKit.ANIM_BARRAGE -> ((int) (ticks / 4)) % 2 == 0 ? PUNCH_R : PUNCH_L;
			case FourArmsKit.ANIM_CLAP -> CLAP;
			case FourArmsKit.ANIM_GRAB -> GRAB;
			case FourArmsKit.ANIM_THROW -> THROW;
			case FourArmsKit.ANIM_LEAP -> LEAP;
			case FourArmsKit.ANIM_CHARGE -> CHARGE;
			case FourArmsKit.ANIM_POUND -> POUND;
			case FourArmsKit.ANIM_DIVE -> DIVE;
			case FourArmsKit.ANIM_ROAR -> ROAR;
			case FourArmsKit.ANIM_STANCE -> STANCE;
			default -> null;
		};
		float t = animId == FourArmsKit.ANIM_BARRAGE ? ticks % 4f * 1.5f : ticks;
		boolean played = clip != null && (animId != FourArmsKit.ANIM_BARRAGE || ticks < 18) && QuirkKeyframes.play(m, clip, t, 1f);
		if ((Omnitrix.state(p).kitFlags & FourArmsKit.F_HOLDING) != 0) {
			// holding something overhead
			m.rightArm.xRot = -2.9f;
			m.leftArm.xRot = -2.9f;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			return true;
		}
		// a heavy walk: the arms hang a little wider
		if (!played) {
			m.rightArm.zRot += 0.08f;
			m.leftArm.zRot -= 0.08f;
			m.head.xRot += Mth.sin(age * 0.05f) * 0.01f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case FourArmsKit.ANIM_HAYMAKER -> FP_HAYMAKER;
			case FourArmsKit.ANIM_PUNCH_L, FourArmsKit.ANIM_PUNCH_R, FourArmsKit.ANIM_BARRAGE, FourArmsKit.ANIM_GRAB -> FP_PUNCH;
			case FourArmsKit.ANIM_CLAP -> FP_CLAP;
			case FourArmsKit.ANIM_POUND, FourArmsKit.ANIM_DIVE -> FP_POUND;
			case FourArmsKit.ANIM_THROW, FourArmsKit.ANIM_LEAP -> FP_THROW;
			case FourArmsKit.ANIM_STANCE, FourArmsKit.ANIM_CHARGE -> FP_GUARD;
			case FourArmsKit.ANIM_ROAR -> FP_ROAR;
			default -> null;
		};
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 4.0f, -2.08f, 3.4f };
	}
}
