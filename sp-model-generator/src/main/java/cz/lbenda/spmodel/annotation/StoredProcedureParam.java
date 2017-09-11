package cz.lbenda.spmodel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Parameter of stored procedure
 * @author lbenda on 9/10/17.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StoredProcedureParam {
  /** Parameter name */
  String name();
  /** Order of property in method */
  int order();
  /** SQL type */
  int sqlType();
}
