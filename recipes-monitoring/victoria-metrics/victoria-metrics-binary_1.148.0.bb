SUMMARY = "VictoriaMetrics time series database and monitoring solution"
DESCRIPTION = "VictoriaMetrics is a fast, cost effective and scalable time \
series database. It speaks the Prometheus remote write and query protocols, \
which makes it a convenient metrics store for IoT gateways and edge devices."
HOMEPAGE = "https://victoriametrics.com"
SECTION = "console/network"

LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

# Upstream release asset naming, keyed off the target architecture
VM_ARCH:x86-64 = "amd64"
VM_ARCH:aarch64 = "arm64"
VM_ARCH:arm = "arm"
VM_ARCH:x86 = "386"

SRC_URI = "https://github.com/VictoriaMetrics/VictoriaMetrics/releases/download/v${PV}/victoria-metrics-linux-${VM_ARCH}-v${PV}.tar.gz;name=${VM_ARCH} \
           file://victoria-metrics.service \
           file://victoria-metrics.default \
          "

SRC_URI[amd64.sha256sum] = "805fef95f7114173da531b1dd6187657a0637b74ebd4492dde5d7a522b6462e3"
SRC_URI[arm64.sha256sum] = "91a737da317c848198e0d4ef89261e02f2484e8a39af2a0ad257e74f6b40a41d"
SRC_URI[arm.sha256sum] = "4dcfe2737424c2746a4fd5f0a1da9fc92116d090ba80cc77faa9f49829b7f769"
SRC_URI[386.sha256sum] = "6cbf5e246c342cf3df37b1be1ed87bb88472d520741f9ed81283bb1a95732b25"

# The release tarball holds a single victoria-metrics-prod binary with no
# leading directory, so unpacking lands everything directly in UNPACKDIR.
S = "${UNPACKDIR}"

inherit bin_package systemd useradd

PROVIDES += "victoria-metrics"
RPROVIDES:${PN} += "victoria-metrics"

VM_DATA_DIR ?= "${localstatedir}/lib/victoria-metrics"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM:${PN} = "--system --no-create-home --home ${VM_DATA_DIR} \
                       --shell /bin/false --user-group victoriametrics"

do_install() {
    install -D -m 0755 ${S}/victoria-metrics-prod ${D}${bindir}/victoria-metrics-prod
    ln -sf victoria-metrics-prod ${D}${bindir}/victoria-metrics

    install -d -m 0750 ${D}${VM_DATA_DIR}
    chown victoriametrics:victoriametrics ${D}${VM_DATA_DIR}

    install -D -m 0644 ${UNPACKDIR}/victoria-metrics.default ${D}${sysconfdir}/default/victoria-metrics

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -D -m 0644 ${UNPACKDIR}/victoria-metrics.service \
            ${D}${systemd_system_unitdir}/victoria-metrics.service
    fi
}

SYSTEMD_SERVICE:${PN} = "victoria-metrics.service"

FILES:${PN} += "${systemd_system_unitdir} ${sysconfdir}/default/victoria-metrics"
FILES:${PN} += "${VM_DATA_DIR}"

CONFFILES:${PN} = "${sysconfdir}/default/victoria-metrics"

# Upstream ships a stripped, statically linked Go binary
INSANE_SKIP:${PN} += "already-stripped ldflags"

# Upstream publishes Linux binaries for these architectures only
COMPATIBLE_HOST = "(x86_64|i.86|arm|aarch64).*-linux"
