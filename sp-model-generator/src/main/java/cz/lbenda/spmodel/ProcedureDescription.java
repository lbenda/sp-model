package cz.lbenda.spmodel;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Complete information about procedure
 * @author lbenda on 9/9/17.
 */
public class ProcedureDescription {

  private String catalag; public String getCatalag() { return catalag; }  public void setCatalag(String catalag) { this.catalag = catalag; }
  private String schema; public String getSchema() { return schema; } public void setSchema(String schema) { this.schema = schema; }
  private String name; public String getName() { return name; } public void setName(String name) { this.name = name; }
  private List<ParamDescription> params = new ArrayList<>(); public List<ParamDescription> getParams() { return params; } public void setParams(List<ParamDescription> params) { this.params = params; }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (obj.getClass() != getClass()) {
      return false;
    }
    ProcedureDescription rhs = (ProcedureDescription) obj;
    return new EqualsBuilder()
            .append(this.catalag, rhs.catalag)
            .append(this.schema, rhs.schema)
            .append(this.name, rhs.name)
            .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
            .append(catalag)
            .append(schema)
            .append(name)
            .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
            .append("catalag", catalag)
            .append("schema", schema)
            .append("name", name)
            .toString();
  }
}
