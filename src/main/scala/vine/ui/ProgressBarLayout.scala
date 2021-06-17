package vine.ui

final case class ProgressBarLayout(label : String, dimensions : ProgressBarDimensions)
final case class ProgressBarDimensions(labelWidth: Int, barWidth: Int)