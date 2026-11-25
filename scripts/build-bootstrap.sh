#!/usr/bin/env bash
set -euo pipefail

# Builds the self-contained ARM64 Linux + glibc + Termux bootstrap zip
# packaged into app/src/main/assets/bootstrap-aarch64.zip.

PREBUNDLE="${1:-false}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSETS_DIR="$ROOT_DIR/app/src/main/assets"
NEW_PKG="com.agyide"

mkdir -p "$ASSETS_DIR"

BOOTSTRAP_VER="2026.02.12-r1%2Bapt.android-7"
BOOTSTRAP_URL="https://github.com/termux/termux-packages/releases/download/bootstrap-${BOOTSTRAP_VER}/bootstrap-aarch64.zip"

TMP_WORK="$(mktemp -d)"
trap 'rm -rf "$TMP_WORK"' EXIT

echo "=== Building Antigravity IDE Bootstrap ($NEW_PKG, prebundle=$PREBUNDLE) ==="
curl -fsSL -o "$TMP_WORK/bootstrap-aarch64.zip" "$BOOTSTRAP_URL"

mkdir -p "$TMP_WORK/extracted"
unzip -q "$TMP_WORK/bootstrap-aarch64.zip" -d "$TMP_WORK/extracted"

# Inject glibc 2.44 aarch64 runtime + DNS configs
python3 "$SCRIPT_DIR/inject_glibc.py" "$TMP_WORK/extracted"

# Inject IDE shell profile and utilities
mkdir -p "$TMP_WORK/extracted/etc/profile.d" "$TMP_WORK/extracted/home/.termux"
cp "$SCRIPT_DIR/ide-init.sh" "$TMP_WORK/extracted/etc/profile.d/00-antigravity-ide.sh"
chmod 755 "$TMP_WORK/extracted/etc/profile.d/00-antigravity-ide.sh"

cp "$SCRIPT_DIR/agy-device" "$TMP_WORK/extracted/bin/agy-device"
chmod 755 "$TMP_WORK/extracted/bin/agy-device"
ln -sf agy-device "$TMP_WORK/extracted/bin/device"
ln -sf agy-device "$TMP_WORK/extracted/bin/control"

cp "$SCRIPT_DIR/patch-faccessat" "$TMP_WORK/extracted/bin/patch-faccessat"
chmod 755 "$TMP_WORK/extracted/bin/patch-faccessat"

if [ "$PREBUNDLE" = "true" ]; then
    echo "[*] Pre-bundling Antigravity CLI engine (agy + agy.va39)..."
    curl -fsSL -o "$TMP_WORK/antigravity.tar.gz" \
      "https://github.com/wallentx/antigravity-cli-termux/releases/latest/download/antigravity-termux-standalone.tar.gz"
    tar -xzf "$TMP_WORK/antigravity.tar.gz" -C "$TMP_WORK/extracted/bin" agy agy.va39
    chmod 755 "$TMP_WORK/extracted/bin/agy"*
fi

# Convert all filesystem symlinks (in glibc/ and bin/) into SYMLINKS.txt entries
# so ZipInputStream extraction on Android recreates real POSIX symlinks cleanly.
python3 - "$TMP_WORK/extracted" << 'PYEOF'
import os, sys

root = sys.argv[1]
symlinks_file = os.path.join(root, "SYMLINKS.txt")
existing = set()
lines = []
if os.path.exists(symlinks_file):
    with open(symlinks_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line:
                existing.add(line)
                lines.append(line)

added = 0
for dirpath, dirnames, filenames in os.walk(root, followlinks=False):
    for name in filenames + dirnames:
        full = os.path.join(dirpath, name)
        if os.path.islink(full):
            target = os.readlink(full)
            rel = os.path.relpath(full, root).replace("\\", "/")
            entry = f"{target}←{rel}"
            if entry not in existing:
                lines.append(entry)
                existing.add(entry)
                added += 1
            os.unlink(full)

with open(symlinks_file, "w", encoding="utf-8") as f:
    for line in lines:
        f.write(line + "\n")

print(f"[✓] Recorded {added} additional symlinks in SYMLINKS.txt (total {len(lines)})")
PYEOF

echo "[*] Creating bootstrap archive..."
(
    cd "$TMP_WORK/extracted"
    zip -r -q "$TMP_WORK/bootstrap-unpatched.zip" .
)

echo "[*] Patching com.termux -> $NEW_PKG references in bootstrap..."
python3 "$SCRIPT_DIR/patch_bootstrap.py" "$TMP_WORK/bootstrap-unpatched.zip" "$ASSETS_DIR/bootstrap-aarch64.zip"

python3 - "$ASSETS_DIR/bootstrap-aarch64.zip" "$NEW_PKG" << 'PYEOF'
import sys, zipfile

zf = zipfile.ZipFile(sys.argv[1])
pkg = sys.argv[2].encode()
names = zf.namelist()
assert "SYMLINKS.txt" in names, "SYMLINKS.txt missing!"
assert any(n.startswith("glibc/") for n in names), "glibc missing!"
assert "glibc/etc/hosts" in names, "glibc/etc/hosts missing!"
assert "glibc/etc/resolv.conf" in names, "glibc/etc/resolv.conf missing!"
login = zf.read("bin/login")
assert pkg in login, f"{pkg} missing in bin/login!"
assert b"com.termux" not in login, "com.termux still in bin/login!"
print(f"[✓] Verified bootstrap archive ({len(names)} entries) for {pkg.decode()}")
PYEOF

ls -lh "$ASSETS_DIR/bootstrap-aarch64.zip"
