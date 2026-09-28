package com.projecthero.mod.hulk;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Where the Hulk touches the rest of the game (v0.13.11, Phase 1):
 * <ul>
 *   <li><b>Rage</b> -- every hit a Gamma player takes (and, as Banner, every hit he deals) feeds
 *       {@link Hulk#onHurt} / {@link Hulk#onDealt}.</li>
 *   <li><b>Fists only</b> -- the Hulk cannot use tools, weapons or bows: attacking with one, drawing a bow /
 *       crossbow / trident, and breaking blocks with a tool are all refused (bare hands and everything else
 *       -- food, blocks, potions -- still work). Firearms are refused in {@code ModNetworking}'s fire
 *       receiver, since firing is its own packet.</li>
 * </ul>
 */
public final class HulkDamage {
	private HulkDamage() {
	}

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HulkDamage::allowDamage);
		// v0.13.14: the Hulk refuses to die (every 3 minutes)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p && Hulk.tryDeathSave(p, source)));
		// v0.13.14: a squad-mate right-clicks the Hulk to climb onto his back (one rider at a time)
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer rider && entity instanceof ServerPlayer hulk
					&& HulkRiding.tryMount(rider, hulk)) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0f) {
				return;
			}
			if (entity instanceof ServerPlayer hurt && Hulk.hasPower(hurt)) {
				Hulk.onHurt(hurt, taken);
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && Hulk.hasPower(attacker)
					&& entity instanceof LivingEntity) {
				Hulk.onDealt(attacker, taken);
			}
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (Hulk.isHulk(player) && isForbidden(player.getMainHandItem())) {
				refuse(player);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (Hulk.isHulk(player) && isForbidden(stack)) {
				refuse(player);
				return InteractionResultHolder.fail(stack);
			}
			return InteractionResultHolder.pass(stack);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (Hulk.isHulk(player) && isForbidden(player.getMainHandItem())) {
				refuse(player);
				return false;
			}
			return true;
		});
	}

	/**
	 * v0.13.14: what hurts the Hulk. Falls, fire and arrows: nothing. Lava: a quarter. Explosions: half. Everything else
	 * in full (his armour, health and regeneration are the defence). ALLOW_DAMAGE is a veto, so a reduction cancels the
	 * hit and re-applies a smaller one behind a re-entrancy guard -- the pattern every power here uses.
	 */
	public static boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (REENTRANT.get() || !(entity instanceof ServerPlayer player) || !Hulk.isHulk(player) || amount <= 0.0f) {
			return true;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
			player.resetFallDistance();
			return false;
		}
		float factor;
		if (source.is(net.minecraft.world.damagesource.DamageTypes.LAVA)) {
			factor = HulkConfig.LAVA_FACTOR;
		} else if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
			player.clearFire();
			return false;
		} else if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow) {
			if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, player.getX(), player.getY() + player.getBbHeight() * 0.6,
						player.getZ(), 4, 0.3, 0.3, 0.3, 0.1);
			}
			return false; // arrows bounce off
		} else if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			factor = HulkConfig.EXPLOSION_FACTOR;
		} else {
			return true;
		}
		float reduced = amount * factor;
		if (reduced < 0.1f) {
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

	/** Tools, swords, axes, the mace, bows, crossbows, tridents and the mod's firearms. */
	public static boolean isForbidden(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		var item = stack.getItem();
		return item instanceof net.minecraft.world.item.TieredItem
				|| item instanceof net.minecraft.world.item.ProjectileWeaponItem
				|| item instanceof net.minecraft.world.item.TridentItem
				|| item instanceof net.minecraft.world.item.MaceItem
				|| item instanceof com.projecthero.mod.firearm.item.FirearmItem;
	}

	private static void refuse(Player player) {
		if (player instanceof ServerPlayer sp) {
			Hulk.say(sp, "message.projecthero.hulk.fists_only", ChatFormatting.GREEN);
		}
	}
}
