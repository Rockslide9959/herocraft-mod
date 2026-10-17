package com.projecthero.mod.client.miraculous.hawkmoth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothConfig;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothKit;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothMarksPayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSensePayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSwarmPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.6: what this client knows about Hawk Moth's work: the akumatized creatures (from {@link HawkMothMarksPayload},
 * sent to everyone), this viewer's own Emotion Sense colours ({@link HawkMothSensePayload}, sent to him alone) and the
 * creatures Swarm Blind's butterflies are swarming. The outline mixins and {@link ButterflyFx} ask here.
 */
public final class HawkMothFxClient {
	/** Entity id -> 1 akumatized / 2 Champion (the latest list from the server). */
	private static volatile Map<Integer, Integer> marks = Map.of();
	private static final Map<Integer, long[]> SENSE = new ConcurrentHashMap<>();
	/** Entity id -> game time the swarm leaves it. */
	static final Map<Integer, Long> SWARMED = new ConcurrentHashMap<>();

	private HawkMothFxClient() {
	}

	static void receiveMarks(HawkMothMarksPayload p) {
		Map<Integer, Integer> m = new ConcurrentHashMap<>();
		for (int i = 0; i < p.ids().size() && i < p.kinds().size(); i++) {
			m.put(p.ids().get(i), p.kinds().get(i));
		}
		marks = m;
	}

	static void receiveSense(HawkMothSensePayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		SENSE.clear();
		long until = mc.level.getGameTime() + p.ticks();
		for (int i = 0; i < p.ids().size() && i < p.colors().size(); i++) {
			SENSE.put(p.ids().get(i), new long[] { p.colors().get(i), until });
		}
	}

	static void receiveSwarm(HawkMothSwarmPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		long until = mc.level.getGameTime() + p.ticks();
		for (int id : p.ids()) {
			SWARMED.put(id, until);
		}
	}

	public static void reset() {
		marks = Map.of();
		SENSE.clear();
		SWARMED.clear();
	}

	/** 0 none, 1 akumatized, 2 Champion. */
	public static int mark(Entity e) {
		Integer k = marks.get(e.getId());
		return k == null || !e.isAlive() ? 0 : k;
	}

	public static Map<Integer, Integer> marks() {
		return marks;
	}

	/**
	 * The outline colour for {@code e} on this client (RGB), or -1 for none: an akuma's purple (everyone sees it), else this
	 * viewer's own Emotion Sense mood colour (only while his sense runs).
	 */
	public static int outline(Entity e) {
		int k = mark(e);
		if (k == 2) {
			return HawkMothConfig.CHAMPION_OUTLINE;
		}
		if (k == 1) {
			return HawkMothConfig.AKUMA_OUTLINE;
		}
		long[] s = SENSE.get(e.getId());
		if (s == null) {
			return -1;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		if (viewer == null || e.level() == null || e.level().getGameTime() > s[1] || !e.isAlive() || !senseOn(viewer)) {
			return -1;
		}
		return (int) s[0];
	}

	private static boolean senseOn(LocalPlayer viewer) {
		MiraculousState st = Miraculous.peek(viewer);
		return st != null && Miraculous.transformedAs(viewer, Kwami.NOOROO) && st.val(HawkMothKit.V_SENSE_UNTIL) > viewer.level().getGameTime();
	}
}
