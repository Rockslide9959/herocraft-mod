package com.projecthero.mod.event.entity;

import software.bernie.geckolib.animatable.GeoEntity;

/**
 * v0.16.1: a raid mob drawn with its own GeckoLib model ({@link RaidAnims}) -- the Powered Zombie Boss ({@link EmpoweredZombie},
 * through {@link AnimatedRaidUndead}). The raid's other mobs are on the player rig with skins; vanilla mobs are
 * untouched.
 */
public interface RaidAnimated extends GeoEntity {
	/** Asset base name (one of {@link RaidAnims#MODELS}). */
	String raidModel();

	/** The synced clip state. */
	RaidAnimator raidAnimator();
}
