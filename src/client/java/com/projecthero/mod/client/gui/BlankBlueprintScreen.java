package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.network.IronManBlueprintChoicePayload;
import com.projecthero.mod.network.IronManBlueprintPickerPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "changes 21": the Blank Blueprint picker. Opened when the player right-clicks a Blank Blueprint;
 * lists every mark that has a blueprint in progression order. An unlocked mark is a button that
 * stamps the blank; a locked mark is a disabled row that names the suit still to be built.
 */
public final class BlankBlueprintScreen extends Screen {
	private final List<IronManBlueprintPickerPayload.Entry> entries;

	public BlankBlueprintScreen(List<IronManBlueprintPickerPayload.Entry> entries) {
		super(Component.translatable("screen.projecthero.blank_blueprint.title"));
		this.entries = entries;
	}

	private static Component markName(String suitId) {
		return Component.translatable("projecthero.ironman.suit." + suitId + ".name");
	}

	@Override
	protected void init() {
		int w = 240;
		int x = this.width / 2 - w / 2;
		int y = 46;

		for (IronManBlueprintPickerPayload.Entry e : entries) {
			if (e.unlocked()) {
				String suitId = e.suitId();
				addRenderableWidget(Button.builder(
						Component.translatable("screen.projecthero.blank_blueprint.make", markName(suitId)),
						b -> pick(suitId)).bounds(x, y, w, 20).build());
			} else {
				Button locked = Button.builder(
						Component.translatable("screen.projecthero.blank_blueprint.locked",
								markName(e.suitId()), markName(e.prerequisiteSuitId())).withStyle(ChatFormatting.DARK_GRAY),
						b -> { }).bounds(x, y, w, 20).build();
				locked.active = false;
				addRenderableWidget(locked);
			}
			y += 23;
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(x, y + 8, w, 20).build());
	}

	private void pick(String suitId) {
		ClientPlayNetworking.send(new IronManBlueprintChoicePayload(suitId));
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFF7FE9FF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
