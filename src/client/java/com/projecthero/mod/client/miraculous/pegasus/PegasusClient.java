package com.projecthero.mod.client.miraculous.pegasus;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;

/** v0.16.6: Pegasus client side (STUB: the kit build adds the suit layer, weapon, poses, renderers). */
public final class PegasusClient implements KwamiClientKit {
	private PegasusClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.KAALKI, new PegasusClient());
	}
}
