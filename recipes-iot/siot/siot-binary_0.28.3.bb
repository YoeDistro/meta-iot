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

SRC_URI[x86_64.sha256sum] = "757ed25c0dcfc47996f15c7b448c2510084f3f2c1d9d13ce807893078b534f5d"
SRC_URI[arm64.sha256sum] = "0a87e7d088c9b5c53c65b262e3c6f116eb5e81d6b762bf6cb87b3be7c4f85da2"
SRC_URI[arm7.sha256sum] = "a7ed165b73cbcf0df43a0ddebcbcf03b1dd6ddafb6546bf9a7f3fe239f5185c2"
SRC_URI[arm6.sha256sum] = "ed2935a0342b492b49e8e2bd2e6519ac640fbe7957f433f788cf607229e0833c"
SRC_URI[riscv64.sha256sum] = "89a11f77d359ad4fe505713509f39538d9c1aabbc793452b2cc3e8d98e8f9c01"

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

# History kept per subject in the JetStream store, in points. Retention is per
# subject, so the current value of every point is preserved and only older
# history is trimmed. Left empty, the shipped /etc/default/siot leaves the
# setting commented out and SimpleIoT keeps its own default of 5000; -1 keeps
# everything. A distro, machine, or layer that wants a different depth sets
# this, for example:
#
#   SIOT_STORE_MAX_MSGS_PER_SUBJECT = "90000"
SIOT_STORE_MAX_MSGS_PER_SUBJECT ?= ""

do_install() {
    install -D -m 0755 ${UNPACKDIR}/${SIOT_BINARY} ${D}${bindir}/siot

    install -D -m 0644 ${UNPACKDIR}/siot.default ${D}${sysconfdir}/default/siot

    if [ -n "${SIOT_STORE_MAX_MSGS_PER_SUBJECT}" ]; then
        sed -i -e 's|^#*SIOT_STORE_MAX_MSGS_PER_SUBJECT=.*|SIOT_STORE_MAX_MSGS_PER_SUBJECT=${SIOT_STORE_MAX_MSGS_PER_SUBJECT}|' \
            ${D}${sysconfdir}/default/siot
        grep -q "^SIOT_STORE_MAX_MSGS_PER_SUBJECT=${SIOT_STORE_MAX_MSGS_PER_SUBJECT}\$" \
            ${D}${sysconfdir}/default/siot || \
            bbfatal "siot.default has no SIOT_STORE_MAX_MSGS_PER_SUBJECT line to set"
    fi

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
