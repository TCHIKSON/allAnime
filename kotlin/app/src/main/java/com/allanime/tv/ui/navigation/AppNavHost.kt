package com.allanime.tv.ui.navigation

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.allanime.tv.ui.accueil.AccueilScreen
import com.allanime.tv.ui.catalogue.CatalogueFiltreScreen
import com.allanime.tv.ui.catalogue.FiltreCatalogue
import com.allanime.tv.ui.components.DialogueSortie
import com.allanime.tv.ui.components.EcranDemarrage
import com.allanime.tv.ui.fiche.FicheScreen
import com.allanime.tv.ui.fiche.ReferenceFiche
import com.allanime.tv.ui.lecteur.LecteurDirectScreen
import com.allanime.tv.ui.lecteur.LecteurScreen
import com.allanime.tv.ui.planning.PlanningScreen
import com.allanime.tv.ui.recherche.RechercheScreen

/**
 * Graphe de navigation de l'application.
 *
 * Deux familles de destinations, qui ne se comportent pas pareil :
 *
 *  - les **ecrans principaux** — accueil, recherche, series, films, planning —
 *    partagent la barre laterale et forment un seul niveau : passer de l'un a
 *    l'autre remplace, il n'empile pas. Sans cela, un aller-retour entre deux
 *    rubriques laisserait une pile de retours a depiler une par une ;
 *  - la **page d'une serie** et le **lecteur**, qui s'empilent normalement, le
 *    bouton Retour les depilant dans l'ordre inverse.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val activite = LocalContext.current as? Activity
    var demandeDeSortie by remember { mutableStateOf(false) }

    // Vrai jusqu'a ce que l'accueil ait rendu son premier resultat, bon ou mauvais.
    var demarrage by rememberSaveable { mutableStateOf(true) }

    /** Passe d'un ecran principal a l'autre sans empiler : ils sont au meme niveau. */
    fun allerVers(destination: DestinationPrincipale) {
        val route = when (destination) {
            DestinationPrincipale.ACCUEIL -> Routes.ACCUEIL
            DestinationPrincipale.RECHERCHE -> Routes.RECHERCHE
            DestinationPrincipale.SERIES -> Routes.SERIES
            DestinationPrincipale.FILMS -> Routes.FILMS
            DestinationPrincipale.PLANNING -> Routes.PLANNING
        }
        navController.navigate(route) {
            popUpTo(Routes.ACCUEIL) { inclusive = destination == DestinationPrincipale.ACCUEIL }
            launchSingleTop = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = Routes.ACCUEIL) {

        composable(Routes.ACCUEIL) {
            CadrePrincipal(
                courante = DestinationPrincipale.ACCUEIL,
                onNaviguer = ::allerVers,
                // L'accueil est la racine : d'ici, remonter d'un cran veut dire
                // quitter l'application, ce qui se demande avant de se faire.
                onRetourDepuisBarre = { demandeDeSortie = true }
            ) { margeDebut ->
                AccueilScreen(
                    margeDebut = margeDebut,
                    onPret = { demarrage = false },
                    onOuvrirCatalogue = { slug ->
                        navController.navigate(Routes.ficheCatalogue(Uri.encode(slug)))
                    },
                    // La reprise ramene sur la page de la serie, ou l'episode en
                    // cours est propose mais ou un autre reste selectionnable. On y
                    // retourne par le slug quand il est connu : c'est la voie
                    // directe vers les episodes, et la seule qui marche pour une
                    // serie qu'AniList ignore.
                    onReprendre = { progression ->
                        val destination = if (progression.slug != null) {
                            Routes.ficheCatalogue(Uri.encode(progression.slug))
                        } else {
                            Routes.fiche(progression.animeId)
                        }
                        navController.navigate(destination)
                    }
                )
            }
        }

        composable(Routes.RECHERCHE) {
            CadrePrincipal(
                courante = DestinationPrincipale.RECHERCHE,
                onNaviguer = ::allerVers,
                onRetourDepuisBarre = { allerVers(DestinationPrincipale.ACCUEIL) }
            ) { margeDebut ->
                RechercheScreen(
                    margeDebut = margeDebut,
                    onOuvrirFiche = { animeId ->
                        navController.navigate(Routes.fiche(animeId))
                    },
                    onOuvrirCatalogue = { slug ->
                        navController.navigate(Routes.ficheCatalogue(Uri.encode(slug)))
                    }
                )
            }
        }

        composable(Routes.SERIES) {
            CadrePrincipal(
                courante = DestinationPrincipale.SERIES,
                onNaviguer = ::allerVers,
                onRetourDepuisBarre = { allerVers(DestinationPrincipale.ACCUEIL) }
            ) { margeDebut ->
                CatalogueFiltreScreen(
                    filtre = FiltreCatalogue.SERIES,
                    margeDebut = margeDebut,
                    onOuvrirCatalogue = { slug ->
                        navController.navigate(Routes.ficheCatalogue(Uri.encode(slug)))
                    }
                )
            }
        }

        composable(Routes.FILMS) {
            CadrePrincipal(
                courante = DestinationPrincipale.FILMS,
                onNaviguer = ::allerVers,
                onRetourDepuisBarre = { allerVers(DestinationPrincipale.ACCUEIL) }
            ) { margeDebut ->
                CatalogueFiltreScreen(
                    filtre = FiltreCatalogue.FILMS,
                    margeDebut = margeDebut,
                    onOuvrirCatalogue = { slug ->
                        navController.navigate(Routes.ficheCatalogue(Uri.encode(slug)))
                    }
                )
            }
        }

        composable(Routes.PLANNING) {
            CadrePrincipal(
                courante = DestinationPrincipale.PLANNING,
                onNaviguer = ::allerVers,
                onRetourDepuisBarre = { allerVers(DestinationPrincipale.ACCUEIL) }
            ) { margeDebut ->
                PlanningScreen(
                    margeDebut = margeDebut,
                    onOuvrirCatalogue = { slug ->
                        navController.navigate(Routes.ficheCatalogue(Uri.encode(slug)))
                    }
                )
            }
        }

        composable(
            route = Routes.FICHE,
            arguments = listOf(navArgument(Routes.ARG_ANIME_ID) { type = NavType.IntType })
        ) { entree ->
            val animeId = entree.arguments?.getInt(Routes.ARG_ANIME_ID) ?: return@composable

            PageSerie(navController, ReferenceFiche.ParId(animeId))
        }

        composable(
            route = Routes.FICHE_CATALOGUE,
            arguments = listOf(navArgument(Routes.ARG_SLUG) { type = NavType.StringType })
        ) { entree ->
            val slug = Uri.decode(entree.arguments?.getString(Routes.ARG_SLUG).orEmpty())
            if (slug.isBlank()) return@composable

            PageSerie(navController, ReferenceFiche.ParSlug(slug))
        }

        composable(
            route = Routes.LECTEUR,
            arguments = listOf(
                navArgument(Routes.ARG_ANIME_ID) { type = NavType.IntType },
                navArgument(Routes.ARG_SAISON) { type = NavType.IntType },
                navArgument(Routes.ARG_LANGUE) { type = NavType.StringType },
                navArgument(Routes.ARG_EPISODE) { type = NavType.IntType }
            )
        ) { entree ->
            val arguments = entree.arguments ?: return@composable

            LecteurScreen(
                animeId = arguments.getInt(Routes.ARG_ANIME_ID),
                indexSaison = arguments.getInt(Routes.ARG_SAISON),
                codeLangue = arguments.getString(Routes.ARG_LANGUE).orEmpty(),
                numeroEpisode = arguments.getInt(Routes.ARG_EPISODE),
                onRetour = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.LECTEUR_DIRECT,
            arguments = listOf(
                navArgument(Routes.ARG_URL) { type = NavType.StringType },
                navArgument(Routes.ARG_TITRE) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entree ->
            val url = entree.arguments?.getString(Routes.ARG_URL).orEmpty()
            val titre = entree.arguments?.getString(Routes.ARG_TITRE).orEmpty()

            LecteurDirectScreen(
                url = Uri.decode(url),
                titre = Uri.decode(titre),
                onRetour = { navController.popBackStack() }
            )
        }
    }

        // L'ecran de demarrage recouvre tout, barre laterale comprise, tant que
        // l'accueil charge ; il s'efface en fondu des que le catalogue a repondu.
        AnimatedVisibility(visible = demarrage, enter = EnterTransition.None, exit = fadeOut()) {
            EcranDemarrage()
        }

        // Posee au-dessus du graphe, et non dans l'accueil : elle doit recouvrir
        // l'ecran entier, barre laterale comprise.
        if (demandeDeSortie) {
            DialogueSortie(
                onConfirmer = { activite?.finish() },
                onAnnuler = { demandeDeSortie = false }
            )
        }
    }
}

/**
 * Page de la serie, identique quelle que soit la porte d'entree empruntee.
 *
 * L'identifiant de l'anime n'est connu qu'une fois la fiche resolue — venant du
 * catalogue, on ne connaissait que le slug : c'est donc l'ecran qui le fournit au
 * moment d'ouvrir le lecteur.
 */
@Composable
private fun PageSerie(navController: NavHostController, reference: ReferenceFiche) {
    FicheScreen(
        reference = reference,
        onLireEpisode = { animeId, indexSaison, codeLangue, numeroEpisode ->
            navController.navigate(
                Routes.lecteur(animeId, indexSaison, codeLangue, numeroEpisode)
            )
        },
        onOuvrirFiche = { autre -> navController.navigate(Routes.fiche(autre)) },
        // La bande-annonce reste dans l'application : elle passe par le meme
        // lecteur que les episodes, sans lecteur ni navigateur externe.
        onLireBandeAnnonce = { url, titre ->
            navController.navigate(Routes.lecteurDirect(Uri.encode(url), Uri.encode(titre)))
        }
    )
}
