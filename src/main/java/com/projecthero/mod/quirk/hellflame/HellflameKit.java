package com.projecthero.mod.quirk.hellflame;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Hellflame's (Endeavor's) six keys.
 * <pre>
 *   R  Hell Spider            Shift+R  Hell Spider Net
 *   G  Jet Burn               Shift+G  Hellflame Beam (hold)
 *   X  Flame Boost (hold)     Shift+X  Burst Dodge
 *   Z  Flashfire Fist         Shift+Z  Flashfire Uppercut
 *   V  Prominence Burn (hold) Shift+V  Hellflame Sun
 *   C  Flame Cloak (toggle)   Shift+C  Vent (works while overheated)
 * </pre>
 * The moves live in {@link HellflameMoves}; every number is in {@link HellflameConfig}. A held move ends on the key's
 * release whatever Shift is doing by then.
 */
public final class HellflameKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			switch (slot) {
				case SLOT_2 -> HellflameMoves.endBeam(player);
				case SLOT_3 -> HellflameMoves.endBoost(player);
				case SLOT_5 -> HellflameMoves.releaseProminence(player);
				default -> {
				}
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// the Cloak can always be put out, cooldown or not
		if (id.equals(HellflameConfig.CLOAK) && HellflameMoves.cloaked(player)) {
			HellflameMoves.toggleCloak(player);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case HellflameConfig.SPIDER -> HellflameMoves.hellSpider(player);
			case HellflameConfig.NET -> HellflameMoves.spiderNet(player);
			case HellflameConfig.JET -> HellflameMoves.jetBurn(player);
			case HellflameConfig.BEAM -> HellflameMoves.startBeam(player);
			case HellflameConfig.BOOST -> HellflameMoves.startBoost(player);
			case HellflameConfig.DODGE -> HellflameMoves.burstDodge(player);
			case HellflameConfig.FIST -> HellflameMoves.flashfire(player, false);
			case HellflameConfig.UPPERCUT -> HellflameMoves.flashfire(player, true);
			case HellflameConfig.PROMINENCE -> HellflameMoves.startCharge(player);
			case HellflameConfig.SUN -> {
				if (!HellflameMoves.sunUp(player)) {
					HellflameMoves.hellflameSun(player);
				}
			}
			case HellflameConfig.CLOAK -> HellflameMoves.toggleCloak(player);
			case HellflameConfig.VENT -> HellflameMoves.vent(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		HellflameMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		HellflameMoves.stopAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return HellflameMoves.incoming(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		HellflameMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		HellflameMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> Hellflame.beaming(player);
			case SLOT_3 -> Hellflame.boosting(player);
			case SLOT_4 -> Hellflame.dashing(player);
			case SLOT_5 -> Hellflame.charging(player) || Hellflame.sunUp(player);
			case SLOT_6 -> Hellflame.cloaked(player);
			default -> false;
		};
	}

	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_6 && shift;
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { HellflameConfig.SPIDER, HellflameConfig.NET };
			case SLOT_2 -> new String[] { HellflameConfig.JET, HellflameConfig.BEAM };
			case SLOT_3 -> new String[] { HellflameConfig.BOOST, HellflameConfig.DODGE };
			case SLOT_4 -> new String[] { HellflameConfig.FIST, HellflameConfig.UPPERCUT };
			case SLOT_5 -> new String[] { HellflameConfig.PROMINENCE, HellflameConfig.SUN };
			case SLOT_6 -> new String[] { HellflameConfig.CLOAK, HellflameConfig.VENT };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case HellflameConfig.SPIDER -> HellflameConfig.SPIDER_COOLDOWN;
			case HellflameConfig.NET -> HellflameConfig.NET_COOLDOWN;
			case HellflameConfig.JET -> HellflameConfig.JET_COOLDOWN;
			case HellflameConfig.BEAM -> HellflameConfig.BEAM_COOLDOWN;
			case HellflameConfig.BOOST -> HellflameConfig.BOOST_COOLDOWN;
			case HellflameConfig.DODGE -> HellflameConfig.DODGE_COOLDOWN;
			case HellflameConfig.FIST -> HellflameConfig.FIST_COOLDOWN;
			case HellflameConfig.UPPERCUT -> HellflameConfig.UPPERCUT_COOLDOWN;
			case HellflameConfig.PROMINENCE -> HellflameConfig.PROMINENCE_COOLDOWN;
			case HellflameConfig.SUN -> HellflameConfig.SUN_COOLDOWN;
			case HellflameConfig.CLOAK -> HellflameConfig.CLOAK_COOLDOWN;
			case HellflameConfig.VENT -> HellflameConfig.VENT_COOLDOWN;
			default -> 0;
		};
	}
}
