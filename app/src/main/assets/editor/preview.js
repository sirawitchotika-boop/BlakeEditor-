/**
 * Blake Editor - Markdown Preview Pipeline
 * Parses Markdown using Marked and sanitizes HTML via DOMPurify before rendering.
 */
(function () {
    const previewContainer = document.getElementById("preview");
    const editorContainer = document.getElementById("editor");

    function renderContent(markdownText) {
        if (!previewContainer) return;
        let html = "";
        try {
            if (typeof marked !== 'undefined' && typeof marked.parse === 'function') {
                html = marked.parse(markdownText || "");
            } else if (typeof marked === 'function') {
                html = marked(markdownText || "");
            } else {
                html = escapeHtml(markdownText || "").replace(/\n/g, "<br>");
            }
        } catch (e) {
            console.error("Marked parsing error:", e);
            html = "<p style='color:red;'>Markdown parse error</p>";
        }

        try {
            if (typeof DOMPurify !== 'undefined' && typeof DOMPurify.sanitize === 'function') {
                html = DOMPurify.sanitize(html, {
                    USE_PROFILES: { html: true }
                });
            }
        } catch (e) {
            console.error("DOMPurify sanitization error:", e);
        }

        previewContainer.innerHTML = html;
    }

    function escapeHtml(text) {
        const div = document.createElement("div");
        div.textContent = text;
        return div.innerHTML;
    }

    window.PreviewHost = {
        show: function (markdownText) {
            renderContent(markdownText);
            if (editorContainer) {
                editorContainer.style.display = "none";
            }
            if (previewContainer) {
                previewContainer.style.display = "block";
                previewContainer.scrollTop = 0;
            }
        },
        hide: function () {
            if (previewContainer) {
                previewContainer.style.display = "none";
            }
            if (editorContainer) {
                editorContainer.style.display = "block";
            }
        },
        render: function (markdownText) {
            renderContent(markdownText);
        }
    };
})();
