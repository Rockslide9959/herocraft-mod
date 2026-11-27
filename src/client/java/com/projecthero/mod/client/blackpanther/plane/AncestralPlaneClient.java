package com.projecthero.mod.client.blackpanther.plane;

import java.util.List;

import com.projecthero.mod.blackpanther.plane.AncestralPlane;
import com.projecthero.mod.blackpanther.plane.ParkourCourse;
import com.projecthero.mod.blackpanther.plane.PlaneGeometry;
import com.projecthero.mod.blackpanther.plane.PlanePayloads;
import com.projecthero.mod.blackpanther.plane.PlaneRealm;
import com.projecthero.mod.blackpanther.plane.PlaneScene;
import com.projecthero.mod.blackpanther.plane.ShadeTrial;
import com.projecthero.mod.client.quirk.QuirkCameraFx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther Ancestral Plane: the client half -- every cutscene's camera ({@link #camera}, used through the shared
 * journey camera mixin), the letterbox, fades (black, and Bast's gold at the end), wrapped subtitles for the ancestors and Bast,
 * the arrival title, the skip prompt for scenes seen before (Space), the trial's and the spirit path's HUD panels, the
 * hold-Sneak wake-up bar, the warm film tint over the plane, and its ambience (wind across the grass, a big cat far off).
 * Movement keys are held while a scene plays (the server holds the player too). Presentation only; every lock times out on
 * its own and everything clears on disconnect.
 */
public final class AncestralPlaneClient {
	private static PlaneScene scene;
	private static long start;
	private static Vec3 anchor = Vec3.ZERO;
	private static int entityId = -1;
	private static boolean skippable;
	private static boolean skipSent;
	private static boolean pov;
	private static long wakeAt = Long.MIN_VALUE / 4;
	private static int loadingTicks;

	private static PlanePayloads.Hud hud = PlanePayloads.Hud.none();
	private static long eventAt = Long.MIN_VALUE / 4;
	private static int event;
	private static int eventMode;
	private static long nextGust, nextCat;

	private AncestralPlaneClient() {
	}

	public static void initialize() {
		AncestralSky.initialize();
		EntityRendererRegistry.register(AncestralPlane.ANCESTOR, AncestorRenderer::new);
		EntityRendererRegistry.register(AncestralPlane.SHADE, PantherShadeRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(PlanePayloads.Scene.TYPE, (p, ctx) -> ctx.client().execute(() -> acceptScene(p)));
		ClientPlayNetworking.registerGlobalReceiver(PlanePayloads.Hud.TYPE, (p, ctx) -> ctx.client().execute(() -> acceptHud(p)));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
		ClientTickEvents.START_CLIENT_TICK.register(AncestralPlaneClient::holdKeys);
		ClientTickEvents.END_CLIENT_TICK.register(AncestralPlaneClient::tick);
		HudRenderCallback.EVENT.register(AncestralPlaneClient::hudRender);
		com.projecthero.mod.client.cutscene.CutsceneHands.register(AncestralPlaneClient::active);
	}

	static void reset() {
		scene = null;
		pov = false;
		hud = PlanePayloads.Hud.none();
	}

	// ---------------------------------------------------------------- packets

	static void acceptScene(PlanePayloads.Scene p) {
		Minecraft mc = Minecraft.getInstance();
		PlaneScene s = PlaneScene.byId(p.scene());
		if (s == null || mc.level == null) {
			scene = null;
			return;
		}
		boolean same = scene == s;
		scene = s;
		start = mc.level.getGameTime() - p.elapsed();
		anchor = new Vec3(p.x(), p.y(), p.z());
		entityId = p.entity();
		skippable = p.skippable();
		if (!same) {
			skipSent = false;
		}
		if (s == PlaneScene.WAKE) {
			wakeAt = mc.level.getGameTime();
			hud = PlanePayloads.Hud.none();
		}
	}

	static void acceptHud(PlanePayloads.Hud p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (p.e() != 0) {
			event = p.e();
			eventMode = p.mode();
			eventAt = mc.level.getGameTime();
			if (p.mode() == 3 && p.e() == 3) {
				QuirkCameraFx.vignette(0xB01030, 0.45f, 16);
			}
		}
		hud = p;
	}

	/** A cutscene has the camera (not the wake-up fade). */
	public static boolean active() {
		return scene != null && scene != PlaneScene.WAKE;
	}

	public static boolean pov() {
		return pov;
	}

	private static float time(float pt) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0f : (mc.level.getGameTime() - start) + pt;
	}

	private static boolean inPlane(Minecraft mc) {
		return mc.level != null && mc.level.dimension() == PlaneRealm.DIMENSION;
	}

	// ---------------------------------------------------------------- tick

	private static void holdKeys(Minecraft mc) {
		if (!active() || mc.player == null) {
			return;
		}
		// Space skips a scene seen before
		while (mc.options.keyJump.consumeClick()) {
			if (skippable && !skipSent && ClientPlayNetworking.canSend(PlanePayloads.Skip.TYPE)) {
				ClientPlayNetworking.send(new PlanePayloads.Skip());
				skipSent = true;
			}
		}
		mc.options.keyUp.setDown(false);
		mc.options.keyDown.setDown(false);
		mc.options.keyLeft.setDown(false);
		mc.options.keyRight.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keyShift.setDown(false);
		mc.options.keySprint.setDown(false);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			scene = null;
			return;
		}
		long now = mc.level.getGameTime();
		// the plane is entered and left under a black fade: never let "Loading terrain" flash over it
		if (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen && (active() || now - wakeAt < 60)) {
			if (++loadingTicks > 8) {
				mc.setScreen(null);
				loadingTicks = 0;
			}
		} else {
			loadingTicks = 0;
		}
		if (scene != null) {
			float t = time(0f);
			if (t >= scene.length + 60 || (scene == PlaneScene.FINALE && t > PlaneScene.FINALE_HOME + 200)) {
				scene = null; // the server never answered: let go
			}
			if (scene == PlaneScene.FINALE && (int) t == PlaneScene.FINALE_SPIRIT + 2) {
				QuirkCameraFx.shake(1.2f, 20);
			}
			if (scene == PlaneScene.TRIAL_INTRO && (int) t == PlaneScene.INTRO_EYES) {
				QuirkCameraFx.shake(1.5f, 18);
				QuirkCameraFx.vignette(0x9A4BFF, 0.35f, 30);
			}
		}
		if (!inPlane(mc)) {
			if (hud.mode() != 0 && now - wakeAt < 5) {
				hud = PlanePayloads.Hud.none();
			}
			return;
		}
		// the plane's ambience: wind across the grass, now and then a big cat somewhere far off
		var r = mc.level.random;
		if (now >= nextGust) {
			nextGust = now + 120 + r.nextInt(180);
			mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.07f,
					0.42f + r.nextFloat() * 0.15f, false);
		}
		if (now >= nextCat) {
			nextCat = now + 500 + r.nextInt(700);
			double a = r.nextDouble() * Math.PI * 2;
			mc.level.playLocalSound(mc.player.getX() + Math.cos(a) * 12, mc.player.getY() + 1, mc.player.getZ() + Math.sin(a) * 12,
					r.nextBoolean() ? SoundEvents.POLAR_BEAR_WARNING : SoundEvents.CAT_PURREOW, SoundSource.AMBIENT, 0.35f, 0.45f + r.nextFloat() * 0.1f, false);
		}
	}

	// ---------------------------------------------------------------- the camera

	private static Entity entity() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || entityId < 0 ? null : mc.level.getEntity(entityId);
	}

	private static Vec3 o(double x, double y, double z) {
		return anchor.add(x, y, z);
	}

	private static double ease(double k) {
		k = Mth.clamp(k, 0.0, 1.0);
		return k * k * (3 - 2 * k);
	}

	/** {x, y, z, yaw, pitch} for this frame, or null to leave the view alone. */
	public static double[] camera(Player player, float pt) {
		pov = false;
		if (!active()) {
			return null;
		}
		float t = time(pt);
		Vec3 cam, look;
		Vec3 me = player.getPosition(pt);
		Entity e = entity();
		switch (scene) {
			case ARRIVAL -> {
				// high over the savanna looking into the sunset, gliding down to the dreamer, then into their eyes
				Vec3 a = o(-34, 30, -30), b = o(-10, 7, -8), eye = player.getEyePosition(pt);
				Vec3 c = me.add(-3.4, 2.1, 1.3);
				if (t < 100) {
					double k = ease(t / 100.0);
					cam = a.lerp(b, k);
					look = o(80, 12, 0).lerp(o(20, 2, 0.5), k);
				} else if (t < 150) {
					double k = ease((t - 100) / 50.0);
					cam = b.lerp(c, k);
					look = o(20, 2, 0.5).lerp(me.add(10, 1.5, 0), k);
				} else {
					double k = ease((t - 150) / 20.0);
					cam = c.lerp(eye, k);
					look = me.add(10, 1.5, 0).lerp(eye.add(10, 0, 0), k);
					pov = k > 0.97;
				}
			}
			case GREETING -> {
				Vec3 eldest = e != null ? e.getPosition(pt) : o(PlaneGeometry.ANCESTORS[0][0] - 2.2, 0, PlaneGeometry.ANCESTORS[0][1]);
				if (t < PlaneScene.GREET_LINE2) {
					// behind the dreamer, the ancestors and the great tree dark against the sun
					double k = ease(t / (double) PlaneScene.GREET_LINE2);
					cam = me.add(-6.5, 1.9, -4.5).lerp(me.add(-5.0, 1.7, -3.6), k);
					look = eldest.add(1.5, 2.2, 0.5);
				} else if (t < PlaneScene.GREET_LINE3) {
					// over the dreamer's shoulder onto the eldest
					double k = ease((t - PlaneScene.GREET_LINE2) / 78.0);
					cam = me.add(-1.7, 2.15, 1.7).lerp(me.add(-1.4, 2.1, 1.5), k);
					look = eldest.add(0, 1.65, 0);
				} else if (t < PlaneScene.GREET_LINE4) {
					// over the eldest's shoulder back at the dreamer
					double k = ease((t - PlaneScene.GREET_LINE3) / 80.0);
					cam = eldest.add(1.7, 2.15, -1.7).lerp(eldest.add(1.4, 2.1, -1.5), k);
					look = me.add(0, 1.6, 0);
				} else {
					// rising wide: the group, the tree, the sun
					double k = ease((t - PlaneScene.GREET_LINE4) / 72.0);
					cam = me.add(-4, 3, 7).lerp(me.add(-6, 8, 10), k);
					look = eldest.add(8, 3, 0).lerp(eldest.add(16, 4, 0), k);
				}
			}
			case PROCESSION -> {
				Vec3 eldest = e != null ? e.getPosition(pt) : o(80, 0, 0);
				if (t < 132) {
					// ahead of the procession, walking backward before it as it comes on through the grass
					double cx = Math.min(eldest.x + 7.5, anchor.x + PlaneGeometry.SHRINE_ENTRY_X - 1.2);
					cam = new Vec3(cx, anchor.y + 1.9, anchor.z - 3.2);
					look = eldest.add(0, 1.4, 1.0);
				} else if (t < 200) {
					// high over the shrine, looking back at them arriving
					double k = ease((t - 132) / 68.0);
					cam = o(118, 26, 26).lerp(o(112, 22, 22), k);
					look = o(92, 1, 0);
				} else {
					double k = ease((t - 200) / 50.0);
					cam = me.add(-5, 2.4, -4.5).lerp(me.add(-4.2, 2.2, -3.4), k);
					look = me.add(12, 3.5, 0.5);
				}
			}
			case TRIAL_INTRO -> {
				Vec3 head = PlaneGeometry.statueHead(net.minecraft.core.BlockPos.containing(anchor));
				if (t < PlaneScene.INTRO_LINE1) {
					double k = ease(t / (double) PlaneScene.INTRO_LINE1);
					cam = o(100, 3.2, -7.5).lerp(o(102, 3.6, -6.0), k);
					look = head;
				} else if (t < PlaneScene.INTRO_LINE2) {
					double k = ease((t - PlaneScene.INTRO_LINE1) / 60.0);
					cam = head.add(-7.5, -0.8, 3.0).lerp(head.add(-6.4, -0.5, 2.4), k);
					look = head.add(0, -0.2, 0);
				} else if (t < PlaneScene.INTRO_LINE3) {
					cam = head.add(3.4, 1.6, -4.4);
					look = me.add(0, 1.0, 0);
				} else {
					double k = ease((t - PlaneScene.INTRO_LINE3) / 46.0);
					double ang = Math.toRadians(205 + 45 * k);
					Vec3 c = PlaneGeometry.arena(net.minecraft.core.BlockPos.containing(anchor));
					cam = c.add(Math.cos(ang) * 13, 6 - 2 * k, Math.sin(ang) * 13);
					look = c.add(0, 1, 0);
				}
			}
			case PATH -> {
				double k = Mth.clamp((t - PlaneScene.PATH_BUILD_FROM) / (double) (PlaneScene.PATH_BUILD_TO - PlaneScene.PATH_BUILD_FROM), 0.0, 1.0);
				ParkourCourse.Platform f = ParkourCourse.finish();
				// the forming front (follows the build: platform x grows linearly, rising as it goes)
				double fx = k * f.x0();
				double fy = PlaneGeometry.PATH_BASE + f.y() * k;
				// high on the shrine's back, close to the dreamer (whose chunks the client has), panning after the front
				Vec3 perch = o(PlaneGeometry.SHRINE_X + 12, 17, -11);
				if (t < PlaneScene.PATH_BUILD_FROM) {
					cam = o(PlaneGeometry.SHRINE_X + 8, 15, -9);
					look = o(PlaneGeometry.PATH_X + 4, PlaneGeometry.PATH_BASE, 0);
				} else if (t < 150) {
					double kk = ease((t - PlaneScene.PATH_BUILD_FROM) / 30.0);
					cam = o(PlaneGeometry.SHRINE_X + 8, 15, -9).lerp(perch, kk);
					look = o(PlaneGeometry.PATH_X + fx + 1, fy - 1, 0);
				} else {
					// down to the start, where the dreamer will stand
					double kk = ease((t - 150) / 20.0);
					Vec3 startEye = o(PlaneGeometry.PATH_X + 2.5, PlaneGeometry.PATH_BASE + 1 + 1.62, 0.5);
					cam = perch.lerp(startEye, kk);
					look = o(PlaneGeometry.PATH_X + f.x0(), PlaneGeometry.PATH_BASE + f.y(), 0).lerp(startEye.add(10, -0.5, 0), kk);
				}
			}
			case FINALE -> {
				Vec3 sp = e != null ? e.getPosition(pt) : PlaneGeometry.spirit(net.minecraft.core.BlockPos.containing(anchor));
				float size = e instanceof com.projecthero.mod.blackpanther.plane.PantherShadeEntity sh ? sh.size() : 9f;
				Vec3 face = sp.add(-0.5 * size * 1.6 * 0.55, 0.62 * size * 1.6 * 0.55, 0);
				if (t < PlaneScene.FINALE_LINE1) {
					double k = ease(t / (double) PlaneScene.FINALE_LINE1);
					cam = me.add(-4.5, 2.2, -2.8).lerp(me.add(-3.6, 2.0, -2.2), k);
					look = me.add(20, 4, 0);
				} else if (t < PlaneScene.FINALE_LINE2) {
					double k = ease((t - PlaneScene.FINALE_LINE1) / 76.0);
					cam = me.add(-2.2, 0.5, 3.8).lerp(me.add(-1.6, 0.6, 3.2), k);
					look = face;
				} else if (t < PlaneScene.FINALE_LINE3) {
					double k = ease((t - PlaneScene.FINALE_LINE2) / 76.0);
					cam = face.add(-9, 0.5, -3.5).lerp(face.add(-7.5, 0.3, -2.8), k);
					look = face;
				} else if (t < PlaneScene.FINALE_GOLD) {
					Vec3 eldest = me.add(1.5, 0, -2.2);
					cam = me.add(-1.8, 2.1, 1.8);
					look = eldest.add(0, 1.65, 0);
				} else {
					double k = ease((t - PlaneScene.FINALE_GOLD) / 60.0);
					cam = me.add(-3, 2, 0).lerp(me.add(-7, 9, 0), k);
					look = me.add(30, 5, 0);
				}
			}
			default -> {
				return null;
			}
		}
		double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float cy = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float cp = (float) (-Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
		return new double[] { cam.x, cam.y, cam.z, cy, cp };
	}

	/** The colour to cover "Loading terrain" with while the plane is entered or left under a fade (0: leave it). */
	public static int loadingCover() {
		Minecraft mc = Minecraft.getInstance();
		if (active() || (mc.level != null && mc.level.getGameTime() - wakeAt < 60)) {
			return 0xFF000000;
		}
		return 0;
	}

	// ---------------------------------------------------------------- the HUD

	private record Line(int from, int to, String key, String speaker) {
	}

	private static final List<Line> GREET = List.of(new Line(PlaneScene.GREET_LINE1, PlaneScene.GREET_LINE2 - 4, "greet1", "eldest"),
			new Line(PlaneScene.GREET_LINE2, PlaneScene.GREET_LINE3 - 4, "greet2", "eldest"),
			new Line(PlaneScene.GREET_LINE3, PlaneScene.GREET_LINE4 - 4, "greet3", "eldest"),
			new Line(PlaneScene.GREET_LINE4, PlaneScene.GREETING.length - 4, "greet4", "eldest"));
	private static final List<Line> PROC = List.of(new Line(PlaneScene.PROC_LINE1, PlaneScene.PROC_LINE2 - 6, "proc1", "eldest"),
			new Line(PlaneScene.PROC_LINE2, PlaneScene.PROCESSION.length - 10, "proc2", "eldest"));
	private static final List<Line> INTRO = List.of(new Line(PlaneScene.INTRO_LINE1, PlaneScene.INTRO_LINE2 - 4, "intro1", "bast"),
			new Line(PlaneScene.INTRO_LINE2, PlaneScene.INTRO_LINE3 - 4, "intro2", "bast"),
			new Line(PlaneScene.INTRO_LINE3, PlaneScene.TRIAL_INTRO.length - 4, "intro3", "bast"));
	private static final List<Line> PATH = List.of(new Line(PlaneScene.PATH_LINE, 120, "path1", "bast"));
	private static final List<Line> FINALE = List.of(new Line(PlaneScene.FINALE_LINE1, PlaneScene.FINALE_LINE2 - 4, "finale1", "bast"),
			new Line(PlaneScene.FINALE_LINE2, PlaneScene.FINALE_LINE3 - 4, "finale2", "bast"),
			new Line(PlaneScene.FINALE_LINE3, PlaneScene.FINALE_GOLD + 10, "finale3", "eldest"));

	private static void hudRender(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		float pt = delta.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth(), h = g.guiHeight();
		if (inPlane(mc)) {
			// the warm film tint: violet at the top, amber at the bottom, darker at the corners
			g.fillGradient(0, 0, w, h / 2, 0x1E3A1258, 0x00000000);
			g.fillGradient(0, h / 2, w, h, 0x00000000, 0x1C8A4A10);
		}
		if (scene != null) {
			float t = time(pt);
			if (scene != PlaneScene.WAKE) {
				float in = Mth.clamp(t / 12f, 0f, 1f);
				if (scene != PlaneScene.ARRIVAL && scene != PlaneScene.FINALE) {
					in = 1f; // straight on from the last scene's bars or the cut
				}
				in *= Mth.clamp((scene.length - t) / 10f, 0f, 1f) + (scene == PlaneScene.FINALE ? 1f : 0f);
				in = Mth.clamp(in, 0f, 1f);
				int bar = (int) (Math.max(34, h * 0.12f) * in);
				g.fill(0, 0, w, bar, 0xFF000000);
				g.fill(0, h - bar, w, h, 0xFF000000);
				lines(g, mc, w, h, t, bar);
				if (scene == PlaneScene.ARRIVAL) {
					title(g, mc, w, h, t);
				}
				if (skippable && !skipSent) {
					Component c = Component.translatable("hud.projecthero.ancestral_plane.skip").withStyle(ChatFormatting.GRAY);
					g.drawString(mc.font, c, w - mc.font.width(c) - 10, h - Math.max(12, bar / 2 + 4), 0xFFAAAAAA, true);
				}
			}
			int fade = fade(t);
			if ((fade >>> 24) > 0) {
				g.fill(0, 0, w, h, fade);
			}
			if (active()) {
				return;
			}
		}
		if (!inPlane(mc)) {
			return;
		}
		switch (hud.mode()) {
			case 1 -> hint(g, mc, w, Component.translatable("hud.projecthero.ancestral_plane.wander"));
			case 2 -> hint(g, mc, w, Component.translatable("hud.projecthero.ancestral_plane.shrine"));
			case 3 -> trialPanel(g, mc, w, h, pt);
			case 4 -> pathPanel(g, mc, w, h, pt);
			default -> {
			}
		}
		if (hud.mode() != 0) {
			if (hud.wake() > 0) {
				wakeBar(g, mc, w, h, hud.wake());
			} else {
				Component c = Component.translatable("hud.projecthero.ancestral_plane.wake_hint");
				g.drawString(mc.font, c, (w - mc.font.width(c)) / 2, h - 62, 0x88D8C8B0, true);
			}
		}
	}

	private static void lines(GuiGraphics g, Minecraft mc, int w, int h, float t, int bar) {
		List<Line> lines = switch (scene) {
			case GREETING -> GREET;
			case PROCESSION -> PROC;
			case TRIAL_INTRO -> INTRO;
			case PATH -> PATH;
			case FINALE -> FINALE;
			default -> List.of();
		};
		for (Line l : lines) {
			if (t < l.from || t > l.to) {
				continue;
			}
			float a = Mth.clamp((t - l.from) / 6f, 0f, 1f) * Mth.clamp((l.to - t) / 6f, 0f, 1f);
			int alpha = Math.max(4, (int) (a * 255));
			Component speaker = Component.translatable("subtitle.projecthero.ancestral_plane.speaker." + l.speaker)
					.withStyle(l.speaker.equals("bast") ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD);
			Component text = Component.translatable("subtitle.projecthero.ancestral_plane." + l.key, mc.player.getName())
					.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC);
			Component c = Component.empty().append(speaker).append(Component.literal(": ").withStyle(ChatFormatting.GRAY)).append(text);
			int maxW = Math.min(w - 40, 380);
			List<FormattedCharSequence> split = mc.font.split(c, maxW);
			int y = h - bar + Math.max(3, (bar - split.size() * 10) / 2);
			if (split.size() * 10 > bar - 4) {
				y = h - split.size() * 10 - 4;
			}
			for (FormattedCharSequence s : split) {
				g.drawString(mc.font, s, (w - mc.font.width(s)) / 2, y, (alpha << 24) | 0xFFFFFF, true);
				y += 10;
			}
		}
	}

	/** "The Ancestral Plane" over the first sight of the savanna. */
	private static void title(GuiGraphics g, Minecraft mc, int w, int h, float t) {
		float from = PlaneScene.ARRIVAL_CAPTION, to = 150;
		if (t < from || t > to) {
			return;
		}
		float a = Mth.clamp((t - from) / 14f, 0f, 1f) * Mth.clamp((to - t) / 14f, 0f, 1f);
		int alpha = Math.max(4, (int) (a * 255));
		Component big = Component.translatable("subtitle.projecthero.ancestral_plane.title");
		g.pose().pushPose();
		g.pose().translate(w / 2f, h * 0.3f, 0);
		float sc = Math.min(2.6f, (w - 40) / (float) Math.max(1, mc.font.width(big)));
		g.pose().scale(sc, sc, 1f);
		g.drawString(mc.font, big, -mc.font.width(big) / 2, -4, (alpha << 24) | 0xF6D27A, true);
		g.pose().popPose();
		Component small = Component.translatable("subtitle.projecthero.ancestral_plane.title_sub").withStyle(ChatFormatting.ITALIC);
		int y = (int) (h * 0.3f) + 18;
		for (FormattedCharSequence s : mc.font.split(small, Math.min(w - 40, 300))) {
			g.drawString(mc.font, s, (w - mc.font.width(s)) / 2, y, (alpha << 24) | 0xE8C8F0, true);
			y += 10;
		}
	}

	private static int fade(float t) {
		float a = 0f;
		int rgb = 0x000000;
		switch (scene) {
			case ARRIVAL -> {
				float k = Mth.clamp((t - PlaneScene.ARRIVAL_DARK) / (float) (PlaneScene.ARRIVAL_CLEAR - PlaneScene.ARRIVAL_DARK), 0f, 1f);
				a = 1f - k * k * (3 - 2 * k);
			}
			case GREETING, TRIAL_INTRO -> a = 1f - Mth.clamp(t / 8f, 0f, 1f);
			case PROCESSION -> a = 1f - Mth.clamp(t / 10f, 0f, 1f);
			case PATH -> a = 1f - Mth.clamp(t / 8f, 0f, 1f);
			case FINALE -> {
				if (t < 10) {
					a = 1f - t / 10f;
				} else if (t >= PlaneScene.FINALE_GOLD && t < PlaneScene.FINALE_BLACK) {
					rgb = 0xFFD98A;
					a = (t - PlaneScene.FINALE_GOLD) / (float) (PlaneScene.FINALE_BLACK - PlaneScene.FINALE_GOLD);
				} else if (t >= PlaneScene.FINALE_BLACK) {
					float k = Mth.clamp((t - PlaneScene.FINALE_BLACK) / 14f, 0f, 1f);
					int r = (int) Mth.lerp(k, 0xFF, 0), gg = (int) Mth.lerp(k, 0xD9, 0), b = (int) Mth.lerp(k, 0x8A, 0);
					rgb = (r << 16) | (gg << 8) | b;
					a = 1f;
				}
			}
			case WAKE -> {
				float k = Mth.clamp((t - 10f) / 50f, 0f, 1f);
				a = 1f - k * k * (3 - 2 * k);
			}
			default -> {
			}
		}
		return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | rgb;
	}

	private static void hint(GuiGraphics g, Minecraft mc, int w, Component c) {
		int y = 14;
		for (FormattedCharSequence s : mc.font.split(c, Math.min(w - 40, 300))) {
			g.drawString(mc.font, s, (w - mc.font.width(s)) / 2, y, 0xFFF2D58A, true);
			y += 10;
		}
	}

	private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, 0x9A120A1C);
		g.fill(x0, y0, x1, y0 + 1, 0xFFD9A84A);
		g.fill(x0, y1 - 1, x1, y1, 0x66D9A84A);
	}

	private static void trialPanel(GuiGraphics g, Minecraft mc, int w, int h, float pt) {
		int pw = Math.min(220, w - 20);
		int x0 = (w - pw) / 2, y0 = 8;
		panel(g, x0, y0, x0 + pw, y0 + 46);
		Component title = Component.translatable("hud.projecthero.ancestral_plane.trial_title").withStyle(ChatFormatting.BOLD);
		g.drawString(mc.font, title, (w - mc.font.width(title)) / 2, y0 + 5, 0xFFE6B8FF, true);
		Component round = Component.translatable("hud.projecthero.ancestral_plane.trial_round", hud.a() + 1, ShadeTrial.ROUNDS);
		g.drawString(mc.font, round, x0 + 8, y0 + 18, 0xFFF2D58A, true);
		// shades struck: one pip each
		int total = Math.max(1, hud.c());
		int pipW = Math.min(10, (pw - 100) / total);
		int px = x0 + pw - 8 - total * pipW;
		for (int i = 0; i < total; i++) {
			int c = i < hud.b() ? 0xFFB070FF : 0xFF3A2A48;
			g.fill(px + i * pipW, y0 + 19, px + i * pipW + pipW - 2, y0 + 26, c);
		}
		// wounds: three marks
		Component wounds = Component.translatable("hud.projecthero.ancestral_plane.trial_wounds");
		g.drawString(mc.font, wounds, x0 + 8, y0 + 32, 0xFFCCCCCC, true);
		int wx = x0 + 12 + mc.font.width(wounds);
		for (int i = 0; i < ShadeTrial.MAX_WOUNDS; i++) {
			int c = i < hud.d() ? 0xFFE0323C : 0xFF4A3A3A;
			g.fill(wx + i * 12, y0 + 32, wx + i * 12 + 3, y0 + 40, c);
			g.fill(wx + i * 12 + 4, y0 + 31, wx + i * 12 + 7, y0 + 40, c);
			g.fill(wx + i * 12 + 8, y0 + 32, wx + i * 12 + 11, y0 + 40, c);
		}
		eventText(g, mc, w, h, pt, 3);
	}

	private static void pathPanel(GuiGraphics g, Minecraft mc, int w, int h, float pt) {
		int pw = Math.min(200, w - 20);
		int x0 = (w - pw) / 2, y0 = 8;
		panel(g, x0, y0, x0 + pw, y0 + 36);
		Component title = Component.translatable("hud.projecthero.ancestral_plane.path_title").withStyle(ChatFormatting.BOLD);
		g.drawString(mc.font, title, (w - mc.font.width(title)) / 2, y0 + 5, 0xFFF2D58A, true);
		Component cp = Component.translatable("hud.projecthero.ancestral_plane.path_checkpoint", hud.a() + 1, hud.b());
		g.drawString(mc.font, cp, x0 + 8, y0 + 17, 0xFFE6B8FF, true);
		// the Hairline progress bar along the bottom
		int bx0 = x0 + 8, bx1 = x0 + pw - 8, by = y0 + 30;
		g.fill(bx0, by, bx1, by + 2, 0xFF3A2A48);
		g.fill(bx0, by, bx0 + (int) ((bx1 - bx0) * Mth.clamp(hud.c() / 1000f, 0f, 1f)), by + 2, 0xFFD9A84A);
		for (int i = 0; i < hud.b(); i++) {
			double x = ParkourCourse.checkpoint(i).x0() / (double) ParkourCourse.length();
			int cx = bx0 + (int) ((bx1 - bx0) * x);
			g.fill(cx - 1, by - 2, cx + 1, by + 4, i <= hud.a() ? 0xFFB070FF : 0xFF6A5A78);
		}
		eventText(g, mc, w, h, pt, 4);
	}

	private static void eventText(GuiGraphics g, Minecraft mc, int w, int h, float pt, int mode) {
		float age = mc.level.getGameTime() - eventAt + pt;
		if (eventMode != mode || age > 50 || event == 0) {
			return;
		}
		String key = switch (mode * 10 + event) {
			case 31 -> hud.a() >= ShadeTrial.ROUNDS - 1 && hud.b() >= hud.c() ? "trial_won" : "round_won";
			case 32 -> "round_lost";
			case 33 -> "wounded";
			case 34 -> "round_start";
			case 41 -> "checkpoint";
			case 42 -> "caught";
			default -> null;
		};
		if (key == null) {
			return;
		}
		float a = Mth.clamp(age / 5f, 0f, 1f) * Mth.clamp((50 - age) / 10f, 0f, 1f);
		int alpha = Math.max(4, (int) (a * 255));
		Component c = Component.translatable("hud.projecthero.ancestral_plane.event." + key, hud.a() + 1);
		boolean big = !key.equals("wounded");
		g.pose().pushPose();
		g.pose().translate(w / 2f, h * 0.36f, 0);
		float sc = big ? Math.min(1.8f, (w - 40) / (float) Math.max(1, mc.font.width(c))) : 1f;
		g.pose().scale(sc, sc, 1f);
		int colour = key.equals("round_lost") || key.equals("wounded") ? 0xFF8080 : key.equals("caught") ? 0xD8B8FF : 0xF6D27A;
		g.drawString(mc.font, c, -mc.font.width(c) / 2, -4, (alpha << 24) | colour, true);
		g.pose().popPose();
	}

	private static void wakeBar(GuiGraphics g, Minecraft mc, int w, int h, int pct) {
		int bw = Math.min(160, w - 40);
		int x0 = (w - bw) / 2, y = h - 64;
		Component c = Component.translatable("hud.projecthero.ancestral_plane.waking");
		g.drawString(mc.font, c, (w - mc.font.width(c)) / 2, y - 11, 0xFFE8D8C0, true);
		g.fill(x0, y, x0 + bw, y + 3, 0xAA201828);
		g.fill(x0, y, x0 + bw * pct / 100, y + 3, 0xFFE8D8C0);
	}
}
