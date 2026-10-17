package com.projecthero.mod.miraculous.renarouge;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.renarouge.entity.FakeWallEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Rena Rouge's six keys (Trixx, the Necklace, the Flute).
 * <pre>
 *   R  Flute Strike        Shift+R  Flute Sweep
 *   G  Flute Throw         Shift+G  Melody Lure
 *   X  Fox Dash            Shift+X  Illusion Swap
 *   Z  Illusion Double     Shift+Z  MIRAGE (starts the countdown when the bubble pops)
 *   V  Fox Camouflage      Shift+V  Fake Wall
 *   C  Fox Pounce          Shift+C  Fox Feint
 * </pre>
 * The moves live in {@link RenaRougeMoves}; every number is in {@link RenaRougeConfig}.
 */
public final class RenaRougeKit implements KwamiKit {
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
		if (!Miraculous.ready(player, id)) {
			return;
		}
		switch (id) {
			case RenaRougeConfig.STRIKE -> RenaRougeMoves.strike(player);
			case RenaRougeConfig.SWEEP -> RenaRougeMoves.sweep(player);
			case RenaRougeConfig.THROW -> RenaRougeMoves.throwFlute(player);
			case RenaRougeConfig.LURE -> RenaRougeMoves.lure(player);
			case RenaRougeConfig.DASH -> RenaRougeMoves.dash(player);
			case RenaRougeConfig.SWAP -> RenaRougeMoves.swap(player);
			case RenaRougeConfig.DOUBLE -> RenaRougeMoves.illusionDouble(player);
			case RenaRougeConfig.MIRAGE -> RenaRougeMoves.mirage(player);
			case RenaRougeConfig.CAMO -> RenaRougeMoves.camouflage(player);
			case RenaRougeConfig.WALL -> RenaRougeMoves.fakeWall(player);
			case RenaRougeConfig.POUNCE -> RenaRougeMoves.pounce(player);
			case RenaRougeConfig.FEINT -> RenaRougeMoves.feint(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!Miraculous.transformedAs(player, Kwami.TRIXX)) {
			return;
		}
		RenaRougeMoves.tick(player);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		RenaRougeMoves.endAll(player);
	}

	@Override
	public void clearSessionState() {
		RenaRougeMoves.clearSessionState();
		FakeWallEntity.ACTIVE.clear();
	}

	@Override
	public void forget(UUID id) {
		RenaRougeMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_2 -> s.val(RenaRouge.V_FLUTE_OUT) != 0L || s.val(RenaRouge.V_LURE) > now;
			case SLOT_4 -> s.val(RenaRouge.V_DOUBLE) > now || s.val(RenaRouge.V_MIRAGE) > now;
			case SLOT_5 -> s.val(RenaRouge.V_CAMO) > now || s.val(RenaRouge.V_WALL) > now;
			case SLOT_6 -> s.val(RenaRouge.V_POUNCE) != 0L || s.val(RenaRouge.V_FEINT) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { RenaRougeConfig.STRIKE, RenaRougeConfig.SWEEP };
			case SLOT_2 -> new String[] { RenaRougeConfig.THROW, RenaRougeConfig.LURE };
			case SLOT_3 -> new String[] { RenaRougeConfig.DASH, RenaRougeConfig.SWAP };
			case SLOT_4 -> new String[] { RenaRougeConfig.DOUBLE, RenaRougeConfig.MIRAGE };
			case SLOT_5 -> new String[] { RenaRougeConfig.CAMO, RenaRougeConfig.WALL };
			case SLOT_6 -> new String[] { RenaRougeConfig.POUNCE, RenaRougeConfig.FEINT };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case RenaRougeConfig.STRIKE -> RenaRougeConfig.STRIKE_COOLDOWN;
			case RenaRougeConfig.SWEEP -> RenaRougeConfig.SWEEP_COOLDOWN;
			case RenaRougeConfig.THROW -> RenaRougeConfig.THROW_COOLDOWN;
			case RenaRougeConfig.LURE -> RenaRougeConfig.LURE_COOLDOWN;
			case RenaRougeConfig.DASH -> RenaRougeConfig.DASH_COOLDOWN;
			case RenaRougeConfig.SWAP -> RenaRougeConfig.SWAP_COOLDOWN;
			case RenaRougeConfig.DOUBLE -> RenaRougeConfig.DOUBLE_COOLDOWN;
			case RenaRougeConfig.MIRAGE -> RenaRougeConfig.MIRAGE_COOLDOWN;
			case RenaRougeConfig.CAMO -> RenaRougeConfig.CAMO_COOLDOWN;
			case RenaRougeConfig.WALL -> RenaRougeConfig.WALL_COOLDOWN;
			case RenaRougeConfig.POUNCE -> RenaRougeConfig.POUNCE_COOLDOWN;
			case RenaRougeConfig.FEINT -> RenaRougeConfig.FEINT_COOLDOWN;
			default -> 0;
		};
	}
}
