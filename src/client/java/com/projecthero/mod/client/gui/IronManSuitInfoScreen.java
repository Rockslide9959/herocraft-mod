package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.ironman.IronManEnergy;
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
 * v0.14.26: press I wearing Iron Man armour -- the suit's spec sheet (v0.17.7: a full power-style page, IronManSuitPage): mark, pieces worn, energy and integrity (now / max,
 * regeneration), flight, weapons and their damage, every ability on R / G / X / Z / V / C, and the helmet systems (scanner
 * range, Mark III targeting, night vision, air supply). Read-only; Esc or I closes it.
 */
public class IronManSuitInfoScreen extends Screen {
	/** v0.17.3: the shared see-through page (like every power's I menu): clipped, a scroll bar that drags and follows the wheel. */
	private final GlassPage page = new GlassPage();
	private final List<FormattedCharSequence> lines = page.lines();

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
		page.layout(this.width, this.height);
		page.hideHud();
		page.scroll().set(0);
		addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
		lines.clear();
		Player p = Minecraft.getInstance().player;
		IronManSuit suit = p == null ? null : wornSuit(p);
		if (suit == null) {
			return;
		}
		TonyStarkState st = p.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		int wrap = page.wrapWidth();
		int pieces = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack s = p.getItemBySlot(slot);
			if (s.getItem() instanceof IronManArmorItem piece && piece.suitId().equals(suit.id())) {
				pieces++;
			}
		}
		float maxInt = IronManEnergy.maxIntegrity(suit.id());
		float integ = st == null ? maxInt : st.suitIntegrity.getOrDefault(suit.id(), maxInt);

		// v0.17.7: the era and a short intro first, like a power's page
		for (Component line : com.projecthero.mod.ironman.ui.IronManSuitPage.intro(suit)) {
			wrap(line, wrap);
		}
		// v0.16.1 comic rework: no suit energy -- integrity and its absorb share are the status
		head("screen.projecthero.ironman_info.status");
		row(wrap, "screen.projecthero.ironman_info.pieces", pieces + " / 4");
		row(wrap, "screen.projecthero.ironman_info.integrity", fmt(integ) + " / " + fmt(maxInt)
				+ (integ <= 0f ? "  " + Component.translatable("hud.projecthero.ironman.disabled_title").getString() : ""));
		if (suit.hasReactor()) {
			// v0.16.7 (user) Era I (and Era II): the reactor -- live energy / capacity, regen, and a brownout flag
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
		row(wrap, "screen.projecthero.ironman_info.platform_repair",
				String.format(java.util.Locale.ROOT, "%.1f / s", IronManEnergy.platformIntegrityPerSecond(suit)));

		// v0.17.7 (user: "make it more fleshed out ... as if its a regular power"): the rest of the page is the shared suit page --
		// stats, every key with its numbers and what it does, passives, how to get / wear it, the HUD and tips (IronManSuitPage)
		String[] keys = new String[6];
		for (int i = 0; i < 6; i++) {
			keys[i] = ModKeyBindings.ABILITY_SLOTS[i].getTranslatedKeyMessage().getString();
		}
		for (Component line : com.projecthero.mod.ironman.ui.IronManSuitPage.details(suit, keys)) {
			wrap(line, wrap);
		}
		lines.add(FormattedCharSequence.EMPTY);
		for (FormattedCharSequence l : font.split(Component.translatable("screen.projecthero.ironman_info.hint").withStyle(ChatFormatting.DARK_GRAY), wrap)) {
			lines.add(l);
		}
		page.done();
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

	/** v0.17.7: word-wraps one line; a line that starts indented keeps its wrapped rows under the same indent (like the power page). */
	private void wrap(Component line, int width) {
		String text = line.getString();
		if (text.isEmpty()) {
			lines.add(FormattedCharSequence.EMPTY);
			return;
		}
		int lead = 0;
		while (lead < text.length() && lead < 8 && text.charAt(lead) == ' ') {
			lead++;
		}
		String indent = " ".repeat(lead);
		int indentW = font.width(indent);
		List<FormattedCharSequence> split = font.split(line, Math.max(40, width - indentW));
		for (int i = 0; i < split.size(); i++) {
			lines.add(i == 0 || lead == 0 ? split.get(i)
					: FormattedCharSequence.composite(FormattedCharSequence.forward(indent, net.minecraft.network.chat.Style.EMPTY), split.get(i)));
		}
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
		g.drawCenteredString(font, this.title, width / 2, 16, 0xFFFFFFFF);
		Component heading = suit == null ? Component.translatable("screen.projecthero.ironman_info.none")
				: com.projecthero.mod.ironman.ui.IronManSuitPage.fullName(suit).withStyle(ChatFormatting.BOLD);
		page.render(g, font, heading, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		return page.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return page.mouseDragged(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = page.mouseReleased(button);
		return super.mouseReleased(mx, my, button) || was;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		return page.mouseScrolled(dy) || super.mouseScrolled(mouseX, mouseY, dx, dy);
	}

	/** Harness / tests: 0..1 of the way down. */
	public void scrollFraction(float f) {
		page.scroll().setFraction(f);
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (ModKeyBindings.POWER_INFO.matches(key, scan)) {
			onClose();
			return true;
		}
		if (page.keyPressed(key)) {
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}

	@Override
	public void removed() {
		page.restoreHud();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
