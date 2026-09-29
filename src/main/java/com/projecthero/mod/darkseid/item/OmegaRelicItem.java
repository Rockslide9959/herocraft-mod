package com.projecthero.mod.darkseid.item;

import java.util.List;

import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.darkseid.entity.OmegaBeamEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
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
 * Darkseid's Omega Relic: a fragment of the Omega Effect, extremely rare. It is <em>not</em> Darkseid's power set --
 * it holds {@link #CHARGES} uses. Each use fires a pair of (weaker) homing Omega Beams at whatever you are looking at,
 * with an 8-second recharge; the last charge crumbles it. It cannot be enchanted (so no Mending loop) or repaired.
 */
public class OmegaRelicItem extends Item {
	public static final int CHARGES = 24;
	public static final float DAMAGE = 12.0f;
	public static final int COOLDOWN_TICKS = 8 * 20;
	private static final double RANGE = 48.0;

	public OmegaRelicItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		Vec3 eye = sp.getEyePosition();
		Vec3 look = sp.getLookAngle();
		Vec3 end = eye.add(look.scale(RANGE));
		HitResult block = server.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp, eye, end, new AABB(eye, end).inflate(1.5),
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != sp, RANGE * RANGE);
		LivingEntity target = hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
		Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
		for (int s = -1; s <= 1; s += 2) {
			Vec3 from = eye.add(look.scale(0.6)).add(side.scale(0.18 * s)).add(0, -0.05, 0);
			Vec3 dir = target == null ? look : look.add(side.scale(0.35 * s));
			OmegaBeamEntity.fire(server, sp, from, dir, target, DAMAGE, 0.9, 8.0, target == null ? 0 : 40);
		}
		server.playSound(null, sp.blockPosition(), DarkseidSounds.OMEGA_FIRE, SoundSource.PLAYERS, 1.2f, 1.3f);
		sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		held.hurtAndBreak(1, sp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		return InteractionResultHolder.consume(held);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.omega_relic.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.omega_relic.charges", stack.getMaxDamage() - stack.getDamageValue(), CHARGES)
				.withStyle(ChatFormatting.RED));
	}
}
