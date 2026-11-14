package com.projecthero.mod.client.doctorfate;

import java.util.HashSet;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.hulk.HulkMindScreen;
import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.network.FateActionPayload;
import com.projecthero.mod.doctorfate.network.FateTrialPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 round 2 (user: "player must go to the same mindscape place like with hulk and alien x, the mindscape should be a hallway that
 * ends in a large circular library with a ton of books and a screen for the player to watch whats happening in the outside world"):
 * Nabu's library, the mind Kent Nelson is trapped in while Nabu fights with his body. The Hulk / Alien X illusion
 * ({@link HulkMindScreen}): the player stays in the world (Nabu drives the body, {@code DoctorFateClient}); the world is rendered as
 * usual from the body's own eyes (first person), and at the head of the HUD pass ({@code GuiHulkRampageMixin}) that frame is copied
 * onto the library's great screen and the library is drawn over the whole window with its own camera.
 * <ul>
 *   <li>He arrives at the far end of a 64-block sandstone hallway; the door behind him slams shut; walking it, the view stretches (a
 *   slow dolly zoom), and at its end a gold door opens into the library -- and slams behind him once he's in. There's no way back.</li>
 *   <li>The library ({@link FateLibraryMesh}): bookshelf walls on four tiers, gold ledges, a lapis ceiling, twelve lecterns, the Eye of
 *   Nabu at the heart (a floating golden ankh and eye that burn redder as he angers), and the screen on the far wall.</li>
 *   <li>WASD + mouse walk it (vanilla speeds and bobbing). Right-click (or R) at a lectern reads its book; at the Eye it faces Nabu
 *   (his challenge, or the willpower test). Space is the willpower test's push. Everything is the server's
 *   ({@code DoctorFateNabuTrial}); this only walks, shows and asks.</li>
 *   <li>Freed, the camera flies into the screen and he is back in his body under a gold flash.</li>
 * </ul>
 * Every other key binding is swallowed except chat, the player list, screenshots and fullscreen. GPU resources are freed when it ends.
 */
public final class FateLibrary {
	static final float EYE = 1.62f;
	static final float RADIUS = 0.3f;
	static final double WALK = 0.21585;
	static final double SPRINT = 0.2806;
	static final long ENTER_FADE_MS = 2200L;
	static final long EXIT_MS = 900L;
	static final long FLASH_MS = 800L;
	static final long SLAM_MS = 450L;
	static final float EXIT_END_DIST = 1.8f;
	static final float READ_REACH = 2.2f;

	private static final int ZONE_NONE = -1;
	private static final int ZONE_EYE = 100;

	// ---- the trial, as the server last told it
	static int stage = 1;
	static int anger;
	static int found;
	static int needed = 3;
	static int wraths;
	static boolean willOn;
	static float will;
	static float band;
	static int progress;
	static int progressNeeded = 200;
	static int outTicks;
	static final int[] lecterns = new int[FateLibraryMesh.LECTERNS];
	private static long verdictMs = -10_000L;
	private static int verdict = -1;

	// ---- state
	private static boolean on;
	private static boolean ended;
	private static long enteredMs;
	private static long exitStartMs = -1L;
	private static long flashMs = -1L;
	private static CameraType savedCamera;
	private static long startDoorMs = -1L;
	private static long libDoorMs = -1L;
	private static boolean inside;
	private static long lastAmbientMs;

	// the walker
	private static double wx;
	private static double wz;
	private static double pwx;
	private static double pwz;
	private static double vx;
	private static double vz;
	private static float yaw = 180.0f;
	private static float pitch;
	private static float walkDist;
	private static float pWalkDist;
	private static float bob;
	private static float pBob;
	private static float nextStep = 1.0f;
	private static float fovMul = 1.0f;
	private static float pFovMul = 1.0f;
	private static int zone = ZONE_NONE;

	private static double ex;
	private static double ey;
	private static double ez;
	private static float eyaw;
	private static float epitch;
	private static float efov;
	private static double lastCx;
	private static double lastCy;
	private static double lastCz;
	private static float lastYaw;
	private static float lastPitch;
	private static float lastFov;

	private static RenderTarget pov;
	private static FateLibraryMesh mesh;

	private FateLibrary() {
	}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(FateLibrary::tick);
		HudRenderCallback.EVENT.register(FateLibrary::hud);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FateLibrary::reset));
	}

	/** The library is up (he's in it, or the flight into the screen is finishing). */
	public static boolean showing() {
		return on || exitStartMs >= 0L;
	}

	public static void turn(double dx, double dy) {
		if (exitStartMs >= 0L) {
			return;
		}
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90.0f, 90.0f);
	}

	/** A {@link FateTrialPayload} from the server. */
	static void receive(FateTrialPayload p) {
		Minecraft mc = Minecraft.getInstance();
		int[] d = p.data();
		switch (p.op()) {
			case FateTrialPayload.STATE -> {
				if (d.length < 11) {
					return;
				}
				stage = d[0];
				anger = d[1];
				found = d[2];
				needed = d[3];
				wraths = d[4];
				willOn = d[5] != 0;
				will = d[6] / 1000.0f;
				band = d[7] / 1000.0f;
				progress = d[8];
				progressNeeded = Math.max(1, d[9]);
				outTicks = d[10];
				for (int i = 0; i < lecterns.length && 11 + i < d.length; i++) {
					lecterns[i] = d[11 + i];
				}
			}
			case FateTrialPayload.BOOK -> {
				if (d.length >= 3) {
					mc.setScreen(FateTrialScreen.book(d[1] == 1, d[2]));
					play(mc, SoundEvents.BOOK_PAGE_TURN, 0.9f, 1.0f);
				}
			}
			case FateTrialPayload.QUESTION -> {
				if (d.length >= 4) {
					mc.setScreen(FateTrialScreen.question(d[0], new int[] { d[1], d[2], d[3] }));
				}
			}
			case FateTrialPayload.VERDICT -> {
				if (d.length >= 1) {
					verdict = d[0];
					verdictMs = Util.getMillis();
					if (mc.screen instanceof FateTrialScreen && (verdict == FateTrialPayload.V_WRONG || verdict == FateTrialPayload.V_RIGHT
							|| verdict == FateTrialPayload.V_WRATH)) {
						mc.setScreen(null);
					}
					switch (verdict) {
						case FateTrialPayload.V_WRONG, FateTrialPayload.V_WILL_FAILED -> play(mc, SoundEvents.ELDER_GUARDIAN_CURSE, 0.4f, 0.8f);
						case FateTrialPayload.V_WRATH -> play(mc, SoundEvents.WARDEN_ROAR, 0.5f, 1.4f);
						case FateTrialPayload.V_RIGHT, FateTrialPayload.V_PAGES_FOUND -> play(mc, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
						case FateTrialPayload.V_TOME -> play(mc, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.6f);
						default -> {
						}
					}
				}
			}
			case FateTrialPayload.END -> ended = true;
			default -> {
			}
		}
	}

	private static void reset() {
		on = false;
		ended = false;
		exitStartMs = -1L;
		flashMs = -1L;
		zone = ZONE_NONE;
		restoreCamera(Minecraft.getInstance());
		free();
	}

	private static void restoreCamera(Minecraft mc) {
		if (savedCamera != null) {
			mc.options.setCameraType(savedCamera);
			savedCamera = null;
		}
	}

	private static void free() {
		if (pov != null) {
			RenderTarget t = pov;
			pov = null;
			RenderSystem.recordRenderCall(t::destroyBuffers);
		}
		mesh = null;
	}

	// ---------------------------------------------------------------- tick

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			if (showing() || pov != null || savedCamera != null) {
				reset();
			}
			return;
		}
		boolean held = DoctorFate.possessed(mc.player) && !ended;
		long ms = Util.getMillis();
		if (held && !on) {
			enter(mc);
		}
		if (!held && on) {
			on = false;
			if (mc.screen instanceof FateTrialScreen) {
				mc.setScreen(null);
			}
			if (mc.player.isDeadOrDying()) {
				reset();
				return;
			}
			startExit(mc);
		}
		if (!DoctorFate.possessed(mc.player)) {
			ended = false;
		}
		if (exitStartMs >= 0L && ms - exitStartMs >= EXIT_MS) {
			exitStartMs = -1L;
			flashMs = ms;
			restoreCamera(mc);
		}
		if (!showing()) {
			return;
		}
		if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
			mc.options.setCameraType(CameraType.FIRST_PERSON); // the screen shows the world through the body's own eyes
		}
		swallowInputs(mc);
		if (on) {
			walk(mc, ms);
			ambience(mc, ms);
		}
	}

	private static void enter(Minecraft mc) {
		on = true;
		ended = false;
		enteredMs = Util.getMillis();
		exitStartMs = -1L;
		flashMs = -1L;
		wx = 0.0;
		wz = FateLibraryMesh.HALL_END - 2.5;
		pwx = wx;
		pwz = wz;
		vx = 0.0;
		vz = 0.0;
		yaw = 180.0f;
		pitch = 0.0f;
		walkDist = 0.0f;
		pWalkDist = 0.0f;
		bob = 0.0f;
		pBob = 0.0f;
		nextStep = 1.0f;
		fovMul = 1.0f;
		pFovMul = 1.0f;
		zone = ZONE_NONE;
		startDoorMs = -1L;
		libDoorMs = -1L;
		inside = false;
		stage = 1;
		anger = 0;
		found = 0;
		willOn = false;
		java.util.Arrays.fill(lecterns, 0);
		if (savedCamera == null) {
			savedCamera = mc.options.getCameraType();
		}
	}

	private static void startExit(Minecraft mc) {
		exitStartMs = Util.getMillis();
		ex = lastCx;
		ey = lastCy;
		ez = lastCz;
		eyaw = lastYaw;
		epitch = lastPitch;
		efov = lastFov;
		play(mc, SoundEvents.BEACON_ACTIVATE, 0.6f, 1.2f);
	}

	private static Set<KeyMapping> allowed(Minecraft mc) {
		Set<KeyMapping> keep = new HashSet<>();
		keep.add(mc.options.keyChat);
		keep.add(mc.options.keyCommand);
		keep.add(mc.options.keyPlayerList);
		keep.add(mc.options.keyScreenshot);
		keep.add(mc.options.keyFullscreen);
		keep.add(mc.options.keySocialInteractions);
		return keep;
	}

	private static void swallowInputs(Minecraft mc) {
		Set<KeyMapping> keep = allowed(mc);
		boolean used = false;
		boolean r = false;
		int jumps = 0;
		while (mc.options.keyUse.consumeClick()) {
			used = true;
		}
		while (ModKeyBindings.ABILITY_1.consumeClick()) {
			r = true;
		}
		while (mc.options.keyJump.consumeClick()) {
			jumps++;
		}
		for (KeyMapping k : mc.options.keyMappings) {
			if (keep.contains(k)) {
				continue;
			}
			while (k.consumeClick()) {
				// swallowed
			}
			k.setDown(false);
		}
		if (!on || mc.screen != null) {
			return;
		}
		for (int i = 0; i < Math.min(jumps, 3); i++) {
			send(FateActionPayload.Action.STRUGGLE, 0); // the willpower test's push (the server ignores it otherwise)
			FateFx.struggled(mc.level.getGameTime());
		}
		if (used || r) {
			if (zone >= 0 && zone < FateLibraryMesh.LECTERNS) {
				send(FateActionPayload.Action.TRIAL_READ, zone);
			} else if (zone == ZONE_EYE) {
				send(FateActionPayload.Action.TRIAL_CONFRONT, 0);
			}
		}
	}

	static void send(FateActionPayload.Action action, int arg) {
		if (ClientPlayNetworking.canSend(FateActionPayload.TYPE)) {
			ClientPlayNetworking.send(new FateActionPayload(action, arg, net.minecraft.core.BlockPos.ZERO));
		}
	}

	private static boolean held(Minecraft mc, KeyMapping k) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(k);
		long win = mc.getWindow().getWindow();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
		}
		return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
				&& InputConstants.isKeyDown(win, key.getValue());
	}

	private static void walk(Minecraft mc, long ms) {
		pwx = wx;
		pwz = wz;
		pWalkDist = walkDist;
		pBob = bob;
		pFovMul = fovMul;
		float fwd = 0.0f;
		float strafe = 0.0f;
		boolean sprint = false;
		if (mc.screen == null && ms - enteredMs > 900L) {
			fwd = (held(mc, mc.options.keyUp) ? 1.0f : 0.0f) - (held(mc, mc.options.keyDown) ? 1.0f : 0.0f);
			strafe = (held(mc, mc.options.keyLeft) ? 1.0f : 0.0f) - (held(mc, mc.options.keyRight) ? 1.0f : 0.0f);
			sprint = fwd > 0.0f && held(mc, mc.options.keySprint);
		}
		float len = Mth.sqrt(fwd * fwd + strafe * strafe);
		if (len > 1.0f) {
			fwd /= len;
			strafe /= len;
		}
		boolean hall = wz > FateLibraryMesh.DOOR_Z - 0.5;
		// the hallway drags at him (it feels longer than it is)
		double speed = (sprint ? SPRINT : WALK) * (hall ? 0.8 : 1.0);
		float yr = yaw * Mth.DEG_TO_RAD;
		double tx = (strafe * Mth.cos(yr) - fwd * Mth.sin(yr)) * speed;
		double tz = (fwd * Mth.cos(yr) + strafe * Mth.sin(yr)) * speed;
		vx += (tx - vx) * 0.5;
		vz += (tz - vz) * 0.5;
		if (open(wx + vx, wz)) {
			wx += vx;
		} else {
			vx = 0.0;
		}
		if (open(wx, wz + vz)) {
			wz += vz;
		} else {
			vz = 0.0;
		}
		float moved = (float) Math.sqrt((wx - pwx) * (wx - pwx) + (wz - pwz) * (wz - pwz));
		walkDist += moved * 0.6f;
		bob += (Math.min(0.1f, moved) - bob) * 0.4f;
		// the dolly zoom down the hallway: the view widens as he goes, so its end seems to run away from him
		float stretch = hall ? 1.0f + 0.32f * Mth.clamp((float) ((FateLibraryMesh.HALL_END - wz) / FateLibraryMesh.HALL_LEN), 0.0f, 1.0f) : 1.0f;
		fovMul += ((sprint && moved > 0.1f ? 1.08f : 1.0f) * stretch - fovMul) * 0.15f;
		if (walkDist > nextStep) {
			nextStep = (float) Math.floor(walkDist) + 1.0f;
			play(mc, hall ? SoundEvents.STONE_STEP : SoundEvents.WOOD_STEP, 0.25f, 0.7f + mc.level.getRandom().nextFloat() * 0.08f);
		}
		// the doors: behind him at the start, and behind him once he's in
		if (startDoorMs < 0L && ms - enteredMs > 1600L) {
			startDoorMs = ms;
			play(mc, SoundEvents.IRON_DOOR_CLOSE, 1.0f, 0.5f);
			play(mc, SoundEvents.ANVIL_LAND, 0.25f, 0.5f);
		}
		if (!inside && wz < FateLibraryMesh.DOOR_Z - 2.5) {
			inside = true;
			libDoorMs = ms;
			play(mc, SoundEvents.IRON_DOOR_CLOSE, 1.0f, 0.45f);
			play(mc, SoundEvents.ANVIL_LAND, 0.35f, 0.4f);
			send(FateActionPayload.Action.TRIAL_ENTERED, 0);
		}
		zone = zoneAt(wx, wz);
	}

	private static void ambience(Minecraft mc, long ms) {
		if (ms - lastAmbientMs < 4500L) {
			return;
		}
		lastAmbientMs = ms + mc.level.getRandom().nextInt(4000);
		SoundEvent[] pool = { SoundEvents.BOOK_PAGE_TURN, SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.AMBIENT_CAVE.value(), SoundEvents.BEACON_AMBIENT };
		play(mc, pool[mc.level.getRandom().nextInt(pool.length)], 0.25f, 0.6f + mc.level.getRandom().nextFloat() * 0.3f);
	}

	/** Where the walker may stand. */
	static boolean open(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		boolean lib = r < FateLibraryMesh.R - 0.7 && z > FateLibraryMesh.SCREEN_Z + FateLibraryMesh.PLINTH_D + 0.4 + RADIUS;
		if (lib) {
			for (int i = 0; i < FateLibraryMesh.LECTERNS; i++) {
				double dx = x - FateLibraryMesh.lecternX(i);
				double dz = z - FateLibraryMesh.lecternZ(i);
				if (dx * dx + dz * dz < 0.8 * 0.8) {
					return false;
				}
			}
			if (!inside || z < FateLibraryMesh.DOOR_Z - 0.6) {
				return true;
			}
		}
		if (inside) {
			return false; // the door behind him is shut
		}
		float far = FateLibraryMesh.HALL_END - 0.8f;
		return Math.abs(x) < FateLibraryMesh.HW - RADIUS - 0.05 && z > FateLibraryMesh.DOOR_Z - 2.0 && z < far;
	}

	static int zoneAt(double x, double z) {
		if (x * x + z * z <= FateLibraryMesh.EYE_R * FateLibraryMesh.EYE_R) {
			return ZONE_EYE;
		}
		for (int i = 0; i < FateLibraryMesh.LECTERNS; i++) {
			double dx = x - FateLibraryMesh.lecternX(i);
			double dz = z - FateLibraryMesh.lecternZ(i);
			if (dx * dx + dz * dz <= READ_REACH * READ_REACH) {
				return i;
			}
		}
		return ZONE_NONE;
	}

	private static void play(Minecraft mc, SoundEvent ev, float volume, float pitch) {
		mc.getSoundManager().play(new SimpleSoundInstance(ev.getLocation(), SoundSource.PLAYERS, volume, pitch, SoundInstance.createUnseededRandom(),
				false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
	}

	private static double sq(double d) {
		return d * d;
	}

	// ---------------------------------------------------------------- rendering

	private static float exitT() {
		if (exitStartMs < 0L) {
			return 0.0f;
		}
		float t = Mth.clamp((Util.getMillis() - exitStartMs) / (float) EXIT_MS, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	static float screenWidth(Minecraft mc) {
		int w = Math.max(1, mc.getWindow().getWidth());
		int h = Math.max(1, mc.getWindow().getHeight());
		return Mth.clamp(FateLibraryMesh.SCREEN_H * w / (float) h, 8.0f, 17.0f);
	}

	/** 0..1: how far a door has slid shut. */
	private static float doorShut(long since, long ms) {
		if (since < 0L) {
			return 0.0f;
		}
		float t = Mth.clamp((ms - since) / (float) SLAM_MS, 0.0f, 1.0f);
		return t * t;
	}

	/** At the head of the HUD pass ({@code GuiHulkRampageMixin}). */
	public static void renderScene(DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		long ms = Util.getMillis();
		if (!showing() || mc.player == null || mc.level == null) {
			if (pov != null) {
				free();
			}
			return;
		}
		RenderTarget main = mc.getMainRenderTarget();
		int w = main.width;
		int h = main.height;
		if (w <= 0 || h <= 0) {
			return;
		}
		pov = HulkMindScreen.capture(pov, main, w, h);
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float screenW = screenWidth(mc);
		TextureAtlasSprite probe = FateLibraryMesh.sprite(mc, "bookshelf");
		if (mesh == null || mesh.screenW != screenW || mesh.probe != probe) {
			mesh = FateLibraryMesh.build(mc, screenW);
		}
		float half = screenW * 0.5f;
		float midY = FateLibraryMesh.SCREEN_Y0 + FateLibraryMesh.SCREEN_H * 0.5f;
		float sz = FateLibraryMesh.SCREEN_Z;

		float fovDeg = mc.options.fov().get().floatValue();
		float exitT = exitT();
		double cx;
		double cy;
		double cz;
		float cyaw;
		float cpitch;
		float bobAmt = 0.0f;
		float wd = 0.0f;
		float fov;
		if (exitStartMs >= 0L) {
			float endFov = 2.0f * (float) Math.atan((FateLibraryMesh.SCREEN_H * 0.5) / EXIT_END_DIST) * Mth.RAD_TO_DEG;
			cx = Mth.lerp(exitT, ex, 0.0);
			cy = Mth.lerp(exitT, ey, midY);
			cz = Mth.lerp(exitT, ez, sz + EXIT_END_DIST);
			cyaw = Mth.rotLerp(exitT, eyaw, 180.0f);
			cpitch = Mth.lerp(exitT, epitch, 0.0f);
			fov = Mth.lerp(exitT, efov, endFov);
		} else {
			cx = Mth.lerp(partial, pwx, wx);
			cz = Mth.lerp(partial, pwz, wz);
			cy = EYE;
			cyaw = yaw;
			cpitch = pitch;
			fov = Mth.clamp(fovDeg * Mth.lerp(partial, pFovMul, fovMul), 30.0f, 150.0f);
			if (mc.options.bobView().get()) {
				bobAmt = Mth.lerp(partial, pBob, bob);
				wd = Mth.lerp(partial, pWalkDist, walkDist);
			}
		}
		lastCx = cx;
		lastCy = cy;
		lastCz = cz;
		lastYaw = cyaw;
		lastPitch = cpitch;
		lastFov = fov;

		// Nabu's wrath shakes the whole library for a moment
		float shake = verdict == FateTrialPayload.V_WRATH ? Math.max(0.0f, 1.0f - (ms - verdictMs) / 1400.0f) : 0.0f;
		Matrix4f view = new Matrix4f();
		if (bobAmt > 0.0f) {
			float a = wd * Mth.PI;
			view.translate(Mth.sin(a) * bobAmt * 0.5f, -Math.abs(Mth.cos(a) * bobAmt), 0.0f);
			view.rotateZ(Mth.sin(a) * bobAmt * 3.0f * Mth.DEG_TO_RAD);
			view.rotateX(Math.abs(Mth.cos(a - 0.2f) * bobAmt) * 5.0f * Mth.DEG_TO_RAD);
		}
		if (shake > 0.0f) {
			view.rotateZ((Mth.sin(ms * 0.07f) * 2.5f * shake) * Mth.DEG_TO_RAD);
			view.translate(Mth.sin(ms * 0.11f) * 0.08f * shake, Mth.cos(ms * 0.13f) * 0.06f * shake, 0.0f);
		}
		view.rotateX(cpitch * Mth.DEG_TO_RAD);
		view.rotateY((cyaw + 180.0f) * Mth.DEG_TO_RAD);

		Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorting savedSort = RenderSystem.getVertexSorting();
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.identity();
		mv.mul(view);
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(fov * Mth.DEG_TO_RAD, w / (float) h, 0.05f, 400.0f),
				VertexSorting.DISTANCE_TO_ORIGIN);

		main.bindWrite(true);
		RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
		RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();
		RenderSystem.disableBlend();
		FogRenderer.setupNoFog();

		float time = (ms % 1_000_000L) / 1000.0f;
		float since = (ms - enteredMs) / 1000.0f;
		float power = Mth.clamp(since / 1.5f, 0.0f, 1.0f);
		float screenLight = Mth.lerp(exitT, power * (0.85f + 0.15f * Mth.sin(time * 1.1f)), 1.0f);
		float rage = Mth.clamp(anger / 100.0f, 0.0f, 1.0f);
		float lampLevel = (0.9f + 0.06f * Mth.sin(time * 7.3f) + 0.04f * Mth.sin(time * 13.1f)) * (1.0f - 0.25f * shake);

		// 1. the library and the hallway
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		mesh.emit(bb, cx, cy, cz, lampLevel, screenLight, rage);
		draw(bb);
		// 2. the doors (gold panels sliding shut)
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		TextureAtlasSprite gold = FateLibraryMesh.sprite(mc, "gold_block");
		door(bb, gold, FateLibraryMesh.HALL_END - 0.3f, doorShut(startDoorMs, ms), cx, cy, cz, lampLevel * 0.5f);
		door(bb, gold, FateLibraryMesh.DOOR_Z + 0.05f, inside ? doorShut(libDoorMs, ms) : 0.0f, cx, cy, cz, lampLevel);
		draw(bb);

		// 3. the screen: the world through the body's eyes, live
		double sd = Math.sqrt(cx * cx + sq(cy - midY) + sq(cz - sz));
		float far = 0.7f + 0.3f * (float) Math.exp(-Math.max(0.0, sd - 8.0) / 40.0);
		int c = Math.round(Mth.clamp(Mth.lerp(exitT, far * (0.9f + 0.1f * screenLight) * power, 1.0f), 0.0f, 1.0f) * 255.0f);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, pov.getColorTextureId());
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		float y0 = FateLibraryMesh.SCREEN_Y0;
		float y1 = y0 + FateLibraryMesh.SCREEN_H;
		float z = sz + 0.02f;
		bb.addVertex((float) (-half - cx), (float) (y0 - cy), (float) (z - cz)).setUv(0.0f, 0.0f).setColor(c, c, c, 255);
		bb.addVertex((float) (half - cx), (float) (y0 - cy), (float) (z - cz)).setUv(1.0f, 0.0f).setColor(c, c, c, 255);
		bb.addVertex((float) (half - cx), (float) (y1 - cy), (float) (z - cz)).setUv(1.0f, 1.0f).setColor(c, c, c, 255);
		bb.addVertex((float) (-half - cx), (float) (y1 - cy), (float) (z - cz)).setUv(0.0f, 1.0f).setColor(c, c, c, 255);
		draw(bb);

		// 4. light: rings and hieroglyphs on the floor, the Eye of Nabu, the books' glow, the screen's halo (additive, no depth writes)
		RenderSystem.enableBlend();
		RenderSystem.depthMask(false);
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		FateGlyphs.begin(bb, new Matrix4f(), new Vec3(cx, cy, cz));
		lights(time, rage, (1.0f - exitT) * power, half);
		draw(bb);

		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		mv.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(savedProj, savedSort);
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
	}

	/** A pair of gold door panels across the hallway at {@code z}, {@code shut} 0 (open, in the walls) to 1 (meeting in the middle). */
	private static void door(BufferBuilder bb, TextureAtlasSprite s, float z, float shut, double cx, double cy, double cz, float light) {
		if (shut <= 0.001f) {
			return;
		}
		float hw = FateLibraryMesh.HW;
		float h = FateLibraryMesh.HH;
		float panel = hw * shut;
		int col = Math.round(Mth.clamp(0.25f + 0.6f * light, 0.0f, 1.0f) * 255.0f);
		int f = Math.round(FateLibraryMesh.fog(Math.sqrt(cx * cx + sq(cz - z))) * col);
		for (int side = -1; side <= 1; side += 2) {
			float xOut = side * hw;
			float xIn = side * (hw - panel);
			for (float y = 0.0f; y < h - 1.0e-3f; y += 1.0f) {
				float yb = Math.min(h, y + 1.0f);
				float vTop = 1.0f - (yb - y);
				bb.addVertex((float) (Math.min(xOut, xIn) - cx), (float) (y - cy), (float) (z - cz)).setUv(s.getU0(), s.getV(1.0f)).setColor(f, f, f, 255);
				bb.addVertex((float) (Math.max(xOut, xIn) - cx), (float) (y - cy), (float) (z - cz)).setUv(s.getU1(), s.getV(1.0f)).setColor(f, f, f, 255);
				bb.addVertex((float) (Math.max(xOut, xIn) - cx), (float) (yb - cy), (float) (z - cz)).setUv(s.getU1(), s.getV(vTop)).setColor(f, f, f, 255);
				bb.addVertex((float) (Math.min(xOut, xIn) - cx), (float) (yb - cy), (float) (z - cz)).setUv(s.getU0(), s.getV(vTop)).setColor(f, f, f, 255);
			}
		}
	}

	private static void lights(float time, float rage, float a, float half) {
		if (a <= 0.01f) {
			return;
		}
		Vec3 ux = new Vec3(1, 0, 0);
		Vec3 uz = new Vec3(0, 0, 1);
		int gold = 0xFFC93A;
		int eyeCol = lerpColour(0xFFD24A, 0xFF4A20, rage);
		// the floor: rings with turning bands of hieroglyphs, and an ankh pointing at the screen
		FateGlyphs.ring(new Vec3(0, 0.16, 0), ux, uz, FateLibraryMesh.EYE_R, 0.05, 48, eyeCol, 0.8f * a, true);
		FateGlyphs.ring(new Vec3(0, 0.03, 0), ux, uz, 7.0, 0.05, 64, gold, 0.45f * a, true);
		FateGlyphs.band(new Vec3(0, 0.035, 0), ux, uz, 8.0, 18, 0.9, 0.035, time * 0.05, gold, 0.4f * a, 3);
		FateGlyphs.ring(new Vec3(0, 0.03, 0), ux, uz, 9.0, 0.04, 64, gold, 0.4f * a, true);
		FateGlyphs.ring(new Vec3(0, 0.03, 0), ux, uz, 15.4, 0.05, 96, gold, 0.35f * a, true);
		FateGlyphs.ankh(new Vec3(0, 0.04, -4.8), ux, new Vec3(0, 0, 1), 2.6, 0.06, gold, 0.5f * a);
		// a runner of light down the hallway's floor
		FateGlyphs.glow(new Vec3(-FateLibraryMesh.HW + 0.4, 0.03, FateLibraryMesh.DOOR_Z), new Vec3(-FateLibraryMesh.HW + 0.4, 0.03, FateLibraryMesh.HALL_END),
				new Vec3(0, 1, 0), 0.03, gold, 0.35f * a);
		FateGlyphs.glow(new Vec3(FateLibraryMesh.HW - 0.4, 0.03, FateLibraryMesh.DOOR_Z), new Vec3(FateLibraryMesh.HW - 0.4, 0.03, FateLibraryMesh.HALL_END),
				new Vec3(0, 1, 0), 0.03, gold, 0.35f * a);
		// the Eye of Nabu: a floating ankh turning slowly, an Eye of Horus over it, a pillar of faint light
		Vec3 eye = new Vec3(0, 4.6 + 0.2 * Mth.sin(time * 0.9f), 0);
		float spin = time * 0.4f;
		Vec3 u = new Vec3(Mth.cos(spin), 0, Mth.sin(spin));
		Vec3 up = new Vec3(0, 1, 0);
		float pulse = 0.75f + 0.25f * Mth.sin(time * (2.0f + 5.0f * rage));
		FateGlyphs.ankh(eye, u, up, 3.2, 0.09, eyeCol, pulse * a);
		FateGlyphs.glyph(1, eye.add(0, 2.6, 0), u, up, 2.0, 0.07, eyeCol, pulse * a);
		FateGlyphs.gradient(new Vec3(-0.6, 0.15, 0).add(0, 0, 0), new Vec3(0.6, 0.15, 0), new Vec3(0.6, 8, 0), new Vec3(-0.6, 8, 0), eyeCol, 0.12f * a, 0f);
		FateGlyphs.gradient(new Vec3(0, 0.15, -0.6), new Vec3(0, 0.15, 0.6), new Vec3(0, 8, 0.6), new Vec3(0, 8, -0.6), eyeCol, 0.12f * a, 0f);
		if (zone == ZONE_EYE) {
			FateGlyphs.wall(new Vec3(0, 0.15, 0), FateLibraryMesh.EYE_R, 0.8, 32, eyeCol, 0.35f * a);
		}
		// the books: unread glow gold, Kent's pages blue, Nabu's tomes a dull ember
		for (int i = 0; i < FateLibraryMesh.LECTERNS; i++) {
			Vec3 book = new Vec3(FateLibraryMesh.lecternX(i), 1.18, FateLibraryMesh.lecternZ(i));
			int st = lecterns[i];
			int col = st == 0 ? 0xFFD860 : st == 2 ? 0x5A9CFF : 0x803018;
			float k = st == 0 ? 0.7f + 0.3f * Mth.sin(time * 2.2f + i) : 0.45f;
			FateGlyphs.disc(book, ux, uz, 0.32, 0.24, 10, col, 0.5f * k * a);
			FateGlyphs.ring(new Vec3(book.x, 0.03, book.z), ux, uz, 1.0, 0.03, 20, col, (zone == i ? 0.9f : 0.3f) * k * a, true);
			if (st == 0) {
				FateGlyphs.glyph(1 + i % 8, book.add(0, 0.9 + 0.08 * Mth.sin(time * 1.7f + i), 0), u, up, 0.45, 0.03, col, 0.55f * k * a);
			}
		}
		// the screen's halo
		float y0 = FateLibraryMesh.SCREEN_Y0 - FateLibraryMesh.BEZEL;
		float y1 = FateLibraryMesh.SCREEN_Y0 + FateLibraryMesh.SCREEN_H + FateLibraryMesh.BEZEL;
		float hx = half + FateLibraryMesh.BEZEL;
		float zz = FateLibraryMesh.SCREEN_Z + 0.3f;
		Vec3 n = new Vec3(0, 0, 1);
		FateGlyphs.glow(new Vec3(-hx, y0, zz), new Vec3(hx, y0, zz), n, 0.05, gold, 0.5f * a);
		FateGlyphs.glow(new Vec3(-hx, y1, zz), new Vec3(hx, y1, zz), n, 0.05, gold, 0.5f * a);
		FateGlyphs.glow(new Vec3(-hx, y0, zz), new Vec3(-hx, y1, zz), n, 0.05, gold, 0.5f * a);
		FateGlyphs.glow(new Vec3(hx, y0, zz), new Vec3(hx, y1, zz), n, 0.05, gold, 0.5f * a);
		// the doors' ankhs
		if (inside) {
			FateGlyphs.ankh(new Vec3(0, 2.8, FateLibraryMesh.DOOR_Z - 0.05), ux, up, 2.4, 0.06, gold, 0.55f * a);
		}
		FateGlyphs.ankh(new Vec3(0, 2.8, FateLibraryMesh.HALL_END - 0.4), ux, up, 2.4, 0.06, gold, 0.45f * a);
	}

	private static int lerpColour(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return (r << 16) | (g << 8) | bl;
	}

	private static void draw(BufferBuilder bb) {
		MeshData data = bb.build();
		if (data != null) {
			BufferUploader.drawWithShader(data);
		}
	}

	// ---------------------------------------------------------------- HUD

	private static void hud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		long ms = Util.getMillis();
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		if (showing()) {
			if (on && !mc.options.hideGui && mc.screen == null) {
				panel(g, mc.font, gw, gh, ms);
			}
			float vt = (ms - verdictMs) / 900.0f;
			if (vt < 1.0f) {
				int rgb = verdict == FateTrialPayload.V_RIGHT || verdict == FateTrialPayload.V_PAGES_FOUND ? 0xFFD060
						: verdict == FateTrialPayload.V_TOME ? 0x601010 : verdict == FateTrialPayload.V_WILL_BEGINS ? 0x6090FF : 0xC01808;
				float peak = verdict == FateTrialPayload.V_WRATH ? 0.6f : verdict == FateTrialPayload.V_EMPTY_HANDED ? 0.0f : 0.3f;
				fill(g, gw, gh, rgb, peak * (1.0f - vt));
			}
			float in = 1.0f - (ms - enteredMs) / (float) ENTER_FADE_MS;
			if (on && in > 0.0f) {
				fill(g, gw, gh, 0x000000, Math.min(1.0f, in * 1.5f));
			}
			float t = exitT();
			if (exitStartMs >= 0L && t > 0.6f) {
				fill(g, gw, gh, 0xFFE8A0, (t - 0.6f) / 0.4f * 0.6f);
			}
			return;
		}
		if (flashMs >= 0L) {
			float t = (ms - flashMs) / (float) FLASH_MS;
			if (t >= 1.0f) {
				flashMs = -1L;
			} else {
				fill(g, gw, gh, 0xFFE8A0, 0.6f * (1.0f - t));
			}
		}
	}

	/** The trial's panel: the stage and what to do, Nabu's anger, the pages, the will test, and what's in reach. */
	private static void panel(GuiGraphics g, Font font, int gw, int gh, long ms) {
		int x = 8;
		int y = 8;
		int width = Math.max(120, Math.min(220, gw / 3));
		g.fill(x - 4, y - 4, x + width + 4, y + 62, 0x90100800);
		g.drawString(font, Component.translatable("hud.projecthero.doctor_fate.trial.title").withStyle(ChatFormatting.BOLD), x, y, 0xFFFFD860, true);
		y += 11;
		Component goal = Component.translatable("hud.projecthero.doctor_fate.trial.stage." + Mth.clamp(stage, 1, 3), found, needed);
		for (FormattedCharSequence row : font.split(goal, width)) {
			g.drawString(font, row, x, y, 0xFFE8DCC0, true);
			y += 10;
		}
		y += 2;
		// Nabu's anger: a Meter, gold to red, with his wrath at the end
		g.drawString(font, Component.translatable("hud.projecthero.doctor_fate.trial.anger"), x, y, 0xFFFF9060, true);
		y += 10;
		int bw = width;
		g.fill(x - 1, y - 1, x + bw + 1, y + 5, 0xFF200800);
		int fillW = Math.round(bw * Mth.clamp(anger / 100.0f, 0.0f, 1.0f));
		g.fill(x, y, x + fillW, y + 4, 0xFF000000 | lerpColour(0xFFC040, 0xFF2010, anger / 100.0f));
		for (int i = 1; i < 10; i++) {
			g.fill(x + bw * i / 10, y, x + bw * i / 10 + 1, y + 4, 0x50000000);
		}
		y += 8;
		// the pages
		String pages = Component.translatable("hud.projecthero.doctor_fate.trial.pages", found, needed).getString();
		g.drawString(font, pages, x, y, 0xFF8FB8FF, true);
		if (wraths > 0) {
			String wr = Component.translatable("hud.projecthero.doctor_fate.trial.wraths", wraths).getString();
			g.drawString(font, wr, x + width - font.width(wr), y, 0xFFC04030, true);
		}
		// the willpower test: a vertical Gauge on the right, the band Nabu lets you hold
		if (willOn) {
			int h = Math.min(160, gh - 80);
			int bx = gw - 34;
			int by = (gh - h) / 2;
			g.fill(bx - 2, by - 2, bx + 14, by + h + 2, 0xC0100800);
			int bandTop = by + Math.round(h * (1.0f - Math.min(1.0f, band + 0.12f)));
			int bandBot = by + Math.round(h * (1.0f - Math.max(0.0f, band - 0.12f)));
			g.fill(bx, bandTop, bx + 12, bandBot, 0x8060A0FF);
			int wy = by + Math.round(h * (1.0f - will));
			boolean inBand = Math.abs(will - band) <= 0.12f;
			g.fill(bx - 3, wy - 1, bx + 15, wy + 2, inBand ? 0xFFFFFFFF : 0xFFFF5030);
			int ph = Math.round(h * Mth.clamp(progress / (float) progressNeeded, 0.0f, 1.0f));
			g.fill(bx + 16, by + h - ph, bx + 20, by + h, 0xFFFFD860);
			Component hint = Component.translatable("hud.projecthero.doctor_fate.trial.will_hint");
			int ty = by + h + 6;
			for (FormattedCharSequence row : font.split(hint, Math.min(200, gw / 2))) {
				g.drawString(font, row, gw - 8 - font.width(row), ty, outTicks > 20 && (ms / 200L) % 2L == 0L ? 0xFFFF6040 : 0xFFE8DCC0, true);
				ty += 10;
			}
		}
		// what's in reach
		int cx = gw / 2;
		g.fill(cx, gh / 2, cx + 1, gh / 2 + 1, 0xC0FFFFFF);
		String key = zone == ZONE_EYE ? "eye" : zone >= 0 ? (lecterns[zone] == 0 ? "lectern" : "lectern_read") : inside ? "hint" : "hall";
		Component line = Component.translatable("hud.projecthero.doctor_fate.trial.zone." + key);
		boolean live = zone != ZONE_NONE;
		if (live) {
			line = line.copy().withStyle(ChatFormatting.BOLD);
		}
		int col = live ? ((ms / 400L) % 2L == 0L ? 0xFFFFD860 : 0xFFFFFFFF) : 0xFFB8A888;
		int ly = gh - 40;
		for (FormattedCharSequence row : font.split(line, Math.max(80, Math.min(320, gw - 40)))) {
			g.drawCenteredString(font, row, cx, ly, col);
			ly += 10;
		}
	}

	private static void fill(GuiGraphics g, int w, int h, int rgb, float alpha) {
		int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
		if (a > 2) {
			g.fill(0, 0, w, h, (a << 24) | (rgb & 0xFFFFFF));
		}
	}

	// ---------------------------------------------------------------- harness / tests

	/** Puts the walker somewhere (screenshots). */
	public static void placeForHarness(double x, double z, float y, float p) {
		wx = x;
		wz = z;
		pwx = x;
		pwz = z;
		yaw = y;
		pitch = p;
		inside = z < FateLibraryMesh.DOOR_Z - 2.5;
		if (inside && libDoorMs < 0L) {
			libDoorMs = Util.getMillis() - 2000L;
		}
		zone = zoneAt(x, z);
	}

	public static int zone() {
		return zone;
	}

	/** Where lectern {@code i} stands in the library (x, 0, z), and where the hallway ends / the door is (harness). */
	public static Vec3 lecternPos(int i) {
		return new Vec3(FateLibraryMesh.lecternX(i), 0.0, FateLibraryMesh.lecternZ(i));
	}

	public static double hallEnd() {
		return FateLibraryMesh.HALL_END;
	}

	public static double doorZ() {
		return FateLibraryMesh.DOOR_Z;
	}
}
