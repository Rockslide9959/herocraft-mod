package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hammer.ThorWeaponSelection;
import com.projecthero.mod.network.ThorWeaponTogglePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.3: Thor's N weapon screen -- one row per callable weapon (Mjolnir, Stormbreaker): its icon, its name, whether
 * one is bound to you, and an Active / Inactive toggle. Only ACTIVE weapons answer R. Purely a request form: each toggle
 * sends a {@link ThorWeaponTogglePayload}, the server re-validates it ({@code ThorWeaponSelection.handleToggle}) and the
 * synced {@link ModAttachments#THOR_WEAPONS_INACTIVE} attachment is what the rows show, so the screen never disagrees
 * with the server. Every line of prose is word-wrapped to the panel. Drawn like the Bifrost menu: gold on night blue.
 */
public final class ThorWeaponScreen extends Screen {
	private static final int PANEL_W = 236;
	private static final int ROW_H = 30;
	private static final int TEXT_W = PANEL_W - 24;

	private static final int PANEL_TOP = 0xF2141A2E;
	private static final int PANEL_BOTTOM = 0xF20A0D18;
	private static final int GOLD = 0xFFE8C766;
	private static final int GOLD_DIM = 0xFF8C7536;
	private static final int TEXT = 0xFFE8ECF8;
	private static final int MUTED = 0xFF8D94AD;
	private static final int ACTIVE = 0xFF7FD8FF;
	private static final int WARN = 0xFFE0A060;
	private static final int ROW_ON = 0x60283A60;
	private static final int ROW_OFF = 0x40181C2C;

	private final Button[] toggles = new Button[ThorWeapon.values().length];
	private List<FormattedCharSequence> intro = List.of();
	private List<FormattedCharSequence> warning = List.of();
	private int panelH;

	public ThorWeaponScreen() {
		super(Component.translatable("screen.projecthero.thor_weapons.title"));
	}

	private static Player player() {
		return Minecraft.getInstance().player;
	}

	private static boolean active(ThorWeapon weapon) {
		Player p = player();
		return p == null || ThorWeaponSelection.isActive(p, weapon);
	}

	private static boolean bound(ThorWeapon weapon) {
		Player p = player();
		return p != null && p.getAttachedOrElse(weapon == ThorWeapon.STORMBREAKER
				? ModAttachments.BOUND_STORMBREAKER_ID : ModAttachments.BOUND_HAMMER_ID, null) != null;
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH) / 2);
	}

	/** First row's top edge, below the title and the wrapped intro. */
	private int rowsTop() {
		return top() + 30 + intro.size() * 10 + 4;
	}

	@Override
	protected void init() {
		intro = this.font.split(Component.translatable("screen.projecthero.thor_weapons.intro"), TEXT_W);
		warning = this.font.split(Component.translatable("screen.projecthero.thor_weapons.none_active"), TEXT_W);
		// room for the warning is always kept, so the panel does not jump when it appears
		panelH = 30 + intro.size() * 10 + 4 + ThorWeapon.values().length * ROW_H + 4 + warning.size() * 10 + 30;

		int l = left();
		int y0 = rowsTop();
		for (ThorWeapon weapon : ThorWeapon.values()) {
			int y = y0 + weapon.ordinal() * ROW_H;
			toggles[weapon.ordinal()] = addRenderableWidget(Button.builder(label(weapon), b -> toggle(weapon))
					.bounds(l + PANEL_W - 82, y + 5, 70, 18).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(l + PANEL_W - 62, top() + panelH - 24, 52, 18).build());
		refresh();
	}

	private static Component label(ThorWeapon weapon) {
		return active(weapon)
				? Component.translatable("screen.projecthero.thor_weapons.active").withStyle(ChatFormatting.AQUA)
				: Component.translatable("screen.projecthero.thor_weapons.inactive").withStyle(ChatFormatting.GRAY);
	}

	private void refresh() {
		for (ThorWeapon weapon : ThorWeapon.values()) {
			Button button = toggles[weapon.ordinal()];
			if (button != null) {
				button.setMessage(label(weapon));
				button.setTooltip(Tooltip.create(Component.translatable(active(weapon)
						? "screen.projecthero.thor_weapons.tip_off" : "screen.projecthero.thor_weapons.tip_on",
						Component.translatable(weapon.item().getDescriptionId()))));
			}
		}
	}

	private static void toggle(ThorWeapon weapon) {
		ClientPlayNetworking.send(new ThorWeaponTogglePayload(weapon.ordinal(), !active(weapon)));
	}

	@Override
	public void tick() {
		super.tick();
		// the server's answer arrives as the synced attachment; also closes if the player stops being Thor
		Player p = player();
		if (p == null || !ThorWeaponSelection.ownsSelector(p)) {
			onClose();
			return;
		}
		refresh();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		int r = l + PANEL_W;
		int b = t + panelH;
		g.fill(l - 1, t - 1, r + 1, b + 1, GOLD_DIM);
		g.fillGradient(l, t, r, b, PANEL_TOP, PANEL_BOTTOM);
		g.fill(l + 2, t + 2, r - 2, t + 3, 0x40FFFFFF);
		g.fill(l + 12, t + 22, r - 12, t + 23, GOLD_DIM);
		int y0 = rowsTop();
		for (ThorWeapon weapon : ThorWeapon.values()) {
			int y = y0 + weapon.ordinal() * ROW_H;
			boolean on = active(weapon);
			g.fill(l + 8, y, r - 8, y + ROW_H - 2, on ? ROW_ON : ROW_OFF);
			g.fill(l + 8, y, l + 10, y + ROW_H - 2, on ? ACTIVE : GOLD_DIM);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		g.drawCenteredString(this.font, this.title, l + PANEL_W / 2, t + 9, GOLD);
		int y = t + 30;
		for (FormattedCharSequence line : intro) {
			g.drawString(this.font, line, l + 12, y, MUTED, false);
			y += 10;
		}
		int y0 = rowsTop();
		for (ThorWeapon weapon : ThorWeapon.values()) {
			int ry = y0 + weapon.ordinal() * ROW_H;
			boolean on = active(weapon);
			g.renderItem(new ItemStack(weapon.item()), l + 14, ry + 6);
			if (!on) {
				// dim the icon of a switched-off weapon
				g.fill(l + 14, ry + 6, l + 30, ry + 22, 0x90101420);
			}
			g.drawString(this.font, Component.translatable(weapon.item().getDescriptionId()), l + 36, ry + 5,
					on ? TEXT : MUTED, false);
			g.drawString(this.font, Component.translatable(bound(weapon)
							? "screen.projecthero.thor_weapons.bound" : "screen.projecthero.thor_weapons.unbound"),
					l + 36, ry + 16, bound(weapon) ? GOLD_DIM : 0xFF6A6F80, false);
		}
		boolean none = true;
		for (ThorWeapon weapon : ThorWeapon.values()) {
			none &= !active(weapon);
		}
		if (none) {
			int wy = y0 + ThorWeapon.values().length * ROW_H + 4;
			for (FormattedCharSequence line : warning) {
				g.drawString(this.font, line, l + 12, wy, WARN, false);
				wy += 10;
			}
		}
		g.drawString(this.font, Component.translatable("screen.projecthero.thor_weapons.footer"), l + 12,
				t + panelH - 19, MUTED, false);
	}

	/** N again closes it, like the inventory key closes the inventory. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (com.projecthero.mod.client.ModKeyBindings.MAX_STEEL_TRANSFORM.matches(keyCode, scanCode)) {
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
