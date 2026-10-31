package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Grey Matter's client kit -- the tiny grey Galvan. The whole body is a third of a player (the scale attribute does
 * it); on top of that the HEAD is drawn half again as big (the player's head is hidden and redrawn from the skin at x1.55 on
 * the same bone), with two huge glossy black eyes bulging from the face, and the legs splay like a frog's. A pose for every
 * move.
 */
public final class GreyMatterClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] WORKSHOP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.1f, 0.35f, 0f, -1f, -0.35f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.f(5, -0.9f, 0.2f, 0f, -1.2f, -0.45f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f), QuirkKeyframes.f(8, -1.2f, 0.4f, 0f, -0.9f, -0.2f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.4f),
			QuirkKeyframes.rest(12) };
	private static final FirstPersonClip FP_WORKSHOP = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(2, -15f, 25f, 0f, 0.12f, 0.05f, -0.1f),
			FirstPersonClip.f(5, -5f, 15f, 0f, 0.06f, 0f, -0.05f), FirstPersonClip.f(8, -15f, 25f, 0f, 0.12f, 0.05f, -0.1f), FirstPersonClip.f(12, 0f, 0f, 0f, 0f, 0f, 0f)).both();
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/grey_matter.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/grey_matter_extras.png");
	public static final float HEAD_SCALE = 1.55f;

	private static final float[] EYE = { 0, 0, 8, 8 };

	private final ModelPart bigHead;

	private static final float[][] PINCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.0f, 0.3f, 0, 0.3f, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(6, -1.9f, 0.1f, 0, 0.3f, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(11) };
	private static final float[][] STRIKE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.8f, 0, 0.2f, -2.8f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.f(5, -0.7f, 0, 0, -0.7f, 0, 0, 0.5f, 0, -0.4f, 0, 0, 0.3f, 0, 0, 0.3f), QuirkKeyframes.rest(12) };
	private static final float[][] ANALYZE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.3f, -0.7f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.f(20, -2.3f, -0.7f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15f), QuirkKeyframes.rest(26) };
	private static final float[][] THROW = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.8f, 0, 0.2f, 0.3f, 0, 0, -0.15f, 0.3f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(6, -1.1f, 0, 0, 0.5f, 0, 0, 0.25f, -0.25f, 0.3f, 0, 0, -0.3f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.8f, 0, 0.5f, 0.8f, 0, -0.5f, 0.3f, 0, -1.0f, 0, 0.2f, -1.0f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(16, 0.8f, 0, 0.5f, 0.8f, 0, -0.5f, 0.3f, 0, -1.0f, 0, 0.2f, -1.0f, 0, -0.2f, -0.3f), QuirkKeyframes.rest(22) };
	private static final float[][] SCURRY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 1.2f, 0, 0.25f, 1.2f, 0, -0.25f, 0.5f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			QuirkKeyframes.f(96, 1.2f, 0, 0.25f, 1.2f, 0, -0.25f, 0.5f, 0, 0, 0, 0, 0, 0, 0, -0.4f), QuirkKeyframes.rest(100) };
	private static final float[][] TRAP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.9f, 0, -0.2f, -0.7f, 0, 0.2f, 0.55f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.4f),
			QuirkKeyframes.f(7, -0.6f, 0, -0.2f, -1.0f, 0, 0.2f, 0.55f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.4f),
			QuirkKeyframes.f(11, -0.9f, 0, -0.2f, -0.7f, 0, 0.2f, 0.55f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.4f), QuirkKeyframes.rest(16) };
	private static final float[][] SMOKE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.5f, 0, 0.2f, 0.2f, 0, 0, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, 0.4f, 0, 0.2f, 0.2f, 0, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(10) };
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
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 0, 10, 0, 0.35f, 0.15f),
			FirstPersonClip.f(6, -10, 20, 0, 0.1f, 0.05f, -0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_TINKER = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 25, 0, 0.18f, 0.1f, -0.05f),
			FirstPersonClip.f(8, -28, 20, 5, 0.16f, 0.14f, -0.08f), FirstPersonClip.f(14, -20, 25, 0, 0.18f, 0.1f, -0.05f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_TEMPLES = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -45, 22, 12, 0.1f, 0.3f, 0.05f),
			FirstPersonClip.f(26, -46, 22, 12, 0.1f, 0.3f, 0.05f), FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(16, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.22f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	public GreyMatterClient() {
		ModelPart root = AlienParts.bake(64, 64, r -> r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
				// v0.17.1: the skin's hat layer on the big head too (its second layer)
				.texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new net.minecraft.client.model.geom.builders.CubeDeformation(0.5f)),
				PartPose.ZERO));
		bigHead = root.getChild("head");
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
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
			case GreyMatterKit.ANIM_DECOY -> THROW;
			case GreyMatterKit.ANIM_LEAP -> LEAP;
			case GreyMatterKit.ANIM_SCURRY -> SCURRY;
			case GreyMatterKit.ANIM_TRAP -> TRAP;
			case GreyMatterKit.ANIM_SMOKE -> SMOKE;
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
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// a frog's splayed stance
			m.rightLeg.zRot += 0.14f;
			m.leftLeg.zRot -= 0.14f;
			m.rightArm.zRot += 0.1f;
			m.leftArm.zRot -= 0.1f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case GreyMatterKit.ANIM_PINCH, GreyMatterKit.ANIM_READ -> FP_PINCH;
			case GreyMatterKit.ANIM_STRIKE -> FP_CHOP;
			case GreyMatterKit.ANIM_ANALYZE -> FP_THINK;
			case GreyMatterKit.ANIM_DECOY, GreyMatterKit.ANIM_SMOKE -> FP_THROW;
			case GreyMatterKit.ANIM_TRAP, GreyMatterKit.ANIM_REPAIR -> FP_TINKER;
			case GreyMatterKit.ANIM_MIND -> FP_TEMPLES;
			case GreyMatterKit.ANIM_LEAP, GreyMatterKit.ANIM_SCURRY -> FP_RUN;
			case GreyMatterKit.ANIM_JAB -> FP_JAB;
			case GreyMatterKit.ANIM_WORKSHOP -> FP_WORKSHOP;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() - s.animStart;
		var r = p.getRandom();
		if ((s.animId == GreyMatterKit.ANIM_ANALYZE || s.animId == GreyMatterKit.ANIM_MIND) && t < 26 && r.nextInt(2) == 0) {
			p.level().addParticle(ParticleTypes.ENCHANT, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + p.getBbHeight() + 0.2,
					p.getZ() + (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.4, 0.3, (r.nextDouble() - 0.5) * 0.4);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "move his omnitrix badge to his back"): over the spot on his back where the skin's own symbol was (painted
		// out), facing backward (the 2)
		return new float[] { 0f, 4.0f, 2.3f, 8.0f, 2f };
	}
}
