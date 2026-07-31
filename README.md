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
| `siot-config`             | 1.0     | node configuration | `allarch`, imported once on first boot, MIT      |

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

## Importing the Simple IoT configuration

Configuring those nodes by hand on every device gets old quickly, so
`siot-config` carries a versioned copy of the configuration in
`/etc/siot/config.yml` and imports it into the local instance one time. A
freshly flashed device comes up already recording metrics.

The shipped configuration adds these children of the device node:

| Node               | Settings                                            |
| ------------------ | --------------------------------------------------- |
| `Database`         | `http://localhost:8428`, `tag` point type recorded  |
| Host metrics       | type `system`, 60 second period                     |
| Simple IoT metrics | type `app`, 120 second period                       |
| Grafana metrics    | type `process`, `grafana`, 120 second period        |
| Database metrics   | type `process`, `victoria-metrics-prod`, 120 second |

The recipe depends on the `simpleiot` runtime package, which either
`siot-binary` or the source-built recipe provides, and it reads
`/etc/default/siot` for `SIOT_NATS_PORT` and `SIOT_AUTH_TOKEN` when that file
is present.

`siot-config.service` runs after `siot.service`, waits for the local NATS port
to answer, reads the device node ID from `siot export`, and imports the file
below that node:

```
siot import -parentID <device node id> < /etc/siot/config.yml
```

Node IDs are left out of the YAML on purpose. Simple IoT assigns a new ID to
every imported node, so one file serves any number of devices. Runtime points,
such as collected metrics and host details, are left out as well; the clients
fill those in as they run. See the
[configuration documentation](https://docs.simpleiot.org/docs/user/configuration.html)
for the file format.

Simple IoT appends ` (import)` to the description of each top-level node it
imports, so the nodes appear in the portal as `Metrics System (import)` and so
on. The descriptions are editable in the portal.

A successful import records `/var/lib/siot/.config-imported`, and the unit
skips itself while that stamp is present. The stamp sits beside the store, so
clearing `/var/lib/siot` returns the device to its first-boot state and the
configuration is imported again on the next start.

To import a revised configuration on a running device:

```
rm /var/lib/siot/.config-imported
systemctl start siot-config
```

Import adds nodes rather than replacing them, so remove the nodes being
replaced in the portal first, otherwise both copies end up in the tree.

To ship a different configuration, add a `siot-config_%.bbappend` in your own
layer holding its own `siot-config.yml`, which then takes precedence over the
copy in this layer:

```
FILESEXTRAPATHS:prepend := "${THISDIR}/siot-config:"
```

## Adding the layer

Add to your `bblayers.conf`:

```
BBLAYERS += "${TOPDIR}/sources/meta-iot"
```

Then install what you need, for example:

```
IMAGE_INSTALL:append = " grafana-binary victoria-metrics-binary siot-binary siot-config"
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
