package pd2.ui

import org.fusesource.jansi.Ansi.ansi

import scala.collection.mutable

final case class BucketProgressBar(buckets : Vector[ProgressBucket], layout : ProgressBarLayout)
final case class ProgressBucket(size : Int, completed : Int, failed : Int)

object BucketProgressBar {

  def render(bar : BucketProgressBar) : String = {

    val itemsCount = bar.buckets.map(_.size).sum

    val cells = itemsCount match {
      case 0 => " " * bar.layout.dimensions.barWidth
      case _ =>
        val itemsPerCell = itemsCount.toDouble / bar.layout.dimensions.barWidth
        bar.buckets
          .map { bucket =>
            val bucketLength = (bucket.size / itemsPerCell).round.toInt
            renderBucket(bucket, bucketLength) }
          .fold("")(_ + _)
    }

    val fraction = bar.buckets.map(b => b.completed + b.failed).sum.toDouble / itemsCount
    val percentage = s"${(fraction * 100).round.toInt.formatted("%3d")}%"

    s"${effectiveLabel(bar.layout)} [$cells] $percentage"
  }

  private def renderBucket(bucket : ProgressBucket, length : Int) : String = {
    val itemsPerCell = bucket.size.toDouble / length
    val completedOrFailedItems = bucket.completed + bucket.failed

    val nonEmpty = (completedOrFailedItems / itemsPerCell).round.toInt
    val empty = length - nonEmpty

    val text = "=" * nonEmpty ++ " " * empty
    if (bucket.failed == 0) text
    else ansi().bgRed().render(text).reset().toString
  }

  private def effectiveLabel(layout : ProgressBarLayout): String = {
    if (layout.label.length <= layout.dimensions.labelWidth)
      layout.label + (" " * (layout.dimensions.labelWidth - layout.label.length))
    else
      layout.label.substring(0, layout.dimensions.labelWidth)
  }
}