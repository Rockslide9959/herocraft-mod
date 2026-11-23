package com.projecthero.mod.hideout.worldgen;

import com.projecthero.mod.alarm.block.AlarmStyle;
import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: one faction's look and cast for the {@link CrawlWing} (the inner level, arena and vault every rebuilt hideout
 * gets below its old layout). Palette, guard kinds per job, the vent kind, tech (cameras + laser tripwires) or not
 * (string tripwires on the faction's alarm), the alarm block style, the mini-boss, and decoration hooks.
 */
public final class CrawlTheme {
	public BlockState wall;
	public BlockState floor;
	public BlockState ceiling;
	public BlockState trim;
	public BlockState pillar;
	public BlockState light;
	public BlockState accent;
	public Block stairs;
	public HideoutVentBlock.Kind vent;
	public boolean tech = true;
	public AlarmStyle style = AlarmStyle.TECH;
	public HideoutGuards.Role grunt;
	public HideoutGuards.Role elite;
	public HideoutGuards.Role ranged;
	public HideoutGuards.Role shield;
	public HideoutGuards.Role captain;
	public HideoutGuards.Role runner;
	public HideoutGuards.Role sniper;
	public EntityType<? extends Mob> boss;
	public Decor decor = new Decor() {
	};

	/** Faction dressing for the wing's rooms (local coordinates; {@code y} is the floor block layer). */
	public interface Decor {
		/** The checkpoint room (x0..x1, z0..z1 interior). */
		default void checkpoint(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
		}

		/** The Captain's quarters (interior). */
		default void quarters(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
		}

		/** The arena (interior). */
		default void arena(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
		}

		/** The vault room (interior). */
		default void vault(HideoutCanvas c, int x0, int y, int z0, int x1, int z1) {
		}
	}

	@SuppressWarnings("unchecked")
	<T extends Mob & HideoutBoss> EntityType<T> bossType() {
		return (EntityType<T>) boss;
	}
}
