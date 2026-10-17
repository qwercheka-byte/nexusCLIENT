#!/bin/sh
# NEXUS Launcher (portable). Uses the bundled Java runtime from the "runtime" folder.
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/runtime/bin/java" -jar "$DIR/nexus-launcher.jar" "$@"
