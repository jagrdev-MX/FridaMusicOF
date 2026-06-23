const { execSync } = require("node:child_process");

const METRICS_COMMIT_PREFIX = "docs: actualizar metricas automaticas de contribucion";

function normalize(value) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .trim();
}

function readCommitMessage() {
  if (process.env.VERCEL_GIT_COMMIT_MESSAGE) {
    return process.env.VERCEL_GIT_COMMIT_MESSAGE;
  }

  try {
    return execSync("git log -1 --pretty=%B", { encoding: "utf8" });
  } catch (error) {
    console.warn(`No se pudo leer el mensaje del commit: ${error.message}`);
    return "";
  }
}

const commitMessage = normalize(readCommitMessage());

if (commitMessage.startsWith(METRICS_COMMIT_PREFIX)) {
  console.log("Commit automatico de metricas detectado. Vercel omitira este build.");
  process.exit(0);
}

console.log("Cambio publicable detectado. Vercel continuara el build.");
process.exit(1);
