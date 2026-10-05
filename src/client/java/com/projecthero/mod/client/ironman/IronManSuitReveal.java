package com.projecthero.mod.client.ironman;

import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21: an Iron Man piece <b>assembles itself</b> on the body instead of popping in. While a piece's synced lock-on
 * clock ({@link IronManSuitFx}) runs, its bones fly in one after another from an exploded view and servo-snap home
 * ({@link IronManAssemblyClient}, per bone in {@code SuperheroArmorRenderer#renderRecursively}) while their surfaces fill
 * in as plate tiles with a hot seam ({@link IronManAssemblyReveal}, the texture swapped in here). Coming off plays the
 * timetable backwards over {@link IronManSuitFx#RELEASE_TICKS} before the piece leaves the slot. The Mark V's case
 * build ({@link IronManSuitFx#STYLE_CASE}) has its bones emerge from the suitcase in the right hand instead.
 *
 * <p>(Until the self-assembly this used the shared {@code ArmorSweepReveal} sweep line, which Thor / Green Lantern /
 * Flash still use.) Read off synced state only, so every viewer sees the same frame.
 */
public final class IronManSuitReveal {
	private IronManSuitReveal() {
	}

	/** Per (player, piece): the clock last seen and the progress drawn, so the drawn value never runs backwards. */
	private record Mono(long start, boolean up, float value) {
	}

	/** Per (player, slot): the item last seen there and the game time it appeared. */
	private record Seen(net.minecraft.world.item.Item item, long since) {
	}

	private static final java.util.Map<Long, Mono> MONO = new java.util.HashMap<>();
	private static final java.util.Map<Long, Seen> SEEN = new java.util.HashMap<>();
	/** A piece that shows up in a slot with no clock yet stays hidden this long, waiting for its lock-on clock to sync. */
	private static final int AWAIT_CLOCK_TICKS = 3;

	/** Client ticks (advanced by {@link IronManAssemblyClient}); keeps running when the server's tick rate is frozen. */
	static long clientTicks;

	/** Once per client tick: note what each armour slot holds, so a piece arriving later is recognised as fresh. */
	public static void observe(Player player) {
		long now = clientTicks;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			long key = ((long) player.getId() << 3) | IronManSuitFx.bit(slot);
			net.minecraft.world.item.Item item = player.getItemBySlot(slot).getItem();
			Seen seen = SEEN.get(key);
			if (seen == null) {
				SEEN.put(key, new Seen(item, Long.MIN_VALUE / 2));
			} else if (seen.item() != item) {
				SEEN.put(key, new Seen(item, now));
			}
		}
	}

	/** Drops all per-player smoothing state (leaving a world). */
	public static void clear() {
		MONO.clear();
		SEEN.clear();
	}

	/**
	 * How much of the piece in {@code slot} is built on (0..1), for every viewer alike. Smooth by construction: read
	 * from {@code gameTime + partialTick}, clamped to be monotonic per clock (a lock-on never runs backwards, a release
	 * never forwards, whatever the client's synced clock does), and a piece whose item reached the slot a tick before
	 * its clock did stays at 0 instead of flashing in whole for a frame.
	 */
	public static float progress(Player player, EquipmentSlot slot, float partialTick) {
		if (slot == null || player.level() == null) {
			return 1f;
		}
		int bit = IronManSuitFx.bit(slot);
		if (bit < 0) {
			return 1f;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		long now = player.level().getGameTime();
		long key = ((long) player.getId() << 3) | bit;
		net.minecraft.world.item.Item item = player.getItemBySlot(slot).getItem();
		Seen seen = SEEN.get(key);
		if (seen == null || seen.item() != item) {
			// first sight of a player (joining, coming into view) counts as long since; a swap mid-view is fresh
			seen = new Seen(item, seen == null ? Long.MIN_VALUE / 2 : clientTicks);
			SEEN.put(key, seen);
		}
		if (fx.pieceAge(slot, now, partialTick) < 0f) {
			MONO.remove(key);
			boolean fresh = item instanceof com.projecthero.mod.ironman.item.IronManArmorItem
					&& clientTicks - seen.since() <= AWAIT_CLOCK_TICKS;
			return fresh ? 0f : 1f;
		}
		boolean up = fx.assembling(bit);
		float raw = fx.pieceProgress(slot, now, partialTick);
		Mono m = MONO.get(key);
		float v = raw;
		if (m != null && m.start() == fx.start(bit) && m.up() == up) {
			v = up ? Math.max(m.value(), raw) : Math.min(m.value(), raw);
		}
		MONO.put(key, new Mono(fx.start(bit), up, v));
		return v;
	}

	/** Is the piece in {@code slot} being put on (true) or taken off (false) right now? (Fresh pieces count as on.) */
	public static boolean assembling(Player player, EquipmentSlot slot) {
		int bit = IronManSuitFx.bit(slot);
		IronManSuitFx fx = IronManSuitFx.of(player);
		if (bit < 0 || player.level() == null || fx.pieceAge(slot, player.level().getGameTime(), 0f) < 0f) {
			return true;
		}
		return fx.assembling(bit);
	}

	/** Is this player's piece being built by the Mark V case (bones emerge from the right hand)? */
	public static boolean fromCase(Player player) {
		return IronManSuitFx.of(player).style() == IronManSuitFx.STYLE_CASE;
	}

	/** The texture to draw an Iron Man piece in {@code slot} with right now (the plain one outside a lock-on / release). */
	public static ResourceLocation texture(Player player, String setId, EquipmentSlot slot, ResourceLocation base,
			float partialTick) {
		float p = progress(player, slot, partialTick);
		if (p < 1f && mk5(player)) {
			// v0.14.29: the Mark 5 suitcase build -- chest before arms, helmet before faceplate (per-bone sub-windows)
			ArmorVisualDefinition def = SuperheroArmorVisuals.get(setId);
			int bit = IronManSuitFx.bit(slot);
			return def == null || bit < 0 ? base : IronManAssemblyReveal.buildTexture(def.geometry(), base, p, bit, true);
		}
		return textureAt(p, building(player, slot), fromCase(player), setId, slot, base);
	}

	/** v0.14.29: is this player's suit going on / coming off as the Mark 5 suitcase build? */
	public static boolean mk5(Player player) {
		return IronManSuitFx.of(player).mk5();
	}

	/** v0.14.28: the build texture of an Iron Man piece at progress {@code p} (whole at 1). */
	public static ResourceLocation textureAt(float p, boolean building, boolean fromCase, String setId, EquipmentSlot slot,
			ResourceLocation base) {
		if (p >= 1f) {
			return base;
		}
		int bit = IronManSuitFx.bit(slot);
		ArmorVisualDefinition def = SuperheroArmorVisuals.get(setId);
		if (bit < 0 || def == null) {
			return base;
		}
		if (building) {
			// v0.14.27: base halves, then the shell one texel at a time
			return IronManAssemblyReveal.buildTexture(def.geometry(), base, p, bit);
		}
		return IronManAssemblyReveal.texture(def.geometry(), base, p, bit, fromCase);
	}

	/**
	 * v0.14.27: is the piece in {@code slot} going on with the 3 s build (base halves + pixel shell) rather than the
	 * Mark V case's fly-out lock-on or a release? (A fresh piece still waiting for its clock counts as building.)
	 */
	public static boolean building(Player player, EquipmentSlot slot) {
		// v0.14.28: the C suit-down un-builds with the same visual run backwards (progress 1 -> 0)
		return !fromCase(player) && (assembling(player, slot) || IronManSuitFx.of(player).unbuilding());
	}

	/**
	 * v0.14.28: a piece drawn on something that is not a player (the send-home suit standing in front of its owner):
	 * the build progress to draw it at, or -1 for whole. Set by the renderer around one draw call, render thread only.
	 */
	public static float standBuild = -1f;
}
