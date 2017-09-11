package cz.lbenda.spmodel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stored procedure
 * @author lbenda on 9/10/17.
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StoredProcedure {
  /** Catalog name */
  String catalog();
  /** DB schema name */
  String schema();
  /** Name of stored procedure */
  String name();
}
