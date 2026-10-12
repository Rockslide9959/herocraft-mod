package com.projecthero.mod.gametest.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev/CI-speed selection, driven by system properties set from Gradle ({@code build.gradle}, "runGameTest"):
 * <ul>
 * <li>{@code -Dprojecthero.gametest.filter=<regex>} ({@code -PtestFilter}): only tests whose "classname.methodname" or
 * batch name matches (case-insensitive) are registered, so one class / one test runs in seconds.</li>
 * <li>{@code -Dprojecthero.gametest.shard=i/n} ({@code -PtestShard}): only the i-th of n disjoint slices of the suite,
 * so CI can run the slices on parallel runners. Tests of a named batch always land in the same slice (they share state
 * on purpose); the big default batch is split test by test.</li>
 * </ul>
 * Neither set = every test, exactly as before.
 * <p>
 * v0.16.1: the slices are DEALT, not hashed. The named batches (sorted by name) go round the slices one at a time,
 * then the default-batch tests (sorted by name) do the same, so every slice gets the same number of named batches and
 * of default tests, give or take one. Each named batch runs on its own after the previous one (a few ticks of set-up
 * each), and the old hash split gave 30 to 38 named batches and 219 to 269 tests per slice, so the slowest slice took
 * about 50 % longer than the quickest. Every runner sees the same test list, so the slices stay disjoint and complete.
 */
@Mixin(GameTestRegistry.class)
public abstract class GameTestFilterMixin {
	@Inject(method = "getAllTestFunctions", at = @At("RETURN"), cancellable = true)
	private static void projecthero$filter(CallbackInfoReturnable<Collection<TestFunction>> cir) {
		String filter = System.getProperty("projecthero.gametest.filter");
		String shard = System.getProperty("projecthero.gametest.shard");
		boolean filtered = filter != null && !filter.isBlank();
		boolean sharded = shard != null && !shard.isBlank();
		if (!filtered && !sharded) {
			return;
		}
		Pattern pattern = filtered ? Pattern.compile(filter.trim(), Pattern.CASE_INSENSITIVE) : null;
		int index = 0;
		int count = 1;
		if (sharded) {
			String[] parts = shard.trim().split("/");
			index = Integer.parseInt(parts[0].trim()) - 1;
			count = Integer.parseInt(parts[1].trim());
			if (count < 1 || index < 0 || index >= count) {
				throw new IllegalArgumentException("projecthero.gametest.shard must be i/n with 1 <= i <= n, got " + shard);
			}
		}
		List<TestFunction> matching = cir.getReturnValue().stream()
				.filter(t -> pattern == null || pattern.matcher(t.testName()).find() || pattern.matcher(t.batchName()).find())
				.toList();
		List<TestFunction> kept = count == 1 ? matching : projecthero$deal(matching, index, count);
		System.out.println("[projecthero-gametest] filter '" + filter + "' shard '" + shard + "' kept " + kept.size() + " of "
				+ cir.getReturnValue().size() + " tests");
		if (kept.isEmpty()) {
			throw new IllegalStateException("projecthero.gametest filter/shard matched no tests");
		}
		cir.setReturnValue(kept);
	}

	/** Slice {@code wanted} (0-based) of {@code count}: the named batches dealt round the slices, then the default tests. */
	private static List<TestFunction> projecthero$deal(List<TestFunction> tests, int wanted, int count) {
		TreeSet<String> namedBatches = new TreeSet<>();
		List<TestFunction> defaults = new ArrayList<>();
		for (TestFunction t : tests) {
			if ("defaultBatch".equals(t.batchName())) {
				defaults.add(t);
			} else {
				namedBatches.add(t.batchName());
			}
		}
		Set<String> myBatches = new HashSet<>();
		int i = 0;
		for (String batch : namedBatches) {
			if (i++ % count == wanted) {
				myBatches.add(batch);
			}
		}
		defaults.sort(Comparator.comparing(TestFunction::testName));
		Set<String> myTests = new HashSet<>();
		for (int j = 0; j < defaults.size(); j++) {
			// carried on from where the named batches stopped, so a slice that got one batch fewer gets the next test
			if ((namedBatches.size() + j) % count == wanted) {
				myTests.add(defaults.get(j).testName());
			}
		}
		return tests.stream()
				.filter(t -> "defaultBatch".equals(t.batchName()) ? myTests.contains(t.testName()) : myBatches.contains(t.batchName()))
				.toList();
	}
}
