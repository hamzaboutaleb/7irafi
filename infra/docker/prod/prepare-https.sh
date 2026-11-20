#!/bin/sh
# Prepare local HTTPS assets; --check validates them without changing any files.
set -eu

file=infra/docker/prod/.env
https_file=infra/docker/prod/.env.https
tls_dir=infra/docker/prod/tls
lock_dir=infra/docker/prod/.https-prepare.lock
cert="$tls_dir/server.crt"
key="$tls_dir/server.key"

fail() {
    echo "$*" >&2
    exit 1
}

read_setting() {
    setting=$(sed -n "s/^${1}=//p" "$2" | tail -n 1 | tr -d '\r')
    case "$setting" in
        \"*\") setting=${setting#\"}; setting=${setting%\"} ;;
        \'*\') setting=${setting#\'}; setting=${setting%\'} ;;
    esac
    printf '%s\n' "$setting"
}

valid_ip() {
    case "$1" in ''|*[!0-9.]*) return 1 ;; esac
    printf '%s\n' "$1" | awk -F. '
        NF != 4 { exit 1 }
        {
            for (i = 1; i <= 4; i++) {
                if ($i == "" || length($i) > 3 || $i + 0 > 255 ||
                    (length($i) > 1 && substr($i, 1, 1) == "0")) exit 1
            }
            if ($1 == 0 || $1 == 127 || $1 >= 224 ||
                ($1 == 169 && $2 == 254)) exit 1
        }'
}

detect_ip() {
    if command -v ip >/dev/null 2>&1; then
        candidates=$(ip -o -4 addr show up scope global | awk '
            $2 !~ /^(lo|docker|br-|veth|virbr|podman|cni|tun|tap|tailscale|wg)/ {
                split($4, address, "/"); print address[1]
            }')
    elif command -v ifconfig >/dev/null 2>&1; then
        candidates=$(ifconfig -a | awk '
            /^[^ \t]/ {
                interface = $1; sub(/:$/, "", interface)
                up = $0 ~ /[<,]UP[,>]/
            }
            $1 == "inet" && up &&
                interface !~ /^(lo|docker|br-|veth|virbr|podman|cni|tun|tap|utun|tailscale|wg|bridge|vboxnet|vmnet)/ {
                print $2
            }')
    else
        fail 'IP detection requires ip or ifconfig; set TLS_HOST_IP explicitly.'
    fi

    addresses=$(
        for candidate in $candidates; do
            if valid_ip "$candidate"; then printf '%s\n' "$candidate"; fi
        done | sort -u
    )
    count=$(printf '%s\n' "$addresses" | awk 'NF { count++ } END { print count + 0 }')
    [ "$count" -eq 1 ] || fail 'Could not select one LAN IPv4 address; set TLS_HOST_IP to the address reachable by your demo devices.'
    printf '%s\n' "$addresses"
}

certificate_usable() {
    [ -f "$1" ] && [ -f "$2" ] || return 1
    "$openssl_bin" x509 -in "$1" -noout -checkend "$3" >/dev/null 2>&1 || return 1
    "$openssl_bin" verify -check_ss_sig -CAfile "$1" -purpose sslserver \
        -verify_ip "$host_ip" "$1" >/dev/null 2>&1 || return 1
    cert_public=$("$openssl_bin" x509 -in "$1" -noout -pubkey 2>/dev/null) || return 1
    key_public=$("$openssl_bin" pkey -in "$2" -passin pass: -pubout 2>/dev/null) || return 1
    [ "$cert_public" = "$key_public" ]
}

certificate_fingerprint() {
    fingerprint_output=$("$openssl_bin" x509 -in "$cert" -noout -fingerprint -sha256) || return 1
    printf '%s\n' "${fingerprint_output#*=}"
}

[ "$#" -le 1 ] || fail 'Usage: sh infra/docker/prod/prepare-https.sh [--check]'
mode=${1:-prepare}
case "$mode" in prepare|--check) : ;; *) fail 'Usage: sh infra/docker/prod/prepare-https.sh [--check]' ;; esac
[ -f "$file" ] || fail "Missing $file (copy .env.example and fill it in)"
openssl_bin=${OPENSSL_BIN:-$(read_setting OPENSSL_BIN "$file")}
openssl_bin=${openssl_bin:-openssl}
command -v "$openssl_bin" >/dev/null 2>&1 || fail 'OpenSSL is required; install OpenSSL 1.1.1+ or set OPENSSL_BIN to its executable.'
if ! "$openssl_bin" req -help 2>&1 | grep -q -- '-addext' ||
   ! "$openssl_bin" verify -help 2>&1 | grep -q -- '-verify_ip'; then
    fail 'OpenSSL must support -addext and -verify_ip (OpenSSL 1.1.1+). Set OPENSSL_BIN if the system version is older.'
fi

if [ "$mode" = --check ]; then
    [ -f "$https_file" ] || fail 'Missing generated HTTPS settings; run make prod-https or make prod-up first.'
    host_ip=$(read_setting HTTPS_HOST_IP "$https_file")
    valid_ip "$host_ip" || fail 'Invalid generated HTTPS_HOST_IP; run make prod-https.'
    [ "$(read_setting FRONTEND_ORIGIN "$https_file")" = "https://$host_ip" ] || fail 'Generated frontend origin does not match the IP; run make prod-https.'
    [ "$(read_setting S3_PUBLIC_ENDPOINT "$https_file")" = "https://$host_ip:9443" ] || fail 'Generated media origin does not match the IP; run make prod-https.'
    certificate_usable "$cert" "$key" 0 || fail 'HTTPS certificate is missing, expired, invalid, or mismatched; run make prod-https.'
    fingerprint=$(certificate_fingerprint)
    [ "$(read_setting TLS_CERT_SHA256 "$https_file")" = "$fingerprint" ] || fail 'Certificate fingerprint changed; run make prod-https.'
    echo 'HTTPS certificate and generated settings passed validation.'
    exit 0
fi

host_ip=${TLS_HOST_IP:-$(read_setting TLS_HOST_IP "$file")}
case "$host_ip" in ''|auto) host_ip=$(detect_ip) ;; esac
valid_ip "$host_ip" || fail 'TLS_HOST_IP must be a reachable unicast IPv4 address (not a loopback or link-local address).'

umask 077
mkdir "$lock_dir" 2>/dev/null || fail "HTTPS preparation is already running. If an earlier run was interrupted, remove the stale $lock_dir directory."
work_dir=
env_tmp=
cleanup() {
    if [ -n "$work_dir" ]; then
        rm -f "$work_dir/server.crt" "$work_dir/server.key" "$work_dir/openssl.log"
        rmdir "$work_dir" 2>/dev/null || true
    fi
    if [ -n "$env_tmp" ]; then rm -f "$env_tmp"; fi
    rmdir "$lock_dir" 2>/dev/null || true
}
trap cleanup 0
trap 'exit 1' HUP INT TERM
mkdir -p "$tls_dir"
chmod 700 "$tls_dir"

# Renew during preparation when fewer than 30 days remain; normal restarts reuse it.
if certificate_usable "$cert" "$key" 2592000; then
    echo "Reusing the HTTPS certificate for $host_ip."
else
    work_dir=$(mktemp -d "$tls_dir/.prepare.XXXXXX")
    if ! "$openssl_bin" req -x509 -newkey rsa:2048 -nodes -sha256 -days 365 \
        -keyout "$work_dir/server.key" -out "$work_dir/server.crt" \
        -subj "/CN=$host_ip" \
        -addext "subjectAltName=IP:$host_ip" \
        -addext 'basicConstraints=critical,CA:FALSE' \
        -addext 'keyUsage=critical,digitalSignature,keyEncipherment' \
        -addext 'extendedKeyUsage=serverAuth' 2>"$work_dir/openssl.log"; then
        cat "$work_dir/openssl.log" >&2
        fail 'Could not generate the HTTPS certificate.'
    fi
    certificate_usable "$work_dir/server.crt" "$work_dir/server.key" 2592000 || fail 'The generated HTTPS certificate failed validation.'
    mv "$work_dir/server.key" "$key"
    mv "$work_dir/server.crt" "$cert"
    echo "Generated a 365-day self-signed HTTPS certificate for $host_ip."
    echo 'Trust the new certificate on each demo machine before using the app and media.'
fi
chmod 600 "$key"
chmod 644 "$cert"

fingerprint=$(certificate_fingerprint)
env_tmp=$(mktemp "$https_file.XXXXXX")
{
    printf '%s\n' '# Generated by prepare-https.sh; do not edit or commit.'
    printf 'HTTPS_HOST_IP=%s\n' "$host_ip"
    printf 'FRONTEND_ORIGIN=https://%s\n' "$host_ip"
    printf 'S3_PUBLIC_ENDPOINT=https://%s:9443\n' "$host_ip"
    printf 'TLS_CERT_SHA256=%s\n' "$fingerprint"
} >"$env_tmp"
if ! cmp -s "$env_tmp" "$https_file"; then mv "$env_tmp" "$https_file"; fi

printf 'Application: https://%s\nMedia: https://%s:9443\nCertificate: %s\n' "$host_ip" "$host_ip" "$cert"
