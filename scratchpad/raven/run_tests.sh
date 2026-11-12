#!/bin/sh
# Filtered gametest run(s) with a summary of build/reports/gametest.xml: sh scratchpad/raven/run_tests.sh "<regex>" [times]
n=${2:-1}
i=0
while [ $i -lt $n ]; do
  ./gradlew runGameTest "-PtestFilter=$1" -q > /dev/null 2>&1
  node scratchpad/raven/summary.js
  i=$((i+1))
done
