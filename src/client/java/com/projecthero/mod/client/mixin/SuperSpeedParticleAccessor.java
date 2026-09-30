package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.particle.Particle;

/** Super Speed Time Slow (v0.14.7): lets a slowed particle creep along instead of ticking normally. */
@Mixin(Particle.class)
public interface SuperSpeedParticleAccessor {
	@Accessor("x")
	double projecthero$x();

	@Accessor("y")
	double projecthero$y();

	@Accessor("z")
	double projecthero$z();

	@Accessor("xo")
	void projecthero$setXo(double v);

	@Accessor("yo")
	void projecthero$setYo(double v);

	@Accessor("zo")
	void projecthero$setZo(double v);

	@Accessor("xd")
	double projecthero$xd();

	@Accessor("yd")
	double projecthero$yd();

	@Accessor("zd")
	double projecthero$zd();
}
