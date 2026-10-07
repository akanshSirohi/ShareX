import { test } from "node:test";
import assert from "node:assert/strict";
import { webVersion, sessionDuration, fileCategory } from "../lib/display.js";
test("folder versions are readable and session duration handles rollover", () => {
 assert.equal(webVersion("sharex_web_v4_2"), "v4.2");
 assert.equal(webVersion("sharex_web_v4_6"), "v4.6");
 assert.equal(webVersion(null), "Unavailable");
 assert.equal(sessionDuration(3661000), "1h 1m 1s");
 assert.equal(sessionDuration(0), "0s");
});
test("file icons use MIME or extension and keep folders distinct", () => {
 assert.equal(fileCategory({name:"unknown",mime:"video/mp4"}), "video");
 assert.equal(fileCategory({name:"report.pdf",mime:"application/octet-stream"}), "pdf");
 assert.equal(fileCategory({name:"clip.mp4"}), "video");
 assert.equal(fileCategory({name:"data.xlsx"}), "spreadsheet");
 assert.equal(fileCategory({name:"archive.zip"}), "archive");
 assert.equal(fileCategory({name:"album.mp4",directory:true}), "folder");
 assert.equal(fileCategory({name:"unrecognized.bin"}), "file");
});
