package com.nomnomsom.armstrongandgetty.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Material icons used by the app, vendored as ImageVectors.
 *
 * Path data extracted verbatim from the material-icons core/extended 1.7.3 sources
 * (Apache 2.0) because JetBrains never published iOS targets for the icons artifacts.
 */
object AppIcons {

    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(19.0f, 4.0f)
                horizontalLineToRelative(-3.5f)
                lineToRelative(-1.0f, -1.0f)
                horizontalLineToRelative(-5.0f)
                lineToRelative(-1.0f, 1.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(4.0f)
                close()
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5.0f, 20.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(19.0f, 9.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineTo(5.0f)
                lineToRelative(7.0f, 7.0f)
                lineTo(19.0f, 9.0f)
                close()
            }
        }.build()
    }

    val ErrorOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "ErrorOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.0f, 15.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
        }.build()
    }

    val Forward10: ImageVector by lazy {
        ImageVector.Builder(
            name = "Forward10", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18.0f, 13.0f)
                curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                verticalLineToRelative(4.0f)
                lineToRelative(5.0f, -5.0f)
                lineToRelative(-5.0f, -5.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, 3.58f, -8.0f, 8.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                horizontalLineTo(18.0f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(10.86f, 15.94f)
                lineToRelative(0.0f, -4.27f)
                lineToRelative(-0.09f, 0.0f)
                lineToRelative(-1.77f, 0.63f)
                lineToRelative(0.0f, 0.69f)
                lineToRelative(1.01f, -0.31f)
                lineToRelative(0.0f, 3.26f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12.25f, 13.44f)
                verticalLineToRelative(0.74f)
                curveToRelative(0.0f, 1.9f, 1.31f, 1.82f, 1.44f, 1.82f)
                curveToRelative(0.14f, 0.0f, 1.44f, 0.09f, 1.44f, -1.82f)
                verticalLineToRelative(-0.74f)
                curveToRelative(0.0f, -1.9f, -1.31f, -1.82f, -1.44f, -1.82f)
                curveTo(13.55f, 11.62f, 12.25f, 11.53f, 12.25f, 13.44f)
                close()
                moveTo(14.29f, 13.32f)
                verticalLineToRelative(0.97f)
                curveToRelative(0.0f, 0.77f, -0.21f, 1.03f, -0.59f, 1.03f)
                curveToRelative(-0.38f, 0.0f, -0.6f, -0.26f, -0.6f, -1.03f)
                verticalLineToRelative(-0.97f)
                curveToRelative(0.0f, -0.75f, 0.22f, -1.01f, 0.59f, -1.01f)
                curveTo(14.07f, 12.3f, 14.29f, 12.57f, 14.29f, 13.32f)
                close()
            }
        }.build()
    }

    val Forward30: ImageVector by lazy {
        ImageVector.Builder(
            name = "Forward30", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18.0f, 13.0f)
                curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                verticalLineToRelative(4.0f)
                lineToRelative(5.0f, -5.0f)
                lineToRelative(-5.0f, -5.0f)
                verticalLineToRelative(4.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, 3.58f, -8.0f, 8.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                horizontalLineTo(18.0f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(10.06f, 15.38f)
                curveToRelative(-0.29f, 0.0f, -0.62f, -0.17f, -0.62f, -0.54f)
                horizontalLineTo(8.59f)
                curveToRelative(0.0f, 0.97f, 0.9f, 1.23f, 1.45f, 1.23f)
                curveToRelative(0.87f, 0.0f, 1.51f, -0.46f, 1.51f, -1.25f)
                curveToRelative(0.0f, -0.66f, -0.45f, -0.9f, -0.71f, -1.0f)
                curveToRelative(0.11f, -0.05f, 0.65f, -0.32f, 0.65f, -0.92f)
                curveToRelative(0.0f, -0.21f, -0.05f, -1.22f, -1.44f, -1.22f)
                curveToRelative(-0.62f, 0.0f, -1.4f, 0.35f, -1.4f, 1.16f)
                horizontalLineToRelative(0.85f)
                curveToRelative(0.0f, -0.34f, 0.31f, -0.48f, 0.57f, -0.48f)
                curveToRelative(0.59f, 0.0f, 0.58f, 0.5f, 0.58f, 0.54f)
                curveToRelative(0.0f, 0.52f, -0.41f, 0.59f, -0.63f, 0.59f)
                horizontalLineTo(9.56f)
                verticalLineToRelative(0.66f)
                horizontalLineToRelative(0.45f)
                curveToRelative(0.65f, 0.0f, 0.7f, 0.42f, 0.7f, 0.64f)
                curveTo(10.71f, 15.11f, 10.5f, 15.38f, 10.06f, 15.38f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(13.85f, 11.68f)
                curveToRelative(-0.14f, 0.0f, -1.44f, -0.08f, -1.44f, 1.82f)
                verticalLineToRelative(0.74f)
                curveToRelative(0.0f, 1.9f, 1.31f, 1.82f, 1.44f, 1.82f)
                curveToRelative(0.14f, 0.0f, 1.44f, 0.09f, 1.44f, -1.82f)
                verticalLineTo(13.5f)
                curveTo(15.3f, 11.59f, 13.99f, 11.68f, 13.85f, 11.68f)
                close()
                moveTo(14.45f, 14.35f)
                curveToRelative(0.0f, 0.77f, -0.21f, 1.03f, -0.59f, 1.03f)
                curveToRelative(-0.38f, 0.0f, -0.6f, -0.26f, -0.6f, -1.03f)
                verticalLineToRelative(-0.97f)
                curveToRelative(0.0f, -0.75f, 0.22f, -1.01f, 0.59f, -1.01f)
                curveToRelative(0.38f, 0.0f, 0.6f, 0.26f, 0.6f, 1.01f)
                verticalLineTo(14.35f)
                close()
            }
        }.build()
    }

    val GraphicEq: ImageVector by lazy {
        ImageVector.Builder(
            name = "GraphicEq", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7.0f, 18.0f)
                horizontalLineToRelative(2.0f)
                lineTo(9.0f, 6.0f)
                lineTo(7.0f, 6.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(11.0f, 22.0f)
                horizontalLineToRelative(2.0f)
                lineTo(13.0f, 2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(20.0f)
                close()
                moveTo(3.0f, 14.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-4.0f)
                lineTo(3.0f, 10.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(15.0f, 18.0f)
                horizontalLineToRelative(2.0f)
                lineTo(17.0f, 6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(19.0f, 10.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.0f, 19.0f)
                horizontalLineToRelative(4.0f)
                lineTo(10.0f, 5.0f)
                lineTo(6.0f, 5.0f)
                verticalLineToRelative(14.0f)
                close()
                moveTo(14.0f, 5.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(4.0f)
                lineTo(18.0f, 5.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
        }.build()
    }

    val PlayArrow: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayArrow", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(8.0f, 5.0f)
                verticalLineToRelative(14.0f)
                lineToRelative(11.0f, -7.0f)
                close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
                curveToRelative(-4.42f, 0.0f, -7.99f, 3.58f, -7.99f, 8.0f)
                reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
                curveToRelative(3.73f, 0.0f, 6.84f, -2.55f, 7.73f, -6.0f)
                horizontalLineToRelative(-2.08f)
                curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
                curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                curveToRelative(1.66f, 0.0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineTo(13.0f, 11.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(4.0f)
                lineToRelative(-2.35f, 2.35f)
                close()
            }
        }.build()
    }

    val Replay10: ImageVector by lazy {
        ImageVector.Builder(
            name = "Replay10", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11.99f, 5.0f)
                verticalLineTo(1.0f)
                lineToRelative(-5.0f, 5.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineTo(7.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                horizontalLineToRelative(-2.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                reflectiveCurveTo(16.41f, 5.0f, 11.99f, 5.0f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(10.89f, 16.0f)
                horizontalLineToRelative(-0.85f)
                verticalLineToRelative(-3.26f)
                lineToRelative(-1.01f, 0.31f)
                verticalLineToRelative(-0.69f)
                lineToRelative(1.77f, -0.63f)
                horizontalLineToRelative(0.09f)
                verticalLineTo(16.0f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(15.17f, 14.24f)
                curveToRelative(0.0f, 0.32f, -0.03f, 0.6f, -0.1f, 0.82f)
                reflectiveCurveToRelative(-0.17f, 0.42f, -0.29f, 0.57f)
                reflectiveCurveToRelative(-0.28f, 0.26f, -0.45f, 0.33f)
                reflectiveCurveToRelative(-0.37f, 0.1f, -0.59f, 0.1f)
                reflectiveCurveToRelative(-0.41f, -0.03f, -0.59f, -0.1f)
                reflectiveCurveToRelative(-0.33f, -0.18f, -0.46f, -0.33f)
                reflectiveCurveToRelative(-0.23f, -0.34f, -0.3f, -0.57f)
                reflectiveCurveToRelative(-0.11f, -0.5f, -0.11f, -0.82f)
                verticalLineTo(13.5f)
                curveToRelative(0.0f, -0.32f, 0.03f, -0.6f, 0.1f, -0.82f)
                reflectiveCurveToRelative(0.17f, -0.42f, 0.29f, -0.57f)
                reflectiveCurveToRelative(0.28f, -0.26f, 0.45f, -0.33f)
                reflectiveCurveToRelative(0.37f, -0.1f, 0.59f, -0.1f)
                reflectiveCurveToRelative(0.41f, 0.03f, 0.59f, 0.1f)
                curveToRelative(0.18f, 0.07f, 0.33f, 0.18f, 0.46f, 0.33f)
                reflectiveCurveToRelative(0.23f, 0.34f, 0.3f, 0.57f)
                reflectiveCurveToRelative(0.11f, 0.5f, 0.11f, 0.82f)
                verticalLineTo(14.24f)
                close()
                moveTo(14.32f, 13.38f)
                curveToRelative(0.0f, -0.19f, -0.01f, -0.35f, -0.04f, -0.48f)
                reflectiveCurveToRelative(-0.07f, -0.23f, -0.12f, -0.31f)
                reflectiveCurveToRelative(-0.11f, -0.14f, -0.19f, -0.17f)
                reflectiveCurveToRelative(-0.16f, -0.05f, -0.25f, -0.05f)
                reflectiveCurveToRelative(-0.18f, 0.02f, -0.25f, 0.05f)
                reflectiveCurveToRelative(-0.14f, 0.09f, -0.19f, 0.17f)
                reflectiveCurveToRelative(-0.09f, 0.18f, -0.12f, 0.31f)
                reflectiveCurveToRelative(-0.04f, 0.29f, -0.04f, 0.48f)
                verticalLineToRelative(0.97f)
                curveToRelative(0.0f, 0.19f, 0.01f, 0.35f, 0.04f, 0.48f)
                reflectiveCurveToRelative(0.07f, 0.24f, 0.12f, 0.32f)
                reflectiveCurveToRelative(0.11f, 0.14f, 0.19f, 0.17f)
                reflectiveCurveToRelative(0.16f, 0.05f, 0.25f, 0.05f)
                reflectiveCurveToRelative(0.18f, -0.02f, 0.25f, -0.05f)
                reflectiveCurveToRelative(0.14f, -0.09f, 0.19f, -0.17f)
                reflectiveCurveToRelative(0.09f, -0.19f, 0.11f, -0.32f)
                reflectiveCurveToRelative(0.04f, -0.29f, 0.04f, -0.48f)
                verticalLineTo(13.38f)
                close()
            }
        }.build()
    }

    val Replay30: ImageVector by lazy {
        ImageVector.Builder(
            name = "Replay30", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12.0f, 5.0f)
                verticalLineTo(1.0f)
                lineTo(7.0f, 6.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineTo(7.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                reflectiveCurveToRelative(-2.69f, 6.0f, -6.0f, 6.0f)
                reflectiveCurveToRelative(-6.0f, -2.69f, -6.0f, -6.0f)
                horizontalLineTo(4.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                reflectiveCurveTo(16.42f, 5.0f, 12.0f, 5.0f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(9.56f, 13.49f)
                horizontalLineToRelative(0.45f)
                curveToRelative(0.21f, 0.0f, 0.37f, -0.05f, 0.48f, -0.16f)
                reflectiveCurveToRelative(0.16f, -0.25f, 0.16f, -0.43f)
                curveToRelative(0.0f, -0.08f, -0.01f, -0.15f, -0.04f, -0.22f)
                reflectiveCurveToRelative(-0.06f, -0.12f, -0.11f, -0.17f)
                reflectiveCurveToRelative(-0.11f, -0.09f, -0.18f, -0.11f)
                reflectiveCurveToRelative(-0.16f, -0.04f, -0.25f, -0.04f)
                curveToRelative(-0.08f, 0.0f, -0.15f, 0.01f, -0.22f, 0.03f)
                reflectiveCurveToRelative(-0.13f, 0.05f, -0.18f, 0.1f)
                reflectiveCurveToRelative(-0.09f, 0.09f, -0.12f, 0.15f)
                reflectiveCurveToRelative(-0.05f, 0.13f, -0.05f, 0.2f)
                horizontalLineTo(8.65f)
                curveToRelative(0.0f, -0.18f, 0.04f, -0.34f, 0.11f, -0.48f)
                reflectiveCurveToRelative(0.17f, -0.27f, 0.3f, -0.37f)
                reflectiveCurveToRelative(0.27f, -0.18f, 0.44f, -0.23f)
                reflectiveCurveToRelative(0.35f, -0.08f, 0.54f, -0.08f)
                curveToRelative(0.21f, 0.0f, 0.41f, 0.03f, 0.59f, 0.08f)
                reflectiveCurveToRelative(0.33f, 0.13f, 0.46f, 0.23f)
                reflectiveCurveToRelative(0.23f, 0.23f, 0.3f, 0.38f)
                reflectiveCurveToRelative(0.11f, 0.33f, 0.11f, 0.53f)
                curveToRelative(0.0f, 0.09f, -0.01f, 0.18f, -0.04f, 0.27f)
                reflectiveCurveToRelative(-0.07f, 0.17f, -0.13f, 0.25f)
                reflectiveCurveToRelative(-0.12f, 0.15f, -0.2f, 0.22f)
                reflectiveCurveToRelative(-0.17f, 0.12f, -0.28f, 0.17f)
                curveToRelative(0.24f, 0.09f, 0.42f, 0.21f, 0.54f, 0.39f)
                reflectiveCurveToRelative(0.18f, 0.38f, 0.18f, 0.61f)
                curveToRelative(0.0f, 0.2f, -0.04f, 0.38f, -0.12f, 0.53f)
                reflectiveCurveToRelative(-0.18f, 0.29f, -0.32f, 0.39f)
                reflectiveCurveToRelative(-0.29f, 0.19f, -0.48f, 0.24f)
                reflectiveCurveToRelative(-0.38f, 0.08f, -0.6f, 0.08f)
                curveToRelative(-0.18f, 0.0f, -0.36f, -0.02f, -0.53f, -0.07f)
                reflectiveCurveToRelative(-0.33f, -0.12f, -0.46f, -0.23f)
                reflectiveCurveToRelative(-0.25f, -0.23f, -0.33f, -0.38f)
                reflectiveCurveToRelative(-0.12f, -0.34f, -0.12f, -0.55f)
                horizontalLineToRelative(0.85f)
                curveToRelative(0.0f, 0.08f, 0.02f, 0.15f, 0.05f, 0.22f)
                reflectiveCurveToRelative(0.07f, 0.12f, 0.13f, 0.17f)
                reflectiveCurveToRelative(0.12f, 0.09f, 0.2f, 0.11f)
                reflectiveCurveToRelative(0.16f, 0.04f, 0.25f, 0.04f)
                curveToRelative(0.1f, 0.0f, 0.19f, -0.01f, 0.27f, -0.04f)
                reflectiveCurveToRelative(0.15f, -0.07f, 0.2f, -0.12f)
                reflectiveCurveToRelative(0.1f, -0.11f, 0.13f, -0.18f)
                reflectiveCurveToRelative(0.04f, -0.15f, 0.04f, -0.24f)
                curveToRelative(0.0f, -0.11f, -0.02f, -0.21f, -0.05f, -0.29f)
                reflectiveCurveToRelative(-0.08f, -0.15f, -0.14f, -0.2f)
                reflectiveCurveToRelative(-0.13f, -0.09f, -0.22f, -0.11f)
                reflectiveCurveToRelative(-0.18f, -0.04f, -0.29f, -0.04f)
                horizontalLineTo(9.56f)
                verticalLineTo(13.49f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(15.3f, 14.24f)
                curveToRelative(0.0f, 0.32f, -0.03f, 0.6f, -0.1f, 0.82f)
                reflectiveCurveToRelative(-0.17f, 0.42f, -0.29f, 0.57f)
                reflectiveCurveToRelative(-0.28f, 0.26f, -0.45f, 0.33f)
                reflectiveCurveToRelative(-0.37f, 0.1f, -0.59f, 0.1f)
                reflectiveCurveToRelative(-0.41f, -0.03f, -0.59f, -0.1f)
                reflectiveCurveToRelative(-0.33f, -0.18f, -0.46f, -0.33f)
                reflectiveCurveToRelative(-0.23f, -0.34f, -0.3f, -0.57f)
                reflectiveCurveToRelative(-0.11f, -0.5f, -0.11f, -0.82f)
                verticalLineTo(13.5f)
                curveToRelative(0.0f, -0.32f, 0.03f, -0.6f, 0.1f, -0.82f)
                reflectiveCurveToRelative(0.17f, -0.42f, 0.29f, -0.57f)
                reflectiveCurveToRelative(0.28f, -0.26f, 0.45f, -0.33f)
                reflectiveCurveToRelative(0.37f, -0.1f, 0.59f, -0.1f)
                reflectiveCurveToRelative(0.41f, 0.03f, 0.59f, 0.1f)
                reflectiveCurveToRelative(0.33f, 0.18f, 0.46f, 0.33f)
                reflectiveCurveToRelative(0.23f, 0.34f, 0.3f, 0.57f)
                reflectiveCurveToRelative(0.11f, 0.5f, 0.11f, 0.82f)
                verticalLineTo(14.24f)
                close()
                moveTo(14.45f, 13.38f)
                curveToRelative(0.0f, -0.19f, -0.01f, -0.35f, -0.04f, -0.48f)
                curveToRelative(-0.03f, -0.13f, -0.07f, -0.23f, -0.12f, -0.31f)
                reflectiveCurveToRelative(-0.11f, -0.14f, -0.19f, -0.17f)
                reflectiveCurveToRelative(-0.16f, -0.05f, -0.25f, -0.05f)
                reflectiveCurveToRelative(-0.18f, 0.02f, -0.25f, 0.05f)
                reflectiveCurveToRelative(-0.14f, 0.09f, -0.19f, 0.17f)
                reflectiveCurveToRelative(-0.09f, 0.18f, -0.12f, 0.31f)
                reflectiveCurveToRelative(-0.04f, 0.29f, -0.04f, 0.48f)
                verticalLineToRelative(0.97f)
                curveToRelative(0.0f, 0.19f, 0.01f, 0.35f, 0.04f, 0.48f)
                reflectiveCurveToRelative(0.07f, 0.24f, 0.12f, 0.32f)
                reflectiveCurveToRelative(0.11f, 0.14f, 0.19f, 0.17f)
                reflectiveCurveToRelative(0.16f, 0.05f, 0.25f, 0.05f)
                reflectiveCurveToRelative(0.18f, -0.02f, 0.25f, -0.05f)
                reflectiveCurveToRelative(0.14f, -0.09f, 0.19f, -0.17f)
                reflectiveCurveToRelative(0.09f, -0.19f, 0.11f, -0.32f)
                curveToRelative(0.03f, -0.13f, 0.04f, -0.29f, 0.04f, -0.48f)
                verticalLineTo(13.38f)
                close()
            }
        }.build()
    }

    val RestartAlt: ImageVector by lazy {
        ImageVector.Builder(
            name = "RestartAlt", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12.0f, 5.0f)
                verticalLineTo(2.0f)
                lineTo(8.0f, 6.0f)
                lineToRelative(4.0f, 4.0f)
                verticalLineTo(7.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                curveToRelative(0.0f, 2.97f, -2.17f, 5.43f, -5.0f, 5.91f)
                verticalLineToRelative(2.02f)
                curveToRelative(3.95f, -0.49f, 7.0f, -3.85f, 7.0f, -7.93f)
                curveTo(20.0f, 8.58f, 16.42f, 5.0f, 12.0f, 5.0f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.0f, 13.0f)
                curveToRelative(0.0f, -1.65f, 0.67f, -3.15f, 1.76f, -4.24f)
                lineTo(6.34f, 7.34f)
                curveTo(4.9f, 8.79f, 4.0f, 10.79f, 4.0f, 13.0f)
                curveToRelative(0.0f, 4.08f, 3.05f, 7.44f, 7.0f, 7.93f)
                verticalLineToRelative(-2.02f)
                curveTo(8.17f, 18.43f, 6.0f, 15.97f, 6.0f, 13.0f)
                close()
            }
        }.build()
    }

    val Stop: ImageVector by lazy {
        ImageVector.Builder(
            name = "Stop", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.0f, 6.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(12.0f)
                horizontalLineTo(6.0f)
                close()
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(19.0f, 6.41f)
                lineTo(17.59f, 5.0f)
                lineTo(12.0f, 10.59f)
                lineTo(6.41f, 5.0f)
                lineTo(5.0f, 6.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.0f, 17.59f)
                lineTo(6.41f, 19.0f)
                lineTo(12.0f, 13.41f)
                lineTo(17.59f, 19.0f)
                lineTo(19.0f, 17.59f)
                lineTo(13.41f, 12.0f)
                close()
            }
        }.build()
    }

    val ExpandLess: ImageVector by lazy {
        ImageVector.Builder(
            name = "ExpandLess", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12.0f, 8.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(1.41f, 1.41f)
                lineTo(12.0f, 10.83f)
                lineToRelative(4.59f, 4.58f)
                lineTo(18.0f, 14.0f)
                close()
            }
        }.build()
    }

    val ExpandMore: ImageVector by lazy {
        ImageVector.Builder(
            name = "ExpandMore", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(16.59f, 8.59f)
                lineTo(12.0f, 13.17f)
                lineTo(7.41f, 8.59f)
                lineTo(6.0f, 10.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(6.0f, -6.0f)
                close()
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = true
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(20.0f, 11.0f)
                horizontalLineTo(7.83f)
                lineToRelative(5.59f, -5.59f)
                lineTo(12.0f, 4.0f)
                lineToRelative(-8.0f, 8.0f)
                lineToRelative(8.0f, 8.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(7.83f, 13.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(-2.0f)
                close()
            }
        }.build()
    }

    val Airplay: ImageVector by lazy {
        ImageVector.Builder(
            name = "Airplay", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(6.0f, 22.0f)
                lineToRelative(12.0f, 0.0f)
                lineToRelative(-6.0f, -6.0f)
                close()
            }
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(21.0f, 3.0f)
                horizontalLineTo(3.0f)
                curveTo(1.9f, 3.0f, 1.0f, 3.9f, 1.0f, 5.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(12.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveTo(23.0f, 3.9f, 22.1f, 3.0f, 21.0f, 3.0f)
                close()
            }
        }.build()
    }
    val Cast: ImageVector by lazy {
        ImageVector.Builder(
            name = "Cast", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                lineTo(3.0f, 5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(1.0f, 18.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(1.0f, 14.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -3.87f, -3.13f, -7.0f, -7.0f, -7.0f)
                close()
                moveTo(1.0f, 10.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, 4.03f, 9.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -6.08f, -4.93f, -11.0f, -11.0f, -11.0f)
                close()
            }
        }.build()
    }
    val CastConnected: ImageVector by lazy {
        ImageVector.Builder(
            name = "CastConnected", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = false
        ).apply {
            path(
                fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null,
                strokeAlpha = 1f, strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(1.0f, 18.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(3.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(1.0f, 14.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -3.87f, -3.13f, -7.0f, -7.0f, -7.0f)
                close()
                moveTo(19.0f, 7.0f)
                lineTo(5.0f, 7.0f)
                verticalLineToRelative(1.63f)
                curveToRelative(3.96f, 1.28f, 7.09f, 4.41f, 8.37f, 8.37f)
                lineTo(19.0f, 17.0f)
                lineTo(19.0f, 7.0f)
                close()
                moveTo(1.0f, 10.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, 4.03f, 9.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -6.08f, -4.93f, -11.0f, -11.0f, -11.0f)
                close()
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                lineTo(3.0f, 5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(14.0f)
                horizontalLineToRelative(-7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(7.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }.build()
    }
}
