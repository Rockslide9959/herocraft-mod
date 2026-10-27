package com.projecthero.mod.quirk.erasure;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp, v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.5): Erasure's six keys (see {@link ErasureConfig}
 * for the table). The moves live in {@link ErasureMoves}, the ultimate in {@link ErasureUltimate}.
 * <ul>
 *   <li>V (the stare) is held: it runs until V is let go (the blink) or his eyes dry out.</li>
 *   <li>R: Capture Bind fires on the press; keep R held ({@link ErasureConfig#HOLD_TICKS}) on a bound creature and it is
 *       strung up -- Reel Lock -- until R is let go.</li>
 *   <li>X: a tap (let go within {@link ErasureConfig#SWING_HOLD_TICKS}) is the Scarf Pull; held, the Scarf Swing starts and
 *       lasts until X is let go. X again mid-swing lets go too.</li>
 *   <li>C: the Scarf Whip; at a full Plus Ultra gauge the framework turns a held C into the ultimate.</li>
 * </ul>
 * The press state (when, which variant) is per player and static: dropped by {@link #forget} / {@link #clearSessionState}.
 */
public final class ErasureKit implements QuirkKit {
	/** Per player, per slot: when the key went down (-1 = up) and whether it was the Shift variant. */
	private static final Map<UUID, long[]> DOWN = new ConcurrentHashMap<>();
	private static final Map<UUID, boolean[]> DOWN_SHIFT = new ConcurrentHashMap<>();
	/** Per player, per slot: the hold has already fired (the release must not also fire the tap). */
	private static final Map<UUID, boolean[]> HELD = new ConcurrentHashMap<>();

	private static long[] down(ServerPlayer p) {
		return DOWN.computeIfAbsent(p.getUUID(), k -> {
			long[] a = new long[AbilitySlot.values().length];
			java.util.Arrays.fill(a, -1L);
			return a;
		});
	}

	private static boolean[] flags(Map<UUID, boolean[]> m, ServerPlayer p) {
		return m.computeIfAbsent(p.getUUID(), k -> new boolean[AbilitySlot.values().length]);
	}

	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		int i = slot.ordinal();
		long now = player.level().getGameTime();
		if (!pressed) {
			long at = down(player)[i];
			boolean wasShift = flags(DOWN_SHIFT, player)[i];
			boolean held = flags(HELD, player)[i];
			down(player)[i] = -1L;
			flags(HELD, player)[i] = false;
			switch (slot) {
				case SLOT_5 -> ErasureMoves.eraseUp(player);
				case SLOT_1 -> ErasureMoves.reelStop(player);
				case SLOT_3 -> {
					if (held) {
						ErasureMoves.swingUp(player);
					} else if (at >= 0L && !wasShift && now - at < ErasureConfig.SWING_HOLD_TICKS && Quirks.ready(player, ErasureConfig.ZIP)) {
						ErasureMoves.zip(player); // a tap: the Scarf Pull
					}
				}
				default -> {
				}
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		down(player)[i] = now;
		flags(DOWN_SHIFT, player)[i] = shift;
		flags(HELD, player)[i] = false;
		String id = ids[shift ? 1 : 0];
		// X again while swinging lets go, whatever its cooldown
		if (slot == AbilitySlot.SLOT_3 && !shift && Quirks.state(player).val(ErasureConfig.V_LINE) != 0L) {
			ErasureMoves.detach(player, true);
			flags(HELD, player)[i] = true; // swallow this press's release
			return;
		}
		if (slot == AbilitySlot.SLOT_3 && !shift) {
			return; // tap or hold: decided on release / in the tick
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case ErasureConfig.BIND -> ErasureMoves.bind(player);
			case ErasureConfig.LASH -> ErasureMoves.pull(player);
			case ErasureConfig.COMBO -> ErasureMoves.combo(player);
			case ErasureConfig.COUNTER -> ErasureMoves.counter(player);
			case ErasureConfig.FLIP -> ErasureMoves.flip(player);
			case ErasureConfig.SPIN -> ErasureMoves.spin(player);
			case ErasureConfig.DISARM -> ErasureMoves.disarm(player);
			case ErasureConfig.WHIP -> ErasureMoves.whip(player);
			case ErasureConfig.MULTI -> ErasureMoves.multiBind(player);
			case ErasureConfig.ERASE -> ErasureMoves.eraseDown(player);
			case ErasureConfig.GOGGLES -> ErasureMoves.goggles(player);
			default -> {
			}
		}
	}

	/** The holds: X held -> the swing; R held on a bound creature -> Reel Lock. */
	private static void tickHolds(ServerPlayer p) {
		long[] d = DOWN.get(p.getUUID());
		if (d == null) {
			return;
		}
		long now = p.level().getGameTime();
		boolean[] shift = flags(DOWN_SHIFT, p);
		boolean[] held = flags(HELD, p);
		int x = AbilitySlot.SLOT_3.ordinal();
		if (d[x] >= 0L && !shift[x] && !held[x] && now - d[x] >= ErasureConfig.SWING_HOLD_TICKS) {
			held[x] = true;
			if (Quirks.ready(p, ErasureConfig.SWING)) {
				ErasureMoves.swingDown(p);
			}
		}
		int r = AbilitySlot.SLOT_1.ordinal();
		if (d[r] >= 0L && !shift[r] && !held[r] && now - d[r] >= ErasureConfig.HOLD_TICKS && ErasureMoves.boundTarget(p) != null) {
			held[r] = ErasureMoves.reelStart(p);
		}
	}

	/** Tests: X held long enough for the swing / R long enough for the reel. */
	public static int holdTicks(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 ? ErasureConfig.SWING_HOLD_TICKS : ErasureConfig.HOLD_TICKS;
	}

	@Override
	public void tick(ServerPlayer player) {
		tickHolds(player);
		ErasureMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		ErasureMoves.stopAll(player);
		DOWN.remove(player.getUUID());
		HELD.remove(player.getUUID());
		DOWN_SHIFT.remove(player.getUUID());
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return ErasureMoves.modifyIncomingDamage(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		ErasureMoves.clearSessionState();
		DOWN.clear();
		DOWN_SHIFT.clear();
		HELD.clear();
	}

	@Override
	public void forget(UUID id) {
		ErasureMoves.forget(id);
		DOWN.remove(id);
		DOWN_SHIFT.remove(id);
		HELD.remove(id);
	}

	@Override
	public void ultimate(ServerPlayer player) {
		ErasureUltimate.fire(player);
	}

	@Override
	public boolean keyLocked(Player player, AbilitySlot slot, boolean shift) {
		// eyes forced shut: no stare until they recover (Eye Drops still work)
		return slot == AbilitySlot.SLOT_5 && !shift && ErasureMoves.eyesShut(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> (s.val(ErasureConfig.V_BIND) != 0L && s.val(ErasureConfig.V_BIND_UNTIL) > now) || s.val(ErasureConfig.V_REEL) != 0L;
			case SLOT_2 -> s.val(ErasureConfig.V_COUNTER) > now || (s.val(ErasureConfig.V_COMBO_AT) != 0L
					&& now - s.val(ErasureConfig.V_COMBO_AT) < ErasureConfig.COMBO_TICKS);
			case SLOT_3 -> s.val(ErasureConfig.V_LINE) != 0L || s.val(ErasureConfig.V_FLIP_UNTIL) > now;
			case SLOT_4 -> s.val(ErasureConfig.V_SPIN) != 0L;
			case SLOT_5 -> s.val(ErasureConfig.V_ERASE) != 0L || s.val(ErasureConfig.V_GOGGLES) != 0L;
			case SLOT_6 -> s.val(ErasureConfig.V_MULTI_UNTIL) > now || s.val(ErasureConfig.V_TD_AT) != 0L;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ErasureConfig.BIND, ErasureConfig.LASH };
			case SLOT_2 -> new String[] { ErasureConfig.COMBO, ErasureConfig.COUNTER };
			case SLOT_3 -> new String[] { ErasureConfig.ZIP, ErasureConfig.FLIP };
			case SLOT_4 -> new String[] { ErasureConfig.SPIN, ErasureConfig.DISARM };
			case SLOT_5 -> new String[] { ErasureConfig.ERASE, ErasureConfig.GOGGLES };
			case SLOT_6 -> new String[] { ErasureConfig.WHIP, ErasureConfig.MULTI };
			default -> new String[0];
		};
	}

	/** v0.17.1: hold R = Reel Lock, hold X = Scarf Swing (hold C = the ultimate, shown by the framework). */
	@Override
	public String holdIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> ErasureConfig.REEL;
			case SLOT_3 -> ErasureConfig.SWING;
			default -> null;
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ErasureConfig.BIND -> ErasureConfig.BIND_COOLDOWN;
			case ErasureConfig.LASH -> ErasureConfig.LASH_COOLDOWN;
			case ErasureConfig.REEL -> ErasureConfig.REEL_COOLDOWN;
			case ErasureConfig.COMBO -> ErasureConfig.COMBO_COOLDOWN;
			case ErasureConfig.COUNTER -> ErasureConfig.COUNTER_COOLDOWN;
			case ErasureConfig.SWING -> ErasureConfig.SWING_COOLDOWN;
			case ErasureConfig.ZIP -> ErasureConfig.ZIP_COOLDOWN;
			case ErasureConfig.FLIP -> ErasureConfig.FLIP_COOLDOWN;
			case ErasureConfig.SPIN -> ErasureConfig.SPIN_COOLDOWN;
			case ErasureConfig.DISARM -> ErasureConfig.DISARM_COOLDOWN;
			case ErasureConfig.WHIP -> ErasureConfig.WHIP_COOLDOWN;
			case ErasureConfig.MULTI -> ErasureConfig.MULTI_COOLDOWN;
			case ErasureConfig.GOGGLES -> ErasureConfig.GOGGLES_COOLDOWN;
			case ErasureConfig.ERASE -> ErasureConfig.ERASE_COOLDOWN;
			case ErasureConfig.DROPS -> ErasureConfig.DROPS_COOLDOWN;
			default -> 0;
		};
	}
}
