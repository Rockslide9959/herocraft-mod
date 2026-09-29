package com.projecthero.mod.client.moonknight;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.ability.MoonKnightSkull;
import com.projecthero.mod.network.MoonKnightKhonshuFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Moon Knight Phase 6, the client half of Khonshu's visuals ({@link MoonKnightKhonshuFxPayload}):
 * <ul>
 *   <li><b>Moonbeam</b> -- a beacon-style column of pale moonlight standing on the impact point, narrowing to nothing
 *       over 1.5 s (drawn for every nearby player);</li>
 *   <li><b>Eye of Khonshu</b> -- Khonshu's skull ({@link MoonKnightSkull}) traced across the sky point by point over
 *       two seconds in glowing motes, its eye sockets burning cold white, then shimmering for three more;</li>
 *   <li><b>Khonshu's Resurrection</b> -- a bright white screen flash for the player who was saved.</li>
 * </ul>
 */
public final class MoonKnightKhonshuFxClient {
	private static final int BEAM_COLOR = 0xFFE4ECFF;
	private static final DustParticleOptions EYE_GLOW = new DustParticleOptions(new org.joml.Vector3f(0.78f, 0.9f, 1.0f), 3.0f);
	private static final DustParticleOptions SKULL_DUST = new DustParticleOptions(new org.joml.Vector3f(0.95f, 0.96f, 1.0f), 1.6f);

	private record Beam(Vec3 at, long start, int life) {
	}

	private static final class Skull {
		final Vec3 centre;
		final Vec3 right;
		final long start;
		int emitted;

		Skull(Vec3 centre, float yawDeg, long start) {
			this.centre = centre;
			double yaw = Math.toRadians(yawDeg);
			// the player's right-hand side when facing this yaw (Minecraft: yaw 0 looks toward +Z, right is -X)
			this.right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
			this.start = start;
		}

		Vec3 at(double x, double y) {
			double s = MoonKnightConfig.EYE_SKULL_SCALE;
			return centre.add(right.scale(x * s)).add(0.0, y * s, 0.0);
		}
	}

	private static final List<Beam> BEAMS = new ArrayList<>();
	private static final List<Skull> SKULLS = new ArrayList<>();
	private static ClientLevel fxLevel;
	private static long flashStart = -1L;
	private static int flashLength;

	private MoonKnightKhonshuFxClient() {
	}

	public static void receive(MoonKnightKhonshuFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (fxLevel != mc.level) {
			BEAMS.clear();
			SKULLS.clear();
			fxLevel = mc.level;
		}
		long now = mc.level.getGameTime();
		switch (p.kind()) {
			case MOONBEAM -> BEAMS.add(new Beam(new Vec3(p.x(), p.y(), p.z()), now, Math.max(5, p.arg())));
			case EYE_SKULL -> SKULLS.add(new Skull(new Vec3(p.x(), p.y(), p.z()), p.arg(), now));
			case RESURRECTION_FLASH -> {
				flashStart = now;
				flashLength = Math.max(4, p.arg());
			}
		}
	}

	// ---------------------------------------------------------------- skull tracing (client tick)

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || level != fxLevel) {
			// a world / dimension change drops anything still in the air
			SKULLS.clear();
			BEAMS.clear();
			return;
		}
		if (SKULLS.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		RandomSource rand = level.getRandom();
		List<double[]> points = MoonKnightSkull.points();
		int draw = MoonKnightConfig.EYE_SKULL_DRAW_TICKS;
		int linger = MoonKnightConfig.EYE_SKULL_LINGER_TICKS;
		Iterator<Skull> it = SKULLS.iterator();
		while (it.hasNext()) {
			Skull s = it.next();
			long e = now - s.start;
			if (e > draw + linger) {
				it.remove();
				continue;
			}
			// trace: emit the next stretch of the outline
			int target = (int) Math.min(points.size(), Math.ceil(points.size() * (e + 1) / (double) draw));
			for (; s.emitted < target; s.emitted++) {
				double[] pt = points.get(s.emitted);
				Vec3 w = s.at(pt[0], pt[1]);
				level.addParticle(ParticleTypes.END_ROD, true, w.x, w.y, w.z, 0.0, 0.0, 0.0);
				if ((s.emitted & 1) == 0) {
					level.addParticle(SKULL_DUST, true, w.x, w.y, w.z, 0.0, 0.0, 0.0);
				}
			}
			// once drawn, keep it shimmering so it holds for the linger time
			if (e >= draw && e % 6 == 0) {
				for (double[] pt : points) {
					if (rand.nextFloat() < 0.3f) {
						Vec3 w = s.at(pt[0], pt[1]);
						level.addParticle(rand.nextBoolean() ? ParticleTypes.END_ROD : SKULL_DUST, true, w.x, w.y, w.z,
								0.0, -0.005, 0.0);
					}
				}
			}
			// the burning eyes, from the moment the sockets are drawn
			if (s.emitted > points.size() / 2 && e % 2 == 0) {
				for (double[] eye : MoonKnightSkull.EYES) {
					Vec3 w = s.at(eye[0], eye[1]);
					level.addParticle(EYE_GLOW, true, w.x + (rand.nextDouble() - 0.5) * 0.8, w.y + (rand.nextDouble() - 0.5) * 0.8,
							w.z + (rand.nextDouble() - 0.5) * 0.8, 0.0, 0.0, 0.0);
					if (rand.nextInt(3) == 0) {
						level.addParticle(ParticleTypes.END_ROD, true, w.x, w.y, w.z, (rand.nextDouble() - 0.5) * 0.05,
								-0.02, (rand.nextDouble() - 0.5) * 0.05);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- moonbeams (world render)

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (BEAMS.isEmpty() || mc.level == null || mc.level != fxLevel) {
			return;
		}
		MultiBufferSource consumers = context.consumers();
		PoseStack pose = context.matrixStack();
		if (consumers == null || pose == null) {
			return;
		}
		Vec3 cam = context.camera().getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long gameTime = mc.level.getGameTime();
		double now = gameTime + partial;
		Iterator<Beam> it = BEAMS.iterator();
		while (it.hasNext()) {
			Beam b = it.next();
			double t = (now - b.start()) / b.life();
			if (t >= 1.0) {
				it.remove();
				continue;
			}
			// a sudden full-width column that narrows away
			float grow = (float) Math.min(1.0, (now - b.start()) / 2.0);
			float width = (float) Math.pow(1.0 - t, 0.7) * grow;
			pose.pushPose();
			// renderBeaconBeam centres itself on (0.5, 0.5) of the block it is given
			pose.translate(b.at().x - 0.5 - cam.x, b.at().y - cam.y, b.at().z - 0.5 - cam.z);
			BeaconRenderer.renderBeaconBeam(pose, consumers, BeaconRenderer.BEAM_LOCATION, partial, 1.0f, gameTime, 0, 256,
					BEAM_COLOR, 0.55f * width, 0.85f * width);
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- resurrection flash (HUD)

	public static void renderFlash(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (flashStart < 0L || mc.level == null) {
			return;
		}
		double t = (mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false) - flashStart) / flashLength;
		if (t >= 1.0 || t < 0.0) {
			flashStart = -1L;
			return;
		}
		int alpha = (int) Math.round(235.0 * Math.pow(1.0 - t, 1.6));
		if (alpha > 3) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0xF4F6FF);
		}
	}

	public static void clear() {
		BEAMS.clear();
		SKULLS.clear();
		fxLevel = null;
		flashStart = -1L;
	}
}
