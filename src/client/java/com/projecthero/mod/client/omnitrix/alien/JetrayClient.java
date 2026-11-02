package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix): Jetray's client kit -- the player body in the user's Jetray skin and nothing else (v0.17.2
 * revamp: the wing, tail, crest and eye cubes are gone, user: "only use the skins i provided"). The neuroshock beams, the tail bolt, the pulse ring, the dive streak and the
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
	private static final float[][] TAKEOFF = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, -0.15f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.2f),
			QuirkKeyframes.rest(10) };
	private static final float[][] BOOST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.8f, 0, 0.2f, 0.8f, 0, -0.2f, 0.6f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.5f),
			QuirkKeyframes.f(8, 0.8f, 0, 0.2f, 0.8f, 0, -0.2f, 0.6f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.5f), QuirkKeyframes.rest(12) };
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
		}
	}

	public static void register() {
		AlienClients.register(Aliens.JETRAY.id(), new JetrayClient());
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// v0.17.2 (user: "delete all the alien models and only use the skins i provided please no extra things untill i say"): the
	// wings, tail, stinger, head crest and glowing eye cubes are gone -- Jetray is the player model in the user's Planet Minecraft
	// skin, nothing else (renderExtras is the default). The move effects below stay.

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case JetrayKit.ANIM_EYES -> EYES;
			case JetrayKit.ANIM_TAIL -> TAIL_SHOT;
			case JetrayKit.ANIM_DIVE -> DIVE;
			case JetrayKit.ANIM_SLASH -> SLASH;
			case JetrayKit.ANIM_TAKEOFF -> TAKEOFF;
			case JetrayKit.ANIM_BOOST -> BOOST;
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
			return true;
		}
		if ((Omnitrix.state(p).kitFlags & JetrayKit.F_FLYING) != 0 && !p.onGround()) {
			// gliding: legs together and trailing, arms a little out from the body
			m.rightLeg.xRot = 0.35f;
			m.leftLeg.xRot = 0.35f;
			m.rightLeg.zRot = 0.03f;
			m.leftLeg.zRot = -0.03f;
			m.rightArm.xRot = 0.3f;
			m.leftArm.xRot = 0.3f;
			m.rightArm.zRot = 0.35f;
			m.leftArm.zRot = -0.35f;
			return true;
		}
		return false;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case JetrayKit.ANIM_EYES -> FP_EYES;
			case JetrayKit.ANIM_TAIL -> FP_TAIL;
			case JetrayKit.ANIM_DIVE, JetrayKit.ANIM_BOOST, JetrayKit.ANIM_STREAM, JetrayKit.ANIM_RAM -> FP_DIVE;
			case JetrayKit.ANIM_SLASH, JetrayKit.ANIM_ROLL -> FP_SWEEP;
			case JetrayKit.ANIM_TAKEOFF, JetrayKit.ANIM_THUNDER -> FP_UP;
			case JetrayKit.ANIM_SPREAD -> FP_SPREAD;
			case JetrayKit.ANIM_PULSE -> FP_OUT;
			case JetrayKit.ANIM_GRAB -> FP_TEMPLE;
			case JetrayKit.ANIM_OVERLOAD -> FP_CHARGE;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
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
					Vec3 tip = fp ? eye.add(up.scale(0.35)).add(right.scale(0.45)).add(f.scale(0.4))
							: pl.getPosition(partial).add(0, 2.05 * sc, 0).subtract(f.scale(0.25 * sc));
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
					|| st.animId == JetrayKit.ANIM_BOOST && t < 10f;
			if (diving && !fp) {
				Vec3 c = pl.getPosition(partial).add(0, 0.9 * sc, 0);
				Vec3 dir = pl.getDeltaMovement().lengthSqr() > 0.05 ? pl.getDeltaMovement().normalize() : f;
				RecalFx.cone(pose, buffers, c.add(dir.scale(0.5)).subtract(cam), dir.scale(-1), 0.75f * sc, 2.8f * sc, CORE, GLOW, 0.55f);
			}
			// v0.17.2 revamp, Tail Grab: a crackling neuroshock tether from the small of his back to what he carries (no tail model:
			// the user wants the skin only)
			if ((st.kitFlags & JetrayKit.F_CARRY) != 0) {
				long id = st.readyAt(JetrayKit.ID + "." + JetrayKit.CARRY_TARGET);
				var held = id > 0 ? mc.level.getEntity((int) (id - 1)) : null;
				if (held != null) {
					Vec3 from = pl.getPosition(partial).add(0, 0.75 * sc, 0).subtract(f.scale(0.2));
					Vec3 to = held.getPosition(partial).add(0, held.getBbHeight() * 0.8, 0);
					RecalFx.bolt(pose, buffers, from.subtract(cam), to.subtract(cam), 0.12f, CORE, GLOW, 0.8f, pl.tickCount / 2);
				}
			}
		}
	}

	private static Vec3 turned(Vec3 look, double yawDeg) {
		double a = Math.toRadians(yawDeg);
		double c = Math.cos(a), s = Math.sin(a);
		return new Vec3(look.x * c - look.z * s, look.y, look.x * s + look.z * c);
	}
}
