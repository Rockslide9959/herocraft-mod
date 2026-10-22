package com.projecthero.mod.ironman.crate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.7 (user): "the uplink is reusable and the suit goes back to orbit". Where a Hulkbuster I lives while it is in
 * orbit: a server-wide {@link SavedData} (on the overworld, like {@code StarkSuitReturnQueue}) of <b>orbit slots</b>.
 *
 * <p>Why a server store and not the Uplink item itself (the decision): the suit has to get back to orbit from places the
 * Uplink can't be reached -- a fold while the Uplink sits in a chest, a crate reaching the top of its lift while its
 * owner is offline, a wreck recalled by someone else's key. So the real four stacks (integrity, per-part damage and
 * charge on them, every component) live here, and the {@link IronManItems#HULKBUSTER_UPLINK Uplink} is only a <b>key</b>:
 * the slot id plus a generation number in its {@code custom_data}. Every piece of the suit carries the slot id too, so
 * whatever brings the suit back up (a fold, a recall, a crate cleared away) knows which slot it goes into.
 *
 * <p>Anti-dupe: the stacks are only ever <em>moved</em> (orbit -> crate / pod -> worn / Sentry -> crate -> orbit), never
 * copied; a slot holds them or it doesn't ({@link Slot#inOrbit}). Copying the Uplink (creative) copies a key, not a suit.
 * A lost Uplink is replaced at a Suit Platform ("Re-link"), which bumps the slot's generation: every older key goes dead,
 * so there is only ever one live key.
 */
public final class HulkbusterOrbit extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_hulkbuster_orbit";
	/** custom_data keys: the slot id (on the Uplink and on every piece) and the key's generation (on the Uplink). */
	public static final String LINK_KEY = "projecthero_hb_orbit", GEN_KEY = "projecthero_hb_orbit_gen";
	/** custom_data keys of the Uplink's last-known view (for the tooltip): 1 in orbit, 2 down, 3 unlinked; integrity %. */
	public static final String VIEW_KEY = "projecthero_hb_view", PCT_KEY = "projecthero_hb_pct";
	public static final int VIEW_ORBIT = 1, VIEW_DOWN = 2, VIEW_UNLINKED = 3;

	/** One orbiting (or deployed) Hulkbuster. */
	public static final class Slot {
		public final UUID id;
		public UUID owner;
		public int gen;
		/** True while the four stacks are up here; false while the suit is down (crate, pod, worn, Sentry). */
		public boolean inOrbit;
		/** Helmet, chest, legs, boots (empty while down). */
		public final ItemStack[] stacks = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
		public float integrity;

		Slot(UUID id, UUID owner) {
			this.id = id;
			this.owner = owner;
		}

		public List<ItemStack> pieces() {
			List<ItemStack> out = new ArrayList<>();
			for (ItemStack s : stacks) {
				if (!s.isEmpty()) {
					out.add(s);
				}
			}
			return out;
		}

		/** The suit's charge (the emptiest piece's). */
		public float energy() {
			float e = IronManEnergy.capacity(HulkbusterCrate.SUIT_ID);
			for (ItemStack s : stacks) {
				if (!s.isEmpty()) {
					e = Math.min(e, IronManEnergy.stackEnergy(s, HulkbusterCrate.SUIT_ID));
				}
			}
			return e;
		}

		public int integrityPct() {
			return Math.round(100f * integrity / Math.max(1f, IronManEnergy.maxIntegrity(HulkbusterCrate.SUIT_ID)));
		}
	}

	private final Map<UUID, Slot> slots = new LinkedHashMap<>();

	private static final SavedData.Factory<HulkbusterOrbit> FACTORY = new SavedData.Factory<>(HulkbusterOrbit::new, HulkbusterOrbit::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static HulkbusterOrbit get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	// ------------------------------------------------------------------ slots

	public Slot slot(UUID id) {
		return id == null ? null : slots.get(id);
	}

	public java.util.Collection<Slot> all() {
		return Collections.unmodifiableCollection(slots.values());
	}

	/** A new slot for {@code owner} holding {@code pieces} in orbit (each piece stamped with the slot). */
	public Slot create(UUID owner, List<ItemStack> pieces, float integrity) {
		Slot s = new Slot(UUID.randomUUID(), owner);
		slots.put(s.id, s);
		store(s, pieces, integrity);
		return s;
	}

	/** The four stacks go up into {@code s} (stamped with their shared charge and {@code integrity}). */
	public void store(Slot s, List<ItemStack> pieces, float integrity) {
		float energy = IronManEnergy.capacity(HulkbusterCrate.SUIT_ID);
		for (ItemStack p : pieces) {
			if (p.getItem() instanceof ArmorItem && !p.isEmpty()) {
				energy = Math.min(energy, IronManEnergy.stackEnergy(p, HulkbusterCrate.SUIT_ID));
			}
		}
		for (ItemStack p : pieces) {
			if (p.getItem() instanceof ArmorItem a && !p.isEmpty()) {
				int i = SuitcaseContents.slotOf(a.getType());
				if (i >= 0) {
					IronManEnergy.stampStack(p, energy, integrity);
					link(p, s.id);
					s.stacks[i] = p;
				}
			}
		}
		s.integrity = integrity;
		s.inOrbit = true;
		setDirty();
	}

	/** The suit leaves orbit: its stacks (taken out of the slot), the slot staying as "down". */
	public List<ItemStack> takeDown(Slot s) {
		List<ItemStack> out = s.pieces();
		for (int i = 0; i < 4; i++) {
			s.stacks[i] = ItemStack.EMPTY;
		}
		s.inOrbit = false;
		setDirty();
		return out;
	}

	/** The slot is closed (the suit was racked on a platform): its pieces stop carrying it. */
	public void remove(Slot s) {
		slots.remove(s.id);
		setDirty();
	}

	/** A new key generation (Re-link): every older Uplink for {@code s} goes dead. */
	public int bump(Slot s) {
		s.gen++;
		setDirty();
		return s.gen;
	}

	// ------------------------------------------------------------------ the custom_data stamps

	/** Stamp {@code piece} with the slot it belongs to. */
	public static void link(ItemStack piece, UUID id) {
		CustomData.update(DataComponents.CUSTOM_DATA, piece, tag -> tag.putUUID(LINK_KEY, id));
	}

	/** Drop a piece's (or a key's) slot stamp. */
	public static void unlink(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && (data.contains(LINK_KEY) || data.contains(GEN_KEY) || data.contains(VIEW_KEY))) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
				tag.remove(LINK_KEY);
				tag.remove(GEN_KEY);
				tag.remove(VIEW_KEY);
				tag.remove(PCT_KEY);
			});
		}
	}

	/** The slot a piece / key carries, or null. */
	public static UUID linkOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(LINK_KEY)) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return tag.hasUUID(LINK_KEY) ? tag.getUUID(LINK_KEY) : null;
	}

	/** The slot linked by any of {@code pieces}, or null. */
	public static UUID linkOf(List<ItemStack> pieces) {
		for (ItemStack p : pieces) {
			UUID id = linkOf(p);
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	public static int genOf(ItemStack key) {
		CustomData data = key.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getInt(GEN_KEY);
	}

	/** Make {@code key} (an Uplink) the live key of {@code s}. */
	public static void bindKey(ItemStack key, Slot s) {
		key.remove(DataComponents.CONTAINER); // a key, not a case (an older Uplink held the stacks itself)
		CustomData.update(DataComponents.CUSTOM_DATA, key, tag -> {
			tag.putUUID(LINK_KEY, s.id);
			tag.putInt(GEN_KEY, s.gen);
		});
		view(key, s);
	}

	/** Is {@code key} an Uplink bound to some slot (live or not)? */
	public static boolean isKey(ItemStack key) {
		return key.is(IronManItems.HULKBUSTER_UPLINK) && linkOf(key) != null;
	}

	/** The slot {@code key} is the LIVE key of (right id and generation), or null. */
	public Slot slotOfKey(ItemStack key) {
		if (!key.is(IronManItems.HULKBUSTER_UPLINK)) {
			return null;
		}
		Slot s = slot(linkOf(key));
		return s != null && s.gen == genOf(key) ? s : null;
	}

	/** Refresh the key's tooltip view (only written when it changed, so nothing re-syncs every tick). */
	public static void view(ItemStack key, Slot s) {
		int v = s == null ? VIEW_UNLINKED : s.inOrbit ? VIEW_ORBIT : VIEW_DOWN;
		int pct = s == null || !s.inOrbit ? -1 : s.integrityPct();
		CustomData data = key.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		if (tag.getInt(VIEW_KEY) == v && (!tag.contains(PCT_KEY) ? -1 : tag.getInt(PCT_KEY)) == pct) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, key, t -> {
			t.putInt(VIEW_KEY, v);
			if (pct >= 0) {
				t.putInt(PCT_KEY, pct);
			} else {
				t.remove(PCT_KEY);
			}
		});
	}

	/** Does {@code player} carry a live key of {@code s}? */
	public boolean carriesKey(ServerPlayer player, Slot s) {
		for (ItemStack st : player.getInventory().items) {
			if (slotOfKey(st) == s) {
				return true;
			}
		}
		for (ItemStack st : player.getInventory().offhand) {
			if (slotOfKey(st) == s) {
				return true;
			}
		}
		return false;
	}

	/** {@code owner}'s first slot whose live key they don't carry (Re-link offers it), or null. */
	public Slot keyless(ServerPlayer owner) {
		for (Slot s : slots.values()) {
			if (owner.getUUID().equals(s.owner) && !carriesKey(owner, s)) {
				return s;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ save / load

	private static HulkbusterOrbit load(CompoundTag tag, HolderLookup.Provider registries) {
		HulkbusterOrbit o = new HulkbusterOrbit();
		ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			if (!t.hasUUID("Id")) {
				continue;
			}
			Slot s = new Slot(t.getUUID("Id"), t.hasUUID("Owner") ? t.getUUID("Owner") : null);
			s.gen = t.getInt("Gen");
			s.inOrbit = t.getBoolean("InOrbit");
			s.integrity = t.getFloat("Integrity");
			ListTag pieces = t.getList("Pieces", Tag.TAG_COMPOUND);
			for (int k = 0; k < 4 && k < pieces.size(); k++) {
				s.stacks[k] = ItemStack.parseOptional(registries, pieces.getCompound(k));
			}
			o.slots.put(s.id, s);
		}
		return o;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Slot s : slots.values()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", s.id);
			if (s.owner != null) {
				t.putUUID("Owner", s.owner);
			}
			t.putInt("Gen", s.gen);
			t.putBoolean("InOrbit", s.inOrbit);
			t.putFloat("Integrity", s.integrity);
			ListTag pieces = new ListTag();
			for (ItemStack st : s.stacks) {
				pieces.add(st.isEmpty() ? new CompoundTag() : st.save(registries));
			}
			t.put("Pieces", pieces);
			list.add(t);
		}
		tag.put("Slots", list);
		return tag;
	}
}
