package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.jetray.JetrayBody;
import com.projecthero.mod.client.omnitrix.alien.jetray.JetraySonicTrail;
import com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.jetray.JetrayKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix): Jetray's client kit -- the player body in the user's Jetray skin (v0.17.2 revamp: the old
 * wing, tail, crest and eye cubes are gone, user: "only use the skins i provided"), and since then, at the user's ask, his gold
 * wing membranes in flight, his whip tail and a proper flight attitude ({@link JetrayBody}). The neuroshock beams, the tail bolt, the pulse ring, the dive streak and the
 * overload's charge and beam are rendered in the world pass ({@link RecalFx}).
 */
public final class JetrayClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/jetray.png");

	public static final int CORE = 0xFFF4FFC8;
	public static final int GLOW = 0xFFA8F030;

	private static boolean worldPass;

	// ---------------------------------------------------------------- third-person poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, head x)
	private static final float[][] EYES = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.35f, 0, 0.35f, 0.35f, 0, -0.35f, 0.15f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(6, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.1f, 0, 0, 0, 0, 0, 0, 0, -0.05f), QuirkKeyframes.rest(10) };
	private static final float[][] TAIL_SHOT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.35f, 0.5f, 0.3f, 0, 0.1f, -0.2f, 0, -0.1f, 0.2f),
			QuirkKeyframes.f(6, 0.4f, 0, 0.6f, 0.4f, 0, -0.6f, 0.3f, -0.2f, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(12) };
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.7f, 0, 0.25f, 0.7f, 0, -0.25f, 0.8f, 0, 0.6f, 0, 0.05f, 0.6f, 0, -0.05f, -0.6f),
			QuirkKeyframes.f(12, 0.7f, 0, 0.25f, 0.7f, 0, -0.25f, 0.8f, 0, 0.6f, 0, 0.05f, 0.6f, 0, -0.05f, -0.6f), QuirkKeyframes.rest(16) };
	private static final float[][] SLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0.6f, 1.5f, -0.3f, -0.6f, -1.5f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.4f, -0.6f, 0.3f, -1.4f, 0.6f, -0.3f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(11) };
	// Round 4, X Wing Beat: the wings thrown up over his head (the membranes stretch with the arms), one hard stroke down, and they settle
	private static final float[][] BEAT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0f, 0, 2.6f, 0f, 0, -2.6f, -0.2f, 0, 0.15f, 0, 0.1f, 0.15f, 0, -0.1f, -0.25f),
			QuirkKeyframes.f(6, 0.3f, 0, 0.35f, 0.3f, 0, -0.35f, 0.3f, 0, -0.15f, 0, 0, -0.15f, 0, 0, 0.15f),
			QuirkKeyframes.f(12, 0f, 0, 1.25f, 0f, 0, -1.25f, 0f, 0, 0, 0, 0, 0, 0, 0, 0f), QuirkKeyframes.rest(18) };
	// Round 4: coming out of Supersonic -- the wings thrown wide as air brakes
	private static final float[][] SONIC_END = { QuirkKeyframes.f(0, 0.2f, 0, 0.12f, 0.2f, 0, -0.12f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0f),
			QuirkKeyframes.f(5, -0.5f, 0, 1.9f, -0.5f, 0, -1.9f, -0.25f, 0, 0.35f, 0, 0.1f, 0.35f, 0, -0.1f, -0.2f), QuirkKeyframes.rest(16) };
	private static final float[][] SPREAD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.4f, -0.5f, 0.3f, -1.4f, 0.5f, -0.3f, 0.1f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(5, -1.5f, 0.4f, 0.3f, -1.5f, -0.4f, -0.3f, 0.1f, 0, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(10) };
	private static final float[][] PULSE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0, 0, 0.4f, 0, 0, -0.4f, -0.1f, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0),
			QuirkKeyframes.f(4, 0, 0, 1.6f, 0, 0, -1.6f, -0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.2f), QuirkKeyframes.rest(12) };
	private static final float[][] STREAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.5f, 0, 0.15f, 0.5f, 0, -0.15f, 0.2f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.rest(10) };
	// v0.17.2 revamp: the Barrel Roll (arms tucked, body twisting round), the Tail Grab (arms reaching down) and the Wing Ram
	private static final float[][] ROLL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.2f, 0, 0.2f, 0.2f, 0, -0.2f, 0.2f, 2.1f, 0.3f, 0, 0, 0.3f, 0, 0, 0),
			QuirkKeyframes.f(8, 0.2f, 0, 0.2f, 0.2f, 0, -0.2f, 0.2f, 4.2f, 0.3f, 0, 0, 0.3f, 0, 0, 0),
			QuirkKeyframes.f(14, 0.2f, 0, 0.2f, 0.2f, 0, -0.2f, 0.2f, 6.283f, 0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(14.1f) };
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.9f, 0, 0.4f, 0.9f, 0, -0.4f, 0.5f, 0, -0.4f, 0, 0, -0.4f, 0, 0, 0.4f),
			QuirkKeyframes.f(9, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(14) };
	private static final float[][] RAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.6f, 0, 0.9f, 0.6f, 0, -0.9f, 0.7f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.6f),
			QuirkKeyframes.rest(9) };
	private static final float[][] THUNDER = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.8f, 0, 0.3f, -2.8f, 0, -0.3f, -0.2f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.3f),
			QuirkKeyframes.f(10, 0.7f, 0, 0.3f, 0.7f, 0, -0.3f, 0.9f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.7f),
			QuirkKeyframes.f(40, 0.7f, 0, 0.3f, 0.7f, 0, -0.3f, 0.9f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.7f), QuirkKeyframes.rest(44) };
	private static final float[][] OVERLOAD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, 0.6f, 0, 1.2f, 0.6f, 0, -1.2f, -0.3f, 0, 0.1f, 0, 0.15f, 0.1f, 0, -0.15f, -0.25f),
			QuirkKeyframes.f(19, 0.7f, 0, 1.3f, 0.7f, 0, -1.3f, -0.35f, 0, 0.1f, 0, 0.15f, 0.1f, 0, -0.15f, -0.3f),
			QuirkKeyframes.f(22, 0.2f, 0, 0.8f, 0.2f, 0, -0.8f, 0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0f),
			QuirkKeyframes.f(28, 0.2f, 0, 0.8f, 0.2f, 0, -0.8f, 0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, 0f), QuirkKeyframes.rest(32) };

	// ---------------------------------------------------------------- first-person clips (tick, pitch, yaw, roll, x, y, z)
	private static final FirstPersonClip FP_EYES = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -12, 30, 0, 0.2f, 0.08f, 0.05f),
			FirstPersonClip.f(7, -10, 28, 0, 0.18f, 0.06f, 0.05f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TAIL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 25, -20, 20, -0.15f, -0.2f, 0.1f),
			FirstPersonClip.f(6, -20, 15, 0, 0.1f, 0.05f, -0.1f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 40, 15, 0, 0.1f, -0.4f, 0.25f),
			FirstPersonClip.f(12, 40, 15, 0, 0.1f, -0.4f, 0.25f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -30, -50, 30, -0.3f, 0.15f, 0),
			FirstPersonClip.f(5, -40, 45, -10, 0.35f, 0.1f, -0.15f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -50, -25, 25, -0.2f, 0.35f, 0.1f),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	/** Round 4, Wing Beat: both arms up past the top of the view, then the stroke down through it. */
	private static final FirstPersonClip FP_BEAT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -55, -20, 25, -0.2f, 0.45f, 0.1f),
			FirstPersonClip.f(6, 30, 10, -10, 0.05f, -0.45f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	/** Round 4, the 3 s charge: the arms thrown out to the edges of the view, drawn slowly in, then down and away as he goes. */
	private static final FirstPersonClip FP_SONIC_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(10, -15, -40, 25, -0.35f, 0.12f, 0.12f),
			FirstPersonClip.f(44, -20, -15, 10, -0.1f, 0f, 0.05f), FirstPersonClip.f(60, 25, 0, 0, 0, -0.25f, 0.1f), FirstPersonClip.f(1000000f, 25, 0, 0, 0, -0.25f, 0.1f)).both();
	private static final FirstPersonClip FP_SONIC = FirstPersonClip.of(FirstPersonClip.f(0, 25, 0, 0, 0, -0.25f, 0.1f), FirstPersonClip.f(4, 30, 0, 0, 0, -0.6f, 0.15f),
			FirstPersonClip.f(1000000f, 30, 0, 0, 0, -0.6f, 0.15f)).both();
	private static final FirstPersonClip FP_SONIC_END = FirstPersonClip.of(FirstPersonClip.f(0, 30, 0, 0, 0, -0.6f, 0.15f), FirstPersonClip.f(5, -20, -40, 25, -0.35f, 0.1f, 0.1f),
			FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPREAD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, -15, 0, -0.1f, 0.1f, -0.2f),
			FirstPersonClip.f(5, -25, 30, 0, 0.2f, 0.1f, -0.2f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OUT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -45, 30, -0.35f, 0.1f, 0.1f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TEMPLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 45, 20, 0.3f, 0.45f, 0.1f),
			FirstPersonClip.f(14, -70, 45, 20, 0.3f, 0.45f, 0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -15, -35, 20, -0.25f, 0.05f, 0.1f),
			FirstPersonClip.f(19, -18, -38, 22, -0.27f, 0.06f, 0.1f), FirstPersonClip.f(22, -30, 20, 0, 0.15f, 0.1f, -0.2f),
			FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();

	public JetrayClient() {
		if (!worldPass) {
			worldPass = true;
			WorldRenderEvents.AFTER_ENTITIES.register(JetrayClient::renderWorld);
			// v0.17.2: the wings / tail / flight state is per player -- dropped when he is gone, turned back or off-world
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(JetrayBody::prune);
			JetraySonicTrail.init(); // round 4
		}
	}

	public static void register() {
		AlienClients.register(Aliens.JETRAY.id(), new JetrayClient());
	}

	/** v0.17.2 (user: "raise our omnitrix badge up slightly please"): a pixel higher than the default, where the skin's own symbol was. */
	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.05f, 3.2f };
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// v0.17.2 (user: "delete all the alien models and only use the skins i provided please no extra things untill i say"): the old
	// stinger, head crest and glowing eye cubes stay gone. v0.17.2 (user: "give him his tail aswell ... when he flies he needs his
	// wings so create the wing model and have it be in between his arms and his tail"): the wings and the tail are back as
	// JetrayBody's membranes and spring chain.
	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
		JetrayBody.render(pose, buffers, light, p, model, partial, age);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case JetrayKit.ANIM_EYES -> EYES;
			case JetrayKit.ANIM_TAIL -> TAIL_SHOT;
			case JetrayKit.ANIM_DIVE -> DIVE;
			case JetrayKit.ANIM_SLASH -> SLASH;
			case JetrayKit.ANIM_BEAT -> BEAT;
			case JetrayKit.ANIM_SONIC_END -> SONIC_END;
			case JetrayKit.ANIM_SPREAD -> SPREAD;
			case JetrayKit.ANIM_PULSE -> PULSE;
			case JetrayKit.ANIM_STREAM -> STREAM;
			case JetrayKit.ANIM_ROLL -> ROLL;
			case JetrayKit.ANIM_GRAB -> GRAB;
			case JetrayKit.ANIM_RAM -> RAM;
			case JetrayKit.ANIM_THUNDER -> THUNDER;
			case JetrayKit.ANIM_OVERLOAD -> OVERLOAD;
			default -> null;
		};
		if (clip != null && QuirkKeyframes.play(m, clip, ticks, 1f)) {
			// v0.17.2: in flight the whole body is already pitched along his travel -- the clip's own lean would double it
			if ((Omnitrix.state(p).kitFlags & JetrayKit.F_FLYING) != 0) {
				m.body.xRot = 0f;
				JetrayBody.headLevel(p, m, age);
			}
			return true;
		}
		// v0.17.2 (user: "give jetray better flight animations too please"): arms out stretching the wings, legs together trailing;
		// flaps hovering, a ripple cruising, swept back sprinting
		return JetrayBody.flightPose(p, m, age);
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case JetrayKit.ANIM_EYES -> FP_EYES;
			case JetrayKit.ANIM_TAIL -> FP_TAIL;
			case JetrayKit.ANIM_DIVE, JetrayKit.ANIM_STREAM, JetrayKit.ANIM_RAM -> FP_DIVE;
			case JetrayKit.ANIM_BEAT -> FP_BEAT;
			case JetrayKit.ANIM_SONIC_CHARGE -> FP_SONIC_CHARGE;
			case JetrayKit.ANIM_SONIC -> FP_SONIC;
			case JetrayKit.ANIM_SONIC_END -> FP_SONIC_END;
			case JetrayKit.ANIM_SLASH, JetrayKit.ANIM_ROLL -> FP_SWEEP;
			case JetrayKit.ANIM_THUNDER -> FP_UP;
			case JetrayKit.ANIM_SPREAD -> FP_SPREAD;
			case JetrayKit.ANIM_PULSE -> FP_OUT;
			case JetrayKit.ANIM_GRAB -> FP_TEMPLE;
			case JetrayKit.ANIM_OVERLOAD -> FP_CHARGE;
			default -> null;
		};
	}

	/** Round 4: the charge building in his hands. */
	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		float k = JetrayBody.charge(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (k > 0f) {
			pose.pushPose(); // (the framework has already put the pose on the arm's bone)
			PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), new Vec3((right ? -1f : 1f) / 16f, 10.5 / 16f, 0), 0.06f + 0.1f * k, PyroDraw.dim(GLOW, 0.25f + 0.35f * k));
			pose.popPose();
		}
	}

	/** Round 4: the charge's progress; past the barrier, the speed he is really doing (the unloaded world can hold him under 200). */
	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		var label = net.minecraft.network.chat.Component.translatable("hud.projecthero.omnitrix.jetray.supersonic");
		if (JetrayKit.supersonicCharging(p)) {
			float k = JetrayBody.charge(p, partial);
			com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6, label, k, 0xFFA8F030,
					String.format(java.util.Locale.ROOT, "%.0f%%", k * 100f), 0xFFFFFFFF);
		} else if (JetrayKit.supersonic(p)) {
			float bps = (float) p.position().subtract(p.xo, p.yo, p.zo).length() * 20f;
			com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, Minecraft.getInstance(), x, y - 6, label, Math.min(1f, bps / 200f), 0xFFA8F030,
					String.format(java.util.Locale.ROOT, "%.0f b/s", bps), 0xFFFFFFFF);
		}
	}

	@Override
	public int hudHeight() {
		var p = Minecraft.getInstance().player;
		return p != null && (JetrayKit.supersonicCharging(p) || JetrayKit.supersonic(p)) ? 10 : 0;
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		JetrayBody.tick(p);
		OmnitrixState s = Omnitrix.state(p);
		var r = p.getRandom();
		// accents only: a few sparks off the stinger, bubbles when he streaks through water
		if ((s.kitFlags & JetrayKit.F_OVERLOAD) != 0 && r.nextInt(2) == 0) {
			p.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getEyeY(), p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.05,
					0);
		}
		if (p.isInWater() && (s.kitFlags & JetrayKit.F_STREAM) != 0 && r.nextInt(3) == 0) {
			p.level().addParticle(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.6, p.getZ(), 0, 0.02, 0);
		}
	}

	// ---------------------------------------------------------------- the world pass: beams, bolts, rings, streaks

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!JetrayKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			boolean fp = pl == mc.player && mc.options.getCameraType().isFirstPerson();
			float t = pl.level().getGameTime() + partial - st.animStart;
			float sc = pl.getScale();
			Vec3 f = pl.getViewVector(partial);
			Vec3 right = f.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 up = right.cross(f).normalize();
			Vec3 eye = pl.getEyePosition(partial);
			Vec3 muzzle = fp ? eye.add(f.scale(0.5)).subtract(up.scale(0.18)) : eye.add(f.scale(0.3 * sc));
			switch (st.animId) {
				case JetrayKit.ANIM_EYES -> {
					float k = RecalFx.fade(t, 6f);
					Vec3 end = RecalFx.traceEnd(pl, eye, f, JetrayKit.EYES_RANGE);
					for (int side = -1; side <= 1; side += 2) {
						Vec3 a = muzzle.add(right.scale(side * 0.12 * sc));
						RecalFx.beam(pose, buffers, a.subtract(cam), end.add(right.scale(side * 0.05)).subtract(cam), 0.14f, CORE, GLOW, k);
					}
				}
				case JetrayKit.ANIM_TAIL -> {
					// the tail arches up over his shoulder and fires
					float k = RecalFx.fade(t - 2f, 7f);
					// v0.17.2: from his real tail's tip
					Vec3 tail = pl instanceof AbstractClientPlayer acp ? JetrayBody.tailTip(acp, partial) : null;
					Vec3 tip = fp ? eye.add(up.scale(0.35)).add(right.scale(0.45)).add(f.scale(0.4))
							: tail != null ? tail : pl.getPosition(partial).add(0, 2.05 * sc, 0).subtract(f.scale(0.25 * sc));
					Vec3 end = RecalFx.traceEnd(pl, eye, f, 28.0);
					RecalFx.bolt(pose, buffers, tip.subtract(cam), end.subtract(cam), 0.3f, CORE, GLOW, k, st.animStart * 31 + (long) (t / 2));
				}
				case JetrayKit.ANIM_SPREAD -> {
					float k = RecalFx.fade(t, 7f);
					for (int i = -2; i <= 2; i++) {
						Vec3 d = turned(f, i * 15.0);
						Vec3 end = RecalFx.traceEnd(pl, eye, d, 16.0);
						RecalFx.beam(pose, buffers, muzzle.subtract(cam), end.subtract(cam), 0.11f, CORE, GLOW, k);
					}
				}
				case JetrayKit.ANIM_PULSE -> {
					float k = RecalFx.fade(t, 10f);
					float r = 0.5f + Math.min(t, 10f) * 0.5f;
					RecalFx.ring(pose, buffers, pl.getPosition(partial).add(0, 0.6 * sc, 0).subtract(cam), r, 0.8f, GLOW, k);
				}
				case JetrayKit.ANIM_OVERLOAD -> {
					if (t < JetrayKit.OVERLOAD_CHARGE) {
						// the eyes and the stinger gather light
						float g = t / JetrayKit.OVERLOAD_CHARGE;
						PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), muzzle.subtract(cam), 0.15f + g * 0.45f, PyroDraw.dim(GLOW, 0.4f + g * 0.6f));
						PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), muzzle.subtract(cam), 0.08f + g * 0.2f, PyroDraw.dim(CORE, 0.6f + g * 0.4f));
					} else {
						float k = RecalFx.fade(t - JetrayKit.OVERLOAD_CHARGE, 10f);
						Vec3 end = RecalFx.traceEnd(pl, eye, f, JetrayKit.OVERLOAD_RANGE);
						RecalFx.beam(pose, buffers, muzzle.subtract(cam), end.subtract(cam), 1.2f, CORE, GLOW, k);
						RecalFx.bolt(pose, buffers, muzzle.subtract(cam), end.subtract(cam), 0.25f, CORE, GLOW, k,
								st.animStart * 17 + (long) (t / 2));
					}
				}
				default -> {
				}
			}
			// the dive / boost / thunder-dive streak: a glowing wake behind him
			boolean diving = (st.kitFlags & (JetrayKit.F_DIVING | JetrayKit.F_THUNDER)) != 0
					;
			if (diving && !fp) {
				Vec3 c = pl.getPosition(partial).add(0, 0.9 * sc, 0);
				Vec3 dir = pl.getDeltaMovement().lengthSqr() > 0.05 ? pl.getDeltaMovement().normalize() : f;
				RecalFx.cone(pose, buffers, c.add(dir.scale(0.5)).subtract(cam), dir.scale(-1), 0.75f * sc, 2.8f * sc, CORE, GLOW, 0.55f);
			}
			if ((st.kitFlags & JetrayKit.F_SONIC_CHARGE) != 0) {
				sonicCharge(pl, partial, fp, f, right, up, muzzle, sc, st.animStart, cam, pose, buffers);
			}
			if ((st.kitFlags & JetrayKit.F_SONIC) != 0) {
				sonicWake(pl, partial, fp, f, right, up, sc, cam, pose, buffers);
			}
			// v0.17.2 revamp, Tail Grab: a crackling neuroshock tether from his tail's tip (else the small of his back) to what he carries
			if ((st.kitFlags & JetrayKit.F_CARRY) != 0) {
				long id = st.readyAt(JetrayKit.ID + "." + JetrayKit.CARRY_TARGET);
				var held = id > 0 ? mc.level.getEntity((int) (id - 1)) : null;
				if (held != null) {
					Vec3 tail = pl instanceof AbstractClientPlayer acp && !fp ? JetrayBody.tailTip(acp, partial) : null;
					Vec3 from = tail != null ? tail : pl.getPosition(partial).add(0, 0.75 * sc, 0).subtract(f.scale(0.2));
					Vec3 to = held.getPosition(partial).add(0, held.getBbHeight() * 0.8, 0);
					RecalFx.bolt(pose, buffers, from.subtract(cam), to.subtract(cam), 0.12f, CORE, GLOW, 0.8f, pl.tickCount / 2);
				}
			}
		}
	}

	/**
	 * Round 4, Supersonic's 3 s charge: rings of green light closing in along his aim, faster as it builds, the eyes and the stinger
	 * gathering light and neuroshock crackling over him. From his own eyes the rings stay well ahead of the lens and thin.
	 */
	private static void sonicCharge(Player pl, float partial, boolean fp, Vec3 f, Vec3 right, Vec3 up, Vec3 muzzle, float sc, long seed, Vec3 cam, PoseStack pose,
			MultiBufferSource buffers) {
		float k = JetrayBody.charge(pl, partial);
		float time = pl.tickCount + partial;
		Vec3 c = pl.getPosition(partial).add(0, 0.9 * sc, 0);
		VertexConsumer add = PyroDraw.additive(buffers);
		for (int i = 0; i < 3; i++) {
			float u = (time * (0.025f + 0.06f * k) + i / 3f) % 1f;
			float r = (fp ? 3.0f : 2.6f) * (1f - u) + (fp ? 1.9f : 0.7f);
			Vec3 at = c.add(f.scale((fp ? 3.2 : 1.4) + (fp ? 3.0 : 2.4) * (1f - u))).subtract(cam);
			pose.pushPose();
			pose.translate(at.x, at.y, at.z);
			PyroDraw.alignY(pose, f);
			PyroDraw.band(pose, add, r, fp ? 0.025f : 0.06f, PyroDraw.dim(GLOW, u * (0.35f + 0.65f * k) * (fp ? 0.6f : 1f)), 0xFF000000);
			pose.popPose();
		}
		if (!fp) {
			VertexConsumer glow = PyroDraw.glowBuffer(buffers);
			PyroDraw.glow(pose, glow, c.subtract(cam), 0.5f + 1.3f * k, PyroDraw.dim(GLOW, 0.15f + 0.4f * k));
			PyroDraw.glow(pose, glow, muzzle.subtract(cam), 0.12f + 0.3f * k, PyroDraw.dim(CORE, 0.6f + 0.4f * k));
			Vec3 tail = pl instanceof AbstractClientPlayer acp ? JetrayBody.tailTip(acp, partial) : null;
			if (tail != null) {
				PyroDraw.glow(pose, glow, tail.subtract(cam), 0.12f + 0.3f * k, PyroDraw.dim(GLOW, 0.6f + 0.4f * k));
			}
			long tickSeed = seed * 13 + pl.tickCount / 2;
			java.util.Random rnd = new java.util.Random(tickSeed);
			int arcs = (int) (k * 4f);
			for (int i = 0; i < arcs; i++) {
				Vec3 a = c.add(right.scale((rnd.nextDouble() - 0.5) * 1.6 * sc)).add(up.scale((rnd.nextDouble() - 0.5) * 1.8 * sc)).add(f.scale((rnd.nextDouble() - 0.5) * 0.8));
				Vec3 b = a.add(right.scale((rnd.nextDouble() - 0.5) * 1.2)).add(up.scale((rnd.nextDouble() - 0.5) * 1.2));
				RecalFx.bolt(pose, buffers, a.subtract(cam), b.subtract(cam), 0.07f, CORE, GLOW, 0.8f, tickSeed + i);
			}
		}
	}

	/**
	 * Round 4, past the barrier: the shock cone he drags round his nose, and, from his own eyes (the trail is behind him there), thin streaks
	 * of green racing past outside the view's middle. Both follow the speed he really has.
	 */
	private static void sonicWake(Player pl, float partial, boolean fp, Vec3 f, Vec3 right, Vec3 up, float sc, Vec3 cam, PoseStack pose, MultiBufferSource buffers) {
		float k = net.minecraft.util.Mth.clamp((float) pl.position().subtract(pl.xo, pl.yo, pl.zo).length() / 4f, 0f, 1f);
		if (k <= 0.02f) {
			return;
		}
		Vec3 c = pl.getPosition(partial).add(0, 0.9 * sc, 0);
		if (pl != Minecraft.getInstance().player) {
			RecalFx.cone(pose, buffers, c.add(f.scale(1.3)).subtract(cam), f.scale(-1), 0.8f * sc, 2.6f * sc, GLOW, GLOW, 0.14f * k); // short: the third-person camera is 4 blocks back on this line
			return;
		}
		if (!fp) {
			return; // his own third-person camera sits on that line: the cone would be a bright disc over him
		}
		float time = pl.tickCount + partial;
		Vec3 eye = pl.getEyePosition(partial);
		for (int i = 0; i < 12; i++) {
			double a = i * 2.399;
			double rr = 2.6 + (i * 7 % 5) * 0.7;
			double run = ((time * 0.45 + i * 0.37) % 1.0);
			Vec3 at = eye.add(right.scale(Math.cos(a) * rr)).add(up.scale(Math.sin(a) * rr)).add(f.scale(16.0 - run * 22.0));
			RecalFx.beam(pose, buffers, at.subtract(cam), at.add(f.scale(5.0)).subtract(cam), 0.035f, GLOW, GLOW, 0.4f * k);
		}
	}

	private static Vec3 turned(Vec3 look, double yawDeg) {
		double a = Math.toRadians(yawDeg);
		double c = Math.cos(a), s = Math.sin(a);
		return new Vec3(look.x * c - look.z * s, look.y, look.x * s + look.z * c);
	}
}
