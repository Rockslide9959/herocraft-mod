package com.herocraft.mod.grave;

import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.entity.AcidGlobEntity;
import com.herocraft.mod.event.entity.AcidZombie;
import com.herocraft.mod.event.entity.JuggernautZombie;
import com.herocraft.mod.event.raid.ZombieRaidNetworking;
import com.herocraft.mod.event.raid.ZombieRaidRewards;
import com.herocraft.mod.grave.item.GraveComponents;
import com.herocraft.mod.grave.item.GravekeeperShieldItem;
import com.herocraft.mod.grave.item.GraveItems;
import com.herocraft.mod.grave.item.NecroticBladeItem;
import com.herocraft.mod.grave.item.UndyingTotemItem;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every hook the Zombie Raid needs on existing gameplay, in one place: curse removal, the artifacts,
 * the raid-specific combat rules, and per-player bookkeeping.
 *
 * <p>Each listener is written to bail out in one or two comparisons for the overwhelmingly common
 * case -- a player with no curse, holding nothing raid-related, being hit by something ordinary --
 * so having these registered globally costs effectively nothing outside a raid.
 */
public final class GraveboundEvents {
	/** Re-entrancy guard for the damage listener, matching {@code HeroDamageRules}' approach. */
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private GraveboundEvents() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GraveboundEvents::onAllowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(GraveboundEvents::onAllowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(GraveboundEvents::onAfterDeath);
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			// The curse attachment is copyOnDeath, so it carries itself over. This only clears any
			// leftover dark-purple raid-sky tint the respawning client might still be showing.
			ZombieRaidNetworking.clearFor(newPlayer);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ZombieRaidNetworking.forget(handler.getPlayer().getUUID()));
	}

	// ---------------- per-player tick ----------------

	/**
	 * Called once per online player per server tick, from the mod's existing tick loop. Everything
	 * here is gated so that a player with no curse and no charm does almost nothing.
	 */
	public static void serverTick(ServerPlayer player) {
		GraveboundCurse.tick(player);
		tickGravewalkerCharm(player);
	}

	// ---------------- curse removal ----------------

	/**
	 * The one and only survival way to break the curse. Hooked on the food being finished rather than
	 * on the item being used, so a cancelled or interrupted eat does not consume the curse.
	 *
	 * <p>Called from {@code LivingEntityEatMixin}.
	 */
	public static void onFinishedEating(ServerPlayer player, ItemStack stack) {
		if (!stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return;
		}
		GraveboundCurse.clear(player, true);
	}

	// ---------------- artifacts ----------------

	/**
	 * Gravewalker Charm. Fires when the player <em>crosses</em> the health threshold, not while they
	 * sit below it: the charm records an absolute ready-at game time on the stack, and the trigger
	 * additionally requires that the player was above the threshold recently. In practice the cooldown
	 * alone is enough -- a two-minute lockout cannot fire every tick -- but the crossing check is what
	 * stops it burning its cooldown the instant it is picked up by an already-hurt player.
	 */
	private static void tickGravewalkerCharm(ServerPlayer player) {
		if (player.tickCount % 10 != 0) {
			return;
		}
		EventConfig.ZombieRaid cfg = EventConfig.raid();
		float fraction = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
		if (fraction > cfg.gravewalkerThreshold || fraction <= 0.0f) {
			return;
		}
		ItemStack charm = findCharm(player);
		if (charm == null) {
			return;
		}
		long now = player.level().getGameTime();
		Long readyAt = charm.get(GraveComponents.CHARM_READY_AT);
		if (readyAt != null && now < readyAt) {
			return;
		}
		charm.set(GraveComponents.CHARM_READY_AT, now + cfg.gravewalkerCooldownTicks);
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, cfg.gravewalkerEffectTicks, 0));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, cfg.gravewalkerEffectTicks, 0));
		player.displayClientMessage(Component.translatable("message.herocraft.charm.triggered")
				.withStyle(ChatFormatting.GOLD), true);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
					20, 0.4, 0.6, 0.4, 0.03);
		}
	}

	private static ItemStack findCharm(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(GraveItems.GRAVEWALKER_CHARM)) {
				return stack;
			}
		}
		return null;
	}

	/** Undying Totem: like a vanilla totem, but it takes three deaths to spend. */
	private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		ItemStack totem = null;
		for (var hand : net.minecraft.world.InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(GraveItems.UNDYING_TOTEM)) {
				totem = held;
				break;
			}
		}
		if (totem == null || !UndyingTotemItem.consumeCharge(totem)) {
			return true;
		}

		player.setHealth(1.0f);
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
		player.level().broadcastEntityEvent(player, (byte) 35); // vanilla totem animation
		player.displayClientMessage(Component.translatable("message.herocraft.undying_totem.used",
				UndyingTotemItem.charges(totem)).withStyle(ChatFormatting.GOLD), true);
		return false;
	}

	// ---------------- combat rules ----------------

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get()) {
			return true;
		}
		if (entity instanceof ServerPlayer player) {
			return playerTakingDamage(player, source, amount);
		}
		if (source.getEntity() instanceof ServerPlayer attacker
				&& attacker.getMainHandItem().is(GraveItems.NECROTIC_BLADE)) {
			return necroticBladeHit(attacker, entity, source, amount);
		}
		return true;
	}

	/**
	 * The Gravekeeper Shield's specialisation. It reduces damage only from undead attackers and from
	 * Acid Zombie attacks -- nothing else -- so it never becomes a general-purpose upgrade.
	 * Reduction is done the same way {@code HeroDamageRules} does it: cancel the hit and re-apply a
	 * smaller one behind a re-entrancy guard, because Fabric's ALLOW_DAMAGE is a veto with no
	 * "reduce" option.
	 */
	private static boolean playerTakingDamage(ServerPlayer player, DamageSource source, float amount) {
		ItemStack shield = shieldFor(player);
		if (shield == null) {
			return true;
		}
		float factor = 1.0f;
		var attacker = source.getEntity();
		var direct = source.getDirectEntity();

		if (direct instanceof AcidGlobEntity || attacker instanceof AcidZombie
				|| source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC) && attacker instanceof AcidZombie) {
			factor = 1.0f - GravekeeperShieldItem.ACID_REDUCTION;
		} else if (attacker instanceof LivingEntity living && living.getType().is(EntityTypeTags.UNDEAD)) {
			factor = 1.0f - GravekeeperShieldItem.UNDEAD_REDUCTION;
		}

		if (factor >= 1.0f) {
			return true;
		}
		float reduced = amount * factor;
		if (reduced < 0.5f) {
			return false;
		}
		REENTRANT.set(true);
		try {
			player.hurt(source, reduced);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}

	private static ItemStack shieldFor(ServerPlayer player) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND }) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.getItem() instanceof GravekeeperShieldItem) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * Juggernaut charge knockback resistance. The charge applies its push directly rather than through
	 * the knockback attribute, so the shield's resistance has to be applied where the push happens --
	 * this is called from {@link JuggernautZombie}'s impact code.
	 *
	 * @return the multiplier to apply to the charge knockback for this target
	 */
	public static double chargeKnockbackFactor(net.minecraft.world.entity.Entity target) {
		if (target instanceof ServerPlayer player && shieldFor(player) != null) {
			return 1.0 - GravekeeperShieldItem.CHARGE_KNOCKBACK_REDUCTION;
		}
		return 1.0;
	}

	// ---------------- deaths ----------------

	private static void onAfterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof ServerPlayer player) {
			ZombieRaidRewards.onPlayerDied(player);
			return;
		}
		// Necrotic Blade: killing an undead builds its temporary damage bonus.
		if (source.getEntity() instanceof ServerPlayer killer && entity.getType().is(EntityTypeTags.UNDEAD)) {
			ItemStack weapon = killer.getMainHandItem();
			if (weapon.is(GraveItems.NECROTIC_BLADE)) {
				NecroticBladeItem.addStack(weapon, level.getGameTime());
			}
		}
		ZombieRaidRewards.onEntityDied(entity, level);
		com.herocraft.mod.event.raid.SupervillainRaidRewards.onEntityDied(entity, level);
	}

	/**
	 * One Necrotic Blade hit: the Wither proc, plus the temporary damage bonus from its current
	 * undead-kill stacks.
	 *
	 * <p>The bonus is applied by cancelling the hit and re-issuing a larger one behind the re-entrancy
	 * guard -- the same technique {@code HeroDamageRules} uses to reduce damage, run in the other
	 * direction. Doing it here rather than through an attribute modifier means the bonus tracks the
	 * stack count on the exact stack that was swung, and expires with it, instead of needing a modifier
	 * added and removed as stacks come and go.
	 */
	private static boolean necroticBladeHit(ServerPlayer attacker, LivingEntity target, DamageSource source,
			float amount) {
		NecroticBladeItem.maybeWither(target, attacker.getRandom());
		float bonus = NecroticBladeItem.bonusDamage(attacker.getMainHandItem(), attacker.level().getGameTime());
		if (bonus <= 0.0f) {
			return true;
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount + bonus);
		} finally {
			REENTRANT.set(false);
		}
		return false;
	}
}
