package com.projecthero.mod.ironman.ability.era1;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.SuitCrateItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) ERA I: the <b>emergency eject</b>. Hold Shift+C for {@link #HOLD_TICKS} (3 s; a bar fills on the HUD --
 * {@code IronManHud}) in a worn Era I suit and it comes off where you stand into a {@link SuitCrateItem Suit Crate}: the
 * real armour stacks, stamped with the suit's integrity and energy. The Crate is heavy (Slowness I while carried) and only
 * opens in a Stark Gantry (used on its floor: the arms put the suit back on; used on a Suit Platform in a Gantry's range:
 * it racks the suit). It works on a wrecked (0 integrity), browned-out or locked-out suit -- that is what it is for.
 *
 * <p>Server-authoritative: the Shift+C press stamps the start, the release (or letting go of Shift) cancels, and
 * {@link #tick} ejects once the hold has lasted. The press map is static per-world state -- cleared in
 * {@code ServerStateReset} and on disconnect.
 */
public final class EraOneEject {
	/** v0.16.7 (user): hold Shift+C this long. */
	public static final int HOLD_TICKS = 60;

	private static final Map<UUID, Long> HOLD_START = new ConcurrentHashMap<>();

	private EraOneEject() {
	}

	public static void clearSessionState() {
		HOLD_START.clear();
	}

	public static void forget(UUID player) {
		HOLD_START.remove(player);
	}

	/** Is this player holding Shift+C on an Era I suit right now (server)? */
	public static boolean holding(ServerPlayer player) {
		return HOLD_START.containsKey(player.getUUID());
	}

	/** 0..1 of the hold done (server; for the gametests and JARVIS-style readouts). */
	public static float progress(ServerPlayer player) {
		Long start = HOLD_START.get(player.getUUID());
		return start == null ? 0f : Math.min(1f, (player.level().getGameTime() - start) / (float) HOLD_TICKS);
	}

	/** A Shift+C edge on a worn Era I suit ({@link IronManEraOne#ejectKey}). */
	public static void key(ServerPlayer player, IronManSuit suit, boolean pressed) {
		if (!pressed) {
			HOLD_START.remove(player.getUUID());
			return;
		}
		if (suit == null || !suit.eraOne() || IronManSuitUpManager.inTransition(player) || HOLD_START.containsKey(player.getUUID())) {
			return;
		}
		HOLD_START.put(player.getUUID(), player.level().getGameTime());
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.eject_hold")
				.withStyle(ChatFormatting.GOLD), true);
	}

	/** Per player tick (from {@code IronManSuitTicker}): finish (or drop) a running hold. */
	public static void tick(ServerPlayer player) {
		Long start = HOLD_START.get(player.getUUID());
		if (start == null) {
			return;
		}
		String worn = IronManArmor.wornSuitId(player);
		if (!player.isAlive() || !player.isShiftKeyDown() || !IronManEraOne.isEraOne(worn) || IronManSuitUpManager.inTransition(player)) {
			HOLD_START.remove(player.getUUID());
			return;
		}
		if (player.level().getGameTime() - start >= HOLD_TICKS) {
			HOLD_START.remove(player.getUUID());
			eject(player);
		}
	}

	/**
	 * Take the worn Era I suit off into a new Suit Crate, now. Public for the gametests. Returns the crate (also put in the
	 * player's pack, else dropped at their feet), or EMPTY if no Era I suit was worn.
	 */
	public static ItemStack eject(ServerPlayer player) {
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null || !suit.eraOne()) {
			return ItemStack.EMPTY;
		}
		float energy = IronManEnergy.energy(player, suitId);
		float integrity = IronManEnergy.integrity(player, suitId);
		IronManSuitTicker.shutDownAllSystems(player, TonyStark.state(player), suit); // flight, holds, running moves
		NonNullList<ItemStack> slots = NonNullList.withSize(4, ItemStack.EMPTY);
		EquipmentSlot[] equip = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		for (int i = 0; i < 4; i++) {
			ItemStack worn = player.getItemBySlot(equip[i]);
			if (worn.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId)) {
				ItemStack copy = worn.copy();
				IronManEnergy.stampStack(copy, energy, integrity);
				player.setItemSlot(equip[i], ItemStack.EMPTY);
				slots.set(i, copy);
			}
		}
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		if (!IronManArmor.wearingAnyIronMan(player)) {
			TonyStark.setActiveSuit(player, "");
		}
		ItemStack crate = SuitCrateItem.of(slots);
		IronManSuitUpManager.giveBack(player, crate.copy());
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.4, 0.6, 0.4, 0.05);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.35, 0.5, 0.35, 0.1);
		IronManSounds.play(player, IronManSounds.RELEASE, 1.0f, 0.8f);
		IronManSounds.play(player, IronManSounds.CLAMP, 0.8f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.ejected")
				.withStyle(ChatFormatting.AQUA), true);
		return crate;
	}
}
