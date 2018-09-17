package org.fs.embroidery

import java.awt.Color
import java.awt.image.BufferedImage

import scala.collection.mutable

// Taken from https://stackoverflow.com/a/42327288/466646
object Pixelator {

  def pixelate(image: BufferedImage, pixelSize: Int): BufferedImage = {
    val pixelateImage = new BufferedImage(image.getWidth, image.getHeight, image.getType)
    for {
      x <- 0 until image.getWidth by pixelSize
      y <- 0 until image.getHeight by pixelSize
    } {
      val croppedImage  = getCroppedSubImage(image, x, y, pixelSize, pixelSize)
      val dominantColor = getDominantColor(croppedImage)

      for {
        xd <- x until math.min(x + pixelSize, pixelateImage.getWidth)
        yd <- y until math.min(y + pixelSize, pixelateImage.getHeight)
      } pixelateImage.setRGB(xd, yd, dominantColor.getRGB)
    }
    pixelateImage
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
    val colorCounter = mutable.Map.empty[Int, Int]
    for (x <- 0 until image.getWidth) {
      for (y <- 0 until image.getHeight) {
        val currentRGB = image.getRGB(x, y)
        colorCounter.put(currentRGB, colorCounter.getOrElse(currentRGB, 0) + 1)
      }
    }
    new Color(colorCounter.maxBy(_._2)._1)
  }
}
