# meta-iot

OpenEmbedded/Yocto layer providing IoT components for the
[Yoe Distribution](https://github.com/YoeDistro/yoe-distro).

The recipes here install prebuilt binaries published by upstream projects. This
keeps build times short on the distro side, at the cost of tracking upstream
release cadence by hand.

## Recipes

| Recipe                    | Version | Provides           | Notes                                            |
| ------------------------- | ------- | ------------------ | ------------------------------------------------ |
| `grafana-binary`          | 13.1.1  | `grafana`          | OSS release from `dl.grafana.com`, AGPL-3.0-only |
| `victoria-metrics-binary` | 1.148.0 | `victoria-metrics` | Single-node release, Apache-2.0                  |
| `siot-binary`             | 0.18.5  | `simpleiot`        | [Simple IoT](https://simpleiot.org), Apache-2.0  |

`siot-binary` conflicts with, replaces, and provides the source-built
`simpleiot` recipe in `meta-openembedded/meta-oe`. Install one or the other, not
both. To prefer the binary in an image that pulls in `simpleiot`, add to your
distro or local configuration:

```
PREFERRED_PROVIDER_simpleiot = "siot-binary"
```

## Architectures

Upstream does not publish binaries for every architecture, so each recipe sets
`COMPATIBLE_HOST` accordingly:

| Recipe                    | x86-64 | x86 | aarch64 | arm | riscv64 |
| ------------------------- | ------ | --- | ------- | --- | ------- |
| `grafana-binary`          | yes    | no  | yes     | yes | no      |
| `victoria-metrics-binary` | yes    | yes | yes     | yes | no      |
| `siot-binary`             | yes    | yes | yes     | yes | yes     |

For `arm`, the ARMv6 and ARMv7 release assets are selected from `TUNE_FEATURES`.

## Services

Each recipe installs a systemd unit and a matching `/etc/default` file holding
its runtime settings:

| Service                    | Default listen address | State directory             | Notes                   |
| -------------------------- | ---------------------- | --------------------------- | ----------------------- |
| `grafana-server.service`   | `:3000`                | `/var/lib/grafana`          |                         |
| `victoria-metrics.service` | `:8428`                | `/var/lib/victoria-metrics` | `/vmui` path for web ui |
| `siot.service`             | `:8118`                | `/var/lib/siot`             |                         |

Grafana and VictoriaMetrics run under dedicated system users created by the
`useradd` class. Simple IoT runs as root, matching upstream, since edge
deployments commonly need access to serial and GPIO devices.

Grafana site configuration lives in `/etc/grafana/grafana.ini`. The shipped
`/usr/share/grafana/conf/defaults.ini` is left untouched, as upstream expects.

## Adding the layer

Add to your `bblayers.conf`:

```
BBLAYERS += "${TOPDIR}/sources/meta-iot"
```

Then install what you need, for example:

```
IMAGE_INSTALL:append = " grafana-binary victoria-metrics-binary siot-binary"
```

## Updating a recipe to a new upstream release

1. Rename the recipe to the new version.
2. Refresh the per-architecture `sha256sum` entries from the upstream checksum
   files:
   - Grafana:
     `https://dl.grafana.com/oss/release/grafana-<version>.linux-<arch>.tar.gz.sha256`
   - VictoriaMetrics: the
     `victoria-metrics-linux-<arch>-v<version>_checksums.txt` release asset
   - Simple IoT: the `checksums.txt` release asset
3. Confirm the license file checksum is unchanged.

## License

Metadata in this layer is MIT licensed; see `COPYING.MIT`. The software each
recipe fetches carries its own license, recorded in the recipe.
