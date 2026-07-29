package com.nomnomsom.armstrongandgetty.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons for the bottom nav + About tab, vendored like AppIcons.kt but from raw
 * SVG path strings (material-icons core/extended 1.7.3 sources, Apache 2.0;
 * X/Instagram glyphs from simple-icons, CC0).
 */

private fun vectorIcon(
    name: String,
    pathData: String,
    fillType: PathFillType = PathFillType.NonZero
): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
            pathFillType = fillType
        )
    }.build()

private val infoIcon by lazy {
    vectorIcon(
        "Info",
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6z" +
            "m0-8h-2V7h2v2z"
    )
}
val AppIcons.Info: ImageVector get() = infoIcon

private val headsetIcon by lazy {
    vectorIcon(
        "Headset",
        "M12 1c-4.97 0-9 4.03-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 " +
            "7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7c0-4.97-4.03-9-9-9z"
    )
}
val AppIcons.Headset: ImageVector get() = headsetIcon

private val publicIcon by lazy {
    vectorIcon(
        "Public",
        "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2z" +
            "m6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56z" +
            "M12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96z" +
            "M4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 " +
            "1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56z" +
            "m2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8z" +
            "M12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96z" +
            "M14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 " +
            "0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 " +
            "2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 " +
            "1.31.26 2s-.1 1.36-.26 2h-3.38z"
    )
}
val AppIcons.Public: ImageVector get() = publicIcon

private val shoppingBagIcon by lazy {
    vectorIcon(
        "ShoppingBag",
        "M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 " +
            "2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm6 16H6V8h2v2c0 " +
            ".55.45 1 1 1s1-.45 1-1V8h4v2c0 .55.45 1 1 1s1-.45 1-1V8h2v12z"
    )
}
val AppIcons.ShoppingBag: ImageVector get() = shoppingBagIcon

private val mailIcon by lazy {
    vectorIcon(
        "Mail",
        "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z" +
            "m0 4l-8 5-8-5V6l8 5 8-5v2z"
    )
}
val AppIcons.Mail: ImageVector get() = mailIcon

private val phoneIcon by lazy {
    vectorIcon(
        "Phone",
        "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 " +
            "2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 " +
            "1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"
    )
}
val AppIcons.Phone: ImageVector get() = phoneIcon

private val openInNewIcon by lazy {
    vectorIcon(
        "OpenInNew",
        "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7z" +
            "M14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z"
    )
}
val AppIcons.OpenInNew: ImageVector get() = openInNewIcon

private val articleIcon by lazy {
    vectorIcon(
        "Article",
        "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z" +
            "m-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"
    )
}
val AppIcons.Article: ImageVector get() = articleIcon

private val facebookIcon by lazy {
    vectorIcon(
        "Facebook",
        "M22 12c0-5.52-4.48-10-10-10S2 6.48 2 12c0 4.84 3.44 8.87 8 9.8V15H8v-3h2V9.5C10 " +
            "7.57 11.57 6 13.5 6H16v3h-2c-.55 0-1 .45-1 1v2h3v3h-3v6.95c5.05-.5 9-4.76 9-9.95z"
    )
}
val AppIcons.Facebook: ImageVector get() = facebookIcon

private val xLogoIcon by lazy {
    vectorIcon(
        "XLogo",
        "M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-" +
            "8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"
    )
}
val AppIcons.XLogo: ImageVector get() = xLogoIcon

private val youTubeIcon by lazy {
    vectorIcon(
        "YouTube",
        "M21.58 7.19c-.23-.86-.91-1.54-1.77-1.77C18.25 5 12 5 12 5s-6.25 0-7.81.42c-.86.23-" +
            "1.54.91-1.77 1.77C2 8.75 2 12 2 12s0 3.25.42 4.81c.23.86.91 1.54 1.77 1.77C5.75 19 " +
            "12 19 12 19s6.25 0 7.81-.42c.86-.23 1.54-.91 1.77-1.77C22 15.25 22 12 22 12s0-3.25-" +
            ".42-4.81zM10 15V9l5.2 3-5.2 3z",
        fillType = PathFillType.EvenOdd
    )
}
val AppIcons.YouTube: ImageVector get() = youTubeIcon

private val instagramIcon by lazy {
    vectorIcon(
        "Instagram",
        "M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265." +
            "069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 " +
            "4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-" +
            "1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-" +
            "3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-" +
            "3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 " +
            "0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 " +
            "4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28." +
            "073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-" +
            "6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2." +
            "759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 " +
            "10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4z" +
            "m6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-" +
            ".645 1.439-1.44s-.644-1.44-1.439-1.44z"
    )
}
val AppIcons.Instagram: ImageVector get() = instagramIcon
