#!/data/data/com.agyide/files/usr/bin/bash
# Antigravity IDE Shell Initialization (fast, non-blocking, does NOT trap bash into agy)

export GODEBUG="netdns=cgo"
export GIT_TERMINAL_PROMPT=0
export SSL_CERT_FILE="${SSL_CERT_FILE:-$PREFIX/etc/tls/cert.pem}"

# Ensure DNS & localhost configs exist without overwriting user changes on every launch
if [ ! -f "$PREFIX/glibc/etc/hosts" ]; then
    mkdir -p "$PREFIX/etc" "$PREFIX/glibc/etc"
    printf "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n" | tee "$PREFIX/etc/hosts" "$PREFIX/glibc/etc/hosts" >/dev/null
fi
if [ ! -f "$PREFIX/glibc/etc/resolv.conf" ]; then
    printf "nameserver 8.8.8.8\nnameserver 1.1.1.1\n" | tee "$PREFIX/etc/resolv.conf" "$PREFIX/glibc/etc/resolv.conf" >/dev/null
fi
if [ ! -f "$PREFIX/glibc/etc/nsswitch.conf" ]; then
    printf "hosts: files dns\nnetworks: files\n" | tee "$PREFIX/etc/nsswitch.conf" "$PREFIX/glibc/etc/nsswitch.conf" >/dev/null
fi

mkdir -p "$HOME/projects" "$HOME/uploads" "$HOME/storage"

# Link internal storage and USB OTG drives if accessible
[ -d "/storage/emulated/0" ] && [ ! -e "$HOME/storage/shared" ] && ln -sf "/storage/emulated/0" "$HOME/storage/shared" 2>/dev/null || true
[ -d "/storage/emulated/0/Download" ] && [ ! -e "$HOME/storage/downloads" ] && ln -sf "/storage/emulated/0/Download" "$HOME/storage/downloads" 2>/dev/null || true
[ -d "/storage/emulated/0/DCIM" ] && [ ! -e "$HOME/storage/dcim" ] && ln -sf "/storage/emulated/0/DCIM" "$HOME/storage/dcim" 2>/dev/null || true

usb_count=1
for drive in /storage/*; do
    d_base=$(basename "$drive" 2>/dev/null || true)
    if [ "$d_base" != "emulated" ] && [ "$d_base" != "self" ] && [ "$d_base" != "*" ] && [ -d "$drive" ]; then
        [ $usb_count -eq 1 ] && [ ! -e "$HOME/storage/usb" ] && ln -sf "$drive" "$HOME/storage/usb" 2>/dev/null || true
        [ ! -e "$HOME/storage/usb$usb_count" ] && ln -sf "$drive" "$HOME/storage/usb$usb_count" 2>/dev/null || true
        usb_count=$((usb_count + 1))
    fi
done

if [ ! -t 1 ]; then
    return 0 2>/dev/null || exit 0
fi

if [ -z "${AGY_IDE_BANNER_SHOWN:-}" ]; then
    export AGY_IDE_BANNER_SHOWN=1
    printf '\033[38;2;0;240;255m▲ Antigravity IDE Shell\033[0m · \033[38;2;148;163;184mRun `agy` for interactive AI or edit directly in the IDE\033[0m\n'
fi
