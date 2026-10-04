export function webVersion(value) {
  const match = String(value || "").match(/^sharex_(?:web_)?v(\d+(?:_\d+)*)$/);
  return match ? `v${match[1].replaceAll("_", ".")}` : value || "Unavailable";
}
export function sessionDuration(milliseconds) {
  const seconds = Math.max(0, Math.floor((milliseconds || 0) / 1000));
  const hours = Math.floor(seconds / 3600), minutes = Math.floor(seconds % 3600 / 60);
  return hours ? `${hours}h ${minutes}m ${seconds % 60}s` : minutes ? `${minutes}m ${seconds % 60}s` : `${seconds}s`;
}
export function fileCategory(item) {
  if (item.directory) return "folder";
  const mime = (item.mime || "").toLowerCase();
  const extension = item.name?.split(".").pop()?.toLowerCase();
  if (mime.startsWith("image/") || ["jpg","jpeg","png","gif","webp","svg","avif","bmp","heic","tiff"].includes(extension)) return "image";
  if (mime.startsWith("video/") || ["mp4","webm","mov","mkv","avi","3gp","m4v"].includes(extension)) return "video";
  if (mime.startsWith("audio/") || ["mp3","wav","flac","ogg","m4a","aac","opus"].includes(extension)) return "audio";
  if (mime === "application/pdf" || extension === "pdf") return "pdf";
  if (["zip","rar","7z","tar","gz","bz2"].includes(extension)) return "archive";
  if (["xls","xlsx","csv","tsv"].includes(extension)) return "spreadsheet";
  if (["ppt","pptx"].includes(extension)) return "presentation";
  if (["js","jsx","py","java","html","css","xml","json","sql","sh","kt"].includes(extension)) return "code";
  if (mime.startsWith("text/") || ["doc","docx","txt","md","rtf"].includes(extension)) return "document";
  if (extension === "apk") return "app";
  return "file";
}
