package com.projecthero.mod.punisher;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.firearm.FirearmHooks;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The passive half of being the Punisher. He is not superhuman, so there are no standing
 * strength/speed/health buffs on the <em>person</em> -- the advantage is entirely in how he handles
 * firearms:
 *
 * <ul>
 *   <li><b>Weapon Proficiency</b> -- a regenerating personal reserve (2 mags/gun), reduced recoil, faster weapon handling
 *       (all via {@link Hooks}).</li>
 *   <li><b>Faster Reloading</b> -- 15% quicker ({@link Hooks#reloadSpeedFactor}).</li>
 *   <li><b>Ballistic Expertise</b> -- slightly tighter spread.</li>
 *   <li><b>No Mercy</b> -- +25% firearm damage to a non-boss hostile below 15% health (+10% to a
 *       boss).</li>
 *   <li><b>Headshot Feedback</b> -- handled in {@link com.projecthero.mod.firearm.FirearmShooting} +
 *       {@link com.projecthero.mod.network.FirearmHeadshotPayload}.</li>
 * </ul>
 *
 * <p>v0.15.18: Adrenaline and Suppressive Fire are gone, and with them every temporary modifier this class used to
 * set. {@link #reconcile} still strips the old Suppressive Fire slow (a fixed-id transient modifier) in case one is
 * left on a player, and keeps the tactical-armour set bonus in line.
 */
public final class PunisherPassives {
	private static final ResourceLocation SUPPRESS_SLOW = ProjectHeroMod.id("punisher_suppressive_slow");

	private PunisherPassives() {
	}

	// ---------------- per-tick upkeep ----------------

	public static void tick(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		// expire the timed buffs by simply reconciling once they lapse; reconcile is a no-op otherwise
		reconcile(player);
		PunisherAmmoReserve.tickRegen(player);
	}

	/** Bring the temporary-buff modifiers in line with the state. Safe to call any time. */
	public static void reconcile(ServerPlayer player) {
		// v0.15.18: Suppressive Fire is gone -- only ever removes its old slow
		setModifier(player, Attributes.MOVEMENT_SPEED, SUPPRESS_SLOW, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);

		PunisherArmorSet.reconcile(player);
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
			double amount, AttributeModifier.Operation op, boolean wanted) {
		AttributeInstance inst = player.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		AttributeModifier current = inst.getModifier(id);
		if (wanted) {
			if (current == null || current.amount() != amount || current.operation() != op) {
				inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
			}
		} else if (current != null) {
			inst.removeModifier(id);
		}
	}

	// ---------------- the firearm-engine seam ----------------

	/** Installed by {@link Punisher#initialize()}. Every method is a no-op for a non-Punisher. */
	public static final class Hooks implements FirearmHooks {
		@Override
		public boolean usesPersonalReserve(ServerPlayer player) {
			return Punisher.hasPower(player);
		}

		@Override
		public int personalReserveCount(ServerPlayer player, com.projecthero.mod.firearm.AmmoKind kind) {
			return PunisherAmmoReserve.count(player, kind);
		}

		@Override
		public int personalReserveTake(ServerPlayer player, com.projecthero.mod.firearm.AmmoKind kind, int want) {
			return PunisherAmmoReserve.take(player, kind, want);
		}

		@Override
		public float reloadSpeedFactor(ServerPlayer player) {
			if (!Punisher.hasPower(player)) {
				return 1.0f;
			}
			float f = PunisherConfig.RELOAD_FACTOR;
			// v0.13.11: Agent Venom's Living Ammunition -- the suit feeds the gun
			if (com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.agentVenom(player)) {
				f *= com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.RELOAD_FACTOR;
			}
			return f;
		}

		@Override
		public float spreadFactor(ServerPlayer player, boolean aiming) {
			if (!Punisher.hasPower(player)) {
				return 1.0f;
			}
			float f = aiming ? PunisherConfig.SPREAD_FACTOR_ADS : PunisherConfig.SPREAD_FACTOR_HIP;
			return f;
		}

		@Override
		public float recoilFactor(ServerPlayer player) {
			if (!Punisher.hasPower(player)) {
				return 1.0f;
			}
			float f = PunisherConfig.RECOIL_FACTOR;
			if (PunisherArmorSet.active(player)) {
				f *= PunisherArmorSet.SET_RECOIL_FACTOR;
			}
			return f;
		}

		@Override
		public float fireIntervalFactor(ServerPlayer player) {
			return 1.0f;
		}

		@Override
		public float damageFactor(ServerPlayer player, LivingEntity target, boolean headshot) {
			if (!Punisher.hasPower(player)) {
				return 1.0f;
			}
			float f = 1.0f;
			// v0.13.11: Agent Venom's Symbiote Rounds
			if (com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.agentVenom(player)) {
				f += com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.ROUNDS_DAMAGE_BONUS;
			}
			float hp = target.getMaxHealth() <= 0 ? 1f : target.getHealth() / target.getMaxHealth();
			if (hp < PunisherConfig.NO_MERCY_HEALTH_FRACTION && isHostile(target)) {
				f += Punisher.isBoss(target)
						? PunisherConfig.NO_MERCY_BONUS_BOSS : PunisherConfig.NO_MERCY_BONUS_NONBOSS;
			}
			return f;
		}

		@Override
		public void onHit(ServerPlayer player, LivingEntity target, boolean headshot) {
			if (com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.agentVenom(player)) {
				com.projecthero.mod.symbiote.SymbioteAgentVenomAbilities.onBulletHit(player, target);
			}
		}

		@Override
		public void onHeadshot(ServerPlayer player, LivingEntity target) {
			// Vigilante Training counts headshots BEFORE the player has the power -- that is how they
			// earn it. VigilanteTraining.onHeadshot is a no-op if training is not active.
			VigilanteTraining.onHeadshot(player);
		}

		@Override
		public void onFirearmKill(ServerPlayer player, LivingEntity target) {
			VigilanteTraining.onFirearmKill(player, target);
		}

		@Override
		public void onCrafted(ServerPlayer player, String weaponId) {
			if (Punisher.hasPower(player)) {
				Punisher.unlockWeapon(player, weaponId);
			}
			VigilanteTraining.onCraftFirearm(player);
		}

		private static boolean isHostile(LivingEntity e) {
			return e instanceof net.minecraft.world.entity.monster.Enemy;
		}
	}
}
