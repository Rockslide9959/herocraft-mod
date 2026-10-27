package com.projecthero.mod.quirk.zerogravity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * v0.16.7 revamp, v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.7): Zero Gravity's six keys (Ochaco Uraraka /
 * Uravity). User rule (every Quirk): X is the MOVEMENT key, V the UTILITY key, R / G / Z / C attacks; hold C at a full Plus
 * Ultra gauge = the ultimate (Full Release).
 * <pre>
 *   R  Comet Toss                      Shift+R  Debris Barrage
 *   G  Gunhead Martial Arts            Shift+G  Zero Gravity Slam
 *   X  Zero-G Leap (tap)               Shift+X  Weightless Push        hold X  Float Self (while held)
 *   Z  Zero Gravity Field              Shift+Z  Debris Shield
 *   C  Meteor Shower (C again: rain)   Shift+C  Gravity Bomb (again: drop)
 *   V  Zero Gravity Touch (tap)        Shift+V  Release (tap)          hold V  Catch, Shift + hold V  Settle (works while sick)
 * </pre>
 * The moves live in {@link ZeroGravityMoves}; every number is in {@link ZeroGravityConfig}. A tap is a press let go within
 * {@link ZeroGravityConfig#HOLD_TICKS}; held longer, the hold move runs until the key is let go. A hanging Meteor Shower cloud
 * and a hanging Gravity Bomb slab can always be dropped, cooldown or not. The press state is per player and static: dropped
 * by {@link #forget} / {@link #clearSessionState}.
 */
public final class ZeroGravityKit implements QuirkKit {
	private static final Map<UUID, long[]> DOWN = new ConcurrentHashMap<>();
	private static final Map<UUID, boolean[]> DOWN_SHIFT = new ConcurrentHashMap<>();
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

	/** X and V tell a tap from a hold. */
	private static boolean holdKey(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 || slot == AbilitySlot.SLOT_5;
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
			if (!holdKey(slot) || at < 0L) {
				return;
			}
			if (held) {
				if (slot == AbilitySlot.SLOT_3) {
					ZeroGravityMoves.floatSelfStop(player);
				} else if (!wasShift) {
					ZeroGravityMoves.catchStop(player);
				}
				return;
			}
			if (now - at >= ZeroGravityConfig.HOLD_TICKS + 2) {
				return; // a hold that could not start (cooldown): no tap either
			}
			String id = idsOf(slot)[wasShift ? 1 : 0];
			if (!Quirks.ready(player, id)) {
				return;
			}
			switch (id) {
				case ZeroGravityConfig.LEAP -> ZeroGravityMoves.leap(player);
				case ZeroGravityConfig.TOUCH -> {
					if (!Quirks.overloaded(player)) {
						ZeroGravityMoves.touch(player);
					}
				}
				case ZeroGravityConfig.RELEASE -> ZeroGravityMoves.release(player);
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
		if (holdKey(slot) && !(slot == AbilitySlot.SLOT_3 && shift)) {
			return; // tap or hold: decided on release / in the tick
		}
		boolean free = (id.equals(ZeroGravityConfig.SHOWER) && ZeroGravityDebris.cloudUp(player))
				|| (id.equals(ZeroGravityConfig.BOMB) && ZeroGravityDebris.bombUp(player));
		if (!free && !Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case ZeroGravityConfig.COMET -> ZeroGravityMoves.cometToss(player);
			case ZeroGravityConfig.BARRAGE -> ZeroGravityMoves.barrage(player);
			case ZeroGravityConfig.GUNHEAD -> ZeroGravityMoves.gunhead(player);
			case ZeroGravityConfig.SLAM -> ZeroGravityMoves.slam(player);
			case ZeroGravityConfig.PUSH -> ZeroGravityMoves.push(player);
			case ZeroGravityConfig.FIELD -> ZeroGravityMoves.field(player);
			case ZeroGravityConfig.SHIELD -> ZeroGravityMoves.debrisShield(player, null);
			case ZeroGravityConfig.SHOWER -> ZeroGravityMoves.meteorShower(player, null);
			case ZeroGravityConfig.BOMB -> ZeroGravityMoves.gravityBomb(player);
			default -> {
			}
		}
	}

	/** The holds: X -> Float Self, V -> Catch, Shift+V -> Settle. */
	private static void tickHolds(ServerPlayer p) {
		long[] d = DOWN.get(p.getUUID());
		if (d == null) {
			return;
		}
		long now = p.level().getGameTime();
		boolean[] shift = flags(DOWN_SHIFT, p);
		boolean[] held = flags(HELD, p);
		int x = AbilitySlot.SLOT_3.ordinal();
		if (d[x] >= 0L && !shift[x] && !held[x] && now - d[x] >= ZeroGravityConfig.HOLD_TICKS && !Quirks.overloaded(p)) {
			if (ZeroGravityFloat.selfFloating(p) || Quirks.ready(p, ZeroGravityConfig.SELF)) {
				held[x] = ZeroGravityMoves.floatSelfStart(p);
			}
		}
		int v = AbilitySlot.SLOT_5.ordinal();
		if (d[v] >= 0L && !held[v] && now - d[v] >= ZeroGravityConfig.HOLD_TICKS) {
			if (shift[v]) {
				if (Quirks.ready(p, ZeroGravityConfig.SETTLE)) {
					ZeroGravityMoves.settle(p);
					Quirks.setAnim(p, ZeroGravity.ANIM_SETTLE);
					held[v] = true;
				}
			} else if (!Quirks.overloaded(p) && Quirks.ready(p, ZeroGravityConfig.CATCH)) {
				ZeroGravityMoves.catchStart(p);
				held[v] = true;
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		tickHolds(player);
		ZeroGravityMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		ZeroGravityMoves.endAll(player);
		DOWN.remove(player.getUUID());
		DOWN_SHIFT.remove(player.getUUID());
		HELD.remove(player.getUUID());
	}

	/**
	 * The Debris Shield stops projectiles (the tick normally catches them before they land); v0.17.1 "Light on her feet": no
	 * fall damage while Nausea is below the Queasy band.
	 */
	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof Projectile && ZeroGravityDebris.shieldUp(player)) {
			return 0f;
		}
		if (source.is(DamageTypeTags.IS_FALL) && Quirks.meter(player) < ZeroGravityConfig.LIGHT_FEET_BELOW) {
			return 0f;
		}
		return amount;
	}

	@Override
	public void clearSessionState() {
		ZeroGravityMoves.clearSessionState();
		ZeroGravityFloat.clearSessionState();
		ZeroGravityDebris.clearSessionState();
		ZeroGravityUltimate.clearSessionState();
		DOWN.clear();
		DOWN_SHIFT.clear();
		HELD.clear();
	}

	@Override
	public void forget(UUID id) {
		ZeroGravityMoves.forget(id);
		ZeroGravityFloat.forget(id);
		ZeroGravityDebris.forget(id);
		ZeroGravityUltimate.forget(id);
		DOWN.remove(id);
		DOWN_SHIFT.remove(id);
		HELD.remove(id);
	}

	@Override
	public void ultimate(ServerPlayer player) {
		ZeroGravityUltimate.fire(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.animId == ZeroGravity.ANIM_BARRAGE && now - s.animStart < 8;
			case SLOT_2 -> s.val(ZeroGravity.V_GUNHEAD) > now;
			case SLOT_3 -> s.val(ZeroGravity.V_SELF) != 0L || s.val(ZeroGravity.V_LEAP) > now;
			case SLOT_4 -> s.val(ZeroGravity.V_FIELD) > now || s.val(ZeroGravity.V_SHIELD) > now;
			case SLOT_5 -> s.val(ZeroGravity.V_FLOATING) > 0L || s.val(ZeroGravity.V_BLOCKS) > 0L || s.val(ZeroGravity.V_SETTLE) > now;
			case SLOT_6 -> s.val(ZeroGravity.V_CLOUD) > now || s.val(ZeroGravity.V_FULL) > now;
			default -> false;
		};
	}

	/** Settle (Shift + hold V) works while she is sick. */
	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_5 && shift;
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ZeroGravityConfig.COMET, ZeroGravityConfig.BARRAGE };
			case SLOT_2 -> new String[] { ZeroGravityConfig.GUNHEAD, ZeroGravityConfig.SLAM };
			case SLOT_3 -> new String[] { ZeroGravityConfig.LEAP, ZeroGravityConfig.PUSH };
			case SLOT_4 -> new String[] { ZeroGravityConfig.FIELD, ZeroGravityConfig.SHIELD };
			case SLOT_5 -> new String[] { ZeroGravityConfig.TOUCH, ZeroGravityConfig.RELEASE };
			case SLOT_6 -> new String[] { ZeroGravityConfig.SHOWER, ZeroGravityConfig.BOMB };
			default -> new String[0];
		};
	}

	/** v0.17.1: hold X = Float Self, hold V = Catch (Shift + hold V = Settle, in Release's description). */
	@Override
	public String holdIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> ZeroGravityConfig.SELF;
			case SLOT_5 -> ZeroGravityConfig.CATCH;
			default -> null;
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ZeroGravityConfig.SHOWER -> ZeroGravityConfig.SHOWER_COOLDOWN;
			case ZeroGravityConfig.SHIELD -> ZeroGravityConfig.SHIELD_COOLDOWN;
			case ZeroGravityConfig.COMET -> ZeroGravityConfig.COMET_COOLDOWN;
			case ZeroGravityConfig.BARRAGE -> ZeroGravityConfig.BARRAGE_COOLDOWN;
			case ZeroGravityConfig.SLAM -> ZeroGravityConfig.SLAM_COOLDOWN;
			case ZeroGravityConfig.SELF -> ZeroGravityConfig.SELF_COOLDOWN;
			case ZeroGravityConfig.LEAP -> ZeroGravityConfig.LEAP_COOLDOWN;
			case ZeroGravityConfig.PUSH -> ZeroGravityConfig.PUSH_COOLDOWN;
			case ZeroGravityConfig.GUNHEAD -> ZeroGravityConfig.GUNHEAD_COOLDOWN;
			case ZeroGravityConfig.FIELD -> ZeroGravityConfig.FIELD_COOLDOWN;
			case ZeroGravityConfig.TOUCH -> ZeroGravityConfig.TOUCH_COOLDOWN;
			case ZeroGravityConfig.RELEASE -> ZeroGravityConfig.RELEASE_COOLDOWN;
			case ZeroGravityConfig.CATCH -> ZeroGravityConfig.CATCH_COOLDOWN;
			case ZeroGravityConfig.SETTLE -> ZeroGravityConfig.SETTLE_COOLDOWN;
			case ZeroGravityConfig.BOMB -> ZeroGravityConfig.BOMB_COOLDOWN;
			default -> 0;
		};
	}
}
