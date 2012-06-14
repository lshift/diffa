/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.participant.scanning;


/**
 * This type represents the concept of an ordering of ids. This is so that
 * we can parameterise the ordering-sensitive parts of our code (Eg: the
 * DigestBuilder, output from the VersionCorrelationStore) in order to
 * support systems who might use an ordering that differs from doing naive
 * ASCII string comparisons. Eg: MySQL uses a swedish case insensitive
 * collation by default, where the case is considered to be less important
 * than the relative ordering of the character within the alphabet.
 * So with a varchar primary key with a case insensitive collation, you might end up
 * with the following ordering:
 *
 * [foo, FooBar, fooBar2]
 *
 * Wheras with a plain ASCII-ordered ordering, we would have:
 *
 * [FooBar, foo, fooBar2]
 */

public interface Collation {
  /**
   * Describes a strict weak ordering over string values. It is semantically
   * equivalent to (left < right), but taking into account other criteria
   * required for an ordering appropriate to the participant's domain (eg:
   * case sensitivity, numeric ordering, &c).
   *
   * @param left The left hand side of the comparison,.
   * @param right The right hand side of the comparison.
   * @return The result of said comparison.
   */
  public boolean sortsBefore(String left, String right);
}
