package com.projecthero.mod.miraculous.multimouse;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: Multimouse's six keys (Mullo, the Mouse Pendant, the jump rope).
 * <pre>
 *   R  Rope Lash             Shift+R  Lasso Yank
 *   G  Rope Twirl            Shift+G  Skipping Trip
 *   X  Rope Swing            Shift+X  Scurry (toggle)
 *   Z  Mouse Swarm           Shift+Z  MULTITUDE (starts the countdown)
 *   V  Mouse Hearing (toggle) Shift+V Gnaw
 *   C  Rope Bind             Shift+C  Mouse Trap
 * </pre>
 * The moves live in {@link MultimouseMoves}, the special in {@link Multitude}; every number is in {@link MultimouseConfig}.
 */
public final class MultimouseKit implements KwamiKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		MiraculousState st = Miraculous.state(player);
		if (id.equals(MultimouseConfig.MULTITUDE)) {
			// split: Shift+Z again recombines (never on the cooldown)
			if (Multitude.live(player)) {
				Multitude.recombine(player);
			} else if (Miraculous.ready(player, id)) {
				Multitude.start(player);
			}
			return;
		}
		// switching a toggle off is never on a cooldown
		if (id.equals(MultimouseConfig.HEARING) && st.val(Multimouse.V_HEARING) != 0L) {
			MultimouseMoves.hearing(player);
			return;
		}
		if (id.equals(MultimouseConfig.SCURRY) && st.val(Multimouse.V_SCURRY) != 0L) {
			MultimouseMoves.scurry(player);
			return;
		}
		if (!Miraculous.ready(player, id)) {
			return;
		}
		switch (id) {
			case MultimouseConfig.LASH -> MultimouseMoves.lash(player);
			case MultimouseConfig.LASSO -> MultimouseMoves.lasso(player);
			case MultimouseConfig.TWIRL -> MultimouseMoves.twirl(player);
			case MultimouseConfig.TRIP -> MultimouseMoves.trip(player);
			case MultimouseConfig.SWING -> MultimouseMoves.ropeSwing(player);
			case MultimouseConfig.SCURRY -> MultimouseMoves.scurry(player);
			case MultimouseConfig.SWARM -> MultimouseMoves.swarm(player);
			case MultimouseConfig.HEARING -> MultimouseMoves.hearing(player);
			case MultimouseConfig.GNAW -> MultimouseMoves.gnaw(player);
			case MultimouseConfig.BIND -> MultimouseMoves.bind(player);
			case MultimouseConfig.TRAP -> MultimouseMoves.trap(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!Miraculous.transformedAs(player, Kwami.MULLO)) {
			MultimouseMoves.clearSize(player);
			return;
		}
		MultimouseMoves.tick(player);
		Multitude.tick(player);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		Multitude.drop(player);
		MultimouseMoves.endAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return MultimouseMoves.incoming(player, source, amount);
	}

	@Override
	public float modifyFallDamage(ServerPlayer player, float amount) {
		return MultimouseMoves.fall(player, amount);
	}

	@Override
	public void clearSessionState() {
		MultimouseMoves.clearSessionState();
		Multitude.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		MultimouseMoves.forget(id);
		Multitude.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Multimouse.V_COMBO) > now || s.val(Multimouse.V_LASSO) > now;
			case SLOT_2 -> s.val(Multimouse.V_TWIRL) > now;
			case SLOT_3 -> s.val(Multimouse.V_SWING) > now || s.val(Multimouse.V_SCURRY) != 0L;
			case SLOT_4 -> s.val(Multimouse.V_SWARM) > now || s.val(Multimouse.V_MULTI) != 0L;
			case SLOT_5 -> s.val(Multimouse.V_HEARING) != 0L;
			case SLOT_6 -> s.val(Multimouse.V_BIND) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { MultimouseConfig.LASH, MultimouseConfig.LASSO };
			case SLOT_2 -> new String[] { MultimouseConfig.TWIRL, MultimouseConfig.TRIP };
			case SLOT_3 -> new String[] { MultimouseConfig.SWING, MultimouseConfig.SCURRY };
			case SLOT_4 -> new String[] { MultimouseConfig.SWARM, MultimouseConfig.MULTITUDE };
			case SLOT_5 -> new String[] { MultimouseConfig.HEARING, MultimouseConfig.GNAW };
			case SLOT_6 -> new String[] { MultimouseConfig.BIND, MultimouseConfig.TRAP };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case MultimouseConfig.LASH -> MultimouseConfig.LASH_COOLDOWN;
			case MultimouseConfig.LASSO -> MultimouseConfig.LASSO_COOLDOWN;
			case MultimouseConfig.TWIRL -> MultimouseConfig.TWIRL_COOLDOWN;
			case MultimouseConfig.TRIP -> MultimouseConfig.TRIP_COOLDOWN;
			case MultimouseConfig.SWING -> MultimouseConfig.SWING_COOLDOWN;
			case MultimouseConfig.SCURRY -> MultimouseConfig.SCURRY_COOLDOWN;
			case MultimouseConfig.SWARM -> MultimouseConfig.SWARM_COOLDOWN;
			case MultimouseConfig.MULTITUDE -> MultimouseConfig.MULTITUDE_COOLDOWN;
			case MultimouseConfig.HEARING -> MultimouseConfig.HEARING_COOLDOWN;
			case MultimouseConfig.GNAW -> MultimouseConfig.GNAW_COOLDOWN;
			case MultimouseConfig.BIND -> MultimouseConfig.BIND_COOLDOWN;
			case MultimouseConfig.TRAP -> MultimouseConfig.TRAP_COOLDOWN;
			default -> 0;
		};
	}
}
