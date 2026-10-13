package com.projecthero.mod.galactus.item;

import java.util.List;

import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>the Power Cosmic</b> -- a shard of the Herald's power, the first-clear reward. Best in the off hand.
 * Right-click: a bolt of cosmic energy along your look (the first living thing within
 * {@link GalactusConfig.PowerCosmic#blastRange}, never your own pets). Sneak + right-click: a short shield (Resistance II +
 * Absorption II). Both on cooldowns.
 */
public class PowerCosmicItem extends Item {
	public PowerCosmicItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(this)) {
			return InteractionResultHolder.fail(stack);
		}
		GalactusConfig.PowerCosmic cfg = GalactusConfig.powerCosmic();
		if (!(level instanceof ServerLevel server)) {
			return InteractionResultHolder.success(stack);
		}
		if (player.isSecondaryUseActive()) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, cfg.shieldTicks, 1, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, cfg.shieldTicks, 1, false, true));
			GalactusFx.ring(server, GalactusFx.SILVER, player.position().add(0, 1, 0), 1.2, 18);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
			player.getCooldowns().addCooldown(this, cfg.shieldCooldownTicks);
			return InteractionResultHolder.consume(stack);
		}
		blast(server, player, cfg);
		player.getCooldowns().addCooldown(this, cfg.blastCooldownTicks);
		return InteractionResultHolder.consume(stack);
	}

	/** The bolt: the first valid target along the look ray (stopped by blocks). Returns who was hit, or null. */
	public static LivingEntity blast(ServerLevel level, Player player, GalactusConfig.PowerCosmic cfg) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(cfg.blastRange));
		HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && e != player && !isFriend(player, e), 0.3f);
		Vec3 to = hit != null ? hit.getLocation() : end;
		GalactusFx.line(level, GalactusFx.COSMIC_SMALL, eye.add(player.getLookAngle().scale(0.8)).add(0, -0.2, 0), to, 0.5);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.7f);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			target.hurt(level.damageSources().indirectMagic(player, player), cfg.blastDamage);
			return target;
		}
		return null;
	}

	private static boolean isFriend(Player player, Entity e) {
		return e instanceof TamableAnimal t && player.getUUID().equals(t.getOwnerUUID()) || e.isAlliedTo(player);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.power_cosmic.hint").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.power_cosmic.hint2").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
