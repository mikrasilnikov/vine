package pd2.ui

import pd2.ui.ProgressBar._

import scala.collection.mutable.ArrayBuffer

final case class ProgressBar(workItems : ArrayBuffer[ItemState], layout: ProgressBarLayout)

object ProgressBar {

  case class ProgressBarDimensions(labelWidth: Int, barWidth: Int)
  case class ProgressBarLayout(label : String, dimensions : ProgressBarDimensions)

  sealed trait ItemState
  case object Pending     extends ItemState
  case object InProgress  extends ItemState
  case object Completed   extends ItemState
  case object Failed      extends ItemState

  def render(bar : ProgressBar, tick : Long = 0) : String = {

    val itemsPerBarCell = bar.workItems.length.toDouble / bar.layout.dimensions.barWidth

    val barCells = (0 until bar.layout.dimensions.barWidth)
      .map (cellIndex => (cellIndex * itemsPerBarCell, (cellIndex + 1) * itemsPerBarCell))
      .map { case (from, until) =>
        val fromInt = from.toInt
        val untilInt = until.toInt

        if (fromInt != untilInt)
          bar.workItems.slice(from.toInt, until.toInt)
        else {
          if (bar.workItems.nonEmpty) Vector(bar.workItems(fromInt))
          else Vector(Pending) // Если bar пустой, то считаем, что он содержит 1 Pending элемент

        }
      }
      .map { items =>
        val cellState = {
          val somePending = items.contains(Pending)
          val someInProgress = items.contains(InProgress)
          val allCompleted = items.forall(state => state == Completed)

          if (allCompleted) Completed
          else if (someInProgress) InProgress
          else if (somePending) Pending
          else Failed
        }
        cellState
      }

    val barBuilder = new StringBuilder
    barBuilder.addOne('[')
    barCells.foreach { state =>
      barBuilder.addOne(
        state match {
          case Pending    => ' '
          case InProgress => tickSymbol(tick)
          case Completed  => '='
          case Failed     => '!'
        }
      )}
    barBuilder.addOne(']')

    val barStr = barBuilder.toString
    val fraction =
      bar.workItems.count(state => state == Completed || state == Failed ) /
      bar.workItems.length.toDouble

    val percentStr = s"${(fraction * 100).round.toInt.formatted("%3d")}%"

    s"${effectiveLabel(bar.layout)} $barStr $percentStr"
  }

  private def tickSymbol(tick : Long) : Char =
    tick % 4 match {
      case 0 => '|'
      case 1 => '/'
      case 2 => '-'
      case 3 => '\\'
    }

  private def effectiveLabel(layout : ProgressBarLayout): String = {
    if (layout.label.length <= layout.dimensions.labelWidth)
      layout.label + (" " * (layout.dimensions.labelWidth - layout.label.length))
    else
      layout.label.substring(0, layout.dimensions.labelWidth)
  }
}