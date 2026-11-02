package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.1: Grey Matter's client kit -- the tiny grey Galvan. The whole body is a third of a player (the scale attribute does
 * it); on top of that the HEAD is drawn half again as big (the player's head is hidden and redrawn from the skin at x1.55 on
 * the same bone), and the legs splay like a frog's. A pose for every move.
 *
 * <p>v0.17.2 revamp: the Gizmo Turret's model (a cobbled box, a dish and a barrel with a blinking red light), Inside Job (he
 * shrinks into the creature over 4 ticks and is hidden while inside; the creature shudders and bulges in pulses --
 * {@code GreyMatterInsideRenderMixin}; he pops out with a spin), Rewire's pointing finger and the flat-to-wall crawl.
 */
public final class GreyMatterClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] WORKSHOP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.1f, 0.35f, 0f, -1f, -0.35f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.f(5, -0.9f, 0.2f, 0f, -1.2f, -0.45f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f), QuirkKeyframes.f(8, -1.2f, 0.4f, 0f, -0.9f, -0.2f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.rest(12) };
	private static final FirstPersonClip FP_WORKSHOP = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(2, -15f, 25f, 0f, 0.12f, 0.05f, -0.1f),
			FirstPersonClip.f(5, -5f, 15f, 0f, 0.06f, 0f, -0.05f), FirstPersonClip.f(8, -15f, 25f, 0f, 0.12f, 0.05f, -0.1f), FirstPersonClip.f(12, 0f, 0f, 0f, 0f, 0f, 0f)).both();
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/grey_matter.png");
	public static final float HEAD_SCALE = 1.55f;

	private final ModelPart bigHead;

	private static final float[][] PINCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.0f, 0.3f, 0, 0.3f, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(6, -1.9f, 0.1f, 0, 0.3f, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(11) };
	private static final float[][] STRIKE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(5, -0.7f, 0, 0, -0.7f, 0, 0, 0.5f, 0, -0.4f, 0, 0, 0.3f, 0, 0, 0.3f), QuirkKeyframes.rest(12) };
	private static final float[][] ANALYZE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.3f, -0.7f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.f(20, -2.3f, -0.7f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15f), QuirkKeyframes.rest(26) };
	/** v0.17.2 Gizmo Turret: crouched over it, both hands fitting parts, then one thrown up -- done. */
	private static final float[][] TURRET = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.0f, -0.3f, 0, -1.1f, 0.3f, 0, 0.5f, 0, -0.5f, 0, 0.15f, -0.5f, 0, -0.15f, 0.45f),
			QuirkKeyframes.f(7, -0.7f, -0.4f, 0, -1.3f, 0.2f, 0, 0.5f, 0, -0.5f, 0, 0.15f, -0.5f, 0, -0.15f, 0.45f),
			QuirkKeyframes.f(11, -1.3f, -0.2f, 0, -0.8f, 0.4f, 0, 0.5f, 0, -0.5f, 0, 0.15f, -0.5f, 0, -0.15f, 0.45f),
			QuirkKeyframes.f(15, -2.9f, 0, 0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(20) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.8f, 0, 0.5f, 0.8f, 0, -0.5f, 0.3f, 0, -1.0f, 0, 0.2f, -1.0f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(16, 0.8f, 0, 0.5f, 0.8f, 0, -0.5f, 0.3f, 0, -1.0f, 0, 0.2f, -1.0f, 0, -0.2f, -0.3f), QuirkKeyframes.rest(22) };
	private static final float[][] SCURRY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 1.2f, 0, 0.25f, 1.2f, 0, -0.25f, 0.5f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			QuirkKeyframes.f(96, 1.2f, 0, 0.25f, 1.2f, 0, -0.25f, 0.5f, 0, 0, 0, 0, 0, 0, 0, -0.4f), QuirkKeyframes.rest(100) };
	private static final float[][] TRAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0, -0.2f, -0.7f, 0, 0.2f, 0.55f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.4f),
			QuirkKeyframes.f(7, -0.6f, 0, -0.2f, -1.0f, 0, 0.2f, 0.55f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.4f),
			QuirkKeyframes.f(11, -0.9f, 0, -0.2f, -0.7f, 0, 0.2f, 0.55f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.4f), QuirkKeyframes.rest(16) };
	/** v0.17.2 Inside Job: a headlong dive, arms first (seen for the 4 ticks he shrinks in). */
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, 0.9f, 0, 0.6f, 0, 0.1f, 0.6f, 0, -0.1f, -0.5f),
			QuirkKeyframes.f(6, -3.0f, 0, 0.15f, -3.0f, 0, -0.15f, 0.9f, 0, 0.6f, 0, 0.1f, 0.6f, 0, -0.1f, -0.5f), QuirkKeyframes.rest(10) };
	/** v0.17.2 Inside Job's exit: out with a little spin (the body's yaw, once round) and arms flung wide. */
	private static final float[][] POP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.4f, -0.3f, 0, -1.4f, 0, 2.2f, 0, 0, 0.3f, 0, 0, -0.3f, -0.2f),
			QuirkKeyframes.f(7, -0.3f, 0, 1.2f, -0.3f, 0, -1.2f, 0, 4.8f, 0, 0, 0.3f, 0, 0, -0.3f, 0.1f),
			QuirkKeyframes.f(10, 0, 0, 0.4f, 0, 0, -0.4f, 0, 6.2832f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(14) };
	/** v0.17.2 Rewire: one arm out, finger pointed at the block, the other hand at his temple. */
	private static final float[][] REWIRE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, -0.1f, 0, -2.4f, 0.8f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.f(10, -1.55f, -0.1f, 0, -2.4f, 0.8f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(15) };
	private static final float[][] REPAIR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.3f, -0.4f, 0, -1.3f, 0.4f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.25f),
			QuirkKeyframes.f(8, -1.1f, -0.5f, 0, -1.4f, 0.3f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.25f),
			QuirkKeyframes.f(14, -1.4f, -0.3f, 0, -1.1f, 0.5f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.25f), QuirkKeyframes.rest(22) };
	private static final float[][] MIND = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.5f, -0.9f, 0, -2.5f, 0.9f, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(26, -2.5f, -0.9f, 0, -2.5f, 0.9f, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(32) };
	private static final float[][] JAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 0.25f, 0, 0.4f, 0, 0, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(7) };
	private static final float[][] READ = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.rest(10) };

	private static final FirstPersonClip FP_PINCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -30, 25, 0, 0.15f, 0.15f, -0.25f),
			FirstPersonClip.f(6, -28, 22, 0, 0.14f, 0.14f, -0.24f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CHOP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(5, 25, 15, 0, 0.1f, -0.25f, -0.1f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_THINK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -60, 45, 20, 0.3f, 0.45f, 0.1f),
			FirstPersonClip.f(20, -60, 45, 20, 0.3f, 0.45f, 0.1f), FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_TINKER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 25, 0, 0.18f, 0.1f, -0.05f),
			FirstPersonClip.f(8, -28, 20, 5, 0.16f, 0.14f, -0.08f), FirstPersonClip.f(14, -20, 25, 0, 0.18f, 0.1f, -0.05f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_TEMPLES = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -45, 22, 12, 0.1f, 0.3f, 0.05f),
			FirstPersonClip.f(26, -46, 22, 12, 0.1f, 0.3f, 0.05f), FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(16, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.22f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));
	/** v0.17.2: Rewire's point -- the hand out straight at the block. */
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -12, 8, 0, 0.05f, 0.18f, -0.3f),
			FirstPersonClip.f(10, -12, 8, 0, 0.05f, 0.18f, -0.3f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	/** v0.17.2: Inside Job's dive -- both hands thrust forward. */
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -35, 0, 0, 0f, 0.25f, -0.45f),
			FirstPersonClip.f(6, -35, 0, 0, 0f, 0.25f, -0.45f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();

	/** v0.17.2 Inside Job: creature entity id -> the game time its shudder runs to (kept up from every inside Grey Matter's tick). */
	private static final Map<Integer, Long> SHAKING = new ConcurrentHashMap<>();
	private static boolean looksRegistered;

	public GreyMatterClient() {
		ModelPart root = AlienParts.bake(64, 64, r -> r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
				// v0.17.1: the skin's hat layer on the big head too (its second layer)
				.texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new net.minecraft.client.model.geom.builders.CubeDeformation(0.5f)),
				PartPose.ZERO));
		bigHead = root.getChild("head");
		if (!looksRegistered) {
			looksRegistered = true;
			OmnitrixRenderers.projectileLook("grey_matter_turret", GreyMatterClient::turret); // v0.17.2 Gizmo Turret
		}
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		AlienParts.on(pose, model.head, () -> {
			pose.pushPose();
			pose.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
			// one buffer at a time: fetching another render type ends the previous one
			bigHead.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN)), light, OverlayTexture.NO_OVERLAY);
			// v0.17.1 (user: "Remove these big eyes from grey matter please, his skin already has eyes"): no eye boxes over the face
			pose.popPose();
		});
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		// the big head replaces the player's (renderExtras redraws it on the same bone)
		m.head.visible = false;
		m.hat.visible = false;
		float[][] clip = switch (animId) {
			case GreyMatterKit.ANIM_PINCH -> PINCH;
			case GreyMatterKit.ANIM_STRIKE -> STRIKE;
			case GreyMatterKit.ANIM_ANALYZE -> ANALYZE;
			case GreyMatterKit.ANIM_TURRET -> TURRET;
			case GreyMatterKit.ANIM_LEAP -> LEAP;
			case GreyMatterKit.ANIM_SCURRY -> SCURRY;
			case GreyMatterKit.ANIM_TRAP -> TRAP;
			case GreyMatterKit.ANIM_DIVE -> DIVE;
			case GreyMatterKit.ANIM_POP -> POP;
			case GreyMatterKit.ANIM_REWIRE -> REWIRE;
			case GreyMatterKit.ANIM_REPAIR -> REPAIR;
			case GreyMatterKit.ANIM_MIND -> MIND;
			case GreyMatterKit.ANIM_JAB -> JAB;
			case GreyMatterKit.ANIM_READ -> READ;
			case GreyMatterKit.ANIM_WORKSHOP -> WORKSHOP;
			default -> null;
		};
		OmnitrixState s = Omnitrix.state(p);
		if (animId == GreyMatterKit.ANIM_SCURRY && (s.kitFlags & GreyMatterKit.F_SCURRY) == 0 && ticks > 4) {
			clip = null;
		}
		if (animId == GreyMatterKit.ANIM_LEAP && (s.kitFlags & GreyMatterKit.F_LEAP) == 0 && ticks > 4) {
			clip = null;
		}
		// v0.17.2: riding a player's shoulder (OmnitrixRiding) -- a move's clip still plays over it
		boolean shoulder = com.projecthero.mod.omnitrix.OmnitrixRiding.onShoulder(p);
		if (shoulder) {
			com.projecthero.mod.client.omnitrix.OmnitrixRidePoses.shoulder(m, p, age);
		}
		// v0.17.2 Sticky Climber: flat to the wall, limbs splayed, hands walking up it
		// (the synced flag for everyone else; his own client knows its physics this frame)
		boolean climbing = !shoulder && !p.isPassenger() && ((s.kitFlags & GreyMatterKit.F_CLIMB) != 0
				|| p instanceof net.minecraft.client.player.LocalPlayer lp && !lp.onGround() && !lp.isInWater() && lp.onClimbable());
		if (climbing) {
			float sw = Mth.sin(age * 0.45f) * 0.35f;
			m.rightArm.xRot = -2.7f + sw;
			m.leftArm.xRot = -2.7f - sw;
			m.rightArm.zRot = 0.45f;
			m.leftArm.zRot = -0.45f;
			m.rightLeg.xRot = -0.35f - sw;
			m.leftLeg.xRot = -0.35f + sw;
			m.rightLeg.zRot = 0.5f;
			m.leftLeg.zRot = -0.5f;
			m.head.xRot = -0.35f;
		}
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && !shoulder && !climbing) {
			// a frog's splayed stance
			m.rightLeg.zRot += 0.14f;
			m.leftLeg.zRot -= 0.14f;
			m.rightArm.zRot += 0.1f;
			m.leftArm.zRot -= 0.1f;
		}
		return played || climbing;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case GreyMatterKit.ANIM_PINCH, GreyMatterKit.ANIM_READ -> FP_PINCH;
			case GreyMatterKit.ANIM_STRIKE -> FP_CHOP;
			case GreyMatterKit.ANIM_ANALYZE -> FP_THINK;
			case GreyMatterKit.ANIM_TURRET, GreyMatterKit.ANIM_TRAP, GreyMatterKit.ANIM_REPAIR -> FP_TINKER;
			case GreyMatterKit.ANIM_MIND -> FP_TEMPLES;
			case GreyMatterKit.ANIM_LEAP, GreyMatterKit.ANIM_SCURRY -> FP_RUN;
			case GreyMatterKit.ANIM_JAB -> FP_JAB;
			case GreyMatterKit.ANIM_WORKSHOP -> FP_WORKSHOP;
			case GreyMatterKit.ANIM_REWIRE -> FP_POINT;
			case GreyMatterKit.ANIM_DIVE -> FP_DIVE;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		float t = now - s.animStart;
		var r = p.getRandom();
		if ((s.animId == GreyMatterKit.ANIM_ANALYZE || s.animId == GreyMatterKit.ANIM_MIND) && t < 26 && r.nextInt(3) == 0) {
			p.level().addParticle(ParticleTypes.ENCHANT, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + p.getBbHeight() + 0.2,
					p.getZ() + (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, 0.3, (r.nextDouble() - 0.5) * 0.4);
		}
		// v0.17.2 Inside Job: keep the creature he is in shuddering (the server's synced timer holds its entity id + 1)
		if ((s.kitFlags & GreyMatterKit.F_INSIDE) != 0) {
			long target = s.readyAt(GreyMatterKit.ID + "." + GreyMatterKit.INSIDE_TARGET);
			if (target > 0) {
				SHAKING.put((int) (target - 1), now + 3);
			}
		}
		if (!SHAKING.isEmpty()) {
			SHAKING.values().removeIf(until -> until < now);
		}
	}

	// ---------------------------------------------------------------- v0.17.2 Inside Job render hooks (GreyMatterInsideRenderMixin)

	/** Ticks since Inside Job's dive began, or -1 if {@code e} is not a Grey Matter inside a creature. */
	private static float insideTicks(LivingEntity e, float partial) {
		if (!(e instanceof Player p) || !GreyMatterKit.insideNow(p)) {
			return -1f;
		}
		OmnitrixState s = Omnitrix.state(p);
		return s.animId == GreyMatterKit.ANIM_DIVE ? p.level().getGameTime() + partial - s.animStart : 99f;
	}

	/** He is not drawn once he has shrunk all the way in (4 ticks into the dive). */
	public static boolean hiddenInside(LivingEntity e, float partial) {
		return insideTicks(e, partial) >= 4f;
	}

	/** The 4-tick shrink into the creature (on him), and the creature's shudder and bulges (on it). */
	public static void insideJitter(LivingEntity e, PoseStack pose, float partial) {
		float in = insideTicks(e, partial);
		if (in >= 0f) {
			float k = Math.max(0.02f, 1f - in / 4f);
			pose.scale(k, k, k);
			return;
		}
		if (SHAKING.isEmpty() || !SHAKING.containsKey(e.getId())) {
			return;
		}
		float t = e.level().getGameTime() + partial;
		// a bulge with every pulse (each half second), then small squirms in between
		float phase = t % 10f;
		float bulge = (float) Math.exp(-phase * 0.55f) * 0.11f + Mth.sin(t * 2.3f) * 0.015f;
		pose.translate(Mth.sin(t * 3.1f) * 0.03f, 0f, Mth.cos(t * 2.7f) * 0.03f);
		pose.scale(1f + bulge, 1f - bulge * 0.4f, 1f + bulge);
	}

	// ---------------------------------------------------------------- v0.17.2 the Gizmo Turret's model

	/** A fist-sized turret: a cobbled box, a dish on top, a barrel aimed where it last shot, and a blinking red light. */
	private static void turret(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		var blocks = Minecraft.getInstance().getBlockRenderer();
		int light = LevelRenderer.getLightColor(e.level(), e.blockPosition());
		float age = e.tickCount + partial;
		float build = Math.min(1f, age / 5f); // it snaps together over a quarter second
		float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		pose.pushPose();
		pose.scale(build, build, build);
		// the body
		pose.pushPose();
		pose.translate(-0.14f, 0f, -0.14f);
		pose.scale(0.28f, 0.24f, 0.28f);
		blocks.renderSingleBlock(Blocks.COBBLESTONE.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		// the turning head: the dish and the barrel
		pose.pushPose();
		pose.translate(0f, 0.24f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.pushPose();
		pose.translate(-0.1f, 0.03f, -0.14f);
		pose.mulPose(Axis.XP.rotationDegrees(-35f));
		pose.scale(0.2f, 0.2f, 0.2f);
		blocks.renderSingleBlock(Blocks.DAYLIGHT_DETECTOR.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		pose.pushPose();
		pose.translate(-0.12f, -0.04f, 0.0f);
		pose.scale(0.24f, 0.24f, 0.34f);
		BlockState rod = Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.SOUTH);
		blocks.renderSingleBlock(rod, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		// the red light, blinking twice a second
		if (((int) (age / 5f)) % 2 == 0) {
			pose.pushPose();
			pose.translate(-0.03f, 0.1f, -0.15f);
			pose.scale(0.06f, 0.06f, 0.06f);
			blocks.renderSingleBlock(Blocks.REDSTONE_BLOCK.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		pose.popPose();
		pose.popPose();
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "move his omnitrix badge to his back"): over the spot on his back where the skin's own symbol was (painted
		// out), facing backward (the 2)
		return new float[] { 0f, 4.0f, 2.3f, 8.0f, 2f };
	}
}
