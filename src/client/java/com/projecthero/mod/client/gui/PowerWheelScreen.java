package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.network.PowerSelectPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * HeroPack power-selection screen: choose which owned experimental power occupies Ability 1-6.
 * Opened with the dedicated power-select key (default H) -- never any of R/G/X/Z/V/C. A simple button
 * list for now; a radial wheel is a later polish pass. Switching here does not touch cooldowns
 * (enforced server-side in {@code ExperimentalPowers.setActive}).
 */
public final class PowerWheelScreen extends Screen {
	public PowerWheelScreen() {
		super(Component.translatable("screen.projecthero.power_select"));
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		List<Power> owned = new ArrayList<>();
		String active = "";
		if (mc.player != null) {
			ExperimentalState state = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
			if (state != null) {
				active = state.activePower;
				for (Power p : Powers.all()) {
					if (state.ownedPowers.contains(p.key())) {
						owned.add(p);
					}
				}
			}
		}

		int y = 40;
		int w = 220;
		int x = this.width / 2 - w / 2;

		// For a Spider-Man, "no mutation selected" is not nothing -- it is when the six slots belong to
		// the Hero Class, so the row says so rather than reading as switching your powers off.
		boolean spiderMan = mc.player != null && com.projecthero.mod.spider.SpiderMan.hasPower(mc.player);
		Component noneLabel = spiderMan
				? Component.translatable("projecthero.spider_man.name")
						.append(active.isEmpty()
								? Component.translatable("screen.projecthero.power_select.active_marker")
								: Component.empty())
				: Component.translatable("screen.projecthero.power_select.none");
		Button none = Button.builder(noneLabel, b -> select(""))
				.bounds(x, y, w, 20).build();
		addRenderableWidget(none);
		y += 24;

		for (Power power : owned) {
			Component label = Component.translatable(power.nameKey());
			if (power.key().equals(active)) {
				label = label.copy().append(Component.translatable("screen.projecthero.power_select.active_marker"));
			}
			String key = power.key();
			addRenderableWidget(Button.builder(label, b -> select(key)).bounds(x, y, w, 20).build());
			y += 24;
		}

		if (owned.isEmpty()) {
			// nothing owned yet -- the "None" button plus the label is enough
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(x, y + 8, w, 20).build());
	}

	private void select(String powerKey) {
		ClientPlayNetworking.send(new PowerSelectPayload(powerKey));
		onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
