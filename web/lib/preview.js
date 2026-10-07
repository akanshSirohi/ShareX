// Browser decoders decide support; extensions cover files with an unknown device MIME type.
export function previewKind(item) {
  const mime = (item?.mime || "").split(";")[0].toLowerCase();
  if (mime.startsWith("image/")) return "image";
  if (mime.startsWith("video/")) return "video";
  if (mime.startsWith("audio/")) return "audio";
  if (mime === "application/pdf") return "document";
  const extension = item?.name?.split(".").pop()?.toLowerCase();
  if (["jpg", "jpeg", "png", "gif", "webp", "avif", "svg", "bmp", "ico", "apng", "heic", "heif", "jxl", "tif", "tiff"].includes(extension)) return "image";
  if (["mp4", "m4v", "webm", "ogv", "mov", "mkv", "avi", "3gp", "ts", "mts", "m2ts", "mpg", "mpeg"].includes(extension)) return "video";
  if (["mp3", "mpeg", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "aif", "aiff", "caf", "wma", "weba"].includes(extension)) return "audio";
  return documentType(item) ? "document" : null;
}

export function documentType(item) {
  const mime = (item?.mime || "").split(";")[0].toLowerCase();
  const extension = item?.name?.split(".").pop()?.toLowerCase();
  if (mime.startsWith("image/")) return "image";
  const extensions = { pdf: "pdf", docx: "docx", xlsx: "xlsx", xls: "xlsx", pptx: "pptx", md: "markdown", markdown: "markdown", mdown: "markdown", mkd: "markdown", csv: "csv", tsv: "csv", txt: "text", log: "text", json: "json", jsonl: "json", geojson: "json", html: "html", htm: "html" };
  if (extensions[extension]) return extensions[extension];
  if (["js", "jsx", "ts", "tsx", "py", "java", "go", "rs", "css", "xml", "yaml", "yml", "sql", "sh", "kt", "c", "cpp", "h", "cs", "php", "rb", "toml", "ini"].includes(extension)) return "code";
  const types = { "application/pdf": "pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "xlsx", "application/vnd.ms-excel": "xlsx", "application/vnd.openxmlformats-officedocument.presentationml.presentation": "pptx", "application/json": "json", "application/geo+json": "json", "text/markdown": "markdown", "text/csv": "csv", "text/tab-separated-values": "csv", "text/html": "html" };
  return types[mime] || (mime.startsWith("text/") ? "text" : null);
}

export function audioFormat(item) {
  const formats = { "audio/mpeg": "mp3", "audio/mp3": "mp3", "audio/mp4": "m4a", "audio/x-m4a": "m4a", "audio/ogg": "ogg", "audio/opus": "opus", "audio/wav": "wav", "audio/x-wav": "wav", "audio/wave": "wav", "audio/flac": "flac", "audio/x-flac": "flac", "audio/aac": "aac", "audio/aiff": "aiff", "audio/x-aiff": "aiff", "audio/webm": "webm", "audio/x-caf": "caf" };
  const mime = (item?.mime || "").split(";")[0].toLowerCase();
  const extension = item?.name?.split(".").pop()?.toLowerCase();
  return formats[mime] || ({ m4b: "m4a", aif: "aiff", oga: "ogg", weba: "webm", mpeg: "mp3" })[extension] || extension;
}
