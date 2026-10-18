package com.projecthero.mod.miraculous.carapace;

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
 * v0.16.6: Carapace's six keys (Wayzz, the Turtle Bracelet, the Shield).
 * <pre>
 *   R  Shield Bash         Shift+R  Shield Throw
 *   G  Shield Slam         Shift+G  Taunt
 *   X  Shield Surf (toggle) Shift+X Shell Roll
 *   Z  Shell Guard (held)  Shift+Z  SHELTER (starts the countdown)
 *   V  Shell Barrier       Shift+V  Protect
 *   C  Steadfast           Shift+C  Tidal Swim
 * </pre>
 * The moves live in {@link CarapaceMoves}; every number is in {@link CarapaceConfig}.
 */
public final class CarapaceKit implements KwamiKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (slot == AbilitySlot.SLOT_4 && !pressed) {
			// Shell Guard is held: any Z release lowers it
			CarapaceMoves.guardDown(player);
			return;
		}
		if (!pressed) {
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		if (id.equals(CarapaceConfig.GUARD)) {
			CarapaceMoves.guardUp(player);
			return;
		}
		if (id.equals(CarapaceConfig.SURF) && Miraculous.state(player).val(Carapace.V_SURF) != 0L) {
			CarapaceMoves.surf(player); // off again is never on a cooldown
			return;
		}
		if (!Miraculous.ready(player, id)) {
			return;
		}
		switch (id) {
			case CarapaceConfig.BASH -> CarapaceMoves.bash(player);
			case CarapaceConfig.THROW -> CarapaceMoves.throwShield(player);
			case CarapaceConfig.SLAM -> CarapaceMoves.slam(player);
			case CarapaceConfig.TAUNT -> CarapaceMoves.taunt(player);
			case CarapaceConfig.SURF -> CarapaceMoves.surf(player);
			case CarapaceConfig.ROLL -> CarapaceMoves.roll(player);
			case CarapaceConfig.SHELTER -> CarapaceMoves.shelter(player);
			case CarapaceConfig.BARRIER -> CarapaceMoves.barrier(player);
			case CarapaceConfig.PROTECT -> CarapaceMoves.protect(player);
			case CarapaceConfig.STEADFAST -> CarapaceMoves.steadfast(player);
			case CarapaceConfig.TIDAL -> CarapaceMoves.tidal(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!Miraculous.transformedAs(player, Kwami.WAYZZ)) {
			return;
		}
		CarapaceMoves.tick(player);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		CarapaceMoves.endAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return CarapaceMoves.incoming(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		CarapaceMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		CarapaceMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Carapace.V_SHIELD_OUT) != 0L || s.val(Carapace.V_BASH) > now;
			case SLOT_2 -> s.val(Carapace.V_TAUNT) > now;
			case SLOT_3 -> s.val(Carapace.V_SURF) != 0L || s.val(Carapace.V_ROLL) > now;
			case SLOT_4 -> s.val(Carapace.V_GUARD) != 0L || s.val(Carapace.V_SHELTER) > now;
			case SLOT_5 -> s.val(Carapace.V_BARRIER) > now || s.val(Carapace.V_PROTECT) > now;
			case SLOT_6 -> s.val(Carapace.V_STEADFAST) > now || s.val(Carapace.V_TIDAL) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { CarapaceConfig.BASH, CarapaceConfig.THROW };
			case SLOT_2 -> new String[] { CarapaceConfig.SLAM, CarapaceConfig.TAUNT };
			case SLOT_3 -> new String[] { CarapaceConfig.SURF, CarapaceConfig.ROLL };
			case SLOT_4 -> new String[] { CarapaceConfig.GUARD, CarapaceConfig.SHELTER };
			case SLOT_5 -> new String[] { CarapaceConfig.BARRIER, CarapaceConfig.PROTECT };
			case SLOT_6 -> new String[] { CarapaceConfig.STEADFAST, CarapaceConfig.TIDAL };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case CarapaceConfig.BASH -> CarapaceConfig.BASH_COOLDOWN;
			case CarapaceConfig.THROW -> CarapaceConfig.THROW_COOLDOWN;
			case CarapaceConfig.SLAM -> CarapaceConfig.SLAM_COOLDOWN;
			case CarapaceConfig.TAUNT -> CarapaceConfig.TAUNT_COOLDOWN;
			case CarapaceConfig.SURF -> CarapaceConfig.SURF_COOLDOWN;
			case CarapaceConfig.ROLL -> CarapaceConfig.ROLL_COOLDOWN;
			case CarapaceConfig.GUARD -> CarapaceConfig.GUARD_COOLDOWN;
			case CarapaceConfig.SHELTER -> CarapaceConfig.SHELTER_COOLDOWN;
			case CarapaceConfig.BARRIER -> CarapaceConfig.BARRIER_COOLDOWN;
			case CarapaceConfig.PROTECT -> CarapaceConfig.PROTECT_COOLDOWN;
			case CarapaceConfig.STEADFAST -> CarapaceConfig.STEADFAST_COOLDOWN;
			case CarapaceConfig.TIDAL -> CarapaceConfig.TIDAL_COOLDOWN;
			default -> 0;
		};
	}
}
