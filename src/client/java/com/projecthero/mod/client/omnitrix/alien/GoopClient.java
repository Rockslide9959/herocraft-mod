package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.goop.GoopFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.goop.GoopKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.17.2 (Recalibrated Omnitrix, basic version): Goop's client kit -- the player body in his generated acid-green slime skin, drawn
 * see-through ({@link #bodyAlpha}), with his anti-gravity projector hovering over his head (a silver saucer, its green light and a
 * faint anti-gravity cone down onto him) unless it has been knocked off. Slime Whip stretches his arm out to 5 blocks. A pose and a
 * first-person clip for every move; his acid is {@link GoopFx}.
 */
public final class GoopClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/goop.png");
	private static final float[] SLIME = { 32 / 64f, 0f, 48 / 64f, 16 / 64f };
	private static final ItemParticleOption DRIP = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL));

	public static void register() {
		AlienClients.register(Aliens.GOOP.id(), new GoopClient());
		GoopFx.register();
	}

	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, 0, 0, 0, 0, 0, 0, hx);
	}

	private static final float[][] SPIT = { QuirkKeyframes.rest(0), f(2, 0.4f, 0, 0.2f, 0.4f, 0, -0.2f, 0.3f, 0.35f), QuirkKeyframes.rest(8) };
	private static final float[][] GLOB = { QuirkKeyframes.rest(0), f(3, -2.8f, 0, 0.2f, 0.2f, 0, 0, -0.15f, 0), f(6, -0.9f, 0, 0, 0.2f, 0, 0, 0.25f, 0),
			QuirkKeyframes.rest(12) };
	private static final float[][] WHIP = { QuirkKeyframes.rest(0), f(2, -2.4f, 0, 0.1f, 0, 0, 0, 0, 0), f(5, -1.55f, 0.05f, 0, 0, 0, 0, 0.15f, 0),
			f(10, -1.5f, 0.05f, 0, 0, 0, 0, 0.1f, 0), QuirkKeyframes.rest(14) };
	private static final float[][] ENGULF = { QuirkKeyframes.rest(0), f(3, -1.6f, -0.9f, 0, -1.6f, 0.9f, 0, 0.2f, 0), f(8, -1.5f, 0.35f, 0, -1.5f, -0.35f, 0, 0.1f, 0),
			f(56, -1.3f, 0.35f, 0, -1.3f, -0.35f, 0, 0.1f, 0), QuirkKeyframes.rest(62) };
	private static final float[][] BOUNCE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, -2.6f, 0, 0.5f, -2.6f, 0, -0.5f, -0.1f, 0, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, -0.2f),
			QuirkKeyframes.f(10, -2.5f, 0, 0.5f, -2.5f, 0, -0.5f, -0.1f, 0, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] SQUEEZE = { QuirkKeyframes.rest(0), f(3, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0.6f, 0.3f), f(96, -0.3f, 0, 0.1f, -0.3f, 0, -0.1f, 0.6f, 0.3f),
			QuirkKeyframes.rest(100) };
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), f(4, -3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, -0.2f), f(7, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.5f, 0.2f),
			QuirkKeyframes.rest(14) };
	private static final float[][] SPRAY = { QuirkKeyframes.rest(0), f(3, -1.55f, -0.15f, 0, -1.55f, 0.15f, 0, 0.15f, 0), f(10, -1.5f, -0.15f, 0, -1.5f, 0.15f, 0, 0.15f, 0),
			QuirkKeyframes.rest(14) };
	private static final float[][] REFORM = { QuirkKeyframes.rest(0), f(4, -0.3f, 0, 1.2f, -0.3f, 0, -1.2f, -0.1f, -0.3f), f(10, -0.6f, 0.4f, 0.2f, -0.6f, -0.4f, -0.2f, 0.1f, 0),
			QuirkKeyframes.rest(16) };
	private static final float[][] GLIDE = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -0.1f, 0, 1.5f, -0.1f, 0, -1.5f, 0, 0, 0, 0, 0.5f, 0, 0, -0.5f, 0),
			QuirkKeyframes.f(116, -0.1f, 0, 1.5f, -0.1f, 0, -1.5f, 0, 0, 0, 0, 0.5f, 0, 0, -0.5f, 0), QuirkKeyframes.rest(120) };
	private static final float[][] FLOOD = { QuirkKeyframes.rest(0), f(4, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, 0.5f, 0.2f), f(20, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, 0.5f, 0.2f),
			QuirkKeyframes.rest(26) };
	private static final float[][] CANNON = { QuirkKeyframes.rest(0), f(4, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, -0.2f, 0), f(7, -1.8f, -0.1f, 0, -1.8f, 0.1f, 0, -0.35f, -0.1f),
			QuirkKeyframes.rest(14) };
	private static final float[][] KNOCKED = { QuirkKeyframes.rest(0), f(3, -0.2f, 0, 1.0f, -0.2f, 0, -1.0f, -0.3f, -0.4f), QuirkKeyframes.rest(20) };

	private static final FirstPersonClip FP_SPIT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, 0, 0, 0, -0.3f, 0.1f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_THROW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.55f, 0.15f),
			FirstPersonClip.f(6, -10, 10, 0, 0.05f, 0.05f, -0.3f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -12, 8, 0, 0.05f, 0.12f, -0.9f),
			FirstPersonClip.f(8, -12, 8, 0, 0.05f, 0.12f, -0.8f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -18, 15, 0, 0.12f, 0.12f, -0.3f),
			FirstPersonClip.f(10, -16, 14, 0, 0.12f, 0.12f, -0.28f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 10, 0, 0.05f, 0.6f, 0.1f),
			FirstPersonClip.f(7, 30, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -30, 25, -0.2f, 0.2f, 0.05f),
			FirstPersonClip.f(10, -20, -30, 25, -0.2f, 0.2f, 0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public int bodyAlpha(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & GoopKit.F_OFF) != 0 ? 140 : 175;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		// Slime Whip: the right arm stretches out to 5 blocks and snaps back
		if (s.animId == GoopKit.ANIM_WHIP && t >= 0f && t < 12f) {
			float out = t < 5f ? t / 5f : Mth.clamp((12f - t) / 7f, 0f, 1f);
			float len = 4f + out * 72f;
			AlienParts.on(pose, model.rightArm, () -> {
				VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GoopFx.EXTRAS));
				AlienParts.box(pose, vc, -2.6f, 8f, -1.6f, 0.6f, 8f + len, 1.6f, SLIME[0], SLIME[1], SLIME[2], SLIME[3], 0xC8FFFFFF, light);
				AlienParts.box(pose, vc, -3.2f, 8f + len, -2.2f, 1.2f, 12f + len, 2.2f, SLIME[0], SLIME[1], SLIME[2], SLIME[3], 0xC8FFFFFF, light);
			});
		}
		// the projector hovering over his head, bobbing, its anti-gravity cone shining down onto him
		if ((s.kitFlags & GoopKit.F_OFF) == 0) {
			float bob = Mth.sin(age * 0.12f) * 0.6f;
			AlienParts.on(pose, model.body, () -> {
				pose.pushPose();
				pose.translate(0, (-12.5f + bob) / 16f, 0);
				pose.mulPose(Axis.YP.rotation(age * 0.05f));
				GoopFx.drawSaucer(pose, buffers, light, 0.8f + 0.2f * Mth.sin(age * 0.3f));
				pose.popPose();
				pose.pushPose();
				// model space: y down -- the cone's wide base sits on his head, its tip up at the saucer's light
				pose.translate(0, -8.2f / 16f, 0);
				PyroDraw.cone(pose, PyroDraw.additive(buffers), 3.2f / 16f, (-2.6f + bob) / 16f, PyroDraw.dim(0x2E7A14, 0.3f), PyroDraw.dim(0x5AE028, 0.55f));
				pose.popPose();
			});
		}
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		float[][] clip = switch (animId) {
			case GoopKit.ANIM_SPIT -> SPIT;
			case GoopKit.ANIM_GLOB -> GLOB;
			case GoopKit.ANIM_WHIP -> WHIP;
			case GoopKit.ANIM_ENGULF -> ENGULF;
			case GoopKit.ANIM_BOUNCE -> BOUNCE;
			case GoopKit.ANIM_SQUEEZE -> SQUEEZE;
			case GoopKit.ANIM_SLAM -> SLAM;
			case GoopKit.ANIM_SPRAY -> SPRAY;
			case GoopKit.ANIM_REFORM -> REFORM;
			case GoopKit.ANIM_GLIDE -> GLIDE;
			case GoopKit.ANIM_FLOOD -> FLOOD;
			case GoopKit.ANIM_CANNON -> CANNON;
			case GoopKit.ANIM_KNOCKED -> KNOCKED;
			default -> null;
		};
		if ((animId == GoopKit.ANIM_SQUEEZE && (s.kitFlags & GoopKit.F_SQUEEZE) == 0 && ticks > 4)
				|| (animId == GoopKit.ANIM_GLIDE && (s.kitFlags & GoopKit.F_GLIDE) == 0 && ticks > 4)
				|| (animId == GoopKit.ANIM_ENGULF && (s.kitFlags & GoopKit.F_ENGULF) == 0 && ticks > 10)) {
			clip = null;
		}
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		// his whole body wobbles like jelly; slumped flat while the projector is off
		float w = Mth.sin(age * 0.25f) * 0.05f;
		m.rightArm.zRot += w;
		m.leftArm.zRot -= w;
		if ((s.kitFlags & GoopKit.F_OFF) != 0 && !played) {
			m.rightArm.zRot += 1.0f;
			m.leftArm.zRot -= 1.0f;
			m.rightLeg.zRot += 0.6f;
			m.leftLeg.zRot -= 0.6f;
			m.head.xRot += 0.4f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case GoopKit.ANIM_SPIT -> FP_SPIT;
			case GoopKit.ANIM_GLOB -> FP_THROW;
			case GoopKit.ANIM_WHIP -> FP_REACH;
			case GoopKit.ANIM_ENGULF, GoopKit.ANIM_SPRAY, GoopKit.ANIM_CANNON -> FP_BOTH;
			case GoopKit.ANIM_SLAM, GoopKit.ANIM_BOUNCE -> FP_SLAM;
			case GoopKit.ANIM_REFORM, GoopKit.ANIM_GLIDE, GoopKit.ANIM_FLOOD, GoopKit.ANIM_SQUEEZE, GoopKit.ANIM_KNOCKED -> FP_WIDE;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		// a slow drip now and then (an accent)
		if (p.getRandom().nextInt(14) == 0) {
			var r = p.getRandom();
			p.level().addParticle(DRIP, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + p.getBbHeight() * r.nextDouble(), p.getZ() + (r.nextDouble() - 0.5) * 0.5,
					0, -0.05, 0);
		}
	}
}
