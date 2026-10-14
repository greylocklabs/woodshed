/** Files issues as the woodshed-reports GitHub App, through an installation token minted from the app's key. */

const API = "https://api.github.com";
const HEADERS = { Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "woodshed-reports" };

export interface GitHubConfig {
  appId: string;
  installationId: string;
  /** The app's private key as PKCS#8 PEM, which Web Crypto can import; GitHub hands out PKCS#1. */
  privateKey: string;
  repo: string;
}

function base64url(bytes: ArrayBuffer | Uint8Array): string {
  const array = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (const b of array) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const b64 = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}

/** A ten-minute JWT identifying the app, backdated a minute against clock drift as GitHub recommends. */
export async function appJwt(appId: string, privateKey: string, now = Date.now()): Promise<string> {
  const iat = Math.floor(now / 1000) - 60;
  const enc = new TextEncoder();
  const header = base64url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const payload = base64url(enc.encode(JSON.stringify({ iat, exp: iat + 600, iss: appId })));
  const key = await crypto.subtle.importKey("pkcs8", pemToDer(privateKey), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(`${header}.${payload}`));
  return `${header}.${payload}.${base64url(signature)}`;
}

/** Installation tokens last an hour; one is reused across requests while this Worker instance lives. */
let cached: { token: string; expires: number } | undefined;

export function forgetToken() {
  cached = undefined;
}

async function installationToken(config: GitHubConfig, fetcher: typeof fetch, now: number): Promise<string> {
  if (cached && cached.expires - now > 5 * 60_000) return cached.token;
  const jwt = await appJwt(config.appId, config.privateKey, now);
  const res = await fetcher(`${API}/app/installations/${config.installationId}/access_tokens`, {
    method: "POST",
    headers: { ...HEADERS, Authorization: `Bearer ${jwt}` },
  });
  if (!res.ok) throw new Error(`installation token: ${res.status}`);
  const { token, expires_at } = (await res.json()) as { token: string; expires_at: string };
  cached = { token, expires: Date.parse(expires_at) };
  return token;
}

/** Opens the issue and returns its number and address. */
export async function createIssue(
  config: GitHubConfig,
  issue: { title: string; body: string; labels: string[] },
  fetcher: typeof fetch = fetch,
  now = Date.now(),
): Promise<{ number: number; url: string }> {
  const token = await installationToken(config, fetcher, now);
  const res = await fetcher(`${API}/repos/${config.repo}/issues`, {
    method: "POST",
    headers: { ...HEADERS, Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(issue),
  });
  if (!res.ok) throw new Error(`create issue: ${res.status}`);
  const { number, html_url } = (await res.json()) as { number: number; html_url: string };
  return { number, url: html_url };
}
