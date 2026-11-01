import { PDFDocument, rgb, StandardFonts } from 'pdf-lib';

export interface Env {
  RAZORPAY_KEY_ID: string;
  RAZORPAY_KEY_SECRET: string;
  MAILGUN_API_KEY: string;
  MAILGUN_DOMAIN: string;
  ADMIN_SECRET_KEY: string;
  DB: D1Database;
  FIREBASE_API_KEY?: string;
  FCM_PROJECT_ID?: string;
  FCM_CLIENT_EMAIL?: string;
  FCM_PRIVATE_KEY?: string;
}

// Helper to send FCM notifications
async function sendFCMNotification(env: Env, topic: string, title: string, body: string) {
  if (!env.FCM_PROJECT_ID || !env.FCM_CLIENT_EMAIL || !env.FCM_PRIVATE_KEY) return;
  
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: env.FCM_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now
  };

  const utf8Encoder = new TextEncoder();
  const b64Url = (str: string) => btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  
  const encodedHeader = b64Url(String.fromCharCode(...utf8Encoder.encode(JSON.stringify(header))));
  const encodedClaim = b64Url(String.fromCharCode(...utf8Encoder.encode(JSON.stringify(claim))));
  const unsignedToken = `${encodedHeader}.${encodedClaim}`;

  const pem = env.FCM_PRIVATE_KEY
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\s+/g, '');
  
  const binaryDer = Uint8Array.from(atob(pem), c => c.charCodeAt(0));
  
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signatureBuffer = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    utf8Encoder.encode(unsignedToken)
  );

  const signatureBytes = new Uint8Array(signatureBuffer);
  const encodedSignature = b64Url(String.fromCharCode(...signatureBytes));
  const jwt = `${unsignedToken}.${encodedSignature}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  const tokenData: any = await tokenRes.json();
  const accessToken = tokenData.access_token;

  if (accessToken) {
    await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${accessToken}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        message: {
          topic: topic,
          notification: { title, body }
        }
      })
    });
  }
}

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Admin-Secret-Key",
};

function jsonResponse(data: any, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

// PDF Invoice Generator
async function generatePdfInvoice(email: string, tier: string, amount: number, receiptId: string, transactionId: string): Promise<Uint8Array> {
  const pdfDoc = await PDFDocument.create();
  const page = pdfDoc.addPage([600, 800]);
  const helveticaFont = await pdfDoc.embedFont(StandardFonts.Helvetica);
  const helveticaBold = await pdfDoc.embedFont(StandardFonts.HelveticaBold);
  const { width, height } = page.getSize();
  const colorDark = rgb(28/255, 25/255, 25/255); // #1C1919
  const colorSurface = rgb(42/255, 43/255, 49/255); // #2A2B31
  const colorBlue = rgb(34/255, 211/255, 238/255); // #22D3EE
  const colorText = rgb(227/255, 227/255, 227/255); // #e3e3e3
  const colorGold = rgb(251/255, 191/255, 36/255); // #FBBF24
  
  // Header Background
  page.drawRectangle({ x: 0, y: height - 120, width: width, height: 120, color: colorDark });
  
  // Title
  page.drawText('PROTCTUS', { x: 40, y: height - 60, size: 28, font: helveticaBold, color: colorText });
  page.drawText('SECURE NETWORK INFRASTRUCTURE', { x: 40, y: height - 80, size: 10, font: helveticaBold, color: colorBlue });
  
  page.drawText('TAX INVOICE', { x: width - 180, y: height - 60, size: 24, font: helveticaBold, color: colorGold });
  
  // Business Info
  let y = height - 160;
  page.drawText('Protctus Security Ltd.', { x: 40, y, size: 12, font: helveticaBold, color: colorDark });
  page.drawText('Level 5, Cyber Hub, Matrix Road', { x: 40, y: y-15, size: 10, font: helveticaFont });
  page.drawText('contact@protctus.com', { x: 40, y: y-30, size: 10, font: helveticaFont });
  
  // Billed To
  page.drawText('BILLED TO:', { x: width - 200, y, size: 10, font: helveticaBold, color: colorDark });
  page.drawText(email, { x: width - 200, y: y-15, size: 10, font: helveticaFont });
  
  y -= 80;
  
  // Invoice Details
  page.drawText(`Invoice No: ${receiptId}`, { x: 40, y, size: 10, font: helveticaBold });
  page.drawText(`Transaction ID: ${transactionId}`, { x: 40, y: y-15, size: 10, font: helveticaFont });
  page.drawText(`Date: ${new Date().toLocaleDateString()}`, { x: width - 200, y, size: 10, font: helveticaFont });
  
  y -= 50;
  
  // Table Header
  page.drawRectangle({ x: 40, y: y-20, width: width - 80, height: 30, color: colorSurface });
  page.drawText('DESCRIPTION', { x: 50, y: y-10, size: 10, font: helveticaBold, color: colorText });
  page.drawText('AMOUNT', { x: width - 120, y: y-10, size: 10, font: helveticaBold, color: colorText });
  
  y -= 40;
  
  // Table Row
  const amountStr = `Rs. ${(amount / 100).toFixed(2)}`;
  page.drawText(`Protctus ${tier} Subscription`, { x: 50, y: y-10, size: 10, font: helveticaFont });
  page.drawText(amountStr, { x: width - 120, y: y-10, size: 10, font: helveticaFont });
  
  page.drawLine({ start: { x: 40, y: y-30 }, end: { x: width - 40, y: y-30 }, thickness: 1, color: rgb(0.8, 0.8, 0.8) });
  
  y -= 60;
  
  // Totals
  page.drawText('Subtotal:', { x: width - 200, y, size: 10, font: helveticaFont });
  page.drawText(amountStr, { x: width - 120, y, size: 10, font: helveticaFont });
  
  page.drawText('Tax (0%):', { x: width - 200, y: y-15, size: 10, font: helveticaFont });
  page.drawText('Rs. 0.00', { x: width - 120, y: y-15, size: 10, font: helveticaFont });
  
  page.drawText('TOTAL PAID:', { x: width - 200, y: y-35, size: 12, font: helveticaBold, color: colorDark });
  page.drawText(amountStr, { x: width - 120, y: y-35, size: 12, font: helveticaBold, color: colorDark });
  
  // Footer
  page.drawText('Thank you for choosing Protctus Security.', { x: 40, y: 50, size: 10, font: helveticaFont });
  page.drawText('This is a computer-generated invoice and requires no signature.', { x: 40, y: 35, size: 8, font: helveticaFont, color: rgb(0.5, 0.5, 0.5) });
  
  return await pdfDoc.save();
}

// Mailgun helper
async function sendInvoiceEmail(env: Env, email: string, tier: string, receiptId: string, transactionId: string, amount: number) {
  if (!env.MAILGUN_API_KEY || !env.MAILGUN_DOMAIN) return;
  const url = `https://api.mailgun.net/v3/${env.MAILGUN_DOMAIN}/messages`;
  const formData = new FormData();
  formData.append("from", `Protctus Billing <billing@${env.MAILGUN_DOMAIN}>`);
  formData.append("to", email);
  formData.append("subject", `Your Protctus ${tier} Receipt [${receiptId}]`);
  
  const htmlTemplate = `
    <!DOCTYPE html>
    <html>
    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #1C1919; margin: 0; padding: 40px 0; color: #e3e3e3;">
      <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 600px; background-color: #2A2B31; border: 1px solid #4A5568; border-radius: 12px; overflow: hidden; box-shadow: 0 0 20px rgba(34, 211, 238, 0.1);">
        <!-- Header -->
        <tr>
          <td style="padding: 40px 30px; text-align: center; border-bottom: 1px solid #4A5568; background: #2A2B31;">
            <h1 style="color: #e3e3e3; margin: 0; font-size: 28px; letter-spacing: 4px; font-weight: 800; text-transform: uppercase;">PROTCTUS<span style="color: #22D3EE;">.</span></h1>
            <p style="color: #22D3EE; margin: 8px 0 0 0; font-size: 11px; letter-spacing: 2px; text-transform: uppercase; font-weight: 600;">Secure Network Infrastructure</p>
          </td>
        </tr>
        <!-- Body -->
        <tr>
          <td style="padding: 40px 30px; background-color: #2A2B31;">
            <h2 style="color: #e3e3e3; margin-top: 0; font-size: 22px; font-weight: 600;">ACCESS GRANTED</h2>
            <p style="color: #7F909F; line-height: 1.6; font-size: 15px;">Welcome to the Inner Circle. Your Protctus <strong style="color: #FBBF24;">${tier}</strong> clearance has been officially authorized and provisioned.</p>
            
            <!-- Receipt Card -->
            <table border="0" cellpadding="20" cellspacing="0" width="100%" style="margin-top: 35px; background-color: #1C1919; border: 1px solid #4A5568; border-radius: 8px;">
              <tr>
                <td colspan="2" style="border-bottom: 1px solid #4A5568; padding-bottom: 15px; padding-top: 5px;">
                  <p style="margin: 0; color: #7F909F; font-size: 10px; letter-spacing: 2px; text-transform: uppercase;">Official Ledger Entry</p>
                </td>
              </tr>
              <tr>
                <td style="color: #7F909F; font-size: 14px; padding-top: 20px;"><strong>TRANSACTION ID</strong></td>
                <td style="color: #e3e3e3; text-align: right; font-family: monospace; font-size: 15px; padding-top: 20px;">${transactionId}</td>
              </tr>
              <tr>
                <td style="color: #7F909F; font-size: 14px;"><strong>RECEIPT NO.</strong></td>
                <td style="color: #e3e3e3; text-align: right; font-family: monospace; font-size: 15px;">${receiptId}</td>
              </tr>
              <tr>
                <td style="color: #7F909F; font-size: 14px;"><strong>CLEARANCE LEVEL</strong></td>
                <td style="color: #FBBF24; text-align: right; font-weight: bold; font-size: 15px;">${tier}</td>
              </tr>
              <tr>
                <td style="color: #7F909F; font-size: 14px;"><strong>AMOUNT PAID</strong></td>
                <td style="color: #e3e3e3; text-align: right; font-size: 14px;">Rs. ${(amount / 100).toFixed(2)}</td>
              </tr>
              <tr>
                <td style="color: #7F909F; font-size: 14px;"><strong>TIMESTAMP</strong></td>
                <td style="color: #e3e3e3; text-align: right; font-size: 14px;">${new Date().toUTCString()}</td>
              </tr>
            </table>

            <!-- Next Steps -->
            <div style="margin-top: 35px; padding: 20px; background-color: rgba(34, 211, 238, 0.05); border-left: 3px solid #22D3EE; border-radius: 4px;">
              <p style="color: #e3e3e3; margin: 0; font-size: 14px; line-height: 1.5;">Your device is now armed. Restart the Protctus application to sync your new cryptographic entitlements.</p>
              <br/>
              <p style="color: #7F909F; margin: 0; font-size: 13px; line-height: 1.5;">Please find your formal tax invoice attached to this email as a PDF.</p>
            </div>
          </td>
        </tr>
        <!-- Footer -->
        <tr>
          <td style="background-color: #1C1919; padding: 30px; text-align: center; border-top: 1px solid #4A5568;">
            <p style="color: #7F909F; font-size: 11px; margin: 0; letter-spacing: 1px;">&copy; ${new Date().getFullYear()} PROTCTUS SYSTEMS. END-TO-END ENCRYPTED.</p>
          </td>
        </tr>
      </table>
    </body>
    </html>
  `;
  
  formData.append("html", htmlTemplate);

  try {
    const pdfBytes = await generatePdfInvoice(email, tier, amount, receiptId, transactionId);
    formData.append("attachment", new File([pdfBytes], "Protctus_Invoice.pdf", { type: "application/pdf" }));
  } catch (pdfErr) {
    console.error("PDF Generation failed:", pdfErr);
  }

  await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Basic ${btoa(`api:${env.MAILGUN_API_KEY}`)}`,
    },
    body: formData,
  });
}

export default {
  // ══════════════════════════════════════════
  // CRON JOBS (Runs Daily)
  // ══════════════════════════════════════════
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil((async () => {
      // 1. Downgrade expired subscriptions
      await env.DB.prepare(`
        UPDATE user_entitlements 
        SET tier = 'FREE', expires_at = NULL 
        WHERE expires_at < CURRENT_TIMESTAMP AND tier != 'FREE'
      `).run();

      // 2. Notify users expiring in 1, 2, or 3 days
      // For simplicity in SQL string operations across DBs, we'll just query those within the next 3 days
      const expiringSoon = await env.DB.prepare(`
        SELECT email, tier, expires_at 
        FROM user_entitlements 
        WHERE expires_at BETWEEN CURRENT_TIMESTAMP AND datetime('now', '+3 days')
          AND tier != 'FREE'
      `).all();

      if (expiringSoon.results && expiringSoon.results.length > 0) {
        for (const user of expiringSoon.results) {
          // Since we don't map email to FCM tokens locally, we broadcast to a topic based on sanitized email.
          // In Android, users must subscribe to topic `user_email_escaped` to receive personal alerts.
          // Alternatively, broadcast to 'all_users' with a generic message, but that's spammy.
          const topic = String(user.email).replace(/[^a-zA-Z0-9]/g, '_');
          await sendFCMNotification(
            env, 
            topic, 
            "Subscription Expiring Soon!", 
            `Your ${user.tier} plan is expiring on ${user.expires_at}. Renew now to keep your protection active.`
          );
        }
      }
    })());
  },

  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    // POST /create-order — Creates a Razorpay Order
    if (request.method === "POST" && path === "/create-order") {
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
          receipt,
        });
      } catch (err: any) {
        return jsonResponse({ error: err.message || "Internal error creating order" }, 500);
      }
    }

    // POST /verify-payment — Verifies Razorpay payment signature & Updates D1 & Sends Email
    if (request.method === "POST" && path === "/verify-payment") {
      try {
        const body: any = await request.json();
        const { razorpay_order_id, razorpay_payment_id, razorpay_signature, email, tier, amount, receipt } = body;

        if (!razorpay_order_id || !razorpay_payment_id || !razorpay_signature || !email) {
          return jsonResponse({ error: "Missing required payment fields (including email)" }, 400);
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

        const resolvedTier = tier || "PREMIUM";
        const txAmount = amount || 0;
        const txReceipt = receipt || `rcpt_${Date.now()}`;
        const txId = `tx_${Date.now()}_${Math.random().toString(36).substring(7)}`;

        // 1. Update D1 Ledger (transactions) and Entitlements
        // Schema assumptions: 
        // transactions (id TEXT PRIMARY KEY, user_email TEXT, payment_id TEXT, receipt_no TEXT, amount INTEGER, tier_purchased TEXT, status TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)
        // user_entitlements (email TEXT PRIMARY KEY, tier TEXT)
        
        let expiresAtObj = new Date();
        const bodyDurationMonths = (body as any).duration_months || 12; // default to 1 year
        expiresAtObj.setMonth(expiresAtObj.getMonth() + bodyDurationMonths);
        const expiresAtStr = expiresAtObj.toISOString().slice(0, 19).replace('T', ' ');

        try {
          await env.DB.prepare(`
            INSERT INTO transactions (id, user_email, payment_id, receipt_no, amount, tier_purchased, status)
            VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS')
          `).bind(txId, email, razorpay_payment_id, txReceipt, txAmount, resolvedTier).run();

          await env.DB.prepare(`
            INSERT INTO user_entitlements (email, tier, expires_at) VALUES (?, ?, ?)
            ON CONFLICT(email) DO UPDATE SET tier = excluded.tier, expires_at = excluded.expires_at
          `).bind(email, resolvedTier, expiresAtStr).run();
        } catch (dbErr) {
          console.error("D1 DB Error. Make sure tables exist.", dbErr);
          // Continuing so the user still gets verified on app side even if D1 schema isn't created yet
        }

        // 2. Trigger Mailgun Email Invoice & Attach PDF
        try {
          await sendInvoiceEmail(env, email, resolvedTier, txReceipt, razorpay_payment_id, txAmount);
        } catch (emailErr) {
          console.error("Mailgun Error", emailErr);
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
      } else {
        // Bypass if no Firebase key is set, rely on app for now (Not recommended for prod)
        // Extract email from unverified JWT
        try {
          const parts = token.split(".");
          const payload = JSON.parse(atob(parts[1]));
          userEmail = payload.email || "unknown";
          valid = true;
        } catch { }
      }

      if (!valid || !userEmail || userEmail === "unknown") {
        return jsonResponse({ error: "Unauthorized or invalid token" }, 401);
      }

      if (request.method === "GET") {
         try {
             const result = await env.DB.prepare("SELECT tier, expires_at FROM user_entitlements WHERE email = ?").bind(userEmail).first();
             return jsonResponse({ 
                email: userEmail, 
                tier: result?.tier || "FREE",
                expires_at: result?.expires_at || null
             });
         } catch (err: any) {
             return jsonResponse({ error: err.message }, 500);
         }
      }

      if (request.method === "POST") {
         try {
             const body: any = await request.json();
             const newTier = body.tier || "FREE";
             const duration = body.duration_months || 1;
             let expiresAtStr = null;
             
             if (newTier !== "FREE") {
                 let d = new Date();
                 d.setMonth(d.getMonth() + duration);
                 expiresAtStr = d.toISOString().slice(0, 19).replace('T', ' ');
             }

             await env.DB.prepare(`
                 INSERT INTO user_entitlements (email, tier, expires_at) 
                 VALUES (?, ?, ?) 
                 ON CONFLICT(email) DO UPDATE SET tier = excluded.tier, expires_at = excluded.expires_at
             `).bind(userEmail, newTier, expiresAtStr).run();
             return jsonResponse({ success: true, email: userEmail, tier: newTier, expires_at: expiresAtStr });
         } catch (err: any) {
             return jsonResponse({ error: err.message }, 500);
         }
      }
    }

    // ══════════════════════════════════════════
    // USER STATUS (Canonical Sync Endpoint)
    // ══════════════════════════════════════════
    if (request.method === "GET" && path === "/api/user/status") {
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
      } else {
        try {
          const parts = token.split(".");
          const payload = JSON.parse(atob(parts[1]));
          userEmail = payload.email || "unknown";
          valid = true;
        } catch { }
      }

      if (!valid || !userEmail || userEmail === "unknown") {
        return jsonResponse({ error: "Unauthorized or invalid token" }, 401);
      }

      try {
        const result = await env.DB.prepare(
          "SELECT tier, expires_at FROM user_entitlements WHERE email = ?"
        ).bind(userEmail).first();

        const tier = (result?.tier as string) || "FREE";
        const expiresAt = result?.expires_at as string | null;

        // Determine active status: FREE is always ACTIVE, paid tiers check expiry
        let status = "ACTIVE";
        if (tier !== "FREE" && expiresAt) {
          const expiryDate = new Date(expiresAt);
          if (expiryDate < new Date()) {
            status = "EXPIRED";
          }
        }

        return jsonResponse({
          email: userEmail,
          tier: tier,
          status: status,
          expires_at: expiresAt
        });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // --- ADMIN ROUTES ---
    const adminKey = request.headers.get("X-Admin-Secret-Key");
    if (path.startsWith("/api/admin")) {
      if (!env.ADMIN_SECRET_KEY || adminKey !== env.ADMIN_SECRET_KEY) {
        return jsonResponse({ error: "403 Forbidden" }, 403);
      }
    }

    // GET /api/admin/users
    if (request.method === "GET" && path === "/api/admin/users") {
      try {
        const users = await env.DB.prepare("SELECT email, tier, expires_at FROM user_entitlements").all();
        return jsonResponse({ success: true, data: users.results });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // POST /api/admin/grant-tier
    if (request.method === "POST" && path === "/api/admin/grant-tier") {
      try {
        const body: any = await request.json();
        const { email, tier, duration_months } = body;
        if (!email || !tier) return jsonResponse({ error: "Email and tier required" }, 400);

        let expiresAtStr = null;
        if (tier !== "FREE") {
            let d = new Date();
            d.setMonth(d.getMonth() + (duration_months || 12));
            expiresAtStr = d.toISOString().slice(0, 19).replace('T', ' ');
        }

        await env.DB.prepare(`
          INSERT INTO user_entitlements (email, tier, expires_at) VALUES (?, ?, ?)
          ON CONFLICT(email) DO UPDATE SET tier = excluded.tier, expires_at = excluded.expires_at
        `).bind(email, tier, expiresAtStr).run();

        return jsonResponse({ success: true, message: `Granted ${tier} to ${email} until ${expiresAtStr || 'forever'}` });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // POST /api/admin/refund
    if (request.method === "POST" && path === "/api/admin/refund") {
      try {
        const body: any = await request.json();
        const { payment_id, amount } = body; // amount in paise
        if (!payment_id) return jsonResponse({ error: "payment_id required" }, 400);

        const credentials = btoa(`${env.RAZORPAY_KEY_ID}:${env.RAZORPAY_KEY_SECRET}`);
        const rzpResponse = await fetch(`https://api.razorpay.com/v1/payments/${payment_id}/refund`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Basic ${credentials}`,
          },
          body: amount ? JSON.stringify({ amount }) : JSON.stringify({}),
        });

        if (!rzpResponse.ok) {
          const errBody: any = await rzpResponse.json();
          return jsonResponse({ error: errBody.error?.description || "Razorpay refund failed" }, rzpResponse.status);
        }

        const refundResponse: any = await rzpResponse.json();

        // Update Ledger
        try {
          await env.DB.prepare(`
            UPDATE transactions SET status = 'REFUNDED' WHERE payment_id = ?
          `).bind(payment_id).run();
        } catch (dbErr) {
          console.error("D1 DB Error during refund", dbErr);
        }

        return jsonResponse({ success: true, refund: refundResponse });
      } catch (err: any) {
        return jsonResponse({ error: err.message }, 500);
      }
    }

    // Default 404
    return jsonResponse({ error: "Endpoint not found." }, 404);
  },
};
