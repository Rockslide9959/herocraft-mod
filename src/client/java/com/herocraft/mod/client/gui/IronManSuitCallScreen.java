package com.herocraft.mod.client.gui;

import java.util.List;

import com.herocraft.mod.network.IronManCallSuitPayload;
import com.herocraft.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "call armour" picker (spec "changes 9"): opened when the player presses the call key unarmoured.
 * Lists only suits the server says are actually assemblable right now -- one on a bound Suit Platform
 * (even in an unloaded chunk) or one fully in the inventory -- each with its live charge / integrity
 * and distance. Picking one sends the choice back and the suit flies in (or equips immediately, for an
 * inventory suit).
 */
public final class IronManSuitCallScreen extends Screen {
	private final List<IronManSuitListPayload.Option> options;

	public IronManSuitCallScreen(List<IronManSuitListPayload.Option> options) {
		super(Component.translatable("screen.herocraft.suit_call.title"));
		this.options = options;
	}

	@Override
	protected void init() {
		int w = 340;
		int x = this.width / 2 - w / 2;
		int y = 44;

		for (IronManSuitListPayload.Option o : options) {
			Component label = buildLabel(o);
			String suitId = o.suitId();
			int source = o.source();
			addRenderableWidget(Button.builder(label, b -> pick(suitId, source)).bounds(x, y, w, 22).build());
			y += 26;
		}

		if (options.isEmpty()) {
			y += 8;
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(x, y + 10, w, 20).build());
	}

	private Component buildLabel(IronManSuitListPayload.Option o) {
		java.util.Locale L = java.util.Locale.ROOT;
		String suitName = Component.translatable("herocraft.ironman.suit." + o.suitId() + ".name").getString();
		String where = o.source() == IronManSuitListPayload.SOURCE_INVENTORY
				? Component.translatable("screen.herocraft.suit_call.in_inventory").getString()
				: Component.translatable("screen.herocraft.suit_call.on_platform", o.distance()).getString();
		// The server sends exact fractions; show them at full 2-decimal precision so this readout can
		// never disagree with the Suit Platform screen or the in-suit HUD (v0.6.6).
		float ePct = Math.max(0f, Math.min(100f, o.energyFrac() * 100f));
		float iPct = Math.max(0f, Math.min(100f, o.integrityFrac() * 100f));
		com.herocraft.mod.ironman.suit.IronManSuit suit =
				com.herocraft.mod.ironman.suit.IronManSuits.byId(o.suitId());
		int cap = suit == null ? 0 : Math.round(suit.energyCapacity());
		int cur = Math.round(o.energyFrac() * cap);
		ChatFormatting eColor = ePct < 15f ? ChatFormatting.RED : ePct < 50f ? ChatFormatting.GOLD : ChatFormatting.AQUA;
		return Component.literal(suitName + "   ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(String.format(L, "%.2f%% chg", ePct)).withStyle(eColor))
				.append(Component.literal(cap > 0 ? String.format(L, " (%,d/%,d)  ", cur, cap) : "  ")
						.withStyle(ChatFormatting.DARK_AQUA))
				.append(Component.literal(String.format(L, "%.2f%% int  ", iPct)).withStyle(
						iPct < 30f ? ChatFormatting.RED : ChatFormatting.GREEN))
				.append(Component.literal("- " + where).withStyle(ChatFormatting.GRAY));
	}

	private void pick(String suitId, int source) {
		ClientPlayNetworking.send(new IronManCallSuitPayload(suitId, source));
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(this.font, this.title, this.width / 2, 22, 0xFF7FE9FF);
		if (options.isEmpty()) {
			g.drawCenteredString(this.font, Component.translatable("screen.herocraft.suit_call.none")
					.withStyle(ChatFormatting.GRAY), this.width / 2, 60, 0xFFB0B0B0);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
