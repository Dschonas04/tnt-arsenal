#!/usr/bin/env bash
# Fetches into libs/ what the build needs but the repository must not carry:
# the Minecraft server and client jars and their libraries from Mojang, and the
# Fabric API modules from the Fabric maven. Used by CI and for a fresh clone.
#
#   tools/fetch-libs.sh
#
# libs/server.jar  classes of the dedicated server (common code)
# libs/client.jar  classes of the client (client source set only)
# libs/mc/         every library of the version manifest, natives left out
# libs/fabric/     every module nested in the fabric-api jar
set -euo pipefail
cd "$(dirname "$0")/.."

mc="$(sed -n 's/^minecraft_version=//p' gradle.properties)"
api="$(sed -n 's/^fabric_api_version=//p' gradle.properties)"
mkdir -p libs/mc libs/fabric
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

version_url="$(curl -fsSL https://piston-meta.mojang.com/mc/game/version_manifest_v2.json |
  python3 -c "import json,sys; print(next(v['url'] for v in json.load(sys.stdin)['versions'] if v['id']=='$mc'))")"
curl -fsSL "$version_url" -o "$work/version.json"

echo "Minecraft $mc"
python3 - "$work" "$mc" <<'PY'
import json, os, shutil, sys, urllib.request, zipfile
work, mc = sys.argv[1], sys.argv[2]
v = json.load(open(os.path.join(work, "version.json")))

def fetch(url, dest):
    if os.path.exists(dest):
        return
    with urllib.request.urlopen(url) as r, open(dest + ".part", "wb") as f:
        shutil.copyfileobj(r, f)
    os.replace(dest + ".part", dest)

# Since 1.18 the server jar is a bundle; the classes themselves are in
# META-INF/versions/<version>/server-<version>.jar.
if not os.path.exists("libs/server.jar"):
    bundle = os.path.join(work, "bundle.jar")
    fetch(v["downloads"]["server"]["url"], bundle)
    with zipfile.ZipFile(bundle) as z, z.open(f"META-INF/versions/{mc}/server-{mc}.jar") as src, open("libs/server.jar", "wb") as dst:
        shutil.copyfileobj(src, dst)
    print("  libs/server.jar")
if not os.path.exists("libs/client.jar"):
    fetch(v["downloads"]["client"]["url"], "libs/client.jar")
    print("  libs/client.jar")

count = 0
for lib in v["libraries"]:
    art = lib.get("downloads", {}).get("artifact")
    if not art or ":natives-" in lib["name"] or "natives" in art["path"]:
        continue
    fetch(art["url"], os.path.join("libs/mc", os.path.basename(art["path"])))
    count += 1
print(f"  libs/mc: {count} libraries")
PY

echo "Fabric API $api"
if [ -z "$(ls libs/fabric)" ]; then
  curl -fsSL "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$api/fabric-api-$api.jar" -o "$work/api.jar"
  python3 - "$work/api.jar" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    names = [n for n in z.namelist() if n.startswith("META-INF/jars/") and n.endswith(".jar")]
    for name in names:
        with open("libs/fabric/" + name.rsplit("/", 1)[1], "wb") as dst:
            dst.write(z.read(name))
print(f"  libs/fabric: {len(names)} modules")
PY
fi
