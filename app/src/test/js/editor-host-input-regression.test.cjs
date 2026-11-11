// Run with: node app/src/test/js/editor-host-input-regression.test.cjs
// This small harness checks the real editor-host.js against a fake Ace API.
// It specifically guards against reset loops that move the caret while typing.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const script = fs.readFileSync(path.resolve(
    __dirname, "../../main/assets/editor/editor-host.js"
), "utf8");

function createEditorHarness() {
    let text = "";
    let caret = 0;
    let setValueCalls = 0;
    let undoResets = 0;
    let emittedContent = [];
    const callbacks = {};
    const bridge = {
        onEditorReady() {},
        onContentChanged(value) { emittedContent.push(value); },
        onCursorPositionChanged() {},
        onScrollChanged() {}
    };
    const session = {
        setMode() {},
        setUseWrapMode() {},
        on(name, callback) { callbacks[name] = callback; },
        getUndoManager() {
            return { reset() { undoResets++; } };
        }
    };
    const editor = {
        session,
        selection: { on() {} },
        setTheme() {},
        setShowPrintMargin() {},
        setHighlightActiveLine() {},
        setOptions() {},
        setValue(value) {
            text = value;
            caret = 0; // Ace setValue() resets the caret: the exact regression.
            setValueCalls++;
            if (callbacks.change) callbacks.change();
        },
        getValue() { return text; },
        getCursorPosition() { return { row: 0, column: caret }; }
    };
    const window = { AndroidBridge: bridge };
    vm.runInNewContext(script, { ace: { edit() { return editor; } }, window, console });
    assert.ok(window.EditorHost, "EditorHost should initialize");

    return {
        host: window.EditorHost,
        setCaret(position) { caret = position; },
        getCaret() { return caret; },
        setValueCalls() { return setValueCalls; },
        undoResets() { return undoResets; },
        emittedContent() { return emittedContent.slice(); },
        simulateTyping(value) {
            text = value;
            caret = value.length;
            if (callbacks.change) callbacks.change();
        }
    };
}

{
    const h = createEditorHarness();
    h.host.setContent("hello", "text");
    assert.equal(h.setValueCalls(), 1);
    assert.equal(h.undoResets(), 1);
    h.setCaret(4);
    h.host.setContent("hello", "text");
    assert.equal(h.getCaret(), 4, "Same content must not move the cursor");
    assert.equal(h.setValueCalls(), 1);
    assert.equal(h.undoResets(), 1);
}

{
    const h = createEditorHarness();
    h.host.setContent("first line\nsecond line", "text");
    h.setCaret(9);
    h.host.setContent("first line\nsecond line", "text");
    assert.equal(h.getCaret(), 9,
        "Re-applying identical wrapped text must not jump to line start");
}

{
    const h = createEditorHarness();
    h.host.setContent("", "text");
    const typed = ["ส", "สว", "สวั", "สวัสดี", "สวัสดี 🌏", "สวัสดี ", "สวัสดี", ""];
    typed.forEach((value) => {
        h.simulateTyping(value);
        const before = h.setValueCalls();
        h.host.setContent(value, "text");
        assert.equal(h.setValueCalls(), before,
            "Echo of Thai/emoji text must not reset an active IME");
        assert.equal(h.getCaret(), value.length);
    });
    assert.equal(h.emittedContent().length, typed.length,
        "Only real editor changes should be delivered to Android");
}

{
    const h = createEditorHarness();
    h.host.setContent("old document", "text");
    h.setCaret(5);
    h.host.setContent("new document", "text");
    assert.equal(h.setValueCalls(), 2,
        "Opening a genuinely different document must still update Ace");
    assert.equal(h.getCaret(), 0);
    assert.equal(h.undoResets(), 2);
    assert.deepEqual(h.emittedContent(), [],
        "Programmatic loads must not echo back as user edits");
}

console.log("EditorHost typing/caret regression checks passed.");
