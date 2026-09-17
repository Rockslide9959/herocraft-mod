package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.network.GreenLanternConstructSelectPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Green Lantern construct wheel (v0.11.2): a radial menu listing every construct unlocked at the
 * player's current Mastery level, opened by holding C for
 * {@code GreenLanternConfig.CONSTRUCT_WHEEL_HOLD_TICKS} (see
 * {@code ProjectHeroModClient#handleGreenLanternConstructWheelHold}). Point at a wedge and either
 * click it or release C to select; release over the centre (or press Escape) cancels. This only
 * changes which construct is <em>selected</em> -- deploying it is still a plain tap of C, unchanged.
 *
 * <p>Modelled directly on {@link PowerWheelScreen}, minus its tap-vs-hold bookkeeping: by the time
 * this screen exists the hold gesture has already been recognised client-side, so a release here
 * always means "confirm", never "was actually just a tap".
 */
public final class GreenLanternConstructWheelScreen extends Screen {
	private final List<ConstructType> entries = new ArrayList<>();
	private int hovered = -1;
	private int selectedOrdinal = -1;

	public GreenLanternConstructWheelScreen() {
		super(Component.translatable("screen.projecthero.green_lantern.construct_wheel"));
	}

	@Override
	protected void init() {
		entries.clear();
		Minecraft mc = Minecraft.getInstance();
		GreenLanternState state = mc.player == null ? null
				: mc.player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		selectedOrdinal = state != null ? state.selectedConstruct : -1;
		if (state != null) {
			for (ConstructType type : ConstructType.values()) {
				if (type.unlockedFor(state)) {
					entries.add(type);
				}
			}
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (com.projecthero.mod.client.ModKeyBindings.ABILITY_6.matches(keyCode, scanCode)) {
			confirmAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int cx = this.width / 2;
		int cy = this.height / 2;
		int n = entries.size();
		int radius = Math.max(70, Math.min(130, 34 + n * 9));

		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		hovered = -1;
		if (dist > 24 && n > 0) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}

		g.drawCenteredString(this.font, this.title, cx, cy - radius - 28, 0xFF35F075);

		for (int i = 0; i < n; i++) {
			double a = -Math.PI / 2 + i * (Math.PI * 2 / n);
			int x = cx + (int) (Math.cos(a) * radius);
			int y = cy + (int) (Math.sin(a) * radius);
			ConstructType type = entries.get(i);
			boolean isHover = i == hovered;
			boolean isSelected = type.ordinal() == selectedOrdinal;
			int box = 50;
			g.fill(x - box, y - 11, x + box, y + 11, isHover ? 0xE00A3018 : 0xB0081408);
			g.renderOutline(x - box, y - 11, box * 2, 22,
					isSelected ? 0xFF35F075 : (isHover ? 0xFF7EF0A0 : 0x4035F075));
			g.drawCenteredString(this.font, Component.translatable(type.translationKey()), x, y - 4,
					isHover ? 0xFFFFFFFF : (isSelected ? 0xFF35F075 : 0xFFC0F0D0));
		}

		Component centre = hovered >= 0 ? Component.translatable(entries.get(hovered).translationKey())
				: Component.translatable("screen.projecthero.green_lantern.construct_wheel.cancel").withStyle(ChatFormatting.GRAY);
		g.drawCenteredString(this.font, centre, cx, cy - 4, 0xFFDDF8E4);
		g.drawCenteredString(this.font, Component.translatable("screen.projecthero.green_lantern.construct_wheel.hint"),
				cx, cy + radius + 22, 0xFF9AD8B0);
	}

	/** Called when the C key is released: pick the hovered wedge, or cancel. */
	public void confirmAndClose() {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new GreenLanternConstructSelectPayload(entries.get(hovered).ordinal()));
		}
		onClose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new GreenLanternConstructSelectPayload(entries.get(hovered).ordinal()));
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
