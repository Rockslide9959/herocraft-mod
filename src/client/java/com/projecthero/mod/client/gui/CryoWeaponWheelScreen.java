package com.projecthero.mod.client.gui;

import com.projecthero.mod.network.CryoWeaponPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Cryokinesis ice-weapon wheel (v0.10.11): opened when the cryokinetic holds Sneak + R for two
 * seconds. A radial menu of the five ice tools -- point at a wedge and click (or press its number
 * key) to shape that one; click the centre or press Escape to cancel.
 */
public final class CryoWeaponWheelScreen extends Screen {
	private record Entry(int index, ItemStack icon, Component label) {
	}

	private static final Entry[] ENTRIES = {
			new Entry(0, new ItemStack(Items.IRON_PICKAXE), Component.literal("Ice Pickaxe")),
			new Entry(1, new ItemStack(Items.IRON_SWORD), Component.literal("Ice Sword")),
			new Entry(2, new ItemStack(Items.IRON_AXE), Component.literal("Ice Axe")),
			new Entry(3, new ItemStack(Items.IRON_SHOVEL), Component.literal("Ice Shovel")),
			new Entry(4, new ItemStack(Items.IRON_HOE), Component.literal("Ice Hoe")),
	};

	private int hovered = -1;

	public CryoWeaponWheelScreen() {
		super(Component.translatable("screen.projecthero.cryo_wheel"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int cx = this.width / 2;
		int cy = this.height / 2;
		int radius = 90;
		int n = ENTRIES.length;

		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > 26) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}

		g.drawCenteredString(this.font, this.title, cx, cy - radius - 30, 0xFF9FD6FF);

		for (int i = 0; i < n; i++) {
			double a = -Math.PI / 2 + i * (Math.PI * 2 / n);
			int x = cx + (int) (Math.cos(a) * radius);
			int y = cy + (int) (Math.sin(a) * radius);
			boolean isHover = i == hovered;
			int box = 52;
			g.fill(x - box, y - 16, x + box, y + 16, isHover ? 0xE01E3A55 : 0xB0101820);
			g.renderOutline(x - box, y - 16, box * 2, 32, isHover ? 0xFF9FD6FF : 0x40FFFFFF);
			g.renderItem(ENTRIES[i].icon(), x - box + 6, y - 8);
			g.drawString(this.font, ENTRIES[i].label(), x - box + 28, y - 4,
					isHover ? 0xFFFFFFFF : 0xFFBFE4FF, false);
		}

		Component centre = hovered >= 0 ? ENTRIES[hovered].label()
				: Component.translatable("screen.projecthero.cryo_wheel.cancel").withStyle(ChatFormatting.GRAY);
		g.drawCenteredString(this.font, centre, cx, cy - 4, 0xFFDDEEFF);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < ENTRIES.length) {
			ClientPlayNetworking.send(new CryoWeaponPayload(ENTRIES[hovered].index()));
			onClose();
			return true;
		}
		if (Math.hypot(mouseX - this.width / 2.0, mouseY - this.height / 2.0) <= 26) {
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		int digit = keyCode - 49; // GLFW_KEY_1 == 49
		if (digit >= 0 && digit < ENTRIES.length) {
			ClientPlayNetworking.send(new CryoWeaponPayload(digit));
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
