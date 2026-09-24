package com.projecthero.mod.wolverine;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The passive half of being Wolverine: standing stat modifiers (unique fixed ids, idempotent, removed
 * cleanly with the power), the healing factor (Super Regeneration's base heal scaled by health tier),
 * the emergency heal, and the timed Rage / Dash expiry. Every number lives in {@link WolverineConfig}.
 */
public final class WolverinePassives {
	private static final ResourceLocation SPEED = ProjectHeroMod.id("wolverine_speed");
	private static final ResourceLocation RAGE_SPEED = ProjectHeroMod.id("wolverine_rage_speed");
	private static final ResourceLocation JUMP = ProjectHeroMod.id("wolverine_jump");
	private static final ResourceLocation ATTACK = ProjectHeroMod.id("wolverine_attack");
	private static final ResourceLocation CLAW_ATTACK = ProjectHeroMod.id("wolverine_claw_attack");
	private static final ResourceLocation RAGE_ATTACK = ProjectHeroMod.id("wolverine_rage_attack");
	private static final ResourceLocation KNOCKBACK = ProjectHeroMod.id("wolverine_knockback");
	private static final ResourceLocation BURST_KNOCKBACK = ProjectHeroMod.id("wolverine_burst_knockback");
	private static final ResourceLocation MINING = ProjectHeroMod.id("wolverine_mining");
	private static final ResourceLocation CLAW_MINING = ProjectHeroMod.id("wolverine_claw_mining");

	private WolverinePassives() {
	}

	// ---------------- per-tick upkeep ----------------

	public static void tick(ServerPlayer player) {
		WolverineState s = Wolverine.state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		expireTimers(player, s, now);
		reconcile(player);

		healingFactor(player);
		tickEmergency(player, now);
		drainRage(player, now);
		clearHands(player);

		if (Wolverine.raging(player) && player.tickCount % 10 == 0 && player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + player.getBbHeight() * 0.85,
					player.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
			level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + player.getBbHeight() * 0.5,
					player.getZ(), 3, 0.35, 0.5, 0.35, 0.0);
		}
	}

	/**
	 * v0.12.16: nothing may be held while the claws are out. Any item in either hand is moved to a free
	 * inventory slot; with no room, the claws retract instead (so nothing is ever dropped or lost).
	 */
	private static void clearHands(ServerPlayer player) {
		if (!Wolverine.clawsOut(player)) {
			return;
		}
		net.minecraft.world.item.ItemStack main = player.getMainHandItem();
		net.minecraft.world.item.ItemStack off = player.getOffhandItem();
		if (main.isEmpty() && off.isEmpty()) {
			return;
		}
		boolean stowedAll = true;
		if (!main.isEmpty()) {
			int free = player.getInventory().getFreeSlot();
			if (free >= 0) {
				player.getInventory().setItem(free, main.copy());
				player.getInventory().setItem(player.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
			} else {
				stowedAll = false;
			}
		}
		if (!off.isEmpty()) {
			int free = player.getInventory().getFreeSlot();
			if (free >= 0) {
				player.getInventory().setItem(free, off.copy());
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
			} else {
				stowedAll = false;
			}
		}
		if (!stowedAll) {
			Wolverine.setClaws(player, false);
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.hands_full")
					.withStyle(ChatFormatting.GRAY), true);
		} else {
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.claws_no_items")
					.withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** The Rage bar bleeds off slowly once he has gone {@code RAGE_DRAIN_DELAY_TICKS} without a hit either way. */
	private static void drainRage(ServerPlayer player, long now) {
		WolverineState s = Wolverine.state(player);
		if (s.rageMeter <= 0.0f || Wolverine.raging(player)
				|| now - s.lastCombatAt < WolverineConfig.RAGE_DRAIN_DELAY_TICKS) {
			return;
		}
		// saved once a second -- the HUD does not need per-tick precision
		if (player.tickCount % 20 != 0) {
			return;
		}
		WolverineState c = s.copy();
		c.rageMeter = Math.max(0.0f, s.rageMeter - WolverineConfig.RAGE_DRAIN_PER_TICK * 20);
		Wolverine.save(player, c);
	}

	private static void expireTimers(ServerPlayer player, WolverineState s, long now) {
		boolean rageEnded = s.rageUntil != 0L && now >= s.rageUntil;
		boolean dashEnded = s.dashUntil != 0L && now >= s.dashUntil;
		boolean healEnded = s.emergencyHealUntil != 0L && now >= s.emergencyHealUntil;
		if (!rageEnded && !dashEnded && !healEnded) {
			return;
		}
		WolverineState c = s.copy();
		if (rageEnded) {
			c.rageUntil = 0L;
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.rage_ended")
					.withStyle(ChatFormatting.GRAY), true);
		}
		if (dashEnded) {
			c.dashUntil = 0L;
		}
		if (healEnded) {
			c.emergencyHealUntil = 0L;
			player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
			player.removeEffect(MobEffects.BLINDNESS);
			player.removeEffect(MobEffects.WEAKNESS);
		}
		Wolverine.save(player, c);
	}

	// ---------------- healing factor ----------------

	/** Super Regeneration's base heal at 1x / 2x / 3x by health tier (4 / 8 / 12 HP/s), doubled in Rage. */
	private static void healingFactor(ServerPlayer player) {
		float frac = player.getMaxHealth() <= 0 ? 1f : player.getHealth() / player.getMaxHealth();
		float mult = frac < WolverineConfig.CRITICAL_BELOW ? WolverineConfig.REGEN_MULT_CRITICAL
				: frac < WolverineConfig.INJURED_BELOW ? WolverineConfig.REGEN_MULT_INJURED
				: WolverineConfig.REGEN_MULT_NORMAL;
		if (Wolverine.raging(player)) {
			mult *= 1.0f + WolverineConfig.RAGE_REGEN_BONUS;
		}
		if (Wolverine.surgeRecovering(player)) {
			mult *= WolverineConfig.SURGE_REGEN_FACTOR; // still weak from the Death Surge: closer to dying
		}
		SuperRegenerationHandlers.tickBaseRegen(player, mult, true);
	}

	// ---------------- emergency heal ----------------

	/**
	 * Below {@link WolverineConfig#EMERGENCY_BELOW} of max health, restore 30% of max health over two
	 * seconds, then a 60 s internal cooldown. Called from the damage hooks and every tick.
	 *
	 * @return true if the resurrection started now
	 */
	public static boolean tryEmergency(ServerPlayer player) {
		WolverineState s = Wolverine.state(player);
		long now = player.level().getGameTime();
		if (!s.hasPower || now < s.emergencyReadyAt || s.emergencyHealUntil > now) {
			return false;
		}
		WolverineState c = s.copy();
		c.emergencyHealUntil = now + WolverineConfig.EMERGENCY_HEAL_TICKS;
		c.emergencyReadyAt = now + WolverineConfig.EMERGENCY_COOLDOWN_TICKS;
		c.fleshStartedAt = now;
		Wolverine.save(player, c);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_GROWL,
					SoundSource.PLAYERS, 0.8f, 1.3f);
			level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.6, 0.4, 0.0);
		}
		player.displayClientMessage(Component.translatable("message.projecthero.wolverine.emergency")
				.withStyle(ChatFormatting.RED), true);
		return true;
	}

	/** While the resurrection window runs: keep Slowness III, Blindness and Weakness I on him (re-applied because his debuff-halving would shorten them). */
	private static void tickEmergency(ServerPlayer player, long now) {
		WolverineState s = Wolverine.state(player);
		if (s.emergencyHealUntil > now && player.tickCount % 5 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 0, true, false, false));
		}
	}

	// ---------------- attribute modifiers ----------------

	/** Bring every modifier in line with the state. Idempotent; safe to call any time. */
	public static void reconcile(ServerPlayer player) {
		boolean power = Wolverine.hasPower(player);
		boolean claws = power && Wolverine.clawsOut(player);
		boolean unarmedClaws = claws && player.getMainHandItem().isEmpty();
		boolean rage = power && Wolverine.raging(player);
		boolean dash = power && Wolverine.dashing(player);

		set(player, Attributes.MOVEMENT_SPEED, SPEED, WolverineConfig.SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE, power);
		set(player, Attributes.MOVEMENT_SPEED, RAGE_SPEED, WolverineConfig.RAGE_SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE, rage);
		set(player, Attributes.JUMP_STRENGTH, JUMP, WolverineConfig.JUMP_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE, power);
		set(player, Attributes.ATTACK_DAMAGE, ATTACK, WolverineConfig.MELEE_BONUS_DAMAGE,
				AttributeModifier.Operation.ADD_VALUE, power);
		set(player, Attributes.ATTACK_DAMAGE, CLAW_ATTACK, WolverineConfig.CLAW_MELEE_BONUS,
				AttributeModifier.Operation.ADD_VALUE, unarmedClaws);
		set(player, Attributes.ATTACK_DAMAGE, RAGE_ATTACK, WolverineConfig.RAGE_DAMAGE_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, rage);
		set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, WolverineConfig.KNOCKBACK_RESISTANCE,
				AttributeModifier.Operation.ADD_VALUE, power);
		set(player, Attributes.KNOCKBACK_RESISTANCE, BURST_KNOCKBACK, WolverineConfig.KNOCKBACK_RESISTANCE_BONUS,
				AttributeModifier.Operation.ADD_VALUE, rage || dash);
		set(player, Attributes.BLOCK_BREAK_SPEED, MINING, WolverineConfig.STRENGTH_MINING_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE, power);
		set(player, Attributes.BLOCK_BREAK_SPEED, CLAW_MINING, WolverineConfig.CLAW_MINING_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE, claws);
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount,
			AttributeModifier.Operation op, boolean wanted) {
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
}
