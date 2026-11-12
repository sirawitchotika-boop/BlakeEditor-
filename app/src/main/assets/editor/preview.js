/**
 * Blake Editor — Markdown preview.
 * Do not render parsed HTML unless a working sanitizer returned a string.
 * Missing/broken sanitizer and parser fall back to inert plain text.
 */
(function () {
    "use strict";
    const previewContainer = document.getElementById("preview");
    const editorContainer = document.getElementById("editor");

    function renderContent(markdownText) {
        if (!previewContainer) return;
        const source = String(markdownText || "");
        try {
            // This preview uses a JS-enabled WebView with an Android bridge:
            // never pass Marked's unsanitized HTML to innerHTML.
            if (typeof DOMPurify === "undefined" || !DOMPurify ||
                typeof DOMPurify.sanitize !== "function" ||
                DOMPurify.isSupported === false) {
                previewContainer.textContent = source;
                return;
            }

            let html;
            if (typeof marked !== "undefined" && marked &&
                typeof marked.parse === "function") {
                html = marked.parse(source);
            } else if (typeof marked === "function") {
                html = marked(source);
            } else {
                previewContainer.textContent = source;
                return;
            }

            const clean = DOMPurify.sanitize(html, {
                USE_PROFILES: { html: true }
            });
            if (typeof clean !== "string") {
                throw new TypeError("Markdown sanitizer returned non-text HTML");
            }
            previewContainer.innerHTML = clean;
        } catch (error) {
            console.error("Markdown preview unavailable:", error);
            // Setting textContent clears any previously rendered HTML as well.
            previewContainer.textContent = source;
        }
    }

    window.PreviewHost = {
        show: function (markdownText) {
            renderContent(markdownText);
            if (editorContainer) editorContainer.style.display = "none";
            if (previewContainer) {
                previewContainer.style.display = "block";
                previewContainer.scrollTop = 0;
            }
        },
        hide: function () {
            if (previewContainer) previewContainer.style.display = "none";
            if (editorContainer) editorContainer.style.display = "block";
        },
        render: function (markdownText) {
            renderContent(markdownText);
        }
    };
})();
