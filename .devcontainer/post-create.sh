#!/bin/bash
# Installs the lint tools used in this repository (shellcheck, actionlint, yq).
# Downloads are retried and verified against the checksums of each release.
set -eo pipefail

: "${ACTIONLINT_VERSION:?ACTIONLINT_VERSION must be set}"
: "${YQ_VERSION:?YQ_VERSION must be set}"

ARCH="$(dpkg --print-architecture)"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

download() {
  echo "Downloading $1"
  curl -fsSL --retry 5 --retry-delay 5 --retry-all-errors --connect-timeout 10 -o "$2" "$1"
}

sudo apt-get -o Acquire::Retries=5 update
sudo apt-get -o Acquire::Retries=5 install -y --no-install-recommends shellcheck

# actionlint
actionlint_url="https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}"
actionlint_tar="actionlint_${ACTIONLINT_VERSION}_linux_${ARCH}.tar.gz"
actionlint_sums="actionlint_${ACTIONLINT_VERSION}_checksums.txt"
download "${actionlint_url}/${actionlint_tar}" "${tmp}/${actionlint_tar}"
download "${actionlint_url}/${actionlint_sums}" "${tmp}/${actionlint_sums}"
(cd "$tmp" && grep -E "^[0-9a-f]{64}  ${actionlint_tar}\$" "$actionlint_sums" | sha256sum -c -)
tar -xzf "${tmp}/${actionlint_tar}" -C "$tmp" actionlint
sudo install -m 0755 "${tmp}/actionlint" /usr/local/bin/actionlint

# yq: the "checksums" file has one line per asset with many hashes, in the
# order listed by "checksums_hashes_order".
yq_url="https://github.com/mikefarah/yq/releases/download/v${YQ_VERSION}"
yq_bin="yq_linux_${ARCH}"
download "${yq_url}/${yq_bin}" "${tmp}/${yq_bin}"
download "${yq_url}/checksums" "${tmp}/yq_checksums"
download "${yq_url}/checksums_hashes_order" "${tmp}/yq_checksums_hashes_order"
sha_index="$(grep -nx 'SHA-256' "${tmp}/yq_checksums_hashes_order" | cut -d: -f1)"
if [[ -z "$sha_index" ]]; then
  echo "SHA-256 not found in yq checksums_hashes_order" >&2
  exit 1
fi
yq_sha="$(awk -v name="$yq_bin" -v idx="$((sha_index + 1))" '$1 == name { print $idx }' "${tmp}/yq_checksums")"
if [[ ! "$yq_sha" =~ ^[0-9a-f]{64}$ ]]; then
  echo "No SHA-256 checksum found for ${yq_bin}" >&2
  exit 1
fi
(cd "$tmp" && echo "${yq_sha}  ${yq_bin}" | sha256sum -c -)
sudo install -m 0755 "${tmp}/${yq_bin}" /usr/local/bin/yq
sudo chmod +x /usr/local/bin/yq

actionlint --version && shellcheck --version && yq --version && java -version
