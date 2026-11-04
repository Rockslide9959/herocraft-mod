package com.projecthero.mod.client.galactus;

import java.util.List;

import com.projecthero.mod.client.quirk.QuirkCameraFx;
import com.projecthero.mod.galactus.cinematic.CinematicPayload;
import com.projecthero.mod.galactus.cinematic.GalactusScenes;
import com.projecthero.mod.galactus.cinematic.GalactusScenes.Scene;
import com.projecthero.mod.galactus.orbit.DevourerEntity;
import com.projecthero.mod.galactus.orbit.GalactusRig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: the client half of the cutscenes -- the camera paths of every scene
 * ({@link #camera}, used by {@code GalactusCinematicCameraMixin}), letterbox bars, fades (black / white / violet),
 * subtitles, the warp streaks of the ascension, the camera shakes and FOV punches on cue, and "hold Sneak to skip" (your
 * own view only; the server holds you for the scene's length either way). Presentation only: your real look and aim are
 * never touched.
 */
public final class GalactusCinematicClient {
	private static Scene scene;
	private static Vec3 anchor = Vec3.ZERO;
	private static float yaw;
	private static long start;
	private static int bossId = -1;
	private static boolean skipped;
	private static int sneakHeld;
	private static int lastCue = -1;
	/** True while the current shot is first-person (no detached body). */
	private static boolean pov;

	private record Line(int from, int to, String key, ChatFormatting colour) {
	}

	private GalactusCinematicClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(CinematicPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> accept(p)));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> scene = null);
		ClientTickEvents.END_CLIENT_TICK.register(GalactusCinematicClient::tick);
		HudRenderCallback.EVENT.register(GalactusCinematicClient::hud);
		net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(GalactusCinematicClient::world);
	}

	private static final net.minecraft.resources.ResourceLocation GLOW = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/particle/glow.png");

	/** C1: the Herald as a falling star -- a bright flare that grows as he nears, and a long silver streak behind him. */
	static void world(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
		if (scene != Scene.HERALD || ctx.consumers() == null) {
			return;
		}
		float t = time(ctx.tickCounter().getGameTimeDeltaPartialTick(false));
		if (t < 6 || t > GalactusScenes.HERALD_LAND + 16) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		var ps = ctx.matrixStack();
		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		var vc = ctx.consumers().getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(GLOW));
		float fade = t > GalactusScenes.HERALD_LAND ? 1f - (t - GalactusScenes.HERALD_LAND) / 16f : Mth.clamp((t - 6) / 10f, 0f, 1f);
		Vec3 head = GalactusScenes.heraldPos(anchor, yaw, t).add(0, 1.0, 0);
		double dist = head.distanceTo(cam);
		float size = (float) Math.max(0.9, dist * 0.03);
		flare(ps, vc, ctx.camera(), head, size * 2.6f, 200, 215, 255, (int) (140 * fade));
		flare(ps, vc, ctx.camera(), head, size, 255, 255, 255, (int) (255 * fade));
		// the streak: the last 18 ticks of the path
		if (t <= GalactusScenes.HERALD_LAND + 4) {
			Vec3 prev = head;
			for (int i = 1; i <= 18; i++) {
				Vec3 p = GalactusScenes.heraldPos(anchor, yaw, Math.max(0, t - i * 1.2f)).add(0, 1.0, 0);
				float w0 = size * 0.55f * (1f - (i - 1) / 18f), w1 = size * 0.55f * (1f - i / 18f);
				int a0 = (int) (200 * fade * (1f - (i - 1) / 18f)), a1 = (int) (200 * fade * (1f - i / 18f));
				ribbon(ps, vc, cam, prev, p, w0, w1, a0, a1);
				prev = p;
			}
		}
		ps.popPose();
	}

	private static void flare(com.mojang.blaze3d.vertex.PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer vc, net.minecraft.client.Camera cam, Vec3 at,
			float s, int r, int g, int b, int a) {
		ps.pushPose();
		ps.translate(at.x, at.y, at.z);
		ps.mulPose(cam.rotation());
		var m = ps.last().pose();
		var n = ps.last();
		int light = 0xF000F0;
		int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
		vc.addVertex(m, -s, -s, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(ov).setLight(light).setNormal(n, 0, 0, 1);
		vc.addVertex(m, s, -s, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(ov).setLight(light).setNormal(n, 0, 0, 1);
		vc.addVertex(m, s, s, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(ov).setLight(light).setNormal(n, 0, 0, 1);
		vc.addVertex(m, -s, s, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(ov).setLight(light).setNormal(n, 0, 0, 1);
		ps.popPose();
	}

	private static void ribbon(com.mojang.blaze3d.vertex.PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer vc, Vec3 cam, Vec3 a, Vec3 b, float wa, float wb,
			int aa, int ab) {
		Vec3 dir = b.subtract(a);
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}
		Vec3 side = dir.cross(cam.subtract(a)).normalize();
		var m = ps.last().pose();
		var n = ps.last();
		int light = 0xF000F0;
		int ov = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
		vc.addVertex(m, (float) (a.x - side.x * wa), (float) (a.y - side.y * wa), (float) (a.z - side.z * wa)).setColor(225, 235, 255, aa).setUv(0.5f, 0f)
				.setOverlay(ov).setLight(light).setNormal(n, 0, 1, 0);
		vc.addVertex(m, (float) (a.x + side.x * wa), (float) (a.y + side.y * wa), (float) (a.z + side.z * wa)).setColor(225, 235, 255, aa).setUv(0.5f, 1f)
				.setOverlay(ov).setLight(light).setNormal(n, 0, 1, 0);
		vc.addVertex(m, (float) (b.x + side.x * wb), (float) (b.y + side.y * wb), (float) (b.z + side.z * wb)).setColor(225, 235, 255, ab).setUv(0.5f, 1f)
				.setOverlay(ov).setLight(light).setNormal(n, 0, 1, 0);
		vc.addVertex(m, (float) (b.x - side.x * wb), (float) (b.y - side.y * wb), (float) (b.z - side.z * wb)).setColor(225, 235, 255, ab).setUv(0.5f, 0f)
				.setOverlay(ov).setLight(light).setNormal(n, 0, 1, 0);
	}

	static void accept(CinematicPayload p) {
		Minecraft mc = Minecraft.getInstance();
		Scene s = Scene.byId(p.scene());
		if (s == null || mc.level == null) {
			scene = null;
			return;
		}
		scene = s;
		anchor = new Vec3(p.x(), p.y(), p.z());
		yaw = p.yaw();
		start = mc.level.getGameTime() - p.elapsed();
		bossId = p.bossId();
		skipped = false;
		sneakHeld = 0;
		lastCue = p.elapsed() - 1;
	}

	public static boolean active() {
		return scene != null && !skipped;
	}

	public static boolean pov() {
		return pov;
	}

	/** True while a scene that needs a clear horizon plays (the Herald's dive, the head on the horizon). */
	public static boolean clearFog() {
		return scene == Scene.HERALD || scene == Scene.DEVOURER;
	}

	private static float time(float pt) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : (mc.level.getGameTime() - start) + pt;
	}

	static void tick(Minecraft mc) {
		if (scene == null) {
			return;
		}
		if (mc.level == null) {
			scene = null;
			return;
		}
		float t = time(0);
		if (t >= scene.length) {
			scene = null;
			return;
		}
		if (!skipped) {
			if (mc.options.keyShift.isDown() && t > 10) {
				if (++sneakHeld >= 14) {
					skipped = true;
				}
			} else {
				sneakHeld = 0;
			}
		}
		int now = (int) t;
		for (int c = lastCue + 1; c <= now; c++) {
			cue(c);
		}
		lastCue = now;
	}

	/** Shakes and punches on the scene's beats (skipped views still feel them). */
	private static void cue(int t) {
		switch (scene) {
			case HERALD -> {
				if (t == GalactusScenes.HERALD_BOOM) {
					QuirkCameraFx.shake(5f, 16);
					QuirkCameraFx.fovPunch(0.12f, 10);
				}
				if (t == GalactusScenes.HERALD_LAND) {
					QuirkCameraFx.shake(2.5f, 10);
				}
			}
			case DEVOURER -> {
				if (t == GalactusScenes.DEVOURER_RISE_START) {
					QuirkCameraFx.shake(1.2f, 100);
				}
				if (t == GalactusScenes.DEVOURER_EYE_R || t == GalactusScenes.DEVOURER_EYE_L) {
					QuirkCameraFx.shake(2f, 8);
				}
				if (t == GalactusScenes.DEVOURER_ROAR) {
					QuirkCameraFx.shake(9f, 34);
					QuirkCameraFx.fovPunch(0.22f, 24);
					QuirkCameraFx.vignette(0x6A1BB0, 0.6f, 40);
				}
				if (t == GalactusScenes.DEVOURER_WARP) {
					QuirkCameraFx.fovPunch(0.35f, 40);
					QuirkCameraFx.shake(3f, 40);
				}
			}
			case WORLDSHIP -> {
				if (t == GalactusScenes.WORLDSHIP_LOCK) {
					QuirkCameraFx.shake(3f, 18);
				}
			}
			case RISE -> {
				if (t == GalactusScenes.RISE_STAND) {
					QuirkCameraFx.shake(2f, 90);
				}
				if (t == GalactusScenes.RISE_PLANT) {
					QuirkCameraFx.shake(8f, 24);
					QuirkCameraFx.fovPunch(0.15f, 16);
				}
			}
			case ENDING -> {
				if (t == 4) {
					QuirkCameraFx.shake(2f, 50);
				}
				if (t == GalactusScenes.ENDING_BURST) {
					QuirkCameraFx.shake(10f, 40);
					QuirkCameraFx.fovPunch(0.3f, 30);
				}
			}
			case CONSUMED -> {
				if (t == 5) {
					QuirkCameraFx.shake(4f, 100);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- the camera

	/** {x, y, z, yaw, pitch} for this frame, or null to leave the view alone. */
	public static double[] camera(Player player, float pt) {
		pov = false;
		if (!active()) {
			return null;
		}
		float t = time(pt);
		Vec3 f = GalactusScenes.forward(yaw), r = GalactusScenes.right(yaw);
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 a = anchor;
		Vec3 cam, look;
		switch (scene) {
			case HERALD -> {
				Vec3 surfer = GalactusScenes.heraldPos(a, yaw, t).add(0, 1.2, 0);
				if (t < 35) {
					cam = a.add(f.scale(-12)).add(r.scale(7)).add(0, 1.4, 0);
					double k = GalactusScenes.ease(t / 35.0);
					look = a.add(0, 2, 0).lerp(a.add(f.scale(60)).add(0, 70, 0), k);
				} else if (t < 100) {
					cam = a.add(f.scale(-14)).add(r.scale(-6)).add(0, 2.2, 0);
					look = surfer;
				} else {
					double k = GalactusScenes.ease((t - 100) / 60.0);
					cam = a.add(f.scale(1)).add(r.scale(6)).add(0, 0.8, 0).lerp(a.add(f.scale(2.5)).add(r.scale(4.2)).add(0, 1.3, 0), k);
					look = surfer;
				}
			}
			case DEVOURER -> {
				Vec3 head = GalactusScenes.devourerFeet(a, yaw, t).add(0, GalactusScenes.DEVOURER_HEIGHT * 0.9, 0);
				if (t < GalactusScenes.DEVOURER_RISE_START) {
					cam = a.add(f.scale(-16)).add(r.scale(6)).add(0, 1.6, 0);
					look = a.add(f.scale(30)).add(0, 12, 0);
				} else if (t < 145) {
					double k = GalactusScenes.ease((t - 40) / 105.0);
					cam = a.add(f.scale(-16)).add(r.scale(6)).add(0, 1.6, 0).lerp(a.add(f.scale(-10)).add(r.scale(4)).add(0, 1.2, 0), k);
					look = a.add(f.scale(30)).add(0, 12, 0).lerp(head, k);
				} else if (t < GalactusScenes.DEVOURER_LIFT) {
					cam = a.add(f.scale(-10)).add(r.scale(4)).add(0, 1.2, 0);
					look = head;
				} else {
					// the ascension, first person: you rise and the ground drops away; then the warp
					pov = true;
					Vec3 eye = player.getEyePosition(pt);
					double k = (t - GalactusScenes.DEVOURER_LIFT) / (double) (Scene.DEVOURER.length - GalactusScenes.DEVOURER_LIFT);
					double lift = k * k * 70;
					cam = eye.add(0, lift, 0);
					if (t < 228) {
						look = head;
					} else {
						double k2 = GalactusScenes.ease((t - 228) / 22.0);
						look = head.lerp(cam.add(0, 100, 0).add(f.scale(4)), k2);
					}
				}
			}
			case ARRIVAL -> {
				double k = GalactusScenes.ease(t / Scene.ARRIVAL.length);
				cam = a.add(f.scale(-46)).add(r.scale(-14)).add(0, 32, 0).lerp(a.add(f.scale(-12)).add(r.scale(-3)).add(0, 5, 0), k);
				look = a.lerp(a.add(f.scale(50)).add(0, 32, 0), GalactusScenes.ease((t - 15) / 55.0));
			}
			case WORLDSHIP -> {
				double ang = Math.toRadians(yaw + 180 - 70 + t * 0.7); // round the far side from him: he stays in the background
				cam = a.add(-Math.sin(ang) * 50, 24 - t * 0.04, Math.cos(ang) * 50);
				look = a.add(0, 2, 0);
				if (t > 140) {
					look = look.lerp(a.add(f.scale(60)).add(0, -20, 0), GalactusScenes.ease((t - 140) / 60.0));
				}
			}
			case RISE -> {
				Entity e = bossId >= 0 && player.level() != null ? player.level().getEntity(bossId) : null;
				Vec3 feet = e instanceof DevourerEntity dd ? dd.getPosition(pt).subtract(0, dd.drop(), 0) : a.add(f.scale(48)).add(0, -70, 0);
				Vec3 headPos = e instanceof DevourerEntity d ? GalactusRig.solve(DevourerRenderer.frameInput(d, pt)).face : feet.add(0, 100, 0);
				// from up at the rim (looking out and down at him kneeling) back to the deck as he rises over it
				cam = a.add(f.scale(44)).add(r.scale(-14)).add(0, 6, 0).lerp(a.add(f.scale(-6)).add(r.scale(-5)).add(0, 2.5, 0), GalactusScenes.ease((t - 30) / 90.0));
				double k = GalactusScenes.ease((t - 15) / 115.0);
				look = headPos.subtract(0, Math.max(20, headPos.y - feet.y) * 0.45, 0).lerp(headPos, k); // from his chest up to his face
			}
			case ENDING -> {
				Entity e = bossId >= 0 && player.level() != null ? player.level().getEntity(bossId) : null;
				Vec3 face = e instanceof DevourerEntity d ? GalactusRig.solve(DevourerRenderer.frameInput(d, pt)).face : a.add(f.scale(40)).add(0, 30, 0);
				if (t < GalactusScenes.ENDING_LEAVE) {
					cam = a.add(f.scale(-9)).add(r.scale(4)).add(0, 4, 0);
					look = face;
				} else {
					double k = GalactusScenes.ease((t - GalactusScenes.ENDING_LEAVE) / 80.0);
					cam = a.add(f.scale(-9)).add(r.scale(4)).add(0, 4, 0).lerp(a.add(f.scale(-30)).add(0, 40, 0), k);
					look = face.lerp(a.add(f.scale(80)).add(0, 50, 0), k);
				}
			}
			case CONSUMED -> {
				cam = a.add(0, 7, 0).add(f.scale(-4));
				Vec3 earth = new Vec3(GalactusOrbitSky.EARTH.x, GalactusOrbitSky.EARTH.y, GalactusOrbitSky.EARTH.z);
				look = cam.add(f.scale(0.4).add(earth.scale(2)).normalize().scale(100)).lerp(cam.add(earth.scale(100)), GalactusScenes.ease(t / 60.0));
			}
			default -> {
				return null;
			}
		}
		// overworld shots: never inside a hill -- the camera stays a little over the ground under it
		if (!pov && (scene == Scene.HERALD || scene == Scene.DEVOURER) && player.level() != null) {
			// the ground under the camera and along the first 10 blocks of the view (a hill in front would hide the shot)
			Vec3 dirH = new Vec3(look.x - cam.x, 0, look.z - cam.z);
			dirH = dirH.lengthSqr() < 1.0e-6 ? Vec3.ZERO : dirH.normalize();
			double want = -Double.MAX_VALUE;
			for (int s = 0; s <= 10; s += 2) {
				int gx = Mth.floor(cam.x + dirH.x * s), gz = Mth.floor(cam.z + dirH.z * s);
				int ground = player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
				want = Math.max(want, ground + (s == 0 ? 1.8 : 1.0));
			}
			if (cam.y < want) {
				cam = new Vec3(cam.x, want, cam.z);
			}
		}
		double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float cy = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float cp = (float) (-Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
		return new double[] { cam.x, cam.y, cam.z, cy, cp };
	}

	// ---------------------------------------------------------------- overlays

	private static final List<Line> HERALD_LINES = List.of(new Line(122, 158, "subtitle.projecthero.galactus.herald", ChatFormatting.WHITE));
	private static final List<Line> DEVOURER_LINES = List.of(
			new Line(176, 214, "subtitle.projecthero.galactus.hunger", ChatFormatting.LIGHT_PURPLE));
	private static final List<Line> WORLDSHIP_LINES = List.of(
			new Line(150, 198, "subtitle.projecthero.galactus.surfer_turns", ChatFormatting.WHITE));
	private static final List<Line> RISE_LINES = List.of(new Line(60, 120, "subtitle.projecthero.galactus.rise", ChatFormatting.LIGHT_PURPLE));
	private static final List<Line> ENDING_LINES = List.of(new Line(30, 80, "subtitle.projecthero.galactus.ending1", ChatFormatting.LIGHT_PURPLE),
			new Line(160, 204, "subtitle.projecthero.galactus.ending2", ChatFormatting.WHITE));
	private static final List<Line> CONSUMED_LINES = List.of(new Line(20, 100, "subtitle.projecthero.galactus.consumed", ChatFormatting.LIGHT_PURPLE));

	private static List<Line> lines(Scene s) {
		return switch (s) {
			case HERALD -> HERALD_LINES;
			case DEVOURER -> DEVOURER_LINES;
			case WORLDSHIP -> WORLDSHIP_LINES;
			case RISE -> RISE_LINES;
			case ENDING -> ENDING_LINES;
			case CONSUMED -> CONSUMED_LINES;
			default -> List.of();
		};
	}

	static void hud(GuiGraphics g, DeltaTracker delta) {
		if (scene == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float t = time(delta.getGameTimeDeltaPartialTick(false));
		int w = g.guiWidth(), h = g.guiHeight();
		// fades (drawn even when skipped: the white-outs hide the teleports)
		int fade = fade(t);
		if (!skipped) {
			if (scene != Scene.HOME) {
				// the letterbox: eased in over 12 ticks, out over the last 10
				float in = Mth.clamp(t / 12f, 0f, 1f) * Mth.clamp((scene.length - t) / 10f, 0f, 1f);
				int bar = (int) (Math.max(44, h * 0.13f) * in);
				g.fill(0, 0, w, bar, 0xFF000000);
				g.fill(0, h - bar, w, h, 0xFF000000);
			}
			if (scene == Scene.DEVOURER && t > GalactusScenes.DEVOURER_WARP) {
				warp(g, w, h, t);
			}
			for (Line l : lines(scene)) {
				if (t >= l.from && t <= l.to) {
					float a = Mth.clamp((t - l.from) / 6f, 0f, 1f) * Mth.clamp((l.to - t) / 6f, 0f, 1f);
					int alpha = Math.max(4, (int) (a * 255));
					Component c = Component.translatable(l.key).withStyle(l.colour, ChatFormatting.ITALIC);
					int tw = mc.font.width(c);
					int bar = (int) Math.max(44, h * 0.13f);
					g.drawString(mc.font, c, (w - tw) / 2, h - bar + (bar - 9) / 2, (alpha << 24) | 0xFFFFFF, true);
				}
			}
			if (t > 20 && scene != Scene.HOME) {
				Component hint = Component.translatable("hud.projecthero.galactus.skip");
				int tw = mc.font.width(hint);
				float a = Mth.clamp((t - 20) / 10f, 0f, 1f) * 0.6f;
				int bar = (int) Math.max(44, h * 0.13f);
				g.drawString(mc.font, hint, w - tw - 8, h - bar / 2 - 4, ((int) (a * 255) << 24) | 0xBBBBBB, false);
			}
		}
		if ((fade >>> 24) > 0) {
			g.fill(0, 0, w, h, fade);
		}
	}

	/** ARGB of the full-screen fade at scene time {@code t}. */
	private static int fade(float t) {
		float a = 0f;
		int rgb = 0xFFFFFF;
		switch (scene) {
			case HERALD -> {
				a = Mth.clamp(1f - t / 14f, 0f, 1f) * 0.8f;
				rgb = 0x000000;
				if (t >= GalactusScenes.HERALD_BOOM && t < GalactusScenes.HERALD_BOOM + 6) {
					a = 0.55f * (1f - (t - GalactusScenes.HERALD_BOOM) / 6f);
					rgb = 0xFFFFFF;
				}
			}
			case DEVOURER -> {
				if (t < 30) {
					a = 0.35f * (t / 30f);
					rgb = 0x10002A;
				} else if (t < GalactusScenes.DEVOURER_RISE_START + 20) {
					a = 0.35f * (1f - (t - 30) / 30f);
					rgb = 0x10002A;
				}
				if (t >= GalactusScenes.DEVOURER_WHITE) {
					a = Mth.clamp((t - GalactusScenes.DEVOURER_WHITE) / 18f, 0f, 1f);
					rgb = 0xFFFFFF;
				}
			}
			case ARRIVAL -> a = Mth.clamp(1f - t / 26f, 0f, 1f);
			case WORLDSHIP -> a = Mth.clamp(1f - t / 10f, 0f, 1f) * 0.7f;
			case RISE -> {
				a = Mth.clamp(1f - t / 10f, 0f, 1f) * 0.7f;
				rgb = 0x000000;
			}
			case ENDING -> {
				if (t >= GalactusScenes.ENDING_BURST && t < GalactusScenes.ENDING_BURST + 10) {
					a = 0.8f * (1f - (t - GalactusScenes.ENDING_BURST) / 10f);
				}
				if (t >= GalactusScenes.ENDING_WHITE) {
					a = Mth.clamp((t - GalactusScenes.ENDING_WHITE) / 24f, 0f, 1f);
				}
			}
			case CONSUMED -> {
				a = Mth.clamp((t - GalactusScenes.CONSUMED_WHITE) / 24f, 0f, 1f);
				rgb = 0xF0E4FF;
			}
			case HOME -> a = Mth.clamp(1f - t / Scene.HOME.length, 0f, 1f);
			default -> {
			}
		}
		return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | rgb;
	}

	/** The ascension's warp: streaks of starlight rushing out from the middle of the screen. */
	private static void warp(GuiGraphics g, int w, int h, float t) {
		float k = Mth.clamp((t - GalactusScenes.DEVOURER_WARP) / 26f, 0f, 1f);
		int cx = w / 2, cy = h / 2;
		int count = (int) (40 + 160 * k);
		for (int i = 0; i < count; i++) {
			double seed = i * 12.9898;
			double ang = (Math.sin(seed) * 43758.5453) % (Math.PI * 2);
			double speed = 0.5 + Math.abs(Math.sin(seed * 1.7)) * 1.5;
			double phase = (t * 0.06 * speed + Math.abs(Math.cos(seed * 3.1))) % 1.0;
			double r0 = phase * Math.max(w, h) * 0.8;
			double len = 6 + 50 * k * phase;
			int x0 = (int) (cx + Math.cos(ang) * r0), y0 = (int) (cy + Math.sin(ang) * r0);
			int x1 = (int) (cx + Math.cos(ang) * (r0 + len)), y1 = (int) (cy + Math.sin(ang) * (r0 + len));
			int alpha = (int) (Math.min(1, phase * 2) * 220 * (0.4 + 0.6 * k));
			int col = (alpha << 24) | (i % 5 == 0 ? 0xD8B0FF : 0xFFFFFF);
			line(g, x0, y0, x1, y1, col);
		}
	}

	private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		steps = Math.max(1, Math.min(steps, 60));
		for (int s = 0; s <= steps; s += 2) {
			int x = x0 + (x1 - x0) * s / steps, y = y0 + (y1 - y0) * s / steps;
			g.fill(x, y, x + 1, y + 1, col);
		}
	}
}
