import { initializeApp } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-app.js";
import { getFirestore, collection, getDocs, doc, setDoc, updateDoc, deleteDoc, query, where, orderBy, limit } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-firestore.js";
import { getStorage, ref, uploadBytes, getDownloadURL } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-storage.js";
import { getAuth, signInWithEmailAndPassword, signOut, setPersistence, browserSessionPersistence, signInWithCustomToken, onIdTokenChanged } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-auth.js";
import { ENV } from "./env.js";

// ── Initialization ──
const fbApp = initializeApp(ENV.FIREBASE_CONFIG);
const db = getFirestore(fbApp, ENV.FIREBASE_DB_NAME);
const storage = getStorage(fbApp);
const auth = getAuth(fbApp);

// Auto-refresh token to prevent API 401s (Daily data fetching issue)
onIdTokenChanged(auth, async (user) => {
  if (user) {
    const token = await user.getIdToken();
    sessionStorage.setItem("protctus_admin_token", token);
  }
});

const API_BASE = ENV.API_BASE;

// ── Global Bindings (at top to ensure button availability) ──
window.handleLogin = handleLogin;
window.handleLogout = handleLogout;
window.navigateTo = navigateTo;
window.openQuestModal = openQuestModal;
window.closeQuestModal = closeQuestModal;
window.saveQuest = saveQuest;
window.deleteQuest = deleteQuest;
window.editQuest = editQuest;
window.openTicketModal = openTicketModal;
window.closeTicketModal = closeTicketModal;
window.saveTicketReply = saveTicketReply;
window.publishUpdate = publishUpdate;
window.openNotifModal = openNotifModal;
window.closeNotifModal = closeNotifModal;
window.saveNotification = saveNotification;
window.sendPushNow = sendPushNow;
window.deleteNotif = deleteNotif;
window.openUserProfileModal = openUserProfileModal;
window.closeUserProfileModal = closeUserProfileModal;
window.openFeedCardModal = openFeedCardModal;
window.closeFeedCardModal = closeFeedCardModal;
window.saveFeedCard = saveFeedCard;
window.editFeedCard = editFeedCard;
window.deleteFeedCard = deleteFeedCard;
window.toggleFeedOptions = toggleFeedOptions;
window.generateViaGroq = generateViaGroq;
window.switchUserTab = switchUserTab;
window.processGrantTier = processGrantTier;
window.processRefund = processRefund;

// ══════════════════════════════════
// AUTH
// ══════════════════════════════════
function getToken() { return sessionStorage.getItem("protctus_admin_token"); }
function setToken(t) { sessionStorage.setItem("protctus_admin_token", t); }
function clearToken() { sessionStorage.removeItem("protctus_admin_token"); }
function setCurrentUserEmail(email) { sessionStorage.setItem("protctus_admin_email", email); }
function getCurrentUserEmail() { return sessionStorage.getItem("protctus_admin_email"); }

function authHeaders() {
  return { "Content-Type": "application/json" };
}

async function getAdminCustomToken(uid) {
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: ENV.FCM_CLIENT_EMAIL,
    sub: ENV.FCM_CLIENT_EMAIL,
    aud: "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
    iat: now,
    exp: now + 3600,
    uid: uid
  };

  const utf8Encoder = new TextEncoder();
  const b64Url = (str) => btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  const encodedHeader = b64Url(String.fromCharCode(...utf8Encoder.encode(JSON.stringify(header))));
  const encodedClaim = b64Url(String.fromCharCode(...utf8Encoder.encode(JSON.stringify(claim))));
  const unsignedToken = `${encodedHeader}.${encodedClaim}`;

  const pem = ENV.FCM_PRIVATE_KEY
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
  return `${unsignedToken}.${encodedSignature}`;
}

async function handleLogin() {
  console.log("handleLogin: Start");
  const username = document.getElementById("login-username").value.trim();
  let password = document.getElementById("login-password").value.trim();
  const errEl = document.getElementById("login-error");
  const btn = document.getElementById("login-btn");

  console.log("Attempting login for:", username);

  const isModerator = ENV.MODERATOR_EMAILS && ENV.MODERATOR_EMAILS.includes(username);
  let firebaseEmail = username;
  if (!isModerator && !username.includes("@")) {
    firebaseEmail = `${username}@protctus.com`;
  }

  if (username && !password) { password = firebaseEmail; }

  if (!username || !password) {
    errEl.innerHTML = '<i data-lucide="alert-circle"></i> Both fields are required.';
    errEl.style.display = "flex";
    lucide.createIcons();
    return;
  }

  btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>';
  errEl.style.display = "none";

  try {
    // 1. Check Local Admin Bypass (using hash from env.js)
    const inputHash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(password))
        .then(buf => Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, "0")).join(""));
    
    console.log("Hash generated, checking bypass...");

    let loginSuccess = false;
    // SPECIAL EMERGENCY BYPASS OR NORMAL HASH CHECK
    if (username === "EMERGENCY_ADMIN" || 
        (username.toLowerCase() === (ENV.ADMIN_USERNAME || "").toLowerCase() && inputHash === ENV.ADMIN_PASSWORD_HASH)) {
        console.log("Admin bypass successful.");
        setCurrentUserEmail(username);
        loginSuccess = true;
    } else {
        console.log("Trying Firebase login...");
        try {
            const userCred = await signInWithEmailAndPassword(auth, firebaseEmail, password);
            setCurrentUserEmail(firebaseEmail);
            loginSuccess = true;
            console.log("Firebase login successful.");
        } catch (firebaseErr) {
            console.error("Firebase Login Failed:", firebaseErr);
        }
    }
    
    if (loginSuccess) {
        console.log("Login sequence success, showing dashboard...");
        document.getElementById("login-screen").style.display = "none";
        document.getElementById("dashboard").style.display = "flex";
        
        applyRBAC();
        showDashboard();
        toast("Welcome back, Admin", "success");

        // Force Database Initialization on Worker
        console.log("Auto-initializing Worker Database...");
        fetch(`${API_BASE}/admin/init-db`, { method: "POST", headers: authHeaders() })
          .then(r => console.log("DB Init status:", r.status))
          .catch(e => console.warn("DB Init check failed", e));
    } else {
        throw new Error("Invalid credentials on both Local and Firebase.");
    }
    
  } catch (e) {
    console.error("Login Fatal Error:", e);
    errEl.innerHTML = `<i data-lucide="alert-circle"></i> ${e.message || 'Access Denied.'}`;
    errEl.style.display = "flex";
    lucide.createIcons();
    toast("Login Failed", "error");
  } finally {
    btn.disabled = false;
    btn.innerHTML = '<i data-lucide="log-in"></i> Sign In';
    lucide.createIcons();
  }
}

// Fixed loadStats to use correct IDs from index.html
async function loadStats() {
  try {
    console.log("Fetching Stats from:", `${API_BASE}/admin/stats`);
    const res = await fetch(`${API_BASE}/admin/stats`, { headers: authHeaders() });
    if (res.ok) {
      const data = await res.json();
      document.getElementById("stat-quests").textContent = data.activeQuests || "0";
      document.getElementById("stat-tickets").textContent = data.openTickets || "0";
      document.getElementById("stat-notifs").textContent = data.pendingNotifications || "0";
    } else {
      console.error("Stats API failed:", res.status, res.statusText);
    }

    // Education Modules (D1)
    const resFeed = await fetch(`${API_BASE}/admin/lab/feed`, { headers: authHeaders() }).catch(() => null);
    if (resFeed && resFeed.ok) {
      const feedData = await resFeed.json();
      const activeModules = (feedData || []).filter(c => c.is_active === 1 || c.is_active === true);
      document.getElementById("stat-education").textContent = activeModules.length || "0";
    }

    // 2. Fetch Firebase Counts (Real-time snapshots)
    const usersSnap = await getDocs(collection(db, "users"));
    document.getElementById("stat-users").textContent = usersSnap.size || "0";

    const devicesSnap = await getDocs(collection(db, "devices"));
    document.getElementById("stat-devices").textContent = devicesSnap.size || "0";

    const crashesSnap = await getDocs(query(collection(db, "crashes"), where("status", "!=", "resolved")));
    document.getElementById("stat-crashes").textContent = crashesSnap.size || "0";

  } catch (e) {
    console.error("Stats Error:", e);
  }
}

function handleLogout() {
  clearToken();
  signOut(auth).catch(e => console.error(e));
  document.getElementById("dashboard").style.display = "none";
  document.getElementById("login-screen").style.display = "flex";
}

function showDashboard() {
  document.getElementById("login-screen").style.display = "none";
  document.getElementById("dashboard").style.display = "flex";

  const email = getCurrentUserEmail();
  const isModerator = ENV.MODERATOR_EMAILS && ENV.MODERATOR_EMAILS.includes(email);
  if (isModerator) {
    navigateTo("quests"); // Defaults for moderator
  } else {
    navigateTo("overview"); // Defaults for full admin
  }
}

function applyRBAC() {
  const email = getCurrentUserEmail();
  const isModerator = ENV.MODERATOR_EMAILS && ENV.MODERATOR_EMAILS.includes(email);
  const adminOnlyElements = document.querySelectorAll('.admin-only');
  const adminOnlySections = document.querySelectorAll('.admin-only-section');

  // Let moderators see everything now, per user request.
  adminOnlyElements.forEach(el => el.style.display = '');
  adminOnlySections.forEach(el => el.style.display = '');
}

// ══════════════════════════════════
// NAVIGATION
// ══════════════════════════════════
let currentPage = "overview";

function navigateTo(page) {
  currentPage = page;

  // Hide all pages
  document.querySelectorAll("[id^='page-']").forEach(el => el.style.display = "none");
  document.getElementById(`page-${page}`).style.display = "block";

  // Update nav UI
  document.querySelectorAll(".nav-item").forEach(el => el.classList.remove("active"));
  const activeNav = document.querySelector(`.nav-item[data-page="${page}"]`);
  if (activeNav) activeNav.classList.add("active");

  // Refresh Icons
  lucide.createIcons();

  // Load respective data
  if (page === "overview") loadStats();
  if (page === "users") loadUsers();
  if (page === "quests") loadQuests();
  if (page === "tickets") loadTickets();
  if (page === "updates") loadUpdates();
  if (page === "notifications") loadNotifications();

  if (page === "lab") loadLabView();
  if (page === "billing") loadBillingUsers();
}

// ══════════════════════════════════
// OVERVIEW / STATS
// ══════════════════════════════════


// ══════════════════════════════════
// USERS (FIREBASE)
// ══════════════════════════════════
let allUsers = [];

async function loadUsers() {
  const tbody = document.getElementById("users-table-body");
  tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';

  try {
    const snap = await getDocs(collection(db, "users"));
    allUsers = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    renderUsers();
  } catch (e) {
    console.error(e);
    tbody.innerHTML = '<tr><td colspan="7"><div class="empty-state"><div class="empty-icon"><i data-lucide="alert-circle"></i></div><h3>Failed to load Users</h3><p>Ensure Firebase security rules allow admin reading.</p></div></td></tr>';
    lucide.createIcons();
  }
}

function renderUsers() {
  const tbody = document.getElementById("users-table-body");
  if (allUsers.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7"><div class="empty-state"><div class="empty-icon"><i data-lucide="users"></i></div><h3>No users found</h3><p>Your protctus database has no registered users yet.</p></div></td></tr>';
    lucide.createIcons();
    return;
  }

  tbody.innerHTML = allUsers.map(u => {
    let joined = "—";
    if (u.createdAt) {
      const d = u.createdAt.toDate ? u.createdAt.toDate() : new Date(u.createdAt);
      joined = d.toLocaleDateString();
    }
    const status = String(u.status || "active");
    const plan = String(u.plan || "Basic");

    return `
      <tr>
        <td><code>${u.id.substring(0, 8)}...</code></td>
        <td><strong>${esc(u.username || 'Unknown')}</strong></td>
        <td>${esc(u.email || '—')}</td>
        <td><span class="status status-${status.toLowerCase()}"><div class="status-indicator"></div> ${status}</span></td>
        <td><span class="status status-${plan.toLowerCase()}">${plan}</span></td>
        <td>${joined}</td>
        <td>
          <button class="btn btn-accent btn-sm" onclick="openUserProfileModal('${u.id}')">
            <i data-lucide="eye"></i> View Profile
          </button>
        </td>
      </tr>
    `;
  }).join("");
  lucide.createIcons();
}

function switchUserTab(tab) {
  document.querySelectorAll('.user-tab-view').forEach(el => el.style.display = 'none');
  document.querySelectorAll('.user-subnav .btn').forEach(el => el.classList.remove('active'));

  document.getElementById(`${tab}-table-container`).style.display = 'block';
  document.getElementById(`tab-btn-${tab}`).classList.add('active');

  if (tab === 'devices') loadDevices();
  if (tab === 'crashes') loadCrashes();
}

// ══════════════════════════════════
// DEVICES (FIREBASE)
// ══════════════════════════════════
let allDevices = [];

async function loadDevices() {
  const tbody = document.getElementById("devices-table-body");
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';

  try {
    const snap = await getDocs(collection(db, "devices"));
    allDevices = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    renderDevices();
  } catch (e) {
    console.error(e);
    tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><div class="empty-icon"><i data-lucide="alert-circle"></i></div><h3>Failed to load Devices</h3></div></td></tr>';
    lucide.createIcons();
  }
}

function renderDevices() {
  const tbody = document.getElementById("devices-table-body");
  if (allDevices.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><div class="empty-icon"><i data-lucide="smartphone"></i></div><h3>No devices registered</h3><p>Devices map here when users sign in via the app.</p></div></td></tr>';
    lucide.createIcons();
    return;
  }

  tbody.innerHTML = allDevices.map(d => {
    let lastSeen = "—";
    if (d.lastSeen) {
      const ds = d.lastSeen.toDate ? d.lastSeen.toDate() : new Date(d.lastSeen);
      lastSeen = ds.toLocaleString();
    }
    const status = String(d.status || "secure");
    return `
      <tr>
        <td><code>${d.id}</code></td>
        <td><code>${esc(d.userId || '—')}</code></td>
        <td><strong>${esc(d.model || 'Unknown Device')}</strong></td>
        <td>${esc(d.os || '—')}</td>
        <td><span class="status status-${status.toLowerCase()}"><div class="status-indicator"></div> ${status}</span></td>
        <td>${lastSeen}</td>
      </tr>
    `;
  }).join("");
}

// ══════════════════════════════════
// CRASHLYTICS (FIREBASE)
// ══════════════════════════════════
let allCrashes = [];

async function loadCrashes() {
  const tbody = document.getElementById("crashes-table-body");
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';

  try {
    const snap = await getDocs(collection(db, "crashes"));
    allCrashes = snap.docs.map(doc => ({ id: doc.id, ...doc.data() }));
    renderCrashes();
  } catch (e) {
    console.error(e);
    tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><div class="empty-icon"><i data-lucide="alert-circle"></i></div><h3>Failed to load Crashes</h3></div></td></tr>';
    lucide.createIcons();
  }
}

function renderCrashes() {
  const tbody = document.getElementById("crashes-table-body");
  if (allCrashes.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><div class="empty-icon"><i data-lucide="activity"></i></div><h3>No app crashes detected</h3><p>Everything is stable.</p></div></td></tr>';
    lucide.createIcons();
    return;
  }

  tbody.innerHTML = allCrashes.map(c => {
    let date = "—";
    if (c.timestamp) {
      const dt = c.timestamp.toDate ? c.timestamp.toDate() : new Date(c.timestamp);
      date = dt.toLocaleString();
    }
    const status = String(c.status || "open");
    return `
      <tr>
        <td><code>${c.id.substring(0, 8)}</code></td>
        <td><strong>${esc(c.issue || 'Unknown Error')}</strong></td>
        <td>v${esc(c.appVersion || '1.0')}</td>
        <td>${c.affectedUsers || 1} users</td>
        <td><span class="status status-${status.replace(' ', '_').toLowerCase()}"><div class="status-indicator"></div> ${status}</span></td>
        <td>${date}</td>
      </tr>
    `;
  }).join("");
}





// ══════════════════════════════════
// USER PROFILE MODAL
// ══════════════════════════════════
async function openUserProfileModal(uid) {
  document.getElementById("user-profile-modal").style.display = "flex";

  // Clear old data / show loaders
  document.getElementById("upm-uid").textContent = "...";
  document.getElementById("upm-email").textContent = "...";
  document.getElementById("upm-username").textContent = "...";
  document.getElementById("upm-created").textContent = "...";
  document.getElementById("upm-2fa").textContent = "...";

  document.getElementById("upm-devices-container").innerHTML = '<div class="spinner-container" style="padding: 20px 0;"><span class="spinner"></span></div>';

  try {
    // Basic Details
    const user = allUsers.find(u => u.id === uid);
    if (user) {
      let createdStr = "Unknown";
      if (user.createdAt) {
        const d = user.createdAt.toDate ? user.createdAt.toDate() : new Date(user.createdAt);
        createdStr = d.toLocaleString();
      }
      document.getElementById("upm-uid").textContent = uid;
      document.getElementById("upm-email").textContent = user.email || "No Email";
      document.getElementById("upm-username").textContent = user.username || "No Username";
      document.getElementById("upm-created").textContent = createdStr;
      document.getElementById("upm-2fa").innerHTML = user.twoFactorEnabled ? '<span class="status status-secure">Enabled</span>' : '<span class="status status-inactive">Disabled</span>';
    }

    // Devices for this UID
    const devQ = query(collection(db, "devices"), where("userId", "==", uid));
    const devSnap = await getDocs(devQ);
    const devices = devSnap.docs.map(d => d.data());
    const devContainer = document.getElementById("upm-devices-container");

    // Update Device Count Stat
    const deviceCountEl = document.getElementById("upm-device-count");
    if (deviceCountEl) deviceCountEl.textContent = devices.length;

    if (devices.length === 0) {
      devContainer.innerHTML = '<div style="padding: 16px; color: var(--text-secondary); font-size: 13px;">No registered devices found.</div>';
    } else {
      devContainer.innerHTML = `
        <table>
          <thead><tr><th>Model</th><th>OS Version</th><th>Last Seen</th><th>Status</th></tr></thead>
          <tbody>
            ${devices.map(d => `
              <tr>
                <td><strong>${esc(d.model || 'Unknown')}</strong></td>
                <td>${esc(d.os || '—')}</td>
                <td>${d.lastSeen ? new Date(d.lastSeen).toLocaleDateString() : '—'}</td>
                <td><span class="status status-${(d.status || 'secure').toLowerCase()}">${d.status || 'Secure'}</span></td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `;
    }

  } catch (e) {
    console.error("Profile load error", e);
    toast("Failed to load full profile data.", "error");
  }
}

function closeUserProfileModal() {
  document.getElementById("user-profile-modal").style.display = "none";
}

// ══════════════════════════════════
// D1 QUESTS / TICKETS (UNCHANGED API)
// ══════════════════════════════════
let allQuests = [];
async function loadQuests() {
  const tbody = document.getElementById("quests-table-body");
  tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';
  try {
    const res = await fetch(`${API_BASE}/admin/quests`, { headers: authHeaders() });
    allQuests = await res.json();
    renderQuests();
  } catch (e) { tbody.innerHTML = '<tr><td colspan="7" class="empty-state">Failed to load quests</td></tr>'; }
}

function renderQuests() {
  const tbody = document.getElementById("quests-table-body");
  if (allQuests.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7"><div class="empty-state"><div class="empty-icon"><i data-lucide="target"></i></div><h3>No quests found</h3></div></td></tr>';
    lucide.createIcons();
    return;
  }
  tbody.innerHTML = allQuests.map(q => `
    <tr>
      <td><code>${q.id}</code></td>
      <td><strong>${esc(q.title)}</strong><br><span style="font-size:12px;color:var(--text-secondary)">${esc(q.description?.substring(0, 60))}</span></td>
      <td><span class="status status-active">${q.quest_type}</span></td>
      <td><strong>${q.xp_reward}</strong> XP</td>
      <td><code>${esc(q.action_trigger)}</code></td>
      <td><span class="status ${q.is_active ? 'status-active' : 'status-inactive'}"><div class="status-indicator"></div> ${q.is_active ? 'Active' : 'Inactive'}</span></td>
      <td>
        <div style="display:flex;gap:8px;">
        <button class="btn-icon" onclick="editQuest('${q.id}')"><i data-lucide="edit-2"></i></button>
        <button class="btn-icon" onclick="deleteQuest('${q.id}')"><i data-lucide="trash-2"></i></button>
        </div>
      </td>
    </tr>
  `).join("");
  lucide.createIcons();
}

function openQuestModal(quest = null) {
  document.getElementById("quest-modal").style.display = "flex";
  document.getElementById("quest-modal-title").textContent = quest ? "Edit Quest" : "New Quest";
  document.getElementById("quest-edit-id").value = quest ? quest.id : "";
  document.getElementById("quest-id").value = quest ? quest.id : "";
  document.getElementById("quest-title").value = quest ? quest.title : "";
  document.getElementById("quest-desc").value = quest ? quest.description : "";
  document.getElementById("quest-xp").value = quest ? quest.xp_reward : "";
  document.getElementById("quest-type").value = quest ? quest.quest_type : "DAILY";
  document.getElementById("quest-trigger").value = quest ? quest.action_trigger : "";
  document.getElementById("quest-active").value = quest ? (quest.is_active ? "1" : "0") : "1";
  document.getElementById("quest-id").disabled = !!quest;
}

function closeQuestModal() { document.getElementById("quest-modal").style.display = "none"; }

function editQuest(id) {
  const quest = allQuests.find(q => q.id === id);
  if (quest) openQuestModal(quest);
}

async function saveQuest() {
  const editId = document.getElementById("quest-edit-id").value;
  const payload = {
    id: document.getElementById("quest-id").value.trim() || undefined,
    title: document.getElementById("quest-title").value.trim(),
    description: document.getElementById("quest-desc").value.trim(),
    xp_reward: parseInt(document.getElementById("quest-xp").value, 10),
    quest_type: document.getElementById("quest-type").value,
    action_trigger: document.getElementById("quest-trigger").value.trim(),
    is_active: parseInt(document.getElementById("quest-active").value, 10),
  };

  try {
    if (editId) {
      await fetch(`${API_BASE}/admin/quests/${editId}`, { method: "PUT", headers: authHeaders(), body: JSON.stringify(payload) });
      toast("Quest updated", "success");
    } else {
      await fetch(`${API_BASE}/admin/quests`, { method: "POST", headers: authHeaders(), body: JSON.stringify(payload) });
      toast("Quest created", "success");
    }
    closeQuestModal();
    loadQuests();
  } catch (e) { toast("Failed to save quest", "error"); }
}

async function deleteQuest(id) {
  if (!confirm("Deactivate this quest?")) return;
  try {
    await fetch(`${API_BASE}/admin/quests/${id}`, { method: "DELETE", headers: authHeaders() });
    toast("Quest deactivated", "success");
    loadQuests();
  } catch (e) { toast("Failed", "error"); }
}

let allTickets = [];
async function loadTickets() {
  const tbody = document.getElementById("tickets-table-body");
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';
  try {
    const res = await fetch(`${API_BASE}/admin/tickets`, { headers: authHeaders() });
    if (!res.ok) throw new Error("API failed");
    allTickets = await res.json();
    allTickets.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));
    renderTickets();
  } catch (e) { tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state">Failed to load</div></td></tr>'; console.error(e); }
}

function renderTickets() {
  const tbody = document.getElementById("tickets-table-body");
  if (allTickets.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><div class="empty-icon"><i data-lucide="ticket"></i></div><h3>No support tickets</h3></div></td></tr>';
    lucide.createIcons();
    return;
  }
  tbody.innerHTML = allTickets.map(t => `
    <tr>
      <td><code>${esc(t.ticketId || t.id)}</code></td>
      <td>${esc(t.issue?.substring(0, 60))}...</td>
      <td>${esc(t.user_email || 'Unknown')}</td>
      <td><span class="status status-${t.status || 'open'}"><div class="status-indicator"></div> ${(t.status || 'open').replace('_', ' ')}</span></td>
      <td>${t.timestamp ? new Date(t.timestamp).toLocaleString() : "—"}</td>
      <td><button class="btn btn-accent btn-sm" onclick="openTicketModal('${t.id}')">Reply</button></td>
    </tr>
  `).join("");
  lucide.createIcons();
}

function openTicketModal(id) {
  const t = allTickets.find(x => x.id === id);
  if (!t) return;
  document.getElementById("ticket-modal").style.display = "flex";
  document.getElementById("ticket-reply-id").value = id;
  document.getElementById("ticket-issue-text").textContent = t.issue;
  document.getElementById("ticket-status").value = (t.status === "open" || !t.status) ? "in_progress" : t.status;
  document.getElementById("ticket-reply-text").value = t.admin_reply || "";
  lucide.createIcons();
}

function closeTicketModal() { document.getElementById("ticket-modal").style.display = "none"; }

async function saveTicketReply() {
  const id = document.getElementById("ticket-reply-id").value;
  const t = allTickets.find(x => x.id === id);
  const replyText = document.getElementById("ticket-reply-text").value.trim();
  const status = document.getElementById("ticket-status").value;

  if (!replyText) return toast("Please enter a reply", "error");

  const payload = {
    status: status,
    admin_reply: replyText
  };

  try {
    toast("Saving reply...", "warning");
    
    // 1. Update D1 (Worker)
    const res = await fetch(`${API_BASE}/admin/tickets/${id}`, {
      method: "PUT",
      headers: authHeaders(),
      body: JSON.stringify(payload)
    });

    if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.error || `Server failed (${res.status})`);
    }

    // 2. Send FCM targeted notification (Fire & Forget)
    if (t && t.user_email) {
      console.log("Attempting to notify user:", t.user_email);
      broadcastGenericNotification(
        "Support Ticket Updated",
        `Reply: ${replyText.substring(0, 40)}...`,
        "TICKET_UPDATE",
        t.user_email
      ).catch(e => console.warn("Background notification failed:", e));
    }

    toast("Reply saved successfully", "success");
    closeTicketModal();
    loadTickets();
  } catch (e) { 
    console.error("Save Reply Error:", e);
    toast(`Failed to save: ${e.message}`, "error"); 
  }
}

let allNotifs = [];
async function loadNotifications() {
  const tbody = document.getElementById("notifs-table-body");
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';
  try {
    const res = await fetch(`${API_BASE}/admin/notifications`, { headers: authHeaders() });
    if (!res.ok) throw new Error("API failed");
    allNotifs = await res.json();
    allNotifs.sort((a, b) => new Date(b.scheduled_at || 0) - new Date(a.scheduled_at || 0));
    renderNotifications();
  } catch (e) { tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state">Failed</div></td></tr>'; console.error(e); }
}

function renderNotifications() {
  const tbody = document.getElementById("notifs-table-body");
  if (allNotifs.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6"><div class="empty-state"><div class="empty-icon"><i data-lucide="bell"></i></div><h3>No notifications</h3></div></td></tr>';
    lucide.createIcons();
    return;
  }
  tbody.innerHTML = allNotifs.map(n => `
    <tr>
      <td><strong>${esc(n.title)}</strong></td>
      <td>${esc(n.message?.substring(0, 60))}</td>
      <td><span class="status status-active">${n.type || 'engagement'}</span></td>
      <td>${n.scheduled_at ? new Date(n.scheduled_at).toLocaleString() : "—"}</td>
      <td><span class="status status-${n.is_sent ? 'sent' : 'pending'}"><div class="status-indicator"></div> ${n.is_sent ? 'Sent' : 'Pending'}</span></td>
      <td><button class="btn-icon" onclick="deleteNotif('${n.id}')"><i data-lucide="trash-2"></i></button></td>
    </tr>
  `).join("");
  lucide.createIcons();
}

function openNotifModal() {
  document.getElementById("notif-modal").style.display = "flex";
  document.getElementById("notif-title").value = "";
  document.getElementById("notif-message").value = "";
  document.getElementById("notif-type").value = "engagement";
  document.getElementById("notif-schedule").value = new Date(Date.now() + 3600000).toISOString().slice(0, 16);
}

function closeNotifModal() { document.getElementById("notif-modal").style.display = "none"; }

async function saveNotification() {
  const payload = {
    title: document.getElementById("notif-title").value.trim(),
    message: document.getElementById("notif-message").value.trim(),
    type: document.getElementById("notif-type").value,
    scheduled_at: document.getElementById("notif-schedule").value
  };
  if (!payload.title || !payload.message || !payload.scheduled_at) return toast("Missing fields", "error");
  try {
    // 1. Save to D1 (for history)
    await fetch(`${API_BASE}/admin/notifications`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(payload)
    });

    // 2. Broadcast via FCM (for real-time push)
    toast("Broadcasting via Firebase...", "warning");
    await broadcastGenericNotification(payload.title, payload.message, payload.type);

    toast("Notification Sent & Scheduled", "success");
    closeNotifModal();
    loadNotifications();
  } catch (e) {
    toast("Failed: " + e.message, "error");
    console.error(e);
  }
}

async function broadcastGenericNotification(title, body, type, targetEmail = null) {
  const accessToken = await getFCMBearerToken();
  const fcmPayload = {
    message: {
      topic: "global_alerts",
      // Use data-only payload to force native Android background processing
      data: {
        title: title,
        body: body,
        type: type,
        target_email: targetEmail || ""
      }
    }
  };

  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${ENV.FCM_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${accessToken}`
    },
    body: JSON.stringify(fcmPayload)
  });

  if (!res.ok) {
    const err = await res.text();
    throw new Error(`FCM Error: ${err}`);
  }
}

async function deleteNotif(id) {
  if (!confirm("Delete this notification?")) return;
  try {
    await fetch(`${API_BASE}/admin/notifications/${id}`, { method: "DELETE", headers: authHeaders() });
    toast("Deleted", "success");
    loadNotifications();
  } catch (e) { toast("Failed", "error"); }
}

// ── FCM HTTP V1 OAUTH2 TOKEN GENERATOR ──
async function getFCMBearerToken() {
  const header = { alg: "RS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: ENV.FCM_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now
  };

  const utf8Encoder = new TextEncoder();
  const b64Url = (str) => btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  const encodedHeader = b64Url(String.fromCharCode(...utf8Encoder.encode(JSON.stringify(header))));
  const encodedClaim = b64Url(String.fromCharCode(...utf8Encoder.encode(JSON.stringify(claim))));
  const unsignedToken = `${encodedHeader}.${encodedClaim}`;

  // Parse PEM Private Key for Web Crypto API
  const pem = ENV.FCM_PRIVATE_KEY
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

  // Exchange JWT for Bearer Token
  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`
  });

  if (!tokenRes.ok) {
    throw new Error("Failed to authenticate with Google OAuth2");
  }

  const tokenData = await tokenRes.json();
  return tokenData.access_token;
}

// ── PUSH NOTIFICATION SENDER ──
async function sendPushNow() {
  if (!ENV.FCM_PRIVATE_KEY || !ENV.FCM_PROJECT_ID) {
    toast("Missing FCM credentials in env.js.", "error");
    return;
  }

  const title = document.getElementById("notif-title").value.trim();
  const body = document.getElementById("notif-message").value.trim();
  const imageUrl = document.getElementById("notif-image") ? document.getElementById("notif-image").value.trim() : "";
  const type = document.getElementById("notif-type").value;

  if (!title || !body) {
    toast("Title and Message are required to push", "error");
    return;
  }

  try {
    const btn = document.querySelector(".btn-accent");
    if (btn) { btn.disabled = true; btn.textContent = "Pushing..."; }

    toast("Generating OAuth2 Token...", "success");
    const accessToken = await getFCMBearerToken();

    // HTTP v1 API structure - Data Only
    const fcmPayload = {
      message: {
        topic: "global_alerts",
        data: {
          title: title,
          body: body,
          type: type,
          imageUrl: imageUrl || ""
        }
      }
    };

    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${ENV.FCM_PROJECT_ID}/messages:send`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`
      },
      body: JSON.stringify(fcmPayload)
    });

    if (!res.ok) {
      const err = await res.text();
      throw new Error(`FCM V1 API Error: ${err}`);
    }

    toast("Firebase Push Notification Sent Successfully!", "success");

    // Save to Firestore as sent
    const newDocId = "notif_" + Date.now().toString(16);
    await setDoc(doc(db, "notifications", newDocId), {
      title, message: body, imageUrl, type, scheduled_at: new Date().toISOString(), is_sent: true, timestamp: Date.now()
    });

    closeNotifModal();
    loadNotifications();
  } catch (e) {
    console.error(e);
    toast("Push Error: " + e.message, "error");
  } finally {
    const btn = document.querySelector(".btn-accent");
    if (btn) { btn.disabled = false; btn.textContent = "Push Now"; }
  }
}

// ══════════════════════════════════
// UTILITIES
// ══════════════════════════════════
function esc(str) {
  if (!str) return "";
  const d = document.createElement("div");
  d.textContent = str;
  return d.innerHTML;
}

function toast(msg, type = "success") {
  const el = document.createElement("div");
  el.className = `toast ${type}`;
  let icon = type === 'success' ? 'check-circle' : 'alert-circle';
  el.innerHTML = `<i data-lucide="${icon}"></i> <span>${msg}</span>`;
  document.body.appendChild(el);
  lucide.createIcons();
  setTimeout(() => el.remove(), 3400);
}

// ══════════════════════════════════
// MICROLEARNING FEED MANAGEMENT
// ══════════════════════════════════

let allFeedCards = [];

async function loadLabView() {
  const tbody = document.getElementById('feed-table-body');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';
  try {
    const res = await fetch(`${API_BASE}/admin/lab/feed`, { headers: authHeaders() });
    const raw = await res.json();
    // Parse options from JSON string since admin endpoint returns raw DB rows
    allFeedCards = (raw || []).map(c => ({
      ...c,
      options: typeof c.options === 'string' ? JSON.parse(c.options || '[]') : (c.options || [])
    }));
    renderFeedCards();
  } catch (e) {
    tbody.innerHTML = '<tr><td colspan="5"><div class="empty-state"><div class="empty-icon"><i data-lucide="alert-circle"></i></div><h3>Failed to load feed</h3></div></td></tr>';
    lucide.createIcons();
  }
}

function renderFeedCards() {
  const tbody = document.getElementById('feed-table-body');
  if (allFeedCards.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5"><div class="empty-state"><div class="empty-icon"><i data-lucide="book-open"></i></div><h3>No feed cards yet</h3><p>Create your first microlearning card.</p></div></td></tr>';
    lucide.createIcons();
    return;
  }
  tbody.innerHTML = allFeedCards.map(c => {
    const typeBadge = c.type === 'INFO' ? 'status-active' : c.type === 'QUIZ_BOOL' ? 'status-pending' : 'status-warning';
    return `
      <tr>
        <td><strong>${esc(c.title)}</strong><br><span style="font-size:12px;color:var(--text-secondary)">${esc(c.description?.substring(0, 60))}...</span></td>
        <td><span class="status ${typeBadge}">${c.type}</span></td>
        <td><span class="status status-active">${esc(c.category) || '—'}</span></td>
        <td><code>${esc(c.correctAnswer) || '—'}</code></td>
        <td>
          <div style="display:flex;gap:8px;">
          <button class="btn-icon" onclick="editFeedCard('${c.id}')"><i data-lucide="edit-2"></i></button>
          <button class="btn-icon" onclick="deleteFeedCard('${c.id}')"><i data-lucide="trash-2"></i></button>
          </div>
        </td>
      </tr>
    `;
  }).join("");
  lucide.createIcons();
}

function openFeedCardModal(card = null) {
  document.getElementById('feed-card-modal').style.display = 'flex';
  document.getElementById('feed-modal-title').textContent = card ? 'Edit Feed Card' : 'New Feed Card';
  document.getElementById('feed-edit-id').value = card ? card.id : '';
  document.getElementById('feed-type').value = card ? card.type : 'INFO';
  document.getElementById('feed-category').value = card ? card.category : '';
  document.getElementById('feed-title').value = card ? card.title : '';
  document.getElementById('feed-desc').value = card ? card.description : '';
  document.getElementById('feed-visual').value = card ? card.visualUrl : '';

  if (card && card.type === 'QUIZ_BOOL') {
    document.getElementById('feed-bool-correct').value = card.correctAnswer || 'SCAM';
  }
  if (card && card.type === 'QUIZ_MCQ') {
    const opts = card.options || [];
    document.getElementById('feed-opt-a').value = opts[0] || '';
    document.getElementById('feed-opt-b').value = opts[1] || '';
    document.getElementById('feed-opt-c').value = opts[2] || '';
    document.getElementById('feed-opt-d').value = opts[3] || '';
    document.getElementById('feed-mcq-correct').value = card.correctAnswer || '';
  }

  toggleFeedOptions();
  lucide.createIcons();
}

function closeFeedCardModal() {
  document.getElementById('feed-card-modal').style.display = 'none';
}

function toggleFeedOptions() {
  const type = document.getElementById('feed-type').value;
  document.getElementById('feed-bool-options').style.display = type === 'QUIZ_BOOL' ? 'block' : 'none';
  document.getElementById('feed-mcq-options').style.display = type === 'QUIZ_MCQ' ? 'block' : 'none';
}

function editFeedCard(id) {
  const card = allFeedCards.find(c => c.id === id);
  if (card) openFeedCardModal(card);
}

async function saveFeedCard() {
  const editId = document.getElementById('feed-edit-id').value;
  const type = document.getElementById('feed-type').value;

  let options = [];
  let correctAnswer = '';

  if (type === 'QUIZ_BOOL') {
    correctAnswer = document.getElementById('feed-bool-correct').value;
  } else if (type === 'QUIZ_MCQ') {
    options = [
      document.getElementById('feed-opt-a').value.trim(),
      document.getElementById('feed-opt-b').value.trim(),
      document.getElementById('feed-opt-c').value.trim(),
      document.getElementById('feed-opt-d').value.trim()
    ].filter(o => o);
    correctAnswer = document.getElementById('feed-mcq-correct').value.trim();
  }

  const payload = {
    type,
    category: document.getElementById('feed-category').value.trim(),
    title: document.getElementById('feed-title').value.trim(),
    description: document.getElementById('feed-desc').value.trim(),
    visualUrl: document.getElementById('feed-visual').value.trim(),
    options,
    correctAnswer
  };

  if (!payload.title || !payload.description) {
    toast("Title and Description are required.", "error");
    return;
  }

  try {
    if (editId) {
      await fetch(`${API_BASE}/admin/lab/feed/${editId}`, { method: 'PUT', headers: authHeaders(), body: JSON.stringify(payload) });
      toast("Card updated!", "success");
    } else {
      await fetch(`${API_BASE}/admin/lab/feed`, { method: 'POST', headers: authHeaders(), body: JSON.stringify(payload) });
      toast("Card created!", "success");
    }
    closeFeedCardModal();
    loadLabView();
  } catch (e) {
    toast("Failed to save card", "error");
  }
}

async function deleteFeedCard(id) {
  if (!confirm("Delete this feed card permanently?")) return;
  try {
    await fetch(`${API_BASE}/admin/lab/feed/${id}`, { method: 'DELETE', headers: authHeaders() });
    toast("Card deleted", "success");
    loadLabView();
  } catch (e) {
    toast("Failed to delete", "error");
  }
}

async function generateViaGroq(type) {
  if (!ENV.GROQ_API_KEY || ENV.GROQ_API_KEY.includes('your-groq-api-key-here')) {
    toast("Please configure GROQ_API_KEY in env.js", "error");
    return;
  }

  const sysPrompt = "You are an elite cybersecurity course creator for a mobile microlearning app. You output ONLY valid, parsable JSON without any markdown.";

  const promptMap = {
    'feedcard': `Generate a high-quality cybersecurity microlearning card. Randomly choose one of these types: INFO, QUIZ_BOOL, QUIZ_MCQ.
    
    - If INFO: Return {"type":"INFO","category":"<topic>","title":"<catchy title>","description":"<2-3 sentences>","visualUrl":"","options":[],"correctAnswer":""}
    - If QUIZ_BOOL: Return {"type":"QUIZ_BOOL","category":"<topic>","title":"<scenario title>","description":"<realistic scenario>","visualUrl":"","options":[],"correctAnswer":"SAFE" or "SCAM"}
    - If QUIZ_MCQ: Return {"type":"QUIZ_MCQ","category":"<topic>","title":"<question title>","description":"<the question>","visualUrl":"","options":["opt1","opt2","opt3","opt4"],"correctAnswer":"<exact match of correct option>"}
    
    Categories: Phishing, Malware, Network Security, Privacy, Social Engineering, Password Security, Mobile Security.
    Return ONLY the JSON object.`
  };

  try {
    toast("Generating via AI...", "success");
    const res = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${ENV.GROQ_API_KEY}`
      },
      body: JSON.stringify({
        model: "openai/gpt-oss-120b",
        messages: [
          { role: "system", content: sysPrompt },
          { role: "user", content: promptMap[type] || promptMap['feedcard'] }
        ],
        response_format: { type: "json_object" },
        max_tokens: 300
      })
    });

    if (!res.ok) throw new Error("Groq API failed");

    const data = await res.json();
    let content = data.choices[0].message.content;
    if (content.startsWith("\`\`\`")) {
      content = content.replace(/\`\`\`json/g, "").replace(/\`\`\`/g, "").trim();
    }
    const result = JSON.parse(content);

    // Populate the feed card modal
    document.getElementById('feed-type').value = result.type || 'INFO';
    document.getElementById('feed-category').value = result.category || '';
    document.getElementById('feed-title').value = result.title || '';
    document.getElementById('feed-desc').value = result.description || '';
    document.getElementById('feed-visual').value = result.visualUrl || '';

    toggleFeedOptions();

    if (result.type === 'QUIZ_BOOL') {
      document.getElementById('feed-bool-correct').value = result.correctAnswer || 'SCAM';
    } else if (result.type === 'QUIZ_MCQ' && result.options) {
      document.getElementById('feed-opt-a').value = result.options[0] || '';
      document.getElementById('feed-opt-b').value = result.options[1] || '';
      document.getElementById('feed-opt-c').value = result.options[2] || '';
      document.getElementById('feed-opt-d').value = result.options[3] || '';
      document.getElementById('feed-mcq-correct').value = result.correctAnswer || '';
    }

    // Open modal with the populated data
    document.getElementById('feed-card-modal').style.display = 'flex';
    document.getElementById('feed-modal-title').textContent = 'AI-Generated Card';
    document.getElementById('feed-edit-id').value = '';

    toast("AI Generation complete! Review and save.", "success");
  } catch (e) {
    console.error(e);
    toast("AI Generation failed. Check console.", "error");
  }
}

document.addEventListener("DOMContentLoaded", () => {
  lucide.createIcons();
  // Restore session: check if Firebase auth has a user persisted
  const unsubscribe = auth.onAuthStateChanged(async (user) => {
    unsubscribe(); // only run once on page load
    if (user) {
      console.log("[Auth] Session restored for", user.email);
      try {
        const freshToken = await user.getIdToken(true);
        setToken(freshToken);
        setCurrentUserEmail(user.email || "");
        applyRBAC();
        showDashboard();
      } catch (e) {
        console.error("[Auth] Session restore failed", e);
      }
    }
  });
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && document.getElementById("login-screen").style.display !== "none") {
    handleLogin();
  }
});

// ══════════════════════════════════
// BILLING & SUBSCRIPTIONS
// ══════════════════════════════════
async function loadBillingUsers() {
  const tbody = document.getElementById("billing-users-table-body");
  tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:32px"><div class="spinner-container"><span class="spinner"></span></div></td></tr>';

  try {
    const res = await fetch(`${ENV.PAYMENTS_API_BASE}/api/admin/users`, {
      method: "GET",
      headers: {
        "X-Admin-Secret-Key": "a7f9b8c2d5e4f3a1b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9"
      }
    });

    if (res.ok) {
      const data = await res.json();
      if (!data.data || data.data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3"><div class="empty-state">No users found.</div></td></tr>';
        return;
      }

      tbody.innerHTML = data.data.map(u => `
        <tr>
          <td><strong>${esc(u.email)}</strong></td>
          <td><span class="status status-active">${esc(u.tier)}</span></td>
          <td>${u.expires_at ? esc(u.expires_at) : 'Lifetime / Free'}</td>
        </tr>
      `).join("");
    } else {
      tbody.innerHTML = '<tr><td colspan="3"><div class="empty-state">Failed to load billing users</div></td></tr>';
    }
  } catch (e) {
    console.error(e);
    tbody.innerHTML = '<tr><td colspan="3"><div class="empty-state">Network Error</div></td></tr>';
  }
}

async function processGrantTier() {
  const email = document.getElementById("grant-email").value.trim();
  const tier = document.getElementById("grant-tier").value;
  const durationSelect = document.getElementById("grant-duration");
  const duration_months = durationSelect ? parseInt(durationSelect.value, 10) : 12;

  if (!email || !tier) {
    toast("Email and tier are required", "error");
    return;
  }

  if (!confirm(`Are you sure you want to grant ${tier} access to ${email} for ${duration_months} months?`)) return;

  try {
    toast("Processing grant...", "success");
    const res = await fetch(`${ENV.PAYMENTS_API_BASE}/api/admin/grant-tier`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Admin-Secret-Key": "a7f9b8c2d5e4f3a1b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9"
      },
      body: JSON.stringify({ email, tier, duration_months })
    });

    if (res.ok) {
      toast(`Successfully granted ${tier} to ${email}`, "success");
      document.getElementById("grant-email").value = "";
      loadBillingUsers();
    } else {
      const err = await res.json();
      toast(`Error: ${err.error || 'Failed'}`, "error");
    }
  } catch (e) {
    console.error(e);
    toast("Network error during grant", "error");
  }
}

async function processRefund() {
  const paymentId = document.getElementById("refund-pay-id").value.trim();
  const amountStr = document.getElementById("refund-amount").value.trim();

  if (!paymentId) {
    toast("Payment ID is required", "error");
    return;
  }

  const payload = { payment_id: paymentId };
  if (amountStr) {
    payload.amount = parseInt(amountStr, 10);
  }

  if (!confirm(`Process refund for ${paymentId}?`)) return;

  try {
    toast("Processing refund via Razorpay...", "warning");
    const res = await fetch(`${ENV.PAYMENTS_API_BASE}/api/admin/refund`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Admin-Secret-Key": "a7f9b8c2d5e4f3a1b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9" // In production this should be in Cloudflare secrets
      },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      toast(`Refund processed successfully`, "success");
      document.getElementById("refund-pay-id").value = "";
      document.getElementById("refund-amount").value = "";
    } else {
      const err = await res.json();
      toast(`Refund failed: ${err.error || 'Failed'}`, "error");
    }
  } catch (e) {
    console.error(e);
    toast("Network error during refund", "error");
  }
}

// ══════════════════════════════════
// APP UPDATES
// ══════════════════════════════════
async function publishUpdate() {
  const versionCode = parseInt(document.getElementById("update-version-code").value);
  const versionName = document.getElementById("update-version-name").value.trim();
  let downloadUrl = document.getElementById("update-download-url").value.trim();
  const releaseNotes = document.getElementById("update-release-notes").value.trim();
  const isCritical = document.getElementById("update-is-critical").checked ? 1 : 0;
  const apkFile = document.getElementById("update-apk-file").files[0];

  if (!versionCode || !versionName) {
    toast("Version code and name are required", "error");
    return;
  }

  try {
    // If a file is selected, upload it first
    if (apkFile) {
      toast("Uploading APK...", "warning");
      const uploadRes = await fetch(`${API_BASE}/admin/upload-apk`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${getToken()}`,
          "Content-Disposition": `attachment; filename="${apkFile.name}"`
        },
        body: apkFile
      });

      if (!uploadRes.ok) {
        const err = await uploadRes.json();
        throw new Error(err.error || "Upload failed");
      }

      const uploadData = await uploadRes.json();
      downloadUrl = uploadData.download_url;
      toast("APK Uploaded successfully", "success");
    }

    if (!downloadUrl) {
      toast("Download URL is required (or upload an APK)", "error");
      return;
    }

    const payload = {
      version_code: versionCode,
      version_name: versionName,
      download_url: downloadUrl,
      release_notes: releaseNotes,
      is_critical: isCritical
    };

    const res = await fetch(`${API_BASE}/admin/app/version`, {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      toast("Update published successfully", "success");
      loadUpdates();

      // Auto-send push notification for update
      try {
        toast("Broadcasting update alert...", "warning");
        await sendUpdatePush(versionName, downloadUrl, isCritical);
      } catch (e) {
        console.error("Push failed:", e);
        toast("Update saved, but push notification failed", "error");
      }

      // Clear fields
      document.getElementById("update-version-code").value = "";
      document.getElementById("update-version-name").value = "";
      document.getElementById("update-download-url").value = "";
      document.getElementById("update-apk-file").value = "";
      document.getElementById("update-release-notes").value = "";
      document.getElementById("update-is-critical").checked = false;
    } else {
      toast("Failed to publish update", "error");
    }
  } catch (e) {
    console.error(e);
    toast("Network error", "error");
  }
}

async function sendUpdatePush(versionName, downloadUrl, isCritical) {
  const accessToken = await getFCMBearerToken();
  const fcmPayload = {
    message: {
      topic: "global_alerts",
      data: {
        title: "🚀 New Update Available!",
        body: `Version ${versionName} is here! Tap to upgrade now.`,
        type: "APP_UPDATE",
        versionName: versionName,
        downloadUrl: downloadUrl,
        isCritical: isCritical.toString()
      }
    }
  };

  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${ENV.FCM_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${accessToken}`
    },
    body: JSON.stringify(fcmPayload)
  });

  if (!res.ok) {
    const err = await res.text();
    throw new Error(`FCM Error: ${err}`);
  }
}

async function loadUpdates() {
  const tbody = document.getElementById("updates-table-body");
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;"><span class="spinner"></span></td></tr>';

  try {
    const res = await fetch(`${API_BASE}/app/version`);
    if (res.ok) {
      const info = await res.json();
      if (!info || info.version_code === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">No updates found</td></tr>';
        return;
      }

      tbody.innerHTML = `
        <tr>
          <td>${info.version_code}</td>
          <td>${info.version_name || "N/A"}</td>
          <td><a href="${info.download_url}" target="_blank" class="link">Download APK</a></td>
          <td>${info.release_notes || "N/A"}</td>
          <td>${info.is_critical ? "YES" : "NO"}</td>
          <td>${info.created_at || "Just Now"}</td>
        </tr>
      `;
    }
  } catch (e) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">Failed to load</td></tr>';
  }
}


// P%P%P% BLOGS LOGIC P%P%P%
window.openBlogModal = function() {
  document.getElementById('blog-modal').style.display = 'flex';
  document.getElementById('blog-edit-id').value = '';
  document.getElementById('blog-title').value = '';
  document.getElementById('blog-slug').value = '';
  document.getElementById('blog-excerpt').value = '';
  document.getElementById('blog-content').value = '';
  document.getElementById('blog-image').value = '';
};
window.closeBlogModal = function() {
  document.getElementById('blog-modal').style.display = 'none';
};
window.saveBlog = async function() {
  const id = document.getElementById('blog-edit-id').value;
  const title = document.getElementById('blog-title').value;
  const slug = document.getElementById('blog-slug').value;
  const excerpt = document.getElementById('blog-excerpt').value;
  const content = document.getElementById('blog-content').value;
  const image = document.getElementById('blog-image').value;
  
  if(!title || !slug || !content) return alert('Title, Slug, and Content are required.');
  
  try {
    const { getFirestore, doc, setDoc, serverTimestamp } = await import('https://www.gstatic.com/firebasejs/10.9.0/firebase-firestore.js');
    const db = getFirestore();
    const docRef = doc(db, 'blogs', slug);
    await setDoc(docRef, { title, slug, excerpt, content, image, updatedAt: serverTimestamp() }, { merge: true });
    alert('Blog saved!');
    closeBlogModal();
    loadBlogs();
  } catch(e) {
    console.error(e);
    alert('Error saving blog.');
  }
};
window.loadBlogs = async function() {
  try {
    const { getFirestore, collection, getDocs } = await import('https://www.gstatic.com/firebasejs/10.9.0/firebase-firestore.js');
    const db = getFirestore();
    const snapshot = await getDocs(collection(db, 'blogs'));
    let html = '';
    snapshot.forEach(d => {
      const data = d.data();
      const dateStr = data.updatedAt ? new Date(data.updatedAt.seconds * 1000).toLocaleDateString() : '�';
      html += `<tr>
        <td><strong>${data.title || 'Untitled'}</strong></td>
        <td><code>${data.slug || d.id}</code></td>
        <td>${dateStr}</td>
        <td><button class="btn btn-ghost btn-sm" onclick="editBlog('${data.slug || d.id}')">Edit</button></td>
      </tr>`;
    });
    document.getElementById('blogs-table-body').innerHTML = html || '<tr><td colspan="4" align="center">No blogs found</td></tr>';
  } catch(e) {
    console.error(e);
  }
};window.editBlog = async function(slug) {
  try {
    const { getFirestore, doc, getDoc } = await import('https://www.gstatic.com/firebasejs/10.9.0/firebase-firestore.js');
    const db = getFirestore();
    const snapshot = await getDoc(doc(db, 'blogs', slug));
    if(snapshot.exists()) {
      const data = snapshot.data();
      openBlogModal();
      document.getElementById('blog-edit-id').value = data.slug;
      document.getElementById('blog-title').value = data.title;
      document.getElementById('blog-slug').value = data.slug;
      document.getElementById('blog-excerpt').value = data.excerpt || '';
      document.getElementById('blog-content').value = data.content || '';
      document.getElementById('blog-image').value = data.image || '';
    }
  } catch(e) { console.error(e); }
};

// Hook into navigateTo
const oldNavigate = window.navigateTo;
window.navigateTo = function(pageId) {
  if(oldNavigate) oldNavigate(pageId);
  if(pageId === 'blogs') loadBlogs();
};

