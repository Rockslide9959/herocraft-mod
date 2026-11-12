package com.projecthero.mod.titans;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Titans Together (docs/TEEN_TITANS_DESIGN.md): a Titan (Starfire, Beast Boy, Raven or Cyborg) within 32 blocks of a
 * squadmate who is also a Titan hits 10% harder and refills their power bar 20% faster. Re-checked once a second; the flag is a
 * synced, unsaved attachment (so no static state) that also drives the small "T" badge on the HUD.
 */
public final class TitansTogether {
	private TitansTogether() {
	}

	public static final double RANGE = 32.0;
	public static final float DAMAGE_MULT = 1.10f;
	public static final float REGEN_MULT = 1.20f;

	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(ProjectHeroMod.id("titans_together"),
			builder -> builder.initializer(() -> false).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Loads the class (registers the attachment). */
	public static void initialize() {
	}

	public static boolean isTitan(Player p) {
		return com.projecthero.mod.starfire.Starfire.hasPower(p) || com.projecthero.mod.raven.Raven.hasPower(p)
				|| com.projecthero.mod.beastboy.BeastBoy.hasPower(p) || com.projecthero.mod.cyborg.Cyborg.hasPower(p);
	}

	public static boolean active(Player p) {
		return p != null && p.getAttachedOrElse(ACTIVE, false);
	}

	/** Bar refill multiplier for {@code p} (1.2 while Titans Together is on). */
	public static float regenMult(Player p) {
		return active(p) ? REGEN_MULT : 1f;
	}

	/** Called every server tick per player; re-checks once a second. */
	public static void tick(ServerPlayer p) {
		if (p.tickCount % 20 != 0) {
			return;
		}
		boolean on = compute(p);
		if (active(p) != on) {
			p.setAttached(ACTIVE, on);
		}
	}

	/** True if {@code p} is a Titan with a living Titan squadmate within {@link #RANGE} in the same dimension. */
	public static boolean compute(ServerPlayer p) {
		if (!p.isAlive() || p.isSpectator() || !isTitan(p)) {
			return false;
		}
		for (ServerPlayer o : p.serverLevel().players()) {
			if (o != p && o.isAlive() && !o.isSpectator() && o.distanceToSqr(p) <= RANGE * RANGE && isTitan(o) && Squads.areAllies(p, o)) {
				return true;
			}
		}
		return false;
	}

	/** Scales a hit a Titan deals while Titans Together is on (from the {@code LivingEntity#hurt} head). */
	public static float scaleOutgoing(LivingEntity victim, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player dealer && dealer != victim && active(dealer) && isTitan(dealer)) {
			return amount * DAMAGE_MULT;
		}
		return amount;
	}
}
