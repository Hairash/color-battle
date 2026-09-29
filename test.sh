#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/tests
javac -d build/tests src/com/example/colorbattle/Game.java src/com/example/colorbattle/Bot.java tests/GameTest.java
java -cp build/tests com.example.colorbattle.GameTest
