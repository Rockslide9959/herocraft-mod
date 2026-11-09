package com.projecthero.mod.client.andy;

import com.projecthero.mod.andy.AndyFlight;
import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: where Andy bleeds ({@link BloodFx} does the drawing): a part bursting, a stump spurting, the flight's jets out of
 * the four stumps (or the neck), the boost's burning edge (embers, soot, scorched pixels dropping off), and the drips off a
 * fresh stump for a couple of seconds. Positions come from where each part was last drawn ({@link AndyRender#partMatrix}).
 */
public final class AndyBlood {
	private static final RandomSource RND = RandomSource.create();
	private static final Vector3f TMP = new Vector3f();

	private AndyBlood() {
	}

	/** A point of {@code part} (its own space, model px) in the world. */
	static Vec3 at(Player player, int part, float x, float y, float z) {
		Matrix4f m = AndyRender.partMatrix(player, part);
		m.transformPosition(TMP.set(x / 16f, y / 16f, z / 16f));
		return new Vec3(TMP.x, TMP.y, TMP.z);
	}

	/** The part's own +y (along a limb toward its end; down the body; the head's down) in the world. */
	static Vec3 axis(Player player, int part) {
		Matrix4f m = AndyRender.partMatrix(player, part);
		m.transformDirection(TMP.set(0f, 1f, 0f));
		if (TMP.lengthSquared() < 1.0e-8f) {
			return new Vec3(0, -1, 0);
		}
		TMP.normalize();
		return new Vec3(TMP.x, TMP.y, TMP.z);
	}

	/**
	 * Which way a flight jet streams out of a limb's stump: mostly straight back against the travel ({@code look} reversed),
	 * only a little along the limb, so the four jets run behind him as one wake instead of fanning out like wings.
	 */
	static Vec3 jetDir(Vec3 limbAxis, Vec3 look) {
		Vec3 d = limbAxis.scale(0.25).add(look.scale(-0.75));
		return d.lengthSqr() < 1.0e-6 ? look.scale(-1) : d.normalize();
	}

	/** Where a limb ends when only {@code rows} of it are left (the middle of the cut). */
	static Vec3 endOf(Player player, int part, float rows) {
		float[] b = AndyTexels.box(part, AndyRender.slim(player));
		return at(player, part, b[0] + b[3] * 0.5f, b[1] + rows, b[2] + b[5] * 0.5f);
	}

	/** Where a missing part met the rest (the stump face) and which way out of it. */
	static Vec3[] stump(Player player, int part) {
		float[] j = AndyTexels.joint(part, AndyRender.slim(player));
		Vec3 p = at(player, part, j[0], j[1], j[2]);
		Vec3 dir = axis(player, part);
		if (part == AndyState.HEAD) {
			dir = dir.scale(-1); // out of the neck, up
		}
		return new Vec3[] { p, dir };
	}

	public static void partBurst(Player player, int part, float power) {
		float[] b = AndyTexels.box(part, AndyRender.slim(player));
		Vec3 c = at(player, part, b[0] + b[3] * 0.5f, b[1] + b[4] * 0.5f, b[2] + b[5] * 0.5f);
		Vec3 carry = player.getDeltaMovement().scale(0.5);
		BloodFx.spurt(c, new Vec3(0, 1, 0), (int) (18 * power), 0.2, 0.9, carry);
		AndySounds.bloodBurst(c, power);
		BloodFx.mist(c, (int) (4 * power), 0.35f, carry);
		Vec3[] s = stump(player, part);
		BloodFx.spurt(s[0], s[1], (int) (10 * power), 0.3, 0.35, carry);
	}

	/** A stump spurting (a part thrown off). */
	public static void stumpSpurt(Player player, int part, float power) {
		Vec3[] s = stump(player, part);
		Vec3 carry = player.getDeltaMovement().scale(0.5);
		BloodFx.spurt(s[0], s[1], (int) (18 * power), 0.3, 0.3, carry);
		BloodFx.mist(s[0], 2, 0.25f, carry);
	}

	/** The end of a limb coming off (a hand at take-off, a finger shot's bite): a spurt from the new end. */
	public static void stumpEndSpurt(Player player, int part, float rows, float power) {
		Vec3 e = endOf(player, part, rows);
		Vec3 carry = player.getDeltaMovement().scale(0.5);
		BloodFx.spurt(e, axis(player, part), (int) (14 * power), 0.22, 0.45, carry);
		AndySounds.spray(e);
		if (power > 1f) {
			BloodFx.mist(e, 2, 0.25f, carry);
		}
	}

	/** Every tick, for every Andy in view: jets, the burn, drips. */
	static void tick(Player player, AndyState s, long now) {
		Minecraft mc = Minecraft.getInstance();
		boolean firstPersonSelf = player == mc.player && mc.options.getCameraType().isFirstPerson();
		Vec3 carry = player.getDeltaMovement();
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		AndySounds.jet(player, flying, flying && s.flag(AndyState.FLAG_BOOST));
		boolean burning = false;
		for (int part = AndyState.BODY; part < AndyState.PARTS && flying && s.flag(AndyState.FLAG_BOOST); part++) {
			burning |= AndyFlight.burnt(s, part) > 0f;
		}
		AndySounds.burn(player, burning);
		AndySounds.punchCharge(player, s.flag(AndyState.FLAG_PUNCH));
		if (s.flag(AndyState.FLAG_PROPELLING) && firstPersonSelf && player instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
			AndyFirstPerson.spray(acp, s);
		}
		if (s.flag(AndyState.FLAG_PROPELLING) && !firstPersonSelf) {
			float power = s.flag(AndyState.FLAG_BOOST) ? 1.8f : 1f;
			if (s.headOnly(now)) {
				Vec3[] neck = stump(player, AndyState.HEAD);
				// out of the neck: the head's own down
				BloodFx.jet(neck[0], player.getLookAngle().scale(-1), carry, power);
			} else {
				for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
					if (!s.broken(part)) {
						AndyCuts.Cut cut = AndyCuts.of(s, part, now);
						float rows = cut != null ? cut.rows() : 12f;
						BloodFx.jet(endOf(player, part, rows), jetDir(axis(player, part), player.getLookAngle()), carry, power);
					} else if (!s.broken(AndyState.BODY)) {
						// the limb is gone: the jet comes straight out of the shoulder / hip
						Vec3[] st = stump(player, part);
						BloodFx.jet(st[0], jetDir(st[1], player.getLookAngle()), carry, power * 0.8f);
					}
				}
				if (s.broken(AndyState.BODY)) {
					Vec3[] neck = stump(player, AndyState.HEAD);
					BloodFx.jet(neck[0], player.getLookAngle().scale(-1), carry, power);
				}
			}
		}
		// the boost's burning edge
		if (s.flag(AndyState.FLAG_PROPELLING) && s.flag(AndyState.FLAG_BOOST)) {
			for (int part = AndyState.BODY; part < AndyState.PARTS; part++) {
				float b = AndyFlight.burnt(s, part);
				if (b <= 0f) {
					continue;
				}
				AndyCuts.Cut cut = AndyCuts.of(s, part, now);
				if (cut == null) {
					continue;
				}
				Vec3 edge = part == AndyState.BODY ? at(player, part, 0f, cut.rows(), 0f) : endOf(player, part, cut.rows());
				BloodFx.embers(edge, carry, 2);
				if (RND.nextFloat() < 0.3f && player.level() != null && !firstPersonSelf) {
					player.level().addParticle(ParticleTypes.SMOKE, edge.x, edge.y, edge.z, carry.x * 0.85, carry.y * 0.85 + 0.02, carry.z * 0.85);
				}
				// a scorched pixel drops off the edge
				if (RND.nextFloat() < 0.7f) {
					java.util.List<AndyTexels.Texel> texels = AndyTexels.of(part, AndyRender.slim(player));
					AndyTexels.Texel t = texels.get(RND.nextInt(texels.size()));
					PixelCubes.ember(AndyRender.skin(player), t, edge.add((RND.nextDouble() - 0.5) * 0.2, 0, (RND.nextDouble() - 0.5) * 0.2), carry);
				}
			}
		}
		// fresh stumps drip for a couple of seconds
		if (now % 3 == 0) {
			for (int part = 0; part < AndyState.PARTS; part++) {
				if (s.broken(part) && now - s.brokenAt(part) < 50 && s.regrowth(part, now) <= 0f && part != AndyState.BODY) {
					Vec3[] st = stump(player, part);
					BloodFx.drip(st[0]);
					if (now % 9 == 0) {
						AndySounds.drip(st[0]);
					}
				}
			}
			if (now - s.ex(AndyState.X_LAST_SHOT) < 30) {
				for (int arm = AndyState.RIGHT_ARM; arm <= AndyState.LEFT_ARM; arm++) {
					float c = s.eaten(arm, now);
					if (c > 0f && !s.broken(arm)) {
						BloodFx.drip(endOf(player, arm, 12f * (1f - c)));
					}
				}
			}
			if (!s.flag(AndyState.FLAG_PROPELLING) && s.ex(AndyState.X_CUT_REGROW) > 0L && now - s.ex(AndyState.X_CUT_REGROW) < 40) {
				for (int part = AndyState.RIGHT_ARM; part <= AndyState.LEFT_LEG; part++) {
					AndyCuts.Cut cut = AndyCuts.of(s, part, now);
					if (cut != null) {
						BloodFx.drip(endOf(player, part, cut.rows()));
					}
				}
			}
		}
	}
}
