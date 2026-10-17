package com.projecthero.mod.client.miraculous.catnoir;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;

/** v0.16.6: CatNoir client side (STUB: the kit build adds the suit layer, weapon, poses, renderers). */
public final class CatNoirClient implements KwamiClientKit {
	private CatNoirClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.PLAGG, new CatNoirClient());
	}
}
