package com.projecthero.mod.client.ironman;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.drone.IronManDroneEntity;
import com.projecthero.mod.ironman.drone.IronManDrones;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.network.IronManDroneInputPayload;
import com.projecthero.mod.network.IronManDroneLinkPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;

/**
 * v0.14.29 Remote Pilot, client side: puts the camera on the drone while the server says the link is open, turns the
 * player's keys into {@link IronManDroneInputPayload}s, and draws the slim REMOTE LINK overlay.
 *
 * <p>How the keys are taken over: at the very start of each client tick (before vanilla and the mod's own key
 * handling run) the movement / jump / sneak / sprint / attack / use / ability keys are read straight from GLFW, then
 * their {@link KeyMapping}s are forced up and their queued clicks drained -- so nothing else (vanilla attack, the
 * double-tap-jump suit call, the R..C abilities) ever sees them. With the camera on another entity vanilla also stops
 * feeding movement into the local player ({@code LocalPlayer.isControlledCamera}), so the real body stands still. The
 * mouse still turns the player; that look is the drone's aim, and {@link IronManDroneEntity#clientView} makes the
 * camera follow it every frame. C (ability 6) closes the link.
 */
public final class IronManDroneClient {
	private static int droneId = -1;
	/** The link range the server enforces (blocks). */
	private static int linkRange = (int) IronManDrones.MAX_RANGE;
	private static boolean linked;
	/** Ticks to wait for the drone entity to arrive on the client after the link packet. */
	private static int waitTicks;
	private static boolean fireClicked;
	private static boolean endClicked;
	// physical key state captured at tick start
	private static boolean kFwd, kBack, kLeft, kRight, kUp, kDown, kBoost, kFire;

	private IronManDroneClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(IronManDrones.DRONE, IronManDroneRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(IronManDroneLinkPayload.TYPE, (payload, context) ->
				context.client().execute(() -> onLink(payload.entityId(), payload.active(), payload.range())));
		ClientTickEvents.START_CLIENT_TICK.register(IronManDroneClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(IronManDroneClient::endTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(IronManDroneClient::release));
		HudRenderCallback.EVENT.register(IronManDroneClient::renderHud);
		IronManDroneEntity.clientView = new IronManDroneEntity.ViewSource() {
			@Override
			public boolean drives(IronManDroneEntity drone) {
				Minecraft mc = Minecraft.getInstance();
				return linked && drone.getId() == droneId && mc.player != null && mc.getCameraEntity() == drone;
			}

			@Override
			public float yaw(float partialTick) {
				Minecraft mc = Minecraft.getInstance();
				return mc.player == null ? 0f : mc.player.getViewYRot(partialTick);
			}

			@Override
			public float pitch(float partialTick) {
				Minecraft mc = Minecraft.getInstance();
				return mc.player == null ? 0f : mc.player.getViewXRot(partialTick);
			}
		};
	}

	/** True while the local player is piloting a drone (camera on it). */
	public static boolean piloting() {
		Minecraft mc = Minecraft.getInstance();
		return linked && mc.getCameraEntity() instanceof IronManDroneEntity d && d.getId() == droneId;
	}

	private static void onLink(int id, boolean active, int range) {
		if (active) {
			droneId = id;
			linkRange = Math.max(1, range);
			linked = true;
			waitTicks = 60;
			fireClicked = false;
			endClicked = false;
		} else if (id < 0 || id == droneId) {
			release();
		}
	}

	private static void release() {
		Minecraft mc = Minecraft.getInstance();
		boolean was = linked;
		linked = false;
		droneId = -1;
		if (mc.player != null && mc.getCameraEntity() != mc.player) {
			mc.setCameraEntity(mc.player);
		}
		if (was) {
			mc.gameRenderer.setRenderHand(true);
		}
	}

	private static IronManDroneEntity drone(Minecraft mc) {
		if (mc.level == null || droneId < 0) {
			return null;
		}
		Entity e = mc.level.getEntity(droneId);
		return e instanceof IronManDroneEntity d && !d.isRemoved() ? d : null;
	}

	// ------------------------------------------------------------------ input

	private static void startTick(Minecraft mc) {
		if (!linked) {
			return;
		}
		if (mc.player == null || mc.level == null) {
			release();
			return;
		}
		IronManDroneEntity d = drone(mc);
		if (d == null) {
			if (mc.getCameraEntity() != mc.player) {
				mc.setCameraEntity(mc.player); // drone gone (destroyed / untracked)
			}
			if (--waitTicks <= 0) {
				ClientPlayNetworking.send(new IronManDroneInputPayload(0f, 0f, 0f, 0f, IronManDroneInputPayload.END));
				release();
			}
			return;
		}
		if (mc.getCameraEntity() != d) {
			mc.setCameraEntity(d);
			mc.gameRenderer.setRenderHand(false);
		}
		boolean free = mc.screen == null;
		var o = mc.options;
		kFwd = free && physical(mc, o.keyUp);
		kBack = free && physical(mc, o.keyDown);
		kLeft = free && physical(mc, o.keyLeft);
		kRight = free && physical(mc, o.keyRight);
		kUp = free && physical(mc, o.keyJump);
		kDown = free && physical(mc, o.keyShift);
		kBoost = free && physical(mc, o.keySprint);
		kFire = free && (physical(mc, o.keyAttack) || physical(mc, o.keyUse) || physical(mc, ModKeyBindings.ABILITY_1));
		// swallow everything the pilot uses so nothing else acts on it this tick
		for (KeyMapping k : new KeyMapping[] { o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift,
				o.keySprint, o.keyAttack, o.keyUse, o.keyPickItem }) {
			fireClicked |= drain(k) && (k == o.keyAttack || k == o.keyUse);
		}
		for (int i = 0; i < ModKeyBindings.ABILITY_SLOTS.length; i++) {
			boolean clicked = drain(ModKeyBindings.ABILITY_SLOTS[i]);
			if (i == 0) {
				fireClicked |= clicked;
			} else if (i == 5) {
				endClicked |= clicked;
			}
		}
		mc.player.xxa = 0f;
		mc.player.zza = 0f;
		mc.player.setJumping(false);
		mc.player.setSprinting(false);
	}

	/** Force a key up and drain its queued presses; true if it had any. */
	private static boolean drain(KeyMapping k) {
		boolean any = false;
		while (k.consumeClick()) {
			any = true;
		}
		k.setDown(false);
		return any;
	}

	/** The real, physical state of the key bound to {@code k} (keyboard or mouse button). */
	private static boolean physical(Minecraft mc, KeyMapping k) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(k);
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return false;
		}
		long window = mc.getWindow().getWindow();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
		}
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(window, key.getValue());
		}
		return false;
	}

	private static void endTick(Minecraft mc) {
		if (!linked || mc.player == null || !piloting()) {
			return;
		}
		float fwd = (kFwd ? 1f : 0f) - (kBack ? 1f : 0f);
		float str = (kLeft ? 1f : 0f) - (kRight ? 1f : 0f);
		int flags = 0;
		flags |= kUp ? IronManDroneInputPayload.UP : 0;
		flags |= kDown ? IronManDroneInputPayload.DOWN : 0;
		flags |= kBoost ? IronManDroneInputPayload.BOOST : 0;
		flags |= kFire || fireClicked ? IronManDroneInputPayload.FIRE : 0;
		flags |= endClicked ? IronManDroneInputPayload.END : 0;
		fireClicked = false;
		endClicked = false;
		mc.player.xxa = 0f;
		mc.player.zza = 0f;
		ClientPlayNetworking.send(new IronManDroneInputPayload(fwd, str, mc.player.getYRot(), mc.player.getXRot(), flags));
	}

	// ------------------------------------------------------------------ HUD

	private static void renderHud(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!piloting() || mc.options.hideGui || mc.player == null) {
			return;
		}
		IronManDroneEntity d = drone(mc);
		if (d == null) {
			return;
		}
		Font font = mc.font;
		int sw = g.guiWidth();
		int sh = g.guiHeight();

		// faint visor edge + corner brackets so it reads as a remote feed
		int edge = IronManGui.alpha(IronManGui.CYAN_FAINT, 0.5f);
		g.fill(0, 0, sw, 2, edge);
		g.fill(0, sh - 2, sw, sh, edge);
		IronManGui.brackets(g, 6, 6, sw - 12, sh - 12, 14, IronManGui.alpha(IronManGui.CYAN_DIM, 0.8f));

		int w = Math.min(200, sw - 24);
		int x = (sw - w) / 2;
		int y = 10;
		IronManGui.panel(g, x, y, w, 46, IronManGui.PANEL_BG_SOFT, IronManGui.PANEL_BORDER, IronManGui.CYAN_DIM);

		IronManSuit suit = d.suit();
		String title = Component.translatable("hud.projecthero.drone.title").getString();
		String name = suit != null ? Component.translatable(suit.nameKey()).getString() : d.suitId();
		long ms = Util.getMillis();
		boolean blink = (ms / 400) % 2 == 0;
		g.fill(x + 5, y + 6, x + 8, y + 9, blink ? IronManGui.RED : IronManGui.alpha(IronManGui.RED, 0.35f)); // live dot
		String head = IronManGui.fit(font, title + "  " + name, w - 16);
		g.drawString(font, head, x + 11, y + 4, IronManGui.CYAN, false);

		float cap = Math.max(1f, IronManEnergy.capacity(d.suitId()));
		float maxI = Math.max(1f, IronManEnergy.maxIntegrity(d.suitId()));
		float ef = d.energy() / cap;
		float inf = d.integrity() / maxI;
		int half = (w - 15) / 2;
		String eLabel = IronManGui.fit(font, Component.translatable("hud.projecthero.drone.energy").getString()
				+ " " + Math.round(ef * 100) + "%", half);
		String iLabel = IronManGui.fit(font, Component.translatable("hud.projecthero.drone.integrity").getString()
				+ " " + Math.round(inf * 100) + "%", half);
		g.drawString(font, eLabel, x + 5, y + 15, IronManGui.TEXT_DIM, false);
		g.drawString(font, iLabel, x + 10 + half, y + 15, IronManGui.TEXT_DIM, false);
		IronManGui.hairline(g, x + 5, y + 25, half, 2, ef, ef < 0.15f ? IronManGui.ORANGE : IronManGui.BLUE, IronManGui.BAR_BG);
		IronManGui.hairline(g, x + 10 + half, y + 25, half, 2, inf, inf < 0.3f ? IronManGui.RED : IronManGui.GREEN, IronManGui.BAR_BG);

		double dist = d.distanceTo(mc.player);
		float rf = (float) (dist / linkRange);
		boolean weak = dist >= linkRange * (IronManDrones.WARN_RANGE / IronManDrones.MAX_RANGE);
		String range = IronManGui.fit(font, Component.translatable("hud.projecthero.drone.range",
				(int) Math.round(dist), linkRange).getString(), w - 10);
		g.drawString(font, range, x + 5, y + 31, weak ? IronManGui.ORANGE : IronManGui.TEXT_DIM, false);
		IronManGui.hairline(g, x + 5, y + 41, w - 10, 2, rf, weak ? IronManGui.ORANGE : IronManGui.CYAN_DIM, IronManGui.BAR_BG);

		if (weak && blink) {
			Component warn = Component.translatable("hud.projecthero.drone.weak");
			String t = IronManGui.fit(font, warn, sw - 20);
			g.drawCenteredString(font, t, sw / 2, y + 52, IronManGui.ORANGE);
		}

		// controls hint above the hotbar, wrapped to fit
		List<FormattedCharSequence> hint = font.split(Component.translatable("hud.projecthero.drone.hint"), Math.min(300, sw - 20));
		int hy = sh - 62 - (hint.size() - 1) * 10;
		for (FormattedCharSequence line : hint) {
			g.drawCenteredString(font, line, sw / 2, hy, IronManGui.alpha(IronManGui.TEXT_DIM, 0.85f));
			hy += 10;
		}
	}
}
