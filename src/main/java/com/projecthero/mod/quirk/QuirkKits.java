package com.projecthero.mod.quirk;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;

/** v0.16.7: which {@link QuirkKit} belongs to which {@link Quirk} (each kit registers itself from its own init). */
public final class QuirkKits {
	private static final Map<Quirk, QuirkKit> KITS = new EnumMap<>(Quirk.class);

	/** Until a kit registers: no moves. */
	private static final QuirkKit NONE = new QuirkKit() {
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

	private QuirkKits() {
	}

	public static void register(Quirk quirk, QuirkKit kit) {
		KITS.put(quirk, kit);
	}

	public static QuirkKit of(@Nullable Quirk quirk) {
		if (quirk == null) {
			return NONE;
		}
		return KITS.getOrDefault(quirk, NONE);
	}

	public static void clearSessionState() {
		KITS.values().forEach(QuirkKit::clearSessionState);
	}

	public static void forget(java.util.UUID id) {
		KITS.values().forEach(k -> k.forget(id));
	}
}
