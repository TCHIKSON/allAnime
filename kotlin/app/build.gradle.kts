import groovy.json.JsonSlurper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Lecture du fichier `.env` place a la racine du projet Gradle (kotlin/.env).
 *
 * Il porte la configuration non secrete (section 31 du cahier des charges) : endpoint
 * AniList et User-Agents. La racine du catalogue, elle, vient d'Infisical sous le nom
 * `BASE_URL_2A` (voir [infisicalSecret]). Rien n'est code en dur dans les ecrans ou
 * les ViewModels, et le build echoue volontairement si une valeur manque, plutot que
 * de retomber sur une valeur inventee.
 */
fun readEnv(file: File): Map<String, String> {
    if (!file.exists()) {
        throw GradleException(
            "Fichier .env introuvable : ${file.absolutePath}. " +
                "Il porte ANILIST_ENDPOINT, PLAYER_USER_AGENT et SCRAPER_USER_AGENT ; " +
                "la racine du catalogue vient d'Infisical (BASE_URL_2A)."
        )
    }
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val separator = line.indexOf('=')
            line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
}

val env = readEnv(rootProject.file(".env"))

/**
 * Signature de la variante `release`, lue depuis `kotlin/keystore.properties`.
 *
 * Ce fichier n'est pas versionne : il contient le mot de passe du magasin de cles.
 * S'il est absent, la variante `release` reste non signee et `assembleRelease`
 * produit un `app-release-unsigned.apk`, qu'Android refuse d'installer.
 */
val signatureProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { fichier ->
        Properties().apply { fichier.inputStream().use { flux -> load(flux) } }
    }

fun requiredEnv(key: String): String = env[key]?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Cle obligatoire absente du fichier .env : $key")

/**
 * Echappe une valeur et l'entoure de guillemets, pour un `buildConfigField` de type
 * String comme pour un litteral JSON : les regles d'echappement sont les memes.
 */
fun asBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/**
 * Acces Infisical, lu depuis `kotlin/local.properties`.
 *
 * Ce fichier n'est pas versionne (il porte deja `sdk.dir`) : c'est la place naturelle
 * du secret de l'identite machine. Le plugin Android ignore les clefs qu'il ne
 * connait pas, les quatre lignes ci-dessous cohabitent donc avec `sdk.dir` :
 *
 *     infisical.clientId=...
 *     infisical.clientSecret=...
 *     infisical.projectId=...
 *     infisical.host=https://eu.infisical.com
 *
 * Chaque reglage peut aussi venir de l'environnement du shell, qui a la priorite sur
 * le fichier : c'est la forme utilisee en CI, ou rien n'est ecrit sur le disque.
 */
val localProperties: Properties? = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.let { fichier ->
        Properties().apply { fichier.inputStream().use { flux -> load(flux) } }
    }

fun infisicalOption(propriete: String, variable: String, defaut: String? = null): String? =
    System.getenv(variable)?.takeIf { it.isNotBlank() }
        ?: localProperties?.getProperty(propriete)?.takeIf { it.isNotBlank() }
        ?: defaut

fun infisicalRequis(propriete: String, variable: String): String =
    infisicalOption(propriete, variable)
        ?: throw GradleException(
            "Reglage Infisical absent : $propriete. Ajoutez a kotlin/local.properties " +
                "les lignes infisical.clientId, infisical.clientSecret, " +
                "infisical.projectId et infisical.host (ou passez-les par " +
                "l'environnement : $variable). Ce fichier n'est jamais versionne."
        )

/**
 * Racine de l'API. `https://eu.infisical.com` pour la region europeenne,
 * `https://app.infisical.com` pour les Etats-Unis, ou une instance auto-hebergee.
 */
val infisicalApi: String =
    infisicalOption("infisical.host", "INFISICAL_HOST", "https://app.infisical.com")!!
        .trimEnd('/')

/**
 * Requete vers l'API Infisical. Renvoie le corps de la reponse tel quel.
 *
 * Un refus (identite invalide, projet inaccessible, secret absent) fait echouer le
 * build en citant la reponse de l'API, jamais les identifiants envoyes.
 */
fun requeteInfisical(url: String, jeton: String?, corpsJson: String?): String {
    val connexion = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = if (corpsJson == null) "GET" else "POST"
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/json")
        jeton?.let { setRequestProperty("Authorization", "Bearer $it") }
        if (corpsJson != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
    }
    try {
        corpsJson?.let { charge ->
            connexion.outputStream.use { flux -> flux.write(charge.toByteArray(Charsets.UTF_8)) }
        }
        val code = connexion.responseCode
        val reponse = (if (code in 200..299) connexion.inputStream else connexion.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (code !in 200..299) {
            throw GradleException(
                "Infisical a refuse la requete (HTTP $code sur ${url.substringBefore('?')}). " +
                    "Verifiez, dans kotlin/local.properties : l'identite machine " +
                    "(infisical.clientId, infisical.clientSecret) et ses droits de lecture, " +
                    "le projet (infisical.projectId), la region (infisical.host), " +
                    "l'environnement (infisical.environment, defaut : dev) et le chemin " +
                    "(infisical.secretPath, defaut : /). Reponse : ${reponse.take(500)}"
            )
        }
        return reponse
    } catch (erreur: IOException) {
        throw GradleException(
            "API Infisical inaccessible ($infisicalApi) : ${erreur.message}",
            erreur
        )
    } finally {
        connexion.disconnect()
    }
}

/**
 * Jeton d'acces obtenu par Universal Auth, a partir de l'identite machine.
 *
 * Calcule au plus une fois par configuration du build, quel que soit le nombre de
 * secrets lus, et jamais journalise.
 */
val infisicalAccessToken: String by lazy {
    val charge = "{\"clientId\":" +
        asBuildConfigString(infisicalRequis("infisical.clientId", "INFISICAL_CLIENT_ID")) +
        ",\"clientSecret\":" +
        asBuildConfigString(
            infisicalRequis("infisical.clientSecret", "INFISICAL_CLIENT_SECRET")
        ) +
        "}"
    val reponse = requeteInfisical(
        url = "$infisicalApi/api/v1/auth/universal-auth/login",
        jeton = null,
        corpsJson = charge
    )
    (JsonSlurper().parseText(reponse) as? Map<*, *>)?.get("accessToken") as? String
        ?: throw GradleException(
            "Reponse d'authentification Infisical inattendue : aucun accessToken. " +
                "L'identite machine utilise-t-elle bien Universal Auth ?"
        )
}

/**
 * Lit un secret dans Infisical. Deux chemins, dans cet ordre :
 *
 *  1. une variable d'environnement du meme nom, ce qui couvre
 *     `infisical run -- ./gradlew :app:assembleDebug` et les runners CI qui
 *     injectent deja le secret ;
 *  2. l'API Infisical, authentifiee par l'identite machine.
 *
 * Aucune valeur de repli : un secret introuvable fait echouer le build, exactement
 * comme une clef absente du `.env`.
 */
fun infisicalSecret(cle: String): String {
    System.getenv(cle)?.takeIf { it.isNotBlank() }?.let { return it.trim() }

    val projet = infisicalRequis("infisical.projectId", "INFISICAL_PROJECT_ID")
    val environnement = infisicalOption("infisical.environment", "INFISICAL_ENV", "dev")!!
    val chemin = infisicalOption("infisical.secretPath", "INFISICAL_PATH", "/")!!
    val parametres = listOf(
        "workspaceId" to projet,
        "environment" to environnement,
        "secretPath" to chemin
    ).joinToString("&") { (nom, valeur) -> "$nom=${URLEncoder.encode(valeur, "UTF-8")}" }

    val reponse = requeteInfisical(
        url = "$infisicalApi/api/v3/secrets/raw/${URLEncoder.encode(cle, "UTF-8")}?$parametres",
        jeton = infisicalAccessToken,
        corpsJson = null
    )
    val secret = (JsonSlurper().parseText(reponse) as? Map<*, *>)?.get("secret") as? Map<*, *>
    return (secret?.get("secretValue") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException(
            "Secret Infisical $cle absent ou vide (projet $projet, environnement " +
                "$environnement, chemin $chemin). Creez-le dans Infisical, ou ajustez " +
                "infisical.environment et infisical.secretPath dans local.properties."
        )
}

android {
    namespace = "com.allanime.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.allanime.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"

        // Racine du catalogue : secret Infisical, jamais versionne dans le `.env`.
        buildConfigField(
            "String",
            "BASE_URL",
            asBuildConfigString(infisicalSecret("BASE_URL_2A"))
        )
        buildConfigField(
            "String",
            "ANILIST_ENDPOINT",
            asBuildConfigString(requiredEnv("ANILIST_ENDPOINT"))
        )
        buildConfigField(
            "String",
            "PLAYER_USER_AGENT",
            asBuildConfigString(requiredEnv("PLAYER_USER_AGENT"))
        )
        buildConfigField(
            "String",
            "SCRAPER_USER_AGENT",
            asBuildConfigString(requiredEnv("SCRAPER_USER_AGENT"))
        )
    }

    signingConfigs {
        signatureProperties?.let { proprietes ->
            create("release") {
                storeFile = rootProject.file(proprietes.getProperty("storeFile"))
                storePassword = proprietes.getProperty("storePassword")
                keyAlias = proprietes.getProperty("keyAlias")
                keyPassword = proprietes.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            // Les scrapers journalisent via android.util.Log : en test unitaire on
            // veut une valeur par defaut plutot qu'une exception.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")

    // Composants Material specifiques Android TV (focus, cartes, surfaces "TV-safe").
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // Historique de visionnage conserve sur le televiseur.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Couche reseau : AniList (GraphQL) + scraping du catalogue.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
