package com.projecthero.mod.frostgiant.item;

import java.util.List;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantConfig;
import com.projecthero.mod.frostgiant.entity.IcePrisonEntity;
import com.projecthero.mod.frostgiant.entity.KingLaufeyEntity;
import com.projecthero.mod.frostgiant.entity.FrostGiantWarlordEntity;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the <b>Casket of Ancient Winters</b> -- Laufey's relic, handed to each fighter on their first ever clear
 * (never in the shared chest). An off-hand artifact:
 * <ul>
 *   <li>Passive, in the off hand: immune to freezing and powder snow, and you walk on powder snow ({@link FrostGear}).</li>
 *   <li>Right-click: <b>Winter's Breath</b> -- a 2 s frost cone, 4 damage every 5 ticks + Slowness III. 20 s cooldown.</li>
 *   <li>Sneak + right-click: <b>Ice Prison</b> on the mob you look at (5 s; bosses resist). 40 s cooldown.</li>
 *   <li>Worthy Thor players: <b>Jotunheim Storm</b> -- their lightning near a Casket holder adds freeze.</li>
 * </ul>
 * The two cooldowns live on the stack ({@link FrostGear#BREATH_READY} / {@link FrostGear#PRISON_READY}).
 */
public class CasketOfAncientWintersItem extends Item {
	public CasketOfAncientWintersItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) {
			return InteractionResultHolder.consume(stack);
		}
		FrostGiantConfig.Casket cfg = FrostGiantConfig.casket();
		long now = server.getGameTime();
		if (player.isShiftKeyDown()) {
			long ready = stack.getOrDefault(FrostGear.PRISON_READY, 0L);
			if (now < ready) {
				player.displayClientMessage(Component.translatable("message.projecthero.casket.cooldown",
						Component.translatable("message.projecthero.casket.prison"), (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
				return InteractionResultHolder.fail(stack);
			}
			LivingEntity target = lookedAt(server, player, cfg.prisonRange);
			if (target == null) {
				player.displayClientMessage(Component.translatable("message.projecthero.casket.no_target").withStyle(ChatFormatting.GRAY), true);
				return InteractionResultHolder.fail(stack);
			}
			if (target.getMaxHealth() >= cfg.bossHealth || target instanceof KingLaufeyEntity || target instanceof FrostGiantWarlordEntity) {
				player.displayClientMessage(Component.translatable("message.projecthero.casket.resists", target.getDisplayName())
						.withStyle(ChatFormatting.GRAY), true);
				stack.set(FrostGear.PRISON_READY, now + 60);
				return InteractionResultHolder.fail(stack);
			}
			IcePrisonEntity.encase(server, target, IcePrisonEntity.CASKET, cfg.prisonTicks, 6, null, false);
			stack.set(FrostGear.PRISON_READY, now + cfg.prisonCooldownTicks);
			return InteractionResultHolder.success(stack);
		}
		long ready = stack.getOrDefault(FrostGear.BREATH_READY, 0L);
		if (now < ready) {
			player.displayClientMessage(Component.translatable("message.projecthero.casket.cooldown",
					Component.translatable("message.projecthero.casket.breath"), (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		stack.set(FrostGear.BREATH_READY, now + cfg.breathCooldownTicks);
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8f, 1.6f);
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return FrostGiantConfig.casket().breathTicks;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BLOCK;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		FrostGiantConfig.Casket cfg = FrostGiantConfig.casket();
		Vec3 eye = user.getEyePosition().subtract(0, 0.3, 0);
		Vec3 dir = user.getLookAngle();
		if (remaining % 2 == 0) {
			for (double r = 1.0; r <= cfg.breathRange; r += 1.5) {
				Vec3 p = eye.add(dir.scale(r));
				double s = r * 0.18;
				server.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 2, s, s, s, 0.02);
				server.sendParticles(FrostFx.WHITE, p.x, p.y, p.z, 1, s, s, s, 0.0);
			}
		}
		if (remaining % 5 == 0) {
			for (LivingEntity e : FrostCombat.inCone(server, user, eye, dir, cfg.breathRange, 25,
					x -> x != user && x.isAlive() && !x.isSpectator() && !(x instanceof com.projecthero.mod.frostgiant.entity.RuneBrazierEntity))) {
				if (e == user || e instanceof Player p && !canHurt(user, p)) {
					continue;
				}
				e.invulnerableTime = 0;
				e.hurt(server.damageSources().indirectMagic(user, user), cfg.breathDamage);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
				FrostCombat.freeze(e, 30);
			}
		}
	}

	private static boolean canHurt(LivingEntity user, Player p) {
		return user instanceof Player up && up.canHarmPlayer(p);
	}

	private static LivingEntity lookedAt(ServerLevel level, Player player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = eye.add(player.getLookAngle().scale(range));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, to, new AABB(eye, to).inflate(1.5),
				e -> e instanceof Mob && e.isAlive() && !e.isSpectator(), 0.4f);
		return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.casket_of_ancient_winters.passive").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("item.projecthero.casket_of_ancient_winters.breath").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.casket_of_ancient_winters.prison").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.casket_of_ancient_winters.storm").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.casket_of_ancient_winters.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
