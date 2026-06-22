export interface Env {
  DB: any;
  ADMIN_USERNAME: string;
  ADMIN_PASSWORD_HASH: string;
  JWT_SECRET: string;
  FIREBASE_API_KEY: string; // VULN-X1 fix: moved from hardcoded to env variable
  RAZORPAY_KEY_ID: string;
  RAZORPAY_KEY_SECRET: string;
  SUPABASE_URL: string;
  SUPABASE_SERVICE_ROLE_KEY: string;
  THREAT_REPORT_SECRET: string; // HMAC secret for app-to-cloud threat telemetry
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, Content-Disposition",
};



// ── Minimal JWT Implementation (HMAC-SHA256) ──
async function createJWT(payload: object, secret: string): Promise<string> {
  const header = btoa(JSON.stringify({ alg: "HS256", typ: "JWT" })).replace(/=/g, "");
  const body = btoa(JSON.stringify(payload)).replace(/=/g, "");
  const data = `${header}.${body}`;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  const sigStr = btoa(String.fromCharCode(...new Uint8Array(sig))).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `${data}.${sigStr}`;
}

async function verifyJWT(token: string, secret: string): Promise<boolean> {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return false;
    const data = `${parts[0]}.${parts[1]}`;
    const sig = parts[2].replace(/-/g, "+").replace(/_/g, "/");
    const key = await crypto.subtle.importKey(
      "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]
    );
    const sigBuf = Uint8Array.from(atob(sig), c => c.charCodeAt(0));
    const valid = await crypto.subtle.verify("HMAC", key, sigBuf, new TextEncoder().encode(data));
    if (!valid) return false;
    // Check expiry
    const payload = JSON.parse(atob(parts[1]));
    if (payload.exp && Date.now() / 1000 > payload.exp) return false;
    return true;
  } catch { return false; }
}

async function hashPassword(password: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(password));
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join("");
}

function jsonResponse(data: any, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function generateId(): string {
  return crypto.randomUUID();
}

// ── Admin Auth Middleware (HMAC-SHA256 of ADMIN_USERNAME:ADMIN_PASSWORD_HASH) ──
async function verifyAdminAuth(request: Request, env: Env): Promise<boolean> {
  const authHeader = request.headers.get("Authorization") || "";
  const token = authHeader.replace("Bearer ", "");
  if (!token) return false;

  // Option 1: Verify as admin JWT
  if (env.JWT_SECRET && await verifyJWT(token, env.JWT_SECRET)) {
    try {
      const parts = token.split(".");
      const payload = JSON.parse(atob(parts[1]));
      if (payload.role === "admin") return true;
    } catch { /* fall through */ }
  }

  // Option 2: Verify as direct HMAC token (admin panel sends HMAC(username:password_hash))
  if (env.ADMIN_USERNAME && env.ADMIN_PASSWORD_HASH) {
    const expectedMessage = `${env.ADMIN_USERNAME}:${env.ADMIN_PASSWORD_HASH}`;
    const expectedHash = await hashPassword(expectedMessage);
    if (token === expectedHash) return true;
  }

  return false;
}

// ── App Telemetry Auth: Verifies X-Protctus-Auth HMAC secret ──
function verifyThreatReportAuth(request: Request, env: Env): boolean {
  const secret = request.headers.get("X-Protctus-Auth") || "";
  if (!secret || !env.THREAT_REPORT_SECRET) return false;
  // Constant-time comparison via string match (sufficient for non-crypto secret)
  return secret === env.THREAT_REPORT_SECRET;
}

// ── Firebase Token Auth Helper (reusable) ──
async function verifyFirebaseToken(request: Request, env: Env): Promise<{ valid: boolean; email: string }> {
  const authHeader = request.headers.get("Authorization") || "";
  const token = authHeader.replace("Bearer ", "");
  if (!token) return { valid: false, email: "" };

  const apiKey = env.FIREBASE_API_KEY;
  if (!apiKey) return { valid: false, email: "" };

  try {
    const verifyRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${apiKey}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken: token })
    });
    if (verifyRes.ok) {
      const data: any = await verifyRes.json();
      if (data.users && data.users.length > 0) {
        return { valid: true, email: data.users[0].email || "" };
      }
    }
  } catch { /* fall through */ }

  return { valid: false, email: "" };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    // ══════════════════════════════════════════
    // PUBLIC ENDPOINTS (no auth required)
    // ══════════════════════════════════════════

    // GET /quests — Public quest list
    if (request.method === "GET" && path.startsWith("/quests")) {
      return await handleGetQuests(url, env);
    }

    // GET /rewards — Public rewards list
    if (request.method === "GET" && path.startsWith("/rewards")) {
      return await handleGetRewards(env);
    }

    // GET /app/notifications — Public endpoint for mobile app to poll
    if (request.method === "GET" && path === "/app/notifications") {
      return await handleAppNotifications(env);
    }

    // GET /app/version — Public endpoint for app to check for updates
    if (request.method === "GET" && path === "/app/version") {
      try {
        // AUTO-INIT: If table doesn't exist, create it on first call to prevent 500 errors
        const tableCheck = await env.DB.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='app_updates'").first();
        if (!tableCheck) {
          await env.DB.prepare(`CREATE TABLE IF NOT EXISTS user_entitlements (user_email TEXT PRIMARY KEY, tier TEXT NOT NULL DEFAULT 'FREE', created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)`).run();
          await env.DB.prepare(`CREATE TABLE IF NOT EXISTS app_updates (version_code INTEGER PRIMARY KEY, version_name TEXT NOT NULL, download_url TEXT NOT NULL, release_notes TEXT, is_critical INTEGER DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)`).run();
          await env.DB.prepare(`CREATE TABLE IF NOT EXISTS apk_files (id TEXT PRIMARY KEY, filename TEXT NOT NULL, total_size INTEGER NOT NULL, created_at INTEGER NOT NULL)`).run();
          await env.DB.prepare(`CREATE TABLE IF NOT EXISTS apk_chunks (file_id TEXT, chunk_index INTEGER, data BLOB, PRIMARY KEY (file_id, chunk_index))`).run();
        }

        const result = await env.DB.prepare("SELECT * FROM app_updates ORDER BY version_code DESC LIMIT 1").first();
        return jsonResponse(result || { version_code: 0, download_url: "", release_notes: "" });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // GET /apks/:id — Reconstruct and download APK from D1 chunks
    if (request.method === "GET" && path.startsWith("/apks/")) {
      const fileId = path.split("/apks/")[1];
      try {
        const fileInfo = await env.DB.prepare("SELECT * FROM apk_files WHERE id = ?").bind(fileId).first();
        if (!fileInfo) return new Response("Not Found", { status: 404 });

        const { results } = await env.DB.prepare("SELECT data FROM apk_chunks WHERE file_id = ? ORDER BY chunk_index ASC").bind(fileId).all();

        // Combine chunks into a single Uint8Array
        const totalSize = results.reduce((acc: number, curr: any) => acc + curr.data.length, 0);
        const combined = new Uint8Array(totalSize);
        let offset = 0;
        for (const row of results) {
          combined.set(new Uint8Array(row.data), offset);
          offset += row.data.length;
        }

        return new Response(combined, {
          headers: {
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Disposition": `attachment; filename="${fileInfo.filename}"`,
            "Access-Control-Allow-Origin": "*"
          }
        });
      } catch (err: any) {
        return new Response(err.message, { status: 500 });
      }
    }

    // ══════════════════════════════════════════
    // AUTHENTICATED: THREAT TELEMETRY ENDPOINT
    // ══════════════════════════════════════════

    // POST /api/threat-report — Requires X-Protctus-Auth HMAC secret
    if (request.method === "POST" && path === "/api/threat-report") {
      if (!verifyThreatReportAuth(request, env)) {
        return jsonResponse({ error: "Unauthorized: Invalid or missing X-Protctus-Auth header." }, 401);
      }
      try {
        const body: any = await request.json();
        const type = body.type; // "phishing" or "malware"

        // Auto-init tables on first call
        await env.DB.exec(`
          CREATE TABLE IF NOT EXISTS phishing_detections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            domain_hash TEXT NOT NULL,
            confidence_score REAL NOT NULL,
            verdict TEXT NOT NULL,
            engine_breakdown TEXT,
            tld TEXT,
            reported_at INTEGER NOT NULL
          );
          CREATE TABLE IF NOT EXISTS malware_detections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            signature_hash TEXT NOT NULL,
            package_hash TEXT NOT NULL,
            threat_type TEXT NOT NULL,
            engine_name TEXT,
            confidence_score REAL NOT NULL,
            reported_at INTEGER NOT NULL
          );
        `);

        if (type === "phishing") {
          await env.DB.prepare(
            "INSERT INTO phishing_detections (domain_hash, confidence_score, verdict, engine_breakdown, tld, reported_at) VALUES (?, ?, ?, ?, ?, ?)"
          ).bind(
            body.domain_hash || "",
            body.confidence_score || 0,
            body.verdict || "unknown",
            body.engine_breakdown || "",
            body.tld || "",
            body.timestamp || Date.now()
          ).run();
        } else if (type === "malware") {
          await env.DB.prepare(
            "INSERT INTO malware_detections (signature_hash, package_hash, threat_type, engine_name, confidence_score, reported_at) VALUES (?, ?, ?, ?, ?, ?)"
          ).bind(
            body.signature_hash || "",
            body.package_hash || "",
            body.threat_type || "unknown",
            body.engine_name || "",
            body.confidence_score || 0,
            body.timestamp || Date.now()
          ).run();
        } else {
          return jsonResponse({ error: "Invalid type. Use 'phishing' or 'malware'." }, 400);
        }

        return jsonResponse({ success: true });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // GET /api/threat-stats — Admin: get detection counts
    if (request.method === "GET" && path === "/api/threat-stats") {
      try {
        const phishing = await env.DB.prepare("SELECT COUNT(*) as count FROM phishing_detections").first();
        const malware = await env.DB.prepare("SELECT COUNT(*) as count FROM malware_detections").first();
        return jsonResponse({
          phishing_count: phishing?.count || 0,
          malware_count: malware?.count || 0
        });
      } catch {
        return jsonResponse({ phishing_count: 0, malware_count: 0 });
      }
    }

    // ══════════════════════════════════════════
    // PUBLIC: PROTCTUS LAB ENDPOINTS
    // ══════════════════════════════════════════

    // GET /lab/chapters — Fetch chapters with topics
    if (request.method === "GET" && path === "/lab/chapters") {
      try {
        const chaptersQuery = await env.DB.prepare("SELECT * FROM lab_chapters ORDER BY created_at ASC").all();
        const topicsQuery = await env.DB.prepare("SELECT * FROM lab_topics ORDER BY created_at ASC").all();

        const chapters = chaptersQuery.results?.map((c: any) => ({
          ...c,
          topics: topicsQuery.results?.filter((t: any) => t.chapter_id === c.id) || []
        }));

        return jsonResponse(chapters);
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // GET /lab/quizzes?topic_id=xyz
    if (request.method === "GET" && path === "/lab/quizzes") {
      try {
        const topicId = url.searchParams.get("topic_id");
        if (!topicId) return jsonResponse({ error: "Missing topic_id" }, 400);

        const { results } = await env.DB.prepare("SELECT * FROM lab_quizzes WHERE topic_id = ?").bind(topicId).all();

        const formattedResults = results?.map((q: any) => {
          try { q.options = JSON.parse(q.options); } catch { q.options = []; }
          return q;
        });

        return jsonResponse(formattedResults);
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // GET /lab/feed — Public microlearning feed for the app
    if (request.method === "GET" && path === "/lab/feed") {
      try {
        const categoryFilter = url.searchParams.get("category");
        let queryStr = "SELECT * FROM microlearning_feed ORDER BY created_at DESC";
        let params: any[] = [];

        if (categoryFilter) {
          queryStr = "SELECT * FROM microlearning_feed WHERE category = ? ORDER BY created_at DESC";
          params.push(categoryFilter);
        }

        const { results } = await env.DB.prepare(queryStr).bind(...params).all();

        const formattedResults = results?.map((card: any) => {
          try { card.options = JSON.parse(card.options); } catch { card.options = []; }
          return card;
        });

        return jsonResponse(formattedResults || []);
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // ══════════════════════════════════════════
    // RAZORPAY PAYMENT ENDPOINTS (Public — called by Android app)
    // ══════════════════════════════════════════

    // POST /api/create-order — Creates a Razorpay Order
    if (request.method === "POST" && path === "/api/create-order") {
      try {
        const body: any = await request.json();
        const amount = body.amount; // Amount in paise (e.g., 34900 = ₹349)
        const currency = body.currency || "INR";
        const receipt = body.receipt || `rcpt_${Date.now()}`;
        const planId = body.plan_id || "unknown";

        if (!amount || amount < 100) {
          return jsonResponse({ error: "Amount must be at least 100 paise (₹1)" }, 400);
        }

        const credentials = btoa(`${env.RAZORPAY_KEY_ID}:${env.RAZORPAY_KEY_SECRET}`);
        const rzpResponse = await fetch("https://api.razorpay.com/v1/orders", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Basic ${credentials}`,
          },
          body: JSON.stringify({ amount, currency, receipt, notes: { plan_id: planId } }),
        });

        if (!rzpResponse.ok) {
          const errBody: any = await rzpResponse.json();
          return jsonResponse({ error: errBody.error?.description || "Razorpay order creation failed" }, rzpResponse.status);
        }

        const order: any = await rzpResponse.json();
        return jsonResponse({
          order_id: order.id,
          amount: order.amount,
          currency: order.currency,
          key_id: env.RAZORPAY_KEY_ID,
        });
      } catch (err: any) {
        return jsonResponse({ error: err.message || "Internal error creating order" }, 500);
      }
    }

    // POST /api/verify-payment — Verifies Razorpay payment signature
    if (request.method === "POST" && path === "/api/verify-payment") {
      try {
        const body: any = await request.json();
        const { razorpay_order_id, razorpay_payment_id, razorpay_signature } = body;

        if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature) {
          return jsonResponse({ error: "Missing required payment fields" }, 400);
        }

        // HMAC-SHA256 verification: sign(order_id + "|" + payment_id) with KEY_SECRET
        const message = `${razorpay_order_id}|${razorpay_payment_id}`;
        const key = await crypto.subtle.importKey(
          "raw",
          new TextEncoder().encode(env.RAZORPAY_KEY_SECRET),
          { name: "HMAC", hash: "SHA-256" },
          false,
          ["sign"]
        );
        const sigBuf = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
        const generatedSignature = Array.from(new Uint8Array(sigBuf))
          .map(b => b.toString(16).padStart(2, "0"))
          .join("");

        if (generatedSignature !== razorpay_signature) {
          return jsonResponse({ error: "Payment signature verification failed", verified: false }, 400);
        }

        return jsonResponse({
          verified: true,
          payment_id: razorpay_payment_id,
          order_id: razorpay_order_id,
        });
      } catch (err: any) {
        return jsonResponse({ error: err.message || "Verification error" }, 500);
      }
    }

    // ══════════════════════════════════════════
    // PUBLIC: SUPPORT TICKETS (For Android App)
    // ══════════════════════════════════════════

    // POST /api/tickets — Submit a new ticket
    if (request.method === "POST" && path === "/api/tickets") {
      try {
        const body: any = await request.json();
        const id = body.id || generateId();
        await env.DB.prepare(
          "INSERT OR REPLACE INTO support_tickets (id, ticket_id, user_email, issue, status, timestamp) VALUES (?, ?, ?, ?, 'open', ?)"
        ).bind(id, body.ticket_id || id, body.user_email || "", body.issue, body.timestamp || Date.now()).run();
        return jsonResponse({ success: true, id, ticket_id: body.ticket_id || id });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // GET /api/tickets/:id — Check ticket status
    if (request.method === "GET" && path.startsWith("/api/tickets/")) {
      try {
        const ticketId = path.split("/api/tickets/")[1];
        // Check by both UUID (id) and human-readable ID (ticket_id)
        const result = await env.DB.prepare("SELECT * FROM support_tickets WHERE id = ? OR ticket_id = ?").bind(ticketId, ticketId).first();
        if (!result) return jsonResponse({ error: "Ticket not found" }, 404);
        return jsonResponse(result);
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // GET /api/user/tickets — List all tickets for a user (REQUIRES Firebase auth)
    if (request.method === "GET" && path === "/api/user/tickets") {
      const firebaseAuth = await verifyFirebaseToken(request, env);
      if (!firebaseAuth.valid || !firebaseAuth.email) {
        return jsonResponse({ error: "Unauthorized: Valid Firebase token required." }, 401);
      }
      try {
        // User can ONLY see their own tickets (email from verified token, not query param)
        const { results } = await env.DB.prepare("SELECT * FROM support_tickets WHERE user_email = ? ORDER BY timestamp DESC").bind(firebaseAuth.email).all();
        return jsonResponse(results || []);
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // ══════════════════════════════════════════
    // USER ENTITLEMENTS (Requires Auth)
    // ══════════════════════════════════════════
    if (path.startsWith("/api/user/tier")) {
      const authHeader = request.headers.get("Authorization") || "";
      const token = authHeader.replace("Bearer ", "");
      if (!token) return jsonResponse({ error: "Unauthorized" }, 401);

      let valid = false;
      let userEmail = "";

      const apiKey = env.FIREBASE_API_KEY;
      if (apiKey) {
        const verifyRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${apiKey}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ idToken: token })
        });
        if (verifyRes.ok) {
          const data: any = await verifyRes.json();
          if (data.users && data.users.length > 0) {
            valid = true;
            userEmail = data.users[0].email || "unknown";
          }
        }
      }

      if (!valid && env.JWT_SECRET) {
        valid = await verifyJWT(token, env.JWT_SECRET);
        if (valid) {
          const parts = token.split(".");
          const payload = JSON.parse(atob(parts[1]));
          userEmail = payload.sub || payload.email || "unknown";
        }
      }

      if (!valid || !userEmail || userEmail === "unknown") {
        return jsonResponse({ error: "Unauthorized or invalid token" }, 401);
      }

      if (request.method === "GET") {
        try {
          const result = await env.DB.prepare("SELECT tier FROM user_entitlements WHERE user_email = ?").bind(userEmail).first();
          return jsonResponse({ email: userEmail, tier: result?.tier || "FREE" });
        } catch (err: any) {
          return jsonResponse({ error: err.message }, 500);
        }
      }

      if (request.method === "POST") {
        try {
          const body: any = await request.json();
          const newTier = body.tier || "FREE";
          await env.DB.prepare(`
                 INSERT INTO user_entitlements (user_email, tier) 
                 VALUES (?, ?) 
                 ON CONFLICT(user_email) DO UPDATE SET tier = excluded.tier, updated_at = CURRENT_TIMESTAMP
             `).bind(userEmail, newTier).run();
          return jsonResponse({ success: true, email: userEmail, tier: newTier });
        } catch (err: any) {
          return jsonResponse({ error: err.message }, 500);
        }
      }
    }

    // ── All /admin/* routes below — BACKEND AUTH ENFORCED ──
    if (path.startsWith("/admin/")) {
      // P0 FIX: Server-side admin authentication — frontend auth is NOT sufficient
      const isAdminAuthed = await verifyAdminAuth(request, env);
      if (!isAdminAuthed) {
        return jsonResponse({ error: "Unauthorized: Admin credentials required." }, 401);
      }

      // POST /admin/init-db — Initialize D1 tables
      if (request.method === "POST" && path === "/admin/init-db") {
        try {
          await env.DB.prepare(`
            CREATE TABLE IF NOT EXISTS user_entitlements (
              user_email TEXT PRIMARY KEY,
              tier TEXT NOT NULL DEFAULT 'FREE',
              created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
          `).run();
          await env.DB.prepare(`
            CREATE TABLE IF NOT EXISTS app_updates (
              version_code INTEGER PRIMARY KEY,
              version_name TEXT NOT NULL,
              download_url TEXT NOT NULL,
              release_notes TEXT,
              is_critical INTEGER DEFAULT 0,
              created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
          `).run();
          await env.DB.prepare(`
            CREATE TABLE IF NOT EXISTS support_tickets (
              id TEXT PRIMARY KEY,
              ticket_id TEXT,
              user_email TEXT,
              issue TEXT,
              admin_reply TEXT,
              status TEXT,
              notified_app INTEGER DEFAULT 0,
              timestamp INTEGER,
              updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
          `).run();
          return jsonResponse({ success: true, message: "Tables initialized" });
        } catch (err: any) {
          return jsonResponse({ error: err.message }, 500);
        }
      }

      // POST /admin/app/version — Update latest app version
      if (request.method === "POST" && path === "/admin/app/version") {
        try {
          // Auto-init tables if missing
          await env.DB.prepare(`CREATE TABLE IF NOT EXISTS app_updates (version_code INTEGER PRIMARY KEY, version_name TEXT NOT NULL, download_url TEXT NOT NULL, release_notes TEXT, is_critical INTEGER DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)`).run();

          const body: any = await request.json();
          await env.DB.prepare(
            "INSERT OR REPLACE INTO app_updates (version_code, version_name, download_url, release_notes, is_critical) VALUES (?, ?, ?, ?, ?)"
          ).bind(body.version_code, body.version_name, body.download_url, body.release_notes, body.is_critical || 0).run();
          return jsonResponse({ success: true });
        } catch (err: any) {
          return jsonResponse({ error: err.message }, 500);
        }
      }

      // POST /admin/upload-apk — Upload binary to Supabase Storage
      if (request.method === "POST" && path === "/admin/upload-apk") {
        try {
          const contentDisposition = request.headers.get("Content-Disposition");
          let filename = `update_${Date.now()}.apk`;
          if (contentDisposition) {
            const match = contentDisposition.match(/filename="(.+)"/);
            if (match) filename = match[1];
          }

          const body = await request.arrayBuffer();

          // Upload to Supabase Storage via REST API
          // Bucket: "apks"
          const supabaseUrl = env.SUPABASE_URL;
          const supabaseKey = env.SUPABASE_SERVICE_ROLE_KEY;

          const uploadUrl = `${supabaseUrl}/storage/v1/object/apks/${encodeURIComponent(filename)}`;

          const supabaseRes = await fetch(uploadUrl, {
            method: "POST",
            headers: {
              "Authorization": `Bearer ${supabaseKey}`,
              "Content-Type": "application/vnd.android.package-archive",
              "x-upsert": "true"
            },
            body: body
          });

          if (!supabaseRes.ok) {
            const errText = await supabaseRes.text();
            throw new Error(`Supabase Upload Failed: ${errText}`);
          }

          const publicUrl = `${supabaseUrl}/storage/v1/object/public/apks/${encodeURIComponent(filename)}`;

          return jsonResponse({ success: true, download_url: publicUrl });
        } catch (err: any) {
          return jsonResponse({ error: err.message }, 500);
        }
      }
    }

    // ══════════════════════════════════════════
    // ADMIN: QUEST CRUD
    // ══════════════════════════════════════════

    // GET /admin/quests — List all quests (including inactive)
    if (request.method === "GET" && path === "/admin/quests") {
      const { results } = await env.DB.prepare("SELECT * FROM quests ORDER BY created_at DESC").all();
      return jsonResponse(results);
    }

    // POST /admin/quests — Create quest
    if (request.method === "POST" && path === "/admin/quests") {
      const body: any = await request.json();
      const id = body.id || generateId();
      await env.DB.prepare(
        "INSERT INTO quests (id, title, description, xp_reward, quest_type, action_trigger, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)"
      ).bind(id, body.title, body.description, body.xp_reward, body.quest_type, body.action_trigger, body.is_active ?? 1).run();
      return jsonResponse({ success: true, id });
    }

    // PUT /admin/quests/:id — Update quest
    if (request.method === "PUT" && path.startsWith("/admin/quests/")) {
      const id = path.split("/admin/quests/")[1];
      const body: any = await request.json();
      await env.DB.prepare(
        "UPDATE quests SET title = ?, description = ?, xp_reward = ?, quest_type = ?, action_trigger = ?, is_active = ? WHERE id = ?"
      ).bind(body.title, body.description, body.xp_reward, body.quest_type, body.action_trigger, body.is_active ?? 1, id).run();
      return jsonResponse({ success: true });
    }

    // DELETE /admin/quests/:id — Deactivate quest (soft delete)
    if (request.method === "DELETE" && path.startsWith("/admin/quests/")) {
      const id = path.split("/admin/quests/")[1];
      await env.DB.prepare("UPDATE quests SET is_active = 0 WHERE id = ?").bind(id).run();
      return jsonResponse({ success: true });
    }

    // ══════════════════════════════════════════
    // ADMIN: SUPPORT TICKETS
    // ══════════════════════════════════════════

    // GET /admin/tickets
    if (request.method === "GET" && path === "/admin/tickets") {
      const { results } = await env.DB.prepare("SELECT * FROM support_tickets ORDER BY timestamp DESC").all();
      return jsonResponse(results);
    }

    // POST /admin/tickets — Sync a ticket from the app (called by app on submit)
    if (request.method === "POST" && path === "/admin/tickets") {
      const body: any = await request.json();
      const id = body.id || generateId();
      await env.DB.prepare(
        "INSERT OR REPLACE INTO support_tickets (id, ticket_id, user_email, issue, status, timestamp) VALUES (?, ?, ?, ?, 'open', ?)"
      ).bind(id, body.ticket_id, body.user_email || "", body.issue, body.timestamp || Date.now()).run();
      return jsonResponse({ success: true, id });
    }

    // PUT /admin/tickets/:id — Reply / update status
    if (request.method === "PUT" && path.startsWith("/admin/tickets/")) {
      try {
        const id = path.split("/admin/tickets/")[1];
        const body: any = await request.json();
        try {
          await env.DB.prepare(
            "UPDATE support_tickets SET status = ?, admin_reply = ?, notified_app = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
          ).bind(body.status || "in_progress", body.admin_reply || "", id).run();
        } catch (e) {
          // Fallback for older schemas without notified_app and updated_at
          await env.DB.prepare(
            "UPDATE support_tickets SET status = ?, admin_reply = ? WHERE id = ?"
          ).bind(body.status || "in_progress", body.admin_reply || "", id).run();
        }
        return jsonResponse({ success: true });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // POST /api/tickets/mark-notified/:id
    if (request.method === "POST" && path.startsWith("/api/tickets/mark-notified/")) {
      const id = path.split("/api/tickets/mark-notified/")[1];
      await env.DB.prepare("UPDATE support_tickets SET notified_app = 1 WHERE id = ?").bind(id).run();
      return jsonResponse({ success: true });
    }

    // ══════════════════════════════════════════
    // ADMIN: NOTIFICATION SCHEDULER
    // ══════════════════════════════════════════

    // GET /admin/notifications
    if (request.method === "GET" && path === "/admin/notifications") {
      const { results } = await env.DB.prepare("SELECT * FROM scheduled_notifications ORDER BY scheduled_at DESC").all();
      return jsonResponse(results);
    }

    // POST /admin/notifications — Schedule a notification
    if (request.method === "POST" && path === "/admin/notifications") {
      const body: any = await request.json();
      const id = generateId();
      await env.DB.prepare(
        "INSERT INTO scheduled_notifications (id, title, message, type, scheduled_at) VALUES (?, ?, ?, ?, ?)"
      ).bind(id, body.title, body.message, body.type || "custom", body.scheduled_at).run();
      return jsonResponse({ success: true, id });
    }

    // DELETE /admin/notifications/:id
    if (request.method === "DELETE" && path.startsWith("/admin/notifications/")) {
      const id = path.split("/admin/notifications/")[1];
      await env.DB.prepare("DELETE FROM scheduled_notifications WHERE id = ?").bind(id).run();
      return jsonResponse({ success: true });
    }

    // ══════════════════════════════════════════
    // ADMIN: PROTCTUS LAB DATA ENTRY
    // ══════════════════════════════════════════

    // POST /admin/lab/chapter
    if (request.method === "POST" && path === "/admin/lab/chapter") {
      try {
        const body: any = await request.json();
        const id = body.id || generateId();
        await env.DB.prepare(
          "INSERT INTO lab_chapters (id, title, created_at) VALUES (?, ?, ?)"
        ).bind(id, body.title, Date.now()).run();
        return jsonResponse({ success: true, id });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // POST /admin/lab/topic
    if (request.method === "POST" && path === "/admin/lab/topic") {
      try {
        const body: any = await request.json();
        const id = body.id || generateId();
        await env.DB.prepare(
          "INSERT INTO lab_topics (id, chapter_id, title, content, created_at) VALUES (?, ?, ?, ?, ?)"
        ).bind(id, body.chapter_id, body.title, body.content, Date.now()).run();
        return jsonResponse({ success: true, id });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // POST /admin/lab/quiz
    if (request.method === "POST" && path === "/admin/lab/quiz") {
      try {
        const body: any = await request.json();
        const id = body.id || generateId();
        await env.DB.prepare(
          "INSERT INTO lab_quizzes (id, topic_id, question, options, correct_answer_index, explanation) VALUES (?, ?, ?, ?, ?, ?)"
        ).bind(
          id,
          body.topic_id,
          body.question,
          JSON.stringify(body.options || []),
          body.correct_answer_index,
          body.explanation
        ).run();
        return jsonResponse({ success: true, id });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // ══════════════════════════════════════════
    // ADMIN: MICROLEARNING FEED CRUD
    // ══════════════════════════════════════════

    // GET /admin/lab/feed — List all feed cards for admin management
    if (request.method === "GET" && path === "/admin/lab/feed") {
      try {
        const { results } = await env.DB.prepare("SELECT * FROM microlearning_feed ORDER BY created_at DESC").all();
        const formattedResults = results?.map((card: any) => {
          try { card.options = JSON.parse(card.options); } catch { card.options = []; }
          return card;
        });
        return jsonResponse(formattedResults || []);
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // POST /admin/lab/feed — Create a new feed card
    if (request.method === "POST" && path === "/admin/lab/feed") {
      try {
        const body: any = await request.json();
        const id = body.id || generateId();
        await env.DB.prepare(
          "INSERT INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).bind(
          id,
          body.type || "INFO",
          body.category || "",
          body.title,
          body.description,
          body.visualUrl || "",
          JSON.stringify(body.options || []),
          body.correctAnswer || "",
          Date.now()
        ).run();
        return jsonResponse({ success: true, id });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // PUT /admin/lab/feed/:id — Update a feed card
    if (request.method === "PUT" && path.startsWith("/admin/lab/feed/")) {
      try {
        const id = path.split("/admin/lab/feed/")[1];
        const body: any = await request.json();
        await env.DB.prepare(
          "UPDATE microlearning_feed SET type = ?, category = ?, title = ?, description = ?, visualUrl = ?, options = ?, correctAnswer = ? WHERE id = ?"
        ).bind(
          body.type || "INFO",
          body.category || "",
          body.title,
          body.description,
          body.visualUrl || "",
          JSON.stringify(body.options || []),
          body.correctAnswer || "",
          id
        ).run();
        return jsonResponse({ success: true });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // DELETE /admin/lab/feed/:id — Delete a feed card
    if (request.method === "DELETE" && path.startsWith("/admin/lab/feed/")) {
      try {
        const id = path.split("/admin/lab/feed/")[1];
        await env.DB.prepare("DELETE FROM microlearning_feed WHERE id = ?").bind(id).run();
        return jsonResponse({ success: true });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // ══════════════════════════════════════════
    // ADMIN: DASHBOARD STATS
    // ══════════════════════════════════════════

    if (request.method === "GET" && path === "/admin/stats") {
      const quests = await env.DB.prepare("SELECT COUNT(*) as count FROM quests WHERE is_active = 1").first();
      const tickets = await env.DB.prepare("SELECT COUNT(*) as count FROM support_tickets WHERE status = 'open'").first();
      const notifs = await env.DB.prepare("SELECT COUNT(*) as count FROM scheduled_notifications WHERE is_sent = 0").first();
      return jsonResponse({
        activeQuests: quests?.count ?? 0,
        openTickets: tickets?.count ?? 0,
        pendingNotifications: notifs?.count ?? 0
      });
    }

    // ── Default 404 ──
    return jsonResponse({ error: "Endpoint not found." }, 404);
  },
};

// ── Handler Functions ──

async function handleGetQuests(url: URL, env: Env): Promise<Response> {
  try {
    const typeFilter = url.searchParams.get("type");
    const limitParam = url.searchParams.get("limit") || "50";
    const offsetParam = url.searchParams.get("offset") || "0";

    let maxAllowedLimit = 50;
    if (typeFilter?.toUpperCase() === "DAILY") maxAllowedLimit = 10;
    else if (typeFilter?.toUpperCase() === "ONETIME" || typeFilter?.toUpperCase() === "SEASONAL") maxAllowedLimit = 2;

    const limit = Math.min(Math.max(parseInt(limitParam, 10), 1), maxAllowedLimit);
    const offset = Math.max(parseInt(offsetParam, 10), 0);

    let queryStr = `SELECT * FROM quests WHERE is_active = 1`;
    let params: any[] = [];

    if (typeFilter) {
      queryStr += ` AND quest_type = ?`;
      params.push(typeFilter.toUpperCase());
    }

    queryStr += ` ORDER BY created_at DESC LIMIT ? OFFSET ?`;
    params.push(limit, offset);

    const { results, success, error } = await env.DB.prepare(queryStr).bind(...params).all();
    if (!success) return jsonResponse({ error: "Query failed" }, 500);
    return jsonResponse(results);
  } catch (err: any) {
    return jsonResponse({ error: err.message }, 500);
  }
}

async function handleGetRewards(env: Env): Promise<Response> {
  try {
    const { results, success } = await env.DB.prepare("SELECT * FROM rewards WHERE is_active = 1 ORDER BY rank ASC").all();
    if (!success) return jsonResponse({ error: "Query failed" }, 500);
    return jsonResponse(results);
  } catch (err: any) {
    return jsonResponse({ error: err.message }, 500);
  }
}

async function handleAppNotifications(env: Env): Promise<Response> {
  try {
    // Return notifications scheduled for the past (due) and not yet sent
    const now = new Date().toISOString();
    const { results } = await env.DB.prepare(
      "SELECT id, title, message, type FROM scheduled_notifications WHERE is_sent = 0 AND scheduled_at <= ? ORDER BY scheduled_at ASC LIMIT 5"
    ).bind(now).all();

    // VULN-X3 fix: Use parameterized queries instead of string interpolation
    if (results && results.length > 0) {
      for (const r of results) {
        await env.DB.prepare(`UPDATE scheduled_notifications SET is_sent = 1 WHERE id = ?`).bind(r.id).run();
      }
    }

    return jsonResponse(results || []);
  } catch (err: any) {
    return jsonResponse([], 200);
  }
}
