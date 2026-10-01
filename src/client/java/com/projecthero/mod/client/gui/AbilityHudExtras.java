package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * v0.14.5: per-power additions to {@link AbilityHud} that live in each power's own client class instead of
 * the HUD itself.
 *
 * <ul>
 *   <li>{@link #mono} -- the power's HUD is drawn in the black-and-gray theme (boxes, borders, key letters,
 *       power name, bar labels). Bar fills keep their own colours.</li>
 *   <li>{@link #registerAbove} -- Hairline bars stacked <em>above</em> the ability-key row (charge-ups,
 *       running-move timers). A registered {@code MutationMeters.Spec} with {@code above = true} lands here
 *       too, without any client code.</li>
 *   <li>{@link #registerDecor} -- free drawing on the power-name line, right of the name (e.g. Super
 *       Regeneration's revive-charge dots).</li>
 * </ul>
 */
public final class AbilityHudExtras {
	/** One Hairline bar above the keys. {@code ratio} 0..1 is the fill. */
	public record AboveBar(String label, float ratio, int fill, int textColor) {
	}

	@FunctionalInterface
	public interface AboveSource {
		void collect(Minecraft client, ExperimentalState state, long gameTime, List<AboveBar> out);
	}

	@FunctionalInterface
	public interface Decor {
		/** Draw at ({@code x}, {@code y}) = just right of the power name, on its text line. */
		void draw(GuiGraphics g, Minecraft client, ExperimentalState state, int x, int y);
	}

	private static final Set<String> MONO = new HashSet<>();
	private static final Map<String, List<AboveSource>> ABOVE = new HashMap<>();
	private static final Map<String, Decor> DECOR = new HashMap<>();
	private static final Map<String, Decor> HEADER = new HashMap<>();

	private AbilityHudExtras() {
	}

	public static void mono(String powerKey) {
		MONO.add(powerKey);
	}

	public static boolean isMono(String powerKey) {
		return MONO.contains(powerKey);
	}

	public static void registerAbove(String powerKey, AboveSource source) {
		ABOVE.computeIfAbsent(powerKey, k -> new ArrayList<>()).add(source);
	}

	public static void registerDecor(String powerKey, Decor decor) {
		DECOR.put(powerKey, decor);
	}

	/**
	 * v0.14.16: a line drawn just ABOVE the power name ({@code x} = the HUD's left edge, {@code y} = one text line over
	 * the name), e.g. Super Speed's "Suit 87%" while the Flash Suit is packed in the worn ring. Draws nothing to skip.
	 */
	public static void registerHeader(String powerKey, Decor header) {
		HEADER.put(powerKey, header);
	}

	static Decor header(String powerKey) {
		return HEADER.get(powerKey);
	}

	static List<AboveBar> above(String powerKey, Minecraft client, ExperimentalState state, long gameTime) {
		List<AboveBar> out = new ArrayList<>();
		for (AboveSource s : ABOVE.getOrDefault(powerKey, List.of())) {
			s.collect(client, state, gameTime, out);
		}
		return out;
	}

	static Decor decor(String powerKey) {
		return DECOR.get(powerKey);
	}
}
