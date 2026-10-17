package com.projecthero.mod.miraculous;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;

/** v0.16.6: which {@link KwamiKit} belongs to which {@link Kwami} (each kit registers itself from its own init). */
public final class MiraculousKits {
	private static final Map<Kwami, KwamiKit> KITS = new EnumMap<>(Kwami.class);

	/** Until a kit registers: no moves. */
	private static final KwamiKit NONE = new KwamiKit() {
		@Override
		public void handle(net.minecraft.server.level.ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		}

		@Override
		public String[] idsOf(AbilitySlot slot) {
			return new String[0];
		}

		@Override
		public int maxCooldown(String abilityId) {
			return 0;
		}
	};

	private MiraculousKits() {
	}

	public static void register(Kwami kwami, KwamiKit kit) {
		KITS.put(kwami, kit);
	}

	public static KwamiKit of(@Nullable Kwami kwami) {
		if (kwami == null) {
			return NONE;
		}
		return KITS.getOrDefault(kwami, NONE);
	}

	public static void clearSessionState() {
		KITS.values().forEach(KwamiKit::clearSessionState);
	}
}
