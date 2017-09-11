package cz.lbenda.spmodel;

import java.sql.Connection;

/**
 * Factory which generate stored procedure and execute it
 * @author lbenda on 9/10/17.
 */
public class SPSybaseFactory extends SPFactory {

  protected String createQuery(ProcedureDescription pd) {
    StringBuilder sb = new StringBuilder("{ ");
    if (pd.getCatalag() != null) {
      sb.append(pd.getCatalag()).append(".");
    }
    if (pd.getSchema() != null) {
      sb.append(pd.getSchema()).append(".");
    }
    sb.append(pd.getName()).append(" ");

    for (int i = 0; i < pd.getParams().size(); i++) {
      if (i > 0) { sb.append(", "); }
      sb.append("?");
    }

    sb.append(" }");
    return sb.toString();
  }
}
