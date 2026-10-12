#!/usr/bin/env bash
# v0.16.1: runs one slice of the gametest suite (build.yml "gametest" jobs and release.yml "test" jobs).
#
#   bash .github/scripts/run-gametests.sh <slice i/n> [test filter regex]
#
# If the slice fails, the tests that failed (read from build/reports/gametest.xml) are run once more ON THEIR OWN.
# Passing there means the test was knocked over by a neighbour in the crowded slice (the flakes that killed the
# 0.13.12 / 0.15.13 / 0.15.16 tags), so the slice passes with a warning annotation naming the test. Failing again is
# a real failure. More than MAX_RETRY failures is a real break, not flakes, so that fails straight away.
# The first run's report is kept as build/reports/gametest-first-try.xml (both are uploaded).
set -uo pipefail

shard="$1"
filter="${2:-}"
MAX_RETRY="${MAX_RETRY:-12}"
report=build/reports/gametest.xml

# The Gradle daemon compiles ~210k lines of Java; the project's own 1 GB heap (sized for dev machines) makes javac
# GC-bound on CI. The user-home gradle.properties wins over the project one. (CI runners only -- never touch a dev's.)
if [ "${GITHUB_ACTIONS:-}" = true ]; then
	mkdir -p ~/.gradle
	echo "org.gradle.jvmargs=-Xmx3g -XX:+UseParallelGC -Dfile.encoding=UTF-8" >> ~/.gradle/gradle.properties
fi

args=(runGameTest --console=plain "-PtestShard=$shard")
if [ -n "$filter" ]; then
	args+=("-PtestFilter=$filter")
fi
if ./gradlew "${args[@]}"; then
	exit 0
fi

if [ ! -f "$report" ]; then
	echo "::error::the gametest run failed before writing a report (crash, compile error or timeout)"
	exit 1
fi
cp "$report" build/reports/gametest-first-try.xml
if ! failed="$(node .github/scripts/failed-gametests.js "$report")"; then
	echo "::error::could not read the failed tests from the report"
	exit 1
fi
count="$(printf '%s\n' "$failed" | grep -c . || true)"
if [ "$count" -eq 0 ]; then
	echo "::error::the gametest run failed but its report names no failed test"
	exit 1
fi
if [ "$count" -gt "$MAX_RETRY" ]; then
	echo "::error::$count gametests failed -- too many to be neighbour flakes, not retrying"
	printf '%s\n' "$failed"
	exit 1
fi

regex="^(?:$(printf '%s\n' "$failed" | sed 's/\./\\./g' | paste -sd'|' -))\$"
echo "re-running $count failed test(s) on their own: $regex"
if ./gradlew runGameTest --console=plain "-PtestFilter=$regex"; then
	for name in $failed; do
		echo "::warning title=flaky gametest::$name failed in slice $shard but passed on its own -- it is sensitive to its neighbours"
	done
	exit 0
fi
echo "::error::the failed gametest(s) failed again on their own: real failures"
exit 1
