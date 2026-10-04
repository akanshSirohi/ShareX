"use client";

import { useState } from "react";
import { LockKeyhole, LoaderCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field, FieldLabel, FieldGroup, FieldError, FieldDescription } from "@/components/ui/field";

export function PasswordGate({ onUnlock }) {
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function unlock(event) {
    event.preventDefault();
    if (busy || !password) return;
    setBusy(true); setError("");
    try {
      const response = await fetch("/ShareX/password", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ password }), cache: "no-store", credentials: "same-origin" });
      if (!response.ok) throw new Error(await response.text());
      setPassword(""); onUnlock();
    } catch (failure) { setError(failure.message || "Unable to unlock. Try again."); }
    finally { setBusy(false); }
  }
  return <div className="flex w-full flex-col gap-6 rounded-xl border bg-card p-6">
    <div className="flex flex-col gap-2"><LockKeyhole className="size-6" /><h1 className="text-xl font-semibold">Unlock ShareX</h1><p className="text-sm text-muted-foreground">Enter the password set on your device. This browser stays unlocked until the session expires.</p></div>
    <form onSubmit={unlock}><FieldGroup><Field data-invalid={!!error}><FieldLabel htmlFor="portal-password">Password</FieldLabel><Input id="portal-password" type="password" autoComplete="current-password" autoFocus maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} disabled={busy} aria-invalid={!!error} aria-describedby={error ? "password-error" : "password-help"} /><FieldDescription id="password-help">Password and session duration can be changed in the Android app’s Settings.</FieldDescription>{error && <FieldError id="password-error" role="alert">{error}</FieldError>}</Field><Button type="submit" disabled={busy || !password}>{busy ? <LoaderCircle data-icon="inline-start" className="animate-spin" /> : <LockKeyhole data-icon="inline-start" />}Unlock</Button></FieldGroup></form>
  </div>;
}
