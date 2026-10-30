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
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] CLIMB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.6f, 0f, 0.3f, -2.6f, 0f, -0.3f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.3f),
			QuirkKeyframes.f(7, -2.4f, 0f, 0.5f, -2.4f, 0f, -0.5f, 0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.3f), QuirkKeyframes.rest(12) };
	private static final FirstPersonClip FP_CLIMB = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -60f, -10f, 0f, -0.05f, 0.3f, 0f),
			FirstPersonClip.f(7, -50f, -10f, 15f, -0.05f, 0.25f, 0f), FirstPersonClip.f(12, 0f, 0f, 0f, 0f, 0f, 0f)).both();

	/** Wall Climb on and on a wall: all four hands reaching up in turn. */
	private static boolean climbing(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & FourArmsKit.F_CLIMB) != 0 && p.onClimbable() && !p.onGround();
	}
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
		if (climbing(p)) {
			float c = Mth.sin((p.tickCount + partial) * 0.6f);
			return new float[] { -2.0f - c * 0.6f, -2.0f + c * 0.6f, 0.2f, -0.2f }; // the lower pair climbs out of step with the upper
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
		// v0.17.1 (user: "make four arms extra arms not look so weird ... I dont mind if the arms overlap a bit"): the lower pair
		// hangs from the ribs right under the upper pair, nearly parallel to them (a slight flare, not splayed out), a touch behind
		// so the two pairs layer instead of crossing; they swing with the walk half a beat behind the upper arms
		float lag = Mth.sin(p.walkAnimation.position(partial) * 0.6662f - 0.9f) * 0.9f * p.walkAnimation.speed(partial);
		boolean busy = off != null;
		lowerRight.x = -5.4f;
		lowerRight.y = 5.4f;
		lowerRight.z = 1.0f;
		lowerRight.xRot = busy ? model.rightArm.xRot * 0.6f + off[0] : lag * 0.85f + model.rightArm.xRot * 0.15f;
		lowerRight.yRot = model.rightArm.yRot * 0.4f;
		lowerRight.zRot = 0.3f + model.rightArm.zRot * 0.35f + (busy ? off[2] : 0f);
		lowerLeft.x = 5.4f;
		lowerLeft.y = 5.4f;
		lowerLeft.z = 1.0f;
		lowerLeft.xRot = busy ? model.leftArm.xRot * 0.6f + off[1] : -lag * 0.85f + model.leftArm.xRot * 0.15f;
		lowerLeft.yRot = model.leftArm.yRot * 0.4f;
		lowerLeft.zRot = -0.3f + model.leftArm.zRot * 0.35f + (busy ? off[3] : 0f);
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
			case FourArmsKit.ANIM_CLIMB -> CLIMB;
			default -> null;
		};
		float t = animId == FourArmsKit.ANIM_BARRAGE ? ticks % 4f * 1.5f : ticks;
		if (chargePose(p, m)) {
			return true;
		}
		boolean played = clip != null && (animId != FourArmsKit.ANIM_BARRAGE || ticks < 18) && QuirkKeyframes.play(m, clip, t, 1f);
		if ((Omnitrix.state(p).kitFlags & FourArmsKit.F_HOLDING) != 0) {
			// holding something overhead
			m.rightArm.xRot = -2.9f;
			m.leftArm.xRot = -2.9f;
			m.rightArm.zRot = 0.25f;
			m.leftArm.zRot = -0.25f;
			return true;
		}
		if (!played && climbing(p)) {
			// v0.17.1: Wall Climb -- hand over hand up the wall, the legs pushing
			float c = Mth.sin(age * 0.6f);
			m.rightArm.xRot = -2.6f + c * 0.6f;
			m.leftArm.xRot = -2.6f - c * 0.6f;
			m.rightArm.zRot = 0.15f;
			m.leftArm.zRot = -0.15f;
			m.rightLeg.xRot = -0.5f - c * 0.5f;
			m.leftLeg.xRot = -0.5f + c * 0.5f;
			m.head.xRot = -0.4f;
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

	/** v0.17.1: holding X -- the long leap's crouch, deeper as it charges, all four fists braced on the ground. */
	private static boolean chargePose(AbstractClientPlayer p, HumanoidModel<?> m) {
		if (!(com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit k) || !k.flag(p, FourArmsKit.F_LEAP_CHARGE)) {
			return false;
		}
		float c = k.leapCharge(p);
		float shake = c > 0.95f ? Mth.sin(p.tickCount * 2.1f) * 0.03f : 0f;
		m.body.xRot = 0.35f + 0.25f * c;
		m.head.xRot = Math.min(m.head.xRot, 0f) - 0.2f;
		m.rightArm.xRot = -0.55f - 0.3f * c + shake;
		m.leftArm.xRot = -0.55f - 0.3f * c - shake;
		m.rightArm.zRot = 0.25f;
		m.leftArm.zRot = -0.25f;
		m.rightLeg.xRot = -0.5f - 0.35f * c;
		m.leftLeg.xRot = -0.5f - 0.35f * c;
		m.rightLeg.zRot = 0.12f;
		m.leftLeg.zRot = -0.12f;
		return true;
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		// the long leap flies drag-free on its owner's client (as the Hulk's Super Leap): the launch's horizontal speed held,
		// vanilla's vertical drag taken back off -- until he hits something, lands or swims
		if (p != Minecraft.getInstance().player || !(com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit k)) {
			return;
		}
		net.minecraft.world.phys.Vec3 v = k.farLeapVelocity(p);
		// v0.17.1 (user: "four arms leap sometimes doesn't launch him in the direction that the player aims"): the take-off tick
		// counts too -- he is still on the ground then, and the ground's friction and the movement keys bent the launch (strafing
		// skewed it); from the first tick the launch's own heading is held
		boolean takeoff = v != null && p.level().getGameTime() - k.timer(p, "air_at") <= 3;
		if (v == null || (p.onGround() && !takeoff) || p.horizontalCollision || p.isInWater() || p.isInLava() || p.getAbilities().flying || p.isPassenger()) {
			return;
		}
		net.minecraft.world.phys.Vec3 d = p.getDeltaMovement();
		p.setDeltaMovement(v.x, d.y / com.projecthero.mod.hulk.HulkLeap.VERTICAL_DRAG, v.z);
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		if (!(com.projecthero.mod.omnitrix.alien.Aliens.kit(FourArmsKit.ID) instanceof FourArmsKit k) || !k.flag(p, FourArmsKit.F_LEAP_CHARGE)) {
			return;
		}
		float c = k.leapCharge(p);
		// v0.17.1 (user: "convert everything to blocks"): the distance in blocks, not metres
		String value = net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.blocks", Math.round(FourArmsKit.leapBlocks(c))).getString();
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6,
				net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.four_arms.leap"), c, c >= 1f ? 0xFFFFE060 : 0xFFE04030, value, 0xFFFFFFFF);
	}

	@Override
	public int hudHeight() {
		return 10;
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
			case FourArmsKit.ANIM_CLIMB -> FP_CLIMB;
			default -> null;
		};
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "four arms badge needs to be on the side of his top left arm"): the outer side of his upper left arm, near
		// the shoulder (arm-bone pixels: the cube spans x -1..3, y -2..10, z -2..2; the 1 = facing out of +x)
		return new float[] { 3.3f, 1.2f, 0f, 2.8f, 1f };
	}

	@Override
	public int badgeBone() {
		return BADGE_LEFT_ARM;
	}
}
