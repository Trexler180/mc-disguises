#!/usr/bin/env bash
# Build the mod, run the dev server briefly, and check for startup errors.
# Exit 0 = success, 1 = build failed, 2 = server failed to start cleanly.

set -euo pipefail

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building mod ==="
if ! ./gradlew build --no-daemon -q 2>&1; then
    echo "BUILD FAILED"
    exit 1
fi
echo "Build OK"

echo ""
echo "=== Starting dev server (will auto-stop after 30s or on ready) ==="

# Run the server, pipe 'stop' once we see it's done loading, capture all output.
SERVER_LOG=$(mktemp)
(
    # Give the server 40 seconds to start; send stop as soon as it's done loading
    # or after timeout
    sleep 5
    for i in $(seq 1 35); do
        if grep -q "Done (" "$SERVER_LOG" 2>/dev/null; then
            echo "stop"
            break
        fi
        sleep 1
    done
    echo "stop"   # fallback if grep never matched
) | ./gradlew runServer --no-daemon 2>&1 | tee "$SERVER_LOG" &
GRADLE_PID=$!

# Wait up to 60s for the server to finish (gradle + server startup + stop)
WAITED=0
while kill -0 $GRADLE_PID 2>/dev/null; do
    sleep 2
    WAITED=$((WAITED + 2))
    if [ $WAITED -ge 90 ]; then
        echo "Timeout — killing server"
        kill $GRADLE_PID 2>/dev/null || true
        break
    fi
done

wait $GRADLE_PID 2>/dev/null || true

echo ""
echo "=== Server log analysis ==="

# Check for fatal errors
if grep -E "Exception in thread|FATAL|Failed to start|Could not load" "$SERVER_LOG"; then
    echo "SERVER ERRORS DETECTED"
    rm -f "$SERVER_LOG"
    exit 2
fi

if grep -q "Done (" "$SERVER_LOG"; then
    echo "Server started successfully"
    # Show any WARN/ERROR lines from disguises mod
    grep -iE "\[Disguises\]|\[disguises\]" "$SERVER_LOG" || true
    rm -f "$SERVER_LOG"
    exit 0
else
    echo "Server did not finish loading"
    tail -20 "$SERVER_LOG"
    rm -f "$SERVER_LOG"
    exit 2
fi
