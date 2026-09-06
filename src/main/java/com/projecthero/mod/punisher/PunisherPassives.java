package com.projecthero.mod.punisher;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.firearm.FirearmHooks;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 *   <li><b>Weapon Proficiency</b> -- a regenerating personal reserve (3 mags/gun), reduced recoil, faster weapon handling
 *       (all via {@link Hooks}).</li>
 *   <li><b>Faster Reloading</b> -- 15% quicker. Adrenaline's 25% <em>supersedes</em> this, it does
 *       not stack ({@link Hooks#reloadSpeedFactor}).</li>
 *   <li><b>Ballistic Expertise</b> -- slightly tighter spread.</li>
 *   <li><b>No Mercy</b> -- +25% firearm damage to a non-boss hostile below 15% health (+10% to a
 *       boss).</li>
 *   <li><b>Headshot Feedback</b> -- handled in {@link com.projecthero.mod.firearm.FirearmShooting} +
 *       {@link com.projecthero.mod.network.FirearmHeadshotPayload}.</li>
 * </ul>
 *
 * <p>The only attribute modifiers this class touches are the <em>temporary</em> ones from Adrenaline
 * and Suppressive Fire -- transient, fixed-id, reconciled on change (the {@code ThorPassives}
 * pattern), so nothing can leak across a relog / death / dimension change.
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
		tickAdrenalineCrash(player);
		PunisherAmmoReserve.tickRegen(player);

		// v0.9.5: the activation burst keeps coming off the player the whole time Adrenaline runs.
		if (Punisher.adrenalineActive(player) && player.tickCount % 5 == 0
				&& player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
					player.getX(), player.getY() + player.getBbHeight() * 0.65, player.getZ(),
					3, 0.35, 0.5, 0.35, 0.0);
		}
	}

	/**
	 * v0.9.4: the Adrenaline crash. The tick {@code adrenalineCrashAt} passes, the player takes Nausea I
	 * once for {@link PunisherConfig#ADRENALINE_CRASH_NAUSEA_TICKS}, and the marker is cleared.
	 */
	private static void tickAdrenalineCrash(ServerPlayer player) {
		long crashAt = Punisher.state(player).adrenalineCrashAt;
		if (crashAt == 0L || player.level().getGameTime() < crashAt) {
			return;
		}
		com.projecthero.mod.punisher.data.PunisherState c = Punisher.state(player).copy();
		c.adrenalineCrashAt = 0L;
		Punisher.save(player, c);
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
				PunisherConfig.ADRENALINE_CRASH_NAUSEA_TICKS, 0, false, true, true));
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				"message.projecthero.punisher.adrenaline_crash").withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					net.minecraft.sounds.SoundEvents.PLAYER_BREATH, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.7f);
		}
	}

	/** Bring the temporary-buff modifiers in line with the state. Safe to call any time. */
	public static void reconcile(ServerPlayer player) {
		boolean suppress = Punisher.suppressiveActive(player);

		setModifier(player, Attributes.MOVEMENT_SPEED, SUPPRESS_SLOW, -PunisherConfig.SUPPRESSIVE_SELF_SLOW,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, suppress);

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
			// Adrenaline supersedes the passive -- they do not compound.
			return Punisher.adrenalineActive(player)
					? PunisherConfig.ADRENALINE_RELOAD_FACTOR : PunisherConfig.RELOAD_FACTOR;
		}

		@Override
		public float spreadFactor(ServerPlayer player, boolean aiming) {
			if (!Punisher.hasPower(player)) {
				return 1.0f;
			}
			float f = aiming ? PunisherConfig.SPREAD_FACTOR_ADS : PunisherConfig.SPREAD_FACTOR_HIP;
			if (Punisher.suppressiveActive(player)) {
				f *= PunisherConfig.SUPPRESSIVE_SPREAD_FACTOR;
			}
			return f;
		}

		@Override
		public float recoilFactor(ServerPlayer player) {
			if (!Punisher.hasPower(player)) {
				return 1.0f;
			}
			float f = Punisher.suppressiveActive(player)
					? PunisherConfig.SUPPRESSIVE_RECOIL_FACTOR : PunisherConfig.RECOIL_FACTOR;
			if (PunisherArmorSet.active(player)) {
				f *= PunisherArmorSet.SET_RECOIL_FACTOR;
			}
			return f;
		}

		@Override
		public float fireIntervalFactor(ServerPlayer player) {
			return Punisher.hasPower(player) && Punisher.suppressiveActive(player)
					? PunisherConfig.SUPPRESSIVE_FIRE_RATE_FACTOR : 1.0f;
		}

		@Override
		public float damageFactor(ServerPlayer player, LivingEntity target, boolean headshot) {
			if (!Punisher.hasPower(player)) {
				return 1.0f;
			}
			float f = 1.0f;
			if (Punisher.adrenalineActive(player)) {
				f += PunisherConfig.ADRENALINE_DAMAGE_BONUS;
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
			if (Punisher.hasPower(player) && Punisher.suppressiveActive(player)) {
				target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
						PunisherConfig.SUPPRESSIVE_SLOW_TICKS, PunisherConfig.SUPPRESSIVE_SLOW_AMP, false, true, true));
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
