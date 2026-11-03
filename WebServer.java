import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {

    public static void main(String[] args) throws IOException {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new DashboardHandler());
        server.setExecutor(null);
        System.out.println("E-Commerce Margin Leak Detector Server running on http://localhost:" + port);
        server.start();
    }

    static class AuditRecord {
        final Product product;
        final List<LeakAlert> alerts;
        final Severity severity;

        AuditRecord(Product product, List<LeakAlert> alerts, Severity severity) {
            this.product = product;
            this.alerts = alerts;
            this.severity = severity;
        }
    }

    static class DashboardHandler implements HttpHandler {
        private final List<AuditRecord> dynamicAudits = Collections.synchronizedList(new ArrayList<>());
        private final MarginLeakDetector detector = new MarginLeakDetector();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String errorMessage = null;

            if ("POST".equalsIgnoreCase(method)) {
                // Read and parse POST body
                InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int read;
                while ((read = is.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                String body = baos.toString(StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                try {
                    AuditRecord record = processProductSubmission(params);
                    // Prepend newly audited product to history
                    dynamicAudits.add(0, record);
                } catch (IllegalArgumentException e) {
                    errorMessage = e.getMessage();
                } catch (Exception e) {
                    errorMessage = "Failed to process product input: " + e.getMessage();
                }
            }

            String html = renderDashboardHtml(errorMessage);
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private AuditRecord processProductSubmission(Map<String, String> params) {
            String sku = params.getOrDefault("sku", "").trim();
            String title = params.getOrDefault("title", "").trim();
            String categoryStr = params.getOrDefault("category", "").trim();
            String priceStr = params.getOrDefault("sellingPrice", "").trim();
            String cogsStr = params.getOrDefault("costOfGoods", "").trim();
            String lenStr = params.getOrDefault("lengthCm", "").trim();
            String widStr = params.getOrDefault("widthCm", "").trim();
            String htStr = params.getOrDefault("heightCm", "").trim();
            String weightStr = params.getOrDefault("actualWeightKg", "").trim();
            String returnRateStr = params.getOrDefault("returnRatePercent", "").trim();

            if (sku.isEmpty()) {
                throw new IllegalArgumentException("Product SKU is required and cannot be blank.");
            }
            if (title.isEmpty()) {
                throw new IllegalArgumentException("Product Title is required and cannot be blank.");
            }

            ProductCategory category;
            try {
                category = ProductCategory.valueOf(categoryStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("Please select a valid product category.");
            }

            double sellingPrice;
            double costOfGoods;
            double lengthCm;
            double widthCm;
            double heightCm;
            double actualWeightKg;
            double returnRatePercent;

            try {
                sellingPrice = Double.parseDouble(priceStr);
                costOfGoods = Double.parseDouble(cogsStr);
                lengthCm = Double.parseDouble(lenStr);
                widthCm = Double.parseDouble(widStr);
                heightCm = Double.parseDouble(htStr);
                actualWeightKg = Double.parseDouble(weightStr);
                returnRatePercent = Double.parseDouble(returnRateStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("All financial amounts, package dimensions, weights, and rates must be valid numbers.");
            }

            // Optional fee parameters with smart defaults
            double commissionPct = parseDoubleOrDefault(params.get("commissionPct"), 15.0);
            double minCommission = parseDoubleOrDefault(params.get("minCommission"), 1.50);
            double shipBaseRate = parseDoubleOrDefault(params.get("shipBaseRate"), 6.50);
            double shipPerKg = parseDoubleOrDefault(params.get("shipPerKg"), 1.50);
            double returnReverseCost = parseDoubleOrDefault(params.get("returnReverseCost"), 10.00);
            double repackagingCost = parseDoubleOrDefault(params.get("repackagingCost"), 5.00);
            double paymentFeePct = parseDoubleOrDefault(params.get("paymentFeePct"), 2.9);

            // Defensive OOP construction (triggers Domain validation)
            Dimensions dimensions = new Dimensions(lengthCm, widthCm, heightCm);
            Product product = new Product(sku, title, category, sellingPrice, costOfGoods,
                    dimensions, actualWeightKg, returnRatePercent);

            // Polymorphic expense attachments
            product.addExpense(new PlatformCommissionExpense("Platform Referral Fee", commissionPct, minCommission));
            product.addExpense(new ShippingExpense("Courier Logistics Freight", shipBaseRate, 1.0, shipPerKg));
            product.addExpense(new ReturnReserveExpense("Reverse Logistics Reserve", returnReverseCost, repackagingCost));
            product.addExpense(new PaymentProcessingExpense("Payment Processing Fee", 0.30, paymentFeePct));

            // Execute OOP Audit Strategy
            List<LeakAlert> alerts = detector.audit(product);

            // Calculate overall risk status
            Severity overallSeverity = Severity.OK;
            if (product.getNetProfit() < 0) {
                overallSeverity = Severity.CRITICAL;
            }
            for (LeakAlert a : alerts) {
                if (a.getSeverity() == Severity.CRITICAL) {
                    overallSeverity = Severity.CRITICAL;
                    break;
                } else if (a.getSeverity() == Severity.HIGH && overallSeverity != Severity.CRITICAL) {
                    overallSeverity = Severity.HIGH;
                } else if (a.getSeverity() == Severity.WARNING && overallSeverity == Severity.OK) {
                    overallSeverity = Severity.WARNING;
                }
            }

            return new AuditRecord(product, alerts, overallSeverity);
        }

        private double parseDoubleOrDefault(String val, double defaultVal) {
            if (val == null || val.trim().isEmpty()) {
                return defaultVal;
            }
            try {
                return Double.parseDouble(val.trim());
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }

        private Map<String, String> parseFormData(String body) {
            Map<String, String> map = new HashMap<>();
            if (body == null || body.trim().isEmpty()) {
                return map;
            }
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String val = (idx < pair.length() - 1)
                            ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                            : "";
                    map.put(key, val);
                } else if (idx == -1 && !pair.isEmpty()) {
                    map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            }
            return map;
        }

        private String escapeHtml(String text) {
            if (text == null) return "";
            return text.replace("&", "&amp;")
                       .replace("<", "&lt;")
                       .replace(">", "&gt;")
                       .replace("\"", "&quot;")
                       .replace("'", "&#39;");
        }

        private String renderDashboardHtml(String errorMessage) {
            StringBuilder sb = new StringBuilder();
            sb.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>E-Commerce Margin Leak Detector | Dynamic Profitability Audit</title>
                    <style>
                        :root {
                            --bg: #0f172a;
                            --card-bg: #1e293b;
                            --border: #334155;
                            --text: #f8fafc;
                            --text-muted: #94a3b8;
                            --primary: #38bdf8;
                            --primary-dark: #0284c7;
                            --critical: #ef4444;
                            --high: #f97316;
                            --warning: #eab308;
                            --ok: #22c55e;
                        }
                        * { box-sizing: border-box; }
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 2rem 1rem; }
                        .container { max-width: 980px; margin: 0 auto; }
                        header { margin-bottom: 2rem; border-bottom: 1px solid var(--border); padding-bottom: 1rem; }
                        h1 { font-size: 1.85rem; margin: 0 0 0.5rem 0; color: var(--primary); display: flex; align-items: center; gap: 0.5rem; }
                        p.subtitle { color: var(--text-muted); margin: 0; font-size: 0.95rem; }
                        
                        /* Alert Banner */
                        .error-banner { background: #451a1a; border-left: 4px solid var(--critical); color: #fca5a5; padding: 1rem; border-radius: 6px; margin-bottom: 1.5rem; font-size: 0.92rem; }
                        
                        /* Form Styles */
                        .form-card { background: var(--card-bg); border-radius: 10px; padding: 1.75rem; margin-bottom: 2.5rem; border: 1px solid #0284c7; box-shadow: 0 6px 24px rgba(2, 132, 199, 0.12); }
                        .form-card h2 { margin: 0 0 0.35rem 0; font-size: 1.3rem; color: var(--primary); }
                        .form-card p.form-desc { color: var(--text-muted); font-size: 0.88rem; margin: 0 0 1.5rem 0; }
                        .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 1rem; margin-bottom: 1rem; }
                        .form-group { display: flex; flex-direction: column; gap: 0.35rem; }
                        .form-group label { font-size: 0.75rem; font-weight: 700; color: #cbd5e1; text-transform: uppercase; letter-spacing: 0.04em; }
                        .form-control { background: #0f172a; border: 1px solid #475569; color: #f8fafc; border-radius: 6px; padding: 0.6rem 0.75rem; font-size: 0.92rem; width: 100%; transition: border-color 0.15s, box-shadow 0.15s; }
                        .form-control:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.25); }
                        
                        /* Accordion / Optional Fees */
                        details.fee-settings { background: #0f172a; border: 1px dashed var(--border); border-radius: 8px; padding: 0.85rem 1rem; margin: 1.25rem 0; }
                        details.fee-settings summary { font-size: 0.82rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; cursor: pointer; user-select: none; }
                        details.fee-settings summary:hover { color: var(--primary); }
                        .fee-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 0.85rem; margin-top: 1rem; }
                        
                        .btn-audit { background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%); color: #ffffff; border: none; border-radius: 6px; padding: 0.85rem 1.5rem; font-size: 1rem; font-weight: 700; cursor: pointer; width: 100%; transition: transform 0.1s, box-shadow 0.15s; box-shadow: 0 4px 12px rgba(2, 132, 199, 0.3); }
                        .btn-audit:hover { background: #0284c7; box-shadow: 0 6px 16px rgba(2, 132, 199, 0.45); }
                        .btn-audit:active { transform: translateY(1px); }
                        
                        /* Section Headings */
                        .section-title { font-size: 1.15rem; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.05em; margin: 2rem 0 1rem 0; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem; }
                        .count-badge { background: #0284c7; color: #fff; font-size: 0.72rem; padding: 0.2rem 0.55rem; border-radius: 12px; }
                        
                        /* Product Cards */
                        .card { background: var(--card-bg); border-radius: 10px; padding: 1.5rem; margin-bottom: 1.5rem; border: 1px solid var(--border); box-shadow: 0 4px 12px rgba(0,0,0,0.15); transition: border-color 0.2s; }
                        .card.dynamic-result { border-color: #38bdf8; position: relative; background: linear-gradient(180deg, #1e293b 0%, #172235 100%); }
                        .card-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 0.85rem; margin-bottom: 1rem; flex-wrap: wrap; gap: 0.5rem; }
                        .title-area { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
                        .sku { font-size: 0.85rem; color: var(--text-muted); font-family: monospace; }
                        .live-pill { background: #0284c7; color: #e0f2fe; padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.68rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.04em; }
                        .ref-pill { background: #334155; color: #cbd5e1; padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.68rem; font-weight: 700; text-transform: uppercase; }
                        
                        /* Badges */
                        .badge { padding: 0.3rem 0.65rem; border-radius: 4px; font-size: 0.75rem; font-weight: 800; letter-spacing: 0.03em; }
                        .badge-critical { background: var(--critical); color: #fff; }
                        .badge-high { background: var(--high); color: #fff; }
                        .badge-warning { background: var(--warning); color: #000; }
                        .badge-ok { background: var(--ok); color: #fff; }
                        
                        /* Financial Grid */
                        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0.85rem; margin-bottom: 1rem; }
                        .stat { background: #0f172a; padding: 0.85rem; border-radius: 8px; border: 1px solid #1e293b; }
                        .stat-label { font-size: 0.72rem; color: var(--text-muted); text-transform: uppercase; font-weight: 600; }
                        .stat-val { font-size: 1.15rem; font-weight: 700; margin-top: 0.3rem; }
                        
                        /* Itemized Deductions */
                        .itemized-box { background: #0f172a; border-radius: 8px; padding: 1rem; margin-bottom: 1rem; border: 1px solid #293548; }
                        .itemized-title { font-size: 0.75rem; font-weight: 800; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 0.5rem; }
                        .itemized-row { display: flex; justify-content: space-between; align-items: center; padding: 0.35rem 0; border-bottom: 1px solid #1e293b; font-size: 0.85rem; }
                        .itemized-row:last-child { border-bottom: none; }
                        .item-name { color: #e2e8f0; }
                        .item-cost { color: #f87171; font-weight: 700; font-family: monospace; }
                        .item-detail { font-size: 0.73rem; color: #64748b; margin-top: 0.1rem; }
                        .logistics-note { font-size: 0.75rem; color: #38bdf8; margin-top: 0.5rem; padding-top: 0.5rem; border-top: 1px dashed #334155; }
                        
                        /* Alert Diagnostics */
                        .leak-alert { background: #451a1a; border-left: 4px solid var(--critical); padding: 0.85rem; border-radius: 6px; font-size: 0.88rem; margin-top: 0.75rem; line-height: 1.45; }
                        .leak-alert.warning { background: #432818; border-color: var(--high); }
                        .leak-alert.low-warning { background: #3f3513; border-color: var(--warning); }
                        .leak-alert.ok { background: #064e3b; border-color: var(--ok); }
                        .alert-remedy { margin-top: 0.4rem; color: #e2e8f0; font-size: 0.83rem; background: rgba(0,0,0,0.2); padding: 0.4rem 0.6rem; border-radius: 4px; display: block; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <header>
                            <h1>🛒 E-Commerce Margin Leak Detector</h1>
                            <p class="subtitle">Object-Oriented unit-economics audit engine. Identifies hidden logistics surcharges, reverse reserves & fee leaks.</p>
                        </header>
                """);

            // Error notification
            if (errorMessage != null) {
                sb.append("<div class=\"error-banner\"><strong>⚠️ Validation Error:</strong> ")
                  .append(escapeHtml(errorMessage))
                  .append("</div>\n");
            }

            // Dynamic User Input Form
            sb.append("""
                        <!-- PRODUCT AUDIT INPUT FORM -->
                        <section class="form-card" id="audit-form">
                            <h2>🔍 Audit Product Unit Economics</h2>
                            <p class="form-desc">Enter your product SKU, physical packaging specs, and financial parameters to evaluate against OOP margin leak rules.</p>

                            <form method="POST" action="/#results">
                                <div class="form-grid">
                                    <div class="form-group">
                                        <label for="sku">SKU Code *</label>
                                        <input type="text" id="sku" name="sku" class="form-control" placeholder="e.g. SKU-TECH-901" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="title">Product Name *</label>
                                        <input type="text" id="title" name="title" class="form-control" placeholder="e.g. RGB Mechanical Keyboard" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="category">Category *</label>
                                        <select id="category" name="category" class="form-control" required>
                                            <option value="ELECTRONICS">Electronics</option>
                                            <option value="APPAREL">Apparel</option>
                                            <option value="FURNITURE">Furniture</option>
                                            <option value="HOME_AND_KITCHEN">Home & Kitchen</option>
                                            <option value="BEAUTY">Beauty</option>
                                            <option value="ACCESSORIES">Accessories</option>
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label for="sellingPrice">Selling Price ($) *</label>
                                        <input type="number" step="0.01" min="0.01" id="sellingPrice" name="sellingPrice" class="form-control" placeholder="79.99" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="costOfGoods">COGS / Unit Cost ($) *</label>
                                        <input type="number" step="0.01" min="0.00" id="costOfGoods" name="costOfGoods" class="form-control" placeholder="24.00" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="actualWeightKg">Dead Weight (kg) *</label>
                                        <input type="number" step="0.01" min="0.01" id="actualWeightKg" name="actualWeightKg" class="form-control" placeholder="1.25" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="lengthCm">Length (cm) *</label>
                                        <input type="number" step="0.1" min="0.1" id="lengthCm" name="lengthCm" class="form-control" placeholder="45.0" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="widthCm">Width (cm) *</label>
                                        <input type="number" step="0.1" min="0.1" id="widthCm" name="widthCm" class="form-control" placeholder="18.0" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="heightCm">Height (cm) *</label>
                                        <input type="number" step="0.1" min="0.1" id="heightCm" name="heightCm" class="form-control" placeholder="5.5" required>
                                    </div>
                                    <div class="form-group">
                                        <label for="returnRatePercent">Customer Return Rate (%) *</label>
                                        <input type="number" step="0.1" min="0.0" max="100.0" id="returnRatePercent" name="returnRatePercent" class="form-control" placeholder="8.5" required>
                                    </div>
                                </div>

                                <details class="fee-settings">
                                    <summary>⚙️ Advanced Marketplace & Logistics Fee Overrides (Optional)</summary>
                                    <div class="fee-grid">
                                        <div class="form-group">
                                            <label for="commissionPct">Referral Commission (%)</label>
                                            <input type="number" step="0.1" min="0.0" id="commissionPct" name="commissionPct" class="form-control" value="15.0">
                                        </div>
                                        <div class="form-group">
                                            <label for="minCommission">Min Referral Floor ($)</label>
                                            <input type="number" step="0.01" min="0.0" id="minCommission" name="minCommission" class="form-control" value="1.50">
                                        </div>
                                        <div class="form-group">
                                            <label for="shipBaseRate">Logistics Base Rate ($)</label>
                                            <input type="number" step="0.01" min="0.0" id="shipBaseRate" name="shipBaseRate" class="form-control" value="6.50">
                                        </div>
                                        <div class="form-group">
                                            <label for="shipPerKg">Logistics Extra ($/kg)</label>
                                            <input type="number" step="0.01" min="0.0" id="shipPerKg" name="shipPerKg" class="form-control" value="1.50">
                                        </div>
                                        <div class="form-group">
                                            <label for="returnReverseCost">Reverse Freight / Return ($)</label>
                                            <input type="number" step="0.01" min="0.0" id="returnReverseCost" name="returnReverseCost" class="form-control" value="10.00">
                                        </div>
                                        <div class="form-group">
                                            <label for="paymentFeePct">Payment Processing (%)</label>
                                            <input type="number" step="0.1" min="0.0" id="paymentFeePct" name="paymentFeePct" class="form-control" value="2.9">
                                        </div>
                                    </div>
                                </details>

                                <button type="submit" class="btn-audit">⚡ Run Unit Economics Audit</button>
                            </form>
                        </section>
                """);

            // Dynamic Results Section
            if (!dynamicAudits.isEmpty()) {
                sb.append("<div id=\"results\" class=\"section-title\"><span>⚡ Dynamic Live Audit Results</span> <span class=\"count-badge\">")
                  .append(dynamicAudits.size())
                  .append(" Evaluated</span></div>\n");

                for (AuditRecord record : dynamicAudits) {
                    renderDynamicProductCard(sb, record);
                }
            }

            // Reference OOP Scenarios Section (Preserved from baseline)
            sb.append("""
                        <div class="section-title"><span>📚 Reference OOP Benchmark Scenarios</span> <span class="ref-pill">Baseline Test Suite</span></div>

                        <!-- Reference Product 1 -->
                        <div class="card">
                            <div class="card-header">
                                <div class="title-area">
                                    <span class="ref-pill">Case Study</span>
                                    <strong>Solid Wood Study Desk</strong>
                                    <span class="sku">(SKU-101)</span>
                                </div>
                                <span class="badge badge-critical">CRITICAL LEAK</span>
                            </div>
                            <div class="grid">
                                <div class="stat"><div class="stat-label">Selling Price</div><div class="stat-val">$80.00</div></div>
                                <div class="stat"><div class="stat-label">Sourcing Cost</div><div class="stat-val">$45.00</div></div>
                                <div class="stat"><div class="stat-label">Logistics & Fees</div><div class="stat-val">$57.50</div></div>
                                <div class="stat"><div class="stat-label">Net Margin</div><div class="stat-val" style="color: #ef4444;">-$22.50 (-28.1%)</div></div>
                            </div>
                            <div class="leak-alert">⚠️ <strong>Critical Margin Leak:</strong> Heavy weight (18.0 kg) logistics surcharges combined with referral fees exceed gross profit. Sold at a net loss.</div>
                        </div>

                        <!-- Reference Product 2 -->
                        <div class="card">
                            <div class="card-header">
                                <div class="title-area">
                                    <span class="ref-pill">Case Study</span>
                                    <strong>Velvet Evening Gown</strong>
                                    <span class="sku">(SKU-202)</span>
                                </div>
                                <span class="badge badge-high">HIGH RISK</span>
                            </div>
                            <div class="grid">
                                <div class="stat"><div class="stat-label">Selling Price</div><div class="stat-val">$120.00</div></div>
                                <div class="stat"><div class="stat-label">Sourcing Cost</div><div class="stat-val">$40.00</div></div>
                                <div class="stat"><div class="stat-label">Logistics & Fees</div><div class="stat-val">$34.60</div></div>
                                <div class="stat"><div class="stat-label">Net Margin</div><div class="stat-val" style="color: #f97316;">+$45.40 (37.8%)</div></div>
                            </div>
                            <div class="leak-alert warning">⚠️ <strong>High Return Rate Erosion:</strong> Customer return rate (28%) requires reverse logistics provisions that silently drain unit profitability.</div>
                        </div>

                        <!-- Reference Product 3 -->
                        <div class="card">
                            <div class="card-header">
                                <div class="title-area">
                                    <span class="ref-pill">Case Study</span>
                                    <strong>Ergonomic Bluetooth Mouse</strong>
                                    <span class="sku">(SKU-303)</span>
                                </div>
                                <span class="badge badge-ok">HEALTHY</span>
                            </div>
                            <div class="grid">
                                <div class="stat"><div class="stat-label">Selling Price</div><div class="stat-val">$35.00</div></div>
                                <div class="stat"><div class="stat-label">Sourcing Cost</div><div class="stat-val">$10.00</div></div>
                                <div class="stat"><div class="stat-label">Logistics & Fees</div><div class="stat-val">$4.25</div></div>
                                <div class="stat"><div class="stat-label">Net Margin</div><div class="stat-val" style="color: #22c55e;">+$20.75 (59.3%)</div></div>
                            </div>
                            <div class="leak-alert ok">✅ <strong>Optimal Margin Profile:</strong> Passing all audit benchmarks. Low return rate, compact freight weight.</div>
                        </div>

                    </div>
                </body>
                </html>
                """);

            return sb.toString();
        }

        private void renderDynamicProductCard(StringBuilder sb, AuditRecord record) {
            Product p = record.product;
            double netProfit = p.getNetProfit();
            double netMarginPct = p.getProfitMarginPercent();
            double totalExpenses = p.calculateTotalExpenses();

            String badgeClass;
            String badgeText;
            switch (record.severity) {
                case CRITICAL -> {
                    badgeClass = "badge-critical";
                    badgeText = "CRITICAL LEAK";
                }
                case HIGH -> {
                    badgeClass = "badge-high";
                    badgeText = "HIGH RISK";
                }
                case WARNING -> {
                    badgeClass = "badge-warning";
                    badgeText = "WARNING RISK";
                }
                default -> {
                    badgeClass = "badge-ok";
                    badgeText = "HEALTHY MARGIN";
                }
            }

            String netProfitColor = netProfit < 0 ? "#ef4444" : (netMarginPct < 15.0 ? "#f97316" : "#22c55e");

            sb.append("<div class=\"card dynamic-result\">\n");
            sb.append("  <div class=\"card-header\">\n");
            sb.append("    <div class=\"title-area\">\n");
            sb.append("      <span class=\"live-pill\">Live Calculated</span>\n");
            sb.append("      <strong>").append(escapeHtml(p.getTitle())).append("</strong>\n");
            sb.append("      <span class=\"sku\">(").append(escapeHtml(p.getSku())).append(")</span>\n");
            sb.append("      <span class=\"ref-pill\">").append(p.getCategory()).append("</span>\n");
            sb.append("    </div>\n");
            sb.append("    <span class=\"badge ").append(badgeClass).append("\">").append(badgeText).append("</span>\n");
            sb.append("  </div>\n");

            // Key financial metrics
            sb.append("  <div class=\"grid\">\n");
            sb.append("    <div class=\"stat\"><div class=\"stat-label\">Selling Price</div><div class=\"stat-val\">$")
              .append(String.format("%.2f", p.getSellingPrice())).append("</div></div>\n");
            sb.append("    <div class=\"stat\"><div class=\"stat-label\">COGS (Unit Cost)</div><div class=\"stat-val\">$")
              .append(String.format("%.2f", p.getCostOfGoods())).append("</div></div>\n");
            sb.append("    <div class=\"stat\"><div class=\"stat-label\">Total Deductions</div><div class=\"stat-val\">$")
              .append(String.format("%.2f", totalExpenses)).append("</div></div>\n");
            sb.append("    <div class=\"stat\"><div class=\"stat-label\">Net Profit / Margin</div><div class=\"stat-val\" style=\"color: ")
              .append(netProfitColor).append(";\">")
              .append(String.format("%s$%.2f (%.1f%%)", netProfit >= 0 ? "+" : "-", Math.abs(netProfit), netMarginPct))
              .append("</div></div>\n");
            sb.append("  </div>\n");

            // Itemized Operational Deductions (Polymorphic results)
            sb.append("  <div class=\"itemized-box\">\n");
            sb.append("    <div class=\"itemized-title\">Itemized Marketplace Deductions (OOP Dynamic Dispatch)</div>\n");
            for (Expense exp : p.getExpenses()) {
                double cost = exp.calculate(p);
                sb.append("    <div class=\"itemized-row\">\n");
                sb.append("      <div>\n");
                sb.append("        <div class=\"item-name\">").append(escapeHtml(exp.getName())).append("</div>\n");
                sb.append("        <div class=\"item-detail\">").append(escapeHtml(exp.getCalculationDetails(p))).append("</div>\n");
                sb.append("      </div>\n");
                sb.append("      <div class=\"item-cost\">-$").append(String.format("%.2f", cost)).append("</div>\n");
                sb.append("    </div>\n");
            }

            double actualWt = p.getActualWeightKg();
            double volWt = p.getDimensions().getVolumetricWeightKg(5000.0);
            double billableWt = p.getBillableWeightKg();
            sb.append("    <div class=\"logistics-note\">📦 Freight Logistics Audit: Actual Weight = ")
              .append(String.format("%.2f kg", actualWt))
              .append(" | Volumetric Weight = ")
              .append(String.format("%.2f kg", volWt))
              .append(" (").append(p.getDimensions()).append(") | Billable Freight Weight = ")
              .append(String.format("%.2f kg", billableWt))
              .append("</div>\n");
            sb.append("  </div>\n");

            // Rule-based leak alerts
            if (record.alerts.isEmpty()) {
                sb.append("  <div class=\"leak-alert ok\">✅ <strong>Optimal Margin Profile:</strong> Zero profitability leaks detected. Net margin of ")
                  .append(String.format("%.1f%%", netMarginPct))
                  .append(" satisfies all safety benchmarks and unit economics criteria.</div>\n");
            } else {
                for (LeakAlert alert : record.alerts) {
                    String alertClass = switch (alert.getSeverity()) {
                        case CRITICAL -> "";
                        case HIGH -> "warning";
                        default -> "low-warning";
                    };

                    sb.append("  <div class=\"leak-alert ").append(alertClass).append("\">\n");
                    sb.append("    ⚠️ <strong>").append(escapeHtml(alert.getRuleName()))
                      .append(" [").append(alert.getSeverity().name()).append("]:</strong> ")
                      .append(escapeHtml(alert.getDiagnosis())).append("\n");
                    sb.append("    <span class=\"alert-remedy\">💡 <strong>Suggested Remedy:</strong> ")
                      .append(escapeHtml(alert.getSuggestedAction())).append("</span>\n");
                    sb.append("  </div>\n");
                }
            }

            sb.append("</div>\n");
        }
    }
}
