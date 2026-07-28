SUMMARY = "Simple IoT edge and cloud application framework (upstream binary release)"
DESCRIPTION = "Simple IoT is a framework for building IoT and connected product \
systems. It runs the same binary at the edge and in the cloud, synchronizing \
state between the two. This recipe installs the prebuilt binary published by \
upstream rather than building from source."
HOMEPAGE = "https://simpleiot.org"
BUGTRACKER = "https://github.com/simpleiot/simpleiot/issues"
SECTION = "console/network"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

# Upstream release asset naming, keyed off the target architecture
SIOT_ARCH:x86-64 = "x86_64"
SIOT_ARCH:aarch64 = "arm64"
SIOT_ARCH:arm = "${@bb.utils.contains_any('TUNE_FEATURES', 'armv7a armv7ve', 'arm7', 'arm6', d)}"
SIOT_ARCH:x86 = "i386"
SIOT_ARCH:riscv64 = "riscv64"

SRC_URI = "https://github.com/simpleiot/simpleiot/releases/download/v${PV}/simpleiot-v${PV}-linux-${SIOT_ARCH}.tar.gz;name=${SIOT_ARCH} \
           file://siot.service \
           file://siot.default \
          "

SRC_URI[x86_64.sha256sum] = "8a94e170b03906c51bc97b52df0517a370ffeb6167909db67d0214a15de51038"
SRC_URI[arm64.sha256sum] = "533e0e3a05992a48cf94bbf39420b414b393086060e09d83971eac66fc03a5bc"
SRC_URI[arm7.sha256sum] = "35b6cc4f3e720e4e08129a6aff9f33a58a9ec527f514453fe2fe62b544a0d8ba"
SRC_URI[arm6.sha256sum] = "7b9156ae2146a42cd0dd3549771b1e4e755eeb980746e3c7fe29d69e715f3611"
SRC_URI[i386.sha256sum] = "900f7e90c978ac9169605843d6374ce70189061047b73df7267b397d8cf27a58"
SRC_URI[riscv64.sha256sum] = "92c307ef99a84dd0154317323dd23f3b82f4ebc4ba439812cbfeb9a550ef0603"

# Upstream tarballs unpack into simpleiot-v<version>-linux-<arch>/
S = "${UNPACKDIR}/simpleiot-v${PV}-linux-${SIOT_ARCH}"

inherit bin_package systemd

# Drop-in replacement for the source-built simpleiot recipe in meta-oe
PROVIDES += "simpleiot"
RPROVIDES:${PN} += "simpleiot"
RCONFLICTS:${PN} += "simpleiot"
RREPLACES:${PN} += "simpleiot"

SIOT_DATA_DIR ?= "/data"

do_install() {
    install -D -m 0755 ${S}/siot ${D}${bindir}/siot

    install -d -m 0755 ${D}${SIOT_DATA_DIR}

    install -D -m 0644 ${UNPACKDIR}/siot.default ${D}${sysconfdir}/default/siot

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -D -m 0644 ${UNPACKDIR}/siot.service ${D}${systemd_system_unitdir}/siot.service
    fi
}

SYSTEMD_SERVICE:${PN} = "siot.service"

FILES:${PN} += "${systemd_system_unitdir} ${sysconfdir}/default/siot ${SIOT_DATA_DIR}"

CONFFILES:${PN} = "${sysconfdir}/default/siot"

# Upstream ships a stripped, statically linked Go binary
INSANE_SKIP:${PN} += "already-stripped ldflags"

# Upstream publishes Linux binaries for these architectures only
COMPATIBLE_HOST = "(x86_64|i.86|arm|aarch64|riscv64).*-linux"
