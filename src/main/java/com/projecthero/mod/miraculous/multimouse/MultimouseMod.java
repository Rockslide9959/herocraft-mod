package com.projecthero.mod.miraculous.multimouse;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.multimouse.entity.MultimouseEntityTypes;

/**
 * v0.17.1: Multimouse server entry point (called from {@code MiraculousMod.initialize}): the mini copies' entity type and
 * the kit. Her lifecycle (detransform, death, logout, dimension change, revoke) reaches the kit through the core's
 * {@code onDetransform}, which drops the copies and gives her her full size back.
 */
public final class MultimouseMod {
	private MultimouseMod() {
	}

	public static void initialize() {
		MultimouseEntityTypes.initialize();
		MiraculousKits.register(Kwami.MULLO, new MultimouseKit());
	}
}
