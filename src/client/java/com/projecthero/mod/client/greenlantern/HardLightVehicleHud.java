package com.projecthero.mod.client.greenlantern;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.entity.HardLightCarEntity;
import com.projecthero.mod.greenlantern.entity.HardLightJetEntity;
import com.projecthero.mod.greenlantern.entity.HardLightVehicleEntity;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1 (user: "make it display on the bottom left of the players screen the entire time ... only show up for the driver"):
 * the Hard-Light Car / Jet controls, always up while you drive -- the vehicle's name, its speed (and STALL on a slow Jet),
 * then each control with the player's own key bindings. Passengers see nothing. Hidden while the chat is open (it sits
 * where the chat does).
 */
public final class HardLightVehicleHud {
	private static final int MARGIN = 4;
	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int BG = 0xB00A2412;
	private static final int BORDER = 0xFF1E661E;
	private static final int TITLE = 0xFF35F075;
	private static final int KEY = 0xFFFFD25A;
	private static final int TEXT = 0xFFCFF8D4;
	private static final int LOW = 0xFFFF5A5A;

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
		boolean stall = jet && bps < GreenLanternConfig.VEHICLE_JET_STALL_BPS;
		Component shift = mc.options.keyShift.getTranslatedKeyMessage();
		Component r = ModKeyBindings.ABILITY_1.getTranslatedKeyMessage();
		Component ws = pair(mc.options.keyUp, mc.options.keyDown);
		Component ad = pair(mc.options.keyLeft, mc.options.keyRight);
		Component shiftR = Component.empty().append(shift).append("+").append(r);
		Component holdShift = Component.translatable("hud.projecthero.green_lantern.vehicle.hold", shift);

		List<Row> rows = new ArrayList<>();
		if (jet) {
			rows.add(new Row(Component.translatable("hud.projecthero.green_lantern.vehicle.mouse"), act("jet.steer")));
			rows.add(new Row(ws, act("jet.throttle")));
			rows.add(new Row(ad, act("jet.yaw")));
			rows.add(new Row(Component.translatable("hud.projecthero.green_lantern.vehicle.hold", r), act("jet.cannons")));
			rows.add(new Row(shiftR, act("jet.afterburner")));
		} else {
			rows.add(new Row(ws, act("car.drive")));
			rows.add(new Row(ad, act("car.steer")));
			rows.add(new Row(r, act("car.bolt")));
			rows.add(new Row(shiftR, act("car.horn")));
		}
		rows.add(new Row(holdShift, act("exit")));

		Component title = Component.translatable(jet ? "hud.projecthero.green_lantern.vehicle.jet" : "hud.projecthero.green_lantern.vehicle.car");
		Component speed = Component.translatable("hud.projecthero.green_lantern.vehicle.speed", Math.round(bps));
		Component stallTag = Component.translatable("hud.projecthero.green_lantern.vehicle.stall");

		int keyW = 0;
		int actW = 0;
		for (Row row : rows) {
			keyW = Math.max(keyW, mc.font.width(row.key()));
			actW = Math.max(actW, mc.font.width(row.action()));
		}
		int headW = mc.font.width(title) + 8 + mc.font.width(speed) + (stall ? 4 + mc.font.width(stallTag) : 0);
		int w = Math.max(keyW + 6 + actW, headW) + PAD * 2;
		int h = PAD * 2 + LINE + 3 + rows.size() * LINE - 1;
		int x0 = MARGIN;
		int y0 = g.guiHeight() - MARGIN - h;

		g.fill(x0, y0, x0 + w, y0 + h, BG);
		g.renderOutline(x0, y0, w, h, BORDER);
		int y = y0 + PAD;
		g.drawString(mc.font, title, x0 + PAD, y, TITLE, true);
		int sx = x0 + w - PAD - mc.font.width(speed);
		g.drawString(mc.font, speed, sx, y, stall ? LOW : TEXT, true);
		if (stall && (mc.player.tickCount / 6) % 2 == 0) {
			g.drawString(mc.font, stallTag, sx - 4 - mc.font.width(stallTag), y, LOW, true);
		}
		y += LINE;
		g.fill(x0 + PAD, y, x0 + w - PAD, y + 1, BORDER);
		y += 3;
		for (Row row : rows) {
			g.drawString(mc.font, row.key(), x0 + PAD + keyW - mc.font.width(row.key()), y, KEY, true);
			g.drawString(mc.font, row.action(), x0 + PAD + keyW + 6, y, TEXT, true);
			y += LINE;
		}
	}

	private static Component pair(KeyMapping a, KeyMapping b) {
		return Component.empty().append(a.getTranslatedKeyMessage()).append("/").append(b.getTranslatedKeyMessage());
	}

	private static Component act(String id) {
		return Component.translatable("hud.projecthero.green_lantern.vehicle." + id);
	}
}
