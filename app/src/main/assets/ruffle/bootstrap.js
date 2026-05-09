(function () {
    if (window.__ruffleBootstrapLoaded) {
        return;
    }
    window.__ruffleBootstrapLoaded = true;

    var ROOT = "/__ruffle__/";
    var PROXY_ROOT = "/__proxy__/";
    var RUFFLE_SCRIPT_URL = ROOT + "ruffle.js";

    window.RufflePlayer = window.RufflePlayer || {};
    var existingConfig = window.RufflePlayer.config || {};
    window.RufflePlayer.config = {
        autoplay: "on",
        allowFullscreen: true,
        polyfills: true,
        publicPath: ROOT,
        warnOnUnsupportedContent: false
    };

    for (var configKey in existingConfig) {
        if (Object.prototype.hasOwnProperty.call(existingConfig, configKey)) {
            window.RufflePlayer.config[configKey] = existingConfig[configKey];
        }
    }

    function ensureViewportZoom() {
        var viewport = document.querySelector("meta[name='viewport']");
        var content = "width=device-width, initial-scale=1, minimum-scale=0.5, maximum-scale=5, user-scalable=yes";
        if (viewport) {
            viewport.setAttribute("content", content);
            return;
        }

        viewport = document.createElement("meta");
        viewport.name = "viewport";
        viewport.content = content;
        (document.head || document.documentElement).appendChild(viewport);
    }

    function injectCompatibilityStyle() {
        if (document.getElementById("__ruffle_compat_style__")) {
            return;
        }

        var style = document.createElement("style");
        style.id = "__ruffle_compat_style__";
        style.textContent =
            "html,body{max-width:100%;overflow:auto !important;}" +
            "ruffle-player,ruffle-embed,ruffle-object,object,embed{" +
            "display:block !important;" +
            "margin:0 auto !important;" +
            "max-width:100% !important;" +
            "box-sizing:border-box !important;" +
            "}" +
            "ruffle-player canvas,ruffle-embed canvas,ruffle-object canvas{" +
            "image-rendering:auto;" +
            "}";
        (document.head || document.documentElement).appendChild(style);
    }

    function normalizeFlashLayout(root) {
        if (!root || !root.querySelectorAll) {
            return;
        }

        var elements = root.querySelectorAll("ruffle-player, ruffle-embed, ruffle-object, object, embed");
        for (var i = 0; i < elements.length; i += 1) {
            var element = elements[i];
            if (!element || !element.getAttribute) {
                continue;
            }

            var rawWidth = element.getAttribute("width");
            var rawHeight = element.getAttribute("height");
            var width = rawWidth ? parseFloat(String(rawWidth).replace(/[^\d.]/g, "")) : 0;
            var height = rawHeight ? parseFloat(String(rawHeight).replace(/[^\d.]/g, "")) : 0;

            if (width > 0 && height > 0) {
                element.style.width = "min(100%, " + width + "px)";
                element.style.maxHeight = "100%";
            }
        }
    }

    function toAbsoluteUrl(rawUrl) {
        if (!rawUrl) {
            return "";
        }

        try {
            return new URL(rawUrl, window.location.href).toString();
        } catch (error) {
            return rawUrl;
        }
    }

    function toProxyUrl(rawUrl) {
        var absoluteUrl = toAbsoluteUrl(rawUrl);
        if (!absoluteUrl) {
            return "";
        }

        try {
            var parsed = new URL(absoluteUrl);
            if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
                return absoluteUrl;
            }

            if (parsed.origin === window.location.origin) {
                return absoluteUrl;
            }

            if (parsed.pathname.indexOf(PROXY_ROOT) === 0 || parsed.pathname.indexOf(ROOT) === 0) {
                return absoluteUrl;
            }

            return window.location.origin
                + PROXY_ROOT
                + parsed.protocol.replace(":", "")
                + "/"
                + parsed.host
                + parsed.pathname
                + parsed.search;
        } catch (error) {
            return absoluteUrl;
        }
    }

    function isFlashElement(element) {
        if (!element || !element.tagName) {
            return false;
        }

        var tagName = element.tagName.toLowerCase();
        if (tagName !== "object" && tagName !== "embed") {
            return false;
        }

        var type = (element.getAttribute("type") || "").toLowerCase();
        var src = (
            element.getAttribute("src")
            || element.getAttribute("data")
            || ""
        ).toLowerCase();

        if (type.indexOf("application/x-shockwave-flash") !== -1) {
            return true;
        }

        if (src.indexOf(".swf") !== -1) {
            return true;
        }

        var movieParam = element.querySelector("param[name='movie'], param[name='src']");
        if (movieParam) {
            return movieParam.getAttribute("value").toLowerCase().indexOf(".swf") !== -1;
        }

        return false;
    }

    function rewriteFlashElementSource(element) {
        if (!isFlashElement(element)) {
            return;
        }

        var src = element.getAttribute("src");
        var data = element.getAttribute("data");
        if (src) {
            element.setAttribute("src", toProxyUrl(src));
        }
        if (data) {
            element.setAttribute("data", toProxyUrl(data));
        }

        var params = element.querySelectorAll("param[name='movie'], param[name='src']");
        for (var i = 0; i < params.length; i += 1) {
            var currentValue = params[i].getAttribute("value");
            if (currentValue) {
                params[i].setAttribute("value", toProxyUrl(currentValue));
            }
        }
    }

    function rewriteExistingFlash(root) {
        if (!root || !root.querySelectorAll) {
            return;
        }

        var elements = root.querySelectorAll("object, embed");
        for (var i = 0; i < elements.length; i += 1) {
            rewriteFlashElementSource(elements[i]);
        }
    }

    function observeFlashNodes() {
        if (!window.MutationObserver) {
            return;
        }

        var observer = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i += 1) {
                var addedNodes = mutations[i].addedNodes;
                for (var j = 0; j < addedNodes.length; j += 1) {
                    var node = addedNodes[j];
                    if (!node || node.nodeType !== Node.ELEMENT_NODE) {
                        continue;
                    }

                    if (isFlashElement(node)) {
                        rewriteFlashElementSource(node);
                        normalizeFlashLayout(node.parentNode || document);
                    } else {
                        rewriteExistingFlash(node);
                        normalizeFlashLayout(node);
                    }
                }
            }
        });

        observer.observe(document.documentElement || document, {
            childList: true,
            subtree: true
        });
    }

    function installTouchBridge() {
        var hoverTarget = null;
        var lastHoverX = 0;
        var lastHoverY = 0;

        function isRuffleHost(element) {
            if (!element || !element.tagName) {
                return false;
            }

            var tagName = element.tagName.toLowerCase();
            return tagName === "ruffle-player"
                || tagName === "ruffle-embed"
                || tagName === "ruffle-object";
        }

        function resolveDispatchTargetAt(x, y) {
            var element = document.elementFromPoint(x, y);
            if (!element) {
                return null;
            }

            if (element.tagName && element.tagName.toLowerCase() === "canvas") {
                return element;
            }

            var host = isRuffleHost(element)
                ? element
                : (element.closest ? element.closest("ruffle-player,ruffle-embed,ruffle-object") : null);

            if (host && host.shadowRoot) {
                return host.shadowRoot.querySelector("canvas") || host;
            }

            return element;
        }

        function dispatchPointer(target, type, x, y, button, buttons) {
            if (!target || !window.PointerEvent) {
                return false;
            }

            var event = new PointerEvent(type, {
                bubbles: true,
                cancelable: true,
                composed: true,
                clientX: x,
                clientY: y,
                pointerId: 1,
                pointerType: "mouse",
                isPrimary: true,
                button: button || 0,
                buttons: buttons || 0
            });
            return target.dispatchEvent(event);
        }

        function dispatchContextMenu(target, x, y) {
            if (!target) {
                return false;
            }

            var event = new MouseEvent("contextmenu", {
                bubbles: true,
                cancelable: true,
                composed: true,
                clientX: x,
                clientY: y,
                button: 2,
                buttons: 2
            });
            return target.dispatchEvent(event);
        }

        window.__ruffleWrapperTouchBridge = {
            hoverAt: function (x, y) {
                var target = resolveDispatchTargetAt(x, y);
                if (!target) {
                    return false;
                }

                if (hoverTarget && hoverTarget !== target) {
                    dispatchPointer(hoverTarget, "pointerleave", lastHoverX, lastHoverY, 0, 0);
                }

                if (hoverTarget !== target) {
                    dispatchPointer(target, "pointerenter", x, y, 0, 0);
                }

                dispatchPointer(target, "pointermove", x, y, 0, 0);
                hoverTarget = target;
                lastHoverX = x;
                lastHoverY = y;
                return true;
            },
            leaveHover: function () {
                if (!hoverTarget) {
                    return false;
                }

                dispatchPointer(hoverTarget, "pointerleave", lastHoverX, lastHoverY, 0, 0);
                hoverTarget = null;
                return true;
            },
            contextMenuAt: function (x, y) {
                var target = resolveDispatchTargetAt(x, y);
                if (!target) {
                    return false;
                }

                this.hoverAt(x, y);
                dispatchContextMenu(target, x, y);
                return true;
            }
        };
    }

    function onRuffleReady() {
        if (window.RufflePlayer && typeof window.RufflePlayer.polyfill === "function") {
            try {
                window.RufflePlayer.polyfill();
                window.setTimeout(function () {
                    normalizeFlashLayout(document);
                }, 50);
                window.setTimeout(function () {
                    normalizeFlashLayout(document);
                }, 300);
            } catch (error) {
                console.error("Ruffle polyfill failed:", error);
            }
        }
    }

    function loadRuffle() {
        ensureViewportZoom();
        injectCompatibilityStyle();
        installTouchBridge();
        rewriteExistingFlash(document);
        normalizeFlashLayout(document);
        observeFlashNodes();

        if (window.RufflePlayer && typeof window.RufflePlayer.newest === "function") {
            onRuffleReady();
            return;
        }

        var existingScript = document.querySelector("script[data-ruffle-loader='1']");
        if (existingScript) {
            existingScript.addEventListener("load", onRuffleReady, { once: true });
            return;
        }

        var script = document.createElement("script");
        script.src = RUFFLE_SCRIPT_URL;
        script.async = false;
        script.dataset.ruffleLoader = "1";
        script.onload = onRuffleReady;
        script.onerror = function (error) {
            console.error("Unable to load Ruffle runtime:", error);
        };

        (document.head || document.documentElement).appendChild(script);
    }

    window.addEventListener("resize", function () {
        normalizeFlashLayout(document);
    });

    loadRuffle();
})();
