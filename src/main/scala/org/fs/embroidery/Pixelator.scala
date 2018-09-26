package org.fs.embroidery

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

import scala.collection.mutable

import org.fs.embroidery.ColorUtils._
import org.fs.embroidery.classify.ColorsSupport

object Pixelator {

  private type Count = Int

  def pixelate(
      image: BufferedImage,
      pixelSize: Int,
      mode: Mode
  ): (BufferedImage, Map[(Int, Int), Color]) = {
    val pixelateImage = new BufferedImage(image.getWidth, image.getHeight, image.getType)
    val colorMapSeq = for {
      x <- (0 until image.getWidth by pixelSize).par
      y <- (0 until image.getHeight by pixelSize).par
    } yield {
      val croppedImage = getCroppedSubImage(image, x, y, pixelSize, pixelSize)
      val dominantColor = mode match {
        case Mode.Dominant => getDominantColor(croppedImage)
        case Mode.Average  => getAverageColor(croppedImage)
      }

      for {
        xd <- x until math.min(x + pixelSize, pixelateImage.getWidth)
        yd <- y until math.min(y + pixelSize, pixelateImage.getHeight)
      } pixelateImage.setRGB(xd, yd, dominantColor.getRGB)
      ((x, y) -> dominantColor)
    }
    (pixelateImage, colorMapSeq.seq.toMap)
  }

  private def getCroppedSubImage(
      image: BufferedImage,
      x: Int,
      y: Int,
      w: Int,
      h: Int
  ): BufferedImage = {
    val x2 = coerceToInclusiveRange(x, 0 to image.getWidth)
    val y2 = coerceToInclusiveRange(y, 0 to image.getHeight)
    val w2 = coerceToInclusiveRange(x2 + w, 0 to image.getWidth) - x2
    val h2 = coerceToInclusiveRange(y2 + h, 0 to image.getHeight) - y2
    image.getSubimage(x2, y2, w2, h2)
  }

  private def coerceToInclusiveRange(v: Int, range: Range): Int =
    if (v < range.start) range.start else if (v > range.end) range.end else v

  private def getDominantColor(image: BufferedImage): Color = {
    // Based on https://stackoverflow.com/a/42327288/466646
    val colorCounter = mutable.Map.empty[RGB, Count]
    for {
      x <- 0 until image.getWidth
      y <- 0 until image.getHeight
    } {
      val currentRGB = image.getRGB(x, y)
      colorCounter.put(currentRGB, colorCounter.getOrElse(currentRGB, 0) + 1)
    }
    val maxCount       = colorCounter.values.max
    val dominantColors = colorCounter.filter(_._2 == maxCount).keys.toList
    new Color(chooseStrongestColor(dominantColors))
  }

  private def getAverageColor(image: BufferedImage): Color = {
    val colors = for {
      x <- 0 until image.getWidth
      y <- 0 until image.getHeight
    } yield new Color(image.getRGB(x, y))
    val avg = ColorsSupport.getAverage(colors)
    avg
  }

  private def chooseStrongestColor(colors: List[RGB]): RGB = colors match {
    case rgb :: Nil => rgb
    case rgbs       => rgbs maxBy rgbToComparable
  }

  private def rgbToComparable(rgb: RGB): Int = {
    val bytes               = ByteBuffer.allocate(4).putInt(rgb).array()
    val (alpha, components) = (ubyte2int(bytes.head), bytes.tail)
    val componentsSum       = components.map(ubyte2int).sum
    alpha * componentsSum
  }

  private def ubyte2int(b: Byte) =
    java.lang.Byte.toUnsignedInt(b)

  sealed trait Mode
  object Mode {
    case object Dominant extends Mode
    case object Average  extends Mode
  }
}
