"use client";

import { useEffect, useState } from "react";
import { ArrowUpLeft, ChevronRight, Copy, Folder, FolderInput, LoaderCircle, HardDrive } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Empty, EmptyHeader, EmptyTitle, EmptyDescription } from "@/components/ui/empty";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { parentLocation, request, formatSize } from "@/lib/api";

function completionMessage(verb, count) {
  return `${verb === "Move" ? "Moved" : "Copied"} ${count} ${count === 1 ? "item" : "items"}.`;
}

export function TransferDialog({ target, onClose, onComplete, canModify }) {
  const [destination, setDestination] = useState("");
  const [folders, setFolders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [job, setJob] = useState(null);
  const [starting, setStarting] = useState(false);
  const verb = target?.mode === "move" ? "Move" : "Copy";

  useEffect(() => { setDestination(target?.parent || ""); setJob(null); setError(""); setStarting(false); }, [target]);
  useEffect(() => {
    if (!target || job) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    request("listFolders", { location: destination }, { signal: controller.signal })
      .then((result) => { if (!controller.signal.aborted) setFolders(result.items); })
      .catch((failure) => { if (!controller.signal.aborted) { setError(failure.message); setFolders([]); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [target, destination, job]);

  useEffect(() => {
    if (!job?.id || !["queued", "running"].includes(job.state)) return;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const updated = await request("fileOperationStatus", { id: job.id }, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setJob(updated);
        setError("");
        if (updated.state === "completed") { onComplete(completionMessage(verb, updated.completed.length)); onClose(); }
        else if (updated.state === "failed") { setError(updated.errors.join("\n")); onComplete(null); }
      } catch (failure) {
        if (!controller.signal.aborted) { setError("Connection lost. The operation may still be running on your device."); setJob((current) => ({ ...current })); }
      }
    }, 700);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [job, onClose, onComplete, verb]);

  async function start() {
    setStarting(true);
    setError("");
    try {
      const result = await request("transferFiles", { mode: target.mode, data: JSON.stringify(target.locations), destination });
      setJob(result);
      if (result.state === "completed") {
        onComplete(completionMessage(verb, result.completed.length));
        onClose();
      } else if (result.state === "failed") {
        setError(result.errors.join("\n"));
        onComplete(null);
      }
    } catch (failure) { setError(failure.message); }
    finally { setStarting(false); }
  }

  const running = starting || !!job && ["queued", "running"].includes(job.state);
  const canClose = !starting && (!running || !!error);
  return <Dialog open={!!target} onOpenChange={(open) => { if (!open && canClose) onClose(); }}>
    <DialogContent onEscapeKeyDown={(event) => { if (!canClose) event.preventDefault(); }} onInteractOutside={(event) => { if (!canClose) event.preventDefault(); }}>
      <DialogHeader><DialogTitle>{verb} {target?.locations.length} {target?.locations.length === 1 ? "item" : "items"}</DialogTitle><DialogDescription>{job ? "Keep this window open to follow the operation." : "Choose a destination on your device. Existing files will not be overwritten."}</DialogDescription></DialogHeader>
      {error && <Alert variant="destructive"><AlertDescription className="whitespace-pre-wrap">{error}</AlertDescription></Alert>}
      {job ? <div className="flex flex-col gap-4 py-4" role="status" aria-live="polite">
        <Progress value={job.total ? Math.min(job.processed / job.total * 100, 100) : 0} aria-label={`${verb} progress`} />
        <p className="text-sm text-muted-foreground">{job.state === "queued" ? "Waiting to start…" : job.state === "failed" ? `${job.completed.length} items completed. Review the errors above.` : `${verb === "Move" ? "Moving" : "Copying"}… ${formatSize(job.processed)} of ${formatSize(job.total)}`}</p>
      </div> : <>
        <nav aria-label="Destination folder" className="flex min-w-0 items-center gap-1 rounded-md bg-muted p-2"><Button variant="ghost" size="icon-sm" disabled={!destination || loading} aria-label="Parent destination" onClick={() => setDestination(parentLocation(destination))}><ArrowUpLeft /></Button><Button variant="ghost" size="sm" onClick={() => setDestination("")}><HardDrive data-icon="inline-start" />Storage</Button><span className="truncate text-sm text-muted-foreground">{destination && `/ ${destination}`}</span></nav>
        <div className="flex max-h-72 min-h-40 flex-col gap-1 overflow-y-auto">
          {loading ? <Skeleton className="h-24 w-full" /> : folders.length ? folders.map((folder) => <Button key={folder.location} variant="ghost" className="justify-start" onClick={() => setDestination(folder.location)}><Folder data-icon="inline-start" /><span className="min-w-0 flex-1 truncate text-left">{folder.name}</span><ChevronRight data-icon="inline-end" /></Button>) : <Empty><EmptyHeader><EmptyTitle>No subfolders</EmptyTitle><EmptyDescription>You can use this folder as the destination.</EmptyDescription></EmptyHeader></Empty>}
        </div>
      </>}
      <DialogFooter><Button variant="outline" disabled={!canClose} onClick={onClose}>{job ? "Close" : "Cancel"}</Button>{!job && <Button disabled={!canModify || loading || starting || !!error} onClick={start}>{starting ? <LoaderCircle className="animate-spin" data-icon="inline-start" /> : target?.mode === "move" ? <FolderInput data-icon="inline-start" /> : <Copy data-icon="inline-start" />}{verb} here</Button>}</DialogFooter>
    </DialogContent>
  </Dialog>;
}
