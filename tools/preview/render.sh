#!/bin/sh
# Renders lcd-preview.png with plain JDK (no device): tools/preview/render.sh <outdir>
set -e
cd "$(dirname "$0")/../.."
T=$(mktemp -d)
SRC=app/src/main/java/com/zmlworks/calc7seg
javac -d "$T" $SRC/Lcd.java $SRC/Calculator.java tools/preview/LcdPreview.java
java -Djava.awt.headless=true -cp "$T" com.zmlworks.calc7seg.LcdPreview "${1:-.}"
rm -rf "$T"
