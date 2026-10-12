package com.projecthero.mod.event.entity;

import software.bernie.geckolib.animatable.GeoEntity;

/**
 * v0.16.1: a raid mob drawn with its own GeckoLib model ({@link RaidAnims}). Implemented by {@link RaidUndead} (every raid
 * zombie, the Cursed Zombie and the Powered Zombie Boss), {@link SwordSkeleton} and {@link PillagerSpy}. Vanilla mobs are
 * untouched -- only these mod entities carry the new models.
 */
public interface RaidAnimated extends GeoEntity {
	/** Asset base name (one of {@link RaidAnims#MODELS}); may depend on state (a baby Risen uses its own model). */
	String raidModel();

	/** The synced clip state. */
	RaidAnimator raidAnimator();
}
