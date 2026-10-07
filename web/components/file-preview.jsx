"use client";

import { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { Download, FileQuestion, LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty";
import { actionUrl, formatSize } from "@/lib/api";
import { previewKind } from "@/lib/preview";
import { cn } from "@/lib/utils";

// Load only the selected preview's code. File browsing never loads player libraries.
const AudioPlayer = dynamic(() => import("@/components/audio-player").then((module) => module.AudioPlayer), { ssr: false });
const VideoPlayer = dynamic(() => import("@/components/video-player").then((module) => module.VideoPlayer), { ssr: false });
const DocumentViewer = dynamic(() => import("@/components/document-viewer").then((module) => module.DocumentViewer), { ssr: false });
const ImageViewer = dynamic(() => import("@/components/image-viewer").then((module) => module.ImageViewer), { ssr: false });

export function FilePreview({ item, onClose, downloadUrl }) {
  const kind = previewKind(item);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setFailed(false);
    setLoading(true);
  }, [item, kind]);
  const available = !!kind && !failed;
  const expanded = available && kind !== "audio";
  const source = item ? actionUrl("previewFile", { location: item.location }) : "";
  const extension = item?.name?.lastIndexOf(".") > 0 ? item.name.split(".").pop().toUpperCase() : "";
  function loaded() { setLoading(false); }
  function unavailable() { setFailed(true); setLoading(false); }
  return <Dialog open={!!item} onOpenChange={(open) => { if (!open) onClose(); }}>
    <DialogContent className={cn("max-h-[90svh] overflow-y-auto", expanded && "flex h-[94svh] max-h-[94svh] w-[96vw] max-w-[96vw] flex-col overflow-hidden p-4 gap-3 sm:max-w-[96vw] sm:p-4")}>
      <DialogHeader className="min-h-6 min-w-0 shrink-0 flex-row items-center gap-3 pr-8 text-left"><DialogTitle className="min-w-0 truncate" title={item?.name}>{item?.name}</DialogTitle><DialogDescription className="shrink-0 whitespace-nowrap">{formatSize(item?.size || 0)}{extension ? ` · ${extension} file` : ""}</DialogDescription></DialogHeader>
      {!available ? <Empty className="py-8"><EmptyHeader><EmptyMedia variant="icon"><FileQuestion /></EmptyMedia><EmptyTitle>Can’t preview this file</EmptyTitle><EmptyDescription>This browser cannot display this file. Download it to open in another app.</EmptyDescription></EmptyHeader></Empty> : <div className={cn("relative flex flex-col items-center justify-center gap-4 rounded-md bg-muted p-3", expanded ? "min-h-0 flex-1 overflow-hidden" : "min-h-32")}>
        {loading && <div role="status" aria-label="Loading preview" className={cn("flex items-center justify-center gap-2 text-sm text-muted-foreground", expanded && "absolute inset-0 z-10 bg-muted")}><LoaderCircle className="size-4 animate-spin" />Loading preview…</div>}
        {kind === "image" && <ImageViewer key={source} item={item} source={source} onReady={loaded} onError={unavailable} />}
        {kind === "document" && <DocumentViewer key={source} item={item} source={source} onReady={loaded} onError={unavailable} />}
        {kind === "video" && <VideoPlayer key={source} item={item} source={source} onReady={loaded} onError={unavailable} />}
        {kind === "audio" && <AudioPlayer key={source} item={item} source={source} onReady={loaded} onError={unavailable} />}
      </div>}
      <DialogFooter className="shrink-0"><Button variant="outline" onClick={onClose}>Close</Button><Button asChild><a href={downloadUrl} download={item?.name}><Download data-icon="inline-start" />Download file</a></Button></DialogFooter>
    </DialogContent>
  </Dialog>;
}
