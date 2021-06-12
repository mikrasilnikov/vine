package pd2.ui

import org.fusesource.jansi.Ansi.ansi


final case class BucketProgressBar(buckets : Vector[ProgressBucket], layout : ProgressBarLayout)
final case class ProgressBucket(size : Int, completed : Int, failed : Int) {
  val progress = completed + failed
  val isEmpty = (completed + failed) == 0
  val isCompleted = progress >= size
  val withFailures   = failed > 0
}

object BucketProgressBar {

  final case class Section(from : Double, to : Double, value : Boolean)

  def render(bar : BucketProgressBar) : String = {

    val acc = List[(Int, ProgressBucket)]()
    val withAddr = bar.buckets.foldLeft(acc) { (acc, b) =>
      acc match {
        case Nil => (0, b) :: Nil
        case (i0, b0) :: _ => (i0 + b0.size, b) :: acc
      }
    }.reverse

    val completionSections = withAddr.flatMap { case (addr, bucket) =>
      if (bucket.isEmpty)
        Section(addr, addr + bucket.size, value = false) :: Nil
      else if (bucket.isCompleted)
        Section(addr, addr + bucket.size, value = true) :: Nil
      else
        Section(addr, addr + bucket.progress, value = true) ::
        Section(addr + bucket.progress, addr + bucket.size, value = false) :: Nil
    }

    val failureSections = withAddr.flatMap { case (addr, bucket) =>
      if (!bucket.withFailures)
        Section(addr, addr + bucket.size, value = false) :: Nil
      else
        Section(addr, addr + bucket.failed, value = true) ::
        Section(addr + bucket.failed, addr + bucket.size, value = false) :: Nil
    }

    val itemsCount = bar.buckets.map(_.size).sum
    val scale = itemsCount.toDouble / bar.layout.dimensions.barWidth
    val completions = mergeSections(completionSections.map { case Section(f,t,v) => Section(f/scale,t/scale,v) })
    val failures    = mergeSections(failureSections.map { case Section(f,t,v) => Section(f/scale,t/scale,v) })

    val cellsBuilder = new StringBuilder
    (0 until bar.layout.dimensions.barWidth).foreach { i =>
      val isCompleted = completions.exists(s => s.from <= i && i+1 <= s.to && s.value)
      val isFailed =  failures.exists(s => s.from <= i && i+1 <= s.to && s.value) ||
                      failures.exists(s => ((i < s.from && s.from < i+1) || (i < s.to && s.to < i+1)) && s.value)

      val text = if (isFailed) "!" else if (isCompleted) "=" else " "
      cellsBuilder.append(text)
    }

    val fraction = bar.buckets.map(b => b.completed + b.failed).sum.toDouble / itemsCount
    val percentage = s"${(fraction * 100).floor.toInt.formatted("%3d")}%"

    s"${effectiveLabel(bar.layout)} [$cellsBuilder] $percentage"
  }

  private[ui] def mergeSections(sections : List[Section]) : List[Section] = {

    def merge1(acc : List[Section], rem : List[Section]) : (Section, List[Section]) = {
      import math._
      rem match {
        case h :: t if acc.head.value == h.value && acc.head.to == h.from =>
          merge1(h :: acc, t)
        case _ =>
          val result = acc.reduce[Section] { case (Section(f1,t1,v), Section(f2,t2,_)) => Section(min(f1,f2), max(t1,t2), v) }
          (result, rem)
      }
    }

    def mergeAll(res : List[Section], rem : List[Section]) : List[Section] = {
      rem match {
        case Nil    => res.reverse
        case h :: t =>
          val (s, r) = merge1(h :: Nil, t)
          mergeAll(s :: res, r)
      }
    }

    mergeAll(Nil, sections)
  }

  private def effectiveLabel(layout : ProgressBarLayout): String = {
    if (layout.label.length <= layout.dimensions.labelWidth)
      layout.label + (" " * (layout.dimensions.labelWidth - layout.label.length))
    else
      layout.label.substring(0, layout.dimensions.labelWidth)
  }
}