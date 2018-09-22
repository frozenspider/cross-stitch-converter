package org.fs.embroidery.classify

import java.awt.Color

import org.fs.embroidery.classify.KMeans.KMeansSupport

object ColorsSupport extends KMeansSupport[Color] {
  private def squaredDiff(v1: Double, v2: Double): Double =
    math.pow(v1 - v2, 2)

  override def getSquaredDistance(c1: Color, c2: Color): Double = {
    val sd1 = squaredDiff(c1.getRed, c2.getRed)
    val sd2 = squaredDiff(c1.getGreen, c2.getGreen)
    val sd3 = squaredDiff(c1.getBlue, c2.getBlue)
    sd1 + sd2 + sd3
  }

  override def getAverage(cs: IndexedSeq[Color]): Color = {
    var (rAcc, gAcc, bAcc) = (0.0, 0.0, 0.0)
    for (c <- cs) {
      rAcc += c.getRed
      gAcc += c.getGreen
      bAcc += c.getBlue
    }
    val (r, g, b) = ((rAcc / cs.size).toInt, (gAcc / cs.size).toInt, (bAcc / cs.size).toInt)
    new Color(r, g, b)
  }

  override def compare(x: Color, y: Color): Int = x.getRGB compareTo y.getRGB
}
