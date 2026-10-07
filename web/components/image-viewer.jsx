"use client";

import { useEffect, useRef, useState } from "react";
import { Maximize, Minus, Plus, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { fitImage } from "@/lib/image-fit";

const initial = { zoom: 1, x: 0, y: 0, rotation: 0 };
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

export function ImageViewer({ item, source, onReady, onError }) {
  const viewport = useRef(null);
  const pointers = useRef(new Map());
  const current = useRef(initial);
  const [view, setView] = useState(initial);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [natural, setNatural] = useState({ width: 0, height: 0 });
  const geometry = useRef(null);
  const rotated = view.rotation % 180 !== 0;
  const width = rotated ? natural.height : natural.width;
  const height = rotated ? natural.width : natural.height;
  const fitted = width && height ? fitImage(width, height, size.width - 24, size.height - 24) : { width: 0, height: 0 };
  const scale = width ? fitted.width / width : 1;
  geometry.current = { width: fitted.width, height: fitted.height, viewport: size };

  function update(next) {
    const g = geometry.current;
    const x = Math.max(0, (g.width * next.zoom - g.viewport.width) / 2);
    const y = Math.max(0, (g.height * next.zoom - g.viewport.height) / 2);
    const bounded = { ...next, x: clamp(next.x, -x, x), y: clamp(next.y, -y, y) };
    current.current = bounded;
    setView(bounded);
  }
  function local(point) {
    const rect = viewport.current.getBoundingClientRect();
    return { x: point.x - rect.left - rect.width / 2, y: point.y - rect.top - rect.height / 2 };
  }
  function zoomTo(value, anchor = { x: 0, y: 0 }, target = anchor) {
    const previous = current.current;
    const zoom = clamp(value, 0.25, 8);
    const ratio = zoom / previous.zoom;
    update({ ...previous, zoom, x: target.x - (anchor.x - previous.x) * ratio, y: target.y - (anchor.y - previous.y) * ratio });
  }

  useEffect(() => {
    const element = viewport.current;
    const observer = new ResizeObserver(() => setSize({ width: element.clientWidth, height: element.clientHeight }));
    observer.observe(element);
    const wheel = (event) => {
      if (!geometry.current.width) return;
      event.preventDefault();
      zoomTo(current.current.zoom * Math.exp(-event.deltaY * 0.002), local({ x: event.clientX, y: event.clientY }));
    };
    element.addEventListener("wheel", wheel, { passive: false });
    return () => { observer.disconnect(); element.removeEventListener("wheel", wheel); pointers.current.clear(); };
  }, []);

  function move(event) {
    const active = pointers.current;
    if (!active.has(event.pointerId)) return;
    const before = [...active.values()];
    active.set(event.pointerId, { x: event.clientX, y: event.clientY });
    const after = [...active.values()];
    if (after.length === 1) {
      update({ ...current.current, x: current.current.x + after[0].x - before[0].x, y: current.current.y + after[0].y - before[0].y });
    } else if (after.length === 2) {
      const distance = (points) => Math.hypot(points[1].x - points[0].x, points[1].y - points[0].y);
      const midpoint = (points) => local({ x: (points[0].x + points[1].x) / 2, y: (points[0].y + points[1].y) / 2 });
      const previous = distance(before);
      if (previous > 0) zoomTo(current.current.zoom * distance(after) / previous, midpoint(before), midpoint(after));
    }
  }

  return <div className="flex h-full min-h-0 w-full flex-col overflow-hidden rounded-md border bg-background" aria-label="Image viewer">
    <div role="toolbar" aria-label="Image controls" className="flex shrink-0 items-center justify-center gap-2 border-b p-2">
      <Button variant="ghost" size="icon-sm" aria-label="Zoom out" disabled={!natural.width || view.zoom <= 0.25} onClick={() => zoomTo(current.current.zoom / 1.25)}><Minus /></Button>
      <span className="min-w-14 text-center text-xs tabular-nums" aria-live="polite">{Math.round(view.zoom * 100)}%</span>
      <Button variant="ghost" size="icon-sm" aria-label="Zoom in" disabled={!natural.width || view.zoom >= 8} onClick={() => zoomTo(current.current.zoom * 1.25)}><Plus /></Button>
      <Button variant="ghost" size="icon-sm" aria-label="Rotate image" disabled={!natural.width} onClick={() => update({ ...initial, rotation: (current.current.rotation + 90) % 360 })}><RotateCw /></Button>
      <Button variant="ghost" size="icon-sm" aria-label="Reset image view" disabled={!natural.width} onClick={() => update(initial)}><Maximize /></Button>
    </div>
    <div ref={viewport} className="relative min-h-0 flex-1 touch-none overflow-hidden bg-muted select-none" style={{ cursor: view.zoom > 1 ? "grab" : "default" }}
      onPointerDown={(event) => {
        if (!natural.width || event.button !== 0 || pointers.current.size >= 2) return;
        event.preventDefault();
        pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY });
        event.currentTarget.setPointerCapture(event.pointerId);
      }} onPointerMove={move} onPointerUp={(event) => pointers.current.delete(event.pointerId)} onPointerCancel={(event) => pointers.current.delete(event.pointerId)} onLostPointerCapture={(event) => pointers.current.delete(event.pointerId)}>
      <img src={source} alt={item.name} draggable={false} className="absolute left-1/2 top-1/2 max-w-none" style={{ width: natural.width * scale, height: natural.height * scale, visibility: natural.width ? "visible" : "hidden", transform: `translate(-50%, -50%) translate(${view.x}px, ${view.y}px) rotate(${view.rotation}deg) scale(${view.zoom})` }}
        onLoad={(event) => { setNatural({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight }); onReady(); }} onError={onError} />
    </div>
  </div>;
}
