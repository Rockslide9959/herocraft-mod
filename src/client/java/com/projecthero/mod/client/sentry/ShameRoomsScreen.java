package com.projecthero.mod.client.sentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.ShameRooms;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.ShameRoomPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.5 <b>the Shame Rooms</b>, the mind screen of a takeover (the Thunderbolts* shame rooms): a black room with memory doors.
 * Points of light drift in the dark -- click them to fill the Light bar -- while darkness shapes chase the cursor and drain it.
 * Fill the bar to clear the room; let it empty, or run out the 45 s, and you fall back a room and the mind shuts for 20 s (the server
 * decides: {@code ShameRooms.report}). Three rooms, each faster and darker; the longer the takeover has run, the harder they get, and
 * the Void's grip (drained by friends reaching you from outside) makes them easier. Never pauses the game. Esc closes it; it comes
 * back after 5 s.
 */
public class ShameRoomsScreen extends Screen {
	private static final long ROOM_LIMIT_MS = 45_000L;
	private static final float START_LIGHT = 30f;

	private static final class Light {
		float x, y, vx, vy, phase;
	}

	private static final class Shade {
		float x, y, r, speed, phase;
	}

	private final Random rnd = new Random();
	private final List<Light> lights = new ArrayList<>();
	private final List<Shade> shades = new ArrayList<>();
	private int room = -1;
	private float light;
	private int clicks;
	private long roomStartMs;
	private long lastMs;
	/** After a result: what to show and until when; and the room the result was for. */
	private long verdictUntil;
	private boolean verdictWin;
	private int sentFor = -1;
	private float mx = -100, my = -100;
	private boolean hurtSound;

	public ShameRoomsScreen() {
		super(Component.translatable("screen.projecthero.shame_rooms"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		VoidTakeoverClient.screenClosed();
		super.onClose();
	}

	private SentryState state() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : Sentry.peek(mc.player);
	}

	/** How hard this room is: per room, the takeover's length, the Void's grip. */
	private float difficulty(SentryState s) {
		Minecraft mc = Minecraft.getInstance();
		long ticks = mc.level == null ? 0L : Math.max(0L, mc.level.getGameTime() - s.takeoverStart);
		float roomK = 1.0f + 0.32f * room;
		float lengthK = 1.0f + Math.min(1.0f, ticks / 1200f * 0.25f);
		float gripK = 0.6f + 0.4f * Mth.clamp(s.grip / 100f, 0f, 1f);
		return roomK * lengthK * gripK;
	}

	private void startRoom(int r, SentryState s) {
		room = r;
		light = START_LIGHT;
		clicks = 0;
		roomStartMs = Util.getMillis();
		sentFor = -1;
		lights.clear();
		shades.clear();
		float d = difficulty(s);
		int nLights = Math.max(3, 6 - r);
		for (int i = 0; i < nLights; i++) {
			lights.add(newLight(d));
		}
		int nShades = 2 + r + (int) d;
		for (int i = 0; i < nShades; i++) {
			Shade sh = new Shade();
			boolean left = rnd.nextBoolean();
			sh.x = left ? 0 : width;
			sh.y = rnd.nextFloat() * height;
			sh.r = 12 + rnd.nextFloat() * 10 + r * 2;
			sh.speed = (40f + rnd.nextFloat() * 25f) * d;
			sh.phase = rnd.nextFloat() * 6.28f;
			shades.add(sh);
		}
	}

	private Light newLight(float d) {
		Light l = new Light();
		l.x = width * 0.15f + rnd.nextFloat() * width * 0.7f;
		l.y = height * 0.2f + rnd.nextFloat() * height * 0.55f;
		float a = rnd.nextFloat() * 6.28f;
		float sp = (25f + rnd.nextFloat() * 20f) * d;
		l.vx = Mth.cos(a) * sp;
		l.vy = Mth.sin(a) * sp;
		l.phase = rnd.nextFloat() * 6.28f;
		return l;
	}

	@Override
	public void tick() {
		SentryState s = state();
		Minecraft mc = Minecraft.getInstance();
		if (s == null || mc.level == null || !s.inTakeover()) {
			mc.setScreen(null);
			return;
		}
		long now = mc.level.getGameTime();
		if (!ShameRooms.mindOpen(s, now) && Util.getMillis() > verdictUntil) {
			mc.setScreen(null); // the mind shut (a failed room) -- the takeover view shows the countdown
			return;
		}
		if (room != s.room && Util.getMillis() > verdictUntil) {
			startRoom(s.room, s);
		} else if (sentFor == room && verdictWin && Util.getMillis() > verdictUntil + 3000L) {
			startRoom(s.room, s); // the server didn't take it (too quick): play it again
		}
	}

	private void step(float dt, SentryState s) {
		if (sentFor == room) {
			return; // waiting on the server's word
		}
		float d = difficulty(s);
		for (Light l : lights) {
			l.x += l.vx * dt;
			l.y += l.vy * dt;
			if (l.x < width * 0.08f || l.x > width * 0.92f) {
				l.vx = -l.vx;
			}
			if (l.y < height * 0.14f || l.y > height * 0.8f) {
				l.vy = -l.vy;
			}
		}
		boolean touched = false;
		for (Shade sh : shades) {
			float dx = mx - sh.x, dy = my - sh.y;
			float dist = Mth.sqrt(dx * dx + dy * dy);
			if (dist > 1f) {
				sh.x += dx / dist * sh.speed * dt;
				sh.y += dy / dist * sh.speed * dt;
			}
			if (dist < sh.r + 3f) {
				touched = true;
			}
		}
		if (touched) {
			light -= 22f * d * dt;
			if (!hurtSound) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.6f, 0.9f));
				hurtSound = true;
			}
		} else {
			hurtSound = false;
		}
		if (light <= 0f) {
			finish(false);
		} else if (light >= 100f) {
			finish(true);
		} else if (Util.getMillis() - roomStartMs > ROOM_LIMIT_MS) {
			finish(false);
		}
	}

	private void finish(boolean won) {
		sentFor = room;
		verdictWin = won;
		verdictUntil = Util.getMillis() + 1600L;
		ClientPlayNetworking.send(new ShameRoomPayload(room, won, clicks));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(won ? SoundEvents.BEACON_ACTIVATE : SoundEvents.WARDEN_ROAR, won ? 1.4f : 0.7f, 0.6f));
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (button != 0 || sentFor == room) {
			return super.mouseClicked(x, y, button);
		}
		SentryState s = state();
		for (int i = 0; i < lights.size(); i++) {
			Light l = lights.get(i);
			if ((x - l.x) * (x - l.x) + (y - l.y) * (y - l.y) <= 10 * 10) {
				clicks++;
				light = Math.min(100f, light + 9f / (1f + room * 0.15f));
				lights.set(i, newLight(s == null ? 1f : difficulty(s)));
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f + rnd.nextFloat() * 0.4f, 0.8f));
				return true;
			}
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		// the black room (no blur, no world behind it)
		g.fill(0, 0, width, height, 0xFF030205);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		SentryState s = state();
		if (s == null) {
			return;
		}
		if (room < 0) {
			startRoom(s.room, s);
		}
		mx = mouseX;
		my = mouseY;
		long ms = Util.getMillis();
		float dt = lastMs == 0L ? 0f : Math.min(0.1f, (ms - lastMs) / 1000f);
		lastMs = ms;
		step(dt, s);
		renderBackground(g, mouseX, mouseY, partial);
		float t = ms / 1000f;
		// the memory doors: tall frames in faint gold, the room's own one lit
		int doors = 5;
		for (int i = 0; i < doors; i++) {
			int dw = width / 12, dh = height / 3;
			int x = width * (i + 1) / (doors + 1) - dw / 2;
			int y = height / 2 - dh / 2 - height / 10;
			boolean mine = i == 2;
			int c = mine ? 0x60FFC83A : 0x22FFC83A;
			g.fill(x, y, x + dw, y + 1, c);
			g.fill(x, y + dh, x + dw, y + dh + 1, c);
			g.fill(x, y, x + 1, y + dh, c);
			g.fill(x + dw - 1, y, x + dw, y + dh, c);
			if (mine) {
				g.fill(x + 1, y + 1, x + dw - 1, y + dh, 0x10FFC83A);
			}
		}
		// the lights: soft gold points with a halo
		for (Light l : lights) {
			float p = 0.75f + 0.25f * Mth.sin(t * 4f + l.phase);
			int halo = (int) (60 * p);
			g.fill((int) l.x - 8, (int) l.y - 8, (int) l.x + 8, (int) l.y + 8, (halo / 3 << 24) | 0xFFC83A);
			g.fill((int) l.x - 5, (int) l.y - 5, (int) l.x + 5, (int) l.y + 5, (halo << 24) | 0xFFD860);
			g.fill((int) l.x - 2, (int) l.y - 2, (int) l.x + 3, (int) l.y + 3, 0xFFFFF4C0);
		}
		// the darkness: blobs of deep violet-black with a pale rim and two cold eyes, wobbling as they come
		for (Shade sh : shades) {
			int r = (int) sh.r;
			for (int k = r; k > 0; k -= 2) {
				float wob = 1f + 0.12f * Mth.sin(t * 6f + sh.phase + k);
				int rr = (int) (k * wob);
				int a = 120 + (int) (100 * (1f - k / (float) r));
				g.fill((int) sh.x - rr, (int) sh.y - rr / 2 - k / 3, (int) sh.x + rr, (int) sh.y + rr / 2 + k / 3, (a << 24) | 0x0E0618);
			}
			g.fill((int) sh.x - r, (int) sh.y - 1, (int) sh.x + r, (int) sh.y, 0x405A3A90);
			g.fill((int) sh.x - 5, (int) sh.y - 3, (int) sh.x - 3, (int) sh.y - 1, 0xFFB088FF);
			g.fill((int) sh.x + 3, (int) sh.y - 3, (int) sh.x + 5, (int) sh.y - 1, 0xFFB088FF);
		}
		// the Light bar (a gold gauge) at the bottom, with the time left
		int bw = Math.min(260, width - 40);
		int bx = (width - bw) / 2, by = height - 34;
		g.fill(bx - 2, by - 2, bx + bw + 2, by + 10, 0xFF3A2A10);
		g.fill(bx, by, bx + bw, by + 8, 0xFF120C04);
		g.fill(bx, by, bx + Math.round(bw * Mth.clamp(light / 100f, 0f, 1f)), by + 8, 0xFFFFC83A);
		for (int i = 1; i < 10; i++) {
			g.fill(bx + bw * i / 10, by, bx + bw * i / 10 + 1, by + 8, 0x60000000);
		}
		long left = Math.max(0L, ROOM_LIMIT_MS - (ms - roomStartMs)) / 1000L;
		g.drawCenteredString(font, Component.translatable("screen.projecthero.shame_rooms.light", Math.round(light), left), width / 2, by + 13, 0xFFE8D8A0);
		// the title and the hint (wrapped to the window)
		g.drawCenteredString(font, Component.translatable("screen.projecthero.shame_rooms.room", room + 1, SentryConfig.ROOMS), width / 2, 12, 0xFFFFC83A);
		int y = 26;
		for (FormattedCharSequence line : font.split(Component.translatable("screen.projecthero.shame_rooms.hint"), Math.min(320, width - 40))) {
			g.drawCenteredString(font, line, width / 2, y, 0xFF9A90B0);
			y += 10;
		}
		if (ms < verdictUntil) {
			Component v = Component.translatable(verdictWin ? "screen.projecthero.shame_rooms.cleared" : "screen.projecthero.shame_rooms.failed");
			g.fill(0, height / 2 - 14, width, height / 2 + 14, 0xB0000000);
			g.drawCenteredString(font, v, width / 2, height / 2 - 4, verdictWin ? 0xFFFFC83A : 0xFFFF5A5A);
		}
		// (no super.render: it would draw the background again over the room; this screen has no widgets)
	}
}
