package scavenger.algebra

/** 
 * The generalized coordinate space with `Double` as field and `X` as base set. 
 * 
 * It is the vector space of all functions from `X` to `Double` that 
 * vanish almost everywhere. 
 * These functions are represented by wrapped maps from `X` to
 * `Double`, with all operations defined point-wise. Therefore, `X` must 
 * provide reasonable `hashCode` and `equals` implementations.
 *
 * This class is used to represent symbolically size-estimates for 
 * certain data structures.
 *
 * @since 2.3
 * @author Andrey Tyukin
 */
private[scavenger] case class GCS[X](underlying: Map[X, Double]) 
extends (X => Double) {
  def apply(x: X) = underlying.get(x).getOrElse(0.0)
  def +(other: GCS[X]): GCS[X] = {
    val allKeys = underlying.keySet ++ other.underlying.keySet
    GCS((for (k <- allKeys) yield (k, this(k) + other(k))).toMap)
  }
  def -(other: GCS[X]): GCS[X] = {
    val allKeys = underlying.keySet ++ other.underlying.keySet
    GCS((for (k <- allKeys) yield (k, this(k) - other(k))).toMap)
  }
  def *(factor: Double): GCS[X] = {
    GCS(for ((k, v) <- underlying) yield (k, v * factor))
  }

  def max(other: GCS[X]): GCS[X] = {
    val allKeys = underlying.keySet ++ other.underlying.keySet
    GCS((for (k <- allkeys) yield (k, math.max(this(k), other(k)))).toMap)
  }

  override def toString = {
    underlying.
    toList.
    sortBy(_._1.toString).
    map{case (k, v) => k + " -> " + v}.
    mkString
  }

  /** 
   * Returns `true` if `this` is smaller than `other` in 
   * at least one coordinate, and smaller or equal in all other coordinates.
   *
   * For example, `3 x + 5 y` is strictly smaller than `3 x + 4 y`.
   *
   * Note that this class does not inherit `Ordered`, because the ordering is
   * partial, not total.
   */
  def <(other: GCS[X]): Boolean = {
    var allLeq = true
    var exLe = false
    for ((k, v) <- underlying) {
      val w = other(k)
      allLeq &= (v <= w)
      exLe |= (v < w)
      if (!allLeq) return false
    }
    allLeq && exLe
  }

}

private[scavenger] object GCS {

  /** Zero of the vector space */
  def zero[X]: GCS[X] = GCS(Map.empty[X, Double])

  /** Returns `x`-th canonical basis vector */
  def basisVector[X](x: X): GCS[X] = GCS(Map(x -> 1.0))

  /** Constructor for functions which are non-zero at one single point */
  def apply[X](k: X, v: Double): GCS[X] = GCS(Map(k -> v))

  /** Returns a GCS which is the characteristic function of the given set,
    * that is, it takes every element of the set, multiplies it with scaling
    * factor `1.0`, and adds the vectors together.
    */
  def charFct[X](set: Set[X]): GCS[X] = {
    (for (elem <- set) yield basisVector(elem)).foldLeft(zero[X]){_ + _}
  }
}