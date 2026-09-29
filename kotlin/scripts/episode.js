const fs = require("fs");

async function genererEpisodes(urlAnim) {
  const url = `${urlAnim}/episodes.js`;

  try {
    console.log("Téléchargement du fichier depuis Anime-Sama...");
    const response = await fetch(url);
    
    if (!response.ok) {
      throw new Error(`Erreur HTTP : ${response.status}`);
    }

    const scriptContent = await response.text();

    // Exécute le code distant et extrait dynamiquement toutes les variables eps1, eps2, etc.
    const getLecteurs = new Function(`
      ${scriptContent}
      const data = {};
      for (let i = 1; i <= 50; i++) {
        try {
          const val = eval('eps' + i);
          if (val) data['eps' + i] = val;
        } catch(e) {}
      }
      return data;
    `);

    const data = getLecteurs();
    
    // Transforme l'objet en tableau trié de lecteurs
    const lecteurs = Object.keys(data)
      .sort((a, b) => parseInt(a.substring(3)) - parseInt(b.substring(3)))
      .map(key => data[key]);

    if (lecteurs.length === 0) {
      console.log("Aucun lecteur trouvé dans le script.");
      return 0;
    }

    // Nombre maximum d'épisodes
    const nombreEpisodes = Math.max(...lecteurs.map(lecteur => lecteur.length));
    const episodes = {};

    // Construction de l'objet final
    for (let i = 0; i < nombreEpisodes; i++) {
      const liens = [];
      for (const lecteur of lecteurs) {
        if (lecteur[i]) {
          liens.push(lecteur[i]);
        }
      }
      episodes[`Episode ${i + 1}`] = liens;
    }

    // Export en JSON
    fs.writeFileSync(
      "episodes.json",
      JSON.stringify(episodes, null, 2),
      "utf8"
    );

    console.log("JSON généré avec succès : episodes.json");
    console.log(`Nombre d'épisodes trouvés : ${nombreEpisodes}`);
    return  { episodes, nombreEpisodes };
       

  } catch (error) {
    console.error("Erreur :", error.message);
    return 0;
  }
}

module.exports = { genererEpisodes };
