package com.projecthero.mod.syndicate;

import com.projecthero.mod.event.EventTypes;

/**
 * v0.14.25: the Syndicate Bust -- one entry point, called from {@code ProjectHeroMod.onInitialize}: the crooks, the
 * stash block, the items and the event type. See {@link SyndicateBust} and {@code docs/SYNDICATE_REFERENCE.md}.
 */
public final class Syndicate {
	private Syndicate() {
	}

	public static void initialize() {
		SyndicateEntityTypes.initialize();
		SyndicateItems.initialize();
		EventTypes.register(SyndicateBust.TYPE_ID, SyndicateBust::new);
	}
}
