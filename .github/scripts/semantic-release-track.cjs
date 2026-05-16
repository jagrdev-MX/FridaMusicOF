const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const ROOT = path.resolve(__dirname, "..", "..");
const OWNER = "jagrdev-MX";
const PROJECT = "FridaMusic TM";

const TRACKS = {
  android: {
    key: "android",
    displayName: "Android APK",
    releaseKind: "ANDROID",
    changelogFile: "CHANGELOG.android.md",
    header: [
      "# Android APK Changelog",
      "",
      "Canonical automatic changelog for the Android APK track.",
      "Tag family: `vX.Y.Z-release-apk`",
      "",
      "New entries are generated automatically from real Conventional Commits that touch Android files."
    ].join("\n"),
    matches(file) {
      return file.startsWith("app/") ||
        file.startsWith("gradle/") ||
        ["build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"].includes(file);
    }
  },
  web: {
    key: "web",
    displayName: "Web",
    releaseKind: "WEB",
    changelogFile: "CHANGELOG.web.md",
    header: [
      "# Web Changelog",
      "",
      "Canonical automatic changelog for the website track.",
      "Tag family: `vX.Y.Z-web`",
      "",
      "New entries are generated automatically from real Conventional Commits that touch website files."
    ].join("\n"),
    matches(file) {
      return ["index.html", "styles.css", "script.js", "vercel.json", "logo1.png", "package.json", "package-lock.json"].includes(file) ||
        file.startsWith("capturas web/");
    }
  },
  "telegram-bot": {
    key: "telegram-bot",
    displayName: "Telegram Bot",
    releaseKind: "TELEGRAM BOT",
    changelogFile: "CHANGELOG.telegram-bot.md",
    header: [
      "# Telegram Bot Changelog",
      "",
      "Canonical automatic changelog for the Telegram Bot track.",
      "Tag family: `vX.Y.Z-telegram-bot`",
      "",
      "New entries are generated automatically from real Conventional Commits that touch Telegram automation files."
    ].join("\n"),
    matches(file) {
      return file === ".github/workflows/telegram-release.yml" || file.startsWith("assets/telegram/");
    }
  }
};

const CATEGORY_DEFS = [
  { key: "features", title: "\u2728 Features", types: ["feat"] },
  { key: "improvements", title: "\u{1F6E0}\uFE0F Improvements", types: ["perf", "improve", "improvement"] },
  { key: "fixes", title: "\u{1F41B} Fixes", types: ["fix"] },
  { key: "tech", title: "\u2699\uFE0F Tech", types: ["build", "ci", "chore", "style", "test"] },
  { key: "docs", title: "\u{1F4DA} Docs", types: ["docs"] },
  { key: "refactor", title: "\u267B\uFE0F Refactor", types: ["refactor"] },
  { key: "security", title: "\u{1F512} Security", types: ["security"] }
];

const CATEGORY_BY_TYPE = new Map(
  CATEGORY_DEFS.flatMap((category) => category.types.map((type) => [type, category]))
);

function normalizeLineEndings(value) {
  return value.replace(/\r\n/g, "\n");
}

function normalizeFile(file) {
  return file.replace(/\\/g, "/").trim();
}

function getTrack(trackKey) {
  const track = TRACKS[trackKey];
  if (!track) throw new Error(`Unknown release track: ${trackKey}`);
  return track;
}

function getChangedFiles(hash) {
  if (!hash) return [];
  const output = execFileSync(
    "git",
    ["diff-tree", "--no-commit-id", "--name-only", "-r", "-m", hash],
    { cwd: ROOT, encoding: "utf8" }
  );
  return [...new Set(output.split("\n").map(normalizeFile).filter(Boolean))];
}

function parseConventionalCommit(commit) {
  const subject = commit.subject || String(commit.message || "").split("\n")[0] || "";
  const match = subject.match(/^([a-zA-Z]+)(?:\(([^)]+)\))?(!)?:\s+(.+)$/);
  if (!match) return null;

  const [, rawType, scope, bang, rawDescription] = match;
  const type = rawType.toLowerCase();
  const normalizedScope = scope || "";
  if (type === "chore" && normalizedScope.toLowerCase() === "release") return null;

  const category = CATEGORY_BY_TYPE.get(type);
  if (!category) return null;

  const hash = commit.hash || commit.gitHead || "";
  const message = commit.message || subject;
  const breaking = Boolean(bang) || /BREAKING[ -]CHANGE:/i.test(message);

  return {
    type,
    scope: normalizedScope,
    description: rawDescription.trim(),
    hash,
    shortHash: hash ? hash.slice(0, 7) : "",
    breaking,
    category
  };
}

function filterTrackCommits(commits, trackKey) {
  const track = getTrack(trackKey);
  return commits.filter((commit) => getChangedFiles(commit.hash || commit.gitHead || "").some((file) => track.matches(file)));
}

function uniqueEntries(commits) {
  const seen = new Set();
  const entries = [];

  for (const commit of commits) {
    const parsed = parseConventionalCommit(commit);
    if (!parsed) continue;

    const key = `${parsed.category.key}|${parsed.description.toLowerCase()}`;
    if (seen.has(key)) continue;
    seen.add(key);
    entries.push(parsed);
  }

  return entries;
}

function groupEntries(entries) {
  return CATEGORY_DEFS.map((category) => ({
    ...category,
    entries: entries.filter((entry) => entry.category.key === category.key)
  })).filter((group) => group.entries.length > 0);
}

function releaseTypeFor(entries) {
  if (entries.some((entry) => entry.breaking)) return "major";
  if (entries.some((entry) => entry.type === "feat")) return "minor";
  if (entries.some((entry) => ["fix", "security", "perf", "improve", "improvement"].includes(entry.type))) return "patch";
  return null;
}

function summaryText({ count, lastTag, nextTag, track }) {
  const from = lastTag || "the previous release";
  const englishCount = `${count} confirmed Conventional Commit${count === 1 ? "" : "s"}`;
  const spanishCount = count === 1 ? "1 commit convencional confirmado" : `${count} commits convencionales confirmados`;
  return {
    en: `${track.displayName} release with ${englishCount} from ${from} to ${nextTag}.`,
    es: `Release de ${track.displayName} con ${spanishCount} desde ${from} hasta ${nextTag}.`
  };
}

function renderEntry(entry) {
  const suffix = entry.shortHash ? ` (${entry.shortHash})` : "";
  return `- ${entry.description}${suffix}`;
}

function renderNotes({ trackKey, version, date, lastTag, releaseType, commits }) {
  const track = getTrack(trackKey);
  const trackCommits = filterTrackCommits(commits, trackKey);
  const entries = uniqueEntries(trackCommits);
  const groups = groupEntries(entries);
  const nextTag = tagFor(trackKey, version);
  const summary = summaryText({ count: entries.length, lastTag, nextTag, track });

  const hiddenMetadata = [
    "<!--",
    `TYPE: ${track.releaseKind}`,
    `LEVEL: ${String(releaseType || "patch").toUpperCase()}`,
    "STAGE: RELEASE",
    "",
    "SUMMARY_EN:",
    summary.en,
    "",
    "SUMMARY_ES:",
    summary.es,
    "",
    "CHANGES:",
    ...(entries.length > 0 ? entries.map(renderEntry) : ["- No qualifying Conventional Commits were detected."]),
    "-->"
  ];

  const lines = [
    ...hiddenMetadata,
    "",
    `## [${nextTag}] - ${date}`,
    "",
    `**Owner:** ${OWNER}  `,
    `**Project:** ${PROJECT}  `,
    `**Track:** ${track.displayName}`,
    ""
  ];

  for (const group of groups) {
    lines.push(`### ${group.title}`);
    lines.push(...group.entries.map(renderEntry));
    lines.push("");
  }

  lines.push("### \u{1F680} Release Notes");
  lines.push(`- ES: ${summary.es}`);
  lines.push(`- EN: ${summary.en}`);

  return lines.join("\n").trimEnd();
}

function tagFor(trackKey, version) {
  if (trackKey === "android") return `v${version}-release-apk`;
  if (trackKey === "web") return `v${version}-web`;
  return `v${version}-telegram-bot`;
}

function ensureTrackChangelog(content, trackKey) {
  const track = getTrack(trackKey);
  const normalized = normalizeLineEndings(content || "").trim();
  if (!normalized) return `${track.header}\n`;
  if (normalized.startsWith(track.header)) return `${normalized}\n`;
  return `${track.header}\n\n${normalized}\n`;
}

function insertReleaseBlock(content, notes, version, trackKey) {
  const managed = ensureTrackChangelog(content, trackKey);
  const headingPrefix = `## [${tagFor(trackKey, version)}] - `;
  if (managed.includes(headingPrefix)) return managed;
  return `${managed.trimEnd()}\n\n${notes}\n`;
}

function readGitCommits(from, to) {
  const range = from ? `${from}..${to || "HEAD"}` : to || "HEAD";
  const output = execFileSync(
    "git",
    ["log", "--format=%H%x1f%s%x1f%b%x1e", range],
    { cwd: ROOT, encoding: "utf8" }
  );

  return output
    .split("\x1e")
    .map((record) => record.trim())
    .filter(Boolean)
    .map((record) => {
      const [hash, subject, body = ""] = record.split("\x1f");
      return {
        hash,
        subject,
        message: body ? `${subject}\n\n${body}` : subject
      };
    });
}

function validateChangelog(content, trackKey) {
  const tagPattern = trackKey === "android"
    ? /^## \[v(\d+\.\d+\.\d+)-release-apk\] - /gm
    : trackKey === "web"
      ? /^## \[v(\d+\.\d+\.\d+)-web\] - /gm
      : /^## \[v(\d+\.\d+\.\d+)-telegram-bot\] - /gm;
  const matches = [...normalizeLineEndings(content).matchAll(tagPattern)];
  const seen = new Set();
  const duplicates = [];

  for (const match of matches) {
    const version = match[1];
    if (seen.has(version)) duplicates.push(version);
    seen.add(version);
  }

  return duplicates;
}

async function analyzeCommits(pluginConfig, context) {
  const trackCommits = filterTrackCommits(context.commits, pluginConfig.track);
  const entries = uniqueEntries(trackCommits);
  return releaseTypeFor(entries);
}

async function generateNotes(pluginConfig, context) {
  return renderNotes({
    trackKey: pluginConfig.track,
    version: context.nextRelease.version,
    date: new Date().toISOString().slice(0, 10),
    lastTag: context.lastRelease.gitTag,
    releaseType: context.nextRelease.type,
    commits: context.commits
  });
}

async function prepare(pluginConfig, context) {
  const track = getTrack(pluginConfig.track);
  const changelogPath = path.join(ROOT, track.changelogFile);
  const current = fs.existsSync(changelogPath) ? fs.readFileSync(changelogPath, "utf8") : "";
  const updated = insertReleaseBlock(current, context.nextRelease.notes, context.nextRelease.version, pluginConfig.track);

  if (updated !== current) {
    fs.writeFileSync(changelogPath, updated, "utf8");
  }
}

function parseArgs(argv) {
  const result = {};
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith("--")) continue;
    const key = token.slice(2);
    const next = argv[index + 1];
    if (!next || next.startsWith("--")) {
      result[key] = true;
      continue;
    }
    result[key] = next;
    index += 1;
  }
  return result;
}

function runCli() {
  const [command, ...rest] = process.argv.slice(2);
  const args = parseArgs(rest);

  if (command === "preview") {
    const trackKey = args.track;
    if (!trackKey) throw new Error("preview requires --track android|web|telegram-bot");
    const version = args.version || "0.0.0";
    const date = args.date || new Date().toISOString().slice(0, 10);
    const commits = readGitCommits(args.from, args.to || "HEAD");
    const entries = uniqueEntries(filterTrackCommits(commits, trackKey));
    process.stdout.write(
      `${renderNotes({
        trackKey,
        version,
        date,
        lastTag: args.from || "",
        releaseType: args.type || releaseTypeFor(entries) || "patch",
        commits
      })}\n`
    );
    return;
  }

  if (command === "validate") {
    let hasErrors = false;
    for (const trackKey of Object.keys(TRACKS)) {
      const track = getTrack(trackKey);
      const changelogPath = path.join(ROOT, track.changelogFile);
      const current = fs.existsSync(changelogPath) ? fs.readFileSync(changelogPath, "utf8") : "";
      const duplicates = validateChangelog(current, trackKey);
      if (duplicates.length > 0) {
        console.error(`${track.displayName}: duplicate release headings found: ${duplicates.join(", ")}`);
        hasErrors = true;
      } else {
        console.log(`${track.displayName}: no duplicate canonical release headings found.`);
      }
    }
    if (hasErrors) process.exitCode = 1;
    return;
  }

  console.error("Usage:");
  console.error("  node .github/scripts/semantic-release-track.cjs preview --track web --from v1.0.0-web --to HEAD --version 2.1.0");
  console.error("  node .github/scripts/semantic-release-track.cjs validate");
  process.exitCode = 1;
}

module.exports = {
  analyzeCommits,
  generateNotes,
  prepare
};

if (require.main === module) {
  runCli();
}
