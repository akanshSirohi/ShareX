import { PHASE_DEVELOPMENT_SERVER } from "next/constants.js";

export default (phase) => {
  const development = phase === PHASE_DEVELOPMENT_SERVER;
  const deviceUrl = process.env.SHAREX_DEVICE_URL;
  return {
    agentRules: false,
    ...(development ? {} : { output: "export" }),
    trailingSlash: !development,
    skipTrailingSlashRedirect: development,
    images: { unoptimized: true },
    // Omni's EMF converter uses browser Canvas APIs. Exclude its optional Node addon
    // from both client and static prerender bundles; previews mount only in a browser.
    turbopack: { resolveAlias: { "@napi-rs/canvas": "./lib/browser-canvas.js" } },
    ...(development && deviceUrl ? {
      async rewrites() {
        const origin = new URL(deviceUrl).origin;
        return ["/ShareX", "/ShareX/:path*", "/SharexApp/:path*"].map((source) => ({
          source, destination: `${origin}${source}`,
        }));
      },
    } : {}),
  };
};
