import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, writeFile, readFile, stat, rm } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import JSZip from "jszip";
import { copyAssets } from "../scripts/copy-assets.mjs";

async function fixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), "sharex-web-test-"));
  const outputDir = path.join(root, "out");
  const assetsDir = path.join(root, "assets");
  await mkdir(path.join(outputDir, "_next", "static"), { recursive: true });
  await mkdir(path.join(assetsDir, "fonts"), { recursive: true });
  await mkdir(path.join(assetsDir, "sharex_web_v3_0"));
  await writeFile(path.join(assetsDir, "fonts", "local.woff2"), "preserve");
  await writeFile(path.join(assetsDir, "keystore.bks"), "preserve");
  await writeFile(path.join(outputDir, "index.html"), "static portal");
  await writeFile(path.join(outputDir, "_next", "static", "app.js"), "compiled");
  await writeFile(path.join(outputDir, "build-info.json"), JSON.stringify({ assetFolder: "sharex_web_v4_0" }));
  return { root, outputDir, assetsDir, assetFolder: "sharex_web_v4_0" };
}

test("copy promotes export, removes old portal, and preserves other Android assets", async (t) => {
  const context = await fixture();
  t.after(() => rm(context.root, { recursive: true, force: true }));
  const target = await copyAssets(context);
  const archive = await JSZip.loadAsync(await readFile(target), { checkCRC32: true });
  assert.equal(await archive.file("_next/static/app.js").async("string"), "compiled");
  assert.equal(await archive.file("index.html").async("string"), "static portal");
  await assert.rejects(stat(path.join(context.assetsDir, "sharex_web_v3_0")), { code: "ENOENT" });
  assert.equal(await readFile(path.join(context.assetsDir, "keystore.bks"), "utf8"), "preserve");
  assert.equal(await readFile(path.join(context.assetsDir, "fonts/local.woff2"), "utf8"), "preserve");
  const original = await readFile(target);
  await copyAssets(context);
  assert.deepEqual(await readFile(target), original, "Packaging must be deterministic");
  await writeFile(path.join(context.outputDir, "obsolete.js"), "old build");
  await copyAssets(context);
  await rm(path.join(context.outputDir, "obsolete.js"));
  await copyAssets(context);
  assert.equal((await JSZip.loadAsync(await readFile(target))).file("obsolete.js"), null);
});

test("version mismatch and unsafe folder names leave installed assets intact", async (t) => {
  const context = await fixture();
  t.after(() => rm(context.root, { recursive: true, force: true }));
  await assert.rejects(copyAssets({ ...context, assetFolder: "../outside" }), /Invalid assetFolder/);
  await assert.rejects(copyAssets({ ...context, assetFolder: "sharex_web_v5_0" }), /Rebuild/);
  assert.ok((await stat(path.join(context.assetsDir, "sharex_web_v3_0"))).isDirectory());
});

test("missing export fails before replacing the installed portal", async (t) => {
  const context = await fixture();
  t.after(() => rm(context.root, { recursive: true, force: true }));
  await rm(path.join(context.outputDir, "index.html"));
  await assert.rejects(copyAssets(context));
  assert.ok((await stat(path.join(context.assetsDir, "sharex_web_v3_0"))).isDirectory());
});

test("a changed folder version installs the new build and removes the prior version", async (t) => {
  const context = await fixture();
  t.after(() => rm(context.root, { recursive: true, force: true }));
  await copyAssets(context);
  await writeFile(path.join(context.outputDir, "build-info.json"), JSON.stringify({ assetFolder: "sharex_web_v4_1" }));
  const target = await copyAssets({ ...context, assetFolder: "sharex_web_v4_1" });
  assert.equal(path.basename(target), "sharex_web_v4_1.zip");
  await assert.rejects(stat(path.join(context.assetsDir, "sharex_web_v4_0.zip")), { code: "ENOENT" });
});
