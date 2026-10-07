"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export function VideoPlayer({ item, source, onReady, onError }) {
  const [Player, setPlayer] = useState(null);
  const callbacks = useRef({ onReady, onError });
  callbacks.current = { onReady, onError };
  const element = useRef(null);
  const detach = useRef(null);

  const localAppearance = useCallback((player) => {
    const root = player?.shadowRoot;
    if (!root || root.querySelector("style[data-movi-font]")) return;
    // Movi skips its deferred Google Fonts import when this sheet already exists.
    // The ref runs before paint, so offline playback never requests a remote font.
    const style = document.createElement("style");
    style.setAttribute("data-movi-font", "");
    style.textContent = ":host { font-family: inherit; } * { font-family: inherit !important; }";
    root.appendChild(style);
  }, []);

  useEffect(() => {
    let active = true;
    // The web component requires browser APIs; keep it out of server rendering and the initial bundle.
    import("movi-player/react/slim").then((module) => { if (active) setPlayer(() => module.MoviPlayer); })
      .catch(() => { if (active) callbacks.current.onError(); });
    return () => { active = false; detach.current?.(); element.current?.pause(); };
  }, []);

  const attach = useCallback((player) => {
    detach.current?.();
    element.current = player;
    const loaded = () => callbacks.current.onReady();
    const failed = () => callbacks.current.onError();
    player.addEventListener("loadedmetadata", loaded);
    player.addEventListener("canplay", loaded);
    player.addEventListener("error", failed);
    detach.current = () => {
      player.removeEventListener("loadedmetadata", loaded);
      player.removeEventListener("canplay", loaded);
      player.removeEventListener("error", failed);
    };
    if (player.readyState >= 1) loaded();
  }, []);

  return Player ? <Player ref={localAppearance} src={source} title={item.name} controls preload="metadata" fallback="native" wasmurl="/players/movi.wasm"
    engine="native wasm" onReady={attach} className="block h-full min-h-0 w-full overflow-hidden rounded-md"
    style={{ "--movi-primary": "var(--primary)", "--movi-secondary": "var(--primary)", "--movi-radius": "var(--radius)" }} /> : <div className="h-full w-full" />;
}
