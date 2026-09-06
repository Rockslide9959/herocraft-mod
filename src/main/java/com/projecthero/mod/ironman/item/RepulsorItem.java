package com.projecthero.mod.ironman.item;

import com.projecthero.mod.ironman.RepulsorBoots;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.Holder;

import java.util.List;

/**
 * The Repulsor ("changes 22"). Still the Stark Fabricator component every suit chestplate and boot is
 * built out of -- the recipes, the stack size and the item id are all unchanged -- but a single one is
 * now a working piece of hardware in its own right:
 *
 * <ul>
 *   <li><b>Wearable.</b> It goes in the <em>boots</em> slot, and wearing it grants repulsor flight at
 *       half the Mark 2's speed (see {@link RepulsorBoots}). It is an {@link ArmorItem} purely so
 *       vanilla will let it into that slot; its material has an empty layer list, so nothing is drawn
 *       over the player's legs, and it gives a single point of armour.</li>
 *   <li><b>Fireable.</b> Right-click and hold for {@value #WINDUP_TICKS} ticks and it fires the Mark
 *       2's repulsor blast -- literally the same {@code fireRepulsor} call, so same damage, range,
 *       beam and knockback.</li>
 * </ul>
 *
 * <h2>Why a held windup rather than an instant shot</h2>
 * The Mark 2's repulsor is defined by its forced one-second spin-up ({@code repulsorWindup(20)}) --
 * it is the crude, slow-firing prototype, and firing it instantly from an item would make the loose
 * component strictly better than the suit it is a part of. Vanilla's use-item hold maps onto that
 * gesture exactly: hold to spin up (sparks + a rising beacon hum), release early and nothing happens.
 *
 * <h2>Deliberately not durability-limited</h2>
 * Giving it durability would set its max stack size to 1, and it is consumed two and four at a time by
 * the armour recipes -- an unstackable Repulsor could not be fed to the Fabricator in the quantities
 * its own recipes ask for. Pacing comes from the spin-up plus a short {@link #COOLDOWN_TICKS} item
 * cooldown instead.
 */
public class RepulsorItem extends ArmorItem {
	/** Matches the Mark 2's {@code repulsorWindup(20)} -- a forced one-second spin-up. */
	public static final int WINDUP_TICKS = 20;
	/** Matches the Mark 2's post-shot ability cooldown. */
	public static final int COOLDOWN_TICKS = 8;

	public RepulsorItem(Holder<ArmorMaterial> material, Properties properties) {
		super(material, ArmorItem.Type.BOOTS, properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(this)) {
			return InteractionResultHolder.fail(stack);
		}
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		// Long enough that the hold is never cut short by vanilla; onUseTick fires the shot and stops.
		return 72000;
	}

	/**
	 * Spin-up feedback, then the shot. Mirrors {@code IronManAbilities.tickRepulsorWindup}: sparks at
	 * the palm every tick, a rising hum, and at {@link #WINDUP_TICKS} the blast fires and the hold ends.
	 */
	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
		if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		int held = getUseDuration(stack, entity) - remainingUseTicks;

		Vec3 look = player.getLookAngle();
		Vec3 muzzle = player.getEyePosition().add(look.scale(0.6)).add(0, -0.35, 0);
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z,
				Math.min(6, 1 + held / 4), 0.06, 0.06, 0.06, 0.02);
		if (held % 6 == 0) {
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 0.8f + Math.min(1.0f, held / 20f));
		}

		if (held >= WINDUP_TICKS) {
			player.stopUsingItem();
			IronManAbilities.fireHandRepulsor(player);
			player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.repulsor.tooltip.flight",
				Math.round(RepulsorBoots.FLIGHT_SPEED * 100), IronManSuits.MARK_2.markNumber())
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.repulsor.tooltip.blast")
				.withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
	}
}
