#!/usr/bin/env bash
# Prepara o runtime embutido no fragmento Windows x64:
#   runtime/node/node.exe              Node.js portátil (SHA-256 conferido com nodejs.org)
#   runtime/server/node_modules/...    servidor mcp-abap-adt e dependências (para win32/x64)
#   runtime/runtime.properties         caminhos e versões lidos pelo plugin
#   runtime/THIRD-PARTY.txt            licenças e árvore de dependências
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PROPS="$ROOT/embedded-runtime.properties"
OUT="$ROOT/com.brainconsulting.adt.mcp.runtime.win32.x86_64/runtime"

prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r' | xargs; }

NODE_VERSION="$(prop node.version)"
SERVER_PACKAGE="$(prop server.package)"
SERVER_VERSION="$(prop server.version)"

rm -rf "$OUT/node" "$OUT/server" "$OUT/runtime.properties" "$OUT/THIRD-PARTY.txt"
mkdir -p "$OUT/node" "$OUT/server"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ---------------------------------------------------------------- Node.js
ZIP="node-v${NODE_VERSION}-win-x64.zip"
BASE="https://nodejs.org/dist/v${NODE_VERSION}"
echo "Baixando Node.js ${NODE_VERSION} (win-x64)..."
curl -fsSL "$BASE/$ZIP" -o "$TMP/$ZIP"
curl -fsSL "$BASE/SHASUMS256.txt" -o "$TMP/SHASUMS256.txt"
EXPECTED="$(grep "  ${ZIP}\$" "$TMP/SHASUMS256.txt" | cut -d' ' -f1)"
ACTUAL="$(sha256sum "$TMP/$ZIP" | cut -d' ' -f1)"
if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
  echo "ERRO: SHA-256 do Node.js não confere (esperado '$EXPECTED', obtido '$ACTUAL')." >&2
  exit 1
fi
unzip -q -j "$TMP/$ZIP" "node-v${NODE_VERSION}-win-x64/node.exe" "node-v${NODE_VERSION}-win-x64/LICENSE" -d "$OUT/node"
echo "Node.js OK: $(du -h "$OUT/node/node.exe" | cut -f1)"

# --------------------------------------------------------- versão do servidor
if [ "$SERVER_VERSION" = "auto" ]; then
  SERVER_VERSION="$(npm view "$SERVER_PACKAGE" time --json | node -e '
    let s = ""; process.stdin.on("data", d => s += d).on("end", () => {
      const t = JSON.parse(s), cutoff = Date.now() - 14 * 864e5;
      const cmp = (a, b) => { const x = a.split(".").map(Number), y = b.split(".").map(Number);
        for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] - y[i]; return 0; };
      const v = Object.keys(t).filter(k => /^\d+\.\d+\.\d+$/.test(k) && Date.parse(t[k]) <= cutoff).sort(cmp);
      if (!v.length) { console.error("nenhuma versão com mais de 14 dias"); process.exit(1); }
      console.log(v[v.length - 1]);
    });')"
  echo "server.version=auto -> ${SERVER_VERSION} (mais recente publicada há pelo menos 14 dias)"
fi

# ------------------------------------------------------------- servidor MCP
echo "Instalando ${SERVER_PACKAGE}@${SERVER_VERSION} para win32/x64..."
cd "$OUT/server"
echo '{ "name": "mcp-abap-adt-embedded", "private": true }' > package.json
npm install "${SERVER_PACKAGE}@${SERVER_VERSION}" \
  --os=win32 --cpu=x64 --omit=dev --ignore-scripts --no-audit --no-fund --save-exact

PKG_DIR="node_modules/${SERVER_PACKAGE}"
ENTRY="$(node -e '
  const p = require(process.argv[1]);
  const b = typeof p.bin === "string" ? p.bin : (p.bin["mcp-abap-adt"] || Object.values(p.bin)[0]);
  if (!b) { console.error("pacote sem bin"); process.exit(1); }
  console.log(b.replace(/^\.\//, ""));' "$OUT/server/$PKG_DIR/package.json")"
ENGINES="$(node -e 'const p=require(process.argv[1]); console.log((p.engines&&p.engines.node)||"");' "$OUT/server/$PKG_DIR/package.json")"
echo "Entrada do servidor: $PKG_DIR/$ENTRY  | engines.node: ${ENGINES:-(não declarado)}"

# Confere o mínimo de versão do Node exigido pelo pacote (formato ">=X.Y")
MIN_MAJOR="$(echo "$ENGINES" | grep -oE '>= *[0-9]+' | head -1 | grep -oE '[0-9]+' || true)"
if [ -n "$MIN_MAJOR" ] && [ "${NODE_VERSION%%.*}" -lt "$MIN_MAJOR" ]; then
  echo "ERRO: ${SERVER_PACKAGE} exige Node ${ENGINES}; ajuste node.version em embedded-runtime.properties." >&2
  exit 1
fi

# --------------------------------------------------------- licenças e índice
{
  echo "Componentes de terceiros embutidos no MCP ABAP ADT Launcher (Windows x64)"
  echo
  echo "Node.js ${NODE_VERSION} - licença em node/LICENSE"
  echo
  echo "${SERVER_PACKAGE}@${SERVER_VERSION} é distribuído sem modificações, sob a licença declarada"
  echo "pelo próprio pacote (veja a lista abaixo e server/${PKG_DIR}/LICENSE)."
  echo "Código-fonte correspondente:"
  echo "  https://www.npmjs.com/package/${SERVER_PACKAGE}/v/${SERVER_VERSION}"
  echo "  https://github.com/fr0ster/mcp-abap-adt"
  echo
  echo "Árvore npm de ${SERVER_PACKAGE}@${SERVER_VERSION}:"
  npm ls --all --omit=dev 2>/dev/null || true
  echo
  echo "Licenças declaradas:"
  node -e '
    const fs = require("fs"), path = require("path"), out = new Set();
    function pkgs(dir) {
      if (!fs.existsSync(dir)) return;
      for (const n of fs.readdirSync(dir, { withFileTypes: true })) {
        if (!n.isDirectory() || n.name.startsWith(".")) continue;
        const p = path.join(dir, n.name);
        if (n.name.startsWith("@")) { pkgs(p); continue; }
        try {
          const j = JSON.parse(fs.readFileSync(path.join(p, "package.json"), "utf8"));
          const lic = typeof j.license === "string" ? j.license : (j.license && j.license.type) || "(sem licença declarada)";
          out.add(j.name + "@" + j.version + "  " + lic);
        } catch (e) { /* sem package.json */ }
        pkgs(path.join(p, "node_modules"));
      }
    }
    pkgs("node_modules");
    console.log([...out].sort().join("\n"));'
} > "$OUT/THIRD-PARTY.txt"

LICENSE_DECL="$(node -e 'const p=require(process.argv[1]); console.log(typeof p.license==="string"?p.license:"(não declarada)");' "$OUT/server/$PKG_DIR/package.json")"
echo "Licença de ${SERVER_PACKAGE}@${SERVER_VERSION}: ${LICENSE_DECL}"

cat > "$OUT/runtime.properties" <<EOF
node=node/node.exe
server.entry=server/${PKG_DIR}/${ENTRY}
server.package=${SERVER_PACKAGE}
server.version=${SERVER_VERSION}
node.version=${NODE_VERSION}
server.license=${LICENSE_DECL}
EOF
echo "runtime.properties:"; cat "$OUT/runtime.properties"
echo "Tamanho do runtime: $(du -sh "$OUT" | cut -f1)"
