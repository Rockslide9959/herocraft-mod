package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.network.PunisherArsenalPayload;
import com.projecthero.mod.punisher.data.PunisherState;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Arsenal weapon wheel (spec section 22). Opened by holding R while the Punisher power is held;
 * lists the pistol plus every firearm the player has unlocked. Point at a wedge and release R (or
 * click) to equip. If R was already released by the time it opens, it closes at once.
 */
public final class ArsenalWheelScreen extends Screen {
	private final List<String> weapons = new ArrayList<>();
	private int hovered = -1;
	private boolean sawKeyDown;

	public ArsenalWheelScreen() {
		super(Component.translatable("screen.projecthero.arsenal.title"));
		weapons.add(Firearms.PISTOL);
		Minecraft mc = Minecraft.getInstance();
		PunisherState s = mc.player == null ? null
				: mc.player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		if (s != null) {
			for (String w : new String[] { Firearms.RIFLE, Firearms.SHOTGUN, Firearms.SNIPER }) {
				if (s.unlockedWeapons.contains(w)) {
					weapons.add(w);
				}
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int cx = this.width / 2;
		int cy = this.height / 2;
		int radius = 74;
		int n = weapons.size();

		double ang = Math.atan2(mouseY - cy, mouseX - cx);
		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > 20 && n > 0) {
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}

		g.drawCenteredString(this.font, this.title, cx, cy - radius - 28, 0xFFE8E8E8);
		for (int i = 0; i < n; i++) {
			double a = -Math.PI / 2 + i * (Math.PI * 2 / n);
			int x = cx + (int) (Math.cos(a) * radius);
			int y = cy + (int) (Math.sin(a) * radius);
			boolean isHover = i == hovered;
			int box = 40;
			g.fill(x - box, y - 14, x + box, y + 14, isHover ? 0xE0303030 : 0xB0121212);
			g.renderOutline(x - box, y - 14, box * 2, 28, isHover ? 0xFFE0C060 : 0x40FFFFFF);
			g.drawCenteredString(this.font, Component.translatable("item.projecthero." + weapons.get(i)),
					x, y - 4, isHover ? 0xFFFFFFFF : 0xFFC8C8C8);
		}
		g.drawCenteredString(this.font, Component.translatable("screen.projecthero.arsenal.hint"),
				cx, cy + radius + 22, 0xFF9AA0B0);
	}

	@Override
	public void tick() {
		// Fallback: some setups don't deliver a keyReleased for a key held when the screen opened.
		if (!ModKeyBindings.ABILITY_1.isDown() && sawKeyDown) {
			equipAndClose();
		} else if (ModKeyBindings.ABILITY_1.isDown()) {
			sawKeyDown = true;
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.ABILITY_1.matches(keyCode, scanCode)) {
			equipAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	private void equipAndClose() {
		if (hovered >= 0 && hovered < weapons.size()) {
			ClientPlayNetworking.send(new PunisherArsenalPayload(weapons.get(hovered)));
		}
		onClose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0) {
			equipAndClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
