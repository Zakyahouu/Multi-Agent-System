#!/bin/bash

# ============================================================
# SmartFarm V2 - Multi-Agent Simulation Launcher (macOS)
# ============================================================

echo "============================================================"
echo "       SMARTFARM V2 - MULTI-AGENT SIMULATION"
echo "============================================================"
echo ""

# Configuration
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/SmartFarmV2"
OUT_DIR="$PROJECT_DIR/out/SmartFarmV2"
LIB_DIR="$PROJECT_DIR/lib/javalin"
RES_DIR="$SRC_DIR/resources/public"

# JADE Jar location - Update this if your JADE location is different
JADE_JAR="$PROJECT_DIR/JADE/JADE-all-4.6.0/jade/lib/jade.jar"

echo "[Build] JADE location: $JADE_JAR"
echo ""

# Create output directories
if [ ! -d "$OUT_DIR" ]; then
    mkdir -p "$OUT_DIR"
fi
if [ ! -d "$OUT_DIR/public" ]; then
    mkdir -p "$OUT_DIR/public"
fi

# Initialize classpath with JADE
FULL_CP="$JADE_JAR"

# Add all Javalin jars
for jar in "$LIB_DIR"/*.jar; do
    FULL_CP="$FULL_CP:$jar"
done

# Add output directory
FULL_CP="$FULL_CP:$OUT_DIR"

echo "[Build] Compiling SmartFarmV2 sources..."

# Find all Java files and compile them
# Using find command to get all java files recursively
find "$SRC_DIR" -name "*.java" > "$OUT_DIR/sources.txt"

if [ ! -s "$OUT_DIR/sources.txt" ]; then
    echo "[ERROR] No source files found in $SRC_DIR"
    exit 1
fi

javac -encoding UTF-8 -d "$OUT_DIR" -cp "$FULL_CP" @"$OUT_DIR/sources.txt"

if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed!"
    exit 1
fi

# Copy HTML resources
echo "[Build] Copying resources..."
# Copy all files from public directory
if [ -d "$RES_DIR" ]; then
    cp -R "$RES_DIR/"* "$OUT_DIR/public/"
    echo "[Build] Copied all resources from $RES_DIR"
fi

echo "[Build] Compilation successful."
echo ""

echo "[Run] Starting SmartFarmV2..."
echo ""

# Run
java -Djade.gui=true -Djade.sniffer=true -cp "$FULL_CP" com.smartfarm.Main
