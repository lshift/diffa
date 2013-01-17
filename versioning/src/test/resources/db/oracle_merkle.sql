select trunc(month,'YYYY') as year, dbms_obfuscation_toolkit.md5(input => listagg(digest,'') within group (order by month)) as digest
from (
  select trunc(day,'MM') as month, dbms_obfuscation_toolkit.md5(input => listagg(digest,'') within group (order by day)) as digest
  from (
    select day, dbms_obfuscation_toolkit.md5(input => listagg(digest,'') within group (order by bucket)) as digest
      from (
      select day, bucket, dbms_obfuscation_toolkit.md5(input => listagg(version,'') within group (order by id)) as digest
        from (
          select id, trunc(entry_date,'dd') as day, version,
          ceil( (row_number() over (partition by trunc(entry_date,'dd') order by id)) / 125) as bucket
          from t2
          where entry_date between to_timestamp('01-JAN-2011', 'DD-MON-YYYY') and  to_timestamp('31-JAN-2011', 'DD-MON-YYYY')
        )
      group by day, bucket
      )
    group by day
    )
  group by trunc(day,'MM')
  )
group by trunc(month,'YYYY')
/