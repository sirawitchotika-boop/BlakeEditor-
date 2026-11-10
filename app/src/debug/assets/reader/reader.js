(function () {
  "use strict";
  const article = document.getElementById("article");
  const supported = typeof marked !== "undefined" &&
    typeof DOMPurify !== "undefined" &&
    typeof DOMPurify.sanitize === "function";

  function show(markdownText) {
    const content = String(markdownText || "");
    if (!supported) {
      article.textContent = content;
      return;
    }
    try {
      const html = marked.parse(content);
      const clean = DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true },
        FORBID_TAGS: ["img", "iframe", "object", "embed", "video", "audio", "form", "input", "button"]
      });
      article.innerHTML = clean;
      article.querySelectorAll("a").forEach(function (link) {
        link.addEventListener("click", function (event) {
          event.preventDefault();
        });
      });
      window.scrollTo(0, 0);
    } catch (_) {
      article.textContent = content;
    }
  }

  window.BlakeReader = { show: show };
}());
