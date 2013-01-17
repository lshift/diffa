package net.lshift.diffa.railyard.questions;

import net.lshift.diffa.adapter.scanning.RangeConstraint;
import net.lshift.diffa.adapter.scanning.ScanAggregation;
import net.lshift.diffa.adapter.scanning.ScanConstraint;
import net.lshift.diffa.config.AggregatingCategoryDescriptor;
import net.lshift.diffa.config.CategoryDescriptor;
import net.lshift.diffa.config.RangeCategoryDescriptor;
import net.lshift.diffa.interview.Question;
import net.lshift.diffa.interview.SimpleQuestion;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class QuestionBuilder {

  public static Question buildInitialQuestion(Map<String, CategoryDescriptor> categories) {

    Set<ScanConstraint> constraints = new HashSet<ScanConstraint>();
    Set<ScanAggregation> aggregations = new HashSet<ScanAggregation>();

    for (Map.Entry<String, CategoryDescriptor> entry : categories.entrySet()) {

      String name = entry.getKey();

      if (entry.getValue() instanceof RangeCategoryDescriptor) {
        RangeCategoryDescriptor rangeDescriptor = (RangeCategoryDescriptor) entry.getValue();
        RangeConstraint constraint = rangeDescriptor.toConstraint(name);
        constraints.add(constraint);
      }

      if (entry.getValue() instanceof AggregatingCategoryDescriptor) {
        AggregatingCategoryDescriptor aggregatingCategoryDescriptor = (AggregatingCategoryDescriptor) entry.getValue();

        ScanAggregation aggregation = aggregatingCategoryDescriptor.getInitialBucketing(name);
        aggregations.add(aggregation);
      }

    }

    return new SimpleQuestion(constraints, aggregations);
  }

}
