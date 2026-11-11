#!/usr/bin/env sh
#
# adb-info.sh - collect technical information from an Ocilion P610 (or any
# Android TV box) over ADB.
#
# Read-only: this script only *queries* the device. It does not install,
# disable, or modify anything.
#
# Usage:
#   ./scripts/adb-info.sh                 # uses the single attached device
#   ./scripts/adb-info.sh HOST:PORT       # connect first, e.g. 192.168.0.50:5555
#   ADB_DEVICE=192.168.0.50:5555 ./scripts/adb-info.sh
#
# Output: prints a report to stdout and saves it to ./adb-info-<timestamp>.txt
#
set -eu

# ---------------------------------------------------------------- helpers
have() { command -v "$1" >/dev/null 2>&1; }
TS=$(date +%Y%m%d-%H%M%S)
OUT="adb-info-${TS}.txt"

section() {
    printf '\n========================================\n'
    printf '  %s\n' "$1"
    printf '========================================\n'
}

# run one command on the device, print label + output
q() {
    # $1 = label, $2 = shell command on device
    printf -- '- %s\n' "$1"
    adb ${DEVICE:+-s "$DEVICE"} shell "$2" 2>&1 | sed 's/^/    /' || true
    printf '\n'
}

# ---------------------------------------------------------------- preflight
if ! have adb; then
    echo "error: adb not found in PATH" >&2
    exit 1
fi

DEVICE="${ADB_DEVICE:-}"
CONNECTED=0
if [ $# -ge 1 ] && [ -z "$DEVICE" ]; then
    DEVICE="$1"
fi

if [ -n "$DEVICE" ]; then
    echo "connecting to $DEVICE ..."
    adb connect "$DEVICE" >/dev/null 2>&1 || true
    adb -s "$DEVICE" shell true >/dev/null 2>&1 && CONNECTED=1
else
    # fall back to exactly one already-attached device
    N=$(adb devices | awk '/\tdevice$/ {c++} END {print c+0}')
    if [ "$N" -ge 1 ]; then
        DEVICE=$(adb devices | awk '/\tdevice$/ {print $1; exit}')
        CONNECTED=1
    fi
fi

if [ "$CONNECTED" != 1 ]; then
    echo "error: no ADB device available." >&2
    echo "hint: adb connect <ip>:5555  (Wi-Fi debugging) or plug in via USB." >&2
    exit 1
fi

if [ -n "$DEVICE" ]; then
    ADB="adb -s $DEVICE"
else
    ADB="adb"
fi

{
    section "Device: $DEVICE  (captured $(date -u '+%Y-%m-%d %H:%M:%S UTC'))"

    section "Identity & build"
    q "model"            'getprop ro.product.model; getprop ro.product.brand; getprop ro.product.manufacturer'
    q "codename/device"  'getprop ro.product.name; getprop ro.product.device; getprop ro.product.board'
    q "fingerprint"      'getprop ro.build.fingerprint; getprop ro.build.reference.fingerprint'
    q "android version"  'getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.version.security_patch'
    q "build"            'getprop ro.build.id; getprop ro.build.version.incremental; getprop ro.build.type; getprop ro.build.tags; getprop ro.build.flavor; getprop ro.build.description'
    q "gms version"      'getprop ro.com.google.gmsversion; getprop ro.com.google.clientidbase'

    section "Kernel"
    q "uname -a"         'uname -a'
    q "kernel arch"      'uname -m; getconf LONG_BIT; getprop ro.zygote'
    q "abi (32/64-bit)"  'getprop ro.product.cpu.abilist; getprop ro.product.cpu.abilist64'

    section "CPU"
    q "cores"            'ls /sys/devices/system/cpu/ | grep -E "^cpu[0-9]+$"; cat /sys/devices/system/cpu/online'
    q "max freq (kHz)"   'cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq'
    q "cur freq (kHz)"   'cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq'
    q "governor"         'cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor'
    q "model/features"   'grep -m1 "model name" /proc/cpuinfo; grep -m1 "Features" /proc/cpuinfo'

    section "RAM"
    q "ddr size"         'getprop ro.boot.ddr_size'
    q "meminfo"          'head -5 /proc/meminfo'

    section "GPU"
    q "egl driver"       'getprop ro.hardware.egl; getprop ro.opengles.version'
    q "dri nodes"        'ls /dev/dri/'
    q "mali node"        'ls /sys/class/misc/ | grep -i mali'

    section "Storage & partitions"
    q "df -h"            'df -h'
    q "mount (system)"   'mount | grep -E "erofs|f2fs|ext4|vfat" | head -30'
    q "block by-name"    'ls /dev/block/by-name/'
    q "boot devices"     'getprop ro.boot.boot_devices'
    q "eMMC size (bytes)" 'blockdev --getsize64 /dev/block/mmcblk0 2>/dev/null || echo "n/a (needs permission)"'

    section "Security & verified boot"
    q "verified boot"    'getprop ro.boot.verifiedbootstate; getprop ro.boot.flash.locked; getprop ro.boot.vbmeta.device_state'
    q "verity/selinux"   'getprop ro.boot.veritymode; getprop ro.boot.selinux; getprop ro.secure'
    q "avb / rollback"   'getprop ro.boot.avb_version; getprop ro.boot.rollback; getprop ro.boot.rpmb_state'
    q "odsign"           'getprop odsign.verification.done; getprop odsign.verification.success'
    q "root check"       'id; ls /system/bin/su /system/xbin/su /sbin/su 2>/dev/null || echo "no su binary"'
    q "a/b slots"        'getprop ro.boot.slot_suffix; getprop ro.build.ab_update'

    section "Bootloader & update"
    q "bootloader"       'getprop ro.bootloader; getprop ro.boot.bootreason'
    q "update engine"    'getprop init.svc.update_engine'
    q "boot reason hist" 'getprop persist.sys.boot.reason.history'

    section "Video codecs (hardware)"
    q "video xmls"       'ls /vendor/etc/media_codecs*.xml'
    q "video decoders"   'grep -hoE "name=\"c2\.[^\"]+\"" /vendor/etc/media_codecs*.xml 2>/dev/null | sort -u'
    q "dolby vision xml" 'ls /vendor/etc/media_codecs*dolby*.xml 2>/dev/null || echo "none"'

    section "Display / HDMI"
    q "sf max res"       'getprop ro.surface_flinger.max_graphics_width; getprop ro.surface_flinger.max_graphics_height'
    q "drm outputs"      'ls /sys/class/drm/'
    q "cec"              'cat /sys/class/cec/cec_version 2>/dev/null; cat /sys/class/cec/device_type 2>/dev/null; getprop ro.hdmi.device_type'
    q "active display"   'dumpsys display 2>/dev/null | grep -m1 "DisplayDeviceInfo" | cut -c1-200'

    section "Wi-Fi"
    q "interfaces"       'ip -o link | awk "{print \$2}"'
    q "wifi state"       'dumpsys wifi 2>/dev/null | grep -m1 "mWifiInfo" | cut -c1-300'

    section "Bluetooth"
    q "bt adapter"       'dumpsys bluetooth_manager 2>/dev/null | grep -m1 "address:"'

    section "Ethernet"
    q "eth0"             'ip -o addr show eth0 2>/dev/null || echo "eth0 not up / not present"'

    section "DRM"
    q "widevine apex"    'ls /apex/ | grep -i widevine || echo "no widevine apex"'
    q "drm services"     'getprop init.svc.drm; getprop init.svc.mediadrm'
    q "protected buffers" 'dumpsys display 2>/dev/null | grep -m1 "FLAG_SUPPORTS_PROTECTED_BUFFERS" | cut -c1-120 || echo "n/a"'

    section "Launchers & HOME"
    q "default HOME"     'cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep -E "packageName|name=" | head -6'
    q "HOME registrants" 'cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep packageName | sort -u'
    q "ocilion launcher" 'dumpsys package com.ocilion.p6 2>/dev/null | grep -E "versionName|sourceDir" | head -3 || echo "com.ocilion.p6 not found"'

    section "Packages (summary)"
    q "total"            'pm list packages | wc -l'
    q "3rd party (/data)" 'pm list packages -3'
    q "ocilion/sdt/droidlogic/ruwido" 'pm list packages | grep -iE "ocilion|sdt|droidlogic|ruwido" | sort'
    q "google packages"  'pm list packages | grep "^package:com.google" | wc -l'

    section "Running init services"
    q "services"         'getprop | grep "^init.svc" | grep -v stopped | sort'

    section "Done"
    echo "    End of report."
} | tee "$OUT"

echo
echo "Report saved to: $OUT"
