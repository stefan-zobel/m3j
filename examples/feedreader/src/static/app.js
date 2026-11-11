// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT
//
// What the pages of the feed reader do in the browser. They work without it.

"use strict";

// Times in the time of the reader.
for (const t of document.querySelectorAll("time[datetime]")) {
  const d = new Date(t.getAttribute("datetime"));
  if (!isNaN(d)) {
    t.textContent = d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
    t.title = t.getAttribute("datetime");
  }
}

// A question before a button that cannot be undone.
for (const b of document.querySelectorAll("button[data-confirm]")) {
  b.addEventListener("click", (e) => {
    if (!confirm(b.dataset.confirm)) e.preventDefault();
  });
}

// News from the server: new entries have come.
const news = document.getElementById("news");
if (news && window.EventSource) {
  const events = new EventSource("/events");
  events.addEventListener("news", () => { news.hidden = false; });
  news.querySelector("a").addEventListener("click", (e) => {
    e.preventDefault();
    location.reload();
  });
}
