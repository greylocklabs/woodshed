import { createIssue } from "./github.ts";
import { LIMITS, issueFor, parseReport } from "./report.ts";

export interface Env {
  GITHUB_APP_ID: string;
  GITHUB_INSTALLATION_ID: string;
  GITHUB_PRIVATE_KEY: string;
  GITHUB_REPO: string;
  REPORT_LIMITER: RateLimit;
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/** POST /report files a report from the app as a GitHub issue. Nothing else is served. */
export async function handle(request: Request, env: Env, fetcher: typeof fetch = fetch): Promise<Response> {
  const url = new URL(request.url);
  if (url.pathname !== "/report") return json(404, { error: "not found" });
  if (request.method !== "POST") return json(405, { error: "use POST" });

  const address = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const { success } = await env.REPORT_LIMITER.limit({ key: address });
  if (!success) return json(429, { error: "too many reports; try again in a minute" });

  const text = await request.text();
  if (text.length > LIMITS.body) return json(413, { error: "report too large" });
  let input: unknown;
  try {
    input = JSON.parse(text);
  } catch {
    return json(400, { error: "not JSON" });
  }
  const report = parseReport(input);
  if (typeof report === "string") return json(400, { error: report });

  try {
    const issue = await createIssue(
      { appId: env.GITHUB_APP_ID, installationId: env.GITHUB_INSTALLATION_ID, privateKey: env.GITHUB_PRIVATE_KEY, repo: env.GITHUB_REPO },
      issueFor(report),
      fetcher,
    );
    return json(201, issue);
  } catch (e) {
    console.error("filing the report failed", e);
    return json(502, { error: "could not file the report" });
  }
}

export default {
  fetch: (request, env) => handle(request, env),
} satisfies ExportedHandler<Env>;
