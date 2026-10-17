package com.projecthero.mod.client.miraculous.hawkmoth;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;

/** v0.16.6: HawkMoth client side (STUB: the kit build adds the suit layer, weapon, poses, renderers). */
public final class HawkMothClient implements KwamiClientKit {
	private HawkMothClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.NOOROO, new HawkMothClient());
	}
}
