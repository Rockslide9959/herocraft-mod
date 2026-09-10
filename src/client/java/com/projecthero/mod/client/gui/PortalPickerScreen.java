package com.projecthero.mod.client.gui;

import com.projecthero.mod.network.PortalCreatePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Teleportation "Portal" destination picker (v0.10.13). Opened after the 5-second Z charge. Type an
 * exact X / Y / Z, pick a dimension, and open the gateway.
 */
public final class PortalPickerScreen extends Screen {
	private static final String[] DIMS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
	private static final String[] DIM_LABELS = {"Overworld", "The Nether", "The End"};

	private final int startX;
	private final int startY;
	private final int startZ;
	private EditBox fx;
	private EditBox fy;
	private EditBox fz;
	private int dim = 0;
	private Button dimButton;

	public PortalPickerScreen(double x, double y, double z) {
		super(Component.literal("Open a Portal"));
		this.startX = (int) Math.round(x);
		this.startY = (int) Math.round(y);
		this.startZ = (int) Math.round(z);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 50;
		fx = coordBox(cx - 90, y, startX);
		fy = coordBox(cx - 20, y, startY);
		fz = coordBox(cx + 50, y, startZ);
		addRenderableWidget(fx);
		addRenderableWidget(fy);
		addRenderableWidget(fz);

		dimButton = Button.builder(Component.literal("Dimension: " + DIM_LABELS[dim]), b -> {
			dim = (dim + 1) % DIMS.length;
			b.setMessage(Component.literal("Dimension: " + DIM_LABELS[dim]));
		}).bounds(cx - 100, y + 34, 200, 20).build();
		addRenderableWidget(dimButton);

		addRenderableWidget(Button.builder(Component.literal("Open Portal"), b -> confirm())
				.bounds(cx - 100, y + 60, 96, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(cx + 4, y + 60, 96, 20).build());
	}

	private EditBox coordBox(int x, int y, int value) {
		EditBox box = new EditBox(this.font, x, y, 60, 18, Component.literal("coord"));
		box.setValue(Integer.toString(value));
		box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,9}"));
		return box;
	}

	private void confirm() {
		int x = parse(fx, startX);
		int yy = parse(fy, startY);
		int z = parse(fz, startZ);
		ClientPlayNetworking.send(new PortalCreatePayload(x, yy, z, DIMS[dim]));
		onClose();
	}

	private static int parse(EditBox box, int fallback) {
		try {
			return Integer.parseInt(box.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(g, mouseX, mouseY, partialTick);
		super.render(g, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		int y = this.height / 2 - 50;
		g.drawCenteredString(this.font, this.title, cx, y - 26, 0xFFB57BFF);
		g.drawString(this.font, "X", cx - 90 + 26, y - 12, 0xFFAAAAAA);
		g.drawString(this.font, "Y", cx - 20 + 26, y - 12, 0xFFAAAAAA);
		g.drawString(this.font, "Z", cx + 50 + 26, y - 12, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
