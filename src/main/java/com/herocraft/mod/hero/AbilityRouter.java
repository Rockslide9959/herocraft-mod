package com.herocraft.mod.hero;

import com.herocraft.mod.power.ThorPassives;
import com.herocraft.mod.power.ThorPowers;
import com.herocraft.mod.worthiness.Worthiness;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative dispatch for the six universal ability slots. Resolves the player's current
 * control context and hands the input to the right place. The client only ever <em>requests</em> an
 * activation (see {@link com.herocraft.mod.network.AbilityInputPayload}); everything is validated
 * here and downstream.
 *
 * <h2>Context priority (spec 0.2 / 0.3)</h2>
 * <ol>
 *   <li><b>Thor</b> -- if the player currently has Thor's control context (worthy, and either holding
 *       Mjolnir or the bound owner). The experimental system never steals the slots from Thor while
 *       Thor should have them.</li>
 *   <li><b>Active experimental power</b> -- otherwise, whichever mutation the player has selected.</li>
 *   <li><b>Nothing</b> -- input is ignored.</li>
 * </ol>
 */
public final class AbilityRouter {
	private AbilityRouter() {
	}

	public static boolean hasThorContext(ServerPlayer player) {
		return Worthiness.isWorthy(player)
				&& (ThorPowers.isHoldingMjolnir(player) || ThorPassives.hasPowerOfThor(player));
	}

	public static void handleInput(ServerPlayer player, int slotNumber, boolean pressed) {
		if (slotNumber < 1 || slotNumber > 6) {
			return;
		}
		AbilitySlot slot = AbilitySlot.byNumber(slotNumber);

		// "changes 17": while Protocol Phoenix has the player incapacitated, no ability of any system
		// can be activated -- the emergency suit is inbound and the pilot is a passenger.
		if (com.herocraft.mod.ironman.ProtocolPhoenix.incapacitated(player)) {
			return;
		}

		// Call Armour (Special-Mode key, unarmoured) always wins for a Tony Stark player who is NOT
		// holding Mjolnir -- "I'm not in my suit and I pressed the suit key" is unambiguous, and it must
		// work regardless of whether the player also has Thor's context or an experimental power, and
		// regardless of whether they have "built" a suit yet or carry any piece (it may be sitting on a
		// platform in an unloaded chunk). Holding Mjolnir means they're in Thor mode, so Thor keeps it.
		if (pressed && slot == AbilitySlot.SLOT_6
				&& com.herocraft.mod.ironman.TonyStark.hasPower(player)
				&& !com.herocraft.mod.ironman.IronManArmor.wearingAnyIronMan(player)
				&& !ThorPowers.isHoldingMjolnir(player)) {
			// "changes 19": plain C auto-equips a full suit sitting in your inventory; sneak + C (or no
			// complete suit in the pack) opens the call-armour picker.
			if (!player.isShiftKeyDown()
					&& com.herocraft.mod.ironman.suit.IronManSuitCall.autoEquipInventorySuit(player)) {
				return;
			}
			com.herocraft.mod.ironman.suit.IronManSuitCall.openMenu(player);
			return;
		}

		if (hasThorContext(player)) {
			ThorAbilityAdapter.handle(player, slot, pressed);
			return;
		}

		// Iron Man takes the slots ahead of any experimental power whenever the player has the Tony
		// Stark power AND is wearing an Iron Man suit -- so each Mark's ability set is (Tony Stark +
		// active suit). Server-authoritative: every downstream ability re-checks power + suit + energy.
		if (com.herocraft.mod.ironman.ability.IronManAbilityManager.hasContext(player)) {
			com.herocraft.mod.ironman.ability.IronManAbilityManager.handle(player, slot, pressed);
			return;
		}

		// Spider-Man takes the slots ahead of an experimental power, but only while the player has not
		// deliberately selected one of their mutations from the power wheel -- picking "none" there is
		// how a Spider-Man who also owns mutations comes back to the web kit. His passives (wall
		// crawling, Spider Sense, the double jump) never depend on this and stay on either way.
		if (com.herocraft.mod.spider.SpiderManAbilityManager.hasContext(player)) {
			com.herocraft.mod.spider.SpiderManAbilityManager.handle(player, slot, pressed);
			return;
		}

		// Max Steel takes the slots ahead of an experimental power on the same terms as Spider-Man:
		// the player has the Hero-Tier power and has not deliberately selected a mutation from the
		// wheel. Server-authoritative -- every ability re-checks power, transformed state and energy.
		if (com.herocraft.mod.maxsteel.MaxSteelAbilityManager.hasContext(player)) {
			com.herocraft.mod.maxsteel.MaxSteelAbilityManager.handle(player, slot, pressed);
			return;
		}

		// The Punisher takes the slots ahead of an experimental power on the same terms: has the
		// Hero-Tier power and has not selected a mutation from the wheel. Server-authoritative.
		if (com.herocraft.mod.punisher.PunisherAbilityManager.hasContext(player)) {
			com.herocraft.mod.punisher.PunisherAbilityManager.handle(player, slot, pressed);
			return;
		}

		// A Normal Symbiote host (bonded, suit active, NOT also Spider-Man -- that combination is Black
		// Suit Spider-Man and stays on SpiderManAbilityManager above) gets its own six tendril/mobility/
		// defence abilities. Moot in practice that this sits after every Hero-Tier check: bonding purges
		// every other Hero-Tier power, so a Normal host never has one to contend with here anyway.
		if (com.herocraft.mod.symbiote.SymbioteAbilityManager.hasContext(player)) {
			com.herocraft.mod.symbiote.SymbioteAbilityManager.handle(player, slot, pressed);
			return;
		}

		Power active = ExperimentalPowers.getActive(player);
		if (active == null || !ExperimentalPowers.owns(player, active)) {
			return;
		}

		Ability ability = active.ability(slot);
		AbilityHandler handler = AbilityHandlers.get(active, ability);
		AbilityContext ctx = new AbilityContext(player, active, ability, pressed);

		if (handler == null) {
			if (pressed) {
				player.displayClientMessage(Component.translatable("message.herocraft.ability.not_implemented",
						Component.translatable(ability.nameKey())), true);
			}
			return;
		}

		switch (ability.activation()) {
			case INSTANT -> {
				if (pressed) {
					if (!ctx.cooldownReady()) {
						player.displayClientMessage(Component.translatable("message.herocraft.ability.on_cooldown",
								Component.translatable(ability.nameKey()),
								String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f)), true);
						return;
					}
					handler.onActivate(ctx);
				}
			}
			case HOLD, CHARGE -> {
				if (pressed) {
					handler.onActivate(ctx);
				} else {
					handler.onRelease(ctx);
				}
			}
			case TOGGLE -> {
				if (pressed) {
					boolean now = !ctx.isToggled();
					ctx.setToggled(now);
					if (now) {
						handler.onToggleOn(ctx);
					} else {
						handler.onToggleOff(ctx);
					}
				}
			}
			case CYCLE -> {
				if (pressed) {
					handler.onCycle(ctx);
				}
			}
		}
	}

	/** Called once per player per server tick (from {@code HeroCraftMod}). */
	public static void serverTick(ServerPlayer player) {
		ExperimentalPowers.serverTick(player);
		// Spider-Man's own upkeep runs regardless of which power holds the slots, because his
		// passives do too.
		com.herocraft.mod.spider.SpiderManAbilityManager.serverTick(player);
		// Spider Adhesion (the un-evolved power) still needs the adhesion engine's server half, and it
		// is not a Hero Class so nothing above covers it.
		if (!com.herocraft.mod.spider.SpiderMan.hasPower(player)) {
			com.herocraft.mod.spider.SpiderClimb.serverTick(player);
		}
		// Max Steel's energy / transform / mode upkeep runs regardless of which power holds the slots.
		com.herocraft.mod.maxsteel.MaxSteelAbilityManager.serverTick(player);
		com.herocraft.mod.punisher.PunisherAbilityManager.serverTick(player);
		// v0.6.20: the Spider-Man costume mask (H key) is tied to the costume, not the power, so its
		// "mask can't stay off once the hood comes off" reconcile has to run for every player.
		com.herocraft.mod.spider.SpiderMask.reconcile(player);
		// v0.9.4: only the Punisher may wear the Punisher tactical armour -- eject it from anyone else.
		com.herocraft.mod.punisher.PunisherArmorGate.enforce(player);
		// v0.9.14: the Symbiote's own upkeep (transform clock, re-equip/deleteLoose backstop) now runs
		// for EVERY player unconditionally -- it used to piggyback on SpiderManAbilityManager's tick and
		// silently never ran for a non-Spider-Man (Normal) host.
		com.herocraft.mod.symbiote.Symbiote.tick(player);
		// The Symbiote suit is power equipment -- strip it from anyone who is not an active bonded
		// Symbiote host of either variant (traded away, chest-stored, ground pickup) so no duplicate
		// suit can exist.
		com.herocraft.mod.symbiote.Symbiote.enforce(player);
		// v0.9.14: a Normal Symbiote host's own tendril/leap/slam/shield/Frenzy upkeep (cooldowns,
		// Frenzy/Shield timers, wall assistance) -- independent of whether it currently holds the
		// ability slots.
		com.herocraft.mod.symbiote.SymbioteAbilityManager.serverTick(player);
		com.herocraft.mod.symbiote.SymbiotePassives.tick(player);
		// Same discipline for Max Steel's suit.
		com.herocraft.mod.maxsteel.MaxSteel.enforce(player);
	}
}
