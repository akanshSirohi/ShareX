// emf-converter checks for document/OffscreenCanvas before importing this fallback.
// Native Node canvas cannot run in the static portal.
export function createCanvas() {
  throw new Error("Document previews require a browser canvas.");
}
