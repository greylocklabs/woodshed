import assert from "node:assert/strict";
import { beforeEach, describe, it } from "node:test";
import { appJwt, createIssue, forgetToken } from "../src/github.ts";
import { type Env, handle } from "../src/index.ts";
import { issueFor, parseReport } from "../src/report.ts";

const report = {
  subject: "TUNE",
  problem: "Wrong key",
  item: "Solar",
  id: "solar",
  details: [["Key", "concert C minor"]],
  note: "It's C minor, but @someone said D.",
  appVersion: "0.1.0",
  androidVersion: "16",
};

async function keyPair() {
  const pair = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  const der = new Uint8Array((await crypto.subtle.exportKey("pkcs8", pair.privateKey)) as ArrayBuffer);
  const b64 = btoa(String.fromCharCode(...der));
  return { pem: `-----BEGIN PRIVATE KEY-----\n${b64.match(/.{1,64}/g)!.join("\n")}\n-----END PRIVATE KEY-----\n`, publicKey: pair.publicKey };
}

function fromBase64url(s: string): Uint8Array {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

/** Answers GitHub's two calls and records what was sent. */
function fakeGitHub(status = 201) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fetcher = (async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    if (url.endsWith("/access_tokens")) return Response.json({ token: "inst-token", expires_at: new Date(Date.now() + 3_600_000).toISOString() });
    return Response.json({ number: 42, html_url: "https://github.com/lararosekelley/woodshed/issues/42" }, { status });
  }) as unknown as typeof fetch;
  return { calls, fetcher };
}

function env(pem: string, allowed = true): Env {
  return {
    GITHUB_APP_ID: "123",
    GITHUB_INSTALLATION_ID: "456",
    GITHUB_PRIVATE_KEY: pem,
    GITHUB_REPO: "lararosekelley/woodshed",
    REPORT_LIMITER: { limit: async () => ({ success: allowed }) } as unknown as RateLimit,
  };
}

const post = (body: unknown) =>
  new Request("https://reports.example/report", { method: "POST", body: typeof body === "string" ? body : JSON.stringify(body), headers: { "CF-Connecting-IP": "1.2.3.4" } });

beforeEach(() => forgetToken());

describe("reports", () => {
  it("become the same issue the app's GitHub link opens, marked as sent from the app", () => {
    const parsed = parseReport(report);
    if (typeof parsed === "string") throw new Error(parsed);
    const issue = issueFor(parsed);
    assert.equal(issue.title, 'Wrong key in "Solar"');
    assert.ok(issue.body.includes("**Tune:** Solar (`solar`)"));
    assert.ok(issue.body.includes("- Key: concert C minor"));
    assert.ok(issue.body.includes("Woodshed 0.1.0, Android 16"));
    assert.deepEqual(issue.labels, ["bug", "from-app"]);
    assert.deepEqual(issueFor({ ...parsed, subject: "APP", problem: "An idea" }).labels, ["enhancement", "from-app"]);
  });

  it("cannot mention people or break out of code spans", () => {
    const parsed = parseReport({ ...report, id: "x` @evil `y" });
    if (typeof parsed === "string") throw new Error(parsed);
    const { body } = issueFor(parsed);
    assert.doesNotMatch(body, /@someone|@evil/);
    assert.ok(body.includes("(`x  @​evil  y`)"));
  });

  it("are refused when malformed or oversized", () => {
    assert.equal(typeof parseReport(null), "string");
    assert.equal(parseReport({ ...report, subject: "PLAYLIST" }), "unknown subject");
    assert.equal(parseReport({ ...report, problem: "" }), "missing or overlong problem");
    assert.equal(parseReport({ ...report, note: "x".repeat(1_501) }), "overlong item, id or note");
    assert.equal(parseReport({ ...report, details: [["only one"]] }), "details must be [label, value] pairs");
    assert.equal(parseReport({ ...report, appVersion: undefined }), "missing versions");
  });
});

describe("the GitHub app", () => {
  it("signs a JWT GitHub can verify with the app's public key", async () => {
    const { pem, publicKey } = await keyPair();
    const jwt = await appJwt("123", pem, 1_000_000_000_000);
    const [header, payload, signature] = jwt.split(".");
    const claims = JSON.parse(new TextDecoder().decode(fromBase64url(payload)));
    assert.deepEqual(claims, { iat: 999_999_940, exp: 1_000_000_540, iss: "123" });
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, fromBase64url(signature), new TextEncoder().encode(`${header}.${payload}`));
    assert.equal(valid, true);
  });

  it("files the issue with an installation token, minted once and reused", async () => {
    const { pem } = await keyPair();
    const { calls, fetcher } = fakeGitHub();
    const config = { appId: "123", installationId: "456", privateKey: pem, repo: "lararosekelley/woodshed" };
    const issue = { title: "t", body: "b", labels: ["bug"] };
    assert.deepEqual(await createIssue(config, issue, fetcher), { number: 42, url: "https://github.com/lararosekelley/woodshed/issues/42" });
    await createIssue(config, issue, fetcher);
    assert.deepEqual(calls.map((c) => c.url), [
      "https://api.github.com/app/installations/456/access_tokens",
      "https://api.github.com/repos/lararosekelley/woodshed/issues",
      "https://api.github.com/repos/lararosekelley/woodshed/issues",
    ]);
    assert.equal((calls[1].init.headers as Record<string, string>).Authorization, "Bearer inst-token");
  });
});

describe("the endpoint", () => {
  it("files a valid report and answers with the issue", async () => {
    const { pem } = await keyPair();
    const res = await handle(post(report), env(pem), fakeGitHub().fetcher);
    assert.equal(res.status, 201);
    assert.deepEqual(await res.json(), { number: 42, url: "https://github.com/lararosekelley/woodshed/issues/42" });
  });

  it("turns away other paths, other methods, bad bodies, floods and GitHub failures", async () => {
    const { pem } = await keyPair();
    const { fetcher } = fakeGitHub();
    assert.equal((await handle(new Request("https://reports.example/"), env(pem), fetcher)).status, 404);
    assert.equal((await handle(new Request("https://reports.example/report"), env(pem), fetcher)).status, 405);
    assert.equal((await handle(post("{not json"), env(pem), fetcher)).status, 400);
    assert.equal((await handle(post({ ...report, subject: "?" }), env(pem), fetcher)).status, 400);
    assert.equal((await handle(post("x".repeat(20_000)), env(pem), fetcher)).status, 413);
    assert.equal((await handle(post(report), env(pem, false), fetcher)).status, 429);
    assert.equal((await handle(post(report), env(pem), fakeGitHub(500).fetcher)).status, 502);
  });
});
