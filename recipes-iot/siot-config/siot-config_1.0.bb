SUMMARY = "Simple IoT node configuration, applied by the built-in provisioning"
DESCRIPTION = "Ships a versioned Simple IoT configuration in YAML form as a \
provisioning file, which the instance applies at start-up and whenever the \
file changes. The shipped configuration adds a Database node writing to \
VictoriaMetrics and the metrics nodes that collect host, application, and \
process data."
HOMEPAGE = "https://docs.simpleiot.org/docs/user/configuration.html"
SECTION = "console/network"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://siot-config.yml \
           file://siot-provisioning.conf \
          "

# The recipe ships local files rather than a source tree, so unpacking lands
# everything directly in UNPACKDIR.
S = "${UNPACKDIR}"

inherit allarch

# Either provider of the siot command works here
RDEPENDS:${PN} = "siot-binary"

SIOT_CONFIG_DIR ?= "${sysconfdir}/siot"
SIOT_PROVISIONING_DIR ?= "${SIOT_CONFIG_DIR}/provisioning"

do_install() {
    # Provisioning applies the files in this directory in lexical order, so
    # the numeric prefix leaves room for a layer to sequence its own files
    # around this one.
    install -D -m 0644 ${UNPACKDIR}/siot-config.yml ${D}${SIOT_PROVISIONING_DIR}/10-config.yml

    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -D -m 0644 ${UNPACKDIR}/siot-provisioning.conf \
            ${D}${systemd_system_unitdir}/siot.service.d/10-provisioning.conf
    fi
}

FILES:${PN} += "${systemd_system_unitdir} ${SIOT_CONFIG_DIR}"

CONFFILES:${PN} = "${SIOT_PROVISIONING_DIR}/10-config.yml"
