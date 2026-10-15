package com.projecthero.mod.client.hulk;

import java.util.HashSet;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.network.HulkActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * v0.16.4 (user): "whenever the player dies they get transported to the same place where they get teleported to when they get
 * their powers ... their should be a screen playing the third person hulk POV and the player should spawn 100 blocks away
 * from the screen, they need to right click the screen to regain control of the hulk, during this time the hulk is rampaging
 * as per usual, make this happen instead of the regular rampage minigame".
 *
 * <p>A client can only draw the dimension its player stands in, and the rampaging Hulk IS the player -- so the player stays
 * in the overworld, driven by the server's rampage AI exactly like any rampage ({@link HulkState#RAMPAGE_MIND}), and the mind
 * is an illusion drawn over the screen, entirely on this client:
 * <ol>
 *   <li>The world is rendered as usual, once, from the forced third-person camera behind the Hulk.</li>
 *   <li>At the head of the HUD pass ({@code GuiHulkRampageMixin}) that finished frame is blitted into {@link #pov} (a
 *       {@link TextureTarget} the size of the window; alpha forced to 1, mipmaps generated so it doesn't shimmer from 100
 *       blocks away).</li>
 *   <li>The window is cleared and the MIND HALL is drawn with its own perspective camera ({@link #renderScene}): a long dark
 *       corridor (polished blackstone floor and bricks, black ceiling, polished deepslate pillars and beams, a few hidden
 *       flickering lights -- the origin mind room's look) opening into a tall chamber whose end wall holds a giant screen in a
 *       deepslate bezel, showing {@link #pov} live, with scanlines, a vignette, a halo of light, a glow on the floor and dust
 *       hanging in its light. Lighting and fog are per vertex on the CPU (the block textures come straight from the block
 *       atlas), so the far end is just a glowing rectangle in the dark at first. The screen has the window's aspect, so the
 *       picture is never stretched.</li>
 *   <li>The player walks a client-only "mind walker": the mouse turns it (the real player's rotation is left alone --
 *       {@code MouseHandlerHulkMindMixin} -- since the server is steering the Hulk), WASD walk and the sprint key sprints at
 *       vanilla speeds, with vanilla's view bobbing and footsteps, against the hall's walls. He starts 100 blocks from the
 *       screen.</li>
 *   <li>Within {@link #REACH} blocks, looking at the screen, right-click sends {@code MIND_SCREEN}; the server checks the
 *       rampage kind and time and ends the rampage ({@code HulkControl.mindScreenReached}, with a roar). Meanwhile the camera
 *       flies into the screen until the picture fills the view exactly -- at that moment the hall IS the world frame -- so when
 *       the server's answer arrives the hall just stops and the player is back in the Hulk, under a green-white flash.</li>
 * </ol>
 * While in the hall every other key binding is swallowed (attack, use, hotbar, drop, abilities ...; chat, the player list,
 * screenshots and the menu still work), the scroll wheel is ignored and the HUD is hidden (a hint, a crosshair and the time
 * left are drawn instead). The rampage's hard end and the watchdog still let him go; a hard end fades the hall out through
 * black. GPU resources are freed when the scene ends and when leaving the world. Other players just see a rampaging Hulk.
 */
public final class HulkMindScreen {
	// ---------------------------------------------------------------- the hall (blocks; the screen faces +Z, the hall runs to +Z)

	/** The screen's surface, and the bezel's front. */
	static final float SCREEN_Z = 0.3f;
	static final float BEZEL_Z = 0.55f;
	static final float BEZEL = 0.6f;
	/** The screen's height and its bottom edge; its width follows the window's aspect. */
	static final float SCREEN_H = 9.0f;
	static final float SCREEN_Y0 = 1.0f;
	/** The chamber: half width, depth, height. */
	static final float CW = 12.0f;
	static final float CD = 16.0f;
	static final float CH = 14.0f;
	/**
	 * The hall: half width, height, its far end -- wide and tall enough that the whole screen shows through the chamber's
	 * doorway from the far end.
	 */
	static final float HW = 6.5f;
	static final float HH = 9.0f;
	static final float HALL_END = 104.0f;
	/** He starts 100 blocks from the screen. */
	static final float START_Z = SCREEN_Z + 100.0f;
	static final float EYE = 1.62f;
	static final float RADIUS = 0.3f;
	static final float PILLAR = 0.6f;
	/** Right-click works this close (blocks from the screen's surface). */
	public static final float REACH = 5.0f;
	/** Vanilla walking and sprinting speeds (blocks / tick). */
	static final double WALK = 0.21585;
	static final double SPRINT = 0.2806;
	/** How long the fly-into-the-screen takes. */
	static final long EXIT_MS = 900L;
	/** Where the flight ends: this close to the screen, with the view widened until the screen exactly fills it. */
	static final float EXIT_END_DIST = 1.8f;
	static final long ENTER_FADE_MS = 1800L;
	static final long FLASH_MS = 700L;
	static final long TIMEOUT_FADE_MS = 1000L;

	private static final float FOG = 30.0f;
	private static final float AMBIENT = 0.025f;

	// ---------------------------------------------------------------- state

	private static boolean on;
	private static long enteredMs;
	private static long exitStartMs = -1L;
	private static boolean exitConfirmed;
	private static long exitSentMs;
	private static long flashMs = -1L;
	private static boolean flashGreen;
	private static long lastShownMs;
	private static long lastRefusedMs;

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
	private static boolean aiming;

	// the exit flight starts from here
	private static double ex;
	private static double ey;
	private static double ez;
	private static float eyaw;
	private static float epitch;

	// GPU
	private static RenderTarget pov;
	private static Mesh mesh;

	private HulkMindScreen() {
	}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(HulkMindScreen::tick);
		HudRenderCallback.EVENT.register(HulkMindScreen::hud);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(HulkMindScreen::reset));
	}

	private static HulkState state(Minecraft mc) {
		return mc.player == null ? null : mc.player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
	}

	/** The hall is up (the mind rampage runs, or the fly-into-the-screen is finishing). */
	public static boolean showing() {
		return on || exitStartMs >= 0L;
	}

	/** Mouse look goes to the walker (from {@code MouseHandlerHulkMindMixin}); same scale as {@code Entity.turn}. */
	public static void turn(double dx, double dy) {
		if (exitStartMs >= 0L) {
			return;
		}
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90.0f, 90.0f);
	}

	private static void reset() {
		on = false;
		exitStartMs = -1L;
		exitConfirmed = false;
		flashMs = -1L;
		aiming = false;
		free();
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
			if (on || exitStartMs >= 0L || pov != null) {
				reset();
			}
			return;
		}
		HulkState s = state(mc);
		boolean mind = s != null && s.inMindScreen(mc.level.getGameTime());
		long ms = Util.getMillis();
		if (mind && !on && exitStartMs < 0L) {
			enter();
		}
		if (!mind && on) {
			on = false;
			if (exitStartMs >= 0L) {
				exitConfirmed = true; // the server let him go: finish the flight
			} else {
				// the hard end (or the Hulk went down): out through black
				flashMs = ms;
				flashGreen = false;
			}
		}
		if (exitStartMs >= 0L) {
			long t = ms - exitStartMs;
			if (exitConfirmed && t >= EXIT_MS) {
				exitStartMs = -1L;
				exitConfirmed = false;
				flashMs = ms;
				flashGreen = true;
			} else if (!exitConfirmed && mind && ms - exitSentMs > 3000L) {
				// refused (too soon): back to the walk, right where he stood
				exitStartMs = -1L;
				lastRefusedMs = ms;
			}
		}
		if (!showing()) {
			return;
		}
		swallowInputs(mc);
		if (on && exitStartMs < 0L) {
			walk(mc);
		}
	}

	private static void enter() {
		on = true;
		enteredMs = Util.getMillis();
		exitStartMs = -1L;
		exitConfirmed = false;
		flashMs = -1L;
		wx = 0.0;
		wz = START_Z;
		pwx = wx;
		pwz = wz;
		vx = 0.0;
		vz = 0.0;
		yaw = 180.0f;
		pitch = -2.0f;
		walkDist = 0.0f;
		pWalkDist = 0.0f;
		bob = 0.0f;
		pBob = 0.0f;
		nextStep = 1.0f;
		fovMul = 1.0f;
		pFovMul = 1.0f;
	}

	/** Bindings that keep working in the hall: chat, commands, the player list, screenshots, fullscreen. */
	private static Set<KeyMapping> allowed(Minecraft mc) {
		Set<KeyMapping> keep = new HashSet<>();
		keep.add(mc.options.keyChat);
		keep.add(mc.options.keyCommand);
		keep.add(mc.options.keyPlayerList);
		keep.add(mc.options.keyScreenshot);
		keep.add(mc.options.keyFullscreen);
		keep.add(mc.options.keySocialInteractions);
		// the walker reads these straight from the keyboard (the rampage lets them go every tick anyway)
		keep.add(mc.options.keyUp);
		keep.add(mc.options.keyDown);
		keep.add(mc.options.keyLeft);
		keep.add(mc.options.keyRight);
		keep.add(mc.options.keySprint);
		return keep;
	}

	/** Every other binding is swallowed before {@code Minecraft.handleKeybinds} (and the mod's own key handlers) see it. */
	private static void swallowInputs(Minecraft mc) {
		Set<KeyMapping> keep = allowed(mc);
		boolean used = false;
		while (mc.options.keyUse.consumeClick()) {
			used = true;
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
		if (used && on && exitStartMs < 0L && mc.screen == null) {
			use(mc);
		}
	}

	/** Physically held right now (keyboard key or mouse button) -- the rampage keeps the bindings themselves released. */
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
		if (mc.screen == null) {
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
			float far = (float) Mth.clamp(wz / CD, 0.0, 1.0);
			play(mc, wz < CD ? SoundEvents.DEEPSLATE_STEP : SoundEvents.STONE_STEP, 0.22f, 0.85f + 0.1f * far + mc.level.getRandom().nextFloat() * 0.08f);
		}
		aiming = lookingAtScreen(eyeX(1.0f), EYE, eyeZ(1.0f), yaw, pitch);
	}

	/** Inside the hall (with the walker's radius): the chamber, or the corridor between its pillars. */
	static boolean open(double x, double z) {
		if (z < BEZEL_Z + RADIUS || z > HALL_END - RADIUS) {
			return false;
		}
		if (z <= CD - RADIUS) {
			return Math.abs(x) <= CW - RADIUS;
		}
		return Math.abs(x) <= HW - PILLAR - RADIUS;
	}

	private static double eyeX(float partial) {
		return Mth.lerp(partial, pwx, wx);
	}

	private static double eyeZ(float partial) {
		return Mth.lerp(partial, pwz, wz);
	}

	/** The screen's width: its height times the window's aspect (so the picture is never stretched), within the chamber. */
	static float screenWidth(Minecraft mc) {
		int w = Math.max(1, mc.getWindow().getWidth());
		int h = Math.max(1, mc.getWindow().getHeight());
		return Mth.clamp(SCREEN_H * w / (float) h, 8.0f, 2.0f * (CW - 1.5f));
	}

	/** The view ray from the eye hits the screen, and he is within {@link #REACH}. */
	static boolean lookingAtScreen(double x, double y, double z, float yawDeg, float pitchDeg) {
		if (z - SCREEN_Z > REACH) {
			return false;
		}
		float yr = yawDeg * Mth.DEG_TO_RAD;
		float pr = pitchDeg * Mth.DEG_TO_RAD;
		double dx = -Mth.sin(yr) * Mth.cos(pr);
		double dy = -Mth.sin(pr);
		double dz = Mth.cos(yr) * Mth.cos(pr);
		if (dz > -1.0e-4) {
			return false;
		}
		double t = (SCREEN_Z - z) / dz;
		double hx = x + dx * t;
		double hy = y + dy * t;
		float half = screenWidth(Minecraft.getInstance()) * 0.5f;
		return Math.abs(hx) <= half + 0.2 && hy >= SCREEN_Y0 - 0.2 && hy <= SCREEN_Y0 + SCREEN_H + 0.2;
	}

	/** Right-click in the hall. */
	private static void use(Minecraft mc) {
		if (!aiming) {
			return;
		}
		exitStartMs = Util.getMillis();
		exitSentMs = exitStartMs;
		exitConfirmed = false;
		ex = wx;
		ey = EYE;
		ez = wz;
		eyaw = yaw;
		epitch = pitch;
		ClientPlayNetworking.send(new HulkActionPayload(HulkActionPayload.Action.MIND_SCREEN));
		play(mc, SoundEvents.BEACON_ACTIVATE, 0.9f, 0.6f);
		play(mc, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.8f);
	}

	private static void play(Minecraft mc, SoundEvent ev, float volume, float pitch) {
		mc.getSoundManager().play(new SimpleSoundInstance(ev.getLocation(), SoundSource.PLAYERS, volume, pitch, SoundInstance.createUnseededRandom(),
				false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
	}

	// ---------------------------------------------------------------- the camera

	private static float exitT() {
		if (exitStartMs < 0L) {
			return 0.0f;
		}
		float t = Mth.clamp((Util.getMillis() - exitStartMs) / (float) EXIT_MS, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	private static float baseFov(Minecraft mc) {
		return mc.options.fov().get().floatValue();
	}

	// ---------------------------------------------------------------- rendering

	/**
	 * At the head of the HUD pass (GuiHulkRampageMixin): the finished world frame goes onto the screen, and the hall is drawn
	 * over the whole window. Only while {@link #showing}; frees the capture once it's over.
	 */
	public static void renderScene(DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		long ms = Util.getMillis();
		if (!showing() || mc.player == null || mc.level == null) {
			if (pov != null && ms - lastShownMs > 3000L) {
				free();
			}
			return;
		}
		lastShownMs = ms;
		RenderTarget main = mc.getMainRenderTarget();
		int w = main.width;
		int h = main.height;
		if (w <= 0 || h <= 0) {
			return;
		}
		capture(main, w, h);

		float partial = delta.getGameTimeDeltaPartialTick(false);
		float screenW = screenWidth(mc);
		TextureAtlasSprite probe = sprite(mc, "polished_blackstone");
		if (mesh == null || mesh.screenW != screenW || mesh.probe != probe) {
			mesh = Mesh.build(mc, screenW, probe);
		}

		// the camera
		float fovDeg = baseFov(mc);
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
			// he is pulled INTO the screen: the camera closes in while the view widens (a dolly zoom), ending exactly where
			// the screen fills the window -- a flat picture square-on, so that last frame is the world frame itself
			float endFov = 2.0f * (float) Math.atan((SCREEN_H * 0.5) / EXIT_END_DIST) * Mth.RAD_TO_DEG;
			cx = Mth.lerp(exitT, ex, 0.0);
			cy = Mth.lerp(exitT, ey, SCREEN_Y0 + SCREEN_H * 0.5);
			cz = Mth.lerp(exitT, ez, SCREEN_Z + EXIT_END_DIST);
			cyaw = Mth.rotLerp(exitT, eyaw, 180.0f);
			cpitch = Mth.lerp(exitT, epitch, 0.0f);
			fov = Mth.lerp(exitT, fovDeg * fovMul, endFov);
		} else {
			cx = eyeX(partial);
			cz = eyeZ(partial);
			cy = EYE;
			cyaw = yaw;
			cpitch = pitch;
			if (mc.options.bobView().get()) {
				bobAmt = Mth.lerp(partial, pBob, bob);
				wd = Mth.lerp(partial, pWalkDist, walkDist);
			}
			fov = fovDeg * Mth.lerp(partial, pFovMul, fovMul);
		}

		Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorting savedSort = RenderSystem.getVertexSorting();
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.identity();
		if (bobAmt > 0.0f) {
			// vanilla's view bobbing
			float a = wd * Mth.PI;
			mv.translate(Mth.sin(a) * bobAmt * 0.5f, -Math.abs(Mth.cos(a) * bobAmt), 0.0f);
			mv.rotateZ(Mth.sin(a) * bobAmt * 3.0f * Mth.DEG_TO_RAD);
			mv.rotateX(Math.abs(Mth.cos(a - 0.2f) * bobAmt) * 5.0f * Mth.DEG_TO_RAD);
		}
		mv.rotateX(cpitch * Mth.DEG_TO_RAD);
		mv.rotateY((cyaw + 180.0f) * Mth.DEG_TO_RAD);
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

		float time = (ms % 1_000_000L) / 1000.0f;
		float[] lamps = mesh.lampLevels(time);
		// the screen's light: a slow breath, and it comes up as he arrives
		float since = (ms - enteredMs) / 1000.0f;
		float power = on || exitStartMs >= 0L ? Mth.clamp(since / 1.2f, 0.0f, 1.0f) : 1.0f;
		float glitch = flicker(time);
		float screenLight = power * (0.9f + 0.1f * Mth.sin(time * 1.3f)) * glitch;
		screenLight = Mth.lerp(exitT, screenLight, 1.0f);

		// 1. the hall
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
		BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		mesh.emit(bb, cx, cy, cz, lamps, screenLight);
		draw(bb);

		// 2. the screen: the Hulk, live
		float half = screenW * 0.5f;
		double sd = Math.sqrt(cx * cx + sq(cy - (SCREEN_Y0 + SCREEN_H * 0.5)) + sq(cz - SCREEN_Z));
		float far = 0.6f + 0.4f * (float) Math.exp(-Math.max(0.0, sd - 6.0) / 45.0);
		float sb = Mth.lerp(exitT, far * (0.88f + 0.12f * screenLight) * power, 1.0f);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.setShaderTexture(0, pov.getColorTextureId());
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		int c = Math.round(Mth.clamp(sb, 0.0f, 1.0f) * 255.0f);
		vtx(bb, -half, SCREEN_Y0, SCREEN_Z, cx, cy, cz).setUv(0.0f, 0.0f).setColor(c, c, c, 255);
		vtx(bb, half, SCREEN_Y0, SCREEN_Z, cx, cy, cz).setUv(1.0f, 0.0f).setColor(c, c, c, 255);
		vtx(bb, half, SCREEN_Y0 + SCREEN_H, SCREEN_Z, cx, cy, cz).setUv(1.0f, 1.0f).setColor(c, c, c, 255);
		vtx(bb, -half, SCREEN_Y0 + SCREEN_H, SCREEN_Z, cx, cy, cz).setUv(0.0f, 1.0f).setColor(c, c, c, 255);
		draw(bb);

		// 3. light and the CRT touches (no depth writes)
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		float fadeTouches = 1.0f - exitT;
		// scanlines, a rolling bright band and the vignette -- fine detail only up close (no moire from far away)
		float near = Mth.clamp((float) (28.0 - sd) / 16.0f, 0.0f, 1.0f) * fadeTouches;
		RenderSystem.defaultBlendFunc();
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		if (near > 0.01f) {
			int lines = 72;
			float step = SCREEN_H / lines;
			int a = Math.round(70.0f * near);
			for (int i = 0; i < lines; i++) {
				float y0 = SCREEN_Y0 + i * step;
				quadZ(bb, -half, y0, half, y0 + step * 0.35f, SCREEN_Z + 0.01f, cx, cy, cz, 0, 0, 0, a, a);
			}
			// vignette: darker toward the edges
			int va = Math.round(110.0f * near);
			float e = 1.6f;
			quadZ(bb, -half, SCREEN_Y0, -half + e, SCREEN_Y0 + SCREEN_H, SCREEN_Z + 0.012f, cx, cy, cz, 0, 0, 0, va, 0, true);
			quadZ(bb, half - e, SCREEN_Y0, half, SCREEN_Y0 + SCREEN_H, SCREEN_Z + 0.012f, cx, cy, cz, 0, 0, 0, 0, va, true);
			quadZ(bb, -half, SCREEN_Y0, half, SCREEN_Y0 + e, SCREEN_Z + 0.012f, cx, cy, cz, 0, 0, 0, va, 0);
			quadZ(bb, -half, SCREEN_Y0 + SCREEN_H - e, half, SCREEN_Y0 + SCREEN_H, SCREEN_Z + 0.012f, cx, cy, cz, 0, 0, 0, 0, va);
		}
		draw(bb);
		// additive: the rolling band, the halo, the floor glow, the dust, the LED
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		if (near > 0.01f) {
			float band = SCREEN_Y0 + SCREEN_H - ((time * 0.35f) % 1.0f) * (SCREEN_H + 1.2f);
			int ba = Math.round(28.0f * near);
			quadZ(bb, -half, band - 0.6f, half, band, SCREEN_Z + 0.014f, cx, cy, cz, 200, 255, 220, 0, ba);
			quadZ(bb, -half, band, half, band + 0.6f, SCREEN_Z + 0.014f, cx, cy, cz, 200, 255, 220, ba, 0);
		}
		float haloFog = far * screenLight * fadeTouches;
		if (haloFog > 0.01f) {
			halo(bb, half + BEZEL, SCREEN_Y0 - BEZEL, SCREEN_Y0 + SCREEN_H + BEZEL, 3.2f, Math.round(80.0f * haloFog), cx, cy, cz);
			halo(bb, half + BEZEL, SCREEN_Y0 - BEZEL, SCREEN_Y0 + SCREEN_H + BEZEL, 10.0f, Math.round(26.0f * haloFog), cx, cy, cz);
			// from far off: a wider bloom, so the end of the hall is a glowing rectangle in the dark
			float distant = Mth.clamp((float) (sd - 18.0) / 50.0f, 0.0f, 1.0f);
			if (distant > 0.01f) {
				halo(bb, half + BEZEL, SCREEN_Y0 - BEZEL, SCREEN_Y0 + SCREEN_H + BEZEL, 2.0f + (float) sd * 0.05f, Math.round(110.0f * haloFog * distant), cx, cy,
						cz);
			}
			// a soft glow on the polished floor in front of it
			float fz0 = BEZEL_Z;
			for (int i = 0; i < 6; i++) {
				float z0 = fz0 + i * 2.0f;
				float z1 = z0 + 2.0f;
				int a0 = Math.round(48.0f * haloFog * (1.0f - i / 6.0f));
				int a1 = Math.round(48.0f * haloFog * (1.0f - (i + 1) / 6.0f));
				floorQuad(bb, -half - 1.5f, z0, half + 1.5f, z1, 0.05f, cx, cy, cz, a0, a1);
			}
		}
		dust(bb, time, screenLight * fadeTouches, cx, cy, cz, cyaw, cpitch);
		// the power LED under the screen, breathing
		int led = Math.round((140.0f + 100.0f * Mth.sin(time * 2.0f)) * fadeTouches * far);
		if (led > 2) {
			float ly = SCREEN_Y0 - BEZEL * 0.5f;
			quadZ(bb, -0.07f, ly - 0.07f, 0.07f, ly + 0.07f, BEZEL_Z + 0.01f, cx, cy, cz, 90, 255, 80, led, led);
		}
		draw(bb);

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

	/** Copies the finished world frame into {@link #pov}: alpha forced to 1 (the shader discards alpha 0), then mipmaps. */
	private static void capture(RenderTarget main, int w, int h) {
		if (pov == null) {
			pov = new TextureTarget(w, h, false, Minecraft.ON_OSX);
		} else if (pov.width != w || pov.height != h) {
			pov.resize(w, h, Minecraft.ON_OSX);
		}
		GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pov.frameBufferId);
		GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
		GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, pov.frameBufferId);
		GlStateManager._colorMask(false, false, false, true);
		GlStateManager._clearColor(0.0f, 0.0f, 0.0f, 1.0f);
		GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
		GlStateManager._colorMask(true, true, true, true);
		GlStateManager._bindTexture(pov.getColorTextureId());
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		GlStateManager._bindTexture(0);
	}

	/** Now and then the signal stutters (a frame or two dimmer). */
	private static float flicker(float time) {
		float slot = (float) Math.floor(time * 12.0f);
		float h = hash(slot * 0.731f);
		return h > 0.985f ? 0.55f : h > 0.97f ? 0.8f : 1.0f;
	}

	private static void draw(BufferBuilder bb) {
		MeshData data = bb.build();
		if (data != null) {
			BufferUploader.drawWithShader(data);
		}
	}

	private static double sq(double d) {
		return d * d;
	}

	static com.mojang.blaze3d.vertex.VertexConsumer vtx(BufferBuilder bb, double x, double y, double z, double cx, double cy, double cz) {
		return bb.addVertex((float) (x - cx), (float) (y - cy), (float) (z - cz));
	}

	private static void quadZ(BufferBuilder bb, float x0, float y0, float x1, float y1, float z, double cx, double cy, double cz, int r, int g, int b,
			int aBottom, int aTop) {
		quadZ(bb, x0, y0, x1, y1, z, cx, cy, cz, r, g, b, aBottom, aTop, false);
	}

	/** A quad in a Z plane; alpha runs bottom -> top, or (horizontal) left -> right. */
	private static void quadZ(BufferBuilder bb, float x0, float y0, float x1, float y1, float z, double cx, double cy, double cz, int r, int g, int b,
			int a0, int a1, boolean horizontal) {
		int bl = a0;
		int br = horizontal ? a1 : a0;
		int tr = a1;
		int tl = horizontal ? a0 : a1;
		vtx(bb, x0, y0, z, cx, cy, cz).setColor(r, g, b, bl);
		vtx(bb, x1, y0, z, cx, cy, cz).setColor(r, g, b, br);
		vtx(bb, x1, y1, z, cx, cy, cz).setColor(r, g, b, tr);
		vtx(bb, x0, y1, z, cx, cy, cz).setColor(r, g, b, tl);
	}

	private static void floorQuad(BufferBuilder bb, float x0, float z0, float x1, float z1, float y, double cx, double cy, double cz, int a0, int a1) {
		vtx(bb, x0, y, z0, cx, cy, cz).setColor(150, 235, 170, a0);
		vtx(bb, x1, y, z0, cx, cy, cz).setColor(150, 235, 170, a0);
		vtx(bb, x1, y, z1, cx, cy, cz).setColor(150, 235, 170, a1);
		vtx(bb, x0, y, z1, cx, cy, cz).setColor(150, 235, 170, a1);
	}

	/** A soft ring of light round the bezel (on the wall plane): {@code a} at the bezel, nothing {@code spread} blocks out. */
	private static void halo(BufferBuilder bb, float half, float y0, float y1, float spread, int a, double cx, double cy, double cz) {
		float z = 0.06f; // clear of the wall even 100 blocks out (depth precision)
		int r = 170;
		int g = 240;
		int b = 190;
		float ox = half + spread;
		float oy0 = y0 - spread;
		float oy1 = y1 + spread;
		// bottom
		ring(bb, -half, y0, half, y0, ox, oy0, -ox, oy0, z, r, g, b, a, cx, cy, cz);
		// top
		ring(bb, half, y1, -half, y1, -ox, oy1, ox, oy1, z, r, g, b, a, cx, cy, cz);
		// left
		ring(bb, -half, y1, -half, y0, -ox, oy0, -ox, oy1, z, r, g, b, a, cx, cy, cz);
		// right
		ring(bb, half, y0, half, y1, ox, oy1, ox, oy0, z, r, g, b, a, cx, cy, cz);
	}

	private static void ring(BufferBuilder bb, float ix0, float iy0, float ix1, float iy1, float ox0, float oy0, float ox1, float oy1, float z, int r,
			int g, int b, int a, double cx, double cy, double cz) {
		vtx(bb, ix0, iy0, z, cx, cy, cz).setColor(r, g, b, a);
		vtx(bb, ix1, iy1, z, cx, cy, cz).setColor(r, g, b, a);
		vtx(bb, ox0, oy0, z, cx, cy, cz).setColor(r, g, b, 0);
		vtx(bb, ox1, oy1, z, cx, cy, cz).setColor(r, g, b, 0);
	}

	/** Dust hanging in the screen's light: tiny camera-facing specks, lit by how close they are to the screen. */
	private static void dust(BufferBuilder bb, float time, float light, double cx, double cy, double cz, float cyaw, float cpitch) {
		if (light <= 0.01f) {
			return;
		}
		float yr = cyaw * Mth.DEG_TO_RAD;
		float pr = cpitch * Mth.DEG_TO_RAD;
		// camera right and up
		float rx = -Mth.cos(yr);
		float rz = -Mth.sin(yr);
		float fx = -Mth.sin(yr) * Mth.cos(pr);
		float fy = -Mth.sin(pr);
		float fz = Mth.cos(yr) * Mth.cos(pr);
		float ux = rz * fy;
		float uy = rx * fz - rz * fx;
		float uz = -rx * fy;
		for (int i = 0; i < 220; i++) {
			float h1 = hash(i * 1.37f);
			float h2 = hash(i * 2.91f + 4.0f);
			float h3 = hash(i * 0.53f + 9.0f);
			float x = (h1 * 2.0f - 1.0f) * (CW - 0.5f) + Mth.sin(time * 0.11f + i) * 0.4f;
			float y = 0.3f + h2 * (CH - 1.0f) + Mth.sin(time * 0.07f + i * 1.7f) * 0.5f - ((time * 0.05f + h3) % 1.0f) * 0.6f;
			float z = SCREEN_Z + 0.8f + h3 * (CD + 6.0f) + Mth.cos(time * 0.09f + i * 0.3f) * 0.4f;
			if (z > CD && Math.abs(x) > HW - 0.5f) {
				x *= (HW - 0.5f) / CW;
			}
			double dx = x - cx;
			double dy = y - cy;
			double dz = z - cz;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < 0.6 || d > 40.0) {
				continue;
			}
			float lit = (float) (2.2 / (1.0 + (z - SCREEN_Z) * (z - SCREEN_Z) / 40.0));
			float twinkle = 0.6f + 0.4f * Mth.sin(time * (1.0f + h1) + i);
			float close = (float) Mth.clamp((d - 1.0) / 3.0, 0.0, 1.0); // never a big square in his face
			int a = Math.round(Mth.clamp(lit * twinkle * light * fog(d) * close, 0.0f, 1.0f) * 130.0f);
			if (a < 3) {
				continue;
			}
			float s = 0.018f + 0.014f * h2;
			float ax = rx * s;
			float az = rz * s;
			float bx = ux * s;
			float by = uy * s;
			float bz = uz * s;
			vtx(bb, x - ax - bx, y - by, z - az - bz, cx, cy, cz).setColor(210, 255, 220, a);
			vtx(bb, x + ax - bx, y - by, z + az - bz, cx, cy, cz).setColor(210, 255, 220, a);
			vtx(bb, x + ax + bx, y + by, z + az + bz, cx, cy, cz).setColor(210, 255, 220, a);
			vtx(bb, x - ax + bx, y + by, z - az + bz, cx, cy, cz).setColor(210, 255, 220, a);
		}
	}

	static float fog(double d) {
		// a gaussian falloff, with a long faint tail so lit places (the screen's glow, the lamps' pools) still show far off
		double k = d / FOG;
		return (float) Math.max(Math.exp(-k * k), 0.3 * Math.exp(-d / 40.0));
	}

	static float hash(float x) {
		float s = Mth.sin(x * 12.9898f + 78.233f) * 43758.547f;
		return s - (float) Math.floor(s);
	}

	static TextureAtlasSprite sprite(Minecraft mc, String block) {
		return mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/" + block));
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
			if (on && exitStartMs < 0L && !mc.options.hideGui) {
				int cx = gw / 2;
				int cy = gh / 2;
				// the crosshair: a dot, brackets when the screen is in reach
				g.fill(cx, cy, cx + 1, cy + 1, 0xC0FFFFFF);
				if (aiming) {
					int col = 0xFF7CFF4A;
					g.fill(cx - 6, cy - 6, cx - 3, cy - 5, col);
					g.fill(cx - 6, cy - 6, cx - 5, cy - 3, col);
					g.fill(cx + 4, cy - 6, cx + 7, cy - 5, col);
					g.fill(cx + 6, cy - 6, cx + 7, cy - 3, col);
					g.fill(cx - 6, cy + 6, cx - 3, cy + 7, col);
					g.fill(cx - 6, cy + 4, cx - 5, cy + 7, col);
					g.fill(cx + 4, cy + 6, cx + 7, cy + 7, col);
					g.fill(cx + 6, cy + 4, cx + 7, cy + 7, col);
				}
				Component line = aiming
						? Component.translatable("hud.projecthero.hulk.mind_take").withStyle(ChatFormatting.BOLD)
						: Component.translatable(ms - lastRefusedMs < 2500L ? "hud.projecthero.hulk.mind_not_yet" : "hud.projecthero.hulk.mind_hint");
				int col = aiming ? ((ms / 300L) % 2L == 0L ? 0xFF7CFF4A : 0xFFD8FFC8) : 0xFFB8C4B8;
				int y = gh - 38;
				for (net.minecraft.util.FormattedCharSequence row : mc.font.split(line, Math.max(80, gw - 40))) {
					g.drawCenteredString(mc.font, row, cx, y, col);
					y += 10;
				}
				HulkState s = state(mc);
				if (s != null) {
					long left = Math.max(0L, s.combat.rampageUntil - mc.level.getGameTime());
					int secs = (int) ((left + 19L) / 20L);
					String clock = String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
					g.drawCenteredString(mc.font, Component.translatable("hud.projecthero.hulk.mind_clock", clock), cx, y + 2, 0xFF5A665A);
				}
			}
			// in from black as he arrives
			float in = 1.0f - (ms - enteredMs) / (float) ENTER_FADE_MS;
			if (on && in > 0.0f) {
				fill(g, gw, gh, 0x000000, Math.min(1.0f, in * 1.4f));
			}
			// the last moment of the flight: a green-white burn
			float t = exitT();
			if (exitStartMs >= 0L && t > 0.6f) {
				fill(g, gw, gh, 0xD8FFC8, (t - 0.6f) / 0.4f * 0.55f);
			}
			return;
		}
		if (flashMs >= 0L) {
			float t = (ms - flashMs) / (float) (flashGreen ? FLASH_MS : TIMEOUT_FADE_MS);
			if (t >= 1.0f) {
				flashMs = -1L;
			} else if (flashGreen) {
				fill(g, gw, gh, 0xD8FFC8, 0.55f * (1.0f - t));
			} else {
				fill(g, gw, gh, 0x000000, 1.0f - t);
			}
		}
	}

	private static void fill(GuiGraphics g, int w, int h, int rgb, float alpha) {
		int a = Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
		if (a > 2) {
			g.fill(0, 0, w, h, (a << 24) | (rgb & 0xFFFFFF));
		}
	}

	// ---------------------------------------------------------------- the hall's geometry

	/**
	 * The hall as textured quads, one per block cell (so the block textures tile), with each vertex's share of the hidden
	 * lights and of the screen's light worked out once. Every frame only the fog (distance to the camera), the lights'
	 * flicker and the screen's brightness are applied. Rebuilt when the screen's width (window aspect) or the atlas changes.
	 */
	static final class Mesh {
		/** Per vertex: x y z u v screen lampA wA lampB wB. */
		private static final int STRIDE = 10;
		final float screenW;
		final TextureAtlasSprite probe;
		private float[] v = new float[STRIDE * 4 * 6000];
		private int n;
		/** Lamps: x y z base flickery. */
		private final java.util.List<float[]> lamps = new java.util.ArrayList<>();
		private float[] levels = new float[0];

		private Mesh(float screenW, TextureAtlasSprite probe) {
			this.screenW = screenW;
			this.probe = probe;
		}

		static Mesh build(Minecraft mc, float screenW, TextureAtlasSprite probe) {
			Mesh m = new Mesh(screenW, probe);
			TextureAtlasSprite floor = probe;
			TextureAtlasSprite bricks = sprite(mc, "polished_blackstone_bricks");
			TextureAtlasSprite black = sprite(mc, "black_concrete");
			TextureAtlasSprite frame = sprite(mc, "polished_deepslate");
			TextureAtlasSprite tiles = sprite(mc, "deepslate_tiles");

			// the hidden lights: one under each beam down the corridor (some dead, some failing), a dim one over the start
			int k = 0;
			for (float zc = CD + 4.0f; zc < HALL_END - 2.0f; zc += 8.0f, k++) {
				float h = hash(k * 3.17f + 1.0f);
				float base = h < 0.22f ? 0.0f : h < 0.6f ? 0.42f : 0.7f;
				m.lamps.add(new float[] { 0.0f, 4.5f, zc, base, hash(k * 5.3f + 2.0f) < 0.35f ? 1.0f : 0.0f });
			}
			m.lamps.add(new float[] { 0.0f, 4.0f, START_Z + 1.0f, 0.55f, 0.0f });
			// two faint ones high in the chamber's back corners
			m.lamps.add(new float[] { -CW + 1.0f, CH - 2.0f, CD - 1.0f, 0.3f, 0.0f });
			m.lamps.add(new float[] { CW - 1.0f, CH - 2.0f, CD - 1.0f, 0.3f, 1.0f });
			m.levels = new float[m.lamps.size()];

			// chamber
			m.planeY(0.0f, -CW, CW, 0.0f, CD, 1, floor);
			m.planeY(CH, -CW, CW, 0.0f, CD, -1, black);
			m.planeX(-CW, 0.0f, CD, 0.0f, CH, 1, bricks);
			m.planeX(CW, 0.0f, CD, 0.0f, CH, -1, bricks);
			m.planeZ(0.0f, -CW, CW, 0.0f, CH, 1, bricks);
			m.planeZ(CD, -CW, -HW, 0.0f, CH, -1, bricks);
			m.planeZ(CD, HW, CW, 0.0f, CH, -1, bricks);
			m.planeZ(CD, -HW, HW, HH, CH, -1, bricks);
			// corridor
			m.planeY(0.0f, -HW, HW, CD, HALL_END, 1, floor);
			m.planeY(HH, -HW, HW, CD, HALL_END, -1, black);
			m.planeX(-HW, CD, HALL_END, 0.0f, HH, 1, bricks);
			m.planeX(HW, CD, HALL_END, 0.0f, HH, -1, bricks);
			m.planeZ(HALL_END, -HW, HW, 0.0f, HH, -1, black);
			// pillars and beams
			for (float zc = CD + 4.0f; zc < HALL_END - 2.0f; zc += 8.0f) {
				float z0 = zc - PILLAR;
				float z1 = zc + PILLAR;
				for (int side = -1; side <= 1; side += 2) {
					float wall = side * HW;
					float face = side * (HW - PILLAR);
					m.planeX(face, z0, z1, 0.0f, HH, -side, frame);
					m.planeZ(z0, Math.min(wall, face), Math.max(wall, face), 0.0f, HH, -1, frame);
					m.planeZ(z1, Math.min(wall, face), Math.max(wall, face), 0.0f, HH, 1, frame);
				}
				m.planeY(HH - PILLAR, -HW, HW, z0, z1, -1, frame);
				m.planeZ(z0, -HW, HW, HH - PILLAR, HH, -1, frame);
				m.planeZ(z1, -HW, HW, HH - PILLAR, HH, 1, frame);
			}
			float half = screenW * 0.5f;
			// a strip of deepslate tiles leading up to the screen
			m.planeY(0.015f, -2.0f, 2.0f, BEZEL_Z, CD, 1, tiles);
			// the bezel: a deepslate frame standing out from the wall, the screen set back in it
			float ox = half + BEZEL;
			float oy0 = SCREEN_Y0 - BEZEL;
			float oy1 = SCREEN_Y0 + SCREEN_H + BEZEL;
			m.planeZ(BEZEL_Z, -ox, ox, oy0, SCREEN_Y0, 1, frame);
			m.planeZ(BEZEL_Z, -ox, ox, SCREEN_Y0 + SCREEN_H, oy1, 1, frame);
			m.planeZ(BEZEL_Z, -ox, -half, SCREEN_Y0, SCREEN_Y0 + SCREEN_H, 1, frame);
			m.planeZ(BEZEL_Z, half, ox, SCREEN_Y0, SCREEN_Y0 + SCREEN_H, 1, frame);
			m.planeY(oy1, -ox, ox, 0.0f, BEZEL_Z, 1, frame);
			m.planeY(oy0, -ox, ox, 0.0f, BEZEL_Z, -1, frame);
			m.planeX(-ox, 0.0f, BEZEL_Z, oy0, oy1, -1, frame);
			m.planeX(ox, 0.0f, BEZEL_Z, oy0, oy1, 1, frame);
			m.planeY(SCREEN_Y0, -half, half, SCREEN_Z, BEZEL_Z, 1, frame);
			m.planeY(SCREEN_Y0 + SCREEN_H, -half, half, SCREEN_Z, BEZEL_Z, -1, frame);
			m.planeX(-half, SCREEN_Z, BEZEL_Z, SCREEN_Y0, SCREEN_Y0 + SCREEN_H, 1, frame);
			m.planeX(half, SCREEN_Z, BEZEL_Z, SCREEN_Y0, SCREEN_Y0 + SCREEN_H, -1, frame);
			return m;
		}

		/** Flicker for each lamp right now. */
		float[] lampLevels(float time) {
			for (int i = 0; i < lamps.size(); i++) {
				float[] l = lamps.get(i);
				float lv = l[3];
				if (l[4] > 0.5f) {
					float slot = (float) Math.floor(time * 9.0f + i * 1.3f);
					float h = hash(slot * 0.37f + i * 7.1f);
					lv *= h > 0.9f ? 0.1f : h > 0.8f ? 0.5f : 0.92f + 0.08f * Mth.sin(time * 23.0f + i);
				} else {
					lv *= 0.95f + 0.05f * Mth.sin(time * 1.7f + i * 2.0f);
				}
				levels[i] = lv;
			}
			return levels;
		}

		void emit(BufferBuilder bb, double cx, double cy, double cz, float[] lv, float screen) {
			float[] a = v;
			for (int i = 0; i < n; i++) {
				int o = i * STRIDE;
				double dx = a[o] - cx;
				double dy = a[o + 1] - cy;
				double dz = a[o + 2] - cz;
				float f = fog(Math.sqrt(dx * dx + dy * dy + dz * dz));
				float lamp = lv[(int) a[o + 6]] * a[o + 7] + lv[(int) a[o + 8]] * a[o + 9];
				float scr = a[o + 5] * screen;
				// lamps: a cold grey-white; the screen: a pale green
				float r = (AMBIENT + lamp * 0.85f + scr * 0.72f) * f;
				float g = (AMBIENT + lamp * 0.9f + scr * 0.98f) * f;
				float b = (AMBIENT * 1.1f + lamp * 0.95f + scr * 0.8f) * f;
				bb.addVertex((float) dx, (float) dy, (float) dz).setUv(a[o + 3], a[o + 4]).setColor(c(r), c(g), c(b), 255);
			}
		}

		private static int c(float x) {
			return Math.round(Mth.clamp(x, 0.0f, 1.0f) * 255.0f);
		}

		// ---- building

		/** A floor / ceiling at {@code y}, facing up (+1) or down (-1). */
		void planeY(float y, float x0, float x1, float z0, float z1, int ny, TextureAtlasSprite s) {
			for (float x = x0; x < x1 - 1.0e-4f; x = next(x, x1)) {
				float xb = next(x, x1);
				for (float z = z0; z < z1 - 1.0e-4f; z = next(z, z1)) {
					float zb = next(z, z1);
					float fx = (float) Math.floor(x + 1.0e-4f);
					float fz = (float) Math.floor(z + 1.0e-4f);
					float u0 = x - fx;
					float u1 = xb - fx;
					float v0 = z - fz;
					float v1 = zb - fz;
					add(x, y, z, s, u0, v0, 0, ny, 0);
					add(xb, y, z, s, u1, v0, 0, ny, 0);
					add(xb, y, zb, s, u1, v1, 0, ny, 0);
					add(x, y, zb, s, u0, v1, 0, ny, 0);
				}
			}
		}

		/** A wall at {@code x}, facing +X (+1) or -X (-1). */
		void planeX(float x, float z0, float z1, float y0, float y1, int nx, TextureAtlasSprite s) {
			for (float z = z0; z < z1 - 1.0e-4f; z = next(z, z1)) {
				float zb = next(z, z1);
				for (float y = y0; y < y1 - 1.0e-4f; y = next(y, y1)) {
					float yb = next(y, y1);
					float fz = (float) Math.floor(z + 1.0e-4f);
					float fy = (float) Math.floor(y + 1.0e-4f);
					float u0 = z - fz;
					float u1 = zb - fz;
					float vTop = 1.0f - (yb - fy);
					float vBot = 1.0f - (y - fy);
					add(x, y, z, s, u0, vBot, nx, 0, 0);
					add(x, y, zb, s, u1, vBot, nx, 0, 0);
					add(x, yb, zb, s, u1, vTop, nx, 0, 0);
					add(x, yb, z, s, u0, vTop, nx, 0, 0);
				}
			}
		}

		/** A wall at {@code z}, facing +Z (+1) or -Z (-1). */
		void planeZ(float z, float x0, float x1, float y0, float y1, int nz, TextureAtlasSprite s) {
			for (float x = x0; x < x1 - 1.0e-4f; x = next(x, x1)) {
				float xb = next(x, x1);
				for (float y = y0; y < y1 - 1.0e-4f; y = next(y, y1)) {
					float yb = next(y, y1);
					float fx = (float) Math.floor(x + 1.0e-4f);
					float fy = (float) Math.floor(y + 1.0e-4f);
					float u0 = x - fx;
					float u1 = xb - fx;
					float vTop = 1.0f - (yb - fy);
					float vBot = 1.0f - (y - fy);
					add(x, y, z, s, u0, vBot, 0, 0, nz);
					add(xb, y, z, s, u1, vBot, 0, 0, nz);
					add(xb, yb, z, s, u1, vTop, 0, 0, nz);
					add(x, yb, z, s, u0, vTop, 0, 0, nz);
				}
			}
		}

		/** The next cell boundary: the next whole block, or the end. */
		private static float next(float a, float end) {
			float n = (float) Math.floor(a + 1.0e-4f) + 1.0f;
			return Math.min(n, end);
		}

		private void add(float x, float y, float z, TextureAtlasSprite s, float u, float vv, int nx, int ny, int nz) {
			if ((n + 1) * STRIDE > v.length) {
				v = java.util.Arrays.copyOf(v, v.length * 2);
			}
			int o = n * STRIDE;
			v[o] = x;
			v[o + 1] = y;
			v[o + 2] = z;
			v[o + 3] = s.getU0() + (s.getU1() - s.getU0()) * Mth.clamp(u, 0.0f, 1.0f);
			v[o + 4] = s.getV0() + (s.getV1() - s.getV0()) * Mth.clamp(vv, 0.0f, 1.0f);
			v[o + 5] = screenShare(x, y, z, nx, ny, nz);
			// the two lamps that light this vertex most
			int best = 0;
			float bw = 0.0f;
			int second = 0;
			float sw = 0.0f;
			for (int i = 0; i < lamps.size(); i++) {
				float[] l = lamps.get(i);
				float dx = l[0] - x;
				float dy = l[1] - y;
				float dz = l[2] - z;
				float d2 = dx * dx + dy * dy + dz * dz;
				float d = Mth.sqrt(d2) + 1.0e-3f;
				float facing = Math.max(0.0f, (dx * nx + dy * ny + dz * nz) / d) * 0.75f + 0.25f;
				float w = facing / (1.0f + d2 / 14.0f);
				if (w > bw) {
					second = best;
					sw = bw;
					best = i;
					bw = w;
				} else if (w > sw) {
					second = i;
					sw = w;
				}
			}
			v[o + 6] = best;
			v[o + 7] = bw;
			v[o + 8] = second;
			v[o + 9] = sw;
			n++;
		}

		/** How much of the screen's light reaches this point (nearest point of the screen, its facing, the surface's). */
		private float screenShare(float x, float y, float z, int nx, int ny, int nz) {
			float half = screenW * 0.5f;
			float qx = Mth.clamp(x, -half, half);
			float qy = Mth.clamp(y, SCREEN_Y0, SCREEN_Y0 + SCREEN_H);
			float qz = BEZEL_Z;
			float dx = qx - x;
			float dy = qy - y;
			float dz = qz - z;
			float d2 = dx * dx + dy * dy + dz * dz;
			float d = Mth.sqrt(d2) + 1.0e-3f;
			if (z < BEZEL_Z - 0.01f) {
				return 0.0f; // the wall behind it and the bezel's back
			}
			float out = Math.max(0.0f, (z - SCREEN_Z) / d) * 0.8f + 0.2f;
			float facing = Math.max(0.0f, (dx * nx + dy * ny + dz * nz) / d) * 0.8f + 0.2f;
			return 1.9f * out * facing / (1.0f + d2 / 30.0f);
		}
	}
}
