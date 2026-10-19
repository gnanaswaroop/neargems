package com.nearwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.nearwise.domain.LatLon
import com.nearwise.domain.distanceMeters
import com.nearwise.domain.formatDistance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

private const val TILE_PX = 256.0
private const val MAP_HEIGHT_DP = 130
private const val MIN_ZOOM = 13
private const val MAX_ZOOM = 18

/**
 * A small street map with your position and the place, so you can see how close you are. Tiles come
 * from OpenStreetMap at runtime; the attribution line is required by the OpenStreetMap licence.
 */
@Composable
fun MiniMap(from: LatLon, to: LatLon, modifier: Modifier = Modifier) {
    val distance = distanceMeters(from, to)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tileFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(if (dark) 0.25f else 0.35f) })
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT_DP.dp)
            .semantics {
                contentDescription = "Map showing your position and the place, ${formatDistance(distance)} apart"
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val widthPx = maxWidth.value.toDouble()
        val heightPx = maxHeight.value.toDouble()
        val zoom = zoomFor(distance, from.lat, widthPx)

        val userX = worldX(from.lon, zoom)
        val userY = worldY(from.lat, zoom)
        val placeX = worldX(to.lon, zoom)
        val placeY = worldY(to.lat, zoom)
        // Centre the view between the two points so both stay on screen.
        val originX = (userX + placeX) / 2 - widthPx / 2
        val originY = (userY + placeY) / 2 - heightPx / 2

        val tileCount = 1 shl zoom
        val firstX = floor(originX / TILE_PX).toInt()
        val lastX = floor((originX + widthPx) / TILE_PX).toInt()
        val firstY = floor(originY / TILE_PX).toInt()
        val lastY = floor((originY + heightPx) / TILE_PX).toInt()
        // The tiles sit in their own full-size layer, clipped to the map, so nothing outside the card shows.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            for (ty in firstY..lastY) {
                if (ty !in 0 until tileCount) continue
                for (tx in firstX..lastX) {
                    val wrappedX = ((tx % tileCount) + tileCount) % tileCount
                    SubcomposeAsyncImage(
                        model = "https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png",
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        colorFilter = tileFilter,
                        loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
                        error = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
                        modifier = Modifier
                            .offset(x = (tx * TILE_PX - originX).dpF(), y = (ty * TILE_PX - originY).dpF())
                            // requiredSize, not size: the map is only 130 dp tall, and size() would squash each tile to it.
                            .requiredSize(TILE_PX.toInt().dp),
                    )
                }
            }
        }

        // Warm the streets towards the design's ground colour; dim them in dark mode.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.6f else 0.3f)),
        )
        MapDot(x = userX - originX, y = userY - originY, color = MaterialTheme.colorScheme.primary)
        MapDot(x = placeX - originX, y = placeY - originY, color = MaterialTheme.colorScheme.onSurface)

        Text(
            formatDistance(distance) + " away",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MapDot(x: Double, y: Double, color: Color) {
    Box(
        modifier = Modifier
            .offset(x = x.dpF() - 8.dp, y = y.dpF() - 8.dp)
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.White, CircleShape),
    )
}

/** Zoom level at which the view spans about 2.6 times the distance, clamped to the tiles OSM serves well. */
private fun zoomFor(distance: Double, lat: Double, widthPx: Double): Int {
    val spanMetres = max(distance * 2.6, 150.0)
    val metresPerPixelWanted = spanMetres / widthPx
    val metresPerPixelAtZoomZero = 156543.03392 * cos(lat * PI / 180)
    return floor(log2(metresPerPixelAtZoomZero / metresPerPixelWanted)).toInt().coerceIn(MIN_ZOOM, MAX_ZOOM)
}

/** Slippy-map world pixel x for a longitude at [zoom]. */
private fun worldX(lon: Double, zoom: Int): Double = (lon + 180) / 360 * TILE_PX * 2.0.pow(zoom)

/** Slippy-map world pixel y for a latitude at [zoom]. */
private fun worldY(lat: Double, zoom: Int): Double {
    val rad = lat * PI / 180
    return (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2 * TILE_PX * 2.0.pow(zoom)
}

private fun Double.dpF() = this.toFloat().dp
