package com.projecthero.mod.ironman.ui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ironman.ability.IronManComicKit;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;

/**
 * v0.16.1 comic rework: the shared player-facing text for a suit's comic stats and its tap / Shift moves -- one source
 * for the I-key spec sheet, the Fabricator "View more" panel, the Call Armour compare panel and the Stark Gear screen.
 * Pure data (no rendering); every caller word-wraps what it gets (TooltipWrap rule).
 */
public final class IronManComicText {
	private IronManComicText() {
	}

	/** A label / value row. */
	public record Row(Component label, String value) {
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	/** "helmet/chest/legs/boots, toughness t". */
	public static String armour(IronManSuit suit) {
		int[] d = new int[4];
		float tough = 0f;
		ArmorItem.Type[] types = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
		for (int i = 0; i < 4; i++) {
			IronManArmorItem a = suit.armor(types[i]);
			if (a != null) {
				d[i] = a.getDefense();
				tough = a.getToughness();
			}
		}
		return d[0] + "/" + d[1] + "/" + d[2] + "/" + d[3] + "  t" + fmt(tough);
	}

	/** The comic stat rows: armour, integrity, absorb, flight, melee, repair, specials. */
	public static List<Row> stats(IronManSuit suit) {
		List<Row> out = new ArrayList<>();
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.name"), Component.translatable(suit.displayKey()).getString()));
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.armour"), armour(suit)));
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.integrity"), fmt(suit.maxIntegrity())));
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.absorb"), Math.round(suit.absorbShare() * 100f) + "%"));
		String flight;
		if (!suit.manualFlight()) {
			flight = Component.translatable("screen.projecthero.ironman_comic.flight_hops").getString();
		} else if (suit.sprintFlight() && suit.flightSprintBps() > suit.flightNormalBps()) {
			flight = Component.translatable("screen.projecthero.ironman_comic.flight_value", fmt(suit.flightNormalBps()),
					fmt(suit.flightSprintBps())).getString();
		} else {
			flight = Component.translatable("screen.projecthero.ironman_comic.flight_hover", fmt(suit.flightNormalBps())).getString();
		}
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.flight"), flight));
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.melee"), "+" + fmt(suit.strengthBonus())));
		String repair = suit.selfRepairPerSecond() > 0f
				? Component.translatable("screen.projecthero.ironman_comic.repair_self", fmt(suit.selfRepairPerSecond())).getString()
				: Component.translatable(suit.hulkbuster() ? "screen.projecthero.ironman_comic.repair_pod"
						: "screen.projecthero.ironman_comic.repair_platform").getString();
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.repair"), repair));
		out.add(new Row(Component.translatable("screen.projecthero.ironman_comic.special"),
				Component.translatable("screen.projecthero.ironman_comic.special." + suit.id()).getString()));
		return out;
	}

	/** One line per key: "[R] Iron Punch 15 (1s) / Shift: Ground Pound 12 (8s)". {@code keys} = the bound key labels. */
	public static List<Component> moves(IronManSuit suit, String[] keys) {
		List<Component> out = new ArrayList<>();
		IronManComicKit.Kit kit = IronManComicKit.kit(suit.id());
		if (kit == null) {
			return out;
		}
		for (int slot = 1; slot <= 5; slot++) {
			IronManComicKit.Key k = kit.key(slot);
			String key = keys != null && keys.length >= slot ? keys[slot - 1] : String.valueOf(slot);
			out.add(Component.translatable("screen.projecthero.ironman_comic.move_line", key, move(k.tap()), move(k.shift())));
		}
		return out;
	}

	/** "Iron Punch 15 (1s)". */
	public static Component move(IronManComicKit.Move m) {
		String dmg = m.damage() > 0f ? " " + fmt(m.damage()) : "";
		String cd = m.cooldownTicks() > 0 ? " (" + fmt(m.cooldownTicks() / 20.0) + "s)" : "";
		return Component.translatable(m.nameKey()).append(dmg + cd);
	}
}
