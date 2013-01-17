create table things (
  id varchar(32) primary key,
  version varchar(32) not null,
  entry_date date
);

drop function md5 if exists;

create function md5(v varchar(32672)) returns varchar(32)
language java deterministic no sql
external name 'CLASSPATH:org.apache.commons.codec.digest.DigestUtils.md5Hex';