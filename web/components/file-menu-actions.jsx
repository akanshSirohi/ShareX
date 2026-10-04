"use client";

import { Copy, Download, FolderInput, FolderOpen, Pencil, Trash2 } from "lucide-react";
import { ContextMenuGroup, ContextMenuItem, ContextMenuSeparator } from "@/components/ui/context-menu";
import { DropdownMenuGroup, DropdownMenuItem, DropdownMenuSeparator } from "@/components/ui/dropdown-menu";

// Both menus share actions and permission rules. Context actions respect a multi-selection.
export function FileMenuActions({ context = false, item, locations, canModify, onOpen, onTransfer, onRename, onDelete, downloadUrl }) {
  const Group = context ? ContextMenuGroup : DropdownMenuGroup;
  const Item = context ? ContextMenuItem : DropdownMenuItem;
  const Separator = context ? ContextMenuSeparator : DropdownMenuSeparator;
  const multiple = locations.length > 1;
  return <>
    <Group><Item onSelect={() => onOpen(item)}><FolderOpen />{item.directory ? "Open" : "Preview"}</Item><Item asChild><a href={downloadUrl} download><Download />{multiple ? `Download ${locations.length} items` : "Download"}</a></Item></Group>
    <Separator />
    <Group><Item disabled={!canModify} onSelect={() => onTransfer("copy", locations)}><Copy />Copy to…</Item><Item disabled={!canModify} onSelect={() => onTransfer("move", locations)}><FolderInput />Move to…</Item><Item disabled={!canModify || multiple} onSelect={() => onRename(item)}><Pencil />Rename</Item><Item variant="destructive" disabled={!canModify} onSelect={() => onDelete(locations)}><Trash2 />Delete</Item></Group>
  </>;
}
