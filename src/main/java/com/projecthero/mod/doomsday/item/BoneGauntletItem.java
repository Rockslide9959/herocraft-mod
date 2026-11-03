package com.projecthero.mod.doomsday.item;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.doomsday.DoomsdayConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/**
 * v0.17.2: <b>Doomsday's Bone Gauntlet</b> -- a first-clear reward, a netherite-grade melee weapon grown from his bone.
 * Like him it <b>adapts</b>: every kind of mob it kills is remembered (the newest
 * {@link DoomsdayConfig.Rewards#gauntletMaxTypes}), and it hits each remembered kind for
 * {@link DoomsdayConfig.Rewards#gauntletBonusPerType} more.
 */
public class BoneGauntletItem extends SwordItem {
	private static final String KEY = "doomsday_adapted";

	public BoneGauntletItem(Tier tier, Properties properties) {
		super(tier, properties);
	}

	public static List<String> learned(ItemStack stack) {
		List<String> out = new ArrayList<>();
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return out;
		}
		ListTag list = data.copyTag().getList(KEY, Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getString(i));
		}
		return out;
	}

	public static void learn(ItemStack stack, EntityType<?> type) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
		List<String> have = learned(stack);
		if (have.contains(id)) {
			return;
		}
		have.add(id);
		int max = Math.max(1, DoomsdayConfig.rewards().gauntletMaxTypes);
		while (have.size() > max) {
			have.remove(0);
		}
		ListTag list = new ListTag();
		for (String s : have) {
			list.add(StringTag.valueOf(s));
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY, list));
	}

	@Override
	public float getAttackDamageBonus(Entity target, float baseDamage, DamageSource source) {
		ItemStack stack = source.getWeaponItem();
		if (stack == null || !(stack.getItem() instanceof BoneGauntletItem)) {
			return 0f;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
		return learned(stack).contains(id) ? (float) DoomsdayConfig.rewards().gauntletBonusPerType : 0f;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (!target.isAlive() || target.isDeadOrDying()) {
			learn(stack, target.getType());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.doomsday_bone_gauntlet.hint").withStyle(ChatFormatting.GRAY));
		for (String id : learned(stack)) {
			ResourceLocation rl = ResourceLocation.tryParse(id);
			EntityType<?> type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.get(rl);
			Component name = type == null ? Component.literal(id) : type.getDescription();
			tooltip.add(Component.translatable("item.projecthero.doomsday_bone_gauntlet.adapted", name,
					(int) DoomsdayConfig.rewards().gauntletBonusPerType).withStyle(ChatFormatting.DARK_RED));
		}
	}
}
