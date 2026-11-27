package com.projecthero.mod.worthy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hammer.ThorWeapon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.17.7 Thor x Super Soldier: the server-wide list of lent Thor weapons -- the one authority on who lent what to whom. Per-world
 * {@link SavedData} in the overworld's data storage (not static state, so nothing for {@code ServerStateReset}); it survives a relog
 * and a server restart whoever is online. Keyed by the weapon's {@code HAMMER_ID}, so one weapon is lent at most once. The players
 * see it through their {@code WorthyState} view lists, which {@link WorthyLending#refreshView} rebuilds from here.
 */
public final class WorthyLoans extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_worthy_loans";

	/** One lent weapon: the weapon's id and kind, its owner (the Thor who lent it) and the Super Soldier holding it. */
	public record Loan(UUID hammerId, ThorWeapon weapon, UUID owner, String ownerName, UUID borrower, String borrowerName, long since) {
		CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putUUID("Hammer", hammerId);
			t.putString("Weapon", weapon.key());
			t.putUUID("Owner", owner);
			t.putString("OwnerName", ownerName);
			t.putUUID("Borrower", borrower);
			t.putString("BorrowerName", borrowerName);
			t.putLong("Since", since);
			return t;
		}

		static Loan load(CompoundTag t) {
			return new Loan(t.getUUID("Hammer"), ThorWeapon.byKey(t.getString("Weapon")), t.getUUID("Owner"), t.getString("OwnerName"),
					t.getUUID("Borrower"), t.getString("BorrowerName"), t.getLong("Since"));
		}
	}

	private final Map<UUID, Loan> loans = new LinkedHashMap<>();

	private static final SavedData.Factory<WorthyLoans> FACTORY = new SavedData.Factory<>(WorthyLoans::new, WorthyLoans::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public WorthyLoans() {
	}

	public static WorthyLoans get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static WorthyLoans load(CompoundTag tag, HolderLookup.Provider registries) {
		WorthyLoans data = new WorthyLoans();
		ListTag list = tag.getList("Loans", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			try {
				Loan loan = Loan.load(list.getCompound(i));
				data.loans.put(loan.hammerId(), loan);
			} catch (RuntimeException ignored) {
				// a hand-edited / corrupted entry is dropped; the weapon itself stays wherever it is, still bound to its owner
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Loan loan : loans.values()) {
			list.add(loan.save());
		}
		tag.put("Loans", list);
		return tag;
	}

	// ------------------------------------------------------------------ queries

	@Nullable
	public Loan byHammer(@Nullable UUID hammerId) {
		return hammerId == null ? null : loans.get(hammerId);
	}

	/** The loan of {@code weapon} that {@code owner} has out, or null. */
	@Nullable
	public Loan lentBy(UUID owner, ThorWeapon weapon) {
		for (Loan loan : loans.values()) {
			if (loan.owner().equals(owner) && loan.weapon() == weapon) {
				return loan;
			}
		}
		return null;
	}

	/** Every loan {@code player} is part of (as the owner or the borrower). A copy: safe to end loans while walking it. */
	public List<Loan> involving(UUID player) {
		List<Loan> out = new ArrayList<>();
		for (Loan loan : loans.values()) {
			if (loan.owner().equals(player) || loan.borrower().equals(player)) {
				out.add(loan);
			}
		}
		return out;
	}

	public List<Loan> ownedBy(UUID owner) {
		List<Loan> out = new ArrayList<>();
		for (Loan loan : loans.values()) {
			if (loan.owner().equals(owner)) {
				out.add(loan);
			}
		}
		return out;
	}

	public List<Loan> borrowedBy(UUID borrower) {
		List<Loan> out = new ArrayList<>();
		for (Loan loan : loans.values()) {
			if (loan.borrower().equals(borrower)) {
				out.add(loan);
			}
		}
		return out;
	}

	public List<Loan> all() {
		return new ArrayList<>(loans.values());
	}

	// ------------------------------------------------------------------ writes

	public void put(Loan loan) {
		loans.put(loan.hammerId(), loan);
		setDirty();
	}

	public boolean remove(UUID hammerId) {
		boolean had = loans.remove(hammerId) != null;
		if (had) {
			setDirty();
		}
		return had;
	}
}
