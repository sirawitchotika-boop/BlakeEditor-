// Fail-closed Markdown security checks for Original preview and V2 reader.
// Run standalone with: node app/src/test/js/markdown-sanitizer-fail-closed.test.cjs
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const editorAssets = path.resolve(__dirname, "../../main/assets/editor");
const previewScript = fs.readFileSync(path.join(editorAssets, "preview.js"), "utf8");
const readerScript = fs.readFileSync(path.resolve(__dirname, "../../main/assets/reader/reader.js"), "utf8");
const placeholder = fs.readFileSync(path.join(editorAssets, "vendor/dompurify/purify.min.js"), "utf8");
const hostile = '<img src=x onerror=alert(1)><svg onload=alert(2)></svg>';

function makeElement() {
    let html = "";
    let text = "";
    let htmlWrites = 0;
    return {
        style: {},
        scrollTop: 3,
        querySelectorAll() { return []; },
        get innerHTML() { return html; },
        set innerHTML(value) {
            html = String(value);
            text = "";
            htmlWrites++;
        },
        get textContent() { return text; },
        set textContent(value) {
            text = String(value);
            html = "";
        },
        get htmlWrites() { return htmlWrites; }
    };
}

function openPreview(options = {}) {
    const preview = makeElement();
    const editor = makeElement();
    const context = {
        window: {},
        console: { error() {} },
        document: {
            getElementById(id) {
                if (id === "preview") return preview;
                if (id === "editor") return editor;
                return null;
            }
        },
        marked: options.marked === undefined ? { parse() { return hostile; } } : options.marked
    };
    if (Object.prototype.hasOwnProperty.call(options, "purify")) {
        context.DOMPurify = options.purify;
    }
    vm.runInNewContext(previewScript, context);
    return { preview, host: context.window.PreviewHost, context };
}

function openReader(options = {}) {
    const article = makeElement();
    const context = {
        window: { scrollTo() {} },
        document: { getElementById() { return article; }, documentElement: { dataset: {} } },
        marked: { parse() { return hostile; } }
    };
    if (Object.prototype.hasOwnProperty.call(options, "purify")) {
        context.DOMPurify = options.purify;
    }
    vm.runInNewContext(readerScript, context);
    return { article, host: context.window.BlakeReader };
}

// The checked-in file cannot claim to be a usable DOMPurify implementation.
{
    const context = { window: {} };
    vm.runInNewContext(placeholder, context);
    assert.equal(context.window.DOMPurify, undefined,
        "Fallback placeholder must never impersonate HTML sanitizer");
}

// No sanitizer: parser output is never assigned to innerHTML.
{
    const { preview, host } = openPreview();
    host.show(hostile);
    assert.equal(preview.textContent, hostile);
    assert.equal(preview.htmlWrites, 0);
}
// Sanitizer failure after a previously rendered preview must clear old HTML.
{
    let shouldThrow = false;
    const { preview, host } = openPreview({
        purify: { sanitize() {
            if (shouldThrow) throw new Error("sanitizer unavailable");
            return "<p>Safe heading</p>";
        } }
    });
    host.show("safe");
    assert.equal(preview.innerHTML, "<p>Safe heading</p>");
    shouldThrow = true;
    host.render(hostile);
    assert.equal(preview.textContent, hostile);
    assert.equal(preview.innerHTML, "");
    assert.equal(preview.htmlWrites, 1);
}
// Bad sanitizer output and explicitly unsupported DOMPurify also fail closed.
for (const purify of [
    { sanitize() { return undefined; } },
    { sanitize() { return null; } },
    { isSupported: false, sanitize() { return hostile; } }
]) {
    const { preview, host } = openPreview({ purify });
    host.show(hostile);
    assert.equal(preview.textContent, hostile);
    assert.equal(preview.htmlWrites, 0);
}
// Missing parser must not pass untrusted Markdown directly as HTML.
{
    const { preview, host } = openPreview({ purify: { sanitize() { return hostile; } }, marked: null });
    host.show(hostile);
    assert.equal(preview.textContent, hostile);
    assert.equal(preview.htmlWrites, 0);
}
// A sanitizer's output is used only on an explicit successful path.
{
    const { preview, host } = openPreview({ purify: {
        sanitize(raw, options) {
            assert.equal(raw, hostile);
            assert.ok(options.USE_PROFILES.html);
            return "<p>Safe content</p>";
        }
    } });
    host.show(hostile);
    assert.equal(preview.innerHTML, "<p>Safe content</p>");
    assert.equal(preview.htmlWrites, 1);
}
// The reader also treats missing, throwing, and invalid sanitizers as plain text.
{
    const { article, host } = openReader();
    host.show(hostile);
    assert.equal(article.textContent, hostile);
    assert.equal(article.htmlWrites, 0);
}
{
    const { article, host } = openReader({ purify: { sanitize() { throw new Error("broken"); } } });
    host.show(hostile);
    assert.equal(article.textContent, hostile);
    assert.equal(article.htmlWrites, 0);
}
{
    const { article, host } = openReader({ purify: { sanitize() { return undefined; } } });
    host.show(hostile);
    assert.equal(article.textContent, hostile);
    assert.equal(article.htmlWrites, 0);
}
console.log("Markdown fail-closed security regression checks passed.");
