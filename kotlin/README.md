# allAnime TV

Application Android TV de streaming d'animes : accueil et planning des sorties
depuis le **catalogue du site**, fiches détaillées depuis **AniList (GraphQL)**,
saisons / langues / épisodes par scraping du catalogue, lecture dans un **WebView
protégé** intégré à la fenêtre principale.

Pas de compte utilisateur : le téléviseur est le contexte.

---

## 1. Démarrage rapide

```bash
cd kotlin
# local.properties : sdk.dir + les quatre lignes infisical.* de la section 2
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Prérequis : JDK 17, Android SDK (plateforme 34 ou 35) et un accès Infisical, d'où
vient la racine du catalogue (`BASE_URL_2A`, voir section 2). Aucun outil à installer
pour cela : le build appelle l'API Infisical lui-même. `local.properties` porte le
chemin du SDK (`sdk.dir=...`) **et** cet accès.

---

### Construire une version signee

`assembleRelease` produit un APK **non signe** tant qu'aucune cle n'est configuree ;
Android refuse alors de l'installer. Mise en place, une seule fois :

```powershell
# Sur une seule ligne : PowerShell n'accepte pas la continuation « \ » de bash.
keytool -genkeypair -v -keystore allanime-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias allanime

copy keystore.properties.example keystore.properties   # puis renseigner les mots de passe
.\gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

`keystore.properties` et `*.jks` sont ignores par git. Perdre le `.jks` ou son mot de
passe rend impossible toute mise a jour de l'application deja installee.

---

## 2. Configuration (Infisical + `.env`)

Toute la configuration réseau est lue **au moment du build** par
`app/build.gradle.kts` et injectée dans `BuildConfig`. Aucune URL n'est codée en dur
dans les écrans ou les ViewModels ; `com.allanime.tv.core.AppConfig` est le seul
point d'accès.

La racine du catalogue est un **secret Infisical** : elle n'est pas versionnée. Le
reste vient de `kotlin/.env`.

| Clef | Source | Rôle |
| --- | --- | --- |
| `BASE_URL_2A` | Infisical | Racine du catalogue. L'URL d'un anime est `BASE_URL_2A/<slug>/`, la liste de l'accueil `BASE_URL_2A/?type[]=Anime&…`. Le dernier segment `/catalogue` est remplacé par `/planning` pour le planning et par `/template-php/defaut/fetch.php` pour le moteur de recherche. |
| `ANILIST_ENDPOINT` | `.env` | Endpoint GraphQL AniList. |
| `PLAYER_USER_AGENT` | `.env` | User-Agent du WebView du lecteur. |
| `SCRAPER_USER_AGENT` | `.env` | User-Agent des requêtes de scraping. |

### Accès Infisical (`local.properties`)

Le build s'authentifie auprès de l'API Infisical avec une **identité machine**
(Universal Auth) : il échange `clientId` / `clientSecret` contre un jeton, puis lit le
secret. Pas de CLI à installer.

L'accès vit dans `kotlin/local.properties`, **non versionné**, à côté de `sdk.dir` —
le plugin Android ignore les clefs qu'il ne connaît pas :

```properties
infisical.clientId=...
infisical.clientSecret=...
infisical.projectId=...
infisical.host=https://eu.infisical.com
```

| Clef | Rôle | Défaut |
| --- | --- | --- |
| `infisical.clientId` | Identité machine (Access Control > Identities). | obligatoire |
| `infisical.clientSecret` | Secret de cette identité. | obligatoire |
| `infisical.projectId` | Projet contenant `BASE_URL_2A`. | obligatoire |
| `infisical.host` | Région : `https://eu.infisical.com`, `https://app.infisical.com`, ou instance auto-hébergée. | `https://app.infisical.com` |
| `infisical.environment` | Environnement du secret. | `dev` |
| `infisical.secretPath` | Chemin du secret dans le projet. | `/` |

Les deux dernières ne servent que si le secret n'est pas dans `dev` à la racine.

Chacune de ces clefs a un équivalent par **variable d'environnement**, qui a la
priorité sur le fichier : `INFISICAL_CLIENT_ID`, `INFISICAL_CLIENT_SECRET`,
`INFISICAL_PROJECT_ID`, `INFISICAL_HOST`, `INFISICAL_ENV`, `INFISICAL_PATH`. C'est la
forme utilisée en CI, où rien n'est écrit sur le disque. L'identité machine n'a besoin
que du droit de **lecture** sur le projet.

Plus court encore en CI : définir directement la variable `BASE_URL_2A`. Le build la
prend telle quelle et n'appelle pas l'API.

Un identifiant absent, une identité refusée ou un secret introuvable **font échouer le
build** avec un message explicite, plutôt que de retomber silencieusement sur une
valeur par défaut.

---

## 3. Structure

```
kotlin/
├── app/src/main/java/com/allanime/tv/
│   ├── core/          AppConfig, LienCatalogue, Slugifier, UiState, AppError,
│   │                  HtmlText, Genres, LibellesAniList
│   ├── domain/model/  Anime (+ AniList), AnimeCatalogue, Planning, Saison, Langue,
│   │                  Episode, Lecteur
│   ├── data/
│   │   ├── anilist/   Requêtes GraphQL, DTO, service HTTP, mapper
│   │   ├── scraper/   CatalogueListeScraper, PlanningScraper, SaisonsScraper,
│   │   │              EpisodesScraper, RechercheCatalogueScraper
│   │   │              (ports des scripts Node), SlugResolver
│   │   ├── historique/HistoriqueRepository (DataStore local)
│   │   ├── planning/  PlanningRepository (DataStore local)
│   │   ├── lecteur/   ClassementLecteurs (fiabilité des hébergeurs)
│   │   ├── net/       Client OkHttp partagé
│   │   └── repository/AnimeRepository, CatalogueListeRepository,
│   │                  CatalogueRepository (caches mémoire)
│   ├── player/        WebPlayerHost, AdBlockRules, OverlayScripts, YouTubeEmbed
│   ├── ui/
│   │   ├── theme/     palette, gabarit responsive, typographie, focus
│   │   ├── components/cartes, états, clavier, flou, zone parcourable
│   │   ├── navigation/barre latérale, cadre, routes, graphe
│   │   ├── accueil/   bannière rotative et rangées thématiques
│   │   ├── catalogue/ Séries et Films (catalogue filtré)
│   │   ├── recherche/ clavier virtuel et grille de résultats
│   │   ├── planning/  semaine de diffusion
│   │   ├── fiche/     onglets de la page d'une série
│   │   └── lecteur/   WebView, barre de transport, réglages
│   ├── di/            ServiceLocator (assemblage manuel)
│   └── MainActivity   activité unique, lecteur compris
├── scripts/           saisson.js + episode.js — scripts Node de référence
│                   et ../lecteur-videos/parser{Catalogue,Planning}.js
├── tools/             Générateur des visuels de marque (bannière, icônes)
│   └── logos-source/  Logos fournis, avant détourage — non compilés
├── .env               Configuration non secrète
└── local.properties   sdk.dir + accès Infisical — non versionné
```

---

## 4. Flux fonctionnel

```
À l'ouverture, deux téléchargements en parallèle :

  BASE_URL_2A/?type[]=Anime&…  ─► CatalogueListeScraper ─► liste du catalogue
  <racine>/planning             ─► PlanningScraper       ─► planning de la semaine

Accueil ─┬─ Reprendre votre visionnage      ┐
         ├─ Sorties du jour (planning)      │
         ├─ Ajouts des jours précédents     │
         └─ Classement par genre            │
                                            │
Séries / Films ── catalogue filtré ─────────┼─► Page de la série
                                            │     │  slug déjà connu
Planning ──────── semaine complète ─────────┤     │  → recherche AniList sur le titre
                                            │     │  → BASE_URL_2A/<slug>/
Recherche ─────── AniList ──────────────────┘     │  → saisons (scraping)
                                                  │  → langues (sondage HTTP)
                                                  │  → épisodes (episodes.js)
                                                  └─► Lecteur WebView
```

L'accueil ne part plus des tendances AniList : tout ce qu'il propose vient du
catalogue du site, donc tout y est réellement lisible. AniList n'intervient qu'à
l'ouverture d'une série, où une **recherche sur le titre** rapporte le synopsis, les
personnages, le staff, les notes et la bande-annonce que la carte du catalogue n'a
pas. Si AniList ne connaît pas le titre, la fiche s'affiche quand même avec ce que le
catalogue en dit, et les épisodes restent lisibles.

La page d'une série a donc deux portes d'entrée (`ReferenceFiche`) : par **slug**
depuis l'accueil et le planning, par **identifiant AniList** depuis la recherche et
les recommandations. Venant du catalogue, le slug est connu d'avance
et `SlugResolver` n'a rien à sonder.

La page de la série porte la sélection saison / langue / épisode : il n'y a pas
d'écran d'épisodes séparé, comme sur les interfaces TV grand public. La rangée
« Reprendre » y ramène avec la saison, la langue et l'épisode en cours déjà
sélectionnés, ce qui laisse le choix d'en lancer un autre.

**La fiche est organisée en onglets**, comme les applications TV grand public :
`Épisodes` · `Suggestions` · `Personnages et voix` · `Staff` · `Détails`, sous une
bannière qui porte le titre, quatre étiquettes et les boutons d'action. Tout tenait
auparavant dans une seule page défilante où rien ne séparait la liste d'épisodes du
synopsis, du staff et des recommandations. Un onglet sans contenu ne s'affiche pas :
une fiche qu'AniList ignore n'a ni distribution ni suggestion, sa barre se réduit à
deux entrées.

**L'onglet change à la validation, pas au focus.** Le changer au passage du focus
renouvelait tout le contenu à chaque pression latérale : la liste en dessous se
reconstruisait, la page se repositionnait, et le focus n'était plus là où la
télécommande l'avait laissé. On longe donc la barre librement — l'onglet survolé se
remplit d'un fond et passe au blanc — et l'on appuie sur OK pour changer de section.

Chaque onglet est mesuré en `IntrinsicSize.Max` : son soulignement s'étend sur toute
la largeur qu'on lui offre, et sans cette contrainte le premier onglet occupe la
barre entière en poussant les suivants hors de l'écran — les acteurs, le staff et les
recommandations deviennent alors inatteignables sans qu'aucune donnée ne manque.

#### `Épisodes` : deux colonnes

À gauche le choix — saisons puis langues — à droite ce que ce choix donne. Les deux
listes se parcourent chacune de haut en bas, et l'on passe de l'une à l'autre d'une
seule pression latérale, au lieu d'empiler des rangées de pastilles au-dessus de la
liste. Le bloc a une hauteur fixe : chaque colonne défile pour son compte, si bien
que la saison choisie reste visible pendant qu'on parcourt ses épisodes.

Le panneau de gauche annonce le nombre d'épisodes de chaque saison. Les langues n'y
figurent que lorsqu'il y a un choix à faire : une série qui n'en propose qu'une
l'annonce en texte, sans en faire un bouton que la télécommande devrait traverser
pour rien.

La colonne de droite rappelle en une ligne ce que les sélecteurs viennent de décider
(`Saison 1 · VOSTFR · 12 épisodes disponibles`), puis liste les épisodes : **une ligne
chacun**, vignette à gauche, titre et nombre de lecteurs à droite. Sans illustration,
le bloc de gauche se réduit à une pastille numérotée et la ligne s'abaisse, pour
qu'une saison de deux cents épisodes reste parcourable. Au-delà de trente épisodes
des pastilles de plage (`1 – 30`, `31 – 60`…) évitent d'avoir à descendre à la
flèche, et l'épisode réellement commencé porte un liséré corail et la mention
« En cours » sans attendre le focus.

Une vignette n'est affichée que lorsqu'elle est **certaine et complète** : une fiche
AniList ne couvre qu'une saison alors que le catalogue en regroupe plusieurs, et
AniList n'illustre parfois que les deux premiers épisodes. Hors de ces conditions la
liste reste uniformément numérotée, ce qui se lit mieux qu'une liste à moitié
illustrée.

#### `Détails` : deux colonnes aussi

À gauche le récit — titre, titre original, synopsis complet, puis un avertissement de
photosensibilité. À droite les métadonnées, en paires libellé / valeur empilées :
date de sortie (`2004 – 2011`, ou `2013 – en cours`), genre, classification, création,
réalisation, studio, statut, épisodes, durée, saison, source, note, origine, thèmes
et liens. Les deux colonnes se lisent indépendamment : on parcourt celle de droite du
regard sans avoir à traverser le synopsis.

Format et langues disponibles sont posés en tête de la colonne de droite sous forme
de **badges** et non de phrases : ce sont les seules valeurs qu'on cherche du regard
plutôt qu'on ne lit. `LibellesAniList` traduit au passage les énumérations d'AniList,
livrées en anglais et en majuscules (`TV` → « Série TV », `NOT_YET_RELEASED` →
« Pas encore diffusé »). Un champ absent ne laisse pas de trou : la colonne se
resserre.

Deux valeurs demandent une règle plutôt qu'un champ : la **création** vient du staff
déclaré `Original Creator` ou `Original Story`, la **réalisation** de celui dont
l'intitulé *commence* par `Director` — `Animation Director` et `Episode Director`
sont d'autres métiers. Rien n'est inventé pour autant : pas de classification par
âge là où aucune source n'en fournit (seul le drapeau `isAdult` d'AniList en produit
une), pas de définition vidéo là où elle dépend du lecteur choisi. L'avertissement de
photosensibilité, lui, est **général** : ni AniList ni le catalogue ne signalent les
séquences clignotantes, et le texte le dit au conditionnel.

#### Les autres onglets

`Suggestions`, `Personnages et voix` et `Staff` présentent chacun **une grille** —
quatre, deux et cinq colonnes — et non une rangée qui défile : l'onglet n'a que cela
à montrer, autant que tout soit atteignable en descendant plutôt qu'en longeant une
rangée à la flèche droite.

L'entête, elle, ne garde que quatre étiquettes, dont la note en corail : cette
rangée-là ne peut pas défiler, faute d'étiquette focalisable, donc tout ce qui
déborderait serait perdu.

**La rubrique « Œuvres liées » a été retirée**, et avec elle le champ `relations` de la
requête GraphQL, son DTO et son modèle : une donnée qu'on n'affiche pas n'a pas à
être demandée ni transportée. Les recommandations, elles, restent.

**La bande-annonce se lit sans quitter l'application** quand AniList en déclare une :
le bouton de l'entête ouvre le même lecteur WebView que les épisodes, avec le même
filtrage réseau, et le bouton Retour ramène sur la fiche. YouTube et Dailymotion sont
les deux plateformes reconnues ; une autre ne produit pas de bouton, faute d'URL
embarquable connue — aucune n'est inventée. Le détail de la lecture YouTube est
décrit au chapitre suivant.

Le bouton de lecture prend le focus à l'ouverture, **et le reprend quand il devient
actionnable** : il ne l'est qu'une fois les saisons puis les épisodes chargés, soit
deux allers-retours réseau après l'affichage, et la page s'ouvrait jusqu'ici sans
focus. Une fois qu'il l'a eu, il ne le reprend plus.

### Classement de l'accueil

L'accueil empile, dans cet ordre : « Reprendre votre visionnage », « Sorties du
jour », « Ajouts des jours précédents », « Parce que vous avez regardé … », puis une
rangée par genre dominant, et enfin « Autres » pour le reliquat. Tout est calculé à
partir de ce qui est déjà chargé — le planning, les genres que porte chaque carte du
catalogue et l'historique local — sans un seul appel réseau supplémentaire.

Deux rangées viennent du planning, seule source qui date ce qu'elle annonce : celle
du jour, et celle des **trois jours précédents**, qui tient lieu de « derniers
ajouts » — le catalogue, lui, ne date pas ses entrées. La règle est portée par
`Planning.sortiesDesJoursPrecedents`, partagée avec l'écran de recherche et couverte
par ses propres tests (retour en arrière dans la semaine, doublons, exclusion du jour
courant).

Il n'y a **pas** de rangée « Films » : la barre latérale y mène directement, et les
répéter ici prenait la place d'un genre sans rien apporter. Ils sont donc classés par
genre comme le reste.

**Un anime ne figure que dans une seule rangée de classement.** Les genres sont
parcourus du plus représenté au moins représenté et chacun ne reçoit que ce que les
précédents n'ont pas pris : un titre étiqueté « Action, Comédie, Drame » apparaît une
fois, dans la plus fournie des trois, et sa fiche continue d'afficher ses trois
genres. Un genre vidé par ce partage passe son tour sans bloquer les suivants.

Deux rangées échappent au classement parce qu'elles ne le sont pas : « Reprendre
votre visionnage » et « Parce que vous avez regardé … », qui répondent à une autre
question. Le voisinage est classé par nombre de genres communs ; les titres déjà
commencés passent après, la rangée « Reprendre » les couvrant déjà, mais un
spectateur assidu retombe sur le voisinage complet plutôt que de perdre la rangée.
Une rangée qui ne réunit pas quatre titres n'est pas proposée. `RangeesAccueil` est
une fonction pure, vérifiée hors appareil.

Le rapprochement entre l'historique et le catalogue passe par le **slug**, désormais
recopié dans chaque entrée d'historique : c'est la seule clef commune aux deux
sources, l'identifiant AniList n'existant pas côté catalogue.

### Sorties du jour et planning

Le planning du site est téléchargé en parallèle du catalogue, puis **conservé sur le
téléviseur** (DataStore). L'accueil affiche donc les sorties du jour dès son
ouverture, sans attendre le réseau, et la rubrique « Planning » donne la semaine
entière.

Le site republie son planning « tous les lundis soir » : la copie locale reste fidèle
toute la semaine. À chaque lancement l'application vérifie quand même en
arrière-plan, et **n'écrit que si le site a publié autre chose** — un planning
identique ne fait donc rien clignoter. Tant que la copie est absente ou antérieure au
dernier lundi soir, la rangée annonce qu'elle se met à jour.

Chaque carte dit ce qu'elle sait de l'horaire : l'heure annoncée, « Reporté » quand
le site le signale, ou « Heure non précisée » quand il affiche `?` — un épisode sans
horaire arrêté n'est pas un épisode à minuit. Le site publie animes et scans dans les
mêmes colonnes ; l'application ne retient que les animes, les seuls qu'elle sache
lire.

Retour télécommande : plein écran → lecteur → page de la série → accueil.

### Résolution du slug

Le catalogue ne suit aucune convention unique : `shingeki-no-kyojin` est en romaji,
`classroom-of-the-elite` en anglais, `demon-slayer` et `frieren` sont des noms
courts. Une règle figée casserait un cas sur deux.

`SlugResolver` emploie deux moyens, dans cet ordre.

**1. La confirmation par le moteur de recherche du site.** Le site expose
`…/template-php/defaut/fetch.php`, qui répond à un `POST`
`application/x-www-form-urlencoded` portant un seul champ, `query`. Sa réponse est un
fragment HTML — une suite de liens `.asn-search-result` avec leur titre et leurs
titres alternatifs — que `RechercheCatalogueScraper` transforme en entrées.
Lorsqu'une correspond, **son slug est celui du site lui-même** : il n'y a plus rien à
deviner. Trois titres candidats au plus sont soumis, chacun étant un aller-retour
réseau et les suivants étant des variantes que le moteur aurait de toute façon
retrouvées par les titres alternatifs.

`CorrespondanceCatalogue` décide laquelle retenir, en deux temps. D'abord le titre
demandé tel quel : égalité avec le titre principal ou l'un des titres alternatifs,
puis présence en tant que mot entier. Ensuite — **et seulement si le titre portait un
marqueur de suite** — le même titre débarrassé de ce marqueur : le catalogue regroupe
toutes les saisons d'une série sous une seule fiche, si bien que
`Shingeki no Kyojin Season 3 Part 2` n'y figure jamais tel quel, mais
`Shingeki no Kyojin` si. `Season 3`, `Saison 2`, `Part 2`, `The Final Season`, `II`,
`2` sont retirés un par un, article compris. Un titre qui ne serait plus qu'un mot de
marqueur (« Saison 2 » seul) est rendu intact : un reste vide correspondrait à
n'importe quelle fiche.

La comparaison porte sur des **slugs** — accents, casse et ponctuation ne doivent pas
la faire échouer — et la présence exige des frontières de mot, sans quoi un titre
court correspondrait à tout. Le sous-titre recomposé figure à côté de ses morceaux,
car son découpage est ambigu : le site sépare les titres alternatifs par des
virgules, mais certains en contiennent.

**2. Le sondage**, si la recherche n'a rien confirmé ou n'a pas répondu. Les titres
candidats sont slugifiés et l'on retient le premier dont la fiche répond 200 :
**romaji d'abord, anglais en repli**, puis synonymes, chacun accompagné de sa forme
raccourcie (la partie avant `:`, qui donne `mushoku-tensei` ou `demon-slayer`).

Le slug retenu, quel que soit le moyen, est mémorisé par anime.

**Chaque candidat est décliné en deux slugs**, l'apostrophe étant le seul point où le
catalogue hésite : `Grimm's Notes` y figure tantôt sous `grimms-notes`, tantôt sous
`grimm-s-notes`. `Slugifier.slugify` efface l'apostrophe, `slugifySeparateur` la
traite comme n'importe quel autre signe de ponctuation — un tiret — et
`Slugifier.variantes` rend les deux, sans doublon. Les deux formes d'un même titre
sont essayées avant de passer au titre suivant, pour que le plus probable reste
exploré en premier. Un titre sans apostrophe n'en produit qu'une : la liste ne
s'allonge que là où le catalogue hésite réellement.

Toute cette résolution ne sert que pour les séries ouvertes **par identifiant
AniList** : la recherche et les recommandations d'une fiche. Venant de l'accueil ou
du planning, le slug est déjà celui du lien de la carte, simplement déclaré au
catalogue (`CatalogueRepository.associer`) — ni recherche ni sondage ne sont émis.

Le point d'entrée est unique (`CatalogueRepository.slug`), si bien qu'ouvrir une
série depuis la recherche ou depuis les suggestions d'une autre fiche suit
exactement le même chemin.

### Reprendre votre visionnage

La position de lecture est remontée par la balise `video` toutes les deux secondes,
puis écrite dans un DataStore local (une entrée par anime, 20 au maximum). La rangée
d'accueil ouvre le lecteur directement à l'épisode et à la seconde où l'on s'était
arrêté. L'historique ne quitte jamais le téléviseur.

---

## 4 bis. Interface

### Navigation : une barre latérale, pas une barre de titre

Cinq destinations — **Rechercher, Accueil, Séries, Films, Planning** — dans un rail
vertical qui ne montre que ses icônes au repos et s'élargit dès qu'il prend le focus.
Il est posé **par-dessus** le contenu : déployé, il le recouvre au lieu de le
pousser, ce qui permet à l'accueil de garder une image plein cadre sans tout
recomposer à chaque pression de touche.

Il n'y a volontairement ni rubrique « Originals » — aucune source ne dit qui produit
quoi — ni « Profils » : l'application n'a pas de comptes, l'historique appartient au
téléviseur. Séries et Films, eux, reposent sur le seul classement fiable du
catalogue : le type `Film`.

**La touche Retour fait deux choses, dans cet ordre** : elle ramène d'abord le focus
sur la barre, puis, si la barre l'a déjà, elle remonte d'un cran — vers l'accueil
depuis une rubrique, vers la confirmation de sortie depuis l'accueil. C'est ce qui
évite de quitter l'application d'une pression de trop.

La **flèche droite** rend la main au contenu sans rien choisir. Cette sortie est
désignée explicitement (`focusProperties { right = … }`) et non laissée à la
recherche géométrique : la barre étant posée *par-dessus* le contenu, leurs
rectangles se chevauchent, si bien qu'aucun candidat n'est franchement « à droite »
d'une entrée du rail. Sans cela, la flèche ne menait nulle part et il fallait valider
une destination pour quitter la barre.

### Mise à l'échelle : pourquoi le `dp` ne suffit pas

Sur un mobile, le `dp` fait son travail : il normalise la taille apparente, parce que
la distance de lecture suit la taille de l'écran. Devant un téléviseur, la distance
ne change pas — on reste dans son canapé — mais le nombre de `dp` déclaré par un même
panneau varie **du simple au double** :

| Panneau | Densité | Largeur déclarée |
| --- | --- | --- |
| 1920 × 1080 | 320 dpi | **960 dp** |
| 1920 × 1080 | 213 dpi | ~1442 dp |
| 1920 × 1080 | 160 dpi | **1920 dp** |
| 3840 × 2160 | 640 dpi | 960 dp |

Une mise en page écrite en `dp` fixes et réglée sur la première ligne s'affiche donc
deux fois plus petite sur la troisième, alors que c'est le même écran à la même
distance.

`ui/theme/Gabarit.kt` calcule à l'exécution un **facteur** rapporté à une référence de
960 × 540 dp, et toutes les dimensions en découlent — marges, cartes, rail, touches,
hauteurs de bloc — **typographie comprise**. Le `sp` souffre exactement du même
défaut que le `dp` : le laisser fixe donnerait un texte qui rétrécit à mesure que
l'écran grandit.

C'est volontairement une **homothétie** et non des points de rupture. Un téléphone
qui s'agrandit doit montrer *plus* de contenu, parce qu'on le rapproche ; un
téléviseur, non : on le regarde de la même distance, et y entasser plus de vignettes
ne ferait que les rendre illisibles. Le facteur suit la plus contraignante des deux
dimensions, pour qu'une dalle plus haute que le 16/9 ne déborde pas verticalement.

Le nombre de colonnes reste malgré tout **compté** (`Gabarit.colonnes`) plutôt que
figé : un téléviseur au rapport inhabituel n'a pas la largeur que le facteur suppose,
et compter évite une dernière colonne coupée net par le bord de l'écran.

Le calcul est une fonction pure, donc vérifiable hors appareil (`GabaritTest`). Il se
vérifie aussi sur un vrai téléviseur sans en changer :

```bash
adb shell wm density 160    # simule une dalle 1920 dp
adb shell wm density 213    # simule une dalle ~1442 dp
adb shell wm density reset  # à ne pas oublier
```

### Deux couleurs, deux significations

| Repère | Sens |
| --- | --- |
| Corail | Ce qui est **choisi** : onglet actif, saison courante, bouton principal |
| Bordure blanche + halo bleuté | Ce qui est **survolé** à la télécommande |

Les confondre est le défaut le plus courant d'une interface TV : on ne sait plus
distinguer « où je suis » de « ce qui est actif ». Les deux états sont donc portés
par des couleurs différentes, et jamais par la même.

L'agrandissement au focus est volontairement marqué (12 % sur une affiche) : à trois
mètres, un agrandissement timide ne se perçoit pas. Les rangées réservent la place
nécessaire, l'agrandissement étant **graphique** — il déborde de la place que la mise
en page a réservée, et recouvrirait sinon le titre de la carte.

La police n'est pas embarquée : celle des grandes plateformes est sous licence
propriétaire. `FontFamily.SansSerif` prend la sans-serif du téléviseur, à laquelle
l'échelle typographique applique ses tailles et ses graisses. En embarquer une libre
ne demanderait que de changer une constante.

### Accueil

Une bannière qui tourne parmi six titres et **s'arrête dès qu'elle a le focus** : on
ne change pas une page sous les yeux de quelqu'un qui est en train de la lire.
L'image de fond suit ce qu'on regarde — la série mise en avant tant qu'on est dans la
bannière, celle qu'on survole dès qu'on descend dans les rangées.

Attention à la hauteur utile : la dalle visée fait **960 × 540 dp** (1920 × 1080 à
320 dpi). Une bannière de 380 dp y occupait 70 % de l'écran, et prendre le focus
déclenchait un « amener à l'écran » qui en faisait sortir le titre par le haut. D'où
deux précautions : une hauteur **minimale** plutôt que fixe, et une remise en tête de
la liste après la prise de focus initiale.

### Recherche

Le clavier du système recouvre la moitié basse d'un écran de télévision, c'est-à-dire
précisément les résultats qu'on cherche à filtrer. Il est donc remplacé par une
grille de 6 colonnes, ancrée à gauche, à côté d'eux.

```
 ┌────────────────┬─────────────────────────────┐
 │  champ         │                             │
 ├────────────────┤  résultats                  │
 │  clavier       │  (grille d'affiches)        │
 │  (grille)      │                             │
 └────────────────┴─────────────────────────────┘
```

Le champ est posé **juste au-dessus** de la grille : c'est là que le regard revient
après chaque touche. La flèche droite sort du clavier vers les résultats, la flèche
gauche y ramène — ces règles ne sont pas codées, elles découlent de la géométrie,
Compose cherchant le voisin de focus dans la direction demandée.

Le champ **affiche** la saisie, il ne la saisit pas : un champ de texte focalisé
rouvrirait le clavier du système. La touche OK y efface la saisie, et le curseur y
clignote en permanence — il ne dit pas « vous êtes ici », il dit « ce que vous tapez
arrive là ».

Trois choix sur la disposition, pour trois raisons différentes :

- **une grille plutôt qu'un ruban** : un alphabet étalé sur une ligne oblige à
  traverser vingt-cinq touches pour atteindre la dernière ; en grille, quatre
  pressions suffisent ;
- **l'ordre alphabétique plutôt qu'azerty** : on ne tape pas à dix doigts avec une
  télécommande, on *cherche* une lettre du regard, et l'ordre qu'on connaît par cœur
  bat alors n'importe quelle disposition de machine à écrire ;
- **des `Row` imbriquées plutôt qu'un `LazyVerticalGrid`** : les quarante touches sont
  toutes visibles en permanence, la paresse n'économise donc rien — et une grille
  paresseuse recycle ses éléments, ce qui déstabilise le focus du D-pad. Des rangées
  explicites donnent une géométrie de focus déterministe, seule chose qui compte ici.

**La grille occupe environ le quart de la largeur**, pas la moitié : le clavier est un
moyen, les résultats sont la fin. Une touche plus grande le fait déborder et chasse
les affiches qu'on est justement en train de filtrer — un test le vérifie
(`le clavier occupe environ le quart de l'écran`).

Le focus s'y marque par un **remplissage blanc à texte sombre**, et non par la bordure
employée partout ailleurs. C'est la seule entorse à la règle de l'application, et elle
est délibérée : sur une grille de quarante tuiles serrées, un trait de deux points se
repère mal, alors qu'une touche qui s'allume saute aux yeux à trois mètres.

Une seconde page donne les symboles. Elle en compte **exactement trente-six**, comme
les lettres et les chiffres réunis, et ce n'est pas un hasard : les deux pages
occupent ainsi le même nombre de rangées, la touche de bascule garde sa place et le
focus reste dessus. Avec des pages de hauteurs différentes, elle se déplaçait, Compose
n'y reconnaissait plus le même élément, et le focus retombait hors du clavier.

Tant que rien n'est tapé, la colonne de droite montre ce que le planning annonce —
aujourd'hui, puis les trois jours précédents. C'est l'équivalent le plus honnête
d'une rubrique « derniers ajouts » : le catalogue, lui, ne date pas ses entrées.

### Fiche : l'onglet Épisodes

Sélecteur de saison en onglets horizontaux, puis une rangée de vignettes au format
paysage qui défile **horizontalement**. Une liste verticale impose de descendre
épisode par épisode ; une rangée se parcourt à la flèche maintenue, et laisse
au-dessus d'elle la place d'afficher les saisons en permanence.

Chaque vignette porte, dans cet ordre de lecture :

1. l'image de l'épisode — à défaut, la jaquette de la série, pour qu'aucune carte ne
   soit un rectangle gris ;
2. **son flou**, tant que l'épisode n'a pas été ouvert. Ce n'est pas décoratif :
   c'est ce qui évite qu'une vignette raconte la scène d'un épisode qu'on n'a pas vu ;
3. l'avancement, en trait fin au bas de l'image ;
4. une pastille : le temps restant si l'épisode est en cours, « Nouveau » s'il vient
   **après** le dernier épisode regardé — le seul sens vérifiable qu'on puisse donner
   à ce mot, aucune source ne datant les épisodes un par un.

Le flou ne passe pas par `Modifier.blur`, qui exige Android 12 : en dessous il ne
lève pas, il ne fait simplement rien, et la vignette s'afficherait nette sur la
plupart des téléviseurs. Il est donc appliqué à l'image au décodage, par une
transformation Coil qui réduit puis **remonte par paliers** — une seule remontée
depuis une miniature étire les pixels en mosaïque au lieu de les fondre.

**Ce qui décide du flou est l'ouverture, pas l'avancement.** Un épisode qu'on vient
de lancer est à zéro seconde, et de durée encore inconnue : le déduire de
l'avancement le laissait flou précisément pendant qu'on le regardait.

L'historique retient donc la **liste des épisodes réellement ouverts**, saison par
saison (`ProgressionAnime.episodesOuverts`). Se fier au seul rang atteint ne suffisait
pas : sauter à l'épisode 10 défloutait les neuf précédents, qu'on n'avait pourtant
jamais vus. Les entrées écrites avant que ce champ n'existe ne connaissent qu'un
épisode, mais elles le connaissent — `aEteOuvert` retombe dessus plutôt que de les
oublier.

### Saisons : la page en déclare plus qu'il n'en existe

Le catalogue est bâti sur un gabarit que les éditeurs recopient, et beaucoup de
séries en gardent les panneaux inutilisés. *Darwin's Incident* en déclare **douze** —
« Saison 1 » à « Saison 10 », « Film », « OAV » — alors qu'elle n'a qu'une saison de
treize épisodes.

Ces appels `panneauAnime(...)` ne sont pas en commentaire : ils sont bel et bien
exécutés. Impossible, donc, de trancher à la lecture de la page — c'est ce que fait
le script Node de référence, et c'est pourquoi l'application affichait dix onglets
vides. Il faut regarder ce que chaque saison **contient**.

Trois marques disqualifient une saison, toutes vérifiées à partir du `episodes.js`
que `SaisonsScraper` télécharge **déjà** pour compter les épisodes — le tri ne coûte
donc aucune requête supplémentaire :

| Marque | Exemple relevé |
| --- | --- |
| Aucune langue | « Saison 5 » à « Saison 10 » |
| Aucun épisode exploitable | « Saison 3 » : un épisode dont les lecteurs sont `embed-.html`, `embed/`, `?oid=&hd=3` |
| Recopie d'une saison déjà vue | « Saison 2 » : les mêmes lecteurs que la Saison 1, tronqués à dix épisodes sur treize |

La recopie se reconnaît au **premier épisode** : comparer les listes entières ne
voyait pas les copies tronquées. Quand plusieurs saisons partagent ce début, on garde
la plus fournie — l'originale, la copie étant par construction incomplète.

Une saison dont la lecture **échoue** est conservée : on ne retire que ce dont on a la
preuve, et une panne réseau ne doit pas faire disparaître une saison.

Le tri est fait dans `SaisonsScraper` **et nulle part ailleurs** : la fiche et le
lecteur désignent une saison par sa position dans la liste, et filtrer d'un seul côté
les ferait pointer sur deux saisons différentes.

Résultat, mesuré contre le site (`SondePanneauxTest`) :

| Série | Déclarées | Retenues |
| --- | --- | --- |
| Darwin's Incident | 12 | **1** |
| Clevatess | 2 | 2 |
| Classroom of the Elite | 4 | 4 |

### Langues : une VF annoncée n'en est pas toujours une

`SaisonsScraper` déclare une langue disponible dès que sa page répond `200` — c'est ce
que fait le script Node de référence. Mais **le site répond `200` pour un segment de
langue qui n'existe pas**, et sert alors la version originale. Une saison annonçait
donc « VOSTFR, VF » alors que les deux adresses rendaient exactement les mêmes
lecteurs : on choisissait la VF et on regardait la VOSTFR.

Le seul moyen de trancher est de comparer ce que chaque langue livre réellement.
`CatalogueRepository.languesDistinctes` récupère les épisodes de chaque langue de la
saison affichée et ne garde que la première de chaque empreinte identique — l'ordre de
`LangueCode` plaçant la version originale devant. Le coût est nul en pratique : les
listes obtenues sont mises en cache et ce sont celles que l'écran affiche juste après.

Une langue dont la lecture échoue est **conservée** : on ne retire que ce dont on a la
preuve que c'est un doublon.

Vérifié contre le site avec `SondeLanguesTest` (annoté `@Ignore`, à lancer à la main) :
*Clevatess* a une VF authentique et la garde ; *Darwin's Incident* voyait sa « VF »
de saison 2 servir les lecteurs de la VOSTFR, et ne la propose plus.

### Fiche : l'onglet Détails

Sur un téléviseur, une page ne défile pas toute seule : elle suit le focus. Un pavé
de texte n'étant ni un bouton ni une carte, il n'a rien de focalisable — et tout ce
qui dépassait la hauteur disponible était littéralement inatteignable. Le bloc est
donc posé dans une zone qui se rend focalisable et interprète les flèches haut et bas
comme un défilement. Elle ne retient la touche **que tant qu'il reste quelque chose à
lire** : arrivée en bas, elle laisse la flèche repartir, ce qui évite le piège d'un
bloc dont on ne peut plus sortir.

### Quitter l'application

Une confirmation en surimpression, avec `app_logo_exite`, « Oui » et « Non ». Le
focus part sur **« Non »** : quitter par mégarde est la seule erreur qu'on ne peut
pas annuler, c'est donc la réponse sûre qui doit être à portée de la touche OK. Elle
n'est pas dans une `Dialog` : sur un téléviseur, une fenêtre séparée capture le focus
dans une autre hiérarchie et la télécommande s'y perd.

---

## 5. Port des scripts Node

Android n'embarque pas Node : la logique des scripts de référence est portée en
Kotlin (OkHttp + Jsoup + analyse syntaxique). Cinq scripts, cinq ports :

| Script de référence | Port Kotlin | Rôle |
| --- | --- | --- |
| `../lecteur-videos/parserCatalogue.js` | `CatalogueListeScraper` | Liste du catalogue → accueil |
| `../lecteur-videos/parserPlanning.js` | `PlanningScraper` | Planning hebdomadaire |
| `../lecteur-videos/parserSearch.js` | `RechercheCatalogueScraper` | Confirmation du slug d'une série |
| `scripts/saisson.js` | `SaisonsScraper` | Saisons et langues d'une série |
| `scripts/episode.js` | `EpisodesScraper` | Épisodes et lecteurs |

Les scripts restent dans le dépôt comme **référence exécutable** :

```bash
node scripts/saisson.js "https://<base>/<slug>/"
```

Les ports reproduisent la même séquence et la même sortie. Les écarts sont tous
délibérés et commentés sur place :

| Écart | Raison |
| --- | --- |
| Le gabarit `panneauAnime("nom", "url")`, présent en commentaire dans la page, est ignoré. | Le script Node le capture et produit une saison fantôme (visible dans `saisons.json`). |
| Les sondages de langue utilisent `HEAD` (repli `GET`) et sont parallélisés. | Neuf pages complètes par saison, en série, c'est trop pour une TV. |
| `episodes.js` est analysé par expression régulière au lieu d'être évalué. | Déterministe, et on n'exécute pas de code distant sur le téléviseur. |
| Les liens des cartes sont réduits à leur **slug** (`LienCatalogue`). | Le site sert parfois des liens absolus vers un autre nom de domaine que celui configuré. |
| Les étiquettes de genre coupées par le site (`Science` + `fiction`) sont recollées. | Laissées telles quelles, elles produiraient deux rangées « Science » et « fiction » sur l'accueil. |
| La pagination du catalogue est lue une fois par page, non recopiée dans chaque carte. | Le script Node l'attache à chaque fiche, où elle n'a rien à faire. |
| Le titre d'une carte de planning vient de `.card-title`, non de `data-title`. | `data-title` sert au filtrage du site et est tout en minuscules. |
| La saison d'une carte de planning est lue **sans** le bloc d'heure. | Le bloc d'heure porte aussi la classe `episode` : le sélecteur du script produit « 18h30Saison 1 ». |
| Une heure `?` devient `null`. | Un épisode sans horaire arrêté n'est pas un épisode à minuit. |
| Les œuvres « sans jour fixe », ignorées par le script, sont récupérées. | Elles font partie du planning que l'utilisateur vient consulter. |
| Les **saisons fantômes** sont écartées : sans langue, sans épisode exploitable, ou recopiant une saison déjà vue. | La page est bâtie sur un gabarit que les éditeurs recopient. *Darwin's Incident* y déclare douze panneaux — « Saison 1 » à « Saison 10 », « Film », « OAV » — pour une série qui n'a qu'une saison. |
| Un lecteur dont l'adresse est **vidée de son identifiant** (`embed-.html`, `embed/`, `?oid=`) est ignoré. | Les pages-gabarits en publient. Elles répondent, mais ne mènent nulle part : les proposer revenait à offrir des épisodes qui n'affichent jamais d'image. |
| Deux langues d'une même saison qui rendent **exactement les mêmes lecteurs** sont ramenées à une seule. | Le site répond `200` pour un segment de langue inexistant et sert alors la version originale : l'application promettait une VF et lisait de la VOSTFR. |
| Les titres alternatifs de la recherche, rendus comme une seule chaîne, sont découpés sur les virgules **et** conservés entiers. | Le découpage est ambigu : certains titres contiennent une virgule. Garder les deux permet de reconnaître un titre correctement isolé comme un titre coupé en deux. |

Le fichier `episodes.js` déclare aussi un tableau `epsAS` de fichiers MP4 directs, que
le script Node ignore. Il reste ignoré ici : ces URL pointent vers le domaine
historique `anime-sama.fr`, qui **ne résout plus** (vérifié sur trois résolveurs).
Les proposer reviendrait à offrir un lecteur systématiquement en échec.

Vérification contre le site réel (test d'intégration ci-dessous) : sorties
identiques à `saisons.json` produit par le script Node.

```
Saison 1 | 12 episodes | VOSTFR, VF
Saison 2 | 13 episodes | VOSTFR, VF, VA
Saison 3 | 13 episodes | VOSTFR, VF, VA
Saison 4 | 16 episodes | VOSTFR, VF, VA
```

---

## 6. Lecteur et filtrage

`WebPlayerHost` configure le WebView : autoplay (`mediaPlaybackRequiresUserGesture
= false`), cookies conservés entre les épisodes, User-Agent configurable, fenêtres
multiples désactivées, plein écran HTML5 relayé à l'interface.

- **Filtrage réseau** (`AdBlockRules`, `shouldInterceptRequest`) : liste explicite de
  régies publicitaires, liste blanche des hôtes de lecteurs qui gagne sur tout le
  reste, et **par défaut on laisse passer** — un domaine inconnu n'est jamais bloqué,
  car les lecteurs dépendent de CDN imprévisibles.
- **Redirections** (`shouldOverrideUrlLoading`) : seules les navigations vers le même
  domaine, les sous-cadres et les redirections serveur du lecteur sont suivies.
- **Popups** : `window.open` neutralisé côté JS, `onCreateWindow` refusé, `target`
  ramené à `_self`.
- **Overlays** (`OverlayScripts`) : CSS pour les sélecteurs publicitaires connus, plus
  un `MutationObserver` qui masque les éléments couvrant plus de 35 % de l'écran avec
  un `z-index` élevé — **jamais** un élément contenant une `video` ou un `iframe`.
- **Échec de lecture** : une source illisible n'émet aucune erreur réseau au niveau
  du WebView — c'est la balise `video` qui le signale. Cet événement est capté et
  déclenche la bascule automatique vers le lecteur suivant.
- **Commandes** : barre de progression avec minutage, puis une seule rangée de
  boutons ronds — saut de ±10 s, lecture/pause, épisode précédent/suivant, et
  l'engrenage. Les touches média et les flèches gauche/droite (contrôles masqués)
  font les mêmes sauts. L'état du bouton Lecture/Pause suit les événements réels de
  la vidéo, pas le clic. Tout appui, y compris un simple déplacement du focus,
  relance le délai d'effacement de la barre.
- **Réglages (engrenage)** : la source et la résolution, les deux seuls choix qui
  concernent la lecture en cours, sont regroupés dans un panneau latéral, ce qui
  dégage la barre. Les lecteurs y gardent leur rang du catalogue ; l'URL n'apparaît
  jamais.
- **Résolution** : la page est interrogée sur ce qu'elle expose réellement — niveaux
  `hls.js`, balises `<source>`, ou menu d'un lecteur tiers. Rien n'est extrapolé :
  quand la page ne permet aucun choix, l'application le dit et affiche seulement la
  hauteur réellement diffusée (`video.videoHeight`) au lieu d'inventer des
  résolutions. Un niveau « Auto » déclaré par la page est reconnu comme tel pour ne
  pas doubler l'entrée automatique.
- **Autoplay** : une promesse `play()` rejetée ne vaut pas échec — elle est souvent
  interrompue par la page elle-même. L'état réel est vérifié, et un refus n'est
  retenu que si les images sont prêtes (`readyState >= 3`) ; sinon c'est le délai de
  garde ci-dessous qui tranche.
- **Lecteur mort sans erreur** : certains hébergeurs répondent 200 avec une page
  « vidéo indisponible », sans balise `video`. Ni le WebView ni l'élément vidéo
  n'émettent alors d'erreur. Un délai de garde conclut à l'échec si aucune vidéo
  n'apparaît, et bascule sur le lecteur suivant.
- **Choix automatique du lecteur** : un lecteur n'est retenu que lorsque des images
  défilent réellement (`currentTime` qui avance), seule mesure fiable d'un
  « chargement fluide » — une page qui répond et une balise `video` présente ne
  garantissent rien. Sans image au bout de douze secondes, ou sur un refus
  d'autoplay, l'application passe au lecteur suivant **sans rien demander**, en
  reprenant à la seconde où l'image s'était arrêtée. Le bouton « Lire » n'est proposé
  qu'une fois toutes les sources épuisées.
- **Ordre des tentatives** : chaque hébergeur porte un score conservé sur le
  téléviseur (`ClassementLecteurs`), incrémenté quand il a diffusé, décrémenté quand
  il n'a rien affiché. Les tentatives suivent ce score, le rang du catalogue ne
  départageant que les hébergeurs inconnus. Le score est borné dans les deux sens :
  un hébergeur en panne une semaine redevient prioritaire en quelques succès. L'ordre
  d'**affichage**, lui, reste celui du catalogue — « Lecteur 2 » désigne toujours le
  deuxième lecteur de la page.
- **Focus** : le WebView est volontairement non focusable
  (`FOCUS_BLOCK_DESCENDANTS`). Sans cela la page web capte les touches
  directionnelles et les commandes de l'application deviennent inatteignables. Le
  focus alterne entre une ancre invisible (contrôles masqués) et le bouton
  Lecture/Pause (contrôles affichés).

### Bande-annonce YouTube, lue sur place

Charger `youtube.com/embed/<id>` comme document principal **ne marche pas** : sans
page parente, YouTube ne considère pas la lecture comme embarquée et propose
d'ouvrir la vidéo sur son site au lieu de la jouer.

`YouTubeEmbed` construit donc une page locale, servie sous l'origine
`https://www.youtube.com` via `loadDataWithBaseURL`, qui porte un vrai `<iframe>`
piloté par l'API IFrame de YouTube. Trois conséquences :

- l'iframe a une page parente et une origine cohérente, donc la vidéo se joue sur
  place ;
- `controls: 0` supprime l'habillage de YouTube — dont le lien « Regarder sur
  YouTube » — et laisse la barre de commandes de l'application seule à l'écran ; un
  voile transparent couvre l'iframe, si bien que rien de YouTube n'est actionnable
  par mégarde ;
- la page expose les mêmes fonctions `window.allAnime*` et le même pont que les
  lecteurs d'épisodes — lecture, pause, saut, progression, fin — si bien que
  l'écran du lecteur n'a rien de particulier à faire pour une bande-annonce. Elle
  pose `window.__allAnimeGuard` dès sa première instruction, ce qui fait se retirer
  le script de protection générique : celui-ci pilote une balise `video`, qui
  n'existe pas ici.

**Les coupures publicitaires sont refusées par le chemin, pas par l'hôte.** Vidéo et
annonces sortent des mêmes serveurs — `youtube.com`, `googlevideo.com` — qu'il faut
donc garder autorisés. `AdBlockRules` vérifie désormais une courte liste de chemins
sans ambiguïté (`/pagead/`, `/api/stats/ads`, `/ptracking`, `/get_midroll_info`,
`/youtubei/v1/player/ad_break`…) **avant** la liste des hôtes autorisés, qui ne peut
donc plus les couvrir. La liste reste volontairement étroite :
`/youtubei/v1/player` porte la lecture elle-même et n'y figure pas.

Une vidéo dont le propriétaire interdit la lecture embarquée (erreurs YouTube 101 et
150) reste injouable : l'application le signale au lieu de renvoyer le spectateur
hors de l'application.

### Limites connues

- Un WebView n'exécute le JS injecté que dans le **document principal**. Si un lecteur
  enferme la vidéo dans un iframe d'une autre origine, le nettoyage des overlays et
  les commandes de lecture ne s'y appliquent pas ; restent le filtrage réseau et le
  changement de lecteur.
- La lecture HLS (`.m3u8`) dépend du WebView du téléviseur.
- L'autoplay peut être refusé par le contenu embarqué : un message et un bouton
  « Lire » prennent alors le relais.

---

## 7. Tests

```bash
./gradlew :app:testDebugUnitTest        # 185 tests hors ligne (+ 7 réseau ignorés)
```

Couvrent la slugification et ses deux formes d'apostrophe, le choix d'une fiche
parmi les réponses du moteur de recherche (titres alternatifs, suites, titres sans
rapport), la reconnaissance des adresses YouTube et la page qui les remplace, l'extraction du slug d'un lien, l'ordre des titres
candidats, l'analyse de `episodes.js` et des panneaux de saison (sur des extraits
**réels** du site, dans `app/src/test/resources/`), l'analyse de la liste du
catalogue et du planning, les règles de fraîcheur du planning, les règles de
filtrage, l'ordre des tentatives de lecteurs, l'inventaire des qualités (dont le
format JSON produit par le script injecté, que la JVM ne peut pas exécuter), la
lecture des vignettes d'épisodes AniList, la traduction des genres et des
énumérations AniList affichées sur la fiche, et le classement sans doublon de
l'accueil.

Deux classes d'intégration dépendent du réseau et sont annotées `@Ignore` ; retirer
l'annotation pour les lancer :

```bash
./gradlew :app:testDebugUnitTest --tests "*CatalogueIntegrationTest"
./gradlew :app:testDebugUnitTest --tests "*AniListIntegrationTest"
```

---

## 8. Identité visuelle

Couleurs reprises du logo : corail `#F94C4C`, blanc, gris `#B7BAC4`, charbon
`#242730`, sur fond sombre `#101216`. La bannière Android TV et les icônes sont
regénérables :

```bash
java tools/BrandAssetGenerator.java app/src/main/res
```

### Logo et états sans contenu

Deux images portent la marque dans l'interface, à la place des formes dessinées en
Compose qu'elles remplaçaient :

| Ressource              | Emploi                                                        |
|------------------------|---------------------------------------------------------------|
| `app_logo`             | En haut à gauche de l'accueil et de la recherche (`LogoAllAnime`) |
| `app_logo_no_data`     | Au-dessus de tout message d'état vide ou en échec (`BlocMessage`) |

`BlocMessage` couvrant les quatre états de `UiState`, l'illustration apparaît
partout où l'application n'a rien à montrer : saison sans épisode disponible,
catalogue injoignable, planning absent, recherche sans résultat, lecteur
inaccessible. Les blocs d'état de la fiche ont été rehaussés pour la loger.

Une seule exception, explicite dans `RechercheScreen` : l'écran de recherche s'ouvre
sur une **invitation** à saisir un titre, pas sur une absence de résultat. Un
personnage en larmes y serait un contresens, donc ce message-là passe
`illustration = false`. La recherche qui ne ramène rien, elle, la garde.

Le logo n'impose que sa hauteur, la largeur suivant le rapport de l'image : un
nouvel export ne demande aucune retouche de code. Deux précautions en revanche du
côté des fichiers, détaillées dans `tools/logos-source/README.md` :

- **le nom** — AAPT2 refuse les majuscules dans un nom de fichier de ressource, donc
  `app_logo_noData.png` fait échouer `mergeDebugResources` ; d'où `app_logo_no_data` ;
- **le détourage** — les logos sont fournis dans un cadre 1280 × 720 où le dessin
  n'occupe qu'un tiers de la hauteur. Les ressources compilées en sont la version
  rognée des pixels entièrement transparents ; les originaux restent dans
  `tools/logos-source/`.
