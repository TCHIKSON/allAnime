package com.allanime.tv.ui.recherche

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.allanime.tv.R

/**
 * Recherche vocale de l'ecran de recherche.
 *
 * Deux voies, essayees dans cet ordre :
 *
 *  1. **l'ecoute dans l'application** ([SpeechRecognizer]) : l'ecran reste affiche,
 *     la touche indique qu'on ecoute, et le texte entendu s'ecrit dans le champ au
 *     fil de la phrase. C'est la voie des televiseurs, dont le micro est sur la
 *     telecommande ; elle demande la permission du micro au premier usage ;
 *  2. **la fenetre de dictee du systeme** ([RecognizerIntent]), si l'ecoute dans
 *     l'application n'existe pas sur l'appareil, si la permission est refusee ou si
 *     l'ecoute echoue avant d'avoir commence.
 *
 * Quand aucune des deux n'existe, [message] le dit : une touche sans effet laisserait
 * croire a une panne.
 */
class DicteeVocale internal constructor() {
    /** `true` pendant que le micro ecoute. */
    var enEcoute by mutableStateOf(false)
        internal set

    /** Ressource du message a montrer a l'utilisateur, ou `null` quand tout va bien. */
    var message by mutableStateOf<Int?>(null)
        internal set

    internal var surAppui: () -> Unit = {}

    /** Appui sur la touche : lance l'ecoute, ou l'arrete si elle est en cours. */
    fun basculer() = surAppui()
}

/**
 * @param onPartiel texte reconnu en cours de phrase, pour l'afficher sans attendre.
 * @param onResultat texte definitif : c'est lui qui lance la recherche.
 */
@Composable
fun rememberDicteeVocale(
    onPartiel: (String) -> Unit,
    onResultat: (String) -> Unit
): DicteeVocale {
    val contexte = LocalContext.current
    val partiel by rememberUpdatedState(onPartiel)
    val resultat by rememberUpdatedState(onResultat)

    // Cree a la premiere ecoute seulement : inutile de reserver le service de
    // reconnaissance pour qui n'utilise que le clavier.
    val reconnaissance = remember { mutableStateOf<SpeechRecognizer?>(null) }
    val dictee = remember { DicteeVocale() }

    val fenetreSysteme = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { retour ->
        val entendu = retour.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (retour.resultCode == Activity.RESULT_OK && !entendu.isNullOrBlank()) {
            resultat(entendu)
        }
    }

    fun ouvrirFenetreSysteme() {
        dictee.enEcoute = false
        dictee.message = try {
            fenetreSysteme.launch(
                demande(contexte).putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    contexte.getString(R.string.search_voice_prompt)
                )
            )
            null
        } catch (absente: ActivityNotFoundException) {
            R.string.search_voice_unavailable
        }
    }

    fun ecouter() {
        val service = reconnaissance.value
            ?: SpeechRecognizer.createSpeechRecognizer(contexte).also { cree ->
                cree.setRecognitionListener(
                    Ecouteur(
                        onPret = { dictee.enEcoute = true },
                        onPartiel = { partiel(it) },
                        onResultat = {
                            dictee.enEcoute = false
                            resultat(it)
                        },
                        onEchec = { code, avaitCommence ->
                            Log.w(TAG, "Ecoute interrompue (erreur $code)")
                            // Deja arretee par un second appui : le service le
                            // signale par une erreur, qui n'en est pas une.
                            val arretVoulu = !dictee.enEcoute
                            dictee.enEcoute = false
                            when {
                                arretVoulu -> Unit
                                code in SILENCES -> dictee.message = R.string.search_voice_nothing
                                // Le service a refuse avant meme d'ecouter : la
                                // fenetre du systeme, elle, peut encore marcher.
                                !avaitCommence -> ouvrirFenetreSysteme()
                                else -> dictee.message = R.string.search_voice_failed
                            }
                        }
                    )
                )
                reconnaissance.value = cree
            }

        dictee.message = null
        dictee.enEcoute = true
        service.startListening(
            demande(contexte).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accordee ->
        if (accordee) ecouter() else ouvrirFenetreSysteme()
    }

    dictee.surAppui = {
        val micro = ContextCompat.checkSelfPermission(
            contexte,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        when {
            dictee.enEcoute -> {
                dictee.enEcoute = false
                reconnaissance.value?.stopListening()
            }

            !SpeechRecognizer.isRecognitionAvailable(contexte) -> ouvrirFenetreSysteme()
            micro -> ecouter()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            reconnaissance.value?.destroy()
            reconnaissance.value = null
        }
    }

    return dictee
}

/** Demande de reconnaissance commune aux deux voies : une phrase libre. */
private fun demande(contexte: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, contexte.packageName)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

/**
 * Traduit les rappels du service en quatre evenements.
 *
 * @param onEchec recoit le code d'erreur, et si l'ecoute avait deja commence — ce
 *   qui distingue un service qui refuse de demarrer d'une phrase mal comprise.
 */
private class Ecouteur(
    private val onPret: () -> Unit,
    private val onPartiel: (String) -> Unit,
    private val onResultat: (String) -> Unit,
    private val onEchec: (code: Int, avaitCommence: Boolean) -> Unit
) : RecognitionListener {

    private var commence = false

    override fun onReadyForSpeech(params: Bundle?) {
        commence = true
        onPret()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        premier(partialResults)?.let(onPartiel)
    }

    override fun onResults(results: Bundle?) {
        commence = false
        val entendu = premier(results)
        if (entendu == null) onEchec(SpeechRecognizer.ERROR_NO_MATCH, true) else onResultat(entendu)
    }

    override fun onError(error: Int) {
        val avaitCommence = commence
        commence = false
        onEchec(error, avaitCommence)
    }

    private fun premier(lot: Bundle?): String? =
        lot?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private const val TAG = "DicteeVocale"

/** Rien n'a ete dit, ou rien n'a ete compris : on invite a recommencer. */
private val SILENCES = setOf(
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT
)
