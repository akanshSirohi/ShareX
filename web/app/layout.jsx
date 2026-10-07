import "./globals.css";
import "./zen.css";
import "./themes.css";

export const metadata = {
  title: "ShareX · Device workspace",
  description: "Browse, download, and send files directly over your local network.",
};

const initializeAppearance = `try {
  var mode = localStorage.getItem('sharex-mode');
  document.documentElement.classList.toggle('dark', mode ? mode === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches);
} catch (_) {}`;

export default function RootLayout({ children }) {
  return <html lang="en" suppressHydrationWarning><head><script dangerouslySetInnerHTML={{ __html: initializeAppearance }} /></head><body>{children}</body></html>;
}
