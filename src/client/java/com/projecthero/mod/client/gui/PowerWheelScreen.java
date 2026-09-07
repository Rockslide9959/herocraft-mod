package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.network.PowerSelectPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The HeroPack power wheel: a radial menu for switching which owned mutation occupies ability slots
 * R/G/X/Z/V/C. Opened by the dedicated power key (default H). Point at a wedge and either click it or
 * release H to select; release H over the centre (or press Escape) to cancel. Switching here never
 * touches cooldowns (enforced server-side in {@code ExperimentalPowers.setActive}).
 *
 * <p>The first wedge is "no mutation": for a plain player it is powers-off; for a Hero Class player
 * (Spider-Man, etc.) it is "hand the slots back to the Hero Class", so it is labelled with that
 * class's name rather than reading as switching powers off.
 */
public final class PowerWheelScreen extends Screen {
	private final List<Entry> entries = new ArrayList<>();
	private int hovered = -1;

	private record Entry(String key, Component label, boolean active) {
	}

	private int ticksOpen = 0;
	/** Once true, the open key is treated as a click-to-select menu, not a hold-and-release wheel. */
	private boolean menuMode = false;

	public PowerWheelScreen() {
		super(Component.translatable("screen.projecthero.power_select"));
	}

	@Override
	public void tick() {
		ticksOpen++;
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (!menuMode && com.projecthero.mod.client.ModKeyBindings.POWER_SELECT.matches(keyCode, scanCode)) {
			if (ticksOpen <= 4) {
				// A quick tap: keep it open as a click-to-select menu rather than closing instantly.
				menuMode = true;
			} else {
				// Held, then released: weapon-wheel style select-on-release.
				confirmAndClose();
			}
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	protected void init() {
		entries.clear();
		Minecraft mc = Minecraft.getInstance();
		String active = "";
		ExperimentalState state = mc.player == null ? null
				: mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (state != null) {
			active = state.activePower;
		}

		boolean spiderMan = mc.player != null && com.projecthero.mod.spider.SpiderMan.hasPower(mc.player);
		Component noneLabel = spiderMan
				? Component.translatable("projecthero.spider_man.name")
				: Component.translatable("screen.projecthero.power_select.none");
		entries.add(new Entry("", noneLabel, active.isEmpty()));

		if (state != null) {
			for (Power p : Powers.all()) {
				if (state.ownedPowers.contains(p.key())) {
					entries.add(new Entry(p.key(), Component.translatable(p.nameKey()), p.key().equals(active)));
				}
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int cx = this.width / 2;
		int cy = this.height / 2;
		int radius = Math.max(70, Math.min(120, 34 + entries.size() * 9));
		int n = entries.size();

		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > 24 && n > 0) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}

		g.drawCenteredString(this.font, this.title, cx, cy - radius - 28, 0xFFB8B0F0);

		for (int i = 0; i < n; i++) {
			double a = -Math.PI / 2 + i * (Math.PI * 2 / n);
			int x = cx + (int) (Math.cos(a) * radius);
			int y = cy + (int) (Math.sin(a) * radius);
			Entry e = entries.get(i);
			boolean isHover = i == hovered;
			int box = 46;
			g.fill(x - box, y - 11, x + box, y + 11, isHover ? 0xE0281E44 : 0xB0101018);
			g.renderOutline(x - box, y - 11, box * 2, 22,
					e.active ? 0xFFC69BFF : (isHover ? 0xFF9A7FE0 : 0x40FFFFFF));
			g.drawCenteredString(this.font, e.label, x, y - 4,
					isHover ? 0xFFFFFFFF : (e.active ? 0xFFC69BFF : 0xFFC0C6E0));
		}

		Component centre = hovered >= 0 ? entries.get(hovered).label
				: Component.translatable("screen.projecthero.power_select.cancel").withStyle(ChatFormatting.GRAY);
		g.drawCenteredString(this.font, centre, cx, cy - 4, 0xFFDDDDF0);
		g.drawCenteredString(this.font, Component.translatable("screen.projecthero.power_select.hint"),
				cx, cy + radius + 22, 0xFF9AA6D0);
	}

	/** Called when the open key is released (weapon-wheel style): pick the hovered wedge, or cancel. */
	public void confirmAndClose() {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new PowerSelectPayload(entries.get(hovered).key()));
		}
		onClose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new PowerSelectPayload(entries.get(hovered).key()));
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
