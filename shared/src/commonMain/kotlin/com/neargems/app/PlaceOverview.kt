package com.neargems.app

import com.neargems.data.VisitedPlace
import com.neargems.platform.SeenPlace

/** How many places the user has discovered (seen) and how many they have marked as visited. */
data class PlaceOverview(val discovered: Int, val visited: Int)

/** The counts shown in Settings. Each place is counted once, whether it is near the user or far away. */
fun placeOverview(seen: List<SeenPlace>, visited: List<VisitedPlace>): PlaceOverview =
    PlaceOverview(discovered = seen.size, visited = visited.size)
