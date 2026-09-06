package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.network.IronManWeaponWheelPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Mark 7 weapon wheel ("changes 16", extended "changes 17"): a radial menu with six sectors --
 * five that re-bind slot 3 (X) to an ability (micro missiles, flamethrower, wrist laser, rocket,
 * supersonic flight) and one that toggles the coloured entity-glow overlay on/off. Opened by the V
 * slot. Hovering a sector highlights it; clicking sends the choice and closes.
 *
 * <p>"changes 17": the hover hit-test is offset by half a sector so the wedge you are pointing at is
 * the wedge whose label sits under the cursor -- previously the sector boundaries were rotated half a
 * wedge off the drawn boxes.
 */
public final class IronManWeaponWheelScreen extends Screen {
	private static final String[] SECTORS = IronManAbilities.WEAPON_WHEEL_SECTORS;
	private int hovered = -1;

	public IronManWeaponWheelScreen() {
		super(Component.translatable("screen.projecthero.weapon_wheel.title"));
	}

	private String currentBinding() {
		Minecraft mc = Minecraft.getInstance();
		TonyStarkState s = mc.player == null ? null : mc.player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		return s == null ? SECTORS[0] : s.weaponWheelChoice;
	}

	private boolean glowOn() {
		Minecraft mc = Minecraft.getInstance();
		TonyStarkState s = mc.player == null ? null : mc.player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		return s != null && s.mobHighlightOn;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int cx = this.width / 2;
		int cy = this.height / 2;
		int radius = 78;

		double ang = Math.atan2(mouseY - cy, mouseX - cx);
		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > 22) {
			// 0 at top, increasing clockwise; + half a sector so each drawn box sits in the MIDDLE of
			// its own hit wedge rather than at the leading edge.
			double norm = (ang + Math.PI / 2 + Math.PI / SECTORS.length + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * SECTORS.length) % SECTORS.length;
		}

		g.drawCenteredString(this.font, this.title, cx, cy - radius - 26, 0xFF7FE9FF);
		String curBinding = currentBinding();
		boolean glow = glowOn();

		for (int i = 0; i < SECTORS.length; i++) {
			double a = -Math.PI / 2 + i * (Math.PI * 2 / SECTORS.length);
			int x = cx + (int) (Math.cos(a) * radius);
			int y = cy + (int) (Math.sin(a) * radius);
			boolean isHover = i == hovered;
			String sector = SECTORS[i];
			boolean isGlow = IronManAbilities.ENTITY_GLOW_TOGGLE.equals(sector);
			boolean isActive = isGlow ? glow : sector.equals(curBinding);
			int box = 32;
			int bg = isHover ? 0xE0203050 : 0xB0101018;
			g.fill(x - box, y - box / 2, x + box, y + box / 2, bg);
			g.renderOutline(x - box, y - box / 2, box * 2, box,
					isActive ? 0xFFFFC24A : (isHover ? 0xFF7FE9FF : 0x40FFFFFF));
			Component name = Component.translatable("hud.projecthero.ironman.ability." + sector);
			g.drawCenteredString(this.font, name, x, y - 8, isHover ? 0xFFFFFFFF : 0xFFC0C6E0);
			if (isGlow) {
				g.drawCenteredString(this.font, Component.literal(glow ? "ON" : "OFF")
						.withStyle(glow ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.DARK_GRAY),
						x, y + 3, glow ? 0xFF7FE9FF : 0xFF6A7286);
			}
		}

		g.drawCenteredString(this.font, Component.translatable("screen.projecthero.weapon_wheel.hint"),
				cx, cy + radius + 20, 0xFF9AA6D0);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < SECTORS.length) {
			ClientPlayNetworking.send(new IronManWeaponWheelPayload(SECTORS[hovered]));
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
