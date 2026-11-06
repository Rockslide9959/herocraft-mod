package com.projecthero.mod.client.omnitrix.alien.alienx;

import java.util.HashSet;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.hulk.HulkMindScreen;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncilPayload;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;

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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * v0.17.2 round 4 (user: "can you change the alien x council thing to be the mind palace thing that the hulk has, i also want a
 * screen where the player can see a third person view of themselves and even turn and look around if they want to please"): the
 * Celestial Council's hall, the Hulk's mind illusion ({@link HulkMindScreen}, whose comment explains the method) dressed as the
 * council. The player stays in the world, held inside his frozen body ({@code AlienXCouncil}); this client draws the rest:
 * <ol>
 *   <li>The world is rendered as usual, once, from {@link #worldCamera}: a third-person camera on an orbit round the BODY, turned by
 *       {@link #orbitYaw} / {@link #orbitPitch} rather than the player's rotation (mixin {@code AlienXHallCameraMixin}, kept out of
 *       walls by a block ray). Everyone, himself included, sees only the body: the real player is never drawn
 *       ({@code AlienXHallHideMixin}).</li>
 *   <li>At the head of the HUD pass ({@code GuiHulkRampageMixin}) that finished frame is copied into {@link #pov}.</li>
 *   <li>The window is cleared and the hall is drawn with its own camera ({@link #renderScene}): a star-filled void, nebulae, a round
 *       dais of dark glass with a causeway leading to it, and at the dais' far edge the giant screen showing {@link #pov} live, with
 *       Serena and Bellicus ({@link AlienXFx}) hanging either side of it, turning to watch him.</li>
 *   <li>He walks it with WASD and the mouse (a client-only walker, vanilla speeds, vanilla bobbing, the real player's keys let
 *       go and his rotation left alone).</li>
 *   <li><b>The heads</b>: inside the ring of light under either head, a right-click or R opens the council screen, which is
 *       unchanged. V, N and the alien wheel work anywhere (they move Let Me Go, and the screen opens wherever he stands).</li>
 *   <li><b>The view</b>: at the point of light in front of the screen a right-click takes the view. The hall camera rises to face the
 *       screen, and now the mouse swings the world camera round his body, the scroll wheel zooms and a left-click resets it; a
 *       right-click (or S, or Sneak) steps back. The view stays where he left it.</li>
 *   <li>When the server lets him go, the camera flies into the screen as the Hulk's does, and he is back in the world under a pale
 *       flash. A death just cuts to the death screen.</li>
 * </ol>
 * Everything else is swallowed as in the Hulk's hall and the HUD is put away. GPU resources are freed when the scene ends and when
 * leaving the world. Other players see only the frozen body.
 */
public final class AlienXHall {
	// ---------------------------------------------------------------- the hall (blocks; the screen faces +Z, the hall runs to +Z)

	static final float SCREEN_Z = 0.0f;
	static final float SCREEN_H = 12.0f;
	static final float SCREEN_Y0 = 1.4f;
	static final float BEZEL = 0.45f;
	/** The round dais. */
	static final float DAIS_Z = 22.0f;
	static final float DAIS_R = 21.5f;
	/** The causeway leading to it. */
	static final float WAY_HALF = 3.6f;
	static final float WAY_END = 60.0f;
	/** He starts here, looking at the screen far off through the heads. */
	static final float START_Z = 57.0f;
	static final float EYE = 1.62f;
	static final float RADIUS = 0.3f;
	/** The point of light that takes the view, and how close he must be. */
	static final float CONSOLE_Z = 15.0f;
	static final float CONSOLE_REACH = 3.2f;
	/** The rings of light under the heads, and their radius. */
	static final float CIRCLE_X = 13.5f;
	static final float CIRCLE_Z = 11.5f;
	static final float CIRCLE_R = 5.0f;
	/** The heads hang here. */
	static final Vec3 SERENA_HEAD = new Vec3(-16.5, 9.5, 8.0);
	static final Vec3 BELLICUS_HEAD = new Vec3(16.5, 9.5, 8.0);
	/** Vanilla walking and sprinting speeds (blocks / tick). */
	static final double WALK = 0.21585;
	static final double SPRINT = 0.2806;
	static final long ENTER_FADE_MS = 1800L;
	static final long EXIT_MS = 900L;
	static final long FLASH_MS = 700L;
	static final long CONSOLE_MS = 450L;
	static final float EXIT_END_DIST = 1.8f;
	/** The world camera's orbit round the body. */
	static final float ORBIT_DEFAULT = 6.5f;
	static final float ORBIT_MIN = 3.0f;
	static final float ORBIT_MAX = 22.0f;
	static final float ORBIT_NEAREST = 1.3f;
	static final float ORBIT_PIVOT = 1.75f;
	static final float ORBIT_PITCH = 14.0f;

	private static final int ZONE_NONE = 0;
	private static final int ZONE_HEADS = 1;
	private static final int ZONE_CONSOLE = 2;

	// ---------------------------------------------------------------- state

	private static boolean on;
	private static long enteredMs;
	private static long exitStartMs = -1L;
	private static long flashMs = -1L;
	private static long lastFrameMs;
	private static long deafMs = -10_000L;
	private static CameraType savedCamera;

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
	private static int zone;

	// the view of his body
	private static boolean atConsole;
	private static float consoleT;
	private static float orbitYaw;
	private static float orbitPitch = ORBIT_PITCH;
	private static float orbitDist = ORBIT_DEFAULT;
	private static float homeYaw;

	// the exit flight starts from here
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

	// GPU
	private static RenderTarget pov;
	private static AlienXHallMesh mesh;

	private AlienXHall() {
	}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(AlienXHall::tick);
		HudRenderCallback.EVENT.register(AlienXHall::hud);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(AlienXHall::reset));
	}

	/** The hall is up (he is in it, or the fly-into-the-screen is finishing). */
	public static boolean showing() {
		return on || exitStartMs >= 0L;
	}

	/** The mouse: the walker's head, or the view round his body at the console. Same scale as {@code Entity.turn}. */
	public static void turn(double dx, double dy) {
		if (exitStartMs >= 0L) {
			return;
		}
		if (atConsole) {
			orbitYaw += (float) dx * 0.15f;
			orbitPitch = Mth.clamp(orbitPitch + (float) dy * 0.15f, -85.0f, 85.0f);
			return;
		}
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90.0f, 90.0f);
	}

	/** The scroll wheel: at the console it zooms. */
	public static void scroll(double dy) {
		if (atConsole) {
			orbitDist = Mth.clamp(orbitDist - (float) dy * 1.25f, ORBIT_MIN, ORBIT_MAX);
		}
	}

	private static void reset() {
		on = false;
		exitStartMs = -1L;
		flashMs = -1L;
		atConsole = false;
		consoleT = 0.0f;
		zone = ZONE_NONE;
		Minecraft mc = Minecraft.getInstance();
		restoreCamera(mc);
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

	// ---------------------------------------------------------------- tick: state, input, the walker

	private static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			if (showing() || pov != null || savedCamera != null) {
				reset();
			}
			return;
		}
		boolean hall = AlienXKit.inHall(mc.player);
		long ms = Util.getMillis();
		if (hall && !on) {
			enter(mc);
		}
		if (!hall && on) {
			on = false;
			atConsole = false;
			if (mc.screen instanceof AlienXCouncilScreen) {
				mc.setScreen(null);
			}
			if (mc.player.isDeadOrDying()) {
				reset();
				return;
			}
			startExit(mc);
		}
		if (exitStartMs >= 0L && ms - exitStartMs >= EXIT_MS) {
			exitStartMs = -1L;
			flashMs = ms;
			restoreCamera(mc);
		}
		if (!showing()) {
			return;
		}
		if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		swallowInputs(mc);
		// his real self stands perfectly still, inside the body
		Vec3 v = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(0.0, v.y, 0.0);
		mc.player.setSprinting(false);
		if (on) {
			walk(mc);
		}
	}

	private static void enter(Minecraft mc) {
		on = true;
		enteredMs = Util.getMillis();
		lastFrameMs = enteredMs;
		exitStartMs = -1L;
		flashMs = -1L;
		wx = 0.0;
		wz = START_Z;
		pwx = wx;
		pwz = wz;
		vx = 0.0;
		vz = 0.0;
		yaw = 180.0f;
		pitch = -4.0f;
		walkDist = 0.0f;
		pWalkDist = 0.0f;
		bob = 0.0f;
		pBob = 0.0f;
		nextStep = 1.0f;
		fovMul = 1.0f;
		pFovMul = 1.0f;
		atConsole = false;
		consoleT = 0.0f;
		zone = ZONE_NONE;
		homeYaw = mc.player.getYRot();
		orbitYaw = homeYaw;
		orbitPitch = ORBIT_PITCH;
		orbitDist = ORBIT_DEFAULT;
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
		play(mc, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.4f);
	}

	/** Bindings that keep working in the hall: chat, the player list, screenshots, and V, N and the wheel (they ask to leave). */
	private static Set<KeyMapping> allowed(Minecraft mc) {
		Set<KeyMapping> keep = new HashSet<>();
		keep.add(mc.options.keyChat);
		keep.add(mc.options.keyCommand);
		keep.add(mc.options.keyPlayerList);
		keep.add(mc.options.keyScreenshot);
		keep.add(mc.options.keyFullscreen);
		keep.add(mc.options.keySocialInteractions);
		keep.add(ModKeyBindings.ABILITY_5);
		keep.add(ModKeyBindings.MAX_STEEL_TRANSFORM);
		keep.add(ModKeyBindings.POWER_SELECT);
		keep.add(ModKeyBindings.POWER_INFO);
		return keep;
	}

	/**
	 * Every other binding is let go and its clicks thrown away before anything sees them, WASD included: the walker reads those
	 * straight from the keyboard, and the real player must not move.
	 */
	private static void swallowInputs(Minecraft mc) {
		Set<KeyMapping> keep = allowed(mc);
		boolean used = false;
		boolean attacked = false;
		boolean r = false;
		while (mc.options.keyUse.consumeClick()) {
			used = true;
		}
		while (mc.options.keyAttack.consumeClick()) {
			attacked = true;
		}
		while (ModKeyBindings.ABILITY_1.consumeClick()) {
			r = true;
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
		if (atConsole) {
			if (used || held(mc, mc.options.keyDown) || held(mc, mc.options.keyShift)) {
				atConsole = false;
				play(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 0.7f);
			} else if (attacked) {
				orbitYaw = homeYaw;
				orbitPitch = ORBIT_PITCH;
				orbitDist = ORBIT_DEFAULT;
				play(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.3f);
			}
			return;
		}
		if ((used || r) && zone == ZONE_HEADS) {
			send(AlienXCouncilPayload.OP_OPEN);
		} else if (used && zone == ZONE_CONSOLE) {
			atConsole = true;
			vx = 0.0;
			vz = 0.0;
			play(mc, SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.2f);
		} else if (r) {
			deafMs = Util.getMillis();
		}
	}

	private static void send(int op) {
		if (ClientPlayNetworking.canSend(AlienXCouncilPayload.TYPE)) {
			ClientPlayNetworking.send(new AlienXCouncilPayload(op, "", 0));
		}
	}

	/** Physically held right now (keyboard key or mouse button): the bindings themselves are kept released. */
	private static boolean held(Minecraft mc, KeyMapping k) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(k);
		long win = mc.getWindow().getWindow();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
		}
		return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
				&& InputConstants.isKeyDown(win, key.getValue());
	}

	private static void walk(Minecraft mc) {
		pwx = wx;
		pwz = wz;
		pWalkDist = walkDist;
		pBob = bob;
		pFovMul = fovMul;
		float fwd = 0.0f;
		float strafe = 0.0f;
		boolean sprint = false;
		if (mc.screen == null && !atConsole) {
			fwd = (held(mc, mc.options.keyUp) ? 1.0f : 0.0f) - (held(mc, mc.options.keyDown) ? 1.0f : 0.0f);
			strafe = (held(mc, mc.options.keyLeft) ? 1.0f : 0.0f) - (held(mc, mc.options.keyRight) ? 1.0f : 0.0f);
			sprint = fwd > 0.0f && held(mc, mc.options.keySprint);
		}
		float len = Mth.sqrt(fwd * fwd + strafe * strafe);
		if (len > 1.0f) {
			fwd /= len;
			strafe /= len;
		}
		double speed = sprint ? SPRINT : WALK;
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
		fovMul += ((sprint && moved > 0.1f ? 1.08f : 1.0f) - fovMul) * 0.5f;
		if (walkDist > nextStep) {
			nextStep = (float) Math.floor(walkDist) + 1.0f;
			play(mc, SoundEvents.AMETHYST_BLOCK_STEP, 0.22f, 0.6f + mc.level.getRandom().nextFloat() * 0.08f);
		}
		zone = zoneAt(wx, wz);
	}

	/** On the dais or the causeway (with the walker's radius); the screen's foot closes the far end. */
	static boolean open(double x, double z) {
		if (z < SCREEN_Z + 1.6) {
			return false;
		}
		double dz = z - DAIS_Z;
		if (x * x + dz * dz <= sq(DAIS_R - RADIUS - 0.2)) {
			return true;
		}
		return Math.abs(x) <= WAY_HALF - RADIUS && z >= DAIS_Z && z <= WAY_END - RADIUS;
	}

	static int zoneAt(double x, double z) {
		if (x * x + sq(z - CONSOLE_Z) <= CONSOLE_REACH * CONSOLE_REACH) {
			return ZONE_CONSOLE;
		}
		return sq(Math.abs(x) - CIRCLE_X) + sq(z - CIRCLE_Z) <= CIRCLE_R * CIRCLE_R ? ZONE_HEADS : ZONE_NONE;
	}

	private static void play(Minecraft mc, SoundEvent ev, float volume, float pitch) {
		mc.getSoundManager().play(new SimpleSoundInstance(ev.getLocation(), SoundSource.PLAYERS, volume, pitch, SoundInstance.createUnseededRandom(),
				false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
	}

	private static double sq(double d) {
		return d * d;
	}

	// ---------------------------------------------------------------- the world camera

	/** The world is being drawn for the hall's screen: the camera orbits the body ({@code AlienXHallCameraMixin}). */
	public static boolean orbiting() {
		return showing();
	}

	public static float orbitYaw() {
		return orbitYaw;
	}

	public static float orbitPitch() {
		return orbitPitch;
	}

	/** Where the world camera stands: on its orbit round the body, pulled in by anything solid in the way. */
	public static Vec3 worldCamera(float partial) {
		Minecraft mc = Minecraft.getInstance();
		Entity centre = mc.player;
		if (mc.player != null && mc.level != null) {
			Entity body = mc.level.getEntity(AlienXKit.bodyId(mc.player));
			if (body != null) {
				centre = body;
			}
		}
		if (centre == null || mc.level == null) {
			return Vec3.ZERO;
		}
		Vec3 pivot = centre.getPosition(partial).add(0.0, ORBIT_PIVOT, 0.0);
		float yr = orbitYaw * Mth.DEG_TO_RAD;
		float pr = orbitPitch * Mth.DEG_TO_RAD;
		Vec3 back = new Vec3(Mth.sin(yr) * Mth.cos(pr), Mth.sin(pr), -Mth.cos(yr) * Mth.cos(pr));
		Vec3 want = pivot.add(back.scale(orbitDist));
		HitResult hit = mc.level.clip(new ClipContext(pivot, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		// never closer than this: a wall right behind him must not put the camera inside his own 2.5-block body
		double d = Math.max(ORBIT_NEAREST, hit.getLocation().distanceTo(pivot) - 0.3);
		return pivot.add(back.scale(d));
	}

	// ---------------------------------------------------------------- rendering

	private static float exitT() {
		if (exitStartMs < 0L) {
			return 0.0f;
		}
		float t = Mth.clamp((Util.getMillis() - exitStartMs) / (float) EXIT_MS, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	/** The screen's width: its height times the window's aspect (never stretched). */
	static float screenWidth(Minecraft mc) {
		int w = Math.max(1, mc.getWindow().getWidth());
		int h = Math.max(1, mc.getWindow().getHeight());
		return Mth.clamp(SCREEN_H * w / (float) h, 8.0f, 25.0f);
	}

	/**
	 * At the head of the HUD pass ({@code GuiHulkRampageMixin}): the finished world frame goes onto the screen, and the hall is drawn
	 * over the whole window. Only while {@link #showing}; frees the capture once it's over.
	 */
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

		long dt = Math.min(100L, ms - lastFrameMs);
		lastFrameMs = ms;
		consoleT = Mth.clamp(consoleT + (atConsole ? dt : -dt) / (float) CONSOLE_MS, 0.0f, 1.0f);

		float partial = delta.getGameTimeDeltaPartialTick(false);
		float screenW = screenWidth(mc);
		if (mesh == null || mesh.screenW != screenW) {
			mesh = AlienXHallMesh.build(screenW);
		}

		// the hall's camera
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
		float half = screenW * 0.5f;
		float midY = SCREEN_Y0 + SCREEN_H * 0.5f;
		if (exitStartMs >= 0L) {
			// a dolly zoom into the screen, ending exactly where the screen fills the window: that last frame IS the world frame
			float endFov = 2.0f * (float) Math.atan((SCREEN_H * 0.5) / EXIT_END_DIST) * Mth.RAD_TO_DEG;
			cx = Mth.lerp(exitT, ex, 0.0);
			cy = Mth.lerp(exitT, ey, midY);
			cz = Mth.lerp(exitT, ez, SCREEN_Z + EXIT_END_DIST);
			cyaw = Mth.rotLerp(exitT, eyaw, 180.0f);
			cpitch = Mth.lerp(exitT, epitch, 0.0f);
			fov = Mth.lerp(exitT, efov, endFov);
		} else {
			double wxp = Mth.lerp(partial, pwx, wx);
			double wzp = Mth.lerp(partial, pwz, wz);
			float t = consoleT * consoleT * (3.0f - 2.0f * consoleT);
			fov = fovDeg * Mth.lerp(partial, pFovMul, fovMul);
			// at the console he rises to face the screen, close enough that it (and a little of its frame) fills the view
			float fit = (SCREEN_H * 0.5f + BEZEL + 0.5f) / (float) Math.tan(fov * 0.5f * Mth.DEG_TO_RAD);
			float vfit = (half + BEZEL + 0.5f) / (float) Math.tan(fov * 0.5f * Mth.DEG_TO_RAD) * h / (float) w;
			float dist = Math.max(fit, vfit);
			cx = Mth.lerp(t, wxp, 0.0);
			cy = Mth.lerp(t, EYE, midY);
			cz = Mth.lerp(t, wzp, SCREEN_Z + dist);
			cyaw = Mth.rotLerp(t, yaw, 180.0f);
			cpitch = Mth.lerp(t, pitch, 0.0f);
			if (mc.options.bobView().get() && t <= 0.0f) {
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

		Matrix4f view = new Matrix4f();
		if (bobAmt > 0.0f) {
			// vanilla's view bobbing
			float a = wd * Mth.PI;
			view.translate(Mth.sin(a) * bobAmt * 0.5f, -Math.abs(Mth.cos(a) * bobAmt), 0.0f);
			view.rotateZ(Mth.sin(a) * bobAmt * 3.0f * Mth.DEG_TO_RAD);
			view.rotateX(Math.abs(Mth.cos(a - 0.2f) * bobAmt) * 5.0f * Mth.DEG_TO_RAD);
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
		RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective(fov * Mth.DEG_TO_RAD, w / (float) h, 0.05f, 600.0f),
				VertexSorting.DISTANCE_TO_ORIGIN);

		main.bindWrite(true);
		RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
		RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.disableCull();
		FogRenderer.setupNoFog();

		float time = (ms % 1_000_000L) / 1000.0f;
		float since = (ms - enteredMs) / 1000.0f;
		float power = Mth.clamp(since / 1.2f, 0.0f, 1.0f);
		float screenLight = Mth.lerp(exitT, power * (0.9f + 0.1f * Mth.sin(time * 1.1f)), 1.0f);
		float touches = 1.0f - exitT;

		// 1. the void: stars and nebulae, at infinity (they turn with the view but never get closer)
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		mesh.emitSky(bb, time, cyaw, cpitch);
		draw(bb);

		// 2. the dark glass: the stars show faintly through it
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		mesh.emitGlass(bb, cx, cy, cz, screenLight);
		draw(bb);

		// 3. the screen: his body, live
		RenderSystem.disableBlend();
		double sd = Math.sqrt(cx * cx + sq(cy - midY) + sq(cz - SCREEN_Z));
		float far = 0.72f + 0.28f * (float) Math.exp(-Math.max(0.0, sd - 8.0) / 45.0);
		int c = Math.round(Mth.clamp(Mth.lerp(exitT, far * (0.92f + 0.08f * screenLight) * power, 1.0f), 0.0f, 1.0f) * 255.0f);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, pov.getColorTextureId());
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		float y0 = SCREEN_Y0;
		float y1 = SCREEN_Y0 + SCREEN_H;
		float sz = SCREEN_Z + 0.02f;
		bb.addVertex((float) (-half - cx), (float) (y0 - cy), (float) (sz - cz)).setUv(0.0f, 0.0f).setColor(c, c, c, 255);
		bb.addVertex((float) (half - cx), (float) (y0 - cy), (float) (sz - cz)).setUv(1.0f, 0.0f).setColor(c, c, c, 255);
		bb.addVertex((float) (half - cx), (float) (y1 - cy), (float) (sz - cz)).setUv(1.0f, 1.0f).setColor(c, c, c, 255);
		bb.addVertex((float) (-half - cx), (float) (y1 - cy), (float) (sz - cz)).setUv(0.0f, 1.0f).setColor(c, c, c, 255);
		draw(bb);

		// 4. light: the inlaid rings, the low curtain of light along every edge, the halo
		RenderSystem.enableBlend();
		RenderSystem.depthMask(false);
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		mesh.emitLight(bb, cx, cy, cz, time, screenLight * touches, zone == ZONE_HEADS ? 1.0f : 0.0f, zone == ZONE_CONSOLE ? 1.0f : 0.0f, wx);
		draw(bb);

		// 5. the heads and the point of light: the same drawing code as a world pass, so the view goes into the pose
		mv.identity();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		Lighting.setupLevel();
		PoseStack pose = new PoseStack();
		pose.mulPose(view);
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		try {
			AlienXFx.council(pose, buffers, new Vec3(cx, cy, cz), new Vec3(cx, cy, cz), SERENA_HEAD, BELLICUS_HEAD, new Vec3(0.0, 0.0, CONSOLE_Z),
					mc.level.getGameTime() + partial, partial, consoleT < 0.5f ? 1.0f : 0.25f);
		} catch (RuntimeException e) {
			// a draw must never take the frame down
		}
		buffers.endBatch();

		// restore
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		mv.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setProjectionMatrix(savedProj, savedSort);
		// the HUD draws on a clean depth buffer, as it would have
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
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
				int cx = gw / 2;
				if (!atConsole) {
					g.fill(cx, gh / 2, cx + 1, gh / 2 + 1, 0xC0FFFFFF);
				}
				String key = atConsole ? "view" : zone == ZONE_HEADS ? "heads" : zone == ZONE_CONSOLE ? "console" : ms - deafMs < 2500L ? "deaf" : "hint";
				boolean live = atConsole || zone != ZONE_NONE;
				Component line = Component.translatable("hud.projecthero.omnitrix.alien_x.hall." + key);
				if (live) {
					line = line.copy().withStyle(ChatFormatting.BOLD);
				}
				int col = live ? ((ms / 400L) % 2L == 0L ? 0xFFBFD8FF : 0xFFFFFFFF) : 0xFF9AA8C8;
				int y = gh - 38;
				for (net.minecraft.util.FormattedCharSequence row : mc.font.split(line, Math.max(80, Math.min(320, gw - 40)))) {
					g.drawCenteredString(mc.font, row, cx, y, col);
					y += 10;
				}
			}
			// in from black as he arrives
			float in = 1.0f - (ms - enteredMs) / (float) ENTER_FADE_MS;
			if (on && in > 0.0f) {
				fill(g, gw, gh, 0x000000, Math.min(1.0f, in * 1.4f));
			}
			// the last moment of the flight: a pale burn
			float t = exitT();
			if (exitStartMs >= 0L && t > 0.6f) {
				fill(g, gw, gh, 0xDDE8FF, (t - 0.6f) / 0.4f * 0.55f);
			}
			return;
		}
		if (flashMs >= 0L) {
			float t = (ms - flashMs) / (float) FLASH_MS;
			if (t >= 1.0f) {
				flashMs = -1L;
			} else {
				fill(g, gw, gh, 0xDDE8FF, 0.55f * (1.0f - t));
			}
		}
	}

	private static void fill(GuiGraphics g, int w, int h, int rgb, float alpha) {
		int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
		if (a > 2) {
			g.fill(0, 0, w, h, (a << 24) | (rgb & 0xFFFFFF));
		}
	}
}
