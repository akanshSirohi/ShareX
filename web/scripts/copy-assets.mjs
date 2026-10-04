import { mkdir, readdir, readFile, writeFile, rename, rm, stat } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import JSZip from "jszip";

const webRoot = fileURLToPath(new URL("../", import.meta.url));

export async function copyAssets({ outputDir, assetsDir, assetFolder }) {
  if (!/^sharex_web_v\d+(?:_\d+)*$/.test(assetFolder)) throw new Error("Invalid assetFolder");
  outputDir = path.resolve(outputDir);
  assetsDir = path.resolve(assetsDir);
  if (!(await stat(path.join(outputDir, "index.html"))).isFile()) throw new Error("Run npm run build first");
  const buildInfo = JSON.parse(await readFile(path.join(outputDir, "build-info.json"), "utf8"));
  if (buildInfo.assetFolder !== assetFolder) throw new Error("Folder version changed. Rebuild before copying assets.");
  const zip = new JSZip();
  async function addDirectory(directory, prefix = "") {
    const entries = await readdir(directory, { withFileTypes: true });
    for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name, "en"))) {
      const source = path.join(directory, entry.name);
      const name = prefix + entry.name;
      if (entry.isDirectory()) await addDirectory(source, name + "/");
      else if (entry.isFile()) zip.file(name, await readFile(source), { date: new Date("2000-01-01T00:00:00Z"), createFolders: false });
      else throw new Error(`Unsupported export entry: ${name}`);
    }
  }
  await addDirectory(outputDir);
  const archive = await zip.generateAsync({ type: "nodebuffer", compression: "DEFLATE", compressionOptions: { level: 9 } });
  await mkdir(assetsDir, { recursive: true });
  const target = path.join(assetsDir, `${assetFolder}.zip`);
  const pending = `${target}.pending`;
  await writeFile(pending, archive);
  await rename(pending, target);
  // Remove only direct children with known web UI version names, including legacy loose builds.
  for (const entry of await readdir(assetsDir, { withFileTypes: true })) {
    if (/^sharex_web_v\d+(?:_\d+)*(?:\.zip)?(?:\.pending|\.backup)?$/.test(entry.name) && entry.name !== path.basename(target)) {
      const obsolete = path.resolve(assetsDir, entry.name);
      if (path.dirname(obsolete) !== assetsDir) throw new Error("Unsafe asset path");
      await rm(obsolete, { recursive: true, force: true });
    }
  }
  return target;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { assetFolder } = JSON.parse(await readFile(path.join(webRoot, "web-ui.json"), "utf8"));
  const target = await copyAssets({ outputDir: path.join(webRoot, "out"), assetsDir: path.join(webRoot, "../app/src/main/assets"), assetFolder });
  console.log(`Static export packaged to ${target}`);
}
