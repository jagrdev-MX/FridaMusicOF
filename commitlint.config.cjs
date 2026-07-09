module.exports = {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "type-enum": [
      2,
      "always",
      [
        "build",
        "chore",
        "ci",
        "docs",
        "feat",
        "fix",
        "improve",
        "improvement",
        "perf",
        "refactor",
        "revert",
        "security",
        "style",
        "test"
      ]
    ]
  }
};
