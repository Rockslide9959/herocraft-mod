package com.projecthero.mod.client.shazam.journey;

import java.util.List;

import com.projecthero.mod.client.quirk.QuirkCameraFx;
import com.projecthero.mod.client.shazam.ThroneWizardRenderer;
import com.projecthero.mod.shazam.journey.JourneyGeometry;
import com.projecthero.mod.shazam.journey.JourneyPayloads;
import com.projecthero.mod.shazam.journey.JourneyScene;
import com.projecthero.mod.shazam.journey.ShazamJourney;
import com.projecthero.mod.shazam.journey.SubwayTrainEntity;
import com.projecthero.mod.shazam.journey.ThroneWizardEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: the client half of the journey -- the illusion ({@link IllusionRenderer}: fading in, flickering,
 * whispering, sparkling), every cutscene's camera ({@link #camera}, used by {@code ShazamJourneyCameraMixin}), the letterbox,
 * fades to black and white, the Wizard's subtitles (wrapped), location captions, the glyphs of the ride, the "SHAZAM!" shout,
 * the prompt ({@link SayShazamScreen}) and its HUD hint, camera shakes on cue, the train's rumble, and holding the movement keys
 * while a scene plays (the server holds the player too). Presentation only: the story runs on the server.
 *
 * <p>Every lock lets go by itself: a scene ends at its length (the descent's black holds at most 10 s more for the subway to
 * arrive), the prompt goes stale without the server's refresh, and everything clears on disconnect.
 */
public final class JourneyClient {
	private static JourneyScene scene;
	private static long start;
	private static Vec3 anchor = Vec3.ZERO;
	private static float sceneYaw;
	private static int entityId = -1;
	private static long refreshedAt;
	private static boolean pov;
	private static int lastCue = -1;
	private static int loadingTicks;
	private static long homeAt = Long.MIN_VALUE / 4;

	// the illusion
	private static boolean illusion;
	private static Vec3 illusionPos = Vec3.ZERO;
	private static float illusionYaw;
	private static long illusionStart;
	private static long illusionFadeAt = -1;
	private static long nextWhisper;
	private static boolean hornPlayed;

	private JourneyClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(ShazamJourney.TRAIN, SubwayTrainRenderer::new);
		EntityRendererRegistry.register(ShazamJourney.THRONE_WIZARD, ThroneWizardRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(JourneyPayloads.Scene.TYPE, (p, ctx) -> ctx.client().execute(() -> acceptScene(p)));
		ClientPlayNetworking.registerGlobalReceiver(JourneyPayloads.Illusion.TYPE, (p, ctx) -> ctx.client().execute(() -> acceptIllusion(p)));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
		ClientTickEvents.START_CLIENT_TICK.register(JourneyClient::holdKeys);
		ClientTickEvents.END_CLIENT_TICK.register(JourneyClient::tick);
		HudRenderCallback.EVENT.register(JourneyClient::hud);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(JourneyClient::world);
		// no first-person hands in any journey scene, the descent through to home (the ride's window shot included)
		com.projecthero.mod.client.cutscene.CutsceneHands.register(() -> scene != null);
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof SubwayTrainEntity train) {
				Minecraft.getInstance().getSoundManager().play(new TrainSound(train));
			}
		});
	}

	static void reset() {
		scene = null;
		illusion = false;
		illusionFadeAt = -1;
		pov = false;
	}

	// ---------------------------------------------------------------- packets

	static void acceptScene(JourneyPayloads.Scene p) {
		Minecraft mc = Minecraft.getInstance();
		JourneyScene s = JourneyScene.byId(p.scene());
		if (s == null || mc.level == null) {
			scene = null;
			return;
		}
		boolean wasPrompt = scene == JourneyScene.PROMPT;
		boolean same = scene == s;
		scene = s;
		start = mc.level.getGameTime() - p.elapsed();
		anchor = new Vec3(p.x(), p.y(), p.z());
		sceneYaw = p.yaw();
		entityId = p.entity();
		refreshedAt = mc.level.getGameTime();
		if (!same) {
			lastCue = p.elapsed() - 1;
		}
		if (s == JourneyScene.HOME) {
			homeAt = mc.level.getGameTime();
		}
		if (s == JourneyScene.DESCENT) {
			illusion = true;
			illusionPos = anchor;
			illusionYaw = sceneYaw;
			illusionFadeAt = -1;
		} else {
			illusion = false;
		}
		if (s == JourneyScene.PROMPT && (p.yaw() > 0.5f || !wasPrompt) && !(mc.screen instanceof SayShazamScreen)
				&& (mc.screen == null || mc.screen instanceof ChatScreen)) {
			mc.setScreen(new SayShazamScreen());
		}
	}

	static void acceptIllusion(JourneyPayloads.Illusion p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (p.active()) {
			illusion = true;
			illusionPos = new Vec3(p.x(), p.y(), p.z());
			illusionYaw = p.yaw();
			illusionStart = mc.level.getGameTime() - p.age();
			illusionFadeAt = -1;
			nextWhisper = mc.level.getGameTime() + 10;
			hornPlayed = false;
		} else if (illusion && scene != JourneyScene.DESCENT) {
			illusionFadeAt = mc.level.getGameTime();
		}
	}

	/** The prompt's button was pressed (it closes; the transformation scene follows from the server). */
	static void answered() {
		if (scene == JourneyScene.PROMPT) {
			refreshedAt = Long.MIN_VALUE / 4; // let the HUD hint go at once
		}
	}

	public static boolean prompting() {
		return scene == JourneyScene.PROMPT;
	}

	public static boolean active() {
		return scene != null && scene != JourneyScene.HOME;
	}

	public static boolean pov() {
		return pov;
	}

	private static float time(float pt) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0f : (mc.level.getGameTime() - start) + pt;
	}

	// ---------------------------------------------------------------- tick

	/** While a scene holds the player, the movement keys do nothing (the server holds them in place anyway). */
	private static void holdKeys(Minecraft mc) {
		if (scene == null || scene == JourneyScene.HOME || mc.player == null) {
			return;
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
			if (scene != null) {
				scene = null;
			}
			return;
		}
		long now = mc.level.getGameTime();
		// a journey's dimension changes happen under a black or white fade: don't let "Loading terrain" sit over the scene
		if (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen && (scene != null || now - homeAt < 60)) {
			if (++loadingTicks > 8) {
				mc.setScreen(null);
				loadingTicks = 0;
			}
		} else {
			loadingTicks = 0;
		}
		if (scene != null) {
			float t = time(0f);
			int limit = scene == JourneyScene.DESCENT ? scene.length + 200 : scene == JourneyScene.PROMPT ? Integer.MAX_VALUE : scene.length;
			if (t >= limit || (scene == JourneyScene.PROMPT && now - refreshedAt > 400)) {
				scene = null;
			} else {
				int n = (int) t;
				for (int c = lastCue + 1; c <= n && c - lastCue < 400; c++) {
					cue(c);
				}
				lastCue = n;
			}
		}
		// the illusion's whispers and sparkles (only the player it came to hears and sees them)
		if (illusion && (illusionFadeAt < 0 || scene == JourneyScene.DESCENT)) {
			Vec3 a = illusionPos;
			if (now >= nextWhisper) {
				nextWhisper = now + 50 + mc.level.random.nextInt(70);
				mc.level.playLocalSound(a.x, a.y - 1.0, a.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 0.32f,
						0.45f + mc.level.random.nextFloat() * 0.35f, false);
			}
			if (!hornPlayed && now - illusionStart > 90) {
				hornPlayed = true;
				mc.level.playLocalSound(a.x, a.y - 2.0, a.z, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(), SoundSource.AMBIENT, 0.12f, 0.55f, false);
			}
			// (the golden motes are drawn by IllusionRenderer: slow, few, and only where they belong)
		}
		if (illusionFadeAt >= 0 && now - illusionFadeAt > ILLUSION_FADE + 2 && scene != JourneyScene.DESCENT) {
			illusion = false;
			illusionFadeAt = -1;
		}
	}

	private static void cue(int t) {
		switch (scene) {
			case TRAIN -> {
				if (t == 100) {
					QuirkCameraFx.shake(1.8f, 26);
				}
			}
			case RIDE -> {
				if (t == JourneyGeometry.RIDE_DEPART) {
					QuirkCameraFx.shake(0.6f, 30);
				}
				if (t == JourneyGeometry.RIDE_FLASH) {
					QuirkCameraFx.shake(7f, 22);
					QuirkCameraFx.fovPunch(0.2f, 14);
				}
				if (t == JourneyGeometry.RIDE_BRAKE + 10) {
					QuirkCameraFx.shake(1.2f, 30);
				}
			}
			case TRANSFORM -> {
				if (t == JourneyScene.TRANSFORM_STRIKE) {
					QuirkCameraFx.shake(10f, 26);
					QuirkCameraFx.fovPunch(0.3f, 20);
					QuirkCameraFx.vignette(0xFFE27A, 0.5f, 30);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- the world: the illusion

	private static void world(WorldRenderContext ctx) {
		if (!illusion || ctx.matrixStack() == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		float age = Math.max(0f, (now - illusionStart) + pt);
		// it materialises out of a gold shimmer (the shimmer first, then the stone and iron, with a few flickers)
		float solid = IllusionRenderer.materialise(age);
		float shimmer = Mth.clamp(age / 6f, 0f, 1f) * (0.14f + 0.6f * (1f - solid));
		if (scene == JourneyScene.DESCENT) {
			float t = time(pt);
			solid = Math.max(solid, Mth.clamp(t / 8f, 0f, 1f));
			shimmer = Math.max(0.14f, shimmer) * (1f - Mth.clamp((t - 24f) / 24f, 0f, 1f)); // it settles as the light goes
		} else if (illusionFadeAt >= 0) {
			// a soft fade: the stone thins away, the shimmer swells a little and goes with it
			float f = Mth.clamp(((now - illusionFadeAt) + pt) / ILLUSION_FADE, 0f, 1f);
			float sf = f * f * (3 - 2 * f);
			solid *= 1f - sf;
			shimmer = (shimmer + 0.35f * Mth.sin(f * Mth.PI)) * (1f - sf);
		}
		net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(illusionPos.x, illusionPos.y + 0.2, illusionPos.z);
		int packed = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, at);
		float night = Mth.clamp((1f - mc.level.getSkyDarken(pt)) / 0.75f, 0f, 1f);
		IllusionRenderer.Lighting lit = new IllusionRenderer.Lighting(net.minecraft.client.renderer.LightTexture.sky(packed),
				net.minecraft.client.renderer.LightTexture.block(packed), night);
		IllusionRenderer.draw(ctx.matrixStack(), mc.renderBuffers().bufferSource(), ctx.camera(), illusionPos, illusionYaw, solid, shimmer, age, lit);
	}

	/** Ticks the illusion takes to fade away. */
	private static final float ILLUSION_FADE = 30f;

	// ---------------------------------------------------------------- the camera

	private static Entity entity() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || entityId < 0 ? null : mc.level.getEntity(entityId);
	}

	private static Vec3 o(double x, double y, double z) {
		return anchor.add(x + 0.5, y, z + 0.5);
	}

	// the descent's shot (ticks): up behind the lamps, then the glide down over the stairs; the light starts to go at DESCENT_DIM
	// and is gone at JourneyScene.DESCENT_BLACK; the platform scene holds the dark a moment, then fades in
	static final int DESCENT_RISE = 26;
	static final int DESCENT_SINK_END = 62;
	static final int DESCENT_DIM = 20;
	static final int TRAIN_DARK_HOLD = 8;
	static final int TRAIN_FADE_IN = 30;

	/** The colour to cover the "Loading terrain" screen with while a journey crosses worlds under a fade (0: leave it). */
	public static int loadingCover() {
		Minecraft mc = Minecraft.getInstance();
		if (scene == JourneyScene.HOME || scene == JourneyScene.TRANSFORM || (mc.level != null && mc.level.getGameTime() - homeAt < 60)) {
			return 0xFFFFFFFF;
		}
		return scene != null ? 0xFF000000 : 0;
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
		Entity e = entity();
		switch (scene) {
			case DESCENT -> {
				Vec3 f = Vec3.directionFromRotation(0, sceneYaw);
				Vec3 up = new Vec3(0, 1, 0);
				Vec3 eye = player.getEyePosition(pt);
				Vec3 a = anchor;
				// v2: one continuous glide -- from your eyes up behind the lamps, then forward and down over the opening, a faint
				// step-bob as it goes, looking ever deeper down the stairs into the doorway while the light goes (fade())
				// your own eyes going down: no body in the shot (it stood right under the camera), no hands (CutsceneHands)
				pov = true;
				// low enough to pass under the sign, which frames the top of the shot
				Vec3 over = a.add(f.scale(-1.3)).add(up.scale(1.9));
				Vec3 deep = a.add(f.scale(0.9)).add(up.scale(0.45));
				Vec3 lookStairs = a.add(f.scale(2.6)).add(up.scale(-2.2));
				Vec3 lookDoor = a.add(f.scale(IllusionRenderer.LANDING + 2.0)).add(up.scale(-3.1));
				if (t < DESCENT_RISE) {
					double k = ease(t / DESCENT_RISE);
					cam = eye.lerp(over, k);
					look = a.add(up.scale(0.6)).lerp(lookStairs, k);
				} else {
					double raw = Mth.clamp((t - DESCENT_RISE) / (double) (DESCENT_SINK_END - DESCENT_RISE), 0.0, 1.0);
					double k = raw * raw * (3 - 2 * raw);
					double bob = 0.03 * Math.sin(raw * Math.PI * 8) * Math.sin(raw * Math.PI);
					cam = over.lerp(deep, k).add(up.scale(bob));
					look = lookStairs.lerp(lookDoor, k);
				}
			}
			case TRAIN -> {
				SubwayTrainEntity train = e instanceof SubwayTrainEntity tr ? tr : null;
				double tx = train != null ? train.getPosition(pt).x : anchor.x + 0.5 + JourneyGeometry.arriveOffset(t - JourneyScene.TRAIN_ARRIVE);
				if (t < 88) {
					cam = o(-15.5, 2.7, 2.6);
					double front = Math.max(tx + JourneyGeometry.TRAIN_HALF, anchor.x - 70);
					look = new Vec3(Math.min(front, cam.x - 6), anchor.y + 1.8, anchor.z + 0.5);
				} else {
					double k = ease((t - 88) / 64.0);
					cam = o(5, 3.3, 7.6).lerp(o(1.5, 2.6, 6.8), k);
					look = new Vec3(tx + 3, anchor.y + 2.0, anchor.z + 0.5);
				}
			}
			case RIDE -> {
				SubwayTrainEntity train = e instanceof SubwayTrainEntity tr ? tr : null;
				Vec3 T = train != null ? train.getPosition(pt) : JourneyGeometry.track(blockAnchor(), JourneyGeometry.rideOffset(t));
				double car = JourneyGeometry.CAR_OFFSET;
				if (t < 72) {
					cam = T.add(car + 6.4, 2.7, 0.85);
					look = T.add(car - 4.0, 1.7, -0.75);
				} else if (t < 116) {
					pov = true;
					cam = T.add(JourneyGeometry.SEAT_X + 0.05, JourneyGeometry.SEAT_Y + 1.25, JourneyGeometry.SEAT_Z + 0.1);
					look = cam.add(0.45, -0.08, 1.0);
				} else if (t < JourneyGeometry.RIDE_FLASH + 4) {
					cam = T.add(car + 0.8, 2.45, 1.0);
					look = T.add(car + 6.5, 2.1, 3.2);
				} else if (t < 198) {
					cam = o(236, 3.4, 2.4);
					look = T.add(JourneyGeometry.TRAIN_HALF, 1.9, 0);
					if (T.x + JourneyGeometry.TRAIN_HALF > cam.x - 1.5) {
						look = new Vec3(cam.x + 6, anchor.y + 2.2, anchor.z + 0.5).lerp(T.add(0, 2, 0), 0.3);
					}
				} else {
					double k = ease((t - 198) / 64.0);
					cam = o(307, 10.5, 9.5).lerp(o(303, 8.0, 8.5), k);
					look = T.add(5, 2.2, 0);
				}
			}
			case MEETING -> {
				Vec3 w = e != null ? e.getPosition(pt) : JourneyGeometry.throneSeat(blockAnchor());
				Vec3 me = player.getPosition(pt);
				if (t < JourneyScene.MEET_LINE1) {
					double k = ease(t / (double) JourneyScene.MEET_LINE1);
					cam = o(316.0, 5.6, -3.4).lerp(o(317.3, 5.9, -2.7), k);
					look = w.add(0, 1.3, 0).lerp(w.add(0, 1.6, 0), k);
				} else if (t < JourneyScene.MEET_LINE3) {
					double k = ease((t - JourneyScene.MEET_LINE1) / 94.0);
					cam = w.add(-2.6, 1.35, -0.8).lerp(w.add(-2.1, 1.45, -0.55), k);
					look = w.add(0, 1.62, 0);
				} else {
					cam = w.add(0.7, 2.3, 1.15);
					look = me.add(0, 1.4, 0);
				}
			}
			case PROMPT -> {
				Vec3 me = player.getPosition(pt);
				double drift = Math.sin(t * 0.01) * 0.3;
				cam = me.add(2.8, 1.75, -1.5 + drift);
				look = me.add(0, 1.45, 0);
			}
			case TRANSFORM -> {
				Vec3 me = player.getPosition(pt);
				Vec3 w = e != null ? e.getPosition(pt) : JourneyGeometry.throneStand(blockAnchor());
				if (t < JourneyScene.TRANSFORM_STRIKE) {
					cam = me.add(2.3, 1.55, 0.9);
					look = me.add(0, 1.55, 0);
				} else if (t < JourneyScene.TRANSFORM_LINE1) {
					double k = (t - JourneyScene.TRANSFORM_STRIKE) / (double) (JourneyScene.TRANSFORM_LINE1 - JourneyScene.TRANSFORM_STRIKE);
					double ang = Math.toRadians(165 + 90 * ease(k));
					double rad = 5.2 - 1.0 * k;
					cam = me.add(Math.cos(ang) * rad, 0.5 + 0.7 * k, Math.sin(ang) * rad);
					look = me.add(0, 1.3, 0);
				} else {
					cam = o(317.0, 6.4, 4.6);
					look = me.add(0, 1.5, 0).lerp(w.add(0, 1.6, 0), 0.5);
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

	private static net.minecraft.core.BlockPos blockAnchor() {
		return net.minecraft.core.BlockPos.containing(anchor);
	}

	// ---------------------------------------------------------------- the HUD

	private record Line(int from, int to, String key, boolean wizard) {
	}

	private static final List<Line> MEET_LINES = List.of(new Line(JourneyScene.MEET_LINE1, JourneyScene.MEET_LINE2 - 2, "meet1", true),
			new Line(JourneyScene.MEET_LINE2, JourneyScene.MEET_LINE3 - 2, "meet2", true), new Line(JourneyScene.MEET_LINE3, 206, "meet3", true));
	private static final List<Line> TRANSFORM_LINES = List.of(new Line(JourneyScene.TRANSFORM_LINE1, JourneyScene.TRANSFORM_LINE2 - 2, "after1", true),
			new Line(JourneyScene.TRANSFORM_LINE2, JourneyScene.TRANSFORM_WHITE + 6, "after2", true));
	private static final List<Line> TRAIN_LINES = List.of(new Line(28, 92, "station", false));
	private static final List<Line> RIDE_LINES = List.of(new Line(206, 262, "rock", false));

	private static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (scene == null || mc.player == null) {
			return;
		}
		float t = time(delta.getGameTimeDeltaPartialTick(false));
		int w = g.guiWidth(), h = g.guiHeight();
		if (scene != JourneyScene.HOME) {
			float in = Mth.clamp(t / 12f, 0f, 1f);
			if (scene != JourneyScene.PROMPT && scene != JourneyScene.DESCENT) {
				in *= Mth.clamp((scene.length - t) / 10f, 0f, 1f);
			}
			if (scene == JourneyScene.TRAIN || scene == JourneyScene.MEETING || scene == JourneyScene.TRANSFORM || scene == JourneyScene.PROMPT) {
				in = 1f; // continuing straight on from the previous scene's bars
			}
			int bar = (int) (Math.max(36, h * 0.12f) * in);
			g.fill(0, 0, w, bar, 0xFF000000);
			g.fill(0, h - bar, w, h, 0xFF000000);
			if (scene == JourneyScene.RIDE && t >= JourneyGeometry.RIDE_GLYPHS && t < JourneyGeometry.RIDE_FLASH + 12) {
				glyphs(g, mc, w, h, t);
			}
			if (scene == JourneyScene.TRANSFORM && t >= JourneyScene.TRANSFORM_SHOUT && t < JourneyScene.TRANSFORM_STRIKE + 3) {
				shout(g, mc, w, h, t);
			}
			lines(g, mc, w, h, t, bar);
			if (scene == JourneyScene.PROMPT && !(mc.screen instanceof SayShazamScreen)) {
				promptHint(g, mc, w, h, t);
			}
		}
		int fade = fade(t);
		if ((fade >>> 24) > 0) {
			g.fill(0, 0, w, h, fade);
		}
	}

	private static void lines(GuiGraphics g, Minecraft mc, int w, int h, float t, int bar) {
		List<Line> lines = switch (scene) {
			case MEETING -> MEET_LINES;
			case TRANSFORM -> TRANSFORM_LINES;
			case TRAIN -> TRAIN_LINES;
			case RIDE -> RIDE_LINES;
			default -> List.of();
		};
		for (Line l : lines) {
			if (t < l.from || t > l.to) {
				continue;
			}
			float a = Mth.clamp((t - l.from) / 6f, 0f, 1f) * Mth.clamp((l.to - t) / 6f, 0f, 1f);
			int alpha = Math.max(4, (int) (a * 255));
			if (!l.wizard) {
				// a location caption, top left, in the bar
				Component c = Component.translatable("subtitle.projecthero.shazam_journey." + l.key).withStyle(ChatFormatting.ITALIC);
				int maxW = Math.max(80, w / 2);
				int y = Math.max(6, bar / 2 - 4);
				for (FormattedCharSequence s : mc.font.split(c, maxW)) {
					g.drawString(mc.font, s, 14, y, (alpha << 24) | 0xE8C870, true);
					y += 10;
				}
				continue;
			}
			MutableComponent text = l.key.equals("meet2") ? Component.translatable("subtitle.projecthero.shazam_journey." + l.key, mc.player.getName())
					: Component.translatable("subtitle.projecthero.shazam_journey." + l.key);
			Component c = Component.translatable("subtitle.projecthero.shazam_journey.speaker").withStyle(ChatFormatting.GOLD)
					.append(Component.literal(": ").withStyle(ChatFormatting.GOLD)).append(text.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
			int maxW = Math.min(w - 40, 360);
			List<FormattedCharSequence> split = mc.font.split(c, maxW);
			int y = h - bar + Math.max(4, (bar - split.size() * 10) / 2);
			for (FormattedCharSequence s : split) {
				g.drawString(mc.font, s, (w - mc.font.width(s)) / 2, y, (alpha << 24) | 0xFFFFFF, true);
				y += 10;
			}
		}
	}

	/** Strange glyphs drifting across the view as the walls turn to rock. */
	private static void glyphs(GuiGraphics g, Minecraft mc, int w, int h, float t) {
		float k = Mth.clamp((t - JourneyGeometry.RIDE_GLYPHS) / 16f, 0f, 1f) * Mth.clamp((JourneyGeometry.RIDE_FLASH + 12 - t) / 8f, 0f, 1f);
		ResourceLocation alt = ResourceLocation.withDefaultNamespace("alt");
		for (int i = 0; i < 34; i++) {
			double seed = i * 12.9898;
			double sx = Math.abs(Math.sin(seed) * 43758.5453) % 1.0, sy = Math.abs(Math.cos(seed * 1.3) * 24634.6345) % 1.0;
			double speed = 0.4 + (Math.abs(Math.sin(seed * 2.1)) % 1.0) * 1.2;
			int x = (int) ((sx * w - t * speed * 3.0) % w);
			if (x < 0) {
				x += w;
			}
			int y = (int) (h * 0.18 + sy * h * 0.64 + Math.sin(t * 0.08 + i) * 6);
			char ch = (char) ('a' + (i * 7 + (int) (t / 6)) % 26);
			Component c = Component.literal(String.valueOf(ch)).withStyle(s -> s.withFont(alt));
			int a = (int) (k * (120 + 100 * Math.abs(Math.sin(t * 0.2 + i))));
			g.pose().pushPose();
			g.pose().translate(x, y, 0);
			float sc = 1.5f + (float) (Math.abs(Math.sin(seed * 3.7)) % 1.0) * 1.8f;
			g.pose().scale(sc, sc, 1f);
			g.drawString(mc.font, c, 0, 0, (Mth.clamp(a, 0, 255) << 24) | (i % 3 == 0 ? 0xFFFFFF : 0xFFD24A), false);
			g.pose().popPose();
		}
	}

	/** "SHAZAM!" -- the shout, huge and shaking, then the bolt. */
	private static void shout(GuiGraphics g, Minecraft mc, int w, int h, float t) {
		float a = Mth.clamp((t - JourneyScene.TRANSFORM_SHOUT) / 3f, 0f, 1f) * Mth.clamp((JourneyScene.TRANSFORM_STRIKE + 3 - t) / 3f, 0f, 1f);
		Component c = Component.translatable("subtitle.projecthero.shazam_journey.shout").withStyle(ChatFormatting.BOLD);
		float grow = 3.2f + Math.min(1.2f, (t - JourneyScene.TRANSFORM_SHOUT) * 0.08f);
		float jx = (float) (Math.sin(t * 7.3) * 2.0), jy = (float) (Math.cos(t * 9.1) * 2.0);
		g.pose().pushPose();
		g.pose().translate(w / 2f + jx, h * 0.36f + jy, 0);
		g.pose().scale(grow, grow, 1f);
		int tw = mc.font.width(c);
		int alpha = Math.max(4, (int) (a * 255));
		g.drawString(mc.font, c, -tw / 2 + 1, -4 + 1, (alpha << 24) | 0x7A0A10, false);
		g.drawString(mc.font, c, -tw / 2, -4, (alpha << 24) | 0xFFD24A, false);
		g.pose().popPose();
	}

	/** The prompt with its screen closed: "Say SHAZAM!" and how. */
	private static void promptHint(GuiGraphics g, Minecraft mc, int w, int h, float t) {
		Component big = Component.translatable("screen.projecthero.shazam_journey.button").withStyle(ChatFormatting.BOLD);
		float pulse = 0.75f + 0.25f * Mth.sin(t * 0.25f);
		g.pose().pushPose();
		g.pose().translate(w / 2f, h * 0.42f, 0);
		g.pose().scale(2.2f, 2.2f, 1f);
		g.drawString(mc.font, big, -mc.font.width(big) / 2, -4, ((int) (pulse * 255) << 24) | 0xFFD24A, true);
		g.pose().popPose();
		Component hint = Component.translatable("hud.projecthero.shazam_journey.prompt_hint");
		int y = (int) (h * 0.42f) + 16;
		for (FormattedCharSequence s : mc.font.split(hint, Math.min(w - 40, 320))) {
			g.drawString(mc.font, s, (w - mc.font.width(s)) / 2, y, 0xFFCCCCCC, true);
			y += 10;
		}
	}

	/** ARGB of the full-screen fade at scene time {@code t}. */
	private static int fade(float t) {
		float a = 0f;
		int rgb = 0x000000;
		switch (scene) {
			case DESCENT -> {
				// the light dims gradually as the camera sinks, easing into full black (then a beat of darkness and the horn)
				float k = Mth.clamp((t - DESCENT_DIM) / (float) (JourneyScene.DESCENT_BLACK - DESCENT_DIM), 0f, 1f);
				a = (float) Math.pow(k * k * (3 - 2 * k), 1.4);
			}
			case TRAIN -> {
				// a short hold in the dark, then a slow ease up into the platform
				float k = Mth.clamp((t - TRAIN_DARK_HOLD) / (float) TRAIN_FADE_IN, 0f, 1f);
				a = 1f - k * k * (3 - 2 * k);
			}
			case RIDE -> {
				if (t >= JourneyGeometry.RIDE_FLASH && t < JourneyGeometry.RIDE_FLASH + 12) {
					a = 1f - (t - JourneyGeometry.RIDE_FLASH) / 12f;
					rgb = 0xFFFFFF;
				}
			}
			case TRANSFORM -> {
				rgb = 0xFFFFFF;
				if (t >= JourneyScene.TRANSFORM_STRIKE && t < JourneyScene.TRANSFORM_STRIKE + 12) {
					a = 0.95f * (1f - (t - JourneyScene.TRANSFORM_STRIKE) / 12f);
				}
				if (t >= JourneyScene.TRANSFORM_WHITE) {
					a = Mth.clamp((t - JourneyScene.TRANSFORM_WHITE) / 12f, 0f, 1f);
				}
			}
			case HOME -> {
				rgb = 0xFFFFFF;
				a = Mth.clamp(1f - t / JourneyScene.HOME.length, 0f, 1f);
			}
			default -> {
			}
		}
		return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | rgb;
	}

	// ---------------------------------------------------------------- the train's rumble

	/** The wheels on the rails: louder and higher as the train goes faster; follows the train (or its rider). */
	static final class TrainSound extends AbstractTickableSoundInstance {
		private final SubwayTrainEntity train;

		TrainSound(SubwayTrainEntity train) {
			super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
			this.train = train;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.0001f;
			this.x = train.getX();
			this.y = train.getY() + 1.5;
			this.z = train.getZ();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			if (train.isRemoved()) {
				stop();
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			double speed = train.speed(0f);
			Vec3 at = train.position().add(0, 1.5, 0);
			if (mc.player != null) {
				// the nearest point of the train to the listener
				double px = Mth.clamp(mc.player.getX(), train.getX() - JourneyGeometry.TRAIN_HALF, train.getX() + JourneyGeometry.TRAIN_HALF);
				at = new Vec3(px, at.y, train.getZ());
			}
			x = at.x;
			y = at.y;
			z = at.z;
			float k = (float) Mth.clamp(speed / JourneyGeometry.RIDE_SPEED, 0.0, 1.0);
			volume = k < 0.02f ? 0.0001f : 0.25f + 0.85f * k;
			pitch = 0.5f + 0.45f * k;
		}
	}
}
