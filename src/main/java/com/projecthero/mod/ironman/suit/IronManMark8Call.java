package com.projecthero.mod.ironman.suit;

import java.util.List;

import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManMark8;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15 (user requests): how the Mark 8 is called and sent home -- hooks {@link IronManSuitCall} calls into, kept
 * here so that file only carries one-line calls.
 * <ul>
 *   <li><b>Call cost</b>: every call takes {@link #CALL_COST} of the suit's capacity off its stored energy, and is
 *       refused with a message when it has less than that.</li>
 *   <li><b>Call</b>: the whole suit flies in as one {@link IronManSentryEntity} (flight pose, thrusters) and lands
 *       {@value IronManSentryEntity#ARRIVE_FRONT} blocks in front of its owner, facing them, closed, in Regular mode --
 *       instead of piece by piece. A partial set still comes in by courier. Protocol Phoenix's emergency recall is left
 *       alone (it must go on, now).</li>
 *   <li><b>Send home</b> (Sneak + C picker on the worn full suit): it opens, its owner steps out backwards, it closes and
 *       flies itself to the platform.</li>
 * </ul>
 */
public final class IronManMark8Call {
	/** A call takes this share of the suit's capacity off its stored energy. */
	public static final float CALL_COST = 0.10f;

	private IronManMark8Call() {
	}

	/** Is this a Mark 8 call these rules cover? (Not Protocol Phoenix's emergency recall.) */
	public static boolean applies(ServerPlayer player, IronManSuit suit) {
		return suit != null && IronManMark8.SUIT_ID.equals(suit.id()) && !TonyStark.phoenixEmergency(player);
	}

	/** The energy the suit to be called has stored: on the platform {@code be}, else on a carried chestplate. */
	public static float storedEnergy(ServerPlayer player, IronManSuit suit, IronManSuitPlatformBlockEntity be) {
		if (be != null && suit.id().equals(be.storedSuitId())) {
			return be.suitEnergy();
		}
		ItemStack ref = null;
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suit.id())) {
				if (ref == null || a.getType() == ArmorItem.Type.CHESTPLATE) {
					ref = s;
				}
			}
		}
		return ref == null ? suit.energyCapacity() : IronManEnergy.stackEnergy(ref, suit.id());
	}

	/** The energy a call costs. */
	public static float cost(IronManSuit suit) {
		return suit.energyCapacity() * CALL_COST;
	}

	/** True (with a message) if a suit holding {@code stored} energy is too low to be called. */
	public static boolean refuseLowEnergy(ServerPlayer player, IronManSuit suit, float stored) {
		if (stored + 1.0e-3f >= cost(suit)) {
			return false;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mark8.call_low_energy",
				Component.translatable(suit.nameKey()), Math.round(CALL_COST * 100f)).withStyle(ChatFormatting.RED), true);
		return true;
	}

	/**
	 * The whole set ({@code taken}, all four real stacks) flies in as one sentry from {@code from} (null = in from the
	 * sky), carrying {@code energy} less the call cost and {@code integrity}.
	 */
	public static IronManSentryEntity flyIn(ServerPlayer player, IronManSuit suit, List<ItemStack> taken, Vec3 from,
			float energy, float integrity) {
		float left = Math.max(0f, energy - cost(suit));
		IronManSentryEntity s = IronManSentryEntity.flyIn(player, suit.id(), taken, from, left, integrity);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mark8.inbound",
				Component.translatable(suit.nameKey()), Math.round(CALL_COST * 100f)).withStyle(ChatFormatting.AQUA), true);
		return s;
	}

	/**
	 * Send-home on the worn full Mark 8: step out of it (it stands as a sentry) and it flies to {@code dock}. False if it
	 * can't deploy right now (flying, mid suit-up) -- the caller then sends it home the ordinary way.
	 */
	public static boolean sendHome(ServerPlayer player, IronManSuit suit, BlockPos dock) {
		if (suit == null || !IronManMark8.SUIT_ID.equals(suit.id())
				|| com.projecthero.mod.ironman.IronManFlight.isFlying(player) || player.isPassenger()) {
			return false;
		}
		IronManSentryEntity s = IronManSentryEntity.sendHome(player, dock);
		if (s == null) {
			return false;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mark8.sent_home",
				Component.translatable(suit.nameKey()), dock.getX(), dock.getY(), dock.getZ()).withStyle(ChatFormatting.AQUA), true);
		return true;
	}
}
