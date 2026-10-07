export function actionUrl(action, parameters = {}) {
  return `/ShareX?${new URLSearchParams({ action, ...parameters })}`;
}

export async function request(action, parameters = {}, { signal, json = true } = {}) {
  const response = await fetch(actionUrl(action, parameters), { cache: "no-store", signal });
  if (response.status === 401 && typeof window !== "undefined") window.dispatchEvent(new Event("sharex-auth-expired"));
  if (!response.ok) throw new Error((await response.text()) || `Request failed (${response.status})`);
  return json ? response.json() : response.text();
}

export function formatSize(bytes) {
  if (!bytes) return "0 B";
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), 4);
  return `${(bytes / 1024 ** exponent).toLocaleString(undefined, { maximumFractionDigits: exponent ? 1 : 0 })} ${["B", "KB", "MB", "GB", "TB"][exponent]}`;
}

export function parentLocation(location) {
  return location.split("/").filter(Boolean).slice(0, -1).join("/");
}

export function validName(value) {
  const name = value.trim();
  return !!name && !/[\\/\x00-\x1f]/.test(name) && name !== "." && name !== "..";
}
