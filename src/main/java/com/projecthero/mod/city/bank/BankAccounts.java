package com.projecthero.mod.city.bank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.city.CityConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.2 City part B: the City Bank's per-player emerald vaults (stored with the Overworld). Every Safe Deposit block
 * opens the same account for the same player -- it is a bank, not a chest -- and only emeralds and emerald blocks go in.
 *
 * <p><b>Interest:</b> {@code interestPercentPerDay} (1%) of the vault's emerald value per in-game day, credited when the
 * owner next opens a vault (fractions carry over), and capped at {@code interestCapEmeralds} (64) emeralds earned per
 * account, ever.
 *
 * <p><b>Heist hook:</b> {@link #addListener} sees every vault opening, and {@link #takeForHeist} removes emeralds from an
 * account -- what a future bank-heist event needs; nothing calls it yet.
 */
public final class BankAccounts extends SavedData {
	public static final int SLOTS = 27;
	private static final String FILE_ID = "projecthero_bank";
	private static final SavedData.Factory<BankAccounts> FACTORY = new SavedData.Factory<>(BankAccounts::new, BankAccounts::load, null);

	/** Told whenever a player opens a Safe Deposit (the future heist event's hook). */
	@FunctionalInterface
	public interface VaultListener {
		void onOpen(ServerPlayer player, BlockPos vault, Account account);
	}

	private static final List<VaultListener> LISTENERS = new CopyOnWriteArrayList<>();

	public static void addListener(VaultListener listener) {
		LISTENERS.add(listener);
	}

	static void fireOpen(ServerPlayer player, BlockPos vault, Account account) {
		for (VaultListener l : LISTENERS) {
			l.onOpen(player, vault, account);
		}
	}

	public final class Account {
		public final SimpleContainer items = new SimpleContainer(SLOTS) {
			@Override
			public boolean canPlaceItem(int slot, ItemStack stack) {
				return accepts(stack);
			}
		};
		/** The last in-game day interest was worked out for (-1 = never opened). */
		public long lastDay = -1;
		/** Interest earned but not yet a whole emerald (or that didn't fit). */
		public double accrued;
		/** Emeralds of interest paid so far (capped). */
		public int interestPaid;

		Account() {
			items.addListener(c -> setDirty());
		}

		public int value() {
			return BankAccounts.value(items);
		}
	}

	private final Map<UUID, Account> accounts = new HashMap<>();

	public static BankAccounts get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public Account account(UUID player) {
		return accounts.computeIfAbsent(player, k -> new Account());
	}

	/** Only emeralds and emerald blocks go in a vault. */
	public static boolean accepts(ItemStack stack) {
		return stack.is(Items.EMERALD) || stack.is(Items.EMERALD_BLOCK);
	}

	/** Emerald value of a container (a block is 9). */
	public static int value(Container c) {
		int v = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.is(Items.EMERALD)) {
				v += s.getCount();
			} else if (s.is(Items.EMERALD_BLOCK)) {
				v += s.getCount() * 9;
			}
		}
		return v;
	}

	/** Today's number (in-game days since the world began). */
	public static long day(MinecraftServer server) {
		return server.overworld().getDayTime() / 24000L;
	}

	/**
	 * Works out the interest owed to {@code account} up to {@code today} and pays the whole emeralds into the vault.
	 * Returns how many emeralds were credited.
	 */
	public int applyInterest(Account account, long today) {
		CityConfig.Bank cfg = CityConfig.bank();
		if (account.lastDay < 0 || today < account.lastDay) {
			account.lastDay = today;
			setDirty();
			return 0;
		}
		long days = Math.min(today - account.lastDay, 3650);
		account.lastDay = today;
		if (days <= 0 || !cfg.interestEnabled) {
			return 0;
		}
		int credited = 0;
		for (long d = 0; d < days && account.interestPaid < cfg.interestCapEmeralds; d++) {
			account.accrued += (account.value() + credited) * cfg.interestPercentPerDay / 100.0;
			int whole = (int) Math.floor(account.accrued);
			int room = cfg.interestCapEmeralds - account.interestPaid - credited;
			int pay = Math.max(0, Math.min(whole, room));
			if (pay > 0) {
				account.accrued -= pay;
				credited += pay;
			}
			if (account.interestPaid + credited >= cfg.interestCapEmeralds) {
				account.accrued = 0;
			}
		}
		if (credited > 0) {
			ItemStack rest = account.items.addItem(new ItemStack(Items.EMERALD, credited));
			int paid = credited - rest.getCount();
			account.accrued += rest.getCount(); // no room: it waits in the books
			account.interestPaid += paid;
			credited = paid;
		}
		setDirty();
		return credited;
	}

	/**
	 * Heist hook: takes up to {@code maxValue} emeralds' worth out of {@code player}'s vault (emeralds first, then blocks).
	 * Returns what was taken.
	 */
	public List<ItemStack> takeForHeist(UUID player, int maxValue) {
		List<ItemStack> out = new ArrayList<>();
		Account acc = accounts.get(player);
		if (acc == null) {
			return out;
		}
		int left = maxValue;
		for (int pass = 0; pass < 2 && left > 0; pass++) {
			for (int i = 0; i < SLOTS && left > 0; i++) {
				ItemStack s = acc.items.getItem(i);
				int each = pass == 0 ? 1 : 9;
				if (s.isEmpty() || !(pass == 0 ? s.is(Items.EMERALD) : s.is(Items.EMERALD_BLOCK)) || each > left) {
					continue;
				}
				int n = Math.min(s.getCount(), left / each);
				out.add(acc.items.removeItem(i, n));
				left -= n * each;
			}
		}
		setDirty();
		return out;
	}

	public Map<UUID, Account> accounts() {
		return accounts;
	}

	private static BankAccounts load(CompoundTag tag, HolderLookup.Provider registries) {
		BankAccounts b = new BankAccounts();
		ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			Account a = b.account(t.getUUID("owner"));
			a.lastDay = t.getLong("lastDay");
			a.accrued = t.getDouble("accrued");
			a.interestPaid = t.getInt("interestPaid");
			NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(t, items, registries);
			for (int s = 0; s < SLOTS; s++) {
				a.items.setItem(s, items.get(s));
			}
		}
		return b;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Account> e : accounts.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("owner", e.getKey());
			t.putLong("lastDay", e.getValue().lastDay);
			t.putDouble("accrued", e.getValue().accrued);
			t.putInt("interestPaid", e.getValue().interestPaid);
			ContainerHelper.saveAllItems(t, e.getValue().items.getItems(), registries);
			list.add(t);
		}
		tag.put("accounts", list);
		return tag;
	}
}
