"use client";

import { useEffect, useRef, useState } from "react";
import { Upload, X, File, LoaderCircle } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { formatSize } from "@/lib/api";
import { cn } from "@/lib/utils";

export function UploadDialog({ open, onOpenChange, location, privateMode, droppedFiles, onComplete }) {
  const [files, setFiles] = useState([]);
  const [transfer, setTransfer] = useState(null);
  const [dragging, setDragging] = useState(false);
  const xhrRef = useRef(null);
  const inputRef = useRef(null);
  useEffect(() => { if (droppedFiles?.length) setFiles(Array.from(droppedFiles)); }, [droppedFiles]);
  useEffect(() => () => xhrRef.current?.abort(), []);

  function addFiles(incoming) {
    setFiles((current) => {
      const merged = [...current];
      for (const file of incoming) {
        if (!merged.some((item) => item.name === file.name && item.size === file.size && item.lastModified === file.lastModified)) merged.push(file);
      }
      return merged;
    });
  }

  function upload() {
    if (!files.length || xhrRef.current) return;
    const form = new FormData();
    files.forEach((file, index) => form.append(`file_${index}`, file, file.name));
    const xhr = new XMLHttpRequest();
    xhrRef.current = xhr;
    const started = performance.now();
    setTransfer({ percent: 0, loaded: 0, speed: 0 });
    xhr.open("POST", `/ShareX/uploadFile?${new URLSearchParams({ location })}`);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) setTransfer({ percent: event.loaded * 100 / event.total, loaded: event.loaded, speed: event.loaded / Math.max((performance.now() - started) / 1000, 0.1) });
    };
    const finish = () => { xhrRef.current = null; setTransfer(null); };
    xhr.onload = () => {
      finish();
      if (xhr.status >= 200 && xhr.status < 300) {
        toast.success(xhr.responseText);
        setFiles([]);
        if (inputRef.current) inputRef.current.value = "";
        onComplete();
        onOpenChange(false);
      } else toast.error(xhr.responseText || "Upload failed. Try again.");
    };
    xhr.onerror = () => { finish(); toast.error("Connection lost. Try uploading again."); };
    xhr.onabort = () => { finish(); toast("Upload cancelled."); };
    xhr.send(form);
  }

  const destination = privateMode ? "Storage / ShareX" : `Storage${location ? ` / ${location.replaceAll("/", " / ")}` : ""}`;
  return <Dialog open={open} onOpenChange={(value) => { if (!xhrRef.current) onOpenChange(value); }}>
    <DialogContent className="sm:max-w-lg" onEscapeKeyDown={(event) => { if (transfer) event.preventDefault(); }} onInteractOutside={(event) => { if (transfer) event.preventDefault(); }}>
      <DialogHeader><DialogTitle>Upload files</DialogTitle><DialogDescription>Send to {destination}. Keep this page open until the upload finishes.</DialogDescription></DialogHeader>
      <FieldGroup>
        <Field data-disabled={!!transfer}>
          <FieldLabel htmlFor="upload-files" className={cn("flex min-h-36 cursor-pointer flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-6 transition-colors", dragging && "bg-accent", transfer && "pointer-events-none")}
            onDragOver={(event) => { event.preventDefault(); if (!transfer) setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => { event.preventDefault(); setDragging(false); if (!transfer) addFiles(event.dataTransfer.files); }}>
            <Upload className="size-6" /><span>Drop files here, or choose files</span><span className="text-xs text-muted-foreground">Multiple files supported</span>
          </FieldLabel>
          <Input ref={inputRef} id="upload-files" type="file" multiple className="sr-only" disabled={!!transfer} onChange={(event) => addFiles(event.target.files)} />
        </Field>
      </FieldGroup>
      {files.length > 0 && <div className="flex max-h-48 flex-col gap-2 overflow-y-auto">
        {files.map((file, index) => <div key={`${file.name}-${index}`} className="flex items-center gap-3 text-sm"><File className="size-4 shrink-0 text-muted-foreground" /><span className="min-w-0 flex-1 truncate">{file.name}</span><span className="text-xs text-muted-foreground">{formatSize(file.size)}</span><Button variant="ghost" size="icon" disabled={!!transfer} aria-label={`Remove ${file.name}`} onClick={() => setFiles((current) => current.filter((_, item) => item !== index))}><X /></Button></div>)}
      </div>}
      {transfer && <div className="flex flex-col gap-3" role="status" aria-live="polite">
        <Progress value={transfer.percent} aria-label="Upload progress" />
        <div className="flex justify-between text-xs text-muted-foreground"><span>{transfer.percent >= 100 ? "Saving on device…" : `${transfer.percent.toFixed(0)}% uploaded`}</span><span>{formatSize(transfer.loaded)} · {formatSize(transfer.speed)}/s</span></div>
      </div>}
      <DialogFooter className="items-center">
        <span className="mr-auto text-xs text-muted-foreground">{files.length} files · {formatSize(files.reduce((total, file) => total + file.size, 0))}</span>
        {transfer ? <Button variant="outline" onClick={() => xhrRef.current?.abort()}>Cancel upload</Button> : <Button onClick={upload} disabled={!files.length}><Upload data-icon="inline-start" />Upload</Button>}
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}
