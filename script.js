const RELEASE_ENDPOINT = 'https://api.github.com/repos/jagrdev-MX/FridaMusicOF/releases?per_page=30';
const RELEASES_URL = 'https://github.com/jagrdev-MX/FridaMusicOF/releases';
const CHANGELOG_URL = 'https://github.com/jagrdev-MX/FridaMusicOF/blob/master/CHANGELOG.android.md';

const translations = {
  es: {
    'nav.home': 'Inicio',
    'nav.identity': 'Identidad',
    'nav.features': 'Características',
    'nav.interface': 'Interfaz',
    'nav.downloads': 'Descargas',
    'nav.repository': 'Repositorio',
    'nav.support': 'Soporte',
    'nav.menu': 'Abrir navegación',
    'hero.download': 'Descarga oficial',
    'hero.tagline': 'Donde la elegancia y la música se encuentran. Una experiencia visual inmersiva diseñada para sentir cada nota.',
    'hero.repository': 'Repositorio',
    'hero.fridaLabs': 'Frida Labs',
    'hero.jagr': 'JAGR Developer',
    'hero.support': 'Soporte Técnico',
    'identity.title': 'Identidad de Marca',
    'identity.desc': 'FridaMusic combina la elegancia del vidrio con la energía de los colores neon, creando un ecosistema visual único para tu música.',
    'features.kicker': 'Características',
    'features.title': 'Lo que ya hace FridaMusic',
    'features.subtitle': 'Funciones respaldadas por la app Android actual y su arquitectura en evolución.',
    'features.library.title': 'Biblioteca local organizada',
    'features.library.desc': 'Escanea la música del dispositivo, muestra canciones recientes y permite filtrar notas de voz para mantener la colección más limpia.',
    'features.search.title': 'Búsqueda y descubrimiento',
    'features.search.desc': 'Busca canciones, artistas y playlists, conserva historial de búsquedas y combina resultados remotos con tu biblioteca local.',
    'features.player.title': 'Reproducción fluida',
    'features.player.desc': 'Incluye play/pause, anterior, siguiente, seek, repeat, miniplayer y una vista inmersiva para controlar cada pista con claridad.',
    'features.playlists.title': 'Playlists, favoritos e historial',
    'features.playlists.desc': 'Permite crear playlists, marcar canciones como favoritas y recuperar reproducciones recientes desde una base local persistente.',
    'features.lyrics.title': 'Letras y metadatos visuales',
    'features.lyrics.desc': 'Integra carátulas, artistas, títulos y letras; cuando existen datos sincronizados, la vista de letras puede seguir la reproducción.',
    'features.design.title': 'Diseño Vitrea inmersivo',
    'features.design.desc': 'Interfaz oscura con glassmorphism, gradientes y microdetalles visuales consistentes entre inicio, biblioteca, reproductor y ajustes.',
    'features.settings.title': 'Ajustes útiles de experiencia',
    'features.settings.desc': 'Ofrece temporizador, ecualizador del sistema, tema visual, escaneo manual, filtro de notas de voz y restauración de la última sesión.',
    'features.android.title': 'Android en evolución',
    'features.android.desc': 'FridaMusic está centrada en Android y sigue creciendo: ya tiene base funcional sólida, con módulos y pantallas que continúan refinándose.',
    'interface.kicker': 'Interfaz',
    'interface.title': 'Un vistazo a FridaMusic',
    'interface.subtitle': 'Pantallas reales del flujo visual con microdetalles pensados para una experiencia inmersiva.',
    'interface.home': 'Home - bienvenida dinámica',
    'interface.player': 'Now Playing - control inmersivo',
    'interface.library': 'Library - mosaico musical',
    'interface.settings': 'Settings - ajustes precisos',
    'downloads.kicker': 'Descargas',
    'downloads.title': 'Instala FridaMusic',
    'downloads.copy': 'La disponibilidad se obtiene desde las releases Android públicas del repositorio para evitar versiones inventadas o enlaces obsoletos.',
    'downloads.platform': 'Plataforma',
    'downloads.android': 'Android',
    'downloads.available': 'Disponible',
    'downloads.requirements': 'Requisito',
    'downloads.requirementsValue': 'Android API 24+',
    'downloads.distribution': 'Distribución',
    'downloads.distributionValue': 'APK de release',
    'downloads.versionLabel': 'Versión actual',
    'downloads.loading': 'Consultando la última release Android pública...',
    'downloads.readyWithApk': 'APK detectada en la última release Android pública.',
    'downloads.readyWithoutApk': 'La última release Android pública existe, pero no expone un APK descargable.',
    'downloads.empty': 'Todavía no hay una release Android pública disponible.',
    'downloads.error': 'No se pudo consultar GitHub en este momento. Usa el enlace de releases para revisar la descarga manualmente.',
    'downloads.downloadApk': 'Descargar APK',
    'downloads.unavailable': 'APK no disponible',
    'downloads.releases': 'Ver releases',
    'downloads.changelog': 'Ver changelog',
    'downloads.previous': 'Versiones anteriores',
    'support.kicker': 'Soporte',
    'support.title': 'Ayuda y seguimiento',
    'support.copy': 'Canales reales del proyecto para soporte, incidencias y seguimiento público.',
    'support.emailTitle': 'Soporte directo',
    'support.emailCopy': 'Usa el correo oficial ya publicado en la landing para consultas de soporte.',
    'support.emailAction': 'Escribir por correo',
    'support.issuesTitle': 'Incidencias',
    'support.issuesCopy': 'Reporta problemas o mejoras en el tablero público de issues del repositorio.',
    'support.issuesAction': 'Abrir issues',
    'team.title': 'Equipo',
    'team.roleCeo': 'CEO DEV',
    'team.roleCoFounder': 'Co-fundador Dev',
    'team.accountantTitle': 'Contador y técnico en sistemas',
    'team.softwareTitle': 'Ingeniero de software',
    'team.profile': 'Ver perfil',
    'footer.copy': '© 2026 Frida Labs MX. Hecho con pasión por el diseño.',
  },
  en: {
    'nav.home': 'Home',
    'nav.identity': 'Identity',
    'nav.features': 'Features',
    'nav.interface': 'Interface',
    'nav.downloads': 'Downloads',
    'nav.repository': 'Repository',
    'nav.support': 'Support',
    'nav.menu': 'Open navigation',
    'hero.download': 'Official Download',
    'hero.tagline': 'Where elegance and music meet. An immersive visual experience designed to feel every note.',
    'hero.repository': 'Repository',
    'hero.fridaLabs': 'Frida Labs',
    'hero.jagr': 'JAGR Developer',
    'hero.support': 'Technical Support',
    'identity.title': 'Brand Identity',
    'identity.desc': 'FridaMusic combines glass-like elegance with neon energy to create a distinctive visual ecosystem for your music.',
    'features.kicker': 'Features',
    'features.title': 'What FridaMusic already does',
    'features.subtitle': 'Functions backed by the current Android app and its evolving architecture.',
    'features.library.title': 'Organized local library',
    'features.library.desc': 'Scans device music, shows recent songs, and can filter voice notes to keep the collection cleaner.',
    'features.search.title': 'Search and discovery',
    'features.search.desc': 'Search songs, artists, and playlists, keep search history, and combine remote results with the local library.',
    'features.player.title': 'Fluid playback',
    'features.player.desc': 'Includes play/pause, previous, next, seek, repeat, miniplayer, and an immersive view to control each track clearly.',
    'features.playlists.title': 'Playlists, favorites, and history',
    'features.playlists.desc': 'Create playlists, mark songs as favorites, and recover recent playback from persistent local storage.',
    'features.lyrics.title': 'Lyrics and visual metadata',
    'features.lyrics.desc': 'Integrates artwork, artists, titles, and lyrics; when synced data exists, the lyrics view can follow playback.',
    'features.design.title': 'Immersive Vitrea design',
    'features.design.desc': 'Dark interface with glassmorphism, gradients, and visual micro-details across home, library, player, and settings.',
    'features.settings.title': 'Useful experience settings',
    'features.settings.desc': 'Offers timer, system equalizer, visual theme, manual scan, voice-note filtering, and last-session restore.',
    'features.android.title': 'Android in evolution',
    'features.android.desc': 'FridaMusic is Android-first and still growing: it already has a solid functional base while modules and screens keep being refined.',
    'interface.kicker': 'Interface',
    'interface.title': 'A look at FridaMusic',
    'interface.subtitle': 'Real screens from the visual flow with micro-details designed for an immersive experience.',
    'interface.home': 'Home - dynamic welcome',
    'interface.player': 'Now Playing - immersive control',
    'interface.library': 'Library - music mosaic',
    'interface.settings': 'Settings - precise controls',
    'downloads.kicker': 'Downloads',
    'downloads.title': 'Install FridaMusic',
    'downloads.copy': 'Availability is pulled from the repository public Android releases so versions and links are not invented or stale.',
    'downloads.platform': 'Platform',
    'downloads.android': 'Android',
    'downloads.available': 'Available',
    'downloads.requirements': 'Requirement',
    'downloads.requirementsValue': 'Android API 24+',
    'downloads.distribution': 'Distribution',
    'downloads.distributionValue': 'Release APK',
    'downloads.versionLabel': 'Current version',
    'downloads.loading': 'Checking the latest public Android release...',
    'downloads.readyWithApk': 'APK detected in the latest public Android release.',
    'downloads.readyWithoutApk': 'The latest public Android release exists, but it does not expose a downloadable APK.',
    'downloads.empty': 'There is no public Android release available yet.',
    'downloads.error': 'GitHub could not be queried right now. Use the releases link to check the download manually.',
    'downloads.downloadApk': 'Download APK',
    'downloads.unavailable': 'APK unavailable',
    'downloads.releases': 'View releases',
    'downloads.changelog': 'View changelog',
    'downloads.previous': 'Previous versions',
    'support.kicker': 'Support',
    'support.title': 'Help and tracking',
    'support.copy': 'Real project channels for support, issues, and public tracking.',
    'support.emailTitle': 'Direct support',
    'support.emailCopy': 'Use the official email already published on the landing page for support questions.',
    'support.emailAction': 'Send email',
    'support.issuesTitle': 'Issues',
    'support.issuesCopy': 'Report problems or improvements in the repository public issues board.',
    'support.issuesAction': 'Open issues',
    'team.title': 'Team',
    'team.roleCeo': 'CEO DEV',
    'team.roleCoFounder': 'Co-founder Dev',
    'team.accountantTitle': 'Accountant & Systems Tech',
    'team.softwareTitle': 'Software Engineer',
    'team.profile': 'View profile',
    'footer.copy': '© 2026 Frida Labs MX. Made with passion for design.',
  },
  pt: {
    'nav.home': 'Início',
    'nav.identity': 'Identidade',
    'nav.features': 'Características',
    'nav.interface': 'Interface',
    'nav.downloads': 'Downloads',
    'nav.repository': 'Repositório',
    'nav.support': 'Suporte',
    'nav.menu': 'Abrir navegação',
    'hero.download': 'Download oficial',
    'hero.tagline': 'Onde elegância e música se encontram. Uma experiência visual imersiva criada para sentir cada nota.',
    'hero.repository': 'Repositório',
    'hero.fridaLabs': 'Frida Labs',
    'hero.jagr': 'JAGR Developer',
    'hero.support': 'Suporte técnico',
    'identity.title': 'Identidade da Marca',
    'identity.desc': 'FridaMusic combina a elegância do vidro com a energia dos tons neon para criar um ecossistema visual único para sua música.',
    'features.kicker': 'Características',
    'features.title': 'O que FridaMusic já faz',
    'features.subtitle': 'Funções respaldadas pelo app Android atual e por sua arquitetura em evolução.',
    'features.library.title': 'Biblioteca local organizada',
    'features.library.desc': 'Escaneia as músicas do dispositivo, mostra faixas recentes e permite filtrar notas de voz para manter a coleção mais limpa.',
    'features.search.title': 'Busca e descoberta',
    'features.search.desc': 'Busca músicas, artistas e playlists, mantém histórico de busca e combina resultados remotos com a biblioteca local.',
    'features.player.title': 'Reprodução fluida',
    'features.player.desc': 'Inclui play/pause, anterior, próxima, seek, repeat, miniplayer e uma visão imersiva para controlar cada faixa com clareza.',
    'features.playlists.title': 'Playlists, favoritos e histórico',
    'features.playlists.desc': 'Permite criar playlists, marcar músicas como favoritas e recuperar reproduções recentes a partir de armazenamento local persistente.',
    'features.lyrics.title': 'Letras e metadados visuais',
    'features.lyrics.desc': 'Integra capas, artistas, títulos e letras; quando há dados sincronizados, a visão de letras pode acompanhar a reprodução.',
    'features.design.title': 'Design Vitrea imersivo',
    'features.design.desc': 'Interface escura com glassmorphism, gradientes e microdetalhes visuais em início, biblioteca, player e ajustes.',
    'features.settings.title': 'Ajustes úteis de experiência',
    'features.settings.desc': 'Oferece temporizador, equalizador do sistema, tema visual, escaneamento manual, filtro de notas de voz e restauração da última sessão.',
    'features.android.title': 'Android em evolução',
    'features.android.desc': 'FridaMusic é focado primeiro em Android e continua crescendo: já possui uma base funcional sólida enquanto módulos e telas seguem sendo refinados.',
    'interface.kicker': 'Interface',
    'interface.title': 'Um olhar para FridaMusic',
    'interface.subtitle': 'Telas reais do fluxo visual com microdetalhes pensados para uma experiência imersiva.',
    'interface.home': 'Home - boas-vindas dinâmicas',
    'interface.player': 'Now Playing - controle imersivo',
    'interface.library': 'Library - mosaico musical',
    'interface.settings': 'Settings - ajustes precisos',
    'downloads.kicker': 'Downloads',
    'downloads.title': 'Instale FridaMusic',
    'downloads.copy': 'A disponibilidade é obtida das releases Android públicas do repositório para evitar versões inventadas ou links desatualizados.',
    'downloads.platform': 'Plataforma',
    'downloads.android': 'Android',
    'downloads.available': 'Disponível',
    'downloads.requirements': 'Requisito',
    'downloads.requirementsValue': 'Android API 24+',
    'downloads.distribution': 'Distribuição',
    'downloads.distributionValue': 'APK de release',
    'downloads.versionLabel': 'Versão atual',
    'downloads.loading': 'Consultando a release Android pública mais recente...',
    'downloads.readyWithApk': 'APK detectado na release Android pública mais recente.',
    'downloads.readyWithoutApk': 'A release Android pública mais recente existe, mas não expõe um APK para download.',
    'downloads.empty': 'Ainda não há uma release Android pública disponível.',
    'downloads.error': 'Não foi possível consultar o GitHub agora. Use o link de releases para revisar o download manualmente.',
    'downloads.downloadApk': 'Baixar APK',
    'downloads.unavailable': 'APK indisponível',
    'downloads.releases': 'Ver releases',
    'downloads.changelog': 'Ver changelog',
    'downloads.previous': 'Versões anteriores',
    'support.kicker': 'Suporte',
    'support.title': 'Ajuda e acompanhamento',
    'support.copy': 'Canais reais do projeto para suporte, incidências e acompanhamento público.',
    'support.emailTitle': 'Suporte direto',
    'support.emailCopy': 'Use o e-mail oficial já publicado na landing para dúvidas de suporte.',
    'support.emailAction': 'Enviar e-mail',
    'support.issuesTitle': 'Incidências',
    'support.issuesCopy': 'Reporte problemas ou melhorias no quadro público de issues do repositório.',
    'support.issuesAction': 'Abrir issues',
    'team.title': 'Equipe',
    'team.roleCeo': 'CEO DEV',
    'team.roleCoFounder': 'Co-fundador Dev',
    'team.accountantTitle': 'Contador e técnico de sistemas',
    'team.softwareTitle': 'Engenheiro de software',
    'team.profile': 'Ver perfil',
    'footer.copy': '© 2026 Frida Labs MX. Feito com paixão por design.',
  },
};

const localeMap = {
  es: 'es-MX',
  en: 'en-US',
  pt: 'pt-BR',
};

let currentLanguage = localStorage.getItem('fridaMusicLanguage') || 'es';
let releaseState = {
  status: 'loading',
  version: '',
  publishedAt: '',
  htmlUrl: RELEASES_URL,
  assetUrl: '',
};

function translate(key) {
  return translations[currentLanguage]?.[key] ?? translations.es[key] ?? key;
}

function applyTranslations(language) {
  currentLanguage = translations[language] ? language : 'es';
  document.documentElement.lang = currentLanguage;
  localStorage.setItem('fridaMusicLanguage', currentLanguage);

  document.querySelectorAll('[data-i18n]').forEach((element) => {
    element.textContent = translate(element.dataset.i18n);
  });

  document.querySelectorAll('[data-i18n-aria-label]').forEach((element) => {
    element.setAttribute('aria-label', translate(element.dataset.i18nAriaLabel));
  });

  document.querySelectorAll('.language-option').forEach((button) => {
    button.setAttribute('aria-pressed', String(button.dataset.lang === currentLanguage));
  });

  renderReleaseState();
}

function renderReleaseState() {
  const versionValue = document.querySelector('[data-release-version]');
  const statusValue = document.querySelector('[data-release-status]');
  const releaseLink = document.querySelector('[data-release-link]');
  const changelogLink = document.querySelector('[data-changelog-link]');
  const previousLink = document.querySelector('[data-previous-link]');
  const downloadButton = document.querySelector('[data-download-button]');
  const releaseDate = document.querySelector('[data-release-date]');

  if (!versionValue || !statusValue || !releaseLink || !changelogLink || !previousLink || !downloadButton || !releaseDate) {
    return;
  }

  releaseLink.href = releaseState.htmlUrl || RELEASES_URL;
  changelogLink.href = CHANGELOG_URL;
  previousLink.href = RELEASES_URL;

  if (releaseState.status === 'ready') {
    versionValue.textContent = releaseState.version;
    releaseDate.textContent = releaseState.publishedAt
      ? new Intl.DateTimeFormat(localeMap[currentLanguage], {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      }).format(new Date(releaseState.publishedAt))
      : '—';

    if (releaseState.assetUrl) {
      statusValue.textContent = translate('downloads.readyWithApk');
      downloadButton.href = releaseState.assetUrl;
      downloadButton.removeAttribute('aria-disabled');
      downloadButton.removeAttribute('tabindex');
      downloadButton.textContent = translate('downloads.downloadApk');
    } else {
      statusValue.textContent = translate('downloads.readyWithoutApk');
      downloadButton.href = RELEASES_URL;
      downloadButton.setAttribute('aria-disabled', 'true');
      downloadButton.setAttribute('tabindex', '-1');
      downloadButton.textContent = translate('downloads.unavailable');
    }

    return;
  }

  versionValue.textContent = '—';
  releaseDate.textContent = '—';
  downloadButton.href = RELEASES_URL;
  downloadButton.setAttribute('aria-disabled', 'true');
  downloadButton.setAttribute('tabindex', '-1');
  downloadButton.textContent = translate('downloads.unavailable');

  if (releaseState.status === 'empty') {
    statusValue.textContent = translate('downloads.empty');
    return;
  }

  if (releaseState.status === 'error') {
    statusValue.textContent = translate('downloads.error');
    return;
  }

  statusValue.textContent = translate('downloads.loading');
}

async function loadLatestRelease() {
  releaseState = { ...releaseState, status: 'loading' };
  renderReleaseState();

  try {
    const response = await fetch(RELEASE_ENDPOINT, {
      headers: { Accept: 'application/vnd.github+json' },
    });

    if (!response.ok) {
      throw new Error(`GitHub responded with ${response.status}`);
    }

    const releases = await response.json();
    const release = Array.isArray(releases)
      ? releases.find((candidate) => !candidate.draft && /-release-apk$/i.test(candidate.tag_name || ''))
      : null;

    if (!release) {
      releaseState = { ...releaseState, status: 'empty' };
      renderReleaseState();
      return;
    }

    const apkAsset = Array.isArray(release.assets)
      ? release.assets.find((asset) => /\.apk$/i.test(asset.name || ''))
      : null;

    releaseState = {
      status: 'ready',
      version: release.tag_name || '',
      publishedAt: release.published_at || release.created_at || '',
      htmlUrl: release.html_url || RELEASES_URL,
      assetUrl: apkAsset?.browser_download_url || '',
    };
  } catch (error) {
    releaseState = { ...releaseState, status: 'error' };
  }

  renderReleaseState();
}

function setupNavbar() {
  const navToggle = document.querySelector('.nav-toggle');
  const navLinks = document.querySelector('.nav-links');

  if (!navToggle || !navLinks) {
    return;
  }

  navToggle.addEventListener('click', () => {
    const isOpen = navToggle.getAttribute('aria-expanded') === 'true';
    navToggle.setAttribute('aria-expanded', String(!isOpen));
    navLinks.classList.toggle('is-open', !isOpen);
  });

  navLinks.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', () => {
      navToggle.setAttribute('aria-expanded', 'false');
      navLinks.classList.remove('is-open');
    });
  });
}

function setupActiveSection() {
  const sectionLinks = Array.from(document.querySelectorAll('.nav-link[href^="#"]'));
  const sections = sectionLinks
    .map((link) => document.querySelector(link.hash))
    .filter(Boolean);

  if (!sections.length) {
    return;
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) {
        return;
      }

      sectionLinks.forEach((link) => {
        link.classList.toggle('is-active', link.hash === `#${entry.target.id}`);
      });
    });
  }, {
    rootMargin: '-35% 0px -55%',
    threshold: 0.01,
  });

  sections.forEach((section) => observer.observe(section));
}

function setupLanguageSwitcher() {
  document.querySelectorAll('.language-option').forEach((button) => {
    button.addEventListener('click', () => applyTranslations(button.dataset.lang));
  });
}

function setupBackgroundCanvas() {
  const canvas = document.getElementById('bg-canvas');
  const context = canvas?.getContext('2d');

  if (!canvas || !context) {
    return;
  }

  let frame = 0;
  let particles = [];
  let width = 0;
  let height = 0;
  let orbs = [];
  const colors = ['#7c4dff', '#e040fb', '#40c4ff', '#1a0850', '#0a2060'];

  const createOrb = () => ({
    x: Math.random() * width,
    y: Math.random() * height,
    r: 70 + Math.random() * 190,
    dx: (Math.random() - 0.5) * 1.35,
    dy: (Math.random() - 0.5) * 1.2,
    color: colors[Math.floor(Math.random() * colors.length)],
    alpha: 0.05 + Math.random() * 0.1,
    pulse: Math.random() * Math.PI * 2,
    pspeed: 0.02 + Math.random() * 0.03,
    drift: Math.random() * Math.PI * 2,
  });

  const resize = () => {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
    orbs = Array.from({ length: 24 }, createOrb);
    particles = [];
  };

  const rgb = (hex) => {
    const r = Number.parseInt(hex.slice(1, 3), 16);
    const g = Number.parseInt(hex.slice(3, 5), 16);
    const b = Number.parseInt(hex.slice(5, 7), 16);
    return `${r},${g},${b}`;
  };

  const draw = () => {
    context.clearRect(0, 0, width, height);
    context.fillStyle = 'rgba(5,3,18,.98)';
    context.fillRect(0, 0, width, height);

    orbs.forEach((orb) => {
      orb.pulse += orb.pspeed;
      orb.drift += 0.004;
      const pulse = 1 + Math.sin(orb.pulse) * 0.12;
      const gradient = context.createRadialGradient(orb.x, orb.y, 0, orb.x, orb.y, orb.r * pulse);
      gradient.addColorStop(0, `rgba(${rgb(orb.color)},${orb.alpha})`);
      gradient.addColorStop(1, `rgba(${rgb(orb.color)},0)`);
      context.beginPath();
      context.arc(orb.x, orb.y, orb.r * pulse, 0, Math.PI * 2);
      context.fillStyle = gradient;
      context.fill();
      orb.dx += Math.cos(orb.drift) * 0.0035;
      orb.dy += Math.sin(orb.drift) * 0.0035;
      orb.dx = Math.max(-1.7, Math.min(1.7, orb.dx));
      orb.dy = Math.max(-1.7, Math.min(1.7, orb.dy));
      orb.x += orb.dx;
      orb.y += orb.dy;
      if (orb.x < -orb.r) orb.x = width + orb.r;
      if (orb.x > width + orb.r) orb.x = -orb.r;
      if (orb.y < -orb.r) orb.y = height + orb.r;
      if (orb.y > height + orb.r) orb.y = -orb.r;
    });

    particles.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 2.8,
      vy: (Math.random() - 0.5) * 2.8,
      life: 28 + Math.random() * 26,
      color: colors[Math.floor(Math.random() * colors.length)],
    });

    particles = particles.filter((particle) => particle.life > 0);
    particles.forEach((particle) => {
      particle.x += particle.vx;
      particle.y += particle.vy;
      particle.life -= 1;
      const alpha = Math.max(0, particle.life / 54);
      context.beginPath();
      context.arc(particle.x, particle.y, 1.1 + (1 - alpha) * 1.4, 0, Math.PI * 2);
      context.fillStyle = `${particle.color}${Math.floor(alpha * 255).toString(16).padStart(2, '0')}`;
      context.fill();
    });

    frame += 1;
    window.requestAnimationFrame(draw);
  };

  resize();
  draw();
  window.addEventListener('resize', resize);
}

function preserveExistingRestrictions() {
  document.addEventListener('contextmenu', (event) => event.preventDefault());
  document.addEventListener('keydown', (event) => {
    const key = event.key?.toLowerCase() || '';
    if (
      event.key === 'F12'
      || (event.ctrlKey && event.shiftKey && (key === 'i' || key === 'j'))
      || (event.ctrlKey && !event.shiftKey && key === 'u')
    ) {
      event.preventDefault();
      event.stopPropagation();
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  setupNavbar();
  setupActiveSection();
  setupLanguageSwitcher();
  setupBackgroundCanvas();
  preserveExistingRestrictions();
  applyTranslations(currentLanguage);
  loadLatestRelease();
});
