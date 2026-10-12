module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-empty": [2, "never"],
    "scope-enum": [2, "always", ["app", "data", "tools", "plan", "docs", "design", "ci", "deps", "build", "tests"]],
  },
};
