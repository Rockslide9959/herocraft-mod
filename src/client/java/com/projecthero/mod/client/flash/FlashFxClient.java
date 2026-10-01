package com.projecthero.mod.client.flash;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.flash.FlashFx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.11: the Flash Ring's light show, for every viewer (all of it keyed off the synced {@link FlashFx} clock).
 *
 * <p><b>Suit-up</b>: a white flash at the ring as the catch pops; the compressed suit -- a knot of red and gold that
 * swells as it flies -- leaps out of the ring and up over the head; then, while the suit spreads over the body, a
 * speed-force vortex: two red-and-gold spirals racing up the body and yellow lightning cracking from the ring to points
 * all round it, re-forking every other tick; at the end a ring of lightning bursts outward along the ground.
 *
 * <p><b>Suit-down</b>: the same lightning drawn inward -- arcs from round the body converge on the ring and the spirals
 * fall and tighten into the fist, then a last spark as the ring snaps shut.
 */
public final class FlashFxClient {
	private static final double MAX_DISTANCE_SQ = 96.0 * 96.0;
	private static final int BOLT_HALO = 0xFF7A00;
	private static final int BOLT_GLOW = 0xFFD43A;
	private static final int BOLT_CORE = 0xFFFDE8;
	private static final Vector3f RED = new Vector3f(0.86f, 0.06f, 0.05f);
	private static final Vector3f GOLD = new Vector3f(1.0f, 0.78f, 0.15f);

	private FlashFxClient() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(FlashFxClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				particles(client.level);
			}
		});
	}

	// ---------------------------------------------------------------- where things are

	private static Vec3 feet(Player p, float partial) {
		return new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()), Mth.lerp(partial, p.zo, p.getZ()));
	}

	/** Roughly where the ring is in the world: the right fist, raised in front of the face early in the suit-up. */
	static Vec3 ringPos(Player p, float partial) {
		float yaw = (Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) + FlashSuitReveal.spin(p, partial)) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		float t = FlashSuitReveal.age(p, partial);
		boolean raised = t >= 0f && (FlashSuitReveal.dir(p) == FlashFx.DOWN || t < FlashSuitReveal.EMERGE + 2);
		float s = p.getScale();
		Vec3 offset = raised
				? right.scale(0.22).add(forward.scale(0.62)).add(0, 1.38, 0)
				: right.scale(0.42).add(forward.scale(0.05)).add(0, 0.72, 0);
		return feet(p, partial).add(offset.scale(s));
	}

	// ---------------------------------------------------------------- lightning

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack.Pose pose = context.matrixStack().last();
		VertexConsumer vc = null;
		for (Player p : client.level.players()) {
			float t = FlashSuitReveal.age(p, partial);
			if (t < 0f || p.distanceToSqr(cam) > MAX_DISTANCE_SQ || p.isInvisible()) {
				continue;
			}
			if (vc == null) {
				vc = ThorDraw.buffer(context.consumers());
			}
			boolean up = FlashSuitReveal.dir(p) == FlashFx.UP;
			float s = p.getScale();
			Vec3 base = feet(p, partial).subtract(cam);
			Vec3 ring = ringPos(p, partial).subtract(cam);
			double seed = p.getId() * 3.17 + Math.floor((p.level().getGameTime() + partial) / 2.0) * 1.91;
			float fade;
			if (up) {
				if (t < FlashSuitReveal.EMERGE - 1 || t > FlashSuitReveal.REVEAL_END + 1) {
					if (t > FlashSuitReveal.REVEAL_END && t < FlashSuitReveal.REVEAL_END + 6) {
						groundBurst(vc, pose, base, (t - FlashSuitReveal.REVEAL_END) / 6f, s, seed);
					}
					continue;
				}
				fade = 1f;
			} else {
				fade = 1f - Mth.clamp((t - FlashSuitReveal.DOWN_END) / 2f, 0f, 1f);
			}
			float progress = FlashSuitReveal.progress(p, partial);
			// bolts from the ring to points on a cylinder round the body, reaching as far as the suit has
			int bolts = 4;
			for (int i = 0; i < bolts; i++) {
				double a = ThorDraw.hash(seed + i * 4.1) * Math.PI * 2.0;
				double h = (0.15 + ThorDraw.hash(seed + i * 9.3) * 1.75) * Math.max(0.25, up ? progress : 1.0 - progress * 0.3);
				double r = 0.45 + ThorDraw.hash(seed + i * 2.7) * 0.35;
				Vec3 end = base.add(Math.cos(a) * r * s, h * s, Math.sin(a) * r * s);
				Vec3[] path = up ? ThorDraw.jagged(ring, end, 7, 0.16 * s, seed + i) : ThorDraw.jagged(end, ring, 7, 0.16 * s, seed + i);
				bolt(vc, pose, path, s, fade * (0.65f + 0.35f * (float) ThorDraw.hash(seed + i * 5.5)));
			}
		}
	}

	private static void bolt(VertexConsumer vc, PoseStack.Pose pose, Vec3[] path, float width, float alpha) {
		ThorDraw.ribbon(vc, pose, path, 0.16f * width, BOLT_HALO, 0.22f * alpha);
		ThorDraw.ribbon(vc, pose, path, 0.07f * width, BOLT_GLOW, 0.6f * alpha);
		ThorDraw.ribbon(vc, pose, path, 0.025f * width, BOLT_CORE, 0.95f * alpha);
	}

	/** The crack at the end of the suit-up: jagged lightning racing outward along the ground. */
	private static void groundBurst(VertexConsumer vc, PoseStack.Pose pose, Vec3 base, float k, float s, double seed) {
		float reach = (0.4f + 2.6f * (1f - (1f - k) * (1f - k))) * s;
		for (int i = 0; i < 7; i++) {
			double a = i * Math.PI * 2.0 / 7.0 + ThorDraw.hash(seed + i) * 0.5;
			Vec3 from = base.add(Math.cos(a) * 0.3 * s, 0.05, Math.sin(a) * 0.3 * s);
			Vec3 to = base.add(Math.cos(a) * reach, 0.05, Math.sin(a) * reach);
			bolt(vc, pose, ThorDraw.jagged(from, to, 6, 0.18 * s, seed + i * 3.3), s, 1f - k);
		}
	}

	// ---------------------------------------------------------------- particles

	private static void particles(ClientLevel level) {
		Minecraft client = Minecraft.getInstance();
		for (Player p : level.players()) {
			float t = FlashSuitReveal.age(p, 0f);
			if (t < 0f || p.isInvisible() || (client.player != null && p.distanceToSqr(client.player) > MAX_DISTANCE_SQ)) {
				continue;
			}
			int tick = (int) t;
			float s = p.getScale();
			Vec3 ring = ringPos(p, 0f);
			Vec3 feet = feet(p, 0f);
			var rand = level.random;
			if (FlashSuitReveal.dir(p) == FlashFx.UP) {
				if (tick == 0) {
					level.addParticle(ParticleTypes.FLASH, ring.x, ring.y, ring.z, 0, 0, 0);
					for (int i = 0; i < 14; i++) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, ring.x, ring.y, ring.z,
								rand.nextGaussian() * 0.25, rand.nextGaussian() * 0.25, rand.nextGaussian() * 0.25);
					}
				}
				if (tick < FlashSuitReveal.EMERGE + 1) {
					// the compressed suit leaps out of the ring, up over the head, swelling as it goes
					float k = (tick + 1f) / (FlashSuitReveal.EMERGE + 1f);
					Vec3 knot = ring.lerp(feet.add(0, 2.25 * s, 0), k);
					float size = 0.4f + 1.1f * k; // small: a dust particle lives longer the bigger it is
					for (int i = 0; i < 4; i++) {
						Vector3f c = i % 3 == 0 ? GOLD : RED;
						level.addParticle(new DustParticleOptions(c, size), knot.x + rand.nextGaussian() * 0.08 * k,
								knot.y + rand.nextGaussian() * 0.08 * k, knot.z + rand.nextGaussian() * 0.08 * k, 0, 0, 0);
					}
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, knot.x, knot.y, knot.z, 0, 0.05, 0);
				} else if (tick <= FlashSuitReveal.REVEAL_END) {
					// the vortex: two spirals racing up the body as the suit wraps on
					float k = FlashSuitReveal.progress(p, 0f);
					for (int arm = 0; arm < 2; arm++) {
						for (int j = 0; j < 3; j++) {
							double a = tick * 1.35 + arm * Math.PI + j * 0.35;
							double y = (k * 2.0 - j * 0.12) * s;
							double r = (0.75 - k * 0.2) * s;
							level.addParticle(new DustParticleOptions(arm == 0 ? GOLD : RED, 1.1f),
									feet.x + Math.cos(a) * r, feet.y + Math.max(0.05, y), feet.z + Math.sin(a) * r, 0, 0, 0);
						}
					}
					if (rand.nextInt(2) == 0) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, feet.x + rand.nextGaussian() * 0.4 * s,
								feet.y + rand.nextDouble() * 1.9 * s, feet.z + rand.nextGaussian() * 0.4 * s, 0, 0, 0);
					}
				}
				if (tick == FlashSuitReveal.REVEAL_END) {
					for (int i = 0; i < 24; i++) {
						double a = i * Math.PI * 2.0 / 24.0;
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 0.1, feet.z,
								Math.cos(a) * 0.55, 0.04, Math.sin(a) * 0.55);
						level.addParticle(new DustParticleOptions(GOLD, 1.4f), feet.x + Math.cos(a) * 0.6, feet.y + 0.1,
								feet.z + Math.sin(a) * 0.6, 0, 0, 0);
					}
				}
			} else {
				if (tick <= FlashSuitReveal.DOWN_END) {
					// the spirals fall and tighten into the fist
					float k = tick / (float) FlashSuitReveal.DOWN_END;
					for (int arm = 0; arm < 2; arm++) {
						double a = -tick * 1.5 + arm * Math.PI;
						double r = (0.8 * (1 - k)) * s;
						Vec3 at = feet.add(Math.cos(a) * r, (1.9 * (1 - k)) * s, Math.sin(a) * r).lerp(ring, k * k);
						level.addParticle(new DustParticleOptions(arm == 0 ? GOLD : RED, 1.2f), at.x, at.y, at.z, 0, 0, 0);
					}
				}
				if (tick == FlashSuitReveal.DOWN_END) {
					level.addParticle(ParticleTypes.FLASH, ring.x, ring.y, ring.z, 0, 0, 0);
					for (int i = 0; i < 10; i++) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, ring.x, ring.y, ring.z,
								rand.nextGaussian() * 0.15, rand.nextGaussian() * 0.15, rand.nextGaussian() * 0.15);
					}
				}
			}
		}
	}
}
