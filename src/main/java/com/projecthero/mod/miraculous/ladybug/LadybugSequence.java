package com.projecthero.mod.miraculous.ladybug;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (user: "a quick transformation happens when the player just says spots on, the long transformation sequence
 * happens when the player says tikki spots on ... mirror the transformation in the show"): Ladybug's full transformation
 * sequence, about 8.5 s. The beats follow the show's:
 * <ol>
 *   <li>{@link #EARRINGS} -- Tikki dives into the earrings; they flash and their spots light up (hands to the ears).</li>
 *   <li>{@link #SPIN} -- she twirls twice inside a ribbon of red, spotted light; the suit forms from the boots up.</li>
 *   <li>{@link #MASK} -- a hand swipes across her eyes and the mask forms (the suit finishes at the head).</li>
 *   <li>{@link #YOYO} -- the yo-yo appears at her hip and she spins it up.</li>
 *   <li>{@link #POSE} -- the hero pose, in front of a burst of pink light and black spots, and she settles back down.</li>
 * </ol>
 * The whole time she hangs a block off the ground, can't be hurt and can't use a power; a cinematic camera
 * ({@code LadybugSequenceCameraMixin}) flies round her for the holder. The timeline below is CLIENT-SAFE (the client plays
 * the suit, the pose, the camera and the light show from {@code formChangeAt}); {@link #start} / {@link #tick} are server.
 */
public final class LadybugSequence {
	/** The long transformation's anim id (a shared one -- the core times it). */
	public static final int ANIM = 5;
	public static final int TOTAL = 170;

	public static final int EARRINGS = 14;
	public static final int SPIN = 34;
	public static final int SPIN_END = 92;
	public static final int MASK = 96;
	public static final int MASK_END = 116;
	public static final int YOYO = 118;
	public static final int POSE = 146;

	/** Synced: the yaw she faced and the height she stood at when it began (the camera and the spin start from them). */
	public static final String V_YAW = "lb_seq_yaw";
	public static final String V_Y = "lb_seq_y";
	public static final String V_X = "lb_seq_x";
	public static final String V_Z = "lb_seq_z";

	private LadybugSequence() {
	}

	/** True while a holder's long transformation is playing ({@code age} = ticks since it began). */
	public static boolean running(MiraculousState s, long age) {
		return s != null && s.transformed && s.animId == ANIM && age >= 0 && age < TOTAL;
	}

	public static boolean running(Player p) {
		MiraculousState s = Miraculous.peek(p);
		return s != null && running(s, p.level().getGameTime() - s.formChangeAt);
	}

	// ---------------------------------------------------------------- the timeline (shared)

	/** How far off the ground she hangs: up over the first second, down again over the last. */
	public static double lift(float t) {
		float up = smooth(t / 20f);
		float down = smooth((t - (POSE + 6)) / 18f);
		return 1.0 * (up - down);
	}

	/** The twirl, in degrees on top of the yaw she started at: two full turns in the spin, a half turn and back with the yo-yo. */
	public static float spin(float t) {
		float a = 720f * smooth((t - SPIN) / (float) (SPIN_END - SPIN));
		float yo = 180f * Mth.sin(Mth.PI * Mth.clamp((t - YOYO) / (float) (POSE - YOYO), 0f, 1f));
		return a + yo;
	}

	/**
	 * How much of the suit is on, 0..1, counted from the BOOTS up (the long sequence's reveal runs the quick one's order
	 * backwards): the legs to the shoulders in the spin, the head and the mask with the swipe.
	 */
	public static float cover(float t) {
		if (t < SPIN + 4) {
			return 0f;
		}
		if (t < SPIN_END) {
			return 0.84f * smooth((t - (SPIN + 4)) / (float) (SPIN_END - SPIN - 4));
		}
		if (t < MASK + 6) {
			return 0.84f;
		}
		return 0.84f + 0.16f * smooth((t - (MASK + 6)) / (float) (MASK_END - MASK - 6));
	}

	public static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	// ---------------------------------------------------------------- server

	/** Starts it (called in place of the quick transformation's look; the core has already set transformed / formChangeAt). */
	public static void start(ServerPlayer p) {
		Miraculous.edit(p, n -> {
			n.animId = ANIM;
			n.vals.put(V_YAW, (long) Float.floatToIntBits(p.getYRot()));
			n.vals.put(V_X, Double.doubleToLongBits(p.getX()));
			n.vals.put(V_Y, Double.doubleToLongBits(p.getY()));
			n.vals.put(V_Z, Double.doubleToLongBits(p.getZ()));
		});
		p.setDeltaMovement(0, 0, 0);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4f, 1.3f);
	}

	/** Every tick while it plays: hold her in the air where she began, and the music of the beats. */
	public static void tick(ServerPlayer p, MiraculousState s, long age) {
		if (s.kwami == null || Kwami.byId(s.kwami) != Kwami.TIKKI) {
			return;
		}
		double x = Double.longBitsToDouble(s.val(V_X));
		double y = Double.longBitsToDouble(s.val(V_Y)) + lift(age);
		double z = Double.longBitsToDouble(s.val(V_Z));
		p.connection.teleport(x, y, z, p.getYRot(), p.getXRot());
		p.setDeltaMovement(0, 0, 0);
		p.resetFallDistance();
		if (age == EARRINGS) {
			sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.6f);
			sound(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.8f);
		} else if (age == SPIN) {
			sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
			sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.8f);
		} else if (age == MASK) {
			sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.8f, 1.6f);
			sound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1.4f);
		} else if (age == YOYO) {
			sound(p, SoundEvents.FISHING_BOBBER_THROW, 1.0f, 1.2f);
		} else if (age == POSE) {
			sound(p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.5f);
			sound(p, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.3f);
			sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.9f);
		} else if (age > YOYO && age < POSE && age % 5 == 0) {
			sound(p, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.4f, 1.6f);
		}
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent e, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), e, SoundSource.PLAYERS, vol, pitch);
	}
}
