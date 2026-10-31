package com.neargems.domain

/** Ideas offered as the user types a keyword. Any word can still be added by hand. */
val SUGGESTED_KEYWORDS: List<String> = listOf(
    // Places of worship
    "temple", "church", "cathedral", "mosque", "gurdwara", "synagogue", "shrine",
    // Culture and history
    "museum", "gallery", "theatre", "library", "mural", "statue", "monument", "memorial",
    "architecture", "public building",
    "castle", "palace", "historic", "heritage",
    // Outdoors and scenery
    "park", "garden", "waterfall", "viewpoint", "lake", "beach", "pier", "lighthouse",
    "woodland", "hill", "river", "canal", "bridge",
    // Food
    "vegan", "vegetarian", "pizza", "sushi", "curry", "dim sum", "bakery", "brunch", "ramen",
    "tapas", "kebab", "ice cream", "street food", "dessert", "seafood",
    // Drinks and evenings
    "cocktail", "wine bar", "craft beer", "brewery", "rooftop", "pub garden", "live music",
    "jazz", "tea room",
    // Fun and shopping
    "cinema", "aquarium", "zoo", "market", "bookshop", "arcade", "climbing",
    "airport", "railway station",
)

/**
 * Suggestions for what has been typed so far: words that start with it come first, then words that
 * contain it. Words already chosen are left out, and the list is kept short.
 */
fun suggestKeywords(typed: String, chosen: List<String>, limit: Int = 8): List<String> {
    val query = typed.trim().lowercase()
    val available = SUGGESTED_KEYWORDS.filter { it !in chosen }
    if (query.isEmpty()) return available.take(limit)
    val starts = available.filter { it.startsWith(query) }
    val contains = available.filter { it.contains(query) && !it.startsWith(query) }
    return (starts + contains).take(limit)
}
