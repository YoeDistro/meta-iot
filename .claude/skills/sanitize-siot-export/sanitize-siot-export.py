#!/usr/bin/env python3
"""Turn a `siot export` dump into an importable Simple IoT configuration.

An export captures one running instance: generated IDs, an origin on every
operator-set point, and whatever the clients have collected so far. An
import file wants none of that. It wants the configuration only, so the
same file installs on any number of devices.

This reads an export on stdin, or from a path, and writes a configuration
on stdout:

  siot export -natsServer nats://127.0.0.1:4222 |
      ./sanitize-siot-export.py > siot.yml

Reference: https://docs.simpleiot.org/docs/user/configuration.html
"""

import argparse
import sys

import yaml

# Node types kept by default: the whole tree an instance is built from,
# suitable for importing at the root of a fresh store. Narrow this with
# --keep when the target already provides some of it. Dropping 'device'
# and 'user', for instance, leaves the nodes that attach beneath a device
# node that already exists.
DEFAULT_KEEP = ("device", "user", "metrics", "db")

# Points an operator sets, per node type, in the order they read best.
# Every other point on these nodes is collected data.
CONFIG_POINTS = {
    "device": ("description",),
    "user": ("email", "firstName", "lastName", "pass", "phone"),
    "metrics": ("description", "type", "name", "period"),
    "db": ("description", "uri", "tagPointType"),
}


def config_points(node):
    """Return the node's configuration points, ordered, data dropped."""
    order = CONFIG_POINTS.get(node["type"])
    points = node.get("points") or []

    if order is None:
        # An unrecognized type keeps everything. Better to hand the
        # reviewer too much than to drop a setting silently.
        print(
            f"note: keeping all points on unrecognized node type "
            f"'{node['type']}', review them by hand",
            file=sys.stderr,
        )
        return [strip_point(p) for p in points]

    kept = []
    for want in order:
        for point in points:
            # An export writes a point for every field a client knows
            # about, including the ones nobody filled in. Those carry no
            # setting, so they are left out.
            if point["type"] == want and ("text" in point or "value" in point):
                kept.append(strip_point(point))
    return kept


def strip_point(point):
    """Drop per-point export bookkeeping and tidy whole-number values."""
    out = {k: v for k, v in point.items() if k != "origin"}
    # Export writes every value as a float. Whole numbers read better as
    # integers, and Simple IoT parses either into the same float64.
    if isinstance(out.get("value"), float) and out["value"].is_integer():
        out["value"] = int(out["value"])
    return out


def sanitize_nodes(nodes, keep):
    """Filter a node tree down to the configuration worth shipping.

    A node whose type is not kept still gives up its children, which move
    into its place. That is what turns an export rooted at a device node
    into a flat list ready to import beneath a device node that already
    exists on the target.
    """
    out = []
    for node in nodes or []:
        children = sanitize_nodes(node.get("children"), keep)
        if node["type"] not in keep:
            out.extend(children)
            continue
        # 'id' and 'parent' name this instance's nodes. Leaving them out
        # lets Simple IoT generate fresh ones on every device.
        entry = {"type": node["type"], "points": config_points(node)}
        if children:
            entry["children"] = children
        out.append(entry)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "export",
        nargs="?",
        type=argparse.FileType("r"),
        default=sys.stdin,
        help="export file to read (default: stdin)",
    )
    parser.add_argument(
        "--keep",
        default=",".join(DEFAULT_KEEP),
        help=(
            "comma-separated node types to keep; the children of a type "
            f"left out move up in its place (default: {','.join(DEFAULT_KEEP)})"
        ),
    )
    args = parser.parse_args()

    doc = yaml.safe_load(args.export)
    nodes = sanitize_nodes(doc.get("nodes"), keep=set(args.keep.split(",")))

    if not nodes:
        print("note: no nodes matched --keep, output is empty", file=sys.stderr)

    yaml.safe_dump({"nodes": nodes}, sys.stdout, sort_keys=False, default_flow_style=False)


if __name__ == "__main__":
    main()
