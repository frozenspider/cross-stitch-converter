package org.fs.embroidery.classify

import java.awt.Color

import org.fs.embroidery.classify.KMeans.KMeansSupport

object ColorsSupport extends KMeansSupport[Color] {
  private def toSeq(c: Color) =
    Seq(c.getRed.toDouble, c.getGreen.toDouble, c.getBlue.toDouble)

  private def squaredDiff(v1: Double, v2: Double): Double =
    math.pow(v1 - v2, 2)

  override def getDistance(c1: Color, c2: Color): Double =
    math.sqrt( ((toSeq(c1) zip toSeq(c2)) map (squaredDiff _).tupled).sum )

  override def getAverage(cs: Seq[Color]): Color = {
    val total      = cs.size
    val components = cs.map(toSeq).transpose.map(_.sum / total).map(_.toInt)
    components foreach (c => assert(c >= 0 && c <= 255))
    val Seq(r, g, b) = components
    new Color(r, g, b)
  }

  override def compare(x: Color, y: Color): Int = x.getRGB compareTo y.getRGB
}
