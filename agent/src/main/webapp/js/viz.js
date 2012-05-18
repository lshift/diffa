Diffa.Helpers.Viz = {
  /**
   * Transforms the bucket value from the API into a value we can use for
   * sizing the blob in the heatmap.
   *
   *   API   -> Size
   *   0     -> 0
   *   1     -> m     (1)
   *   ...
   *   100   -> M     (2)
   *
   * M is the maximum size. m is the minimum display size.
   * Between (1) and (2), growth should be logarithmic based
   * on the endpoints. Inputs over 100 are capped to M.
   *
   * We'd like a function f on [0, 100] where
   *
   *   f(1)   = m,
   *   f(100) = M
   *
   * and where f(x) is in some way logarithmic. So let
   *
   *   f(x) = a + b*log(x).
   *
   * (Note this is equivalent to f(x) = a + b*log(c*x))
   *
   * and solve for a, b:
   *
   *   f(1)   = a = m
   *   f(100) = m + b*log(100) = M  =>  b = (M-m)/log(100)
   */
  transformBucketSize: function(size, opts) {
    // Validate the options
    if (!opts.outputMax) throw "Missing outputMax option"; // the maximum output value. f(x) = this if x >= inputMax
    if (!opts.outputMin) throw "Missing outputMin option"; // the minimum value of f(x). f(x) = this, if x <= inputMin
    if (!opts.inputMin) throw "Missing inputMin option";   // the minimum input value. any value <= this results in outputMin
    if (!opts.inputMax) throw "Missing inputMax option";   // the maximum input value. f(x) = outputMax if x >= inputMax

    if (size == 0)             { return 0; }
    if (size <= opts.inputMin) { return opts.outputMin; }

    var a = opts.outputMin;
    var b = (opts.outputMax - opts.outputMin)/Math.log(opts.inputMax);

    return Diffa.Helpers.Viz.limit(a + b*Math.log(size), opts.outputMax);
  },

  /**
   * Limits a value to a given maximum value, somewhat like Math.min().
   * Returns an object with two properties: "value", the limited value and "limited",
   * which flags whether the original value was greater than the maximum value.
   */
  limit: function(value, maximum) {
    if (value <= maximum) {
      return {"value":value, "limited":false};
    }
    return {"value":maximum, "limited":true};
  }
};