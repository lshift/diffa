-- Zoom level should be a parameter
create or replace PROCEDURE aggregatedDiffs
(  res OUT SYS_REFCURSOR,
   domainName IN diffs.domain%type,
   pairKey    IN diffs.pair%type,
   lowerBound IN diffs.detected_at%type,
   upperBound IN diffs.detected_at%type) AS
BEGIN
  OPEN res FOR select
      trunc(d.detected_at,'HH') +
      (trunc(( cast(d.detected_at as date) - trunc(d.detected_at,'HH')) * 24/(15/60)))/(60/15)/24 lower_bound,
      max(d.detected_at) last_detection_time,
      count(*) count,
      d.pair pair,
      d.domain domain
    from
      diffs d
    where
      d.pair = pairKey and
      d.domain = domainName and
      d.detected_at >= lowerBound and
      d.detected_at <= upperBound
    group by
      trunc(d.detected_at,'HH') + (trunc(( cast(d.detected_at as date) - trunc(d.detected_at,'HH'))*24/(15/60)))/(60/15)/24,
      d.pair,
      d.domain
    order by
      last_detection_time;
END aggregatedDiffs;