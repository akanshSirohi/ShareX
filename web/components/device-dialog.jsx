"use client";

import { useEffect, useState } from "react";
import { Battery, HardDrive, Timer, Wifi, WifiOff } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { formatSize } from "@/lib/api";
import { sessionDuration } from "@/lib/display";

export function DeviceDialog({ open, onOpenChange, state, connected }) {
  const [now, setNow] = useState(0);
  useEffect(() => {
    if (!open) return;
    setNow(performance.now());
    const timer = setInterval(() => setNow(performance.now()), 1000);
    return () => clearInterval(timer);
  }, [open]);
  const elapsed = connected ? (state?.sessionElapsedMs || 0) + Math.max(0, now - (state?.receivedAt || now)) : 0;
  const total = state?.storageTotal || 0;
  const used = state?.storageUsed || 0;
  const percent = total ? Math.min(100, Math.round(used / total * 100)) : 0;
  const ConnectionIcon = connected ? Wifi : WifiOff;
  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent>
    <DialogHeader><DialogTitle>{state?.deviceName || "Your device"}</DialogTitle><DialogDescription>Current sharing session and device status.</DialogDescription></DialogHeader>
    <Badge variant="outline" className="w-fit gap-2"><ConnectionIcon className="size-3" />{connected ? "Connected" : "Disconnected"}</Badge>
    {!connected && <p className="text-sm text-muted-foreground">Device unavailable. Storage and battery show the last received details.</p>}
    <div className="flex flex-col gap-3"><div className="flex items-center gap-2 text-sm font-medium"><HardDrive className="size-4" />Shared storage</div>
      {total ? <><div role="progressbar" aria-label="Storage used" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100} className="h-2 overflow-hidden rounded-full bg-muted"><div className="h-full bg-primary" style={{width:`${percent}%`}} /></div><div className="flex justify-between gap-3 text-sm"><span>{formatSize(used)} used ({percent}%)</span><span className="text-muted-foreground">{formatSize(state.storageFree)} free</span></div><p className="text-xs text-muted-foreground">{formatSize(total)} total</p></> : <p className="text-sm text-muted-foreground">Storage information unavailable.</p>}
    </div>
    <div className="flex items-center justify-between gap-3 border-t pt-4 text-sm"><span className="flex items-center gap-2"><Battery className="size-4" />Battery</span><span>{state?.battery >= 0 ? `${state.battery}%${state.charging ? " · Charging" : " · On battery"}` : "Unavailable"}</span></div>
    <div className="flex items-center justify-between gap-3 border-t pt-4 text-sm"><span className="flex items-center gap-2"><Timer className="size-4" />Session running</span><span>{connected ? sessionDuration(elapsed) : "Sharing unavailable"}</span></div>
    <p className="text-xs text-muted-foreground">Session duration resets when ShareX sharing stops or restarts.</p>
  </DialogContent></Dialog>;
}
