package net.lshift.diffa.conductor;

public class DriverConfiguration {


  private String tableName;
  private String idFieldName;
  private String versionFieldName;
  private String partitionFieldName;
  private String driverClass;
  private String url;
  private String username;
  private String password;
  private String jooqDialect;

  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  public void setIdFieldName(String idFieldName) {
    this.idFieldName = idFieldName;
  }

  public void setVersionFieldName(String versionFieldName) {
    this.versionFieldName = versionFieldName;
  }

  public void setPartitionFieldName(String partitionFieldName) {
    this.partitionFieldName = partitionFieldName;
  }

  public void setDriverClass(String driverClass) {
    this.driverClass = driverClass;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setDialect(String dialect) {
    this.jooqDialect = dialect;
  }

  public String getTableName() {
    return tableName;
  }

  public String getIdFieldName() {
    return idFieldName;
  }

  public String getVersionFieldName() {
    return versionFieldName;
  }

  public String getPartitionFieldName() {
    return partitionFieldName;
  }

  public String getDriverClass() {
    return driverClass;
  }

  public String getUrl() {
    return url;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getDialect() {
    return jooqDialect;
  }
}
