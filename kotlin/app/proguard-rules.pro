# Les modeles AniList sont deserialises par kotlinx.serialization : on conserve
# les serializers generes.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Interfaces JavaScript exposees au WebView du lecteur.
-keepclassmembers class com.allanime.tv.player.** {
    @android.webkit.JavascriptInterface <methods>;
}
