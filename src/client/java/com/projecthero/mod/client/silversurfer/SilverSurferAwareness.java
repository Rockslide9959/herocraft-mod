package com.projecthero.mod.client.silversurfer;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.moonknight.MoonKnightScholarSightRenderer;
import com.projecthero.mod.network.MoonKnightScholarSightPayload;
import com.projecthero.mod.silversurfer.SilverSurfer;
import com.projecthero.mod.silversurfer.SilverSurferConfig;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.1: V Cosmic Awareness, entirely client-side and the Surfer's own (the v0.15.4 privacy rule): every creature within
 * {@link SilverSurferConfig#AWARENESS_RANGE} blocks glows silver through walls ({@code SilverSurferGlowMixin}) and the ores
 * around him are outlined through walls (Moon Knight's Scholar's Sight boxes, re-scanned every two seconds).
 */
public final class SilverSurferAwareness {
	public static final int SILVER = 0xE4EEFF;
	private static final int MAX_ORES = 96;
	private static boolean wasOn;

	private SilverSurferAwareness() {
	}

	/** Should {@code e} glow for the local player? */
	public static boolean glows(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (me == null || e == me || !(e instanceof LivingEntity) || !e.isAlive() || !SilverSurfer.awareness(me)) {
			return false;
		}
		double r = SilverSurferConfig.AWARENESS_RANGE;
		return e.distanceToSqr(me) <= r * r;
	}

	static void tick(Minecraft mc) {
		LocalPlayer me = mc.player;
		boolean on = me != null && mc.level != null && SilverSurfer.awareness(me);
		if (!on) {
			if (wasOn) {
				MoonKnightScholarSightRenderer.clear();
			}
			wasOn = false;
			return;
		}
		if (wasOn && me.tickCount % 40 != 0) {
			return;
		}
		wasOn = true;
		List<long[]> ores = new ArrayList<>();
		BlockPos c = me.blockPosition();
		int r = SilverSurferConfig.AWARENESS_ORE_RADIUS;
		int vr = 16;
		for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -vr, -r), c.offset(r, vr, r))) {
			BlockState st = mc.level.getBlockState(q);
			if (st.is(ConventionalBlockTags.ORES)) {
				ores.add(new long[] { q.asLong(), (long) c.distSqr(q), colour(st) });
			}
		}
		ores.sort((a, b) -> Long.compare(a[1], b[1]));
		int count = Math.min(MAX_ORES, ores.size());
		long[] positions = new long[count];
		int[] colours = new int[count];
		for (int i = 0; i < count; i++) {
			positions[i] = ores.get(i)[0];
			colours[i] = (int) ores.get(i)[2];
		}
		MoonKnightScholarSightRenderer.receive(new MoonKnightScholarSightPayload(positions, colours, 60));
	}

	private static int colour(BlockState st) {
		String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
		if (id.contains("diamond")) {
			return 0x5CF0F0;
		}
		if (id.contains("emerald")) {
			return 0x3CE070;
		}
		if (id.contains("gold")) {
			return 0xFFD040;
		}
		if (id.contains("redstone")) {
			return 0xFF3030;
		}
		if (id.contains("lapis")) {
			return 0x3050FF;
		}
		if (id.contains("iron")) {
			return 0xE0B090;
		}
		if (id.contains("copper")) {
			return 0xE07040;
		}
		if (id.contains("debris")) {
			return 0xA06040;
		}
		return 0xC0C0C0;
	}
}
