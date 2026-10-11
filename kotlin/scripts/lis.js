const crypto = require("node:crypto");
const fs = require("node:fs");
// Racine du site : le secret Infisical BASE_URL_LIS, jamais ecrit ici.
//   infisical run -- node scripts/lis.js "titre de l anime"
const DEFAULT_BASE_URL = process.env.BASE_URL_LIS;
const DEFAULT_TITLE = "titre-de-lanime";

async function searchJson(baseUrl, title) {
  const origin = getOrigin(baseUrl);
  const res = await fetch(new URL("/template-php/defaut/fetch.php", origin), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ query: title })
  });

  if (!res.ok) throw new Error(`HTTP ${res.status} on search endpoint`);

  const html = await res.text();

  return extractLinks(html).map(({ href, text }) => {
    const url = new URL(href, origin);

    return {
      title: text,
      url: url.href,
      slug: url.pathname.replace(/^\/|\/$/g, "")
    };
  });
}

async function findSlug(baseUrl, title) {
  const results = await searchJson(baseUrl, title);

  return results[0] ?? null;
}

async function episodeVa(url = DEFAULT_BASE_URL, episodeTitle = DEFAULT_TITLE) {
  const target = await resolveContentTarget(url, episodeTitle);

  if (!target) return null;

  return getPageMetadata(target.url, target.title);
}

async function summarizeFirstResult(url = DEFAULT_BASE_URL, title = DEFAULT_TITLE, options = {}) {
  const anime = await resolveAnimeTarget(url, title);

  if (!anime) return null;

  const seasons = sortContentGroups(await getSeasonLinks(anime.url));

  if (seasons.length === 0) {
    const movie = await getMovieMetadata(anime.url, anime.title);

    return {
      query: title,
      anime,
      type: "movie",
      seasonCount: 0,
      totalEpisodes: 0,
      movie
    };
  }

  const selectedSeasons = seasons.slice(0, options.maxSeasons ?? seasons.length);
  const summary = {
    query: title,
    anime,
    type: "series",
    seasonCount: seasons.length,
    seasons: []
  };

  for (const season of selectedSeasons) {
    await delay(options.delayMs ?? 150);

    const episodes = sortEpisodes(await getEpisodeLinks(season.url));
    summary.seasons.push({
      season: extractNumber(season.slug, "saison"),
      group: season.groupSlug,
      groupType: season.groupType,
      groupNumber: season.groupNumber,
      title: season.title,
      url: season.url,
      episodeCount: episodes.length,
      firstEpisode: episodes[0]?.url ?? null,
      lastEpisode: episodes.at(-1)?.url ?? null
    });
  }

  summary.totalEpisodes = summary.seasons.reduce((total, season) => total + season.episodeCount, 0);

  return summary;
}

async function crawlFirstResult(url = DEFAULT_BASE_URL, title = DEFAULT_TITLE, options = {}) {
  const anime = await resolveAnimeTarget(url, title);

  if (!anime) return null;

  const seasons = sortContentGroups(await getSeasonLinks(anime.url));

  if (seasons.length === 0) {
    const movie = await getMovieMetadata(anime.url, anime.title);

    return {
      query: title,
      anime,
      type: "movie",
      movie
    };
  }

  const selectedSeasons = seasons.slice(0, options.maxSeasons ?? seasons.length);
  const output = {
    query: title,
    anime,
    type: "series",
    seasons: []
  };

  for (const season of selectedSeasons) {
    await delay(options.delayMs ?? 150);

    const episodes = sortEpisodes(await getEpisodeLinks(season.url));
    const selectedEpisodes = episodes.slice(0, options.maxEpisodes ?? episodes.length);
    const seasonResult = {
      season: extractNumber(season.slug, "saison"),
      group: season.groupSlug,
      groupType: season.groupType,
      groupNumber: season.groupNumber,
      title: season.title,
      url: season.url,
      episodeCount: episodes.length,
      episodes: []
    };

    for (const episode of selectedEpisodes) {
      await delay(options.delayMs ?? 150);

      const metadata = await getEpisodeMetadata(episode.url, episode.title);
      seasonResult.episodes.push({
        title: episode.title,
        url: episode.url,
        ...metadata
      });
    }

    output.seasons.push(seasonResult);
  }

  return output;
}

async function getSeasonLinks(animeUrl) {
  const html = await fetchText(animeUrl);
  const page = new URL(animeUrl);
  const route = parseEpisodeRoute(page);
  const groupRoute = parseContentGroupRoute(page);
  const animePath = route.slug
    ? `/${route.slug}`
    : groupRoute.slug
      ? `/${groupRoute.slug}`
      : page.pathname.replace(/\/$/, "");

  return uniqueLinks(extractLinks(html)
    .map(link => normalizeLink(link, page.origin))
    .filter(link => isDirectContentGroupLink(link.url, animePath))
    .map(link => {
      const group = parseContentGroupRoute(link.url);

      return {
        title: link.text || link.url.pathname.split("/").filter(Boolean).at(-1),
        url: link.url.href,
        slug: link.url.pathname.replace(/^\/|\/$/g, ""),
        ...group
      };
    }));
}

async function getEpisodeLinks(seasonUrl) {
  const html = await fetchText(seasonUrl);
  const page = new URL(seasonUrl);
  const seasonPath = page.pathname.replace(/\/$/, "");

  return uniqueLinks(extractLinks(html)
    .map(link => normalizeLink(link, page.origin))
    .filter(link => link.url.pathname.startsWith(`${seasonPath}/episode-`))
    .map(link => {
      const route = parseEpisodeRoute(link.url);

      return {
        title: link.text || link.url.pathname.split("/").filter(Boolean).at(-1),
        url: link.url.href,
        slug: link.url.pathname.replace(/^\/|\/$/g, ""),
        episode: route.episode,
        language: route.language,
        groupSlug: route.groupSlug,
        groupType: route.groupType
      };
    }), link => {
      return link.groupSlug && link.episode
        ? `${link.groupSlug}/episode-${link.episode}`
        : link.url;
    });
}

async function getEpisodeMetadata(episodeUrl, title = "") {
  return getPageMetadata(episodeUrl, title);
}

async function getMovieMetadata(movieUrl, title = "") {
  const html = await fetchText(movieUrl);
  const pageUrl = new URL(movieUrl);
  const readersByLanguage = parseFilmUrls(html);
  const languages = Object.keys(readersByLanguage);

  if (languages.length === 0) {
    const fallbackReaders = parseIframeReaders(html);
    if (fallbackReaders.length) readersByLanguage.default = fallbackReaders;
  }

  return {
    type: "movie",
    title,
    url: pageUrl.href,
    slug: pageUrl.pathname.replace(/^\/|\/$/g, ""),
    movieId: extractMovieId(html),
    canonicalUrl: extractCanonical(html),
    access: {
      routePattern: "/{slug}/",
      playerAnchor: "#film-player"
    },
    languages: formatLanguages(readersByLanguage)
  };
}

async function getPageMetadata(pageUrlValue, title = "") {
  const html = await fetchText(pageUrlValue);
  const pageUrl = new URL(pageUrlValue);
  const episodeRoute = parseEpisodeRoute(pageUrl);
  const isEpisode = episodeRoute.slug !== null;

  if (isEpisode) {
    const readersByLanguage = parseReadersByLanguage(html);
    const languages = Object.keys(readersByLanguage);

    if (languages.length === 0) {
      readersByLanguage.default = parseIframeReaders(html);
    }

    return {
      type: "episode",
      title,
      url: pageUrl.href,
      slug: episodeRoute.slug,
      season: episodeRoute.season,
      group: episodeRoute.groupSlug,
      groupType: episodeRoute.groupType,
      groupNumber: episodeRoute.groupNumber,
      episode: episodeRoute.episode,
      pageLanguage: episodeRoute.language,
      animeId: extractAnimeId(html),
      canonicalUrl: extractCanonical(html),
      access: {
        routePattern: "/{slug}/{group}/episode-{episode}/{lang}/",
        langPathSegment: "lang",
        examples: Object.keys(readersByLanguage).map(lang =>
          `${pageUrl.origin}/${episodeRoute.slug}/${episodeRoute.groupSlug}/episode-${episodeRoute.episode}/${lang}/`
        )
      },
      languages: formatLanguages(readersByLanguage)
    };
  }

  return getMovieMetadata(pageUrl.href, title);
}

async function resolveContentTarget(url, title) {
  const parsed = toUrl(url, DEFAULT_BASE_URL);

  if (!isRootUrl(parsed)) {
    return {
      title,
      url: parsed.href,
      slug: parsed.pathname.replace(/^\/|\/$/g, "")
    };
  }

  return findSlug(parsed.origin, title);
}

async function resolveAnimeTarget(url, title) {
  const target = await resolveContentTarget(url, title);

  if (!target) return null;

  const parsed = new URL(target.url);
  const episodeRoute = parseEpisodeRoute(parsed);
  const groupRoute = parseContentGroupRoute(parsed);

  if (!episodeRoute.slug && !groupRoute.slug) return target;

  const animeSlug = episodeRoute.slug ?? groupRoute.slug;
  const animeUrl = new URL(`/${animeSlug}/`, parsed.origin);

  return {
    title: target.title,
    url: animeUrl.href,
    slug: animeSlug
  };
}

function formatLanguages(readersByLanguage) {
  return Object.entries(readersByLanguage).map(([language, readers]) => ({
    language,
    readerCount: readers.length,
    readers
  }));
}

function parseEpisodeRoute(url) {
  const parts = url.pathname.split("/").filter(Boolean);
  const episodeIndex = parts.findIndex(part => /^episode-\d+$/i.test(part));

  if (episodeIndex < 2) {
    return {
      slug: null,
      groupSlug: null,
      groupType: null,
      groupNumber: null,
      season: null,
      episode: null,
      language: null
    };
  }

  const groupSlug = parts.slice(1, episodeIndex).join("/");
  const episode = Number.parseInt(parts[episodeIndex].replace(/^episode-/i, ""), 10);
  const group = getGroupInfo(groupSlug);

  return {
    slug: parts[0],
    groupSlug,
    groupType: group.groupType,
    groupNumber: group.groupNumber,
    season: group.season,
    episode,
    language: parts[episodeIndex + 1] ?? null
  };
}

function parseContentGroupRoute(url) {
  const parts = url.pathname.split("/").filter(Boolean);

  if (parts.length !== 2 || /^episode-\d+$/i.test(parts[1])) {
    return {
      slug: null,
      groupSlug: null,
      groupType: null,
      groupNumber: null,
      season: null
    };
  }

  return {
    slug: parts[0],
    ...getGroupInfo(parts[1])
  };
}

function getGroupInfo(groupSlug) {
  const groupType = groupSlug.startsWith("saison-")
    ? "saison"
    : groupSlug.startsWith("kai-saga-")
      ? "kai-saga"
      : groupSlug.startsWith("saga-")
        ? "saga"
        : "section";
  const groupNumber = extractGroupNumber(groupSlug);

  return {
    groupSlug,
    groupType,
    groupNumber,
    season: groupType === "saison" ? groupNumber : null
  };
}

function extractAnimeId(html) {
  const match = html.match(/\bconst\s+animeId\s*=\s*(\d+)\s*;/);

  return match ? Number.parseInt(match[1], 10) : null;
}

function extractMovieId(html) {
  const match = html.match(/toggle(?:Favorite|Watchlist|Watched)\((\d+)\)/)
    ?? html.match(/\b(?:movie|anime)Data\s*=\s*\{[\s\S]*?\bid\s*:\s*(\d+)/);

  return match ? Number.parseInt(match[1], 10) : null;
}

function extractCanonical(html) {
  const relFirst = html.match(/<link\b[^>]*\brel=(["'])canonical\1[^>]*\bhref=(["'])(.*?)\2/i);
  const hrefFirst = html.match(/<link\b[^>]*\bhref=(["'])(.*?)\1[^>]*\brel=(["'])canonical\3/i);
  const match = relFirst ?? hrefFirst;

  return match ? decodeHtml(match[3] ?? match[2]) : null;
}

function parseReadersByLanguage(html) {
  const match = html.match(/\bconst\s+readers\s*=\s*(\{[\s\S]*?\})\s*;/);

  if (!match) return {};

  try {
    const raw = JSON.parse(match[1]);
    const out = {};

    for (const [language, urls] of Object.entries(raw)) {
      if (!Array.isArray(urls)) continue;
      out[language] = uniqueBy(
        urls.map(redactReaderUrl).filter(Boolean),
        reader => `${reader.host}${reader.path}${reader.urlSha256}`
      );
    }

    return out;
  } catch {
    return {};
  }
}

function parseFilmUrls(html) {
  const match = html.match(/\bconst\s+filmUrls\s*=\s*(\{[\s\S]*?\})\s*;/);

  if (!match) return {};

  try {
    const raw = JSON.parse(match[1]);
    const out = {};

    for (const [language, value] of Object.entries(raw)) {
      if (typeof value !== "string" || !value) continue;
      out[language] = [redactReaderUrl(value)];
    }

    return out;
  } catch {
    return {};
  }
}

function parseIframeReaders(html) {
  const refs = [];

  for (const match of html.matchAll(/<iframe\b[^>]*(?:src|data-src)=(["'])(.*?)\1/gi)) {
    refs.push(redactReaderUrl(decodeHtml(match[2])));
  }

  return uniqueBy(refs.filter(Boolean), reader => `${reader.host}${reader.path}${reader.urlSha256}`);
}

function redactReaderUrl(value) {
  const url = new URL(value, DEFAULT_BASE_URL);
  const parts = url.pathname.split("/").filter(Boolean);
  const firstPart = parts[0] || "";
  const genericPath = firstPart.startsWith("embed-")
    ? "/embed-...html"
    : parts.length
      ? `/${firstPart.replace(/[a-z0-9]{6,}/gi, "...")}/...`
      : "/";

  return {
    scheme: url.protocol.replace(":", ""),
    host: url.host,
    path: genericPath,
    urlLength: value.length,
    urlSha256: crypto.createHash("sha256").update(value).digest("hex"),
    urlvalue: value,
    redacted: true
  };
}

async function fetchText(url) {
  const target = new URL(url, DEFAULT_BASE_URL);
  const res = await fetch(target.href, {
    headers: {
      "User-Agent": "Mozilla/5.0",
      "Referer": target.origin
    }
  });

  if (!res.ok) throw new Error(`HTTP ${res.status} on ${target.href}`);

  return res.text();
}

function normalizeLink(link, baseUrl) {
  return {
    ...link,
    url: new URL(link.href, baseUrl)
  };
}

function uniqueLinks(links, keyFn = link => link.url?.href ?? link.url) {
  return uniqueBy(links, keyFn);
}

function uniqueBy(items, keyFn) {
  const seen = new Set();
  const out = [];

  for (const item of items) {
    const key = keyFn(item);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
  }

  return out;
}

function sortByNumber(items, label) {
  return [...items].sort((left, right) => {
    const a = extractNumber(left.slug, label) ?? Number.MAX_SAFE_INTEGER;
    const b = extractNumber(right.slug, label) ?? Number.MAX_SAFE_INTEGER;

    return a - b;
  });
}

function sortContentGroups(items) {
  if (!items.every(item => item.groupType === "saison")) return items;

  return [...items].sort((left, right) => {
    const a = left.groupNumber ?? Number.MAX_SAFE_INTEGER;
    const b = right.groupNumber ?? Number.MAX_SAFE_INTEGER;

    return a - b;
  });
}

function sortEpisodes(items) {
  return [...items].sort((left, right) => {
    const a = left.episode ?? Number.MAX_SAFE_INTEGER;
    const b = right.episode ?? Number.MAX_SAFE_INTEGER;

    return a - b;
  });
}

function extractNumber(slug, label) {
  const match = slug.match(new RegExp(`${label}-(\\d+)`, "i"));

  return match ? Number.parseInt(match[1], 10) : null;
}

function extractGroupNumber(groupSlug) {
  const match = groupSlug.match(/^(?:kai-)?saga-(\d+)/i)
    ?? groupSlug.match(/^saison-(\d+)/i);

  return match ? Number.parseInt(match[1], 10) : null;
}

function isDirectContentGroupLink(url, animePath) {
  const basePath = animePath.replace(/\/$/, "");
  const linkPath = url.pathname.replace(/\/$/, "");

  if (!basePath || linkPath === basePath || !linkPath.startsWith(`${basePath}/`)) {
    return false;
  }

  const rest = linkPath.slice(basePath.length + 1);

  return Boolean(rest) && !rest.includes("/") && !/^episode-\d+$/i.test(rest);
}

function extractLinks(html) {
  const links = [];
  const anchorPattern = /<a\b[^>]*\bhref=(["'])(.*?)\1[^>]*>([\s\S]*?)<\/a>/gi;
  const hrefPattern = /\bhref=(["'])(.*?)\1/gi;
  const jsonUrlPattern = /"url"\s*:\s*"([^"]+)"/gi;
  let match;

  while ((match = anchorPattern.exec(html)) !== null) {
    links.push({
      href: decodeHtml(match[2]),
      text: toText(match[3])
    });
  }

  while ((match = hrefPattern.exec(html)) !== null) {
    links.push({
      href: decodeHtml(match[2]),
      text: ""
    });
  }

  while ((match = jsonUrlPattern.exec(html)) !== null) {
    links.push({
      href: decodeHtml(match[1].replace(/\\\//g, "/")),
      text: ""
    });
  }

  return links;
}

function toText(html) {
  return decodeHtml(html.replace(/<[^>]*>/g, " "))
    .replace(/\s+/g, " ")
    .trim();
}

function decodeHtml(value) {
  const named = {
    amp: "&",
    lt: "<",
    gt: ">",
    quot: '"',
    apos: "'",
    nbsp: " "
  };

  return value.replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (_, entity) => {
    if (entity[0] === "#") {
      const codePoint = entity[1]?.toLowerCase() === "x"
        ? Number.parseInt(entity.slice(2), 16)
        : Number.parseInt(entity.slice(1), 10);

      return Number.isFinite(codePoint) ? String.fromCodePoint(codePoint) : _;
    }

    return named[entity.toLowerCase()] ?? _;
  });
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function getOrigin(value) {
  return toUrl(value, DEFAULT_BASE_URL).origin;
}

function toUrl(value, baseUrl) {
  return new URL(value || DEFAULT_BASE_URL, baseUrl || DEFAULT_BASE_URL);
}

function isRootUrl(url) {
  return url.pathname === "/" || url.pathname === "";
}

function isHttpUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function parseArgs(argv) {
  const options = {
    episodes: false,
    search: false,
    summary: false,
    maxSeasonsExplicit: false,
    maxSeasons: undefined,
    maxEpisodes: undefined,
    delayMs: 150
  };
  const terms = [];
  let url = DEFAULT_BASE_URL;

  for (const arg of argv) {
    if (arg === "--episodes") {
      options.episodes = true;
    } else if (arg === "--search") {
      options.search = true;
    } else if (arg === "--summary") {
      options.summary = true;
    } else if (arg.startsWith("--max-seasons=")) {
      options.maxSeasonsExplicit = true;
      options.maxSeasons = Number.parseInt(arg.split("=")[1], 10);
    } else if (arg.startsWith("--max-episodes=")) {
      options.maxEpisodes = Number.parseInt(arg.split("=")[1], 10);
    } else if (arg.startsWith("--delay-ms=")) {
      options.delayMs = Number.parseInt(arg.split("=")[1], 10);
    } else if (isHttpUrl(arg) && url === DEFAULT_BASE_URL) {
      url = arg;
    } else {
      terms.push(arg);
    }
  }

  return {
    url,
    title: terms.join(" ") || DEFAULT_TITLE,
    options
  };
}

function printSummary(result) {
  if (!result) {
    console.log("Aucun resultat trouve.");
    return;
  }

  console.log(`Titre: ${result.anime.title}`);
  console.log(`URL: ${result.anime.url}`);

  if (result.type === "movie") {
    console.log("Type: film");
    console.log(`Movie ID: ${result.movie.movieId ?? "?"}`);
    console.log(`Langues: ${result.movie.languages.map(item => item.language).join(", ") || "?"}`);
    console.table(result.movie.languages.map(item => ({
      langue: item.language,
      lecteurs: item.readerCount,
      domaines: item.readers.map(reader => reader.host).join(", ")
    })));
    return;
  }

  console.log(`Saisons/groupes disponibles: ${result.seasonCount}`);
  console.log(`Episodes comptes: ${result.totalEpisodes}`);
  console.table(result.seasons.map(season => ({
    groupe: season.group ?? `saison-${season.season ?? "?"}`,
    type: season.groupType ?? "saison",
    numero: season.groupNumber ?? season.season ?? "?",
    episodes: season.episodeCount,
    url: season.url
  })));
}

if (require.main === module) {
  const { url, title, options } = parseArgs(process.argv.slice(2));
  const directUrl = isHttpUrl(url) && !isRootUrl(new URL(url));
  const directEpisodeUrl = directUrl && parseEpisodeRoute(new URL(url)).slug !== null;

  const task = options.summary
    ? summarizeFirstResult(url, title, {
      ...options,
      maxSeasons: options.maxSeasonsExplicit ? options.maxSeasons : undefined
    })
    : options.episodes
      ? crawlFirstResult(url, title, options)
      : options.search
        ? searchJson(url, title)
        : directEpisodeUrl
        ? episodeVa(url, title)
        : crawlFirstResult(url, title, options);

  task
    .then(result => {
      if (options.summary) {
        printSummary(result);
      } else {
        console.log(JSON.stringify("result:ok", null, 2));
      }
    })
    .catch(error => {
      console.error(error);
      process.exitCode = 1;
    });
}

module.exports = {
  episodeVa,
  searchJson,
  findSlug,
  summarizeFirstResult,
  crawlFirstResult,
  getSeasonLinks,
  getEpisodeLinks,
  getEpisodeMetadata,
  getMovieMetadata
};
