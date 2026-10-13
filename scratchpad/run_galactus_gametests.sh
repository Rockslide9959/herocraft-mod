#!/bin/sh
# Runs only GalactusGameTests locally (run from the worktree root). Output: build/galactus_gametests.log
./gradlew runGameTest '-PtestFilter=^(?:galactusgametests\..*)$' > build/galactus_gametests.log 2>&1
echo "exit $?"
grep -iE "failed|passed|required tests|tests? (did|were)" build/galactus_gametests.log | head -40
