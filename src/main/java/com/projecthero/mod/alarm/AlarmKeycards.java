package com.projecthero.mod.alarm;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.BlastDoorBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the <b>Keycard</b> -- one per guarded place, carried by its Captain (dropped on death). Its custom data names
 * the zone ({@code ZoneId}) and the place's profile ({@code Place}); using it on a Blast Door of that zone unlocks and
 * opens the whole door, even during an Alert. Another place's card is refused. A card with no zone (creative) opens any
 * door (operators / testing).
 */
public final class AlarmKeycards {
	public static Item KEYCARD;

	private AlarmKeycards() {
	}

	public static void initialize() {
		KEYCARD = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("keycard")),
				new KeycardItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
	}

	public static boolean isKeycard(ItemStack stack) {
		return stack.is(KEYCARD);
	}

	/** A keycard for {@code zone}. */
	public static ItemStack create(AlarmZone zone) {
		ItemStack s = new ItemStack(KEYCARD);
		CompoundTag t = new CompoundTag();
		t.putLong("ZoneId", zone.id);
		t.putString("Place", zone.profileId == null ? "" : zone.profileId);
		BlockPos c = zone.box.getCenter();
		t.putInt("X", c.getX());
		t.putInt("Z", c.getZ());
		CustomData.set(DataComponents.CUSTOM_DATA, s, t);
		return s;
	}

	/** The zone id on a keycard, or null for a blank (master) card. */
	public static Long zoneOf(ItemStack stack) {
		CustomData d = stack.get(DataComponents.CUSTOM_DATA);
		if (d == null) {
			return null;
		}
		CompoundTag t = d.copyTag();
		return t.contains("ZoneId") ? t.getLong("ZoneId") : null;
	}

	/** Right-click a Blast Door with a keycard. */
	public static void useOnDoor(ServerLevel level, BlockPos pos, ItemStack card, ServerPlayer player) {
		AlarmZone z = AlarmZones.get(level).zoneAt(pos);
		Long id = zoneOf(card);
		if (id != null && (z == null || z.id != id)) {
			player.displayClientMessage(Component.translatable("item.projecthero.keycard.wrong").withStyle(ChatFormatting.RED), true);
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.VAULT_INSERT_ITEM_FAIL, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.8f);
			return;
		}
		List<BlockPos> door = BlastDoorBlock.connected(level, pos);
		boolean changed = false;
		for (BlockPos p : door) {
			BlockState s = level.getBlockState(p);
			if (s.getValue(BlastDoorBlock.LOCKED) || !s.getValue(BlastDoorBlock.OPEN)) {
				BlastDoorBlock.setOpen(level, p, s.setValue(BlastDoorBlock.LOCKED, false), true);
				changed = true;
			}
			if (z != null) {
				z.keyOpened.add(p.asLong());
			}
		}
		if (z != null) {
			AlarmZones.get(level).changed();
		}
		if (changed) {
			BlastDoorBlock.sound(level, pos, true);
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.6f);
			player.displayClientMessage(Component.translatable("item.projecthero.keycard.opened").withStyle(ChatFormatting.GREEN), true);
		}
	}

	/** Right-click a shut Blast Door with no keycard: say why it's shut. */
	public static void explainDoor(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		String key = state.getValue(BlastDoorBlock.LOCKED) ? "block.projecthero.blast_door.locked" : "block.projecthero.blast_door.sealed";
		AlarmZone z = AlarmZones.get(level).zoneAt(pos);
		AlarmProfile p = z == null ? null : z.profile();
		Component place = p == null ? Component.translatable("projecthero.alarm.place") : p.placeName();
		player.displayClientMessage(Component.translatable(key, place).withStyle(ChatFormatting.GRAY), true);
	}

	/** The Captain died: drop this place's keycard once. */
	public static void captainDied(ServerLevel level, Mob captain) {
		if (captain.getTags().contains(AlarmGuards.KEYCARD_DROPPED)) {
			return;
		}
		captain.addTag(AlarmGuards.KEYCARD_DROPPED);
		AlarmZone z = AlarmZones.find(level, captain.blockPosition());
		if (z == null) {
			// wandered out: the nearest zone within 32 blocks
			double best = 32 * 32;
			for (AlarmZone c : AlarmZones.get(level).all()) {
				double edge = c.aabb().distanceToSqr(captain.position());
				if (edge < best) {
					best = edge;
					z = c;
				}
			}
		}
		if (z == null) {
			return;
		}
		captain.spawnAtLocation(create(z), 0.5f);
		level.playSound(null, captain.blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1f, 1.2f);
	}

	/** Tooltip lines (the item class calls this). */
	static void tooltip(ItemStack stack, List<Component> lines, TooltipFlag flag) {
		CustomData d = stack.get(DataComponents.CUSTOM_DATA);
		if (d == null || !d.copyTag().contains("ZoneId")) {
			lines.add(Component.translatable("item.projecthero.keycard.blank").withStyle(ChatFormatting.GRAY));
			return;
		}
		CompoundTag t = d.copyTag();
		AlarmProfile p = AlarmProfiles.get(t.getString("Place"));
		Component place = p == null ? Component.translatable("projecthero.alarm.place") : p.placeName();
		lines.add(Component.translatable("item.projecthero.keycard.place", place).withStyle(s -> s.withColor(p == null ? 0xAAAAAA : p.colour())));
		lines.add(Component.translatable("item.projecthero.keycard.near", t.getInt("X"), t.getInt("Z")).withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("item.projecthero.keycard.use").withStyle(ChatFormatting.GRAY));
	}

	/** The keycard item. */
	public static final class KeycardItem extends Item {
		public KeycardItem(Properties props) {
			super(props);
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
			tooltip(stack, lines, flag);
		}
	}
}
