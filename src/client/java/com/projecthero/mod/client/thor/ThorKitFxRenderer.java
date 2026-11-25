package com.projecthero.mod.client.thor;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorKit;
import com.projecthero.mod.power.ThorState;
import com.projecthero.mod.power.ThorTwinKit;
import com.projecthero.mod.power.ThorUltimates;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Cosmic revamp: the effects of Thor's new moves, drawn in code from each player's synced {@link ThorFx} (like
 * {@link ThorFxRenderer}), with a distinct look per weapon state:
 * <ul>
 *   <li><b>Unarmed</b> -- blue-white arcs crackling over the fists and body: the Thunder Fist charge ball, punch shock discs,
 *   Static Burst's shell of bolts, Lightning Incarnate's body of living lightning.</li>
 *   <li><b>Mjolnir</b> -- classic white-blue lightning: sky bolts, shockwave rings, the whirling strap ring, the dash's burnt
 *   trail, the Wrath of the Storm vortex and its giant bolt.</li>
 *   <li><b>Stormbreaker</b> -- lightning with the Bifrost's rainbow: rainbow streaks (Bifrost Step / Dash), prismatic rings and
 *   bursts, the spiralling rainbow beam, the impaling spectral axe of Bifrost Judgement.</li>
 *   <li><b>Twin Gods</b> -- both intertwined: the rainbow beam wound with lightning, the crossing spectral throw, the
 *   Thunderstruck wave, Ragnarok.</li>
 * </ul>
 * Every beam is skipped while {@code BeamClashClient.hides} its caster. Spectral weapons are drawn last, in their own pass.
 */
public final class ThorKitFxRenderer {
	private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;
	private static final int[] RAINBOW = ThorKit.RAINBOW;

	private record Spectral(ItemStack stack, Vec3 at, float yaw, float pitch, float roll, float scale) {
	}

	private ThorKitFxRenderer() {
	}

	public static void initialize() {
		WorldRenderEvents.AFTER_ENTITIES.register(ThorKitFxRenderer::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		double now = mc.level.getGameTime() + partial;
		Vec3 cam = ctx.camera().getPosition();
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource buffers = ctx.consumers();
		List<Spectral> spectral = new ArrayList<>();
		VertexConsumer vc = null;
		for (Player p : mc.level.players()) {
			ThorFx fx = p.getAttachedOrElse(ModAttachments.THOR_FX, null);
			if (fx == null || fx.equals(ThorFx.EMPTY) || p.distanceToSqr(cam) > MAX_DISTANCE_SQ) {
				continue;
			}
			if (vc == null) {
				vc = ThorDraw.buffer(buffers);
			}
			Vec3 feet = new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()), Mth.lerp(partial, p.zo, p.getZ()));
			double seed = p.getId() * 5.13;
			double t = now - fx.animStart();
			Vec3 at = new Vec3(fx.x(), fx.y(), fx.z());
			boolean self = ThorLightningArcClient.firstPersonSelf(p);
			if (ThorKitPose.owns(fx.anim()) && t >= 0 && t < 120) {
				anim(ps, vc, p, fx.anim(), t, at, feet, cam, now, seed, partial, self, spectral);
			}
			if (fx.has(ThorFx.CH_FIST)) {
				fistCharge(ps, vc, p, feet, cam, now - fx.chargeStart(), now, seed, partial, self);
			}
			if (fx.has(ThorFx.CH_INCARNATE)) {
				incarnate(ps, vc, p, feet.subtract(cam), now, seed, self);
			}
			if (fx.has(ThorFx.CH_GOD) && !self) {
				godEyes(ps, vc, p, cam, now, seed, partial);
			}
			if (fx.has(ThorFx.CH_SPRINT)) {
				sprint(ps, vc, p, feet.subtract(cam), now, seed);
			}
			if (fx.has(ThorFx.CH_BEAM) && !com.projecthero.mod.client.beamclash.BeamClashClient.hides(p)) {
				ThorState st = ThorState.of(p);
				if (st == ThorState.STORMBREAKER || st == ThorState.TWIN) {
					bifrostBeam(ps, vc, p, cam, now, partial, st == ThorState.TWIN, self);
				}
			}
		}
		if (!spectral.isEmpty()) {
			for (Spectral s : spectral) {
				ps.pushPose();
				ps.translate(s.at.x - cam.x, s.at.y - cam.y, s.at.z - cam.z);
				ps.mulPose(Axis.YP.rotationDegrees(s.yaw));
				ps.mulPose(Axis.XP.rotationDegrees(s.pitch));
				ps.mulPose(Axis.ZP.rotationDegrees(s.roll));
				ps.scale(s.scale, s.scale, s.scale);
				mc.getItemRenderer().renderStatic(s.stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
						ps, buffers, mc.level, 0);
				ps.popPose();
			}
		}
	}

	// ================================================================== positions

	private static Vec3 right(Player p, float partial) {
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
	}

	private static Vec3 forward(Player p, float partial) {
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/** Roughly where a hand is: the main arm's ({@code main}) or the other's; {@code raised} 0 low .. 1 overhead. */
	private static Vec3 hand(Player p, Vec3 feet, boolean main, float raised, float reach, float partial) {
		boolean rightSide = (p.getMainArm() == HumanoidArm.RIGHT) == main;
		Vec3 r = right(p, partial).scale(rightSide ? 0.4 : -0.4);
		double y = Mth.lerp(raised, p.getBbHeight() * 0.48, p.getBbHeight() * 1.42);
		return feet.add(0, y, 0).add(r).add(forward(p, partial).scale(0.2 + reach));
	}

	/** The hand holding {@code mjolnir} (true) / Stormbreaker (false); the main hand when neither is there. */
	private static boolean weaponInMain(Player p, boolean mjolnir) {
		var item = mjolnir ? ModItems.MJOLNIR : ModItems.STORMBREAKER;
		return p.getMainHandItem().is(item) || !p.getOffhandItem().is(item);
	}

	private static float fade(double t, double start, double len) {
		return (float) Mth.clamp(1.0 - (t - start) / len, 0.0, 1.0);
	}

	// ================================================================== the moves

	private static void anim(PoseStack ps, VertexConsumer vc, Player p, int anim, double t, Vec3 at, Vec3 feetW, Vec3 cam, double now,
			double seed, float partial, boolean self, List<Spectral> spectral) {
		PoseStack.Pose pose = ps.last();
		Vec3 feet = feetW.subtract(cam);
		Vec3 a = at.subtract(cam);
		double frame = Math.floor(now * 0.5);
		switch (anim) {
			case ThorFx.ANIM_THUNDER_FIST -> {
				if (t < 12) {
					float f = fade(t, 1, 11);
					Vec3 n = a.subtract(feet.add(0, 1.2, 0)).normalize();
					float r = 0.3f + 2.2f * (float) Math.min(1.0, t / 6.0);
					ringFacing(vc, pose, a, n, Math.max(0f, r - 0.5f), r, ThorDraw.GLOW, 0.8f * f, 40);
					ringFacing(vc, pose, a, n, Math.max(0f, r - 0.12f), r + 0.05f, ThorDraw.CORE, 0.9f * f, 40);
					ThorDraw.flare(vc, ps, a, 0.6f * f + 0.1f, (float) t * 40f, f);
					radialBolts(vc, pose, a, 2.2 * Math.min(1.0, t / 4.0 + 0.4), 6, seed + frame * 3.0, 0.5f, f);
				}
			}
			case ThorFx.ANIM_LIGHTNING_PALM -> {
				float f = fade(t, 3, 8);
				if (!self) {
					ThorDraw.flare(vc, ps, hand(p, feetW, true, 0.55f, 0.55f, partial).subtract(cam), 0.25f * f, (float) t * 35f, f);
				}
				ThorDraw.flare(vc, ps, a, 0.45f * f, (float) t * 30f, f);
			}
			case ThorFx.ANIM_GRAPPLE -> {
				Vec3 h = hand(p, feetW, true, t < 11 ? 1f : 0.6f, 0.2f, partial).subtract(cam);
				if (t < 11) {
					Vec3 held = feet.add(forward(p, partial).scale(0.5)).add(0, 2.6, 0);
					for (int i = 0; i < 3; i++) {
						Vec3 end = held.add((ThorDraw.hash(seed + frame + i) - 0.5) * 1.2, (ThorDraw.hash(seed + frame * 2 + i) - 0.5) * 1.0,
								(ThorDraw.hash(seed + frame * 3 + i) - 0.5) * 1.2);
						ThorDraw.bolt(vc, pose, ThorDraw.jagged(h, end, 5, 0.2, seed + frame * 7 + i), 0.45f, 0.85f);
					}
				} else if (t < 20) {
					float f = fade(t, 11, 9);
					ThorDraw.flare(vc, ps, h.add(forward(p, partial).scale(1.0)), 0.6f * f, (float) t * 40f, f);
				}
			}
			case ThorFx.ANIM_STORM_RUSH -> {
				double[] blows = { 2.2, 5.2, 8.2, 12.2 };
				for (int i = 0; i < blows.length; i++) {
					double tb = t - blows[i];
					if (tb >= 0 && tb < 4) {
						float f = fade(tb, 0, 4);
						boolean lead = i != 1;
						Vec3 fist = hand(p, feetW, lead, i == 3 ? 0.9f : 0.62f, 0.85f, partial).subtract(cam);
						ThorDraw.flare(vc, ps, fist, (i == 3 ? 0.7f : 0.4f) * f, (float) (t * 45.0), f);
						radialBolts(vc, pose, fist, 1.1, 4, seed + i * 9 + frame, 0.35f, f);
					}
				}
				if (!self && t < 14) {
					fistArcs(vc, pose, p, feetW, cam, now, seed, partial, 0.6f);
				}
			}
			case ThorFx.ANIM_STATIC_BURST -> {
				if (t < 4) {
					fistArcs(vc, pose, p, feetW, cam, now, seed, partial, 0.8f);
				} else if (t < 18) {
					double tb = t - 4;
					float f = fade(tb, 0, 14);
					float r = (float) (0.6 + ThorKit.BURST_RADIUS * (1 - Math.pow(1 - Math.min(1, tb / 8.0), 3)));
					Vec3 c = feet.add(0, 1.0, 0);
					for (int h = -2; h <= 2; h++) {
						float rr = r * (float) Math.sqrt(1 - (h / 2.6) * (h / 2.6));
						ThorDraw.ring(vc, pose, c.add(0, h * r * 0.35, 0), Math.max(0f, rr - 0.5f), rr, ThorDraw.GLOW, 0.4f * f, 40);
					}
					radialBolts(vc, pose, c, r, 14, seed + Math.floor(tb * 0.7) * 5, 0.6f, f);
					ThorDraw.flare(vc, ps, c, 0.9f * f, (float) tb * 40f, f);
				}
			}
			case ThorFx.ANIM_GROUND_POUND -> {
				if (t < 7 && !self) {
					fistArcs(vc, pose, p, feetW, cam, now, seed, partial, 0.7f);
				}
				shockwave(vc, ps, a.add(0, 0.06, 0), t - 7, 6.5f, seed, false);
			}
			case ThorFx.ANIM_LEAP, ThorFx.ANIM_METEOR_DIVE, ThorFx.ANIM_EXECUTIONER -> {
				boolean rainbow = anim == ThorFx.ANIM_EXECUTIONER;
				if (t < 10) {
					float f = fade(t, 0, 10);
					float r = 0.5f + 2.5f * (float) (t / 10.0);
					ThorDraw.ring(vc, pose, a.add(0, 0.06, 0), Math.max(0f, r - 0.6f), r, ThorDraw.GLOW, 0.7f * f, 40);
					if (rainbow) {
						rainbowRing(vc, pose, a.add(0, 0.08, 0), r * 0.7f, r, 0.6f * f, 40);
					}
				}
				if (!p.onGround() && t < 70) {
					// the streak he leaves through the air
					Vec3 v = p.getDeltaMovement();
					if (v.lengthSqr() > 0.01) {
						Vec3 c = feet.add(0, 1.0, 0);
						Vec3 back = c.subtract(v.normalize().scale(3.5));
						if (rainbow) {
							rainbowStreak(vc, pose, c, back, 0.35f, 0.8f, now);
						} else {
							ThorDraw.bolt(vc, pose, ThorDraw.jagged(c, back, 7, 0.35, seed + frame * 11), anim == ThorFx.ANIM_METEOR_DIVE ? 1.3f : 0.8f, 0.9f);
						}
						if (anim == ThorFx.ANIM_METEOR_DIVE && t > 10) {
							ThorDraw.flare(vc, ps, c.add(v.normalize().scale(0.6)), 0.7f, (float) now * 30f, 0.9f);
						}
					}
				}
			}
			case ThorFx.ANIM_LEAP_SLAM -> shockwave(vc, ps, a.add(0, 0.06, 0), t, 7.0f, seed, true);
			case ThorFx.ANIM_EXECUTIONER_SLAM -> {
				shockwave(vc, ps, a.add(0, 0.06, 0), t, 5.5f, seed, true);
				if (t < 16) {
					float f = fade(t, 0, 16);
					float r = 0.6f + 5.5f * (float) (1 - Math.pow(1 - Math.min(1, t / 10.0), 3));
					rainbowRing(vc, pose, a.add(0, 0.1, 0), Math.max(0f, r - 1.4f), r, 0.85f * f, 56);
					prismBurst(vc, pose, a.add(0, 0.5, 0), 3.0 * (1 - f) + 0.5, 10, seed + 3, f);
				}
			}
			case ThorFx.ANIM_WHIRLWIND -> {
				if (t < 10.5) {
					Vec3 c = hand(p, feetW, weaponInMain(p, true), 1f, 0f, partial).subtract(cam).add(0, 0.3, 0);
					float spin = (float) (t * t * 0.18);
					float f = (float) Math.min(1.0, t / 3.0);
					Vec3[] pts = new Vec3[17];
					for (int i = 0; i < 17; i++) {
						double ang = spin + i * Math.PI * 2 / 16;
						pts[i] = c.add(Math.cos(ang) * 1.0, Math.sin(ang * 3 + now) * 0.08, Math.sin(ang) * 1.0);
					}
					ThorDraw.ribbon(vc, pose, pts, 0.12f, ThorDraw.GLOW, 0.45f * f);
					ThorDraw.ribbon(vc, pose, pts, 0.04f, ThorDraw.CORE, 0.8f * f);
					ThorDraw.flare(vc, ps, c.add(Math.cos(spin) * 1.0, 0, Math.sin(spin) * 1.0), 0.3f, (float) now * 40f, f);
				} else if (t < 16) {
					float f = fade(t, 10.5, 5.5);
					ThorDraw.flare(vc, ps, hand(p, feetW, true, 0.6f, 1.0f, partial).subtract(cam), 0.7f * f, (float) t * 40f, f);
				}
			}
			case ThorFx.ANIM_STORM_DASH -> {
				if (t < 12) {
					float f = fade(t, 3, 9);
					Vec3 from = a.add(0, 1.0, 0);
					Vec3 to = feet.add(0, 1.0, 0);
					for (int i = 0; i < 3; i++) {
						ThorDraw.bolt(vc, pose, ThorDraw.jagged(from, to, 10, 0.45, seed + frame * 13 + i * 7), 0.9f - i * 0.2f, f);
					}
					ThorDraw.flare(vc, ps, to, 0.5f * f, (float) t * 40f, f);
				}
			}
			case ThorFx.ANIM_BIFROST_HURL -> {
				if (t < 6.5) {
					prismBurst(vc, pose, hand(p, feetW, weaponInMain(p, false), 0.95f, -0.1f, partial).subtract(cam), 0.6, 6, seed + frame, 0.7f);
				} else if (t < 14) {
					float f = fade(t, 6.5, 7.5);
					prismBurst(vc, pose, hand(p, feetW, true, 0.6f, 1.0f, partial).subtract(cam), 1.6 * (1 - f) + 0.3, 12, seed + 9, f);
				}
			}
			case ThorFx.ANIM_STORM_CLEAVE -> {
				Vec3 blade = feet.add(0, 3.0, 0).add(forward(p, partial).scale(0.2));
				if (t >= 2 && t < 7.2) {
					Vec3 sky = blade.add((ThorDraw.hash(seed) - 0.5) * 3, 16, (ThorDraw.hash(seed + 1) - 0.5) * 3);
					ThorDraw.bolt(vc, pose, ThorDraw.jagged(sky, blade, 12, 1.0, seed + frame * 5), 1.0f, 0.9f);
					ThorDraw.flare(vc, ps, blade, 0.45f, (float) now * 40f, 0.9f);
					prismBurst(vc, pose, blade, 0.8, 5, seed + frame, 0.6f);
				}
				if (t >= 7.2 && t < 18) {
					float f = fade(t, 7.2, 10);
					float r = 0.5f + 3.0f * (float) (1 - Math.pow(1 - Math.min(1, (t - 7.2) / 6), 3));
					ThorDraw.ring(vc, pose, a.add(0, 0.08, 0), Math.max(0f, r - 0.8f), r, ThorDraw.GLOW, 0.8f * f, 40);
					rainbowRing(vc, pose, a.add(0, 0.1, 0), Math.max(0f, r - 0.5f), r + 0.2f, 0.7f * f, 40);
					ThorDraw.flare(vc, ps, a.add(0, 1.0, 0), 0.9f * f, (float) t * 30f, f);
				}
			}
			case ThorFx.ANIM_BIFROST_STEP, ThorFx.ANIM_BIFROST_DASH -> {
				double len = anim == ThorFx.ANIM_BIFROST_STEP ? 14 : 10;
				if (t < len) {
					float f = fade(t, 2, len - 2);
					Vec3 from = a.add(0, 1.0, 0);
					Vec3 to = feet.add(0, 1.0, 0);
					rainbowStreak(vc, pose, from, to, 0.55f * (0.5f + 0.5f * f), f, now);
					rainbowRing(vc, pose, a.add(0, 0.08, 0), 0.4f, 1.6f + (1 - f) * 1.5f, 0.7f * f, 32);
					rainbowRing(vc, pose, feet.add(0, 0.08, 0), 0.4f, 1.2f + (1 - f) * 2.0f, 0.7f * f, 32);
					if (anim == ThorFx.ANIM_BIFROST_STEP && t >= 2 && t < 9) {
						arcSweep(vc, pose, p, feet, partial, 4.0f, 75f, fade(t, 2, 7), now, true);
					}
				}
			}
			case ThorFx.ANIM_RENDING_ARC -> {
				if (t >= 5 && t < 13) {
					arcSweep(vc, pose, p, feet, partial, (float) ThorKit.ARC_RADIUS, 90f, fade(t, 5, 8), now, true);
				} else if (t < 5 && !self) {
					prismBurst(vc, pose, hand(p, feetW, weaponInMain(p, false), 0.55f, 0f, partial).subtract(cam), 0.5, 4, seed + frame, 0.6f);
				}
			}
			case ThorFx.ANIM_TWIN_THROW -> twinThrow(vc, ps, p, t, at, feetW, cam, now, seed, partial, spectral);
			case ThorFx.ANIM_THUNDERSTRUCK -> thunderstruck(vc, ps, p, t, at, feetW, cam, now, seed, partial);
			case ThorFx.ANIM_CROSSFIRE -> {
				if (t >= 2 && t < ThorTwinKit.CROSSFIRE_HIT + 5) {
					float f = t < ThorTwinKit.CROSSFIRE_HIT + 1 ? 1f : fade(t, ThorTwinKit.CROSSFIRE_HIT + 1, 4);
					Vec3 axe = hand(p, feetW, weaponInMain(p, false), 0.62f, 0.9f, partial).subtract(cam);
					rainbowBeam(vc, pose, axe, a, 0.3f, f, now);
					if (t >= ThorTwinKit.CROSSFIRE_HIT) {
						ThorDraw.flare(vc, ps, a, 0.9f * f, (float) t * 40f, f);
						prismBurst(vc, pose, a, 1.5, 12, seed + 4, f);
					}
				}
			}
			case ThorFx.ANIM_INCARNATE_BURST -> {
				if (t < 30) {
					float f = fade(t, 0, 30);
					Vec3 c = feet.add(0, 0.08, 0);
					for (int w = 0; w < 3; w++) {
						double tw = t - w * 3;
						if (tw < 0) {
							continue;
						}
						float k = (float) Math.min(1, tw / 22);
						float r = 1f + 11f * (1f - (1f - k) * (1f - k) * (1f - k));
						ThorDraw.ring(vc, pose, c, Math.max(0f, r - 2f), r, ThorDraw.GLOW, 0.85f * (1 - k), 72);
						ThorDraw.ring(vc, pose, c, Math.max(0f, r - 0.4f), r + 0.15f, ThorDraw.CORE, 0.9f * (1 - k), 72);
						ThorDraw.wall(vc, pose, c, r, 3.0f * (1 - k) + 0.3f, ThorDraw.GLOW, 0.5f * (1 - k), 72);
					}
					radialBolts(vc, pose, c.add(0, 1.0, 0), 10.0 * Math.min(1, t / 6 + 0.3), 18, seed + Math.floor(t * 0.6) * 3, 0.9f, f);
					ThorDraw.flare(vc, ps, c.add(0, 1.0, 0), 2.6f * f + 0.2f, (float) t * 25f, f);
				}
			}
			case ThorFx.ANIM_WRATH_STORM -> wrathStorm(vc, ps, p, t, a, feet, now, seed, partial);
			case ThorFx.ANIM_JUDGEMENT -> judgement(vc, ps, p, t, at, feetW, cam, now, seed, partial, spectral);
			case ThorFx.ANIM_RAGNAROK -> ragnarok(vc, ps, p, t, at, feetW, cam, now, seed, partial, spectral);
			case ThorFx.ANIM_GOD_OF_THUNDER -> {
				if (t < 16) {
					float f = fade(t, 4, 12);
					Vec3 c = feet.add(0, 1.1, 0);
					radialBolts(vc, pose, c, 2.5, 8, seed + Math.floor(t * 0.6), 0.6f, f);
					ThorDraw.flare(vc, ps, feet.add(0, p.getEyeHeight(), 0), 0.8f * f, (float) t * 30f, f);
					ThorDraw.ring(vc, pose, feet.add(0, 0.06, 0), 0.5f, 1.2f + 3f * (1 - f), ThorDraw.GLOW, 0.7f * f, 40);
				}
			}
			case ThorFx.ANIM_STORM_ANCHOR -> {
				if (t >= 3 && t < 12) {
					float f = fade(t, 3, 9);
					prismBurst(vc, pose, hand(p, feetW, true, 0.4f, 0.8f, partial).subtract(cam), 1.2, 10, seed, f);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- Unarmed bits

	/** Thunder Fist charging: a ball of lightning swelling on the cocked fist, arcs running up the arm and to the ground. */
	private static void fistCharge(PoseStack ps, VertexConsumer vc, Player p, Vec3 feetW, Vec3 cam, double held, double now, double seed,
			float partial, boolean self) {
		float b = (float) Mth.clamp(held / ThorKit.FIST_CHARGE_TICKS, 0.0, 1.0);
		PoseStack.Pose pose = ps.last();
		if (self) {
			return; // first person: the crackle is drawn round the fist by the hand pose's own sparks (server particles)
		}
		Vec3 fist = hand(p, feetW, true, 0.35f, -0.25f, partial).subtract(cam);
		double frame = Math.floor(now * 0.6);
		float pulse = 0.85f + 0.15f * (float) ThorDraw.hash(seed + frame);
		ThorDraw.flare(vc, ps, fist, (0.12f + 0.35f * b) * pulse, (float) (now * 35 % 360), 0.6f + 0.4f * b);
		if (!self) {
			Vec3 shoulder = hand(p, feetW, true, 1.0f, -0.1f, partial).subtract(cam).add(0, -0.15, 0);
			for (int i = 0; i < 1 + (int) (b * 3); i++) {
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(fist, shoulder, 5, 0.18, seed + frame * 7 + i), 0.35f, 0.8f);
			}
			if (b >= 1f && ThorDraw.hash(seed + frame * 3.1) < 0.6) {
				Vec3 ground = feetW.subtract(cam).add((ThorDraw.hash(seed + frame) - 0.5) * 2, 0.05, (ThorDraw.hash(seed + frame * 2) - 0.5) * 2);
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(fist, ground, 6, 0.3, seed + frame * 13), 0.45f, 0.8f);
			}
		}
	}

	/** Blue-white arcs crackling between and over his fists (Unarmed moves). */
	private static void fistArcs(VertexConsumer vc, PoseStack.Pose pose, Player p, Vec3 feetW, Vec3 cam, double now, double seed, float partial,
			float alpha) {
		double frame = Math.floor(now * 0.6);
		Vec3 a = hand(p, feetW, true, 0.55f, 0.3f, partial).subtract(cam);
		Vec3 b = hand(p, feetW, false, 0.55f, 0.3f, partial).subtract(cam);
		for (Vec3 f : new Vec3[] { a, b }) {
			for (int i = 0; i < 2; i++) {
				Vec3 end = f.add((ThorDraw.hash(seed + frame + i * 3) - 0.5) * 0.7, (ThorDraw.hash(seed + frame * 2 + i) - 0.5) * 0.7,
						(ThorDraw.hash(seed + frame * 3 + i) - 0.5) * 0.7);
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(f, end, 4, 0.12, seed + frame * 5 + i), 0.3f, alpha);
			}
		}
	}

	/** Lightning Incarnate: his body a cage of living lightning, a glowing core, arcs leaping to the ground around him. */
	private static void incarnate(PoseStack ps, VertexConsumer vc, Player p, Vec3 feet, double now, double seed, boolean self) {
		PoseStack.Pose pose = ps.last();
		double frame = Math.floor(now * 0.8);
		float h = p.getBbHeight();
		if (!self) {
			for (int i = 0; i < 7; i++) {
				double a0 = Math.PI * 2 * ThorDraw.hash(seed + frame * 1.3 + i);
				double a1 = a0 + 0.5 + ThorDraw.hash(seed + frame * 2.3 + i) * 1.6;
				double y0 = h * ThorDraw.hash(seed + frame * 3.3 + i);
				double y1 = h * ThorDraw.hash(seed + frame * 4.3 + i);
				Vec3 p0 = feet.add(Math.cos(a0) * 0.42, y0, Math.sin(a0) * 0.42);
				Vec3 p1 = feet.add(Math.cos(a1) * 0.42, y1, Math.sin(a1) * 0.42);
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(p0, p1, 6, 0.18, seed + frame * 7 + i), 0.5f, 1f);
			}
			ThorDraw.flare(vc, ps, feet.add(0, h * 0.6, 0), 0.5f, (float) (now * 40 % 360), 0.85f);
		}
		for (int i = 0; i < 3; i++) {
			if (ThorDraw.hash(seed + frame * 5.1 + i) < 0.55) {
				double ang = Math.PI * 2 * ThorDraw.hash(seed + frame * 6.1 + i);
				double r = 1.2 + 2.0 * ThorDraw.hash(seed + frame * 7.1 + i);
				Vec3 g = feet.add(Math.cos(ang) * r, 0.05, Math.sin(ang) * r);
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(feet.add(0, h * 0.5, 0), g, 6, 0.3, seed + frame * 9 + i), 0.55f, 0.85f);
			}
		}
		ThorDraw.ring(vc, pose, feet.add(0, 0.05, 0), 0.6f, (float) ThorKit.INCARNATE_AURA_RADIUS, ThorDraw.HALO,
				0.25f + 0.1f * (float) Math.sin(now * 0.8), 40);
	}

	/** God of Thunder: eyes blazing white-blue, a spark leaping off them now and then. */
	private static void godEyes(PoseStack ps, VertexConsumer vc, Player p, Vec3 cam, double now, double seed, float partial) {
		Vec3 eye = p.getEyePosition(partial).subtract(cam);
		float yaw = Mth.lerp(partial, p.yHeadRotO, p.yHeadRot) * Mth.DEG_TO_RAD;
		float pitch = p.getViewXRot(partial) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
		Vec3 side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 centre = eye.add(fwd.scale(0.26)).add(0, 0.02, 0);
		float flick = 0.85f + 0.15f * (float) Math.sin(now * 1.7);
		for (int s = -1; s <= 1; s += 2) {
			Vec3 e = centre.add(side.scale(0.11 * s));
			ps.pushPose();
			ps.translate(e.x, e.y, e.z);
			com.projecthero.mod.client.maxsteel.TurboDraw.orb(vc, ps, 0.06f * flick, ThorDraw.GLOW, (float) (now * 30), 1f);
			ps.popPose();
		}
		double frame = Math.floor(now * 0.4);
		if (ThorDraw.hash(seed + frame * 1.7) < 0.3) {
			Vec3 from = centre.add(side.scale(0.11 * (ThorDraw.hash(seed + frame) < 0.5 ? -1 : 1)));
			Vec3 to = from.add(side.scale((ThorDraw.hash(seed + frame * 2) - 0.5) * 0.8)).add(0, 0.3 + ThorDraw.hash(seed + frame * 3) * 0.4, 0);
			ThorDraw.bolt(vc, ps.last(), ThorDraw.jagged(from, to, 4, 0.08, seed + frame * 5), 0.25f, 0.9f);
		}
	}

	/** Storm Sprint: lightning streaming off his legs behind him. */
	private static void sprint(PoseStack ps, VertexConsumer vc, Player p, Vec3 feet, double now, double seed) {
		Vec3 v = p.getDeltaMovement().multiply(1, 0, 1);
		if (v.lengthSqr() < 0.002) {
			return;
		}
		double frame = Math.floor(now * 0.7);
		Vec3 back = v.normalize().scale(-2.4);
		for (int i = 0; i < 2; i++) {
			Vec3 a = feet.add(0, 0.3 + i * 0.6, 0);
			ThorDraw.bolt(vc, ps.last(), ThorDraw.jagged(a, a.add(back).add(0, (ThorDraw.hash(seed + frame + i) - 0.5) * 0.6, 0), 6, 0.25,
					seed + frame * 9 + i), 0.45f, 0.85f);
		}
	}

	// ---------------------------------------------------------------- beams

	/** Bifrost Beam (narrow, rainbow) / Twin Beam (rainbow wound with lightning) from the weapon along his aim. */
	private static void bifrostBeam(PoseStack ps, VertexConsumer vc, Player p, Vec3 cam, double now, float partial, boolean twin,
			boolean self) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = p.getEyePosition(partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 end = eye.add(look.scale(20.0));
		HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e != p && e.isPickable() && !(e instanceof Player pl && pl.isSpectator()), eye.distanceToSqr(end));
		if (eh != null) {
			Entity e = eh.getEntity();
			end = e.position().add(0, e.getBbHeight() * 0.5, 0);
		}
		Vec3 feet = new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()), Mth.lerp(partial, p.zo, p.getZ()));
		Vec3 start;
		if (self) {
			start = eye.add(look.scale(0.6)).add(0, -0.45, 0).add(right(p, partial).scale(0.25));
		} else {
			boolean axeMain = weaponInMain(p, false);
			start = hand(p, feet, axeMain, 0.85f, 1.1f, partial).add(look.scale(0.3));
		}
		Vec3 a = start.subtract(cam);
		Vec3 b = end.subtract(cam);
		float width = twin ? 0.36f : 0.22f;
		rainbowBeam(vc, ps.last(), a, b, width, 1f, now);
		ThorDraw.flare(vc, ps, b, twin ? 0.7f : 0.5f, (float) (now * 40 % 360), 0.9f);
		prismBurst(vc, ps.last(), b, 0.9, 6, p.getId() + Math.floor(now * 0.5), 0.8f);
		if (twin) {
			// the lightning wound round it
			double frame = Math.floor(now * 0.5);
			Vec3 d = b.subtract(a);
			int n = Math.max(8, (int) (d.length() * 2));
			Vec3 dir = d.normalize();
			Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 u = dir.cross(ref).normalize();
			Vec3 w = dir.cross(u).normalize();
			Vec3[] helix = new Vec3[n + 1];
			for (int i = 0; i <= n; i++) {
				double k = i / (double) n;
				double ang = k * d.length() * 2.2 - now * 0.9;
				double r = 0.45 * Math.sin(Math.PI * Math.min(1, k * 6)) + 0.05 * (ThorDraw.hash(frame + i) - 0.5);
				helix[i] = a.add(d.scale(k)).add(u.scale(Math.cos(ang) * r)).add(w.scale(Math.sin(ang) * r));
			}
			ThorDraw.bolt(vc, ps.last(), helix, 0.7f, 0.95f);
		}
	}

	// ---------------------------------------------------------------- Twin Gods

	private static void twinThrow(VertexConsumer vc, PoseStack ps, Player p, double t, Vec3 at, Vec3 feetW, Vec3 cam, double now, double seed,
			float partial, List<Spectral> spectral) {
		PoseStack.Pose pose = ps.last();
		int wind = ThorTwinKit.THROW_WINDUP;
		int fly = ThorTwinKit.THROW_FLIGHT;
		if (t >= wind && t < wind + fly) {
			double k = (t - wind) / fly;
			for (int w = 0; w < 2; w++) {
				boolean mj = w == 0;
				Vec3 startW = hand(p, feetW, weaponInMain(p, mj), 1f, 0.3f, partial);
				Vec3 side = right(p, partial).scale((weaponInMain(p, mj) == (p.getMainArm() == HumanoidArm.RIGHT) ? 1 : -1) * 3.0);
				// a curve that swings out wide and crosses the other weapon's path onto the meeting point
				Vec3 ctrl = startW.lerp(at, 0.5).add(side).add(0, 1.5, 0);
				Vec3 pos = bezier(startW, ctrl, at, k);
				Vec3 prev = bezier(startW, ctrl, at, Math.max(0, k - 0.18));
				Vec3 dir = pos.subtract(prev);
				float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
				float spin = (float) (t * (mj ? 55 : -55));
				spectral.add(new Spectral(new ItemStack(mj ? ModItems.MJOLNIR : ModItems.STORMBREAKER), pos, yaw, spin, 0, mj ? 0.9f : 1.0f));
				Vec3 a = pos.subtract(cam);
				Vec3 b = prev.subtract(cam);
				if (mj) {
					ThorDraw.bolt(vc, pose, ThorDraw.jagged(a, b, 6, 0.25, seed + Math.floor(now * 0.6) * 3), 0.8f, 0.95f);
				} else {
					rainbowStreak(vc, pose, a, b, 0.3f, 0.95f, now);
				}
			}
		}
		double ti = t - (wind + fly);
		if (ti >= 0 && ti < 18) {
			Vec3 c = at.subtract(cam);
			float f = fade(ti, 0, 18);
			ThorDraw.flare(vc, ps, c, 1.6f * f + 0.2f, (float) ti * 30f, f);
			float r = 0.6f + 4.5f * (float) (1 - Math.pow(1 - Math.min(1, ti / 10), 3));
			Vec3 ground = c.add(0, -0.9, 0);
			ThorDraw.ring(vc, pose, ground, Math.max(0f, r - 1.2f), r, ThorDraw.GLOW, 0.8f * f, 56);
			rainbowRing(vc, pose, ground.add(0, 0.03, 0), Math.max(0f, r - 0.6f), r + 0.3f, 0.75f * f, 56);
			radialBolts(vc, pose, c, 4.0 * Math.min(1, ti / 4 + 0.4), 10, seed + Math.floor(ti * 0.7), 0.7f, f);
			prismBurst(vc, pose, c, 3.0 * (1 - f) + 0.5, 14, seed + 1, f);
		}
	}

	private static void thunderstruck(VertexConsumer vc, PoseStack ps, Player p, double t, Vec3 at, Vec3 feetW, Vec3 cam, double now,
			double seed, float partial) {
		PoseStack.Pose pose = ps.last();
		double frame = Math.floor(now * 0.6);
		if (t < ThorTwinKit.CLEAVE_SLASH) {
			// lightning flowing from Mjolnir into Stormbreaker, building
			Vec3 m = hand(p, feetW, weaponInMain(p, true), 0.75f, 0.7f, partial).subtract(cam);
			Vec3 s = hand(p, feetW, weaponInMain(p, false), 0.75f, 0.7f, partial).subtract(cam);
			float build = (float) Math.min(1, t / ThorTwinKit.CLEAVE_SLASH);
			for (int i = 0; i < 1 + (int) (build * 3); i++) {
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(m, s, 6, 0.2, seed + frame * 7 + i), 0.5f + build * 0.3f, 0.95f);
			}
			ThorDraw.flare(vc, ps, s, 0.15f + 0.35f * build, (float) now * 40f, 0.9f);
			prismBurst(vc, pose, s, 0.4 + build * 0.5, 4, seed + frame, 0.7f);
		} else {
			double tw = t - ThorTwinKit.CLEAVE_SLASH;
			if (tw < ThorTwinKit.CLEAVE_WAVE_TICKS + 6) {
				Vec3 end = at.subtract(cam);
				Vec3 start = feetW.subtract(cam);
				Vec3 dir = end.subtract(start).multiply(1, 0, 1);
				dir = dir.lengthSqr() < 1e-6 ? forward(p, partial) : dir.normalize();
				Vec3 side = new Vec3(-dir.z, 0, dir.x);
				double k = Math.min(1.0, (tw + 1) / ThorTwinKit.CLEAVE_WAVE_TICKS);
				float f = tw < ThorTwinKit.CLEAVE_WAVE_TICKS ? 1f : fade(tw, ThorTwinKit.CLEAVE_WAVE_TICKS, 6);
				Vec3 front = start.add(dir.scale(1.0 + (ThorTwinKit.WAVE_LENGTH - 1.0) * k)).add(0, 1.0, 0);
				double half = 1.2 + 2.2 * k;
				int n = 14;
				Vec3[] crescent = new Vec3[n + 1];
				for (int i = 0; i <= n; i++) {
					double u = i / (double) n * 2 - 1;
					crescent[i] = front.add(side.scale(u * half)).add(dir.scale(-(u * u) * 1.1)).add(0, -Math.abs(u) * 0.25, 0);
				}
				// a rainbow crescent with a white-hot lightning edge
				for (int c = 0; c < RAINBOW.length; c++) {
					Vec3[] band = new Vec3[n + 1];
					for (int i = 0; i <= n; i++) {
						band[i] = crescent[i].add(dir.scale(-0.12 * c)).add(0, 0.05 * (c - 3), 0);
					}
					ThorDraw.ribbon(vc, pose, band, 0.09f, RAINBOW[c], 0.65f * f);
				}
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(crescent[0], crescent[n], n, 0.12, seed + frame * 3), 1.0f, f);
				for (int i = 0; i < 3; i++) {
					Vec3 sp = crescent[(int) (ThorDraw.hash(seed + frame + i) * n)];
					ThorDraw.bolt(vc, pose, ThorDraw.jagged(sp, sp.add(0, -1.0, 0).add(dir.scale(-0.6)), 4, 0.25, seed + frame * 9 + i), 0.5f, f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- ultimates

	/** A vortex of cloud-light and arcs wheeling over his head while the bolts rain; then one colossal bolt. */
	private static void wrathStorm(VertexConsumer vc, PoseStack ps, Player p, double t, Vec3 target, Vec3 feet, double now, double seed,
			float partial) {
		PoseStack.Pose pose = ps.last();
		int fin = ThorUltimates.WRATH_FINAL;
		double frame = Math.floor(now * 0.5);
		if (t < fin + 4) {
			float in = (float) Math.min(1, t / 10);
			Vec3 sky = feet.add(0, 9.0, 0);
			for (int ring = 0; ring < 3; ring++) {
				float r = 3f + ring * 3.2f;
				ThorDraw.ring(vc, pose, sky.add(0, ring * 0.4, 0), r - 1.3f, r, ring == 0 ? ThorDraw.GLOW : ThorDraw.HALO, (0.45f - ring * 0.1f) * in, 56);
			}
			for (int i = 0; i < 5; i++) {
				double a0 = now * 0.08 + i * Math.PI * 2 / 5;
				Vec3 p0 = sky.add(Math.cos(a0) * 8, 0.3, Math.sin(a0) * 8);
				Vec3 p1 = sky.add(Math.cos(a0 + 1.0) * 4, 0.1, Math.sin(a0 + 1.0) * 4);
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(p0, p1, 6, 0.6, seed + frame * 3 + i), 0.6f, 0.7f * in);
			}
			// the raised hammer drinks the storm
			Vec3 head = feet.add(0, p.getBbHeight() * 1.45, 0).add(right(p, partial).scale(weaponInMain(p, true) == (p.getMainArm() == HumanoidArm.RIGHT) ? 0.4 : -0.4));
			ThorDraw.flare(vc, ps, head, 0.35f + 0.15f * (float) Math.sin(now), (float) now * 40f, in);
			if (ThorDraw.hash(seed + frame * 1.9) < 0.5) {
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(sky.add((ThorDraw.hash(seed + frame) - 0.5) * 4, 0, (ThorDraw.hash(seed + frame * 2) - 0.5) * 4),
						head, 10, 0.9, seed + frame * 11), 0.9f, 0.9f * in);
			}
		}
		double tf = t - fin;
		if (tf >= -3 && tf < 28) {
			// the colossal bolt
			float f = tf < 3 ? 1f : fade(tf, 3, 25);
			double bf = Math.floor((tf + 10) * 0.8);
			Vec3 top = target.add(0, 60, 0);
			for (int i = 0; i < 3; i++) {
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(top.add((i - 1) * 1.5, 0, 0), target, 24, 2.2, seed + bf * 13 + i), 3.6f - i, f * (tf < 0 ? 0.5f : 1f));
			}
			if (tf >= 0) {
				shockwave(vc, ps, target.add(0, 0.08, 0), tf, 12f, seed, true);
				ThorDraw.flare(vc, ps, target.add(0, 1.5, 0), 3.2f * f + 0.3f, (float) tf * 20f, f);
				ThorDraw.wall(vc, pose, target, 1.6f, 30f * f, ThorDraw.CORE, 0.35f * f, 24);
			}
		}
	}

	private static void judgement(VertexConsumer vc, PoseStack ps, Player p, double t, Vec3 at, Vec3 feetW, Vec3 cam, double now, double seed,
			float partial, List<Spectral> spectral) {
		PoseStack.Pose pose = ps.last();
		int jt = ThorUltimates.JUDGE_THROW, ji = ThorUltimates.JUDGE_IMPALE, jb = ThorUltimates.JUDGE_BURST;
		Vec3 target = at.add(0, 1.0, 0);
		if (t < jt) {
			prismBurst(vc, pose, hand(p, feetW, weaponInMain(p, false), 1f, -0.2f, partial).subtract(cam), 0.7, 6, seed + Math.floor(now * 0.5), 0.8f);
		} else if (t < ji) {
			double k = (t - jt) / (ji - jt);
			Vec3 from = hand(p, feetW, true, 1f, 0.2f, partial);
			Vec3 pos = from.lerp(target, k).add(0, Math.sin(Math.PI * k) * 1.5, 0);
			Vec3 dir = target.subtract(from);
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			spectral.add(new Spectral(new ItemStack(ModItems.STORMBREAKER), pos, yaw, (float) (t * 60), 0, 1.15f));
			rainbowStreak(vc, pose, pos.subtract(cam), from.lerp(target, Math.max(0, k - 0.35)).subtract(cam), 0.4f, 0.95f, now);
		} else if (t < jb + 2) {
			// impaled: the axe stands in the target, blade-down
			spectral.add(new Spectral(new ItemStack(ModItems.STORMBREAKER), target.add(0, 0.6, 0), 0, 180, 0, 1.15f));
			Vec3 c = target.subtract(cam);
			double k = (t - ji) / (jb - ji);
			for (int s = 0; s < 6; s++) {
				double y = s * 1.6 + k * 5;
				float r = (float) (2.6 - s * 0.32) * (float) (0.4 + 0.6 * k);
				rainbowRing(vc, pose, c.add(0, y, 0), Math.max(0f, r - 0.4f), r, (float) (0.25 + 0.5 * k), 40);
			}
			Vec3 sky = c.add(0, 30, 0);
			rainbowBeam(vc, pose, sky, c.add(0, 1.0, 0), (float) (0.1 + 0.4 * k), (float) k, now);
			ThorDraw.flare(vc, ps, c, 0.5f + 0.4f * (float) k, (float) now * 40f, 0.9f);
		}
		double tb = t - jb;
		if (tb >= 0 && tb < 18) {
			Vec3 c = target.subtract(cam).add(0, -0.9, 0);
			float f = fade(tb, 0, 18);
			float r = 0.8f + (float) ThorUltimates.JUDGE_BURST_RADIUS * 1.2f * (float) (1 - Math.pow(1 - Math.min(1, tb / 12), 3));
			rainbowRing(vc, pose, c.add(0, 0.08, 0), Math.max(0f, r - 2.2f), r, 0.95f * f, 72);
			ThorDraw.ring(vc, pose, c.add(0, 0.06, 0), Math.max(0f, r - 0.4f), r + 0.2f, ThorDraw.CORE, 0.9f * f, 72);
			rainbowWall(vc, pose, c, r, 3.2f * f + 0.3f, 0.5f * f, 72);
			prismBurst(vc, pose, c.add(0, 1.2, 0), 6 * (1 - f) + 0.6, 22, seed + 7, f);
			ThorDraw.flare(vc, ps, c.add(0, 1.2, 0), 2.4f * f + 0.2f, (float) tb * 30f, f);
		}
	}

	private static void ragnarok(VertexConsumer vc, PoseStack ps, Player p, double t, Vec3 at, Vec3 feetW, Vec3 cam, double now, double seed,
			float partial, List<Spectral> spectral) {
		PoseStack.Pose pose = ps.last();
		double frame = Math.floor(now * 0.5);
		Vec3 feet = feetW.subtract(cam);
		Vec3 c0 = at.subtract(cam);
		Vec3 mh = hand(p, feetW, weaponInMain(p, true), 1f, 0f, partial).subtract(cam);
		Vec3 sh = hand(p, feetW, weaponInMain(p, false), 1f, 0f, partial).subtract(cam);
		if (t < ThorUltimates.RAG_HURL) {
			float in = (float) Math.min(1, t / 12);
			// lightning between the raised weapons, a rainbow halo round him
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(mh, sh, 6, 0.3, seed + frame * 3), 0.8f, 0.9f * in);
			rainbowRing(vc, pose, feet.add(0, 1.0, 0), 1.0f, 1.8f, 0.5f * in, 40);
			ThorDraw.flare(vc, ps, mh, 0.35f, (float) now * 40f, in);
			prismBurst(vc, pose, sh, 0.6, 4, seed + frame, 0.8f * in);
			if (t >= ThorUltimates.RAG_RAIN_START) {
				// over the centre: a wheel of storm and rainbow
				Vec3 sky = c0.add(0, 14, 0);
				for (int r = 0; r < 3; r++) {
					float rr = 4f + r * 4f;
					ThorDraw.ring(vc, pose, sky.add(0, r * 0.5, 0), rr - 1.5f, rr, ThorDraw.GLOW, 0.35f, 56);
					rainbowRing(vc, pose, sky.add(0, r * 0.5 - 0.2, 0), rr - 0.6f, rr + 0.4f, 0.35f, 56);
				}
			}
		}
		if (t >= ThorUltimates.RAG_HURL && t < ThorUltimates.RAG_IMPACT) {
			double k = (t - ThorUltimates.RAG_HURL) / (ThorUltimates.RAG_IMPACT - ThorUltimates.RAG_HURL);
			Vec3 from = sh.add(cam);
			Vec3 pos = from.lerp(at, k * k);
			Vec3 dir = at.subtract(from);
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			spectral.add(new Spectral(new ItemStack(ModItems.STORMBREAKER), pos, yaw, (float) (t * 70), 0, 1.4f));
			rainbowStreak(vc, pose, pos.subtract(cam), from.lerp(at, Math.max(0, k * k - 0.3)).subtract(cam), 0.5f, 1f, now);
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(pos.subtract(cam), mh, 8, 0.5, seed + frame * 5), 0.7f, 0.8f);
		}
		double ti = t - ThorUltimates.RAG_IMPACT;
		if (ti >= 0 && ti < 30) {
			if (ti < 14) {
				spectral.add(new Spectral(new ItemStack(ModItems.STORMBREAKER), at.add(0, 0.9, 0), 0, 180, 0, 1.4f));
			}
			float f = fade(ti, 0, 30);
			shockwave(vc, ps, c0.add(0, 0.08, 0), ti, 14f, seed, true);
			float r = 1f + 13f * (float) (1 - Math.pow(1 - Math.min(1, ti / 18), 3));
			rainbowRing(vc, pose, c0.add(0, 0.12, 0), Math.max(0f, r - 2.5f), r, 0.9f * f, 80);
			rainbowWall(vc, pose, c0, r, 4f * f + 0.4f, 0.45f * f, 80);
			prismBurst(vc, pose, c0.add(0, 1.5, 0), 8 * (1 - f) + 0.6, 26, seed + 11, f);
			ThorDraw.flare(vc, ps, c0.add(0, 1.5, 0), 3.4f * f + 0.3f, (float) ti * 20f, f);
			if (ti < 12) {
				Vec3 top = c0.add(0, 50, 0);
				ThorDraw.bolt(vc, pose, ThorDraw.jagged(top, c0, 22, 2.0, seed + Math.floor(ti * 0.8) * 7), 3.0f, fade(ti, 0, 12));
			}
		}
	}

	// ================================================================== drawing helpers

	/** A shockwave on the ground: rolling ring + light wall + ground lightning (+ glowing cracks when {@code cracks}). */
	private static void shockwave(VertexConsumer vc, PoseStack ps, Vec3 c, double t, float radius, double seed, boolean cracks) {
		if (t < 0 || t >= 22) {
			return;
		}
		PoseStack.Pose pose = ps.last();
		float k = (float) Math.min(1.0, t / 16.0);
		float grow = 1f - (1f - k) * (1f - k) * (1f - k);
		float f = 1f - k;
		float r = 0.6f + radius * grow;
		ThorDraw.ring(vc, pose, c, Math.max(0f, r - 1.6f), r, ThorDraw.GLOW, 0.8f * f, 64);
		ThorDraw.ring(vc, pose, c, Math.max(0f, r - 0.35f), r + 0.12f, ThorDraw.CORE, 0.85f * f, 64);
		ThorDraw.wall(vc, pose, c, r, 1.6f * f + 0.2f, ThorDraw.GLOW, 0.45f * f, 64);
		if (t < 9) {
			radialBolts(vc, pose, c.add(0, 0.05, 0), r * 0.9, 10, seed + Math.floor(t * 0.5) * 9, 0.6f, (float) (1 - t / 9));
		}
		if (t < 6) {
			ThorDraw.flare(vc, ps, c.add(0, 0.5, 0), 1.4f * (float) (1 - t / 6) + 0.2f, (float) t * 30f, (float) (1 - t / 6));
		}
		if (cracks) {
			float ca = (float) (t < 5 ? 1.0 : Math.max(0.0, 1.0 - (t - 5) / 17.0));
			for (int i = 0; i < 10; i++) {
				double ang = Math.PI * 2 * (i + ThorDraw.hash(seed + i * 1.7) * 0.5) / 10;
				double len = (radius * 0.4 + radius * 0.4 * ThorDraw.hash(seed + i * 2.9)) * Math.min(1.0, t / 3.0 + 0.3);
				Vec3[] path = ThorDraw.jagged(c.add(Math.cos(ang) * 0.5, 0.02, Math.sin(ang) * 0.5), c.add(Math.cos(ang) * len, 0.02, Math.sin(ang) * len),
						7, 0.28, seed + i * 31);
				for (int n = 0; n < path.length; n++) {
					path[n] = new Vec3(path[n].x, c.y + 0.02, path[n].z);
				}
				ThorDraw.ribbon(vc, pose, path, 0.12f, ThorDraw.GLOW, 0.55f * ca);
				ThorDraw.ribbon(vc, pose, path, 0.04f, ThorDraw.CORE, 0.85f * ca);
			}
		}
	}

	/** Short bolts bursting out of {@code c} in every direction to {@code reach}. */
	private static void radialBolts(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double reach, int count, double seed, float width, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		for (int i = 0; i < count; i++) {
			Vec3 dir = new Vec3(ThorDraw.hash(seed + i) - 0.5, (ThorDraw.hash(seed + i * 2.3) - 0.5) * 0.8, ThorDraw.hash(seed + i * 3.7) - 0.5);
			if (dir.lengthSqr() < 1e-4) {
				continue;
			}
			Vec3 end = c.add(dir.normalize().scale(reach * (0.6 + 0.4 * ThorDraw.hash(seed + i * 5.1))));
			ThorDraw.bolt(vc, pose, ThorDraw.jagged(c, end, 5, reach * 0.12, seed + i * 11), width, alpha);
		}
	}

	/** An annulus facing {@code normal} (a punch's shock disc). */
	private static void ringFacing(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, Vec3 normal, float r0, float r1, int rgb, float alpha,
			int seg) {
		if (alpha <= 0.003f || r1 <= r0) {
			return;
		}
		Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : normal.normalize();
		Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = n.cross(ref).normalize();
		Vec3 w = n.cross(u).normalize();
		float mid = (r0 + r1) * 0.5f;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0)));
			Vec3 d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
			quad(vc, pose, c.add(d0.scale(r0)), c.add(d1.scale(r0)), c.add(d1.scale(mid)), c.add(d0.scale(mid)), rgb, 0f, alpha);
			quad(vc, pose, c.add(d0.scale(mid)), c.add(d1.scale(mid)), c.add(d1.scale(r1)), c.add(d0.scale(r1)), rgb, alpha, 0f);
		}
	}

	/** A ring of the Bifrost's seven colours, red outside to violet in. */
	static void rainbowRing(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, float inner, float outer, float alpha, int seg) {
		if (alpha <= 0.01f || outer <= inner) {
			return;
		}
		float step = (outer - inner) / RAINBOW.length;
		for (int i = 0; i < RAINBOW.length; i++) {
			float r1 = outer - step * i;
			ThorDraw.ring(vc, pose, c, Math.max(0f, r1 - step * 1.6f), r1, RAINBOW[i], alpha * 0.8f, seg);
		}
	}

	/** A standing band of rainbow light on a circle. */
	private static void rainbowWall(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, float r, float h, float alpha, int seg) {
		for (int i = 0; i < RAINBOW.length; i++) {
			ThorDraw.wall(vc, pose, c.add(0, i * h * 0.04, 0), r - i * 0.08f, h * (1f - i * 0.06f), RAINBOW[i], alpha * 0.6f, seg);
		}
	}

	/** A rainbow streak from {@code a} to {@code b}: seven bands twisting round a white core. */
	static void rainbowStreak(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float width, float alpha, double now) {
		rainbowBeam(vc, pose, a, b, width, alpha, now);
	}

	/** The rainbow beam: seven coloured strands spiralling round a white-hot core, in a soft white glow. */
	static void rainbowBeam(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float width, float alpha, double now) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1e-3 || alpha <= 0.01f) {
			return;
		}
		Vec3 dir = d.scale(1 / len);
		Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = dir.cross(ref).normalize();
		Vec3 w = dir.cross(u).normalize();
		int n = Math.max(4, (int) (len * 3));
		for (int c = 0; c < RAINBOW.length; c++) {
			Vec3[] strand = new Vec3[n + 1];
			double phase = c * Math.PI * 2 / RAINBOW.length - now * 0.35;
			for (int i = 0; i <= n; i++) {
				double k = i / (double) n;
				double ang = phase + k * len * 0.9;
				double r = width * 0.75;
				strand[i] = a.add(d.scale(k)).add(u.scale(Math.cos(ang) * r)).add(w.scale(Math.sin(ang) * r));
			}
			ThorDraw.ribbon(vc, pose, strand, width * 0.32f, RAINBOW[c], 0.75f * alpha);
		}
		Vec3[] line = { a, a.lerp(b, 0.5), b };
		ThorDraw.ribbon(vc, pose, line, width * 1.6f, 0xFFFFFF, 0.18f * alpha);
		ThorDraw.ribbon(vc, pose, line, width * 0.45f, 0xFFFFFF, 0.95f * alpha);
	}

	/** Prismatic sparks: short rainbow shards flying out of {@code c}. */
	static void prismBurst(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double reach, int count, double seed, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		for (int i = 0; i < count; i++) {
			Vec3 dir = new Vec3(ThorDraw.hash(seed + i * 1.1) - 0.5, ThorDraw.hash(seed + i * 2.7) - 0.5, ThorDraw.hash(seed + i * 4.3) - 0.5);
			if (dir.lengthSqr() < 1e-4) {
				continue;
			}
			Vec3 s = c.add(dir.normalize().scale(reach * 0.3 * ThorDraw.hash(seed + i * 6.1)));
			Vec3 e = c.add(dir.normalize().scale(reach * (0.5 + 0.5 * ThorDraw.hash(seed + i * 7.9))));
			ThorDraw.ribbon(vc, pose, new Vec3[] { s, e }, 0.06f, RAINBOW[i % RAINBOW.length], 0.9f * alpha);
		}
	}

	/** A sweep of light through {@code halfDeg} either side of his facing at {@code r}: rainbow bands + a lightning edge. */
	private static void arcSweep(VertexConsumer vc, PoseStack.Pose pose, Player p, Vec3 feet, float partial, float r, float halfDeg, float alpha,
			double now, boolean rainbow) {
		if (alpha <= 0.01f) {
			return;
		}
		Vec3 fwd = forward(p, partial);
		Vec3 side = right(p, partial);
		int n = 18;
		double half = Math.toRadians(halfDeg);
		for (int band = 0; band < (rainbow ? RAINBOW.length : 1); band++) {
			Vec3[] arc = new Vec3[n + 1];
			double rr = r * (0.55 + 0.45 * band / (double) RAINBOW.length);
			for (int i = 0; i <= n; i++) {
				double a = -half + 2 * half * i / n;
				arc[i] = feet.add(0, 1.0 + band * 0.03, 0).add(fwd.scale(Math.cos(a) * rr)).add(side.scale(Math.sin(a) * rr));
			}
			ThorDraw.ribbon(vc, pose, arc, 0.12f, rainbow ? RAINBOW[band] : ThorDraw.GLOW, 0.7f * alpha);
		}
		Vec3[] edge = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			double a = -half + 2 * half * i / n;
			edge[i] = feet.add(0, 1.0, 0).add(fwd.scale(Math.cos(a) * r)).add(side.scale(Math.sin(a) * r));
		}
		ThorDraw.ribbon(vc, pose, edge, 0.05f, ThorDraw.CORE, 0.9f * alpha);
	}

	private static Vec3 bezier(Vec3 a, Vec3 c, Vec3 b, double k) {
		return a.scale((1 - k) * (1 - k)).add(c.scale(2 * (1 - k) * k)).add(b.scale(k * k));
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int rgb, float a0, float a1) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		int ia0 = Math.round(Mth.clamp(a0, 0f, 1f) * 255), ia1 = Math.round(Mth.clamp(a1, 0f, 1f) * 255);
		vert(vc, pose, p0, r, g, b, ia0);
		vert(vc, pose, p1, r, g, b, ia0);
		vert(vc, pose, p2, r, g, b, ia1);
		vert(vc, pose, p3, r, g, b, ia1);
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int r, int g, int b, int a) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 1f, 0f);
	}
}
