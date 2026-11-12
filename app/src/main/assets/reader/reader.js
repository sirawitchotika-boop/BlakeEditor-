(function () {
  "use strict";
  const article = document.getElementById("article");
  const supported = typeof marked !== "undefined" &&
    marked && typeof marked.parse === "function" &&
    typeof DOMPurify !== "undefined" && DOMPurify &&
    typeof DOMPurify.sanitize === "function" &&
    DOMPurify.isSupported !== false;

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
      if (typeof clean !== "string") {
        throw new TypeError("Markdown sanitizer returned non-text HTML");
      }
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

  window.BlakeReader = {
    show: show,
    setTheme: function(mode) {
      const themes = ["light", "dark", "amoled", "eye"];
      document.documentElement.dataset.theme =
        themes.includes(mode) ? mode : "dark";
    }
  };
}());
