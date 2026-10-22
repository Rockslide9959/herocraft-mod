package com.projecthero.mod.ironman.suit;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.RepulsorBoots;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.item.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user): "make mark 7s thruster pack give slow basic flight to the player while in the inventory". While a
 * loaded Mk VIII Space <b>Thruster Pack</b> ({@code space_thruster_pack}, suit {@code mark_vii}) is carried -- it is
 * already worn on the back then ({@code PortableDeploy.backTick}) -- and no Iron Man armour is on, the pack's own thrusters
 * give a slow, basic flight:
 * <ul>
 *   <li>double-tap jump to take off / drop out, like the suits; gentle speed (just under vanilla creative flight), no
 *       sprint flight ({@code DirectionalFlightModel.thrusterPack});</li>
 *   <li>small thruster puffs from the pack's nozzles (client {@code IronManFlightFxClient});</li>
 *   <li>it drains the packed suit's own stored charge ({@code SUIT_ENERGY} on the pack) at {@value #DRAIN_PER_SECOND}/s and
 *       drops out at 0;</li>
 *   <li>no fall damage while it is on (it is creative-style flight); it ends when the pack leaves the inventory or the
 *       player suits up.</li>
 * </ul>
 * Like every Iron Man suit / pack it answers only to Tony Stark ({@code TonyStark.hasPower} -- the pack's own deploy rule).
 * It rides the Repulsor Boots' gear-flight flag ({@code REPULSOR_BOOTS_FLYING}: the same gesture, landing, no-sprint and
 * flight-conflict rules); a flier with that flag and no Repulsor on the feet is pack-flying ({@link #packFlying}).
 */
public final class ThrusterPackFlight {
	public static final String SUIT = "mark_vii";
	public static final float DRAIN_PER_SECOND = 1f;

	private ThrusterPackFlight() {
	}

	/** The carried loaded Thruster Pack that powers the flight (main inventory, then off hand), or null. */
	public static ItemStack pack(Player player) {
		PortableForm form = PortableForms.MK8_THRUSTER_PACK;
		var inv = player.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack st = inv.items.get(i);
			if (st.is(form.packedItem().get()) && PortableDeploy.deployable(st)) {
				return st;
			}
		}
		ItemStack off = player.getOffhandItem();
		return off.is(form.packedItem().get()) && PortableDeploy.deployable(off) ? off : null;
	}

	/** The pack's stored charge (0 without a pack). */
	public static float charge(Player player) {
		ItemStack st = pack(player);
		return st == null ? 0f : IronManEnergy.stackEnergy(st, SUIT);
	}

	/** Can {@code player} take off on the pack right now? */
	public static boolean available(ServerPlayer player) {
		return TonyStark.hasPower(player) && !IronManArmor.wearingAnyIronMan(player) && !PortableDeploy.running(player)
				&& charge(player) > 0f;
	}

	/** Is {@code player} flying on the pack (either side: the gear-flight flag with no Repulsor on the feet)? */
	public static boolean packFlying(Player player) {
		return player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false) && !RepulsorBoots.worn(player);
	}

	/**
	 * One tick of pack flight ({@code RepulsorBoots.tick}): the drain, and the end conditions. False when the flight has to
	 * stop (no pack, suited up, no power, or the pack ran dry this tick).
	 */
	public static boolean tick(ServerPlayer player) {
		if (!TonyStark.hasPower(player) || IronManArmor.wearingAnyIronMan(player) || PortableDeploy.running(player)) {
			return false;
		}
		ItemStack st = pack(player);
		if (st == null) {
			return false;
		}
		float e = IronManEnergy.stackEnergy(st, SUIT);
		if (player.level().getGameTime() % 20L == 0L) {
			// once a second (one stack write a second, not one a tick -- every write resyncs the slot)
			e -= DRAIN_PER_SECOND;
			st.set(ModDataComponents.SUIT_ENERGY, Math.max(0f, e));
		}
		if (e <= 0f) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.thruster_pack.empty")
					.withStyle(ChatFormatting.RED), true);
			return false;
		}
		return true;
	}
}
