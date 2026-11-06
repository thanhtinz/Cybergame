"use strict";
// ===== Trang quản trị Thị Trấn Mặt Trời — gọi API /api/* của AdminServer =====
let TOKEN = localStorage.getItem("adminToken") || "";
let USERNAME = localStorage.getItem("adminUser") || "";

const $ = (id) => document.getElementById(id);
function toast(msg) {
  const t = $("toast");
  t.textContent = msg;
  t.classList.remove("hidden");
  setTimeout(() => t.classList.add("hidden"), 2600);
}
function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[c]));
}
function fmt(n) { return Number(n || 0).toLocaleString("vi-VN"); }

async function api(path, body) {
  const opts = { method: body ? "POST" : "GET", headers: { "X-Admin-Token": TOKEN } };
  if (body) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const data = await res.json().catch(() => ({ ok: false, error: "Phản hồi không hợp lệ" }));
  if (res.status === 401) { logout(); throw new Error(data.error || "Hết phiên đăng nhập"); }
  if (!data.ok) throw new Error(data.error || ("Lỗi " + res.status));
  return data;
}
function guard(p) { p.catch((e) => toast(e.message)); }

// ===== Đăng nhập =====
async function doLogin() {
  $("loginErr").textContent = "";
  try {
    const res = await fetch("/api/login", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: $("loginUser").value.trim(), password: $("loginPass").value })
    });
    const data = await res.json();
    if (!data.ok) throw new Error(data.error || "Đăng nhập thất bại");
    TOKEN = data.token; USERNAME = data.username;
    localStorage.setItem("adminToken", TOKEN);
    localStorage.setItem("adminUser", USERNAME);
    showApp();
  } catch (e) { $("loginErr").textContent = e.message; }
}
function logout() {
  TOKEN = ""; USERNAME = "";
  localStorage.removeItem("adminToken"); localStorage.removeItem("adminUser");
  $("app").classList.add("hidden");
  $("loginBox").classList.remove("hidden");
}
function showApp() {
  $("loginBox").classList.add("hidden");
  $("app").classList.remove("hidden");
  $("whoami").textContent = "Xin chào, " + USERNAME;
  guard(loadOverview());
}
$("loginBtn").addEventListener("click", doLogin);
$("loginPass").addEventListener("keydown", (e) => { if (e.key === "Enter") doLogin(); });
$("logoutBtn").addEventListener("click", logout);

// ===== Điều hướng =====
const LOADERS = {};
document.querySelectorAll("[data-nav]").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll("[data-nav]").forEach((b) => b.classList.remove("active"));
    btn.classList.add("active");
    document.querySelectorAll(".sec").forEach((s) => s.classList.add("hidden"));
    const name = btn.getAttribute("data-nav");
    $("sec-" + name).classList.remove("hidden");
    if (LOADERS[name]) guard(LOADERS[name]());
  });
});
function showSection(name) {
  document.querySelector('[data-nav="' + name + '"]').click();
}

// ===== Tổng quan =====
async function loadOverview() {
  const o = await api("/api/overview");
  const cards = [
    [o.users, "Tài khoản"], [o.onlineNow, "Đang online (phiên sống)"],
    [o.playersOnline, "Đang online (DB)"], [o.items, "Vật phẩm"],
    [o.itemsInShop, "Đang bán ở Cửa Hàng"], [o.npcsActive + " / " + o.npcs, "NPC đang bật / tổng"],
    [o.giftcodes, "Giftcode còn hạn"], [o.uptimeMin + " phút", "Server đã chạy"]
  ];
  $("ovCards").innerHTML = cards.map((c) =>
    '<div class="card"><div class="num">' + esc(c[0]) + '</div><div class="lbl">' + esc(c[1]) + "</div></div>").join("");
  $("ovStatus").textContent = "Thông báo server hiện tại: " + (o.notify ? '"' + o.notify + '"' : "(trống)");
}
LOADERS.overview = loadOverview;

// ===== Người chơi =====
let plPage = 1;
async function loadPlayers() {
  const q = encodeURIComponent($("plQ").value.trim());
  const o = await api("/api/players?q=" + q + "&page=" + plPage);
  const tb = document.querySelector("#plTable tbody");
  tb.innerHTML = o.players.map((p) => {
    const status = p.ban ? '<span class="badge off">Bị khoá: ' + esc(p.ban) + "</span>"
      : (p.online ? '<span class="badge on">Online</span>' : '<span class="badge info">Offline</span>');
    const banBtn = p.ban
      ? '<button class="btn small" data-unban="' + p.id + '">Mở khoá</button>'
      : '<button class="btn small danger" data-ban="' + p.id + '">Khoá</button>';
    return "<tr><td>" + p.id + "</td><td><b>" + esc(p.username) + "</b></td><td>" + status + "</td>" +
      '<td><input type="number" id="xu-' + p.id + '" value="' + p.xu + '"></td>' +
      '<td><input type="number" id="luong-' + p.id + '" value="' + p.luong + '"></td>' +
      '<td><input type="number" id="lk-' + p.id + '" value="' + p.luongKhoa + '"></td>' +
      '<td><input type="number" id="xeng-' + p.id + '" value="' + p.xeng + '"></td>' +
      '<td><input type="number" id="role-' + p.id + '" value="' + p.role + '" title="Role: -1 thường, 0/1 là admin"></td>' +
      "<td>" + esc(p.timeCreate) + "</td>" +
      '<td><button class="btn small primary" data-money="' + p.id + '">Lưu tiền + role</button>' + banBtn + "</td></tr>";
  }).join("");
  const pages = Math.max(1, Math.ceil(o.total / 20));
  $("plPageInfo").textContent = "Trang " + o.page + " / " + pages + " — tổng " + o.total + " tài khoản";
  tb.querySelectorAll("[data-money]").forEach((b) => b.addEventListener("click", () => guard(saveMoney(+b.getAttribute("data-money")))));
  tb.querySelectorAll("[data-ban]").forEach((b) => b.addEventListener("click", () => guard(banPlayer(+b.getAttribute("data-ban"), true))));
  tb.querySelectorAll("[data-unban]").forEach((b) => b.addEventListener("click", () => guard(banPlayer(+b.getAttribute("data-unban"), false))));
}
async function saveMoney(id) {
  await api("/api/players/money", {
    id: id,
    xu: +$("xu-" + id).value, luong: +$("luong-" + id).value,
    luongKhoa: +$("lk-" + id).value, xeng: +$("xeng-" + id).value
  });
  await api("/api/players/role", { id: id, role: +$("role-" + id).value });
  toast("Đã lưu người chơi " + id);
  loadPlayers();
}
async function banPlayer(id, ban) {
  let reason = "";
  if (ban) {
    reason = prompt("Lý do khoá tài khoản " + id + ":", "Vi phạm nội quy");
    if (reason === null) return;
  }
  await api("/api/players/ban", { id: id, ban: ban ? 1 : 0, reason: reason });
  toast(ban ? "Đã khoá tài khoản " + id : "Đã mở khoá tài khoản " + id);
  loadPlayers();
}
$("plSearch").addEventListener("click", () => { plPage = 1; guard(loadPlayers()); });
$("plPrev").addEventListener("click", () => { if (plPage > 1) { plPage--; guard(loadPlayers()); } });
$("plNext").addEventListener("click", () => { plPage++; guard(loadPlayers()); });
LOADERS.players = () => { plPage = 1; return loadPlayers(); };

// ===== Vật phẩm & Shop =====
let itPage = 1;
const ZORDER_NAME = { 5: "Trang sức", 10: "Quần", 20: "Áo", 30: "Mắt", 40: "Mặt", 50: "Tóc", 52: "Trang sức", 53: "Trang sức", 60: "Nón", 65: "Trang sức", 70: "Phương tiện" };
async function loadItems() {
  const q = encodeURIComponent($("itQ").value.trim());
  const sell = encodeURIComponent($("itSell").value);
  const o = await api("/api/items?q=" + q + "&sell=" + sell + "&page=" + itPage);
  const tb = document.querySelector("#itTable tbody");
  tb.innerHTML = o.items.map((p) => {
    const inShop = p.sell === 8 || p.sell === 14;
    return "<tr><td>" + p.id + "</td>" +
      '<td><input type="text" class="name-in" id="iname-' + p.id + '" value="' + esc(p.name) + '"></td>' +
      '<td><input type="number" id="icoin-' + p.id + '" value="' + p.coin + '"></td>' +
      '<td><input type="number" id="igold-' + p.id + '" value="' + p.gold + '"></td>' +
      '<td><input type="number" id="isell-' + p.id + '" value="' + p.sell + '"> ' +
      (inShop ? '<span class="badge on">Cửa Hàng</span>' : '<span class="badge info">Ẩn</span>') + "</td>" +
      '<td><input type="number" id="iday-' + p.id + '" value="' + p.expiredDay + '"></td>' +
      "<td>" + esc(ZORDER_NAME[p.zorder] || ("zOrder " + p.zorder)) + "</td>" +
      '<td><button class="btn small primary" data-isave="' + p.id + '">Lưu</button>' +
      '<button class="btn small danger" data-idel="' + p.id + '">Xoá</button></td></tr>';
  }).join("");
  const pages = Math.max(1, Math.ceil(o.total / 30));
  $("itPageInfo").textContent = "Trang " + o.page + " / " + pages + " — tổng " + fmt(o.total) + " vật phẩm";
  tb.querySelectorAll("[data-isave]").forEach((b) => b.addEventListener("click", () => guard(saveItem(+b.getAttribute("data-isave")))));
  tb.querySelectorAll("[data-idel]").forEach((b) => b.addEventListener("click", () => guard(delItem(+b.getAttribute("data-idel")))));
}
async function saveItem(id) {
  await api("/api/items/update", {
    id: id, name: $("iname-" + id).value,
    coin: +$("icoin-" + id).value, gold: +$("igold-" + id).value,
    sell: +$("isell-" + id).value, expiredDay: +$("iday-" + id).value
  });
  toast("Đã lưu vật phẩm " + id + " (game đã nạp lại)");
  loadItems();
}
async function delItem(id) {
  if (!confirm("Xoá hẳn vật phẩm " + id + " khỏi game? Người chơi đang giữ món này có thể lỗi hiển thị.")) return;
  await api("/api/items/delete", { id: id });
  toast("Đã xoá vật phẩm " + id);
  loadItems();
}
$("itSearch").addEventListener("click", () => { itPage = 1; guard(loadItems()); });
$("itPrev").addEventListener("click", () => { if (itPage > 1) { itPage--; guard(loadItems()); } });
$("itNext").addEventListener("click", () => { itPage++; guard(loadItems()); });
$("newItemBtn").addEventListener("click", () => guard((async () => {
  const tpl = +$("newItemTemplate").value;
  const name = $("newItemName").value.trim();
  if (!tpl || !name) { toast("Cần ID mẫu và tên vật phẩm mới"); return; }
  const o = await api("/api/items/create", { template: tpl, name: name });
  toast("Đã tạo vật phẩm mới ID " + o.newId);
  $("itQ").value = String(o.newId); itPage = 1; loadItems();
})()));
LOADERS.items = () => { itPage = 1; return loadItems(); };

// ===== NPC =====
async function loadNpcs() {
  const o = await api("/api/npcs");
  const tb = document.querySelector("#npcTable tbody");
  tb.innerHTML = o.npcs.map((n) =>
    "<tr><td>" + n.id + "</td>" +
    '<td><input type="text" class="name-in" id="nname-' + n.id + '" value="' + esc(n.name) + '"></td>' +
    '<td><input type="number" id="nmap-' + n.id + '" value="' + n.map + '"></td>' +
    '<td><input type="number" id="nx-' + n.id + '" value="' + n.x + '"></td>' +
    '<td><input type="number" id="ny-' + n.id + '" value="' + n.y + '"></td>' +
    '<td><label class="switch"><input type="checkbox" id="nact-' + n.id + '"' + (n.active ? " checked" : "") +
    '><span class="slider"></span></label></td>' +
    '<td><button class="btn small primary" data-nsave="' + n.id + '">Lưu</button>' +
    '<button class="btn small danger" data-ndel="' + n.id + '">Xoá</button></td></tr>').join("");
  tb.querySelectorAll("[data-nsave]").forEach((b) => b.addEventListener("click", () => guard(saveNpc(+b.getAttribute("data-nsave")))));
  tb.querySelectorAll("[data-ndel]").forEach((b) => b.addEventListener("click", () => guard(delNpc(+b.getAttribute("data-ndel")))));
}
async function saveNpc(id) {
  await api("/api/npcs/update", {
    id: id, name: $("nname-" + id).value,
    map: +$("nmap-" + id).value, x: +$("nx-" + id).value, y: +$("ny-" + id).value,
    active: $("nact-" + id).checked ? 1 : 0
  });
  toast("Đã lưu NPC " + id + " (đã nạp lại vào thế giới)");
  loadNpcs();
}
async function delNpc(id) {
  if (!confirm("Xoá hẳn NPC " + id + "?")) return;
  await api("/api/npcs/delete", { id: id });
  toast("Đã xoá NPC " + id);
  loadNpcs();
}
$("newNpcBtn").addEventListener("click", () => guard((async () => {
  const name = $("newNpcName").value.trim();
  if (!name) { toast("Cần tên NPC mới"); return; }
  const body = { name: name, map: +$("newNpcMap").value || 0, x: +$("newNpcX").value || 100, y: +$("newNpcY").value || 100 };
  const tpl = +$("newNpcTemplate").value;
  if (tpl) body.template = tpl;
  const o = await api("/api/npcs/create", body);
  toast("Đã tạo NPC mới ID " + o.newId);
  loadNpcs();
})()));
LOADERS.npcs = loadNpcs;

// ===== Vòng quay =====
async function loadWheelConfig() {
  const c = await api("/api/wheel/config");
  $("wcfgPriceXu").value = c.priceXu;
  $("wcfgPriceLuong").value = c.priceLuong;
  $("wcfgWItem").value = c.weightItem;
  $("wcfgWXu").value = c.weightXu;
  $("wcfgWXp").value = c.weightXp;
  $("wcfgWLuong").value = c.weightLuong;
  $("wcfgXuMin").value = c.xuMin;
  $("wcfgXuMax").value = c.xuMax;
  $("wcfgXpMin").value = c.xpMin;
  $("wcfgXpMax").value = c.xpMax;
  $("wcfgLuongMin").value = c.luongMin;
  $("wcfgLuongMax").value = c.luongMax;
  $("wcfgExpireDays").value = c.expireDays;
}
$("wcfgSave").addEventListener("click", () => guard((async () => {
  await api("/api/wheel/config/save", {
    priceXu: +$("wcfgPriceXu").value, priceLuong: +$("wcfgPriceLuong").value,
    weightItem: +$("wcfgWItem").value, weightXu: +$("wcfgWXu").value,
    weightXp: +$("wcfgWXp").value, weightLuong: +$("wcfgWLuong").value,
    xuMin: +$("wcfgXuMin").value, xuMax: +$("wcfgXuMax").value,
    xpMin: +$("wcfgXpMin").value, xpMax: +$("wcfgXpMax").value,
    luongMin: +$("wcfgLuongMin").value, luongMax: +$("wcfgLuongMax").value,
    expireDays: $("wcfgExpireDays").value
  });
  toast("Đã lưu cấu hình vòng quay — có hiệu lực ngay");
})()));
async function loadWheel() {
  await loadWheelConfig();
  const o = await api("/api/wheel");
  const tb = document.querySelector("#wheelTable tbody");
  tb.innerHTML = o.rows.map((r) => {
    const kinds = [];
    if (r.xu) kinds.push("Vòng Xu");
    if (r.luong) kinds.push("Vòng Lượng");
    if (r.free) kinds.push("Miễn phí");
    return "<tr><td>" + r.id + "</td>" +
      '<td><input type="number" id="witem-' + r.id + '" value="' + r.itemId + '"> ' + esc(r.itemName || "") + "</td>" +
      "<td>" + esc(kinds.join(", ") || "—") + "</td>" +
      '<td><input type="number" id="wratio-' + r.id + '" value="' + r.ratio + '"></td>' +
      '<td><button class="btn small primary" data-wsave="' + r.id + '">Lưu</button>' +
      '<button class="btn small danger" data-wdel="' + r.id + '">Xoá</button></td></tr>';
  }).join("");
  tb.querySelectorAll("[data-wsave]").forEach((b) => b.addEventListener("click", () => guard((async () => {
    const id = +b.getAttribute("data-wsave");
    await api("/api/wheel/update", { id: id, itemId: +$("witem-" + id).value, ratio: +$("wratio-" + id).value });
    toast("Đã lưu ô quà " + id + " (vòng quay đã nạp lại)");
    loadWheel();
  })())));
  tb.querySelectorAll("[data-wdel]").forEach((b) => b.addEventListener("click", () => guard((async () => {
    const id = +b.getAttribute("data-wdel");
    if (!confirm("Xoá ô quà " + id + "?")) return;
    await api("/api/wheel/delete", { id: id });
    toast("Đã xoá ô quà " + id);
    loadWheel();
  })())));
}
$("newWheelBtn").addEventListener("click", () => guard((async () => {
  const item = +$("newWheelItem").value;
  if (!item) { toast("Cần ID vật phẩm quà"); return; }
  const kind = $("newWheelKind").value;
  await api("/api/wheel/create", {
    itemId: item, ratio: +$("newWheelRatio").value || 10,
    xu: kind === "xu" ? 1 : 0, luong: kind === "luong" ? 1 : 0, free: kind === "free" ? 1 : 0
  });
  toast("Đã thêm ô quà");
  loadWheel();
})()));
LOADERS.wheel = loadWheel;

// ===== Giftcode =====
async function loadGiftcodes() {
  const o = await api("/api/giftcodes");
  const tb = document.querySelector("#gcTable tbody");
  tb.innerHTML = o.codes.map((g) =>
    "<tr><td>" + g.id + "</td><td><b>" + esc(g.code) + "</b></td>" +
    "<td><code>" + esc(g.data) + "</code></td><td>" + g.num + "</td>" +
    "<td>" + esc(g.startTime) + "</td><td>" + esc(g.endTime) + "</td>" +
    '<td><button class="btn small" data-gedit="' + g.id + '">Sửa</button>' +
    '<button class="btn small danger" data-gdel="' + g.id + '">Xoá</button></td></tr>').join("");
  tb.querySelectorAll("[data-gedit]").forEach((b) => b.addEventListener("click", () => {
    const g = o.codes.find((x) => x.id === +b.getAttribute("data-gedit"));
    if (!g) return;
    $("gcId").value = g.id; $("gcCode").value = g.code; $("gcNum").value = g.num;
    $("gcMsg").value = g.message || ""; $("gcData").value = g.data || "{}";
    $("gcStart").value = (g.startTime || "").replace(" ", "T").slice(0, 16);
    $("gcEnd").value = (g.endTime || "").replace(" ", "T").slice(0, 16);
    $("gcFormTitle").textContent = "Sửa giftcode #" + g.id;
    window.scrollTo({ top: 0, behavior: "smooth" });
  }));
  tb.querySelectorAll("[data-gdel]").forEach((b) => b.addEventListener("click", () => guard((async () => {
    const id = +b.getAttribute("data-gdel");
    if (!confirm("Xoá giftcode " + id + "?")) return;
    await api("/api/giftcodes/delete", { id: id });
    toast("Đã xoá giftcode " + id);
    loadGiftcodes();
  })())));
}
$("gcSave").addEventListener("click", () => guard((async () => {
  const body = {
    code: $("gcCode").value.trim(), num: +$("gcNum").value || 1,
    message: $("gcMsg").value, data: $("gcData").value,
    startTime: $("gcStart").value, endTime: $("gcEnd").value
  };
  const id = +$("gcId").value;
  if (id) body.id = id;
  if (!body.code) { toast("Cần mã giftcode"); return; }
  await api("/api/giftcodes/save", body);
  toast("Đã lưu giftcode " + body.code);
  $("gcReset").click();
  loadGiftcodes();
})()));
$("gcReset").addEventListener("click", () => {
  $("gcId").value = ""; $("gcCode").value = ""; $("gcNum").value = 1;
  $("gcMsg").value = ""; $("gcStart").value = ""; $("gcEnd").value = "";
  $("gcData").value = '{"xu": 0, "luong": 0, "items": [{"id": 0, "day": 0, "quantity": 1}]}';
  $("gcFormTitle").textContent = "Tạo giftcode mới";
});
$("mgSend").addEventListener("click", () => guard((async () => {
  const items = [];
  const raw = $("mgItems").value.trim();
  if (raw) {
    for (const part of raw.split(",")) {
      const bits = part.split(":").map((x) => +x.trim());
      if (bits[0] > 0) items.push({ id: bits[0], quantity: bits[1] || 1, day: bits[2] || 0 });
    }
  }
  const body = {
    to: $("mgTo").value.trim(),
    title: $("mgTitle").value.trim(),
    body: $("mgBody").value,
    xu: +$("mgXu").value || 0,
    luong: +$("mgLuong").value || 0,
    items: items
  };
  if (!body.to) { toast("Cần người nhận (tên tài khoản hoặc *)"); return; }
  if (body.to === "*" && !confirm("Gửi quà cho TẤT CẢ người chơi?")) return;
  const r = await api("/api/mail/send", body);
  toast("Đã gửi thư quà tới " + r.sent + " người chơi");
})()));
LOADERS.giftcodes = loadGiftcodes;

// ===== Sự kiện & Thông báo =====
async function loadEvents() {
  const o = await api("/api/overview");
  $("evNotify").value = o.notify || "";
}
$("evSave").addEventListener("click", () => guard((async () => {
  await api("/api/announce", { text: $("evNotify").value, broadcast: $("evBroadcast").checked ? 1 : 0 });
  toast("Đã lưu thông báo" + ($("evBroadcast").checked ? " và phát cho người đang online" : ""));
})()));
LOADERS.events = loadEvents;

// ===== Bật / Tắt tính năng =====
const FLAG_DEFS = [
  ["maintenance", "Bảo trì toàn server", "Chặn mọi đăng nhập (trừ tài khoản admin role 0/1). Người đang chơi không bị văng.", true],
  ["register", "Cho đăng ký tài khoản mới", "Tắt là người chơi mới không tạo được tài khoản.", false],
  ["shop", "Cửa Hàng (NPC Cửa Hàng + mua đồ)", "Tắt là NPC Cửa Hàng không mở menu và gói mua đồ bị chặn.", false],
  ["wheel", "Vòng quay may mắn", "Tắt là không quay được vòng quay.", false],
  ["giftcode", "Nhập giftcode", "Tắt là người chơi nhập code sẽ nhận thông báo tạm đóng.", false],
  ["farm", "Nông trại", "Tắt là mọi thao tác trong nông trại bị chặn.", false],
  ["chat", "Chat trong game", "Tắt là tin nhắn park và nhắn riêng bị bỏ qua.", false],
  ["event", "Sự kiện (NPC sự kiện + shop đổi quà)", "Tắt là Bạch/Hắc Vô Thường, Pay/Chay To Win, Saitama và shop Đổi Quà của NPC 861 không mở menu/shop. Thợ Kim Hoàn vẫn hoạt động (dịch vụ thường trực).", false]
];
async function loadFlags() {
  const o = await api("/api/flags");
  $("flagList").innerHTML = FLAG_DEFS.map((d) => {
    const on = !!o.flags[d[0]];
    const checked = d[3] ? on : on; // maintenance bật = đang bảo trì
    return '<div class="flag-row"><div class="fname">' + esc(d[1]) + '</div>' +
      '<div class="fdesc">' + esc(d[2]) + "</div>" +
      '<label class="switch"><input type="checkbox" data-flag="' + d[0] + '"' + (checked ? " checked" : "") +
      (d[3] ? ' class="danger"' : "") + '><span class="slider"></span></label></div>';
  }).join("");
  document.querySelectorAll("[data-flag]").forEach((cb) => cb.addEventListener("change", () => guard((async () => {
    await api("/api/flags/set", { name: cb.getAttribute("data-flag"), value: cb.checked ? 1 : 0 });
    toast("Đã cập nhật: " + cb.getAttribute("data-flag") + " = " + (cb.checked ? "BẬT" : "TẮT"));
  })())));
}
LOADERS.flags = loadFlags;

// ===== Khởi động =====
if (TOKEN) {
  api("/api/overview").then(() => showApp()).catch(() => { $("loginBox").classList.remove("hidden"); });
}
