package com.projecthero.mod.gear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Tier IV: the Celestial Warhammer. A big, slow hammer (11 damage, 0.9 attacks a second). Hold right-click to
 * raise it and let go to slam the ground: everything within {@link #RADIUS} blocks takes {@link #MIN_DAMAGE}-
 * {@link #MAX_DAMAGE} (by charge) and is thrown back. Needs {@link #MIN_CHARGE} ticks of charge, full at
 * {@link #FULL_CHARGE}; {@link #COOLDOWN} ticks cooldown, 2 durability a slam. Never hits you, your pets or allies.
 */
public class CelestialWarhammerItem extends GearSwordItem {
	public static final int MIN_CHARGE = 12;
	public static final int FULL_CHARGE = 30;
	public static final double RADIUS = 5.0;
	public static final float MIN_DAMAGE = 8.0f;
	public static final float MAX_DAMAGE = 14.0f;
	public static final int COOLDOWN = 160;

	public CelestialWarhammerItem(Properties properties) {
		super(GearMaterials.CELESTIAL_TIER, 4, false, "item.projecthero.celestial_warhammer.hint", properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(player.getItemInHand(hand));
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.SPEAR;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		int charge = getUseDuration(stack, entity) - remaining;
		if (charge == FULL_CHARGE && !level.isClientSide) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.8f);
		}
		if (level.isClientSide && charge >= MIN_CHARGE && charge % 2 == 0) {
			Vec3 p = entity.getEyePosition().add(entity.getLookAngle().scale(0.6)).add(0, 0.6, 0);
			level.addParticle(charge >= FULL_CHARGE ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z,
					(level.random.nextDouble() - 0.5) * 0.1, 0.05, (level.random.nextDouble() - 0.5) * 0.1);
		}
	}

	@Override
	public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
		if (!(entity instanceof Player player)) {
			return;
		}
		int charge = getUseDuration(stack, entity) - remaining;
		if (charge < MIN_CHARGE || level.isClientSide) {
			return;
		}
		float strength = Math.min(1f, (charge - MIN_CHARGE) / (float) (FULL_CHARGE - MIN_CHARGE));
		slam(player, strength);
		player.getCooldowns().addCooldown(this, COOLDOWN);
		if (!player.getAbilities().instabuild) {
			stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
		}
	}

	/** The slam itself (server). {@code strength} 0..1 scales the damage. Returns how many entities it hit. */
	public static int slam(Player player, float strength) {
		Level level = player.level();
		Vec3 c = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2));
		float damage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * strength;
		int hit = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RADIUS, 2.5, RADIUS),
				e -> e != player && e.isAlive() && e.distanceToSqr(c) <= RADIUS * RADIUS + 4 && !(e instanceof ArmorStand))) {
			if (e.isAlliedTo(player) || (e instanceof OwnableEntity o && player.getUUID().equals(o.getOwnerUUID()))) {
				continue;
			}
			double falloff = 1.0 - 0.4 * Math.min(1.0, Math.sqrt(e.distanceToSqr(c)) / RADIUS); // full in the middle, 60% at the rim
			if (e.hurt(level.damageSources().playerAttack(player), (float) (damage * falloff))) {
				hit++;
			}
			Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
			push = push.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 0) : push.normalize().scale(0.9 + 0.5 * strength);
			e.push(push.x, 0.45 + 0.25 * strength, push.z);
			e.hurtMarked = true;
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.7f);
		if (level instanceof ServerLevel sl) {
			BlockPos ground = BlockPos.containing(c.x, player.getY() - 0.5, c.z);
			BlockState bs = sl.getBlockState(ground);
			for (int ring = 1; ring <= 5; ring++) {
				double r = RADIUS * ring / 5.0;
				int n = 10 + ring * 6;
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n;
					double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
					if (!bs.isAir()) {
						sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), x, player.getY() + 0.1, z, 2, 0.1, 0.05, 0.1, 0.15);
					}
					if (ring % 2 == 1) {
						sl.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 0.2, z, 1, 0, 0.1, 0, 0.06);
					}
				}
			}
			sl.sendParticles(ParticleTypes.EXPLOSION, c.x, player.getY() + 0.3, c.z, 1, 0, 0, 0, 0);
			sl.sendParticles(ParticleTypes.FLASH, c.x, player.getY() + 0.5, c.z, 1, 0, 0, 0, 0);
		}
		return hit;
	}
}
