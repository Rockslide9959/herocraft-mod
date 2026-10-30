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
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Ghostfreak's client kit -- the pale, black-striped Ectonurite skin drawn see-through (the framework's
 * {@link #bodyAlpha}: fainter still while phased), NO LEGS: a wispy tail of shrinking, swaying segments trails from the waist
 * instead, one big glowing purple eye, black claws, and a drifting ghost-hands pose. A pose for every move.
 */
public final class GhostfreakClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] SIGHT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.2f, -0.6f, 0f, 0.1f, 0f, 0f, 0f, 0.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f),
			QuirkKeyframes.f(10, -2.1f, -0.6f, 0f, 0.1f, 0f, 0f, 0f, 0.2f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f), QuirkKeyframes.rest(15) };
	private static final FirstPersonClip FP_SIGHT = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -70f, 35f, 0f, 0.1f, 0.4f, 0.1f),
			FirstPersonClip.f(10, -68f, 35f, 0f, 0.1f, 0.4f, 0.1f), FirstPersonClip.f(15, 0f, 0f, 0f, 0f, 0f, 0f));

	/** v0.17.1: Spirit Sight -- every living thing within 32 blocks outlined, on the wearer's own screen only. */
	@Override
	public boolean glows(net.minecraft.client.player.LocalPlayer viewer, net.minecraft.world.entity.Entity e) {
		return (Omnitrix.state(viewer).kitFlags & GhostfreakKit.F_SIGHT) != 0 && e instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive()
				&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
				&& e.distanceToSqr(viewer) <= GhostfreakKit.SIGHT_RANGE * GhostfreakKit.SIGHT_RANGE;
	}
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/ghostfreak.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/ghostfreak_extras.png");

	private static final float[] TAIL = { 0f, 0f, 0.5f, 0.5f };
	private static final float[] EYE = { 0.5f, 0f, 1f, 0.5f };
	private static final float[] CLAW = { 0f, 0.5f, 0.25f, 0.75f };

	private static final float[][] CLAW_SWIPE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.7f, 0.6f, 0.5f, -0.3f, 0, 0, -0.1f, 0.4f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -0.9f, -0.8f, -0.3f, -0.4f, 0, 0, 0.35f, -0.4f, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(10) };
	private static final float[][] SCREAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.6f, 0, 0.7f, 0.6f, 0, -0.7f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.f(6, 0.9f, 0, 0.9f, 0.9f, 0, -0.9f, 0.35f, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(16, 0.9f, 0, 0.9f, 0.9f, 0, -0.9f, 0.35f, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(20) };
	private static final float[][] POSSESS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.0f, -0.2f, 0.2f, -2.0f, 0.2f, -0.2f, 0.7f, 0, 0, 0, 0, 0, 0, 0, -0.5f),
			QuirkKeyframes.f(9, -1.6f, -0.3f, 0, -1.6f, 0.3f, 0, 0.9f, 0, 0, 0, 0, 0, 0, 0, -0.6f), QuirkKeyframes.rest(14) };
	private static final float[][] SIPHON = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.55f, -0.1f, 0, -0.3f, 0, -0.3f, 0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(12, -1.5f, -0.1f, 0.1f, -0.3f, 0, -0.3f, 0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(16) };
	private static final float[][] PHASE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.2f, 0.9f, 0, -1.2f, -0.9f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			QuirkKeyframes.f(7, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(12) };
	private static final float[][] FLIGHT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.5f, 0, 1.0f, -0.5f, 0, -1.0f, 0.1f, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			QuirkKeyframes.rest(12) };
	private static final float[][] FADE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.0f, 1.0f, -0.2f, -1.0f, -1.0f, 0.2f, 0.25f, 0, 0, 0, 0, 0, 0, 0, 0.5f),
			QuirkKeyframes.f(12, -1.0f, 1.0f, -0.2f, -1.0f, -1.0f, 0.2f, 0.25f, 0, 0, 0, 0, 0, 0, 0, 0.5f), QuirkKeyframes.rest(16) };
	private static final float[][] TENDRILS = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.9f, 0, 0.5f, -2.9f, 0, -0.5f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			QuirkKeyframes.f(8, 0.2f, 0, 0.8f, 0.2f, 0, -0.8f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.5f),
			QuirkKeyframes.f(18, 0.2f, 0, 0.8f, 0.2f, 0, -0.8f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.5f), QuirkKeyframes.rest(24) };
	private static final float[][] AURA = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.4f, 0, 1.7f, -0.4f, 0, -1.7f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.f(16, -0.5f, 0, 1.8f, -0.5f, 0, -1.8f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f), QuirkKeyframes.rest(22) };
	private static final float[][] HAUNT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.0f, 0, 0.3f, -3.0f, 0, -0.3f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.f(9, -1.3f, 0, 1.4f, -1.3f, 0, -1.4f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.2f), QuirkKeyframes.rest(18) };
	private static final float[][] TOUCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.5f, 0.2f, 0, 0, 0, 0, 0.15f, -0.3f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(8) };
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0.05f, 0, 0.2f, 0, 0, 0.05f, -0.2f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };

	private static final FirstPersonClip FP_CLAW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -60, -30, -30, -0.1f, 0.4f, 0.05f),
			FirstPersonClip.f(5, 10, 45, 30, 0.35f, -0.2f, -0.25f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SCREAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 35, -35, 0, -0.25f, -0.25f, 0.1f),
			FirstPersonClip.f(16, 35, -35, 0, -0.25f, -0.25f, 0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 25, 0, 0.18f, 0.15f, -0.35f),
			FirstPersonClip.f(10, -28, 25, 0, 0.18f, 0.15f, -0.35f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SIPHON = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 22, 0, 0.15f, 0.12f, -0.3f),
			FirstPersonClip.f(12, -25, 22, 10, 0.15f, 0.12f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CROSS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 45, 0, 0.3f, 0.15f, 0.05f),
			FirstPersonClip.f(12, -20, 45, 0, 0.3f, 0.15f, 0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -40, 30, -0.25f, 0.2f, 0),
			FirstPersonClip.f(16, -20, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(9, 30, 10, 0, 0.05f, -0.3f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TOUCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** v0.17.1 (user: "dont make him transparent like he is right now"): solid -- only Phase makes him see-through (the cue it is on). */
	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & GhostfreakKit.F_PHASE) != 0 ? 130 : 255;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		boolean phased = (Omnitrix.state(p).kitFlags & GhostfreakKit.F_PHASE) != 0;
		int alpha = phased ? 130 : 255;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		// the wispy tail in place of legs: shrinking segments, each swaying a little more than the last
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(0, 11.5f / 16f, 0);
			float[] w = { 8.4f, 7.2f, 5.8f, 4.2f, 2.6f, 1.2f };
			float[] d = { 4.4f, 3.8f, 3.2f, 2.6f, 1.8f, 0.9f };
			float[] h = { 4.4f, 4.2f, 4.0f, 3.6f, 3.2f, 2.6f };
			for (int i = 0; i < w.length; i++) {
				float sway = Mth.sin(age * 0.16f + i * 0.8f) * (0.08f + i * 0.05f);
				pose.mulPose(Axis.ZP.rotation(sway));
				pose.mulPose(Axis.XP.rotation(0.05f + i * 0.04f + Mth.sin(age * 0.11f + i) * 0.04f));
				int a = Math.max(40, alpha - i * 26);
				AlienParts.box(pose, vc, -w[i] / 2, 0, -d[i] / 2, w[i] / 2, h[i], d[i] / 2, TAIL[0], TAIL[1] + i * 0.065f, TAIL[2], TAIL[1] + i * 0.065f + 0.08f,
						(a << 24) | 0xFFFFFF, light);
				pose.translate(0, (h[i] - 0.4f) / 16f, 0);
			}
			pose.popPose();
		});
		// v0.17.1 (user: "remove ghost freaks claws"): none
		// the one great purple eye (last: fetching the eyes buffer ends the translucent one)
		VertexConsumer eye = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float blink = (age % 90) < 3 ? 0.3f : 1f;
		AlienParts.on(pose, model.head, () -> AlienParts.box(pose, eye, -2.0f, -6.2f + (1 - blink) * 1.8f, -4.3f, 2.0f, -2.6f - (1 - blink) * 1.8f, -4.05f, EYE[0],
				EYE[1], EYE[2], EYE[3], 0xFFFFFFFF, LightTexture.FULL_BRIGHT));
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// v0.17.1: no claws (the skin's own arm)
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		// no legs: he floats on the tail (setModelProperties makes every part visible again before each render)
		m.rightLeg.visible = false;
		m.leftLeg.visible = false;
		if (m instanceof PlayerModel<?> pm) {
			pm.rightPants.visible = false;
			pm.leftPants.visible = false;
		}
		float[][] clip = switch (animId) {
			case GhostfreakKit.ANIM_CLAW -> CLAW_SWIPE;
			case GhostfreakKit.ANIM_SCREAM -> SCREAM;
			case GhostfreakKit.ANIM_POSSESS -> POSSESS;
			case GhostfreakKit.ANIM_SIPHON -> SIPHON;
			case GhostfreakKit.ANIM_PHASE -> PHASE;
			case GhostfreakKit.ANIM_FLIGHT -> FLIGHT;
			case GhostfreakKit.ANIM_FADE -> FADE;
			case GhostfreakKit.ANIM_TENDRILS -> TENDRILS;
			case GhostfreakKit.ANIM_AURA -> AURA;
			case GhostfreakKit.ANIM_HAUNT -> HAUNT;
			case GhostfreakKit.ANIM_TOUCH -> TOUCH;
			case GhostfreakKit.ANIM_BOLT -> BOLT;
			case GhostfreakKit.ANIM_SIGHT -> SIGHT;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// ghost hands: drifting forward, slowly
			m.rightArm.xRot = m.rightArm.xRot * 0.3f - 0.55f + Mth.sin(age * 0.08f) * 0.08f;
			m.leftArm.xRot = m.leftArm.xRot * 0.3f - 0.55f + Mth.sin(age * 0.08f + 1.3f) * 0.08f;
			m.rightArm.zRot += 0.1f;
			m.leftArm.zRot -= 0.1f;
		}
		return true;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case GhostfreakKit.ANIM_CLAW -> FP_CLAW;
			case GhostfreakKit.ANIM_SCREAM -> FP_SCREAM;
			case GhostfreakKit.ANIM_POSSESS -> FP_REACH;
			case GhostfreakKit.ANIM_SIPHON, GhostfreakKit.ANIM_BOLT -> FP_SIPHON;
			case GhostfreakKit.ANIM_PHASE, GhostfreakKit.ANIM_FADE -> FP_CROSS;
			case GhostfreakKit.ANIM_FLIGHT, GhostfreakKit.ANIM_AURA -> FP_WIDE;
			case GhostfreakKit.ANIM_TENDRILS, GhostfreakKit.ANIM_HAUNT -> FP_DOWN;
			case GhostfreakKit.ANIM_TOUCH -> FP_TOUCH;
			case GhostfreakKit.ANIM_SIGHT -> FP_SIGHT;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		var level = p.level();
		if (p.isInvisible()) {
			return;
		}
		if (r.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.WHITE_ASH, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + 0.2 + r.nextDouble() * 0.6,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, -0.01, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & GhostfreakKit.F_PHASE) != 0 && r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.SOUL, p.getX() + (r.nextDouble() - 0.5) * 0.7, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
		}
		if ((flags & GhostfreakKit.F_AURA) != 0) {
			double a = r.nextDouble() * Math.PI * 2;
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + Math.cos(a) * 2.5, p.getY() + 0.1, p.getZ() + Math.sin(a) * 2.5, 0, 0.03, 0);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), just left of the middle of his chest
		return new float[] { 1.0f, 3.0f, -2.06f, 2.4f };
	}
}
