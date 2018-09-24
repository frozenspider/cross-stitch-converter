package org.fs.embroidery.classify

import scala.annotation.tailrec
import scala.util.Random

/** Naïve implementation of k-means clustering algorithm */
class KMeans[DT] private (
    val centroids: IndexedSeq[DT]
)(implicit support: KMeans.KMeansSupport[DT]) {

  val k: Int = centroids.size

  def apply(i: Int): DT = centroids(i)

  /** Classify the given value, returning 0-based index of a cluster (mean) it belongs to */
  def classify(v: DT): Int = {
    // Optimized for performance
    var minDistance:    Double = Double.MaxValue
    var minCentroidIdx: Int    = -1
    for (idx <- 0 until k) {
      val centroid   = centroids(idx)
      val sqDistance = support.getSquaredDistance(centroid, v)
      if (sqDistance < minDistance) {
        minDistance = sqDistance
        minCentroidIdx = idx
      }
    }
    minCentroidIdx
  }

  /** Classify the given value, returning closest centroid */
  def classifyAndGet(v: DT): DT = {
    this(classify(v))
  }
}

object KMeans {
  private val AttemptsNum = 50
  private val StepsNum    = 15

  /** Uses avg. squared distance */
  private def distortionCostFunction[DT](
      means: KMeans[DT],
      data: Seq[DT]
  )(implicit support: KMeansSupport[DT]): Double = {
    val m = data.size
    data.map { v =>
      val mean = means.classifyAndGet(v)
      support.getSquaredDistance(v, mean)
    }.sum / m
  }

  def apply[DT: KMeansSupport](k: Int, data: IndexedSeq[DT], distinctOnly: Boolean): KMeans[DT] = {
    val support = implicitly[KMeansSupport[DT]]

    val distinctData = data.distinct
    val dataToUse    = if (distinctOnly) distinctData else data

    def initClusters(): KMeans[DT] = {
      val meanValues = Random.shuffle(distinctData).take(k)
      new KMeans(meanValues.sorted)
    }

    def step(means: KMeans[DT]): KMeans[DT] = {
      // Optimized for performance
      val dataWithClusterIdx = dataToUse map (v => (v, means.classify(v)))
      val newMeanValues = for {
        i <- 0 until means.k
        vs = dataWithClusterIdx.collect {
          case (v, clusterIdx) if clusterIdx == i => v
        } if vs.nonEmpty
      } yield support.getAverage(vs)
      new KMeans(newMeanValues.sorted)
    }

    @tailrec def iterate(means: KMeans[DT], stepsLeft: Int): KMeans[DT] = {
      if (stepsLeft <= 0) means
      else {
        val newMeans = step(means)
        if (newMeans == means) means else iterate(newMeans, stepsLeft - 1)
      }
    }

    val candidatesWithObjectives = (1 to AttemptsNum).par.map { _ =>
      val initial   = initClusters()
      val result    = iterate(initial, StepsNum)
      val objective = distortionCostFunction(result, dataToUse)
      (result, objective)
    }
    val result = candidatesWithObjectives.minBy(_._2)._1

    result
  }

  trait KMeansSupport[DT] extends Ordering[DT] {
    def getSquaredDistance(v1: DT, v2: DT): Double
    def getAverage(vs: IndexedSeq[DT]): DT
  }
}
