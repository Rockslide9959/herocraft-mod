package com.projecthero.mod.miraculous;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.entity.KwamiEntity;
import com.projecthero.mod.miraculous.entity.MiraculousEntityTypes;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.6: keeps exactly one {@link KwamiEntity} beside every detransformed holder (server). The entity is never saved,
 * so the tick re-creates it whenever it is missing -- after a login, a respawn, a dimension change, a chunk unload or
 * anything that removed it -- and drops it once the transformation has pulled it into the jewel. Also plays the
 * Kwami's introduction (three chat lines, a little apart) after a jewel is used.
 */
public final class KwamiCompanion {
	private static final Map<UUID, UUID> ENTITY = new ConcurrentHashMap<>();

	private record Line(long at, String key) {
	}

	private static final Map<UUID, Deque<Line>> INTRO = new ConcurrentHashMap<>();

	/** How many intro lines each Kwami has ({@code message.projecthero.miraculous.intro.<id>.1..N}). */
	public static final int INTRO_LINES = 3;

	private KwamiCompanion() {
	}

	static void clearSessionState() {
		ENTITY.clear();
		INTRO.clear();
	}

	static void forget(UUID id) {
		INTRO.remove(id);
	}

	/** The holder's live Kwami, or null. */
	public static @Nullable KwamiEntity find(ServerPlayer player) {
		UUID id = ENTITY.get(player.getUUID());
		if (id == null) {
			return null;
		}
		Entity e = player.serverLevel().getEntity(id);
		if (e instanceof KwamiEntity k && !k.isRemoved()) {
			return k;
		}
		return null;
	}

	/** The holder's Kwami, spawned beside them if missing (null without the power). */
	public static @Nullable KwamiEntity ensure(ServerPlayer player) {
		Kwami kwami = Miraculous.kwami(player);
		if (kwami == null) {
			return null;
		}
		KwamiEntity k = find(player);
		if (k != null && k.kwami() == kwami) {
			return k;
		}
		if (k != null) {
			k.discard();
		}
		KwamiEntity e = MiraculousEntityTypes.KWAMI.create(player.serverLevel());
		if (e == null) {
			return null;
		}
		e.setup(player, kwami);
		e.snapTo(player);
		e.setYRot(player.getYRot());
		player.serverLevel().addFreshEntity(e);
		ENTITY.put(player.getUUID(), e.getUUID());
		return e;
	}

	/** Puts the Kwami in a mood (spawning it if needed). */
	public static void mood(ServerPlayer player, int mode, int ticks) {
		KwamiEntity k = ensure(player);
		if (k != null) {
			k.setMode(mode, ticks);
		}
	}

	public static void remove(ServerPlayer player) {
		KwamiEntity k = find(player);
		if (k != null) {
			k.discard();
		}
		ENTITY.remove(player.getUUID());
	}

	/** Every server tick for a holder. */
	static void tick(ServerPlayer player, MiraculousState s) {
		long now = player.level().getGameTime();
		if (!player.isAlive() || player.isSpectator()) {
			remove(player);
		} else if (!s.transformed) {
			ensure(player);
		} else if (now - s.formChangeAt > MiraculousConfig.INTO_JEWEL_TICKS + 2 || now < s.formChangeAt) {
			KwamiEntity k = find(player);
			if (k != null) {
				k.discard();
			}
		}
		Deque<Line> lines = INTRO.get(player.getUUID());
		if (lines != null) {
			Line l = lines.peekFirst();
			if (l != null && now >= l.at()) {
				lines.pollFirst();
				say(player, l.key());
			}
			if (lines.isEmpty()) {
				INTRO.remove(player.getUUID());
			}
		}
	}

	/** The Kwami pops out of a newly used jewel, waves and introduces itself. */
	static void introduce(ServerPlayer player, Kwami kwami) {
		mood(player, KwamiEntity.MODE_POP_OUT, MiraculousConfig.POP_OUT_TICKS);
		long now = player.level().getGameTime();
		Deque<Line> q = new ArrayDeque<>();
		for (int i = 1; i <= INTRO_LINES; i++) {
			q.add(new Line(now + MiraculousConfig.INTRO_FIRST_LINE + (long) (i - 1) * MiraculousConfig.INTRO_LINE_GAP,
					"message.projecthero.miraculous.intro." + kwami.id + "." + i));
		}
		INTRO.put(player.getUUID(), q);
	}

	/** A line in the Kwami's voice in chat: {@code [Tikki] ...}. */
	public static void say(ServerPlayer player, String key, Object... args) {
		Kwami k = Miraculous.kwami(player);
		if (k == null) {
			return;
		}
		player.sendSystemMessage(Component.literal("[").append(Component.translatable("entity.projecthero.kwami." + k.id)).append("] ")
				.withStyle(st -> st.withColor(TextColor.fromRgb(k.color & 0xFFFFFF)))
				.append(Component.translatable(key, args).withStyle(net.minecraft.ChatFormatting.WHITE)));
	}
}
