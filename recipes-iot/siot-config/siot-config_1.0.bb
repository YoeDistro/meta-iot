SUMMARY = "Simple IoT node configuration, imported once on first boot"
DESCRIPTION = "Ships a versioned Simple IoT configuration in YAML form along \
with a service that imports it into the local instance one time. The shipped \
configuration adds a Database node writing to VictoriaMetrics and the metrics \
nodes that collect host, application, and process data."
HOMEPAGE = "https://docs.simpleiot.org/docs/user/configuration.html"
SECTION = "console/network"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://siot-config.yml \
           file://siot-config-import \
           file://siot-config.service \
          "

inherit allarch systemd

# Either provider of the siot command works here
RDEPENDS:${PN} = "siot-binary"

SIOT_CONFIG_DIR ?= "${sysconfdir}/siot"

do_install() {
    install -D -m 0644 ${UNPACKDIR}/siot-config.yml ${D}${SIOT_CONFIG_DIR}/config.yml

    # On PATH rather than in libexec: --force makes this a command an
    # operator runs by hand to import a revised configuration.
    install -D -m 0755 ${UNPACKDIR}/siot-config-import ${D}${bindir}/siot-config-import

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -D -m 0644 ${UNPACKDIR}/siot-config.service ${D}${systemd_system_unitdir}/siot-config.service
    fi
}

SYSTEMD_SERVICE:${PN} = "siot-config.service"

FILES:${PN} += "${systemd_system_unitdir} ${SIOT_CONFIG_DIR}"

CONFFILES:${PN} = "${SIOT_CONFIG_DIR}/config.yml"
