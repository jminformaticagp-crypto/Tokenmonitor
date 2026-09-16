#!/usr/bin/env bash
set -euo pipefail

echo "Corrigindo dependências da carteira e melhorando o diagnóstico..."

python3 - <<'PY'
from pathlib import Path
import re

p = Path("app/build.gradle")
s = p.read_text(encoding="utf-8")

deps = """dependencies {
    implementation 'org.web3j:crypto:4.12.3-android'
    implementation 'org.web3j:utils:4.12.3-android'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
}
"""

s = re.sub(r"dependencies\s*\{.*?\}\s*$", deps, s, flags=re.S)
s = re.sub(r"versionCode\s+\d+", "versionCode 6", s)
s = re.sub(r"versionName\s+'[^']+'", "versionName '0.2.4-wallet-fix'", s)
p.write_text(s, encoding="utf-8")

main = Path("app/src/main/java/com/jean/tokenmonitor/MainActivity.java")
m = main.read_text(encoding="utf-8")
old = '.setMessage("Erro ao criar a carteira: " + e.getClass().getSimpleName())'
new = '.setMessage("Erro ao criar a carteira:\\n" + e.toString() + (e.getCause() != null ? "\\nCausa: " + e.getCause().toString() : ""))'
m = m.replace(old, new)
main.write_text(m, encoding="utf-8")

w = Path(".github/workflows/build-apk.yml")
x = w.read_text(encoding="utf-8")
x = re.sub(r"name:\s*TokenMonitorJean-[^\n]+", "name: TokenMonitorJean-Beta-0.2.4-Wallet-Fix", x)
w.write_text(x, encoding="utf-8")
PY

git add app .github
git commit -m "Corrigir dependencias de runtime da carteira" || true
git push origin HEAD

echo
echo "Concluído. Aguarde o GitHub Actions gerar TokenMonitorJean-Beta-0.2.4-Wallet-Fix."
