"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { documentType } from "@/lib/preview";

const pdf = { workerSrc: "/players/pdf/build/pdf.worker.min.mjs", assetsUrl: "/players/pdf/", workerFallbackCdn: false };
const toolbarItems = { download: false };

export function DocumentViewer({ item, source, onReady, onError }) {
  const type = documentType(item);
  const [Viewer, setViewer] = useState(null);
  const [theme, setTheme] = useState("light");
  const [narrow, setNarrow] = useState(false);
  const viewer = useRef(null);
  const thumbnails = useMemo(() => ({ width: narrow ? 64 : 120 }), [narrow]);
  const callbacks = useRef({ onReady, onError });
  callbacks.current = { onReady, onError };
  useEffect(() => {
    let active = true;
    import("omni-doc-viewer/react").then((module) => { if (active) setViewer(() => module.DocViewer); })
      .catch(() => { if (active) callbacks.current.onError(); });
    const readTheme = () => setTheme(document.documentElement.classList.contains("dark") ? "dark" : "light");
    readTheme();
    const observer = new MutationObserver(readTheme);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    const screen = window.matchMedia("(max-width: 639px)");
    const readScreen = () => setNarrow(screen.matches);
    readScreen();
    screen.addEventListener("change", readScreen);
    return () => { active = false; observer.disconnect(); screen.removeEventListener("change", readScreen); };
  }, []);
  useEffect(() => {
    const frame = requestAnimationFrame(() => viewer.current?.fitWidth());
    return () => cancelAnimationFrame(frame);
  }, [narrow]);
  const loaded = useCallback(() => callbacks.current.onReady(), []);
  const failed = useCallback(() => callbacks.current.onError(), []);
  return <div className="document-preview h-full min-h-0 w-full min-w-0" aria-label="Document viewer" onClick={(event) => {
    if (event.target.closest("button[aria-label='Thumbnails']")) requestAnimationFrame(() => viewer.current?.fitWidth());
  }}>
    {Viewer && <Viewer ref={viewer} source={source} type={type} toolbar={type !== "html"} pagination thumbnails={thumbnails} height="100%" style={{ height: "100%" }} initialZoom="fit-width" theme={theme}
      pdf={pdf} toolbarItems={toolbarItems} loading={null} errorFallback={() => null} onLoad={loaded} onError={failed} />}
  </div>;
}
