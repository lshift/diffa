package net.lshift.diffa.participant.scanning;

/**
 * Marker interface for constraints that constrain over a range of values.
 */
public interface RangeConstraint extends ScanConstraint {

  /**
   * The start of this range as a string. The specific format will depend on the concrete implementation.
   *
   * This will return null if the start value has not been set (i.e. the constraint has no lower bound)
   *
   * @return the starting value.
   */
  public String getStartText();

  /**
   * The end of this range as a string. The specific format will depend on the concrete implementation.
   *
   * This will return null if the end value has not been set (i.e. the constraint has no upper bound)
   *
   * @return the ending value.
   */
  public String getEndText();

  /**
   * Indicates whether this constraint has a lower bound.
   * @return True if this constraint has a lower bound.
   */
  public boolean hasLowerBound();

  /**
   * Indicates whether this constraint has a lower bound.
   * @return True if this constraint has a lower bound.
   */
  public boolean hasUpperBound();

}
