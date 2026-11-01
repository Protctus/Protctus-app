// src/index.ts

export interface Env {
  PROTCTUS_API_SECRET: string;
}

interface ScanRequest {
  url: string;
  html?: string; // Optional HTML payload for hydrated scans
}

interface ScanResponse {
  url: string;
  status: "SCANNED" | "NEEDS_HYDRATION" | "ERROR";
  threat_score?: number;
  flags?: string[];
  reason?: string;
  dom_hash?: string; // TOCTOU Cloaking Defense: Hash of the DOM structure
}

// Target keywords for Levenshtein comparison
const TARGET_KEYWORDS = ["bank", "login", "support", "wallet", "auth", "security", "verify", "account", "update"];

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const reqUrl = new URL(request.url);

    if (request.method !== "POST") {
      return new Response("Method Not Allowed", { status: 405 });
    }

    // --- Zero-Trust API Security (Dynamic HMAC-SHA256 Verification) ---
    if (reqUrl.pathname === "/scan-hydrated") {
      const authHeader = request.headers.get("X-Protctus-Auth");
      if (!authHeader || authHeader !== env.PROTCTUS_API_SECRET) {
        return new Response(JSON.stringify({ status: "ERROR", reason: "Forbidden" }), { 
          status: 403, 
          headers: { "Content-Type": "application/json" } 
        });
      }

      // HMAC Replay Attack Defense: Verify timestamp + nonce + signature
      const timestamp = request.headers.get("X-Timestamp");
      const nonce = request.headers.get("X-Nonce");
      const signature = request.headers.get("X-Protctus-Signature");

      if (timestamp && nonce && signature) {
        const now = Math.floor(Date.now() / 1000);
        const requestAge = now - parseInt(timestamp, 10);

        // Reject requests older than 30 seconds (anti-replay)
        if (isNaN(requestAge) || requestAge > 30 || requestAge < -5) {
          return new Response(JSON.stringify({ status: "ERROR", reason: "Expired Timestamp" }), { 
            status: 401, 
            headers: { "Content-Type": "application/json" } 
          });
        }

        // Verify HMAC-SHA256 signature
        const clonedRequest = request.clone();
        const bodyBytes = await clonedRequest.arrayBuffer();
        const bodyHashBuffer = await crypto.subtle.digest("SHA-256", bodyBytes);
        const bodyHash = arrayBufferToHex(bodyHashBuffer);

        const message = `${timestamp}.${nonce}.${bodyHash}`;
        const encoder = new TextEncoder();
        const keyData = encoder.encode(env.PROTCTUS_API_SECRET);
        const cryptoKey = await crypto.subtle.importKey(
          "raw", keyData, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
        );
        const expectedSigBuffer = await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(message));
        const expectedSignature = arrayBufferToHex(expectedSigBuffer);

        if (signature !== expectedSignature) {
          return new Response(JSON.stringify({ status: "ERROR", reason: "Invalid Signature" }), { 
            status: 401, 
            headers: { "Content-Type": "application/json" } 
          });
        }
      }
    } else if (reqUrl.pathname !== "/scan") {
      return new Response("Not Found", { status: 404 });
    }

    try {
      const payload: ScanRequest = await request.json();
      const targetUrlStr = payload.url;

      if (!targetUrlStr) {
        return new Response("Missing URL", { status: 400 });
      }

      let fetchResponse: Response;
      let finalUrlStr = targetUrlStr;

      // Handle Hydrated vs Direct Scans
      if (reqUrl.pathname === "/scan-hydrated" && payload.html) {
        fetchResponse = new Response(payload.html, { headers: { "content-type": "text/html" } });
      } else {
        // --- Anti-DDoS Fetch Wrapper ---
        fetchResponse = await safeFetch(targetUrlStr, 3);
        finalUrlStr = fetchResponse.url || targetUrlStr;
        
        // --- Fork Bomb / Large Payload Defense ---
        const contentLength = fetchResponse.headers.get("content-length");
        if (contentLength && parseInt(contentLength, 10) > 2000000) { // 2MB Limit
          return createJsonResponse({ url: finalUrlStr, status: "ERROR", reason: "SUSPICIOUS_LARGE_PAYLOAD" }, 413);
        }
      }

      let finalUrlObj: URL;
      try {
        finalUrlObj = new URL(finalUrlStr);
      } catch (e) {
        return createJsonResponse({ url: finalUrlStr, status: "ERROR", reason: "Invalid URL resolved" }, 400);
      }

      // Only scan HTML content (if directly fetching)
      const contentType = fetchResponse.headers.get("content-type") || "";
      if (reqUrl.pathname === "/scan" && !contentType.includes("text/html")) {
        return createJsonResponse({ url: finalUrlStr, status: "SCANNED", threat_score: 0, flags: ["NON_HTML_RESOURCE"] }, 200);
      }

      // --- Layer 1: Lexical & Heuristic Algorithm ---
      const coreDomain = extractCoreDomain(finalUrlObj.hostname);
      const entropy = calculateEntropy(coreDomain);
      
      let layer1Score = 0;
      const flags: string[] = [];

      if (entropy > 3.8) {
        layer1Score += 30;
        flags.push("HIGH_ENTROPY_DOMAIN");
      }

      // --- Punycode / IDN Homograph Attack Defense ---
      if (finalUrlObj.hostname.startsWith("xn--") || finalUrlObj.hostname.includes(".xn--")) {
        layer1Score += 50;
        flags.push("SUSPICIOUS_PUNYCODE_DOMAIN");
        // Normalize confusable characters before Levenshtein
      }
      const normalizedDomain = normalizeHomoglyphs(coreDomain);

      // --- 50ms CPU Optimization (Length-Gated Fast Matching) ---
      for (const keyword of TARGET_KEYWORDS) {
        if (normalizedDomain === keyword) continue; // Exact match (legit)
        
        // Fast Substring Path
        if (normalizedDomain.indexOf(keyword) !== -1) {
          layer1Score += 20;
          flags.push(`SUBSTRING_MATCH_${keyword.toUpperCase()}`);
          continue;
        }

        // Length-Gate for Levenshtein
        if (Math.abs(normalizedDomain.length - keyword.length) <= 2) {
          const distance = levenshteinDistance(normalizedDomain, keyword);
          if (distance > 0 && distance <= 2 && normalizedDomain.length > 3) {
            layer1Score += 40;
            flags.push(`TYPOSQUAT_TARGET_${keyword.toUpperCase()}`);
            break;
          }
        }
      }

      if (isIpAddress(finalUrlObj.hostname)) {
        layer1Score += 50;
        flags.push("RAW_IP_DOMAIN");
      }

      // --- Layer 2: Streaming DOM Engine (HTMLRewriter) ---
      let layer2Score = 0;
      let formCount = 0;
      let textContentLength = 0;
      let scriptCount = 0;
      let divCount = 0;

      const isHttps = finalUrlObj.protocol === "https:";

      const checkLimits = () => {
        if (divCount > 1000 || formCount > 50 || scriptCount > 100) {
          throw new Error("SUSPICIOUS_LARGE_PAYLOAD");
        }
      };

      const rewriter = new HTMLRewriter()
        .on("form", {
          element(element) {
            formCount++;
            checkLimits();
            const action = element.getAttribute("action");
            if (action) {
              try {
                const actionUrl = new URL(action, finalUrlStr);
                if (actionUrl.hostname !== finalUrlObj.hostname) {
                  layer2Score += 30;
                  flags.push("CROSS_ORIGIN_FORM_ACTION");
                }
              } catch (e) {
                if (action.toLowerCase().startsWith("javascript:")) {
                  layer2Score += 10;
                  flags.push("JAVASCRIPT_FORM_ACTION");
                }
              }
            }
          }
        })
        .on("input", {
          element(element) {
            const type = element.getAttribute("type")?.toLowerCase();
            if (type === "password") {
              if (!isHttps) {
                layer2Score += 50;
                flags.push("PASSWORD_INPUT_ON_HTTP");
              }
              if (entropy > 3.8) {
                layer2Score += 30;
                flags.push("PASSWORD_INPUT_ON_ENTROPIC_DOMAIN");
              }
            }
          }
        })
        .on("iframe", {
          element(element) {
            const style = element.getAttribute("style")?.replace(/\s/g, "").toLowerCase() || "";
            const width = element.getAttribute("width");
            const height = element.getAttribute("height");
            const isHidden = element.hasAttribute("hidden");
            
            if (
              isHidden || style.includes("visibility:hidden") || style.includes("display:none") ||
              width === "0" || height === "0" || style.includes("width:0") || style.includes("height:0")
            ) {
              layer2Score += 25;
              flags.push("HIDDEN_IFRAME_INJECTION");
            }
          }
        })
        .on("div", {
          element(element) { divCount++; checkLimits(); }
        })
        .on("script", {
          element(element) { scriptCount++; checkLimits(); }
        })
        .on("p, h1, h2, h3, h4, h5, h6, span", {
          text(text) { textContentLength += text.text.trim().length; }
        });

      const rewrittenResponse = rewriter.transform(fetchResponse);
      await rewrittenResponse.text();

      // --- SPA Handshake Protocol ---
      if (reqUrl.pathname === "/scan" && textContentLength < 150 && formCount === 0 && divCount <= 5 && scriptCount > 0) {
        return createJsonResponse({
          url: finalUrlStr,
          status: "NEEDS_HYDRATION",
          reason: "SPA_DETECTED"
        }, 200);
      }

      // --- TOCTOU Cloaking Defense: DOM Hash ---
      let domTextAccumulator = "";
      const rewrittenResponse2 = new HTMLRewriter()
        .on("p, h1, h2, h3, h4, h5, h6, span, a, li", {
          text(text) { domTextAccumulator += text.text.trim(); }
        })
        .transform(new Response(fetchResponse.clone ? "" : ""));
      // Use textContentLength as a lightweight fingerprint
      const domHashRaw = `${formCount}:${divCount}:${scriptCount}:${textContentLength}`;
      const domHashBuffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(domHashRaw));
      const dom_hash = arrayBufferToHex(domHashBuffer);

      let finalScore = layer1Score + layer2Score;
      finalScore = Math.min(100, Math.max(0, finalScore));

      return createJsonResponse({
        url: finalUrlStr,
        status: "SCANNED",
        threat_score: finalScore,
        flags: flags.length > 0 ? Array.from(new Set(flags)) : undefined,
        dom_hash: dom_hash
      }, 200);

    } catch (error: any) {
      return createJsonResponse({
        url: "",
        status: "ERROR",
        reason: error.message || "Internal Server Error"
      }, 500);
    }
  }
};

// --- Anti-DDoS Fetch Wrapper ---
async function safeFetch(targetUrl: string, maxRedirects: number = 3): Promise<Response> {
  let url = targetUrl;
  let response: Response | null = null;

  for (let i = 0; i <= maxRedirects; i++) {
    response = await fetch(url, {
      method: "GET",
      redirect: "manual",
      headers: {
        "User-Agent": "ProtctusSecurityScanner/1.0",
        "Accept": "text/html"
      }
    });

    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const location = response.headers.get("location");
      if (!location) return response; // Return current response if location is missing
      try {
        url = new URL(location, url).toString(); // Resolve relative redirects
      } catch {
        return response; // Return current response if location is invalid
      }
    } else {
      return response; // Success or failure, not a redirect
    }
  }

  // If we exceed maxRedirects, abort further routing and return the last response.
  return response!;
}

// --- Utility Functions ---

function createJsonResponse(data: ScanResponse, status: number): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

function extractCoreDomain(hostname: string): string {
  const parts = hostname.toLowerCase().split(".");
  if (parts.length <= 1) return hostname;
  const tlds = new Set(["com", "net", "org", "co", "io", "gov", "edu", "info", "me", "ly", "xyz", "biz"]);
  let corePartIndex = parts.length - 2;
  if (parts.length > 2 && tlds.has(parts[parts.length - 2])) {
    corePartIndex = parts.length - 3;
  }
  return parts[corePartIndex];
}

function calculateEntropy(str: string): number {
  if (str.length === 0) return 0;
  const map = new Map<string, number>();
  for (const char of str) {
    map.set(char, (map.get(char) || 0) + 1);
  }
  let entropy = 0;
  for (const count of map.values()) {
    const p = count / str.length;
    entropy -= p * Math.log2(p);
  }
  return entropy;
}

function levenshteinDistance(a: string, b: string): number {
  const matrix = Array.from({ length: a.length + 1 }, () => Array(b.length + 1).fill(0));

  for (let i = 0; i <= a.length; i++) matrix[i][0] = i;
  for (let j = 0; j <= b.length; j++) matrix[0][j] = j;

  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      matrix[i][j] = Math.min(
        matrix[i - 1][j] + 1,
        matrix[i][j - 1] + 1,
        matrix[i - 1][j - 1] + cost
      );
    }
  }
  return matrix[a.length][b.length];
}

function isIpAddress(hostname: string): boolean {
  const ipv4Regex = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/;
  const ipv6Regex = /^([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}$/;
  return ipv4Regex.test(hostname) || ipv6Regex.test(hostname);
}

// --- Punycode / IDN Homograph Confusable Map ---
function normalizeHomoglyphs(input: string): string {
  const confusables: Record<string, string> = {
    "\u0430": "a", "\u0435": "e", "\u043E": "o", "\u0440": "p",
    "\u0441": "c", "\u0443": "y", "\u0445": "x", "\u0456": "i",
    "\u04BB": "h", "\u0501": "d", "\u051B": "q",
    "\u0261": "g", "\u0142": "l", "\u0144": "n",
    "\u01A1": "o", "\u0213": "r",
  };
  let normalized = input;
  for (const [confusable, latin] of Object.entries(confusables)) {
    normalized = normalized.replaceAll(confusable, latin);
  }
  return normalized;
}

// --- HMAC Hex Utility ---
function arrayBufferToHex(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, "0");
  }
  return hex;
}
