package com.projecthero.mod.miraculous;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (user: "can you add it to the other miraculous as well please, also make them all more unique and look like how
 * they look in the show"): the shared frame of the LONG transformation -- the one the full phrase with the Kwami's name
 * plays ("Plagg, claws out!"; the short phrase alone stays the quick one, see {@link MiraculousPhrases}). Each Kwami
 * supplies its own {@link Timeline} (its beats, how far off the ground it hangs, the turn, how much of the suit is on
 * and its music); the core does the rest the same way for every hero, exactly as Ladybug's first did it:
 * <ul>
 *   <li>the anim id {@link #ANIM} (shared; the Kwami tells which hero's it is) and {@link #formTicks} -- no power, no
 *   change back, until it is over;</li>
 *   <li>{@link #start} -- where they stood and the way they faced, synced so every client plays the same scene;</li>
 *   <li>{@link #tick} -- held in the air where they began, can't fall (and {@code MiraculousDamage}: can't be hurt).</li>
 * </ul>
 * The client half (pose, light show, cinematic camera) is {@code client.miraculous.MiraculousSequenceClient}.
 */
public final class MiraculousSequence {
	/** The long transformation's anim id, for every hero (the kits' own ids start at 20). */
	public static final int ANIM = 5;

	/** Synced: the yaw they faced and where they stood when it began (the camera and the turn start from them). */
	public static final String V_YAW = "lb_seq_yaw";
	public static final String V_Y = "lb_seq_y";
	public static final String V_X = "lb_seq_x";
	public static final String V_Z = "lb_seq_z";

	/** One hero's long transformation. Everything but {@link #start} / {@link #beats} is client-safe. */
	public interface Timeline {
		/** How long it runs, in ticks. */
		int total();

		/** How far off the ground they hang at {@code t}. */
		double lift(float t);

		/** The turn at {@code t}, in degrees on top of the way they faced at the start. */
		float spin(float t);

		/** How much of the suit is on at {@code t}, 0..1 (each kit's own order of the change, from its jewel). */
		float cover(float t);

		/** The sound as it begins (the core has already played the transformation's own). */
		void start(ServerPlayer p);

		/** The music of the beats, every tick while it plays. */
		void beats(ServerPlayer p, MiraculousState s, long age);
	}

	private MiraculousSequence() {
	}

	/** The hero's long transformation (every Kwami has one). */
	public static @Nullable Timeline of(@Nullable Kwami k) {
		if (k == null) {
			return null;
		}
		return switch (k) {
			case TIKKI -> com.projecthero.mod.miraculous.ladybug.LadybugSequence.TIMELINE;
			case PLAGG -> com.projecthero.mod.miraculous.catnoir.CatNoirSequence.TIMELINE;
			case TRIXX -> com.projecthero.mod.miraculous.renarouge.RenaRougeSequence.TIMELINE;
			case NOOROO -> com.projecthero.mod.miraculous.hawkmoth.HawkMothSequence.TIMELINE;
			case WAYZZ -> com.projecthero.mod.miraculous.carapace.CarapaceSequence.TIMELINE;
			case KAALKI -> com.projecthero.mod.miraculous.pegasus.PegasusSequence.TIMELINE;
			case SASS -> com.projecthero.mod.miraculous.viperion.ViperionSequence.TIMELINE;
			case MULLO -> null; // v0.17.1: Multimouse has the quick transformation for now
		};
	}

	public static @Nullable Timeline of(@Nullable MiraculousState s) {
		return s == null || s.kwami == null ? null : of(Kwami.byId(s.kwami));
	}

	/** The long one is playing in this state at all (whatever its age). */
	public static boolean isLong(@Nullable MiraculousState s) {
		return s != null && s.transformed && s.animId == ANIM && of(s) != null;
	}

	/** True while a holder's long transformation is playing ({@code age} = ticks since it began). */
	public static boolean running(@Nullable MiraculousState s, long age) {
		if (s == null || !s.transformed || s.animId != ANIM || age < 0) {
			return false;
		}
		Timeline tl = of(s);
		return tl != null && age < tl.total();
	}

	public static boolean running(Player p) {
		MiraculousState s = Miraculous.peek(p);
		return s != null && running(s, p.level().getGameTime() - s.formChangeAt);
	}

	/** How long the transformation that is playing runs (the hero's long one, or the quick one). */
	public static int formTicks(MiraculousState s) {
		if (s.animId == ANIM) {
			Timeline tl = of(s);
			if (tl != null) {
				return tl.total();
			}
		}
		return MiraculousConfig.TRANSFORM_TICKS;
	}

	public static float startYaw(MiraculousState s) {
		return Float.intBitsToFloat((int) s.val(V_YAW));
	}

	public static double startX(MiraculousState s) {
		return Double.longBitsToDouble(s.val(V_X));
	}

	public static double startY(MiraculousState s) {
		return Double.longBitsToDouble(s.val(V_Y));
	}

	public static double startZ(MiraculousState s) {
		return Double.longBitsToDouble(s.val(V_Z));
	}

	public static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** 0 before {@code from}, 1 after {@code from + len}, smooth between. */
	public static float ramp(float t, float from, float len) {
		return smooth((t - from) / len);
	}

	// ---------------------------------------------------------------- server

	/** Starts it (called in place of the quick transformation's look; the core has already set transformed / formChangeAt). */
	public static void start(ServerPlayer p) {
		Timeline tl = of(Miraculous.peek(p));
		if (tl == null) {
			return;
		}
		Miraculous.edit(p, n -> {
			n.animId = ANIM;
			n.vals.put(V_YAW, (long) Float.floatToIntBits(p.getYRot()));
			n.vals.put(V_X, Double.doubleToLongBits(p.getX()));
			n.vals.put(V_Y, Double.doubleToLongBits(p.getY()));
			n.vals.put(V_Z, Double.doubleToLongBits(p.getZ()));
		});
		p.setDeltaMovement(0, 0, 0);
		tl.start(p);
	}

	/** Every tick while it plays: hold them in the air where they began, and the music of the beats. */
	public static void tick(ServerPlayer p, MiraculousState s, long age) {
		Timeline tl = of(s);
		if (tl == null) {
			return;
		}
		double x = startX(s);
		double y = startY(s) + tl.lift(age);
		double z = startZ(s);
		p.connection.teleport(x, y, z, p.getYRot(), p.getXRot());
		p.setDeltaMovement(0, 0, 0);
		p.resetFallDistance();
		tl.beats(p, s, age);
	}

	/** A sound at the holder, heard by everyone round them. */
	public static void sound(ServerPlayer p, SoundEvent e, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), e, SoundSource.PLAYERS, vol, pitch);
	}
}
