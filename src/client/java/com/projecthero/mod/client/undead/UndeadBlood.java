package com.projecthero.mod.client.undead;


import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: where Undead bleeds ({@link BloodFx} does the drawing): a part bursting, a stump spurting, the flight's jets out of
 * the four stumps (or the neck), the boost's burning edge (embers, soot, scorched pixels dropping off), and the drips off a
 * fresh stump for a couple of seconds. Positions come from where each part was last drawn ({@link UndeadRender#partMatrix}).
 */
public final class UndeadBlood {
	private static final RandomSource RND = RandomSource.create();
	private static final Vector3f TMP = new Vector3f();

	private UndeadBlood() {
	}

	/** A point of {@code part} (its own space, model px) in the world. */
	static Vec3 at(Player player, int part, float x, float y, float z) {
		Matrix4f m = UndeadRender.partMatrix(player, part);
		m.transformPosition(TMP.set(x / 16f, y / 16f, z / 16f));
		return new Vec3(TMP.x, TMP.y, TMP.z);
	}

	/** The part's own +y (along a limb toward its end; down the body; the head's down) in the world. */
	static Vec3 axis(Player player, int part) {
		Matrix4f m = UndeadRender.partMatrix(player, part);
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
		float[] b = UndeadTexels.box(part, UndeadRender.slim(player));
		return at(player, part, b[0] + b[3] * 0.5f, b[1] + rows, b[2] + b[5] * 0.5f);
	}

	/** Where a missing part met the rest (the stump face) and which way out of it. */
	static Vec3[] stump(Player player, int part) {
		float[] j = UndeadTexels.joint(part, UndeadRender.slim(player));
		Vec3 p = at(player, part, j[0], j[1], j[2]);
		Vec3 dir = axis(player, part);
		if (part == UndeadState.HEAD) {
			dir = dir.scale(-1); // out of the neck, up
		}
		return new Vec3[] { p, dir };
	}

	public static void partBurst(Player player, int part, float power) {
		float[] b = UndeadTexels.box(part, UndeadRender.slim(player));
		Vec3 c = at(player, part, b[0] + b[3] * 0.5f, b[1] + b[4] * 0.5f, b[2] + b[5] * 0.5f);
		Vec3 carry = player.getDeltaMovement().scale(0.5);
		BloodFx.spurt(c, new Vec3(0, 1, 0), (int) (18 * power), 0.2, 0.9, carry);
		UndeadSounds.bloodBurst(c, power);
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
		UndeadSounds.spray(e);
		if (power > 1f) {
			BloodFx.mist(e, 2, 0.25f, carry);
		}
	}

	/** Every tick, for every Undead in view: jets, the ejected stumps, drips. */
	static void tick(Player player, UndeadState s, long now) {
		Minecraft mc = Minecraft.getInstance();
		boolean firstPersonSelf = player == mc.player && mc.options.getCameraType().isFirstPerson();
		UndeadSounds.jet(player, UndeadJets.jetting(player), false);
		UndeadSounds.punchCharge(player, s.flag(UndeadState.FLAG_PUNCH));
		if (s.flag(UndeadState.FLAG_PROPELLING) && firstPersonSelf && player instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
			UndeadFirstPerson.spray(acp, s);
		}
		// the jets: pulsing arterial spurts, the start's eruption and the stop's sputter
		UndeadJets.tick(player, s, now, firstPersonSelf);
		// fresh stumps drip for a couple of seconds
		if (now % 3 == 0) {
			for (int part = 0; part < UndeadState.PARTS; part++) {
				if (s.broken(part) && now - s.brokenAt(part) < 50 && s.regrowth(part, now) <= 0f && part != UndeadState.BODY) {
					Vec3[] st = stump(player, part);
					BloodFx.drip(st[0]);
					if (now % 9 == 0) {
						UndeadSounds.drip(st[0]);
					}
				}
			}
			if (now - s.ex(UndeadState.X_LAST_SHOT) < 30) {
				for (int arm = UndeadState.RIGHT_ARM; arm <= UndeadState.LEFT_ARM; arm++) {
					float c = s.eaten(arm, now);
					if (c > 0f && !s.broken(arm)) {
						BloodFx.drip(endOf(player, arm, 12f * (1f - c)));
					}
				}
			}
			// v0.17.3: an ejected end not jetting or spraying drips off its stump until it has grown back over
			if (s.ex(UndeadState.X_EJECTED) != 0L && !UndeadJets.jetting(s, now)) {
				for (int part = UndeadState.RIGHT_ARM; part <= UndeadState.LEFT_LEG; part++) {
					UndeadCuts.Cut cut = UndeadCuts.of(s, part, now);
					if (cut != null && cut.q() < 0.5f && !UndeadJets.armBusy(s, part) && !(firstPersonSelf && part <= UndeadState.LEFT_ARM)) {
						BloodFx.drip(endOf(player, part, cut.rows() + (12f - cut.rows()) * cut.q()));
					}
				}
			}
		}
	}
}
