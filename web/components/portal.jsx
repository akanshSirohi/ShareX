"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { LayoutGrid, List, FileVideo, FileAudio, FileArchive, FileSpreadsheet, FileCode, Presentation, ArrowDown, Copy, FolderInput, ArrowUpLeft, Check, ChevronLeft, ChevronRight, Download, ExternalLink, File, FileImage, FileText, Folder, FolderOpen, FolderPlus, HardDrive, Info, LoaderCircle, Lock, MoreHorizontal, Package, Plug, RefreshCw, Search, ShieldCheck, Smartphone, Trash2, Upload, Wifi, X } from "lucide-react";
import { Toaster, toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupContent, SidebarGroupLabel, SidebarHeader, SidebarInset, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarProvider, SidebarRail, SidebarSeparator, SidebarTrigger, useSidebar } from "@/components/ui/sidebar";
import { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from "@/components/ui/breadcrumb";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { TransferDialog } from "@/components/transfer-dialog";
import { DeviceDialog } from "@/components/device-dialog";
import { webVersion, fileCategory } from "@/lib/display";
import { FilePreview } from "@/components/file-preview";
import { FileMenuActions } from "@/components/file-menu-actions";
import { ContextMenu, ContextMenuContent, ContextMenuTrigger } from "@/components/ui/context-menu";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Checkbox } from "@/components/ui/checkbox";
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import { DropdownMenu, DropdownMenuContent, DropdownMenuTrigger, DropdownMenuGroup, DropdownMenuRadioGroup, DropdownMenuRadioItem } from "@/components/ui/dropdown-menu";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogCancel, AlertDialogAction } from "@/components/ui/alert-dialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Empty, EmptyHeader, EmptyTitle, EmptyDescription, EmptyMedia, EmptyContent } from "@/components/ui/empty";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { AppearanceControls } from "@/components/appearance-controls";
import { UploadDialog } from "@/components/upload-dialog";
import { NameDialog } from "@/components/name-dialog";
import { PasswordGate } from "@/components/password-gate";
import { actionUrl, formatSize, parentLocation, request } from "@/lib/api";
import { cn } from "@/lib/utils";

const sections = [
  { id: "files", title: "Files", icon: FolderOpen, description: "Browse and manage files on your device." },
  { id: "apps", title: "Apps", icon: Package, description: "Download installed apps from your device." },
  { id: "plugins", title: "Plugins", icon: Plug, description: "Open the tools installed on your device." },
];
const PAGE_SIZE = 50;

function SortButton({ field, sort, onSort, children }) {
  const active = sort.field === field;
  return <Button variant="ghost" size="sm" aria-label={`Sort by ${children}${active ? (sort.descending ? ", descending" : ", ascending") : ""}`} onClick={() => onSort(field)}>{children}<ArrowDown data-icon="inline-end" className={cn(!active && "opacity-40", active && !sort.descending && "rotate-180")} /></Button>;
}

function GridSort({ sort, onSort }) {
  const label = { name: "Name", size: "Size", modified: "Modified" }[sort.field];
  return <div className="flex items-center gap-1">
    <DropdownMenu><DropdownMenuTrigger asChild><Button variant="outline" size="sm" aria-label="Choose sort field">Sort by: {label}</Button></DropdownMenuTrigger>
      <DropdownMenuContent align="end"><DropdownMenuGroup><DropdownMenuRadioGroup value={sort.field} onValueChange={(field) => { if (field !== sort.field) onSort(field); }}>
        <DropdownMenuRadioItem value="name">Name</DropdownMenuRadioItem>
        <DropdownMenuRadioItem value="size">Size</DropdownMenuRadioItem>
        <DropdownMenuRadioItem value="modified">Modified</DropdownMenuRadioItem>
      </DropdownMenuRadioGroup></DropdownMenuGroup></DropdownMenuContent>
    </DropdownMenu>
    <Button variant="outline" size="icon-sm" aria-label={sort.descending ? "Sort ascending" : "Sort descending"} title={sort.descending ? "Descending — switch to ascending" : "Ascending — switch to descending"} onClick={() => onSort(sort.field)}><ArrowDown className={cn(!sort.descending && "rotate-180")} /></Button>
  </div>;
}

function NavigationButton({ onClick, ...props }) {
  const { setOpenMobile } = useSidebar();
  return <SidebarMenuButton {...props} onClick={(event) => { onClick?.(event); setOpenMobile(false); }} />;
}

function ItemIcon({ item, large = false }) {
  const [thumbnailFailed, setThumbnailFailed] = useState(false);
  const category = fileCategory(item);
  const iconClass = cn("shrink-0 text-muted-foreground", large ? "size-12" : "size-5");
  if (item.directory) return <Folder className={iconClass} />;
  if (item.mime?.startsWith("image/")) return thumbnailFailed ? <FileImage className={iconClass} /> : <img src={actionUrl("thumbImage", { location: item.location })} alt="" loading="lazy" className={cn("shrink-0 rounded object-cover", large ? "size-24" : "size-9")} onError={() => setThumbnailFailed(true)} />;
  const Icon = { video: FileVideo, audio: FileAudio, pdf: FileText, archive: FileArchive, spreadsheet: FileSpreadsheet, presentation: Presentation, code: FileCode, document: FileText, image: FileImage, app: Package }[category] || File;
  return <Icon data-file-type={category} className={iconClass} />;
}

function NothingHere({ title, description, icon: Icon = FolderOpen, children }) {
  return <Empty className="min-h-72"><EmptyHeader><EmptyMedia variant="icon"><Icon /></EmptyMedia><EmptyTitle>{title}</EmptyTitle><EmptyDescription>{description}</EmptyDescription></EmptyHeader>{children && <EmptyContent>{children}</EmptyContent>}</Empty>;
}

export function Portal() {
  const [authorization, setAuthorization] = useState("waiting");
  const [authError, setAuthError] = useState("");
  const [authAttempt, setAuthAttempt] = useState(0);
  const [tab, setTab] = useState("files");
  const [location, setLocation] = useState("");
  const [state, setState] = useState(null);
  const [items, setItems] = useState([]);
  const [rootFolders, setRootFolders] = useState([]);
  const [transferTarget, setTransferTarget] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(new Set());
  const [sort, setSort] = useState({ field: "name", descending: false });
  const [page, setPage] = useState(0);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [droppedFiles, setDroppedFiles] = useState(null);
  const [nameTarget, setNameTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [preview, setPreview] = useState(null);
  const [aboutOpen, setAboutOpen] = useState(false);
  const [deviceOpen, setDeviceOpen] = useState(false);
  const [connected, setConnected] = useState(false);
  const [viewMode, setViewMode] = useState("list");
  useEffect(() => { try { if (localStorage.getItem("sharex-view") === "grid") setViewMode("grid"); } catch (_) {} }, []);
  function changeView(mode) { setViewMode(mode); try { localStorage.setItem("sharex-view", mode); } catch (_) {} }

  const [dragging, setDragging] = useState(false);
  const listingRequest = useRef(null);
  const section = sections.find((entry) => entry.id === tab);
  const itemLabel = tab === "files" ? "item" : tab === "apps" ? "app" : "plugin";
  const canModify = state && !state.privateMode && !state.restrictModify;

  useEffect(() => {
    let timer;
    const controller = new AbortController();
    setAuthorization("waiting");
    setAuthError("");
    async function authorize() {
      try {
        const result = await request("auth", {}, { signal: controller.signal, json: false });
        if (controller.signal.aborted) return;
        if (result === "true") setAuthorization("approved");
        else if (result === "password") setAuthorization("password");
        else if (result === "denied") setAuthorization("denied");
        else timer = setTimeout(authorize, 1500);
      } catch (failure) {
        if (!controller.signal.aborted) setAuthError("Cannot connect to your device. Check that sharing is running.");
      }
    }
    authorize();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [authAttempt]);

  useEffect(() => {
    function expired() {
      listingRequest.current?.abort();
      setPreview(null); setTransferTarget(null); setUploadOpen(false);
      setNameTarget(null); setDeleteTarget(null); setDeviceOpen(false); setAboutOpen(false);
      setItems([]); setRootFolders([]); setSelected(new Set()); setState(null);
      setAuthAttempt((current) => current + 1);
    }
    window.addEventListener("sharex-auth-expired", expired);
    return () => window.removeEventListener("sharex-auth-expired", expired);
  }, []);

  const readNavigation = useCallback(() => {
    const parameters = new URLSearchParams(window.location.hash.slice(1));
    const nextTab = parameters.get("tab");
    setTab(sections.some((entry) => entry.id === nextTab) ? nextTab : "files");
    setLocation(parameters.get("location") || "");
  }, []);
  useEffect(() => {
    readNavigation();
    window.addEventListener("popstate", readNavigation);
    return () => window.removeEventListener("popstate", readNavigation);
  }, [readNavigation]);

  function navigate(nextLocation, nextTab = tab) {
    const clean = nextLocation.split("/").filter(Boolean).join("/");
    window.history.pushState(null, "", `#${new URLSearchParams({ tab: nextTab, location: clean })}`);
    setTab(nextTab);
    setLocation(clean);
    setQuery("");
  }

  const refresh = useCallback(async () => {
    listingRequest.current?.abort();
    const controller = new AbortController();
    listingRequest.current = controller;
    setLoading(true);
    setError("");
    setSelected(new Set());
    try {
      const nextState = await request("getState", {}, { signal: controller.signal });
      if (controller.signal.aborted) return;
      setState({ ...nextState, receivedAt: performance.now() });
      setConnected(nextState.connected !== false);
      if (nextState.privateMode && location) { setLocation(""); return; }
      const result = await request(tab === "files" ? "listFiles" : tab === "apps" ? "listApps" : "getInstalledPlugins", tab === "files" ? { location } : {}, { signal: controller.signal });
      if (!controller.signal.aborted) {
        const entries = Array.isArray(result) ? result : result.items;
        setItems(entries);
        if (tab === "files" && !location && !nextState.privateMode) setRootFolders(entries.filter((item) => item.directory).slice(0, 8));
        setPage(0);
      }
    } catch (failure) {
      if (!controller.signal.aborted) { setError(failure.message); setItems([]); setConnected(false); }
    } finally { if (!controller.signal.aborted) setLoading(false); }
  }, [tab, location]);

  useEffect(() => {
    if (authorization === "approved") refresh();
    return () => listingRequest.current?.abort();
  }, [authorization, refresh]);

  useEffect(() => {
    if (authorization !== "approved") return;
    const controller = new AbortController();
    const timer = setInterval(async () => {
      try {
        const latest = await request("getState", {}, { signal: controller.signal });
        if (!controller.signal.aborted) {
          setConnected(latest.connected !== false);
          setState((current) => {
          if (current && (current.privateMode !== latest.privateMode || current.appsAllowed !== latest.appsAllowed)) setTimeout(refresh, 0);
          return { ...latest, receivedAt: performance.now() };
        });
        }
      } catch (_) { if (!controller.signal.aborted) setConnected(false); }
    }, 15000);
    return () => { controller.abort(); clearInterval(timer); };
  }, [authorization, refresh]);

  const visibleItems = useMemo(() => {
    const result = items.filter((item) => `${item.name} ${item.package || ""} ${item.author || ""}`.toLowerCase().includes(query.toLowerCase()));
    if (tab === "files") result.sort((left, right) => {
      if (left.directory !== right.directory) return left.directory ? -1 : 1;
      const difference = sort.field === "name" ? left.name.localeCompare(right.name, undefined, { numeric: true, sensitivity: "base" }) : left[sort.field] - right[sort.field];
      return sort.descending ? -difference : difference;
    });
    return result;
  }, [items, query, sort, tab]);
  const currentItems = visibleItems.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const allSelected = currentItems.length > 0 && currentItems.every((item) => selected.has(item.location));
  const someSelected = currentItems.some((item) => selected.has(item.location));
  const crumbs = location.split("/").filter(Boolean);

  function toggleSelection(item, checked) {
    setSelected((current) => { const next = new Set(current); checked ? next.add(item.location) : next.delete(item.location); return next; });
  }
  function selectPage(checked) {
    setSelected((current) => { const next = new Set(current); currentItems.forEach((item) => checked ? next.add(item.location) : next.delete(item.location)); return next; });
  }
  function downloadUrl(locations) {
    return actionUrl("downloadFiles", { data: JSON.stringify(locations), filename: "files" });
  }
  function openItem(item) {
    if (item.directory) navigate(item.location);
    else setPreview(item);
  }
  function menuActions(item, context = false) {
    const locations = context && selected.has(item.location) ? [...selected] : [item.location];
    return <FileMenuActions context={context} item={item} locations={locations} canModify={canModify}
      onOpen={openItem} onTransfer={(mode, paths) => setTransferTarget({ mode, locations: paths, parent: location })}
      onRename={(entry) => setNameTarget({ item: entry })} onDelete={setDeleteTarget} downloadUrl={downloadUrl(locations)} />;
  }
  async function saveName(name) {
    if (!canModify) throw new Error("File changes are restricted on this device.");
    const action = nameTarget.item ? "renF" : "newF";
    const parameters = nameTarget.item ? { old_n: nameTarget.item.location, new_n: name, parent: location } : { name, parent: location };
    const message = await request(action, parameters, { json: false });
    if (/restricted/i.test(message)) throw new Error(message);
    toast.success(message.replace(/;$/, ""));
    refresh();
  }
  async function deleteItems(event) {
    event.preventDefault();
    if (!canModify || deleting) return;
    setDeleting(true);
    try {
      const message = await request("delFiles", { data: JSON.stringify(deleteTarget) }, { json: false });
      if (/restricted/i.test(message)) throw new Error(message);
      toast.success(message.replace(/;$/, ""));
      setDeleteTarget(null);
      refresh();
    } catch (failure) { toast.error(failure.message); }
    finally { setDeleting(false); }
  }
  function sortBy(field) { setSort((current) => ({ field, descending: current.field === field ? !current.descending : false })); setPage(0); }

  return <div className="min-h-svh bg-background">
    {authorization !== "approved" && <header className="sticky top-0 z-20 flex min-h-18 flex-wrap items-center justify-between gap-3 border-b bg-background/95 px-4 py-3 backdrop-blur-sm sm:px-6">
      <a href="#" className="flex items-center gap-2.5" aria-label="ShareX home" onClick={(event) => { event.preventDefault(); navigate("", "files"); }}>
        <img src="/sharex-logo.png" alt="" className="size-9 shrink-0 object-contain" />
        <span className="text-xl font-semibold tracking-tight">ShareX<span className="ml-2 text-xs font-normal text-muted-foreground max-sm:hidden">WEB</span></span>
      </a>
      <div className="flex items-center gap-3"><Badge variant="outline" className="gap-1.5 max-sm:hidden"><Wifi className="size-3" />Local connection</Badge><AppearanceControls deviceTheme={state?.theme} /></div>
    </header>}

    {authorization !== "approved" ? <main className="mx-auto flex min-h-[75svh] max-w-lg items-center p-6">
      {authorization === "password" ? <PasswordGate onUnlock={() => setAuthAttempt((current) => current + 1)} /> : <>
      <NothingHere icon={authorization === "denied" ? Lock : Smartphone} title={authorization === "denied" ? "Access denied" : authError ? "Device unavailable" : "Connect to your device"} description={authorization === "denied" ? "Your device declined this connection. Start a new sharing session on your phone to request access again." : authError || "Approve this browser on your phone to open your files."}>
        {authorization === "waiting" && !authError ? <div className="flex items-center gap-2 text-sm text-muted-foreground" role="status"><LoaderCircle className="size-4 animate-spin" />Waiting for approval</div> : <Button variant="outline" onClick={() => setAuthAttempt((current) => current + 1)}><RefreshCw data-icon="inline-start" />Try again</Button>}
      </NothingHere></>}
    </main> : <SidebarProvider>
      <Sidebar variant="inset" collapsible="icon">
        <SidebarHeader>
          <SidebarMenu><SidebarMenuItem><SidebarMenuButton size="lg" asChild>
            <a href="#" aria-label="ShareX home" onClick={(event) => { event.preventDefault(); navigate("", "files"); }}>
              <img src="/sharex-logo.png" alt="" className="size-8 shrink-0 object-contain" />
              <div className="grid flex-1 text-left text-sm leading-tight"><span className="truncate font-semibold">ShareX</span><span className="truncate text-xs text-muted-foreground">Local file sharing</span></div>
            </a>
          </SidebarMenuButton></SidebarMenuItem></SidebarMenu>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup><SidebarGroupLabel>Workspace</SidebarGroupLabel><SidebarGroupContent><SidebarMenu>
            {sections.map(({ id, title, icon: Icon }) => <SidebarMenuItem key={id}><NavigationButton tooltip={title} isActive={tab === id} aria-current={tab === id ? "page" : undefined} onClick={() => navigate(location, id)}><Icon /><span>{title}</span></NavigationButton></SidebarMenuItem>)}
          </SidebarMenu></SidebarGroupContent></SidebarGroup>
          {!state?.privateMode && rootFolders.length > 0 && <SidebarGroup><SidebarGroupLabel>Storage folders</SidebarGroupLabel><SidebarGroupContent><SidebarMenu>
            {rootFolders.map((folder) => <SidebarMenuItem key={folder.location}><NavigationButton tooltip={folder.name} isActive={tab === "files" && location === folder.location} onClick={() => navigate(folder.location, "files")}><Folder /><span>{folder.name}</span></NavigationButton></SidebarMenuItem>)}
          </SidebarMenu></SidebarGroupContent></SidebarGroup>}
        </SidebarContent>
        <SidebarFooter>
          <SidebarMenu><SidebarMenuItem><SidebarMenuButton tooltip="About ShareX" onClick={() => setAboutOpen(true)}><Info /><span>About ShareX</span></SidebarMenuButton></SidebarMenuItem></SidebarMenu>
          <SidebarSeparator />
          <SidebarMenu><SidebarMenuItem><SidebarMenuButton size="lg" tooltip={state?.deviceName || "Your device"} onClick={() => setDeviceOpen(true)}>
            <Avatar className="rounded-lg"><AvatarFallback className="rounded-lg"><Smartphone className="size-4" /></AvatarFallback></Avatar>
            <div className="grid flex-1 text-left text-sm leading-tight"><span className="truncate font-medium">{state?.deviceName || "Your device"}</span><span className="truncate text-xs text-muted-foreground">{state?.battery >= 0 ? state.battery + "% battery" : "Connected"}{state?.charging ? " · Charging" : ""}</span></div>
          </SidebarMenuButton></SidebarMenuItem></SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset className="min-w-0">
        <header className="flex h-16 shrink-0 items-center justify-between gap-3 border-b px-4 sm:px-6">
          <div className="flex min-w-0 items-center gap-3"><SidebarTrigger /><Separator orientation="vertical" className="h-4" /><Breadcrumb><BreadcrumbList><BreadcrumbItem className="hidden sm:block"><BreadcrumbLink href="#" onClick={(event) => { event.preventDefault(); navigate("", "files"); }}>My device</BreadcrumbLink></BreadcrumbItem><BreadcrumbSeparator className="hidden sm:block" /><BreadcrumbItem><BreadcrumbPage>{section.title}</BreadcrumbPage></BreadcrumbItem></BreadcrumbList></Breadcrumb></div>
          <div className="flex items-center gap-3"><Badge variant="outline" className="gap-1.5 max-sm:hidden"><Wifi className="size-3" />{connected ? "Connected" : "Disconnected"}</Badge><AppearanceControls deviceTheme={state?.theme} /></div>
        </header>
        <div className="flex flex-1 flex-col p-4 sm:p-6 lg:p-8">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div><h1 className="text-2xl font-semibold tracking-tight">{section.title}</h1><p className="mt-2 text-sm text-muted-foreground">{state?.privateMode && tab === "files" ? "Files shared with you by your device." : section.description}</p></div>
            <div className="flex flex-wrap items-center gap-2">
              {tab === "files" && <div role="group" aria-label="File view" className="flex items-center gap-1"><Button variant={viewMode === "list" ? "secondary" : "outline"} size="icon" aria-label="List view" aria-pressed={viewMode === "list"} onClick={() => changeView("list")}><List /></Button><Button variant={viewMode === "grid" ? "secondary" : "outline"} size="icon" aria-label="Grid view" aria-pressed={viewMode === "grid"} onClick={() => changeView("grid")}><LayoutGrid /></Button></div>}
              <Button variant="outline" size="icon" onClick={refresh} disabled={loading} aria-label="Refresh"><RefreshCw className={cn(loading && "animate-spin")} /></Button>
              {tab === "files" && <><Button variant="outline" aria-label="New folder" onClick={() => setNameTarget({})} disabled={!canModify || loading}><FolderPlus data-icon="inline-start" /><span className="max-sm:hidden">New folder</span></Button><Button onClick={() => setUploadOpen(true)} disabled={!state}><Upload data-icon="inline-start" />Upload files</Button></>}
            </div>
          </div>

          {state?.privateMode && tab === "files" && <Alert><ShieldCheck /><AlertTitle>Private sharing</AlertTitle><AlertDescription>Only shared files are visible. Uploads are saved to Storage / ShareX.</AlertDescription></Alert>}
          {state?.restrictModify && !state.privateMode && tab === "files" && <Alert><Lock /><AlertTitle>File changes restricted</AlertTitle><AlertDescription>You can browse, download, and upload. Rename, delete, and new folders are disabled by your device.</AlertDescription></Alert>}

          <div className="flex flex-wrap items-center justify-between gap-3">
            {tab === "files" && !state?.privateMode ? <nav aria-label="Folder path" className="flex min-w-0 flex-wrap items-center gap-1">
              <Button variant="ghost" size="icon-sm" aria-label="Parent folder" disabled={!location || loading} onClick={() => navigate(parentLocation(location))}><ArrowUpLeft /></Button>
              <Button variant="ghost" size="sm" disabled={loading} onClick={() => navigate("")}><HardDrive data-icon="inline-start" />Storage</Button>
              {crumbs.map((crumb, index) => <span key={index} className="flex min-w-0 items-center gap-1"><ChevronRight className="size-3 text-muted-foreground" /><Button variant="ghost" size="sm" className="max-w-44" disabled={loading} onClick={() => navigate(crumbs.slice(0, index + 1).join("/"))}><span className="truncate">{crumb}</span></Button></span>)}
            </nav> : <Badge variant="secondary">{tab === "files" ? "Shared with you" : `${items.length} ${itemLabel}${items.length === 1 ? "" : "s"}`}</Badge>}
            <InputGroup className="w-full sm:w-72"><InputGroupAddon><Search /></InputGroupAddon><InputGroupInput aria-label={`Search ${section.title.toLowerCase()}`} placeholder={`Search ${section.title.toLowerCase()}…`} value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} /></InputGroup>
          </div>

          {selected.size > 0 && tab === "files" && <div role="toolbar" aria-label="Selected file actions" className="sticky top-3 z-20 flex flex-wrap items-center gap-2 rounded-lg border bg-secondary px-3 py-2 shadow-sm"><Check className="size-4" /><span className="mr-auto text-sm">{selected.size} selected</span><Button variant="outline" size="sm" asChild><a href={downloadUrl([...selected])} download><Download data-icon="inline-start" />Download</a></Button><Button variant="outline" size="sm" disabled={!canModify} onClick={() => setTransferTarget({ mode: "copy", locations: [...selected], parent: location })}><Copy data-icon="inline-start" />Copy</Button><Button variant="outline" size="sm" disabled={!canModify} onClick={() => setTransferTarget({ mode: "move", locations: [...selected], parent: location })}><FolderInput data-icon="inline-start" />Move</Button><Button variant="ghost" size="sm" disabled={!canModify} onClick={() => setDeleteTarget([...selected])}><Trash2 data-icon="inline-start" />Delete</Button><Button variant="ghost" size="icon-sm" aria-label="Clear selection" onClick={() => setSelected(new Set())}><X /></Button></div>}

          {error && <Alert variant="destructive"><Info /><AlertTitle>Could not load {section.title.toLowerCase()}</AlertTitle><AlertDescription className="flex flex-col items-start gap-3">{error}<Button variant="outline" size="sm" onClick={refresh}>Retry</Button></AlertDescription></Alert>}

          <div className={cn("min-h-80 rounded-lg border transition-colors", dragging && "bg-accent")}
            onDragOver={(event) => { if (tab === "files" && event.dataTransfer.types.includes("Files")) { event.preventDefault(); setDragging(true); } }}
            onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDragging(false); }}
            onDrop={(event) => { event.preventDefault(); setDragging(false); if (tab === "files" && event.dataTransfer.files.length) { setDroppedFiles(Array.from(event.dataTransfer.files)); setUploadOpen(true); } }}>
            {loading ? <div className="flex flex-col gap-5 p-6" aria-label="Loading items" role="status">{Array.from({ length: 6 }, (_, index) => <div key={index} className="flex items-center gap-4"><Skeleton className="size-9 rounded" /><Skeleton className="h-4 flex-1" /><Skeleton className="h-4 w-16" /></div>)}</div> : <>
              {tab === "files" && <section aria-label="files" className="workspace-enter">
                {visibleItems.length ? (viewMode === "grid" ? <div className="flex flex-col gap-3 p-3"><div className="flex items-center justify-between gap-2 px-1"><Checkbox aria-label="Select all visible files" checked={allSelected ? true : someSelected ? "indeterminate" : false} onCheckedChange={selectPage} /><GridSort sort={sort} onSort={sortBy} /></div><div role="list" aria-label="Files grid" className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">{currentItems.map((item) => <ContextMenu key={item.location}><ContextMenuTrigger asChild><div role="listitem" className={cn("flex min-w-0 flex-col rounded-lg border p-3", selected.has(item.location) && "bg-accent")} onContextMenu={() => { if (!selected.has(item.location)) setSelected(new Set([item.location])); }}><div className="flex items-center justify-between"><Checkbox aria-label={`Select ${item.name}`} checked={selected.has(item.location)} onCheckedChange={(checked) => toggleSelection(item, checked)} /><DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon-sm" aria-label={`Actions for ${item.name}`}><MoreHorizontal /></Button></DropdownMenuTrigger><DropdownMenuContent align="end">{menuActions(item)}</DropdownMenuContent></DropdownMenu></div><button type="button" onClick={() => openItem(item)} className="flex min-w-0 flex-1 flex-col items-center gap-3 rounded-md py-3 text-center hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring" aria-label={item.name}><div className="flex h-24 items-center justify-center"><ItemIcon item={item} large /></div><span className="w-full truncate text-sm font-medium" title={item.name}>{item.name}</span><span className="text-xs text-muted-foreground">{item.directory ? "Folder" : formatSize(item.size)}</span></button></div></ContextMenuTrigger><ContextMenuContent>{menuActions(item, true)}</ContextMenuContent></ContextMenu>)}</div></div> : <Table><TableHeader><TableRow><TableHead className="w-12 pl-5"><Checkbox aria-label="Select all visible files" checked={allSelected ? true : someSelected ? "indeterminate" : false} onCheckedChange={selectPage} /></TableHead><TableHead aria-sort={sort.field === "name" ? (sort.descending ? "descending" : "ascending") : "none"}><SortButton field="name" sort={sort} onSort={sortBy}>Name</SortButton></TableHead><TableHead className="w-28" aria-sort={sort.field === "size" ? (sort.descending ? "descending" : "ascending") : "none"}><SortButton field="size" sort={sort} onSort={sortBy}>Size</SortButton></TableHead><TableHead className="hidden w-40 md:table-cell" aria-sort={sort.field === "modified" ? (sort.descending ? "descending" : "ascending") : "none"}><SortButton field="modified" sort={sort} onSort={sortBy}>Modified</SortButton></TableHead><TableHead className="w-12"><span className="sr-only">Actions</span></TableHead></TableRow></TableHeader>
                  <TableBody>{currentItems.map((item) => <ContextMenu key={item.location}><ContextMenuTrigger asChild><TableRow data-state={selected.has(item.location) ? "selected" : undefined} onContextMenu={() => { if (!selected.has(item.location)) setSelected(new Set([item.location])); }}>
                    <TableCell className="pl-5"><Checkbox aria-label={`Select ${item.name}`} checked={selected.has(item.location)} onCheckedChange={(checked) => toggleSelection(item, checked)} /></TableCell>
                    <TableCell><div className="flex min-w-0 items-center gap-3 py-1"><div className="flex size-9 shrink-0 items-center justify-center"><ItemIcon item={item} /></div><Button variant="ghost" className="min-w-0 max-w-[40vw] justify-start px-2 sm:max-w-none" onClick={() => openItem(item)}><span className="truncate">{item.name}</span></Button>{item.hidden && <Badge variant="outline" className="hidden sm:inline-flex">Hidden</Badge>}</div></TableCell>
                    <TableCell className="text-muted-foreground">{item.directory ? "—" : formatSize(item.size)}</TableCell><TableCell className="hidden text-xs text-muted-foreground md:table-cell">{item.modified ? new Date(item.modified).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "—"}</TableCell>
                    <TableCell><DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" size="icon-sm" aria-label={`Actions for ${item.name}`}><MoreHorizontal /></Button></DropdownMenuTrigger><DropdownMenuContent align="end">{menuActions(item)}</DropdownMenuContent></DropdownMenu></TableCell>
                  </TableRow></ContextMenuTrigger><ContextMenuContent>{menuActions(item, true)}</ContextMenuContent></ContextMenu>)}</TableBody></Table>) : <NothingHere title={query ? "No matching files" : state?.privateMode ? "No files shared yet" : "This folder is empty"} description={query ? "Try another file name." : state?.privateMode ? "Choose files to share on your phone." : "Upload files here or drag them into this window."}>{!query && !state?.privateMode && <Button variant="outline" onClick={() => setUploadOpen(true)}><Upload data-icon="inline-start" />Upload files</Button>}</NothingHere>}
              </section>}
              {tab === "apps" && <section aria-label="apps" className="workspace-enter">
                {!state?.appsAllowed ? <NothingHere icon={Lock} title="App sharing is disabled" description="Enable app access in ShareX settings on your phone." /> : visibleItems.length ? <Table><TableHeader><TableRow><TableHead className="pl-6">App</TableHead><TableHead>Size</TableHead><TableHead className="pr-6 text-right">Download</TableHead></TableRow></TableHeader><TableBody>{currentItems.map((item) => <TableRow key={item.package}><TableCell className="pl-6"><div className="flex items-center gap-3 py-2"><img src={item.icon} alt="" loading="lazy" className="size-10 rounded-lg" /><div className="min-w-0"><p className="font-medium">{item.name}</p><p className="max-w-[45vw] truncate text-xs text-muted-foreground">{item.package}</p></div></div></TableCell><TableCell className="text-muted-foreground">{formatSize(item.size)}</TableCell><TableCell className="pr-6 text-right"><Button variant="outline" size="sm" asChild><a href={actionUrl("getApp", { pkg: item.package })} target="_blank" rel="noreferrer"><Download data-icon="inline-start" /><span className="max-sm:hidden">APK</span></a></Button></TableCell></TableRow>)}</TableBody></Table> : <NothingHere icon={Package} title={query ? "No matching apps" : "No apps available"} description={query ? "Try another app name." : "User-installed apps will appear here."} />}
              </section>}
              {tab === "plugins" && <section aria-label="plugins" className="workspace-enter">
                {visibleItems.length ? <div className="flex flex-col divide-y">{currentItems.map((item) => <div key={item.uid} className="flex flex-wrap items-center gap-4 p-6"><div className="flex size-10 items-center justify-center rounded-lg bg-secondary"><Plug className="size-5" /></div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h2 className="font-medium">{item.name}</h2><Badge variant="secondary">{item.version}</Badge></div><p className="mt-1 text-xs text-muted-foreground">By {item.author}</p><p className="mt-2 text-sm text-muted-foreground">{item.description}</p></div><Button variant="outline" size="sm" asChild><a href={`/SharexApp/${encodeURIComponent(item.uid)}/`} target="_blank" rel="noreferrer">Open<ExternalLink data-icon="inline-end" /></a></Button></div>)}</div> : <NothingHere icon={Plug} title={query ? "No matching plugins" : "No plugins enabled"} description={query ? "Try another plugin name." : "Install and enable plugins in ShareX on your phone."} />}
              </section>}
            </>}
          </div>
          <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground"><span>{visibleItems.length} {itemLabel}{visibleItems.length === 1 ? "" : "s"}{query ? ` matching “${query}”` : ""}</span><div className="flex items-center gap-2">{visibleItems.length > PAGE_SIZE && <><span>Page {page + 1} of {Math.ceil(visibleItems.length / PAGE_SIZE)}</span><Button variant="outline" size="icon-sm" aria-label="Previous page" disabled={!page} onClick={() => setPage((current) => current - 1)}><ChevronLeft /></Button><Button variant="outline" size="icon-sm" aria-label="Next page" disabled={(page + 1) * PAGE_SIZE >= visibleItems.length} onClick={() => setPage((current) => current + 1)}><ChevronRight /></Button></>}<span className="hidden sm:inline">Shared directly from your device</span></div></div>
          <div className="flex items-center justify-between text-xs text-muted-foreground lg:hidden"><Button variant="ghost" size="sm" onClick={() => setDeviceOpen(true)}>{state?.deviceName}{state?.battery >= 0 ? ` · ${state.battery}% battery` : ""}</Button><Button variant="ghost" size="sm" onClick={() => setAboutOpen(true)}>About</Button></div>
        </div>
        </div>
      </SidebarInset>
    </SidebarProvider>}

    <TransferDialog target={transferTarget} onClose={() => setTransferTarget(null)} canModify={canModify} onComplete={(message) => { if (message) toast.success(message); refresh(); }} />
    <UploadDialog open={uploadOpen} onOpenChange={setUploadOpen} location={location} privateMode={state?.privateMode} droppedFiles={droppedFiles} onComplete={refresh} />
    <NameDialog target={nameTarget} onClose={() => setNameTarget(null)} onSave={saveName} />
    <AlertDialog open={!!deleteTarget} onOpenChange={(open) => { if (!open && !deleting) setDeleteTarget(null); }}><AlertDialogContent><AlertDialogHeader><AlertDialogTitle>Delete {deleteTarget?.length} {deleteTarget?.length === 1 ? "item" : "items"}?</AlertDialogTitle><AlertDialogDescription>This permanently removes the selected files and folders from your device.</AlertDialogDescription></AlertDialogHeader><AlertDialogFooter><AlertDialogCancel disabled={deleting}>Cancel</AlertDialogCancel><AlertDialogAction variant="destructive" disabled={deleting || !canModify} onClick={deleteItems}>{deleting && <LoaderCircle className="animate-spin" data-icon="inline-start" />}Delete</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog>
    <FilePreview item={preview} onClose={() => setPreview(null)} downloadUrl={downloadUrl(preview ? [preview.location] : [])} />
    <Dialog open={aboutOpen} onOpenChange={setAboutOpen}><DialogContent><DialogHeader><DialogTitle>ShareX</DialogTitle><DialogDescription>File sharing, simplified.</DialogDescription></DialogHeader><p className="text-sm">Send and receive files directly over your local network.</p><p className="text-sm">Built by Akansh Sirohi.</p><p className="text-xs text-muted-foreground">Web UI: {webVersion(state?.webVersion)} · Open source under AGPL v3.0</p><Button variant="outline" asChild><a href="https://github.com/akanshSirohi/ShareX" target="_blank" rel="noreferrer">Source code<ExternalLink data-icon="inline-end" /></a></Button></DialogContent></Dialog>
    <DeviceDialog open={deviceOpen} onOpenChange={setDeviceOpen} state={state} connected={connected} />
    <Toaster closeButton toastOptions={{ style: { background: "var(--popover)", color: "var(--popover-foreground)", borderColor: "var(--border)" } }} />
  </div>;
}
