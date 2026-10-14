module.exports = {
  extends: ["@commitlint/config-conventional"],
  // Dependabot's subjects already conform; its bodies carry release notes and long link lists it gives no way to
  // wrap, so its signed-off commits are skipped rather than loosening the rules for everyone.
  ignores: [(message) => message.includes("Signed-off-by: dependabot[bot]")],
  rules: {
    "scope-empty": [2, "never"],
    "scope-enum": [2, "always", ["app", "data", "tools", "plan", "docs", "design", "ci", "deps", "build", "tests", "release", "worker"]],
  },
};
