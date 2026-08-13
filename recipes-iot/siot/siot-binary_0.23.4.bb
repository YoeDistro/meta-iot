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

SRC_URI[x86_64.sha256sum] = "596e037f00e4aa08091ef6dcb391e87f9f74f39d58044e75055686ed48e3c078"
SRC_URI[arm64.sha256sum] = "6ec1f80582ccdaaadcec13ed886a42fe4eb74a3a7a4d92c0687fba5c7922f4b5"
SRC_URI[arm7.sha256sum] = "34c061e2d7a4e8462d00a7d8bafd3edae57cd64971f6ae0e6a41d9f4a762c795"
SRC_URI[arm6.sha256sum] = "97e1c76e12ca1fe892f1277e44fd5c125a3b19d21c42a2774650453d1599a303"
SRC_URI[riscv64.sha256sum] = "23655c412b9d88a9a00c8cd29e41b6bd17f138422b93f303419b19958fb3e63a"

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
