# Run with -DryRun to review the deployment or -Deploy to apply it.
[CmdletBinding(DefaultParameterSetName = 'Preview')]
param(
    [Parameter(ParameterSetName = 'Preview')]
    [switch]$DryRun,
    [Parameter(Mandatory, ParameterSetName = 'Live')]
    [switch]$Deploy,
    [string]$NeoNebulaRoot = 'C:\Users\miw\Documents\NeoNebula'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$properties = Get-Content (Join-Path $project 'gradle.properties') -Raw
$version = [regex]::Match($properties, '(?m)^mod_version\s*=\s*([\w.-]+)\s*$').Groups[1].Value
if (!$version) { throw 'Could not read mod_version from gradle.properties.' }
$jarName = "neoworld-$version.jar"
$moduleId = "generated.forgemod:neoworld:$version@jar"
$serverId = 'NeoForge-1.21.1'
$manifest = Join-Path $NeoNebulaRoot 'distro/distribution.json'
$launcherJar = Join-Path $NeoNebulaRoot "distro/servers/$serverId/forgemods/required/$jarName"
$publicManifest = 'https://api.ambn.online/distro/distribution.json'
$publicJar = "https://api.ambn.online/distro/servers/$serverId/forgemods/required/$jarName"

function Get-NeoWorldModule($distribution) {
    $servers = @($distribution.servers | Where-Object id -eq $serverId)
    if ($servers.Count -ne 1) { throw "Expected one distribution server named $serverId." }
    $modules = @($servers[0].modules | Where-Object id -Like 'generated.forgemod:neoworld:*')
    if ($modules.Count -ne 1 -or $modules[0].id -ne $moduleId) {
        throw "Expected one NeoWorld module with ID $moduleId. Version migrations require a separate procedure."
    }
    return $modules[0]
}

if (!(Test-Path -LiteralPath $launcherJar -PathType Leaf)) { throw "Missing launcher JAR: $launcherJar" }
$priorDistribution = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
$priorModule = Get-NeoWorldModule $priorDistribution
if (!$Deploy) {
    Write-Output "Dry run: compileJava jar -> $jarName -> Cronus and NeoNebula."
    Write-Output 'The live run stages and checks files, backs up the stopped server, replaces the two JARs and one distribution entry, then verifies startup and the public download.'
    return
}

foreach ($command in 'ssh', 'scp', 'npm.cmd') { Get-Command $command -ErrorAction Stop | Out-Null }
$tag = 'deploy-nw-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$stage = New-Item -ItemType Directory -Path (Join-Path $project "stage/$tag")
$jar = Join-Path $project "build/libs/$jarName"

Push-Location $project
try {
    & .\gradlew.bat compileJava jar
    if ($LASTEXITCODE -ne 0) { throw 'Gradle failed.' }
}
finally { Pop-Location }

$sha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$md5 = (Get-FileHash -LiteralPath $jar -Algorithm MD5).Hash.ToLowerInvariant()
$length = (Get-Item -LiteralPath $jar).Length
$oldLocalManifest = Join-Path $stage 'local-distribution-before.json'
$oldLocalJar = Join-Path $stage 'local-launcher-before.jar'
Copy-Item -LiteralPath $manifest -Destination $oldLocalManifest
Copy-Item -LiteralPath $launcherJar -Destination $oldLocalJar
$remoteInvoked = $false
try {
    Copy-Item -LiteralPath $jar -Destination $launcherJar
    Push-Location $NeoNebulaRoot
    try {
        & npm.cmd start -- g distro *> (Join-Path $stage 'nebula.log')
        if ($LASTEXITCODE -ne 0) { throw "NeoNebula failed. See $stage/nebula.log." }
    }
    finally { Pop-Location }

    $module = Get-NeoWorldModule (Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json)
    if ($module.artifact.url -cne $publicJar -or $module.artifact.MD5 -ne $md5 -or $module.artifact.size -ne $length) {
        throw 'NeoNebula generated metadata that does not match the staged JAR.'
    }
    $priorModule.artifact = $module.artifact
    $priorDistribution | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $manifest -Encoding utf8
    $moduleFile = Join-Path $stage 'module.json'
    $module | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $moduleFile -Encoding utf8

    & scp $jar "cronus:/home/miw/staging/$tag.jar"
    if ($LASTEXITCODE -ne 0) { throw 'JAR upload failed.' }
    & scp $moduleFile "cronus:/home/miw/staging/$tag.json"
    if ($LASTEXITCODE -ne 0) { throw 'Module metadata upload failed.' }

    $remoteScript = @'
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

tag, jar_name, module_id, expected_sha = sys.argv[1:]
base = Path("/home/miw")
game = base / "services/neoworld"
distro = base / "services/ambition-api/public/distro"
manifest = distro / "distribution.json"
staged = base / f"staging/{tag}.jar"
module = json.loads((base / f"staging/{tag}.json").read_text(encoding="utf-8-sig"))
backup = base / "backups" / tag
container = "neoworld-server"
lock = (base / "staging/neoworld-deploy.lock").open("a")
fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)

def run(*args):
    return subprocess.check_output(args, text=True).strip()

def replace(src, dst):
    temp = dst.with_name(f".{dst.name}.{tag}.tmp")
    shutil.copyfile(src, temp)
    os.chmod(temp, 0o644)
    temp.replace(dst)

payload = staged.read_bytes()
if (hashlib.sha256(payload).hexdigest() != expected_sha
        or hashlib.md5(payload).hexdigest() != module["artifact"]["MD5"].lower()
        or len(payload) != module["artifact"]["size"]
        or module["id"] != module_id):
    raise RuntimeError("Staged JAR or metadata mismatch")

state = json.loads(run("docker", "inspect", container))[0]
if not state["State"]["Running"] or not any(
    mount["Source"] == str(game) and mount["Destination"] == "/data" for mount in state["Mounts"]
):
    raise RuntimeError("Cronus container is not running with the expected mount")
if sorted(p.name for p in (game / "mods").glob("neoworld-*.jar")) != [jar_name]:
    raise RuntimeError("Expected exactly one existing NeoWorld JAR with the same version")

data = json.loads(manifest.read_text())
server, = (s for s in data["servers"] if s["id"] == "NeoForge-1.21.1")
entry, = (m for m in server["modules"] if m["id"].startswith("generated.forgemod:neoworld:"))
if entry["id"] != module_id:
    raise RuntimeError("Live distribution uses a different NeoWorld version")
entry["artifact"] = module["artifact"]
paths = [
    (game / "mods" / jar_name, backup / "server.jar"),
    (distro / "servers/NeoForge-1.21.1/forgemods/required" / jar_name, backup / "launcher.jar"),
    (manifest, backup / "distribution.json"),
]
backup.mkdir()
print(f"Backup: {backup}", flush=True)
for live, prior in paths:
    shutil.copy2(live, prior)
pending = backup / "distribution-next.json"
pending.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")

run("python3", str(base / "send_console.py"), "say Server restarting for NeoWorld mod update")
run("python3", str(base / "send_console.py"), "save-all flush")
time.sleep(3)
run("docker", "stop", "-t", "120", container)
if run("docker", "inspect", "--format", "{{.State.Status}}", container) != "exited":
    raise RuntimeError("Server did not stop")

modified = []
try:
    run("docker", "run", "--rm", "--volumes-from", f"{container}:ro",
        "-v", f"{backup}:/backup", "--entrypoint", "tar", state["Image"],
        "-czf", "/backup/server-before.tar.gz", "-C", "/data",
        "world", "plugins", "mods", "config", "defaultconfigs", "customnpcs")
    with gzip.open(backup / "server-before.tar.gz", "rb") as archive:
        while archive.read(1024 * 1024):
            pass
    for (live, prior), source in zip(paths, (staged, staged, pending)):
        if live.read_bytes() != prior.read_bytes():
            raise RuntimeError(f"Live file changed during staging: {live}")
        replace(source, live)
        modified.append((live, prior))
        if live.read_bytes() != source.read_bytes():
            raise RuntimeError(f"Installed file did not match: {live}")
    run("docker", "start", container)
except Exception:
    for live, prior in reversed(modified):
        replace(prior, live)
    run("docker", "start", container)
    raise

print("Installed NeoWorld JARs and restarted Cronus", flush=True)
started_at = run("docker", "inspect", "--format", "{{.State.StartedAt}}", container)
deadline = time.monotonic() + 300
while time.monotonic() < deadline:
    logs = subprocess.run(["docker", "logs", "--since", started_at, container],
                          capture_output=True, text=True, check=True)
    output = logs.stdout + logs.stderr
    if "Done (" in output and "NeoWorld MARIADB database is ready" in output:
        for line in output.splitlines():
            if " ERROR" in line or " FATAL" in line:
                print(f"Startup log: {line[:240]}", flush=True)
        print("Cronus startup and database ready", flush=True)
        break
    if run("docker", "inspect", "--format", "{{.State.StartedAt}} {{.State.Status}}", container) != f"{started_at} running":
        raise RuntimeError(f"Server exited or restarted after installation. Review logs and backup {backup}.")
    time.sleep(8)
else:
    raise RuntimeError(f"Startup did not confirm readiness. Review logs and backup {backup}.")
'@
    $remoteInvoked = $true
    $remoteScript | & ssh cronus "python3 - $tag $jarName '$moduleId' $sha"
    if ($LASTEXITCODE -ne 0) { throw "Cronus deploy or startup check failed. Review /home/miw/backups/$tag and server logs." }

    $published = Get-NeoWorldModule (Invoke-RestMethod -Uri $publicManifest -TimeoutSec 30)
    if ($published.artifact.url -cne $publicJar -or $published.artifact.MD5 -ne $md5 -or $published.artifact.size -ne $length) {
        throw 'Public manifest does not match the deployed JAR.'
    }
    $download = Join-Path $stage 'public.jar'
    Invoke-WebRequest -Uri $publicJar -OutFile $download -TimeoutSec 120
    if ((Get-FileHash -LiteralPath $download).Hash -ne $sha -or
        (Get-FileHash -LiteralPath $download -Algorithm MD5).Hash -ne $md5 -or
        (Get-Item -LiteralPath $download).Length -ne $length) {
        throw 'Public JAR hash or size does not match the deployed artifact.'
    }
    Write-Output "Deployed and verified $jarName. Backup: /home/miw/backups/$tag"
}
catch {
    if (!$remoteInvoked) {
        Copy-Item -LiteralPath $oldLocalManifest -Destination $manifest
        Copy-Item -LiteralPath $oldLocalJar -Destination $launcherJar
    }
    throw
}
