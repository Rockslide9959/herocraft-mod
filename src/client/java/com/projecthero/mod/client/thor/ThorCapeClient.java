package com.projecthero.mod.client.thor;

import com.projecthero.mod.client.render.FlowingCapeLayer;

/**
 * v0.14.16: registers Thor's cape ({@link ThorCapeLayer}) on both player renderers (wide + slim). v0.14.21: through the
 * shared {@link FlowingCapeLayer#register}, which also drops the easing on disconnect.
 */
public final class ThorCapeClient {
	private ThorCapeClient() {
	}

	public static void initialize() {
		FlowingCapeLayer.register(ThorCapeLayer::new);
	}
}
