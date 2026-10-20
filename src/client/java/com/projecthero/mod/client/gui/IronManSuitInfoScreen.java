package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.26: press I wearing Iron Man armour -- the suit's spec sheet: mark, pieces worn, energy and integrity (now / max,
 * regeneration), flight, weapons and their damage, every ability on R / G / X / Z / V / C, and the helmet systems (scanner
 * range, Mark III targeting, night vision, air supply). Read-only; Esc or I closes it.
 */
public class IronManSuitInfoScreen extends Screen {
	private static final int W = 300;
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private int scroll;

	public IronManSuitInfoScreen() {
		super(Component.translatable("screen.projecthero.ironman_info"));
	}

	/** The suit worn (helmet first, then any piece), or null. */
	public static IronManSuit wornSuit(Player player) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).getItem() instanceof IronManArmorItem piece) {
				IronManSuit s = IronManSuits.byId(piece.suitId());
				if (s != null) {
					return s;
				}
			}
		}
		return null;
	}

	@Override
	protected void init() {
		lines.clear();
		Player p = Minecraft.getInstance().player;
		IronManSuit suit = p == null ? null : wornSuit(p);
		if (suit == null) {
			return;
		}
		TonyStarkState st = p.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		int wrap = W - 16;
		int pieces = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.getItem() instanceof IronManArmorItem piece && piece.suitId().equals(suit.id())) {
				pieces++;
			}
		}
		float maxInt = IronManEnergy.maxIntegrity(suit.id());
		float integ = st == null ? maxInt : st.suitIntegrity.getOrDefault(suit.id(), maxInt);

		// v0.16.1 comic rework: no suit energy -- integrity and its absorb share are the status
		head("screen.projecthero.ironman_info.status");
		row(wrap, "screen.projecthero.ironman_info.pieces", pieces + " / 4");
		row(wrap, "screen.projecthero.ironman_info.integrity", fmt(integ) + " / " + fmt(maxInt)
				+ (integ <= 0f ? "  " + Component.translatable("hud.projecthero.ironman.disabled_title").getString() : ""));
		if (suit.eraOne()) {
			// v0.16.7 (user) Era I: the reactor -- live energy / capacity, regen, and a brownout flag
			float cap = suit.energyCapacity();
			float en = st == null ? cap : Math.max(0f, Math.min(cap, st.suitEnergy.getOrDefault(suit.id(), cap)));
			boolean out = st != null && st.brownout.contains(suit.id());
			row(wrap, Component.translatable("screen.projecthero.ironman_comic.energy"), fmt(en) + " / " + fmt(cap) + "  +"
					+ fmt(suit.energyRegenPerSecond()) + "/s"
					+ (out ? "  " + Component.translatable("hud.projecthero.ironman.era1.brownout").getString() : ""));
		}
		// v0.15.12: Vibranium Plating -- how many worn pieces are plated, and what it adds
		int plated = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (com.projecthero.mod.ultron.item.VibraniumPlating.isPlated(p.getItemBySlot(slot))) {
				plated++;
			}
		}
		row(wrap, "screen.projecthero.ironman_info.vibranium", Component.translatable("screen.projecthero.ironman_info.vibranium_value",
				plated, fmt((float) (plated * com.projecthero.mod.ultron.item.VibraniumPlating.ARMOR_BONUS)),
				fmt((float) (plated * com.projecthero.mod.ultron.item.VibraniumPlating.TOUGHNESS_BONUS))).getString());
		// v0.16.1: Vibranium Plating II (replaces plating I) and the Firewall Chip / JARVIS Firewall protocol
		int plated2 = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (com.projecthero.mod.ultron.contagion.item.VibraniumPlatingII.isPlated(p.getItemBySlot(slot))) {
				plated2++;
			}
		}
		if (plated2 > 0) {
			row(wrap, "screen.projecthero.ironman_info.vibranium_2", Component.translatable("screen.projecthero.ironman_info.vibranium_value",
					plated2, fmt((float) (plated2 * com.projecthero.mod.ultron.contagion.item.VibraniumPlatingII.ARMOR_BONUS)),
					fmt((float) (plated2 * com.projecthero.mod.ultron.contagion.item.VibraniumPlatingII.TOUGHNESS_BONUS))).getString());
		}
		boolean chip = com.projecthero.mod.ultron.contagion.item.FirewallChip.hasChip(p.getItemBySlot(EquipmentSlot.HEAD));
		boolean protocol = p.getAttachedOrElse(com.projecthero.mod.ultron.contagion.SuitHijack.FIREWALL_PROTOCOL, false);
		row(wrap, "screen.projecthero.ironman_info.firewall", Component.translatable(protocol ? "screen.projecthero.ironman_info.firewall_protocol"
				: chip ? "screen.projecthero.ironman_info.firewall_chip" : "screen.projecthero.ironman_info.firewall_none").getString());
		if (suit.resistanceAmplifier() >= 0) {
			row(wrap, "screen.projecthero.ironman_info.resistance", String.valueOf(suit.resistanceAmplifier() + 1));
		}
		row(wrap, "screen.projecthero.ironman_info.platform_repair",
				String.format(java.util.Locale.ROOT, "%.1f / s", IronManEnergy.platformIntegrityPerSecond(suit)));

		head("screen.projecthero.ironman_comic.stats");
		for (com.projecthero.mod.ironman.ui.IronManComicText.Row r : com.projecthero.mod.ironman.ui.IronManComicText.stats(suit)) {
			row(wrap, r.label(), r.value());
		}
		if (suit.altitudeCeiling() > 0 && suit.altitudeCeiling() < 10000) {
			row(wrap, "screen.projecthero.ironman_info.ceiling", String.format(java.util.Locale.ROOT, "%.0f", suit.altitudeCeiling()));
		}

		// v0.16.1: the comic kit -- every key, tap / Shift, with damage and cooldown
		head("screen.projecthero.ironman_comic.moves");
		String[] keys = new String[6];
		for (int i = 0; i < 6; i++) {
			keys[i] = ModKeyBindings.ABILITY_SLOTS[i].getTranslatedKeyMessage().getString();
		}
		for (Component line : com.projecthero.mod.ironman.ui.IronManComicText.moves(suit, keys)) {
			lines.addAll(font.split(line.copy().withStyle(ChatFormatting.WHITE), wrap));
		}
		if (!suit.eraOne()) { // v0.16.7: an Era I kit lists its C key above (tap move / Shift eject)
			String c = keys[5];
			row(wrap, Component.literal("[" + c + "]"), Component.translatable("hud.projecthero.ironman.ability." + suit.abilityInSlot(6)).getString());
		}

		head("screen.projecthero.ironman_info.systems");
		row(wrap, "screen.projecthero.ironman_info.scanner", "mark_1".equals(suit.id()) ? "-"
				: ("mark_2".equals(suit.id()) ? "25" : "100") + " m");
		row(wrap, "screen.projecthero.ironman_info.targeting", Component.translatable(IronManTargeting.hasTargeting(suit)
				? "screen.projecthero.ironman_info.yes" : "screen.projecthero.ironman_info.no").getString());
		row(wrap, "screen.projecthero.ironman_info.night_vision", Component.translatable(suit.helmetNightVision()
				? "screen.projecthero.ironman_info.night_vision_auto" : "screen.projecthero.ironman_info.no").getString());
		if (suit.waterBreathing()) {
			row(wrap, "screen.projecthero.ironman_info.air", Component.translatable("screen.projecthero.ironman_info.air_unlimited").getString());
		} else if (suit.airTankSeconds() > 0) {
			row(wrap, "screen.projecthero.ironman_info.air", suit.airTankSeconds() + " s");
		} else {
			row(wrap, "screen.projecthero.ironman_info.air", Component.translatable("screen.projecthero.ironman_info.no").getString());
		}
		row(wrap, "screen.projecthero.ironman_info.auto_feed", Component.translatable(suit.autoFeed() // v0.14.27
				? "screen.projecthero.ironman_info.yes" : "screen.projecthero.ironman_info.no").getString());
		lines.add(FormattedCharSequence.EMPTY);
		for (FormattedCharSequence l : font.split(Component.translatable("screen.projecthero.ironman_info.hint").withStyle(ChatFormatting.DARK_GRAY), wrap)) {
			lines.add(l);
		}
	}

	private static String fmt(float v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	private void head(String key) {
		if (!lines.isEmpty()) {
			lines.add(FormattedCharSequence.EMPTY);
		}
		lines.add(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).getVisualOrderText());
	}

	private void row(int wrap, String key, String value) {
		row(wrap, Component.translatable(key), value);
	}

	private void row(int wrap, Component label, String value) {
		Component c = Component.empty().append(label.copy().withStyle(ChatFormatting.GRAY)).append(Component.literal("  " + value).withStyle(ChatFormatting.WHITE));
		lines.addAll(font.split(c, wrap));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		Player p = Minecraft.getInstance().player;
		IronManSuit suit = p == null ? null : wornSuit(p);
		int x = (width - W) / 2;
		int top = 24;
		int h = height - 48;
		g.fill(x, top, x + W, top + h, 0xE0081018);
		g.fill(x, top, x + W, top + 2, 0xFFB22222);
		g.fill(x, top + h - 2, x + W, top + h, 0xFFE8B83A);
		Component title = suit == null ? Component.translatable("screen.projecthero.ironman_info.none")
				: Component.translatable(suit.nameKey()).withStyle(ChatFormatting.BOLD);
		g.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFC24A);
		int y = top + 24 - scroll;
		g.enableScissor(x, top + 20, x + W, top + h - 4);
		for (FormattedCharSequence l : lines) {
			g.drawString(font, l, x + 8, y, 0xFFFFFFFF, false);
			y += 10;
		}
		g.disableScissor();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		int max = Math.max(0, lines.size() * 10 - (height - 48 - 30));
		scroll = Math.max(0, Math.min(max, scroll - (int) (dy * 12)));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (ModKeyBindings.POWER_INFO.matches(key, scan)) {
			onClose();
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
