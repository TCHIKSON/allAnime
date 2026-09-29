package com.allanime.tv.data.anilist

import com.allanime.tv.core.HtmlText
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.AnimeTag
import com.allanime.tv.domain.model.AnimeTitle
import com.allanime.tv.domain.model.Character
import com.allanime.tv.domain.model.CharacterRole
import com.allanime.tv.domain.model.CoverImage
import com.allanime.tv.domain.model.ExternalLink
import com.allanime.tv.domain.model.FuzzyDate
import com.allanime.tv.domain.model.Ranking
import com.allanime.tv.domain.model.Recommendation
import com.allanime.tv.domain.model.StaffMember
import com.allanime.tv.domain.model.StaffRole
import com.allanime.tv.domain.model.StreamingEpisode
import com.allanime.tv.domain.model.Studio
import com.allanime.tv.domain.model.Trailer
import com.allanime.tv.domain.model.VoiceActor

/**
 * Conversion des DTO AniList vers les modeles de domaine.
 *
 * Les entrees inexploitables (noeud absent, nom vide) sont ecartees plutot que
 * remplacees par une valeur inventee.
 */
internal object AniListMapper {

    fun toDomain(dto: MediaDto): Anime = Anime(
        id = dto.id,
        idMal = dto.idMal,
        title = AnimeTitle(
            romaji = dto.title?.romaji?.nullIfBlank(),
            english = dto.title?.english?.nullIfBlank(),
            nativeTitle = dto.title?.nativeTitle?.nullIfBlank()
        ),
        synonyms = dto.synonyms.filter { it.isNotBlank() },
        description = HtmlText.toPlainText(dto.description),
        format = dto.format,
        status = dto.status,
        episodes = dto.episodes,
        duration = dto.duration,
        season = dto.season,
        seasonYear = dto.seasonYear,
        source = dto.source,
        hashtag = dto.hashtag,
        countryOfOrigin = dto.countryOfOrigin,
        isAdult = dto.isAdult ?: false,
        averageScore = dto.averageScore,
        meanScore = dto.meanScore,
        popularity = dto.popularity,
        favourites = dto.favourites,
        trending = dto.trending,
        genres = dto.genres.filter { it.isNotBlank() },
        tags = dto.tags.mapNotNull { tag ->
            val name = tag.name?.nullIfBlank() ?: return@mapNotNull null
            AnimeTag(
                id = tag.id,
                name = name,
                description = HtmlText.toPlainText(tag.description),
                rank = tag.rank,
                isMediaSpoiler = tag.isMediaSpoiler ?: false
            )
        },
        bannerImage = dto.bannerImage?.nullIfBlank(),
        coverImage = CoverImage(
            extraLarge = dto.coverImage?.extraLarge?.nullIfBlank(),
            large = dto.coverImage?.large?.nullIfBlank(),
            medium = dto.coverImage?.medium?.nullIfBlank(),
            color = dto.coverImage?.color?.nullIfBlank()
        ),
        startDate = dto.startDate?.toDomain(),
        endDate = dto.endDate?.toDomain(),
        studios = dto.studios?.edges.orEmpty().mapNotNull { edge ->
            val node = edge.node ?: return@mapNotNull null
            val name = node.name?.nullIfBlank() ?: return@mapNotNull null
            Studio(
                id = node.id,
                name = name,
                isAnimationStudio = node.isAnimationStudio ?: false,
                siteUrl = node.siteUrl,
                isMain = edge.isMain ?: false
            )
        },
        characters = dto.characters?.edges.orEmpty().mapNotNull { edge ->
            val node = edge.node ?: return@mapNotNull null
            val fullName = node.name?.full?.nullIfBlank() ?: return@mapNotNull null
            CharacterRole(
                role = edge.role,
                character = Character(
                    id = node.id,
                    fullName = fullName,
                    nativeName = node.name.nativeName?.nullIfBlank(),
                    image = node.image?.large?.nullIfBlank(),
                    gender = node.gender,
                    age = node.age,
                    description = HtmlText.toPlainText(node.description)
                ),
                voiceActors = edge.voiceActors.mapNotNull { actor ->
                    val actorName = actor.name?.full?.nullIfBlank() ?: return@mapNotNull null
                    VoiceActor(
                        id = actor.id,
                        fullName = actorName,
                        nativeName = actor.name.nativeName?.nullIfBlank(),
                        image = actor.image?.large?.nullIfBlank()
                    )
                }
            )
        },
        staff = dto.staff?.edges.orEmpty().mapNotNull { edge ->
            val node = edge.node ?: return@mapNotNull null
            val fullName = node.name?.full?.nullIfBlank() ?: return@mapNotNull null
            StaffRole(
                role = edge.role,
                member = StaffMember(
                    id = node.id,
                    fullName = fullName,
                    nativeName = node.name.nativeName?.nullIfBlank(),
                    image = node.image?.large?.nullIfBlank(),
                    description = HtmlText.toPlainText(node.description)
                )
            )
        },
        rankings = dto.rankings.map { ranking ->
            Ranking(
                rank = ranking.rank,
                type = ranking.type,
                format = ranking.format,
                year = ranking.year,
                season = ranking.season,
                allTime = ranking.allTime ?: false,
                context = ranking.context
            )
        },
        recommendations = dto.recommendations?.nodes.orEmpty().mapNotNull { node ->
            val media = node.mediaRecommendation ?: return@mapNotNull null
            Recommendation(
                rating = node.rating,
                mediaId = media.id,
                titleRomaji = media.title?.romaji?.nullIfBlank(),
                titleEnglish = media.title?.english?.nullIfBlank(),
                coverImage = media.coverImage?.large?.nullIfBlank()
            )
        },
        externalLinks = dto.externalLinks.map { link ->
            ExternalLink(
                id = link.id,
                url = link.url?.nullIfBlank(),
                site = link.site?.nullIfBlank(),
                type = link.type,
                language = link.language,
                color = link.color,
                icon = link.icon?.nullIfBlank()
            )
        },
        streamingEpisodes = dto.streamingEpisodes.map { episode ->
            StreamingEpisode(
                title = episode.title?.nullIfBlank(),
                thumbnail = episode.thumbnail?.nullIfBlank(),
                url = episode.url?.nullIfBlank(),
                site = episode.site?.nullIfBlank()
            )
        },
        trailer = dto.trailer?.let { trailer ->
            Trailer(
                id = trailer.id?.nullIfBlank(),
                site = trailer.site?.nullIfBlank(),
                thumbnail = trailer.thumbnail?.nullIfBlank()
            )
        },
        siteUrl = dto.siteUrl?.nullIfBlank(),
        updatedAt = dto.updatedAt
    )

    private fun FuzzyDateDto.toDomain(): FuzzyDate? {
        val date = FuzzyDate(year = year, month = month, day = day)
        return if (date.isEmpty) null else date
    }

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
}
