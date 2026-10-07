package com.projecthero.mod.ultron.item;

import java.util.List;

import com.projecthero.mod.ultron.MindStoneCharm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
 * v0.15.12: the <b>Mind Stone</b> -- torn out of Ultron's core; guaranteed on a fighter's first Ultron clear. The first
 * piece of the planned Infinity-stone chain, with a small power for now (user call):
 * <ul>
 *   <li><b>In the off hand</b>: Night Vision, and hostile mobs within 24 blocks are outlined gold -- in the holder's own
 *       view only (client-side, like every other mob highlight in the mod).</li>
 *   <li><b>Right-click</b> a mob you look at (24 blocks): it is charmed for 10 s -- it stops hunting you and turns on the
 *       hostiles round it, then reverts. 30 s cooldown. Bosses (100+ health) shrug it off.</li>
 * </ul>
 */
public class MindStoneItem extends Item {
	public static final int COOLDOWN_TICKS = 600;
	public static final int CHARM_TICKS = 200;
	public static final double RANGE = 24.0;
	public static final float MAX_CHARM_HEALTH = 100f;

	public MindStoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer p) || p.tickCount % 20 != 0) {
			return;
		}
		if (p.getOffhandItem() == stack) {
			MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
			if (nv == null || nv.getDuration() < 260) {
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, true));
			}
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		Mob mob = lookedAt(sp);
		if (mob == null) {
			sp.displayClientMessage(Component.translatable("item.projecthero.mind_stone.no_target").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		if (mob.getMaxHealth() >= MAX_CHARM_HEALTH) {
			sp.displayClientMessage(Component.translatable("item.projecthero.mind_stone.resisted", mob.getDisplayName())
					.withStyle(ChatFormatting.GOLD), true);
			server.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.5f);
			sp.getCooldowns().addCooldown(this, 60);
			return InteractionResultHolder.fail(stack);
		}
		MindStoneCharm.charm(server, mob, sp, CHARM_TICKS);
		sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		sp.displayClientMessage(Component.translatable("item.projecthero.mind_stone.charmed", mob.getDisplayName())
				.withStyle(ChatFormatting.YELLOW), true);
		return InteractionResultHolder.consume(stack);
	}

	/** The mob under the crosshair within {@link #RANGE} (blocks stop the look). */
	public static Mob lookedAt(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
		HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof Mob m && m.isAlive() && !e.isSpectator(), eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof Mob m ? m : null;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mind_stone.lore").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.projecthero.mind_stone.offhand").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.mind_stone.use").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.mind_stone.chain").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
