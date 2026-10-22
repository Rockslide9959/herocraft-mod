package com.projecthero.mod.ironman;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

/**
 * v0.16.7 (user) Hulkbuster "Veronica" overhaul, Age of Ultron: "i want the hulkbuster to be able to be worn even if you're
 * already wearing an iron man armour and it shouldnt break anything or delete any other armours please".
 *
 * <p>When the Hulkbuster goes on ({@link #stash} -- the Sentry / crate step-in, the mid-air build) whatever armour the
 * player has on -- any Iron Man suit, Era I or II, or any other armour -- comes off into a saved per-player "inner suit"
 * ({@link ModAttachments#HULKBUSTER_INNER_SUIT}): the four real stacks, whole (every component), an Iron Man suit's live
 * integrity and energy stamped on first. The Hulkbuster's pieces then take the slots; the inner suit is not worn, so none
 * of its abilities, flight or energy drain run, and the Hulkbuster's kit and HUD apply.
 *
 * <p><b>One rule brings it back</b> ({@link #tick}, every player tick): the moment no Hulkbuster piece is worn any more,
 * by ANY path -- the fold (step out into the crate), Sentry Mode (the climb out of the back), the wreck (thrown out of
 * the back), a Suit Platform taking it, a revoke stripping it, a hand pulling the pieces off -- the inner suit goes back
 * on exactly as it was (an Iron Man suit through {@code receivePart}, whole, with its integrity and energy loaded back),
 * so you are back in your own suit as you climb out. A slot that is somehow taken meanwhile gets its stack in the pack
 * (or at the feet) instead -- nothing is ever deleted. Without Tony Stark's power (a revoke) the Iron Man pieces go to
 * the pack, like the armour enforcement would.
 *
 * <p><b>Death</b> ({@link #onDeath}): with keepInventory the stash is kept (the attachment is copied to the respawned
 * player, who still wears the Hulkbuster); without it the stash drops where they died, like worn armour. <b>Logout /
 * server stop / dimension change</b>: the attachment is persistent and the Hulkbuster stays on, so nothing changes; the
 * next tick after it comes off restores it. While a Hulkbuster build is running ({@code HulkbusterPodEntity}) the
 * restore waits.
 */
public final class HulkbusterInnerSuit {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final String[] KEYS = { "head", "chest", "legs", "feet" };

	private HulkbusterInnerSuit() {
	}

	public static void initialize() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				onDeath(p);
			}
		});
	}

	/** Is any Hulkbuster piece in an armour slot? */
	public static boolean wearingHulkbusterPiece(Player p) {
		for (EquipmentSlot s : SLOTS) {
			if (p.getItemBySlot(s).getItem() instanceof IronManArmorItem a && HulkbusterParts.SUIT_ID.equals(a.suitId())) {
				return true;
			}
		}
		return false;
	}

	/** Does {@code p} have an inner suit stashed? */
	public static boolean has(Player p) {
		CompoundTag t = p.getAttached(ModAttachments.HULKBUSTER_INNER_SUIT);
		return t != null && !t.isEmpty();
	}

	/** The stashed stack for armour slot {@code i} (0 head .. 3 feet), EMPTY if none. */
	public static ItemStack stashed(ServerPlayer p, int i) {
		CompoundTag t = p.getAttached(ModAttachments.HULKBUSTER_INNER_SUIT);
		if (t == null || !t.contains(KEYS[i], Tag.TAG_COMPOUND)) {
			return ItemStack.EMPTY;
		}
		return ItemStack.parseOptional(p.registryAccess(), t.getCompound(KEYS[i]));
	}

	/** The stashed Iron Man suit id ("" for none / not an Iron Man suit). */
	public static String stashedSuit(ServerPlayer p) {
		CompoundTag t = p.getAttached(ModAttachments.HULKBUSTER_INNER_SUIT);
		return t == null ? "" : t.getString("suit");
	}

	/**
	 * The Hulkbuster is about to go on {@code p}: take off whatever armour they wear (Hulkbuster pieces excepted) and keep
	 * it. An Iron Man suit's live integrity / energy are stamped on its pieces and its running systems shut down. A stash
	 * already there (should not happen) is merged: what it holds stays, anything worn on top goes to the pack.
	 */
	public static void stash(ServerPlayer p) {
		String worn = IronManArmor.wornSuitId(p);
		IronManSuit suit = worn == null || HulkbusterParts.SUIT_ID.equals(worn) ? null : IronManSuits.byId(worn);
		if (suit != null) {
			if (IronManFlight.isFlying(p)) {
				IronManFlight.setFlying(p, false);
			}
			IronManSuitTicker.onDisabled(p, suit);
		}
		CompoundTag t = p.getAttached(ModAttachments.HULKBUSTER_INNER_SUIT);
		t = t == null ? new CompoundTag() : t.copy();
		boolean any = false;
		for (int i = 0; i < 4; i++) {
			ItemStack st = p.getItemBySlot(SLOTS[i]);
			if (st.isEmpty() || st.getItem() instanceof IronManArmorItem a && HulkbusterParts.SUIT_ID.equals(a.suitId())) {
				continue;
			}
			ItemStack out = st.copy();
			if (out.getItem() instanceof IronManArmorItem a) {
				IronManEnergy.stampStack(out, IronManEnergy.energy(p, a.suitId()), IronManEnergy.integrity(p, a.suitId()));
			}
			p.setItemSlot(SLOTS[i], ItemStack.EMPTY);
			if (t.contains(KEYS[i])) {
				IronManSuitUpManager.giveBack(p, out); // never overwrite a stashed stack
				continue;
			}
			t.put(KEYS[i], out.save(p.registryAccess()));
			any = true;
		}
		if (any && suit != null) {
			t.putString("suit", suit.id());
		}
		if (!t.isEmpty()) {
			p.setAttached(ModAttachments.HULKBUSTER_INNER_SUIT, t);
		}
		if (suit != null) {
			TonyStark.setActiveSuit(p, "");
		}
		p.inventoryMenu.broadcastChanges();
	}

	/** Every player tick: put the inner suit back on once no Hulkbuster piece is worn (and no build is running). */
	public static void tick(ServerPlayer p) {
		if (!has(p) || !p.isAlive() || wearingHulkbusterPiece(p) || com.projecthero.mod.ironman.entity.HulkbusterPodEntity.building(p)) {
			return;
		}
		restore(p);
	}

	/** Put the stashed armour back on now (public for the gametests); false if there was none. */
	public static boolean restore(ServerPlayer p) {
		if (!has(p)) {
			return false;
		}
		ItemStack[] stacks = new ItemStack[4];
		for (int i = 0; i < 4; i++) {
			stacks[i] = stashed(p, i);
		}
		String suitId = stashedSuit(p);
		p.removeAttached(ModAttachments.HULKBUSTER_INNER_SUIT);
		boolean power = TonyStark.hasPower(p);
		for (int i = 0; i < 4; i++) {
			ItemStack st = stacks[i];
			if (st.isEmpty()) {
				continue;
			}
			boolean ironMan = st.getItem() instanceof IronManArmorItem;
			if (!p.getItemBySlot(SLOTS[i]).isEmpty() || ironMan && !power) {
				IronManSuitUpManager.giveBack(p, st);
				continue;
			}
			if (ironMan) {
				if (!IronManSuitUpManager.receivePart(p, st, false)) {
					IronManSuitUpManager.giveBack(p, st);
				}
			} else {
				p.setItemSlot(SLOTS[i], st);
			}
		}
		if (!suitId.isEmpty() && power && IronManArmor.wearingAnyIronMan(p)) {
			ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
			ItemStack any = chest.getItem() instanceof IronManArmorItem ? chest : p.getItemBySlot(EquipmentSlot.HEAD);
			if (any.getItem() instanceof IronManArmorItem a && suitId.equals(a.suitId())) {
				IronManEnergy.loadFromStack(p, suitId, any);
				TonyStark.setActiveSuit(p, suitId);
			}
			IronManSuit s = IronManSuits.byId(suitId);
			if (s != null) {
				p.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_inner.restored",
						Component.translatable(s.nameKey())).withStyle(ChatFormatting.AQUA), true);
			}
		}
		p.inventoryMenu.broadcastChanges();
		return true;
	}

	/** Death: keepInventory keeps the stash (copied to the respawned player); otherwise it drops here like worn armour. */
	public static void onDeath(ServerPlayer p) {
		if (!has(p)) {
			return;
		}
		if (p.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || p.isSpectator()) {
			return;
		}
		for (int i = 0; i < 4; i++) {
			ItemStack st = stashed(p, i);
			if (!st.isEmpty()) {
				ItemEntity drop = new ItemEntity(p.level(), p.getX(), p.getEyeY() - 0.3, p.getZ(), st);
				drop.setPickUpDelay(40);
				p.level().addFreshEntity(drop);
			}
		}
		p.removeAttached(ModAttachments.HULKBUSTER_INNER_SUIT);
	}
}
