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

## Sending Simple IoT data to VictoriaMetrics

The Simple IoT `Database` client writes points with the InfluxDB 2 line
protocol, and VictoriaMetrics accepts that protocol at `/api/v2/write`, so the
two services work together without a bridge.

In the Simple IoT portal (`http://<device>:8118`), add a `Database` node as a
child of the node whose subtree should be recorded. The client records points
published below its parent, so attaching it to the root node captures
everything. Configure the node with:

| Field           | Value                                                    |
| --------------- | -------------------------------------------------------- |
| URI             | `http://localhost:8428`                                  |
| Org             | any value, or blank; VictoriaMetrics ignores it          |
| Bucket          | any value, or blank; VictoriaMetrics ignores it          |
| Auth token      | blank, unless a proxy in front of the database wants one |
| Tag point types | optional; point types to record as tags on each sample   |

Use `http` rather than `https`. VictoriaMetrics serves plain HTTP on `:8428`
unless TLS is configured, while the portal's placeholder text for this field
suggests an `https` URL. A TLS mismatch appears in the journal as
`server gave HTTP response to HTTPS client`. Editing the URI rebuilds the write
connection, so the change takes effect without restarting `siot.service`.

VictoriaMetrics has no organizations or buckets, which is why those two fields
carry no meaning here. Its cluster version expresses tenancy in the URL path
instead.

Points land in the `points` measurement, which VictoriaMetrics maps to the
metric names `points_value` and `points_text`, labeled with:

| Label                                      | Source                     |
| ------------------------------------------ | -------------------------- |
| `type`, `key`                              | the point's type and key   |
| `node.id`, `node.description`, `node.type` | the node holding the point |
| `node.<point type>.<point key>`            | each configured tag point  |

Grafana reads this through the VictoriaMetrics or Prometheus data source
pointed at `http://localhost:8428`:

```
points_value{type="temp", "node.description"="Sensor 1"}
```

Label names contain dots, so queries need the quoted label syntax, which means
writing them in Grafana's code editor rather than the query builder.

VictoriaMetrics stores numeric samples and converts other field values to zero,
so the text field of every point becomes a `points_text` series of zeros. Text
point values are better served by InfluxDB. To leave the zeros out, create
`/etc/victoria-metrics/relabel.yml`:

```yaml
- if: '{__name__="points_text"}'
  action: drop
```

and reference it from `/etc/default/victoria-metrics`:

```
VM_OPTS=-relabelConfig=/etc/victoria-metrics/relabel.yml
```

The `/metric-relabel-debug` page shows how a rule applies before it goes live.

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
