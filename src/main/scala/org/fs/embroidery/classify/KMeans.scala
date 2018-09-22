package org.fs.embroidery.classify

import scala.annotation.tailrec
import scala.util.Random

class KMeans[DT] private (
    val centroids: IndexedSeq[DT]
)(implicit support: KMeans.KMeansSupport[DT]) {

  lazy val k: Int = centroids.size

  def apply(i: Int): DT = centroids(i)

  /** Classify the given value, returning 0-based index of a cluster (mean) it belongs to */
  def classify(v: DT): Int =
    centroids.zipWithIndex.minBy {
      case (mean, _) => support.getDistance(mean, v)
    }._2

}

object KMeans {
  private val AttemptsNum = 50
  private val StepsNum    = 10

  /** Uses avg. squared distance */
  private def distortionCostFunction[DT](
      means: KMeans[DT],
      data: Seq[DT]
  )(implicit support: KMeansSupport[DT]): Double = {
    val m = data.size
    data.map { v =>
      val clusterIdx = means.classify(v)
      val mean       = means(clusterIdx)
      val distance   = support.getDistance(v, mean)
      math.pow(distance, 2)
    }.sum / m
  }

  def apply[DT: KMeansSupport](k: Int, data: Seq[DT]): KMeans[DT] = {
    val support = implicitly[KMeansSupport[DT]]

    val distinctData = data.distinct

    def initClusters(): KMeans[DT] = {
      val meanValues = Random.shuffle(distinctData).take(k).toIndexedSeq
      new KMeans(meanValues.sorted)
    }

    def step(means: KMeans[DT]): KMeans[DT] = {
      val clustered     = data groupBy (means.classify)
      val newMeanValues = clustered.values.filter(_.nonEmpty).map(support.getAverage).toIndexedSeq
      new KMeans(newMeanValues.sorted)
    }

    @tailrec def iterate(means: KMeans[DT], stepsLeft: Int): KMeans[DT] = {
      if (stepsLeft <= 0) means
      else {
        val newMeans = step(means)
        if (newMeans == means) means else iterate(newMeans, stepsLeft - 1)
      }
    }

    val candidatesWithObjectives = (1 to AttemptsNum).map { _ =>
      val initial   = initClusters()
      val result    = iterate(initial, StepsNum)
      val objective = distortionCostFunction(result, data)
      (result, objective)
    }
    val result = candidatesWithObjectives.minBy(_._2)._1

    result
  }

  trait KMeansSupport[DT] extends Ordering[DT] {
    def getDistance(v1: DT, v2: DT): Double
    def getAverage(vs: Seq[DT]): DT
  }
}
