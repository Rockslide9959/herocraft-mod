package com.projecthero.mod.hideout;

import java.util.function.IntSupplier;

import com.projecthero.mod.event.EventInstance;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5: the raid calm. Clearing a hideout (mini-boss dead + core broken) stops that faction's raids and world
 * events from starting within {@link HideoutState#CALM_RADIUS} blocks of it for {@link HideoutState#CALM_TICKS}.
 *
 * <p>Hooks: {@code EventManager.start} asks {@link #refuses(ServerLevel, EventInstance, BlockPos)} for every event
 * (Syndicate Bust and street crimes = Kingpin, Ultron Uprising = Ultron, Apokolips Invasion = Apokolips, Frost Giant
 * Assault = Frost), and the natural spawners that are not events ask {@link #calmed(ServerLevel, HideoutFaction, BlockPos)}
 * directly (Carnage's meteor = Symbiote, the Ultron / Frost Giant natural triggers). Operator commands
 * ({@code /projecthero raid start ...}) run inside {@link #bypass} and are never refused.
 */
public final class HideoutCalm {
	/** Set while an operator command runs: nothing is refused. A plain flag per thread, no world references. */
	private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

	private HideoutCalm() {
	}

	/** Runs {@code action} with the calm switched off (operator raid commands). */
	public static int bypass(IntSupplierThrows action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		boolean was = BYPASS.get();
		BYPASS.set(true);
		try {
			return action.getAsInt();
		} finally {
			BYPASS.set(was);
		}
	}

	/** {@link IntSupplier} that may throw a command syntax exception. */
	@FunctionalInterface
	public interface IntSupplierThrows {
		int getAsInt() throws com.mojang.brigadier.exceptions.CommandSyntaxException;
	}

	/** The faction an event belongs to, or null for events no hideout calms. */
	public static HideoutFaction factionOf(EventInstance instance) {
		if (instance instanceof com.projecthero.mod.crime.CrimeEvent) {
			return HideoutFaction.KINGPIN;
		}
		return factionOf(instance.typeId());
	}

	public static HideoutFaction factionOf(String typeId) {
		if (typeId == null) {
			return null;
		}
		return switch (typeId) {
			case com.projecthero.mod.syndicate.SyndicateBust.TYPE_ID -> HideoutFaction.KINGPIN;
			case com.projecthero.mod.ultron.UltronUprising.TYPE_ID -> HideoutFaction.ULTRON;
			case com.projecthero.mod.darkseid.raid.DarkseidRaid.TYPE_ID -> HideoutFaction.APOKOLIPS;
			case com.projecthero.mod.frostgiant.FrostGiantAssault.TYPE_ID -> HideoutFaction.FROST;
			case com.projecthero.mod.event.EventTypes.ZOMBIE_RAID -> HideoutFaction.GRAVE; // v0.17.7
			default -> null;
		};
	}

	/** True when a cleared {@code faction} hideout's calm covers {@code pos} right now (ignores the operator bypass). */
	public static boolean calmed(ServerLevel level, HideoutFaction faction, BlockPos pos) {
		return calming(level, faction, pos) != null;
	}

	/** The hideout entry calming {@code pos} for {@code faction}, or null. */
	public static HideoutState.Entry calming(ServerLevel level, HideoutFaction faction, BlockPos pos) {
		if (faction == null || pos == null) {
			return null;
		}
		return HideoutState.get(level).calming(faction, pos, HideoutState.CALM_RADIUS, level.getGameTime());
	}

	/**
	 * The {@code EventManager.start} hook: true (and the players nearby are told why) when this event's faction is
	 * calmed at {@code center}. Never refuses inside {@link #bypass}.
	 */
	public static boolean refuses(ServerLevel level, EventInstance instance, BlockPos center) {
		if (BYPASS.get()) {
			return false;
		}
		HideoutFaction faction = factionOf(instance);
		HideoutState.Entry calm = calming(level, faction, center);
		if (calm == null) {
			return false;
		}
		if (instance instanceof com.projecthero.mod.crime.CrimeEvent) {
			return true; // street crimes are rolled quietly in the background: no message
		}
		// tell the players nearby why (at most once every 10 s each: a Syndicate stash retries every second)
		Component msg = message(faction, calm.calmLeft(level.getGameTime()));
		HideoutState state = HideoutState.get(level);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) <= 48 * 48
					&& state.mayNotify(p.getUUID(), level.getGameTime(), 200)) {
				p.displayClientMessage(msg, false);
			}
		}
		return true;
	}

	/** "The Syndicate is lying low around here since you cleared Kingpin's Front (3.4 days left)." */
	public static Component message(HideoutFaction faction, long ticksLeft) {
		String days = String.format(java.util.Locale.ROOT, "%.1f", ticksLeft / 24000.0);
		return Component.translatable("projecthero.hideout.calm.refused", faction.factionName(), faction.hideoutName(), days)
				.withStyle(s -> s.withColor(faction.colour()));
	}

	/** A short note for chat when a hideout is cleared. */
	public static Component clearedNote(HideoutFaction faction) {
		return Component.translatable("projecthero.hideout.calm.started", faction.factionName()).withStyle(ChatFormatting.GRAY);
	}
}
