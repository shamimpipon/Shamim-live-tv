/**
 * Cloudflare Playlist Manager
 * Single-file Worker: serves the dashboard UI + REST API + public M3U links.
 *
 * Requires one KV namespace binding: PLAYLISTS
 * (see wrangler.toml)
 */

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function json(data, init = {}) {
  return new Response(JSON.stringify(data), {
    ...init,
    headers: { "content-type": "application/json;charset=UTF-8", ...(init.headers || {}) },
  });
}

function slugify(name) {
  return (
    name
      .toLowerCase()
      .trim()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/(^-|-$)/g, "") || "playlist"
  );
}

function shortId() {
  return crypto.randomUUID().slice(0, 8);
}

// Parse M3U/M3U8 text into channel objects
function parseM3U(text) {
  const lines = text.split(/\r?\n/);
  const channels = [];
  let pending = null;

  for (let raw of lines) {
    const line = raw.trim();
    if (!line) continue;

    if (line.startsWith("#EXTINF")) {
      const attrsPart = line.substring(line.indexOf(":") + 1);
      const commaIdx = attrsPart.lastIndexOf(",");
      const attrsStr = commaIdx >= 0 ? attrsPart.substring(0, commaIdx) : attrsPart;
      const name = commaIdx >= 0 ? attrsPart.substring(commaIdx + 1).trim() : "Unnamed Channel";

      const logoMatch = attrsStr.match(/tvg-logo="([^"]*)"/i);
      const groupMatch = attrsStr.match(/group-title="([^"]*)"/i);

      pending = {
        id: shortId(),
        name: name || "Unnamed Channel",
        logo: logoMatch ? logoMatch[1] : "",
        group: groupMatch ? groupMatch[1] : "General",
        url: "",
        hidden: false,
      };
    } else if (line.startsWith("#")) {
      // ignore other directives (#EXTM3U, #EXTGRP, etc.)
      continue;
    } else {
      // stream URL line
      if (pending) {
        pending.url = line;
        channels.push(pending);
        pending = null;
      }
    }
  }
  return channels;
}

// Build M3U text from channel objects (only non-hidden channels included)
function buildM3U(channels) {
  const lines = ["#EXTM3U"];
  for (const ch of channels) {
    if (ch.hidden) continue;
    lines.push(
      `#EXTINF:-1 tvg-logo="${ch.logo || ""}" group-title="${ch.group || "General"}",${ch.name}`
    );
    lines.push(ch.url);
  }
  return lines.join("\n") + "\n";
}

// ---------------------------------------------------------------------------
// KV data access
// ---------------------------------------------------------------------------

async function getIndex(env) {
  const raw = await env.PLAYLISTS.get("index");
  return raw ? JSON.parse(raw) : [];
}

async function saveIndex(env, index) {
  await env.PLAYLISTS.put("index", JSON.stringify(index));
}

async function getPlaylist(env, slug) {
  const raw = await env.PLAYLISTS.get(`playlist:${slug}`);
  return raw ? JSON.parse(raw) : null;
}

async function savePlaylist(env, slug, data) {
  await env.PLAYLISTS.put(`playlist:${slug}`, JSON.stringify(data));
}

async function deletePlaylist(env, slug) {
  await env.PLAYLISTS.delete(`playlist:${slug}`);
}

// ---------------------------------------------------------------------------
// API handlers
// ---------------------------------------------------------------------------

async function handleListPlaylists(env) {
  const index = await getIndex(env);
  return json({ playlists: index });
}

async function handleCreatePlaylist(request, env, origin) {
  const body = await request.json();
  const { name, type } = body;

  if (!name || !type) {
    return json({ error: "name and type are required" }, { status: 400 });
  }

  let content = "";
  let sourceUrl = "";

  if (type === "url") {
    if (!body.url) return json({ error: "url is required for type=url" }, { status: 400 });
    sourceUrl = body.url;
    try {
      const resp = await fetch(body.url, { headers: { "User-Agent": "Mozilla/5.0 (PlaylistManager)" } });
      if (!resp.ok) throw new Error(`Fetch failed: ${resp.status}`);
      content = await resp.text();
    } catch (e) {
      return json({ error: `Could not fetch playlist URL: ${e.message}` }, { status: 400 });
    }
  } else if (type === "file") {
    if (!body.content) return json({ error: "content is required for type=file" }, { status: 400 });
    content = body.content;
  } else {
    return json({ error: "type must be 'file' or 'url'" }, { status: 400 });
  }

  const channels = parseM3U(content);
  if (channels.length === 0) {
    return json({ error: "No channels found — check the M3U content/URL" }, { status: 400 });
  }

  let slug = slugify(name);
  const index = await getIndex(env);
  if (index.find((p) => p.slug === slug)) {
    slug = `${slug}-${shortId()}`;
  }

  const playlist = {
    slug,
    name,
    type,
    sourceUrl,
    createdAt: new Date().toISOString(),
    channels,
  };

  await savePlaylist(env, slug, playlist);

  index.push({
    slug,
    name,
    type,
    channelCount: channels.length,
    createdAt: playlist.createdAt,
    workerLink: `${origin}/playlist/${slug}.m3u`,
  });
  await saveIndex(env, index);

  return json({ playlist: summarize(playlist, origin) }, { status: 201 });
}

function summarize(playlist, origin) {
  return {
    slug: playlist.slug,
    name: playlist.name,
    type: playlist.type,
    createdAt: playlist.createdAt,
    channelCount: playlist.channels.length,
    workerLink: `${origin}/playlist/${playlist.slug}.m3u`,
  };
}

async function handleGetPlaylist(env, slug, origin) {
  const playlist = await getPlaylist(env, slug);
  if (!playlist) return json({ error: "Playlist not found" }, { status: 404 });
  return json({
    ...playlist,
    workerLink: `${origin}/playlist/${playlist.slug}.m3u`,
  });
}

async function handleRenamePlaylist(request, env, slug) {
  const playlist = await getPlaylist(env, slug);
  if (!playlist) return json({ error: "Playlist not found" }, { status: 404 });
  const body = await request.json();
  if (body.name) playlist.name = body.name;
  await savePlaylist(env, slug, playlist);

  const index = await getIndex(env);
  const entry = index.find((p) => p.slug === slug);
  if (entry && body.name) entry.name = body.name;
  await saveIndex(env, index);

  return json({ ok: true, playlist });
}

async function handleDeletePlaylist(env, slug) {
  await deletePlaylist(env, slug);
  let index = await getIndex(env);
  index = index.filter((p) => p.slug !== slug);
  await saveIndex(env, index);
  return json({ ok: true });
}

async function handleEditChannel(request, env, slug, cid) {
  const playlist = await getPlaylist(env, slug);
  if (!playlist) return json({ error: "Playlist not found" }, { status: 404 });
  const ch = playlist.channels.find((c) => c.id === cid);
  if (!ch) return json({ error: "Channel not found" }, { status: 404 });

  const body = await request.json();
  for (const field of ["name", "url", "group", "logo"]) {
    if (typeof body[field] === "string") ch[field] = body[field];
  }
  await savePlaylist(env, slug, playlist);
  return json({ ok: true, channel: ch });
}

async function handleToggleChannel(env, slug, cid) {
  const playlist = await getPlaylist(env, slug);
  if (!playlist) return json({ error: "Playlist not found" }, { status: 404 });
  const ch = playlist.channels.find((c) => c.id === cid);
  if (!ch) return json({ error: "Channel not found" }, { status: 404 });

  ch.hidden = !ch.hidden;
  await savePlaylist(env, slug, playlist);
  return json({ ok: true, channel: ch });
}

async function handleDeleteChannel(env, slug, cid) {
  const playlist = await getPlaylist(env, slug);
  if (!playlist) return json({ error: "Playlist not found" }, { status: 404 });
  playlist.channels = playlist.channels.filter((c) => c.id !== cid);
  await savePlaylist(env, slug, playlist);

  const index = await getIndex(env);
  const entry = index.find((p) => p.slug === slug);
  if (entry) entry.channelCount = playlist.channels.length;
  await saveIndex(env, index);

  return json({ ok: true });
}

async function handlePublicM3U(env, slug) {
  const playlist = await getPlaylist(env, slug);
  if (!playlist) return new Response("Playlist not found", { status: 404 });
  const text = buildM3U(playlist.channels);
  return new Response(text, {
    headers: {
      "content-type": "application/vnd.apple.mpegurl;charset=UTF-8",
      "content-disposition": `inline; filename="${slug}.m3u"`,
      "cache-control": "no-cache",
    },
  });
}

// ---------------------------------------------------------------------------
// Router
// ---------------------------------------------------------------------------

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const origin = `${url.protocol}//${url.host}`;
    const path = url.pathname;
    const method = request.method;

    try {
      if (path === "/" && method === "GET") {
        return new Response(HTML_PAGE, { headers: { "content-type": "text/html;charset=UTF-8" } });
      }

      if (path === "/api/playlists" && method === "GET") {
        return await handleListPlaylists(env);
      }
      if (path === "/api/playlists" && method === "POST") {
        return await handleCreatePlaylist(request, env, origin);
      }

      let m;
      if ((m = path.match(/^\/api\/playlists\/([^/]+)$/))) {
        const slug = decodeURIComponent(m[1]);
        if (method === "GET") return await handleGetPlaylist(env, slug, origin);
        if (method === "PATCH") return await handleRenamePlaylist(request, env, slug);
        if (method === "DELETE") return await handleDeletePlaylist(env, slug);
      }

      if ((m = path.match(/^\/api\/playlists\/([^/]+)\/channels\/([^/]+)\/toggle$/))) {
        if (method === "PATCH") return await handleToggleChannel(env, decodeURIComponent(m[1]), m[2]);
      }

      if ((m = path.match(/^\/api\/playlists\/([^/]+)\/channels\/([^/]+)$/))) {
        const slug = decodeURIComponent(m[1]);
        const cid = m[2];
        if (method === "PATCH") return await handleEditChannel(request, env, slug, cid);
        if (method === "DELETE") return await handleDeleteChannel(env, slug, cid);
      }

      if ((m = path.match(/^\/playlist\/([^/]+)\.m3u$/))) {
        return await handlePublicM3U(env, decodeURIComponent(m[1]));
      }

      return json({ error: "Not found" }, { status: 404 });
    } catch (err) {
      return json({ error: err.message || "Internal error" }, { status: 500 });
    }
  },
};

// ---------------------------------------------------------------------------
// Frontend (embedded)
// ---------------------------------------------------------------------------

const HTML_PAGE = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>Playlist Manager Dashboard</title>
<style>
:root{
  --bg:#0b1220; --panel:#111a2e; --panel-2:#0f1830; --border:#1f2b45;
  --text:#e7ecf5; --muted:#8b98b3;
  --blue:#3b82f6; --blue-bg:#12213f;
  --green:#22c55e; --green-bg:#0f2a1c;
  --purple:#a855f7; --purple-bg:#22163d;
  --amber:#f59e0b; --amber-bg:#2b2110;
  --red:#ef4444; --red-bg:#2a1414;
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
}
*{box-sizing:border-box;}
body{margin:0;background:var(--bg);color:var(--text);}
.wrap{max-width:1280px;margin:0 auto;padding:28px 20px 60px;}
h1{font-size:26px;margin:0 0 4px;}
.subtitle{color:var(--muted);margin:0 0 24px;font-size:14px;}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:28px;}
@media(max-width:900px){.stats{grid-template-columns:repeat(2,1fr);}}
.stat-card{background:var(--panel);border:1px solid var(--border);border-radius:12px;padding:18px;}
.stat-card .icon{width:36px;height:36px;border-radius:9px;display:flex;align-items:center;justify-content:center;margin-bottom:10px;font-size:18px;}
.stat-card .label{font-size:13px;color:var(--muted);margin-bottom:2px;}
.stat-card .value{font-size:26px;font-weight:700;}
.stat-card .sub{font-size:12px;color:var(--muted);margin-top:2px;}
.stat-blue .icon{background:var(--blue-bg);color:var(--blue);}
.stat-green .icon{background:var(--green-bg);color:var(--green);}
.stat-purple .icon{background:var(--purple-bg);color:var(--purple);}
.stat-amber .icon{background:var(--amber-bg);color:var(--amber);}
.badge{display:inline-block;padding:2px 9px;border-radius:5px;font-size:11px;font-weight:700;}
.badge-active{background:var(--green-bg);color:var(--green);}
.section{background:var(--panel);border:1px solid var(--border);border-radius:12px;padding:20px;margin-bottom:24px;}
.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;flex-wrap:wrap;gap:10px;}
.section-head h2{font-size:18px;margin:0;}
.btn{border:none;border-radius:8px;padding:9px 14px;font-size:13px;font-weight:600;cursor:pointer;display:inline-flex;align-items:center;gap:6px;color:#fff;}
.btn-green{background:var(--green);}
.btn-blue{background:var(--blue);}
.btn-outline{background:transparent;border:1px solid var(--border);color:var(--text);}
.btn-sm{padding:6px 8px;border-radius:6px;font-size:12px;}
table{width:100%;border-collapse:collapse;font-size:13px;}
th{text-align:left;color:var(--muted);font-weight:600;padding:10px 8px;border-bottom:1px solid var(--border);white-space:nowrap;}
td{padding:10px 8px;border-bottom:1px solid var(--border);vertical-align:middle;}
tr:hover td{background:rgba(255,255,255,0.02);}
a{color:var(--blue);text-decoration:none;}
a:hover{text-decoration:underline;}
.type-file{background:var(--green-bg);color:var(--green);}
.type-url{background:var(--purple-bg);color:var(--purple);}
.actions{display:flex;gap:6px;}
.icon-btn{width:30px;height:30px;border-radius:6px;border:none;cursor:pointer;display:flex;align-items:center;justify-content:center;font-size:13px;color:#fff;}
.icon-view{background:var(--blue);}
.icon-edit{background:var(--green);}
.icon-hide{background:var(--amber);}
.icon-delete{background:var(--red);}
.icon-logo{width:34px;height:34px;border-radius:6px;object-fit:cover;background:#1c2947;}
.status-active{color:var(--green);background:var(--green-bg);padding:2px 9px;border-radius:5px;font-size:11px;font-weight:700;}
.status-hidden{color:var(--amber);background:var(--amber-bg);padding:2px 9px;border-radius:5px;font-size:11px;font-weight:700;}
.channel-header{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;margin-bottom:16px;}
.channel-header .title{display:flex;align-items:center;gap:10px;font-size:16px;font-weight:700;}
input[type=text], input[type=url], select{background:var(--panel-2);border:1px solid var(--border);color:var(--text);border-radius:7px;padding:8px 10px;font-size:13px;}
.grid-layout{display:grid;grid-template-columns:2.6fr 1fr;gap:20px;align-items:start;}
@media(max-width:980px){.grid-layout{grid-template-columns:1fr;}}
.guide-item{display:flex;gap:10px;margin-bottom:14px;}
.guide-dot{width:26px;height:26px;border-radius:6px;flex:none;display:flex;align-items:center;justify-content:center;color:#fff;font-size:12px;}
.guide-title{font-weight:700;font-size:13px;}
.guide-desc{font-size:12px;color:var(--muted);}
.pagination{display:flex;align-items:center;gap:6px;margin-top:14px;flex-wrap:wrap;}
.page-btn{background:var(--panel-2);border:1px solid var(--border);color:var(--text);border-radius:6px;padding:6px 11px;font-size:12px;cursor:pointer;}
.page-btn.active{background:var(--green);border-color:var(--green);color:#fff;}
.empty{color:var(--muted);text-align:center;padding:30px 0;font-size:13px;}
.modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;align-items:center;justify-content:center;z-index:50;padding:16px;}
.modal{background:var(--panel);border:1px solid var(--border);border-radius:12px;padding:22px;width:100%;max-width:440px;}
.modal h3{margin:0 0 14px;font-size:17px;}
.field{margin-bottom:14px;}
.field label{display:block;font-size:12px;color:var(--muted);margin-bottom:6px;}
.field input, .field textarea{width:100%;background:var(--panel-2);border:1px solid var(--border);color:var(--text);border-radius:7px;padding:9px 10px;font-size:13px;}
.field textarea{min-height:110px;font-family:monospace;}
.modal-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:6px;}
.toast{position:fixed;bottom:20px;right:20px;background:var(--panel);border:1px solid var(--border);border-radius:8px;padding:12px 16px;font-size:13px;z-index:60;box-shadow:0 4px 16px rgba(0,0,0,0.4);}
.copy-cell{display:flex;align-items:center;gap:6px;}
.mono{font-family:monospace;}
</style>
</head>
<body>
<div class="wrap">
  <h1>Playlist Manager Dashboard</h1>
  <p class="subtitle">Manage your M3U playlists, channels and Cloudflare Worker links.</p>

  <div class="stats" id="stats"></div>

  <div class="section">
    <div class="section-head">
      <h2>Playlists</h2>
      <div style="display:flex;gap:8px;">
        <button class="btn btn-green" onclick="openAddModal('file')">+ Add Playlist (File)</button>
        <button class="btn btn-blue" onclick="openAddModal('url')">+ Add Playlist (URL)</button>
        <button class="btn btn-outline" onclick="loadPlaylists()">&#8635; Refresh</button>
      </div>
    </div>
    <div id="playlistsTableWrap"></div>
  </div>

  <div id="channelSection"></div>
</div>

<div id="modalRoot"></div>

<script>
const API = "";
let playlists = [];
let currentPlaylist = null;
let currentPage = 1;
const PAGE_SIZE = 10;
let searchTerm = "";
let groupFilter = "";

function esc(s){ return (s||"").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c])); }

async function api(path, opts) {
  const res = await fetch(API + path, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || "Request failed");
  return data;
}

function toast(msg) {
  const t = document.createElement("div");
  t.className = "toast";
  t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2500);
}

function renderStats() {
  const totalChannels = playlists.reduce((s,p) => s + p.channelCount, 0);
  document.getElementById("stats").innerHTML = \`
    <div class="stat-card stat-blue"><div class="icon">&#128193;</div><div class="label">Total Playlists</div><div class="value">\${playlists.length}</div><div class="sub">Active Playlists</div></div>
    <div class="stat-card stat-green"><div class="icon">&#128250;</div><div class="label">Total Channels</div><div class="value">\${totalChannels}</div><div class="sub">Across all playlists</div></div>
    <div class="stat-card stat-purple"><div class="icon">&#128065;</div><div class="label">Playlists Tracked</div><div class="value">\${playlists.length}</div><div class="sub">With generated links</div></div>
    <div class="stat-card stat-amber"><div class="icon">&#128279;</div><div class="label">Worker Link</div><div class="value" style="font-size:18px;">Active</div><div class="sub"><span class="badge badge-active">Running</span></div></div>
  \`;
}

function renderPlaylists() {
  if (playlists.length === 0) {
    document.getElementById("playlistsTableWrap").innerHTML = '<div class="empty">No playlists yet — add one to get started.</div>';
    return;
  }
  const rows = playlists.map((p, i) => \`
    <tr>
      <td>\${i+1}</td>
      <td>\${esc(p.name)}</td>
      <td><span class="badge \${p.type === 'file' ? 'type-file' : 'type-url'}">\${p.type === 'file' ? 'File' : 'URL'}</span></td>
      <td>\${p.channelCount}</td>
      <td class="copy-cell"><a href="\${p.workerLink}" target="_blank" class="mono">\${p.workerLink}</a>
        <button class="icon-btn icon-view btn-sm" onclick="copyText('\${p.workerLink}')" title="Copy link">&#128203;</button></td>
      <td>\${new Date(p.createdAt).toLocaleDateString()}</td>
      <td class="actions">
        <button class="icon-btn icon-view" title="View channels" onclick="viewPlaylist('\${p.slug}')">&#128065;</button>
        <button class="icon-btn icon-edit" title="Rename" onclick="renamePlaylist('\${p.slug}')">&#9998;</button>
        <button class="icon-btn icon-delete" title="Delete playlist" onclick="deletePlaylist('\${p.slug}')">&#128465;</button>
      </td>
    </tr>\`).join("");
  document.getElementById("playlistsTableWrap").innerHTML = \`
    <table><thead><tr><th>#</th><th>Playlist Name</th><th>Type</th><th>Channels</th><th>Cloudflare Worker Link</th><th>Created At</th><th>Actions</th></tr></thead>
    <tbody>\${rows}</tbody></table>\`;
}

async function loadPlaylists() {
  const data = await api("/api/playlists");
  playlists = data.playlists;
  renderStats();
  renderPlaylists();
}

function copyText(text) {
  navigator.clipboard.writeText(text).then(() => toast("Link copied to clipboard"));
}

async function renamePlaylist(slug) {
  const p = playlists.find(x => x.slug === slug);
  const name = prompt("New playlist name:", p.name);
  if (!name) return;
  await api(\`/api/playlists/\${slug}\`, { method: "PATCH", headers: {"content-type":"application/json"}, body: JSON.stringify({name}) });
  toast("Playlist renamed");
  await loadPlaylists();
}

async function deletePlaylist(slug) {
  if (!confirm("Delete this playlist permanently?")) return;
  await api(\`/api/playlists/\${slug}\`, { method: "DELETE" });
  if (currentPlaylist && currentPlaylist.slug === slug) {
    currentPlaylist = null;
    document.getElementById("channelSection").innerHTML = "";
  }
  toast("Playlist deleted");
  await loadPlaylists();
}

async function viewPlaylist(slug) {
  currentPlaylist = await api(\`/api/playlists/\${slug}\`);
  currentPage = 1;
  searchTerm = "";
  groupFilter = "";
  renderChannelSection();
}

function getFilteredChannels() {
  let list = currentPlaylist.channels;
  if (groupFilter) list = list.filter(c => c.group === groupFilter);
  if (searchTerm) {
    const q = searchTerm.toLowerCase();
    list = list.filter(c => c.name.toLowerCase().includes(q) || (c.group||"").toLowerCase().includes(q));
  }
  return list;
}

function renderChannelSection() {
  if (!currentPlaylist) { document.getElementById("channelSection").innerHTML = ""; return; }
  const groups = [...new Set(currentPlaylist.channels.map(c => c.group || "General"))];
  const filtered = getFilteredChannels();
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  currentPage = Math.min(currentPage, totalPages);
  const pageItems = filtered.slice((currentPage-1)*PAGE_SIZE, currentPage*PAGE_SIZE);

  const rows = pageItems.map((c, idx) => \`
    <tr>
      <td>\${(currentPage-1)*PAGE_SIZE + idx + 1}</td>
      <td>\${c.logo ? \`<img class="icon-logo" src="\${esc(c.logo)}" onerror="this.style.visibility='hidden'">\` : '<div class="icon-logo"></div>'}</td>
      <td>\${esc(c.group || "General")}</td>
      <td>\${esc(c.name)}</td>
      <td class="mono" style="max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">\${esc(c.url)}</td>
      <td>\${c.hidden ? '<span class="status-hidden">Hidden</span>' : '<span class="status-active">Active</span>'}</td>
      <td class="actions">
        <button class="icon-btn icon-view" title="Copy URL" onclick="copyText('\${esc(c.url)}')">&#128203;</button>
        <button class="icon-btn icon-edit" title="Edit" onclick="openEditChannel('\${c.id}')">&#9998;</button>
        <button class="icon-btn icon-hide" title="\${c.hidden ? 'Unhide' : 'Hide'}" onclick="toggleChannel('\${c.id}')">&#128065;</button>
        <button class="icon-btn icon-delete" title="Delete" onclick="deleteChannel('\${c.id}')">&#128465;</button>
      </td>
    </tr>\`).join("");

  let pager = "";
  for (let i=1;i<=totalPages;i++){
    pager += \`<button class="page-btn \${i===currentPage?'active':''}" onclick="goToPage(\${i})">\${i}</button>\`;
  }

  document.getElementById("channelSection").innerHTML = \`
    <div class="section">
      <div class="channel-header">
        <div class="title">Playlist: \${esc(currentPlaylist.name)} <span class="badge type-file">\${filtered.length} / \${currentPlaylist.channels.length} Channels</span></div>
        <div style="display:flex;gap:8px;align-items:center;">
          <span class="mono" style="font-size:12px;">\${currentPlaylist.workerLink}</span>
          <button class="btn btn-outline btn-sm" onclick="copyText('\${currentPlaylist.workerLink}')">Copy Link</button>
        </div>
      </div>
      <div style="display:flex;gap:10px;margin-bottom:14px;flex-wrap:wrap;">
        <select onchange="groupFilter=this.value;currentPage=1;renderChannelSection()">
          <option value="">All Groups</option>
          \${groups.map(g => \`<option value="\${esc(g)}" \${g===groupFilter?'selected':''}>\${esc(g)}</option>\`).join("")}
        </select>
        <input type="text" placeholder="Search channel..." value="\${esc(searchTerm)}" oninput="searchTerm=this.value;currentPage=1;renderChannelSection()" style="flex:1;min-width:180px;">
      </div>
      <table><thead><tr><th>#</th><th>Logo</th><th>Group Title</th><th>Channel Name</th><th>Stream URL</th><th>Status</th><th>Actions</th></tr></thead>
      <tbody>\${rows || '<tr><td colspan="7" class="empty">No channels match.</td></tr>'}</tbody></table>
      <div class="pagination">
        <span style="color:var(--muted);font-size:12px;">Showing \${filtered.length===0?0:(currentPage-1)*PAGE_SIZE+1} to \${Math.min(currentPage*PAGE_SIZE, filtered.length)} of \${filtered.length} channels</span>
        \${pager}
      </div>
    </div>\`;
}

function goToPage(p){ currentPage = p; renderChannelSection(); }

async function toggleChannel(cid) {
  await api(\`/api/playlists/\${currentPlaylist.slug}/channels/\${cid}/toggle\`, { method: "PATCH" });
  currentPlaylist = await api(\`/api/playlists/\${currentPlaylist.slug}\`);
  renderChannelSection();
}

async function deleteChannel(cid) {
  if (!confirm("Remove this channel from the playlist?")) return;
  await api(\`/api/playlists/\${currentPlaylist.slug}/channels/\${cid}\`, { method: "DELETE" });
  currentPlaylist = await api(\`/api/playlists/\${currentPlaylist.slug}\`);
  await loadPlaylists();
  renderChannelSection();
}

function openEditChannel(cid) {
  const c = currentPlaylist.channels.find(x => x.id === cid);
  renderModal(\`
    <h3>Edit Channel</h3>
    <div class="field"><label>Channel Name</label><input id="f_name" type="text" value="\${esc(c.name)}"></div>
    <div class="field"><label>Group Title</label><input id="f_group" type="text" value="\${esc(c.group)}"></div>
    <div class="field"><label>Logo URL</label><input id="f_logo" type="text" value="\${esc(c.logo)}"></div>
    <div class="field"><label>Stream URL</label><input id="f_url" type="text" value="\${esc(c.url)}"></div>
    <div class="modal-actions">
      <button class="btn btn-outline" onclick="closeModal()">Cancel</button>
      <button class="btn btn-green" onclick="saveChannelEdit('\${cid}')">Save changes</button>
    </div>\`);
}

async function saveChannelEdit(cid) {
  const body = {
    name: document.getElementById("f_name").value,
    group: document.getElementById("f_group").value,
    logo: document.getElementById("f_logo").value,
    url: document.getElementById("f_url").value,
  };
  await api(\`/api/playlists/\${currentPlaylist.slug}/channels/\${cid}\`, { method:"PATCH", headers:{"content-type":"application/json"}, body: JSON.stringify(body) });
  closeModal();
  currentPlaylist = await api(\`/api/playlists/\${currentPlaylist.slug}\`);
  renderChannelSection();
  toast("Channel updated");
}

function openAddModal(type) {
  if (type === "file") {
    renderModal(\`
      <h3>Add Playlist from File</h3>
      <div class="field"><label>Playlist Name</label><input id="f_name" type="text" placeholder="e.g. Bangladesh TV"></div>
      <div class="field"><label>M3U File</label><input id="f_file" type="file" accept=".m3u,.m3u8,text/plain"></div>
      <div class="modal-actions">
        <button class="btn btn-outline" onclick="closeModal()">Cancel</button>
        <button class="btn btn-green" onclick="submitFilePlaylist()">Add Playlist</button>
      </div>\`);
  } else {
    renderModal(\`
      <h3>Add Playlist from URL</h3>
      <div class="field"><label>Playlist Name</label><input id="f_name" type="text" placeholder="e.g. Sports Channels"></div>
      <div class="field"><label>M3U URL</label><input id="f_url" type="url" placeholder="https://example.com/playlist.m3u"></div>
      <div class="modal-actions">
        <button class="btn btn-outline" onclick="closeModal()">Cancel</button>
        <button class="btn btn-blue" onclick="submitUrlPlaylist()">Add Playlist</button>
      </div>\`);
  }
}

async function submitFilePlaylist() {
  const name = document.getElementById("f_name").value.trim();
  const fileInput = document.getElementById("f_file");
  if (!name || !fileInput.files[0]) { toast("Name and file are required"); return; }
  const content = await fileInput.files[0].text();
  try {
    await api("/api/playlists", { method:"POST", headers:{"content-type":"application/json"}, body: JSON.stringify({ name, type:"file", content }) });
    closeModal();
    toast("Playlist added");
    await loadPlaylists();
  } catch (e) { toast(e.message); }
}

async function submitUrlPlaylist() {
  const name = document.getElementById("f_name").value.trim();
  const url = document.getElementById("f_url").value.trim();
  if (!name || !url) { toast("Name and URL are required"); return; }
  try {
    await api("/api/playlists", { method:"POST", headers:{"content-type":"application/json"}, body: JSON.stringify({ name, type:"url", url }) });
    closeModal();
    toast("Playlist added");
    await loadPlaylists();
  } catch (e) { toast(e.message); }
}

function renderModal(inner) {
  document.getElementById("modalRoot").innerHTML = \`<div class="modal-overlay" onclick="if(event.target===this)closeModal()"><div class="modal">\${inner}</div></div>\`;
}
function closeModal() { document.getElementById("modalRoot").innerHTML = ""; }

loadPlaylists();
</script>
</body>
</html>`;
