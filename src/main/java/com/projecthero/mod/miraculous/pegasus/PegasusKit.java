package com.projecthero.mod.miraculous.pegasus;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.pegasus.entity.PegasusPortalEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * v0.16.6: Pegasus's six keys (Kaalki, the Horseshoe Glasses, the Horseshoe).
 * <pre>
 *   R  Horseshoe Throw     Shift+R  Horseshoe Volley
 *   G  Hoof Kick           Shift+G  Gallop Trample
 *   X  Portal Hop          Shift+X  Gallop
 *   Z  Portal Trap         Shift+Z  VOYAGE (starts the countdown when the portal opens)
 *   V  Portal Shield       Shift+V  Sky Gallop
 *   C  Mark Waypoint       Shift+C  Linked Portals
 * </pre>
 * The moves live in {@link PegasusMoves}; every number is in {@link PegasusConfig}.
 */
public final class PegasusKit implements KwamiKit {
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
		// V closes a standing shield even though its cooldown has not started yet
		if (!Miraculous.ready(player, id)) {
			return;
		}
		switch (id) {
			case PegasusConfig.THROW -> PegasusMoves.throwShoe(player);
			case PegasusConfig.VOLLEY -> PegasusMoves.volley(player);
			case PegasusConfig.KICK -> PegasusMoves.kick(player);
			case PegasusConfig.TRAMPLE -> PegasusMoves.trample(player);
			case PegasusConfig.HOP -> PegasusMoves.hop(player);
			case PegasusConfig.GALLOP -> PegasusMoves.gallop(player);
			case PegasusConfig.TRAP -> PegasusMoves.trap(player);
			case PegasusConfig.VOYAGE -> PegasusMoves.voyage(player);
			case PegasusConfig.SHIELD -> PegasusMoves.shield(player);
			case PegasusConfig.SKY -> {
				if (!Pegasus.running(player, Pegasus.V_SKY)) {
					PegasusMoves.sky(player);
				}
			}
			case PegasusConfig.WAYPOINT -> PegasusMoves.markWaypoint(player);
			case PegasusConfig.LINK -> {
				if (!Pegasus.running(player, Pegasus.V_LINK)) {
					PegasusMoves.link(player);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!Miraculous.transformedAs(player, Kwami.KAALKI)) {
			return;
		}
		PegasusMoves.tick(player);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		PegasusMoves.endAll(player);
	}

	/** The Portal Shield swallows a projectile hit from the front (the tick normally catches it before it lands). */
	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		PegasusMoves.noteAttacker(player, source);
		if (source.getDirectEntity() instanceof Projectile pr && PegasusMoves.shieldCovers(player, pr.position().subtract(pr.getDeltaMovement()))) {
			return 0f;
		}
		return amount;
	}

	@Override
	public void clearSessionState() {
		PegasusMoves.clearSessionState();
		PegasusPortalEntity.ACTIVE.clear();
		PegasusPortalEntity.PASSED.clear();
	}

	@Override
	public void forget(UUID id) {
		PegasusMoves.forget(id);
		PegasusPortalEntity.PASSED.remove(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Pegasus.V_SHOE_OUT) != 0L;
			case SLOT_2 -> s.val(Pegasus.V_TRAMPLE) > now;
			case SLOT_3 -> s.val(Pegasus.V_GALLOP) != 0L;
			case SLOT_4 -> s.val(Pegasus.V_VOYAGE) > now;
			case SLOT_5 -> s.val(Pegasus.V_SHIELD) > now || s.val(Pegasus.V_SKY) > now;
			case SLOT_6 -> s.val(Pegasus.V_LINK) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { PegasusConfig.THROW, PegasusConfig.VOLLEY };
			case SLOT_2 -> new String[] { PegasusConfig.KICK, PegasusConfig.TRAMPLE };
			case SLOT_3 -> new String[] { PegasusConfig.HOP, PegasusConfig.GALLOP };
			case SLOT_4 -> new String[] { PegasusConfig.TRAP, PegasusConfig.VOYAGE };
			case SLOT_5 -> new String[] { PegasusConfig.SHIELD, PegasusConfig.SKY };
			case SLOT_6 -> new String[] { PegasusConfig.WAYPOINT, PegasusConfig.LINK };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case PegasusConfig.THROW -> PegasusConfig.THROW_COOLDOWN;
			case PegasusConfig.VOLLEY -> PegasusConfig.VOLLEY_COOLDOWN;
			case PegasusConfig.KICK -> PegasusConfig.KICK_COOLDOWN;
			case PegasusConfig.TRAMPLE -> PegasusConfig.TRAMPLE_COOLDOWN;
			case PegasusConfig.HOP -> PegasusConfig.HOP_COOLDOWN;
			case PegasusConfig.GALLOP -> PegasusConfig.GALLOP_COOLDOWN;
			case PegasusConfig.TRAP -> PegasusConfig.TRAP_COOLDOWN;
			case PegasusConfig.VOYAGE -> PegasusConfig.VOYAGE_COOLDOWN;
			case PegasusConfig.SHIELD -> PegasusConfig.SHIELD_COOLDOWN;
			case PegasusConfig.SKY -> PegasusConfig.SKY_COOLDOWN;
			case PegasusConfig.WAYPOINT -> PegasusConfig.WAYPOINT_COOLDOWN;
			case PegasusConfig.LINK -> PegasusConfig.LINK_COOLDOWN;
			default -> 0;
		};
	}
}
