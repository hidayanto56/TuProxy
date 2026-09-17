#!/bin/bash
set -e
cd "$(dirname "$0")"

# Muat secret dari .env (file ini di-gitignore, jangan hardcode key di script)
if [ -f .env ]; then set -a; . ./.env; set +a; fi

# OpenCode via Headroom proxy (hemat token) -> upstream OpenRouter.
#   ./opencode-headroom.sh              # serve 0.0.0.0:4096 + TUI lokal
#   PORT=5096 ./opencode-headroom.sh    # port lain
#   ./opencode-headroom.sh --tunnel     # serve localhost saja (untuk SSH -L tunnel)
# Login browser: username `opencode`, password `pass` (override: OPENCODE_PASSWORD=...).
#
# Rantai: OpenCode provider openai -> http://127.0.0.1:8788/v1 (headroom-openrouter)
#         -> upstream OpenRouter. Auth: OpenRouter key diteruskan via header.
# Kontainer LAMA headroom-proxy (milik claude.sh) tidak disentuh.

BIND="0.0.0.0"
ARGS=()
for a in "$@"; do
    [ "$a" = "--tunnel" ] && BIND="127.0.0.1" || ARGS+=("$a")
done
PORT="${PORT:-4096}"
OPENCODE_PASSWORD="${OPENCODE_PASSWORD:-pass}"
export OPENCODE_PASSWORD
export OPENCODE_SERVER_PASSWORD="$OPENCODE_PASSWORD"

# --- env: sama seperti opencode.sh (bypass MITM VPN) ---
unset OPENCODE_API_KEY
unset OPENAI_API_KEY
unset ANTHROPIC_API_KEY
unset ANTHROPIC_AUTH_TOKEN
unset ANTHROPIC_BASE_URL
unset OLLAMA_API_KEY
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy
# Egress kontainer headroom SELALU via Tailscale (sesuai instruksi),
# bukan hasil probe — kontainer tidak selalu bisa menjangkau IP LAN HP.
UPSTREAM_PROXY="http://100.100.1.1:8080"
export HTTP_PROXY_SERVER="${HTTP_PROXY_SERVER:-http://100.100.1.1:8080}"
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
if [ -f "/Users/admin/.config/opencode/certs/gp-ca.pem" ]; then
    export NODE_EXTRA_CA_CERTS="/Users/admin/.config/opencode/certs/gp-ca.pem"
fi
: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY kosong — isi di .env (file di-gitignore)}"
export OPENROUTER_API_KEY
export P_CONFIG="/Users/admin/.config/opencode/opencode.json"
export OPENCODE_CONFIG="$P_CONFIG"

# --- headroom-openrouter (kontainer BARU, headroom-proxy lama tidak disentuh) ---
# Entrypoint image = `headroom proxy`; argumen ditempel setelah nama image.
# --backend openrouter : upstream = OpenRouter (bukan default api.openai.com)
# Egress via -e HTTP_PROXY/HTTPS_PROXY process-wide: image ini (chopratejas,
# lebih tua dari docs online) TIDAK punya flag --http-proxy / HEADROOM_HTTP_PROXY,
# jadi ini satu-satunya mekanisme (HTTPX membacanya). Docs versi baru melarang
# pola ini, tapi di image ini tidak ada alternatif.
HP_PORT=8788
if ! docker info >/dev/null 2>&1; then
    echo "Docker belum jalan, membuka Docker Desktop..."
    open -a Docker
    until docker info >/dev/null 2>&1; do sleep 2; done
fi
if docker ps -a --format '{{.Names}}' | grep -q '^headroom-openrouter$'; then
    docker rm -f headroom-openrouter >/dev/null 2>&1 || true
fi
docker run -d -p 127.0.0.1:$HP_PORT:8787 --name headroom-openrouter \
  -e HTTP_PROXY="$UPSTREAM_PROXY" -e HTTPS_PROXY="$UPSTREAM_PROXY" \
  -e http_proxy="$UPSTREAM_PROXY" -e https_proxy="$UPSTREAM_PROXY" \
  -e NO_PROXY="localhost,127.0.0.1" -e no_proxy="localhost,127.0.0.1" \
  ghcr.io/chopratejas/headroom:latest \
  --host 0.0.0.0 --port 8787 --backend openrouter >/dev/null
i=0
while [ $i -lt 40 ]; do
    curl -s -m 2 "http://127.0.0.1:$HP_PORT/health" >/dev/null 2>&1 && break
    sleep 0.5
    i=$((i + 1))
done
curl -s -m 3 "http://127.0.0.1:$HP_PORT/health" >/dev/null 2>&1 \
  || { echo "headroom-openrouter gagal start"; docker logs headroom-openrouter 2>&1 | tail -n 10; exit 1; }
echo "headroom-openrouter jalan di :$HP_PORT (backend openrouter, egress via $UPSTREAM_PROXY)"

# Tes fungsional end-to-end (chat completion 1 token pada model :free = $0).
# /v1/models TIDAK bisa jadi patokan: di image ini endpoint itu selalu diteruskan
# ke default api.openai.com (menghasilkan "Incorrect API key") apa pun --backend-nya.
# Yang menentukan adalah /v1/chat/completions.
# Catatan: 429 "free-models-per-day" dari OpenRouter BUKAN kegagalan rantai —
# justru bukti tembus (respons upstream asli). Yang habis hanya kuota harian
# model gratis (50/hari); model berbayar tetap jalan.
HR_RESP="$(curl -s -m 60 -H "Authorization: Bearer $OPENROUTER_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"nex-agi/nex-n2.5-mini:free","messages":[{"role":"user","content":"ping"}],"max_tokens":1}' \
  "http://127.0.0.1:$HP_PORT/v1/chat/completions")"
if echo "$HR_RESP" | grep -q '"choices"'; then
    echo "headroom tembus ke OpenRouter (chat completions 200)."
    USE_HEADROOM=1
elif echo "$HR_RESP" | grep -q 'free-models-per-day'; then
    RESET="$(echo "$HR_RESP" | grep -o 'X-RateLimit-Reset[^0-9]*[0-9]*' | grep -o '[0-9]*' | head -n 1)"
    [ -n "$RESET" ] && RESET=" (reset $(date -r $((RESET / 1000)) '+%d %b %H:%M' 2>/dev/null || echo "$RESET"))"
    echo "headroom tembus, tapi kuota harian model GRATIS habis$RESET."
    echo "Lanjut dengan headroom (model berbayar tetap jalan)."
    USE_HEADROOM=1
else
    echo "PERINGATAN: headroom belum tembus ke OpenRouter. Respons:"
    echo "$HR_RESP" | head -c 400
    echo
    echo "Lanjut tanpa headroom? (Enter = lanjut, Ctrl-C = batal)"
    read -r _
    USE_HEADROOM=0
fi

# --- arahkan provider openai OpenCode ke headroom (lengkap, bukan terpotong) ---
if [ "$USE_HEADROOM" = "1" ]; then
    export OPENAI_BASE_URL="http://127.0.0.1:$HP_PORT/v1"
    export OPENAI_API_KEY="$OPENROUTER_API_KEY"
fi

LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || echo '<ip-lan>')"
TS_IP="$(ifconfig 2>/dev/null | grep -o 'inet 100\.[0-9.]*' | head -1 | awk '{print $2}')"
[ -z "$TS_IP" ] && TS_IP='<ip-tailscale-100.x>'

# --- serve web + TUI lokal dalam satu jalan (seperti opencode.sh) ---
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
    echo "OpenCode Web (via headroom: $([ "$USE_HEADROOM" = "1" ] && echo ya || echo tidak)):"
    echo "  via Tailscale : http://$TS_IP:$PORT   (atau http://opencode:$OPENCODE_PASSWORD@$TS_IP:$PORT agar tanpa popup)"
    echo "  via LAN       : http://$LAN_IP:$PORT"
    echo "  login         : username=opencode password=$OPENCODE_PASSWORD"
    [ "$BIND" = "127.0.0.1" ] && echo "  mode tunnel   : ssh -L $PORT:localhost:$PORT <user>@<host-ini>"
    echo "  cek hemat     : curl -s http://127.0.0.1:$HP_PORT/stats | head -c 300"
    exec opencode2 --server "http://opencode:$OPENCODE_PASSWORD@localhost:$PORT" . "${ARGS[@]}"
else
    echo "opencode2 tidak ditemukan."
    exit 1
fi
