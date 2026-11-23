package com.projecthero.mod.temple.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 artifact (Ancient Temple): <b>the Bound King's Ankh</b> -- the gold ankh Ankhatep was bound with. Carried
 * anywhere in your inventory, it refuses your death once: a killing blow leaves you on {@link #SAVE_HEALTH} health with
 * Regeneration II, and a burst of sand hurls every hostile within {@link #BURST_RADIUS} away ({@link #BURST_DAMAGE}).
 * Then it sleeps for {@link #DORMANT_TICKS} (ten minutes, kept on the item, so relogging doesn't wake it). Not against
 * the void or {@code /kill}. Not a power: no keys, no meter. Only the first ankh in the inventory counts.
 */
public class BoundKingsAnkhItem extends Item {
	public static final float SAVE_HEALTH = 8.0f;
	public static final double BURST_RADIUS = 5.0;
	public static final float BURST_DAMAGE = 6.0f;
	public static final int DORMANT_TICKS = 12000;
	private static final String READY_AT = "AnkhReadyAt";

	public BoundKingsAnkhItem(Properties props) {
		super(props);
	}

	public static long readyAt(ItemStack stack) {
		CustomData d = stack.get(DataComponents.CUSTOM_DATA);
		return d == null ? 0L : d.copyTag().getLong(READY_AT);
	}

	/** Called when {@code player} is about to die. Returns true when an ankh saved them (the death is cancelled). */
	public static boolean tryCheatDeath(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		long now = level.getGameTime();
		ItemStack ankh = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.getItem() instanceof BoundKingsAnkhItem) {
				ankh = s;
				break;
			}
		}
		if (ankh.isEmpty() || readyAt(ankh) > now) {
			return false;
		}
		CompoundTag tag = ankh.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putLong(READY_AT, now + DORMANT_TICKS);
		ankh.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		player.getCooldowns().addCooldown(ankh.getItem(), DORMANT_TICKS);
		player.setHealth(SAVE_HEALTH);
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1));
		Vec3 c = player.position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), c.x, c.y + 0.6, c.z, 120,
				BURST_RADIUS * 0.4, 0.5, BURST_RADIUS * 0.4, 0.4);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1.0, c.z, 30, 0.5, 0.8, 0.5, 0.3);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BURST_RADIUS),
				e -> e != player && e.isAlive() && e instanceof Enemy && e.distanceToSqr(player) <= BURST_RADIUS * BURST_RADIUS)) {
			e.hurt(level.damageSources().playerAttack(player), BURST_DAMAGE);
			Vec3 push = e.position().subtract(c).multiply(1, 0, 1).normalize().scale(1.4);
			e.push(push.x, 0.45, push.z);
			e.hurtMarked = true;
		}
		player.displayClientMessage(Component.translatable("item.projecthero.bound_kings_ankh.saved").withStyle(s -> s.withColor(0xE8C060)), true);
		return true;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("item.projecthero.bound_kings_ankh.hint").withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("item.projecthero.bound_kings_ankh.note").withStyle(ChatFormatting.DARK_GRAY));
	}
}
