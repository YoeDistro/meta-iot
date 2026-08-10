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
| `grafana-server.service`   | `:3000`                | `/data/grafana`             | logs in `/var/log`      |
| `victoria-metrics.service` | `:8428`                | `/data/victoria-metrics`    | `/vmui` path for web ui |
| `siot.service`             | `:8118`                | `/data/siot`                |                         |

Grafana and VictoriaMetrics run under dedicated system users created by the
`useradd` class. Simple IoT runs as root, matching upstream, since edge
deployments commonly need access to serial and GPIO devices.

### Persistent data on /data

Yoe carries `/data` on its own partition, laid down by the image definitions
in `meta-yoe`, so the databases these services keep there outlive a rootfs
update: the Simple IoT store with its write-ahead log files, the Grafana
database along with any plugins installed later, and the VictoriaMetrics time
series data. Only that dynamic data moves. Grafana writes its logs to
`/var/log` as before, and everything the recipes build stays in the rootfs.

None of the recipes install anything under `/data`. A mount covers whatever
the rootfs ships beneath it, so a packaged directory would disappear the
moment the partition came up. Each unit creates its own directory in
`ExecStartPre` instead, and waits for the partition through
`RequiresMountsFor=/data`. Grafana and VictoriaMetrics prefix those commands
with `+` so they run as root, before the drop to the service user.

The paths live in the matching `/etc/default` file, which is where to change
them. A device that keeps its data somewhere else needs no recipe change.

Updating a device that already stores data under `/var/lib` leaves the old
directories where they are, and the services come up against empty ones. To
carry the contents across, after the update and before the databases collect
anything worth keeping:

```
systemctl stop siot grafana-server victoria-metrics
mkdir -p /data/siot /data/grafana /data/victoria-metrics
cp -a /var/lib/siot/. /data/siot/
cp -a /var/lib/grafana/. /data/grafana/
cp -a /var/lib/victoria-metrics/. /data/victoria-metrics/
systemctl start siot grafana-server victoria-metrics
```

`cp -a` carries the dot files, including the `.config-imported` stamp, so the
configuration import stays recorded. Ownership is set again on the next start.

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

### Query latency offset

A sample reaches VictoriaMetrics about a second after Simple IoT records it,
because the write client sends a batch every second. Upstream then holds it back
from queries for another 30 seconds: `-search.latencyOffset` shifts the end of
every query range that far into the past so that slow Prometheus scrapes are
still counted. Data arrives here by push rather than by scrape, so this layer
sets the offset to `0s` and dashboards show a value as soon as it is written.

The setting lives in `/etc/default/victoria-metrics`:

```
VM_SEARCH_LATENCY_OFFSET=0s
```

A second or two is worth using instead when the clocks on the writing devices
and the database may differ. After changing it, run
`systemctl restart victoria-metrics`; `curl -s localhost:8428/flags` lists the
flags that differ from their defaults.

## Importing the Simple IoT configuration

Configuring those nodes by hand on every device gets old quickly, so
`siot-config` carries a versioned copy of the configuration in
`/etc/siot/config.yml` and imports it into the local instance one time. A
freshly flashed device comes up already recording metrics.

The shipped configuration describes a device node and everything beneath it:

| Node                | Settings                                             |
| ------------------- | ---------------------------------------------------- |
| Device              | description `siot`                                   |
| Administrative user | `admin` / `admin`                                    |
| Host metrics        | type `system`, 60 second period                      |
| `Database`          | `http://localhost:8428`, `tag` point type recorded   |
| Simple IoT metrics  | type `app`, 60 second period                         |
| Grafana metrics     | type `process`, `grafana`, 60 second period          |
| Database metrics    | type `process`, `victoria-metrics-prod`, 60 second   |

The administrative account ships with a known password, so change it in the
portal on any device reachable beyond a bench, or replace the configuration as
described at the end of this section.

The recipe depends on the `simpleiot` runtime package, which either
`siot-binary` or the source-built recipe provides.

`siot-config.service` runs after `siot.service`, waits for the instance to
answer, and imports the file at the root of the tree:

```
siot import -parentID root < /etc/siot/config.yml
```

Importing at the root means the file describes a whole tree, beginning with
its own device node, rather than a set of nodes to attach beneath the device
node an instance generates for itself. The `siot` command picks up its
connection settings from the environment, and `siot-config.service` reads
`/etc/default/siot` so that it reaches the instance the same way
`siot.service` starts it.

Node IDs are left out of the YAML on purpose. Simple IoT assigns a new ID to
every imported node, so one file serves any number of devices. Runtime points,
such as collected metrics and host details, are left out as well; the clients
fill those in as they run. See the
[configuration documentation](https://docs.simpleiot.org/docs/user/configuration.html)
for the file format.

Simple IoT appends ` (import)` to the description of each node at the top
level of the imported file, so a node described as `Metrics System` appears in
the portal as `Metrics System (import)`. The descriptions are editable there.

### Importing again on a running device

A successful import records `/data/siot/.config-imported`, and both the unit
and the script stand down while that stamp is present. The stamp sits beside
the store, so clearing `/data/siot` returns the device to its first-boot state
and the configuration is imported again on the next start.

To import a revised configuration while keeping the store:

```
siot-config-import --force
```

`--force` imports regardless of the stamp, then rewrites the stamp with a
`forced: yes` line recording how the import happened. The recipe installs the
script in `/usr/bin` so that it is on the path for this. Run it directly:
`systemctl start siot-config` does nothing once the stamp exists, because the
unit's `ConditionPathExists` holds it back before the script gets a chance to
run.

Import adds nodes rather than replacing them, so remove the nodes being
replaced in the portal first, otherwise both copies end up in the tree.

### Building a configuration from a running device

Setting a device up through the portal and capturing the result is usually
easier than writing the YAML by hand. `siot export` describes one running
instance, though: generated node IDs, an `origin` on every point an operator
set, and whatever the clients have collected so far. None of that belongs in a
configuration meant for every device.

The `sanitize-siot-export` skill in this layer removes it:

```
siot export | .claude/skills/sanitize-siot-export/sanitize-siot-export.py \
    > recipes-iot/siot-config/files/siot-config.yml
```

The script drops `id`, `parent`, and `origin` fields, keeps only the points an
operator sets, and leaves out points nobody filled in. `--keep` chooses which
node types survive, and a type left out gives up its children, which move into
its place. Keeping `device,user,metrics,db`, the default, produces a whole
tree ready to import at the root.

That rewrites the shipped configuration in place, so review the result before
committing it: a capture carries whatever the source device was set to,
including the description on its device node and the administrative account's
password. The skill's `SKILL.md` records which points count as configuration
for each node type, and why each piece of the export is left behind.

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
