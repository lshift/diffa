package net.lshift.diffa.kernel.config

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.experimental.theories.{DataPoint, Theories, Theory}
import org.hamcrest.Matchers._
import org.hamcrest.MatcherAssert.assertThat
import net.lshift.diffa.participant.scanning.{AsciiCollation, Collation}
import org.junit.Assert._
import scala.Tuple3

trait CollationTestMixin {
  val ordering: Collation
  @Theory def sortsBefore(ex: Tuple3[String, String, Boolean]) = ex match {
    case (left, right, result) =>
      assert(ordering.sortsBefore(left, right) == result,
        ("%s should sort before %s => %s".format(left, right, result)))
  }
}

@RunWith(classOf[Theories])
class UnicodeCollationOrderingTest extends CollationTestMixin {
  val ordering = UnicodeCollationOrdering
}
object UnicodeCollationOrderingTest {
  @DataPoint def trivial = ("a", "b", true)
  @DataPoint def caseIsSecondaryToOrdinal = ("a", "B", true)

  @DataPoint def cSortsAfterB = ("c", "b", false)
  @DataPoint def upperCaseDoesNotSortBeforeLower() = ("C", "b", false)

}


@RunWith(classOf[Theories])
class AsciiCollationOrderingTest extends CollationTestMixin {
  val ordering = AsciiCollationOrdering
}
object AsciiCollationOrderingTest {
  @DataPoint def trivial = ("a", "b", true)
  @DataPoint def isCaseInsensitive = ("a", "B", false)

  @DataPoint def cSortsAfterB = ("c", "b", false)
  @DataPoint def upperCaseSortsBeforeLower = ("C", "b", true)

}

class CollationOrderingTest {
  @Test def testGetCollatorForUnicode() = {
    assertEquals(UnicodeCollationOrdering, CollationOrdering.named("unicode"))
  }
  @Test def testGetCollatorForAscii() = {
    assertEquals(AsciiCollationOrdering, CollationOrdering.named("ascii"))
  }

  @Test(expected=classOf[Exception])
  def testGetInvalidCollationThrowsException {
    CollationOrdering.named("an invalid name")
  }
}