package com.arjunaayush.looply.domain.model

enum class ReelSortOrder(val displayName: String) {
    RECENTLY_ADDED("Recently Added"),
    OLDEST("Oldest Added"),
    LEAST_VIEWED("Least Viewed"),
    MOST_VIEWED("Most Viewed"),
    SIZE_DESC("Largest File"),
    SIZE_ASC("Smallest File"),
    CREATOR_A_Z("Creator (A–Z)")
}

enum class ReelSmartFilter(val displayName: String) {
    ALL("All"),
    LIKED("Liked"),
    UNWATCHED("Unwatched"),
    WATCHED("Watched")
}

fun List<Video>.applyFilterAndSort(
    filter: ReelSmartFilter = ReelSmartFilter.ALL,
    sortOrder: ReelSortOrder = ReelSortOrder.RECENTLY_ADDED,
    creator: String? = null
): List<Video> {
    var result = this

    if (!creator.isNullOrBlank()) {
        result = result.filter { it.displayAuthor.equals(creator, ignoreCase = true) }
    }

    result = when (filter) {
        ReelSmartFilter.ALL -> result
        ReelSmartFilter.LIKED -> result.filter { it.isFavorite }
        ReelSmartFilter.UNWATCHED -> result.filter { !it.isWatched }
        ReelSmartFilter.WATCHED -> result.filter { it.isWatched }
    }

    return when (sortOrder) {
        ReelSortOrder.RECENTLY_ADDED -> result.sortedByDescending { it.createdAt }
        ReelSortOrder.OLDEST -> result.sortedBy { it.createdAt }
        ReelSortOrder.LEAST_VIEWED -> result.sortedWith(
            compareBy<Video> { it.isWatched }.thenByDescending { it.createdAt }
        )
        ReelSortOrder.MOST_VIEWED -> result.sortedWith(
            compareByDescending<Video> { it.isWatched }.thenByDescending { it.createdAt }
        )
        ReelSortOrder.SIZE_DESC -> result.sortedByDescending { it.sizeBytes }
        ReelSortOrder.SIZE_ASC -> result.sortedBy { it.sizeBytes }
        ReelSortOrder.CREATOR_A_Z -> result.sortedBy { it.displayAuthor.lowercase() }
    }
}
