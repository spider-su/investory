"""Isolated private metrics listener sharing the serving process's collector registry.

Inference remains bound to pod loopback (127.0.0.1:8090). Only this
metrics-only listener binds the pod interface. A dedicated token is required.
Kubernetes NetworkPolicy must permit port 8091 only from monitoring pods.
"""
from contextlib import contextmanager
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from .config import Settings


@contextmanager
def private_metrics_listener(settings: Settings):
    if not settings.metrics_listener_enabled:
        yield None
        return
    credential = settings.metrics_token
    if credential is None or not credential.get_secret_value():
        raise RuntimeError("RAG metrics listener requires a dedicated metrics token")
    token = credential.get_secret_value()

    class MetricsHandler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != "/metrics":
                self.send_error(404)
                return
            scheme, _, supplied = (self.headers.get("Authorization") or "").partition(" ")
            if scheme.lower() != "bearer" or not hmac.compare_digest(supplied, token):
                self.send_response(401)
                self.send_header("WWW-Authenticate", "Bearer")
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                return
            body = generate_latest()
            self.send_response(200)
            self.send_header("Content-Type", CONTENT_TYPE_LATEST)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format, *args):
            # Avoid URL, auth, or query logging through BaseHTTPRequestHandler.
            pass

        def do_POST(self):
            self.send_error(405)

    server = ThreadingHTTPServer(("0.0.0.0", settings.metrics_listener_port), MetricsHandler)
    server.daemon_threads = True
    thread = Thread(target=server.serve_forever, name="rag-metrics", daemon=True)
    thread.start()
    try:
        yield server
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=3)
