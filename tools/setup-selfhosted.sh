#!/usr/bin/env bash
# Self-hosted release runner setup (Linux x64, run ONCE on the server).
#
#   curl -sSL https://raw.githubusercontent.com/PDX122/Actors/main/tools/setup-selfhosted.sh -o setup.sh
#   TOKEN=<registration-token> bash setup.sh
#
# The token comes from Settings -> Actions -> Runners -> New self-hosted
# runner (single-use, expires fast). Run as a user with sudo. After this,
# copy the proprietary jars over (one time, ~1.8 GB):
#
#   scp -r libs <server>:~/fbactors-jars/
#   for v in 1.21.1 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1 26.2 26.3; do
#     scp versions/$v/run/mods/Flashback-*.jar <server>:~/fbactors-jars/run-mods/$v/
#   done
#
# Every CI job then stages those jars into its fresh checkout itself (see the
# release job in .github/workflows/ci.yml), so nothing proprietary ever
# enters git.
set -euo pipefail

TOKEN="${1:-${TOKEN:-}}"
REPO="PDX122/Actors"
RUNNER_DIR="$HOME/actions-runner"
JARS_DIR="$HOME/fbactors-jars"

if [ -z "$TOKEN" ]; then
    echo "usage: TOKEN=<registration-token> bash $0"
    echo "Get the token at: https://github.com/${REPO}/settings/actions/runners/new"
    exit 1
fi

sudo apt-get update
sudo apt-get install -y git curl python3 wget apt-transport-https gnupg

if ! command -v java >/dev/null 2>&1 || [ ! -d /usr/lib/jvm/temurin-25-jdk-amd64 ]; then
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/VERSION_CODENAME/{print$2}' /etc/os-release) main" \
        | sudo tee /etc/apt/sources.list.d/adoptium.list
    sudo apt-get update
    sudo apt-get install -y temurin-21-jdk temurin-25-jdk
fi

mkdir -p "$RUNNER_DIR" "$JARS_DIR/libs" "$JARS_DIR/run-mods"
cd "$RUNNER_DIR"
if [ ! -f ./run.sh ]; then
    VER="$(curl -s https://api.github.com/repos/actions/runner/releases/latest \
        | grep -o '"tag_name": "v[^"]*"' | head -1 | cut -d'"' -f4 | tr -d v)"
    curl -sSLo runner.tgz "https://github.com/actions/runner/releases/download/v${VER}/actions-runner-linux-x64-${VER}.tar.gz"
    tar xzf runner.tgz && rm runner.tgz
fi
./config.sh --unattended --replace \
    --url "https://github.com/${REPO}" --token "$TOKEN" \
    --labels fbactors-release --name "$(hostname)-fbactors"
sudo ./svc.sh install "$(whoami)"
sudo ./svc.sh start

echo "Runner live. Now scp the jars (see header), then dispatch the release workflow."
