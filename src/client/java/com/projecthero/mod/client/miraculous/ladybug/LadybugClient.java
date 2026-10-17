package com.projecthero.mod.client.miraculous.ladybug;

import com.projecthero.mod.client.miraculous.KwamiClientKit;
import com.projecthero.mod.client.miraculous.MiraculousClient;
import com.projecthero.mod.miraculous.Kwami;

/** v0.16.6: Ladybug client side (STUB: the kit build adds the suit layer, weapon, poses, renderers). */
public final class LadybugClient implements KwamiClientKit {
	private LadybugClient() {
	}

	public static void initialize() {
		MiraculousClient.register(Kwami.TIKKI, new LadybugClient());
	}
}
