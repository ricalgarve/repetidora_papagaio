#!/usr/bin/env python3
"""
==============================================================================
 Repetidora Papagaio (Dust Coyotes) - Servidor de Compartilhamento do APK
==============================================================================
Inicia um servidor local na rede Wi-Fi/Ethernet para você baixar e instalar
o APK em qualquer celular, tablet ou máquina da rede sem precisar de cabo USB.
==============================================================================
"""

import os
import sys
import socket
import argparse
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

try:
    import qrcode
    HAS_QRCODE = True
except ImportError:
    HAS_QRCODE = False

def get_local_ip():
    """Descobre o IP real da máquina na rede local Wi-Fi / Ethernet."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip

def find_apk_file():
    """Localiza o arquivo APK mais recente no projeto."""
    candidates = [
        "RepetidoraPapagaio.apk",
        "app/build/outputs/apk/debug/app-debug.apk",
        "app/build/outputs/apk/release/app-release.apk",
        "app/build/outputs/apk/release/app-release-unsigned.apk",
        "../RPT_Papagaio.apk",
        "RPT_Papagaio.apk"
    ]
    base_dir = os.path.dirname(os.path.abspath(__file__))
    for rel_path in candidates:
        full_path = os.path.join(base_dir, rel_path)
        if os.path.isfile(full_path):
            return full_path
    return None

class ApkDownloadHandler(BaseHTTPRequestHandler):

    def do_HEAD(self):
        apk_path = find_apk_file()
        if not apk_path:
            self.send_error(404, "Arquivo APK nao encontrado no servidor.")
            return

        filename = os.path.basename(apk_path)
        file_size = os.path.getsize(apk_path)
        self.send_response(200)
        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Length", str(file_size))
        self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
        self.end_headers()

    def send_apk(self, apk_path, file_size, filename):
        range_header = self.headers.get("Range")
        start = 0
        end = file_size - 1
        is_range = False

        if range_header and range_header.startswith("bytes="):
            try:
                parts = range_header[6:].split("-")
                start = int(parts[0]) if parts[0] else 0
                if len(parts) > 1 and parts[1]:
                    end = int(parts[1])
                is_range = True
            except Exception:
                start = 0
                end = file_size - 1
                is_range = False

        content_length = (end - start) + 1

        if is_range:
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
        else:
            self.send_response(200)

        self.send_header("Content-Type", "application/vnd.android.package-archive")
        self.send_header("Content-Length", str(content_length))
        self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        self.end_headers()
        self.wfile.flush()

        try:
            self.connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            self.connection.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 4 * 1024 * 1024)
        except Exception:
            pass

        chunk_size = 1024 * 1024  # 1 MB
        with open(apk_path, "rb") as f:
            if start > 0:
                f.seek(start)
            remaining = content_length
            while remaining > 0:
                to_read = min(remaining, chunk_size)
                buf = f.read(to_read)
                if not buf:
                    break
                try:
                    self.wfile.write(buf)
                    remaining -= len(buf)
                except (BrokenPipeError, ConnectionResetError):
                    break

        try:
            self.wfile.flush()
        except Exception:
            pass

    def do_GET(self):
        apk_path = find_apk_file()
        filename = os.path.basename(apk_path) if apk_path else "RepetidoraPapagaio.apk"
        file_size = os.path.getsize(apk_path) if apk_path else 0
        size_mb = f"{file_size / (1024 * 1024):.1f} MB" if file_size else "Desconhecido"

        # Rota de download direto do arquivo APK
        if self.path in ["/download", "/RepetidoraPapagaio.apk", "/app-debug.apk", "/app-release.apk"] or self.path.endswith(".apk"):
            if not apk_path:
                self.send_error(404, "Arquivo APK nao encontrado.")
                return
            self.send_apk(apk_path, file_size, filename)
            return

        # Rota para servir o logo do app
        if self.path in ["/logo.png", "/logo_dust_coyotes.png"]:
            base_dir = os.path.dirname(os.path.abspath(__file__))
            logo_path = os.path.join(base_dir, "logo_dust_coyotes.png")
            if os.path.isfile(logo_path):
                with open(logo_path, "rb") as lf:
                    data = lf.read()
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                return

        # Pagina Web de Apresentacao e Download
        local_ip = get_local_ip()
        download_url = f"http://{local_ip}:{self.server.server_port}/download"

        html_content = f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Repetidora Papagaio (Dust Coyotes) - Download APK</title>
    <style>
        :root {{
            --bg: #0b0f19;
            --card: #151d30;
            --accent: #d97706;
            --accent-hover: #b45309;
            --success: #10b981;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --border: #22304d;
        }}
        * {{ box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }}
        body {{
            background: var(--bg);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }}
        .container {{
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 24px;
            max-width: 480px;
            width: 100%;
            padding: 36px 26px;
            text-align: center;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
        }}
        .logo-img {{
            width: 128px;
            height: 128px;
            margin: 0 auto 16px;
            border-radius: 50%;
            box-shadow: 0 10px 30px rgba(217, 119, 6, 0.35);
            display: block;
            object-fit: cover;
        }}
        h1 {{ font-size: 26px; font-weight: 800; margin-bottom: 6px; letter-spacing: -0.5px; }}
        .badge {{
            display: inline-block;
            background: rgba(16, 185, 129, 0.15);
            color: var(--success);
            border: 1px solid var(--success);
            font-size: 11px;
            font-weight: 700;
            padding: 3px 12px;
            border-radius: 20px;
            margin-bottom: 14px;
        }}
        p.desc {{
            color: var(--text-muted);
            font-size: 14px;
            line-height: 1.5;
            margin-bottom: 22px;
        }}
        .info-grid {{
            background: rgba(11, 15, 25, 0.7);
            border: 1px solid var(--border);
            border-radius: 14px;
            padding: 14px;
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            margin-bottom: 24px;
            text-align: left;
            font-size: 13px;
        }}
        .info-label {{ color: var(--text-muted); font-size: 11px; }}
        .info-value {{ font-weight: 600; color: var(--text-main); }}
        .btn-download {{
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
            background: linear-gradient(135deg, #f59e0b, #d97706);
            color: white;
            text-decoration: none;
            padding: 16px 24px;
            border-radius: 14px;
            font-size: 16px;
            font-weight: 700;
            transition: all 0.2s ease;
            box-shadow: 0 10px 25px rgba(217, 119, 6, 0.4);
        }}
        .btn-download:hover, .btn-download:active {{
            background: #b45309;
            transform: translateY(-2px);
            box-shadow: 0 14px 28px rgba(217, 119, 6, 0.5);
        }}
        .install-guide {{
            margin-top: 24px;
            padding-top: 20px;
            border-top: 1px solid var(--border);
            text-align: left;
            font-size: 12px;
            color: var(--text-muted);
            line-height: 1.6;
        }}
        .install-guide b {{ color: var(--text-main); }}
    </style>
</head>
<body>
    <div class="container">
        <img class="logo-img" src="/logo.png" alt="Dust Coyotes Logo" onerror="this.style.display='none'">
        <h1>Repetidora Papagaio</h1>
        <div class="badge">100% Livre de Rastreamento • Tela Bloqueada</div>
        <p class="desc">Aplicativo repetidora de áudio em Java nativo com sensibilidade ajustável, reprodução periódica, histórico com expurgo automático e operação em background.</p>

        <div class="info-grid">
            <div>
                <div class="info-label">Arquivo APK</div>
                <div class="info-value">{filename}</div>
            </div>
            <div>
                <div class="info-label">Tamanho</div>
                <div class="info-value">{size_mb}</div>
            </div>
            <div>
                <div class="info-label">Rastreadores / Ads</div>
                <div class="info-value" style="color: #10b981;">0% (Zero Trackers)</div>
            </div>
            <div>
                <div class="info-label">Expurgo de Histórico</div>
                <div class="info-value" style="color: #10b981;">Ativo (Automático)</div>
            </div>
        </div>

        <a href="{download_url}" class="btn-download">
            <span>📥</span> Baixar {filename}
        </a>

        <div class="install-guide">
            <b>💡 Como instalar no celular:</b>
            <ol style="margin-left: 18px; margin-top: 6px;">
                <li>Toque no botão acima para iniciar o download do APK.</li>
                <li>Ao abrir o instalador, se o Android solicitar, selecione <b>"Permitir desta fonte"</b>.</li>
                <li>Conceda as permissões de Microfone e Notificação para o funcionamento em background com tela bloqueada!</li>
            </ol>
        </div>
    </div>
</body>
</html>"""

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(html_content.encode("utf-8"))))
        self.end_headers()
        self.wfile.write(html_content.encode("utf-8"))

    def log_message(self, format, *args):
        print(f"[{self.log_date_time_string()}] {args[0]} - {args[1]}")

def main():
    parser = argparse.ArgumentParser(description="Servidor de Compartilhamento do APK Repetidora Papagaio via Wi-Fi")
    parser.add_argument("--port", type=int, default=8000, help="Porta HTTP (padrao: 8000)")
    args = parser.parse_args()

    apk_path = find_apk_file()
    if not apk_path:
        print("\n[AVISO] Nenhum arquivo APK encontrado na pasta ainda!")
        print("Compile o projeto com 'build.bat' ou './gradlew assembleDebug'.")
        print("O servidor ficara ativo aguardando o APK...\n")
        file_size_mb = 0.0
        apk_name = "Aguardando APK..."
    else:
        file_size_mb = os.path.getsize(apk_path) / (1024 * 1024)
        apk_name = os.path.basename(apk_path)

    local_ip = get_local_ip()
    url = f"http://{local_ip}:{args.port}"

    print("==============================================================================")
    print("      🦜  REPETIDORA PAPAGAIO (DUST COYOTES) - SERVIDOR DE DOWNLOAD VIA WI-FI  ")
    print("==============================================================================")
    print(f" [*] Arquivo APK : {apk_name} ({file_size_mb:.1f} MB)")
    print(f" [*] Endereco    : {url}")
    print("==============================================================================")
    print("\nNo celular ou em outro computador da rede, acesse:")
    print(f"👉 \033[1;32m{url}\033[0m\n")

    if HAS_QRCODE:
        print("Ou aponte a camera do celular para o QR Code abaixo:")
        qr = qrcode.QRCode(border=1)
        qr.add_data(url)
        qr.print_ascii(invert=True)
        print("==============================================================================")

    print(f"Servidor ativo na porta {args.port}. Pressione Ctrl + C para encerrar.\n")

    server = ThreadingHTTPServer(("0.0.0.0", args.port), ApkDownloadHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[*] Servidor encerrado.")
        server.server_close()

if __name__ == "__main__":
    main()
