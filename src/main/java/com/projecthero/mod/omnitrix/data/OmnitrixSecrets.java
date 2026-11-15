package com.projecthero.mod.omnitrix.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.5 (user: "change how master control is gained, it should be a random 6 number combination"): the codes a player's
 * Omnitrix answers to -- his own Master Control code and one code per hidden alien. Rolled once per player the first time
 * anything asks ({@code OmnitrixCodes.secrets}) and never changed after. Lives in its own attachment
 * ({@code projecthero:omnitrix_secrets}): persistent, kept through death, kept when the watch comes off, and NEVER synced --
 * a client only ever learns the digits he has earned (those sit in the synced {@link OmnitrixState} timers).
 *
 * @param master the six-digit Master Control code ("" = not rolled yet)
 * @param aliens hidden alien id -> its six-digit code
 */
public record OmnitrixSecrets(String master, Map<String, String> aliens) {
	public static final OmnitrixSecrets EMPTY = new OmnitrixSecrets("", Map.of());

	public static final Codec<OmnitrixSecrets> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("master", "").forGetter(OmnitrixSecrets::master),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("aliens", Map.of()).forGetter(OmnitrixSecrets::aliens)
	).apply(i, OmnitrixSecrets::new));

	public OmnitrixSecrets {
		master = master == null ? "" : master;
		aliens = Map.copyOf(aliens == null ? Map.of() : aliens);
	}

	public OmnitrixSecrets withMaster(String code) {
		return new OmnitrixSecrets(code, aliens);
	}

	public OmnitrixSecrets withAlien(String id, String code) {
		Map<String, String> m = new HashMap<>(aliens);
		m.put(id, code);
		return new OmnitrixSecrets(master, m);
	}
}
