SUMMARY = "Simple IoT edge and cloud application framework (upstream binary release)"
DESCRIPTION = "Simple IoT is a framework for building IoT and connected product \
systems. It runs the same binary at the edge and in the cloud, synchronizing \
state between the two. This recipe installs the prebuilt binary published by \
upstream rather than building from source."
HOMEPAGE = "https://simpleiot.org"
BUGTRACKER = "https://github.com/simpleiot/simpleiot/issues"
SECTION = "console/network"

LICENSE = "Apache-2.0"
# The release assets are bare executables, so there is no LICENSE file to
# check against. Use the copy that comes with OE-core instead.
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

# Upstream release asset naming, keyed off the target architecture
SIOT_ARCH:x86-64 = "x86_64"
SIOT_ARCH:aarch64 = "arm64"
SIOT_ARCH:arm = "${@bb.utils.contains_any('TUNE_FEATURES', 'armv7a armv7ve', 'arm7', 'arm6', d)}"
SIOT_ARCH:riscv64 = "riscv64"

# As of v0.19.0 upstream publishes the executable directly rather than wrapping
# it in a tarball, so the fetcher copies the asset into ${UNPACKDIR} unchanged.
SIOT_BINARY = "simpleiot-v${PV}-linux-${SIOT_ARCH}"

SRC_URI = "https://github.com/simpleiot/simpleiot/releases/download/v${PV}/${SIOT_BINARY};name=${SIOT_ARCH} \
           file://siot.service \
           file://siot.default \
          "

SRC_URI[x86_64.sha256sum] = "e6b06363885d4d1857005baa3499b720436b55a673d6fa911154c590cdc6c9ad"
SRC_URI[arm64.sha256sum] = "897f493d769802bb84ab7fbbc552d65077d3e1f15cf66b7ad35a4bbc8f90e462"
SRC_URI[arm7.sha256sum] = "08a8aa04ee7324dc987ccf34c183e6670d0862783cb5431c62af0e92c1dffad1"
SRC_URI[arm6.sha256sum] = "a5757c4f210dba521b6b59daa867ab30627a4c33272d3a3a3567e2c8c96a11f2"
SRC_URI[riscv64.sha256sum] = "b6f758d4127bf77f3d9fbf93fddf4e7314a584c24be4f482c85acb2355a3c1b4"

S = "${UNPACKDIR}"

inherit bin_package systemd

# Drop-in replacement for the source-built simpleiot recipe in meta-oe
PROVIDES += "simpleiot"
RPROVIDES:${PN} += "simpleiot"
RCONFLICTS:${PN} += "simpleiot"
RREPLACES:${PN} += "simpleiot"

# The store directory is not packaged here. SIOT_DATA in /etc/default/siot
# points it at the /data partition, which is a mount point, so anything the
# rootfs shipped underneath would be covered. siot.service creates it.

do_install() {
    install -D -m 0755 ${UNPACKDIR}/${SIOT_BINARY} ${D}${bindir}/siot

    install -D -m 0644 ${UNPACKDIR}/siot.default ${D}${sysconfdir}/default/siot

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -D -m 0644 ${UNPACKDIR}/siot.service ${D}${systemd_system_unitdir}/siot.service
    fi
}

SYSTEMD_SERVICE:${PN} = "siot.service"

FILES:${PN} += "${systemd_system_unitdir} ${sysconfdir}/default/siot"

CONFFILES:${PN} = "${sysconfdir}/default/siot"

# Upstream ships a stripped, statically linked Go binary
INSANE_SKIP:${PN} += "already-stripped ldflags"

# Upstream publishes Linux binaries for these architectures only
COMPATIBLE_HOST = "(x86_64|arm|aarch64|riscv64).*-linux"
