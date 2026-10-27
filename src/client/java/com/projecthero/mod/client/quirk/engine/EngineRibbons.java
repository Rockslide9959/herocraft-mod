package com.projecthero.mod.client.quirk.engine;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.engine.Engine;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Engine revamp: the exhaust as <b>ribbons</b> out of the twelve calf nozzles, for every viewer -- a tapered glowing jet out
 * of each mouth that streams back along the path the nozzle took (recorded each client tick), so at speed the calves draw twelve
 * long blue lines behind him: blue in gear, white-hot in Recipro Turbo and the Burst, orange-red in the Redline band, and a thin
 * grey smoke ribbon when stalled. At Gear 3 flat out (and in Turbo / the Burst) a <b>speed afterimage</b> follows him: three
 * fading blue copies of his body on his recent path (the Speed Force echo look). Recorded and drawn client side only.
 */
public final class EngineRibbons {
	/** Samples kept per nozzle (one a client tick). */
	private static final int KEEP = 12;
	private static final Map<Integer, Trail> TRAILS = new HashMap<>();

	private static final class Trail {
		final ArrayDeque<Vec3[]> samples = new ArrayDeque<>();
		final ArrayDeque<Ghost> ghosts = new ArrayDeque<>();
		long lastTick = -1L;
	}

	private record Ghost(Vec3 at, float bodyYaw, float limbPos, float limbSpeed, float pitch, float lean, long tick) {
	}

	private EngineRibbons() {
	}

	static void reset() {
		TRAILS.clear();
	}

	/** How strongly the engines burn for the ribbons (0 = no jet). */
	private static float drive(Player p) {
		float d = Engine.drive(p);
		if (PlusUltra.clientHold(p) >= 0f) {
			d = Math.max(d, 2f + 4f * PlusUltra.clientHold(p)); // the rev of the Burst's hold: white-hot
		}
		return d;
	}

	/** END_CLIENT_TICK: record every Engine holder's nozzle mouths (and the afterimage poses). */
	static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			if (!Engine.hasEngines(p) || p.isSpectator() || p.isInvisible()) {
				TRAILS.remove(p.getId());
				continue;
			}
			Trail t = TRAILS.computeIfAbsent(p.getId(), k -> new Trail());
			if (t.lastTick == now) {
				continue;
			}
			t.lastTick = now;
			EngineCalves.Nozzles n = EngineCalves.nozzles(p);
			Vec3[] s = new Vec3[2 * EngineCalves.PER_LEG];
			for (int k = 0; k < s.length; k++) {
				s[k] = EngineFx.mouth(p, n, k);
			}
			t.samples.addFirst(s);
			while (t.samples.size() > KEEP) {
				t.samples.removeLast();
			}
			boolean fast = Engine.turbo(p) || Engine.burst(p) || Engine.gear(p) >= 3 && Engine.momentum(p) > 0.85f;
			double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
			if (fast && speed > 0.4 && now % 2 == 0) {
				t.ghosts.addFirst(new Ghost(p.position(), p.yBodyRot, p.walkAnimation.position(), Math.min(1f, p.walkAnimation.speed()), p.getXRot(),
						0.35f, now));
			}
			t.ghosts.removeIf(g -> now - g.tick() > 8);
		}
		TRAILS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
	}

	/** AFTER_ENTITIES (the nozzles were captured as the bodies were drawn this frame). */
	static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null || TRAILS.isEmpty()) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Trail> e = it.next();
			if (!(mc.level.getEntity(e.getKey()) instanceof AbstractClientPlayer p) || p.distanceToSqr(cam) > 96 * 96) {
				continue;
			}
			boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
			try {
				ribbons(ctx.matrixStack(), ctx, p, e.getValue(), cam, partial);
				if (!firstPerson) {
					ghosts(ctx.matrixStack(), ctx, p, e.getValue(), cam, partial);
				}
			} catch (RuntimeException ex) {
				it.remove();
			}
		}
	}

	private static void ribbons(PoseStack pose, WorldRenderContext ctx, AbstractClientPlayer p, Trail t, Vec3 cam, float partial) {
		boolean stalled = Engine.stalled(p);
		float drive = drive(p);
		if (!stalled && drive <= 0f || t.samples.isEmpty()) {
			return;
		}
		EngineCalves.Nozzles live = EngineCalves.nozzles(p);
		float heat = Quirks.clientMeter(p);
		boolean white = Engine.turbo(p) || Engine.burst(p) || PlusUltra.clientHold(p) >= 0f;
		int core, edge;
		if (stalled) {
			core = 0x6A6A6A;
			edge = 0x2A2A2A;
		} else if (white) {
			core = 0xFFFFFF;
			edge = 0x9FD0FF;
		} else if (heat >= 80f) {
			core = 0xFFE0B0;
			edge = 0xFF5A2A;
		} else {
			core = 0xD8F0FF;
			edge = 0x3A7BFF;
		}
		double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
		// the jet: wider and longer the harder the engines work; the trail: the recorded path, only when he is moving
		float w0 = stalled ? 0.035f : 0.03f + 0.018f * Math.min(6f, drive);
		float jet = stalled ? 0.12f : 0.1f + 0.06f * Math.min(6f, drive);
		int trailLen = stalled || speed < 0.08 ? 1 : Math.min(KEEP, 3 + (int) (speed * 6));
		VertexConsumer vc = QuirkFxDraw.glow(ctx.consumers(), QuirkFxDraw.RIBBON);
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose last = pose.last();
		Vector3f camV = new Vector3f((float) cam.x, (float) cam.y, (float) cam.z);
		float flicker = 0.85f + 0.15f * Mth.sin((p.tickCount + partial) * 3.7f);
		for (int k = 0; k < 2 * EngineCalves.PER_LEG; k++) {
			Vec3 mouth = EngineFx.mouth(p, live, k);
			Vec3 blow = EngineFx.blow(p, live, k);
			java.util.List<Vec3> pts = new java.util.ArrayList<>();
			pts.add(mouth);
			pts.add(mouth.add(blow.scale(jet * flicker)));
			int i = 0;
			for (Vec3[] s : t.samples) {
				if (i++ == 0) {
					continue; // the newest sample is this tick's mouth
				}
				if (i > trailLen) {
					break;
				}
				pts.add(s[k]);
			}
			for (int j = 0; j < pts.size() - 1; j++) {
				float f0 = 1f - j / (float) (pts.size() - 1), f1 = 1f - (j + 1) / (float) (pts.size() - 1);
				Vector3f a = new Vector3f((float) pts.get(j).x, (float) pts.get(j).y, (float) pts.get(j).z);
				Vector3f b = new Vector3f((float) pts.get(j + 1).x, (float) pts.get(j + 1).y, (float) pts.get(j + 1).z);
				Vector3f tan = new Vector3f(b).sub(a);
				if (tan.lengthSquared() < 1.0e-6f) {
					continue;
				}
				Vector3f side = tan.cross(new Vector3f(camV).sub(a), new Vector3f());
				if (side.lengthSquared() < 1.0e-8f) {
					continue;
				}
				side.normalize();
				float wa = w0 * (j == 0 ? 0.55f : f0), wb = w0 * f1;
				int ca = QuirkFxDraw.lerpRgb(1f - f0, core, edge), cb = QuirkFxDraw.lerpRgb(1f - f1, core, edge);
				float aa = (stalled ? 0.35f : 0.9f) * Math.min(1f, f0 * 1.4f), ab = (stalled ? 0.35f : 0.9f) * Math.min(1f, f1 * 1.4f);
				float[][] q = { { a.x - side.x * wa, a.y - side.y * wa, a.z - side.z * wa, f0, 0 }, { b.x - side.x * wb, b.y - side.y * wb, b.z - side.z * wb, f1, 0 },
						{ b.x + side.x * wb, b.y + side.y * wb, b.z + side.z * wb, f1, 1 }, { a.x + side.x * wa, a.y + side.y * wa, a.z + side.z * wa, f0, 1 } };
				QuirkFxDraw.gquad(vc, last, q, new int[] { ca, cb, cb, ca }, new float[] { aa, ab, ab, aa });
			}
		}
		pose.popPose();
	}

	/** The speed afterimage: fading blue copies of his body along his recent path. */
	private static void ghosts(PoseStack pose, WorldRenderContext ctx, AbstractClientPlayer p, Trail t, Vec3 cam, float partial) {
		if (t.ghosts.isEmpty() || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer renderer)) {
			return;
		}
		PlayerModel<AbstractClientPlayer> model = renderer.getModel();
		long now = p.level().getGameTime();
		VertexConsumer vc = ctx.consumers().getBuffer(RenderType.entityTranslucent(p.getSkin().texture()));
		int overlay = LivingEntityRenderer.getOverlayCoords(p, 0f);
		int light = 0xF000F0;
		boolean white = Engine.turbo(p) || Engine.burst(p);
		for (Ghost g : t.ghosts) {
			float age = now - g.tick() + partial;
			float a = Mth.clamp(0.42f * (1f - age / 8f), 0f, 1f);
			if (a <= 0.02f || g.at().distanceToSqr(p.position()) < 0.5) {
				continue;
			}
			model.setAllVisible(true);
			model.crouching = false;
			model.riding = false;
			model.attackTime = 0f;
			model.prepareMobModel(p, g.limbPos(), g.limbSpeed(), partial);
			model.setupAnim(p, g.limbPos(), g.limbSpeed(), p.tickCount + partial, 0f, g.pitch());
			pose.pushPose();
			pose.translate(g.at().x - cam.x, g.at().y - cam.y, g.at().z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(180f - g.bodyYaw()));
			pose.mulPose(Axis.XP.rotation(g.lean()));
			pose.scale(-1f, -1f, 1f);
			float s = 0.9375f * p.getScale();
			pose.scale(s, s, s);
			pose.translate(0f, -1.501f, 0f);
			int argb = ((int) (a * 255) << 24) | (white ? 0xD8F0FF : 0x6FB0FF);
			model.renderToBuffer(pose, vc, light, overlay, argb);
			pose.popPose();
		}
	}
}
