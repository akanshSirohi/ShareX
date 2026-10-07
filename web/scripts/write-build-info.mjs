import { cp, copyFile, readFile, mkdir, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";

const { assetFolder } = JSON.parse(await readFile(new URL("../web-ui.json", import.meta.url), "utf8"));
if (!/^sharex_web_v\d+(?:_\d+)*$/.test(assetFolder)) throw new Error("Invalid assetFolder");
await mkdir(new URL("../public/", import.meta.url), { recursive: true });
await writeFile(new URL("../public/build-info.json", import.meta.url), JSON.stringify({ assetFolder }) + "\n");
// Host the video decoder locally so device playback never needs a CDN.
await mkdir(new URL("../public/players/", import.meta.url), { recursive: true });
await copyFile(createRequire(import.meta.url).resolve("movi-player/movi.wasm"), new URL("../public/players/movi.wasm", import.meta.url));
const pdfRoot = path.dirname(createRequire(import.meta.url).resolve("pdfjs-dist/package.json"));
await mkdir(new URL("../public/players/pdf/build/", import.meta.url), { recursive: true });
await copyFile(path.join(pdfRoot, "build/pdf.worker.min.mjs"), new URL("../public/players/pdf/build/pdf.worker.min.mjs", import.meta.url));
for (const directory of ["cmaps", "standard_fonts", "wasm", "iccs"]) {
  await cp(path.join(pdfRoot, directory), new URL(`../public/players/pdf/${directory}/`, import.meta.url), { recursive: true });
}
