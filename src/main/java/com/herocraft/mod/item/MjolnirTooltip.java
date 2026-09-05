package com.herocraft.mod.item;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The contents of Mjolnir's tooltip, and the one client-side thing it needs to know: whether shift
 * is currently held.
 *
 * <p>{@code appendHoverText} lives on the item, which is common code, but "is shift down" is a
 * client-only question ({@code Screen.hasShiftDown}) and this source set cannot see client classes.
 * Rather than reaching across with a loader check, the client installs a supplier into
 * {@link #expandKeyHeld} on startup; on a dedicated server it stays at its default and the tooltip
 * simply never expands, which is correct -- nothing renders tooltips there anyway.
 */
public final class MjolnirTooltip {
	/**
	 * Every ability line: its lang key and, where it has one, the keybind it reads. Kept in the same
	 * order the player is likely to discover them -- the two mouse actions, then flight, then the
	 * keyed powers.
	 *
	 * <p>Keybind lines are built with {@link Component#keybind}, which resolves to whatever the key
	 * is <em>currently</em> bound to on the viewing client -- so rebinding an ability in
	 * Options &gt; Controls updates the tooltip too, instead of it advertising a stale default.
	 */
	private static final String[][] ABILITY_LINES = {
			{ "item.herocraft.mjolnir.ability.throw", null },
			{ "item.herocraft.mjolnir.ability.bind", null },
			{ "item.herocraft.mjolnir.ability.flight", null },
			{ "item.herocraft.mjolnir.ability.call_hammer", "key.herocraft.ability_1" },
			{ "item.herocraft.mjolnir.ability.lightning_strike", "key.herocraft.ability_2" },
			{ "item.herocraft.mjolnir.ability.lightning_laser", "key.herocraft.ability_3" },
			{ "item.herocraft.mjolnir.ability.god_of_thunder", "key.herocraft.ability_4" },
			{ "item.herocraft.mjolnir.ability.thunderclap", "key.herocraft.ability_5" },
			{ "item.herocraft.mjolnir.ability.chain_lightning", "key.herocraft.ability_6" },
	};

	/** Replaced by the client initialiser with the real "is shift down" check. */
	public static BooleanSupplier expandKeyHeld = () -> false;

	private MjolnirTooltip() {
	}

	public static boolean expanded() {
		return expandKeyHeld.getAsBoolean();
	}

	/**
	 * The collapsed state's one line. The key name is pulled from the vanilla sneak binding rather
	 * than hardcoding the word "Shift", so it stays correct for anyone who has rebound it.
	 */
	public static Component expandHint() {
		return Component.translatable("item.herocraft.mjolnir.expand_hint",
						Component.keybind("key.sneak").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
	}

	public static void appendAbilities(List<Component> tooltip) {
		for (String[] line : ABILITY_LINES) {
			MutableComponent text = line[1] == null
					? Component.translatable(line[0])
					: Component.translatable(line[0], Component.keybind(line[1]).withStyle(ChatFormatting.WHITE));
			tooltip.add(text.withStyle(ChatFormatting.GRAY));
		}
	}
}
