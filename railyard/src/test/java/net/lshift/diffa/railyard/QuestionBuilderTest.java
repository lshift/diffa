package net.lshift.diffa.railyard;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.lshift.diffa.adapter.scanning.*;
import net.lshift.diffa.config.CategoryDescriptor;
import net.lshift.diffa.config.RangeCategoryDescriptor;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.railyard.questions.QuestionBuilder;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import java.util.Map;
import java.util.Set;

@RunWith(Theories.class)
public class QuestionBuilderTest {

  @DataPoint
  public static Scenario singleDate() {
    String name = "some_date";
    String lower = "1999-10-10";
    String upper = "1999-10-11";

    CategoryDescriptor descriptor = new RangeCategoryDescriptor("date", lower, upper);
    ScanConstraint constraint = new DateRangeConstraint(name, lower, upper);
    ScanAggregation aggregation = new DateAggregation(name, DateGranularityEnum.Yearly, null);
    return new Scenario(ImmutableMap.of(name, descriptor), ImmutableSet.of(constraint), ImmutableSet.of(aggregation));
  }

  @Theory
  public void shouldBuildInitialConstraintsAndAggregations(Scenario scenario) {
    Question question = QuestionBuilder.buildInitialQuestion(scenario.getCategories());
    assertEquals(scenario.getConstraints(), question.getConstraints());
    assertEquals(scenario.getAggregations(), question.getAggregations());
  }

  private static class Scenario {
    private Map<String,CategoryDescriptor> categories;
    private Set<ScanConstraint> constraints;
    private Set<ScanAggregation> aggregations;

    private Scenario(Map<String, CategoryDescriptor> categories, Set<ScanConstraint> constraints, Set<ScanAggregation> aggregations) {
      this.categories = categories;
      this.constraints = constraints;
      this.aggregations = aggregations;
    }

    public Map<String, CategoryDescriptor> getCategories() {
      return categories;
    }

    public Set<ScanAggregation> getAggregations() {
      return aggregations;
    }

    public Set<ScanConstraint> getConstraints() {
      return constraints;
    }
  }
}
