package com.projecthero.mod.hideout.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 artifact (Kingpin's Front): <b>Kingpin's Gilded Cane</b> -- Fisk's ivory-and-gold walking cane (not the cane
 * gun the Kingpin carries in a Syndicate Bust). A heavy club: 9 attack damage, a slow swing, and a lot of knockback.
 * <b>Cane Sweep</b> (use): a wide swing in front of you -- everything within 4 blocks in a 160-degree arc takes 8 and is
 * thrown back hard. 6 s cooldown. 900 uses.
 */
public class GildedCaneItem extends Item {
	public static final float SWEEP_DAMAGE = 8.0f;
	public static final double SWEEP_REACH = 4.0;
	public static final int SWEEP_COOLDOWN = 120;

	public GildedCaneItem(Properties props) {
		super(props.durability(900).attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 8.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(com.projecthero.mod.ProjectHeroMod.id("gilded_cane_knockback"), 1.5,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build()));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		player.swing(hand, true);
		if (level instanceof ServerLevel server) {
			sweep(server, player);
			stack.hurtAndBreak(2, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		player.getCooldowns().addCooldown(this, SWEEP_COOLDOWN);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** The sweep itself; returns how many it hit. */
	public static int sweep(ServerLevel level, Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.7f);
		for (int i = -4; i <= 4; i++) {
			double a = Math.toRadians(i * 20);
			Vec3 d = new Vec3(fwd.x * Math.cos(a) - fwd.z * Math.sin(a), 0, fwd.x * Math.sin(a) + fwd.z * Math.cos(a));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + d.x * 2.4, player.getY() + 1.0, player.getZ() + d.z * 2.4, 1, 0, 0, 0, 0);
		}
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SWEEP_REACH, 1.5, SWEEP_REACH),
				e -> e != player && e.isAlive() && !e.isAlliedTo(player))) {
			Vec3 to = e.position().subtract(player.position());
			Vec3 h = new Vec3(to.x, 0, to.z);
			if (h.lengthSqr() > SWEEP_REACH * SWEEP_REACH || h.lengthSqr() < 1.0e-4) {
				continue;
			}
			if (h.normalize().dot(fwd) < Math.cos(Math.toRadians(80))) {
				continue;
			}
			if (e.hurt(player.damageSources().playerAttack(player), SWEEP_DAMAGE)) {
				Vec3 push = h.normalize().scale(1.4);
				e.push(push.x, 0.45, push.z);
				e.hurtMarked = true;
				n++;
			}
		}
		return n;
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.gilded_cane.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.kingpins_front"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
