// Lưu ứng dụng vào bộ nhớ máy để mở được cả khi không có mạng.
// Có mạng: luôn hỏi máy chủ bản mới nhất (bỏ qua bộ nhớ đệm 10 phút của GitHub Pages).
const CACHE = "maychieu-v5";
const FILES = ["./", "./index.html", "./manifest.webmanifest", "./icon.svg", "./icon-192.png", "./icon-512.png", "./icon-180.png", "./icon-96.png"];

const fresh = (url) => fetch(new Request(url, { cache: "no-cache", credentials: "same-origin" }));

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(CACHE)
      .then((c) => Promise.all(FILES.map((f) => fresh(f).then((r) => r.ok && c.put(f, r)).catch(() => {}))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET" || new URL(req.url).origin !== self.location.origin) return;
  e.respondWith(
    fresh(req.url)
      .then((res) => {
        if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(req.url, copy)); }
        return res;
      })
      .catch(() => caches.match(req.url).then((r) => r || caches.match("./index.html")))
  );
});
