package com.projecthero.mod.spin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * v0.17.3 round 2: one of the Spin's two Steel Balls as an item he holds (see {@link SpinBalls}). It does nothing on its own: the
 * Spin's keys use it. A stale copy (replaced, respawned, or its owner lost the Spin) vanishes from any inventory it ticks in. Drawn as
 * the real 3D ball in the hand ({@code SteelBallItemRenderer}).
 */
public class SteelBallItem extends Item {
	public SteelBallItem(Properties props) {
		super(props);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
		if (level instanceof ServerLevel sl && holder.tickCount % 10 == 0 && SpinBalls.stale(sl.getServer(), stack)) {
			stack.setCount(0);
		}
	}
}
