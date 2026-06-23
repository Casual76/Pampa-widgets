package com.pampa.widgets.core.update

internal fun isStableVersionNewer(candidate: String, current: String): Boolean {
  if (candidate.contains("-")) return false
  return compareAppVersions(candidate, current) > 0
}

internal fun compareAppVersions(left: String, right: String): Int {
  val leftParts = left.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
  val rightParts = right.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
  val max = maxOf(leftParts.size, rightParts.size)

  for (index in 0 until max) {
    val leftValue = leftParts.getOrElse(index) { 0 }
    val rightValue = rightParts.getOrElse(index) { 0 }
    if (leftValue != rightValue) return leftValue.compareTo(rightValue)
  }

  val leftPreRelease = left.substringAfter("-", "")
  val rightPreRelease = right.substringAfter("-", "")
  return when {
    leftPreRelease.isBlank() && rightPreRelease.isNotBlank() -> 1
    leftPreRelease.isNotBlank() && rightPreRelease.isBlank() -> -1
    else -> leftPreRelease.compareTo(rightPreRelease)
  }
}
