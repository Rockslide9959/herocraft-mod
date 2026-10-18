package com.projecthero.mod.quirk.item;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import org.joml.Vector3f;

/**
 * v0.16.7: a Quirk Serum -- drink it and the Quirk awakens ({@link Quirks#grant}: one Primary power, it replaces whatever
 * you had). {@code quirk == null} is the Quirk Trigger: a random Quirk you don't have. Refused (and kept) when you
 * already have that Quirk or the unique-powers rule says someone else does.
 */
public class QuirkSerumItem extends Item {
	private final @Nullable Quirk quirk;

	public QuirkSerumItem(@Nullable Quirk quirk, Properties properties) {
		super(properties.stacksTo(16));
		this.quirk = quirk;
	}

	public @Nullable Quirk quirk() {
		return quirk;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 32;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (!(level instanceof ServerLevel server) || !(user instanceof ServerPlayer player)) {
			return stack;
		}
		Quirk q = drink(player, quirk, server.random);
		if (q == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.quirk.serum_nothing").withStyle(ChatFormatting.GRAY), true);
			return stack; // nothing happened -- keep the serum
		}
		int c = q.meterColour;
		server.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.4f),
				player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.7, 0.4, 0.02);
		server.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (stack.isEmpty()) {
				return bottle;
			}
			if (!player.getInventory().add(bottle)) {
				player.drop(bottle, false);
			}
		}
		return stack;
	}

	/**
	 * The serum's effect (tests call this directly): grants {@code quirk}, or for the Trigger ({@code null}) a random Quirk
	 * the player doesn't have. Returns the Quirk granted, or null if nothing changed.
	 */
	public static @Nullable Quirk drink(ServerPlayer player, @Nullable Quirk quirk, RandomSource random) {
		if (quirk != null) {
			return Quirks.grant(player, quirk) ? quirk : null;
		}
		List<Quirk> pool = new ArrayList<>(List.of(Quirk.values()));
		pool.remove(Quirks.quirk(player));
		while (!pool.isEmpty()) {
			Quirk pick = pool.remove(random.nextInt(pool.size()));
			if (Quirks.grant(player, pick)) {
				return pick;
			}
		}
		return null;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (quirk == null) {
			tooltip.add(Component.translatable("item.projecthero.quirk_serum_random.hint").withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.translatable("item.projecthero.quirk_serum.hint", Quirks.quirkName(quirk), Quirks.heroName(quirk))
					.withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("item.projecthero.quirk_serum.rule").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
