/** A problem report as the app sends it. It matches the app's Report, which renders the same title and body. */
export interface Report {
  subject: "TUNE" | "SOLO" | "EXERCISE" | "APP";
  problem: string;
  item?: string;
  id?: string;
  details?: [string, string][];
  note?: string;
  appVersion: string;
  androidVersion: string;
}

const SUBJECT_LABELS: Record<Report["subject"], string> = { TUNE: "Tune", SOLO: "Solo", EXERCISE: "Exercise", APP: "App" };

/** Reports are short; anything near these limits is not one. The note limit matches the app's. */
export const LIMITS = { body: 16_000, problem: 100, item: 200, id: 200, details: 20, detailKey: 100, detailValue: 500, note: 1_500, version: 40 };

/** The report in [input], or a reason it is not one. */
export function parseReport(input: unknown): Report | string {
  if (typeof input !== "object" || input === null) return "expected a JSON object";
  const r = input as Record<string, unknown>;
  if (typeof r.subject !== "string" || !Object.hasOwn(SUBJECT_LABELS, r.subject)) return "unknown subject";
  const text = (key: string, max: number, required = false): string | undefined | null => {
    const v = r[key];
    if (v === undefined || v === null) return required ? null : undefined;
    return typeof v === "string" && v.length <= max && (!required || v.trim() !== "") ? v : null;
  };
  const problem = text("problem", LIMITS.problem, true);
  const item = text("item", LIMITS.item);
  const id = text("id", LIMITS.id);
  const note = text("note", LIMITS.note);
  const appVersion = text("appVersion", LIMITS.version, true);
  const androidVersion = text("androidVersion", LIMITS.version, true);
  if (problem == null) return "missing or overlong problem";
  if (item === null || id === null || note === null) return "overlong item, id or note";
  if (appVersion == null || androidVersion == null) return "missing versions";

  let details: [string, string][] | undefined;
  if (r.details !== undefined) {
    if (!Array.isArray(r.details) || r.details.length > LIMITS.details) return "too many details";
    details = [];
    for (const d of r.details) {
      if (!Array.isArray(d) || d.length !== 2) return "details must be [label, value] pairs";
      const [k, v] = d;
      if (typeof k !== "string" || typeof v !== "string" || k.length > LIMITS.detailKey || v.length > LIMITS.detailValue) {
        return "overlong detail";
      }
      details.push([k, v]);
    }
  }
  return { subject: r.subject as Report["subject"], problem, item, id, details, note, appVersion, androidVersion };
}

/**
 * Keeps reports from notifying anyone or anything: a zero-width space defuses @mentions, #123 and owner/repo#123
 * issue references, GH-123, and links to GitHub, which would otherwise put a cross-reference on that issue.
 */
function defuse(s: string): string {
  return s
    .replace(/@/g, "@\u200b")
    .replace(/#(?=\d)/g, "#\u200b")
    .replace(/\bGH-(?=\d)/gi, (m) => `${m.slice(0, 2)}\u200b-`)
    .replace(/github\.com/gi, (m) => `${m.slice(0, 6)}\u200b${m.slice(6)}`);
}

/** Also keeps text from closing the code span it sits in. */
function inline(s: string): string {
  return defuse(s.replace(/[`\r\n]/g, " "));
}

function plain(s: string): string {
  return defuse(s.replace(/\r/g, ""));
}

/** The issue the report becomes: the same title, body and label as the app's own GitHub link, plus from-app. */
export function issueFor(report: Report): { title: string; body: string; labels: string[] } {
  const item = report.item?.trim() ? inline(report.item) : undefined;
  const title = [inline(report.problem), item && `in "${item}"`].filter(Boolean).join(" ");
  const lines = [
    `**${SUBJECT_LABELS[report.subject]}:** ${item ?? "Woodshed"}${report.id ? ` (\`${inline(report.id)}\`)` : ""}`,
    `**Problem:** ${inline(report.problem)}`,
  ];
  if (report.details?.length) {
    lines.push("", "What the app shows:", ...report.details.map(([k, v]) => `- ${inline(k)}: ${inline(v)}`));
  }
  lines.push(
    "",
    "**What it should be, or what happened:**",
    report.note?.trim() ? plain(report.note.trim()) : "(please describe)",
    "",
    `Woodshed ${inline(report.appVersion)}, Android ${inline(report.androidVersion)}`,
    "",
    "_Sent from the app._",
  );
  const kind = report.subject === "APP" && report.problem === "An idea" ? "enhancement" : "bug";
  return { title, body: lines.join("\n"), labels: [kind, "from-app"] };
}
