create or replace procedure sync_pair_diff_partitions(diffs_table_name VARCHAR2 default 'diffs')
IS
BEGIN
  for p in (select domain,pair_key from pair) loop
    declare 
      pair_part varchar2(255);
      h_string varchar2(255);
      hex_string varchar2(255);
      part_name varchar2(255);
      matching INTEGER;
    begin
      pair_part := p.domain || '_' || p.pair_key;
      dbms_obfuscation_toolkit.md5(input_string => pair_part, checksum_string => h_string);
      hex_string := rawtohex(utl_raw.cast_to_raw(h_string));
      part_name := 'P_' || substr(hex_string, 0, 28);
  
      select count(*) into matching from all_tab_partitions where table_name=diffs_table_name and partition_name=part_name;
      
      if matching = 0 then
        dbms_output.enable;
        dbms_output.put_line('Adding missing partition ' || part_name || ' for ' || pair_part);
        
        execute immediate('alter table ' || diffs_table_name || ' add partition ' || part_name || ' values (''' || pair_part || ''')');
      end if;
    end;
  end loop;
end;