#!/bin/bash
set -e
cd "$(dirname "$0")"

# Muat secret dari .env (file ini di-gitignore, jangan hardcode key di script)
if [ -f .env ]; then set -a; . ./.env; set +a; fi

# Bersihkan variabel pembajak routing cloud & proxy HP peninggalan mode lokal
unset OPENCODE_API_KEY
unset OPENAI_API_KEY
unset ANTHROPIC_API_KEY
unset ANTHROPIC_AUTH_TOKEN
unset ANTHROPIC_BASE_URL
unset OLLAMA_API_KEY
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy
# Proxy HP POCO untuk bypass MITM VPN (seperti semula)
# Utama via Tailscale (100.100.1.1 — jalan lintas jaringan, tidak tergantung Wi-Fi yang
# sama); fallback ke LAN (192.168.31.192) bila Tailscale tidak tembus. Port 8080 di kedua
# interface adalah proxy yang sama. Override manual: HTTP_PROXY_SERVER=... ./opencode.sh
if [ -z "${HTTP_PROXY_SERVER:-}" ]; then
    if curl -s -m 3 -x http://100.100.1.1:8080 -o /dev/null http://example.com 2>/dev/null; then
        HTTP_PROXY_SERVER="http://100.100.1.1:8080"
    else
        HTTP_PROXY_SERVER="http://192.168.31.192:8080"
    fi
fi
export HTTP_PROXY="$HTTP_PROXY_SERVER"
export HTTPS_PROXY="$HTTP_PROXY_SERVER"
export ALL_PROXY="$HTTP_PROXY_SERVER"
export http_proxy="$HTTP_PROXY_SERVER"
export https_proxy="$HTTP_PROXY_SERVER"
export all_proxy="$HTTP_PROXY_SERVER"

export NO_PROXY="localhost,127.0.0.1,::1,.local,100.100.1.1,192.168.31.192"
export no_proxy="$NO_PROXY"
export NODE_TLS_REJECT_UNAUTHORIZED="0"
export PYTHONHTTPSVERIFY=0
export REQUESTS_CA_BUNDLE=""
export CURL_CA_BUNDLE=""
export LITELLM_INSECURE_SKIP_VERIFY="true"
# CA GlobalProtect agar TLS tidak SELF_SIGNED_CERT_IN_CHAIN saat VPN aktif
# (mekanisme resmi docs network). Guard -f: file hilang = jangan set-vars.
if [ -f "/Users/admin/.config/opencode/certs/gp-ca.pem" ]; then
    export NODE_EXTRA_CA_CERTS="/Users/admin/.config/opencode/certs/gp-ca.pem"
fi
: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY kosong — isi di .env (file di-gitignore)}"
export OPENROUTER_API_KEY
# Jalur berkas konfigurasi resmi OpenCode 2.0
export P_CONFIG="/Users/admin/.config/opencode/opencode.json"
export OPENCODE_CONFIG="$P_CONFIG"

# Docker + headroom-proxy (cermin claude.sh: dipakai Claude, disiapkan juga untuk OpenCode)
if ! docker info >/dev/null 2>&1; then
    echo "Docker belum jalan, membuka Docker Desktop..."
    open -a Docker
    until docker info >/dev/null 2>&1; do sleep 2; done
fi
if docker ps -a --format '{{.Names}}' | grep -q '^headroom-proxy$'; then
    docker start headroom-proxy >/dev/null 2>&1 || true
else
    docker run -d -p 8787:8787 --name headroom-proxy ghcr.io/chopratejas/headroom:latest >/dev/null
fi

echo "🚀 Meluncurkan OpenCode 2.0 (TUI lokal + serve web, 1 file)..."

# --- serve web + TUI lokal dalam satu jalan ---
# serve di background, TUI ditempel ke server itu via --server (sesi shared).
#   ./opencode.sh              # serve 0.0.0.0:4096 + TUI lokal
#   PORT=5096 ./opencode.sh    # port lain
#   ./opencode.sh --tunnel     # serve localhost saja (untuk SSH -L tunnel)
BIND="0.0.0.0"
ARGS=()
for a in "$@"; do
    [ "$a" = "--tunnel" ] && BIND="127.0.0.1" || ARGS+=("$a")
done
PORT="${PORT:-4096}"
OPENCODE_PASSWORD="${OPENCODE_PASSWORD:-pass}"
export OPENCODE_PASSWORD
export OPENCODE_SERVER_PASSWORD="$OPENCODE_PASSWORD"

LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || echo '<ip-lan>')"
TS_IP="$(ifconfig 2>/dev/null | grep -o 'inet 100\.[0-9.]*' | head -1 | awk '{print $2}')"
[ -z "$TS_IP" ] && TS_IP='<ip-tailscale-100.x>'

# Eksekusi biner murni tanpa flag --config yang dilarang
if command -v opencode2 >/dev/null 2>&1; then
    LOG="/tmp/opencode-serve-$PORT.log"
    : >"$LOG"
    opencode2 serve --hostname "$BIND" --port "$PORT" >>"$LOG" 2>&1 &
    SRV=$!
    trap 'kill $SRV 2>/dev/null' INT TERM EXIT
    i=0
    while [ $i -lt 40 ]; do
        grep -q 'server listening on' "$LOG" 2>/dev/null && break
        sleep 0.5
        i=$((i + 1))
    done
    if ! grep -q 'server listening on' "$LOG" 2>/dev/null; then
        echo "serve gagal start — lihat $LOG"
        exit 1
    fi
    echo "OpenCode Web:"
    echo "  via Tailscale : http://$TS_IP:$PORT   (atau http://opencode:$OPENCODE_PASSWORD@$TS_IP:$PORT agar tanpa popup)"
    echo "  via LAN       : http://$LAN_IP:$PORT"
    echo "  login         : username=opencode password=$OPENCODE_PASSWORD"
    [ "$BIND" = "127.0.0.1" ] && echo "  mode tunnel   : ssh -L $PORT:localhost:$PORT <user>@<host-ini>"
    exec opencode2 --server "http://opencode:$OPENCODE_PASSWORD@localhost:$PORT" . "${ARGS[@]}"
else
    opencode_remote_url="http://localhost:$PORT"
    npx -y @opencode/cli@beta web --hostname "$BIND" --port "$PORT" >/tmp/opencode-web-$PORT.log 2>&1 &
    SRV=$!
    trap 'kill $SRV 2>/dev/null' INT TERM EXIT
    sleep 3
    exec npx -y @opencode/cli@beta attach "$opencode_remote_url" "${ARGS[@]}"
fi
