"use client";

import { useEffect, useRef, useState } from "react";
import { Howl, Howler } from "howler";
import { Music, Pause, Play, Volume2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { audioFormat } from "@/lib/preview";

function timestamp(seconds) {
  const value = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
  return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, "0")}`;
}

export function AudioPlayer({ item, source, onReady, onError }) {
  const sound = useRef(null);
  const callbacks = useRef({ onReady, onError });
  callbacks.current = { onReady, onError };
  const [ready, setReady] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(0.8);
  const [seeking, setSeeking] = useState(false);
  const [playError, setPlayError] = useState("");
  const format = audioFormat(item);

  useEffect(() => {
    let active = true;
    setReady(false); setPlaying(false); setPosition(0); setDuration(0); setVolume(0.8); setPlayError("");
    if (!Howler.codecs(format)) { callbacks.current.onError(); return; }
    const player = new Howl({
      src: [source], format: [format], html5: true, preload: true, volume: 0.8,
      onload() { if (active) { setDuration(player.duration()); setReady(true); callbacks.current.onReady(); } },
      onloaderror() { if (active) callbacks.current.onError(); },
      onplay() { if (active) { setPlaying(true); setPlayError(""); } },
      onpause() { if (active) setPlaying(false); },
      onend() { if (active) { setPlaying(false); setPosition(0); } },
      onplayerror() { if (active) { setPlaying(false); setPlayError("Playback could not start. Press play to try again."); } },
    });
    sound.current = player;
    return () => { active = false; sound.current = null; player.unload(); };
  }, [source, format]);

  useEffect(() => {
    if (!playing || seeking) return;
    const timer = setInterval(() => { const seconds = sound.current?.seek(); if (typeof seconds === "number") setPosition(seconds); }, 250);
    return () => clearInterval(timer);
  }, [playing, seeking]);

  return <div className="flex w-full flex-col gap-5 p-3" aria-label="Audio player">
    <Music className="mx-auto my-4 size-12 text-muted-foreground" />
    <Slider aria-label="Audio position" value={[position]} min={0} max={Number.isFinite(duration) && duration > 0 ? duration : 1} step={0.1} disabled={!ready || !Number.isFinite(duration) || duration <= 0}
      onValueChange={([value]) => { setSeeking(true); setPosition(value); }} onValueCommit={([value]) => { sound.current?.seek(value); setSeeking(false); }} />
    <div className="flex items-center justify-between gap-4 text-xs tabular-nums text-muted-foreground"><span>{timestamp(position)}</span><span>{timestamp(duration)}</span></div>
    <div className="flex items-center justify-between gap-6">
      <Button size="icon" disabled={!ready} aria-label={playing ? "Pause audio" : "Play audio"} onClick={() => { if (sound.current?.playing()) sound.current.pause(); else sound.current?.play(); }}>{playing ? <Pause /> : <Play />}</Button>
      <div className="flex w-36 items-center gap-3"><Volume2 className="size-4 shrink-0 text-muted-foreground" /><Slider aria-label="Volume" value={[volume]} min={0} max={1} step={0.01} onValueChange={([value]) => { setVolume(value); sound.current?.volume(value); }} /></div>
    </div>
    {playError && <p role="status" className="text-sm text-muted-foreground">{playError}</p>}
  </div>;
}
