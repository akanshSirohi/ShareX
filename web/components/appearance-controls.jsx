"use client";

import { useEffect, useState } from "react";
import { themes } from "@/lib/themes";
import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";

export function AppearanceControls({ deviceTheme }) {
  const [dark, setDark] = useState(false);
  useEffect(() => {
    document.documentElement.dataset.theme = themes.some((theme) => theme.key === deviceTheme) ? deviceTheme : "default";
    // Theme selection belongs to the Android app. Remove old browser overrides.
    try { localStorage.removeItem("sharex-theme"); } catch (_) {}
    setDark(document.documentElement.classList.contains("dark"));
  }, [deviceTheme]);

  function changeMode() {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    try { localStorage.setItem("sharex-mode", next ? "dark" : "light"); } catch (_) {}
  }
  return <div className="flex shrink-0 items-center gap-2">
    <Button variant="outline" size="icon" aria-label={dark ? "Switch to light mode" : "Switch to dark mode"} onClick={changeMode}>
      {dark ? <Sun /> : <Moon />}
    </Button>
  </div>;
}
