package net.lshift.hibernate.migrations;


import org.junit.Test;

import java.sql.Connection;
import java.util.Arrays;

import static net.lshift.hibernate.migrations.HibernateHelper.mockExecutablePreparedStatement;
import static org.easymock.EasyMock.*;

public class CopyTableBuilderTest {
  
  @Test
  public void shouldDropColumnUsingCapitalizedName() throws Exception {
    MigrationBuilder mb = new MigrationBuilder(HibernateHelper.configuration());

    Iterable<String> columns = Arrays.asList("foo", "bar", "baz");
    mb.copyTableContents("src", "dest", columns);

    Connection conn = createStrictMock(Connection.class);

    expect(conn.prepareStatement("insert into dest(foo,bar,baz) select foo,bar,baz from src")).andReturn(mockExecutablePreparedStatement());
    replay(conn);

    mb.apply(conn);
    verify(conn);
  }
}
