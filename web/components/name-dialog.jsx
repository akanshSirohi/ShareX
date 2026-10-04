"use client";

import { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel, FieldError } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { validName } from "@/lib/api";

export function NameDialog({ target, onClose, onSave }) {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => { setName(target?.item?.name || ""); setError(""); }, [target]);
  async function save(event) {
    event.preventDefault();
    if (!validName(name)) { setError("Enter a name without slashes or control characters."); return; }
    setBusy(true);
    try { await onSave(name.trim()); onClose(); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  return <Dialog open={!!target} onOpenChange={(open) => { if (!open && !busy) onClose(); }}>
    <DialogContent>
      <DialogHeader><DialogTitle>{target?.item ? "Rename" : "New folder"}</DialogTitle><DialogDescription>{target?.item ? "Change the name on your device." : "Create a folder in this location."}</DialogDescription></DialogHeader>
      <form onSubmit={save} className="flex flex-col gap-6">
        <FieldGroup><Field data-invalid={!!error}><FieldLabel htmlFor="item-name">Name</FieldLabel><Input id="item-name" value={name} onChange={(event) => { setName(event.target.value); setError(""); }} aria-invalid={!!error} autoFocus disabled={busy} />{error && <FieldError>{error}</FieldError>}</Field></FieldGroup>
        <DialogFooter><Button type="button" variant="outline" disabled={busy} onClick={onClose}>Cancel</Button><Button type="submit" disabled={busy || !name.trim()}>{busy && <LoaderCircle className="animate-spin" data-icon="inline-start" />} {target?.item ? "Save name" : "Create folder"}</Button></DialogFooter>
      </form>
    </DialogContent>
  </Dialog>;
}
