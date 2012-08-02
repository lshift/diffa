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

package net.lshift.diffa.kernel.util

import org.joda.time.DateTime
import org.easymock.internal.ArgumentToString
import org.easymock.{IAnswer, IArgumentMatcher, EasyMock}

/**
 * Utilities for making EasyMock work nicely with Scala.
 */

object EasyMockScalaUtils {
  /**
   * Replacement for EasyMock.anyObject when working with a Function1 that returns Unit.
   */
  def anyUnitF1[A] = {
    EasyMock.anyObject
    (a:A) => {}
  }

  /**
   * Replacement for EasyMock.anyObject when working with a Function3 that returns Unit.
   */
  def anyUnitF3[A, B, C] = {
    EasyMock.anyObject
    (a:A, b:B, c:C) => {}
  }

  /**
   * Replacement for EasyMock.anyObject when working with a Function4 that returns Unit.
   */
  def anyUnitF4[A, B, C, D] = {
    EasyMock.anyObject
    (a:A, b:B, c:C, d:D) => {}
  }

  /**
   * Replacement for EasyMock.anyObject when working with a Function5 that returns Unit.
   */
  def anyUnitF5[A, B, C, D, E] = {
    EasyMock.anyObject
    (a:A, b:B, c:C, d:D, e:E) => {}
  }

  def anyString = {
    EasyMock.anyObject
    ""
  }

  def anyTimestamp = {
    EasyMock.anyObject()
    new DateTime
  }

  /**
   * Allows a DateTime to be validated by being between two values.
   */
  def between(start:DateTime, end:DateTime):DateTime = {
    // We create our own matcher here that confirms the value is between the two points. Note that we can't
    // use EasyMock.and(EasyMock.geq(start), EasyMock.leq(end)) because DateTime isn't Comparable[DateTime] - it is just
    // Comparable, and this makes the Scala type-checker unhappy.
    EasyMock.reportMatcher(new IArgumentMatcher() {
      def matches(argument: Any) = {
        argument match {
          case d:DateTime => d.compareTo(start) >= 0 && d.compareTo(end) <= 0
          case _          => false
        }
      }
      def appendTo(buffer: StringBuffer) = buffer.append("between " + start + " and " + end)
    })
    null
  }

  def asUnorderedList[T](expected:Seq[T]):Seq[T] = {
    EasyMock.reportMatcher(new IArgumentMatcher() {
      def matches(argument: Any):Boolean = {
        val argSeq = argument.asInstanceOf[Seq[T]]

        argSeq.toSet == expected.toSet
      }
      
      def appendTo(buffer: StringBuffer) {
        ArgumentToString.appendArgument(expected, buffer)
      }
    })
    null
  }

  val emptyAnswer = new IAnswer[Unit] {
    def answer() {}
  }
}