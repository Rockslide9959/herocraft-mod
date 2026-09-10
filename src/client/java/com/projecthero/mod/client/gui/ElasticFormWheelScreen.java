package com.projecthero.mod.client.gui;

import com.projecthero.mod.network.ElasticFormPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Elasticity body-shape wheel (v0.10.13): opened by Shift + C. Three wedges -- Elastic (the
 * default stretchy shape), Inflated (a bouncy tank that flings attackers away), and Compression (a
 * one-block-tall sprinter). Point at a wedge and click, or press its number key; click the centre or
 * press Escape to keep the current shape.
 */
public final class ElasticFormWheelScreen extends Screen {
	private record Entry(int index, ItemStack icon, Component label, Component blurb) {
	}

	private static final Entry[] ENTRIES = {
			new Entry(0, new ItemStack(Items.SLIME_BALL), Component.literal("Elastic"),
					Component.literal("+reach, +speed, high jump, deflects projectiles")),
			new Entry(1, new ItemStack(Items.HONEY_BLOCK), Component.literal("Inflated"),
					Component.literal("wider, Slowness II, -50% damage, flings attackers 10 blocks")),
			new Entry(2, new ItemStack(Items.PISTON), Component.literal("Compression"),
					Component.literal("one block tall, +25% move speed")),
	};

	private int hovered = -1;

	public ElasticFormWheelScreen() {
		super(Component.literal("Elastic Form"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int cx = this.width / 2;
		int cy = this.height / 2;
		int radius = 96;
		int n = ENTRIES.length;

		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > 26) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}

		g.drawCenteredString(this.font, this.title, cx, cy - radius - 34, 0xFF9FE7A6);

		for (int i = 0; i < n; i++) {
			double a = -Math.PI / 2 + i * (Math.PI * 2 / n);
			int x = cx + (int) (Math.cos(a) * radius);
			int y = cy + (int) (Math.sin(a) * radius);
			boolean isHover = i == hovered;
			int box = 64;
			g.fill(x - box, y - 18, x + box, y + 18, isHover ? 0xE01E552B : 0xB0101820);
			g.renderOutline(x - box, y - 18, box * 2, 36, isHover ? 0xFF9FE7A6 : 0x40FFFFFF);
			g.renderItem(ENTRIES[i].icon(), x - box + 6, y - 8);
			g.drawString(this.font, ENTRIES[i].label(), x - box + 28, y - 6,
					isHover ? 0xFFFFFFFF : 0xFFBFE4C6, false);
		}

		Component centre = hovered >= 0 ? ENTRIES[hovered].blurb()
				: Component.literal("keep current shape").withStyle(ChatFormatting.GRAY);
		g.drawCenteredString(this.font, centre, cx, cy - 4, 0xFFDDEEDD);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < ENTRIES.length) {
			ClientPlayNetworking.send(new ElasticFormPayload(ENTRIES[hovered].index()));
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
			ClientPlayNetworking.send(new ElasticFormPayload(digit));
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
