import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WebServer {
    public static void main(String[] args) throws IOException {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new DashboardHandler());
        server.setExecutor(null);
        System.out.println("Server running on port " + port);
        server.start();
    }

    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>E-Commerce Margin Leak Detector</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 2rem; }
                        .container { max-width: 900px; margin: 0 auto; }
                        h1 { font-size: 1.8rem; margin-bottom: 0.5rem; color: #38bdf8; }
                        p.subtitle { color: #94a3b8; margin-top: 0; margin-bottom: 2rem; }
                        .card { background: #1e293b; border-radius: 8px; padding: 1.5rem; margin-bottom: 1.5rem; border: 1px solid #334155; }
                        .card-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 0.75rem; margin-bottom: 1rem; }
                        .sku { font-size: 0.85rem; color: #94a3b8; }
                        .badge { padding: 0.25rem 0.6rem; border-radius: 4px; font-size: 0.75rem; font-weight: bold; }
                        .badge-critical { background: #ef4444; color: #fff; }
                        .badge-high { background: #f97316; color: #fff; }
                        .badge-ok { background: #22c55e; color: #fff; }
                        .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1rem; }
                        .stat { background: #0f172a; padding: 0.75rem; border-radius: 6px; }
                        .stat-label { font-size: 0.75rem; color: #94a3b8; }
                        .stat-val { font-size: 1.1rem; font-weight: 600; margin-top: 0.25rem; }
                        .leak-alert { background: #451a1a; border-left: 4px solid #ef4444; padding: 0.75rem; border-radius: 4px; font-size: 0.9rem; margin-top: 0.75rem; }
                        .leak-alert.warning { background: #432818; border-color: #f97316; }
                        .leak-alert.ok { background: #064e3b; border-color: #22c55e; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🛒 E-Commerce Margin Leak Detector</h1>
                        <p class="subtitle">Real-time unit economics audit & automated profitability leak inspection.</p>

                        <div class="card">
                            <div class="card-header">
                                <div><strong>Solid Wood Study Desk</strong> <span class="sku">(SKU-101)</span></div>
                                <span class="badge badge-critical">CRITICAL LEAK</span>
                            </div>
                            <div class="grid">
                                <div class="stat"><div class="stat-label">Selling Price</div><div class="stat-val">$80.00</div></div>
                                <div class="stat"><div class="stat-label">Sourcing Cost</div><div class="stat-val">$45.00</div></div>
                                <div class="stat"><div class="stat-label">Logistics/Fees</div><div class="stat-val">$57.50</div></div>
                                <div class="stat"><div class="stat-label">Net Margin</div><div class="stat-val" style="color: #ef4444;">-$22.50 (-28.1%)</div></div>
                            </div>
                            <div class="leak-alert">⚠️ <strong>Critical Margin Leak:</strong> Heavy weight (18.0 kg) logistics surcharges combined with referral fees exceed gross profit. Sold at a net loss.</div>
                        </div>

                        <div class="card">
                            <div class="card-header">
                                <div><strong>Velvet Evening Gown</strong> <span class="sku">(SKU-202)</span></div>
                                <span class="badge badge-high">HIGH RISK</span>
                            </div>
                            <div class="grid">
                                <div class="stat"><div class="stat-label">Selling Price</div><div class="stat-val">$120.00</div></div>
                                <div class="stat"><div class="stat-label">Sourcing Cost</div><div class="stat-val">$40.00</div></div>
                                <div class="stat"><div class="stat-label">Logistics/Fees</div><div class="stat-val">$34.60</div></div>
                                <div class="stat"><div class="stat-label">Net Margin</div><div class="stat-val" style="color: #f97316;">+$45.40 (37.8%)</div></div>
                            </div>
                            <div class="leak-alert warning">⚠️ <strong>High Return Rate Erosion:</strong> Customer return rate (28%) requires reverse logistics provisions that silently drain unit profitability.</div>
                        </div>

                        <div class="card">
                            <div class="card-header">
                                <div><strong>Ergonomic Bluetooth Mouse</strong> <span class="sku">(SKU-303)</span></div>
                                <span class="badge badge-ok">HEALTHY</span>
                            </div>
                            <div class="grid">
                                <div class="stat"><div class="stat-label">Selling Price</div><div class="stat-val">$35.00</div></div>
                                <div class="stat"><div class="stat-label">Sourcing Cost</div><div class="stat-val">$10.00</div></div>
                                <div class="stat"><div class="stat-label">Logistics/Fees</div><div class="stat-val">$4.25</div></div>
                                <div class="stat"><div class="stat-label">Net Margin</div><div class="stat-val" style="color: #22c55e;">+$20.75 (59.3%)</div></div>
                            </div>
                            <div class="leak-alert ok">✅ <strong>Optimal Margin Profile:</strong> Passing all audit benchmarks. Low return rate, compact freight weight.</div>
                        </div>
                    </div>
                </body>
                </html>
                """;

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
