package com.projecthero.mod.city;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.crime.CrimeEvent;
import com.projecthero.mod.crime.CrimeVillages;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * v0.16.2 City part B: what the Daily Bulletin (and the Precinct Desk) reports -- every running crime, world event
 * (raids, bosses, invasions from the event framework) and vanilla raid within {@code bulletin.reportRange} blocks, nearest
 * first, each with a "~140 m NE" direction from where it was read.
 */
public final class DailyBulletin {
	private static final int REPORTS_PER_PAGE = 3;

	/** One news item. */
	public record Report(Component headline, Component hint, double distance, boolean crime) {
		public Component line() {
			return Component.empty().append(headline.copy().withStyle(crime ? ChatFormatting.RED : ChatFormatting.DARK_PURPLE))
					.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(hint.copy().withStyle(ChatFormatting.GRAY));
		}
	}

	private DailyBulletin() {
	}

	public static List<Report> reports(ServerLevel level, BlockPos from, int range) {
		List<Report> out = new ArrayList<>();
		double r2 = (double) range * range;
		for (EventInstance e : EventManager.active(level.getServer())) {
			if (e.dimension() != level.dimension() || !e.state().active() || e.center() == null) {
				continue;
			}
			BlockPos c = e.center();
			double d2 = horizontal2(from, c);
			if (d2 > r2) {
				continue;
			}
			Component hint = CrimeVillages.hint(from.getX() + 0.5, from.getZ() + 0.5, c.getX() + 0.5, c.getZ() + 0.5);
			if (e instanceof CrimeEvent crime) {
				out.add(new Report(Component.translatable("item.projecthero.daily_bulletin.crime", crime.displayName(), crime.villageName()), hint,
						Math.sqrt(d2), true));
			} else {
				out.add(new Report(Component.translatable("item.projecthero.daily_bulletin.event", e.displayName()), hint, Math.sqrt(d2), false));
			}
		}
		Raid raid = level.getRaids().getNearbyRaid(from, range * range);
		if (raid != null && raid.isActive()) {
			BlockPos c = raid.getCenter();
			out.add(new Report(Component.translatable("item.projecthero.daily_bulletin.raid"),
					CrimeVillages.hint(from.getX() + 0.5, from.getZ() + 0.5, c.getX() + 0.5, c.getZ() + 0.5), Math.sqrt(horizontal2(from, c)), false));
		}
		out.sort(Comparator.comparingDouble(Report::distance));
		return out;
	}

	private static double horizontal2(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** The newspaper's pages, as read at {@code from} now. */
	public static List<Component> pages(ServerLevel level, BlockPos from) {
		List<Report> reports = reports(level, from, CityConfig.bulletin().reportRange);
		long day = level.getDayTime() / 24000L + 1;
		int crimes = (int) reports.stream().filter(Report::crime).count();
		List<Component> pages = new ArrayList<>();
		MutableComponent front = Component.empty()
				.append(Component.translatable("item.projecthero.daily_bulletin.masthead").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED))
				.append("\n")
				.append(Component.translatable("item.projecthero.daily_bulletin.edition", day).withStyle(ChatFormatting.GRAY))
				.append("\n\n");
		if (reports.isEmpty()) {
			front.append(Component.translatable("item.projecthero.daily_bulletin.quiet"));
			pages.add(front);
			return pages;
		}
		front.append(Component.translatable("item.projecthero.daily_bulletin.summary", reports.size(), crimes).withStyle(ChatFormatting.BLACK));
		pages.add(front);
		MutableComponent page = null;
		int onPage = 0;
		for (Report r : reports) {
			if (page == null || onPage >= REPORTS_PER_PAGE) {
				if (page != null) {
					pages.add(page);
				}
				page = Component.empty();
				onPage = 0;
			} else {
				page.append("\n\n");
			}
			page.append(r.headline().copy().withStyle(ChatFormatting.BOLD, r.crime() ? ChatFormatting.DARK_RED : ChatFormatting.DARK_PURPLE))
					.append("\n").append(r.hint().copy().withStyle(ChatFormatting.DARK_GRAY));
			onPage++;
		}
		if (page != null) {
			pages.add(page);
		}
		return pages;
	}

	/** The written-book content the reader's screen shows. */
	public static WrittenBookContent content(ServerLevel level, BlockPos from) {
		List<Filterable<Component>> pages = new ArrayList<>();
		for (Component c : pages(level, from)) {
			pages.add(Filterable.passThrough(c));
		}
		return new WrittenBookContent(Filterable.passThrough("The Daily Bulletin"), "City Desk", 0, pages, true);
	}

	/** True if any page mentions {@code needle} in its plain text (tests). */
	public static boolean mentions(List<Component> pages, String needle) {
		for (Component c : pages) {
			if (c.getString().contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
