package com.projecthero.mod.client.greenlantern;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.greenlantern.entity.HardLightCarEntity;
import com.projecthero.mod.greenlantern.entity.HardLightJetEntity;
import com.projecthero.mod.greenlantern.entity.HardLightVehicleEntity;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1 (user: "make it display on the bottom left of the players screen the entire time ... only show up for the driver"):
 * the Hard-Light Car / Jet controls, always up while you drive -- the vehicle's name, its speed (the Jet: its throttle lever,
 * and HOVER while it is under lift speed on its VTOL jets), then each control with the player's own key bindings. Passengers see nothing. Hidden while the chat is open (it sits
 * where the chat does). v0.17.3: drawn like the game's own HUD (shadowed chat-palette text, the throttle a boss bar), no panel.
 */
public final class HardLightVehicleHud {
	private static final int MARGIN = 4;
	private static final int LINE = 10;
	private static final int TITLE = 0xFF35F075;

	private HardLightVehicleHud() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(HardLightVehicleHud::render);
	}

	private record Row(Component key, Component action) {
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen instanceof ChatScreen) {
			return;
		}
		Entity vehicle = mc.player.getVehicle();
		if (!(vehicle instanceof HardLightVehicleEntity v) || v.getControllingPassenger() != mc.player) {
			return;
		}
		boolean jet = v instanceof HardLightJetEntity;
		if (!jet && !(v instanceof HardLightCarEntity)) {
			return;
		}

		double bps = v.position().subtract(v.xo, v.yo, v.zo).length() * 20.0;
		// v0.17.1 round 2: no more STALL -- a slow Jet hovers on its VTOL jets (a calm HOVER tag), and the throttle lever shows
		boolean stall = jet && ((HardLightJetEntity) v).hovering();
		Component shift = mc.options.keyShift.getTranslatedKeyMessage();
		Component r = ModKeyBindings.ABILITY_1.getTranslatedKeyMessage();
		Component ws = pair(mc.options.keyUp, mc.options.keyDown);
		Component ad = pair(mc.options.keyLeft, mc.options.keyRight);
		Component shiftR = Component.empty().append(shift).append("+").append(r);
		Component holdShift = Component.translatable("hud.projecthero.green_lantern.vehicle.hold", shift);
		Component space = mc.options.keyJump.getTranslatedKeyMessage();

		List<Row> rows = new ArrayList<>();
		if (jet) {
			rows.add(new Row(Component.translatable("hud.projecthero.green_lantern.vehicle.mouse"), act("jet.steer")));
			rows.add(new Row(ws, act("jet.throttle")));
			rows.add(new Row(ad, act("jet.yaw")));
			rows.add(new Row(space, act("jet.rise")));
			rows.add(new Row(shift, act("jet.descend")));
			rows.add(new Row(Component.translatable("hud.projecthero.green_lantern.vehicle.hold", r), act("jet.cannons")));
			rows.add(new Row(shiftR, act("jet.afterburner")));
			rows.add(new Row(holdShift, act("jet.exit")));
		} else {
			rows.add(new Row(ws, act("car.drive")));
			rows.add(new Row(ad, act("car.steer")));
			rows.add(new Row(Component.translatable("hud.projecthero.green_lantern.vehicle.mouse_w", mc.options.keyUp.getTranslatedKeyMessage()),
					act("car.assist")));
			rows.add(new Row(r, act("car.bolt")));
			rows.add(new Row(shiftR, act("car.horn")));
			rows.add(new Row(holdShift, act("exit")));
		}

		Component title = Component.translatable(jet ? "hud.projecthero.green_lantern.vehicle.jet" : "hud.projecthero.green_lantern.vehicle.car");
		Component speed = Component.translatable("hud.projecthero.green_lantern.vehicle.speed", Math.round(bps));
		Component stallTag = Component.translatable("hud.projecthero.green_lantern.vehicle.hover");

		// v0.17.3 (user: "I really like the Minecraft-style HUD ... apply this to all the powers"): no framed panel any more --
		// shadowed text in the chat palette like the game's own HUD, the Jet's throttle a boss bar. Still bottom left (the
		// user's placement), kept clear of the hotbar: everything is cut to the room left of it.
		Font font = mc.font;
		int maxW = Math.max(80, Math.min(200, g.guiWidth() / 2 - 91 - 8 - MARGIN));
		int keyW = 0;
		for (Row row : rows) {
			keyW = Math.max(keyW, font.width(row.key()));
		}
		keyW = Math.min(keyW, maxW / 2);
		int x0 = MARGIN;
		int y = g.guiHeight() - MARGIN - 8;
		// the controls, bottom up (the last row at the bottom)
		for (int i = rows.size() - 1; i >= 0; i--) {
			Row row = rows.get(i);
			String key = cut(font, row.key().getString(), keyW);
			g.drawString(font, key, x0 + keyW - font.width(key), y, VanillaHud.YELLOW, true);
			g.drawString(font, cut(font, row.action().getString(), maxW - keyW - 6), x0 + keyW + 6, y, VanillaHud.WHITE, true);
			y -= LINE;
		}
		y -= 2;
		if (jet) {
			// the throttle lever: label, boss bar, percent
			float throttle = ((HardLightJetEntity) v).throttle();
			VanillaHud.meter(g, x0, y + 2, maxW, Component.translatable("hud.projecthero.vanilla_hud.throttle"), throttle, TITLE,
					Math.round(throttle * 100f) + "%", VanillaHud.WHITE);
			y -= 12;
		}
		// the name, the speed and the Jet's HOVER
		String head = cut(font, title.getString(), maxW / 2);
		g.drawString(font, head, x0, y, TITLE, true);
		int sx = x0 + font.width(head) + 6;
		String sp = speed.getString();
		g.drawString(font, sp, sx, y, VanillaHud.GREY, true);
		if (stall && sx + font.width(sp) + 6 + font.width(stallTag) <= x0 + maxW) {
			g.drawString(font, stallTag, sx + font.width(sp) + 6, y, VanillaHud.YELLOW, true);
		}
	}

	private static String cut(Font font, String text, int w) {
		return font.width(text) <= w ? text : font.plainSubstrByWidth(text, Math.max(0, w - font.width("."))) + ".";
	}

	private static Component pair(KeyMapping a, KeyMapping b) {
		return Component.empty().append(a.getTranslatedKeyMessage()).append("/").append(b.getTranslatedKeyMessage());
	}

	private static Component act(String id) {
		return Component.translatable("hud.projecthero.green_lantern.vehicle." + id);
	}
}
