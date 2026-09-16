(function () {
    if (typeof ace === 'undefined') {
        console.error('Ace editor is not loaded');
        return;
    }

    const editor = ace.edit("editor");

    editor.setTheme("ace/theme/twilight");
    editor.session.setMode("ace/mode/text");
    editor.setShowPrintMargin(false);
    editor.setHighlightActiveLine(true);
    editor.session.setUseWrapMode(true);
    editor.setOptions({
        enableBasicAutocompletion: false,
        enableLiveAutocompletion: false,
        fontSize: "14px",
        showLineNumbers: true,
        showGutter: true,
        useWorker: false
    });

    let isInternalChange = false;

    editor.session.on('change', function () {
        if (!isInternalChange && window.AndroidBridge) {
            window.AndroidBridge.onContentChanged(editor.getValue());
        }
    });

    editor.selection.on('changeCursor', function () {
        if (window.AndroidBridge) {
            const pos = editor.getCursorPosition();
            window.AndroidBridge.onCursorPositionChanged(pos.row + 1, pos.column + 1);
        }
    });

    editor.session.on('changeScrollTop', function (scrollTop) {
        if (window.AndroidBridge) {
            window.AndroidBridge.onScrollChanged(Math.round(scrollTop));
        }
    });

    window.EditorHost = {
        setContent: function (text, mode) {
            isInternalChange = true;
            editor.setValue(text || "", -1);
            if (mode) {
                editor.session.setMode("ace/mode/" + mode);
            }
            editor.session.getUndoManager().reset();
            isInternalChange = false;
        },

        setMode: function (mode) {
            editor.session.setMode("ace/mode/" + mode);
        },

        setTheme: function (theme, mode) {
            editor.setTheme("ace/theme/" + theme);
            if (mode) {
                document.body.className = "theme-" + mode;
            } else if (theme) {
                document.body.className = "theme-" + theme;
            }
        },

        setReadOnly: function (readOnly) {
            editor.setReadOnly(readOnly);
        },

        setFontSize: function (size) {
            editor.setFontSize(size + "px");
        },

        goToLine: function (line) {
            editor.gotoLine(line, 0, true);
        },

        find: function (needle, caseSensitive, wholeWord) {
            editor.find(needle, {
                caseSensitive: !!caseSensitive,
                wholeWord: !!wholeWord,
                wrap: true
            });
        },

        replace: function (needle, replacement, replaceAll) {
            if (replaceAll) {
                editor.replaceAll(replacement, {
                    needle: needle,
                    wrap: true
                });
            } else {
                editor.replace(replacement);
            }
        },

        undo: function () {
            editor.undo();
        },

        redo: function () {
            editor.redo();
        },

        getContent: function () {
            const val = editor.getValue();
            if (window.AndroidBridge) {
                window.AndroidBridge.onContentDelivered(val);
            }
            return val;
        }
    };

    if (window.AndroidBridge) {
        window.AndroidBridge.onEditorReady();
    }
})();
