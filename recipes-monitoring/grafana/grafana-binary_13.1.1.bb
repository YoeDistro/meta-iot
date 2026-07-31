SUMMARY = "Grafana observability and data visualization platform"
DESCRIPTION = "Grafana lets you query, visualize and alert on metrics from \
sources such as VictoriaMetrics and Prometheus. This recipe installs the \
prebuilt OSS binary release published by upstream rather than building from \
source."
HOMEPAGE = "https://grafana.com"
BUGTRACKER = "https://github.com/grafana/grafana/issues"
SECTION = "console/network"

LICENSE = "AGPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=eb1e647870add0502f8f010b19de32af"

# Upstream release asset naming, keyed off the target architecture
GRAFANA_ARCH:x86-64 = "amd64"
GRAFANA_ARCH:aarch64 = "arm64"
GRAFANA_ARCH:arm = "${@bb.utils.contains_any('TUNE_FEATURES', 'armv7a armv7ve', 'armv7', 'armv6', d)}"

SRC_URI = "https://dl.grafana.com/oss/release/grafana-${PV}.linux-${GRAFANA_ARCH}.tar.gz;name=${GRAFANA_ARCH} \
           file://grafana-server.service \
           file://grafana-server.default \
          "

SRC_URI[amd64.sha256sum] = "0c07116968aea49768af8babd3c3f162d19012655a1a220cd7a9d97efe91da6c"
SRC_URI[arm64.sha256sum] = "8403dc0b5f50126fbe16d1a9c88f655823d104a8d9830335e572cd30dd0fffff"
SRC_URI[armv7.sha256sum] = "aea42ec281aab4e774757dfac679f5709c02a158eaf9ce067bf53f5cb7d691fc"
SRC_URI[armv6.sha256sum] = "071c170899c5e92e5d29521b2fc5f2921bc1dba529c0b822d79155436182437b"

# Upstream tarballs unpack into grafana-<version>/
S = "${UNPACKDIR}/grafana-${PV}"

inherit bin_package systemd useradd

PROVIDES += "grafana"
RPROVIDES:${PN} += "grafana"

GRAFANA_HOME ?= "${datadir}/grafana"
# The data directory is not packaged here. DATA_DIR in
# /etc/default/grafana-server points it at the /data partition, which is a
# mount point, so anything the rootfs shipped underneath would be covered.
# grafana-server.service creates it. Logs stay under /var/log.
GRAFANA_LOG_DIR ?= "${localstatedir}/log/grafana"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM:${PN} = "--system --no-create-home --home ${GRAFANA_HOME} \
                       --shell /bin/false --user-group grafana"

do_install() {
    install -d ${D}${GRAFANA_HOME}

    # The server resolves its web assets and bundled plugins relative to
    # GRAFANA_HOME, so keep the upstream layout intact under there.
    cp -R --no-dereference --preserve=mode,links ${S}/bin ${D}${GRAFANA_HOME}/
    cp -R --no-dereference --preserve=mode,links ${S}/conf ${D}${GRAFANA_HOME}/
    cp -R --no-dereference --preserve=mode,links ${S}/public ${D}${GRAFANA_HOME}/
    cp -R --no-dereference --preserve=mode,links ${S}/plugins-bundled ${D}${GRAFANA_HOME}/
    cp ${S}/VERSION ${D}${GRAFANA_HOME}/

    install -d ${D}${bindir}
    ln -sf ${GRAFANA_HOME}/bin/grafana ${D}${bindir}/grafana

    # Site configuration lives in /etc/grafana; grafana.ini overrides the
    # shipped defaults.ini, which upstream expects to stay unmodified.
    install -D -m 0640 ${S}/conf/sample.ini ${D}${sysconfdir}/grafana/grafana.ini
    install -d ${D}${sysconfdir}/grafana/provisioning/datasources
    install -d ${D}${sysconfdir}/grafana/provisioning/dashboards
    install -d ${D}${sysconfdir}/grafana/provisioning/notifiers
    install -d ${D}${sysconfdir}/grafana/provisioning/plugins
    install -d ${D}${sysconfdir}/grafana/provisioning/alerting
    install -d ${D}${sysconfdir}/grafana/provisioning/access-control

    install -d -m 0750 ${D}${GRAFANA_LOG_DIR}
    chown -R grafana:grafana ${D}${GRAFANA_LOG_DIR}
    chown root:grafana ${D}${sysconfdir}/grafana/grafana.ini

    install -D -m 0644 ${UNPACKDIR}/grafana-server.default ${D}${sysconfdir}/default/grafana-server

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -D -m 0644 ${UNPACKDIR}/grafana-server.service \
            ${D}${systemd_system_unitdir}/grafana-server.service
    fi
}

SYSTEMD_SERVICE:${PN} = "grafana-server.service"

FILES:${PN} += "${GRAFANA_HOME} ${sysconfdir}/grafana ${sysconfdir}/default/grafana-server \
                ${GRAFANA_LOG_DIR} ${systemd_system_unitdir}"

CONFFILES:${PN} = "${sysconfdir}/grafana/grafana.ini ${sysconfdir}/default/grafana-server"

# Upstream ships a prebuilt, stripped Go binary, so it carries neither our
# LDFLAGS nor a separate debug section. The tree also includes helper scripts
# whose interpreters are resolved at runtime rather than through package deps.
INSANE_SKIP:${PN} += "already-stripped ldflags file-rdeps"

INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

# Upstream publishes Linux binaries for these architectures only
COMPATIBLE_HOST = "(x86_64|arm|aarch64).*-linux"
