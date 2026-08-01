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

SRC_URI[x86_64.sha256sum] = "2b5639ea1980bb25c7846d112fc79ccd9f3ee999d8750c747600269cabb78357"
SRC_URI[arm64.sha256sum] = "855c8dcff071663d080d3a04b46c65f236f4dc24bfe6e092c448ef4f14c03a91"
SRC_URI[arm7.sha256sum] = "145f00fd5e9496c228f8ba6a3fc060813ee6aade47e74aabbc65759d6d2ba847"
SRC_URI[arm6.sha256sum] = "37631f8ae69a83d144faebab3e402229efd986016956d5010461a6a4c88963c3"
SRC_URI[riscv64.sha256sum] = "f6fc903d21682edc2154e5c3d5f764b7a3ad51c02622eef4764e1d24259c7aa9"

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
