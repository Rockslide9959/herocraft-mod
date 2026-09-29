package com.projecthero.mod.darkseid.item;

import java.util.List;

import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.darkseid.entity.BoomTubeEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Mother Box of your own -- the very rare raid reward. Sneak-use to attune it to where you stand (any dimension);
 * use it to open a Boom Tube and step through to that spot. Two-minute recharge. Also a crafting result (Omega Core
 * + Omega Shards), so it is attainable without luck, just slowly.
 *
 * <p>The anchor lives in the stack's own {@code minecraft:custom_data} -- no new component type for three numbers.
 */
public class MotherBoxItem extends Item {
	public static final int COOLDOWN_TICKS = 120 * 20;

	public MotherBoxItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(held);
		}
		if (sp.isShiftKeyDown()) {
			CompoundTag tag = new CompoundTag();
			tag.putString("AnchorDim", server.dimension().location().toString());
			tag.putDouble("AnchorX", sp.getX());
			tag.putDouble("AnchorY", sp.getY());
			tag.putDouble("AnchorZ", sp.getZ());
			CustomData.set(DataComponents.CUSTOM_DATA, held, tag);
			server.playSound(null, sp.blockPosition(), DarkseidSounds.MOTHER_BOX_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
			sp.displayClientMessage(Component.translatable("message.projecthero.mother_box.attuned").withStyle(ChatFormatting.AQUA), true);
			return InteractionResultHolder.success(held);
		}
		CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (!tag.contains("AnchorDim")) {
			sp.displayClientMessage(Component.translatable("message.projecthero.mother_box.unattuned").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(held);
		}
		ResourceLocation dimId = ResourceLocation.tryParse(tag.getString("AnchorDim"));
		ServerLevel dest = dimId == null ? null : server.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
		if (dest == null) {
			sp.displayClientMessage(Component.translatable("message.projecthero.mother_box.lost").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(held);
		}
		Vec3 to = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
		BoomTubeEntity.open(server, sp.position().add(0, 1, 0), BoomTubeEntity.Kind.MELEE, 2.6f, 30, null);
		sp.teleportTo(dest, to.x, to.y, to.z, sp.getYRot(), sp.getXRot());
		sp.fallDistance = 0.0f;
		BoomTubeEntity.open(dest, to.add(0, 1, 0), BoomTubeEntity.Kind.MELEE, 2.6f, 30, null);
		sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		return InteractionResultHolder.consume(held);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mother_box.hint").withStyle(ChatFormatting.GRAY));
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		if (tag.contains("AnchorDim")) {
			tooltip.add(Component.translatable("item.projecthero.mother_box.anchor", (int) Math.floor(tag.getDouble("AnchorX")),
					(int) Math.floor(tag.getDouble("AnchorY")), (int) Math.floor(tag.getDouble("AnchorZ")), tag.getString("AnchorDim"))
					.withStyle(ChatFormatting.AQUA));
		}
	}
}
