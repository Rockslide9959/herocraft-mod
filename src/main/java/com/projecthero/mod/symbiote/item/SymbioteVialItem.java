package com.projecthero.mod.symbiote.item;

import java.util.List;

import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteBonding;
import com.projecthero.mod.symbiote.SymbioteSounds;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Symbiote Vial (v0.11.15) -- a way to move a Symbiote around instead of being stuck with it.
 *
 * <ul>
 *   <li><b>Empty vial + sneak + use</b> -- cuts the bonded Symbiote out of the host and bottles it (the
 *       vial becomes a filled one).</li>
 *   <li><b>Empty vial + right-click a free Symbiote</b> -- bottles that Symbiote instead
 *       ({@link #capture}).</li>
 *   <li><b>Filled vial + use</b> -- releases it into the user: the normal bond flow, then an empty
 *       vial is handed back.</li>
 * </ul>
 */
public class SymbioteVialItem extends Item {
	private final boolean filled;

	public SymbioteVialItem(Properties properties, boolean filled) {
		super(properties);
		this.filled = filled;
	}

	public boolean isFilled() {
		return filled;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (filled) {
			if (SymbioteBonding.attemptFromVial(sp)) {
				swapVial(sp, hand, held, new ItemStack(SymbioteHostItems.SYMBIOTE_VIAL));
				return InteractionResultHolder.consume(player.getItemInHand(hand));
			}
			return InteractionResultHolder.fail(held);
		}

		// Empty vial.
		if (!sp.isShiftKeyDown()) {
			sp.displayClientMessage(Component.translatable("message.projecthero.symbiote_vial.hint"), true);
			return InteractionResultHolder.fail(held);
		}
		if (!Symbiote.hasSymbiote(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.symbiote_vial.no_bond"), true);
			return InteractionResultHolder.fail(held);
		}
		Symbiote.remove(sp);
		fx(sp);
		swapVial(sp, hand, held, new ItemStack(SymbioteHostItems.SYMBIOTE_VIAL_FILLED));
		sp.displayClientMessage(Component.translatable("message.projecthero.symbiote_vial.extracted")
				.withStyle(ChatFormatting.DARK_PURPLE), false);
		return InteractionResultHolder.consume(sp.getItemInHand(hand));
	}

	/** An empty vial held to a free Symbiote bottles it. Called from {@link SymbioteEntity#interact}. */
	public static void capture(ServerPlayer player, SymbioteEntity symbiote) {
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof SymbioteVialItem vial) || vial.filled) {
			return;
		}
		fx(player, symbiote.getX(), symbiote.getY() + 0.5, symbiote.getZ());
		symbiote.discard();
		swapVial(player, InteractionHand.MAIN_HAND, held, new ItemStack(SymbioteHostItems.SYMBIOTE_VIAL_FILLED));
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote_vial.captured")
				.withStyle(ChatFormatting.DARK_PURPLE), true);
	}

	/** Turn one vial of the held stack into {@code result}: replace it if alone, else split one off. */
	private static void swapVial(ServerPlayer player, InteractionHand hand, ItemStack held, ItemStack result) {
		if (held.getCount() <= 1) {
			player.setItemInHand(hand, result);
			return;
		}
		held.shrink(1);
		if (!player.getInventory().add(result)) {
			player.drop(result, false);
		}
	}

	private static void fx(ServerPlayer player) {
		fx(player, player.getX(), player.getY() + 1.0, player.getZ());
	}

	private static void fx(ServerPlayer player, double x, double y, double z) {
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 50, 0.35, 0.6, 0.35, 0.05);
		SymbioteSounds.organic(level, x, y, z, 1.0f, 0.6f);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return filled;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String base = filled ? "item.projecthero.symbiote_vial_filled" : "item.projecthero.symbiote_vial";
		tooltip.add(Component.translatable(base + ".desc1").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable(base + ".desc2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
