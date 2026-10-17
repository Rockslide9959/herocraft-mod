package com.projecthero.mod.client.miraculous.renarouge;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;

/** v0.16.6: RenaRouge client side (STUB: the kit build adds the suit layer, weapon, poses, renderers). */
public final class RenaRougeClient implements KwamiClientKit {
	private RenaRougeClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.TRIXX, new RenaRougeClient());
	}
}
