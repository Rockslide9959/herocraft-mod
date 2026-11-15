package com.projecthero.mod.vaultdungeon.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 artifact (The Vault): <b>Riot Capacitor</b> -- the power cell out of a riot warden's shock shield. Carried
 * anywhere in your inventory: when something within {@link #REACH} blocks hurts you, the capacitor discharges --
 * {@link #DAMAGE} to the attacker and to every other hostile within {@link #RADIUS} blocks of you, and a shove away.
 * Recharges in 15 s (an item cooldown shows it). Several capacitors don't stack: only the first one fires.
 */
public class RiotCapacitorItem extends Item {
	public static final double REACH = 6.0;
	public static final double RADIUS = 3.5;
	public static final float DAMAGE = 6.0f;
	public static final int COOLDOWN = 300;

	public RiotCapacitorItem(Properties props) {
		super(props);
	}

	/** A hit landed on {@code player}: discharge if a capacitor is carried and charged. True if it fired. */
	public static boolean onHurt(ServerPlayer player, DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player || !attacker.isAlive()
				|| attacker.distanceToSqr(player) > REACH * REACH) {
			return false;
		}
		ItemStack cap = first(player);
		if (cap.isEmpty() || player.getCooldowns().isOnCooldown(cap.getItem())) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		player.getCooldowns().addCooldown(cap.getItem(), COOLDOWN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.9f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 40, RADIUS * 0.4, 0.6, RADIUS * 0.4, 0.25);
		zap(level, player, attacker);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
				e -> e != attacker && e != player && e.isAlive() && e instanceof Enemy && e.distanceToSqr(player) <= RADIUS * RADIUS)) {
			zap(level, player, e);
		}
		return true;
	}

	private static void zap(ServerLevel level, Player player, LivingEntity e) {
		e.hurt(level.damageSources().playerAttack(player), DAMAGE);
		Vec3 out = e.position().subtract(player.position());
		Vec3 h = new Vec3(out.x, 0, out.z).lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
		e.push(h.x * 0.8, 0.3, h.z * 0.8);
		e.hurtMarked = true;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.1);
	}

	private static ItemStack first(Player player) {
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof RiotCapacitorItem) {
				return s;
			}
		}
		for (ItemStack s : player.getInventory().offhand) {
			if (s.getItem() instanceof RiotCapacitorItem) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.riot_capacitor.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.vault.artifact.from").withStyle(ChatFormatting.DARK_GRAY));
	}
}
