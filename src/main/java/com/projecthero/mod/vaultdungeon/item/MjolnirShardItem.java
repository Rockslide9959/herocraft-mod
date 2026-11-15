package com.projecthero.mod.vaultdungeon.item;

import java.util.List;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;

/**
 * v0.17.5 artifact (The Vault): <b>Cracked Mjolnir Shard</b> -- a sliver of an Asgardian hammer, kept in The Vault's
 * evidence locker. Hold use for {@link #CHARGE_TICKS} (it crackles), release: lightning strikes where you are looking
 * (up to {@link #RANGE} blocks): {@link #DAMAGE} to every non-player within {@link #RADIUS} blocks of the strike,
 * Slowness II 2 s. The bolt is for show only -- it lights no fires. 25 s cooldown; released early, nothing happens.
 */
public class MjolnirShardItem extends Item {
	public static final int CHARGE_TICKS = 20;
	public static final double RANGE = 32.0;
	public static final double RADIUS = 3.0;
	public static final float DAMAGE = 10.0f;
	public static final int COOLDOWN = 500;

	public MjolnirShardItem(Properties props) {
		super(props);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.SPEAR;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(player.getItemInHand(hand));
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		int held = getUseDuration(stack, user) - remaining;
		if (level instanceof ServerLevel server && held % 4 == 0) {
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, user.getX(), user.getY() + 1.4, user.getZ(), held >= CHARGE_TICKS ? 6 : 2, 0.3, 0.3, 0.3, 0.05);
			if (held == CHARGE_TICKS) {
				server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4f, 1.8f);
			}
		}
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
		int held = getUseDuration(stack, user) - remaining;
		if (held < CHARGE_TICKS || !(level instanceof ServerLevel server) || !(user instanceof Player player)) {
			return;
		}
		Vec3 at = aimPoint(server, player);
		strike(server, player, at);
		player.getCooldowns().addCooldown(this, COOLDOWN);
	}

	/** Where the look ray first hits a block or a living thing (or its end). */
	public static Vec3 aimPoint(ServerLevel level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult ent = ProjectileUtil.getEntityHitResult(level, player, eye, stop, new AABB(eye, stop).inflate(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && e != player, 0.3f);
		return ent != null ? ent.getEntity().position() : stop;
	}

	/** The strike at {@code at}: a show-only bolt plus our own damage. Returns how many were hit. */
	public static int strike(ServerLevel level, Player player, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 1.2f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 40, 1.2, 0.6, 1.2, 0.2);
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS, RADIUS + 1, RADIUS),
				e -> e.isAlive() && !(e instanceof Player) && e.position().distanceToSqr(at) <= (RADIUS + 0.5) * (RADIUS + 0.5))) {
			if (e.hurt(level.damageSources().playerAttack(player), DAMAGE)) {
				n++;
			}
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
		}
		return n;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.cracked_mjolnir_shard.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.vault.artifact.from").withStyle(ChatFormatting.DARK_GRAY));
	}
}
