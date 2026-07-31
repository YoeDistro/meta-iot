---
name: sanitize-siot-export
description:
  Use when turning a `siot export` dump into an importable Simple IoT
  configuration for meta-iot — stripping node IDs, origins, and collected
  data so the file installs on any device. Triggers on requests like
  "sanitize this siot export", "make this export importable", "regenerate
  siot-config.yml from the device", "update the SIOT config from a running
  instance", or any editing of siot.yml or
  recipes-iot/siot-config/files/siot-config.yml.
---

# Sanitize a `siot export` file

An export captures one running instance. An import file describes
configuration that should apply to every device the layer builds. Going
from the first to the second means removing everything specific to the
instance the export came from, while keeping every setting an operator
chose.

Reference: <https://docs.simpleiot.org/docs/user/configuration.html>

## Run the script

`sanitize-siot-export.py` performs the whole conversion in one pass. It
reads an export on stdin, or from a path, and writes the configuration on
stdout.

```sh
# From a running device
siot export -natsServer nats://127.0.0.1:4222 |
    .claude/skills/sanitize-siot-export/sanitize-siot-export.py > siot.yml

# From a saved export
.claude/skills/sanitize-siot-export/sanitize-siot-export.py siot.yml
```

The script needs PyYAML. It is present on the development host; it is not
part of the target image, so run it on the host rather than on the device.

## Choosing what to keep

`--keep` names the node types that survive, and it is the one control that
shapes the output. A type left out is dropped, and its children move up
into its place. That single rule produces both forms the layer uses:

| Command | Result | Import against |
| --- | --- | --- |
| `--keep device,user,metrics,db` (default) | the whole tree, device node at the root | the root of a fresh store |
| `--keep metrics,db` | a flat list, no wrapper | a device node that already exists |

`siot.yml` at the layer root holds the first form: a complete picture of
how a device is configured, including the device node and its
administrative user.

`recipes-iot/siot-config/files/siot-config.yml` holds the second.
`siot-config-import` runs `siot import -parentID "$device_id"`, discovering
that ID from the target instance at first boot, so the imported nodes
attach to the device node already there. A device node in that file would
create a second one beneath the first.

## What the conversion removes, and why

Review the output against this list whenever the script is changed, or when
converting by hand.

1. **`id:` fields.** Simple IoT generates a new ID for every imported node.
   Leaving IDs out lets one file install on any number of devices.

2. **`parent:` fields.** These name nodes of the instance the export came
   from. Position comes from the structure of the file and the parent
   chosen at import time.

3. **`origin:` fields.** These record which user set a point on the source
   instance, by ID, and those IDs are gone with step 1.

4. **Collected points.** Clients populate these as they run, so shipping a
   snapshot of one device's readings would be misleading. The
   `CONFIG_POINTS` table in the script holds the per-type allowlist:

   - **device** — `description`. The `metricNats*` and `version*` points
     are reported by the running instance.
   - **user** — `email`, `firstName`, `lastName`, `pass`, `phone`.
   - **metrics** — `description`, `type`, `name`, `period`. Everything
     else is data, including `count`, which reports how many matching
     processes a client found.
   - **db** — `description`, `uri`, `tagPointType`, all configuration.

5. **Points with no `text` and no `value`.** An export writes a point for
   every field a client knows about, including the ones nobody filled in.
   An empty point carries no setting, so it adds only noise.

A node type missing from `CONFIG_POINTS` keeps all of its points and prints
a note on stderr, so new types surface for review rather than losing
settings quietly.

## Check the result

Confirm the file parses and holds the nodes expected:

```sh
python3 -c "
import yaml
def walk(nodes, indent=0):
    for n in nodes:
        print(' ' * indent + n['type'],
              [(p['type'], p.get('text', p.get('value'))) for p in n['points']])
        walk(n.get('children', []), indent + 2)
walk(yaml.safe_load(open('siot.yml'))['nodes'])
"
```

Then read it through. The file is short enough to check by eye, and the
values carry hostnames, URIs, and credentials worth confirming against the
target rather than the machine the export came from. The `pass` point on
the user node is one to look at every time.

## Keeping the shipped config readable

`recipes-iot/siot-config/files/siot-config.yml` is maintained by hand and
carries comments explaining what each node does. The script emits no
comments. When regenerating that file, bring the comments across, or apply
the script's output as a diff so the surrounding prose stays intact.
