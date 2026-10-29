package com.projecthero.mod.omnitrix;

import java.util.UUID;

import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.hero.UniquePowersData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.1: the {@code projectheroUniquePowers} gamerule for an object, not a player. While a meteor is falling, a pod sits in
 * its crater or a popped-off Omnitrix lies around as an item, that Omnitrix is recorded in the unique-powers table under a
 * pseudo holder ({@link #LOOSE}, "a loose Omnitrix"). With the rule on, it then counts as held: no second meteor falls (natural
 * or scanned) and nobody gains the power another way. Whoever finally puts THAT Omnitrix on clears the entry first.
 * {@code /projecthero uniquepowers release omnitrix} clears it too (e.g. after the item burned in lava).
 */
public final class OmnitrixWorld {
	public static final UUID LOOSE = UUID.nameUUIDFromBytes("projecthero:loose_omnitrix".getBytes(java.nio.charset.StandardCharsets.UTF_8));
	public static final String LOOSE_NAME = "a loose Omnitrix";

	private OmnitrixWorld() {
	}

	public static void markLoose(MinecraftServer server) {
		if (server != null) {
			UniquePowersData.get(server).add(Omnitrix.KEY, LOOSE, LOOSE_NAME);
		}
	}

	public static void clearLoose(MinecraftServer server) {
		if (server != null) {
			UniquePowersData.get(server).remove(Omnitrix.KEY, LOOSE);
		}
	}

	/** True if the rule is on and an Omnitrix already exists (worn by anyone, or loose). */
	public static boolean oneExists(MinecraftServer server) {
		return server != null && UniquePowers.enabled(server) && !UniquePowersData.get(server).holders(Omnitrix.KEY).isEmpty();
	}

	/**
	 * The watch jumps onto {@code p} from a loose source (the pod, the item): the loose entry is lifted for the grant (so it
	 * does not block its own wearer) and put back if the grant is refused. True if it went on.
	 */
	public static boolean putOnLoose(ServerPlayer p) {
		return putOnLoose(p, false);
	}

	/** v0.17.1: {@code latch} -- the pod's leap: {@link Omnitrix#latch} (the activation sound and the shake-off) instead of a plain grant. */
	public static boolean putOnLoose(ServerPlayer p, boolean latch) {
		MinecraftServer server = p.getServer();
		boolean wasLoose = server != null && UniquePowersData.get(server).holders(Omnitrix.KEY).containsKey(LOOSE);
		if (wasLoose) {
			clearLoose(server);
		}
		boolean ok = latch ? Omnitrix.latch(p) : Omnitrix.grant(p);
		if (!ok && wasLoose) {
			markLoose(server);
		}
		return ok;
	}
}
