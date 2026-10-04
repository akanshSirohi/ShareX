import { test } from "node:test";
import assert from "node:assert/strict";
import { audioFormat, documentType, previewKind } from "../lib/preview.js";
import { fitImage } from "../lib/image-fit.js";

test("images fit both viewport dimensions without enlarging smaller originals", () => {
  assert.deepEqual(fitImage(4000, 3000, 1200, 600), { width: 800, height: 600 });
  assert.deepEqual(fitImage(3000, 4000, 1200, 600), { width: 450, height: 600 });
  assert.deepEqual(fitImage(100, 50, 1200, 600), { width: 100, height: 50 });
});

test("MIME selects previews even when Android returns a file without an extension", () => {
  assert.equal(previewKind({ name: "photo", mime: "image/avif" }), "image");
  assert.equal(previewKind({ name: "clip", mime: "video/webm" }), "video");
  assert.equal(previewKind({ name: "recording", mime: "audio/ogg; codecs=opus" }), "audio");
  assert.equal(documentType({ name: "document", mime: "application/pdf" }), "pdf");
});

test("unknown device MIME uses filename formats and leaves unsupported binaries for download", () => {
  const file = (name) => ({ name, mime: "application/octet-stream" });
  assert.equal(previewKind(file("PHOTO.AVIF")), "image");
  assert.equal(previewKind(file("camera.M2TS")), "video");
  assert.equal(documentType(file("presentation.PPTX")), "pptx");
  assert.equal(documentType(file("sheet.XLS")), "xlsx");
  assert.equal(previewKind(file("archive.zip")), null);
  assert.equal(previewKind(file("unknown.bin")), null);
});

test("Howler receives a codec format for extensionless URLs and filename aliases", () => {
  assert.equal(audioFormat({ name: "recording", mime: "audio/mp4" }), "m4a");
  assert.equal(audioFormat({ name: "recording.oga" }), "ogg");
  assert.equal(audioFormat({ name: "recording.weba" }), "webm");
  assert.equal(audioFormat({ name: "book.m4b" }), "m4a");
});
