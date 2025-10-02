package com.magicitengineer.digitaltarotandroidapp.camera

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class GuideResult(
    val isGood: Boolean,
    val detectedCircle: Boolean,
    val message: String?
)

object OpenCvProcessor {

    // Thresholds
    private const val SIZE_TOLERANCE = 0.15 // ±15%
    private const val MIN_EDGE = 200.0 // minimum edge length in px to consider

    fun detectAndGuide(w: Int, h: Int, frame: Bitmap): GuideResult {
        val mat = Mat()
        Utils.bitmapToMat(frame, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(mat, mat, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(mat, mat, 50.0, 150.0)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(mat, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var bestQuad: MatOfPoint2f? = null
        var bestArea = 0.0
        for (c in contours) {
            val curve = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(curve, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(curve, approx, 0.02 * peri, true)
            if (approx.total().toInt() == 4) {
                val pts = approx.toArray()
                val area = abs(Imgproc.contourArea(MatOfPoint(*pts.map { Point(it.x, it.y) }.toTypedArray())))
                if (area > bestArea) {
                    bestArea = area
                    bestQuad = approx
                }
            }
        }

        if (bestQuad != null) {
            val ordered = orderCorners(bestQuad!!.toArray())
            val short = edgeLen(ordered[0], ordered[1]).coerceAtMost(edgeLen(ordered[1], ordered[2]))
            val long = edgeLen(ordered[0], ordered[3]).coerceAtLeast(edgeLen(ordered[2], ordered[3]))
            val frameShort = min(w, h) * (1 - 2 * 0.08)
            val frameLong = max(w, h) * (1 - 2 * 0.08)

            val shortOk = isWithin(short.toDouble(), frameShort, SIZE_TOLERANCE)
            val longOk = isWithin(long.toDouble(), frameLong, SIZE_TOLERANCE)

            return if (shortOk && longOk) GuideResult(true, false, null)
            else if (short < frameShort) GuideResult(false, false, "カードが小さすぎます。枠に対して85%以上になるよう近づけてください。")
            else GuideResult(false, false, "カードが枠からはみ出しています。もう少し離れてください。")
        }

        // Try circle detection
        val gray = Mat()
        Utils.bitmapToMat(frame, gray)
        Imgproc.cvtColor(gray, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.medianBlur(gray, gray, 5)
        val circles = Mat()
        Imgproc.HoughCircles(
            gray, circles, Imgproc.HOUGH_GRADIENT, 1.0,
            gray.rows() / 8.0, 100.0, 30.0, 0, 0
        )
        if (circles.cols() > 0) {
            val r = circles.get(0, 0)[2]
            val targetR = (min(w, h) * (0.5 - 0.08)).toFloat()
            val ok = r in targetR * (1 - SIZE_TOLERANCE)..targetR * (1 + SIZE_TOLERANCE)
            return if (ok) GuideResult(true, true, null)
            else if (r < targetR) GuideResult(false, true, "カードが小さすぎます。枠に対して85%以上になるよう近づけてください。")
            else GuideResult(false, true, "カードが枠からはみ出しています。もう少し離れてください。")
        }

        return GuideResult(false, false, "ブレを検出。端末を固定してください。")
    }

    fun detectAndWarp(src: Bitmap, outW: Int = 720, outH: Int = 1024): Bitmap? {
        val rgba = Mat()
        Utils.bitmapToMat(src, rgba)

        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(gray, gray, 50.0, 150.0)

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(gray, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var bestQuad: MatOfPoint2f? = null
        var bestArea = 0.0
        for (c in contours) {
            val curve = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(curve, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(curve, approx, 0.02 * peri, true)
            if (approx.total().toInt() == 4) {
                val pts = approx.toArray()
                val area = abs(Imgproc.contourArea(MatOfPoint(*pts.map { Point(it.x, it.y) }.toTypedArray())))
                if (area > bestArea && minEdge(pts) > MIN_EDGE) {
                    bestArea = area
                    bestQuad = approx
                }
            }
        }

        if (bestQuad != null) {
            val ordered = orderCorners(bestQuad!!.toArray())
            val srcPts = MatOfPoint2f(
                ordered[0], ordered[1], ordered[2], ordered[3]
            )
            val dstPts = MatOfPoint2f(
                Point(0.0, 0.0), Point(outW - 1.0, 0.0),
                Point(outW - 1.0, outH - 1.0), Point(0.0, outH - 1.0)
            )
            val m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            val out = Mat(Size(outW.toDouble(), outH.toDouble()), rgba.type())
            Imgproc.warpPerspective(rgba, out, m, out.size())
            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(out, bmp)
            return bmp
        }

        // Circle fallback using bounding crop
        val circle = findLargestCircle(gray)
        if (circle != null) {
            val (cx, cy, r) = circle
            val x = (cx - r).toInt().coerceAtLeast(0)
            val y = (cy - r).toInt().coerceAtLeast(0)
            val size = (r * 2).toInt()
            val x2 = min(x + size, rgba.cols())
            val y2 = min(y + size, rgba.rows())
            val roi = rgba.submat(Rect(x, y, x2 - x, y2 - y))
            val square = Mat()
            Imgproc.resize(roi, square, Size(outW.toDouble(), outW.toDouble()))
            val bmp = Bitmap.createBitmap(outW, outW, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(square, bmp)
            return bmp
        }

        return null
    }

    private fun findLargestCircle(gray: Mat): Triple<Float, Float, Float>? {
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, gray.rows() / 8.0, 100.0, 30.0, 0, 0)
        var best: Triple<Float, Float, Float>? = null
        for (x in 0 until circles.cols()) {
            val v = circles.get(0, x) ?: continue
            val r = v[2].toFloat()
            if (best == null || r > best!!.third) {
                best = Triple(v[0].toFloat(), v[1].toFloat(), r)
            }
        }
        return best
    }

    private fun isWithin(value: Double, target: Double, tol: Double): Boolean {
        return value in target * (1 - tol)..target * (1 + tol)
    }

    private fun orderCorners(points: Array<Point>): Array<Point> {
        val sorted = points.sortedWith(compareBy<Point> { it.y }.thenBy { it.x })
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.takeLast(2).sortedByDescending { it.x }
        return arrayOf(top[0], top[1], bottom[0], bottom[1])
    }

    private fun edgeLen(a: Point, b: Point): Int {
        return kotlin.math.sqrt(((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))).toInt()
    }

    private fun minEdge(pts: Array<Point>): Double {
        val e1 = kotlin.math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
        val e2 = kotlin.math.hypot(pts[1].x - pts[2].x, pts[1].y - pts[2].y)
        val e3 = kotlin.math.hypot(pts[2].x - pts[3].x, pts[2].y - pts[3].y)
        val e4 = kotlin.math.hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y)
        return min(min(e1, e2), min(e3, e4))
    }
}
