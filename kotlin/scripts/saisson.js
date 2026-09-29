const axios = require("axios");
const cheerio = require("cheerio");
const fs = require("fs");
const { genererEpisodes } = require("./episode.js");
// Petite pause de sécurité pour éviter de spammer le serveur
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function getDetailsLangues(baseUrlSaison) {
  // Liste de tous les suffixes de langues possibles à tester
  const languesTest = [
    { code: "VOSTFR", path: "vostfr" },
    { code: "VF", path: "vf" },
    { code: "VA", path: "va" },
    { code: "VAR", path: "var" },
    { code: "VKR", path: "vkr" },
    { code: "VCN", path: "vcn" },
    { code: "VQC", path: "vqc" },
    { code: "VF1", path: "vf1" },
    { code: "VF2", path: "vf2" },
  ];

  const languesDispo = [];

  // On s'assure que l'URL de base se termine par un slash
  const base = baseUrlSaison.endsWith("/")
    ? baseUrlSaison
    : baseUrlSaison + "/";

  for (const lang of languesTest) {
    const testUrl = new URL(lang.path, base).href;

    try {
      // On fait une requête (HEAD ou GET) pour tester si la page existe
      // On gère les redirections automatiques et on attrape le statut 200
      const response = await axios.get(testUrl, {
        headers: {
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        },
        validateStatus: function (status) {
          // On accepte uniquement le statut 200 comme "valide/existant"
          return status === 200;
        },
      });

      if (response.status === 200) {
        languesDispo.push(lang.code);
      }
    } catch (error) {
      // Si le serveur renvoie 404 ou une erreur, axios lève une exception, on l'ignore (la langue n'existe pas)
    }
  }

  return languesDispo.length > 0 ? languesDispo.join(", ") : "Aucune";
}

function genererPathsLangues(path, langues) {
  const languesPaths = {
    VOSTFR: ["pathVO", "vostfr"],
    VF: ["pathVF", "vf"],
    VA: ["pathVA", "va"],
    VAR: ["pathVAR", "var"],
    VKR: ["pathVKR", "vkr"],
    VCN: ["pathVCN", "vcn"],
    VQC: ["pathVQC", "vqc"],
    VF1: ["pathVF1", "vf1"],
    VF2: ["pathVF2", "vf2"],
  };
  const racine = path.replace(/\/[^/]+$/, "");
  const paths = {};

  if (langues === "Aucune") return paths;

  for (const langue of langues.split(", ")) {
    const definition = languesPaths[langue.trim()];
    if (definition) {
      paths[definition[0]] = `${racine}/${definition[1]}`;
    }
  }

  return paths;
}

async function explorerCatalogueAnime(urlAnim) {
  try {
    const urlPrincipale = `${urlAnim}`;
    console.log(`Récupération de la page principale : ${urlPrincipale}`);

    const { data } = await axios.get(urlPrincipale, {
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
      },
    });

    const $ = cheerio.load(data);
    const container = $("div.flex.flex-wrap.overflow-y-hidden");

    if (container.length === 0) {
      console.log("Div principale des panneaux introuvable.");
      return;
    }

    const scriptContent = container.find("script").html();
    if (!scriptContent) {
      console.log("Aucun script trouvé dans cette div.");
      return;
    }

    // Regex pour capturer panneauAnime("Titre", "Lien")
    const regex =
      /panneauAnime\s*\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)/g;

    const saisonsBrutes = [];
    let match;

    while ((match = regex.exec(scriptContent)) !== null) {
      saisonsBrutes.push({
        text: match[1], // Ex: "Saison 1"
        path: match[2], // Ex: "saison1/vostfr"
        // On nettoie l'URL de base de la saison (si le path finit par /vostfr, on le retire pour avoir la racine de la saison)
        fullUrl: new URL(match[2].replace(/\/[^\/]+$/, ""), urlPrincipale).href,
      });
    }

    console.log(
      `\n${saisonsBrutes.length} éléments trouvés. Test des liens de langues (200 OK) en cours...\n`,
    );

    const resultatFinal = [];

    for (const item of saisonsBrutes) {
      process.stdout.write(`-> Test de : ${item.text} ... `);
      const langues = await getDetailsLangues(item.fullUrl);
      const episodesUrl = new URL(item.path, urlPrincipale).href;
      const nombreEpisodes = await genererEpisodes(episodesUrl);
      console.log(`[ Langues : ${langues} ]`);
      console.log(
        `   Nombre d'épisodes trouvés : ${nombreEpisodes.nombreEpisodes}\n`,
      );

      resultatFinal.push({
        Saison: item.text,
        nombreEpisodes: nombreEpisodes.nombreEpisodes,
        path: item.path,
        Langues: langues,
        BaseUrl: item.fullUrl,
        ...genererPathsLangues(item.path, langues),
      });

      // Petite pause pour ne pas surcharger le serveur
      await sleep(200);
    }

    console.log("\n=== RÉSULTAT FINAL ===");
    console.table(resultatFinal);
    fs.writeFileSync(
      "saisons.json",
      JSON.stringify(resultatFinal, null, 2) + "\n",
    );
    console.log("\nJSON enrichi enregistré dans saisons.json");
  } catch (error) {
    console.error("Erreur globale :", error.message);
  }
}

// URL passee en argument, avec la valeur d'exemple en repli :
//   node scripts/saisson.js "https://<base>/<slug>/"
explorerCatalogueAnime(
  process.argv[2] || "https://anime-sama.to/catalogue/classroom-of-the-elite/",
);
