package com.allanime.tv.data.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representation brute des reponses AniList.
 *
 * Tout est nullable ou pourvu d'une valeur par defaut : AniList omet regulierement
 * des champs (fiches non encore diffusees, medias sans studio, sans banniere...).
 * La conversion vers les modeles de domaine est faite par [AniListMapper].
 */

@Serializable
internal data class GraphQlRequest(
    val query: String,
    val variables: Map<String, kotlinx.serialization.json.JsonElement>? = null
)

@Serializable
internal data class GraphQlError(
    val message: String? = null
)

@Serializable
internal data class PageResponse(
    val data: PageData? = null,
    val errors: List<GraphQlError> = emptyList()
)

@Serializable
internal data class PageData(
    @SerialName("Page") val page: MediaPageDto? = null
)

@Serializable
internal data class MediaPageDto(
    val media: List<MediaDto> = emptyList()
)

@Serializable
internal data class MediaResponse(
    val data: MediaData? = null,
    val errors: List<GraphQlError> = emptyList()
)

@Serializable
internal data class MediaData(
    @SerialName("Media") val media: MediaDto? = null
)

@Serializable
internal data class MediaDto(
    val id: Int,
    val idMal: Int? = null,
    val title: TitleDto? = null,
    val synonyms: List<String> = emptyList(),
    val description: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val source: String? = null,
    val hashtag: String? = null,
    val countryOfOrigin: String? = null,
    val isAdult: Boolean? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val trending: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val bannerImage: String? = null,
    val coverImage: CoverImageDto? = null,
    val startDate: FuzzyDateDto? = null,
    val endDate: FuzzyDateDto? = null,
    val studios: StudioConnectionDto? = null,
    val characters: CharacterConnectionDto? = null,
    val staff: StaffConnectionDto? = null,
    val rankings: List<RankingDto> = emptyList(),
    val recommendations: RecommendationConnectionDto? = null,
    val externalLinks: List<ExternalLinkDto> = emptyList(),
    val streamingEpisodes: List<StreamingEpisodeDto> = emptyList(),
    val trailer: TrailerDto? = null,
    val siteUrl: String? = null,
    val updatedAt: Long? = null
)

@Serializable
internal data class TitleDto(
    val romaji: String? = null,
    val english: String? = null,
    @SerialName("native") val nativeTitle: String? = null
)

@Serializable
internal data class NameDto(
    val full: String? = null,
    @SerialName("native") val nativeName: String? = null
)

@Serializable
internal data class ImageDto(
    val large: String? = null
)

@Serializable
internal data class CoverImageDto(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
    val color: String? = null
)

@Serializable
internal data class FuzzyDateDto(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
)

@Serializable
internal data class TagDto(
    val id: Int? = null,
    val name: String? = null,
    val description: String? = null,
    val rank: Int? = null,
    val isMediaSpoiler: Boolean? = null
)

@Serializable
internal data class StudioConnectionDto(
    val edges: List<StudioEdgeDto> = emptyList()
)

@Serializable
internal data class StudioEdgeDto(
    val isMain: Boolean? = null,
    val node: StudioNodeDto? = null
)

@Serializable
internal data class StudioNodeDto(
    val id: Int? = null,
    val name: String? = null,
    val isAnimationStudio: Boolean? = null,
    val siteUrl: String? = null
)

@Serializable
internal data class CharacterConnectionDto(
    val edges: List<CharacterEdgeDto> = emptyList()
)

@Serializable
internal data class CharacterEdgeDto(
    val role: String? = null,
    val voiceActors: List<VoiceActorDto> = emptyList(),
    val node: CharacterNodeDto? = null
)

@Serializable
internal data class VoiceActorDto(
    val id: Int? = null,
    val name: NameDto? = null,
    val image: ImageDto? = null
)

@Serializable
internal data class CharacterNodeDto(
    val id: Int? = null,
    val name: NameDto? = null,
    val image: ImageDto? = null,
    val gender: String? = null,
    val age: String? = null,
    val description: String? = null
)

@Serializable
internal data class StaffConnectionDto(
    val edges: List<StaffEdgeDto> = emptyList()
)

@Serializable
internal data class StaffEdgeDto(
    val role: String? = null,
    val node: StaffNodeDto? = null
)

@Serializable
internal data class StaffNodeDto(
    val id: Int? = null,
    val name: NameDto? = null,
    val image: ImageDto? = null,
    val description: String? = null
)

@Serializable
internal data class RankingDto(
    val rank: Int? = null,
    val type: String? = null,
    val format: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val allTime: Boolean? = null,
    val context: String? = null
)

@Serializable
internal data class RecommendationConnectionDto(
    val nodes: List<RecommendationNodeDto> = emptyList()
)

@Serializable
internal data class RecommendationNodeDto(
    val rating: Int? = null,
    val mediaRecommendation: RecommendedMediaDto? = null
)

@Serializable
internal data class RecommendedMediaDto(
    val id: Int? = null,
    val title: TitleDto? = null,
    val coverImage: CoverImageDto? = null
)

@Serializable
internal data class ExternalLinkDto(
    val id: Int? = null,
    val url: String? = null,
    val site: String? = null,
    val type: String? = null,
    val language: String? = null,
    val color: String? = null,
    val icon: String? = null
)

@Serializable
internal data class StreamingEpisodeDto(
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    val site: String? = null
)

@Serializable
internal data class TrailerDto(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null
)
